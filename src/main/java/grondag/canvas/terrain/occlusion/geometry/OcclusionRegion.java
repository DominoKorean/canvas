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

package grondag.canvas.terrain.occlusion.geometry;

import static grondag.canvas.terrain.util.RenderRegionAddressHelper.INTERIOR_CACHE_WORDS;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.INTERIOR_STATE_COUNT;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.TOTAL_CACHE_WORDS;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.address;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.interiorIndex;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;

public abstract class OcclusionRegion {
	public static final int CULL_DATA_REGION_BOUNDS = 0;
	public static final int CULL_DATA_FIRST_BOX = 1;
	public static final int[] EMPTY_CULL_DATA = {PackedBox.EMPTY_BOX};
	// PERF: do we need space for exterior positions in all cases?
	static final int RENDERABLE_OFFSET = TOTAL_CACHE_WORDS;
	static final int EXTERIOR_VISIBLE_OFFSET = RENDERABLE_OFFSET + TOTAL_CACHE_WORDS;
	static final int WORD_COUNT = EXTERIOR_VISIBLE_OFFSET + TOTAL_CACHE_WORDS;
	static final long[] EMPTY_BITS = new long[WORD_COUNT];
	static final long[] EXTERIOR_MASK = new long[INTERIOR_CACHE_WORDS];

	static {
		//		final int[] open = {0, 0, 0, 16, 16, 16, 0};
		//		ALL_OPEN = new RegionOcclusionData(open);
		//		RegionOcclusionData.isVisibleFullChunk(open, true);
		//		ALL_OPEN.fill(true);
		//
		//		final int[] closed = {0, 0, 0, 16, 16, 16, 0};
		//		RegionOcclusionData.isVisibleFullChunk(open, true);
		//		RegionOcclusionData.sameAsVisible(open, true);
		//		ALL_CLOSED = new RegionOcclusionData(closed);
		//		ALL_CLOSED.fill(false);

		for (int i = 0; i < 4096; i++) {
			final int x = i & 15;
			final int y = (i >> 4) & 15;
			final int z = (i >> 8) & 15;

			if (x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15) {
				EXTERIOR_MASK[(i >> 6)] |= (1L << (i & 63));
			}
		}
	}

	public final BoxFinder boxFinder = new BoxFinder(new AreaFinder());
	private final IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
	private final long[] bits = new long[WORD_COUNT];
	private int openCount;
	private int minRenderableX;
	private int minRenderableY;
	private int minRenderableZ;
	private int maxRenderableX;
	private int maxRenderableY;
	private int maxRenderableZ;

	public void prepare() {
		System.arraycopy(EMPTY_BITS, 0, bits, 0, WORD_COUNT);
		captureFaces();
		captureEdges();
		captureCorners();

		openCount = INTERIOR_STATE_COUNT;
		captureInterior();
	}

	protected abstract BlockState blockStateAtIndex(int index);

	protected abstract boolean closedAtRelativePos(BlockState blockState, int x, int y, int z);

	public boolean isClosed(int index) {
		return (bits[(index >> 6)] & (1L << (index & 63))) != 0;
	}

	public boolean shouldRender(int interiorIndex) {
		return (bits[(interiorIndex >> 6) + RENDERABLE_OFFSET] & (1L << (interiorIndex & 63))) != 0;
	}

	protected void setVisibility(int index, boolean isRenderable, boolean isClosed) {
		final long mask = (1L << (index & 63));
		final int baseIndex = index >> 6;

		if (isClosed) {
			--openCount;
			bits[baseIndex] |= mask;
		}

		if (isRenderable) {
			bits[baseIndex + RENDERABLE_OFFSET] |= mask;
		}
	}

	private void captureInteriorVisibility(int index, int x, int y, int z) {
		final BlockState blockState = blockStateAtIndex(index);

		// TODO: remove or make configurable
		//		final boolean isHack = blockState.getBlock() == Blocks.WHITE_STAINED_GLASS;

		if (blockState.getRenderType() != BlockRenderType.INVISIBLE || !blockState.getFluidState().isEmpty()) {
			//			setVisibility(index, true, closedAtRelativePos(blockState, x, y, z) || isHack);
			setVisibility(index, true, closedAtRelativePos(blockState, x, y, z));
		}
	}

