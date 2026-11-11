package dev.xy177.waterlogged.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import dev.xy177.waterlogged.WaterloggedMod;
import dev.xy177.waterlogged.common.WaterloggedConfig;
import dev.xy177.waterlogged.common.block.BlockWaterloggedStateCarrier;
import dev.xy177.waterlogged.common.block.WaterloggedPlantFluid;
import dev.xy177.waterlogged.common.fluid.FFDFluidloggedData;
import dev.xy177.waterlogged.common.fluid.FFDStoredFluidStates;

public final class WaterloggedBlockApi {
    public static final PropertyBool UNIVERSAL_WATERLOGGED =
            new UniversalWaterloggedProperty();
    private static final Set<Block> RENDERED_BLOCKS =
            Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
    private static final Set<Block> WATER_COLOR_BLOCKS =
            Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
    private static final Set<ResourceLocation> RENDERED_MODELS =
            new java.util.LinkedHashSet<>();
    private static final Map<Block, Block> STATE_CARRIERS = new IdentityHashMap<>();
    private static final Map<IBlockState, Integer> WET_LEGACY_IDS = new IdentityHashMap<>();
    private static final Map<Integer, IBlockState> WET_STATES_BY_LEGACY_ID =
            new LinkedHashMap<>();
    private static final ThreadLocal<Boolean> INTERNAL_STATE_WRITE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private WaterloggedBlockApi() {
    }

    public interface Adapter {
        boolean matches(IBlockState state);

        boolean isWaterlogged(IBlockState state);

        IBlockState withWaterlogged(IBlockState state, boolean waterlogged);
    }

    public interface FluidMixRule {
        boolean matches(Fluid source, Fluid target);

        IBlockState getResult(World world, BlockPos targetPos, BlockPos sourcePos,
                               EnumFacing side, IBlockState sourceState, IBlockState targetState);
    }

    public static void registerAdapter(Adapter adapter) {
        WaterloggedPlantFluid.registerAdapter(adapter);
    }

    public static void unregisterAdapter(Adapter adapter) {
        WaterloggedPlantFluid.unregisterAdapter(adapter);
    }

    public static void registerFluidMixRule(FluidMixRule rule) {
        WaterloggedPlantFluid.registerFluidMixRule(rule);
    }

    public static void unregisterFluidMixRule(FluidMixRule rule) {
        WaterloggedPlantFluid.unregisterFluidMixRule(rule);
    }

    public static synchronized void registerRenderedBlock(Block block) {
        if (block != null && (block instanceof IWaterloggableBlock
                || WaterloggedConfig.isWaterloggingEnabled())) {
            RENDERED_BLOCKS.add(block);
        }
    }

    public static synchronized void unregisterRenderedBlock(Block block) {
        RENDERED_BLOCKS.remove(block);
    }

    public static synchronized void registerWaterColorBlock(Block block) {
        if (block != null && (block instanceof IWaterloggableBlock
                || WaterloggedConfig.isWaterloggingEnabled())) {
            WATER_COLOR_BLOCKS.add(block);
        }
    }

    public static synchronized void unregisterWaterColorBlock(Block block) {
        WATER_COLOR_BLOCKS.remove(block);
    }

    public static synchronized void registerRenderedModel(ResourceLocation model) {
        if (model != null) {
            RENDERED_MODELS.add(model);
        }
    }

    public static synchronized void unregisterRenderedModel(ResourceLocation model) {
        RENDERED_MODELS.remove(model);
    }

    public static synchronized List<Block> renderedBlocks() {
        return new ArrayList<>(RENDERED_BLOCKS);
    }

    public static synchronized List<Block> waterColorBlocks() {
        return new ArrayList<>(WATER_COLOR_BLOCKS);
    }

    public static synchronized List<Block> stateCarriers() {
        return new ArrayList<>(STATE_CARRIERS.values());
    }

