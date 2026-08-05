# Éléments moyens — plan d'implémentation

> **Pour un exécutant agentique** : utiliser `superpowers:subagent-driven-development` ou
> `superpowers:executing-plans` pour dérouler ce plan tâche par tâche. Les étapes utilisent la
> syntaxe case à cocher (`- [ ]`).

**Spec** : `specs/orbit-reporting/01-elements-moyens.md`

**But** : rapporter l'orbite **moyenne** (Brouwer-Lyddane) en plus de l'orbite osculatrice
partout où OrbitLab rapporte une orbite, sans qu'aucune trajectoire ne change.

**Architecture** : un record `OrbitElements` dans `simulation/`, avec deux fabriques —
`osculating(Orbit)` (formule à deux corps, celle d'aujourd'hui) et `mean(Orbit)`
(Brouwer-Lyddane, rendant un `Optional`). Deux points d'ancrage l'utilisent en lecture seule :
le log d'insertion des tests d'optimisation et `MissionOptimizer` en fin de mission. Aucune
fonction de coût, aucun détecteur, aucune borne CMA-ES n'est touchée.

**Stack** : Java 17+, Orekit 13.1.1
(`org.orekit.propagation.conversion.osc2mean.EcksteinHechlerTheory` +
`FixedPointConverter`), JUnit 5, Log4j 2.

**Commandes** — Gradle exige le JDK 21 :

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*OrbitElementsTest*"
```

> **Tests longs** : ne jamais lancer `LEOMissionOptimizationTest`,
> `LEOMissionOptimizedTransferTest`, `GEOMissionOptimizationTest` ni
> `PropellantLoadOptimizerIntegrationTest` de sa propre initiative. L'utilisateur les lance
> lui-même (plusieurs minutes à ~20 min).

> **Commits** : les étapes de commit sont écrites pour être complètes. Ne les exécuter que si
> l'utilisateur a demandé des commits.

---

## Structure des fichiers

| Fichier | Rôle |
|---|---|
| **Créer** `src/main/java/com/smousseur/orbitlab/simulation/OrbitElements.java` | le record + les deux fabriques + le formatage de log |
| **Créer** `src/test/java/com/smousseur/orbitlab/simulation/OrbitElementsTest.java` | l'oracle : BL contre la formule fermée J2, sur orbites synthétiques |
| **Modifier** `src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/GravityTurnFloorProbeTest.java` | la sonde de mesure (étape 0) |
| **Modifier** `src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/AbstractTrajectoryOptimizerTest.java:105-127` | log d'insertion : ajouter la moyenne, assertions inchangées |
| **Modifier** `src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionOptimizer.java:238` | log de l'orbite finale, osculatrice + moyenne |

Ordre imposé par la spec : le converter (T1, T2), **puis la mesure** (T3), **puis** seulement
le câblage du reporting (T4, T5).

---

## Task 1 : `OrbitElements` — l'orbite osculatrice

**Fichiers :**
- Créer : `src/main/java/com/smousseur/orbitlab/simulation/OrbitElements.java`
- Test : `src/test/java/com/smousseur/orbitlab/simulation/OrbitElementsTest.java`

- [ ] **Étape 1 : écrire le test qui échoue**

Créer `src/test/java/com/smousseur/orbitlab/simulation/OrbitElementsTest.java` :

```java
package com.smousseur.orbitlab.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

class OrbitElementsTest {

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  /**
   * Les altitudes rapportées sont sphériques-équatoriales — {@code a(1±e) − RE} — exactement
   * la convention des sites d'appel d'aujourd'hui (spec 01 §3.3). Ce test la verrouille : la
   * changer pour du géodésique déplacerait tous les chiffres rapportés de ~200 m.
   */
  @Test
  void osculating_reportsSphericalEquatorialApsides() {
    double a = RE + 500_000.0;
    double e = 0.01;
    KeplerianOrbit orbit =
        new KeplerianOrbit(
            a,
            e,
            0.1,
            0.2,
            0.3,
            0.4,
            PositionAngleType.TRUE,
            FramesFactory.getGCRF(),
            AbsoluteDate.J2000_EPOCH,
            Constants.WGS84_EARTH_MU);

    OrbitElements elements = OrbitElements.osculating(orbit);

    assertEquals(a, elements.semiMajorAxis(), 1e-6);
    assertEquals(e, elements.eccentricity(), 1e-12);
    assertEquals(0.1, elements.inclination(), 1e-12);
    assertEquals(a * (1.0 - e) - RE, elements.perigeeAltitude(), 1e-6);
    assertEquals(a * (1.0 + e) - RE, elements.apogeeAltitude(), 1e-6);
  }
}
```

Ce test ne demande **pas** `orekit-data.zip` : il construit une orbite képlérienne pure, sans
champ de gravité ni repère terrestre. Pas d'`Assumptions` ici.

- [ ] **Étape 2 : lancer le test, vérifier qu'il échoue**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*OrbitElementsTest*"
```

