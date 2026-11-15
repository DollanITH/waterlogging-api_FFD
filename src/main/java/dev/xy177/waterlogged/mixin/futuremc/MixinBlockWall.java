package dev.xy177.waterlogged.mixin.futuremc;

import dev.xy177.waterlogged.api.IWaterloggableBlock;
import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import thedarkcolour.futuremc.block.villagepillage.BlockWall;

@Mixin(BlockWall.class)
public class MixinBlockWall implements IWaterloggableBlock {

    @Override
    public boolean isWaterloggedState(IBlockState state) {
        return WaterloggedBlockApi.hasUniversalWaterloggedProperty(state)
                && state.getValue(WaterloggedBlockApi.UNIVERSAL_WATERLOGGED);
    }

    @Override
    public IBlockState setWaterloggedState(IBlockState state, boolean waterlogged) {
        if (!WaterloggedBlockApi.hasUniversalWaterloggedProperty(state)) {
            return null;
        }
        return state.withProperty(WaterloggedBlockApi.UNIVERSAL_WATERLOGGED, waterlogged);
    }
}
