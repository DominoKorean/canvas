/*
 *  Copyright 2019, 2020 grondag
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License.  You may obtain a copy
 *  of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package grondag.canvas.texture;

import com.mojang.blaze3d.platform.TextureUtil;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteAtlasTexture.Data;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;

import grondag.canvas.CanvasMod;
import grondag.canvas.config.Configurator;
import grondag.canvas.mixinterface.SpriteAtlasTextureDataExt;
import grondag.canvas.render.CanvasTextureState;
import grondag.canvas.varia.GFX;

@Environment(EnvType.CLIENT)
public class SpriteInfoTexture {
	private static final Object2ObjectOpenHashMap<Identifier, SpriteInfoTexture> MAP = new Object2ObjectOpenHashMap<>(64, Hash.VERY_FAST_LOAD_FACTOR);

	public static final SpriteInfoTexture getOrCreate(Identifier id) {
		return MAP.computeIfAbsent(id, SpriteInfoTexture::new);
	}

	public static final SpriteInfoTexture BLOCKS = getOrCreate(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);

	private ObjectArrayList<Sprite> spriteIndex = null;
	private SpriteAtlasTexture atlas;
	private SpriteFinder spriteFinder;
	private int atlasWidth;
	private int atlasHeight;
	private int spriteCount = -1;
	private int textureSize = -1;
	private int glId = -1;
	public final Identifier id;

	private SpriteInfoTexture(Identifier id) {
		this.id = id;
	}

	public void reset(Data dataIn, ObjectArrayList<Sprite> spriteIndexIn, SpriteAtlasTexture atlasIn) {
		if (Configurator.enableLifeCycleDebug) {
			CanvasMod.LOG.info("Lifecycle Event: SpriteInfoTexture init");
		}

		if (glId != -1) {
			disable();
			TextureUtil.releaseTextureId(glId);
			glId = -1;
		}

		atlas = atlasIn;
		spriteFinder = SpriteFinder.get(atlas);
		spriteIndex = spriteIndexIn;
		spriteCount = spriteIndex.size();
		textureSize = MathHelper.smallestEncompassingPowerOfTwo(spriteCount);
		atlasWidth = ((SpriteAtlasTextureDataExt) dataIn).canvas_atlasWidth();
		atlasHeight = ((SpriteAtlasTextureDataExt) dataIn).canvas_atlasHeight();
	}

	private void createImageIfNeeded() {
		if (glId == -1) {
			createImage();
		}
	}

	private void createImage() {
		try (SpriteInfoImage image = new SpriteInfoImage(spriteIndex, spriteCount, textureSize)) {
			glId = TextureUtil.generateTextureId();

			CanvasTextureState.activeTextureUnit(TextureData.SPRITE_INFO);
			CanvasTextureState.bindTexture(GFX.GL_TEXTURE_2D, glId);

			// Bragging rights and eternal gratitude to Wyn Price (https://github.com/Wyn-Price)
			// for reminding me pixelStore exists, thus fixing #92 and preserving a tattered
			// remnant of my sanity. I owe you a favor!

			GFX.pixelStore(GFX.GL_UNPACK_ROW_LENGTH, 0);
			GFX.pixelStore(GFX.GL_UNPACK_SKIP_ROWS, 0);
			GFX.pixelStore(GFX.GL_UNPACK_SKIP_PIXELS, 0);
			GFX.pixelStore(GFX.GL_UNPACK_ALIGNMENT, 4);

			image.upload();

			image.close();

			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_MAX_LEVEL, 0);
			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_MIN_LOD, 0);
			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_MAX_LOD, 0);
			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_LOD_BIAS, 0.0F);
			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_MIN_FILTER, GFX.GL_NEAREST);
			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_MAG_FILTER, GFX.GL_NEAREST);
			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_WRAP_S, GFX.GL_REPEAT);
			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_WRAP_T, GFX.GL_REPEAT);
			CanvasTextureState.activeTextureUnit(TextureData.MC_SPRITE_ATLAS);

			CanvasTextureState.bindTexture(GFX.GL_TEXTURE_2D, 0);
			CanvasTextureState.activeTextureUnit(TextureData.MC_SPRITE_ATLAS);
		} catch (final Exception e) {
			CanvasMod.LOG.warn("Unable to create sprite info texture due to error:", e);

			if (glId != -1) {
				TextureUtil.releaseTextureId(glId);
				glId = -1;
			}
		}
	}

	public static void disable() {
		CanvasTextureState.activeTextureUnit(TextureData.SPRITE_INFO);
		CanvasTextureState.bindTexture(0);
		CanvasTextureState.activeTextureUnit(TextureData.MC_SPRITE_ATLAS);
	}

	public void enable() {
		createImageIfNeeded();
		CanvasTextureState.activeTextureUnit(TextureData.SPRITE_INFO);
		CanvasTextureState.bindTexture(glId);
	}

	public int coordinate(int spriteId) {
		return spriteId;
	}

	public Sprite fromId(int spriteId) {
		return spriteIndex.get(spriteId);
	}

	public float mapU(int spriteId, float unmappedU) {
		final Sprite sprite = spriteIndex.get(spriteId);
		final float u0 = sprite.getMinU();
		return u0 + unmappedU * (sprite.getMaxU() - u0);
	}

	public float mapV(int spriteId, float unmappedV) {
		final Sprite sprite = spriteIndex.get(spriteId);
		final float v0 = sprite.getMinV();
		return v0 + unmappedV * (sprite.getMaxV() - v0);
	}

	public int textureSize() {
		return textureSize;
	}

	public int atlasWidth() {
		return atlasWidth;
	}

	public int atlasHeight() {
		return atlasHeight;
	}

	public SpriteAtlasTexture atlas() {
		return atlas;
	}

	public SpriteFinder spriteFinder() {
		return spriteFinder;
	}
}
