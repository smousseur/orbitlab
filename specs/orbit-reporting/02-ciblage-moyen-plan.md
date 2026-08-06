# Ciblage en éléments moyens — plan d'implémentation

> **Pour un exécutant agentique** : utiliser `superpowers:subagent-driven-development` ou
> `superpowers:executing-plans` pour dérouler ce plan tâche par tâche. Les étapes utilisent la
> syntaxe case à cocher (`- [ ]`).

**Spec** : `specs/orbit-reporting/02-ciblage-moyen.md`

**Statut : les 7 tâches sont faites (2026-08-05).** Mesures et verdicts dans la spec §5.0, §5.5 et
§6.1. Reste les 4 suites longues et la baseline λ\* LEO, côté utilisateur (spec §6.2).

**But** : faire viser à la dernière poussée de forme (`AnalyticTrimBurnStage`) un périgée
**moyen** au lieu d'un périgée osculateur, de sorte que la bande d'altitude volée soit centrée
sur la demande au lieu d'être perchée à son extrême haut. Écart pire-cas divisé par deux.

**Architecture** : une classe utilitaire `MeanPerigeeAim` dans `simulation/`, qui inverse
numériquement « paramètre de forme → périgée moyen » par point fixe sur `OrbitElements.mean()`,
avec repli sur la formule fermée `(3/2)·J2·RE²/a`. `AnalyticTrimBurnStage` gagne **un seul
appel** ; aucune autre étape, aucun détecteur, aucune fonction de coût, aucune borne CMA-ES n'est
touchée.

**Stack** : Java 17+, Orekit 13.1.1
(`org.orekit.propagation.conversion.osc2mean.EcksteinHechlerTheory` via `OrbitElements`),
JUnit 5, Log4j 2.

