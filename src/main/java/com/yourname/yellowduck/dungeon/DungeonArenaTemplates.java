package com.yourname.yellowduck.dungeon;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class DungeonArenaTemplates {
    private static final Map<String,ArenaTemplate> TEMPLATES=new LinkedHashMap<>();
    static {
        register("cleopatra",new ArenaTemplate(new ResourceLocation(YellowDuckMod.MOD_ID,"dungeons/cleopatra"),new BlockPos(-29,0,-29),new BlockPos(0,0,0),List.of(new BlockPos(-25,0,-7),new BlockPos(-25,0,-3),new BlockPos(-25,0,1),new BlockPos(-25,0,5),new BlockPos(-25,0,9)),30,39));
        register("sakura",new ArenaTemplate(new ResourceLocation(YellowDuckMod.MOD_ID,"dungeons/sakura"),new BlockPos(-14,0,-15),new BlockPos(0,0,0),List.of(new BlockPos(0,0,-12),new BlockPos(-11,0,0),new BlockPos(11,0,0),new BlockPos(0,0,12)),16,25));
        register("garmr",new ArenaTemplate(new ResourceLocation(YellowDuckMod.MOD_ID,"dungeons/garmr"),new BlockPos(-45,-16,-35),new BlockPos(0,0,0),List.of(new BlockPos(0,0,30),new BlockPos(-1,0,30),new BlockPos(1,0,30),new BlockPos(-2,0,30),new BlockPos(2,0,30)),48,51));
        // 广寒宫：81 x 133 x 88。蓝图中央唯一绿宝石块作为嫦娥出生点；唯一标靶方块一侧作为玩家入口。
        register("change",new ArenaTemplate(new ResourceLocation(YellowDuckMod.MOD_ID,"dungeons/change"),new BlockPos(-40,-5,-47),new BlockPos(0,0,0),List.of(new BlockPos(0,0,-27),new BlockPos(-1,0,-27),new BlockPos(1,0,-27),new BlockPos(-2,0,-27),new BlockPos(2,0,-27)),45,133));
    }
    private DungeonArenaTemplates(){}
    public static void register(String id,ArenaTemplate t){if(id!=null&&!id.isBlank()&&t!=null)TEMPLATES.put(id.trim().toLowerCase(Locale.ROOT),t);}
    public static ArenaTemplate get(String id){return id==null?null:TEMPLATES.get(id.trim().toLowerCase(Locale.ROOT));}
    public static boolean has(String id){return get(id)!=null;}
    public record ArenaTemplate(ResourceLocation structureId,BlockPos placementOffset,BlockPos bossOffset,List<BlockPos> entranceOffsets,int horizontalRadius,int height){public ArenaTemplate{entranceOffsets=List.copyOf(entranceOffsets);}}
}
