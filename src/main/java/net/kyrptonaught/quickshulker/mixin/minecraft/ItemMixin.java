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

package net.kyrptonaught.quickshulker.mixin.minecraft;

import net.kyrptonaught.quickshulker.util.BundleHelper;
import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.client.ClientUtil;
import net.kyrptonaught.quickshulker.network.QuickBundlePacket;
import net.kyrptonaught.shulkerutils.ShulkerUtils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickAction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemMixin {

    /**
     * Local tracing switch. Deliberately not delegating to MouseDraggedHandler: that class is client-side
     * flavoured (client-only types in its signatures), while this mixin is applied on a dedicated server
     * too. Touching it there is what produced NoClassDefFoundError at runtime.
     */
    /** TEMPORARY: tracing is on by default while the unbundle failure is being chased. */
    private static final boolean QS_DEBUG = false;

    private static void qsDebug(String message) {
        if (QS_DEBUG) System.out.println("[QS-DRAG] " + message);
    }

    /**
     * Caller chain for PRIMARY clicks. The window is deliberately wider than before: the frame that explains where
     * the click came from sits deeper than ten entries (slotClicked -> RecipeBookScreen -> mouseClicked/mouseDragged),
     * and cutting it off hid the actual origin.
     */
    private static String stackTrace() {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int printed = 0;
        for (StackTraceElement frame : frames) {
            String cls = frame.getClassName();
            if (cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("org.spongepowered")
                    || cls.startsWith("com.llamalad7")) {
                continue;
            }
            if (frame.getMethodName().startsWith("getStackTrace") || frame.getMethodName().equals("stackTrace")) {
                continue;
            }
            sb.append("\n        at ").append(frame);
            if (++printed >= 24) break;
        }
        return sb.toString();
    }

    @Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
    public void QS$onClicked(ItemStack hostStack, ItemStack insertStack, Slot slot, ClickAction clickType, Player player, SlotAccess cursorStackReference, CallbackInfoReturnable<Boolean> cir) {
        try {
            qsDebug("hook otherStackedOnMe slot=" + (slot == null ? -1 : slot.index) + " clickType=" + clickType + " client=" + player.level().isClientSide()
                    + " host=" + hostStack.getItem().getDescriptionId() + "x" + hostStack.getCount() + " insert=" + insertStack.getItem().getDescriptionId() + "x" + insertStack.getCount()
                    + " bundle=" + BundleHelper.shouldAttemptBundle(player, clickType, hostStack, insertStack, true)
                    + " transfer=" + BundleHelper.shouldAttemptTransfer(player, clickType, hostStack, insertStack, true));
            if (BundleHelper.shouldAttemptBundle(player, clickType, hostStack, insertStack, QuickShulkerMod.getConfig().supportsBundlingInsert)) {
                if (ShulkerUtils.isShulkerItem(hostStack) || !player.level().isClientSide()) {
                    BundleHelper.bundleItemIntoStack(player, hostStack, insertStack, cir);
                } else if (player.level().isClientSide() && slot.container instanceof Inventory && ClientUtil.isCreativeScreen(player)) {//stupid creative menu shiz
                    QuickBundlePacket.sendPacket(ClientUtil.getPlayerInvSlot(player.containerMenu, slot), insertStack);
                    BundleHelper.bundleItemIntoStack(player, hostStack, insertStack, cir);
                }
            } else if (BundleHelper.shouldAttemptTransfer(player, clickType, hostStack, insertStack, QuickShulkerMod.getConfig().supportsBundlingTransfer)) {
                BundleHelper.transferItemsToShulker(player, hostStack, insertStack, cir);
            }
        } catch (Throwable t) {
            // This hook runs for every player, modded or not, and an exception here kills the whole server.
            // Nothing QuickShulker does is worth that, so log and let vanilla carry on.
            System.err.println("[QuickShulker] overrideOtherStackedOnMe failed, ignoring: " + t);
            t.printStackTrace();
        }
    }

    @Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true)
    public void QS$onStackClicked(ItemStack hostStack, Slot slot, ClickAction clickType, Player player, CallbackInfoReturnable<Boolean> cir) {
        try {
            ItemStack insertStack = slot.getItem();
            if (QS_DEBUG) {
                qsDebug("hook stackedOnOther slot=" + slot.index + " clickType=" + clickType + " client=" + player.level().isClientSide()
                        + " host=" + hostStack.getItem().getDescriptionId() + "x" + hostStack.getCount() + " insert=" + insertStack.getItem().getDescriptionId() + "x" + insertStack.getCount()
                        + " bundle=" + BundleHelper.shouldAttemptBundle(player, clickType, hostStack, insertStack, true)
                        + " unbundle=" + BundleHelper.shouldAttemptUnBundle(player, clickType, hostStack, insertStack, true));
                // A left click arriving in the middle of a right-drag is the anomaly under investigation: it is not
                // issued by QuickShulker (that one is always SECONDARY). Printing the caller chain for PRIMARY clicks
                // shows which mod or vanilla path produced it, without guessing.
                if (clickType == ClickAction.PRIMARY) {
                    qsDebug("PRIMARY click origin:\n" + stackTrace());
                }
            }
            if (BundleHelper.shouldAttemptBundle(player, clickType, hostStack, insertStack, QuickShulkerMod.getConfig().supportsBundlingPickup)) {//bundle stack into held item
                if (ShulkerUtils.isShulkerItem(hostStack) || !player.level().isClientSide()) {
                    BundleHelper.bundleItemIntoStack(player, hostStack, insertStack, slot, cir);
                } else if (player.level().isClientSide() && slot.container instanceof Inventory && ClientUtil.isCreativeScreen(player)) { //stupid creative menu shiz
                    QuickBundlePacket.BundleIntoHeld.sendPacket(insertStack, hostStack, ClientUtil.getPlayerInvSlot(player.containerMenu, slot));
                    BundleHelper.bundleItemIntoStack(player, hostStack, insertStack, slot, cir);
                }
            } else if (BundleHelper.shouldAttemptUnBundle(player, clickType, hostStack, insertStack, QuickShulkerMod.getConfig().supportsBundlingExtract)) {//unbundle held stack into slot
                if (ShulkerUtils.isShulkerItem(hostStack) || !player.level().isClientSide()) {
                    BundleHelper.unbundleStackIntoSlot(player, hostStack, slot, cir);
                } else if (player.level().isClientSide() && slot.container instanceof Inventory && ClientUtil.isCreativeScreen(player)) { //stupid creative menu shiz
                    QuickBundlePacket.UnbundlePacket.sendPacket(ClientUtil.getPlayerInvSlot(player.containerMenu, slot), hostStack);
                    BundleHelper.unbundleStackIntoSlot(player, hostStack, slot, cir);
                }
            } else if (QuickShulkerMod.getConfig().supportsBundlingExtract
                    && clickType == ClickAction.SECONDARY
                    && insertStack.isEmpty()
                    && Boolean.TRUE.equals(net.kyrptonaught.quickshulker.api.Util.isOpenableItem(hostStack))
                    && net.kyrptonaught.quickshulker.api.Util.getQuickItemInventory(player, hostStack) != null) {
                // The container on the cursor ran dry: shouldAttemptUnBundle() refuses once its inventory is empty,
                // and the click then falls through to vanilla - which simply puts the carried container into the
                // empty slot. That is what emptied the player's hand mid-drag and killed the rest of the transfer
                // ("takes one out, immediately puts it back, then nothing works"). Swallow the click instead; the
                // drag continues and the next slot gets a chance.
                if (cir != null) cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            System.err.println("[QuickShulker] overrideStackedOnOther failed, ignoring: " + t);
            t.printStackTrace();
        }
    }
}
