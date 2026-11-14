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

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.api.ItemInventoryContainer;
import net.kyrptonaught.quickshulker.client.ClientUtil;
import net.kyrptonaught.quickshulker.client.CursorFollower;
import net.kyrptonaught.quickshulker.client.QuickShulkerModClient;

import net.kyrptonaught.quickshulker.network.CloseAllPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
//#if MC >= 1.21.10
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
//#endif
//#if MC < 26.3
import org.lwjgl.glfw.GLFW;
//#else
//$$ import org.lwjgl.sdl.SDLMouse;
//#endif
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
@Environment(EnvType.CLIENT)
public abstract class ScreenMixin {
    @Shadow
    protected Slot hoveredSlot;

    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Shadow
    private boolean skipNextRelease;

    @Inject(method = "init", at = @At("TAIL"))
    private void fixMouse(CallbackInfo ci) {        // Deferred on purpose. Subclasses like InventoryScreen and CraftingScreen reach this method through
        // AbstractRecipeBookScreen.init(), which only assigns leftPos *after* calling super.init(), and the
        // recipe book can move the panel again after that. CursorFollower therefore waits for the target to
        // settle on a rendered frame instead of snapping to the coordinates it would read right here.
        CursorFollower.beginSnap((AbstractContainerScreen<?>) (Object) this);
        // Restoring a nested level reopens it through a plain server-side openMenu, so the screen that comes back
        // is a vanilla one and no QuickShulker packet carries the pointer position. This hook runs for every
        // container screen being initialised, which covers both "back to the previous container" and "back to the
        // inventory"; restoreCursor() is a no-op unless a position was captured when the old screen closed.
        Minecraft client = Minecraft.getInstance();
        if (DEBUG_CURSOR) {
            System.out.println("[QS-CURSOR] init " + this.getClass().getSimpleName());
        }
        if (client != null) QuickShulkerModClient.restoreCursor(client);
    }

    /** TEMPORARY: cursor tracing is on by default while the restore position is being chased. */
    private static final boolean DEBUG_CURSOR = false;

    /** Holding the configurable extra key while closing drops the whole nested chain instead of one level. */
    @Inject(method = "onClose", at = @At("HEAD"))
    private void QS$closeAllNested(CallbackInfo ci) {
        if (DEBUG_CURSOR) {
            System.out.println("[QS-CURSOR] close nestedScreens=" + QuickShulkerMod.getConfig().nestedScreens
                    + " menu=" + this.menu.getClass().getSimpleName()
                    + " quickOpened=" + (this.menu instanceof ItemInventoryContainer q && q.QS$isQuickOpened()));
        }
        // Capture the pointer whenever a quick-opened container is closed, regardless of the nesting setting.
        // Going back to the inventory or to the container underneath is plain "return where I came from" and keeps
        // the cursor where the player left it; only the nesting option decides whether the chain is remembered.
        if (this.menu instanceof ItemInventoryContainer) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                QuickShulkerModClient.rememberCursor(client.mouseHandler.xpos(), client.mouseHandler.ypos());
            }
        }
        if (QuickShulkerMod.getConfig().nestedScreens) {
            // Two ways to ask for "leave the whole chain": hold the key while closing (default), or press it once
            // beforehand to arm the toggle. The toggle lives in the config file so it survives a restart.
            boolean leaveAll = QuickShulkerMod.getConfig().nestedMode
                    ? QuickShulkerMod.getConfig().closeAllToggled
                    : QuickShulkerModClient.memoryKeyHeld();
            if (leaveAll) {
                CloseAllPacket.send();
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    //#if MC >= 1.21.10
    private void QS$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (QuickShulkerMod.getConfig().keybingInInv) {
            //#if MC >= 26.3
            //$$ if (QuickShulkerModClient.getKeybinding().matches(input.input(), InputConstants.Type.KEYBOARD)) {
            //#else
            if (QuickShulkerModClient.getKeybinding().matches(input.input(), InputConstants.Type.KEYSYM)) {
            //#endif
    //#else
    //$$ private void QS$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (QuickShulkerMod.getConfig().keybingInInv) {
    //$$         if (QuickShulkerModClient.getKeybinding().matches(keyCode, InputConstants.Type.KEYSYM)) {
    //#endif
                if (handleTrigger())
                    cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    //#if MC >= 1.21.10
    private void QS$mousePressed(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (QuickShulkerMod.getConfig().rightClickInv) {
            //#if MC >= 26.3
            //$$ if (this.menu.getCarried().isEmpty() && click.button() == 3 && this.hoveredSlot != null && this.hoveredSlot.getItem().getCount() == 1) {
            //#else
            if (this.menu.getCarried().isEmpty() && click.button() == 1 && this.hoveredSlot != null && this.hoveredSlot.getItem().getCount() == 1) {
            //#endif
    //#else
    //$$ private void QS$mousePressed(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (QuickShulkerMod.getConfig().rightClickInv) {
    //$$         if (this.menu.getCarried().isEmpty() && button == 1 && this.hoveredSlot != null && this.hoveredSlot.getItem().getCount() == 1) {
    //#endif
                if (handleTrigger()) {
                    this.skipNextRelease = true;
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
        if (QuickShulkerMod.getConfig().keybingInInv) {
            //#if MC >= 1.21.10
            if (QuickShulkerModClient.getKeybinding().matches(click.button(), InputConstants.Type.MOUSE)) {
            //#else
            //$$ if (QuickShulkerModClient.getKeybinding().matches(button, InputConstants.Type.MOUSE)) {
            //#endif
                if (handleTrigger()) {
                    this.skipNextRelease = true;
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Unique
    private boolean handleTrigger() {
        if (this.hoveredSlot != null) {
            return isValid(this.hoveredSlot.getItem(), ClientUtil.getSlotId(menu, this.hoveredSlot));
        }
        return false;
    }

    @Unique
    private boolean isValid(ItemStack stack, int id) {
        if (this.hoveredSlot.container instanceof Inventory) {
            if (ClientUtil.CheckAndSend(stack, id)) {
                CursorFollower.remember((AbstractContainerScreen<?>) (Object) this, this.hoveredSlot);
                return true;
            }
            return false;
        }
        // Cross-container open: the stack sits in a chest, an ender chest or another open container.
        // Only allowed with the option on and the nested-container key held down.
        if (!QuickShulkerMod.getConfig().crossContainerOpen) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        if (!QuickShulkerModClient.nestedKeyHeld()) return false;
        if (ClientUtil.CheckAndSendMenuSlot(stack, id)) {
            // No slot to follow here: the source slot lives in another container, so the item the player came
            // from is not on the screen that is about to open. Keep the pointer at its current position instead
            // of letting the new screen reset it to the centre of the window.
            CursorFollower.rememberPosition();
            return true;
        }
        return false;
    }
}
