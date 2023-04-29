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

import net.minecraft.client.renderer.ScreenEffectRenderer;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {
	// TODO: re-enable this
	// @Inject(method = "renderFire", at = @At("RETURN"), cancellable = true)
	// private static void onRenderFire(CallbackInfo ci) {
	// 	final TextureAtlasSprite sprite = ModelBakery.FIRE_1.sprite();
	// 	((TextureAtlasExt) sprite.atlas()).canvas_trackFrameAnimation(((SpriteContentsExt) sprite.contents()).canvas_animationIndex());
	// }
}