    public static synchronized boolean isRenderedModel(ResourceLocation model) {
        if (model == null) {
            return false;
        }
        if (RENDERED_MODELS.contains(new ResourceLocation(
                model.getNamespace(), model.getPath()))) {
            return true;
        }
        for (Block block : RENDERED_BLOCKS) {
            ResourceLocation registryName = block.getRegistryName();
            if (registryName != null
                    && registryName.getNamespace().equals(model.getNamespace())
                    && registryName.getPath().equals(model.getPath())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWaterlogged(IBlockState state) {
        return WaterloggedPlantFluid.isWaterloggedBlock(state);
    }

    public static boolean hasUniversalWaterloggedProperty(IBlockState state) {
        if (state == null) {
            return false;
        }
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property == UNIVERSAL_WATERLOGGED) {
                return true;
            }
        }
        return false;
    }

    public static IProperty<?>[] appendUniversalWaterloggedProperty(
            Block block, IProperty<?>[] properties) {
        if (!WaterloggedConfig.isWaterloggingEnabled()
                || !supportsUniversalWaterlogging(block) || properties == null) {
            return properties;
        }
        for (IProperty<?> property : properties) {
            if ("waterlogged".equals(property.getName())) {
                return properties;
            }
        }
        IProperty<?>[] expanded = java.util.Arrays.copyOf(properties, properties.length + 1);
        expanded[properties.length] = UNIVERSAL_WATERLOGGED;
        return expanded;
    }

    public static synchronized void prepareUniversalStateCarriers(
            IForgeRegistry<Block> registry) {
        if (!WaterloggedConfig.isWaterloggingEnabled()) {
            return;
        }
        List<Block> sources = new ArrayList<>();
        for (Block block : registry.getValuesCollection()) {
            if (hasUniversalWaterloggedProperty(block.getDefaultState())
                    && requiresStateCarrier(block)) {
                sources.add(block);
            }
        }
        for (Block source : sources) {
            if (STATE_CARRIERS.containsKey(source) || source.getRegistryName() == null) {
                continue;
            }
            ResourceLocation sourceName = source.getRegistryName();
            Block carrier = new BlockWaterloggedStateCarrier();
            carrier.setRegistryName(WaterloggedMod.MODID,
                    "waterlogged_state/" + sourceName.getNamespace() + "/"
                            + sourceName.getPath());
            registry.register(carrier);
            STATE_CARRIERS.put(source, carrier);
        }
    }

    public static synchronized void initializeUniversalStateMappings() {
        WET_LEGACY_IDS.clear();
        WET_STATES_BY_LEGACY_ID.clear();
        if (!WaterloggedConfig.isWaterloggingEnabled()) {
            return;
        }
        for (Block source : net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS) {
            if (!hasUniversalWaterloggedProperty(source.getDefaultState())) {
                continue;
            }
            List<IBlockState> dryStates = persistedDryStates(source);
            List<Integer> freeMetas = freeMetadataValues(source, dryStates);
            Block aliasBlock = freeMetas.size() >= dryStates.size()
                    ? source : STATE_CARRIERS.get(source);
            if (aliasBlock == null) {
                continue;
            }
            int aliasBlockId = Block.getIdFromBlock(aliasBlock);
            for (int index = 0; index < dryStates.size(); index++) {
                IBlockState wetState = dryStates.get(index)
                        .withProperty(UNIVERSAL_WATERLOGGED, true);
                int aliasMeta = aliasBlock == source ? freeMetas.get(index) : index;
                int paletteId = aliasBlockId << 4 | aliasMeta;
                int legacyId = aliasBlockId | aliasMeta << 12;
                int sourceMeta = source.getMetaFromState(dryStates.get(index)) & 15;
                for (IBlockState variant : source.getBlockState().getValidStates()) {
                    if (variant.getValue(UNIVERSAL_WATERLOGGED)
                            && (source.getMetaFromState(variant) & 15) == sourceMeta) {
                        Block.BLOCK_STATE_IDS.put(variant, paletteId);
                        WET_LEGACY_IDS.put(variant, legacyId);
                    }
                }
                Block.BLOCK_STATE_IDS.put(wetState, paletteId);
                WET_LEGACY_IDS.put(wetState, legacyId);
                WET_STATES_BY_LEGACY_ID.put(legacyId, wetState);
            }
        }
    }

    public static synchronized int encodeUniversalStateId(int stateId, IBlockState state) {
        if (!WaterloggedConfig.isWaterloggingEnabled()) {
            return stateId;
        }
        Integer encoded = WET_LEGACY_IDS.get(state);
        return encoded == null ? stateId : encoded;
    }

    public static synchronized IBlockState decodeUniversalStateId(
            IBlockState state, int stateId) {
        if (!WaterloggedConfig.isWaterloggingEnabled()) {
            return state;
        }
        IBlockState decoded = WET_STATES_BY_LEGACY_ID.get(stateId);
        return decoded == null ? state : decoded;
    }

    /** Resolve the original host state used by vanilla block-destroy particles. */
    public static IBlockState waterloggedDestroyParticleState(Block block, int meta, int data) {
        IBlockState decoded = decodeUniversalStateId(null, data);
        if (decoded != null) {
            return particleHostState(decoded);
        }
        if (block == null) {
            return Blocks.AIR.getDefaultState();
        }
        IBlockState state;
        try {
            state = block.getStateFromMeta(meta);
        } catch (RuntimeException ignored) {
            state = block.getDefaultState();
        }
        return particleHostState(state);
    }

    /** Resolve the state passed to ParticleDigging for a destroyed block. */
    public static IBlockState waterloggedDestroyParticleState(IBlockState original, int data) {
        IBlockState decoded = decodeUniversalStateId(null, data);
        IBlockState state = decoded == null ? original : decoded;
        return particleHostState(state == null ? Blocks.AIR.getDefaultState() : state);
    }

    private static IBlockState particleHostState(IBlockState state) {
        if (isWaterlogged(state)) {
            IBlockState dry = withWaterlogged(state, false);
            if (dry != null) {
                return dry;
            }
        }
        return state;
    }

    public static Map<IProperty<?>, Comparable<?>> withoutUniversalWaterloggedProperty(
            Map<IProperty<?>, Comparable<?>> properties) {
        if (properties == null) {
            return properties;
        }
        boolean found = false;
        for (IProperty<?> property : properties.keySet()) {
            if (property == UNIVERSAL_WATERLOGGED) {
                found = true;
                break;
            }
        }
        if (!found) {
            return properties;
        }
        Map<IProperty<?>, Comparable<?>> filtered = new LinkedHashMap<>();
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : properties.entrySet()) {
            if (entry.getKey() != UNIVERSAL_WATERLOGGED) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private static boolean supportsUniversalWaterlogging(Block block) {
        if (block instanceof BlockWaterloggedStateCarrier) {
            return false;
        }
        if (block instanceof BlockStairs || block instanceof BlockFence
                || block instanceof BlockWall || block instanceof BlockPane
                || block instanceof BlockTrapDoor || block instanceof BlockLadder
                || block instanceof BlockSign || block instanceof BlockChest
                || block instanceof BlockLeaves
                || block instanceof net.minecraft.block.BlockRailBase
                || block instanceof net.minecraft.block.BlockEnderChest
) {
            return true;
        }
        return block instanceof BlockSlab && !((BlockSlab) block).isDouble();
    }

    private static boolean requiresStateCarrier(Block block) {
        List<IBlockState> dryStates = persistedDryStates(block);
        return freeMetadataValues(block, dryStates).size() < dryStates.size();
    }

    private static List<IBlockState> persistedDryStates(Block block) {
        List<IBlockState> states = new ArrayList<>();
        for (int meta = 0; meta < 16; meta++) {
            IBlockState state;
            try {
                state = block.getStateFromMeta(meta);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (hasUniversalWaterloggedProperty(state)) {
                state = state.withProperty(UNIVERSAL_WATERLOGGED, false);
            }
            if (!states.contains(state)) {
                states.add(state);
            }
        }
        return states;
    }

    private static List<Integer> freeMetadataValues(Block block, List<IBlockState> dryStates) {
        boolean[] occupied = new boolean[16];
        for (IBlockState state : dryStates) {
            occupied[block.getMetaFromState(state) & 15] = true;
        }
        List<Integer> values = new ArrayList<>();
        for (int meta = 0; meta < occupied.length; meta++) {
            if (!occupied[meta]) {
                values.add(meta);
            }
        }
        return values;
    }

    public static boolean canBeWaterlogged(IBlockState state) {
        return WaterloggedPlantFluid.withWaterlogged(state, true) != null;
    }

    /** Match vanilla's liquid-level reads while allowing a host state without LEVEL. */
    public static Comparable<?> getLiquidLevelValue(IBlockState state, IProperty<?> property) {
        if (state == null || property == null) {
            return Integer.valueOf(0);
        }
        if (property == BlockLiquid.LEVEL
                && !state.getPropertyKeys().contains(BlockLiquid.LEVEL)) {
            return Integer.valueOf(0);
        }
        return state.getValue(property);
    }

    /** Same calculation as BlockLiquid#getDepth, usable from a mixin targeting a subclass. */
    public static int vanillaLiquidDepth(Block liquid, IBlockState state) {
        if (liquid == null || state == null
                || state.getMaterial() != liquid.getDefaultState().getMaterial()) {
            return -1;
        }
        return state.getPropertyKeys().contains(BlockLiquid.LEVEL)
                ? state.getValue(BlockLiquid.LEVEL) : -1;
    }

    public static boolean isWaterloggedStateForLiquid(IBlockState state) {
        return WaterloggedPlantFluid.isWaterlogged(state);
    }

    /** State view used by BlockFluidRenderer when sampling a waterlogged cell. */
    public static IBlockState fluidRenderState(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return isWaterlogged(world, pos) ? getExtendedState(state, world, pos) : state;
    }

    /** Material view used by BlockFluidRenderer for contained fluid states. */
    public static Material renderedFluidMaterial(IBlockState state) {
        if (!isWaterlogged(state)) {
            return state.getMaterial();
        }
        Fluid fluid = WaterloggedBlockApi.getContainedFluidFromState(state);
        IBlockState fluidState = sourceState(fluid == null ? FluidRegistry.WATER : fluid);
        return fluidState.getMaterial();
    }

    /**
     * Match the upstream replacement-material hook used by Block and fences.
     * Custom waterloggable states may expose a different material while wet;
     * replacement and connection checks must inspect the dry host state.
     */
    public static Material blockReplacementMaterial(IBlockState state) {
        if (state == null) {
            return Material.AIR;
        }
        IBlockState dry = isWaterlogged(state) ? withWaterlogged(state, false) : null;
        return dry == null ? state.getMaterial() : dry.getMaterial();
    }

    private static Fluid getContainedFluidFromState(IBlockState state) {
        if (state instanceof net.minecraftforge.common.property.IExtendedBlockState) {
            String name = ((net.minecraftforge.common.property.IExtendedBlockState) state)
                    .getValue(WaterloggedPlantFluid.CONTAINED_FLUID);
            if (name != null && !name.isEmpty()) {
                return FluidRegistry.getFluid(name);
            }
        }
        return FluidRegistry.WATER;
    }

    /** Make ordinary liquid faces share the face-visibility rule with contained fluid. */
    public static boolean fluidSideVisible(boolean original, IBlockState state,
            IBlockAccess world, BlockPos pos, EnumFacing side) {
        Fluid fluid = getFluidForBlock(state.getBlock());
        return original && !(fluid != null && containsFluid(world, pos.offset(side), fluid));
    }

    /** Return the vanilla depth represented by a waterlogged state for this liquid. */
    public static int waterloggedLiquidDepth(Block liquid) {
        return liquid != null && liquid.getDefaultState().getMaterial() == Material.WATER ? 0 : -1;
    }

    /** Resolve a neighbouring waterlogged cell's stored flow depth for a liquid update. */
    public static int waterloggedLiquidDepth(int depth, Block liquid, IBlockAccess world,
            BlockPos pos) {
        if (liquid == null || world == null || pos == null
                || !isWaterlogged(world, pos)) {
            return depth;
        }
        Fluid liquidFluid = getFluidForBlock(liquid);
        return liquidFluid != null && getContainedFluid(world, pos) == liquidFluid
                ? FFDStoredFluidStates.level(world, pos) : -1;
    }

    public static boolean isContainedFluidSource(Block liquid, IBlockAccess world, BlockPos pos) {
        if (liquid == null || world == null || pos == null
                || !isWaterlogged(world, pos)) {
            return false;
        }
        Fluid liquidFluid = getFluidForBlock(liquid);
        return liquidFluid != null && getSourceFluid(world, pos) == liquidFluid;
    }

    public static boolean hasContainedFluidAlongDensity(Block liquid, IBlockAccess world,
            BlockPos pos, int densityDirection) {
        if (liquid == null || world == null || pos == null) {
            return false;
        }
        BlockPos neighbor = pos.down(densityDirection);
        return isWaterlogged(world, neighbor)
                && getContainedFluid(world, neighbor) == getFluidForBlock(liquid);
    }

    public static boolean protectContainedHost(IBlockAccess world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return isWaterlogged(world, pos)
                || WaterloggedConfig.isAdditionalWaterloggingBlock(world,
                        world.getBlockState(pos).getBlock());
    }

    public static boolean isWaterloggedFlowOccluded(Block liquid, World world,
            BlockPos source, BlockPos neighbor) {
        if (liquid == null || world == null || source == null || neighbor == null) {
            return false;
        }
        Fluid liquidFluid = getFluidForBlock(liquid);
        if (liquidFluid == null || !isWaterlogged(world, neighbor)
                || getContainedFluid(world, neighbor) != liquidFluid) {
            return false;
        }
        EnumFacing side = EnumFacing.getFacingFromVector(
                neighbor.getX() - source.getX(),
                neighbor.getY() - source.getY(),
                neighbor.getZ() - source.getZ());
        return !WaterloggedPlantFluid.canPassThrough(world, source, neighbor, side);
    }

    public static int waterloggedDepthAbove(int depth, Block liquid, World world, BlockPos pos) {
        if (world == null || pos == null) {
            return depth;
        }
        depth = waterloggedLiquidDepth(depth, liquid, world, pos.up());
        return depth >= 0 && isWaterloggedFlowOccluded(liquid, world, pos, pos.up())
                ? -1 : depth;
    }

    public static boolean isWaterlogged(IBlockAccess world, BlockPos pos) {
        return isWaterlogged(world.getBlockState(pos)) || FFDStoredFluidStates.has(world, pos);
    }

    public static boolean containsWater(IBlockState state) {
        return WaterloggedPlantFluid.containsWater(state);
    }

    public static boolean containsWater(IBlockAccess world, BlockPos pos) {
        return getContainedFluid(world, pos) == FluidRegistry.WATER;
    }

    public static boolean isWaterSource(IBlockState state) {
        return WaterloggedPlantFluid.isSourceWater(state);
    }

    public static boolean isWaterSource(IBlockAccess world, BlockPos pos) {
        return getSourceFluid(world, pos) == FluidRegistry.WATER;
    }

    public static IBlockState withWaterlogged(IBlockState state, boolean waterlogged) {
        return WaterloggedPlantFluid.withWaterlogged(state, waterlogged);
    }

    public static boolean setContainedFluid(World world, BlockPos pos, IBlockState state,
                                            Fluid fluid, int flags) {
        if (!WaterloggedConfig.isFluidAllowed(world, fluid) || isWaterlogged(world, pos)) {
            return false;
        }
        IBlockState wetState = withWaterlogged(state, true);
        if (wetState == null && WaterloggedConfig.isAdditionalWaterloggingBlock(world, state.getBlock())) {
            FFDStoredFluidStates.set(world, pos, sourceState(fluid));
            world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
            world.scheduleUpdate(pos, state.getBlock(), fluidTickRate(world, fluid));
            return true;
        }
        if (wetState == null) {
            return false;
        }
        if (!world.setBlockState(pos, wetState, flags)) {
            return false;
        }
        recordContainedFluid(world, pos, fluid);
        world.scheduleUpdate(pos, wetState.getBlock(), fluidTickRate(world, fluid));
        return true;
    }

    public static boolean isInternalStateWrite() {
        return Boolean.TRUE.equals(INTERNAL_STATE_WRITE.get());
    }

    public static boolean setBlockStateWithoutWaterloggingHook(
            World world, BlockPos pos, IBlockState state, int flags) {
        boolean previous = isInternalStateWrite();
        INTERNAL_STATE_WRITE.set(Boolean.TRUE);
        try {
            return world.setBlockState(pos, state, flags);
        } finally {
            if (previous) {
                INTERNAL_STATE_WRITE.set(Boolean.TRUE);
            } else {
                INTERNAL_STATE_WRITE.remove();
            }
        }
    }

    public static boolean setDryState(World world, BlockPos pos, IBlockState state, int flags) {
        if (!isWaterlogged(world, pos)) {
            return false;
        }
        IBlockState dryState = withWaterlogged(state, false);
        boolean changed;
        if (dryState != null && !dryState.equals(state)) {
            changed = setBlockStateWithoutWaterloggingHook(world, pos, dryState, flags);
        } else if (FFDStoredFluidStates.has(world, pos)) {
            changed = true;
        } else {
            return false;
        }
        if (changed) {
            FFDStoredFluidStates.remove(world, pos);
            recordContainedFluid(world, pos, null);
            if (!world.isRemote) {
                IBlockState current = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, current, current, 3);
                world.notifyNeighborsOfStateChange(pos, current.getBlock(), false);
            }
        }
        return changed;
    }

    /** Match the upstream removal path: harvest the host first, then replace
     * a waterlogged host with its contained fluid instead of air. */
    public static boolean removeWaterloggedBlockByPlayer(World world, BlockPos pos,
                                                         IBlockState removedState) {
        return setBlockStateAfterRemoval(world, pos, Blocks.AIR.getDefaultState(),
                world.isRemote ? 11 : 3, removedState);
    }

    public static boolean setBlockStateAfterRemoval(World world, BlockPos pos,
                                                     IBlockState replacement, int flags,
                                                     IBlockState removedState) {
        if (world == null || pos == null) {
            return false;
        }
        boolean wasWaterlogged = isWaterlogged(world, pos);
        IBlockState next = wasWaterlogged
                ? getReplacementState(world, pos, removedState) : replacement;
        if (next == null) {
            return false;
        }
        // A static vanilla source has no pending update after its host is
        // harvested. Restore the dynamic form so it can spread on the next
        // fluid tick, just as a newly placed source does.
        if (wasWaterlogged && next.getBlock() == Blocks.WATER) {
            next = Blocks.FLOWING_WATER.getDefaultState()
                    .withProperty(BlockLiquid.LEVEL, next.getValue(BlockLiquid.LEVEL));
        } else if (wasWaterlogged && next.getBlock() == Blocks.LAVA) {
            next = Blocks.FLOWING_LAVA.getDefaultState()
                    .withProperty(BlockLiquid.LEVEL, next.getValue(BlockLiquid.LEVEL));
        }
        boolean changed = setBlockStateWithoutWaterloggingHook(world, pos, next, flags);
        if (changed) {
            recordContainedFluid(world, pos, null);
            Fluid restored = getFluidForBlock(next.getBlock());
            if (!world.isRemote && restored != null) {
                world.scheduleUpdate(pos, next.getBlock(), 1);
                world.notifyNeighborsOfStateChange(pos, next.getBlock(), false);
            }
            if (world.isRemote) {
                world.markBlockRangeForRenderUpdate(pos.add(-1, -1, -1),
                        pos.add(1, 1, 1));
            }
        }
        return changed;
    }

    /**
     * Apply the upstream removal rule used by ItemShears.  A sheared
     * waterlogged host is replaced by its contained fluid state, while a dry
     * host keeps vanilla's requested replacement state.
     */
    public static boolean setBlockStateAfterShearing(World world, BlockPos pos,
                                                       IBlockState replacement, int flags) {
        if (world == null || pos == null) {
            return false;
        }

        IBlockState removed = world.getBlockState(pos);
        return setBlockStateAfterRemoval(world, pos, replacement, flags, removed);
    }

    public static void runBlockTick(Block block, World world, BlockPos pos,
                                    IBlockState state, Random random) {
        if (!world.isRemote && getFluidForBlock(block) != null) {
            boolean mixed = WaterloggedPlantFluid.tryMixFluidNeighbors(world, pos, state);
            if (mixed && getFluidForBlock(world.getBlockState(pos).getBlock()) == null) {
                return;
            }
        }
        block.updateTick(world, pos, state, random);
        IBlockState current = world.getBlockState(pos);
        if (isWaterlogged(world, pos)) {
            updateTick(world, pos, current);
        } else {
            WaterloggedPlantFluid.tryFormWaterSource(world, pos, current);
        }
    }

    public static void recordContainedFluid(World world, BlockPos pos, Fluid fluid) {
        FFDStoredFluidStates.remove(world, pos);
        FFDFluidloggedData.set(world, pos, fluid);
    }

    public static Fluid getContainedFluid(IBlockAccess world, BlockPos pos) {
        IBlockState storedState = FFDStoredFluidStates.get(world, pos);
        if (storedState != null) {
            return getFluidForBlock(storedState.getBlock());
        }
        IBlockState state = world.getBlockState(pos);
        if (isWaterlogged(state)) {
            Fluid stored = FFDFluidloggedData.get(world, pos);
            return stored == null ? FluidRegistry.WATER : stored;
        }
        return getFluidForBlock(state.getBlock());
    }

    public static boolean containsFluid(IBlockAccess world, BlockPos pos, Fluid fluid) {
        return fluid != null && getContainedFluid(world, pos) == fluid;
    }

    public static boolean isFluidSource(IBlockAccess world, BlockPos pos) {
        return getSourceFluid(world, pos) != null;
    }

    public static Fluid getSourceFluid(IBlockAccess world, BlockPos pos) {
        IBlockState stored = FFDStoredFluidStates.get(world, pos);
        if (stored != null) {
            return WaterloggedPlantFluid.isSourceFluid(stored)
                    ? getFluidForBlock(stored.getBlock()) : null;
        }
        IBlockState state = world.getBlockState(pos);
        if (isWaterlogged(state)) {
            return getContainedFluid(world, pos);
        }
        Fluid fluid = getFluidForBlock(state.getBlock());
        return fluid != null && WaterloggedPlantFluid.isSourceFluid(state) ? fluid : null;
    }

    public static IBlockState getFluidState(IBlockState state) {
        return WaterloggedPlantFluid.getFluidState(state);
    }

    public static IBlockState getFluidState(IBlockAccess world, BlockPos pos) {
        IBlockState stored = FFDStoredFluidStates.get(world, pos);
        if (stored != null) {
            return stored;
        }
        IBlockState state = world.getBlockState(pos);
        if (isWaterlogged(state)) {
            return sourceState(getContainedFluid(world, pos));
        }
        if (getFluidForBlock(state.getBlock()) != null) return state;
        return getFluidState(state);
    }

    public static IBlockState getReplacementState(IBlockState state) {
        return isWaterlogged(state) ? Blocks.WATER.getDefaultState()
                : Blocks.AIR.getDefaultState();
    }

    public static IBlockState getReplacementState(IBlockAccess world, BlockPos pos,
                                                   IBlockState state) {
        return isWaterlogged(world, pos) ? getFluidState(world, pos)
                : Blocks.AIR.getDefaultState();
    }

    public static boolean restoreFluid(World world, BlockPos pos, IBlockState state, int flags) {
        IBlockState replacement = getReplacementState(world, pos, state);
        boolean changed = world.setBlockState(pos, replacement, flags);
        if (changed) {
            FFDFluidloggedData.set(world, pos, null);
        }
        return changed;
    }

    public static Vec3d modifyAcceleration(World world, BlockPos pos, Entity entity, Vec3d motion) {
        IBlockState state = world.getBlockState(pos);
        if (!isWaterlogged(world, pos)) {
            return containsWater(state)
                    ? Blocks.WATER.modifyAcceleration(world, pos, entity, motion) : motion;
        }
        int currentDepth = getRenderedWaterDepth(world, pos);
        Vec3d flow = Vec3d.ZERO;
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos target = pos.offset(direction);
            IBlockState targetState = world.getBlockState(target);
            int targetDepth = getRenderedWaterDepth(world, target);
            if (targetDepth >= 0) {
                flow = addFlow(flow, direction, targetDepth - currentDepth);
            } else if (!targetState.getMaterial().blocksMovement()) {
                int lowerDepth = getRenderedWaterDepth(world, target.down());
                if (lowerDepth >= 0) {
                    flow = addFlow(flow, direction, lowerDepth - (currentDepth - 8));
                }
            }
        }
        return motion.add(flow.normalize());
    }

    private static int getRenderedWaterDepth(IBlockState state) {
        if (isWaterlogged(state)) {
            return 0;
        }
        if (state == null || state.getMaterial() != net.minecraft.block.material.Material.WATER
                || !state.getPropertyKeys().contains(BlockLiquid.LEVEL)) {
            return -1;
        }
        int depth = state.getValue(BlockLiquid.LEVEL);
        return depth >= 8 ? 0 : depth;
    }

    /** Resolve a rendered liquid depth from the world, including stored flow
     * levels attached to a waterlogged host. */
    private static int getRenderedWaterDepth(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return -1;
        }
        IBlockState stored = FFDStoredFluidStates.get(world, pos);
        if (stored != null) {
            return getRenderedWaterDepth(stored);
        }
        IBlockState state = world.getBlockState(pos);
        if (isWaterlogged(world, pos)
                && getContainedFluid(world, pos) == FluidRegistry.WATER) {
            return 0;
        }
        return getRenderedWaterDepth(state);
    }

