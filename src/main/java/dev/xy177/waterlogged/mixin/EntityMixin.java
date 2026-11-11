package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow public World world;
    @Shadow public double posX;
    @Shadow public double posY;
    @Shadow public double posZ;
    @Shadow public abstract float getEyeHeight();

    @Inject(method = "isInsideOfMaterial", at = @At("RETURN"), cancellable = true)
    private void waterlogged$eyeInContainedWater(Material material,
            CallbackInfoReturnable<Boolean> result) {
        if (material != Material.WATER || result.getReturnValue()) return;
        double eyeY = posY + getEyeHeight();
        BlockPos pos = new BlockPos(posX, eyeY, posZ);
        if (WaterloggedBlockApi.isWaterlogged(world, pos)
                && WaterloggedBlockApi.getContainedFluid(world, pos) == FluidRegistry.WATER
                && eyeY < pos.getY() + 8.0D / 9.0D) {
            result.setReturnValue(true);
        }
    }
}
