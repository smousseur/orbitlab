#import "Common/ShaderLib/GLSLCompat.glsllib"

// The exhaust plume of one nozzle cluster, marched through its cone with emission and absorption.
// Output premultiplied, tone mapped here: the scene has no tone mapping of its own.

#import "MatDefs/Fx/NozzlePlume.glsllib"

void main() {
    vec3 acc;
    float transmittance;
    if (!marchPlume(acc, transmittance)) {
        discard;
    }
    gl_FragColor = vec4(1.0 - exp(-acc * m_Exposure), 1.0 - transmittance);
}
