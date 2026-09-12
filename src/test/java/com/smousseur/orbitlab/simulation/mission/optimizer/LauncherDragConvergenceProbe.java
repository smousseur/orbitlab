package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlan;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * PHY-2 / L5 — does the search <em>find</em> what {@link GravityTurnHandoffEnvelopeProbe} shows is
 * in the box, on every launcher of the catalog?
 *
 * <p>The envelope probe grades a grid and answers what the vehicle can do. This one runs the real
 * production computation — {@link MissionPlanOptimizer} on a wizard-built mission, the path the
 * runtime takes — and answers what CMA-ES actually retains. Run together they separate a physics
 * limit from a search limit, which is the distinction a hand-off floor calibrated on one launcher
 * has to be checked against.
 *
 * <p>Asserts nothing: it prints the achieved orbit per launcher and environment. Gated on {@code
 * orbitlab.probe} so it never runs in a normal suite.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class LauncherDragConvergenceProbe {

  private static final Logger logger = LogManager.getLogger(LauncherDragConvergenceProbe.class);

  private static final double LATITUDE_DEG = 5.23;
  private static final double LONGITUDE_DEG = -52.77;

  /**
   * The configurations compared.
   *
   * <p>The Ariane 64 at 400 km comes first because it is the profile that exposed the sizing loop's
   * divergence — residual 66.5 %, then 92.8 %, then 0.0 % (see {@code MeasuredLoadPlanner#plan});
   * the Falcon Heavy at the same target is the control that converges inside the band on pass 2.
   *
   * <p>The 550 / 800 / 1500 km sweep was run once on 2026-09-12 and every profile closed in both
   * environments, drag-on and drag-off agreeing to the decimetre, so it is not kept standing here.
   * Widen this list to re-run it.
   */
  private static final List<Profile> PROFILES =
      List.of(
          new Profile("ARIANE_64", 10_000.0, 400.0), new Profile("FALCON_HEAVY", 10_000.0, 400.0));

  /**
   * The core ISPs the Vulcain sweep flies, and why these three.
   *
   * <p>{@code PHY-2 / L2} decided the Vulcain goes to its <b>physical lapse</b> — its
   * thrust-weighted sea-level-to-vacuum mean, somewhere in [320, 431], "to be posed by flying" —
   * and no lot ever carried it: the catalog still holds the 360 that {@code L2} describes as pulled
   * down to calibrate the FH/Ariane ratio (spec {@code docs/atmosphere/13-cloture-PHY-2.md} §5.1).
   * The stage burns from lift-off to ~480 s and the atmosphere is behind it after ~100 s, so an
   * honest lapse sits high in the bracket: the range worth flying is [360, 431], not all of it.
   *
   * <p><b>The ceiling is measured before the value is posed.</b> 431 s is pure vacuum — no launcher
   * does better — so it bounds what the whole question is worth, and three flights here decide
   * whether a lot re-baselining every Ariane profile is owed.
   *
   * <p><b>Run on 2026-09-12, and it closed the question.</b> The achieved orbit is {@code 399.3 x
   * 420.3 km} at all three ISPs, to the decimal: the launcher is not held back by its 360, and
   * raising it buys no measurable capacity — pure vacuum is the <em>second</em> best of the three,
   * not the best. What the ISP moves is the sizing loop: the retained upper-stage load goes 3 804
   * kg (92.8 % residual) → 215 kg (4.9 %) → 353 kg (47.3 %), a factor 17.7 for a 10 % perturbation,
   * on one launcher and one target (spec {@code docs/atmosphere/13-cloture-PHY-2.md} §5.1 and
   * §5.3). Re-run this only against a change to {@code MeasuredLoadPlanner}, for which it is now
   * the sharpest instrument in the repository.
   */
  private static final double[] CORE_ISP_SWEEP = {360.0, 395.0, 431.0};

  /** A wizard mission to fly, named by what distinguishes it. */
  private record Profile(String launcherId, double payloadMass, double targetKm) {
    String label() {
      return String.format(
          Locale.ROOT, "%s+%.0ft@%.0fkm", launcherId, payloadMass / 1000.0, targetKm);
    }
  }

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void achievedOrbit_perLauncher_dragOnAgainstVacuum() {
    for (Profile profile : PROFILES) {
      MissionSpec dragOn = MissionFactory.specFromWizardValues(values(profile), MissionType.LEO);
      fly(profile.label() + " NRLMSISE", dragOn);
      fly(profile.label() + " NONE", dragOn.withAtmosphere(AtmosphereModel.NONE));
    }
  }

  @Test
  void ariane64_coreIspSweep_whatTheVulcainLapseWouldBuy() {
    Profile profile = new Profile("ARIANE_64", 10_000.0, 400.0);
    MissionSpec.EarthOrbit dragOn =
        (MissionSpec.EarthOrbit)
            MissionFactory.specFromWizardValues(values(profile), MissionType.LEO);
    for (double isp : CORE_ISP_SWEEP) {
      fly(
          String.format(Locale.ROOT, "%s NRLMSISE coreIsp=%.0f", profile.label(), isp),
          withCoreIsp(dragOn, isp));
    }
  }

  /**
   * The same mission flown with another core ISP, the catalog untouched.
   *
   * <p>The seed propellant loads stay the ones {@code PropellantBudget} sized at the catalog ISP,
   * and that is honest for what this sweep reads: the lower stages fly full whatever their ISP, and
   * the upper stage — the quantity the sweep is about — is re-sized in flight by {@code
   * MeasuredLoadPlanner}, so its seed is not what the answer depends on.
   *
   * @param spec the mission to fly
   * @param isp the ISP to give every {@link StageRole#CORE} stage (s)
   * @return the same spec flying a launcher whose core carries that ISP
   */
  private static MissionSpec.EarthOrbit withCoreIsp(MissionSpec.EarthOrbit spec, double isp) {
    LaunchConfiguration configuration = spec.configuration();
    LauncherModel launcher = configuration.launcher();
    List<StageModel> stages =
        launcher.stages().stream()
            .map(
                stage ->
                    stage.capabilities().role() == StageRole.CORE ? withIsp(stage, isp) : stage)
            .toList();
    LauncherModel rebuilt =
        new LauncherModel(
            launcher.id(),
            launcher.displayName(),
            stages,
            launcher.ascentProfile(),
            launcher.heightMeters());
    return new MissionSpec.EarthOrbit(
        spec.name(),
        new LaunchConfiguration(
            rebuilt,
            configuration.propellantLoads(),
            configuration.payload(),
            configuration.payloadId()),
        spec.perigeeAltitude(),
        spec.apogeeAltitude(),
        spec.targetInclination(),
        spec.nodeBranch(),
        spec.targetRaan(),
        spec.siteName(),
        spec.latitude(),
        spec.longitude(),
        spec.altitude(),
        spec.horizon(),
        spec.atmosphere());
  }

  private static StageModel withIsp(StageModel stage, double isp) {
    return new StageModel(
        stage.name(),
        stage.unitDryMass(),
        stage.unitPropellantCapacity(),
        new PropulsionSystem(isp, stage.unitPropulsion().thrust()),
        stage.capabilities(),
        stage.unitAerodynamics(),
        stage.multiplicity());
  }

  private void fly(String label, MissionSpec spec) {
    MissionEntry entry = new MissionEntry(spec);
    entry.setOptimizationType(OptimizationType.FAST);
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());

    long start = System.nanoTime();
    try {
      MissionPlan plan = new MissionPlanOptimizer(entry, epoch).compute();
      double seconds = (System.nanoTime() - start) / 1e9;
      MissionEphemerisPoint last = plan.computation().ephemeris().lastPoint();
      KeplerianOrbit orbit =
          new KeplerianOrbit(
              new PVCoordinates(last.position(), last.velocity()),
              OrekitService.get().gcrf(),
              last.time(),
              Constants.WGS84_EARTH_MU);
      double earthRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
      int topStage = spec.configuration().launcher().stages().size() - 1;
      String top =
          plan.computation()
              .performanceReport()
              .residualForStage(topStage)
              .map(
                  sp ->
                      String.format(
                          Locale.ROOT,
                          "%.0f kg loaded / %.1f %% residual",
                          sp.loaded(),
                          100.0 * sp.residualRatio()))
              .orElse("no per-stage split");
      logger.info(
          "[L5 convergence] {} — {} s, achieved {} x {} km, final mass {} kg, top stage {}",
          label,
          fmt(seconds),
          fmt((orbit.getA() * (1.0 - orbit.getE()) - earthRadius) / 1000.0),
          fmt((orbit.getA() * (1.0 + orbit.getE()) - earthRadius) / 1000.0),
          fmt(last.mass()),
          top);
    } catch (RuntimeException e) {
      logger.info(
          "[L5 convergence] {} — {} s, REFUSED: {}",
          label,
          fmt((System.nanoTime() - start) / 1e9),
          e.getMessage());
    }
  }

  private static Map<String, Object> values(Profile profile) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", profile.label() + " (convergence probe)");
    values.put("LAUNCH_SITE_NAME", "Kourou - French Guiana");
    values.put("LAUNCH_SITE_LAT", LATITUDE_DEG);
    values.put("LAUNCH_SITE_LONG", LONGITUDE_DEG);
    values.put("LAUNCH_SITE_ALT", 0.0);
    values.put("LAUNCHER_TYPE", profile.launcherId());
    values.put("PAYLOAD_TYPE", "EARTH_OBS_SAT");
    values.put("PAYLOAD_MASS", profile.payloadMass());
    values.put("LEO_PERIGEE_ALT", profile.targetKm());
    values.put("LEO_APOGEE_ALT", profile.targetKm());
    return values;
  }

  private static String fmt(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }
}
