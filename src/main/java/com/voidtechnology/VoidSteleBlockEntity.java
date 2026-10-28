package com.voidtechnology;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * 虚空石碑的方块实体：记住要成型的结构 id 与尺寸，并提供「成型」动作。
 *
 * <p>结构文件位置：{@code data/void_technology/structure/void_palace.nbt}，
 * 即 id {@code void_technology:void_palace}（旧的 void_monument 会在读档时自动迁移）。</p>
 */
public class VoidSteleBlockEntity extends BlockEntity {

    /** 默认结构 id：虚空石碑「成型结构」放置的就是它。 */
    public static final ResourceLocation DEFAULT_STRUCTURE =
            ResourceLocation.fromNamespaceAndPath(VoidTechnology.MODID, "void_palace");

    /** 旧版默认结构（已放下的石碑存档里记的是它），读档时自动迁移到 {@link #DEFAULT_STRUCTURE}。 */
    private static final ResourceLocation LEGACY_STRUCTURE =
            ResourceLocation.fromNamespaceAndPath(VoidTechnology.MODID, "void_monument");

    private static final String TAG_STRUCTURE = "Structure";
    private static final String TAG_SIZE = "Size";

    private ResourceLocation structureId = DEFAULT_STRUCTURE;
    /** 结构尺寸缓存（X/Y/Z），客户端预览线框要用，随方块实体数据同步。 */
    private int sizeX = 0, sizeY = 0, sizeZ = 0;

    public VoidSteleBlockEntity(BlockPos pos, BlockState state) {
        super(VoidTechnology.VOID_STELE_BE.get(), pos, state);
    }

    public ResourceLocation getStructureId() {
        return structureId;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    /** 服务端：从结构模板读尺寸（找不到结构时保持 0）。 */
    public void refreshStructureSize() {
        if (!(level instanceof ServerLevel server)) return;
        var template = server.getStructureManager().get(structureId);
        if (template.isEmpty()) {
            VoidTechnology.LOGGER.warn("[voidstele] structure {} not found", structureId);
            return;
        }
        var size = template.get().getSize();
        if (size.getX() != sizeX || size.getY() != sizeY || size.getZ() != sizeZ) {
            sizeX = size.getX();
            sizeY = size.getY();
            sizeZ = size.getZ();
            setChanged();
            sync();
            VoidTechnology.LOGGER.info("[voidstele] structure {} size {}x{}x{}", structureId, sizeX, sizeY, sizeZ);
        }
    }

    /**
     * 成型：把结构放置到石碑上方一格。
     *
     * @return 是否成功
     */
    public boolean buildStructure(Player player) {
        if (!(level instanceof ServerLevel server)) return false;
        refreshStructureSize();
        var templateOpt = server.getStructureManager().get(structureId);
        if (templateOpt.isEmpty()) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "msg.void_technology.stele.missing", structureId.toString()), true);
            return false;
        }
        StructureTemplate template = templateOpt.get();
        // 石碑在结构正前方、左右居中、与最低层同高（见 VoidSteleGeometry）
        BlockPos origin = VoidSteleGeometry.structureOrigin(worldPosition, sizeX, sizeZ);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(true)
                .setKnownShape(true);

        boolean ok = template.placeInWorld(server, origin, origin, settings, server.random, 3);
        if (ok) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "msg.void_technology.stele.built", sizeX, sizeY, sizeZ), true);
            VoidTechnology.LOGGER.info("[voidstele] placed {} at {} ({})", structureId, origin, player.getName().getString());
        }
        return ok;
    }

    /** 预览用的世界坐标包围盒：与成型落点完全一致。 */
    public Vec3 previewMin() {
        var origin = VoidSteleGeometry.structureOrigin(worldPosition, sizeX, sizeZ);
        return new Vec3(origin.getX(), origin.getY(), origin.getZ());
    }

    public Vec3 previewMax() {
        return previewMin().add(sizeX, sizeY, sizeZ);
    }

    // ------------------------------------------------------------------ 存档与同步

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_STRUCTURE, structureId.toString());
        tag.putIntArray(TAG_SIZE, new int[]{sizeX, sizeY, sizeZ});
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_STRUCTURE)) {
            var parsed = ResourceLocation.tryParse(tag.getString(TAG_STRUCTURE));
            if (parsed != null) {
                // 旧存档里记的是 void_monument，迁移成新的默认结构
                structureId = parsed.equals(LEGACY_STRUCTURE) ? DEFAULT_STRUCTURE : parsed;
            }
        }
        var arr = tag.getIntArray(TAG_SIZE);
        if (arr.length == 3) {
            sizeX = arr[0];
            sizeY = arr[1];
            sizeZ = arr[2];
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
