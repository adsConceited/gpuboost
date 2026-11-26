package dev.vconceited.gpuboost.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

import dev.vconceited.gpuboost.client.optimize.EntityCulling;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
	@WrapMethod(method = "shouldRender")
	private boolean gpuboost$distanceCull(T entity, Frustum culler, double camX, double camY, double camZ, float partialTicks,
	                                      Operation<Boolean> original) {
		if (EntityCulling.shouldSkip(entity, camX, camY, camZ)) {
			return false;
		}

		return original.call(entity, culler, camX, camY, camZ, partialTicks);
	}
}
