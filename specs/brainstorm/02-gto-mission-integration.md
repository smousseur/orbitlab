# Brainstorm — Intégration des missions GTO (Geostationary Transfer Orbit)

> Document **fonctionnel**, pas un plan d'exécution. Capture les choix
> de cadrage avant de spécifier l'implémentation. Le plan d'exécution
> correspondant sera écrit dans `~/.claude/plans/…` et promu vers
> `specs/mission/` au moment du chantier.

## Pourquoi cette discussion

OrbitLab ne sait modéliser qu'un seul type de mission concrète :
`LEOMission`, qui suppose une **orbite cible circulaire** définie par une
**altitude scalaire**. Cette hypothèse est inscrite en dur à plusieurs
endroits (`OrbitInsertionObjective(body, altitude, eccentricity=0)`,
`GravityTurnConstraints.forTarget(double targetAltitude)`,
`TransferTwoManeuverProblem` qui circularise sur `aTarget`,
`MissionWizardAppState` qui instancie `LEOMission` sans factory). La carte
« GTO » du wizard existe (`StepMissionType.java`) mais est désactivée.

Ouvrir une mission GTO impose trois ruptures :

1. **Cible elliptique** (rₚ ≠ rₐ) — l'optim et la définition d'objectif
   doivent porter au moins un couple périgée/apogée plutôt qu'une altitude.
2. **Plan non équatorial** — la trajectoire doit pouvoir gérer une
   inclinaison cible distincte de la latitude du site, donc un changement
   de plan partiel ou total.
3. **Phases longues** — un transfert GTO dure ~5 h ; la trajectoire
   comporte une phase de coast majeure et potentiellement une burn
   d'apogée (vers GEO). Le séquencement des phases et la place de
   l'optim dans chacune doivent être repensés.

Ce document cadre **(Partie 1)** les changements à apporter à l'optim
existante pour qu'elle accepte une cible orbitale arbitraire,
**(Partie 2)** ce qu'est une mission GTO côté OrbitLab — vocabulaire,
profil de mission, dépendance à la latitude, parking orbit, budget Δv —
et **(Partie 3)** les sujets transverses à acter avant de démarrer.

## Partie 1 — Préparation du code d'optim existant

### D1 — Généralisation de la cible orbitale

`OrbitInsertionObjective` ne porte aujourd'hui qu'`(altitude,
eccentricity)`. C'est insuffisant pour GTO et plus généralement pour
toute orbite arbitraire. La cible doit pouvoir spécifier :

| Élément       | Symbole | Source       | Exposé wizard V1 ? |
|---------------|---------|--------------|---------------------|
| Périgée       | rₚ      | utilisateur  | oui                 |
| Apogée        | rₐ      | utilisateur  | oui                 |
| Inclinaison   | i       | utilisateur  | oui                 |
| RAAN          | Ω       | calculé      | non (fixé par site + heure) |
| Argument péri.| ω       | calculé      | non (fixé "apogée à l'équateur") |
| Anomalie      | ν       | propagation  | non                 |

**Recommandation** : remplacer le record actuel par une cible polymorphe
(p.ex. record `TargetOrbit` avec factories `circular(alt, inc)`,
`elliptic(rp, ra, inc)`, `geostationaryTransfer(...)`). Garder la
dépendance à Orekit (`Orbit`) en interne mais exposer des champs
"physiquement parlants" (km, °) côté wizard. Conserver une factory
`circular(alt)` pour ne pas casser LEO.

### D2 — Adaptations du gravity turn

`GravityTurnConstraints.forTarget(double targetAltitude)` dérive ses
fenêtres apogée et vitesse à partir d'une altitude unique, et
`GravityTurnProblem` minimise sur cette cible. Pour une cible
elliptique :

- La sortie du gravity turn vise toujours **un état sub-orbital dont
  l'apogée tombe à une altitude raisonnable** — mais cet objectif
  intermédiaire est désormais le **périgée de l'orbite cible** (cas
  direct ascent) ou **l'altitude de l'orbite de parking** (cas avec
  parking). Pas l'apogée de la cible.
