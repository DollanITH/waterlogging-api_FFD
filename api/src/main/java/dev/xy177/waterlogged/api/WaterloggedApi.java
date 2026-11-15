package dev.xy177.waterlogged.api;

import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Stable third-party integration surface. Runtime implementation is supplied by Waterlogged.
 *
 * <p>API 1.1 adds {@link #UNIVERSAL_WATERLOGGED}: a block can append that property in its own
 * {@code createBlockState()} instead of waiting for the mod to inject it through a mixin.
 */
public final class WaterloggedApi {
    /** Capability version. 1.1 adds the universal property accessor and the per-state support check. */
    public static final String API_VERSION = "1.1.0";

    /**
     * The single property instance the runtime uses for waterlogging it adds to a block's state container.
     * Integrations that build their own container, or that own a {@code waterlogged} property, must use this
     * same instance so state identity and every runtime check agree.
     */
    public static final PropertyBool UNIVERSAL_WATERLOGGED = PropertyBool.create("waterlogged");

    /** Accessor for {@link #UNIVERSAL_WATERLOGGED}; kept as a method so the field can be looked up after init. */
    public static PropertyBool universalWaterloggedProperty() {
        return UNIVERSAL_WATERLOGGED;
    }

    /**
     * Block classes the runtime should give the universal waterlogged property to, beyond the built-in list.
     *
     * <p>State containers are created while the block object is constructed, so this must run before that
     * block is instantiated. A static initializer placed above the block field in the integration's mod
     * class is enough:
     * <pre>{@code
     * static { WaterloggedApi.registerUniversalWaterloggableBlock(MyBlock.class); }
     * public static final Block MY_BLOCK = new MyBlock();
     * }</pre>
     */
    private static final Set<Class<?>> EXPLICIT_UNIVERSAL_CLASSES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public static void registerUniversalWaterloggableBlock(Class<? extends Block> blockClass) {
        if (blockClass != null) {
            EXPLICIT_UNIVERSAL_CLASSES.add(blockClass);
        }
    }

    public static void unregisterUniversalWaterloggableBlock(Class<? extends Block> blockClass) {
        EXPLICIT_UNIVERSAL_CLASSES.remove(blockClass);
    }

    public static boolean isUniversalWaterloggableBlock(Class<?> blockClass) {
        return blockClass != null && EXPLICIT_UNIVERSAL_CLASSES.contains(blockClass);
    }

    private static volatile Provider provider;
    private static final List<Adapter> PENDING_ADAPTERS = new ArrayList<>();
    private static final List<FluidMixRule> PENDING_RULES = new ArrayList<>();

    private WaterloggedApi() { }

    /** Installed by the Waterlogged runtime during mod initialization. */
    public interface Provider {
        void registerAdapter(Adapter adapter);
        void unregisterAdapter(Adapter adapter);
        void registerFluidMixRule(FluidMixRule rule);
        void unregisterFluidMixRule(FluidMixRule rule);
        boolean isWaterlogged(IBlockState state);
        boolean canBeWaterlogged(IBlockState state);
        IBlockState withWaterlogged(IBlockState state, boolean waterlogged);
        boolean isWaterlogged(IBlockAccess world, BlockPos pos);
        Fluid getContainedFluid(IBlockAccess world, BlockPos pos);
        boolean containsWater(IBlockAccess world, BlockPos pos);
    }

    public static void installProvider(Provider implementation) {
        if (implementation == null) throw new IllegalArgumentException("implementation");
        synchronized (WaterloggedApi.class) {
            provider = implementation;
            for (Adapter adapter : PENDING_ADAPTERS) implementation.registerAdapter(adapter);
            PENDING_ADAPTERS.clear();
            for (FluidMixRule rule : PENDING_RULES) implementation.registerFluidMixRule(rule);
            PENDING_RULES.clear();
        }
    }

    private static Provider provider() {
        Provider current = provider;
        if (current == null) throw new IllegalStateException(
                "Waterlogged runtime is not installed; check the mod dependency");
        return current;
    }

    /** Adapter for blocks whose waterlogged state is stored in a custom property. */
    public interface Adapter {
        boolean matches(IBlockState state);
        boolean isWaterlogged(IBlockState state);
        IBlockState withWaterlogged(IBlockState state, boolean waterlogged);
    }

    /** Optional fluid interaction rule, evaluated by the Waterlogged fluid implementation. */
    public interface FluidMixRule {
        boolean matches(Fluid source, Fluid target);
        IBlockState getResult(World world, BlockPos targetPos, BlockPos sourcePos,
                EnumFacing side, IBlockState sourceState, IBlockState targetState);
    }

    /** Register an adapter with the Waterlogged runtime. */
    public static void registerAdapter(Adapter adapter) {
        if (adapter == null) return;
        synchronized (WaterloggedApi.class) {
            if (provider == null) {
                if (!PENDING_ADAPTERS.contains(adapter)) PENDING_ADAPTERS.add(adapter);
            } else provider.registerAdapter(adapter);
        }
    }

    public static void unregisterAdapter(Adapter adapter) {
        synchronized (WaterloggedApi.class) {
            if (provider == null) PENDING_ADAPTERS.remove(adapter);
            else provider.unregisterAdapter(adapter);
        }
    }

    public static void registerFluidMixRule(FluidMixRule rule) {
        if (rule == null) return;
        synchronized (WaterloggedApi.class) {
            if (provider == null) {
                if (!PENDING_RULES.contains(rule)) PENDING_RULES.add(rule);
            } else provider.registerFluidMixRule(rule);
        }
    }

    public static void unregisterFluidMixRule(FluidMixRule rule) {
        synchronized (WaterloggedApi.class) {
            if (provider == null) PENDING_RULES.remove(rule);
            else provider.unregisterFluidMixRule(rule);
        }
    }

    public static boolean isWaterlogged(IBlockState state) {
        return provider().isWaterlogged(state);
    }

    public static boolean canBeWaterlogged(IBlockState state) {
        return provider().canBeWaterlogged(state);
    }

    public static IBlockState withWaterlogged(IBlockState state, boolean waterlogged) {
        return provider().withWaterlogged(state, waterlogged);
    }

    public static boolean isWaterlogged(IBlockAccess world, BlockPos pos) {
        return provider().isWaterlogged(world, pos);
    }

    public static Fluid getContainedFluid(IBlockAccess world, BlockPos pos) {
        return provider().getContainedFluid(world, pos);
    }

    public static boolean containsWater(IBlockAccess world, BlockPos pos) {
        return provider().containsWater(world, pos);
    }
}
