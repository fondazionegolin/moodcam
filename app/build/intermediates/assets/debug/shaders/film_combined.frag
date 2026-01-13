#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform samplerExternalOES uCameraTexture;
uniform sampler2D uLutTexture;
uniform sampler2D uNoiseTexture;

uniform vec2 uResolution;
uniform float uTime;

uniform float uExposureEV;
uniform float uTemperatureK;
uniform float uTint;
uniform float uSaturation;
uniform float uVibrance;
uniform float uFade;
uniform float uContrast;

uniform float uHighlights;
uniform float uMidtones;
uniform float uShadows;

uniform float uGrainStrength;
uniform float uGrainSize;
uniform int uGrainToneMode;

uniform float uVignette;
uniform float uBloom;
uniform float uHalation;
uniform float uClarity;

uniform int uPortraitMode;
uniform sampler2D uMaskTexture;

// ============================================================
// UTILITY FUNCTIONS
// ============================================================

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 srgbToLinear(vec3 srgb) {
    srgb = clamp(srgb, 0.0, 1.0);
    vec3 cutoff = step(srgb, vec3(0.04045));
    vec3 higher = pow((srgb + 0.055) / 1.055, vec3(2.4));
    vec3 lower = srgb / 12.92;
    return mix(higher, lower, cutoff);
}

vec3 linearToSrgb(vec3 linear) {
    linear = max(linear, vec3(0.0));
    vec3 cutoff = step(linear, vec3(0.0031308));
    vec3 higher = 1.055 * pow(linear, vec3(1.0/2.4)) - 0.055;
    vec3 lower = linear * 12.92;
    return clamp(mix(higher, lower, cutoff), 0.0, 1.0);
}

// ============================================================
// COLOR PROCESSING
// ============================================================

vec3 applyWhiteBalance(vec3 rgb, float tempShift, float tintShift) {
    vec3 colorShift = vec3(
        1.0 + tempShift * 0.3,
        1.0 - abs(tempShift) * 0.05 + tintShift * 0.15,
        1.0 - tempShift * 0.3
    );
    return rgb * colorShift;
}

vec3 applyExposure(vec3 rgb, float ev) {
    return rgb * pow(2.0, ev);
}

vec3 applyContrast(vec3 rgb, float contrast) {
    return (rgb - 0.5) * contrast + 0.5;
}

vec3 applyFade(vec3 rgb, float fade) {
    return rgb * (1.0 - fade) + fade;
}

vec3 applyCurvesLUT(vec3 rgb, float lumaVal) {
    if (lumaVal < 0.10) {
        return rgb;
    }
    
    vec3 safeRgb = clamp(rgb, 0.01, 0.99);
    
    float r = texture(uLutTexture, vec2(safeRgb.r, 0.125)).r;
    float g = texture(uLutTexture, vec2(safeRgb.g, 0.375)).g;
    float b = texture(uLutTexture, vec2(safeRgb.b, 0.625)).b;
    
    vec3 curved = vec3(r, g, b);
    float blendFactor = smoothstep(0.10, 0.25, lumaVal);
    
    return mix(rgb, curved, blendFactor);
}

vec3 applySaturation(vec3 rgb, float saturation) {
    float l = luma(rgb);
    return mix(vec3(l), rgb, saturation);
}

vec3 applyVibrance(vec3 rgb, float vibrance) {
    float l = luma(rgb);
    float maxCh = max(max(rgb.r, rgb.g), rgb.b);
    float minCh = min(min(rgb.r, rgb.g), rgb.b);
    float satAmount = maxCh - minCh;
    float vibranceAmount = vibrance * (1.0 - satAmount);
    return mix(vec3(l), rgb, 1.0 + vibranceAmount);
}

vec3 applyTones(vec3 rgb, float highlights, float midtones, float shadows) {
    float l = luma(rgb);
    float shadowWeight = pow(1.0 - l, 2.0);
    float highlightWeight = pow(l, 2.0);
    float midtoneWeight = exp(-pow((l - 0.5) * 2.5, 2.0));
    float adjustment = shadows * shadowWeight + midtones * midtoneWeight + highlights * highlightWeight;
    return rgb * pow(2.0, adjustment * 0.5);
}

// ============================================================
// AUTHENTIC FILM GRAIN
// Based on PetaPixel article principles:
// 1. Particles are ORGANIC - varied sizes, not uniform
// 2. UNEVENLY distributed - not grid-based
// 3. Color film has DYE particles - colored grain per channel
// 4. Grain in ALL tones, but less visible in highlights
// 5. NO pixel-grid-based noise - must break regularity
// ============================================================