	private void captureExteriorVisibility(int index, int x, int y, int z) {
		final BlockState blockState = blockStateAtIndex(index);

		if ((blockState.getRenderType() != BlockRenderType.INVISIBLE || !blockState.getFluidState().isEmpty()) && closedAtRelativePos(blockState, x, y, z)) {
			setVisibility(index, false, true);
		}
	}

	private void captureInterior() {
		for (int i = 0; i < INTERIOR_STATE_COUNT; i++) {
			captureInteriorVisibility(i, i & 0xF, (i >> 4) & 0xF, (i >> 8) & 0xF);
		}
	}

	private void captureFaces() {
		for (int i = 0; i < 16; i++) {
			for (int j = 0; j < 16; j++) {
				captureExteriorVisibility(address(-1, i, j), -1, i, j);
				captureExteriorVisibility(address(16, i, j), 16, i, j);

				captureExteriorVisibility(address(i, j, -1), i, j, -1);
				captureExteriorVisibility(address(i, j, 16), i, j, 16);

				captureExteriorVisibility(address(i, -1, j), i, -1, j);
				captureExteriorVisibility(address(i, 16, j), i, 16, j);
			}
		}
	}

	private void captureEdges() {
		for (int i = 0; i < 16; i++) {
			captureExteriorVisibility(address(-1, -1, i), -1, -1, i);
			captureExteriorVisibility(address(-1, 16, i), -1, 16, i);
			captureExteriorVisibility(address(16, -1, i), 16, -1, i);
			captureExteriorVisibility(address(16, 16, i), 16, 16, i);

			captureExteriorVisibility(address(-1, i, -1), -1, i, -1);
			captureExteriorVisibility(address(-1, i, 16), -1, i, 16);
			captureExteriorVisibility(address(16, i, -1), 16, i, -1);
			captureExteriorVisibility(address(16, i, 16), 16, i, 16);

			captureExteriorVisibility(address(i, -1, -1), i, -1, -1);
			captureExteriorVisibility(address(i, -1, 16), i, -1, 16);
			captureExteriorVisibility(address(i, 16, -1), i, 16, -1);
			captureExteriorVisibility(address(i, 16, 16), i, 16, 16);
		}
	}

	private void captureCorners() {
		captureExteriorVisibility(address(-1, -1, -1), -1, -1, -1);
		captureExteriorVisibility(address(-1, -1, 16), -1, -1, 16);
		captureExteriorVisibility(address(-1, 16, -1), -1, 16, -1);
		captureExteriorVisibility(address(-1, 16, 16), -1, 16, 16);

		captureExteriorVisibility(address(16, -1, -1), 16, -1, -1);
		captureExteriorVisibility(address(16, -1, 16), 16, -1, 16);
		captureExteriorVisibility(address(16, 16, -1), 16, 16, -1);
		captureExteriorVisibility(address(16, 16, 16), 16, 16, 16);
	}

	/**
	 * Checks if the position is interior and not already visited.
	 * If the position is interior and not already visited, marks it visited
	 * and returns a boolean indicating opacity.
	 *
	 * @param index
	 * @return True if position met the conditions for visiting AND was not opaque.
	 */
	private boolean setVisited(int index) {
		// interior only
		if (index >= INTERIOR_STATE_COUNT) {
			return false;
		}

		final int wordIndex = index >> 6;
		final long mask = 1L << (index & 63);

		if ((bits[wordIndex + EXTERIOR_VISIBLE_OFFSET] & mask) == 0) {
			// not already visited

			// mark visited
			bits[wordIndex + EXTERIOR_VISIBLE_OFFSET] |= mask;

			// return opacity result
			return (bits[wordIndex] & mask) == 0;
		} else {
			// already visited
			return false;
		}
	}

	private void clearInteriorRenderable(int x, int y, int z) {
		final int index = interiorIndex(x, y, z);
		bits[(index >> 6) + RENDERABLE_OFFSET] &= ~(1L << (index & 63));
	}