Attendu : échec de **compilation** — `cannot find symbol: class OrbitElements`.

- [ ] **Étape 3 : écrire l'implémentation minimale**

Créer `src/main/java/com/smousseur/orbitlab/simulation/OrbitElements.java` :

```java
package com.smousseur.orbitlab.simulation;

import java.util.Locale;
import java.util.Objects;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.Orbit;
import org.orekit.utils.Constants;

/**
 * Les éléments d'une orbite tels qu'OrbitLab les rapporte. Grandeur de <em>compte rendu</em>
 * uniquement : rien ici n'entre dans une propagation, une fonction de coût ou un ciblage.
 *
 * <p><b>Convention d'altitude.</b> Les apsides sont sphériques-équatoriales,
 * {@code a(1±e) − WGS84_EARTH_EQUATORIAL_RADIUS} — la convention déjà en place sur tous les
 * sites d'appel. Elle n'est pas géodésique : à 5,23° d'inclinaison l'écart est de ~180 m
 * (spec 01 §1.1). La garder identique est ce qui rend l'osculatrice et la moyenne comparables
 * ligne à ligne.
 *
 * @param semiMajorAxis demi-grand axe (m)
 * @param eccentricity excentricité
 * @param inclination inclinaison (rad)
 * @param perigeeAltitude altitude du périgée (m)
 * @param apogeeAltitude altitude de l'apogée (m)
 */
public record OrbitElements(
    double semiMajorAxis,
    double eccentricity,
    double inclination,
    double perigeeAltitude,
    double apogeeAltitude) {

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  /** Les éléments osculateurs de {@code orbit}, lus tels quels. */
  public static OrbitElements osculating(Orbit orbit) {
    Objects.requireNonNull(orbit, "orbit");
    return elementsOf(orbit);
  }

  /** Formule d'apsides partagée : mêmes lignes pour l'osculatrice et pour la moyenne. */
  private static OrbitElements elementsOf(Orbit orbit) {
    double a = orbit.getA();
    double e = orbit.getE();
    return new OrbitElements(
        a, e, orbit.getI(), a * (1.0 - e) - RE, a * (1.0 + e) - RE);
  }

  /** Ligne de log compacte, commune à tous les sites de compte rendu. */
  public String format() {
    return String.format(
        Locale.ROOT,
        "%.0f x %.0f m (e=%.3e, i=%.4f deg)",
        perigeeAltitude,
        apogeeAltitude,
        eccentricity,
        FastMath.toDegrees(inclination));
  }
}
```

