package top.xiaotang27.trdm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xiaotang27.trdm.block.BlockTrdmBigStationSign;
import top.xiaotang27.trdm.block.BlockTrdmHungBigSign;
import top.xiaotang27.trdm.block.BlockTrdmPSDControl;
import top.xiaotang27.trdm.block.BlockTrdmPSDDoor;
import top.xiaotang27.trdm.block.BlockTrdmPSDGlass;
import top.xiaotang27.trdm.block.BlockTrdmPSDGlassEnd;
import top.xiaotang27.trdm.block.BlockTrdmPSDTop;
import top.xiaotang27.trdm.block.BlockTrdmPis;
import top.xiaotang27.trdm.block.BlockTrdmPSDEndGate;
import top.xiaotang27.trdm.block.BlockTrdmEndGateModels;
import top.xiaotang27.trdm.block.BlockTrdmPSDExtension;
import top.xiaotang27.trdm.block.BlockTrdmPisPole;
import top.xiaotang27.trdm.block.BlockTrdmStationSign;
import top.xiaotang27.trdm.block.BlockTicketVendingMachine;
import top.xiaotang27.trdm.block.BlockCustomerServiceTerminal;
import top.xiaotang27.trdm.block.BlockTrdmTactilePaving;
import top.xiaotang27.trdm.block.BlockTrdmTactilePavingStep;
import top.xiaotang27.trdm.item.ItemTrdmPSD;
import top.xiaotang27.trdm.item.ItemTrdmBigSign;
import top.xiaotang27.trdm.item.ItemSingleTicket;
import top.xiaotang27.trdm.item.ItemTransitCard;
import top.xiaotang27.trdm.item.ItemVariableTicket;
import top.xiaotang27.trdm.net.TicketNetwork;
import top.xiaotang27.trdm.server.ServerFareRuntime;
import top.xiaotang27.trdm.control.PsdManualHold;
import top.xiaotang27.trdm.net.TrdmControlNetwork;

/**
 * TangsRail Decoration (TRD) — 主入口。
 * <p>
 */
public class Trdm implements ModInitializer {

    public static final String MOD_ID = "trdm";
    public static final Logger LOGGER = LoggerFactory.getLogger("TangsRail Decoration");

    /** 旧存档门楣 side 迁移队列：区块加载时只入队，服务器 tick 再写方块。 */
    private static final java.util.Queue<HeaderKey> PENDING_HEADER_FIXES = new java.util.ArrayDeque<>();

    // 方块：一套门扇、玻璃、端头玻璃，外加一格门楣
    public static final BlockTrdmPSDDoor PSD_DOOR_1 = new BlockTrdmPSDDoor();
    public static final BlockTrdmPSDGlass PSD_GLASS_1 = new BlockTrdmPSDGlass();
    public static final BlockTrdmPSDGlassEnd PSD_GLASS_END_1 = new BlockTrdmPSDGlassEnd();
    public static final BlockTrdmPSDTop PSD_TOP = new BlockTrdmPSDTop();
    /** 屏蔽门集控装置：刷子右键选站台，空手右键开关该站台所有屏蔽门。 */
    public static final BlockTrdmPSDControl PSD_CONTROL = new BlockTrdmPSDControl();
    /** 站名牌：显示本站 / 下一站 / 线路色带，刷子右键选站台。 */
    public static final BlockTrdmStationSign STATION_SIGN = new BlockTrdmStationSign();
    /** 大站名牌：深灰横牌，标志 + 线路编号 + 站名 + 出口字母；站 / ZHAN 与无障碍电梯牌都在刷子菜单里切。 */
    public static final BlockTrdmBigStationSign BIG_SIGN_1 = new BlockTrdmBigStationSign();
    /** 悬挂式站外站名牌：内容同上，吊在天花板下，单面有字、自发光。 */
    public static final BlockTrdmHungBigSign HUNG_SIGN = new BlockTrdmHungBigSign();
    /** PIS 乘客信息屏：16:9 吊挂横屏，内容由 MTR3 到站数据实时绘制。 */
    public static final BlockTrdmPis PIS = new BlockTrdmPis();
    /** 显示屏柱：单独一格的立杆，用来把 PIS 架高。 */
    public static final BlockTrdmPisPole PIS_POLE = new BlockTrdmPisPole();
    /** 盲道砖：贴在地面的薄贴片，按四邻自动在「线」和「点」之间切换。 */
    public static final BlockTrdmTactilePaving TACTILE_PAVING = new BlockTrdmTactilePaving();
    /** 盲道台阶：半格高（8px）的盲道砖，连接逻辑与贴地盲道一致。 */
    public static final BlockTrdmTactilePavingStep TACTILE_PAVING_STEP = new BlockTrdmTactilePavingStep();
    /** 屏蔽门端门：玻璃面板 + 上方不动门楣。 */
    public static final BlockTrdmPSDEndGate PSD_END_GATE = new BlockTrdmPSDEndGate();
    /** 纯技术方块：只为把端门门扇的 4 个模型烘进模型管理器，永远不会被放置、也没有物品。 */
    public static final BlockTrdmEndGateModels PSD_END_GATE_MODELS = new BlockTrdmEndGateModels();
    /** 售票机：右键打开售票界面。 */
    public static final BlockTicketVendingMachine TICKET_VENDING_MACHINE = new BlockTicketVendingMachine();
    /** 客服终端：右键打开客服界面。 */
    public static final BlockCustomerServiceTerminal CUSTOMER_SERVICE_TERMINAL = new BlockCustomerServiceTerminal();
    /** 高站台门延伸板：在门楣高度把门楣往两侧/拐角延伸，直线 / 转角 / 拐弯后自动换门楣模型。 */
    public static final BlockTrdmPSDExtension PSD_EXTENSION = new BlockTrdmPSDExtension();
    /** 屏蔽门端门：门楣 + 可旋开的门 + 固定玻璃。 */

