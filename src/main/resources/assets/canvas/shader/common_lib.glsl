#version 120
#extension GL_EXT_gpu_shader4 : enable
#define LAYER_COUNT 1
#define SOLID // will be TRANSLUCENT when rendering translucent layer

uniform float u_time;
uniform sampler2D u_textures;
uniform sampler2D u_lightmap;
uniform vec3 u_eye_position;
uniform vec3 u_fogColor;
uniform vec3 u_fogAttributes;
uniform mat4 u_modelView;
uniform mat4 u_projection;
uniform mat4 u_modelViewProjection;

varying float v_ao;
varying float v_diffuse;
varying vec4 v_color_0;
varying vec2 v_texcoord_0;
varying vec4 v_light;
flat varying vec2 v_flags;

#if LAYER_COUNT > 1
varying vec4 v_color_1;
varying vec2 v_texcoord_1;
#endif

#if LAYER_COUNT > 2
varying vec4 v_color_2;
varying vec2 v_texcoord_2;
#endif

const int FLAG_DISABLE_DIFFUSE = 4;
const int FLAG_DISABLE_AO_0 = 5;
const int FLAG_CUTOUT_0 = 6;
const int FLAG_UNMIPPED_0 = 7;

/**
 * Formula mimics vanilla lighting for plane-aligned quads and is vaguely
 * consistent with Phong lighting ambient + diffuse for others.
 */
float diffuse (vec3 normal) {
	return min(0.5 + abs(normal.x) * 0.1 + (normal.y > 0 ? 0.5 * normal.y : 0.0) + abs(normal.z) * 0.3, 1.0);
}

// from somewhere on the Internet...
float random (vec2 st) {
    return fract(sin(dot(st.xy,
                         vec2(12.9898,78.233)))*
        43758.5453123);
}

// Ken Perlin's improved smoothstep
float smootherstep(float edge0, float edge1, float x)
{
  // Scale, and clamp x to 0..1 range
  x = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
  // Evaluate polynomial
  return x * x * x * (x * (x * 6 - 15) + 10);
}

// Based in part on 2D Noise by Morgan McGuire @morgan3d
// https://www.shadertoy.com/view/4dS3Wd
float tnoise (in vec2 st, float t)
{
    vec2 i = floor(st);
    vec2 f = fract(st);

    // Compute values for four corners
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    a =  0.5 + sin((0.5 + a) * t) * 0.5;
    b =  0.5 + sin((0.5 + b) * t) * 0.5;
    c =  0.5 + sin((0.5 + c) * t) * 0.5;
    d =  0.5 + sin((0.5 + d) * t) * 0.5;

    // Mix 4 corners
    return mix(a, b, f.x) +
            (c - a)* f.y * (1.0 - f.x) +
            (d - b) * f.x * f.y;
}

const float[8] BITWISE_DIVISORS = float[8](0.5, 0.25, 0.125, 0.0625, 0.03125, 0.015625, 0.0078125, 0.00390625);

/**
 * Returns the value (0-1) of the indexed bit (0-7)
 * within a float value that represents a single byte (0-255).
 *
 * GLSL 120 unfortunately lacks bitwise operations
 * so we need to emulate them unless the extension is active.
 */
float bitValue(float byteValue, int bitIndex)
{
	return floor(fract(byteValue * BITWISE_DIVISORS[bitIndex]) * 2.0);
}