- Donc `forTarget` prend désormais un **périgée intermédiaire**
  (~200 km en GTO, identique LEO), pas l'altitude finale GTO. La
  méthode reste cohérente côté LEO existant : périgée intermédiaire =
  altitude finale.
- **Pas de changement structurel** dans les variables CMA-ES : on garde
  les 2 variables actuelles (`transitionTime`, `exponent`). Les bornes
  `GravityTurnConstraints` peuvent rester très proches du LEO 200 km.
- **L'inclinaison est fixée par l'azimut de tir**, pas par le gravity
  turn. Elle se règle dans `getInitialState` / la trajectoire
  d'ascension, pas dans `GravityTurnProblem`. À documenter clairement
  pour ne pas créer de variable CMA-ES "fantôme".

### D3 — Adaptations du problème "transfer-2"

`TransferTwoManeuverProblem` optimise une burn 1 (4 variables : `t1,
dt1, alpha1, beta1`) puis exécute une burn 2 **circularisation
déterministe** vers `aTarget`. Pour GTO, deux situations à séparer :

- **Profil B (parking + injection GTO)** : le transfer-2 actuel
  garde son rôle pour la **circularisation parking** (200-300 km). La
  burn 2 reste une circularisation. Pas de changement majeur. C'est
  ce qui permet de réutiliser l'existant pour la première moitié de
  la mission.
- **Injection GTO** (nouvelle étape, pas dans le transfer-2 actuel) :
  c'est une burn de **raise-apogee** depuis le parking. Il faut soit
  généraliser `TransfertTwoManeuver` avec un mode `RAISE_APOGEE` (où
  burn 2 est désactivée ou réduite à une correction), soit créer un
  stage dédié plus simple à 1 burn (3 variables : `dt_inj, alpha_inj,
  beta_inj`). Recommandation : **stage dédié**, plus lisible que de
  surcharger le transfer-2.

Côté **plane change** : `TransfertTwoManeuver` accepte déjà un angle
hors plan (`beta1`) côté décodage, mais peu testé en hors plan. À
réactiver / valider pour le cas où l'injection GTO porte un plane change
partiel (Cape Canaveral, Tanegashima).

Côté **coût** : généraliser le coût pour viser `(aTarget, eTarget)`
plutôt qu'`aTarget` seul. Pour LEO `eTarget = 0` ; pour le parking GTO
aussi ; pour l'injection GTO `eTarget ≈ 0.73`. Les pondérations
actuelles (apogée 3.0, périgée 10.0, e 2.0, vitesse 1.0) restent
indicatives, à recalibrer.

### D4 — Factorisation Mission / fin du hardcode LEO

`MissionWizardAppState` instancie `new LEOMission(name, alt*1000, lat,
lon, alt_site)` en dur. Pour ouvrir GTO sans dupliquer le wizard :

- Formaliser un enum `MissionType` (LEO, GTO, …) — aujourd'hui c'est une
  String dans `selectedMissionType`.
- Introduire une factory `MissionFactory.build(MissionType, FormFields,
  TargetOrbit, LaunchSite) → Mission`. Branchée dans
  `MissionWizardAppState`.
- `Mission` reste la classe de base ; `LEOMission` et `GTOMission`
  diffèrent par la **séquence de stages** et la cible.

**Bénéfice** : le reste de la pipeline (`MissionOptimizer`,
`MissionRenderer`, etc.) ne change pas tant que `Mission` reste
l'abstraction.

## Partie 2 — Mission GTO

### P0 — Caractéristiques orbitales à intégrer (rappels et définitions)

LEO n'expose qu'un paramètre (altitude). GTO en demande au moins quatre.
Avant les profils, on pose le vocabulaire physique pour que la suite
soit lisible sans ouvrir un manuel de mécanique spatiale :

- **a — demi-grand axe** : moyenne (rₐ + rₚ)/2. Pour GTO type
  (200 × 35 786 km) : a ≈ 24 396 km depuis le centre Terre, soit
  ~18 020 km en altitude moyenne.
- **e — excentricité** : 0 = circulaire, < 1 = ellipse, → 1 =
  parabolique. GTO type : e ≈ 0.73. C'est la grandeur qui caractérise
  "à quel point l'orbite est étirée".
