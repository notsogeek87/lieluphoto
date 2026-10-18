package com.hinnka.mycamera.lut

/**
 * Final display-referred sharpness pass for encoded sRGB images.
 *
 * Positive values increase luminance edge contrast. Negative values reduce the same detail and
 * converge toward the local Gaussian blur without reversing edges at the -1 endpoint.
 */
internal object SrgbSharpnessShader {
    const val DEFAULT_RADIUS = 1f
    const val DEFAULT_THRESHOLD = 0.005f
    private const val MAX_RADIUS = 2.5f

    // Rayon proportionnel à la définition : à rayon fixe de 1 px (sigma 0,5 px) une photo 12 Mpx
    // n'était accentuée que sur le bruit, pas sur les détails visibles (barbe, tissu). Repère :
    // ~1 px pour 1500 px de petit côté, soit 2 px en 12 Mpx (3000 px), plafonné à 2,5 px.
    fun radiusFor(width: Int, height: Int): Float =
        (minOf(width, height) / 1500f).coerceIn(DEFAULT_RADIUS, MAX_RADIUS)

    val FRAGMENT_SHADER = """
        #version 300 es
        precision highp float;

        in vec2 vTexCoord;
        out vec4 fragColor;

        uniform sampler2D uInputTexture;
        uniform vec2 uTexelSize;
        uniform float uSharpening;
        uniform float uRadius;
        uniform float uThreshold;

        float luminance(vec3 color) {
            return dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        void main() {
            vec3 center = texture(uInputTexture, vTexCoord).rgb;
            if (abs(uSharpening) <= 0.0001) {
                fragColor = vec4(center, 1.0);
                return;
            }

            float radius = max(uRadius, 0.001);
            float sigma = max(radius * 0.5, 0.001);
            float twoSigmaSquared = 2.0 * sigma * sigma;
            vec3 blur = vec3(0.0);
            float weightSum = 0.0;

            for (int y = -2; y <= 2; y++) {
                for (int x = -2; x <= 2; x++) {
                    vec2 offset = vec2(float(x), float(y));
                    float distanceSquared = dot(offset, offset);
                    float weight = exp(-distanceSquared / twoSigmaSquared);
                    blur += texture(
                        uInputTexture,
                        vTexCoord + offset * uTexelSize * radius
                    ).rgb * weight;
                    weightSum += weight;
                }
            }
            blur /= max(weightSum, 1e-5);

            float centerLuma = luminance(center);
            float blurLuma = luminance(blur);
            float delta = centerLuma - blurLuma;
            float detail = sign(delta) * max(abs(delta) - uThreshold, 0.0);
            float sharpeningStrength = uSharpening > 0.0
                ? uSharpening * 2.0
                : uSharpening;
            vec3 result = center + center *
                (detail / max(centerLuma, 1e-5)) * sharpeningStrength;
            fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
        }
    """.trimIndent()
}
