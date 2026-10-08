package com.smousseur.orbitlab.engine.scene.hover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The hover rule over projected orbits: a band of 8 px to enter, 12 px to leave, and a lit orbit
 * that keeps the hand until another one is inside its own entry band and strictly closer.
 *
 * <p>Orbits are drawn here as horizontal screen segments, so a cursor {@code d} pixels above one is
 * exactly {@code d} pixels away from it.
 */
class OrbitHoverDetectorTest {

  private static final float ENTER_PX = 8f;
  private static final float EXIT_PX = 12f;

  private final OrbitHoverDetector detector = new OrbitHoverDetector(ENTER_PX, EXIT_PX);

  @Test
  void entersAtEightPixelsInclusive() {
    List<ProjectedOrbit> orbits = List.of(line(SolarSystemBody.MARS, 100f));

    assertEquals(Optional.of(SolarSystemBody.MARS), detector.pick(orbits, 500f, 108f, null));
  }

  @Test
  void doesNotEnterBeyondEightPixels() {
    List<ProjectedOrbit> orbits = List.of(line(SolarSystemBody.MARS, 100f));

    assertEquals(Optional.empty(), detector.pick(orbits, 500f, 108.1f, null));
  }

  @Test
  void staysLitUpToTwelvePixelsInclusive() {
    List<ProjectedOrbit> orbits = List.of(line(SolarSystemBody.MARS, 100f));

    assertEquals(
        Optional.of(SolarSystemBody.MARS), detector.pick(orbits, 500f, 112f, SolarSystemBody.MARS));
  }

  @Test
  void goesOutBeyondTwelvePixels() {
    List<ProjectedOrbit> orbits = List.of(line(SolarSystemBody.MARS, 100f));

    assertEquals(Optional.empty(), detector.pick(orbits, 500f, 112.1f, SolarSystemBody.MARS));
  }

  @Test
  void measuresToTheSegmentEndBeyondItsSpan() {
    ProjectedOrbit stub = polyline(SolarSystemBody.MARS, 0f, 100f, 100f, 100f);

    assertEquals(Optional.of(SolarSystemBody.MARS), detector.pick(List.of(stub), 106f, 100f, null));
    assertEquals(Optional.empty(), detector.pick(List.of(stub), 108.5f, 100f, null));
  }

  @Test
  void switchesToACloserOrbitInsideItsEntryBand() {
    List<ProjectedOrbit> orbits =
        List.of(line(SolarSystemBody.MARS, 100f), line(SolarSystemBody.EARTH, 117f));

    assertEquals(
        Optional.of(SolarSystemBody.EARTH),
        detector.pick(orbits, 500f, 110f, SolarSystemBody.MARS));
  }

  @Test
  void keepsTheCurrentAgainstAFartherChallenger() {
    List<ProjectedOrbit> orbits =
        List.of(line(SolarSystemBody.MARS, 100f), line(SolarSystemBody.EARTH, 113f));

    assertEquals(
        Optional.of(SolarSystemBody.MARS), detector.pick(orbits, 500f, 106f, SolarSystemBody.MARS));
  }

  @Test
  void ignoresACloserChallengerOutsideItsEntryBand() {
    List<ProjectedOrbit> orbits =
        List.of(line(SolarSystemBody.MARS, 100f), line(SolarSystemBody.EARTH, 120f));

    assertEquals(
        Optional.of(SolarSystemBody.MARS), detector.pick(orbits, 500f, 111f, SolarSystemBody.MARS));
  }

  @Test
  void keepsTheCurrentOnATie() {
    List<ProjectedOrbit> orbits =
        List.of(line(SolarSystemBody.MARS, 100f), line(SolarSystemBody.EARTH, 116f));

    assertEquals(
        Optional.of(SolarSystemBody.EARTH),
        detector.pick(orbits, 500f, 108f, SolarSystemBody.EARTH));
    assertEquals(
        Optional.of(SolarSystemBody.MARS), detector.pick(orbits, 500f, 108f, SolarSystemBody.MARS));
  }

  @Test
  void breaksATieWithoutCurrentByListOrder() {
    List<ProjectedOrbit> orbits =
        List.of(line(SolarSystemBody.EARTH, 100f), line(SolarSystemBody.MARS, 116f));

    assertEquals(Optional.of(SolarSystemBody.EARTH), detector.pick(orbits, 500f, 108f, null));
  }

  @Test
  void losesACurrentWhoseOrbitLeftTheList() {
    List<ProjectedOrbit> orbits = List.of(line(SolarSystemBody.EARTH, 300f));

    assertEquals(Optional.empty(), detector.pick(orbits, 500f, 110f, SolarSystemBody.MARS));
  }

  @Test
  void reacquiresALostOrbitOnlyInsideItsEntryBand() {
    List<ProjectedOrbit> orbits = List.of(line(SolarSystemBody.MARS, 100f));

    assertEquals(Optional.empty(), detector.pick(orbits, 500f, 110f, null));
    assertEquals(Optional.of(SolarSystemBody.MARS), detector.pick(orbits, 500f, 108f, null));
  }

  @Test
  void ignoresSegmentsTouchingAnInvalidPoint() {
    ProjectedOrbit gapped =
        polyline(SolarSystemBody.MARS, 0f, 100f, 400f, 100f, Float.NaN, Float.NaN, 1000f, 100f);

    assertEquals(Optional.empty(), detector.pick(List.of(gapped), 700f, 100f, null));
    assertEquals(
        Optional.of(SolarSystemBody.MARS), detector.pick(List.of(gapped), 200f, 100f, null));
  }

  @Test
  void findsNothingInAnEmptyList() {
    assertEquals(Optional.empty(), detector.pick(List.of(), 500f, 100f, SolarSystemBody.MARS));
  }

  @Test
  void refusesAnEntryBandWiderThanTheExitBand() {
    assertThrows(IllegalArgumentException.class, () -> new OrbitHoverDetector(12f, 8f));
  }

  /** A horizontal orbit across the screen at height {@code y}. */
  private static ProjectedOrbit line(SolarSystemBody body, float y) {
    return polyline(body, 0f, y, 1000f, y);
  }

  private static ProjectedOrbit polyline(SolarSystemBody body, float... xy) {
    ProjectedOrbit orbit = new ProjectedOrbit(body);
    orbit.reset(xy.length / 2);
    for (int i = 0; i < xy.length; i += 2) {
      orbit.add(xy[i], xy[i + 1]);
    }
    return orbit;
  }
}