	private void adjustSurfaceVisibility() {
		// mask renderable to surface only
		for (int i = 0; i < 64; i++) {
			bits[i + RENDERABLE_OFFSET] &= EXTERIOR_MASK[i];
		}

		// don't render face blocks obscured by neighboring chunks
		for (int i = 1; i < 15; i++) {
			for (int j = 1; j < 15; j++) {
				if (isClosed(address(-1, i, j))) clearInteriorRenderable(0, i, j);
				if (isClosed(address(16, i, j))) clearInteriorRenderable(15, i, j);

				if (isClosed(address(i, j, -1))) clearInteriorRenderable(i, j, 0);
				if (isClosed(address(i, j, 16))) clearInteriorRenderable(i, j, 15);

				if (isClosed(address(i, -1, j))) clearInteriorRenderable(i, 0, j);
				if (isClosed(address(i, 16, j))) clearInteriorRenderable(i, 15, j);
			}
		}

		// don't render edge blocks obscured by neighboring chunks
		for (int i = 1; i < 15; i++) {
			if (isClosed(address(-1, 0, i)) && isClosed(address(0, -1, i))) {
				clearInteriorRenderable(0, 0, i);
			}

			if (isClosed(address(16, 0, i)) && isClosed(address(15, -1, i))) {
				clearInteriorRenderable(15, 0, i);
			}

			if (isClosed(address(-1, 15, i)) && isClosed(address(0, 16, i))) {
				clearInteriorRenderable(0, 15, i);
			}

			if (isClosed(address(16, 15, i)) && isClosed(address(15, 16, i))) {
				clearInteriorRenderable(15, 15, i);
			}

			if (isClosed(address(i, 0, -1)) && isClosed(address(i, -1, 0))) {
				clearInteriorRenderable(i, 0, 0);
			}

			if (isClosed(address(i, 0, 16)) && isClosed(address(i, -1, 15))) {
				clearInteriorRenderable(i, 0, 15);
			}

			if (isClosed(address(i, 15, -1)) && isClosed(address(i, 16, 0))) {
				clearInteriorRenderable(i, 15, 0);
			}

			if (isClosed(address(i, 15, 16)) && isClosed(address(i, 16, 15))) {
				clearInteriorRenderable(i, 15, 15);
			}

			if (isClosed(address(-1, i, 0)) && isClosed(address(0, i, -1))) {
				clearInteriorRenderable(0, i, 0);
			}

			if (isClosed(address(16, i, 0)) && isClosed(address(15, i, -1))) {
				clearInteriorRenderable(15, i, 0);
			}

			if (isClosed(address(-1, i, 15)) && isClosed(address(0, i, 16))) {
				clearInteriorRenderable(0, i, 15);
			}

			if (isClosed(address(16, i, 15)) && isClosed(address(15, i, 16))) {
				clearInteriorRenderable(15, i, 15);
			}
		}

		// don't render corner blocks obscured by neighboring chunks
		if (isClosed(address(-1, 0, 0)) && isClosed(address(0, -1, 0)) && isClosed(address(0, 0, -1))) {
			clearInteriorRenderable(0, 0, 0);
		}

		if (isClosed(address(16, 0, 0)) && isClosed(address(15, -1, 0)) && isClosed(address(15, 0, -1))) {
			clearInteriorRenderable(15, 0, 0);
		}

		if (isClosed(address(-1, 15, 0)) && isClosed(address(0, 16, 0)) && isClosed(address(0, 15, -1))) {
			clearInteriorRenderable(0, 15, 0);
		}

		if (isClosed(address(16, 15, 0)) && isClosed(address(15, 16, 0)) && isClosed(address(15, 15, -1))) {
			clearInteriorRenderable(15, 15, 0);
		}

		if (isClosed(address(-1, 0, 15)) && isClosed(address(0, -1, 15)) && isClosed(address(0, 0, 16))) {
			clearInteriorRenderable(0, 0, 15);
		}

		if (isClosed(address(16, 0, 15)) && isClosed(address(15, -1, 15)) && isClosed(address(15, 0, 16))) {
			clearInteriorRenderable(15, 0, 15);
		}

		if (isClosed(address(-1, 15, 15)) && isClosed(address(0, 16, 15)) && isClosed(address(0, 15, 16))) {
			clearInteriorRenderable(0, 15, 15);
		}

		if (isClosed(address(16, 15, 15)) && isClosed(address(15, 16, 15)) && isClosed(address(15, 15, 16))) {
			clearInteriorRenderable(15, 15, 15);
		}
	}

