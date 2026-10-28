package com.voidtechnology;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(VoidTechnology.MODID)
public class VoidTechnology {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "void_technology";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "void_technology" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "void_technology" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "void_technology" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    // Create a Deferred Register to hold BlockEntityTypes
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    // ---------------------------------------------------------------- 虚空控制器
    public static final DeferredBlock<Block> VOID_CONTROLLER = BLOCKS.register("void_controller",
            () -> new VoidControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));
    public static final DeferredItem<BlockItem> VOID_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem("void_controller", VOID_CONTROLLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidControllerBlockEntity>> VOID_CONTROLLER_BE =
            BLOCK_ENTITIES.register("void_controller",
                    () -> BlockEntityType.Builder.of(VoidControllerBlockEntity::new, VOID_CONTROLLER.get()).build(null));

    // ---------------------------------------------------------------- 虚空样板供应器
    // 继承 AE2 的样板供应器：方块状态/朝向/推送方向沿用 AE2，界面直接复用 AE2 的 PatternProviderScreen。
    public static final DeferredBlock<Block> VOID_PATTERN_PROVIDER = BLOCKS.register("void_pattern_provider",
            VoidPatternProviderBlock::new);
    public static final DeferredItem<BlockItem> VOID_PATTERN_PROVIDER_ITEM =
            ITEMS.registerSimpleBlockItem("void_pattern_provider", VOID_PATTERN_PROVIDER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidPatternProviderBlockEntity>> VOID_PATTERN_PROVIDER_BE =
            BLOCK_ENTITIES.register("void_pattern_provider",
                    () -> BlockEntityType.Builder.of(VoidPatternProviderBlockEntity::new, VOID_PATTERN_PROVIDER.get()).build(null));

    // ---------------------------------------------------------------- 虚空石碑（装饰方块）
    public static final DeferredBlock<Block> VOID_STELE = BLOCKS.register("void_stele",
            () -> new VoidSteleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidSteleBlockEntity>> VOID_STELE_BE =
            BLOCK_ENTITIES.register("void_stele",
                    () -> BlockEntityType.Builder.of(VoidSteleBlockEntity::new, VOID_STELE.get()).build(null));
    public static final DeferredItem<BlockItem> VOID_STELE_ITEM =
            ITEMS.registerSimpleBlockItem("void_stele", VOID_STELE);

    /** 创造模式物品栏。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> VOID_TAB =
            CREATIVE_MODE_TABS.register("void_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.void_technology"))
                    .withTabsBefore(net.minecraft.world.item.CreativeModeTabs.COMBAT)
                    .icon(() -> VOID_STELE_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(VOID_CONTROLLER_ITEM.get());
                        output.accept(VOID_PATTERN_PROVIDER_ITEM.get());
                        output.accept(VOID_STELE_ITEM.get());
                    }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public VoidTechnology(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);        // AE2 能力注册。注意两点：
        // 1) 不能在 @Mod 构造期判断 AE2 是否在场（ModList 可能尚未就绪），所以判断放进监听器内部；
        // 2) 监听器内部才触碰 Ae2MachineCapabilities，AE2 缺席时该类不会被加载。
        modEventBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            if (Ae2Compat.isLoaded()) {
                Ae2MachineCapabilities.register(event);
            }
        });



        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        // Register block entity types
        BLOCK_ENTITIES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (VoidTechnology) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(VoidLinkTicker.class);
        // 客户端：石碑的包围盒预览渲染器（只在客户端加载该类）
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(VoidStelePreviewRenderer.class);
        }
        // 客户端：虚空石碑的打字机工具提示（组件注册走 mod 总线）
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(VoidSteleTooltips::register);
        }


    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        // AE2 addon wiring: logs and no-ops when Applied Energistics 2 is not installed.
        Ae2Compat.init();

        // 关键：AE2 的 AEBaseEntityBlock 用 blockEntityClass 做类型校验（getBlockEntityBlockState），
        // 继承它的方块必须在注册完成后重新绑定一次，否则方块放下的第一个 tick 会 NPE：
        //   Cannot invoke "java.lang.Class.isInstance(Object)" because "this.blockEntityClass" is null
        if (VOID_PATTERN_PROVIDER.get() instanceof VoidPatternProviderBlock providerBlock) {
            // AE2 的 PatternProviderBlock 是 AEBaseEntityBlock<PatternProviderBlockEntity>，
            // 泛型参数写死成父类；我们的实体是它的子类，所以这里需要 unchecked 转换。
            @SuppressWarnings({"unchecked", "rawtypes"})
            var bound = (Class) VoidPatternProviderBlockEntity.class;
            @SuppressWarnings({"unchecked", "rawtypes"})
            var boundType = (BlockEntityType) VOID_PATTERN_PROVIDER_BE.get();
            providerBlock.setBlockEntity(bound, boundType, null, null);
            // AE2 用它来把方块实体和掉落物/提示关联起来
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    VOID_PATTERN_PROVIDER_BE.get(), VOID_PATTERN_PROVIDER_ITEM.get());
            LOGGER.info("[ae2] void pattern provider bound to block entity type");
        }


    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