// Gold noise - completely non-repetitive
float goldNoise(vec2 xy, float seed) {
    float PHI = 1.61803398874989484820459;
    return fract(tan(distance(xy * PHI, xy) * seed) * xy.x);
}

// High quality random using sin - breaks grid patterns
float random(vec2 st) {
    return fract(sin(dot(st, vec2(12.9898, 78.233))) * 43758.5453123);
}

// Organic particle noise - varies size and position
float organicNoise(vec2 p, float seed) {
    // Add rotation to break any axis-aligned patterns
    float angle = seed * 0.1;
    float c = cos(angle);
    float s = sin(angle);
    p = mat2(c, -s, s, c) * p;
    
    // Non-integer offset to prevent grid alignment
    vec2 offset = vec2(
        goldNoise(p * 0.01, seed),
        goldNoise(p * 0.01 + 100.0, seed + 1.0)
    ) * 2.0;
    
    p += offset;
    
    // Layer multiple noise at non-harmonic frequencies (avoid 2x, 4x patterns)
    float n1 = random(floor(p * 1.0));
    float n2 = random(floor(p * 2.7));  // Non-harmonic
    float n3 = random(floor(p * 7.3));  // Non-harmonic
    
    // Mix with varying weights based on position
    float mixFactor = goldNoise(p * 0.1, seed + 2.0);
    float n = mix(n1, n2, mixFactor) * 0.6 + n3 * 0.4;
    
    return n;
}

// Voronoi-like cellular pattern for organic particle shapes
float cellularGrain(vec2 p, float seed) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    
    float minDist = 1.0;
    float secondDist = 1.0;
    
    // Check 3x3 neighborhood
    for(int y = -1; y <= 1; y++) {
        for(int x = -1; x <= 1; x++) {
            vec2 neighbor = vec2(float(x), float(y));
            
            // Random point position within cell
            vec2 cellOffset = i + neighbor;
            vec2 point = vec2(
                random(cellOffset + seed),
                random(cellOffset + seed + 100.0)
            );
            
            // Distance to point
            float d = length(neighbor + point - f);
            
            if(d < minDist) {
                secondDist = minDist;
                minDist = d;
            } else if(d < secondDist) {
                secondDist = d;
            }
        }
    }
    
    // Use distance difference for organic shapes
    return secondDist - minDist;
}

// Main grain function - authentic film-like
vec3 getAuthenticFilmGrain(vec2 uv, float Y) {
    // Animated seed per frame (24fps film look)
    float frame = floor(uTime * 24.0);
    float seed = frame * 137.5;  // Golden ratio derivative for good distribution
    
    // Scale by resolution and grain size - MUCH FINER grain
    vec2 pixelPos = uv * uResolution * 3.0 / max(uGrainSize * 1.5 + 0.5, 0.5);
    
    // ========================================
    // 1. LUMINANCE-BASED RESPONSE
    // Grain exists everywhere but is more visible in shadows/midtones
    // Per article: "less grain in highlights and deep shadows"
    // ========================================
    float toneMask = 1.0 - pow(abs(Y - 0.35), 0.8);  // Peak at shadows/lower-mids
    toneMask = toneMask * 0.7 + 0.3;  // Always some grain (never zero)
    
    // ========================================
    // 2. FINE GRAIN - Organic particles per channel
    // Each color layer has DIFFERENT grain (dye particles)
    // ========================================
    float scale1 = 2.5;   // Finer grain
    float scale2 = 2.9;   // Slightly different scale per channel
    float scale3 = 2.2;
    
    // Red channel grain
    float grainR = organicNoise(pixelPos * scale1, seed);
    grainR += cellularGrain(pixelPos * 0.5, seed) * 0.3;
    grainR = grainR * 2.0 - 1.0;  // Center around 0
    
    // Green channel grain (different offset and scale)
    float grainG = organicNoise(pixelPos * scale2 + 50.0, seed + 33.0);
    grainG += cellularGrain(pixelPos * 0.53, seed + 33.0) * 0.3;
    grainG = grainG * 2.0 - 1.0;
    
    // Blue channel grain
    float grainB = organicNoise(pixelPos * scale3 + 100.0, seed + 66.0);
    grainB += cellularGrain(pixelPos * 0.47, seed + 66.0) * 0.3;
    grainB = grainB * 2.0 - 1.0;
    
    vec3 fineGrain = vec3(grainR, grainG, grainB);
    
    // ========================================
    // 3. COARSE GRAIN - Larger organic clumps
    // Creates the "organic uneven distribution"
    // ========================================
    float coarseScale = 0.15 / max(uGrainSize, 0.3);
    vec2 coarsePos = pixelPos * coarseScale;
    
    float coarse = cellularGrain(coarsePos, seed * 0.1);
    coarse = smoothstep(0.1, 0.5, coarse);  // Threshold for clumping
    
    // ========================================
    // 4. MACRO VARIATION - Large scale non-uniformity
    // Simulates emulsion thickness variations
    // ========================================
    float macroNoise = goldNoise(uv * 5.0, frame * 0.01);
    float macroMod = 0.75 + macroNoise * 0.5;  // 0.75 to 1.25
    
    // ========================================
    // 5. COMBINE: fine grain * coarse mask * tone mask * macro
    // ========================================
    vec3 grain = fineGrain * coarse * toneMask * macroMod * uGrainStrength;
    
    return grain;
}

