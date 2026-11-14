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
import net.kyrptonaught.shulkerutils.ShulkerUtils;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class QuickShulkerData {
    public BiConsumer<Player, ItemStack> openConsumer;
    public boolean supportsBundleing = false;
    /**
     * Retained for source compatibility only - it is no longer consulted.
     * <p>
     * {@code Util.isOpenableItem} now requires exactly one item in the stack for every container, so stackable
     * containers (workbench, ender chest, ...) cannot be opened while more than one sits in the hand or the slot.
     * Opening one out of a stack would mean silently picking a copy and leaving the rest, which was never
     * intentional behaviour.
     */
    @Deprecated
    public boolean ignoreSingleStackCheck = false;
    /**
     * Evaluated on every lookup, not once at registration.
     * <p>
     * The "enabled items" config switches used to be read only while the registry was being filled at startup, so
     * turning an item off in the config screen had no effect until the game was restarted. Keeping the predicate on
     * the data lets {@code getQuickie} answer with the current setting instead of the one that was live at launch.
     */
    public java.util.function.BooleanSupplier canOpen = () -> true;

    public boolean canOpenInHand = true;
    BiFunction<Player, ItemStack, Container> bundleInvGetter;
    BiFunction<ItemStack, Boolean, SoundEvent> soundGetter;
    CanBundleInsertItemFunction canBundleInsertItem;

    public QuickShulkerData() {

    }

    public QuickShulkerData(BiConsumer<Player, ItemStack> openConsumer, Boolean supportsBundleing) {
        this.openConsumer = openConsumer;
        this.supportsBundleing = supportsBundleing;
    }

    public QuickShulkerData(BiConsumer<Player, ItemStack> openConsumer, Boolean supportsBundleing, Boolean ignoreSingleStackCheck) {
        this.openConsumer = openConsumer;
        this.supportsBundleing = supportsBundleing;
        this.ignoreSingleStackCheck = ignoreSingleStackCheck;
    }

    public Container getInventory(Player player, ItemStack stack) {
        if (bundleInvGetter != null) return bundleInvGetter.apply(player, stack);
        return ShulkerUtils.getInventoryFromShulker(stack);
    }

    public SoundEvent getSound(ItemStack stack, boolean isOpenSound){
        return soundGetter == null ? null : soundGetter.apply(stack, isOpenSound);
    }

    public boolean canBundleInsertItem(Player player, Container inventory, ItemStack hostStack, ItemStack insertStack) {
        if (canBundleInsertItem != null)
            return canBundleInsertItem.canBundleInsertItem(player, inventory, hostStack, insertStack);
        return !ShulkerUtils.isShulkerItem(insertStack);
    }

    public static class QuickEnderData extends QuickShulkerData {
        public QuickEnderData() {
            super();
            canBundleInsertItem = CanBundleInsertItemFunction.ALWAYS;
        }

        public QuickEnderData(BiConsumer<Player, ItemStack> openConsumer, Boolean supportsBundleing) {
            super(openConsumer, supportsBundleing);
            canBundleInsertItem = CanBundleInsertItemFunction.ALWAYS;
        }

        public QuickEnderData(BiConsumer<Player, ItemStack> openConsumer, Boolean supportsBundleing, Boolean ignoreSingleStackCheck) {
            super(openConsumer, supportsBundleing, ignoreSingleStackCheck);
            canBundleInsertItem = CanBundleInsertItemFunction.ALWAYS;
        }

        public Container getInventory(Player player, ItemStack stack) {
            if (!QuickShulkerMod.getConfig().quickEChest)
                return null;
            return player.getEnderChestInventory();
        }
    }
}