	/**
	 * Removes renderable flag and marks closed if position has no open neighbors and is not visible from exterior.
	 * Should not be called if camera may be inside the chunk!
	 */
	private void hideInteriorClosedPositions() {
		for (int i = 0; i < INTERIOR_STATE_COUNT; i++) {
			// PERF: iterate by word vs recomputing mask each time
			final long mask = (1L << (i & 63));
			final int wordIndex = (i >> 6);

			final int x = i & 0xF;
			final int y = (i >> 4) & 0xF;
			final int z = (i >> 8) & 0xF;

			if ((bits[wordIndex + EXTERIOR_VISIBLE_OFFSET] & mask) == 0 && x != 0 && y != 0 && z != 0 && x != 15 && y != 15 && z != 15) {
				bits[wordIndex + RENDERABLE_OFFSET] &= ~mask;
				// mark it opaque
				bits[wordIndex] |= mask;
			}
		}
	}

	private void computeRenderableBounds() {
		int worldIndex = RENDERABLE_OFFSET;

		long combined0 = 0;
		long combined1 = 0;
		long combined2 = 0;
		long combined3 = 0;

		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (int z = 0; z < 16; ++z) {
			final long w0 = bits[worldIndex++];
			final long w1 = bits[worldIndex++];
			final long w2 = bits[worldIndex++];
			final long w3 = bits[worldIndex++];

			if ((w0 | w1 | w2 | w3) != 0) {
				combined0 |= w0;
				combined1 |= w1;
				combined2 |= w2;
				combined3 |= w3;

				if (z < minZ) {
					minZ = z;
				}

				if (z > maxZ) {
					maxZ = z;
				}
			}
		}

		int yBits = 0;

		if ((combined0 & 0x000000000000FFFFL) != 0) yBits |= 0x0001;
		if ((combined0 & 0x00000000FFFF0000L) != 0) yBits |= 0x0002;
		if ((combined0 & 0x0000FFFF00000000L) != 0) yBits |= 0x0004;
		if ((combined0 & 0xFFFF000000000000L) != 0) yBits |= 0x0008;

		if ((combined1 & 0x000000000000FFFFL) != 0) yBits |= 0x0010;
		if ((combined1 & 0x00000000FFFF0000L) != 0) yBits |= 0x0020;
		if ((combined1 & 0x0000FFFF00000000L) != 0) yBits |= 0x0040;
		if ((combined1 & 0xFFFF000000000000L) != 0) yBits |= 0x0080;

		if ((combined2 & 0x000000000000FFFFL) != 0) yBits |= 0x0100;
		if ((combined2 & 0x00000000FFFF0000L) != 0) yBits |= 0x0200;
		if ((combined2 & 0x0000FFFF00000000L) != 0) yBits |= 0x0400;
		if ((combined2 & 0xFFFF000000000000L) != 0) yBits |= 0x0800;

		if ((combined3 & 0x000000000000FFFFL) != 0) yBits |= 0x1000;
		if ((combined3 & 0x00000000FFFF0000L) != 0) yBits |= 0x2000;
		if ((combined3 & 0x0000FFFF00000000L) != 0) yBits |= 0x4000;
		if ((combined3 & 0xFFFF000000000000L) != 0) yBits |= 0x8000;

		long xBits = combined0 | combined1 | combined2 | combined3;

		xBits |= (xBits >> 32);
		xBits |= (xBits >> 16);

		final int ixBits = (int) (xBits & 0xFFFFL);

		minRenderableX = Integer.numberOfTrailingZeros(ixBits);
		minRenderableY = Integer.numberOfTrailingZeros(yBits);
		minRenderableZ = minZ;
		maxRenderableX = 31 - Integer.numberOfLeadingZeros(ixBits);
		maxRenderableY = 31 - Integer.numberOfLeadingZeros(yBits);
		maxRenderableZ = maxZ < minZ ? minZ : maxZ;
	}

	private void visitSurfaceIfPossible(int x, int y, int z) {
		final int index = interiorIndex(x, y, z);

		if (setVisited(index)) {
			fill(index);
		}
	}