float getVignette(vec2 uv, float strength) {
    vec2 center = uv - 0.5;
    float dist = length(center);
    return 1.0 - smoothstep(0.3, 0.9, dist * strength * 2.0);
}

vec3 dither(vec3 color, vec2 uv) {
    float ditherVal = fract(dot(floor(uv * uResolution), vec2(0.5, 0.25)));
    ditherVal = (ditherVal - 0.5) / 255.0;
    return color + ditherVal;
}

// Bloom (glow on bright areas)
vec3 applyBloom(vec3 rgb, float amount) {
    float brightness = luma(rgb);
    float bloomMask = smoothstep(0.6, 1.0, brightness);
    vec3 bloomColor = rgb * bloomMask * amount;
    return rgb + bloomColor;
}

// Halation (red glow around bright areas)
vec3 applyHalation(vec3 rgb, float amount) {
    float brightness = luma(rgb);
    float halationMask = smoothstep(0.7, 1.0, brightness);
    vec3 halationColor = vec3(1.0, 0.3, 0.1) * halationMask * amount * 0.5;
    return rgb + halationColor;
}

// Clarity (local contrast)
vec3 applyClarity(vec3 rgb, float amount, float lumaVal) {
    float midtoneBoost = 1.0 - abs(lumaVal - 0.5) * 2.0;
    float clarityFactor = 1.0 + (amount * midtoneBoost * 0.3);
    return (rgb - 0.5) * clarityFactor + 0.5;
}

// ============================================================
// MAIN
// ============================================================

void main() {
    vec3 rgb = texture(uCameraTexture, vTexCoord).rgb;
    
    rgb = srgbToLinear(rgb);
    
    float tempShift = (6500.0 - uTemperatureK) / 6500.0;
    rgb = applyWhiteBalance(rgb, tempShift, uTint);
    
    rgb = applyExposure(rgb, uExposureEV);
    rgb = clamp(rgb, 0.0, 1.0);
    
    float Y = luma(rgb);
    rgb = applyCurvesLUT(rgb, Y);
    
    rgb = applyContrast(rgb, uContrast);
    rgb = applyFade(rgb, uFade);
    
    rgb = applySaturation(rgb, uSaturation);
    rgb = applyVibrance(rgb, uVibrance);
    
    rgb = applyTones(rgb, uHighlights, uMidtones, uShadows);
    rgb = clamp(rgb, 0.0, 1.0);
    
    Y = luma(rgb);
    
    // AUTHENTIC FILM GRAIN
    if (uGrainStrength > 0.001) {
        vec3 grain = getAuthenticFilmGrain(vTexCoord, Y);
        rgb += grain * 0.18;  // Balanced intensity
    }
    
    if (uVignette > 0.001) {
        rgb *= getVignette(vTexCoord, uVignette);
    }
    
    // Clarity
    if (abs(uClarity) > 0.001) {
        rgb = applyClarity(rgb, uClarity, Y);
        rgb = clamp(rgb, 0.0, 1.0);
    }
    
    // Bloom
    if (uBloom > 0.001) {
        rgb = applyBloom(rgb, uBloom);
        rgb = clamp(rgb, 0.0, 1.0);
    }
    
    // Halation
    if (uHalation > 0.001) {
        rgb = applyHalation(rgb, uHalation);
        rgb = clamp(rgb, 0.0, 1.0);
    }
    
    rgb = linearToSrgb(rgb);
    rgb = clamp(rgb, 0.0, 1.0);
    
    rgb = dither(rgb, vTexCoord);
    
    fragColor = vec4(rgb, 1.0);
}