- [ ] **Étape 4 : lancer le test, vérifier qu'il passe**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*OrbitElementsTest*"
```

Attendu : `BUILD SUCCESSFUL`. Vérifier ensuite dans
`build/test-results/test/TEST-com.smousseur.orbitlab.simulation.OrbitElementsTest.xml` que
`tests="1"` et `skipped="0"` — plusieurs suites du dépôt sautent silencieusement sur une
`Assumption` quand `orekit-data.zip` est absent, celle-ci ne doit pas.

- [ ] **Étape 5 : commit**

```bash
git add src/main/java/com/smousseur/orbitlab/simulation/OrbitElements.java src/test/java/com/smousseur/orbitlab/simulation/OrbitElementsTest.java
git commit -m "Add OrbitElements record reporting osculating apsides in the existing spherical-equatorial convention"
```

---

## Task 2 : `OrbitElements.mean` — Eckstein-Hechler, avec la formule fermée pour oracle

**Fichiers :**
- Modifier : `src/main/java/com/smousseur/orbitlab/simulation/OrbitElements.java`
- Test : `src/test/java/com/smousseur/orbitlab/simulation/OrbitElementsTest.java`

> **Cette tâche a été réécrite le 2026-08-05.** La version initiale employait
> `BrouwerLyddanePropagator.computeMeanOrbit`. La mesure l'a disqualifié : divergence 0/8 à
> `e = 5e-6`, et surtout un périgée moyen qui varie de **8 216 m selon l'anomalie
> d'échantillonnage** parmi les phases où il converge. Voir spec §3.2.1. Ne pas revenir à BL.

**L'idée du test.** On ne peut pas fabriquer l'oscillation J2 en changeant l'anomalie d'un jeu
d'éléments képlériens figés : elle naît de la propagation. On construit donc un
`EcksteinHechlerPropagator` **à partir d'éléments moyens** (`PropagationType.MEAN`), qui génère
la série osculatrice sur une période. Deux propriétés se mesurent alors sans deviner aucune
phase :

1. l'amplitude crête-à-crête du périgée **osculateur** doit valoir `2·a·f`, avec
   `f = (3/2)·J2·(RE/a)²` — la formule fermée de la spec §1.1. Rapport mesuré : 0,983 sur les
   trois cas circulaires. **Ne vaut que sur le quasi-circulaire** : 0,898 sur l'elliptique ;
2. le périgée **moyen** rendu par le converter doit être plat sur toute la série, et égal à
   celui qui a servi d'entrée.

La seconde propriété est celle qui casserait bruyamment si le converter rendait de
l'osculateur déguisé.

**Le degré du champ compte.** `EcksteinHechlerTheory` exige le terme C60 : avec
`getUnnormalizedProvider(5, 0)` elle lève « no term (6, 0) in a 5x0 spherical harmonics
decomposition ». Degré 6.

- [ ] **Étape 1 : écrire les tests qui échouent**

Ajouter à `OrbitElementsTest.java` — imports supplémentaires en tête de fichier :

```java
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider;
import org.orekit.propagation.PropagationType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.EcksteinHechlerPropagator;
```

et les membres suivants :

```java
  private static final Logger logger = LogManager.getLogger(OrbitElementsTest.class);

  /** J2 = −C20, champ WGS84 — la constante de la formule fermée de la spec §1.1. */
  private static final double J2 = -Constants.WGS84_EARTH_C20;

  /** Kourou, {@code EarthMission.DEFAULT_LATITUDE} : l'inclinaison des profils mesurés. */
  private static final double INCLINATION = FastMath.toRadians(5.23);

  /**
   * Quasi-circulaire, à l'ordre de grandeur des insertions réelles (e mesurée entre 4,7e-6 et
   * 6,3e-6). C'est le régime qui a disqualifié Brouwer-Lyddane (spec §3.2.1) : le tester ici
   * est tout l'intérêt de l'oracle.
   */
  private static final double ECCENTRICITY = 1.0e-5;

  private static final int SAMPLES = 200;

  /**
   * Marge de l'accord entre Brouwer-Lyddane (J2..J5, exact) et la formule fermée (J2, premier
   * ordre). Les mesures numériques de la spec §1.1 s'accordent à 1,2 % ; 20 % laisse la place
   * aux zonaux impairs sans laisser passer une erreur de signe, de facteur ou de convention.
   */
  private static final double CLOSED_FORM_TOLERANCE = 0.20;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /**
   * L'oracle de la spec §5.2, aux trois altitudes déjà mesurées. Génère la série osculatrice
   * d'une orbite moyenne connue, puis vérifie que le converter en retire exactement
   * l'oscillation que la formule fermée prédit.
   */
  @Test
  void mean_removesTheShortPeriodJ2OscillationTheClosedFormPredicts() {
    for (double altitude : new double[] {400_000.0, 600_000.0, 1_000_000.0}) {
      double a = RE + altitude;
      double f = 1.5 * J2 * (RE / a) * (RE / a);
      double predictedSpan = 2.0 * a * f;

      UnnormalizedSphericalHarmonicsProvider provider =
          GravityFieldFactory.getUnnormalizedProvider(6, 0);
      KeplerianOrbit meanInput =
          new KeplerianOrbit(
              a,
              ECCENTRICITY,
              INCLINATION,
              0.0,
              0.0,
              0.0,
              PositionAngleType.MEAN,
              FramesFactory.getGCRF(),
              AbsoluteDate.J2000_EPOCH,
              provider.getMu());
      EcksteinHechlerPropagator propagator =
          new EcksteinHechlerPropagator(meanInput, provider, PropagationType.MEAN);

      double period = 2.0 * FastMath.PI * FastMath.sqrt(a * a * a / provider.getMu());
      double oscMin = Double.MAX_VALUE;
      double oscMax = -Double.MAX_VALUE;
      double meanMin = Double.MAX_VALUE;
      double meanMax = -Double.MAX_VALUE;

      for (int i = 0; i < SAMPLES; i++) {
        SpacecraftState state =
            propagator.propagate(AbsoluteDate.J2000_EPOCH.shiftedBy(i * period / SAMPLES));
        KeplerianOrbit sampled = new KeplerianOrbit(state.getOrbit());

        double oscPerigee = OrbitElements.osculating(sampled).perigeeAltitude();
        oscMin = FastMath.min(oscMin, oscPerigee);
        oscMax = FastMath.max(oscMax, oscPerigee);

        Optional<OrbitElements> mean = OrbitElements.mean(sampled);
        assertTrue(mean.isPresent(), "mean orbit unavailable at sample " + i);
        double meanPerigee = mean.get().perigeeAltitude();
        meanMin = FastMath.min(meanMin, meanPerigee);
        meanMax = FastMath.max(meanMax, meanPerigee);
      }

      double measuredSpan = oscMax - oscMin;
      double meanSpan = meanMax - meanMin;
      logger.info(
          "[{} km] osculating perigee span={} m, closed form 2af={} m, ratio={} | mean span={} m",
          (int) (altitude / 1000),
          String.format(Locale.ROOT, "%.0f", measuredSpan),
          String.format(Locale.ROOT, "%.0f", predictedSpan),
          String.format(Locale.ROOT, "%.3f", measuredSpan / predictedSpan),
          String.format(Locale.ROOT, "%.1f", meanSpan));

      double ratio = measuredSpan / predictedSpan;
      assertTrue(
          FastMath.abs(ratio - 1.0) <= CLOSED_FORM_TOLERANCE,
          () ->
              String.format(
                  Locale.ROOT,
                  "osculating perigee span %.0f m disagrees with the closed form %.0f m"
                      + " (ratio %.3f, tolerance ±%.0f%%)",
                  measuredSpan,
                  predictedSpan,
                  ratio,
                  100.0 * CLOSED_FORM_TOLERANCE));

      // La propriété qui définit un élément moyen : les termes courte période sont partis.
      // 50 m est trois ordres de grandeur sous les ~19 km de l'oscillation osculatrice.
      assertTrue(
          meanSpan <= 50.0,
          () ->
              String.format(
                  Locale.ROOT,
                  "mean perigee still oscillates by %.1f m over one period — the converter is"
                      + " returning osculating elements",
                  meanSpan));
    }
  }

  /**
   * Le mode dégradé de la spec §3.4 : une entrée que la théorie ne peut pas traiter rend un
   * {@code Optional} vide, jamais une exception. Une mission ne doit pas échouer parce qu'un
   * log n'a pas pu être calculé. L'orbite hyperbolique est le cas le plus net : la théorie
   * n'a aucun sens dessus.
   */
  @Test
  void mean_returnsEmptyRatherThanThrowing() {
    KeplerianOrbit hyperbolic =
        new KeplerianOrbit(
            -(RE + 500_000.0),
            1.5,
            INCLINATION,
            0.0,
            0.0,
            0.1,
            PositionAngleType.TRUE,
            FramesFactory.getGCRF(),
            AbsoluteDate.J2000_EPOCH,
            Constants.WGS84_EARTH_MU);

    assertTrue(OrbitElements.mean(hyperbolic).isEmpty());
  }
