package com.yourname.yellowduck.block;

import com.yourname.yellowduck.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;

public class IndStatueBlock extends Block {

    public IndStatueBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && placer instanceof Player) {
            level.playSound(
                    null,
                    pos,
                    ModSounds.XJY.get(),
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F
            );
        }
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        super.onRemove(oldState, level, pos, newState, isMoving);

        // 只有真正被替换/破坏时才停止音效
        if (oldState.getBlock() != newState.getBlock()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    stopXjySound()
            );
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void stopXjySound() {
        Minecraft.getInstance()
                .getSoundManager()
                .stop(
                        new ResourceLocation("yellowduck", "xjy"),
                        SoundSource.BLOCKS
                );
    }
}
