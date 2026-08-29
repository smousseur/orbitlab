package com.smousseur.orbitlab.ui.mission.wizard;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import java.util.Arrays;
import java.util.List;
import org.hipparchus.util.FastMath;

/**
 * A card on the wizard's first step: one target orbit family, with the parameter bounds and the
 * inclination behaviour that go with it (spec {@code
 * docs/earth-orbit/02-wizard-orbites-terrestres.md} §2).
 *
 * <p><b>A profile is not a {@link MissionType}.</b> Four of the six profiles map onto the very same
 * type, and onto the very same {@code MissionSpec.EarthOrbit}: a polar orbit is that record with
 * {@code i = 90°}, a sun-synchronous one is it with an inclination derived from the altitude, and a
 * MEO is it with an apogee past the ceiling {@code MissionComposer} routes on. Giving each of them
 * a {@code MissionType} would force the spec to carry its type alongside {@code targetInclination}
 * — a redundant component, of which {@code EarthOrbit(POLAR, i = 28°)} would be a representable and
 * meaningless value. That is the inconsistency spec {@code 01} §3.2 refuses for {@code
 * targetEccentricity}, and the reason profiles live in the UI.
 *
 * <p><b>Deliberately free of Lemur.</b> It carries an icon <em>path</em> and plain numbers, never a
 * widget, so the mapping between a spec and its card is unit-testable where a wizard step is not.
 */
public enum MissionProfile {

  /**
   * The historical target: any low orbit, plane left to whatever a due-east launch reaches for
   * free. Its inclination default is therefore not a constant but the site's latitude, and stays on
   * {@link InclinationMode#AUTO} until the user overrides it (§2.0).
   */
  LEO(
      MissionType.LEO,
      MissionDomain.EARTH,
      "LEO",
      "Low Earth Orbit",
      "160 - 2 000 km",
      "interface/wizard/icon-mission-leo.png",
      new AltitudeRange(200, 2_000, 550),
      false,
      InclinationMode.AUTO,
      Double.NaN,
      Availability.AVAILABLE),

  /** A low orbit crossing both poles, which is a {@link #LEO} whose inclination is asked for. */
  POLAR(
      MissionType.LEO,
      MissionDomain.EARTH,
      "POLAR",
      "Polar Orbit",
      "i = 90 deg",
      "interface/wizard/icon-mission-polar.png",
      new AltitudeRange(200, 2_000, 550),
      false,
      InclinationMode.EXPLICIT,
      90.0,
      Availability.AVAILABLE),

  /**
   * A circular orbit whose nodal precession keeps pace with the Sun. The one profile whose
   * inclination is <b>not</b> a choice: it follows from the altitude, and the field shows it rather
   * than accepting it (spec {@code 01} §5).
   */
  SSO(
      MissionType.LEO,
      MissionDomain.EARTH,
      "SSO",
      "Sun-Synchronous",
      "600 - 800 km",
      "interface/wizard/icon-mission-sso.png",
      new AltitudeRange(600, 800, 700),
      true,
      InclinationMode.DERIVED,
      Double.NaN,
      Availability.AVAILABLE),

  /**
   * A medium Earth orbit — the Galileo/GPS band. Past {@code MissionComposer}'s direct-chain
   * ceiling, so it is flown through a parking orbit and refused outright on a vehicle that can
   * neither hold the coast nor delegate the apogee burn (spec {@code 01} §6.1).
   */
  MEO(
      MissionType.LEO,
      MissionDomain.EARTH,
      "MEO",
      "Medium Earth Orbit",
      "20 200 km",
      "interface/wizard/icon-mission-meo.png",
      new AltitudeRange(2_000, 35_000, 20_200),
      true,
      InclinationMode.EXPLICIT,
      55.0,
      Availability.CONSTRAINED),

  /** The geostationary belt, and the only profile backed by {@link MissionType#GEO}. */
  GEO(
      MissionType.GEO,
      MissionDomain.EARTH,
      "GEO",
      "Geostationary Orbit",
      "35 786 km",
      "interface/wizard/icon-mission-geo.png",
      new AltitudeRange(200, 2_000, 550),
      false,
      InclinationMode.NONE,
      Double.NaN,
      Availability.AVAILABLE),

