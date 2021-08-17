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

package grondag.canvas.render.terrain;

import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.BASE_VERTEX_STRIDE;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.VERTEX_COLOR;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.VERTEX_LIGHTMAP;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.VERTEX_NORMAL;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.VERTEX_U;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.VERTEX_V;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.VERTEX_X;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.VERTEX_Y;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.VERTEX_Z;
import static grondag.canvas.apiimpl.mesh.QuadViewImpl.roundSpriteData;
import static grondag.canvas.buffer.format.CanvasVertexFormats.BASE_RGBA_4UB;
import static grondag.canvas.buffer.format.CanvasVertexFormats.BASE_TEX_2US;
import static grondag.canvas.buffer.format.CanvasVertexFormats.LIGHTMAPS_2UB;
import static grondag.canvas.buffer.format.CanvasVertexFormats.MATERIAL_1US;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.util.math.MathHelper;

import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;

import grondag.canvas.buffer.format.CanvasVertexFormat;
import grondag.canvas.buffer.format.CanvasVertexFormatElement;
import grondag.canvas.buffer.format.QuadTranscoder;
import grondag.canvas.material.state.RenderMaterialImpl;
import grondag.canvas.mixinterface.Matrix3fExt;
import grondag.canvas.mixinterface.Matrix4fExt;

public class TerrainFormat {
	private TerrainFormat() { }

	private static final CanvasVertexFormatElement REGION = new CanvasVertexFormatElement(VertexFormatElement.DataType.USHORT, 4, "in_region", false, true);
	private static final CanvasVertexFormatElement BLOCK_POS_AO = new CanvasVertexFormatElement(VertexFormatElement.DataType.UBYTE, 4, "in_blockpos_ao", false, true);
	public static final CanvasVertexFormatElement NORMAL_TANGENT_4B = new CanvasVertexFormatElement(VertexFormatElement.DataType.BYTE, 4, "in_normal_tangent", true, false);

	// Would be nice to make this smaller but with less precision in position we start
	// to see Z-fighting on iron bars, fire, etc. Iron bars require a resolution of 1/16000.
	// Reducing resolution of UV is problematic for multi-block textures.
	public static final CanvasVertexFormat TERRAIN_MATERIAL = new CanvasVertexFormat(
			REGION,
			BLOCK_POS_AO,
			BASE_RGBA_4UB, BASE_TEX_2US, LIGHTMAPS_2UB, MATERIAL_1US, NORMAL_TANGENT_4B);

	static final int TERRAIN_QUAD_STRIDE = TERRAIN_MATERIAL.quadStrideInts;
	static final int TERRAIN_VERTEX_STRIDE = TERRAIN_MATERIAL.vertexStrideInts;

	public static final QuadTranscoder TERRAIN_TRANSCODER = (quad, context, buff) -> {
		final Matrix4fExt matrix = (Matrix4fExt) (Object) context.matrix();
		final Matrix3fExt normalMatrix = context.normalMatrix();
		final int overlay = context.overlay();

		quad.overlay(overlay);

		final boolean aoDisabled = !MinecraftClient.isAmbientOcclusionEnabled();
		final float[] aoData = quad.ao;
		final RenderMaterialImpl mat = quad.material();

		assert mat.blendMode != BlendMode.DEFAULT;

		int packedNormal = 0;
		int transformedNormal = 0;
		// bit 16 is set if normal Z component is negative
		int normalFlagBits = 0;
		final boolean useVertexNormals = quad.hasVertexNormals();

		if (useVertexNormals) {
			quad.populateMissingNormals();
		} else {
			packedNormal = quad.packedFaceNormal();
			transformedNormal = normalMatrix.canvas_transform(packedNormal);
			normalFlagBits = (transformedNormal >>> 8) & 0x8000;
		}

		final int material = mat.dongle().index(quad.spriteId()) << 16;

		final int baseTargetIndex = buff.allocate(TERRAIN_QUAD_STRIDE, quad.cullFaceId());
		final int[] target = buff.data();
		final int baseSourceIndex = quad.vertexStart();
		final int[] source = quad.data();

		// This and pos vertex encoding are the only differences from standard format
		final int sectorId = context.sectorId;
		assert sectorId >= 0;
		final int sectorRelativeRegionOrigin = context.sectorRelativeRegionOrigin;

		for (int i = 0; i < 4; i++) {
			final int fromIndex = baseSourceIndex + i * BASE_VERTEX_STRIDE;
			final int toIndex = baseTargetIndex + i * TERRAIN_VERTEX_STRIDE;

			// We do this here because we need to pack the normal Z sign bit with sector ID
			if (useVertexNormals) {
				final int p = source[fromIndex + VERTEX_NORMAL];

				if (p != packedNormal) {
					packedNormal = p;
					transformedNormal = normalMatrix.canvas_transform(packedNormal);
					normalFlagBits = (transformedNormal >>> 8) & 0x8000;
				}
			}

			// PERF: Consider fixed precision integer math
			final float x = Float.intBitsToFloat(source[fromIndex + VERTEX_X]);
			final float y = Float.intBitsToFloat(source[fromIndex + VERTEX_Y]);
			final float z = Float.intBitsToFloat(source[fromIndex + VERTEX_Z]);

			final float xOut = matrix.a00() * x + matrix.a01() * y + matrix.a02() * z + matrix.a03();
			final float yOut = matrix.a10() * x + matrix.a11() * y + matrix.a12() * z + matrix.a13();
			final float zOut = matrix.a20() * x + matrix.a21() * y + matrix.a22() * z + matrix.a23();

			int xInt = MathHelper.floor(xOut);
			int yInt = MathHelper.floor(yOut);
			int zInt = MathHelper.floor(zOut);

			final int xFract = Math.round((xOut - xInt) * 0xFFFF);
			final int yFract = Math.round((yOut - yInt) * 0xFFFF);
			final int zFract = Math.round((zOut - zInt) * 0xFFFF);

			// because our integer component could be negative, we have to unpack and re-pack the sector components
			xInt += (sectorRelativeRegionOrigin & 0xFF);
			yInt += ((sectorRelativeRegionOrigin >> 8) & 0xFF);
			zInt += ((sectorRelativeRegionOrigin >> 16) & 0xFF);

			target[toIndex] = sectorId | normalFlagBits | (xFract << 16);
			target[toIndex + 1] = yFract | (zFract << 16);

			final int ao = aoDisabled ? 0xFF000000 : (Math.round(aoData[i] * 255) << 24);
			target[toIndex + 2] = xInt | (yInt << 8) | (zInt << 16) | ao;

			target[toIndex + 3] = source[fromIndex + VERTEX_COLOR];
			target[toIndex + 4] = roundSpriteData(source[fromIndex + VERTEX_U]) | (roundSpriteData(source[fromIndex + VERTEX_V]) << 16);

			final int packedLight = source[fromIndex + VERTEX_LIGHTMAP];
			final int blockLight = packedLight & 0xFF;
			final int skyLight = (packedLight >> 16) & 0xFF;
			target[toIndex + 5] = blockLight | (skyLight << 8) | material;

			target[toIndex + 6] = transformedNormal;
		}
	};
}