**Commandes** — Gradle exige le JDK 21 :

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*MeanPerigeeAimTest*"
```

> **Tests longs** : ne jamais lancer `LEOMissionOptimizationTest`,
> `LEOMissionOptimizedTransferTest`, `GEOMissionOptimizationTest` ni
> `PropellantLoadOptimizerIntegrationTest` de sa propre initiative. L'utilisateur les lance
> lui-même.

> **Commits** : l'utilisateur s'occupe des commits. Aucune étape de ce plan n'en exécute.

> **Langue** : commentaires et javadoc **en anglais**, sans exception (CLAUDE.md). Seul ce plan
> et la spec sont en français.

---

## Structure des fichiers

| Fichier | Rôle |
|---|---|
| **Créer** `src/main/java/com/smousseur/orbitlab/simulation/MeanPerigeeAim.java` | l'inversion « périgée moyen visé → paramètre de forme », plus le repli fermé |
| **Créer** `src/test/java/com/smousseur/orbitlab/simulation/MeanPerigeeAimTest.java` | convergence, repli forcé, oracle de la formule fermée |
| **Modifier** `src/main/java/com/smousseur/orbitlab/simulation/mission/stage/AnalyticTrimBurnStage.java:140-144` | un appel : `rPerigeeTarget` devient une visée résolue |
| **Modifier** `src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/GravityTurnFloorProbeTest.java` | **ajouter** un `@Test` de mesure de bande — ne rien modifier d'existant |
| **Modifier** `src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/AbstractTrajectoryOptimizerTest.java:53,109-168` | assertions moyen/moyen + plancher de sécurité reposé |

**Ordre imposé par la spec §6** : mesure de la ligne de base (T1), **puis** le helper (T2),
**puis** le câblage (T3), **puis** la falsification (T4), **puis seulement** les assertions et le
plancher (T5, T6).

**Contrainte sur T1** : le `@Test` ajouté à la sonde doit être **nouveau**. Les sorties des
sondes existantes (`meanVersusOsculatingAtInsertion`, `defautB_coastMinimumVersusOsculatingPerigee`)
sont la ligne de base enregistrée dans `01-elements-moyens.md` ; les modifier rendrait les
mesures d'hier incomparables à celles de demain.

---

## Task 1 : la sonde de ligne de base — ✅ FAIT (2026-08-05)

Mesure ce que la spec §5 prédit, **avant** toute modification de comportement. Sans elle,
« la bande est centrée » n'est pas falsifiable.

> **Correction apportée pendant l'exécution.** Le code ci-dessous mesurait la **bande d'altitude
> volée** ; c'est la mauvaise grandeur sur une cible elliptique, où l'altitude balaie l'ellipse
> entière (mesuré : 183 094 → 1 000 573 m sur le 200×1000, « centre » 591 833 m, dénué de sens).
> La sonde livrée mesure l'**excursion du périgée osculateur**, identique sur les deux profils,
> plus la min d'altitude volée pour la Task 6. Voir le fichier de test pour la version qui fait
> foi, et la spec §5.0 pour les chiffres.

**Fichiers :**
- Modifier : `src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/GravityTurnFloorProbeTest.java`

- [ ] **Étape 1 : ajouter le `@Test` et son helper**

À insérer après `meanVersusOsculatingAtInsertion` (vers la ligne 570), sans toucher aux méthodes
existantes :

```java
  /**
   * Flown-band centring and its propellant cost — the baseline of spec 02, and the measurement
   * that falsifies it after the change (spec 02 sections 5 P1 and 5 P2).
   *
   * <p>Prints, per profile: the min and max geodetic altitude of the final coast, their centre,
   * the offset the centring would need ({@code target − centre}), and the sized stage's residual.
   *
   * <p>Baseline measured 2026-08-05, before the retargeting — FH-400: 380 718 → 400 171 m,
   * centre 390 445 m, required offset 9 555 m. LEO-200x1000: 183 094 → 200 000 m, centre
   * 191 547 m, required offset 8 453 m. After the change both centres must land within 1 500 m
   * of the target; if either misses, the "mean perigee = band centre" identity spec 02 rests on
   * is wrong and the work stops there.
   */
  @Test
  void flownBandCentringAndCost() {
    bandProbe(
        "FH-400", falconHeavyBudgetLoads(), falconHeavyBudgetLoads(), FH_400_RETAINED, 400_000);
    bandProbe(
        "LEO-200x1000", elliptic200x1000(), elliptic200x1000(), ELLIPTIC_RETAINED, 200_000);
  }

  private static void bandProbe(
      String tag, LEOMission mission, LEOMission twin, double[] vars, double targetPerigee) {
    double stagingComplete =
        stagingCompleteTime(twin, Launchers.FALCON_HEAVY.ascentProfile());
    Flight flight = fly(mission, vars, stagingComplete);

    // Re-run the chain through the sampling generator, exactly as MissionOptimizer closes a run.
    SpacecraftState initial = mission.getInitialState(epoch());
    MissionEphemeris ephemeris = new MissionEphemerisGenerator().generate(mission, initial);

    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (MissionEphemerisPoint pt : ephemeris.allPoints()) {
      if ("Coasting".equals(pt.stageName())) {
        min = FastMath.min(min, pt.altitudeMeters());
        max = FastMath.max(max, pt.altitudeMeters());
      }
    }
    if (!Double.isFinite(min) || !Double.isFinite(max)) {
      logger.warn("[C/{}] no Coasting samples in the ephemeris", tag);
      return;
    }

    double centre = 0.5 * (min + max);
    logger.info(
        "[C/{}] flown band {} -> {} m | centre {} m | target {} m | required offset {} m"
            + " | worst-case deviation {} m",
        tag,
        fmt(min, 0),
        fmt(max, 0),
        fmt(centre, 0),
        fmt(targetPerigee, 0),
        fmt(targetPerigee - centre, 0),
        fmt(FastMath.max(targetPerigee - min, max - targetPerigee), 0));
    logger.info(
        "[C/{}] sized stage residual {} kg of {} kg loaded (ratio {}), final mass {} kg",
        tag,
        fmt(flight.s2Residual(), 0),
        fmt(flight.s2Loaded(), 0),
        fmt(flight.s2ResidualRatio(), 4),
        fmt(flight.finalMass(), 0));
  }
