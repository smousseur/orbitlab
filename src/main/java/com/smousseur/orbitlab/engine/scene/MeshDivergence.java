package com.smousseur.orbitlab.engine.scene;

import com.smousseur.orbitlab.core.SolarSystemBody;

/**
 * Why a loaded asset no longer matches what {@link PlanetMeshCorrection} has committed for it.
 *
 * <p>The two reasons are independent and can occur together, which is the whole reason the texture
 * is checked at all: a re-export that keeps the mesh and swaps the texture leaves the frame
 * identical, and the committed λ0 silently wrong. The mesh check alone would stay quiet.
 *
 * @param body the body whose asset diverged
 * @param frameDeviationDeg how far the frame moved, in degrees: the larger of the pole's and the
 *     prime meridian's angular deviation from what was committed. Deliberately these two angles
 *     rather than the angle of the rotation between the frames — a log line saying which direction
 *     moved and by how much is what a reader can act on. 0 when the mesh is untouched
 * @param textureChanged whether the base colour texture no longer has the committed dimensions
 */
public record MeshDivergence(
    SolarSystemBody body, float frameDeviationDeg, boolean textureChanged) {}