  /**
   * A flyby of the Moon: ascent, parking orbit, translunar injection, and a pass at the perilune
   * asked for. The one card aimed at another body — and the one whose launch date is not free,
   * hence {@link Availability#WINDOWED} (MIS-4 / L5 §2).
   *
   * <p>Named {@code LUNAR} and not {@code LUNAR_FLYBY} because {@code
   * StepParameters.defaultMissionName} composes {@code %s-%03d} on the constant: the proposed name
   * is {@code LUNAR-001}.
   */
  LUNAR(
      MissionType.LUNAR_FLYBY,
      MissionDomain.LUNAR,
      "LUNAR",
      "Lunar Flyby",
      "perilune 100 km",
      "interface/wizard/icon-mission-lunar.png",
      // The perilune band, in kilometres. The floor of 50 km holds the ±10 km merit band of
      // LunarFlybyMission.PERILUNE_TOLERANCE clear of the surface; the ceiling of 500 km is still a
      // flyby. Neither bound is measured — what the geometry refuses depends on the epoch as much
      // as on the value asked for (MIS-4 / L5 §2.2).
      new AltitudeRange(50, 500, 100),
      false,
      InclinationMode.NONE,
      Double.NaN,
      Availability.WINDOWED);

  /** What the card's badge says about flying this profile. */
  public enum Availability {
    /** Any launcher and payload of the catalog can be offered. */
    AVAILABLE,

    /**
     * The catalog constrains it. Only the MEO: past the ascent's reach, it needs an upper stage
     * that holds a 2 h 58 coast or a payload whose kick motor takes the apogee burn over, and
     * {@code MissionComposer} refuses the rest by name (spec {@code 01} §6).
     */
    CONSTRAINED,

    /**
     * The launch date is not free: the mission has to leave inside a window, and the wizard's
     * planning step is where it is picked.
     *
     * <p><b>Not {@link #CONSTRAINED}</b>, whose motive is the catalog and whose wording is the
     * MEO's. Nothing in the catalog constrains a lunar mission — both launchers fly the injection
     * with margin — and no site is refused either, the cost of launching outside the window being
     * priced rather than declared (MIS-4 / L2 §1.3). What binds is the geometry of the encounter.
     */
    WINDOWED
  }

  /** How the parameters panel treats the inclination of a profile. */
  public enum InclinationMode {
    /**
     * Editable, and derived from the launch site until it is edited. While it holds, the wizard
     * publishes <b>no</b> inclination key at all, which is what keeps a due-east mission
     * bit-identical to its pre-P2 self (§2.0).
     */
    AUTO,

    /** Editable, starting from the profile's own default, and always published. */
    EXPLICIT,

    /** Read-only: computed from the altitude by {@link LaunchPlane#sunSynchronous(double)}. */
    DERIVED,

    /**
     * No inclination field — the profile's own panel owns its parameters. GEO, whose belt is
     * equatorial by definition, and LUNAR, which flies {@code i = φ} with no branch to choose.
     */
    NONE
  }

  /**
   * The altitude band a profile offers, in kilometers.
   *
   * @param minKm the lowest altitude the sliders accept
   * @param maxKm the highest altitude the sliders accept
   * @param defaultKm the altitude a fresh mission starts on
   */
  public record AltitudeRange(double minKm, double maxKm, double defaultKm) {}

  /**
   * How close an inclination must sit to 90° to read as a polar target rather than an ordinary one.
   * Sized well above the ~3° ascent residual is <em>not</em> the question here — this reads a
   * <em>requested</em> inclination back off a spec, not an achieved one, so it only has to absorb
   * the rounding of a form field.
   */
  private static final double POLAR_TOLERANCE_DEG = 0.05;

  /** Same role for a sun-synchronous target, against the inclination the altitude derives. */
  private static final double SSO_TOLERANCE_DEG = 0.05;

  private final MissionType missionType;
  private final MissionDomain domain;
  private final String title;
  private final String subtitle;
  private final String value;
  private final String iconPath;
  private final AltitudeRange altitudes;
  private final boolean circular;
  private final InclinationMode inclinationMode;
  private final double defaultInclinationDeg;
  private final Availability availability;