- **rₚ / rₐ — rayons périgée et apogée** : couple plus parlant que
  (a, e) pour l'utilisateur. C'est ce qu'on exposera dans le wizard
  (en altitudes au-dessus du sol, pas en rayons depuis le centre).
- **i — inclinaison** : angle entre le plan orbital et le plan
  équatorial terrestre. Sans manœuvre hors plan, **i ≥ latitude du
  site** (impossible de viser plus bas qu'une latitude depuis laquelle
  on lance). GEO vise i = 0°, donc plane change nécessaire dès qu'on
  ne lance pas depuis l'équateur.
- **Ω — RAAN (right ascension of the ascending node)** : longitude du
  nœud ascendant dans le repère inertiel (typiquement EME2000). Fixe
  l'orientation du plan orbital autour de l'axe terrestre. Dépend
  directement de **l'heure de lancement** (rotation Terre) et de
  **l'azimut**. Pas exposé à l'utilisateur en V1 — on le subit.
- **ω — argument du périgée** : angle entre le nœud ascendant et le
  périgée, mesuré dans le plan orbital. Pour GTO classique on cherche
  **ω = 0° ou 180°**, ce qui place le **périgée à l'équateur** ; par
  symétrie, l'**apogée tombe aussi à l'équateur**. C'est là qu'on fera
  le plane change vers GEO à coût Δv minimal (vitesse minimale).
- **ν — anomalie vraie** : angle entre le périgée et la position
  courante du satellite, mesuré dans le plan orbital. C'est la
  variable temporelle de la trajectoire ; ν = 0° = périgée, ν = 180°
  = apogée. Sert à dater les événements de mission via
  `ApsideDetector`.

**Notions dérivées utiles dans la suite** :

- **Nœuds ascendant / descendant** : intersections entre l'orbite et le
  plan équatorial terrestre. Seuls points où un changement
  d'inclinaison est sans coût d'altitude (et où il est moins cher en
  Δv si la vitesse y est faible — d'où l'apogée).
- **Vitesses caractéristiques en GTO** : v_péri ≈ 10.2 km/s (rapide,
  près de la Terre), v_apo ≈ 1.6 km/s (lente, loin). Plane change à
  l'apogée coûte ~5× moins cher qu'à un point où la vitesse est plus
  élevée.
- **Lien latitude–inclinaison via l'azimut A** :
  `cos(i) = cos(lat) × sin(A)`. Au minimum `i_min = lat`, atteint pour
  un tir plein est (A = 90°). Détermine l'inclinaison atteignable
  depuis un site donné sans plane change.

**Pour OrbitLab V1** : le wizard expose `(rₚ, rₐ, i)`, fixe ω
implicitement à "apogée à l'équateur", et laisse Ω + ν découler de
l'heure de lancement et de la trajectoire d'ascension simulée.

### P1 — Profil de mission type et variables CMA-ES associées

Trois profils possibles. Pour chacun on liste les phases et **ce que
CMA-ES doit optimiser** (les autres phases sont déterministes ou
pilotées par détecteurs Orekit).

#### Profil A — Direct ascent (1 burn)

| Phase | Description                                  | Variables CMA-ES |
|-------|----------------------------------------------|------------------|
| 1     | Ascension verticale                          | — (durée fixe)   |
| 2     | Gravity turn vers (rₚ_GTO, rₐ_GTO) en sortie | `transitionTime`, `exponent` (2) |
| 3     | Coast / correction périgée éventuelle        | — (optionnel)    |

**Total ≈ 2 variables.** Le moins cher en Δv (pas de circularisation
intermédiaire) mais le plus dur côté pilotage : la trajectoire
d'ascension doit livrer directement la GTO, donc azimut et instant de
tir très contraints. Aussi le moins flexible si l'utilisateur change
d'avis sur l'inclinaison cible.

#### Profil B — Parking orbit + injection GTO (2 burns) [recommandé V1]

