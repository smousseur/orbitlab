package com.smousseur.orbitlab.engine.scene.mesh;

import com.jme3.math.Vector3f;

/**
 * The plane a flat annulus lies in — a planetary ring system, as a model carries it (see {@code
 * docs/orientation-planetes/01-decoupage.md} §2.2).
 *
 * <p><b>A ring has one degree of freedom fewer than a globe, and it is the interesting one.</b> It
 * carries no longitude: a ring is banded radially and uniform all the way round, so no rotation
 * about its own axis is observable and there is nothing to calibrate by eye. What is left is the
 * plane, and a real ring system lies in its planet's equatorial plane to a fraction of a degree.
 * Its correctness is therefore a single number — the angle between this normal and the globe's pole
 * — and that number needs no eye at all.
 *
 * @param normal unit normal of the plane, in the model's own axes. Its sign is arbitrary: a plane
 *     has no preferred side, so a check on it must compare absolute values
 * @param flatness variance of the vertices along {@code normal} as a fraction of their total
 *     variance. Zero for a mathematically flat disc; a sphere scores about 0.2, which is what makes
 *     this the test that tells the two apart
 */
public record RingPlane(Vector3f normal, float flatness) {}
