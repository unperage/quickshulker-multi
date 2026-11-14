/*
 * MIT License
 *
 * Copyright (c) 2019 kyrptonaught
 * Copyright (c) 2024 Haocen2004
 * Copyright (c) 2025 MoRanpcy
 * Copyright (c) 2025 EnderPhantomWing
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.kyrptonaught.quickshulker.api;

import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.network.OpenInventoryPacket;
import net.kyrptonaught.quickshulker.util.NestedMenus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public class Util {

    public static void openItem(Player player, int invSlot, boolean nest, boolean fromScreen) {
        if (ItemInventoryContainer.isMenuSlot(invSlot)) {
            openItemFromMenuSlot(player, ItemInventoryContainer.menuSlotOf(invSlot), nest, fromScreen);
            return;
        }
        if (invSlot < 0 || invSlot >= player.containerMenu.slots.size()) {
            System.out.println("[Unper's QuickShulker]: unknown slot opened");
            return;
        }
        openItem(player, invSlot, player.containerMenu.slots.get(invSlot).getContainerSlot(), nest, fromScreen);
    }

    /**
     * Opens an openable item that sits in any slot of the current menu instead of the player inventory: a
     * chest, an ender chest, or a container that was itself quick-opened. The stack is read from the menu
     * slot, and the used-slot marker stores that menu slot, so closing and the "item vanished" guard keep
     * working the same way they do for the inventory path.
     */
    /** TEMPORARY: guard tracing is on by default while the container-drop issue is being chased. */
    private static final boolean DEBUG_GUARD = true;

    public static void openItemFromMenuSlot(Player player, int menuSlotIndex, boolean nest, boolean fromScreen) {
        if (menuSlotIndex < 0 || menuSlotIndex >= player.containerMenu.slots.size()) {
            return;
        }
        Slot sourceSlot = player.containerMenu.slots.get(menuSlotIndex);
        ItemStack stack = sourceSlot.getItem();
        if (stack.isEmpty()) {
            return;
        }
        int marker = ItemInventoryContainer.encodeMenuSlot(menuSlotIndex);
        // The used-slot marker only makes sense for the player inventory path, where it is a real inventory
        // index. A menu slot marker is negative; comparing two of them made every click on the same index look
        // like "right click to close", which closed the container and dropped the player into their inventory.
        int usedSlot = ((ItemInventoryContainer) player.containerMenu).QS$getUsedSlotInPlayerInv();
        if (QuickShulkerMod.getConfig().rightClickClose && usedSlot >= 0 && marker == usedSlot) {
            ((ServerPlayer) player).closeContainer();
            OpenInventoryPacket.send((ServerPlayer) player);
            return;
        }
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickieRaw(stack.getItem());
        if (qsData == null) {
            return;
        }
        if (player instanceof ServerPlayer && !ServerRules.isAllowed(stack)) {
            return;
        }        if (player instanceof ServerPlayer serverPlayer) {
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!ClientSwitches.allowsItem(serverPlayer, itemId)) {
                if (DEBUG_GUARD) {
                    System.out.println("[QS-GUARD] server denied by client switches: " + itemId);
                }
                return;
            }
        }
        if (player instanceof ServerPlayer sp) {
            NestedMenus.captureCurrent(sp, nest, fromScreen);
            NestedMenus.beginOpening(sp);
        }
        try {
            qsData.openConsumer.accept(player, stack);
        } finally {
            if (player instanceof ServerPlayer sp) NestedMenus.endOpening(sp);
        }
        if (QuickShulkerMod.getConfig().playSound) playSound(player, qsData.getSound(stack, true));
        // A container opened from another container has no player-inventory slot, so the "right click the
        // opened shulker to close it" gesture must stay off for this menu.
        ((ItemInventoryContainer) player.containerMenu).QS$setUsedSlot(-1);
        ((ItemInventoryContainer) player.containerMenu).QS$setQuickOpened(true);
        // No "item vanished" listener here: sourceSlot belongs to the menu that openMenu just closed, so its
        // getItem() reads empty right away and the guard would close the new container on the spot. The stack
        // lives in another container, which cannot lose it by opening it, so the guard is not needed at all.
    }

    /**
     * Re-opens a stack that sits in a plain {@link Container} - used when the nested screen stack restores a
     * previous level that was itself a quick-opened container.
     */
    public static void openItemFromContainer(ServerPlayer player, Container container, int containerSlot, boolean restoring) {
        if (container == null || containerSlot < 0 || containerSlot >= container.getContainerSize()) return;
        ItemStack stack = container.getItem(containerSlot);
        if (stack.isEmpty()) return;
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData == null) return;
        if (!restoring) NestedMenus.captureCurrent(player, true, true);
        NestedMenus.beginOpening(player);
        try {
            qsData.openConsumer.accept(player, stack);
        } finally {
            NestedMenus.endOpening(player);
        }
        if (QuickShulkerMod.getConfig().playSound) playSound(player, qsData.getSound(stack, true));
    }

    public static void openItem(Player player, int invSlot, int playerInvIndex, boolean nest, boolean fromScreen) {
        if (DEBUG_GUARD) {
            System.out.println("[QS-GUARD] server Util.openItem ENTER invSlot=" + invSlot
                    + " playerInvIndex=" + playerInvIndex + " nest=" + nest + " fromScreen=" + fromScreen
                    + " menu=" + player.containerMenu.getClass().getSimpleName());
        }
        if (QuickShulkerMod.getConfig().rightClickClose && playerInvIndex == ((ItemInventoryContainer) player.containerMenu).QS$getUsedSlotInPlayerInv()) {
            ((ServerPlayer) player).closeContainer();
            OpenInventoryPacket.send((ServerPlayer) player);
            return;
        }
        ItemStack stack = player.getInventory().getItem(playerInvIndex);
        //stack.removeSubNbt(QuickShulkerMod.MOD_ID);
        // The server's own allow list, managed by /qs. Independent of the client config: the player decides what
        // they want, the server decides what is permitted here, and either can say no.
        if (player instanceof ServerPlayer && !ServerRules.isAllowed(stack)) {
            if (DEBUG_GUARD) {
                System.out.println("[QS-GUARD] server denied by /qs rules: " + stack.getItem());
            }
            return;
        }        if (player instanceof ServerPlayer serverPlayer) {
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!ClientSwitches.allowsItem(serverPlayer, itemId)) {
                if (DEBUG_GUARD) {
                    System.out.println("[QS-GUARD] server denied by client switches: " + itemId);
                }
                return;
            }
        }
        // Deliberately the raw lookup: the enabled switches live in each player's own config file, and this is the
        // server. The server's say comes from /uqs (ServerRules) and nothing else - reading getQuickie() here made the
        // server obey its own copy of the client's switches, which is why a client-disabled item still opened.
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickieRaw(stack.getItem());
        if (DEBUG_GUARD) {
            System.out.println("[QS-GUARD] server Util.openItem stack=" + stack.getItem()
                    + " count=" + stack.getCount() + " qsData=" + (qsData == null ? "NULL(unregistered)" : "present")
                    + " serverAllowed=" + ServerRules.isAllowed(stack));
        }
        if (qsData != null) {
            if (player instanceof ServerPlayer sp) {
                NestedMenus.captureCurrent(sp, nest, fromScreen);
                NestedMenus.beginOpening(sp);
            }
            try {
                qsData.openConsumer.accept(player, stack);
            } finally {
                if (player instanceof ServerPlayer sp) NestedMenus.endOpening(sp);
            }
            if(QuickShulkerMod.getConfig().playSound) playSound(player, qsData.getSound(stack, true));
            ((ItemInventoryContainer) player.containerMenu).QS$setUsedSlot(playerInvIndex);
            ((ItemInventoryContainer) player.containerMenu).QS$setQuickOpened(true);
            player.containerMenu.addSlotListener(forceCloseScreenIfNotPresent(player, playerInvIndex, stack));
        }
    }

    public static Boolean isOpenableItem(ItemStack stack) {
        QuickShulkerData qsdata = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsdata == null) {
            if (DEBUG_GUARD) {
                System.out.println("[QS-GUARD] isOpenableItem " + stack.getItem()
                        + " -> FALSE (not registered, disabled, or unsupported menu)");
            }
            return false;
        }
        // Only a lone item can be opened in place. A stack of workbenches or ender chests is not one container -
        // opening it would have to pick one of the copies out of the stack and leave the rest untouched, which is
        // never what the player meant. "Ignore single stack check" used to bypass this for the stackable container
        // items; that is deliberately no longer honoured.
        boolean result = stack.getCount() == 1;
        if (DEBUG_GUARD) {
            System.out.println("[QS-GUARD] isOpenableItem " + stack.getItem()
                    + " count=" + stack.getCount() + " -> " + result);
        }
        return result;
    }

    public static Container getQuickItemInventory(Player player, ItemStack stack) {
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData != null) {
            if (qsData.supportsBundleing)
                return qsData.getInventory(player, stack);
        }
        return null;
    }

    public static boolean canOpenInHand(ItemStack stack) {
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData != null) {
            return qsData.canOpenInHand;
        }
        return false;
    }

    public static void playOpenSound(Player player, int selectedInvIndex){
        ItemStack stack = player.getInventory().getItem(selectedInvIndex);
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData != null) {
            playSound(player, qsData.getSound(stack, true));
        }
    }

    public static void playCloseSound(Player player, int usedSlot){
        ItemStack stack = getItemInUsedSlot(player, usedSlot);
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData != null) {
            playSound(player, qsData.getSound(stack, false));
        }
    }

    /** Resolves a used-slot marker back to the stack it refers to (inventory slot or menu slot). */
    public static ItemStack getItemInUsedSlot(Player player, int usedSlot) {
        if (ItemInventoryContainer.isMenuSlot(usedSlot)) {
            int index = ItemInventoryContainer.menuSlotOf(usedSlot);
            if (index < 0 || index >= player.containerMenu.slots.size()) return ItemStack.EMPTY;
            return player.containerMenu.slots.get(index).getItem();
        }
        if (usedSlot < 0 || usedSlot >= player.getInventory().getContainerSize()) return ItemStack.EMPTY;
        return player.getInventory().getItem(usedSlot);
    }

    public static void playSound(Player player, SoundEvent sound){
        if(sound != null){
//            player.level().playSound(null, player.blockPosition(), sound, player.getSoundSource());
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.BLOCKS);
        }
    }

    public static boolean areItemsEqual(ItemStack stack1, ItemStack stack2) {
        return ItemStack.isSameItem(stack1, stack2) && ItemStack.matches(stack1, stack2) && stack1.getCount() == stack2.getCount();
    }

    /** Same guard as the inventory variant, but for a stack that lives in another container. */
    public static ContainerListener forceCloseScreenIfNotPresent(Player player, Slot sourceSlot, ItemStack stack) {
        return new ContainerListener() {
            @Override
            public void slotChanged(@NotNull AbstractContainerMenu handler, int slotId, @NotNull ItemStack changed) {
                isValid();
            }

            @Override
            public void dataChanged(@NotNull AbstractContainerMenu handler, int property, int value) {
                isValid();
            }

            public void isValid() {
                ItemStack stackInSlot = sourceSlot.getItem();
                if (stackInSlot.isEmpty() || !areItemsEqual(stack, stackInSlot)) {
                    ((ServerPlayer) player).closeContainer();
                }
            }
        };
    }

    public static ContainerListener forceCloseScreenIfNotPresent(Player player, int slotID, ItemStack stack) {        return new ContainerListener() {
            @Override
            public void slotChanged(@NotNull AbstractContainerMenu handler, int slotId, @NotNull ItemStack stack) {
                isValid();
            }

            @Override
            public void dataChanged(@NotNull AbstractContainerMenu handler, int property, int value) {
                isValid();
            }

            public void isValid() {
                ItemStack stackInSlot = player.getInventory().getItem(slotID);
                if (stackInSlot.isEmpty() || !areItemsEqual(stack, stackInSlot)) {
                    if (DEBUG_GUARD) {
                        System.out.println("[QS-GUARD] force close (inventory) slot=" + slotID
                                + " opened=" + stack.getItem().getDescriptionId() + "x" + stack.getCount()
                                + " now=" + (stackInSlot.isEmpty() ? "empty" : stackInSlot.getItem().getDescriptionId() + "x" + stackInSlot.getCount())
                                + " sameItem=" + ItemStack.isSameItem(stack, stackInSlot)
                                + " matches=" + ItemStack.matches(stack, stackInSlot));
                    }
                    ((ServerPlayer) player).closeContainer();
                }
            }
        };
    }
}