# Phase 0 — Niveau 0 : alignement de la métrique de coût (géocentrique → géodésique)

Spec technique détaillée du **Niveau 0** annoncé dans
[`04-phase0-baseline-analysis.md`](04-phase0-baseline-analysis.md) §
« Niveau 0 — Pré-requis : aligner la métrique de coût (P1) ».

Ce document remplace l'ébauche analytique (`sin(i)`) du doc 04 par une
solution géodésique directe et autoporteuse.

## 1. Contexte et motivation

La suite paramétrique `LEOAltitudeSweepTest` exécutée sur
`[185, 250, 400, 600, 800, 1200, 1500, 2000]` km montre un biais constant
sur l'erreur d'apoapsis :

| target | apoErr (mesuré) |
|---|---|
| 185 km | **+13 033 m** |
| 250 km | **+12 953 m** |
| 400 km | **+11 686 m** |
| 600 km | **+14 420 m** |
| 800 km | **+13 389 m** |
| 1200 km | **+16 101 m** |
| 1500 km | **+16 042 m** |
| 2000 km | **+16 095 m** |

Tableau extrait de `04-phase0-baseline-analysis.md` § « Tableau récap ».

Tant que ce biais ~+13 km existe, le seuil `acceptableCost = 8e-4`
(`TransferTwoManeuverProblem.getAcceptableCost`) n'a aucune signification
absolue : tous les correctifs des Niveaux 1-3 sont évalués dans le bruit
du biais. C'est pourquoi le doc 04 priorise ce correctif en Niveau 0.

## 2. Diagnostic technique

### 2.1 Côté optimiseur — mesure géocentrique

`TransferTwoManeuverProblem.computeCost`
(`src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/TransferTwoManeuverProblem.java`,
lignes 225-232) :

```java
double apoapsis  = finalOrbit.getA() * (1.0 + finalOrbit.getE());
double periapsis = finalOrbit.getA() * (1.0 - finalOrbit.getE());
double rTarget   = aTarget;

double targetAlt = rTarget   - EARTH_RADIUS;   // EARTH_RADIUS = WGS84 équatorial
double apoAlt    = apoapsis  - EARTH_RADIUS;
double periAlt   = periapsis - EARTH_RADIUS;
```

Avec `EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS = 6 378 137 m`
(ligne 38). C'est une simple soustraction : altitude **géocentrique** par
rapport au **rayon équatorial**.

### 2.2 Côté test/mission — mesure géodésique

`Mission.computeAltitudeMeters`
(`src/main/java/com/smousseur/orbitlab/simulation/mission/Mission.java`,
lignes 60-64) :

```java
public double computeAltitudeMeters(SpacecraftState state) {
  OneAxisEllipsoid earth = OrekitService.get().getEarthEllipsoid();
  GeodeticPoint gp = earth.transform(state.getPosition(), state.getFrame(), state.getDate());
  return gp.getAltitude();
}
```

Utilise `OneAxisEllipsoid.transform()` d'Orekit, qui convertit une position
ECI/ECEF en `GeodeticPoint` exprimé dans l'ellipsoïde **WGS84**
(`a = 6 378 137 m`, `f = 1/298.257223563`).

Chaîne d'usage :

- `MissionEphemerisGenerator` (lignes 69, 98) appelle
  `mission.computeAltitudeMeters(state)` pour échantillonner la trajectoire.
- `LEOMissionOptimizationTest` et `LEOAltitudeSweepTest` lisent
  `MissionEphemerisPoint.altitudeMeters()` pour leurs assertions.

### 2.3 Inconsistance interne secondaire (signalée, hors périmètre)

`MinAltitudeTracker.computeAltitudeMeters`
(`src/main/java/com/smousseur/orbitlab/simulation/mission/detector/MinAltitudeTracker.java`,
ligne 80) :

```java
double altitude = state.getPVCoordinates().getPosition().getNorm()
                  - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
```

Approche géocentrique elle aussi, alimentant
`barrierBelow(tracker.getMinAltitude(), ALT_MIN)` dans `computeCost`
(ligne 252). À aligner dans une étape ultérieure dédiée — non couvert ici
pour rester strictement focalisé sur l'objectif Niveau 0.

## 3. Analyse des approches

### 3.1 Approche A — compensation analytique `sin(i)` (écartée)

Initialement proposée par `04-phase0-baseline-analysis.md` § « Niveau 0 » :

```java
double sinPhi = FastMath.sin(initialOrbit.getI()); // φ_apo ≈ inclination
double f = Constants.WGS84_EARTH_FLATTENING;
double e2 = 2 * f - f * f;
double rEllipsoid = EARTH_RADIUS *
    FastMath.sqrt((1 - e2 * (2 - e2) * sinPhi * sinPhi)
                / (1 - e2 * sinPhi * sinPhi));
double geodeticOffset = EARTH_RADIUS - rEllipsoid; // ~+10.7 km à i=45°
double effectiveTargetAlt = targetAltitude + altitudeOffset + geodeticOffset;
```

