package com.smousseur.orbitlab.engine.scene.mesh;

/**
 * A second shell in a body's model whose visible layer turns at a rate of its own.
 *
 * <p>Only Venus has one in this repo, and it is the reason the L4 term could not simply be a number
 * per body: its asset holds the radar map of the ground and a separate cloud shell over it, and the
 * two differ by a factor of fifty-eight in rate. A single rotation for the whole model can only be
 * right for one of them.
 *
 * @param nodeNamePrefix prefix of the shell node's name in the asset, as the exporter wrote it
 * @param lambda0Deg the body-fixed longitude the shell texture's column {@code u = 0} represents,
 *     at J2000. Its own value: the shell carries its own image, and nothing says the two maps share
 *     an origin
 * @param driftDegPerDay east-longitude drift of the shell's layer within the frame Orekit rotates
 *     the body in, in degrees per day
 */
public record AtmosphereShell(String nodeNamePrefix, float lambda0Deg, double driftDegPerDay) {}
