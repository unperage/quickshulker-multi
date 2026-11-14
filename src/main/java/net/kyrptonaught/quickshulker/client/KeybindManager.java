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

package net.kyrptonaught.quickshulker.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kyrptonaught.kyrptconfig.keybinding.CustomKeyBinding;
import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.config.ConfigOptions;

/**
 * Runtime owner of the four key bindings.
 * <p>
 * The settings themselves are stored as plain strings in {@link ConfigOptions}. That is the whole point: a plain
 * String survives the config round trip unconditionally, while a {@code CustomKeyBinding} only round trips when its
 * {@code fromJson} hook is reached - if it is not, the field silently keeps whatever the freshly constructed
 * ConfigOptions put there, which is the default key. That is what made a rebound key look like it reset on restart.
 * <p>
 * These objects are rebuilt from those strings whenever the settings change, so the live binding and the stored value
 * can never drift apart.
 */
@Environment(EnvType.CLIENT)
public final class KeybindManager {

    private static CustomKeyBinding openItem;
    private static CustomKeyBinding openSettings;
    private static CustomKeyBinding nested;
    private static CustomKeyBinding memory;

    private KeybindManager() {
    }

    /**
     * Pushes the current config strings into the existing binding objects.
     * <p>
     * The objects are reused rather than rebuilt: {@code DisplayOnlyKeyBind} keeps a reference to each of them and
     * registers a consumer on it, so replacing the instance would silently orphan that wiring and the controls
     * screen would stop updating the config.
     */
    public static void refresh() {
        ConfigOptions options = QuickShulkerMod.getConfig();
        openItem = apply(openItem, options.keybinding);
        openSettings = apply(openSettings, options.openSettingGui);
        nested = apply(nested, options.nestedKey);
        memory = apply(memory, options.memoryKey);
    }

    private static CustomKeyBinding apply(CustomKeyBinding existing, String rawKey) {
        if (existing == null) {
            CustomKeyBinding binding = new CustomKeyBinding(QuickShulkerMod.MOD_ID);
            binding.defaultKey = rawKey;
            binding.setRaw(rawKey);
            return binding;
        }
        if (!rawKey.equals(existing.rawKey)) {
            existing.setRaw(rawKey);
        }
        return existing;
    }

    public static CustomKeyBinding openItem() {
        if (openItem == null) refresh();
        return openItem;
    }

    public static CustomKeyBinding openSettings() {
        if (openSettings == null) refresh();
        return openSettings;
    }

    public static CustomKeyBinding nested() {
        if (nested == null) refresh();
        return nested;
    }

    public static CustomKeyBinding memory() {
        if (memory == null) refresh();
        return memory;
    }
}
