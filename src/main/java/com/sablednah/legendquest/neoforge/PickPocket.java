package com.sablednah.legendquest.neoforge;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.legendquest.core.Stat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Picking a pocket: the thief sees the mark's inventory as a chest, clicks one
 * thing, and a Sleight of Hand (DEX) check decides whether it comes away. Pass
 * or fail, the pocket closes. One reach per cast is the whole point, and the
 * skill's cooldown is what stops the next.
 *
 * <p>A chest menu, so a vanilla client can do it. The layout is Standards'
 * {@code /invsee} with the rows a thief may not reach left out: the pack
 * always, then the hotbar, then the equipment row (H C L B, a spacer, the off
 * hand) only when the skill allows any of it.</p>
 *
 * <p><b>Nothing here moves an item except a passed roll.</b> Vanilla's
 * {@code Slot.mayPlace} says yes to everything and never asks the container,
 * which is how Standards' first invsee destroyed items dropped into slots that
 * led nowhere. So these slots refuse both ways, and {@link Menu#clicked}
 * replaces vanilla's click handling outright rather than filtering it: a
 * drag, a double-click or a shift-click has no path to an item at all. The
 * view reads the mark's inventory live, so what the thief sees is what is
 * there now, and every item carries the difficulty of taking it.</p>
 */
public final class PickPocket {

    /** Which parts of the mark the skill lets a thief reach. The pack is always in reach. */
    public record Reach(boolean hotbar, boolean armour, boolean offhand, boolean mainHand) {}

    // How much harder each place is to reach into than a pocket.
    private static final int HOTBAR = 2;
    private static final int ARMOUR = 5;
    private static final int OFFHAND = 5;
    /** Taking the sword out of somebody's hand without them noticing takes a very good thief. */
    private static final int MAIN_HAND = 10;

    // And how much harder each thing is to lift unnoticed. Bread is easy; a named,
    // enchanted heirloom is not.
    private static final int NAMED = 3;
    private static final int LORE_MAX = 3;
    private static final int PER_FULL_STACK_QUARTER = 1; // +1 for every 16 in the stack

    private PickPocket() {
    }

    /** Opens the mark's pockets to the thief. The caller has already checked they may. */
    public static void open(ServerPlayer thief, ServerPlayer mark, int baseDc, Reach reach,
            double range, boolean alert) {
        Pockets pockets = new Pockets(mark, baseDc, reach);
        Component title = Feedback.colored(Lang.fmt("msg.pickpocket.title",
                "target", mark.getName().getString()));
        thief.openMenu(new SimpleMenuProvider(
                (id, inventory, player) -> new Menu(id, inventory, thief, pockets, range, alert),
                title));
    }

    /**
     * The difficulty of lifting this stack from this place. Uses the item's
     * own rarity rather than {@code getRarity()}, which already counts
     * enchantment once — and enchantments are counted here, level by level.
     */
    static int difficulty(ItemStack stack, int baseDc, int placeBonus) {
        int dc = baseDc + placeBonus;
        dc += switch (stack.getOrDefault(DataComponents.RARITY, Rarity.COMMON)) {
            case COMMON -> 0;
            case UNCOMMON -> 2;
            case RARE -> 4;
            case EPIC -> 6;
        };
        dc += enchantmentLevels(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        dc += enchantmentLevels(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
        if (stack.has(DataComponents.CUSTOM_NAME)) dc += NAMED;
        dc += Math.min(LORE_MAX, stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().size());
        dc += (stack.getCount() / 16) * PER_FULL_STACK_QUARTER;
        return dc;
    }

    private static int enchantmentLevels(ItemEnchantments enchantments) {
        int total = 0;
        for (var entry : enchantments.entrySet()) {
            total += entry.getIntValue();
        }
        return total;
    }

    private static ItemStack pane(String label) {
        ItemStack stack = new ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(label).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return stack;
    }

    /** One position in the view: an inventory slot, how much harder it is, and whether it shows at all. */
    private record Place(int index, int bonus) {
        static final Place SPACER = new Place(-1, 0);
    }

    /** A read-only, live view of the mark's inventory, laid out a chest row at a time. */
    static final class Pockets implements Container {

        private final ServerPlayer mark;
        private final int baseDc;
        private final Reach reach;
        private final List<Place> places = new ArrayList<>();
        private final int hotbarRow;
        private final ItemStack spacer = pane(" ");
        private final ItemStack outOfReach = pane(Lang.get("msg.pickpocket.out_of_reach"));
        private final ItemStack inHand = pane(Lang.get("msg.pickpocket.in_hand"));

        Pockets(ServerPlayer mark, int baseDc, Reach reach) {
            this.mark = mark;
            this.baseDc = baseDc;
            this.reach = reach;
            for (int i = 0; i < 27; i++) places.add(new Place(i + 9, 0));
            if (reach.hotbar() || reach.mainHand()) {
                hotbarRow = places.size();
                for (int i = 0; i < 9; i++) places.add(new Place(i, HOTBAR));
            } else {
                hotbarRow = -1;
            }
            if (reach.armour() || reach.offhand()) {
                // H C L B, a spacer, the off hand: Standards' row, so the two screens read alike.
                for (int index : new int[] {39, 38, 37, 36}) places.add(new Place(index, ARMOUR));
                places.add(Place.SPACER);
                places.add(new Place(Inventory.SLOT_OFFHAND, OFFHAND));
                for (int i = 0; i < 3; i++) places.add(Place.SPACER);
            }
        }

        int rows() {
            return places.size() / 9;
        }

        /** The place a thief may actually take from, or null for a spacer or a slot out of reach. */
        Place reachable(int slot) {
            if (slot < 0 || slot >= places.size()) return null;
            Place place = places.get(slot);
            if (place.index() < 0) return null;
            if (hotbarRow >= 0 && slot >= hotbarRow && slot < hotbarRow + 9) {
                // Which slot is "in hand" changes as the mark scrolls, so it is asked every time.
                boolean held = place.index() == mark.getInventory().getSelectedSlot();
                if (held) return reach.mainHand() ? new Place(place.index(), MAIN_HAND) : null;
                return reach.hotbar() ? place : null;
            }
            if (place.index() == Inventory.SLOT_OFFHAND) return reach.offhand() ? place : null;
            if (place.index() >= 36) return reach.armour() ? place : null;
            return place;
        }

        /** Why a slot cannot be taken from, for the thief who tries; null when it can. */
        String refusal(int slot) {
            if (slot < 0 || slot >= places.size() || places.get(slot).index() < 0) return null;
            if (reachable(slot) != null) return null;
            ItemStack shown = getItem(slot);
            return shown == inHand ? "msg.pickpocket.in_hand_refused" : "msg.pickpocket.out_of_reach_refused";
        }

        ServerPlayer mark() {
            return mark;
        }

        int baseDc() {
            return baseDc;
        }

        ItemStack real(Place place) {
            return mark.getInventory().getItem(place.index());
        }

        @Override
        public int getContainerSize() {
            return places.size();
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        /** What the thief sees: the real item with its difficulty added to the tooltip, never the item itself. */
        @Override
        public ItemStack getItem(int slot) {
            if (slot < 0 || slot >= places.size()) return ItemStack.EMPTY;
            Place place = places.get(slot);
            if (place.index() < 0) return spacer;
            Place open = reachable(slot);
            if (open == null) {
                boolean held = hotbarRow >= 0 && slot >= hotbarRow && slot < hotbarRow + 9
                        && place.index() == mark.getInventory().getSelectedSlot();
                return held ? inHand : outOfReach;
            }
            ItemStack item = real(open);
            if (item.isEmpty()) return ItemStack.EMPTY;
            ItemStack shown = item.copy();
            Component line = Component.empty()
                    .append(Feedback.colored(Lang.fmt("msg.pickpocket.dc",
                            "dc", difficulty(item, baseDc, open.bonus()))))
                    .withStyle(style -> style.withItalic(false));
            shown.set(DataComponents.LORE,
                    shown.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).withLineAdded(line));
            return shown;
        }

        // Read-only. Taking happens in Menu.clicked and nowhere else.

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            // never written through: see the class comment
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
        }
    }

    static final class Menu extends AbstractContainerMenu {

        private final ServerPlayer thief;
        private final Pockets pockets;
        private final double range;
        private final boolean alert;
        /** One reach per cast. Set by the attempt; the next tick's validity check closes the screen. */
        private boolean done;

        Menu(int containerId, Inventory own, ServerPlayer thief, Pockets pockets, double range, boolean alert) {
            super(switch (pockets.rows()) {
                case 3 -> MenuType.GENERIC_9x3;
                case 4 -> MenuType.GENERIC_9x4;
                default -> MenuType.GENERIC_9x5;
            }, containerId);
            this.thief = thief;
            this.pockets = pockets;
            this.range = range;
            this.alert = alert;

            int size = pockets.getContainerSize();
            for (int i = 0; i < size; i++) {
                addSlot(new Slot(pockets, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return false;
                    }
                });
            }
            // The thief's own inventory underneath, where the client expects it. Untouchable
            // while the pocket is open: every click goes through clicked() below.
            int offset = (pockets.rows() - 4) * 18;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new Slot(own, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + offset));
                }
            }
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(own, col, 8 + col * 18, 161 + offset));
            }
        }

        /**
         * Every click, of every kind, lands here and moves nothing on its own. The
         * client predicted a pickup; the packet handler's broadcast after this
         * returns puts its screen back to the truth.
         */
        @Override
        public void clicked(int slotId, int button, ContainerInput input, Player player) {
            if (done || player != thief || slotId < 0 || slotId >= pockets.getContainerSize()) return;
            String refusal = pockets.refusal(slotId);
            if (refusal != null) {
                Feedback.actionBar(thief, Lang.get(refusal));
                return;
            }
            Place place = pockets.reachable(slotId);
            if (place == null) return;
            ItemStack item = pockets.real(place);
            if (item.isEmpty()) return; // an empty pocket is not an attempt
            done = true;
            attempt(place, item);
        }

        private void attempt(Place place, ItemStack item) {
            ServerPlayer mark = pockets.mark();
            int dc = difficulty(item, pockets.baseDc(), place.bonus());
            int mod = CharacterService.statModifier(thief, Stat.DEX);
            int roll = thief.getRandom().nextInt(20) + 1;
            boolean ok = roll == 20 || (roll != 1 && roll + mod >= dc);
            String name = item.getHoverName().getString();
            Feedback.chat(thief, Lang.fmt(ok ? "msg.pickpocket.ok" : "msg.pickpocket.fail",
                    "roll", roll,
                    "mod", mod == 0 ? "" : (mod > 0 ? " +" : " ") + mod,
                    "total", roll + mod,
                    "dc", dc,
                    "item", name,
                    "target", mark.getName().getString()));
            if (ok) {
                ItemStack taken = mark.getInventory().removeItemNoUpdate(place.index());
                mark.getInventory().setChanged();
                thief.getInventory().placeItemBackInInventory(taken);
            } else if (alert) {
                // Chat as well as the action bar, never a notice: a notice is a four-second
                // banner meant as the answer to something the player just did, and the mark
                // did nothing. Being robbed is news they should be able to scroll back to.
                String caught = Lang.fmt("msg.pickpocket.caught",
                        "thief", thief.getName().getString(), "item", name);
                Feedback.chat(mark, caught);
                Feedback.actionBar(mark, caught);
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        /** Closes after the attempt, or when the mark is gone or out of reach. */
        @Override
        public boolean stillValid(Player player) {
            ServerPlayer mark = pockets.mark();
            return !done && mark.isAlive() && !mark.isRemoved() && mark.level() == thief.level()
                    && mark.distanceTo(thief) <= range + 2.0D;
        }
    }
}
