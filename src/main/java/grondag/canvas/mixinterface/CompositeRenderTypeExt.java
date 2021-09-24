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

package grondag.canvas.mixinterface;

import java.util.Optional;

import io.vram.frex.mixin.core.AccessCompositeState;

import net.minecraft.client.renderer.RenderType;

import grondag.canvas.material.state.RenderMaterialImpl;

public interface CompositeRenderTypeExt extends RenderTypeExt {
	Optional<RenderType> canvas_affectedOutline();

	boolean canvas_outline();

	void canvas_startDrawing();

	void canvas_endDrawing();

	AccessCompositeState canvas_phases();

	RenderMaterialImpl canvas_materialState();

	String canvas_name();
}