```

Ajouter aussi `import java.util.Locale;` en tête du fichier de test.

- [ ] **Étape 2 : lancer les tests, vérifier qu'ils échouent**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*OrbitElementsTest*"
```

Attendu : échec de compilation — `cannot find symbol: method mean(Orbit)`.

- [ ] **Étape 3 : écrire l'implémentation**

Dans `OrbitElements.java`, ajouter les imports :

```java
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.conversion.osc2mean.EcksteinHechlerTheory;
import org.orekit.propagation.conversion.osc2mean.FixedPointConverter;
```

et les membres suivants au record :

```java
  private static final Logger logger = LogManager.getLogger(OrbitElements.class);

  /**
   * Degré du champ zonal. Eckstein-Hechler a besoin du terme C60 : au degré 5 elle lève
   * « no term (6, 0) in a 5x0 spherical harmonics decomposition ».
   */
  private static final int ZONAL_DEGREE = 6;

  /** Réglages du point fixe. Mesuré : la conversion converge en 5 itérations. */
  private static final double CONVERGENCE_THRESHOLD = 1.0e-12;

  private static final int MAX_ITERATIONS = 500;

  /** Pas d'amortissement : mesuré inutile ici, il ne ferait que ralentir la convergence. */
  private static final double DAMPING = 1.0;

  /**
   * Le provider zonal, construit paresseusement. Volontairement <b>pas</b> un holder statique :
   * son initialisation demande {@code orekit-data.zip}, et un holder qui échoue laisse la
   * classe définitivement inutilisable ({@code NoClassDefFoundError} sur tous les accès
   * suivants). Ici l'échec est rattrapé par {@link #mean(Orbit)} et ne coûte qu'un log.
   * Course bénigne : deux threads peuvent construire deux providers équivalents.
   */
  private static volatile UnnormalizedSphericalHarmonicsProvider zonalProvider;

  /**
   * Les éléments <b>moyens</b> de {@code orbit} — l'orbite débarrassée des termes courte
   * période, c'est-à-dire l'orbite de mission plutôt que l'instantané.
   *
   * <p>Eckstein-Hechler, et non Brouwer-Lyddane : mesuré contre un référé sans théorie
   * (moyennes équinoxiales des éléments osculateurs sur une période, champ 8×8), EH tombe à
   * ~200 m sur tout le domaine d'excentricité utile, là où BL diverge ou, pire, rend un
   * périgée moyen qui varie de 8 216 m selon l'anomalie d'échantillonnage (spec 01 §3.2.1).
   *
   * <p>Rend {@code Optional.empty()} plutôt que de lever : un point fixe peut ne pas
   * converger, et <b>aucune mission ne doit échouer parce qu'un compte rendu n'a pas pu être
   * calculé</b> (spec 01 §3.4).
   */
  public static Optional<OrbitElements> mean(Orbit orbit) {
    Objects.requireNonNull(orbit, "orbit");
    try {
      UnnormalizedSphericalHarmonicsProvider provider = zonalProvider();
      // Rebâtie sur le µ du provider : le mélanger avec WGS84_EARTH_MU décale les éléments de
      // l'ordre du mètre, qu'on lirait comme du J2 (spec 01 §3.3).
      KeplerianOrbit rebased =
          new KeplerianOrbit(
              orbit.getPVCoordinates(), orbit.getFrame(), orbit.getDate(), provider.getMu());
      // Neuf à chaque appel : le converter porte un compteur d'itérations, donc un état.
      FixedPointConverter converter =
          new FixedPointConverter(
              new EcksteinHechlerTheory(provider),
              CONVERGENCE_THRESHOLD,
              MAX_ITERATIONS,
              DAMPING);
      return Optional.of(elementsOf(converter.convertToMean(rebased)));
    } catch (RuntimeException e) {
      logger.debug("Mean orbit unavailable ({}): {}", e.getClass().getSimpleName(), e.getMessage());
      return Optional.empty();
    }
  }

  private static UnnormalizedSphericalHarmonicsProvider zonalProvider() {
    UnnormalizedSphericalHarmonicsProvider local = zonalProvider;
    if (local == null) {
      local = GravityFieldFactory.getUnnormalizedProvider(ZONAL_DEGREE, 0);
      zonalProvider = local;
    }
    return local;
  }
```

