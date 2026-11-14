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

package net.kyrptonaught.quickshulker.util;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.mixin.screen.ScreenAccessor;
import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.api.Util;
import net.kyrptonaught.quickshulker.mixin.minecraft.AbstractContainerScreenInvoker;
import net.kyrptonaught.shulkerutils.ShulkerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
//#if MC >= 26.1
//$$ import net.minecraft.world.inventory.ContainerInput;
//#else
import net.minecraft.world.inventory.ClickType;
//#endif

//#if MC >= 1.21.10
import net.minecraft.client.input.MouseButtonEvent;
//#else
//#endif

import java.util.Set;

/**
 * Drag-to-transfer for quick opened containers, following the upstream implementation: every slot the pointer
 * passes over is clicked through vanilla's own {@code slotClicked}, and vanilla's quick-craft machinery is left
 * alone.
 * <p>
 * Two things are kept on top of upstream, both required for 26.3:
 * <ul>
 *   <li>{@code InputConstants.MOUSE_BUTTON_RIGHT} instead of the literal {@code 1}. 26.3 replaced GLFW with SDL,
 *       which numbers the buttons left = 1, middle = 2, right = 3 - upstream's {@code == 1} therefore never matches
 *       a right click and the drag never starts.</li>
 *   <li>{@link #toContainerButton(int)} around the button handed to {@code slotClicked}. That method expects the
 *       container protocol's button (0 = left, 1 = right); passing the raw SDL id makes the PICKUP branch ignore
 *       the click, so every dragged slot silently did nothing.</li>
 * </ul>
 */
public class MouseDraggedHandler {
    private static final Set<Slot> DRAGGED_SLOTS = Sets.newHashSet();
    /**
     * Direction of the drag in progress, fixed at the moment of the press and kept until the button is released.
     * <p>
     * The slot the press landed on decides it: empty means the whole drag takes stacks out of the container,
     * occupied means the whole drag puts stacks in. It does not flip per slot, so sweeping back over an area that
     * was just emptied keeps taking instead of immediately putting everything back.
     */
    private static DragMode dragMode;

    /** TEMPORARY: tracing is on by default while the unbundle failure is being chased. */
    public static final boolean DEBUG = false;

    public static void dbg(String message) {
        if (DEBUG) System.out.println("[QS-DRAG] " + message);
    }

    /**
     * True while a QuickShulker drag owns the right button. Read by the Mouse Tweaks compat mixin, which has to
     * stand down for the duration of the drag: Mouse Tweaks clicks the slot the drag started on the moment the
     * pointer leaves it, and with a container on the cursor that click drops the container into that slot - the
     * "first slot is always skipped instead of filled" symptom.
     */
    public static boolean isDragActive() {
        return dragMode != null;
    }

    public static boolean canInsertIntoContainer(Player player, ItemStack hostStack, ItemStack insertStack) {
        Container inv = Util.getQuickItemInventory(player, hostStack);
        if (inv == null) return false;
        for (int i = inv.getContainerSize() - 1; i >= 0; i--) {
            ItemStack pickStack = inv.getItem(i);
            if (pickStack.isEmpty() || (ItemStack.isSameItemSameComponents(pickStack, insertStack) && pickStack.getCount() < pickStack.getMaxStackSize()))
                return true;
        }
        return false;
    }

    public static boolean isContainerEmpty(Player player, ItemStack hostStack) {
        Container inv = Util.getQuickItemInventory(player, hostStack);
        if (inv != null) {
            return inv.isEmpty();
        }
        return true;
    }

    /**
     * Translates a raw platform mouse button into the id the container click protocol uses (0 = left,
     * 1 = right). 26.3 moved the mouse layer from GLFW to SDL, which numbers the buttons differently
     * (left = 1, middle = 2, right = 3), and vanilla converts them back inside
     * {@code AbstractContainerScreen#getContainerClickButton} before it calls {@code slotClicked(...)}.
     * Anything calling that method directly has to translate as well.
     */
    //#if MC >= 26.3
    //$$ private static int toContainerButton(int rawButton) {
    //$$     if (rawButton == InputConstants.MOUSE_BUTTON_LEFT) return 0;
    //$$     if (rawButton == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
    //$$     return rawButton;
    //$$ }
    //#else
    private static int toContainerButton(int rawButton) {
        return rawButton;
    }
    //#endif

    //#if MC >= 1.21.10
    public static boolean beforeMouseClick(AbstractContainerScreen<?> screen, MouseButtonEvent click){
    //#else
    //$$ public static boolean beforeMouseClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
    //#endif
        if (!QuickShulkerMod.getConfig().supportsMouseDragged) return false;
        // Record every press verbatim: which button the event really carries, and whether a drag is already armed.
        // The left-button events visible in the logs have no confirmed origin yet, so this is the evidence trail.
        //#if MC >= 1.21.10
        if (DEBUG) {
        dbg("click: button=" + click.button()
                + " modifiers=" + click.modifiers()
                + " activeDrag=" + (dragMode == null ? "none" : dragMode));
        }
        //#else
        //$$ dbg("click: button=" + button + " activeDrag=" + (dragMode == null ? "none" : dragMode));
        //#endif
        // A drag is already running: swallow presses of any other button. Vanilla would read a left click on an
        // empty slot while a container sits on the cursor as "put it there", which drops the box back into the
        // inventory in the middle of a transfer.
        //#if MC >= 1.21.10
        if (dragMode != null && click.button() != InputConstants.MOUSE_BUTTON_RIGHT) return true;
        //#else
        //$$ if (dragMode != null && button != 1) return true;
        //#endif
        //#if MC >= 1.21.10
        Slot slot = ((AbstractContainerScreenInvoker) screen).QS$getSlotAt(click.x(), click.y());
        if(slot != null && click.button() == InputConstants.MOUSE_BUTTON_RIGHT){
        //#else
        //$$ Slot slot = ((AbstractContainerScreenInvoker) screen).QS$getSlotAt(mouseX, mouseY);
        //$$ if (slot != null && button == 1) {
        //#endif
            Minecraft client = ((ScreenAccessor) screen).getClient();
            ItemStack itemStack = screen.getMenu().getCarried();
            Container inv = Util.getQuickItemInventory(client.player, itemStack);
            if (inv == null) return false;
            // The slot under the press decides the direction for the entire drag.
            dragMode = slot.hasItem() ? DragMode.PUT : DragMode.TAKE;
            DRAGGED_SLOTS.clear();
            if (DEBUG) {
            dbg("press: mode=" + dragMode + " slot=" + slot.index);
            }
            return true;
        }
        return false;
    }

