package com.yourname.yellowduck.config;

import com.mojang.brigadier.CommandDispatcher;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.cleopatra.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class YellowDuckReloadCommand {
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) { register(e.getDispatcher()); }
    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("yellowduck").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reload").executes(ctx -> {
                    try {
                        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FMLPaths.CONFIGDIR.get());
                        int count=0;
                        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
                            for (var entity : level.getAllEntities()) {
                                if (entity instanceof LivingEntity living) { EntityTuningConfig.reapply(living);
                                    if (living instanceof CleopatraBoss) apply(living, CleopatraConfig.bossHealth.get(), CleopatraConfig.bossAttack.get());
                                    else if (living instanceof CleopatraSandworm) apply(living, CleopatraConfig.sandwormHealth.get(), null);
                                    else if (living instanceof CleopatraScorpion) apply(living, CleopatraConfig.scorpionHealth.get(), null);
                                    else if (living instanceof CleopatraVenomSnake) apply(living, CleopatraConfig.snakeHealth.get(), CleopatraConfig.snakeAttack.get());
                                    count++; }
                            }
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("YellowDuck 配置已重新读取；已重新应用在线实体属性。"), true);
                        return count;
                    } catch (Throwable t) {
                        ctx.getSource().sendFailure(Component.literal("YellowDuck reload 失败: "+t.getMessage()));
                        return 0;
                    }
                })));
    }
    private static void apply(LivingEntity e, Double hp, Double attack) {
        float ratio=e.getMaxHealth()>0?e.getHealth()/e.getMaxHealth():1;
        if(hp!=null&&e.getAttribute(Attributes.MAX_HEALTH)!=null){e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);e.setHealth(Math.max(.1f,Math.min(e.getMaxHealth(),e.getMaxHealth()*ratio)));}
        if(attack!=null&&e.getAttribute(Attributes.ATTACK_DAMAGE)!=null)e.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack);
    }
    private YellowDuckReloadCommand(){}
}