- [ ] **Étape 4 : lancer les tests, vérifier qu'ils passent**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*OrbitElementsTest*"
```

Attendu : `BUILD SUCCESSFUL`, 3 tests. **Vérifier `skipped="0"`** dans
`build/test-results/test/TEST-com.smousseur.orbitlab.simulation.OrbitElementsTest.xml` : si
`orekit-data.zip` manque, les deux tests BL sautent sur l'`Assumption` et le vert ne prouve
rien.

Relever dans la sortie les trois lignes `osculating perigee span=… ratio=…`. Les rapports
attendus sont proches de 1,0 ; les portées mesurées doivent être voisines de 19 493 / 18 934 /
17 908 m (spec §1.1).

- [ ] **Étape 5 : commit**

```bash
git add src/main/java/com/smousseur/orbitlab/simulation/OrbitElements.java src/test/java/com/smousseur/orbitlab/simulation/OrbitElementsTest.java
git commit -m "Add Brouwer-Lyddane mean-orbit conversion, validated against the closed-form J2 short-period amplitude"
```

---

## Task 3 : la sonde — mesurer l'écart osculateur↔moyen (étape 0 de la spec)

**Fichiers :**
- Modifier : `src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/GravityTurnFloorProbeTest.java`

C'est la tâche qui **mesure**, avant que le §2 de la spec ne soit tenu pour acquis. Elle
n'assert rien : elle imprime.

- [ ] **Étape 1 : exposer l'état d'insertion dans le record `Flight`**

`fly()` s'arrête déjà sur la phase « Coasting » et rend l'état que la dernière poussée a
transmis — c'est exactement l'état d'insertion. Il n'est simplement pas conservé.

Dans `GravityTurnFloorProbeTest.java`, ajouter un champ au record `Flight` (l.108-124), juste
après `perStagePerigeeApogee` :

```java
      Map<String, double[]> perStagePerigeeApogee,
      SpacecraftState insertionState) {
```

et, dans `fly()` (l.202-218), passer `last` comme dernier argument du constructeur :

```java
        perStage,
        last);
