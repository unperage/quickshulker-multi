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

package net.kyrptonaught.quickshulker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.kyrptonaught.kyrptconfig.config.ConfigManager;
import net.kyrptonaught.quickshulker.api.*;
import net.kyrptonaught.quickshulker.compat.ModIds;
import net.kyrptonaught.quickshulker.compat.ModUtils;
import net.kyrptonaught.quickshulker.compat.reinfshulker.ReinfshulkerOpenableRegistry;
import net.kyrptonaught.quickshulker.config.ConfigOptions;
import net.kyrptonaught.quickshulker.command.QuickShulkerCommand;
import net.kyrptonaught.quickshulker.event.EventListeners;
import net.kyrptonaught.quickshulker.api.MenuFactory;
import net.kyrptonaught.quickshulker.api.RegisterQuickShulker;
import net.kyrptonaught.quickshulker.network.CloseAllPacket;
import net.kyrptonaught.quickshulker.network.EnderChestS2CSyncPacket;
import net.kyrptonaught.quickshulker.network.OpenInventoryPacket;
import net.kyrptonaught.quickshulker.network.OpenShulkerPacket;
import net.kyrptonaught.quickshulker.network.OpenShulkerPacketV1;
import net.kyrptonaught.quickshulker.network.QuickBundlePacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
//#if MC >= 1.21.2
import net.minecraft.world.InteractionResult;
//#else
//$$ import net.minecraft.world.InteractionResultHolder;
//#endif
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuickShulkerMod implements ModInitializer, RegisterQuickShulker {

    public static final String MOD_ID = "quickshulker";
    public static ConfigManager.SingleConfigManager config = new ConfigManager.SingleConfigManager(MOD_ID, new ConfigOptions());
    public static double lastMouseX, lastMouseY;
    /** Player-inventory index of the slot the cursor was on when the last quick-open was triggered, or -1. */
    public static int lastCursorSlot = -1;
    /**
     * Where inside that slot the cursor sat, in GUI-scaled units (0..16 across the cell). Kept so the next
     * screen restores the same relative spot instead of snapping the pointer to the slot centre.
     */
    public static double lastCursorOffsetX, lastCursorOffsetY;
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ConfigOptions getConfig() {
        return (ConfigOptions) config.getConfig();
    }

    @Override
    public void onInitialize() {
        config.load();
        ServerRules.init(LOGGER);
        net.kyrptonaught.quickshulker.network.ClientSwitchesPacket.register();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                net.kyrptonaught.quickshulker.network.ClientSwitchesPacket.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ClientSwitches.update(context.player(), payload.switches())));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> ClientSwitches.clear(handler.player));
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> QuickShulkerCommand.register(dispatcher));
        // Both shapes stay registered: a client on an older build sends the one-field packet under the
        // original channel, and decoding it with the newer codec would kick that player off the server.
        OpenShulkerPacketV1.registerReceivePacket();
        OpenShulkerPacket.registerReceivePacket();
        QuickBundlePacket.registerReceivePacket();
        CloseAllPacket.registerReceivePacket();
        EventListeners.registerEventListeners();
        LOGGER.info("Unper's QuickShulker is loaded.");

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            boolean openable = Util.isOpenableItem(stack);
            boolean inHand = Util.canOpenInHand(stack);
            if (QuickShulkerMod.getConfig().rightClickToOpen && openable && inHand) {
                if (world.isClientSide()) {
                    // The client has to agree with the server, otherwise it runs vanilla's "use item" for the same
                    // click and predicts placing the block while the server is opening a menu. Returning SUCCESS
                    // keeps the two sides on the same path; the menu itself still arrives from the server.
                    //#if MC >= 1.21.2
                    return InteractionResult.SUCCESS;
                    //#else
                    //$$ return InteractionResultHolder.success(stack);
                    //#endif
                }
                if (hand == InteractionHand.MAIN_HAND)
                    //#if MC >= 1.21.5
                    Util.openItem(player, 0, player.getInventory().getSelectedSlot(), true, false);
                    //#else
                    //$$ Util.openItem(player, 0, player.getInventory().selected, true, false);
                    //#endif
                else Util.openItem(player, 0, Inventory.SLOT_OFFHAND, true, false);

                //#if MC >= 1.21.2
                return InteractionResult.SUCCESS_SERVER;
                //#else
                //$$ return InteractionResultHolder.success(stack);
                //#endif
            }
            // A container the player turned off in their own settings: swallow the click on the client so vanilla
            // never sends the use-item packet. Without this the server - reading its own config - would still open
            // the menu, which is exactly how a disabled item kept opening.
            if (world.isClientSide() && !openable
                    && QuickOpenableRegistry.getQuickieRaw(stack.getItem()) != null) {
                //#if MC >= 1.21.2
                return InteractionResult.SUCCESS;
                //#else
                //$$ return InteractionResultHolder.success(stack);
                //#endif
            }
            //#if MC >= 1.21.2
            return InteractionResult.PASS;
            //#else
            //$$ return InteractionResultHolder.pass(stack);
            //#endif
        });

        PayloadTypeRegistry.playS2C().register(OpenInventoryPacket.OPEN_INV_ID, OpenInventoryPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(EnderChestS2CSyncPacket.S2CEChestContentPacket.S2C_ECHEST_CONTENT_PACKET_ID, EnderChestS2CSyncPacket.S2CEChestContentPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(EnderChestS2CSyncPacket.S2CEChestSlotPacket.S2C_ECHEST_SLOT_PACKET_ID, EnderChestS2CSyncPacket.S2CEChestSlotPacket.CODEC);

        FabricLoader.getInstance().getEntrypoints(MOD_ID, RegisterQuickShulker.class).forEach(RegisterQuickShulker::registerProviders);
    }

    private static ContainerLevelAccess createAccess(Player player) {
        //#if MC >= 1.21.6
        return ContainerLevelAccess.create(player.level(), player.blockPosition());
        //#else
        //$$ return ContainerLevelAccess.create(player.getCommandSenderWorld(), player.blockPosition());
        //#endif
    }

    /**
     * Always registers, with the config switch attached as a live predicate. Dropping the registration when the
     * switch was off at launch meant the item stayed openable (or stayed dead) until a restart, because the registry
     * is only filled once.
     */
    private void registerMenuBlock(java.util.function.BooleanSupplier enabled, Block block, Component title, MenuFactory factory) {
        ServerRules.register(block, block.asItem());
        new QuickOpenableRegistry.Builder()
                .setItem(block.getClass())
                .ignoreSingleStackCheck(true)
                .canOpen(enabled)
                .setOpenAction((player, stack) ->
                        player.openMenu(new SimpleMenuProvider((i, playerInventory, playerEntity) ->
                                factory.create(i, playerInventory, createAccess(playerEntity)), title)))
                .register();
    }

    @Override
    public void registerProviders() {
        if (getConfig().quickShulkerBox) {
            ServerRules.register(Blocks.SHULKER_BOX, Items.SHULKER_BOX);
            new QuickOpenableRegistry.Builder()
                    .setItem(ShulkerBoxBlock.class)
                    .supportsBundleing(true)
                    .getSound((stack, isOpenSound) -> isOpenSound ? SoundEvents.SHULKER_BOX_OPEN : SoundEvents.SHULKER_BOX_CLOSE)
                    .setOpenAction(((player, stack) ->
                            player.openMenu(new SimpleMenuProvider((i, playerInventory, playerEntity) ->
                                    new ShulkerBoxMenu(i, player.getInventory(), new ItemStackInventory(stack, 27)),
                                    stack.getComponents().has(DataComponents.CUSTOM_NAME) ? stack.getHoverName() : Component.translatable("container.shulkerBox")))))
                    .register();
        }

        if (getConfig().quickEChest) {
            ServerRules.register(Blocks.ENDER_CHEST, Items.ENDER_CHEST);
            new QuickOpenableRegistry.Builder(new QuickShulkerData.QuickEnderData())
                    .setItem(EnderChestBlock.class)
                    .supportsBundleing(true)
                    .getSound((stack, isOpenSound) -> isOpenSound ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.ENDER_CHEST_CLOSE)
                    .ignoreSingleStackCheck(true)
                    .setOpenAction(((player, stack) ->
                            player.openMenu(new SimpleMenuProvider((i, playerInventory, playerEntity) ->
                                    ChestMenu.threeRows(i, playerInventory, player.getEnderChestInventory()), Component.translatable("container.enderchest")))))
                    .register();
        }

        registerMenuBlock(() -> getConfig().quickCraftingTables,  Blocks.CRAFTING_TABLE,
                Component.translatable("container.crafting"),           CraftingMenu::new);
        registerMenuBlock(() -> getConfig().quickStonecutter,     Blocks.STONECUTTER,
                Component.translatable("container.stonecutter"),        StonecutterMenu::new);
        registerMenuBlock(() -> getConfig().quickAnvil,           Blocks.ANVIL,
                Component.translatable("container.repair"),             AnvilMenu::new);
        // Chipped and damaged anvils are separate items (not block states), so /uqs has to know all three or
        // "all" silently misses two of them. They share AnvilBlock, so item lookup already resolves them to the
        // same openable entry; this only adds their ids to the server's allow list.
        ServerRules.register(Blocks.CHIPPED_ANVIL, Items.CHIPPED_ANVIL);
        ServerRules.register(Blocks.DAMAGED_ANVIL, Items.DAMAGED_ANVIL);

        registerMenuBlock(() -> getConfig().quickGrindstone,      Blocks.GRINDSTONE,
                Component.translatable("container.grindstone_title"),   GrindstoneMenu::new);
        registerMenuBlock(() -> getConfig().quickSmithingTable,   Blocks.SMITHING_TABLE,
                Component.translatable("container.upgrade"),            SmithingMenu::new);
        registerMenuBlock(() -> getConfig().quickLoom,            Blocks.LOOM,
                Component.translatable("container.loom"),               LoomMenu::new);
        // Runs last so every provider is already registered: catches the dyed shulker boxes, the two extra anvil
        // items, and anything a compatible mod registered under an existing block class.
        ServerRules.scanRegistry();

        //#if MC < 26.1
        if (ModUtils.isModLoad(ModIds.reinfshulker) && QuickShulkerMod.getConfig().quickShulkerBox) {
            ReinfshulkerOpenableRegistry.registerProviders();
        }
        //#endif
    }

}
