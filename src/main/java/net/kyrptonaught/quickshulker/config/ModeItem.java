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

package net.kyrptonaught.quickshulker.config;

import net.kyrptonaught.kyrptconfig.config.screen.NotSuckyButton;
import net.kyrptonaught.kyrptconfig.config.screen.items.ConfigItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
//#if MC >= 1.21.10
import net.minecraft.client.input.MouseButtonEvent;
//#else
//#endif

/**
 * A two-state button whose label is a word instead of true/false.
 * <p>
 * Used for "execution mode": the value is shown as Hold or Toggle, and a click flips it - same interaction as
 * {@code BooleanItem}, only the wording differs.
 */
public class ModeItem extends ConfigItem<Boolean> {
    private final Component holdLabel;
    private final Component toggleLabel;
    private final NotSuckyButton widget;

    public ModeItem(Component name, Boolean value, Boolean defaultValue, Component holdLabel, Component toggleLabel) {
        super(name, value, defaultValue);
        this.holdLabel = holdLabel;
        this.toggleLabel = toggleLabel;
        this.widget = new NotSuckyButton(0, 0, 100, 20, Component.literal("Mode"), w -> setValue(!this.value));
        setValue(value);
        useDefaultResetBTN();
    }

    @Override
    public void setValue(Boolean value) {
        super.setValue(value);
        // Both states render in plain white: the label already says Hold or Toggle, so tinting the button only made
        // the two states look like different kinds of control.
        widget.setButtonColor(DyeColor.WHITE.getFireworkColor());
        widget.setMessage(value ? toggleLabel : holdLabel);
    }

    @Override
    //#if MC >= 1.21.10
    public void mouseClicked(MouseButtonEvent click, boolean doubled) {
        super.mouseClicked(click, doubled);
        widget.mouseClicked(click, doubled);
    }
    //#else
    //$$ public void mouseClicked(double mouseX, double mouseY, int button) {
    //$$     super.mouseClicked(mouseX, mouseY, button);
    //$$     widget.mouseClicked(mouseX, mouseY, button);
    //$$ }
    //#endif

    @Override
    public void render(GuiGraphics context, int x, int y, int mouseX, int mouseY, float delta) {
        super.render(context, x, y, mouseX, mouseY, delta);
        this.widget.setY(y);
        this.widget.setX(resetButton.getX() - resetButton.getWidth() - (widget.getWidth() / 2) - 20);
        widget.render(context, mouseX, mouseY, delta);
    }
}