```

- [ ] **Étape 2 : ajouter le test de mesure**

Ajouter à `GravityTurnFloorProbeTest.java`, dans la section « Défaut B », les imports
`com.smousseur.orbitlab.simulation.OrbitElements` et `java.util.Optional`, puis :

```java
  /**
   * Étape 0 du chantier « éléments moyens » (spec orbit-reporting/01 §5.1). Read-only : mesure,
   * à l'insertion, l'écart entre l'orbite osculatrice — celle que les étages analytiques visent
   * et touchent au mètre — et l'orbite moyenne, celle que l'utilisateur a demandée.
   *
   * <p>La prédiction à vérifier : l'insertion tombant en haut de l'oscillation J2, la moyenne
   * devrait se lire autour de {@code cible − a·f}, soit ~9,7 km sous la cible à 400 km. Si le
   * signe s'inverse, ou s'il diffère d'un profil à l'autre, l'insertion ne tombe pas au même
   * point de l'oscillation selon le profil et le §2 de la spec doit être réécrit.
   */
  @Test
  void meanVersusOsculatingAtInsertion() {
    meanOrbitProbe(
        "FH-400", falconHeavyBudgetLoads(), falconHeavyBudgetLoads(), FH_400_RETAINED, 400_000);
    meanOrbitProbe(
        "LEO-200x1000", elliptic200x1000(), elliptic200x1000(), ELLIPTIC_RETAINED, 200_000);
  }

  private static void meanOrbitProbe(
      String tag, LEOMission mission, LEOMission twin, double[] vars, double targetPerigee) {
    double stagingComplete =
        stagingCompleteTime(twin, Launchers.FALCON_HEAVY.ascentProfile());
    Flight flight = fly(mission, vars, stagingComplete);

    KeplerianOrbit insertion =
        new KeplerianOrbit(
            flight.insertionState().getPVCoordinates(),
            flight.insertionState().getFrame(),
            flight.insertionState().getDate(),
            Constants.WGS84_EARTH_MU);

    OrbitElements osculating = OrbitElements.osculating(insertion);
    Optional<OrbitElements> mean = OrbitElements.mean(insertion);

    double a = osculating.semiMajorAxis();
    double j2 = -Constants.WGS84_EARTH_C20;
    double af = a * 1.5 * j2 * (RE / a) * (RE / a);

    logger.info("[M/{}] osculating at insertion: {}", tag, osculating.format());
    if (mean.isEmpty()) {
      logger.warn("[M/{}] mean orbit UNAVAILABLE — Brouwer-Lyddane did not converge", tag);
      return;
    }
    logger.info("[M/{}] mean at insertion:       {}", tag, mean.get().format());
    logger.info(
        "[M/{}] target perigee={} m | osculating−target={} m | mean−target={} m"
            + " | mean−osculating={} m | closed-form a·f={} m",
        tag,
        fmt(targetPerigee, 0),
        fmt(osculating.perigeeAltitude() - targetPerigee, 0),
        fmt(mean.get().perigeeAltitude() - targetPerigee, 0),
        fmt(mean.get().perigeeAltitude() - osculating.perigeeAltitude(), 0),
        fmt(af, 0));
  }
```

- [ ] **Étape 3 : lancer la sonde**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*GravityTurnFloorProbeTest.meanVersusOsculatingAtInsertion*" -Dorbitlab.probe=true
```

