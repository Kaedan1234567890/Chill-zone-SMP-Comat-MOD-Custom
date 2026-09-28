package com.chillzone.combat;

import com.combat.gui.ChestGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 54-slot player-facing selectable PvP ability menu. */
public final class RankedAbilitiesGui extends ChestGui {
    private static final int PAGE_SIZE = 16;
    private static final int[] ABILITY_SLOTS = {
            0,1,2,3,4,5,6,7,
            18,19,20,21,22,23,24,25
    };
    private static final int[] STATUS_SLOTS = {
            9,10,11,12,13,14,15,16,
            27,28,29,30,31,32,33,34
    };

    private final int page;

    public RankedAbilitiesGui(ServerPlayer player, int page) {
        super(player, "PvP Rank Abilities", 6);
        int maxPage = Math.max(0, (AbilityDefinition.values().length - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(page, maxPage));
    }

    @Override
    protected void setupItems() {
        inventory.clearContent();
        int rank = RankManager.getRank(player.getUUID());
        AbilityLoadoutStore.sanitize(player.getUUID(), rank);
        Set<String> equipped = AbilityLoadoutStore.equipped(player.getUUID());
        int slots = AbilityDefinition.slotsForRank(rank);

        AbilityDefinition[] abilities = AbilityDefinition.values();
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            if (index >= abilities.length) break;
            AbilityDefinition ability = abilities[index];
            inventory.setItem(ABILITY_SLOTS[i], abilityItem(ability, rank, equipped));
            inventory.setItem(STATUS_SLOTS[i], statusItem(ability, rank, equipped, slots));
        }

        if (page > 0) inventory.setItem(45, named(new ItemStack(Items.ARROW), "Previous Page", ChatFormatting.YELLOW));
        if ((page + 1) * PAGE_SIZE < abilities.length) inventory.setItem(53, named(new ItemStack(Items.ARROW), "Next Page", ChatFormatting.YELLOW));

        ItemStack info = new ItemStack(rank >= 1 ? Items.NETHER_STAR : Items.BARRIER);
        info.set(DataComponents.CUSTOM_NAME, Component.literal(rank >= 1 ? "Your Ability Loadout" : "Unranked Preview")
                .withStyle(rank >= 1 ? ChatFormatting.GOLD : ChatFormatting.RED, ChatFormatting.BOLD));
        List<Component> lore = new ArrayList<>();
        if (rank >= 1 && rank <= 10) {
            lore.add(Component.literal("PvP Rank: #" + rank).withStyle(ChatFormatting.YELLOW));
            lore.add(Component.literal("Equipped: " + equipped.size() + " / " + slots).withStyle(ChatFormatting.AQUA));
            lore.add(Component.literal("Your rank health and Top-3 permanent effects are automatic.").withStyle(ChatFormatting.GRAY));
        } else {
            lore.add(Component.literal("You are currently Unranked.").withStyle(ChatFormatting.RED));
            lore.add(Component.literal("You can browse every ability, but cannot equip one yet.").withStyle(ChatFormatting.GRAY));
        }
        lore.add(Component.literal("Page " + (page + 1) + " / " + (((abilities.length - 1) / PAGE_SIZE) + 1)).withStyle(ChatFormatting.DARK_GRAY));
        info.set(DataComponents.LORE, new ItemLore(lore));
        inventory.setItem(49, info);
    }

