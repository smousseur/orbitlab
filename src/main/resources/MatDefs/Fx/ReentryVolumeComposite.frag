#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

// Lays the plasma volumes over the scene: premultiplied, so the volume hides its alpha's share of
// the scene and adds its light.
//
// The volumes were marched at a reduced resolution, each ray stopping on the body it met. Blending
// the four reduced texels around a pixel smears that stop across a body's edge, which then reads
// in steps of a reduced texel. So the composite looks at the scene's depth where each of the four
// texels was centred: where they all saw about the pixel's own depth, it blends them; across an
// edge, it takes the one texel that saw the pixel's depth - the one whose ray stopped on the same
// surface, or went past it.

uniform COLORTEXTURE m_Texture;
uniform DEPTHTEXTURE m_DepthTexture;
uniform sampler2D m_VolumeTex;
uniform vec2 g_FrustumNearFar;

varying vec2 texCoord;

// Set on screen, not measured: the relative depth gap from which four texels no longer see one
// surface. On a body seen from ten metres, neighbouring reduced texels differ by up to a body
// radius over the distance (two metres in ten, derived), well under a body seen against the
// background, which is farther by orders of magnitude.
const float EDGE_DEPTH_RATIO = 0.25;

float viewDepth(vec2 uv) {
    float depth = fetchTextureSample(m_DepthTexture, uv, 0).r;
    float near = g_FrustumNearFar.x;
    float far = g_FrustumNearFar.y;
    return near * far / (far - depth * (far - near));
}

vec4 volumeAt(vec2 uv) {
    vec2 size = vec2(textureSize(m_VolumeTex, 0));
    vec2 corner = floor(uv * size - 0.5);
    vec2 uv00 = (corner + vec2(0.5, 0.5)) / size;
    vec2 uv10 = (corner + vec2(1.5, 0.5)) / size;
    vec2 uv01 = (corner + vec2(0.5, 1.5)) / size;
    vec2 uv11 = (corner + vec2(1.5, 1.5)) / size;

    float here = viewDepth(uv);
    float gap00 = abs(viewDepth(uv00) - here);
    float gap10 = abs(viewDepth(uv10) - here);
    float gap01 = abs(viewDepth(uv01) - here);
    float gap11 = abs(viewDepth(uv11) - here);
    if (max(max(gap00, gap10), max(gap01, gap11)) < EDGE_DEPTH_RATIO * here) {
        return texture2D(m_VolumeTex, uv);
    }
    vec2 nearest = uv00;
    float best = gap00;
    if (gap10 < best) { best = gap10; nearest = uv10; }
    if (gap01 < best) { best = gap01; nearest = uv01; }
    if (gap11 < best) { nearest = uv11; }
    return texture2D(m_VolumeTex, nearest);
}

void main() {
    vec4 scene = getColor(m_Texture, texCoord);
    vec4 volume = volumeAt(texCoord);
    gl_FragColor = vec4(scene.rgb * (1.0 - volume.a) + volume.rgb, scene.a);
}
