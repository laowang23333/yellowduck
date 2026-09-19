package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.silk.SilkCombatEvents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NetCraft 原地复活按钮的服务端最终校验，防止客户端伪造按钮点击。 */
@Mixin(targets = "com.jiufeng.netcraft.rescue.RescueManager")
public abstract class RescueManagerMixin {
    @Inject(method = "handleReviveOnSpot", at = @At("HEAD"), cancellable = true, remap = false)
    private void yellowduck$denySilkLock(ServerPlayer player, CallbackInfo ci) {
        if (player != null && SilkCombatEvents.locked(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§4心智腐蚀：原地复活仍被禁用"), true);
            ci.cancel();
        }
    }
}