**Limites** :

1. **Hypothèse `φ_apo ≈ i`** : à l'apogée d'une orbite osculatrice
   (anomalie vraie ν = π), la latitude vaut
   `sin(φ_apo) = sin(i) · sin(ω + π) = −sin(i) · sin(ω)` où ω est
   l'argument du périgée. La latitude réelle de l'apogée peut tomber
   n'importe où dans `[−i, +i]` ; appliquer `sin(i)` revient à supposer
   le cas pire (apogée à la latitude maximale), donc à sur-corriger.
2. **Fragilité à la généralisation** : la formule fige l'inclinaison du
   transfert (`initialOrbit.getI()`) comme proxy de la latitude
   d'apogée. Cette équivalence ne tient que dans le cas particulier
   actuel (LEOMission Kourou avec inclinaison cible ≈ latitude site,
   ω peu contraint).
   - Mission à **inclinaison cible spécifique** (ex. ISS 51.6°,
     SSO 97°, équatoriale 0°) : la latitude d'apogée se découple de `i`.
   - **Niveau 5 (anticipation GTO)** annoncé en doc 04 § « Niveau 5 » :
     orbite elliptique avec apogée à une longitude/latitude données
     par mission — la formule analytique ne s'adapte plus.

L'affirmation « la valeur ne dépend que de l'inclinaison initiale,
déjà disponible — Risque : nul » du doc 04 § « Niveau 0 » est donc
trop optimiste dès qu'on dépasse le cas de référence.

### 3.2 Approche B — calcul géodésique direct (retenue)

Réutiliser **la même primitive Orekit que le test** :
`OneAxisEllipsoid.transform(position, frame, date)`. Pour chaque
évaluation de la cost function :

1. Construire l'orbite osculatrice à l'apogée (ν = π) et au périgée
   (ν = 0) à partir des éléments képlériens de `finalOrbit`.
2. Récupérer la position 3D via `getPVCoordinates().getPosition()`.
3. Convertir en `GeodeticPoint` puis lire `getAltitude()`.

**Avantages** :

- **Aligné par construction** avec `Mission.computeAltitudeMeters`
  utilisé par tests et ephemeris ; le biais ~+13 km disparaît
  par identité de méthode, sans calibration.
- **Indépendant** de `i`, `ω`, et de la latitude réelle de l'apogée.
  Une mission à inclinaison cible spécifique ou un futur cas GTO
  passe sans modification.
- **Coût négligeable** : deux `transform` (apogée + périgée) par
  appel `computeCost`, dont la complexité est dominée par la
  propagation Orekit en amont (plusieurs secondes de simulation
  intégrée).
- **Primitive éprouvée** : déjà utilisée par `Mission` et
  `MissionEphemerisGenerator` en production.

## 4. Spécification de la modification

**Fichier visé** :
`src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/TransferTwoManeuverProblem.java`

### 4.1 Constructeur (lignes 100-117)

Conserver la compensation J2 court-période existante. Stocker l'altitude
géodésique cible directement, en complément de `aTarget` (utilisé pour
`vCircTarget`).

```java
// ── J2 short-period altitude compensation ── (inchangé)
double rNominal = EARTH_RADIUS + targetAltitude;
double j2 = 1.0826e-3;
double sinI = FastMath.sin(initialOrbit.getI());
double j2Amplitude = j2 * EARTH_RADIUS * EARTH_RADIUS / rNominal * (1.0 - 1.5 * sinI * sinI);
double altitudeOffset = j2Amplitude / 2.0;

double effectiveTargetAlt = targetAltitude + altitudeOffset;
double rTarget = EARTH_RADIUS + effectiveTargetAlt;

this.effectiveTargetAlt = effectiveTargetAlt;   // NOUVEAU champ final
this.aTarget = rTarget;                          // conservé pour vCircTarget
this.vCircTarget = FastMath.sqrt(mu / rTarget);  // conservé
```

Note : `aTarget` reste utilisé pour `vCircTarget` ; la simplification
géocentrique sur la vitesse circulaire est sans effet significatif
(`W_V = 1.0` ; `errV` compare à une vitesse radiale qui doit tendre
vers zéro ; sensibilité au flattening de l'ordre de
`0.5 · ΔR/R ≈ 8·10⁻⁴`, négligeable au regard de la cost function).

### 4.2 `computeCost` (lignes 215-260)

Remplacer le calcul géocentrique par un appel au helper géodésique.

