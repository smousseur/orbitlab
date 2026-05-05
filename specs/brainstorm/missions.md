# Brainstorm — Types de missions OrbitLab

> **Baseline** : LEO et GTO sont stables et intégrés.
>
> **Critères de classement** : **difficulté technique** (briques manquantes à créer) × **intérêt utilisateur** (valeur pédagogique, spectacle visuel, réalisme).

---

## Tier 1 — Facile + Intérêt élevé

### 1. Orbite Polaire

- **Intérêt** : ★★★★☆ — Couverture globale, météo, reconnaissance, science
- **Difficulté** : ★☆☆☆☆
- **Description** : Orbite à 90° d'inclinaison. Passe au-dessus des pôles à chaque révolution, permettant de couvrir toute la surface terrestre.
- **Ce qui existe déjà** : Tout. Juste un changement d'azimut de lancement + inclinaison cible.
- **Ce qui manque** :
  - Support d'un paramètre `launchAzimuth` dans la mission (aujourd'hui hardcodé pour Kourou)
  - Contrainte d'inclinaison dans le coût de `TransferTwoManeuverProblem` (beta1 déjà disponible pour l'out-of-plane)
- **Notes** : Techniquement trivial. Fort intérêt pédagogique pour visualiser la couverture polaire dans OrbitLab.

---

### 2. SSO — Orbite Héliosynchrone

- **Intérêt** : ★★★★★ — Omniprésente dans les satellites d'observation terrestre (Sentinel, Spot, SWOT…)
- **Difficulté** : ★★☆☆☆
- **Description** : Orbite basse (~600–800 km) à inclinaison précise (~97–98°) pour que le plan orbital précesse à la même vitesse que la Terre autour du Soleil. Illumination solaire quasiment constante sur le site observé.
- **Ce qui existe déjà** : Gravity turn + two-burn, propagateur 50×50 (J2 requis pour modéliser la précession)
- **Ce qui manque** :
  - Objectif `SSOMissionObjective` ciblant l'inclinaison exacte (formule analytique en fonction de l'altitude via J2)
  - Ajout de la contrainte inclinaison dans le coût CMA-ES (beta1 dans `TransferTwoManeuverProblem` déjà partiellement disponible)
  - Paramètre de site de lancement (azimut ~98° ≠ Kourou standard)
- **Notes** : Très proche du LEO. Bon candidat pour une première itération rapide. La formule d'inclinaison SSO est analytique : `cos(i) = -((Re + h)^(7/2) * n_prec) / (3/2 * J2 * Re^2 * sqrt(mu))`.

---

### 3. MEO — Medium Earth Orbit (GPS / Galileo)

- **Intérêt** : ★★★★☆ — Altitude des constellations GPS (~20 200 km), Galileo, GLONASS
- **Difficulté** : ★★☆☆☆
- **Description** : Insertion à ~20 000 km, orbite circulaire à inclinaison ~55°. Période ~12h (demi-journée sidérale).
- **Ce qui existe déjà** : Two-burn transfer, propagateur haute fidélité, vehicle stack
- **Ce qui manque** :
  - Adaptation des bornes CMA-ES pour la longue durée de poussée et le budget propergol différent (upper stage spécialisé)
  - Éventuellement un 3e étage / upper stage à haute ISP (moteur cryogénique)
- **Notes** : Même architecture que LEO/GTO. Bon test de robustesse de l'optimiseur sur de longues trajectoires de transfert.

---

## Tier 2 — Difficulté moyenne + Intérêt élevé

### 4. GEO — Orbite Géostationnaire