Attendu : `BUILD SUCCESSFUL` en quelques secondes, et huit lignes `[M/…]`. Sans
`-Dorbitlab.probe=true` la classe entière est désactivée par `@EnabledIfSystemProperty` et ne
mesure rien.

- [ ] **Étape 4 : POINT D'ARRÊT — rendre les chiffres à l'utilisateur**

Ne pas enchaîner. Rapporter les quatre écarts mesurés (`mean−osculating` et `mean−target` sur
les deux profils) et dire explicitement si la prédiction du §2 tient :

- `mean − osculating ≈ −a·f` sur les deux profils → le §2 tient, rien à réécrire ;
- signe opposé, ou signes différents d'un profil à l'autre → le §2 est faux et doit être
  corrigé avec les chiffres avant que les tâches 4 et 5 ne soient écrites.

- [ ] **Étape 5 : commit**

```bash
git add src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/GravityTurnFloorProbeTest.java
git commit -m "Probe the osculating-to-mean insertion offset on both calibrated LEO profiles"
```

---

## Task 4 : le log d'insertion des tests d'optimisation

**Fichiers :**
- Modifier : `src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/AbstractTrajectoryOptimizerTest.java:105-127`

**Contrainte de la tâche** : les deux assertions l.144-157 lisent `insertionPerigee` et
`insertionApogee`. Ces deux valeurs doivent rester **numériquement identiques**.
`OrbitElements.osculating` applique exactement la même formule (`a(1±e) − RE`) ; la
substitution est donc sûre, et c'est la vérification de l'étape 3 qui le confirme.

- [ ] **Étape 1 : remplacer le calcul manuel et ajouter la ligne moyenne**

Ajouter les imports `com.smousseur.orbitlab.simulation.OrbitElements` et `java.util.Optional`.

Remplacer l.109-127 (de `KeplerianOrbit insertionOrbit =` jusqu'à la fin du `logger.info` de
« Insertion orbit ») par :

```java
    KeplerianOrbit insertionOrbit =
        new KeplerianOrbit(
            new PVCoordinates(insertion.position(), insertion.velocity()),
            OrekitService.get().gcrf(),
            insertion.time(),
            Constants.WGS84_EARTH_MU);
    OrbitElements osculating = OrbitElements.osculating(insertionOrbit);
    double insertionPerigee = osculating.perigeeAltitude();
    double insertionApogee = osculating.apogeeAltitude();
    logger.info(
        "[{}/{} km] Insertion orbit (osculating): {}",
        (int) (perigeeAltitude / 1000),
        (int) (apogeeAltitude / 1000),
        osculating.format());

    // La grandeur que l'utilisateur a demandée : l'orbite de mission, débarrassée de
    // l'oscillation J2 courte période de ~19 km (spec orbit-reporting/01). Rapportée en plus
    // de l'osculatrice, jamais à la place : les assertions ci-dessous restent sur
    // l'osculatrice, seule grandeur que les étages analytiques visent réellement.
    Optional<OrbitElements> mean = OrbitElements.mean(insertionOrbit);
    logger.info(
        "[{}/{} km] Insertion orbit (mean):       {}",
        (int) (perigeeAltitude / 1000),
        (int) (apogeeAltitude / 1000),
        mean.map(OrbitElements::format).orElse("unavailable"));
```

- [ ] **Étape 2 : vérifier que rien d'autre n'a bougé**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew compileTestJava
```

Attendu : `BUILD SUCCESSFUL`. Relire ensuite le diff et confirmer que les deux blocs
`Assertions.assertTrue` (l.144-157 d'origine) sont **inchangés au caractère près**, de même que
`errorApogeeMargin`, `errorPerigeeMargin` et le contrôle de plancher `FLOWN_PERIGEE_FLOOR_MARGIN_M`.

- [ ] **Étape 3 : vérifier sur une suite courte**

Les tests qui héritent de cette classe sont tous longs et sont lancés par l'utilisateur. Le
seul contrôle bon marché est la sonde de la tâche 3, qui partage la chaîne de propagation :

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*GravityTurnFloorProbeTest.meanVersusOsculatingAtInsertion*" -Dorbitlab.probe=true
```

Attendu : `BUILD SUCCESSFUL`, chiffres identiques à ceux de la tâche 3.

- [ ] **Étape 4 : commit**

```bash
git add src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/AbstractTrajectoryOptimizerTest.java
git commit -m "Log the mean insertion orbit alongside the osculating one, assertions unchanged"
```

