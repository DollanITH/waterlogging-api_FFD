package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.properties.IProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockStateContainer.class)
public abstract class BlockStateContainerMixin {
    @ModifyVariable(method = "<init>(Lnet/minecraft/block/Block;[Lnet/minecraft/block/properties/IProperty;)V",
            at = @At("HEAD"), argsOnly = true, index = 2)
    private static IProperty<?>[] waterlogged$properties(IProperty<?>[] properties, Block block) {
        return WaterloggedBlockApi.appendUniversalWaterloggedProperty(block, properties);
    }
}
