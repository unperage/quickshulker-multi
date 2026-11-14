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

package net.kyrptonaught.quickshulker.mixin.compat.mousetweaks;

import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.kyrptonaught.quickshulker.util.MouseDraggedHandler;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stands Mouse Tweaks down while QuickShulker owns the right-button drag.
 * <p>
 * Mouse Tweaks' own right-button tweak clicks the slot under the pointer. The one that matters is in
 * {@code Main#onMouseDrag}: on the first move away from the slot the press landed on it calls
 * {@code rmbTweakMaybeClickSlot(oldSelectedSlot, stackOnMouse)}. With a container on the cursor, that click drops
 * the container into that slot instead of filling it from the slot - which is exactly the reported
 * "the stack the drag started on always comes back out / never goes in" symptom.
 * <p>
 * {@code rmbTweakMaybeClickSlot} is the single funnel for every right-button tweak click (both the original slot and
 * every slot entered afterwards), so cancelling it here disables Mouse Tweaks' right-drag-as-a-whole while leaving
 * its wheel tweak, shift-click tweak and left-button tweak untouched.
 * <p>
 * Targets are referenced by name so Mouse Tweaks stays out of the compile classpath, and {@code @Restriction} keeps
 * this mixin from being applied at all when the mod is absent. The injected method is long-standing (it predates the
 * 1.20 rewrite), so a failure here means a radically different Mouse Tweaks; drop the {@code require} to 0 rather
 * than letting it crash if that ever happens.
 */
@Restriction(require = @Condition("mousetweaks"))
@Mixin(targets = "yalter.mousetweaks.Main")
public abstract class MouseTweaksMainMixin {

    @Inject(method = "rmbTweakMaybeClickSlot", at = @At("HEAD"), cancellable = true)
    private static void quickshulker$skipWhileQuickShulkerDrag(Slot slot, ItemStack stackOnMouse, CallbackInfo ci) {
        if (MouseDraggedHandler.isDragActive()) {
            ci.cancel();
        }
    }
}