```java
@Override
public double computeCost(SpacecraftState state) {
  double elapsed = state.getDate().durationFrom(initialState.getDate());
  if (elapsed < 1.0) {
    return 1e6;
  }

  KeplerianOrbit finalOrbit = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(state.getOrbit());

  OneAxisEllipsoid earth = OrekitService.get().getEarthEllipsoid();
  double apoAlt  = computeGeodeticAltitude(finalOrbit, FastMath.PI, earth);   // ν = π
  double periAlt = computeGeodeticAltitude(finalOrbit, 0.0,         earth);   // ν = 0
  double targetAlt = effectiveTargetAlt;

  double errApo  = (apoAlt  - targetAlt) / targetAlt;
  double errPeri = (periAlt - targetAlt) / targetAlt;
  double errE    = finalOrbit.getE();
  double errV    = Physics.computeRadialVelocity(state) / vCircTarget;

  double objective =
        W_APO  * errApo  * errApo
      + W_PERI * errPeri * errPeri
      + W_E    * errE    * errE
      + W_V    * errV    * errV;

  double barrier = 0.0;
  barrier += barrierBelow(periAlt, PERIAPSIS_MIN);   // périapsis géodésique

  double altMaxPenalty = 0.0;
  MinAltitudeTracker tracker = lastResult != null ? lastResult.altitudeTracker() : null;
  if (tracker != null) {
    barrier += barrierBelow(tracker.getMinAltitude(), ALT_MIN);
    if (tracker.getMaxAltitude() > altMax) {
      double excess = (tracker.getMaxAltitude() - altMax) / altMax;
      altMaxPenalty = excess * excess;
    }
  }

  return objective + W_BARRIER * barrier + W_ALT_MAX * altMaxPenalty;
}

private static double computeGeodeticAltitude(
    KeplerianOrbit src, double trueAnomaly, OneAxisEllipsoid earth) {
  KeplerianOrbit at = new KeplerianOrbit(
      src.getA(), src.getE(), src.getI(),
      src.getPerigeeArgument(), src.getRightAscensionOfAscendingNode(),
      trueAnomaly, PositionAngleType.TRUE,
      src.getFrame(), src.getDate(), src.getMu());
  Vector3D pos = at.getPVCoordinates().getPosition();
  return earth.transform(pos, src.getFrame(), src.getDate()).getAltitude();
}
```

Imports additionnels (à ajouter en tête de fichier) :

```java
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.orbits.PositionAngleType;
import com.smousseur.orbitlab.simulation.OrekitService;
```

### 4.3 Hors périmètre (à traiter ultérieurement)

- **`MinAltitudeTracker`** (alignement géodésique du min altitude tracker
  utilisé dans le barrier `barrierBelow(tracker.getMinAltitude(), ALT_MIN)`).
  Ce barrier protège contre la rentrée atmosphérique pendant la
  trajectoire ; sa cohérence avec la cost function renforcerait
  l'alignement complet, mais n'est pas requis pour résorber le biais
  d'apoapsis du Niveau 0.
- **Recalcul géodésique de `vCircTarget`** : effet de second ordre
  (`~8·10⁻⁴` relatif), non significatif.

## 5. Critères d'acceptation

1. `LEOMissionOptimizationTest` (cible 400 km, marge de 7 %)
   continue de passer.
2. `LEOAltitudeSweepTest` :
   - `apoErr` médian < **2 km** à 400 km (baseline +11.7 km).
   - Le biais constant ~+13 km est supprimé sur les 8 altitudes
     (185 → 2000 km). Les `apoErr` résiduels reflètent désormais
     uniquement les pathologies P2/P3/P4 (à traiter aux Niveaux 1-3),
     non un mismatch de mesure.
3. Les pathologies P2 (effondrement passation GT → transfert),
   P3 (bornes burn 1) et P4 (bornes GT non adaptatives) restent
   visibles et inchangées — non traitées au Niveau 0.

## 6. Procédure de vérification

```bash
./gradlew classes
./gradlew test --tests LEOMissionOptimizationTest
./gradlew test --tests LEOAltitudeSweepTest
```

Comparer les `apoErr` à la baseline tabulée § 1 ci-dessus et au
tableau « Mesures Phase 0 » du doc 04.

**Validation croisée** : ajouter un log temporaire (à retirer après
validation) dans `computeCost` qui compare l'ancienne valeur géocentrique
à la nouvelle valeur géodésique, par exemple :

```java
logger.debug("apoAlt geodetic={} vs geocentric={} (Δ={})",
    apoAlt, finalOrbit.getA() * (1.0 + finalOrbit.getE()) - EARTH_RADIUS,
    apoAlt - (finalOrbit.getA() * (1.0 + finalOrbit.getE()) - EARTH_RADIUS));
```

On attend Δ ≈ +10 à +13 km sur le LEOMission Kourou existant
(la valeur exacte dépendant de la latitude où l'apogée tombe — c'est
précisément la dépendance que la formule analytique du doc 04 ne
capturait pas).

## 7. Effort & risque

| Item | Estimation |
|---|---|
| Lignes modifiées | ~30 (helper + import + delta `computeCost` + champ) |
| Fichiers modifiés | 1 (`TransferTwoManeuverProblem.java`) |
| API publique | Inchangée |
| Risque | **Faible** — primitive Orekit déjà utilisée en production ; helper local et facilement testable. |
