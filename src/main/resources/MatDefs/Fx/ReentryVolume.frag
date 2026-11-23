#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

// The plasma around a re-entering piece: the user's ray-marched prototype (plasma.frag), ported.
// Bow shock after Billig, shock layer, boundary layer on the wall, turbulent wake, all integrated
// along the ray with emission and absorption, in the box's own axes and in body radii.
//
// The flow axis - the prototype's x - is the box's +Y, the air-relative velocity, and the nose is
// at the origin. The shock, the shock layer and the wake hang on that axis. The drawn body follows
// its inertial heading instead, a few degrees off, along BodyAxis, BodyLength back from the nose.
// The rays stop on whatever the scene drew, read from its depth: the piece's own mesh, whatever its
// shape - a cylinder one radius wide fitted a launcher piece, but showed as a shell of its own
// around a boxy payload. The boundary layer glows over the last stretch of a ray before that stop.
// The prototype's solid (engines first, nozzles, grid fins) is not drawn: the body is the piece's
// own model.
//
// Output premultiplied, tone mapped here: the scene has no tone mapping of its own.

uniform mat4  g_WorldMatrix;
uniform mat4  g_WorldMatrixInverse;
uniform vec3  g_CameraPosition;
uniform vec3  g_CameraDirection;
uniform vec2  g_FrustumNearFar;

uniform DEPTHTEXTURE m_DepthTexture;
uniform vec2  m_TargetSize;

uniform int   m_Steps;
uniform float m_Intensity;
uniform float m_EmissionGain;
uniform float m_Exposure;
uniform float m_XA;
uniform float m_K;
uniform float m_A;
uniform float m_Rc;
uniform float m_Viol;
uniform float m_Temp;
uniform vec3  m_BodyAxis;
uniform float m_BodyLength;
uniform vec3  m_BoxMin;
uniform vec3  m_BoxMax;
uniform sampler3D m_NoiseTex;
uniform float m_FlowSeconds;
uniform float m_Turbulence;

varying vec3 vObjectPosition;

// Lattice cells along each side of the noise texture, which repeats.
const float NOISE_CELLS = 32.0;

// A ray stopping nowhere.
const float NO_HIT = 1e9;

// Set on screen, not measured: over how many body radii, before the box's rear face, the plasma
// fades out. The prototype's box cut its wake where it still glowed at about a sixth of its
// brightness (exp(-20/11) twenty radii past the base), and the cut read as a hard edge.
const float REAR_FADE_RADII = 8.0;

// The spacing of the samples along a ray, in body radii, up to Steps samples: a ray crossing the
// box sideways, some 22 radii, takes about 49 of them where a fixed count spent all 56, and a ray
// along the box keeps them all. A fixed count made a close side view the costliest one, measured:
// there the rays cross thin plasma and never stop early, as they do in the dense wake. Measured
// too: at 0.6 radius the jitter's pattern showed as a checker on the body; at 0.45 it barely does.
const float STEP_RADII = 0.45;

// How a ray that ends on the scene takes its last stretch: its last body radius in WALL_STEPS
// samples. The boundary layer fades within 0.3 radius of the wall, so at STEP_RADII it fell inside
// a single sample, whose share of it depended on the jitter: the jitter's pattern showed on the
// body as a checker. Measured: 16 samples there bring that checker down to what the other terms
// leave, for 0.03 ms in the costliest view.
const float WALL_RADII = 1.0;
const int WALL_STEPS = 16;

// The per-pixel offset of the first sample, which trades the steps' banding for grain: Jimenez's
// interleaved gradient noise (Call of Duty: Advanced Warfare, 2014). Its neighbouring pixels
// spread their offsets evenly, where the prototype's white-noise hash left clumps that read as
// grain in the reduced target's two-pixel blocks.
float interleavedGradientNoise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

float vnoise(vec3 x) {
    return texture(m_NoiseTex, x / NOISE_CELLS).r;
}

float fbm(vec3 p) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 3; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s / 0.875;
}

