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

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.kyrptonaught.kyrptconfig.keybinding.CustomKeyBinding;
import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.event.KeyBindingRegister;
import net.kyrptonaught.quickshulker.event.ModKeyCallback;
import net.kyrptonaught.quickshulker.util.EnderChestSyncHandler;
import net.kyrptonaught.quickshulker.api.RegisterQuickShulkerClient;
import net.kyrptonaught.quickshulker.network.EnderChestS2CSyncPacket;
import net.kyrptonaught.quickshulker.network.OpenInventoryPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.PlayerEnderChestContainer;

@Environment(EnvType.CLIENT)
public class QuickShulkerModClient implements ClientModInitializer {

    public static CustomKeyBinding getKeybinding() {
        return KeybindManager.openItem();
    }

    /** Whether the remembered-container key is held right now. Thin forwarder so mixins stay off the client class. */
    public static boolean memoryKeyHeld() {
        return KeybindManager.memory().isKeybindPressed();
    }

    /** Whether the nested-container key is held right now. */
    public static boolean nestedKeyHeld() {
        return KeybindManager.nested().isKeybindPressed();
    }
    /** Sends the current per-item switches to the server. Called on join and after every config change. */
    public static void sendSwitches() {
        if (net.minecraft.client.Minecraft.getInstance().player == null) return;
        net.kyrptonaught.quickshulker.network.ClientSwitchesPacket.send(
                QuickShulkerMod.getConfig().switchArray());
    }

    /**
     * Pointer position captured just before a quick-opened container is closed, so the inventory screen that gets
     * restored underneath does not open with the cursor in the middle of the window. Captured in
     * {@code ScreenMixin#QS$closeAllNested} while the container screen is still up, applied here once the
     * inventory screen is on screen.
     */
    private static double pendingCursorX = Double.NaN;
    private static double pendingCursorY = Double.NaN;
    private static long pendingCursorAt = 0L;

    /**
     * How long a captured position stays usable.
     * <p>
     * The restore exists for exactly one transition: closing a nested container makes the server reopen the level
     * underneath within the same network round trip, so the next screen is up almost immediately. 200 ms covers that
     * round trip, and is short enough that any other case - closing a container and going back to the world, then
     * opening something unrelated later - has long expired. A stale capture would otherwise drag the cursor back to
     * wherever it sat minutes ago instead of letting the new screen centre it.
     */
    private static final long PENDING_CURSOR_TTL_MS = 200L;

    /** TEMPORARY: cursor tracing is on by default while the restore position is being chased. */
    private static final boolean DEBUG_CURSOR = false;

    public static void rememberCursor(double x, double y) {
        pendingCursorX = x;
        pendingCursorY = y;
        pendingCursorAt = System.currentTimeMillis();
        if (DEBUG_CURSOR) System.out.println("[QS-CURSOR] remember x=" + x + " y=" + y);
    }

    private static String screenName(Minecraft client) {
        //#if MC >= 26.2
        //$$ net.minecraft.client.gui.screens.Screen current = client.gui.screen();
        //#else
        net.minecraft.client.gui.screens.Screen current = client.screen;
        //#endif
        return current == null ? "null" : current.getClass().getSimpleName();
    }

    /**
     * Restores the captured pointer position onto whatever screen has just been opened and clears it.
     * <p>
     * Called from {@code ScreenMixin#fixMouse} ({@code init} TAIL) rather than from the packet handler: closing a
     * nested container makes the server reopen the previous level with a plain {@code openMenu}, which reaches the
     * client as a vanilla OpenScreen - there is no QuickShulker packet involved on that path.
     */
    public static void restoreCursor(Minecraft client) {
        boolean usable = !(Double.isNaN(pendingCursorX) || Double.isNaN(pendingCursorY))
                && (System.currentTimeMillis() - pendingCursorAt) <= PENDING_CURSOR_TTL_MS;
        if (DEBUG_CURSOR) {
            System.out.println("[QS-CURSOR] restore attempt pending=" + usable
                    + " ageMs=" + (pendingCursorAt == 0L ? -1 : System.currentTimeMillis() - pendingCursorAt)
                    + " screen=" + screenName(client));
        }
        if (!usable) {
            pendingCursorX = Double.NaN;
            pendingCursorY = Double.NaN;
            pendingCursorAt = 0L;
            return;
        }
        float x = (float) pendingCursorX;
        float y = (float) pendingCursorY;
        pendingCursorX = Double.NaN;
        pendingCursorY = Double.NaN;
        pendingCursorAt = 0L;
        if (DEBUG_CURSOR) System.out.println("[QS-CURSOR] warp x=" + x + " y=" + y);
        //#if MC >= 26.3
        //$$ org.lwjgl.sdl.SDLMouse.SDL_WarpMouseInWindow(client.getWindow().handle(), x, y);
        //#else
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().handle(), x, y);
        //#endif
    }

    @Override
    public void onInitializeClient() {
        //#if MC >= 26.1
        //$$ ClientTickEvents.START_LEVEL_TICK.register(ModKeyCallback::onKeyPressed);
        //#else
        ClientTickEvents.START_WORLD_TICK.register(ModKeyCallback::onKeyPressed);
        //#endif
        KeybindManager.refresh();
        // Publish the per-item switches as soon as we are in a world: the server is the only side able to keep an
        // item shut (vanilla sends the use-item packet before any client callback can refuse it).
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> sendSwitches());
        KeyBindingRegister.register();
        // Options has already pushed the default key into every mapping by now, so read the configured value
        // back in - otherwise the player's choice only ever survives until the next launch.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> KeyBindingRegister.reapplyConfiguredKeys());
        // The mapping still drifts back to the default after that event, so keep it in line every tick. The call
        // compares first and does nothing when they already agree, so this costs no writes.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.options != null) KeyBindingRegister.reapplyConfiguredKeys();
        });

        PayloadTypeRegistry.playC2S().register(OpenInventoryPacket.OPEN_INV_ID, OpenInventoryPacket.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(OpenInventoryPacket.OPEN_INV_ID, (payload, context) ->
                context.client().execute(() -> {
                    Minecraft client = context.client();
                    client.setScreen(new InventoryScreen(context.player()));
                    restoreCursor(client);
                }));

        PayloadTypeRegistry.playC2S().register(EnderChestS2CSyncPacket.S2CEChestContentPacket.S2C_ECHEST_CONTENT_PACKET_ID, EnderChestS2CSyncPacket.S2CEChestContentPacket.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(EnderChestS2CSyncPacket.S2CEChestContentPacket.S2C_ECHEST_CONTENT_PACKET_ID, (payload, context) ->
                context.client().execute(() ->
                        EnderChestSyncHandler.setEnderChestContent(context.player(), payload.itemStacks())));

        PayloadTypeRegistry.playC2S().register(EnderChestS2CSyncPacket.S2CEChestSlotPacket.S2C_ECHEST_SLOT_PACKET_ID, EnderChestS2CSyncPacket.S2CEChestSlotPacket.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(EnderChestS2CSyncPacket.S2CEChestSlotPacket.S2C_ECHEST_SLOT_PACKET_ID, (payload, context) ->
                context.client().execute(() -> {
            PlayerEnderChestContainer enderChestInventory = context.player().getEnderChestInventory();
            enderChestInventory.setItem(payload.slotId(), payload.itemStack());
        }));

        FabricLoader.getInstance().getEntrypoints(QuickShulkerMod.MOD_ID + "_client", RegisterQuickShulkerClient.class).forEach(RegisterQuickShulkerClient::registerClient);
    }
}