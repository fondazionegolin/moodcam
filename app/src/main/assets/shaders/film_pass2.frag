#version 300 es

precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// Input from pass 1 (or direct camera for single-pass)
uniform sampler2D uTexture;

// Grain noise texture
uniform sampler2D uNoiseTexture;

// Screen dimensions for grain scaling
uniform vec2 uResolution;

// Time for grain animation
uniform float uTime;

// Grain parameters
uniform float uGrainStrength;   // 0.0 to 1.0
uniform float uGrainSize;       // 0.5 to 2.0
uniform int uGrainToneMode;     // 0=SHADOW, 1=MID, 2=FLAT

// Vignette (Phase 2, but ready)
uniform float uVignette;        // 0.0 to 1.0

// sRGB conversion
vec3 linearToSrgb(vec3 linear) {
    vec3 cutoff = step(linear, vec3(0.0031308));
    vec3 higher = 1.055 * pow(linear, vec3(1.0/2.4)) - 0.055;
    vec3 lower = linear * 12.92;
    return mix(higher, lower, cutoff);
}

// Hash function for procedural noise
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Procedural grain with texture
float getGrain(vec2 uv, float time) {
    vec2 grainUV = uv * uResolution / (uGrainSize * 4.0);
    
    // Add time-based offset for animation
    grainUV += vec2(hash(vec2(time * 0.1, 0.0)), hash(vec2(0.0, time * 0.1)));
    
    // Sample noise texture with wrapping
    float noise1 = texture(uNoiseTexture, fract(grainUV * 0.5)).r;
    float noise2 = texture(uNoiseTexture, fract(grainUV * 0.25 + 0.5)).r;
    
    // Combine for more organic look
    float grain = (noise1 * 0.7 + noise2 * 0.3) * 2.0 - 1.0;
    
    return grain;
}

// Tone response for grain
float getToneResponse(float luma, int toneMode) {
    if (toneMode == 0) {
        // SHADOW: more grain in shadows
        return pow(1.0 - luma, 1.5);
    } else if (toneMode == 1) {
        // MID: bell curve, peak at midtones
        return exp(-pow((luma - 0.5) * 2.5, 2.0));
    } else {
        // FLAT: uniform grain
        return 1.0;
    }
}

// Vignette effect
float getVignette(vec2 uv, float strength) {
    vec2 center = uv - 0.5;
    float dist = length(center);
    float vignette = 1.0 - smoothstep(0.3, 0.9, dist * strength * 2.0);
    return vignette;
}

// Dithering to reduce banding
vec3 dither(vec3 color, vec2 uv) {
    // Bayer matrix 2x2 approximation
    float dither = fract(dot(floor(uv * uResolution), vec2(0.5, 0.25)));
    dither = (dither - 0.5) / 255.0;
    return color + dither;
}

void main() {
    vec3 rgb = texture(uTexture, vTexCoord).rgb;
    
    // Calculate luma for grain response
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    
    // Apply grain
    if (uGrainStrength > 0.001) {
        float grain = getGrain(vTexCoord, uTime);
        float toneResponse = getToneResponse(luma, uGrainToneMode);
        
        // Apply grain multiplicatively for film-like response
        float grainAmount = uGrainStrength * toneResponse * grain * 0.15;
        rgb *= (1.0 + grainAmount);
    }
    
    // Apply vignette (Phase 2)
    if (uVignette > 0.001) {
        float vignette = getVignette(vTexCoord, uVignette);
        rgb *= vignette;
    }
    
    // Convert to sRGB
    rgb = linearToSrgb(rgb);
    
    // Clamp to valid range
    rgb = clamp(rgb, 0.0, 1.0);
    
    // Apply dithering to reduce banding
    rgb = dither(rgb, vTexCoord);
    
    fragColor = vec4(rgb, 1.0);
}
