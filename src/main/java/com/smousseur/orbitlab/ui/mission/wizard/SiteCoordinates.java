package com.smousseur.orbitlab.ui.mission.wizard;

/**
 * The launch pad's three numbers, as the site step currently holds them.
 *
 * @param latitude degrees
 * @param longitude degrees
 * @param altitude meters
 */
public record SiteCoordinates(double latitude, double longitude, double altitude) {}