| Phase | Description                                                          | Variables CMA-ES |
|-------|----------------------------------------------------------------------|------------------|
| 1     | Ascension verticale                                                  | —                |
| 2     | Gravity turn vers parking (~250 km)                                  | `transitionTime`, `exponent` (2) |
| 3     | Circularisation parking (transfer-2 burn 1, burn 2 = circ)           | `t1, dt1, alpha1, beta1` (4) |
| 4     | Coast en parking jusqu'au nœud d'injection                           | — (détecteur de nœud) |
| 5     | Injection GTO au périgée (raise-apogee, plane change partiel via β)  | `dt_inj, alpha_inj, beta_inj` (3) |

**Total ≈ 9 variables**, optimisées **par phase séquentiellement** via
`MissionOptimizer` (comme aujourd'hui), pas en bloc. Pas d'optim
multi-phase couplée en V1.

**Justifications V1** :

- Réutilise gravity turn + transfer-2 existants moyennant les
  adaptations D2/D3.
- Pas de propagation longue (5 h) à charge de l'optim — la phase
  parking + injection se déroule en moins d'une orbite.
- Permet un test de non-régression progressif : étape parking ≈ LEO
  existant (canary), étape injection est la nouveauté à valider.

#### Profil C — Parking + GTO + apogée kick (3 burns, vers GEO)

Phases 1-5 identiques au profil B, plus :

| Phase | Description                                            | Variables CMA-ES |
|-------|--------------------------------------------------------|------------------|
| 6     | Coast GTO jusqu'à l'apogée (~5 h)                      | — (`ApsideDetector`) |
| 7     | Apogée kick : circularisation 35 786 km + plane change | `dt_apo, alpha_apo, beta_apo` (3) |

**Total ≈ 12 variables.** Mention dans le doc, **hors scope V1** :
ajoute une propagation 5 h à chaque évaluation CMA-ES (coûteux en CPU)
et un couplage avec les perturbations Lune/Soleil qu'on n'a pas
modélisées (cf. Partie 3). À ouvrir dans un chantier séparé une fois
GTO seul stable.

### P2 — Profils selon latitude du site de lancement

L'inclinaison atteignable sans plane change ≈ latitude du site. Le coût
du plane change vers GEO (i = 0°) à l'apogée GTO dépend donc
directement du site :

| Site            | Latitude | i min sans plane change | Δv plane change apogée GEO |
|-----------------|---------:|------------------------:|---------------------------:|
| Kourou          |    5.2°  |                    5.2° | ~1 500 m/s                 |
| Cape Canaveral  |   28.5°  |                   28.5° | ~1 830 m/s                 |
| Tanegashima     |   30.4°  |                   30.4° | ~1 900 m/s                 |
| Vandenberg      |   34.7°  |                   34.7° | ~2 100 m/s                 |
| Baikonur        |   45.6°  |                   45.6° | ~2 400 m/s                 |

**Conséquences fonctionnelles** :

- GTO depuis Baikonur ou Vandenberg vers GEO équatorial est physiquement
  possible mais commercialement marginal (charge utile fortement
  réduite). On ne l'interdit pas, mais on **affiche un avertissement
  dans le wizard** quand la combinaison site/inclinaison est
  défavorable.
- Le profil B reste valable pour tous les sites ; ce qui varie c'est la
  répartition du plane change (tout en injection vs tout en apogée vs
  réparti).
- Pour V1 on ne fait pas d'optim sur la répartition du plane change
  (entrer une inclinaison cible suffit à fixer ce qu'il reste à faire).

### P3 — Forme typique de l'orbite de parking

- **Forme** : circulaire, **200-300 km d'altitude** (300 km recommandé
  pour limiter le drag pendant le coast).
- **Inclinaison** : égale à la latitude du site, ou imposée par
  l'azimut choisi (si on veut commencer le plane change dès l'ascension,
  ce qui est rare).
- **Durée de coast** : entre quart et demi-orbite (~20-45 min) pour
  amener le satellite au **nœud d'injection** : le périgée de la GTO
  doit tomber à l'équateur, ce qui impose `ω = 0°` (apogée à
  l'équateur, plane change peu cher). Concrètement : on injecte la GTO
  quand le satellite traverse le plan équatorial.
- **RAAN** : non choisi explicitement par l'utilisateur. C'est la
  conséquence du couple (heure de lancement, azimut). En V1 on fixe
  l'instant de lancement et on propage ; pas de fenêtre de tir
  optimisée.