    //#if MC >= 1.21.10
    public static boolean beforeMouseDragged(AbstractContainerScreen<?> screen, MouseButtonEvent click){
    //#else
    //$$ public static boolean beforeMouseDragged(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
    //#endif
        if (!QuickShulkerMod.getConfig().supportsMouseDragged) {
            return false;
        }
        // Nothing to do until a press armed the drag.
        if (dragMode == null) {
            return false;
        }
        Minecraft client = ((ScreenAccessor) screen).getClient();
        AbstractContainerMenu handler = screen.getMenu();
        ItemStack itemStack = handler.getCarried();
        //#if MC >= 1.21.10
        if(click.button() != InputConstants.MOUSE_BUTTON_RIGHT){
        //#else
        //$$ if (button != 1) {
        //#endif
            // Another button moving during a right-drag: swallow it instead of ending the drag or handing it to
            // vanilla. Clearing the drag here would abort the transfer on any stray left-button release.
            return true;
        }
        //#if MC >= 1.21.10
        Slot slot = ((AbstractContainerScreenInvoker) screen).QS$getSlotAt(click.x(), click.y());
        //#else
        //$$ Slot slot = ((AbstractContainerScreenInvoker) screen).QS$getSlotAt(mouseX, mouseY);
        //#endif
        if (client.player != null && slot != null && (handler.canDragTo(slot) || slot.mayPickup(client.player))
                && !DRAGGED_SLOTS.contains(slot)) {
            // The direction was fixed by the press and stays for the whole drag; each slot is only checked against
            // it. A slot that does not fit the direction (occupied during a take, empty during a put) is skipped
            // rather than flipped.
            boolean actionable = dragMode == DragMode.PUT
                    ? slot.hasItem()
                        && canInsertIntoContainer(client.player, itemStack, slot.getItem())
                        && !ShulkerUtils.isShulkerItem(slot.getItem())
                    : !slot.hasItem() && !isContainerEmpty(client.player, itemStack);
            if (actionable) {
                DRAGGED_SLOTS.add(slot);
                //#if MC >= 26.1
                //$$ ((AbstractContainerScreenInvoker) screen).QS$onMouseClick(slot, slot.index, toContainerButton(click.button()), ContainerInput.PICKUP);
                //#elseif MC >= 1.21.10
                ((AbstractContainerScreenInvoker) screen).QS$onMouseClick(slot, slot.index, toContainerButton(click.button()), ClickType.PICKUP);
                //#else
                //$$ ((AbstractContainerScreenInvoker) screen).QS$onMouseClick(slot, slot.index, toContainerButton(button), ClickType.PICKUP);
                //#endif
                if (DEBUG) {
                dbg((dragMode == DragMode.PUT ? "put: slot=" : "take: slot=") + slot.index);
                }
            }
        }
        // Swallow every motion while a QuickShulker drag is running, including the ones that produced no click.
        // Returning false handed those back to vanilla, whose quick-craft pass then dropped the carried container
        // into the slot under the cursor - which is what emptied the player's hand mid-drag and killed the rest of
        // the transfer.
        return true;
    }

    //#if MC >= 1.21.10
    public static boolean beforeMouseReleased(AbstractContainerScreen<?> screen, MouseButtonEvent click){
    //#else
    //$$ public static boolean beforeMouseReleased(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
    //#endif
        if (!QuickShulkerMod.getConfig().supportsMouseDragged) return false;
        // Marker cleanup lives here because this is the only place that knows a drag has ended; the flag set is
        // owned by BundleHelper, which is also reached from the server side.
        BundleHelper.clearJustBundled();
        //#if MC >= 1.21.10
        int releasedButton = click.button();
        //#else
        //$$ int releasedButton = button;
        //#endif
        int draggedCount = DRAGGED_SLOTS.size();
        boolean consumed = false;
        // Only the right button ends the drag it started. A left-button release arrives on its own while the right
        // button is still held, and clearing the state there aborted the transfer halfway through.
        if (dragMode != null && releasedButton == InputConstants.MOUSE_BUTTON_RIGHT) {
            draggedCount = DRAGGED_SLOTS.size();
            dragMode = null;
            if(draggedCount > 0){
                DRAGGED_SLOTS.clear();
                consumed = true;
            }
        }
        if (DEBUG) {
        dbg("release: button=" + releasedButton + " slots=" + draggedCount
                + " consumed=" + consumed + " carriedEmpty=" + screen.getMenu().getCarried().isEmpty());
        }
        return consumed;
    }

    public static void beforeDrawForeground(AbstractContainerScreen<?> screen, GuiGraphics context, int mouseX, int mouseY) {
        AbstractContainerMenu handler = screen.getMenu();
        for (Slot slot : handler.slots) {
            if (DRAGGED_SLOTS.contains(slot)) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80FFFFFF);
            }
        }
    }

    private enum DragMode {
        PUT,
        TAKE
    }
}
