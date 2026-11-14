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

package net.kyrptonaught.quickshulker.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.kyrptonaught.quickshulker.api.ServerRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * {@code /uqs} - the server operator's allow list for quick-opened containers.
 * <p>
 * This is the server half of the split: the client config decides what a player wants, and this decides what the
 * server permits. Either side turning an item off makes it unusable.
 */
public final class QuickShulkerCommand {

    private QuickShulkerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("uqs")
                //#if MC >= 1.21.11
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                //#else
                //$$ .requires(source -> source.hasPermission(2))
                //#endif
                // No "list" literal: the client sorts completion entries alphabetically (CommandSuggestions
                // .sortSuggestions), so a literal would land between "grindstone" and "loom" and look like noise.
                // Plain "/uqs" shows the list instead, which keeps the completion list to "all" plus the item ids.
                .executes(QuickShulkerCommand::list)
                .then(Commands.argument("item", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            java.util.List<String> options = new java.util.ArrayList<>();
                            options.add("all");
                            ServerRules.registered().keySet().stream()
                                    .map(id -> id.replace("minecraft:", ""))
                                    .sorted()
                                    .forEach(options::add);
                            return SharedSuggestionProvider.suggest(options, builder);
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(QuickShulkerCommand::apply))));
    }

    private static int apply(CommandContext<CommandSourceStack> ctx) {
        String item = StringArgumentType.getString(ctx, "item");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        CommandSourceStack source = ctx.getSource();

        if (item.equals("all")) {
            ServerRules.setAll(enabled);
            source.sendSuccess(() -> Component.translatable(
                    "key.quickshulker.command.all", enabled ? on() : off()), true);
            return 1;
        }

        String id = item.contains(":") ? item : "minecraft:" + item;
        if (!ServerRules.registered().containsKey(id)) {
            source.sendFailure(Component.translatable("key.quickshulker.command.unknown", id));
            return 0;
        }
        ServerRules.set(id, enabled);
        source.sendSuccess(() -> Component.translatable(
                "key.quickshulker.command.one", id, enabled ? on() : off()), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Map<String, Item> items = ServerRules.registered();
        if (items.isEmpty()) {
            source.sendFailure(Component.translatable("key.quickshulker.command.empty"));
            return 0;
        }
        for (Map.Entry<String, Item> entry : items.entrySet()) {
            String id = entry.getKey();
            Component name = Component.translatable(entry.getValue().getDescriptionId());
            String state = ServerRules.isAllowed(id) ? on() : off();
            // id, then the localised item name, then the current state - the name is what the operator recognises,
            // the id is what has to be typed.
            source.sendSuccess(() -> Component.literal(id + " ").append(name).append(Component.literal(" " + state)), false);
        }
        return items.size();
    }

    private static String on() {
        return Component.translatable("key.quickshulker.command.enabled").getString();
    }

    private static String off() {
        return Component.translatable("key.quickshulker.command.disabled").getString();
    }
}
