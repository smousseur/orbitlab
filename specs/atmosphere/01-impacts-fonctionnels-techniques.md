# Introduction de l'atmosphère dans les simulations — Impacts

> Étude des impacts **fonctionnels** puis **techniques** de l'ajout d'un
> modèle atmosphérique (densité + traînée) au cœur de la simulation
> OrbitLab. À la rédaction de ce document, **aucun modèle atmosphérique
> n'existe** dans la base de code : tous les propagateurs sont
> uniquement gravitationnels. Ce doc cadre la portée d'un futur
> ajout — il ne propose pas encore d'implémentation détaillée.

## 1. Contexte & motivation

### 1.1 État actuel (rappel)

| Sous-système | État | Conséquence |
|---|---|---|
| `OrekitService` | Trois propagateurs : Newtonien (Simple), 8×8 (Optim), 50×50 (Default). Aucun `DragForce`, aucun `Atmosphere`. | Le vide spatial est simulé jusqu'au sol. Une trajectoire qui passe à 80 km vole comme à 800 km. |
| `Spacecraft`, `LaunchVehicle` | Records `(dryMass, propellantCapacity, propulsion)`. | **Aucune propriété aérodynamique** (Cd, surface, coefficient balistique). |
| Stages d'ascension (`VerticalAscentStage`, `GravityTurnStage`) | Poussée + gravité. | Pas de pertes par traînée pendant la montée. Pas de pression dynamique calculée. |
| Optimiseur (`GravityTurnProblem`, `TransferTwoManeuverProblem`) | Coût piloté par apogée, vTan, FPA, periapsis floor (100 km). | Convergence systématiquement "trop optimiste" : Δv théoriques sans pénalité atmosphérique, perigee floor inadapté < 200 km. |
| Détecteurs | `MinAltitudeTrackerDetector` borne géométrique. | Aucun détecteur "réentrée atmosphère" ni "MaxQ". |
| UI mission | Wizard : type, paramètres, lanceur, site. | Aucun réglage atmosphère / météo spatiale. |

### 1.2 Pourquoi l'introduire

Trois pressions convergent :