### P4 — Budget Δv comparé à LEO

Ordres de grandeur depuis Kourou (i = 5°) pour une mission jusqu'à GTO
puis jusqu'à GEO (apogée kick combiné circularisation + plane change) :

| Phase                                          | LEO 400 km | GTO 200 × 35 786 km | GEO       |
|------------------------------------------------|-----------:|--------------------:|----------:|
| Ascent + gravity turn + parking (pertes incl.) |  ~9.4 km/s |           ~9.4 km/s | ~9.4 km/s |
| Injection GTO depuis parking                   |          — |          ~2.45 km/s | ~2.45 km/s|
| Apogée kick (circ. + plane change Kourou)      |          — |                   — | ~1.5 km/s |
| **Total Δv au sol (mission complète)**         |  ~9.4 km/s |          ~11.85 km/s | ~13.35 km/s|

**Points fonctionnels à retenir** :

- L'écart LEO → GTO est **~+2.45 km/s** sur le budget total — significatif
  mais pas écrasant.
- L'écart GTO → GEO est encore **~+1.5 km/s** depuis Kourou, et grimpe
  à ~+2.4 km/s depuis Baikonur.
- **Δv ≠ masse propergol** : à cause de l'équation de Tsiolkovski, ce
  surcoût Δv divise typiquement la masse charge utile **par ~2 à 3**
  entre LEO et GTO pour un lanceur donné. C'est ce qui justifie qu'un
  Ariane 5 fasse 20 t en LEO mais 10 t en GTO.
- Pour V1, on ne dimensionne pas le lanceur dynamiquement : `LaunchVehicle`
  garde ses masses fixes et la mission échouera (propergol épuisé) si
  les valeurs LEO ne suffisent pas. À documenter, à ouvrir plus tard.

## Partie 3 — Autres points fonctionnels à soulever

- **Wizard UI** : `StepParameters` aujourd'hui = un slider altitude.
  GTO demande au minimum 3 champs : périgée, apogée, inclinaison cible.
  Validation croisée à prévoir (apogée ≥ périgée, inclinaison ≥
  latitude site sans plane change initial). Recommandation : **step
  dédié par type de mission** plutôt qu'inflation de `StepParameters`.
- **Visualisation** : afficher trois orbites simultanément (parking,
  GTO, cible finale GEO si profil C). Le `MissionRenderer` actuel
  suppose une trajectoire continue ; clarifier comment annoter
  visuellement les phases (couleurs, segments, événements de burn).
- **Échelles de temps** : transfert GTO ≈ 5 h vs LEO ≈ 45 min. À
  vérifier que `SimulationClock` et le renderer encaissent — a priori
  oui mais coût d'optim multiplié si la phase de coast GTO entre dans
  la boucle (cf. profil C, hors scope V1).
- **Budget d'optim CMA-ES** : profil B = 9 variables réparties sur 3
  étages d'optim, vs ~6 aujourd'hui (LEO sur 2 étages). À revoir
  `maxRetries`/budget par stage en lien avec
  `01-cmaes-retry-acceptance.md` (le retry schedule avec backup
  multi-try devient plus important).
- **Modèle physique** : à l'apogée GTO (~36 000 km) les perturbations
  **Lune et Soleil** sont non négligeables (gradient gravitationnel
  significatif). Aujourd'hui OrbitLab tourne en gravité 8×8 ou 50×50
  sans 3rd-body. **Décision V1** : on accepte la simplification (pas
  de 3rd-body) tant qu'on ne propage pas les 5 h jusqu'à l'apogée
  pour optim ; en visualisation ça reste acceptable.
- **Fenêtre de tir / RAAN** : l'instant de lancement détermine Ω et
  donc l'alignement nœud / équateur à l'injection GTO. **V1** : on
  fixe l'instant de lancement, on laisse l'optim trouver une trajectoire
  cohérente, **pas de notion de "launch window" UI**. À ouvrir plus
  tard.
