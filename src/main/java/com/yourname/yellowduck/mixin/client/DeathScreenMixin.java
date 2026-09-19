package com.yourname.yellowduck.mixin.client;
import com.yourname.yellowduck.client.SilkReviveClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin {
 @Inject(method="render",at=@At("TAIL"))
 private void yellowduck$disableSilkRevive(GuiGraphics graphics,int mouseX,int mouseY,float partialTick,CallbackInfo ci){
  if (!SilkReviveClientState.locked()) return;
  for (GuiEventListener listener: ((DeathScreen)(Object)this).children())
   if(listener instanceof Button button && button.getMessage().getString().contains("原地复活")) button.active=false;
 }
}