```

- [ ] **Étape 2 : lancer la sonde et enregistrer les chiffres**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*GravityTurnFloorProbeTest.flownBandCentringAndCost" -Dorbitlab.probe=true --info
```

Attendu : PASS en ~10 s, deux blocs `[C/FH-400]` et `[C/LEO-200x1000]`.

**Reporter les quatre chiffres par profil** (min, max, centre, résidu) dans un bloc de la spec
`02-ciblage-moyen.md`, sous un nouveau titre « ### 5.0 Ligne de base mesurée (avant) ». Le
résidu est la référence de P2.

- [ ] **Étape 3 : vérifier que la suite a réellement tourné**

```bash
grep -o 'tests="[0-9]*" skipped="[0-9]*"' build/test-results/test/TEST-com.smousseur.orbitlab.simulation.mission.optimizer.GravityTurnFloorProbeTest.xml
```

Attendu : `skipped="0"`. Sans le flag `-Dorbitlab.probe=true` la classe entière est ignorée et
un « vert » ne prouverait rien.

---

## Task 2 : `MeanPerigeeAim`

**Fichiers :**
- Créer : `src/main/java/com/smousseur/orbitlab/simulation/MeanPerigeeAim.java`
- Test : `src/test/java/com/smousseur/orbitlab/simulation/MeanPerigeeAimTest.java`

- [ ] **Étape 1 : écrire les tests qui échouent**

```java
package com.smousseur.orbitlab.simulation;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Unit oracle for the mean-perigee aim. Synthetic orbits only — no mission, no numerical
 * propagation, tens of milliseconds (spec 02 section 6.2).
 */
class MeanPerigeeAimTest {

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double MU = Constants.WGS84_EARTH_MU;
  private static final double INCLINATION = FastMath.toRadians(5.23);

  /** The Eckstein-Hechler modelling residual measured in spec 01 section 3.2.2. */
  private static final double MEAN_RESIDUAL_M = 1_000.0;

  @BeforeAll
  static void setup() {
    try {
      OrekitService.get();
    } catch (RuntimeException e) {
      Assumptions.abort("orekit-data.zip unavailable: " + e.getMessage());
    }
  }

  /**
   * The contract: the aim it returns produces an orbit whose MEAN perigee is the requested one,
   * to within Eckstein-Hechler's own residual. Checked at three altitudes so a single lucky
   * profile cannot carry the test.
   */
  @Test
  void aimLandsTheMeanPerigeeOnTarget() {
    for (double altitude : new double[] {400_000.0, 600_000.0, 1_000_000.0}) {
      double targetRadius = RE + altitude;
      double aim =
          MeanPerigeeAim.resolve(targetRadius, targetRadius, r -> circularAimedOrbit(r, targetRadius));

      OrbitElements mean =
          OrbitElements.mean(circularAimedOrbit(aim, targetRadius))
              .orElseThrow(() -> new AssertionError("mean unavailable at " + targetRadius));
      double miss = mean.perigeeAltitude() - altitude;
      Assertions.assertTrue(
          FastMath.abs(miss) < MEAN_RESIDUAL_M,
          () -> String.format("mean perigee missed by %.0f m at %.0f km", miss, altitude / 1000.0));
    }
  }

  /** The aim raises the shaping radius: a mission perched at the top of the band must come up. */
  @Test
  void aimRaisesTheShapingRadius() {
    double targetRadius = RE + 400_000.0;
    double aim =
        MeanPerigeeAim.resolve(targetRadius, targetRadius, r -> circularAimedOrbit(r, targetRadius));
    Assertions.assertTrue(
        aim > targetRadius + 5_000.0 && aim < targetRadius + 15_000.0,
        () -> "aim out of the expected 5-15 km raise: " + (aim - targetRadius));
  }

  /**
   * Forced fallback: an aimed orbit Eckstein-Hechler refuses (e far beyond its near-circular
   * domain) must NOT fail the mission — it must fall back on the closed form. This is the
   * property that keeps the targeting path total (spec 02 section 3.2).
   */
  @Test
  void fallsBackOnTheClosedFormWhenTheMeanIsUnavailable() {
    double targetRadius = RE + 400_000.0;
    double aim = MeanPerigeeAim.resolve(targetRadius, targetRadius, r -> veryEccentricOrbit());
    Assertions.assertEquals(
        targetRadius + MeanPerigeeAim.closedFormOffset(targetRadius), aim, 1.0e-6);
  }

  /** The closed form against the values measured in spec 01 section 2 and spec 02 section 5 P3. */
  @Test
  void closedFormMatchesTheMeasuredTable() {
    Assertions.assertEquals(9_746.0, MeanPerigeeAim.closedFormOffset(RE + 400_000.0), 10.0);
    Assertions.assertEquals(9_467.0, MeanPerigeeAim.closedFormOffset(RE + 600_000.0), 10.0);
    Assertions.assertEquals(1_567.0, MeanPerigeeAim.closedFormOffset(42_164_000.0), 10.0);
  }

  /**
   * The orbit an aim of {@code shapingRadius} would produce for a burn at {@code apsisRadius}:
   * velocity perpendicular to the radius, magnitude from vis-viva on a = (shaping + apsis)/2.
   * Mirrors AnalyticHohmannTransferStage#computeTargetVelocityAtApogee for the equatorial-normal
   * case, without dragging the mission packages into a unit test.
   */
  private static Orbit circularAimedOrbit(double shapingRadius, double apsisRadius) {
    double a = 0.5 * (shapingRadius + apsisRadius);
    double v = FastMath.sqrt(MU * (2.0 / apsisRadius - 1.0 / a));
    Vector3D position = new Vector3D(apsisRadius, 0.0, 0.0);
    Vector3D velocity =
        new Vector3D(0.0, v * FastMath.cos(INCLINATION), v * FastMath.sin(INCLINATION));
    return new KeplerianOrbit(new PVCoordinates(position, velocity), frame(), date(), MU);
  }

  /** An orbit outside Eckstein-Hechler's domain, so OrbitElements.mean() returns empty. */
  private static Orbit veryEccentricOrbit() {
    return new KeplerianOrbit(
        RE + 4_000_000.0, 0.6, INCLINATION, 0.0, 0.0, 0.0,
        PositionAngleType.TRUE, frame(), date(), MU);
  }

  private static Frame frame() {
    return OrekitService.get().gcrf();
  }

  private static AbsoluteDate date() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }
}
```