- **Intérêt** : ★★★★★ — Télécommunications, météo (Meteosat), surveillance. Visuellement très impactant (orbite à 35 786 km)
- **Difficulté** : ★★★☆☆
- **Description** : Orbite circulaire à 35 786 km, inclinaison ~0°, période 24h sidérales. Le GTO amène déjà au périgée ; un 3e burn (apogee kick motor) circularise à l'apogée.
- **Ce qui existe déjà** : GTO est stable → point de départ naturel. `Burn2Resolver` peut être adapté pour la circularisation GEO.
- **Ce qui manque** :
  - Objectif `GeostationaryInsertionObjective` (altitude ~35 786 km + inclinaison ~0°)
  - `ApogeeKickStage` (nouveau stage ou extension de `ConstantThrustStage` déclenchée à l'apogée)
  - Correction d'inclinaison résiduelle au passage à l'apogée (combined plane change + raise)
- **Notes** : Grande valeur « visuelle » pour le projet. Un satellite en orbite GEO dans OrbitLab est très impressionnant. L'apogee kick est structurellement proche de ce qui existe déjà.

---

### 5. Orbite de Molniya / HEO Hautement Elliptique

- **Intérêt** : ★★★★☆ — Comms russes (Molniya), TESS, XMM-Newton. Très pédagogique sur la mécanique des perturbations J2.
- **Difficulté** : ★★★☆☆
- **Description** : Orbite très elliptique (apogée ~40 000 km, périgée ~500 km), inclinaison critique de 63,4° pour annuler la précession de l'argument du périgée via J2. L'apogée reste au-dessus des latitudes polaires pendant des heures.
- **Ce qui existe déjà** : Propagateur 50×50 avec J2, two-burn insert, MinAltitudeTracker
- **Ce qui manque** :
  - Objectif `MolniyaOrbitObjective` ciblant apogée + périgée + inclinaison 63,4°
  - Contrainte sur l'argument du périgée dans le coût de l'optimiseur
  - Helper `Physics.criticalInclination()` (formule analytique)
- **Notes** : Excellent cas d'usage pour illustrer la dynamique des perturbations J2 dans la simulation OrbitLab.

---

### 6. Déorbitage Contrôlé / Rentrée Atmosphérique

- **Intérêt** : ★★★★★ — Très demandé, spectaculaire (rentrée en boule de feu), pertinent post-SpaceX, ISS reboost/deorbit
- **Difficulté** : ★★★☆☆
- **Description** : Depuis une orbite basse, appliquer un burn rétrograde (deorbit burn) pour abaisser le périgée en dessous de ~80 km. Propager jusqu'à l'entrée atmosphérique et prédire le point d'impact.
- **Ce qui existe déjà** : `MinAltitudeTracker`, `MassDepletionDetector`, propagateurs Orekit (supporte NRLMSISE-00 pour le drag)
- **Ce qui manque** :
  - Activation du `AtmosphericDragForceModel` dans le propagateur (supporté nativement par Orekit)
  - `ReentryDetector` (stoppe la propagation en dessous d'un seuil d'altitude ~80 km)
  - `DeorbitBurnStage` (burn rétrograde = ConstantThrustStage en direction opposée)
  - `ReentryObjective` (fenêtre d'entrée, point d'impact, heure de rentrée)
- **Notes** : Fort intérêt narratif et pédagogique. Le drag atmosphérique augmente la durée des propagations ; à évaluer côté perfs.

---

### 7. Déploiement de Constellation

- **Intérêt** : ★★★★☆ — Starlink, OneWeb, Galileo. Très actuel et visuellement saisissant.
- **Difficulté** : ★★★☆☆
- **Description** : Injecter N satellites dans des plans orbitaux différents avec espacement RAAN régulier. Nécessite des manœuvres de déphasage entre les injections successives.
- **Ce qui existe déjà** : `MissionContext` gère plusieurs missions simultanées, le rendu multi-spacecraft existe
- **Ce qui manque** :
  - `ConstellationMission` orchestrant N missions LEO/SSO avec RAAN décalé automatiquement
  - `PhasingManeuverStage` pour ajuster le déphasage entre les slots orbitaux
  - UI pour paramétrer la constellation (N satellites, inclinaison, altitude, espacement RAAN)
- **Notes** : Plus complexe côté UX que côté physique. La simulation de N trajectoires simultanées demande de l'optimisation des performances.

---

## Tier 3 — Difficile + Très fort intérêt

### 8. Transfert Lunaire (TLI + LOI)

- **Intérêt** : ★★★★★ — La Lune ! Apollo, Artemis, CAPSTONE. Impressionnant visuellement à l'échelle du système solaire dans OrbitLab.
- **Difficulté** : ★★★★☆
- **Description** : Trans-Lunar Injection (TLI) depuis une parking orbit LEO → coast ~3 jours → Lunar Orbit Insertion (LOI) burn rétrograde → orbite lunaire circulaire.
- **Ce qui existe déjà** : Éphémérides lunaires Orekit (SPICE DE430), propagateurs, architecture multi-stage
- **Ce qui manque** :
  - Force de 3rd body (gravité lunaire) dans le propagateur pendant le coast TLI→LOI
  - Objectif `LunarOrbitObjective` (périlune altitude, inclinaison lunaire)
  - `TLIBurnStage` (burn depuis apogée de parking orbit vers la Lune)
  - `LunarInsertionStage` (burn rétrograde à l'arrivée)
  - Calcul de la fenêtre de lancement lunaire (géométrie Terre-Lune, ~monthly window)
  - Optimiseur : le timing TLI est très contraint ; patch-conic comme initial guess
- **Notes** : Rupture technique majeure (multi-body). Orekit gère nativement les forces gravitationnelles de 3rd body et les éphémérides SPICE. Très fort impact pour la visibilité du projet.

---

### 9. Rendezvous Orbital (ISS-style)

- **Intérêt** : ★★★★★ — Dragon, Soyuz, Cygnus. Mécaniquement fascinant, fort intérêt pédagogique.
- **Difficulté** : ★★★★☆
- **Description** : Rejoindre une cible en orbite (ISS, debris, satellite). Phases : insertion en orbite de phasing → transfert de Hohmann → approche (V-bar ou R-bar) → point de rendezvous.
- **Ce qui existe déjà** : Propagateurs, attitude control, missions multiples simultanées
- **Ce qui manque** :
  - `TargetedObject` : objet cible avec son propre état orbital
  - `RendezvousObjective` (position relative, vitesse relative < seuil)
  - `PhasingManeuverStage` (Hohmann de phase, correction de drift)
  - Optimiseur multi-objectif : timing + delta-v total
  - Guidage relatif pour l'approche finale (LVLH frame)
- **Notes** : Un rendezvous balistique simplifié (Hohmann exact + corrections analytiques) est faisable sans GNC temps-réel. Le rendezvous « vrai » avec guidage en boucle fermée dépasse l'architecture CMA-ES actuelle.

---

### 10. Point de Lagrange L1/L2 — Orbite de Halo

- **Intérêt** : ★★★★☆ — JWST, SOHO, DSCOVR. Très pédagogique sur la mécanique à 3 corps.
- **Difficulté** : ★★★★★
- **Description** : Insérer un satellite au voisinage du point L2 Terre-Soleil (~1,5M km). L'orbite de halo est quasi-périodique dans le problème à 3 corps restreint circulaire (CR3BP).
- **Ce qui existe déjà** : Orekit supporte les propagateurs multi-corps et les référentiels héliocentriques
- **Ce qui manque** :
  - Problème à 3 corps (CR3BP) ou propagateur héliocentrique perturbé
  - Calcul analytique du point L2 et initialisation d'une orbite de halo
  - Optimiseur de trajectoire depuis LEO → L2 (insertion via tir parabolique ou invariant manifold)
  - `HaloOrbitObjective` + station-keeping
  - Nouveau référentiel synodique (Terre-Soleil tournant)
- **Notes** : Le plus difficile techniquement. Excellent positionnement si OrbitLab veut couvrir les missions de science profonde.

---

## Tier 4 — Très difficile + Audience spécialisée

### 11. Transfert Interplanétaire (Mars, Vénus)

- **Intérêt** : ★★★★☆ pour les passionnés, ★★☆☆☆ grand public
- **Difficulté** : ★★★★★
- **Description** : Transfert de Hohmann ou trajectoire balistique vers Mars. Nécessite une fenêtre de lancement (période synodique ~26 mois pour Mars). Durée du transit : 6–9 mois.
- **Ce qui existe déjà** : Éphémérides Orekit/SPICE pour toutes les planètes, propagateurs héliocentriques
- **Ce qui manque** :
  - Propagateur héliocentrique (référentiel ICRF barycentrique)
  - `PatchedConicCalculator` pour la trajectoire d'Hohmann interplanétaire
  - `PlanetaryInsertionObjective` (Mars Orbit Insertion burn)
  - Calcul des fenêtres de lancement (pork-chop plots C3 vs date de départ)
  - Durée de propagation très longue → gestion des performances
- **Notes** : Nécessite un refactoring important pour supporter les voyages interplanétaires dans l'interface et la simulation.

---

### 12. Assistance Gravitationnelle (Gravity Assist / Flyby)

- **Intérêt** : ★★★☆☆ — Voyager, Cassini, JUICE, BepiColombo
- **Difficulté** : ★★★★★
- **Description** : Utiliser la gravité d'une planète pour modifier la vitesse et la trajectoire sans propulsion. Technique indispensable pour les missions vers le système solaire externe.
- **Ce qui existe déjà** : Propagateurs multi-corps Orekit
- **Ce qui manque** :
  - Trajectoire calculée sur plusieurs arcs (hyperbole d'approche, flyby, hyperbole de départ)
  - Optimiseur sur le vecteur B (b-plane targeting : BT, BR, TOF)
  - Visualisation de la sphère d'influence (SOI)
  - Séquence multi-flyby (ex : Venus-Earth-Earth pour Cassini)
- **Notes** : Complexité algorithmique très élevée. Intérêt pédagogique fort mais audience restreinte. À envisager seulement après le transfert interplanétaire de base.

---

## Matrice de priorisation

| Mission                       | Intérêt User | Difficulté | Réutilisation | Priorité recommandée     |
|-------------------------------|:------------:|:----------:|:-------------:|:------------------------:|
| Orbite Polaire                | ★★★★         | ★          | 95%           | 🟢 P0 — Trivial          |
| SSO                           | ★★★★★        | ★★         | 85%           | 🟢 P0 — Court terme      |
| MEO (GPS)                     | ★★★★         | ★★         | 90%           | 🟢 P0 — Court terme      |
| GEO                           | ★★★★★        | ★★★        | 70%           | 🟡 P1 — Moyen terme      |
| Molniya / HEO                 | ★★★★         | ★★★        | 75%           | 🟡 P1 — Moyen terme      |
| Déorbitage / Rentrée          | ★★★★★        | ★★★        | 60%           | 🟡 P1 — Moyen terme      |
| Constellation                 | ★★★★         | ★★★        | 65%           | 🟡 P1 — Moyen terme      |
| Transfert Lunaire (TLI + LOI) | ★★★★★        | ★★★★       | 40%           | 🔴 P2 — Long terme       |
| Rendezvous Orbital            | ★★★★★        | ★★★★       | 45%           | 🔴 P2 — Long terme       |
| Point de Lagrange L1/L2       | ★★★★         | ★★★★★      | 25%           | 🔴 P3 — Ambitieux        |
| Transfert Interplanétaire     | ★★★★         | ★★★★★      | 20%           | 🔴 P3 — Ambitieux        |
| Gravity Assist                | ★★★          | ★★★★★      | 15%           | 🔴 P3 — Ambitieux        |

---

## Recommandations architecturales

1. **Quick wins P0** : Polaire + SSO + MEO sont quasi-gratuits car ils réutilisent tout le stack existant.
   Le seul vrai travail est de rendre `launchAzimuth` et `targetInclination` paramétrables dans la mission.

2. **Impact maximum P1** : GEO est le plus impactant visuellement pour l'effort supplémentaire le plus faible
   (3e burn d'apogée ≈ 1 nouveau stage). Le déorbitage est fort en valeur narrative et pédagogique.

3. **Rupture technique P2** : Le transfert lunaire constitue une rupture (multi-body), mais Orekit supporte nativement
   les forces de 3rd body et les éphémérides SPICE DE430. À traiter comme un nouveau sous-système dédié.

4. **Refactoring préalable recommandé** : Avant d'implémenter SSO / orbite polaire, généraliser `LEOMission` en
   `EarthOrbitMission` avec `launchSite`, `targetInclination`, `targetAltitude` et `targetEccentricity`
   comme paramètres configurables. Cela couvre les trois P0 et une bonne partie des P1 sans duplication de code.