    // 物品（与 MTR 一致：门楣没有独立物品，用玻璃物品放置）
    public static final ItemTrdmPSD PSD_DOOR_ITEM_1 = new ItemTrdmPSD(ItemTrdmPSD.Kind.DOOR);
    public static final ItemTrdmPSD PSD_GLASS_ITEM_1 = new ItemTrdmPSD(ItemTrdmPSD.Kind.GLASS);
    public static final ItemTrdmPSD PSD_GLASS_END_ITEM_1 = new ItemTrdmPSD(ItemTrdmPSD.Kind.GLASS_END);
    public static final Item PSD_CONTROL_ITEM = new BlockItem(PSD_CONTROL, new Item.Properties());
    public static final Item STATION_SIGN_ITEM = new BlockItem(STATION_SIGN, new Item.Properties());
    public static final Item BIG_SIGN_ITEM_1 = new ItemTrdmBigSign(BIG_SIGN_1);
    public static final Item HUNG_SIGN_ITEM = new ItemTrdmBigSign(HUNG_SIGN);
    public static final Item PIS_ITEM = new BlockItem(PIS, new Item.Properties());
    public static final Item PIS_POLE_ITEM = new BlockItem(PIS_POLE, new Item.Properties());
    public static final Item TACTILE_PAVING_ITEM = new BlockItem(TACTILE_PAVING, new Item.Properties());
    public static final Item TACTILE_PAVING_STEP_ITEM = new BlockItem(TACTILE_PAVING_STEP, new Item.Properties());
    public static final ItemTrdmPSD PSD_END_GATE_ITEM = new ItemTrdmPSD(ItemTrdmPSD.Kind.END_GATE);
    public static final Item PSD_EXTENSION_ITEM = new BlockItem(PSD_EXTENSION, new Item.Properties());
    public static final Item TICKET_VENDING_MACHINE_ITEM =
            new BlockItem(TICKET_VENDING_MACHINE, new Item.Properties());
    public static final Item CUSTOMER_SERVICE_TERMINAL_ITEM =
            new BlockItem(CUSTOMER_SERVICE_TERMINAL, new Item.Properties());
    public static final Item SINGLE_TICKET_ITEM = new ItemSingleTicket();
    public static final Item VARIABLE_TICKET_ITEM = new ItemVariableTicket();
    public static final Item TRANSIT_CARD_ITEM = new ItemTransitCard();

    public static BlockEntityType<BlockTrdmPSDDoor.TileEntityTrdmPSDDoor> PSD_DOOR_1_BE;
    public static BlockEntityType<BlockTrdmPSDTop.TileEntityTrdmPSDTop> PSD_TOP_BE;
    public static BlockEntityType<BlockTrdmPSDControl.TileEntityTrdmPSDControl> PSD_CONTROL_BE;
    public static BlockEntityType<BlockTrdmStationSign.TileEntityTrdmStationSign> STATION_SIGN_BE;
    public static BlockEntityType<BlockTrdmBigStationSign.TileEntityTrdmBigStationSign> BIG_SIGN_1_BE;
    public static BlockEntityType<BlockTrdmHungBigSign.TileEntityTrdmHungBigSign> HUNG_SIGN_BE;
    public static BlockEntityType<BlockTrdmPis.TileEntityTrdmPis> PIS_BE;
    public static BlockEntityType<BlockTrdmPSDEndGate.TileEntityTrdmPSDEndGate> PSD_END_GATE_BE;

