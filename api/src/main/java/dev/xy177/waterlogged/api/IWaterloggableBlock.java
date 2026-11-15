package dev.xy177.waterlogged.api;

import net.minecraft.block.state.IBlockState;

/** State contract for blocks that own their waterlogged property. */
public interface IWaterloggableBlock {
    boolean isWaterloggedState(IBlockState state);

    /**
     * Returns the same state with the waterlogged flag applied, or {@code null} when this state cannot
     * represent waterlogging at all. Callers must treat {@code null} as "unsupported" rather than as
     * "already dry".
     */
    IBlockState setWaterloggedState(IBlockState state, boolean waterlogged);

    /**
     * Whether this block can hold water in the given state. Implementations that support only a subset of
     * their states (for example a double slab) override this instead of making callers guess from a null
     * return value.
     */
    default boolean supportsWaterlogging(IBlockState state) {
        return true;
    }
}
