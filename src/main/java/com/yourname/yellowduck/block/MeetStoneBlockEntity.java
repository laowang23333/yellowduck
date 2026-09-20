package com.yourname.yellowduck.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * 副本柱子方块实体。
 * 每一根柱子只保存一个 DungeonId；真正的副本参数由 DungeonConfig 动态读取。
 */
public class MeetStoneBlockEntity extends BlockEntity {
    private static final String TAG_DUNGEON_ID = "DungeonId";
    private String dungeonId = "";

    public MeetStoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public String getDungeonId() {
        return dungeonId;
    }

    public boolean isBound() {
        return dungeonId != null && !dungeonId.isBlank();
    }

    public void setDungeonId(String id) {
        this.dungeonId = normalize(id);
        setChanged();
    }

    public void clearDungeonId() {
        this.dungeonId = "";
        setChanged();
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (isBound()) tag.putString(TAG_DUNGEON_ID, dungeonId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        dungeonId = normalize(tag.getString(TAG_DUNGEON_ID));
    }
}
