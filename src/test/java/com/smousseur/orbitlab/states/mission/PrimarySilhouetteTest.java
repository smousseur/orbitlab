package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

class PrimarySilhouetteTest {

  private static AbsoluteDate epoch;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static DebrisTrack debris(StageRole role, double secondsFromEpoch) {
    AbsoluteDate t0 = epoch.shiftedBy(secondsFromEpoch);
    MissionEphemerisPoint p0 = point(t0);
    MissionEphemerisPoint p1 = point(t0.shiftedBy(10.0));
    return new DebrisTrack(new MissionEphemeris(List.of(p0, p1)), role, 1);
  }

  private static MissionEphemerisPoint point(AbsoluteDate time) {
    return new MissionEphemerisPoint(
        time,
        new Vector3D(7_000_000.0, 0.0, 0.0),
        new Vector3D(0.0, 7_500.0, 0.0),
        "debris",
        false,
        1_000.0,
        0.0,
        TrajectoryArc.forBody(SolarSystemBody.EARTH));
  }

  @Test
  void fullBeforeAnySeparation() {
    PrimarySilhouette silhouette =
        PrimarySilhouette.from(
            List.of(debris(StageRole.BOOSTER, 100.0), debris(StageRole.CORE, 200.0)));
    assertEquals("", silhouette.suffixAt(epoch.shiftedBy(50.0)));
  }

  @Test
  void afterBoostersOnceTheBoosterHasGone() {
    PrimarySilhouette silhouette =
        PrimarySilhouette.from(
            List.of(debris(StageRole.BOOSTER, 100.0), debris(StageRole.CORE, 200.0)));
    assertEquals("-after_boosters", silhouette.suffixAt(epoch.shiftedBy(150.0)));
  }

  @Test
  void afterS1OnceTheCoreHasGone() {
    PrimarySilhouette silhouette =
        PrimarySilhouette.from(
            List.of(debris(StageRole.BOOSTER, 100.0), debris(StageRole.CORE, 200.0)));
    assertEquals("-after_s1", silhouette.suffixAt(epoch.shiftedBy(250.0)));
  }

  @Test
  void severalBoostersAtOneDateAreOneTransition() {
    PrimarySilhouette silhouette =
        PrimarySilhouette.from(
            List.of(debris(StageRole.BOOSTER, 100.0), debris(StageRole.BOOSTER, 100.0)));
    assertEquals("-after_boosters", silhouette.suffixAt(epoch.shiftedBy(150.0)));
  }

  @Test
  void upperSeparationIsIgnoredInL3() {
    PrimarySilhouette silhouette =
        PrimarySilhouette.from(
            List.of(
                debris(StageRole.BOOSTER, 100.0),
                debris(StageRole.CORE, 200.0),
                debris(StageRole.UPPER, 300.0)));
    assertEquals("-after_s1", silhouette.suffixAt(epoch.shiftedBy(350.0)));
  }

  @Test
  void reversesUnderScrub() {
    PrimarySilhouette silhouette =
        PrimarySilhouette.from(
            List.of(debris(StageRole.BOOSTER, 100.0), debris(StageRole.CORE, 200.0)));
    assertEquals("", silhouette.suffixAt(epoch.shiftedBy(50.0)));
    assertEquals("-after_s1", silhouette.suffixAt(epoch.shiftedBy(250.0)));
    assertEquals("-after_boosters", silhouette.suffixAt(epoch.shiftedBy(150.0)));
  }
}
