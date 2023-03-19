/*
 * This file is part of Canvas Renderer and is licensed to the project under
 * terms that are compatible with the GNU Lesser General Public License.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership and licensing.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package grondag.canvas.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import grondag.canvas.apiimpl.rendercontext.CanvasEntityBlockRenderContext;

@Mixin(BlockRenderDispatcher.class)
public abstract class MixinBlockRenderDispatcher {
	@Shadow private ModelBlockRenderer modelRenderer;
	@Shadow private BlockEntityWithoutLevelRenderer blockEntityRenderer;

	/**
	 * @author grondag
	 * @reason performance; less bad than inject and cancel at head
	 */
	@Overwrite
	public void renderSingleBlock(BlockState state, PoseStack poseStack, MultiBufferSource consumers, int light, int overlay) {
		final RenderShape blockRenderType = state.getRenderShape();

		if (blockRenderType != RenderShape.INVISIBLE) {
			switch (blockRenderType) {
				case MODEL:
					final BakedModel bakedModel = ((BlockRenderDispatcher) (Object) this).getBlockModel(state);
					CanvasEntityBlockRenderContext.get().render(modelRenderer, bakedModel, state, poseStack, consumers, overlay, light);
					break;
				case ENTITYBLOCK_ANIMATED:
					blockEntityRenderer.renderByItem(new ItemStack(state.getBlock()), ItemDisplayContext.NONE, poseStack, consumers, light, overlay);
					break;
				default:
					break;
			}
		}
	}
}