    private static Vec3d addFlow(Vec3d flow, EnumFacing direction, int depthDifference) {
        return flow.add(new Vec3d(direction.getXOffset() * depthDifference,
                direction.getYOffset() * depthDifference,
                direction.getZOffset() * depthDifference));
    }

    public static IUnlistedProperty<?>[] extendedProperties() {
        return WaterloggedPlantFluid.extendedProperties();
    }

    public static IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return WaterloggedPlantFluid.getExtendedState(state, world, pos);
    }

    public static void onBlockAdded(World world, BlockPos pos, Block block) {
        WaterloggedPlantFluid.onBlockAdded(world, pos, block);
    }

    public static void onNeighborChanged(World world, BlockPos pos, Block block) {
        WaterloggedPlantFluid.onNeighborChanged(world, pos, block);
    }

    public static void updateTick(World world, BlockPos pos, IBlockState state) {
        WaterloggedPlantFluid.updateTick(world, pos, state);
    }

    public static IBlockState sourceState(Fluid fluid) {
        if (fluid == null || fluid.getBlock() == null) {
            return Blocks.AIR.getDefaultState();
        }
        try {
            return fluid.getBlock().getStateFromMeta(0);
        } catch (RuntimeException ignored) {
            return fluid.getBlock().getDefaultState();
        }
    }

    public static Fluid getFluidForBlock(Block block) {
        if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
            return FluidRegistry.WATER;
        }
        if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) {
            return FluidRegistry.LAVA;
        }
        return FluidRegistry.lookupFluidForBlock(block);
    }

    public static int fluidTickRate(World world, Fluid fluid) {
        if (fluid == null || fluid.getBlock() == null) {
            return 5;
        }
        return Math.max(1, fluid.getBlock().tickRate(world));
    }

    private static final class UniversalWaterloggedProperty extends PropertyBool {
        private UniversalWaterloggedProperty() {
            super("waterlogged");
        }

        @Override
        public Collection<Boolean> getAllowedValues() {
            return Arrays.asList(Boolean.FALSE, Boolean.TRUE);
        }
    }
}
