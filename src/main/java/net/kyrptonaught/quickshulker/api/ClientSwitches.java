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

import net.kyrptonaught.quickshulker.config.ConfigOptions;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Per-player copy of the client's own "enabled items" switches, sent on join and after every change.
 * <p>
 * The switches belong to the player, but vanilla sends {@code ServerboundUseItemPacket} regardless of anything the
 * client decides - {@code MultiPlayerGameMode.useItem} builds that packet before {@code ItemStack.use} runs, so a
 * client-side callback cannot suppress it. The server is therefore the only place an item can actually be kept shut,
 * and to do that it has to know what this particular player turned off.
 * <p>
 * A player with no report (older client, or a vanilla client that cannot open these items anyway) is treated as
 * "everything on", which keeps the previous behaviour.
 */
public final class ClientSwitches {

    /** Must mirror the order used by {@link ConfigOptions#switchArray()} and the packet's {@code SIZE}. */
    public static final int SHULKER_BOX = 0;
    public static final int CRAFTING_TABLE = 1;
    public static final int STONECUTTER = 2;
    public static final int ENDER_CHEST = 3;
    public static final int SMITHING_TABLE = 4;
    public static final int LOOM = 5;
    public static final int ANVIL = 6;
    public static final int GRINDSTONE = 7;

    private static final Map<ServerPlayer, boolean[]> SWITCHES = new WeakHashMap<>();

    private ClientSwitches() {
    }

    public static void update(ServerPlayer player, boolean[] values) {
        SWITCHES.put(player, values.clone());
    }

    public static void clear(ServerPlayer player) {
        SWITCHES.remove(player);
    }

    /** True when this player's client has the given switch on, or when the client never reported. */
    public static boolean allows(ServerPlayer player, int switchIndex) {
        boolean[] values = SWITCHES.get(player);
        if (values == null || switchIndex < 0 || switchIndex >= values.length) return true;
        return values[switchIndex];
    }

    /** Which switch governs a given item, or -1 when the item has none (shulker box handled separately). */
    public static int switchFor(String itemId) {
        return switch (itemId) {
            case "minecraft:shulker_box", "minecraft:white_shulker_box", "minecraft:orange_shulker_box",
                 "minecraft:magenta_shulker_box", "minecraft:light_blue_shulker_box", "minecraft:yellow_shulker_box",
                 "minecraft:lime_shulker_box", "minecraft:pink_shulker_box", "minecraft:gray_shulker_box",
                 "minecraft:light_gray_shulker_box", "minecraft:cyan_shulker_box", "minecraft:purple_shulker_box",
                 "minecraft:blue_shulker_box", "minecraft:brown_shulker_box", "minecraft:green_shulker_box",
                 "minecraft:red_shulker_box", "minecraft:black_shulker_box" -> SHULKER_BOX;
            case "minecraft:crafting_table" -> CRAFTING_TABLE;
            case "minecraft:stonecutter" -> STONECUTTER;
            case "minecraft:ender_chest" -> ENDER_CHEST;
            case "minecraft:smithing_table" -> SMITHING_TABLE;
            case "minecraft:loom" -> LOOM;
            case "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil" -> ANVIL;
            case "minecraft:grindstone" -> GRINDSTONE;
            default -> -1;
        };
    }

    private static final Map<UUID, String> LAST_SEEN = new WeakHashMap<>();

    /** Convenience for tests and diagnostics. */
    public static boolean allowsItem(ServerPlayer player, String itemId) {
        int index = switchFor(itemId);
        return index < 0 || allows(player, index);
    }
}