	private int[] computeOcclusion(boolean isNear) {
		// determine which blocks are visible

		for (int i = 0; i < 16; i++) {
			for (int j = 0; j < 16; j++) {
				if (!isClosed(address(-1, i, j))) {
					visitSurfaceIfPossible(0, i, j);
				}

				if (!isClosed(address(16, i, j))) {
					visitSurfaceIfPossible(15, i, j);
				}

				if (!isClosed(address(i, j, -1))) {
					visitSurfaceIfPossible(i, j, 0);
				}

				if (!isClosed(address(i, j, 16))) {
					visitSurfaceIfPossible(i, j, 15);
				}

				if (!isClosed(address(i, -1, j))) {
					visitSurfaceIfPossible(i, 0, j);
				}

				if (!isClosed(address(i, 16, j))) {
					visitSurfaceIfPossible(i, 15, j);
				}
			}
		}

		// don't hide inside position if we may be inside the chunk!
		if (!isNear) {
			hideInteriorClosedPositions();
		}

		computeRenderableBounds();

		final BoxFinder boxFinder = this.boxFinder;
		final IntArrayList boxes = boxFinder.boxes;

		boxFinder.findBoxes(bits, 0);

		final int boxCount = boxes.size();

		final int[] result = new int[boxCount + 1];

		int n = OcclusionRegion.CULL_DATA_FIRST_BOX;

		if (boxCount > 0) {
			for (int i = 0; i < boxCount; i++) {
				result[n++] = boxes.getInt(i);
			}
		}

		if (minRenderableX == Integer.MAX_VALUE) {
			result[CULL_DATA_REGION_BOUNDS] = PackedBox.EMPTY_BOX;
		} else {
			if ((minRenderableX | minRenderableY | minRenderableZ) == 0 && (maxRenderableX & maxRenderableY & maxRenderableZ) == 15) {
				result[CULL_DATA_REGION_BOUNDS] = PackedBox.FULL_BOX;
			} else {
				result[CULL_DATA_REGION_BOUNDS] = PackedBox.pack(minRenderableX, minRenderableY, minRenderableZ,
						maxRenderableX + 1, maxRenderableY + 1, maxRenderableZ + 1, PackedBox.RANGE_EXTREME);
			}
		}

		return result;
	}

	//	public static final RegionOcclusionData ALL_OPEN;
	//	public static final RegionOcclusionData ALL_CLOSED;

	public int[] build(boolean isNear) {
		if (openCount == 0) {
			// only surface blocks are visible, and only if not covered

			// PERF: should do this after hiding interior closed positions?
			// PERF: should still compute render box instead of assuming it is full
			adjustSurfaceVisibility();

			final int[] result = new int[2];
			result[CULL_DATA_REGION_BOUNDS] = PackedBox.FULL_BOX;
			result[CULL_DATA_FIRST_BOX] = PackedBox.FULL_BOX;
			return result;
		} else {
			return computeOcclusion(isNear);
		}
	}

	private void fill(int xyz4) {
		final int faceBits = 0;
		visit(xyz4, faceBits);

		while (!queue.isEmpty()) {
			final int nextXyz4 = queue.dequeueInt();
			visit(nextXyz4, faceBits);
		}
	}

	private void visit(int xyz4, int faceBits) {
		final int x = xyz4 & 0xF;

		if (x == 0) {
			enqueIfUnvisited(xyz4 + 1);
		} else if (x == 15) {
			enqueIfUnvisited(xyz4 - 1);
		} else {
			enqueIfUnvisited(xyz4 - 1);
			enqueIfUnvisited(xyz4 + 1);
		}

		final int y = xyz4 & 0xF0;

		if (y == 0) {
			enqueIfUnvisited(xyz4 + 0x10);
		} else if (y == 0xF0) {
			enqueIfUnvisited(xyz4 - 0x10);
		} else {
			enqueIfUnvisited(xyz4 - 0x10);
			enqueIfUnvisited(xyz4 + 0x10);
		}

		final int z = xyz4 & 0xF00;

		if (z == 0) {
			enqueIfUnvisited(xyz4 + 0x100);
		} else if (z == 0xF00) {
			enqueIfUnvisited(xyz4 - 0x100);
		} else {
			enqueIfUnvisited(xyz4 - 0x100);
			enqueIfUnvisited(xyz4 + 0x100);
		}
	}

	private void enqueIfUnvisited(int xyz4) {
		if (setVisited(xyz4)) {
			queue.enqueue(xyz4);
		}
	}
}