- [ ] **Étape 2 : lancer les tests pour vérifier qu'ils échouent**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*MeanPerigeeAimTest*"
```

Attendu : ÉCHEC de compilation — `cannot find symbol: class MeanPerigeeAim`.

- [ ] **Étape 3 : écrire l'implémentation minimale**

```java
package com.smousseur.orbitlab.simulation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.Orbit;
import org.orekit.utils.Constants;

/**
 * Resolves the shaping radius an analytic burn must aim at so the orbit it produces carries the
 * requested <b>mean</b> perigee — that is, so the flown altitude band is <em>centred</em> on the
 * requested altitude instead of perched at its top (spec orbit-reporting/02).
 *
 * <p><b>What the returned value is, and is not.</b> It is the shaping parameter of {@code
 * AnalyticHohmannTransferStage#computeTargetVelocityAtApogee}, which enters only through {@code a
 * = (aim + apsisRadius)/2}. It is <b>not</b> a prediction of the achieved osculating perigee: on a
 * near-circular target the aim exceeds the burn-point radius, so the burn point becomes the
 * perigee and the aim is the far apside (spec 02 section 3.1). The name says what it steers, not
 * what comes out.
 *
 * <p><b>Why a fixed point rather than the closed form.</b> On the two profiles measured 2026-08-05
 * the two are equivalent — they straddle the true centring within ~1 km. What separates them is
 * eccentricity: the closed form's accuracy degrades monotonically with {@code e} (ratio
 * {@code span/2af} of 0.983 circular to 0.898 at {@code e = 0.057}), where Eckstein-Hechler holds
 * (−200 m circular, −115 m elliptic, both against a theory-free referee). The fixed point is
 * retained for the profiles to come, not for a gain on today's (spec 02 section 3.2.1).
 *
 * <p><b>Total by construction.</b> {@link OrbitElements#mean(Orbit)} returns an {@code Optional}
 * because a fixed point may fail to converge, and <b>no mission may fail because a centring
 * refinement did not converge</b> — the mission flies perfectly well on the old aim. An empty
 * mean therefore falls back on {@link #closedFormOffset(double)} rather than throwing. This is
 * what keeps the targeting path free of any new failure mode.
 */
