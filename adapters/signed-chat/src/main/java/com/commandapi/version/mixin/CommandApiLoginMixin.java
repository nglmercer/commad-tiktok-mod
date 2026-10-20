package com.commandapi.version.mixin;

import com.commandapi.version.CommandApiCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows the config summary when the player joins a world on the signed-chat
 * generation (1.19 through 1.19.2).
 *
 * <p>{@code handleLogin} creates the local player on every supported version,
 * so at {@code TAIL} the player exists and can be told in chat. Dimension
 * changes go through {@code handleRespawn} instead, so the summary prints once
 * per login, not once per teleport.</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class CommandApiLoginMixin {

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void commandapi$onLogin(CallbackInfo ci) {
        CommandApiCommands.onLogin(Minecraft.getInstance().player);
    }
}
