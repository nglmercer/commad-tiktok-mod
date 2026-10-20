package com.commandapi.version.mixin;

import com.commandapi.version.CommandApiCompletions;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers completions whenever a server sends a fresh command tree. */
@Mixin(ClientPacketListener.class)
public abstract class CommandApiSuggestionsMixin {

    @Inject(method = "handleCommands", at = @At("TAIL"))
    private void commandapi$onCommands(CallbackInfo ci) {
        CommandApiCompletions.register(((ClientPacketListener) (Object) this).getCommands());
    }
}