public final class MeanPerigeeAim {
  private static final Logger logger = LogManager.getLogger(MeanPerigeeAim.class);

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double J2 = -Constants.WGS84_EARTH_C20;

  /**
   * Fixed-point steps. The map "aim to mean perigee" has slope ≈ 1 and the loop is seeded on the
   * closed form, which already lands within ~1 km — so the first step normally converges and the
   * budget only covers the elliptic profiles where the seed is 1 km off.
   */
  private static final int MAX_ITERATIONS = 4;

  /**
   * Residual below which the aim is converged (m). Set well under Eckstein-Hechler's own ~600 m
   * modelling residual (spec 01 section 3.2.2): iterating past the model's own noise buys
   * nothing, so this only has to be small against the 9.5 km correction.
   */
  private static final double CONVERGENCE_M = 10.0;

  private MeanPerigeeAim() {}

  /**
   * The shaping radius to aim at so the resulting orbit's mean perigee radius equals {@code
   * targetMeanPerigeeRadius}.
   *
   * @param targetMeanPerigeeRadius the mean perigee radius the mission asks for (m from the
   *     Earth's centre)
   * @param semiMajorAxisHint semi-major axis used by the closed-form seed and fallback (m)
   * @param aimedOrbit maps a candidate shaping radius to the orbit that aim would produce; called
   *     a handful of times, must not propagate
   * @return the shaping radius to hand the burn planner (m)
   */
  public static double resolve(
      double targetMeanPerigeeRadius, double semiMajorAxisHint, DoubleFunction<Orbit> aimedOrbit) {
    Objects.requireNonNull(aimedOrbit, "aimedOrbit");
    double fallback = targetMeanPerigeeRadius + closedFormOffset(semiMajorAxisHint);

    double aim = fallback;
    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      Optional<OrbitElements> mean = OrbitElements.mean(aimedOrbit.apply(aim));
      if (mean.isEmpty()) {
        logger.debug(
            "Mean perigee aim: mean orbit unavailable at iteration {}, falling back on the "
                + "closed-form offset ({} m).",
            iteration,
            fallback - targetMeanPerigeeRadius);
        return fallback;
      }
      double residual = targetMeanPerigeeRadius - (mean.get().perigeeAltitude() + RE);
      aim += residual;
      if (FastMath.abs(residual) < CONVERGENCE_M) {
        return aim;
      }
    }
    logger.debug(
        "Mean perigee aim: {} iterations without settling under {} m; flying the last aim "
            + "({} m of offset).",
        MAX_ITERATIONS,
        CONVERGENCE_M,
        aim - targetMeanPerigeeRadius);
    return aim;
  }

  /**
   * The closed-form recentring offset {@code a·f = (3/2)·J2·RE²/a} — half the peak-to-peak of the
   * J2 short-period perigee oscillation. Seeds the fixed point and serves as its fallback.
   *
   * <p>A near-circular approximation: measured 9 746 m at 400 km and 1 567 m at GEO, and 12 % long
   * on the 200x1000 elliptic profile (spec 02 section 3.2.1).
   *
   * @param semiMajorAxis the orbit's semi-major axis (m)
   * @return the offset to add to the requested perigee radius (m)
   */
  public static double closedFormOffset(double semiMajorAxis) {
    return 1.5 * J2 * RE * RE / semiMajorAxis;
  }
}
```

- [ ] **Étape 4 : lancer les tests pour vérifier qu'ils passent**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*MeanPerigeeAimTest*"
```

Attendu : PASS, 4 tests.

