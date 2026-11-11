package dev.xy177.waterlogged.mixin;

import java.util.Map;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import net.minecraft.block.properties.IProperty;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StateMapperBase.class)
public abstract class StateMapperMixin {
    @ModifyVariable(method = "getPropertyString", at = @At("HEAD"), argsOnly = true, index = 1)
    private Map<IProperty<?>, Comparable<?>> waterlogged$ignoreRenderProperty(
            Map<IProperty<?>, Comparable<?>> properties) {
        return WaterloggedBlockApi.withoutUniversalWaterloggedProperty(properties);
    }
}
