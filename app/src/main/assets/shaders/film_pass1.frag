#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// Camera texture (external OES)
uniform samplerExternalOES uTexture;

// LUT texture for curves (RGBA: R, G, B, Luma)
uniform sampler2D uLutTexture;

// Parameters
uniform float uExposureEV;      // -3.0 to +3.0
uniform float uTemperatureK;    // 2000 to 12000 (normalized as shift)
uniform float uTint;            // -1.0 to +1.0
uniform float uSaturation;      // 0.0 to 2.0
uniform float uVibrance;        // -1.0 to +1.0
uniform float uFade;            // 0.0 to 1.0
uniform float uContrast;        // 0.5 to 2.0

// sRGB <-> Linear conversion
vec3 srgbToLinear(vec3 srgb) {
    vec3 cutoff = step(srgb, vec3(0.04045));
    vec3 higher = pow((srgb + 0.055) / 1.055, vec3(2.4));
    vec3 lower = srgb / 12.92;
    return mix(higher, lower, cutoff);
}

vec3 linearToSrgb(vec3 linear) {
    vec3 cutoff = step(linear, vec3(0.0031308));
    vec3 higher = 1.055 * pow(linear, vec3(1.0/2.4)) - 0.055;
    vec3 lower = linear * 12.92;
    return mix(higher, lower, cutoff);
}

// White balance adjustment using temperature
vec3 applyWhiteBalance(vec3 rgb, float tempShift, float tintShift) {
    // Simplified Kelvin to RGB shift
    // tempShift: negative = cooler (blue), positive = warmer (yellow/orange)
    // tintShift: negative = green, positive = magenta
    
    vec3 colorShift = vec3(
        1.0 + tempShift * 0.3,           // Red increases with warmth
        1.0 - abs(tempShift) * 0.05 + tintShift * 0.15,  // Green
        1.0 - tempShift * 0.3            // Blue decreases with warmth
    );
    
    return rgb * colorShift;
}

// Apply exposure in EV stops
vec3 applyExposure(vec3 rgb, float ev) {
    return rgb * pow(2.0, ev);
}

// Apply contrast around midpoint
vec3 applyContrast(vec3 rgb, float contrast) {
    return (rgb - 0.5) * contrast + 0.5;
}

// Apply fade (lift blacks)
vec3 applyFade(vec3 rgb, float fade) {
    return rgb * (1.0 - fade) + fade;
}

// LUT curve lookup
vec3 applyCurvesLUT(vec3 rgb, float luma) {
    // Sample individual channel curves
    float r = texture(uLutTexture, vec2(rgb.r, 0.125)).r;  // Row 0
    float g = texture(uLutTexture, vec2(rgb.g, 0.375)).g;  // Row 1
    float b = texture(uLutTexture, vec2(rgb.b, 0.625)).b;  // Row 2
    
    // Sample luma curve
    float lumaCurve = texture(uLutTexture, vec2(luma, 0.875)).a;  // Row 3
    
    // Apply luma adjustment
    vec3 curved = vec3(r, g, b);
    float currentLuma = dot(curved, vec3(0.2126, 0.7152, 0.0722));
    float lumaRatio = (currentLuma > 0.001) ? lumaCurve / currentLuma : 1.0;
    
    return curved * lumaRatio;
}

// Saturation adjustment
vec3 applySaturation(vec3 rgb, float saturation) {
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(luma), rgb, saturation);
}

// Vibrance (saturation that affects less saturated colors more)
vec3 applyVibrance(vec3 rgb, float vibrance) {
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    float maxChannel = max(max(rgb.r, rgb.g), rgb.b);
    float minChannel = min(min(rgb.r, rgb.g), rgb.b);
    float satAmount = maxChannel - minChannel;
    
    // Less saturated colors get more boost
    float vibranceAmount = vibrance * (1.0 - satAmount);
    return mix(vec3(luma), rgb, 1.0 + vibranceAmount);
}

void main() {
    // Sample camera texture
    vec4 texColor = texture(uTexture, vTexCoord);
    vec3 rgb = texColor.rgb;
    
    // Convert to linear space
    rgb = srgbToLinear(rgb);
    
    // Calculate temperature shift from Kelvin
    // 6500K = neutral, lower = warmer, higher = cooler
    float tempShift = (6500.0 - uTemperatureK) / 6500.0;
    
    // Apply white balance in linear space
    rgb = applyWhiteBalance(rgb, tempShift, uTint);
    
    // Apply exposure
    rgb = applyExposure(rgb, uExposureEV);
    
    // Clamp to valid range
    rgb = clamp(rgb, 0.0, 1.0);
    
    // Calculate luma for curve
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    
    // Apply curves via LUT
    rgb = applyCurvesLUT(rgb, luma);
    
    // Apply contrast
    rgb = applyContrast(rgb, uContrast);
    
    // Apply fade (black lift)
    rgb = applyFade(rgb, uFade);
    
    // Apply saturation and vibrance
    rgb = applySaturation(rgb, uSaturation);
    rgb = applyVibrance(rgb, uVibrance);
    
    // Clamp again
    rgb = clamp(rgb, 0.0, 1.0);
    
    // Output stays in linear for pass 2
    fragColor = vec4(rgb, 1.0);
}