Si `fallsBackOnTheClosedFormWhenTheMeanIsUnavailable` échoue parce qu'Eckstein-Hechler **accepte**
`e = 0,6`, monter l'excentricité de `veryEccentricOrbit()` jusqu'à ce que `OrbitElements.mean()`
rende vide — vérifier en loggant `OrbitElements.mean(veryEccentricOrbit()).isPresent()`. Ne pas
remplacer le test par un mock : c'est le comportement réel du converter qu'on veut verrouiller.

- [ ] **Étape 5 : vérifier `skipped="0"`**

```bash
grep -o 'tests="[0-9]*" skipped="[0-9]*"' build/test-results/test/TEST-com.smousseur.orbitlab.simulation.MeanPerigeeAimTest.xml
```

Attendu : `tests="4" skipped="0"`.

---

## Task 3 : câbler `AnalyticTrimBurnStage`

**Fichiers :**
- Modifier : `src/main/java/com/smousseur/orbitlab/simulation/mission/stage/AnalyticTrimBurnStage.java:140-144`

- [ ] **Étape 1 : remplacer la visée**

Dans `computeTrimBurn`, remplacer ces cinq lignes :

```java
    double rPerigeeTarget = EARTH_RADIUS + targetPerigeeAltitude;

    Vector3D vTarget =
        AnalyticHohmannTransferStage.computeTargetVelocityAtApogee(
            rApo, vCurrentApo, mu, rPerigeeTarget, r2, targetInclination);
```

par :

```java
    double targetMeanPerigeeRadius = EARTH_RADIUS + targetPerigeeAltitude;
    // Aim at the MEAN perigee, not the osculating one: an osculating-circular insertion sits at
    // the TOP of the J2 short-period oscillation, so the flown perigee can only fall, by the whole
    // ~19 km amplitude. Centring the band halves the worst-case deviation for ~3 m/s (spec
    // orbit-reporting/02). The amplitude itself is not a choice — no orbit is flat under J2 — only
    // the centring is.
    //
    // The resolved value is the SHAPING parameter below, not the perigee we will read back: on a
    // circular target it exceeds |rApo|, so the burn point stays the perigee and the aim is the
    // far apside (spec 02 section 3.1).
    double rPerigeeTarget =
        MeanPerigeeAim.resolve(
            targetMeanPerigeeRadius,
            0.5 * (targetMeanPerigeeRadius + r2),
            aim ->
                new KeplerianOrbit(
                    new PVCoordinates(
                        rApo,
                        AnalyticHohmannTransferStage.computeTargetVelocityAtApogee(
                            rApo, vCurrentApo, mu, aim, r2, targetInclination)),
                    stateAtApogee.getFrame(),
                    stateAtApogee.getDate(),
                    mu));

    Vector3D vTarget =
        AnalyticHohmannTransferStage.computeTargetVelocityAtApogee(
            rApo, vCurrentApo, mu, rPerigeeTarget, r2, targetInclination);
```

Le lambda appelle **la même** fonction que la ligne qui suit : aucune dérive possible entre ce
qui est visé et ce qui est volé.

- [ ] **Étape 2 : ajouter les imports**

```java
import com.smousseur.orbitlab.simulation.MeanPerigeeAim;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.utils.PVCoordinates;
```

- [ ] **Étape 3 : enrichir le log du plan**

Remplacer le `logger.info("Trim burn plan: …")` existant par :

```java
    logger.info(
        "Trim burn plan: dv={} m/s, dt={}s, apogee altitude {} km, mean-perigee aim offset {} m",
        dv,
        dt,
        (r2 - Constants.WGS84_EARTH_EQUATORIAL_RADIUS) / 1000.0,
        rPerigeeTarget - targetMeanPerigeeRadius);
```

L'offset logué est la grandeur que la sonde de T4 confronte à `a·f` : 9,4 km attendu en LEO,
1,6 km en GEO.

- [ ] **Étape 4 : dire dans `OrbitInsertionObjective` que sa convention a changé**

