package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.smousseur.orbitlab.engine.NozzlePlumeConfig;
import com.smousseur.orbitlab.engine.scene.spacecraft.PlumeDrive;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/** How a burn drives its plume at a date: ignition and cut-off ramps, flame time, thin air. */
class NozzlePlumeScheduleTest {

  private static final double RAMP = 0.3;
  private static final float THROTTLE = 0.81f;

  private static AbsoluteDate epoch;
  private static BurnTimeline.Burn burn;
  private static NozzlePlumeConfig config;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    burn =
        new BurnTimeline.Burn(
            epoch, epoch.shiftedBy(100.0), 0, StageRole.BOOSTER, 1.0, 1.0, 1.0, 1.0, 1.0);
    config = NozzlePlumeConfig.defaults();
  }

  @Test
  void betweenBurnsThePlumeIsOff() {
    PlumeDrive drive = NozzlePlumeSchedule.driveAt(null, epoch, 0.0, 1.0, THROTTLE, config);

    assertNull(drive.role());
    assertEquals(0f, drive.ramp(), 0f);
  }

  @Test
  void thePlumeLightsUpOverTheRampAfterIgnition() {
    assertEquals(0f, NozzlePlumeSchedule.rampAt(burn, epoch, RAMP), 1e-6f);
    assertEquals(0.5f, NozzlePlumeSchedule.rampAt(burn, epoch.shiftedBy(RAMP / 2), RAMP), 1e-6f);
    assertEquals(1f, NozzlePlumeSchedule.rampAt(burn, epoch.shiftedBy(50.0), RAMP), 0f);
  }

  /** The same function of the date both ways, so rewinding through a cut-off replays it. */
  @Test
  void thePlumeGoesOutOverTheRampBeforeCutOff() {
    assertEquals(
        0.5f, NozzlePlumeSchedule.rampAt(burn, burn.end().shiftedBy(-RAMP / 2), RAMP), 1e-6f);
    assertEquals(0f, NozzlePlumeSchedule.rampAt(burn, burn.end(), RAMP), 1e-6f);
  }

  @Test
  void aBurnShorterThanItsTwoRampsNeverLightsFully() {
    BurnTimeline.Burn blip =
        new BurnTimeline.Burn(
            epoch, epoch.shiftedBy(0.2), 0, StageRole.UPPER, 1.0, 1.0, 1.0, 1.0, 1.0);

    assertEquals(
        (float) (0.1 / RAMP), NozzlePlumeSchedule.rampAt(blip, epoch.shiftedBy(0.1), RAMP), 1e-6f);
  }

  @Test
  void withoutARampThePlumeLightsAtOnce() {
    assertEquals(1f, NozzlePlumeSchedule.rampAt(burn, epoch.shiftedBy(1e-3), 0.0), 0f);
  }

  /** Paused, the date does not move, nor does the flame. */
  @Test
  void theFlameTimeCountsFromIgnition() {
    AbsoluteDate now = epoch.shiftedBy(42.5);

    PlumeDrive first = NozzlePlumeSchedule.driveAt(burn, now, 0.0, 0.0, THROTTLE, config);
    PlumeDrive again = NozzlePlumeSchedule.driveAt(burn, now, 0.0, 0.0, THROTTLE, config);

    assertEquals(42.5f, first.flowSeconds(), 1e-4f);
    assertEquals(first, again);
  }

  @Test
  void theDriveCarriesTheBurnAndTheAir() {
    PlumeDrive drive =
        NozzlePlumeSchedule.driveAt(burn, epoch.shiftedBy(30.0), 25_000.0, 2.0, THROTTLE, config);

    assertEquals(StageRole.BOOSTER, drive.role());
    assertEquals(THROTTLE, drive.coreThrottle(), 0f);
    assertEquals(2.0, drive.clockRate(), 0.0);
    assertEquals(config.pressureFactor(25_000.0), drive.pressureFactor(), 0f);
  }
}