---

## Task 5 : l'orbite finale dans `MissionOptimizer`

**Fichiers :**
- Modifier : `src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionOptimizer.java:238`

À la l.237 la boucle de phases est terminée et `mission.getCurrentState()` porte l'état final.
Aucune plomberie, aucun appariement de nom de phase.

- [ ] **Étape 1 : ajouter l'appel et la méthode**

Ajouter les imports `com.smousseur.orbitlab.simulation.OrbitElements`,
`java.util.Optional`, `org.orekit.orbits.KeplerianOrbit` et `org.orekit.utils.Constants` s'ils
ne sont pas déjà présents.

Après la l.238 (`logReport(report);`) :

```java
    logAchievedOrbit(mission.getCurrentState());
```

et la méthode, à côté de `logReport` :

```java
  /**
   * L'orbite atteinte en fin de mission, dans les deux conventions (spec orbit-reporting/01) :
   * l'osculatrice, qui est la convention de précision d'un lanceur, et la moyenne, qui est
   * celle de l'orbite de mission que l'utilisateur a demandée.
   *
   * <p>Compte rendu pur : aucune de ces deux valeurs n'est relue par quoi que ce soit. Entre
   * l'insertion et la fin du coast, l'osculatrice a oscillé d'environ 19 km sous J2 pendant que
   * la moyenne ne bougeait que de sa dérive séculaire — un écart comparable sur les deux
   * lignes signalerait que la conversion est fausse.
   */
  private static void logAchievedOrbit(SpacecraftState state) {
    KeplerianOrbit orbit =
        new KeplerianOrbit(
            state.getPVCoordinates(),
            state.getFrame(),
            state.getDate(),
            Constants.WGS84_EARTH_MU);
    logger.info("Achieved orbit (osculating): {}", OrbitElements.osculating(orbit).format());
    Optional<OrbitElements> mean = OrbitElements.mean(orbit);
    logger.info(
        "Achieved orbit (mean):       {}",
        mean.map(OrbitElements::format).orElse("unavailable"));
  }
```

- [ ] **Étape 2 : compiler**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew classes
```

Attendu : `BUILD SUCCESSFUL`.

- [ ] **Étape 3 : vérifier que les suites courtes voisines restent vertes**

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*OrbitElementsTest*" --tests "*StageSeparationStageTest*" --tests "*DepletionGuardTest*" --tests "*ReentryGuardTest*"
```

Attendu : `BUILD SUCCESSFUL`. Vérifier `skipped="0"` dans `build/test-results/test/` pour
chacune des quatre suites.

- [ ] **Étape 4 : commit**

```bash
git add src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionOptimizer.java
git commit -m "Report the achieved orbit in both osculating and mean conventions at mission end"
```

---

## Task 6 : clôture — non-régression et mise à jour de la spec

- [ ] **Étape 1 : rendre la main pour les suites longues**

Demander à l'utilisateur de lancer, et **ne pas les lancer soi-même** :

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*LEOMissionOptimizationTest*"
```

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*GEOMissionOptimizationTest*"
```

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew test --tests "*PropellantLoadOptimizerIntegrationTest*"
```

Références à retrouver **à l'identique** (spec §4) :

| Profil | Référence |
|---|---|
| `geoMultiStage` | λ*=[0,9344 ; 0,8141] / 28 évals / 2 passes / −82 890 kg / ~6 min |
| LEO simple-λ | λ*≈0,4313 |
| LEO FH-400 / 600 / 1000 / 200×1000 | insertions osculatrices du §1.1, au mètre |

Un seul chiffre qui bouge est un bug du chantier, pas une tolérance à élargir.

- [ ] **Étape 2 : reporter les mesures dans la spec**

Compléter `specs/orbit-reporting/01-elements-moyens.md` :

- §2 : remplacer la prédiction par les écarts mesurés à la tâche 3, ou la réécrire si le signe
  ne tient pas ;
- §5.2 : consigner les trois rapports `mesuré / 2af` de la tâche 2 ;
- §5.1 : consigner le tableau de la sonde ;
- en-tête : passer le **Statut** de « validé, à implémenter » à « implémenté, non-régression
  vérifiée » une fois les trois suites longues confirmées.

- [ ] **Étape 3 : commit**

```bash
git add specs/orbit-reporting/01-elements-moyens.md
git commit -m "Record the measured osculating-to-mean offsets in the mean-elements spec"
```
