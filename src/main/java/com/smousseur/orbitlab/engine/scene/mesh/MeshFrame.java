package com.smousseur.orbitlab.engine.scene.mesh;

import com.jme3.math.Vector3f;

/**
 * The body-fixed frame a textured sphere carries in its own geometry, as recovered from its UV map
 * (see {@code docs/orientation-planetes/01-decoupage.md} §2.1).
 *
 * @param pole direction of the texture's {@code v = 0} edge — the north pole, since a planetary
 *     equirectangular map puts north on the image's top row and glTF's {@code v = 0} is that row
 * @param primeMeridian direction of the texture's column {@code u = 0} at the equator
 * @param equirectangularResidualDeg mean departure, in degrees of latitude, between where a vertex
 *     actually sits and where an exact equirectangular map would put it. This is the validity test
 *     of everything else in this record: at 0 the map is exactly equirectangular and the two
 *     directions above mean what they say; well above 0 it is not a lat/long map at all and nothing
 *     here is usable — a ring, or a globe unwrapped some other way
 * @param azimuthDegreesPerU how fast the azimuth about {@code pole} turns as {@code u} advances,
 *     which carries the map's chirality in its sign. A mirrored texture cannot be fixed by any
 *     rotation — it takes a UV flip — so this has to be measured rather than assumed. All eleven
 *     planetary assets share {@code −360}
 */
public record MeshFrame(
    Vector3f pole,
    Vector3f primeMeridian,
    float equirectangularResidualDeg,
    float azimuthDegreesPerU) {}
