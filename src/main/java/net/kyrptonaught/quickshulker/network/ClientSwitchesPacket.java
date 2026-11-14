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

package net.kyrptonaught.quickshulker.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#if MC >= 1.21.11
import net.minecraft.resources.Identifier;
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//#endif
import org.jetbrains.annotations.NotNull;

/**
 * Carries the player's own "enabled items" switches to the server once per join (and after every change).
 * <p>
 * Needed because the switches live in the client's own config file, and vanilla sends the use-item packet to the server
 * regardless of what a client-side callback returns - {@code MultiPlayerGameMode.useItem} builds the packet before
 * calling {@code ItemStack.use}, so no client code can suppress it. The server therefore has to know what this player
 * has turned off, otherwise a client-disabled item still opens (the server reads its own copy of those switches, or
 * the defaults).
 * <p>
 * One byte per switch, in the fixed order declared by {@link #SIZE}. The server treats a missing report as
 * "everything on", so older clients keep working unchanged.
 */
public record ClientSwitchesPacket(boolean[] switches) implements CustomPacketPayload {

    /** Number of switches carried; must match {@link net.kyrptonaught.quickshulker.config.ConfigOptions}' order. */
    public static final int SIZE = 8;

    //#if MC <= 1.20.6
    //$$ public static final ResourceLocation SWITCHES_ID = ResourceLocation.tryBuild("quickshulker", "client_switches");
    //#elseif MC >= 1.21.11
    public static final Identifier SWITCHES_ID = Identifier.fromNamespaceAndPath("quickshulker", "client_switches");
    //#else
    //$$ public static final ResourceLocation SWITCHES_ID = ResourceLocation.fromNamespaceAndPath("quickshulker", "client_switches");
    //#endif

    public static final Type<ClientSwitchesPacket> TYPE = new Type<>(SWITCHES_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientSwitchesPacket> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                for (int i = 0; i < SIZE; i++) {
                    buf.writeBoolean(i < value.switches.length && value.switches[i]);
                }
            },
            buf -> {
                boolean[] values = new boolean[SIZE];
                for (int i = 0; i < SIZE; i++) {
                    values[i] = buf.readBoolean();
                }
                return new ClientSwitchesPacket(values);
            });

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
    }

    @Environment(EnvType.CLIENT)
    public static void send(boolean[] switches) {
        ClientPlayNetworking.send(new ClientSwitchesPacket(switches));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