Le record enregistre ce que l'utilisateur a demandé ; c'est son *interprétation* qui bascule, et
rien dans le fichier ne le dirait (spec §3.4). Ajouter au javadoc de classe, après le paragraphe
« Altitudes are geodetic… » de
`src/main/java/com/smousseur/orbitlab/simulation/mission/objective/OrbitInsertionObjective.java` :

```java
 * <p>Read as <b>mean</b> elements by the analytic trim that shapes the final orbit (spec
 * orbit-reporting/02), so the flown altitude band is centred on these altitudes rather than
 * perched at the top of the J2 short-period oscillation. The record itself is unchanged and
 * carries no convention: every other reader still treats these as osculating targets.
```

- [ ] **Étape 5 : compiler**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew classes
```

Attendu : BUILD SUCCESSFUL.

---

## Task 4 : falsification

C'est **l'étape qui décide** si le chantier tient. Elle coûte des secondes.

**Fichiers :** aucun (lecture de mesure)

- [ ] **Étape 1 : relancer la sonde de T1**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*GravityTurnFloorProbeTest.flownBandCentringAndCost" -Dorbitlab.probe=true --info
```

- [ ] **Étape 2 : confronter aux prédictions de la spec §5**

| Prédiction | Critère de succès | Si ça échoue |
|---|---|---|
| **P1** centrage | `\|target − centre\|` < 1 500 m **sur les deux profils** | L'identité §1.2 est fausse. **Arrêter**, ne pas ajuster de constante pour faire passer. |
| **P1** pire-cas | ~9,9 km (FH-400), ~9,5 km (200×1000) | Réexaminer avant d'aller plus loin. |
| **P2** coût | résidu en baisse de ≲ 50 kg par rapport à T1 | > 50 kg : le mécanisme fait autre chose qu'un recentrage. |

- [ ] **Étape 3 : consigner dans la spec**

Ajouter à `02-ciblage-moyen.md` une section « ### 5.5 Mesuré après le changement », avec les
chiffres **tels que sortis**, y compris s'ils infirment une prédiction. Une prédiction infirmée
et consignée vaut mieux qu'une prédiction reformulée après coup.

---

## Task 5 : les assertions passent en moyen/moyen

**Fichiers :**
- Modifier : `src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/AbstractTrajectoryOptimizerTest.java:109-168`

- [ ] **Étape 1 : lire la moyenne et asserter dessus**

Remplacer le bloc qui va de `OrbitElements osculating = …` (l.116) jusqu'au log « Insertion orbit
(mean) » inclus (l.138) par le code ci-dessous.

**Le commentaire de dix lignes « Do not read the gap as a targeting miss… » (l.126-133) disparaît
avec le bloc, et c'est voulu** : il expliquait pourquoi l'écart osculateur↔moyen n'était *pas* un
défaut de ciblage tant qu'on visait l'osculatrice. Après ce chantier il est faux. Ne pas le
reporter.

```java
    OrbitElements osculating = OrbitElements.osculating(insertionOrbit);
    // The assertions target the MEAN orbit since the analytic trim aims at it (spec
    // orbit-reporting/02). Asserting the osculating one against a mean target would be the very
    // comparison defect this class fixed on 2026-08-05, mirrored.
    //
    // orElseThrow, not a fallback: a TEST may fail because a report could not be computed — a
    // MISSION may not, which is why MeanPerigeeAim falls back instead (spec 02 section 3.2).
    OrbitElements meanElements =
        OrbitElements.mean(insertionOrbit)
            .orElseThrow(
                () ->
                    new AssertionError(
                        "mean insertion orbit unavailable — the assertions below target it"));
    double insertionPerigee = meanElements.perigeeAltitude();
    double insertionApogee = meanElements.apogeeAltitude();
    logger.info(
        "[{}/{} km] Insertion orbit (osculating): {}",
        (int) (perigeeAltitude / 1000),
        (int) (apogeeAltitude / 1000),
        osculating.format());
    logger.info(
        "[{}/{} km] Insertion orbit (mean, asserted): {}",
        (int) (perigeeAltitude / 1000),
        (int) (apogeeAltitude / 1000),
        meanElements.format());
```