    public static CreativeModeTab CREATIVE_TAB;

    @Override
    public void onInitialize() {
        registerBlock("psd_door_1", PSD_DOOR_1);
        registerBlock("psd_glass_1", PSD_GLASS_1);
        registerBlock("psd_glass_end_1", PSD_GLASS_END_1);
        registerBlock("psd_top", PSD_TOP);
        registerBlock("psd_control", PSD_CONTROL);
        registerBlock("station_sign", STATION_SIGN);
        registerBlock("big_station_sign_1", BIG_SIGN_1);
        registerBlock("hung_big_station_sign", HUNG_SIGN);
        registerBlock("pis", PIS);
        registerBlock("pis_pole", PIS_POLE);
        registerBlock("tactile_paving", TACTILE_PAVING);
        registerBlock("tactile_paving_step", TACTILE_PAVING_STEP);
        registerBlock("psd_end_gate", PSD_END_GATE);
        registerBlock("psd_end_gate_models", PSD_END_GATE_MODELS);
        registerBlock("psd_extension", PSD_EXTENSION);
        registerBlock("ticket_vending_machine", TICKET_VENDING_MACHINE);
        registerBlock("customer_service_terminal", CUSTOMER_SERVICE_TERMINAL);

        registerItem("psd_door_1", PSD_DOOR_ITEM_1);
        registerItem("psd_glass_1", PSD_GLASS_ITEM_1);
        registerItem("psd_glass_end_1", PSD_GLASS_END_ITEM_1);
        registerItem("psd_control", PSD_CONTROL_ITEM);
        registerItem("station_sign", STATION_SIGN_ITEM);
        registerItem("big_station_sign_1", BIG_SIGN_ITEM_1);
        registerItem("hung_big_station_sign", HUNG_SIGN_ITEM);
        registerItem("pis", PIS_ITEM);
        registerItem("pis_pole", PIS_POLE_ITEM);
        registerItem("tactile_paving", TACTILE_PAVING_ITEM);
        registerItem("tactile_paving_step", TACTILE_PAVING_STEP_ITEM);
        registerItem("psd_end_gate", PSD_END_GATE_ITEM);
        registerItem("psd_extension", PSD_EXTENSION_ITEM);
        registerItem("ticket_vending_machine", TICKET_VENDING_MACHINE_ITEM);
        registerItem("customer_service_terminal", CUSTOMER_SERVICE_TERMINAL_ITEM);
        registerItem("single_ticket", SINGLE_TICKET_ITEM);
        registerItem("variable_ticket", VARIABLE_TICKET_ITEM);
        registerItem("transit_card", TRANSIT_CARD_ITEM);

        PSD_DOOR_1_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("psd_door_1"),
                BlockEntityType.Builder.of(BlockTrdmPSDDoor.TileEntityTrdmPSDDoor::new, PSD_DOOR_1).build(null));
        PSD_TOP_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("psd_top"),
                BlockEntityType.Builder.of(BlockTrdmPSDTop.TileEntityTrdmPSDTop::new, PSD_TOP).build(null));
        PSD_CONTROL_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("psd_control"),
                BlockEntityType.Builder.of(BlockTrdmPSDControl.TileEntityTrdmPSDControl::new, PSD_CONTROL).build(null));
        STATION_SIGN_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("station_sign"),
                BlockEntityType.Builder.of(BlockTrdmStationSign.TileEntityTrdmStationSign::new, STATION_SIGN).build(null));
        BIG_SIGN_1_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("big_station_sign_1"),
                BlockEntityType.Builder.of(BlockTrdmBigStationSign.TileEntityTrdmBigStationSign::new, BIG_SIGN_1).build(null));
        HUNG_SIGN_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("hung_big_station_sign"),
                BlockEntityType.Builder.of(BlockTrdmHungBigSign.TileEntityTrdmHungBigSign::new, HUNG_SIGN).build(null));
        PIS_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("pis"),
                BlockEntityType.Builder.of(BlockTrdmPis.TileEntityTrdmPis::new, PIS).build(null));
        PSD_END_GATE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("psd_end_gate"),
                BlockEntityType.Builder.of(BlockTrdmPSDEndGate.TileEntityTrdmPSDEndGate::new, PSD_END_GATE).build(null));

        // C2S：刷子右键选完站台 → 写回集控装置；门楣外观 / 大站名牌选项同理
        TrdmControlNetwork.registerServer();
        // 票务交易：买票 / 买卡 / 充值 / 补车费
        TicketNetwork.registerServer();
        // 服务端全局 tick：手动开门保持着的时候，每 tick 把门压回开启（列车逻辑改不动它）
        PsdManualHold.registerServerTick();
        // 旧存档的门楣 SIDE_EXTENDED 还是「整排连起来」的旧值；区块加载时按门/玻璃分组重算一次
        registerHeaderSideMigration();

        CREATIVE_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id(MOD_ID),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(PSD_DOOR_ITEM_1))
                        .title(Component.translatable("itemGroup.trdm"))
                        .build());
        ItemGroupEvents.modifyEntriesEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, id(MOD_ID)))
                .register(entries -> {
                    entries.accept(new ItemStack(PSD_DOOR_ITEM_1));
                    entries.accept(new ItemStack(PSD_GLASS_ITEM_1));
                    entries.accept(new ItemStack(PSD_GLASS_END_ITEM_1));
                    entries.accept(new ItemStack(PSD_CONTROL_ITEM));
                    entries.accept(new ItemStack(STATION_SIGN_ITEM));
                    entries.accept(new ItemStack(BIG_SIGN_ITEM_1));
                    entries.accept(new ItemStack(HUNG_SIGN_ITEM));
                    entries.accept(new ItemStack(PIS_ITEM));
                    entries.accept(new ItemStack(PIS_POLE_ITEM));
                    entries.accept(new ItemStack(TACTILE_PAVING_ITEM));
                    entries.accept(new ItemStack(TACTILE_PAVING_STEP_ITEM));
                    entries.accept(new ItemStack(PSD_END_GATE_ITEM));
                    entries.accept(new ItemStack(PSD_EXTENSION_ITEM));
                    entries.accept(new ItemStack(TICKET_VENDING_MACHINE_ITEM));
                    entries.accept(new ItemStack(CUSTOMER_SERVICE_TERMINAL_ITEM));
                    entries.accept(new ItemStack(SINGLE_TICKET_ITEM));
                    entries.accept(new ItemStack(VARIABLE_TICKET_ITEM));
                    entries.accept(new ItemStack(TRANSIT_CARD_ITEM));
                });

        LOGGER.info("TangsRail Decoration initialized —— Version {}", FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?"));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /**
     * 旧存档迁移：以前门楣的 {@code SIDE_EXTENDED} 是「左右有门楣就连起来」算的，
     * 所以一整排门/玻璃的门楣 side 都是 left/middle/right，门楣贴图会按整排走。
     * 现在改成 MTR 原版的门/玻璃分组（一扇门用门的 SIDE，一组玻璃用 SIDE_EXTENDED），
     * 旧存档里的已放置门楣不会自己重算，因此区块加载时只把门楣位置入队，
     * 等服务器 tick（区块完全加载后）再重算 —— 不在 CHUNK_LOAD 里直接写方块，避免加载卡死。
     */
    private static void registerHeaderSideMigration() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            for (final BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof BlockTrdmPSDTop.TileEntityTrdmPSDTop) {
                    PENDING_HEADER_FIXES.add(new HeaderKey(world, blockEntity.getBlockPos()));
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 一 tick 最多处理一批，避免进服第一 tick 一次改太多方块。
            for (int i = 0; i < 256; i++) {
                final HeaderKey key = PENDING_HEADER_FIXES.poll();
                if (key == null) {
                    return;
                }
                final ServerLevel level = key.level();
                if (!level.isLoaded(key.pos())) {
                    continue;
                }
                final BlockState current = level.getBlockState(key.pos());
                if (!(current.getBlock() instanceof BlockTrdmPSDTop)) {
                    continue;
                }
                final BlockState actual = BlockTrdmPSDTop.getActualState(level, key.pos());
                if (actual != current) {
                    level.setBlock(key.pos(), actual, Block.UPDATE_CLIENTS);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PENDING_HEADER_FIXES.clear();
            ServerFareRuntime.reset();
        });
    }

    private record HeaderKey(ServerLevel level, BlockPos pos) {
    }

    private static void registerBlock(String path, Block block) {
        Registry.register(BuiltInRegistries.BLOCK, id(path), block);
    }

    private static void registerItem(String path, Item item) {
        Registry.register(BuiltInRegistries.ITEM, id(path), item);
    }
}