1. **Roadmap optimiseur.** `specs/optimizer/03-robustness-roadmap.md` §"Questions ouvertes à trancher" identifie déjà la traînée comme un point de blocage à 185 km : *« est-ce que le propagateur d'optimisation modélise l'atmosphère ? Si non, il faut soit l'ajouter, soit fixer un floor d'altitude minimal de coast plus haut »*. Le bornage adaptatif des contraintes est limité tant qu'on n'a pas une trajectoire physiquement réaliste en bas LEO.
2. **Roadmap features long-terme.** `specs/brainstorm/features-long-terme.md` cite trois features dont l'atmosphère est pré-requis structurel : #13 (modèles de force avancés), #15 (réentrée atmosphérique avec heating), #22 (météo spatiale).
3. **Réalisme et crédibilité scientifique.** Sans atmosphère, la simulation surestime systématiquement les performances en ascension et ignore la décroissance orbitale en très basse orbite. La validation contre données réelles (#12) reste structurellement biaisée.

### 1.3 Périmètre du document

- **Inclus** : traînée aérodynamique sur trajectoires propulsées et balistiques, conséquences en ascension et en propagation orbitale.
- **Exclus** (références futures) : flux thermique de réentrée, ablation de bouclier, plasma/blackout radio (cf. feature long-terme #15), pression de radiation solaire (cf. #13), météo spatiale active (cf. #22).
- **Granularité** : Terre uniquement dans cette première itération. Le cadre doit toutefois rester extensible (Mars EDL → cf. §6.4).

## 2. Impacts fonctionnels

> "Qu'est-ce qui change pour l'utilisateur et pour le comportement
> observable de la simulation ?"

### 2.1 Phase d'ascension (lift-off → injection)

| Phénomène | Avant | Après |
|---|---|---|
| Pertes Δv par traînée | 0 | ~100–300 m/s typique sur un lanceur lourd (selon profil) |
| Pression dynamique Q (Pa) | non calculée | observable, pic vers 10–14 km d'altitude |
| Échauffement aéro indicatif | aucun | calcul possible (ρ·v² indicateur) |
| Marge propergol post-MECO | sur-estimée | revue à la baisse |
| Profil pitch optimal | piqué possible très tôt | retardé / lissé pour limiter la pression dynamique |

**Conséquence métier** : un GT optimisé sans drag va atteindre l'apogée
cible avec moins de propergol que ne le ferait un vrai lanceur.
Activer la traînée recale la simulation sur des ordres de grandeur
réalistes (Falcon 9, Ariane 5/6, Soyouz). C'est un pas important pour
la feature long-terme #12 (validation contre données réelles).

### 2.2 Phase de transfert / coast (LEO basse)

| Cible | Effet du drag |
|---|---|
| 185 km | Décroissance significative en quelques heures à quelques jours. Periapsis "rentre" en atmosphère dense rapidement. |
| 300 km | Décroissance lente mais mesurable sur la durée d'une mission (heures simulées). |
| 400 km (ISS) | Drag faible mais non nul, périodique avec F10.7. |
| > 800 km | Drag négligeable. |

**Conséquence métier** : à 185 km la mission "réussit" aujourd'hui
parce que la cible est circulaire géométriquement, mais en réalité
l'orbite n'est pas stable. Avec drag actif, on peut détecter et
afficher la durée de vie orbitale (orbital lifetime). Cela change
la signification même de "mission réussie" pour les très basses
orbites.

### 2.3 Optimiseur de mission

L'introduction du drag réécrit certaines hypothèses du coût.

#### 2.3.1 `GravityTurnProblem`

- L'apogée atteinte pour un même `(transitionTime, exponent)` sera **plus basse** : la pénalité `apogee < targetApogee` se déclenche plus souvent → l'optimiseur cherche à allonger / pencher davantage le profil.
- La vitesse tangentielle de fin de GT (`vTangential`) sera également plus basse → la pénalité `vTangential < minVtan` mord plus.
- Le terme `W_P · (massConsumed/initialMass)` augmente naturellement parce qu'il faut plus de propergol pour atteindre la cible. **Les pondérations peuvent rester valides**, mais les coûts absolus de référence pour `acceptableCost` sont à recalibrer.
- Le `periapsisFloor` (`100 km` par défaut) doit être relevé : à 100 km la densité atmosphérique fait déjà chuter l'orbite en heures. Cf. `specs/optimizer/02-altitude-dependent-design.md` §2.4 : `periapsisFloor = max(120 km, targetAlt − 100 km)`.

#### 2.3.2 `TransferTwoManeuverProblem`

- L'estimation analytique de Hohmann (`dv1`, `dv2`) ne change pas, mais **le Δv réellement consommé augmente**. Le `dt1MaxPhysical` (calculé sur le propergol restant) peut devenir saturant pour les cibles basses → ajouter un check de faisabilité a priori incluant une **réserve drag**.
- Le coût `errApo` est calculé sur la trajectoire propagée avec drag : si le drag est fort à 185 km, la « cible apogée » oscille pendant la propagation post-burn-2. Le calcul doit se faire **sur un échantillon stable** (apogée moyenne sur la première période, par exemple) pour éviter de pénaliser la dispersion intrinsèque à l'orbite basse.
- Les barrières `altMax` et `periapsis floor` deviennent activables par le **drag seul** (sans erreur de pilotage). Le sens des barrières change : aujourd'hui c'est "ne pas se vautrer", demain c'est aussi "tenir N révolutions sans rentrer".

#### 2.3.3 Convergence et budget compute

- **Espace de recherche élargi** : pour compenser la traînée, l'optimiseur devra explorer des `transitionTime` plus longs, des `α1` plus grands, des `dt1` plus gros. Les bornes physiques (cf. doc 02 du dossier optimizer) absorbent une partie ; le reste tombe dans la valeur de `sigma`.
- **Budget compute** : la traînée ajoute un appel à un modèle d'atmosphère par pas d'intégration. NRLMSISE-00 est notablement plus coûteux que Harris-Priester. Sur une optimisation CMA-ES de quelques milliers d'évaluations, c'est mesurable. Voir §4.4.

### 2.4 UI / UX

#### 2.4.1 Configuration côté wizard

Aujourd'hui aucun champ. Quatre options (à trancher §5) :

1. **Atmosphère toujours activée**, modèle Harris-Priester (faible coût, statique). Aucun champ ajouté.
2. **Toggle simple** "Activer atmosphère" dans `StepParameters`. Modèle implicite (Harris-Priester).
3. **Sélecteur de fidélité** (Off / Static / Realistic) dans `StepParameters`, mappé vers (rien / Harris-Priester / NRLMSISE-00).
4. **Step dédié** `StepAtmosphere` avec date solaire, F10.7, Kp (uniquement utile une fois la météo spatiale #22 introduite).

Recommandation : option 3 par défaut, démarrer en option 2 si la pression UI est forte.

#### 2.4.2 Télémétrie

- Nouveau widget : pression dynamique Q (Pa), altitude où elle atteint son max, drag instantané (N).
- Existant `TelemetryWidgetAppState` à étendre.
- Surcouche réentrée plus tard (#15).

#### 2.4.3 Mission display & analytics

- La courbe `altitude(t)` montrera la décroissance orbitale → utile pour la feature #3 (analytics post-mission).
- Le profil `Q(t)` ouvre une lecture pédagogique forte (MaxQ).

### 2.5 Comportements émergents à anticiper

- Une mission optimisée avant le merge "drag-on" peut **échouer** après le merge (apogée trop basse, propergol insuffisant). → Stratégie de migration : conserver le code "no-drag" derrière un flag, ou re-baseline les tests d'intégration. Voir §5.
- Les cibles GTO (cf. `specs/optimizer/03-robustness-roadmap.md` §5) commencent typiquement par un parking 200–400 km : **le parking subit le drag**. La tenue du parking jusqu'à l'injection est une nouvelle contrainte.
- Les missions interplanétaires partent souvent depuis un parking LEO 200–250 km : même conséquence.

## 3. Impacts techniques

> "Quelles classes, quels patterns, quel risque de régression ?"

### 3.1 Couche Orekit (briques disponibles)

Orekit 13.1.1 (déjà présent dans `build.gradle`) fournit nativement :

| Brique | Rôle |
|---|---|
| `org.orekit.forces.drag.DragForce` | Force traînée à ajouter au `NumericalPropagator`. |
| `org.orekit.forces.drag.IsotropicDrag` | Modèle simple Cd × surface (suffit pour début). |
| `org.orekit.models.earth.atmosphere.Atmosphere` | Interface des modèles. |
| `org.orekit.models.earth.atmosphere.HarrisPriester` | Statique, peu coûteux, 0–1000 km. Bon défaut pour optimisation. |
| `org.orekit.models.earth.atmosphere.NRLMSISE00` | Réaliste, dépend de F10.7 / Ap. Bon défaut pour playback / validation. |
| `org.orekit.models.earth.atmosphere.data.MarshallSolarActivityFutureEstimation` | Fournisseur d'indices solaires (pour NRLMSISE-00). |
| `org.orekit.models.earth.atmosphere.data.CssiSpaceWeatherData` | Alternative aux indices solaires. |

**Bonne nouvelle** : aucune dépendance à ajouter, aucune couche "binding" à écrire. **Mauvaise nouvelle** : les modèles atmosphériques ont besoin de **fichiers de données** supplémentaires (indices solaires, géomagnétiques) qui sont dans `orekit-data.zip` standard, mais à vérifier sur la version embarquée.

### 3.2 `OrekitService` — point d'injection central

État cible (esquisse) :

```java
// Nouveau
public NumericalPropagator createOptimizationPropagator(AtmosphereModel atmModel) {
  NumericalPropagator p = ...; // comme aujourd'hui
  p.addForceModel(getLightGravityModel());
  if (atmModel != AtmosphereModel.NONE) {
    p.addForceModel(buildDragForce(atmModel));
  }
  return p;
}
```

Trois choix de design à trancher :

1. **Signature des factories** : ajouter un paramètre `AtmosphereModel` ou injecter un contexte (`PropagationProfile`) qui regroupe le modèle d'atmosphère + le Cd + la surface. **Recommandé** : regroupement, sinon explosion combinatoire de surcharges.
2. **Caching du modèle d'atmosphère** : Harris-Priester est immuable une fois construit, NRLMSISE-00 dépend des données solaires. Mêmes patterns que `lightGravityModel` (volatile, double-checked locking).
3. **Cohérence Optim ↔ Default** : si l'optimisation utilise un modèle plus simple (Harris-Priester) que la propagation runtime (NRLMSISE-00), l'écart "propagator gap" — déjà présent sur la gravité (8×8 vs 50×50) — s'amplifie. Voir §4.3.

### 3.3 Modèle véhicule — propriétés aérodynamiques

Aujourd'hui `Spacecraft` et `LaunchVehicle` n'ont **ni Cd ni surface
de référence**. C'est la modification la plus structurante du chantier.

Trois options (par ordre d'impact croissant) :

| Option | Description | Avantages | Inconvénients |
|---|---|---|---|
| **A. Constantes par défaut** | Hard-coder `Cd=2.2`, `S_ref` calculé heuristique (∝ dryMass^(2/3)). | Aucun changement de signature, déploiement immédiat. | Inflexible. Tous les engins identiques. Ne tient pas la route pour la validation #12. |
| **B. Champs additionnels sur `Vehicle`** | Ajouter `dragCoefficient(): double` et `crossSection(): double` sur l'interface `Vehicle`. Implémentations par défaut sur les records. | Local, facile à réviser. | `record` Java → cassure ABI à chaque ajout. Pas idéal pour l'extensibilité (SRP, masse mouillée, etc.). |
| **C. Record dédié `AerodynamicProperties`** | Nouveau record `AerodynamicProperties(double dragCoefficient, double crossSection, double liftCoefficient)`. Champ optionnel (`Optional<AerodynamicProperties>`) sur `Spacecraft`/`LaunchVehicle`. Stack composite dérive sa surface depuis le stage actif. | Évolutif (lift, normales, BLH), nullable = "no aero". | Plus de surface API. Demande règle claire pour `VehicleStack.activeStage`. |

**Recommandation** : option **C**, avec valeurs par défaut quand le
champ est `null` (rétrocompat des scénarios existants).

Détails :

- `Vehicle.crossSection()` agrégé : la `VehicleStack` retourne la surface du stage actif (cohérent avec `propulsion()` qui retourne le moteur du stage actif).
- `Vehicle.dragCoefficient()` agrégé : idem.
- Surface de référence : varie de l'ascension (face frontale lanceur, ~10 m² Falcon 9) au satellite déployé (panneaux solaires, ~30–80 m²). Pour rester simple, exposer **une** valeur par véhicule, l'utilisateur la choisit selon le contexte de mission.

### 3.4 Stages et manœuvres — point d'injection du drag

Le drag, dans Orekit, est ajouté comme un `ForceModel` au propagateur,
exactement comme la gravité ou un `ConstantThrustManeuver`.

Aujourd'hui les stages utilisent ce pattern :

```java
public void configure(NumericalPropagator propagator, Mission mission) {
  propagator.addForceModel(burn);
  propagator.addEventDetector(mecoDetector);
}
```

Le drag est **propagator-level**, donc il **doit être ajouté à la
création du propagateur**, pas dans chaque stage. Cela signifie :

- `OrekitService.createXxxPropagator(...)` reçoit la config aéro et ajoute `DragForce` une fois pour toutes.
- Les stages **ne touchent à rien** côté drag.

Mais : dans `GravityTurnManeuver.propagateForOptimization` et
`TransfertTwoManeuver.propagateForOptimization`, le propagateur est
créé localement via `OrekitService.get().createOptimizationPropagator()`.
Ces deux endroits sont les **points de modification critiques** : il
faut leur passer le contexte aéro.

L'idéal architectural : un `PropagationContext` (ou `MissionContext`,
déjà existant) passé au propagateur, qui transporte :

- modèle d'atmosphère sélectionné,
- propriétés aéro de l'engin courant (peut changer après séparation des étages !),
- date / époque de référence (utile pour NRLMSISE-00).

Le second point — `Vehicle` change pendant la mission (séparation
S1) — est le plus délicat. Orekit gère le cas via
`PropulsionSystem`-style force models qui consultent `SpacecraftState`,
mais pour le drag il faudra un `IsotropicDrag` qui **lit la masse et
la surface depuis l'état courant** ou être recréé à chaque transition
de stage. Voir §6.2.

### 3.5 Détecteurs — nouvelles règles

Détecteurs existants à revisiter :

- `MinAltitudeTracker` : la sémantique reste valable (détection sol/borne basse), mais les seuils `altitudeThreshold` doivent suivre le `periapsisFloor` adaptatif (cf. §2.3.1).
- `MassDepletionDetector` : inchangé.

Nouveaux détecteurs candidats :

- **`MaxQDetector`** (`g(state) = q − qThreshold` où `q = 0.5 ρ v²`). Émet un événement métier (event-bus) → utile pour télémétrie et pour future analyse de charge structurelle.
- **`AtmosphericInterfaceDetector`** (passage à 100 km Karman line). Pré-requis pour la future réentrée (#15).
- **`OrbitalDecayDetector`** (à terme) : détecte le moment où la périgée passe sous un seuil critique → marque la fin de vie orbitale.

### 3.6 Tests — risque de régression

Les tests d'intégration impactés :

- `LEOMissionOptimizationTest` valide actuellement une insertion à 400 km à ±7 % près. À 400 km le drag est faible (< 1 % d'écart de Δv typique) → ce test continuera vraisemblablement de passer, mais la marge va se serrer. **Action** : lancer le test avec drag activé et observer la marge réelle avant de durcir le critère.
- `AbstractTrajectoryOptimizerTest.propagateMission()` → dépend du propagateur configuré. Si on active le drag par défaut, **toutes** les missions de test sont impactées.
- Suite d'altitude paramétrique (`LEOAltitudeSweepTest` planifiée dans `specs/optimizer/03-robustness-roadmap.md` §4) : doit obligatoirement intégrer un mode "drag-on" pour 185–300 km.

Tests à créer :

- Reproductibilité : même seed CMA-ES, même engin, même date → même résultat à 1e-6 près. Garantit que l'ajout du drag est déterministe.
- Comparaison drag-on / drag-off à 800+ km : l'écart doit être < 0.1 % (sanity check).
- Couplage NRLMSISE-00 : avec `F10.7` à valeurs extrêmes (70 vs 250), l'altitude à 24h d'un parking 250 km doit varier de manière mesurable.

### 3.7 Performances

Estimation grossière (à valider) :

| Modèle atmosphère | Coût relatif par appel | Coût ajouté sur une optim CMA-ES (~3000 évals × ~1000 steps) |
|---|---|---|
| Aucun (état actuel) | 1× | 0 |
| Harris-Priester | ~1.05× | +5 % temps total |
| NRLMSISE-00 | ~1.30–1.50× | +30–50 % temps total |
| NRLMSISE-00 + indices solaires dynamiques | ~1.50× | +50 % temps total |

→ **Recommandation** : Harris-Priester pour l'optimisation, NRLMSISE-00
pour la propagation runtime / playback / validation. Cohérent avec la
philosophie actuelle (8×8 optim, 50×50 default).

### 3.8 Configuration & sérialisation (futur)

- `SimulationConfig` n'a aujourd'hui **aucun champ "atmosphère"**. Cela viendra avec la feature #1 (format scénario).
- À court terme : champ par mission (`MissionConfig` n'existe pas encore en tant que record persisté → impact UI uniquement, état porté par le wizard).
- À moyen terme, intégrer dans le scénario v1 :
  ```
  atmosphere:
    model: NRLMSISE-00 | HarrisPriester | none
    solar:
      f107: 150
      ap: 9
  ```

## 4. Décisions à trancher

Avant d'écrire la première ligne de code :

1. **Modèle par défaut** : Off ? Harris-Priester ? NRLMSISE-00 ? Recommandation : **Harris-Priester pour l'optimisation, NRLMSISE-00 pour le playback**, jamais "off" en prod. Off seulement pour tests / mode pédagogique.
2. **Cd & surface par défaut** quand `AerodynamicProperties` est `null` : `Cd=2.2`, `S=10 m²` (lanceur) / `S=2 m²` (spacecraft). Documenter la convention.
3. **Mass effective sur la traînée** : Orekit `IsotropicDrag` calcule `F = -0.5 · ρ · v² · Cd · S · v̂`. La masse intervient dans `a = F/m` côté propagateur. Vérifier que `SpacecraftState.getMass()` est bien à jour à chaque pas (séparation des étages).
4. **Surface lors d'un `VehicleStack`** : prendre la surface du stage actif ? Ou la max (pénalisant ce qui simule une coiffe / fairing) ? Recommandation : stage actif. Ouvre la voie à un futur "fairing jettison stage".
5. **Indices solaires statiques vs dynamiques** : MarshallSolarActivityFutureEstimation lit des fichiers historiques. Pour les missions futures (date > today), il faut une stratégie (extrapolation cycle solaire ?). Recommandation : valeurs constantes par défaut (F10.7=150, Ap=9), dynamiques quand la feature météo spatiale (#22) sera prête.
6. **Activation au runtime** : flag global, par mission, ou les deux ? Recommandation : par mission (cohérent avec wizard), avec un défaut global dans `SimulationConfig`.
7. **Compatibilité ascendante des optimisations passées** : on stocke parfois un `MissionOptimizerResult` pour rejouer. Si le modèle de propagation change, le replay diverge. Recommandation : marquer chaque résultat avec `formatVersion` + `atmosphereModel` (utile pour la feature scénario #1).
8. **Floor d'atmosphère** : à quelle altitude tronquer le modèle ? Harris-Priester va jusqu'à 1000 km, NRLMSISE jusqu'à 1000 km. En dessous, certains modèles ne sont pas définis. Recommandation : floor à 80 km (à confirmer avec docs Orekit).

## 5. Plan de mise en œuvre suggéré

> Basé sur le pattern phasé de `specs/optimizer/03-robustness-roadmap.md`.

### Phase 0 — Prototype isolé

- Test ad-hoc : créer un `NumericalPropagator` avec gravity 8×8 + `DragForce(HarrisPriester, IsotropicDrag(2.2, 10))`, propager un parking 250 km sur 24h, observer la décroissance.
- Objectif : valider la boucle Orekit, mesurer le coût réel par pas.
- Aucune modification du code de production.

### Phase 1 — Modèle véhicule

- Introduire `AerodynamicProperties` (record), champ optionnel sur `Spacecraft`/`LaunchVehicle`/`VehicleStack`.
- Constructeurs par défaut conservés (champ `null` → constantes).
- Tests unitaires : agrégation par stage, valeurs par défaut.

### Phase 2 — `OrekitService` étendu

- Ajouter `AtmosphereModel` enum (`NONE`, `HARRIS_PRIESTER`, `NRLMSISE`).
- Surcharger les factories `createXxxPropagator(AtmosphereModel, AerodynamicProperties)`.
- Conserver les anciennes signatures (`= NONE` par défaut) pour ne pas casser les call-sites.
- Caching des modèles atmosphériques.

### Phase 3 — Câblage des manœuvres

- `GravityTurnManeuver.propagateForOptimization` et `TransfertTwoManeuver.propagateForOptimization` reçoivent la config aéro depuis `Mission` ou `MissionContext`.
- Pas de changement dans les `*Stage` (drag posé au niveau propagateur).

### Phase 4 — Optimiseur : recalibration

- `GravityTurnConstraints` : ajuster `periapsisFloor` (cf. doc 02 §2.4).
- `TransferTwoManeuverProblem` : adapter le `dt1MaxPhysical` pour absorber les pertes drag estimées (Δv_drag ≈ ∫q·Cd·S/m dt → estimation grossière par table altitude → 1 valeur).
- Re-lancer la suite paramétrique `[185, 250, 400, 600, 800, 1200, 1500, 2000]` km.

### Phase 5 — Détecteurs & télémétrie

- Implémenter `MaxQDetector`, `AtmosphericInterfaceDetector`.
- Étendre `TelemetryWidgetAppState` avec Q et drag.
- Émission d'événements via `EventBus` pour future cinématique (#4).

### Phase 6 — UI

- Ajouter sélecteur de modèle atmosphérique dans `StepParameters` (option "Atmosphère : Off / Statique / Réaliste").
- Champ `Cd` / `Surface` dans `StepLauncher` (avancé, valeurs par défaut sinon).

### Phase 7 — Tests de régression

- Re-baseline `LEOMissionOptimizationTest` avec drag-on (Harris-Priester par défaut).
- Nouveau test `LowOrbitDecayTest` : parking 200 km, 24h, vérifier décroissance > 0 et < seuil catastrophe.

### Phase 8 — Préparer la suite (sans implémenter)

- Réserver l'extensibilité pour réentrée (#15) : structure du `MaxQDetector` réutilisable pour heating ; `AtmosphericInterfaceDetector` est déjà le hook entrée atmosphère.
- Documenter dans `specs/atmosphere/02-...` le plan reentry, le plan SRP/lunisolaire, le plan météo spatiale.

## 6. Compléments

### 6.1 Couplage avec les autres specs

| Spec | Recouvrement |
|---|---|
| `specs/optimizer/02-altitude-dependent-design.md` | §1 (`apogeeMin_safe`), §2.4 (`periapsisFloor adaptatif`) deviennent **physiquement justifiés** au lieu d'empiriques. |
| `specs/optimizer/03-robustness-roadmap.md` | Question ouverte §"Drag" tranchée par ce doc. |
| `specs/brainstorm/features-long-terme.md` #13 | Ce doc est le **premier livrable** de #13 (drag) ; SRP/lunisolaire suivront. |
| `specs/brainstorm/features-long-terme.md` #15 | Pré-requis structurel : atmosphère, détecteur d'interface. |
| `specs/brainstorm/features-long-terme.md` #22 | Pré-requis : indices solaires (F10.7/Kp) consommés par NRLMSISE-00. |

### 6.2 Risques techniques notables

- **Instabilité numérique en très basse altitude** : à 100 km, ρ varie sur 4 ordres de grandeur sur 50 km de variation d'altitude. Le pas adaptatif `DormandPrince853` doit gérer — mais peut nécessiter de durcir les tolérances. À mesurer.
- **Cohérence du `state.getMass()`** : pendant un burn, la masse change vite. `IsotropicDrag` lit la masse à chaque pas → OK avec Orekit. Lors d'une **séparation d'étage** (transition stage), il faut s'assurer que le `DragForce` voit la nouvelle surface. Si la surface est encodée dans un `IsotropicDrag` créé une fois, il faut le recréer ou utiliser un `IsotropicDrag` paramétrique.
- **Dépendance aux données Orekit** : `MarshallSolarActivityFutureEstimation` dépend de fichiers présents dans `orekit-data.zip`. Vérifier le contenu de la version embarquée (cf. `.gitignore` : `src/main/resources/` est exclu, donc la zip est chargée à part).
- **Reproductibilité** : NRLMSISE-00 lit les fichiers d'indices selon la **date de simulation**. Deux runs à dates différentes ne peuvent pas être comparés directement. Stratégie : fixer `F10.7` et `Ap` constants pour les tests.

### 6.3 Métriques de succès

À documenter dans le PR final :

1. `LEOMissionOptimizationTest` 400 km converge avec drag-on, écart < 7 %.
2. Test 250 km converge avec drag-on, écart < 10 %.
3. Test 185 km converge avec drag-on, periapsis post-insertion ≥ 160 km.
4. Décroissance d'un parking 250 km sur 24h : 0.5–3 km (ordre de grandeur attendu, F10.7=150).
5. Coût compute additionnel mesuré sur l'optim 400 km : < +50 % temps.

### 6.4 Hors-périmètre explicite

- **Atmosphère martienne** (DTM-Mars, Mars-GRAM) : pertinent pour Mars EDL (#15 + #14). Réutilisera `Atmosphere` interface Orekit.
- **Vénus, Titan** : très long terme.
- **Ablation, échauffement** : module séparé (#15).
- **Pression de radiation solaire** : `SolarRadiationPressure` Orekit. Spec dédiée à venir.
- **Effets lunisolaires (third-body perturbations)** : `ThirdBodyAttraction`. Spec dédiée à venir.

## 7. Fichiers de code concernés (estimation)

| Fichier | Modification attendue |
|---|---|
| `simulation/OrekitService.java` | Ajout `AtmosphereModel`, factories surchargées, cache |
| `simulation/mission/vehicle/AerodynamicProperties.java` | **Nouveau** record |
| `simulation/mission/vehicle/Vehicle.java` | Méthode `aerodynamics()` (default null) |
| `simulation/mission/vehicle/Spacecraft.java` | Champ optionnel |
| `simulation/mission/vehicle/LaunchVehicle.java` | Champ optionnel |
| `simulation/mission/vehicle/VehicleStack.java` | Délégation au stage actif |
| `simulation/mission/maneuver/GravityTurnManeuver.java` | Propagation du contexte aéro |
| `simulation/mission/maneuver/TransfertTwoManeuver.java` | Idem |
| `simulation/mission/optimizer/problems/GravityTurnConstraints.java` | `periapsisFloor` adaptatif |
| `simulation/mission/optimizer/problems/TransferTwoManeuverProblem.java` | Réserve drag dans `dt1MaxPhysical` |
| `simulation/mission/detector/MaxQDetector.java` | **Nouveau** |
| `simulation/mission/detector/AtmosphericInterfaceDetector.java` | **Nouveau** |
| `app/SimulationConfig.java` | Champ `atmosphereModel` (défaut) |
| `ui/mission/wizard/step/StepParameters.java` | Sélecteur modèle atmosphère |
| `ui/mission/wizard/step/StepLauncher.java` | Champs Cd / surface (avancé) |
| `ui/telemetry/...` | Widget Q et drag |
| `test/.../LEOMissionOptimizationTest.java` | Re-baseline |
| `test/.../LowOrbitDecayTest.java` | **Nouveau** |
| `test/.../AtmosphereModelTest.java` | **Nouveau** : sanity Harris-Priester vs NRLMSISE-00 |

---

*Document rédigé le 2026-05-05. Itération 0 : impacts. Les itérations
suivantes (`02-...`, `03-...`) traiteront du design détaillé puis du
plan d'implémentation.*