Les deux assertions `±ORBIT_MARGIN_RATIO` en aval sont **inchangées** : seules les deux variables
qu'elles lisent ont changé de convention.

- [ ] **Étape 2 : mettre à jour le javadoc de `FLOWN_PERIGEE_FLOOR_MARGIN_M`**

Son paragraphe « Why the achieved orbit is read at insertion and not from the coast minimum »
justifie encore une lecture osculatrice. Remplacer sa première phrase par :

```java
   * <p><b>Why the achieved orbit is read at insertion and not from the coast minimum.</b> The
   * mission's analytic trim targets the <em>mean</em> perigee (spec orbit-reporting/02), which
   * centres the flown band on the request instead of perching it at the top of the J2 short-period
   * oscillation. The band itself remains ~19 km wide — no orbit is flat under J2 — so sampling the
   * minimum geodetic altitude over a day and comparing it against the target still measures the
   * oscillation, not an insertion error.
```

- [ ] **Étape 3 : compiler**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew testClasses
```

Attendu : BUILD SUCCESSFUL.

---

## Task 6 : reposer le plancher de sécurité

**Dépend de la mesure de T4.** Ne pas exécuter avant.

**Fichiers :**
- Modifier : `src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/AbstractTrajectoryOptimizerTest.java:53`

- [ ] **Étape 1 : calculer la valeur depuis la mesure**

`FLOWN_PERIGEE_FLOOR_MARGIN_M` = **2 × le pire-cas mesuré à l'étape 2 de T4**, arrondi au millier
supérieur. Avec les 9,9 km prédits cela donne 20 000 ; **si la mesure dit autre chose, c'est la
mesure qui gagne.** La valeur ne s'invente pas : c'est la leçon du 01 (« la première version du
plan portait 50 m, inventés »).

- [ ] **Étape 2 : appliquer**

Remplacer `private static final double FLOWN_PERIGEE_FLOOR_MARGIN_M = 40_000.0;` par la valeur
calculée, et remplacer les deux paragraphes de mesure de son javadoc par :

```java
   * <p>Sized at twice the worst-case deviation measured after the mean retargeting (spec
   * orbit-reporting/02 section 5.5), not at a round number chosen a priori. It keeps a genuinely
   * decaying or re-entering trajectory failing while leaving the measured J2 band alone.
```

en conservant le paragraphe « Why the achieved orbit is read at insertion » de T5.

---

## Task 7 : suites courtes

**Fichiers :** aucun

- [ ] **Étape 1 : lancer tout ce qui touche au trim et au converter**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*OrbitElementsTest*" --tests "*AchievedOrbitTest*" --tests "*MeanPerigeeAimTest*" --tests "*StageSeparationStageTest*" --tests "*DepletionGuardTest*" --tests "*ReentryGuardTest*"
```

Attendu : PASS.

- [ ] **Étape 2 : vérifier que chaque suite a réellement tourné**

```bash
grep -o 'name="[^"]*" tests="[0-9]*" skipped="[0-9]*"' build/test-results/test/TEST-*.xml
```

Attendu : `skipped="0"` sur chacune. Un `skipped` non nul veut dire que la suite s'est esquivée
(`orekit-data.zip` absent, flag manquant) et qu'un vert ne prouve rien.

- [ ] **Étape 3 : passer la main**

Les quatre suites longues sont **hors périmètre de l'exécutant** :
`LEOMissionOptimizationTest`, `LEOMissionOptimizedTransferTest`, `GEOMissionOptimizationTest`,
`PropellantLoadOptimizerIntegrationTest`.

Rappeler à l'utilisateur les deux points de la spec §4.1 :

1. la **baseline λ\* LEO doit avoir été prise avant** ce chantier, faute de quoi la
   non-régression LEO n'a rien contre quoi se mesurer ;
2. la prédiction P3 sur le GEO est **λ\* = [0,934375 ; 0,8140625]**, 28 évaluations, 2 passes,
   1 243 619 → 1 160 729 kg. Si λ\* bouge, cela se regarde — cela ne se rationalise pas.
