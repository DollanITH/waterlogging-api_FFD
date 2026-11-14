package dev.xy177.waterlogged.api;

import net.minecraft.block.state.IBlockState;

/** State contract for blocks that own their waterlogged property. */
public interface IWaterloggableBlock {
    boolean isWaterloggedState(IBlockState state);

    IBlockState setWaterloggedState(IBlockState state, boolean waterlogged);
}
