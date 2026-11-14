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

import net.kyrptonaught.kyrptconfig.config.screen.ConfigScreen;
import net.kyrptonaught.kyrptconfig.config.screen.ConfigSection;
import net.kyrptonaught.kyrptconfig.config.screen.items.BooleanItem;
import net.kyrptonaught.kyrptconfig.config.screen.items.ConfigItem;
import net.kyrptonaught.kyrptconfig.config.screen.items.KeybindItem;
import net.kyrptonaught.kyrptconfig.config.screen.items.SubItem;
import net.kyrptonaught.quickshulker.client.KeybindManager;
import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ModConfigMenu {
    /**
     * Applies a value and persists it at once.
     * <p>
     * The library exposes two hooks: {@code setSaveConsumer} fires only when the screen is closed with "save", and
     * {@code setValueUpdatedEvent} fires on every value change. Everything here is wired to the latter, so flipping a
     * switch in the menu is written to disk immediately and the player never has to remember to save on exit.
     */
    private static <T> ConfigItem<T> live(ConfigItem<T> item, Consumer<T> setter) {
        item.setValueUpdatedEvent(value -> {
            setter.accept(value);
            QuickShulkerMod.config.save();
            // Keep the server's per-player copy in step, otherwise a toggle here would only take effect after the
            // next join and the item would keep opening in the meantime.
            net.kyrptonaught.quickshulker.client.QuickShulkerModClient.sendSwitches();
        });
        return item;
    }

    public static Screen getModConfigMenu(Screen screen) {
        ConfigOptions options = QuickShulkerMod.getConfig();

        ConfigScreen configScreen = new ConfigScreen(screen, Component.translatable("key.quickshulker.config.category.title"));
        // Kept as a safety net for anything the live hooks do not see (e.g. a reset-to-default button).
        configScreen.setSavingEvent(() -> QuickShulkerMod.config.save());

        ConfigSection activationSection = new ConfigSection(configScreen, Component.translatable("key.quickshulker.config.category.activation"));
        activationSection.addConfigItem(live(new KeybindItem(Component.translatable("key.quickshulker.config.keybinding"), options.keybinding, ConfigOptions.defualtKeybind), (String value) -> { options.keybinding = value; KeybindManager.refresh(); }));
        activationSection.addConfigItem(live(new KeybindItem(Component.translatable("key.quickshulker.config.openSettingGui"), options.openSettingGui, "key.keyboard.keypad.add"), (String value) -> { options.openSettingGui = value; KeybindManager.refresh(); }));
        activationSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.playSound"), options.playSound, true), (Boolean value) -> options.playSound = value));
        activationSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.keybind"), options.keybind, true), (Boolean value) -> options.keybind = value));
        activationSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.rightClick"), options.rightClickToOpen, true), (Boolean value) -> options.rightClickToOpen = value));
        activationSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.keybindInInv"), options.keybingInInv, true), (Boolean value) -> options.keybingInInv = value));
        activationSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.rightClickInInv"), options.rightClickInv, true), (Boolean value) -> options.rightClickInv = value));

        ConfigSection optionsSection = new ConfigSection(configScreen, Component.translatable("key.quickshulker.config.category.options"));
        optionsSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.rightClickClose"), options.rightClickClose, false), (Boolean value) -> options.rightClickClose = value));
        @SuppressWarnings("unchecked")
        SubItem<Boolean> subItem = (SubItem<Boolean>) optionsSection.addConfigItem(new SubItem<>(Component.translatable("key.quickshulker.config.category.bundleing"), true));
        subItem.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.supportsBundlingInsert"), options.supportsBundlingInsert, true), (Boolean value) -> options.supportsBundlingInsert = value));
        subItem.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.supportsBundlingPickup"), options.supportsBundlingPickup, true), (Boolean value) -> options.supportsBundlingPickup = value));
        subItem.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.supportsBundlingTransfer"), options.supportsBundlingTransfer, true), (Boolean value) -> options.supportsBundlingTransfer = value));
        subItem.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.supportsBundlingExtract"), options.supportsBundlingExtract, true), (Boolean value) -> options.supportsBundlingExtract = value));
        subItem.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.supportsMouseDragged"), options.supportsMouseDragged, true), (Boolean value) -> options.supportsMouseDragged = value));

        ConfigSection enhanceSection = new ConfigSection(configScreen, Component.translatable("key.quickshulker.config.category.enhance"));
        // Two independent features, each self-contained: the outer row collapses a group that holds its own on/off
        // switch, its own key, and (for the memory chain only) the hold/toggle mode.
        @SuppressWarnings("unchecked")
        SubItem<Boolean> nestedContainerGroup = (SubItem<Boolean>) enhanceSection.addConfigItem(new SubItem<>(Component.translatable("key.quickshulker.config.crossContainerOpen"), options.crossContainerOpen));
        nestedContainerGroup.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.nestedEnabled"), options.crossContainerOpen, false), (Boolean value) -> options.crossContainerOpen = value));
        nestedContainerGroup.addConfigItem(live(new KeybindItem(Component.translatable("key.quickshulker.config.nestedKey"), options.nestedKey, "key.keyboard.left.shift"), (String value) -> { options.nestedKey = value; KeybindManager.refresh(); }));

        @SuppressWarnings("unchecked")
        SubItem<Boolean> nestedGroup = (SubItem<Boolean>) enhanceSection.addConfigItem(new SubItem<>(Component.translatable("key.quickshulker.config.nestedScreens"), options.nestedScreens));
        nestedGroup.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.nestedEnabled"), options.nestedScreens, false), (Boolean value) -> options.nestedScreens = value));
        nestedGroup.addConfigItem(live(new ModeItem(
                Component.translatable("key.quickshulker.config.nestedMode"),
                options.nestedMode,
                false,
                Component.translatable("key.quickshulker.config.modeHold"),
                Component.translatable("key.quickshulker.config.modeToggle")), (Boolean value) -> options.nestedMode = value));
        nestedGroup.addConfigItem(live(new KeybindItem(Component.translatable("key.quickshulker.config.memoryKey"), options.memoryKey, "key.keyboard.left.control"), (String value) -> { options.memoryKey = value; KeybindManager.refresh(); }));
        enhanceSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.cursorFollow"), options.cursorFollow, true), (Boolean value) -> options.cursorFollow = value));

        ConfigSection enabledSection = new ConfigSection(configScreen, Component.translatable("key.quickshulker.config.category.enabled"));
        enabledSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.quickShulkerBox"), options.quickShulkerBox, true), (Boolean value) -> options.quickShulkerBox = value).setRequiresRestart());
        enabledSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.quickCraftingTable"), options.quickCraftingTables, true), (Boolean value) -> options.quickCraftingTables = value).setRequiresRestart());
        enabledSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.quickStonecutter"), options.quickStonecutter, true), (Boolean value) -> options.quickStonecutter = value).setRequiresRestart());
        enabledSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.quickEChest"), options.quickEChest, true), (Boolean value) -> options.quickEChest = value).setRequiresRestart());
        enabledSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.quickSmithingTable"), options.quickSmithingTable, true), (Boolean value) -> options.quickSmithingTable = value).setRequiresRestart());
        enabledSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.quickLoom"), options.quickLoom, true), (Boolean value) -> options.quickLoom = value).setRequiresRestart());
        enabledSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.quickAnvil"), options.quickAnvil, true), (Boolean value) -> options.quickAnvil = value).setRequiresRestart());
        enabledSection.addConfigItem(live(new BooleanItem(Component.translatable("key.quickshulker.config.quickGrindstone"), options.quickGrindstone, true), (Boolean value) -> options.quickGrindstone = value).setRequiresRestart());

        return configScreen;
    }
}