vec3 heatRamp(float t) {
    t = max(t, 0.0);
    vec3 c = mix(vec3(0.20, 0.015, 0.005), vec3(0.95, 0.18, 0.04), smoothstep(0.0, 0.30, t));
    c = mix(c, vec3(1.00, 0.55, 0.12), smoothstep(0.25, 0.55, t));
    c = mix(c, vec3(1.00, 0.85, 0.45), smoothstep(0.50, 0.80, t));
    c = mix(c, vec3(1.00, 0.97, 0.88), smoothstep(0.75, 1.10, t));
    c = mix(c, vec3(0.85, 0.92, 1.00), smoothstep(1.10, 1.70, t));
    return c * (0.10 + 1.15 * t);
}

// wallDistance: how far along the ray the sample lies before the surface the ray stops on, in body
// radii; NO_HIT for a ray that stops on nothing.
vec3 plasma(vec3 p, float wallDistance, out float sig) {
    // The prototype's frame: x along the flow, y and z across it.
    vec3 flow = vec3(p.y, p.z, p.x);
    float r = length(flow.yz);
    float x = flow.x;

    float sq = sqrt(1.0 + m_A * r * r);
    float xs = m_XA - m_K * (sq - 1.0);
    float g = -(r / m_Rc) / sq;
    float s = sqrt(1.0 + g * g);
    float f = 1.0 / (1.0 + g * g);

    // The noise drifts with the simulated time; at a clock rate where it would jump from frame to
    // frame, it is eased toward its mean of one half (ReentryFlowClock).
    vec3 q = flow + vec3(m_FlowSeconds * 5.5, 0.0, 0.0);
    float n1 = mix(0.5, fbm(q * vec3(1.1, 1.8, 1.8)), m_Turbulence);
    float n2 = mix(0.5, fbm(q * vec3(2.5, 3.5, 3.5) + 7.0), m_Turbulence);
    float tf = smoothstep(0.5, -7.0, x);

    float d = (xs - x) / s;
    d += (n1 - 0.5) * (0.04 + 0.22 * tf);
    float inside = smoothstep(-0.04, 0.08, d);

    // Squared by hand: the prototype's pow(u, 2.0) has no defined value for a negative u in GLSL.
    float u = (d - 0.06) / 0.11;
    float sheet = exp(-u * u) * (0.02 + 1.2 * pow(f, 2.6)) * smoothstep(10.0, 5.0, r);
    float pre = d < 0.0 ? exp(d / 0.35) * 0.10 * m_Viol * (0.2 + f) : 0.0;

    float delta = 0.28 + 0.55 * (1.0 - f) + 0.25 * tf;
    float layer = inside * exp(-max(d, 0.0) / delta) * pow(f, 1.8);
    float halo = inside * exp(-max(d, 0.0) / 2.0) * pow(f, 1.8) * 0.05;
    float modS = mix(1.0, 0.35 + 1.3 * n1, tf * 0.9);

    float along = dot(p, m_BodyAxis);
    float xb = max(-along, 0.0);
    float bl = inside * exp(-wallDistance / 0.30) * exp(-xb / 9.0) * (0.6 + 0.8 * mix(1.0, n2, tf));

    float xt = -m_BodyLength - x;
    float sw = 0.9 + 0.17 * max(xt, 0.0);
    float wk0 = smoothstep(-0.2, 1.0, xt);
    float wmod = clamp(n1 * 1.6 + n2 * 0.6 - 0.2, 0.0, 2.0);
    float wake = inside * wk0 * exp(-r * r / (2.0 * sw * sw)) * exp(-max(xt, 0.0) / 11.0)
        * (0.25 + 1.2 * wmod);
    float spark = smoothstep(0.80, 0.92, n2) * wake;

    vec3 vcol = mix(vec3(1.0, 0.50, 0.28), vec3(0.80, 0.35, 1.0), m_Viol);
    float tl = (0.30 + 0.70 * f) * m_Temp;
    vec3 lcol = mix(heatRamp(tl), vcol, exp(-max(d, 0.0) / 0.30) * (0.30 + 0.50 * m_Viol));
    float tb = (0.55 + 0.45 * exp(-xb / 6.0)) * m_Temp;
    float tw = m_Temp * (0.25 + 0.45 * exp(-max(xt, 0.0) / 6.0));

    vec3 e = vec3(0.0);
    e += vcol * (sheet + pre) * 1.8;
    e += lcol * layer * modS * 1.4;
    e += heatRamp(tl * 0.8 + 0.1) * halo;
    e += heatRamp(tb) * bl * 1.6;
    e += heatRamp(tw) * wake * 1.4 + vec3(1.0, 0.8, 0.25) * spark * 0.8;
    // The intensity scales the absorption with the light: a faint plasma veils as little as it
    // shines, and vanishes whole as the intensity reaches zero.
    float rearFade = smoothstep(m_BoxMin.y, m_BoxMin.y + REAR_FADE_RADII, p.y);
    sig = 0.25 * (layer + bl + 0.8 * wake) * m_Intensity * rearFade;
    return e * m_EmissionGain * m_Intensity * rearFade;
}