- **Masse au lancement / charge utile** : un lanceur 20 t en LEO ≈ 6-7
  t en GTO. Le modèle `LaunchVehicle` actuel a des masses fixes ;
  utilisable tel quel mais la mission GTO peut échouer (propergol
  épuisé) avec les valeurs LEO par défaut. À documenter, et possible
  ajustement empirique des masses pour les tests GTO.
- **Cas dégénérés** : signaler dans le wizard les combinaisons où
  l'optim n'aboutira pas raisonnablement (Vandenberg → GEO équatorial,
  apogée < périgée, inclinaison cible < latitude site, etc.).

## Hors scope — à reprendre dans d'autres chantiers

- **Profil C — apogée kick / GEO complète** : optimisation de la
  circularisation + plane change apogée. Nécessite propagation 5 h dans
  l'optim, modèle 3rd-body, et probablement un retry schedule différent.
- **Optim multi-phase couplée** (injection GTO + apogée kick optimisés
  ensemble) : pour V1 on garde le découpage par phases.
- **Fenêtre de tir / contraintes RAAN** exposées à l'utilisateur.
- **Modèle 3rd-body Lune/Soleil** dans les force models pour les
  propagations longues.
- **Modèle de lanceur paramétrable** (étages dédiés GTO, masses
  différentes selon profil de mission).
- **Trajectoires sub-GTO / supersynchrones** (apogée plus haut que GEO
  pour économiser sur le plane change) : pas en V1.

## Risques identifiés

| Risque                                                                 | Mitigation                                                              |
|------------------------------------------------------------------------|-------------------------------------------------------------------------|
| Régression LEO en généralisant `OrbitInsertionObjective` / contraintes | Test `LEOMissionOptimizationTest` comme canary, validé avant chaque PR. |
| Convergence CMA-ES dégradée par cible elliptique                       | S'appuyer sur le retry schedule de `01-cmaes-retry-acceptance.md`.      |
| Plane change mal modélisé dans `TransfertTwoManeuver` (β1 peu testé)   | Test dédié hors plan avant V1, sur un cas simple (Cape, i = 28°).       |
| UX wizard surchargée si GTO ajoute beaucoup de champs                  | Step dédié par type plutôt qu'inflation de `StepParameters`.            |
| Mission GTO échoue par propergol insuffisant avec masses LEO           | Documenter ; ajuster empiriquement pour les tests, ne pas refondre.     |
| Approximation Lune/Soleil acceptable ?                                 | Acter en V1, vérifier visuellement qu'aucun comportement absurde n'apparaît. |

## Références code

Fichiers impactés à terme par le chantier (pas modifiés dans ce
brainstorm) :

- `simulation/mission/objective/OrbitInsertionObjective.java` —
  généralisation cible (D1).
- `simulation/mission/LEOMission.java` — modèle pour `GTOMission` (D4).
- `simulation/mission/optimizer/problems/GravityTurnConstraints.java` —
  cible = périgée intermédiaire (D2).
- `simulation/mission/optimizer/problems/TransferTwoManeuverProblem.java` —
  cible (a, e) au lieu d'a seul (D3).
- `simulation/mission/maneuver/TransfertTwoManeuver.java` — mode
  raise-apogee, plane change actif (D3).
- `simulation/mission/stage/TransfertTwoManeuverStage.java` — adaptation
  côté stage (D3).
- Nouveau : stage d'injection GTO (P1, profil B, phase 5).
- `states/mission/MissionWizardAppState.java` — factory de mission (D4).
- `ui/mission/wizard/step/StepMissionType.java` — activer carte GTO.
- `ui/mission/wizard/step/StepParameters.java` — step dédié GTO.
- `ui/mission/wizard/FormField.java` — ajouter champs GTO_PERIGEE,
  GTO_APOGEE, GTO_INCLINATION.

## Liens

- `specs/brainstorm/01-cmaes-retry-acceptance.md` — budget retries
  impacté par l'augmentation du nombre de variables CMA-ES.
- `specs/optimizer/02-altitude-dependent-design.md` — bornes
  altitude-dépendantes, à étendre périgée/apogée.
- Plan d'exécution : à créer après validation, suivra le découpage
  D1 → D2 → D3 → D4 puis P1 (profil B) puis P2/P3/P4 (élargissement
  fonctionnel).
