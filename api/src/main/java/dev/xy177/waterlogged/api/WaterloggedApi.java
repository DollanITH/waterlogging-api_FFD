package dev.xy177.waterlogged.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import java.util.ArrayList;
import java.util.List;

/** Stable third-party integration surface. Runtime implementation is supplied by Waterlogged. */
public final class WaterloggedApi {
    public static final String API_VERSION = "1.0.0";
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