vec2 boxHit(vec3 ro, vec3 rd) {
    vec3 inv = 1.0 / rd;
    vec3 t0 = (m_BoxMin - ro) * inv;
    vec3 t1 = (m_BoxMax - ro) * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    return vec2(max(max(tmin.x, tmin.y), tmin.z), min(min(tmax.x, tmax.y), tmax.z));
}

// Where the ray from the camera meets what the scene drew - the piece's own mesh, or anything in
// front of it - read from the scene's depth where this reduced texel is centred, as the composite
// reads it; NO_HIT where the scene drew nothing.
float sceneHit(vec3 rd) {
    float depth = fetchTextureSample(m_DepthTexture, gl_FragCoord.xy / m_TargetSize, 0).r;
    if (depth >= 1.0) {
        return NO_HIT;
    }
    float near = g_FrustumNearFar.x;
    float far = g_FrustumNearFar.y;
    float viewDepth = near * far / (far - depth * (far - near));
    // How far the view depth advances per unit of the ray: the world matrix carries the body
    // radius, so the ray, like the box, stays in body radii.
    float depthPerUnit = dot(mat3(g_WorldMatrix) * rd, g_CameraDirection);
    return depthPerUnit > 0.0 ? viewDepth / depthPerUnit : NO_HIT;
}

// Integrates the plasma from distance a to b along the ray, in n samples, until it hides all but a
// hundredth of what lies behind. wallT is where the ray stops on the scene, NO_HIT if it does not.
void march(vec3 ro, vec3 rd, float a, float b, int n, float jitter, float wallT,
        inout vec3 acc, inout float transmittance) {
    float dt = (b - a) / float(n);
    for (int i = 0; i < n; i++) {
        if (transmittance < 0.01) {
            return;
        }
        float t = a + (float(i) + jitter) * dt;
        float sig;
        vec3 e = plasma(ro + rd * t, wallT - t, sig);
        acc += transmittance * e * dt;
        transmittance *= exp(-sig * dt);
    }
}

void main() {
    vec3 ro = (g_WorldMatrixInverse * vec4(g_CameraPosition, 1.0)).xyz;
    vec3 rd = normalize(vObjectPosition - ro);
    vec2 box = boxHit(ro, rd);
    float start = max(box.x, 0.0);
    float sceneT = sceneHit(rd);
    float end = min(box.y, sceneT);
    if (end <= start) {
        discard;
    }
    float jitter = interleavedGradientNoise(gl_FragCoord.xy);
    bool stopsOnScene = sceneT <= box.y;
    float wallT = stopsOnScene ? end : NO_HIT;
    float wall = stopsOnScene ? max(start, end - WALL_RADII) : end;
    vec3 acc = vec3(0.0);
    float transmittance = 1.0;
    if (wall > start) {
        int steps = clamp(int(ceil((wall - start) / STEP_RADII)), 1, m_Steps);
        march(ro, rd, start, wall, steps, jitter, wallT, acc, transmittance);
    }
    if (end > wall) {
        march(ro, rd, wall, end, WALL_STEPS, jitter, wallT, acc, transmittance);
    }
    gl_FragColor = vec4(1.0 - exp(-acc * m_Exposure), 1.0 - transmittance);
}