    @Override
    protected void handleSlotClick(int slot, int button, ContainerInput input) {
        if (slot == 45 && page > 0) {
            new RankedAbilitiesGui(player, page - 1).open();
            return;
        }
        if (slot == 53 && (page + 1) * PAGE_SIZE < AbilityDefinition.values().length) {
            new RankedAbilitiesGui(player, page + 1).open();
            return;
        }

        AbilityDefinition ability = abilityForClickedSlot(slot);
        if (ability == null) return;

        int rank = RankManager.getRank(player.getUUID());
        if (rank < 1 || rank > 10) {
            player.sendSystemMessage(Component.literal("You must have a PvP rank before you can equip abilities.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (CombatState.isInCombat(player)) {
            player.sendSystemMessage(Component.literal("You cannot change your ability loadout while in combat.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!ability.unlockedFor(rank)) {
            player.sendSystemMessage(Component.literal(ability.displayName() + " requires PvP Rank #" + ability.unlockRank() + " or better.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        boolean wasEquipped = AbilityLoadoutStore.isEquipped(player.getUUID(), ability.id());
        if (!wasEquipped && AbilityLoadoutStore.equipped(player.getUUID()).size() >= AbilityDefinition.slotsForRank(rank)) {
            player.sendSystemMessage(Component.literal("All of your ability slots are full. Unequip an ability first.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        if (AbilityLoadoutStore.toggle(player.getUUID(), rank, ability)) {
            boolean nowEquipped = AbilityLoadoutStore.isEquipped(player.getUUID(), ability.id());
            player.sendSystemMessage(Component.literal((nowEquipped ? "Equipped " : "Unequipped ") + ability.displayName() + ".")
                    .withStyle(nowEquipped ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            setupItems();
        }
    }

    private AbilityDefinition abilityForClickedSlot(int slot) {
        for (int i = 0; i < PAGE_SIZE; i++) {
            if (slot != ABILITY_SLOTS[i] && slot != STATUS_SLOTS[i]) continue;
            int index = page * PAGE_SIZE + i;
            AbilityDefinition[] values = AbilityDefinition.values();
            return index < values.length ? values[index] : null;
        }
        return null;
    }

    private ItemStack abilityItem(AbilityDefinition ability, int rank, Set<String> equipped) {
        ItemStack stack = new ItemStack(ability.icon());
        boolean unlocked = ability.unlockedFor(rank);
        boolean active = equipped.contains(ability.id());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(ability.displayName())
                .withStyle(active ? ChatFormatting.GREEN : (unlocked ? ChatFormatting.GOLD : ChatFormatting.RED), ChatFormatting.BOLD));
        List<Component> lore = new ArrayList<>();
        for (String line : ability.description()) lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        lore.add(Component.empty());
        lore.add(Component.literal("Unlock: PvP Rank #" + ability.unlockRank() + " or better")
                .withStyle(unlocked ? ChatFormatting.AQUA : ChatFormatting.RED));
        lore.add(Component.literal(active ? "EQUIPPED" : (unlocked ? "Click to equip" : "LOCKED"))
                .withStyle(active ? ChatFormatting.GREEN : (unlocked ? ChatFormatting.YELLOW : ChatFormatting.RED), ChatFormatting.BOLD));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private ItemStack statusItem(AbilityDefinition ability, int rank, Set<String> equipped, int slots) {
        boolean unlocked = ability.unlockedFor(rank);
        boolean active = equipped.contains(ability.id());
        ItemStack stack;
        String label;
        ChatFormatting color;
        if (active) {
            stack = new ItemStack(Items.EMERALD);
            label = "EQUIPPED";
            color = ChatFormatting.GREEN;
        } else if (unlocked && equipped.size() < slots) {
            stack = new ItemStack(Items.GOLD_INGOT);
            label = "AVAILABLE";
            color = ChatFormatting.YELLOW;
        } else {
            stack = new ItemStack(Items.REDSTONE);
            label = unlocked ? "NO FREE SLOTS" : "LOCKED";
            color = ChatFormatting.RED;
        }
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(label).withStyle(color, ChatFormatting.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(ability.displayName()).withStyle(ChatFormatting.GRAY));
        if (unlocked) lore.add(Component.literal("Click to " + (active ? "unequip" : "equip") + ".").withStyle(ChatFormatting.WHITE));
        else lore.add(Component.literal("Requires Rank #" + ability.unlockRank() + " or better.").withStyle(ChatFormatting.RED));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack named(ItemStack stack, String name, ChatFormatting color) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color, ChatFormatting.BOLD));
        return stack;
    }
}
