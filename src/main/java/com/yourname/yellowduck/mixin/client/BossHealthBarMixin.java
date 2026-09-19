package com.yourname.yellowduck.mixin.client;
import com.yourname.yellowduck.silk.SilkNetcraftHud;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** 把自定义斯尔克加入 NetCraft 的血条渲染流程。 */
@Mixin(targets = "com.jiufeng.netcraft.client.renderer.BossHealthBarRenderer")
public abstract class BossHealthBarMixin {
 @Inject(method = "onRenderGui", at = @At("HEAD"), remap = false)
 private static void yellowduck$renderSilk(RenderGuiEvent.Post event, CallbackInfo ci) { SilkNetcraftHud.render(event); }
}
