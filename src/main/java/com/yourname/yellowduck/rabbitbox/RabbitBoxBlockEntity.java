package com.yourname.yellowduck.rabbitbox;

import com.yourname.yellowduck.registry.ModItems;
import com.yourname.yellowduck.statue.ChangeStatueContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 兔兔宝箱倒计时与奖励逻辑。
 *
 * 10 秒后只播放烟花式粒子和声音，不创建真实 Explosion，
 * 因此不会造成伤害、击退或破坏附近方块。
 */
public final class RabbitBoxBlockEntity extends BlockEntity {
    public static final int OPEN_DELAY_TICKS = 20 * 10;

    private int ticksRemaining = OPEN_DELAY_TICKS;
    private boolean opened;

    public RabbitBoxBlockEntity(BlockPos pos, BlockState state) {
        super(RabbitBoxContent.JADE_RABBIT_BOX_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RabbitBoxBlockEntity box) {
        if (!(level instanceof ServerLevel serverLevel) || box.opened) return;

        if (box.ticksRemaining > 0) {
            box.ticksRemaining--;
            // 每秒标记一次即可保证存档进度，同时避免每 tick 都把区块标脏。
            if (box.ticksRemaining % 20 == 0) box.setChanged();
            if (box.ticksRemaining > 0) return;
        }

        box.opened = true;
        box.open(serverLevel, pos);
    }

    private void open(ServerLevel level, BlockPos pos) {
        // 先选奖励，随后移除方块。整个过程不调用 Level#explode。
        ItemStack reward = rollReward(level);

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.65D;
        double z = pos.getZ() + 0.5D;

        // 烟花爆炸视觉。
        level.sendParticles(ParticleTypes.FLASH, x, y + 0.15D, z, 2, 0.05D, 0.05D, 0.05D, 0.0D);
        level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 85, 0.55D, 0.50D, 0.55D, 0.16D);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 28, 0.45D, 0.40D, 0.45D, 0.08D);
        level.sendParticles(ParticleTypes.POOF, x, y, z, 18, 0.35D, 0.25D, 0.35D, 0.05D);

        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 1.25F, 1.0F);
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 0.9F, 1.08F);

        level.removeBlock(pos, false);

        if (!reward.isEmpty()) {
            ItemEntity item = new ItemEntity(level, x, y, z, reward);
            item.setDefaultPickUpDelay();
            item.setDeltaMovement(
                    (level.random.nextDouble() - 0.5D) * 0.16D,
                    0.24D,
                    (level.random.nextDouble() - 0.5D) * 0.16D
            );
            level.addFreshEntity(item);
        }
    }

    /**
     * 每次开启抽取一种奖励：
     * 嫦娥雕像 5%，掉落 1 个；
     * 其余 95% 在五个月饼之间等分，因此每种月饼均为 19%，抽中后一次掉落 10 个。
     */
    private static ItemStack rollReward(ServerLevel level) {
        if (level.random.nextFloat() < 0.05F) {
            return new ItemStack(ChangeStatueContent.CHANGE_STATUE_ITEM.get());
        }

        return switch (level.random.nextInt(5)) {
            case 0 -> new ItemStack(ModItems.SAKURA_ICE_MOONCAKE_RED.get(), 10);
            case 1 -> new ItemStack(ModItems.SAKURA_ICE_MOONCAKE_YELLOW.get(), 10);
            case 2 -> new ItemStack(ModItems.ROSE_MOONCAKE.get(), 10);
            case 3 -> new ItemStack(ModItems.RABBIT_CAKE.get(), 10);
            default -> new ItemStack(ModItems.RABBIT_RABBIT_CAKE.get(), 10);
        };
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ticksRemaining = tag.contains("TicksRemaining")
                ? Math.max(0, tag.getInt("TicksRemaining"))
                : OPEN_DELAY_TICKS;
        opened = tag.getBoolean("Opened");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("TicksRemaining", ticksRemaining);
        tag.putBoolean("Opened", opened);
    }
}
