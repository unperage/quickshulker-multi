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

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Server-side allow list, edited by operators through {@code /qs}.
 * <p>
 * Deliberately separate from {@code quickshulkerconfig.json5}: that file lives next to whoever is running the game,
 * so on a dedicated server it holds the server's own copy of what each player is expected to configure for themselves.
 * The client already decides whether to send the request at all; this list is the server's own say, and it has to be
 * editable while the server runs rather than requiring a restart and a file edit.
 * <p>
 * Unknown items default to allowed, so a fresh server behaves exactly like a client that has everything on.
 */
public final class ServerRules {

    private static final String FILE_NAME = "quickshulker-server.properties";
    private static final Map<String, Boolean> DISABLED = new LinkedHashMap<>();

    /** Item id to the block it came from, filled at registration so the command can list everything. */
    private static final Map<String, Item> REGISTERED = new TreeMap<>();

    private static Logger logger;

    private ServerRules() {
    }

    public static void init(Logger modLogger) {
        logger = modLogger;
        load();
    }

    /** Records a container item so {@code /uqs list} and tab completion can enumerate it. */
    public static void register(Block block, Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null) {
            REGISTERED.put(id.toString(), item);
        }
    }

    /**
     * Adds every registered item that this mod can quick-open.
     * <p>
     * Called once after all providers are registered. Enumerating the item registry beats listing variants by hand:
     * anvils alone are three separate items ({@code anvil}, {@code chipped_anvil}, {@code damaged_anvil}) and shulker
     * boxes are seventeen, so a hand-written list silently leaves holes in "all" - and it breaks again whenever a mod
     * adds another container that reuses an existing block class.
     */
    public static void scanRegistry() {
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            if (QuickOpenableRegistry.getQuickieRaw(item) != null) {
                REGISTERED.put(id.toString(), item);
            }
        }
    }

    /** Every container item the mod knows about, sorted by id. */
    public static Map<String, Item> registered() {
        return REGISTERED;
    }

    /** Null when the item has no explicit server rule, meaning "allowed". */
    public static Boolean get(String itemId) {
        return DISABLED.get(itemId);
    }

    /**
     * A container is allowed unless it appears in the disabled set.
     * <p>
     * The set only ever holds disabled ids, so presence is the answer. The previous form -
     * {@code !Boolean.FALSE.equals(DISABLED.get(id))} - read the stored {@code true} marker as "not false" and
     * therefore reported every disabled item as allowed, which made {@code /uqs <item> false} do nothing.
     */
    public static boolean isAllowed(String itemId) {
        return !DISABLED.containsKey(itemId);
    }

    public static boolean isAllowed(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null || isAllowed(id.toString());
    }

    public static void set(String itemId, boolean allowed) {
        if (allowed) {
            DISABLED.remove(itemId);
        } else {
            DISABLED.put(itemId, true);
        }
        save();
    }

    public static void setAll(boolean allowed) {
        if (allowed) {
            DISABLED.clear();
        } else {
            for (String id : REGISTERED.keySet()) {
                DISABLED.put(id, true);
            }
        }
        save();
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static void load() {
        Path path = configPath();
        if (!Files.exists(path)) return;
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            if (logger != null) logger.warn("Could not read {}", path, e);
            return;
        }
        for (String key : props.stringPropertyNames()) {
            if (!Boolean.parseBoolean(props.getProperty(key))) {
                DISABLED.put(key, true);
            }
        }
    }

    private static void save() {
        Properties props = new Properties();
        for (String id : REGISTERED.keySet()) {
            props.setProperty(id, String.valueOf(isAllowed(id)));
        }
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                props.store(writer, "quickshulker server rules - false means the item cannot be quick-opened here");
            }
        } catch (IOException e) {
            if (logger != null) logger.warn("Could not write {}", path, e);
        }
    }
}
