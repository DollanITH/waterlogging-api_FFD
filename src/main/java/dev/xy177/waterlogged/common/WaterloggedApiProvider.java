package dev.xy177.waterlogged.common;

import dev.xy177.waterlogged.api.WaterloggedApi;
import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.block.WaterloggedPlantFluid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.Fluid;
import java.util.IdentityHashMap;
import java.util.Map;

/** Connects the stable API module to Waterlogged's internal implementation. */
public final class WaterloggedApiProvider implements WaterloggedApi.Provider {
    private final Map<WaterloggedApi.Adapter, WaterloggedBlockApi.Adapter> adapters =
            new IdentityHashMap<>();
    private final Map<WaterloggedApi.FluidMixRule, WaterloggedBlockApi.FluidMixRule> rules =
            new IdentityHashMap<>();

    @Override public void registerAdapter(WaterloggedApi.Adapter adapter) {
        if (adapter == null) return;
        synchronized (adapters) {
            if (adapters.containsKey(adapter)) return;
            WaterloggedBlockApi.Adapter bridge;
            if (adapter instanceof WaterloggedBlockApi.Adapter) {
                bridge = (WaterloggedBlockApi.Adapter) adapter;
            } else {
                bridge = new WaterloggedBlockApi.Adapter() {
                @Override public boolean matches(IBlockState state) { return adapter.matches(state); }
                @Override public boolean isWaterlogged(IBlockState state) {
                    return adapter.isWaterlogged(state);
                }
                @Override public IBlockState withWaterlogged(IBlockState state, boolean wet) {
                    return adapter.withWaterlogged(state, wet);
                }
                };
            }
            adapters.put(adapter, bridge);
            WaterloggedBlockApi.registerAdapter(bridge);
        }
    }

    @Override public void unregisterAdapter(WaterloggedApi.Adapter adapter) {
        synchronized (adapters) {
            WaterloggedBlockApi.Adapter bridge = adapters.remove(adapter);
            if (bridge != null) WaterloggedBlockApi.unregisterAdapter(bridge);
        }
    }

    @Override public void registerFluidMixRule(WaterloggedApi.FluidMixRule rule) {
        if (rule == null) return;
        synchronized (rules) {
            if (rules.containsKey(rule)) return;
            WaterloggedBlockApi.FluidMixRule bridge = new WaterloggedBlockApi.FluidMixRule() {
                @Override public boolean matches(Fluid source, Fluid target) {
                    return rule.matches(source, target);
                }
                @Override public IBlockState getResult(net.minecraft.world.World world,
                        BlockPos targetPos, BlockPos sourcePos, net.minecraft.util.EnumFacing side,
                        IBlockState sourceState, IBlockState targetState) {
                    return rule.getResult(world, targetPos, sourcePos, side, sourceState, targetState);
                }
            };
            rules.put(rule, bridge);
            WaterloggedBlockApi.registerFluidMixRule(bridge);
        }
    }

    @Override public void unregisterFluidMixRule(WaterloggedApi.FluidMixRule rule) {
        synchronized (rules) {
            WaterloggedBlockApi.FluidMixRule bridge = rules.remove(rule);
            if (bridge != null) WaterloggedBlockApi.unregisterFluidMixRule(bridge);
        }
    }
    @Override public boolean isWaterlogged(IBlockState state) {
        return WaterloggedBlockApi.isWaterlogged(state);
    }
    @Override public boolean canBeWaterlogged(IBlockState state) {
        return WaterloggedBlockApi.canBeWaterlogged(state);
    }
    @Override public IBlockState withWaterlogged(IBlockState state, boolean wet) {
        return WaterloggedBlockApi.withWaterlogged(state, wet);
    }
    @Override public boolean isWaterlogged(IBlockAccess world, BlockPos pos) {
        return WaterloggedBlockApi.isWaterlogged(world, pos);
    }
    @Override public Fluid getContainedFluid(IBlockAccess world, BlockPos pos) {
        return WaterloggedBlockApi.getContainedFluid(world, pos);
    }
    @Override public boolean containsWater(IBlockAccess world, BlockPos pos) {
        return WaterloggedBlockApi.containsWater(world, pos);
    }
}
