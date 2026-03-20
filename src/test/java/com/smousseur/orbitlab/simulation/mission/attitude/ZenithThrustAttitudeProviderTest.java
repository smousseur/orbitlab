package com.smousseur.orbitlab.simulation.mission.attitude;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.attitudes.Attitude;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedPVCoordinates;

class ZenithThrustAttitudeProviderTest {

  private static Frame gcrf;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
    gcrf = OrekitService.get().gcrf();
  }

  private static TimeStampedPVCoordinates tpv(AbsoluteDate date, Vector3D pos, Vector3D vel) {
    return new TimeStampedPVCoordinates(date, pos, vel, Vector3D.ZERO);
  }

  @Test
  void returnedAttitude_isValid_noNaN() {
    ZenithThrustAttitudeProvider p = new ZenithThrustAttitudeProvider(gcrf);
    AbsoluteDate date = AbsoluteDate.J2000_EPOCH;
    Attitude att =
        p.getAttitude(
            (d, f) -> tpv(d, new Vector3D(7_000_000, 1_000_000, 500_000), Vector3D.ZERO),
            date,
            gcrf);
    Rotation rot = att.getRotation();
    assertFalse(Double.isNaN(rot.getQ0()));
    assertFalse(Double.isNaN(rot.getQ1()));
    assertFalse(Double.isNaN(rot.getQ2()));
    assertFalse(Double.isNaN(rot.getQ3()));
  }

  @Test
  void samePosition_differentVelocity_sameAttitude() {
    ZenithThrustAttitudeProvider p = new ZenithThrustAttitudeProvider(gcrf);
    Vector3D pos = new Vector3D(7_000_000, 0, 0);
    AbsoluteDate date = AbsoluteDate.J2000_EPOCH;

    Attitude att1 = p.getAttitude((d, f) -> tpv(d, pos, new Vector3D(0, 7500, 0)), date, gcrf);
    Attitude att2 = p.getAttitude((d, f) -> tpv(d, pos, new Vector3D(0, 5000, 100)), date, gcrf);

    assertEquals(0.0, Rotation.distance(att1.getRotation(), att2.getRotation()), 1e-10);
  }

  @Test
  void differentPositions_giveDifferentAttitudes() {
    ZenithThrustAttitudeProvider p = new ZenithThrustAttitudeProvider(gcrf);
    AbsoluteDate date = AbsoluteDate.J2000_EPOCH;

    Attitude att1 =
        p.getAttitude(
            (d, f) -> tpv(d, new Vector3D(7_000_000, 0, 0), Vector3D.ZERO), date, gcrf);
    Attitude att2 =
        p.getAttitude(
            (d, f) -> tpv(d, new Vector3D(0, 7_000_000, 0), Vector3D.ZERO), date, gcrf);

    double angleDiff = Rotation.distance(att1.getRotation(), att2.getRotation());
    assertTrue(angleDiff > 0.5, "Orthogonal positions should give very different zenith attitudes");
  }

  @Test
  void positionAlongPoleAxis_usesValidFallback() {
    // Position exactly along Z (north pole): cross product with PLUS_K = zero → fallback used
    ZenithThrustAttitudeProvider p = new ZenithThrustAttitudeProvider(gcrf);
    AbsoluteDate date = AbsoluteDate.J2000_EPOCH;

    Attitude att =
        p.getAttitude(
            (d, f) -> tpv(d, new Vector3D(0, 0, 7_000_000), Vector3D.ZERO), date, gcrf);

    Rotation rot = att.getRotation();
    assertFalse(Double.isNaN(rot.getQ0()));
    assertFalse(Double.isNaN(rot.getQ1()));
    assertFalse(Double.isNaN(rot.getQ2()));
    assertFalse(Double.isNaN(rot.getQ3()));
  }

  @Test
  void samePositionAtDifferentDates_sameAttitude() {
    // Attitude depends only on position, not time
    ZenithThrustAttitudeProvider p = new ZenithThrustAttitudeProvider(gcrf);
    Vector3D pos = new Vector3D(7_000_000, 0, 0);

    Attitude att1 =
        p.getAttitude(
            (d, f) -> tpv(d, pos, Vector3D.ZERO), AbsoluteDate.J2000_EPOCH, gcrf);
    Attitude att2 =
        p.getAttitude(
            (d, f) -> tpv(d, pos, Vector3D.ZERO), AbsoluteDate.J2000_EPOCH.shiftedBy(3600), gcrf);

    assertEquals(0.0, Rotation.distance(att1.getRotation(), att2.getRotation()), 1e-10);
  }
}