  MissionProfile(
      MissionType missionType,
      MissionDomain domain,
      String title,
      String subtitle,
      String value,
      String iconPath,
      AltitudeRange altitudes,
      boolean circular,
      InclinationMode inclinationMode,
      double defaultInclinationDeg,
      Availability availability) {
    this.availability = availability;
    this.missionType = missionType;
    this.domain = domain;
    this.title = title;
    this.subtitle = subtitle;
    this.value = value;
    this.iconPath = iconPath;
    this.altitudes = altitudes;
    this.circular = circular;
    this.inclinationMode = inclinationMode;
    this.defaultInclinationDeg = defaultInclinationDeg;
  }

  /**
   * @return the mission type this profile is flown as
   */
  public MissionType missionType() {
    return missionType;
  }

  /**
   * Where this profile flies, which is the tab of the first step that holds its card.
   *
   * <p>A constructor argument and not a derivation from {@link #missionType()}: a card that omitted
   * it would not compile, which is how {@code L7} is prevented from adding a seventh card without
   * saying which tab it belongs to (MIS-5 / L6 §3).
   *
   * @return the domain whose tab shows this card
   */
  public MissionDomain domain() {
    return domain;
  }

  /**
   * @return the card title
   */
  public String title() {
    return title;
  }

  /**
   * @return the card subtitle
   */
  public String subtitle() {
    return subtitle;
  }

  /**
   * @return the card's value line
   */
  public String value() {
    return value;
  }

  /**
   * @return the classpath location of the card icon
   */
  public String iconPath() {
    return iconPath;
  }

  /**
   * @return the altitude band the parameters panel offers
   */
  public AltitudeRange altitudes() {
    return altitudes;
  }

  /**
   * Whether the target is circular by construction, in which case the panel shows a single altitude
   * slider instead of a perigee/apogee pair.
   *
   * @return {@code true} when perigee and apogee are one and the same value
   */
  public boolean isCircular() {
    return circular;
  }

  /**
   * @return how this profile's inclination is obtained
   */
  public InclinationMode inclinationMode() {
    return inclinationMode;
  }

  /**
   * @return what the card's badge reports about flying this profile
   */
  public Availability availability() {
    return availability;
  }

  /**
   * The inclination a fresh mission on this profile starts at, in degrees.
   *
   * @param launchLatitudeDeg the latitude of the currently selected launch site
   * @param altitudeMeters the currently targeted circular altitude, for {@link
   *     InclinationMode#DERIVED}
   * @return the starting inclination in degrees
   */
  public double initialInclinationDeg(double launchLatitudeDeg, double altitudeMeters) {
    return switch (inclinationMode) {
      // The site's free plane: what a due-east launch reaches, and what every mission flew
      // before MIS-7.
      case AUTO, NONE -> FastMath.abs(launchLatitudeDeg);
      case EXPLICIT -> defaultInclinationDeg;
      case DERIVED ->
          FastMath.toDegrees(LaunchPlane.sunSynchronous(altitudeMeters).targetInclination());
    };
  }

  /**
   * @return the profiles backed by {@code MissionSpec.EarthOrbit}, in card order
   */
  public static List<MissionProfile> earthOrbitProfiles() {
    // On what the name says, and not on an exclusion by name: a sixth constant slipping through a
    // "!= GEO" filter would be handed an EarthOrbitDynamicParameters — a perigee/apogee panel for a
    // lunar flyby (MIS-4 / L5 §1.4).
    return Arrays.stream(values())
        .filter(profile -> profile.missionType() == MissionType.LEO)
        .toList();
  }

