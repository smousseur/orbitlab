package com.smousseur.orbitlab.states.camera;

/**
 * Where the camera stands relative to a pivot: how far from it, and from which bearing. The two
 * always travel together — a distance without its bearing does not place a camera — which is why a
 * transition takes three of these rather than six loose numbers.
 *
 * @param distance the distance to the pivot, in solar units
 * @param orientation the bearing the camera sits at, around that pivot
 */
record CameraStation(float distance, CameraOrientation orientation) {}
