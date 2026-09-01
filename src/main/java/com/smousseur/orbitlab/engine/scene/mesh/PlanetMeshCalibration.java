package com.smousseur.orbitlab.engine.scene.mesh;

/**
 * What a body's asset was measured to carry, plus the one value a measurement cannot supply.
 *
 * <p>The two components have deliberately different natures, and fusing them into a single
 * quaternion is what the previous design got wrong (see {@code
 * docs/orientation-planetes/01-decoupage.md} §4.2): {@code measured} is produced by {@link
 * MeshFrameProbe} and copied verbatim from its report, never edited by hand, while {@code
 * lambda0Deg} is a human datum about the <em>texture</em> that no file inspection can establish.
 *
 * <p>Which is also why they age differently. Replacing a mesh invalidates {@code measured} and
 * leaves {@code lambda0Deg} intact whenever the new model reuses the same texture — the common
 * case.
 *
 * @param measured the frame the asset carries, as reported by {@code ./gradlew meshProbe}
 * @param lambda0Deg the body-fixed longitude, in degrees, that the texture's column {@code u = 0}
 *     represents. Zero until L3 measures it: L1 deliberately calibrates nothing
 * @param textureWidth width of the base colour texture the globe was bound to when measured
 * @param textureHeight its height. Together with the width this is the asset's texture identity,
 *     which the startup guard needs: a re-export that keeps the mesh and swaps the texture leaves
 *     {@code measured} identical and {@code lambda0Deg} silently wrong
 */
public record PlanetMeshCalibration(
    MeshFrame measured, float lambda0Deg, int textureWidth, int textureHeight) {}