  /**
   * Recovers the profile a mission was created on, so reopening the wizard lights the right card
   * (§2.1).
   *
   * <p><b>Derived rather than stored.</b> A spec component carrying the profile could contradict
   * the inclination beside it; this cannot.
   *
   * <p>The worst this can do is answer {@link #LEO} where {@link #SSO} was meant, on a mission
   * whose altitude has been edited away from the band. The inclination is then shown as a free
   * value — which is what it has become — so nothing is lost but the card's label.
   *
   * @param spec the spec to classify
   * @return the profile whose card the wizard should show as selected
   */
  public static MissionProfile of(MissionSpec spec) {
    // A switch over the sealed hierarchy, and not an instanceof with a fallback: that fallback
    // answered GEO for a lunar spec, and the verdict only held because WizardPrefill threw before
    // reaching it. It is the compiler that must point here at the fourth spec type (MIS-4 / L5
    // §1.3).
    return switch (spec) {
      case MissionSpec.Geo ignored -> GEO;
      case MissionSpec.Lunar ignored -> LUNAR;
      // No card exists yet, and inventing one here would be worse than refusing: answering LUNAR
      // would light the flyby card for an orbit insertion — the exact fallback whose removal this
      // switch documents. The room L6 was waiting for now exists, the lunar tab holding one card
      // and having space for a second; what is still missing is the card itself and the parameter
      // field key that goes with it. Unreachable until L7 adds them, since nothing creates a lunar
      // orbit mission before it.
      case MissionSpec.LunarOrbit ignored ->
          throw new UnsupportedOperationException(
              "LUNAR_ORBIT has no wizard card yet: MissionProfile.LUNAR_ORBIT lands in MIS-5 / L7");
      case MissionSpec.EarthOrbit earthOrbit -> ofEarthOrbit(earthOrbit);
    };
  }

  /**
   * The card a wizard opened without prefilled values starts on, given the type the context last
   * selected.
   *
   * <p>Replaces a {@code == GEO ? GEO : LEO} ternary that answered {@link #LEO} for a lunar type —
   * harmless while the cards were one grid, and no longer so now that the answer also decides which
   * tab opens (MIS-5 / L6 §6). Four profiles share {@link MissionType#LEO}, and the one this
   * returns for it is the historical default rather than a preset.
   *
   * @param type the mission type the context currently selects
   * @return the profile whose card the wizard should open on
   */
  public static MissionProfile defaultFor(MissionType type) {
    return switch (type) {
      case LEO -> LEO;
      case GEO -> GEO;
      case LUNAR_FLYBY -> LUNAR;
      // Unreachable for the same reason as the LunarOrbit branch of of(MissionSpec): the two
      // callers of MissionContext.setSelectedMissionType read a spec or a card, and neither can
      // carry LUNAR_ORBIT while MissionFactory and ScenarioMapper both refuse to build one.
      case LUNAR_ORBIT ->
          throw new UnsupportedOperationException(
              "LUNAR_ORBIT has no wizard card yet: MissionProfile.LUNAR_ORBIT lands in MIS-5 / L7");
    };
  }

  /**
   * Which of the four Earth-orbit cards a target belongs to.
   *
   * <p>The ordering matters: the apogee ceiling is checked first because it is the one condition
   * {@code MissionComposer} itself routes on, and a MEO at 55° would otherwise be read as an
   * ordinary low orbit.
   */
  private static MissionProfile ofEarthOrbit(MissionSpec.EarthOrbit earthOrbit) {
    if (MissionComposer.needsParkingOrbit(earthOrbit.apogeeAltitude())) {
      return MEO;
    }
    double inclinationDeg = FastMath.toDegrees(earthOrbit.targetInclination());
    if (isSunSynchronous(earthOrbit, inclinationDeg)) {
      return SSO;
    }
    if (FastMath.abs(inclinationDeg - 90.0) < POLAR_TOLERANCE_DEG) {
      return POLAR;
    }
    return LEO;
  }

  /**
   * Whether a target is the sun-synchronous orbit of its own altitude. Elliptic targets are
   * excluded outright: the derived inclination is a function of a circular altitude, so comparing
   * it against an ellipse would be comparing against a number the panel could not have produced.
   */
  private static boolean isSunSynchronous(MissionSpec.EarthOrbit spec, double inclinationDeg) {
    if (FastMath.abs(spec.apogeeAltitude() - spec.perigeeAltitude()) > 1.0) {
      return false;
    }
    try {
      double derived =
          FastMath.toDegrees(LaunchPlane.sunSynchronous(spec.apogeeAltitude()).targetInclination());
      return FastMath.abs(inclinationDeg - derived) < SSO_TOLERANCE_DEG;
    } catch (RuntimeException e) {
      // No sun-synchronous inclination exists at that altitude, so the target cannot be one.
      return false;
    }
  }
}
