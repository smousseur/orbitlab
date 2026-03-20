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
import org.orekit.utils.PVCoordinatesProvider;
import org.orekit.utils.TimeStampedPVCoordinates;

class GravityTurnAttitudeProviderTest {

  private static Frame gcrf;

  // Fixed PV: position along X (zenith=[1,0,0]), velocity along Y (horizDir=[0,1,0])
  private static final PVCoordinatesProvider PV_PROV =
      (date, frame) ->
          new TimeStampedPVCoordinates(
              date,
              new Vector3D(7_000_000, 0, 0),
              new Vector3D(0, 7500, 0),
              Vector3D.ZERO);

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
    gcrf = OrekitService.get().gcrf();
  }

  @Test
  void returnedAttitude_isValid_noNaN() {
    GravityTurnAttitudeProvider p =
        new GravityTurnAttitudeProvider(AbsoluteDate.J2000_EPOCH, 200.0, 1.5);
    Attitude att = p.getAttitude(PV_PROV, AbsoluteDate.J2000_EPOCH.shiftedBy(100), gcrf);
    Rotation rot = att.getRotation();
    assertFalse(Double.isNaN(rot.getQ0()));
    assertFalse(Double.isNaN(rot.getQ1()));
    assertFalse(Double.isNaN(rot.getQ2()));
    assertFalse(Double.isNaN(rot.getQ3()));
  }

  @Test
  void alphaClamped_wayBeforeKickDate_sameAsAtKickDate() {
    AbsoluteDate kickDate = AbsoluteDate.J2000_EPOCH;
    GravityTurnAttitudeProvider p = new GravityTurnAttitudeProvider(kickDate, 200.0, 1.0);

    Attitude atKick = p.getAttitude(PV_PROV, kickDate, gcrf);
    Attitude wayBefore = p.getAttitude(PV_PROV, kickDate.shiftedBy(-1000), gcrf);

    assertEquals(0.0, Rotation.distance(atKick.getRotation(), wayBefore.getRotation()), 1e-10);
  }

  @Test
  void alphaClamped_wayAfterTransition_sameAsAtTransitionEnd() {
    AbsoluteDate kickDate = AbsoluteDate.J2000_EPOCH;
    double transitionTime = 200.0;
    GravityTurnAttitudeProvider p = new GravityTurnAttitudeProvider(kickDate, transitionTime, 1.0);

    AbsoluteDate atEnd = kickDate.shiftedBy(transitionTime);
    Attitude attAtEnd = p.getAttitude(PV_PROV, atEnd, gcrf);
    Attitude attWayAfter = p.getAttitude(PV_PROV, kickDate.shiftedBy(transitionTime + 1000), gcrf);

    assertEquals(0.0, Rotation.distance(attAtEnd.getRotation(), attWayAfter.getRotation()), 1e-10);
  }

  @Test
  void attitudeChanges_significantly_throughTransition() {
    AbsoluteDate kickDate = AbsoluteDate.J2000_EPOCH;
    double transitionTime = 200.0;
    GravityTurnAttitudeProvider p = new GravityTurnAttitudeProvider(kickDate, transitionTime, 1.0);

    Attitude attStart = p.getAttitude(PV_PROV, kickDate, gcrf);
    Attitude attEnd = p.getAttitude(PV_PROV, kickDate.shiftedBy(transitionTime), gcrf);

    double totalAngle = Rotation.distance(attStart.getRotation(), attEnd.getRotation());
    assertTrue(totalAngle > 0.5, "Attitude should rotate significantly (zenith → horizontal)");
  }

  @Test
  void differentExponents_giveDifferentAttitudes_atMidpoint() {
    AbsoluteDate kickDate = AbsoluteDate.J2000_EPOCH;
    double transitionTime = 200.0;
    AbsoluteDate midpoint = kickDate.shiftedBy(transitionTime / 2);

    GravityTurnAttitudeProvider linear =
        new GravityTurnAttitudeProvider(kickDate, transitionTime, 1.0);
    GravityTurnAttitudeProvider quadratic =
        new GravityTurnAttitudeProvider(kickDate, transitionTime, 2.0);

    Attitude attLinear = linear.getAttitude(PV_PROV, midpoint, gcrf);
    Attitude attQuadratic = quadratic.getAttitude(PV_PROV, midpoint, gcrf);

    // exponent=2 lags behind exponent=1 at midpoint (slower start)
    double angleDiff = Rotation.distance(attLinear.getRotation(), attQuadratic.getRotation());
    assertTrue(angleDiff > 0.01, "Different exponents should give different attitudes at midpoint");
  }

  @Test
  void attitudeIsMonotonicallyProgressing() {
    AbsoluteDate kickDate = AbsoluteDate.J2000_EPOCH;
    double transitionTime = 300.0;
    GravityTurnAttitudeProvider p = new GravityTurnAttitudeProvider(kickDate, transitionTime, 1.0);

    Attitude att0 = p.getAttitude(PV_PROV, kickDate, gcrf);
    Attitude att1 = p.getAttitude(PV_PROV, kickDate.shiftedBy(100), gcrf);
    Attitude att2 = p.getAttitude(PV_PROV, kickDate.shiftedBy(transitionTime), gcrf);

    double d01 = Rotation.distance(att0.getRotation(), att1.getRotation());
    double d12 = Rotation.distance(att1.getRotation(), att2.getRotation());
    double d02 = Rotation.distance(att0.getRotation(), att2.getRotation());

    // Total rotation = sum of partial rotations (approximately)
    assertTrue(d01 > 0, "Should rotate in first segment");
    assertTrue(d12 > 0, "Should rotate in second segment");
    assertTrue(d02 > d01, "Total rotation greater than first segment");
    assertTrue(d02 > d12, "Total rotation greater than second segment");
  }
}
