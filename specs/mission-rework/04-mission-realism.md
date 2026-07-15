# Document fonctionnel — Réalisme de la simulation de mission

> Périmètre : ce document est **fonctionnel**. Il décrit l'état actuel, les écarts au réel, des recommandations et une roadmap. Aucune implémentation n'est faite ici.
> Hypothèse : **l'atmosphère est négligée** dans toute cette étude (sujet d'un travail dédié ultérieur).

---

## 1. Contexte

La simulation actuelle d'OrbitLab produit des trajectoires LEO et GEO physiquement plausibles mais souffre d'un manque de réalisme à trois niveaux :

1. Le **lanceur** est figé en dur dans `LaunchVehicle.java` avec des valeurs numériques qui ne correspondent à aucun lanceur réel ; il est utilisé à l'identique pour LEO et GEO, ce qui contredit la pratique aérospatiale où la masse de propergol embarquée est dimensionnée pour chaque mission.
2. Le **profil de vol** comporte des choix simplificateurs (ascension verticale de 10 s en dur, burns de transfert calculés analytiquement avec une durée déterministe via Tsiolkovsky, absence de coast inter-étage, GEO où l'étage supérieur fait l'apogée) qui éloignent la simulation d'un vol réel.
3. L'**optimisation** ne porte que sur la trajectoire (timing, durée, angles de poussée). La quantité de propergol embarquée n'est jamais optimisée et la condition d'arrêt « épuisement du réservoir » n'est pas câblée (le détecteur existe pourtant : `MassDepletionDetector`).

Le but de ce document est d'évaluer ces écarts et de proposer un chemin progressif vers une simulation où **chaque mission embarque la juste quantité de propergol** dans un lanceur réaliste, et où **chaque étage est consommé jusqu'à épuisement**.

---

## 2. État des lieux

### 2.1 Lanceur (valeurs actuelles)

Source : `simulation/mission/vehicle/LaunchVehicle.java`, `Spacecraft.java`, `PropulsionSystem.java`.

| Élément       | Dry mass | Propergol max | ISP   | Poussée  | Mass flow |
|---------------|----------|---------------|-------|----------|-----------|
| Étage 1       | 27 000 kg | 425 000 kg   | 300 s | 8 400 kN | ~2 858 kg/s |
| Étage 2       | 5 000 kg  | 134 000 kg   | 348 s | 1 250 kN | ~366 kg/s |
| Spacecraft    | 150 kg    | 0 kg         | 300 s | 3 kN     | ~1 kg/s |

- Stack assemblé par `VehicleStack` (`vehicle/VehicleStack.java:12`). `resolveActiveStage(currentMass)` existe déjà et permet de basculer dynamiquement d'étage en fonction de la masse cumulée — la mécanique de jettison est donc déjà prête.
- **Le stack est identique pour LEO et GEO** : `LEOMission.buildVehicle()` (`operation/LEOMission.java:134`) et `GEOMission.buildVehicle()` (`operation/GEOMission.java:112`) appellent les mêmes factories statiques.
- Incohérence test/code à corriger : `VehicleTest` ligne 62 attend dry stage 2 = 10 000 kg alors que le code retourne 5 000 kg.
- Le spacecraft a un propergol nul mais un moteur de 3 kN — sa propulsion est inutilisable en l'état.

### 2.2 Profil de vol actuel

Source : `simulation/mission/operation/LEOMission.java`, `GEOMission.java`, `stage/*`.

**LEO** :
```
1. VerticalAscentStage(10 s)                  ← ASCENSION_DURATION en dur
2. GravityTurnStage(10 s ascent, 3.0 kick, …) ← optimisé (transitionTime, exponent)
3. AnalyticHohmannTransferStage               ← 2 burns finite-thrust, ΔV analytique
4. AnalyticTrimBurnStage                      ← 1 burn de correction à l'apogée
5. CoastingStage(∞)
```

**GEO** :
```
1. VerticalAscentStage(10 s)
2. GravityTurnStage
3. AnalyticParkingInsertionStage              ← injection LEO ~200 km
4. CoastingStage(stopAtNode=true)             ← attente du nœud équatorial
5. AnalyticHohmannTransferStage               ← LEO → 35 786 km
6. AnalyticTrimBurnStage
7. CoastingStage(∞)
```

Points clés :
- `ASCENSION_DURATION = 10` (constante de `LEOMission` / `GEOMission`) est passée à la fois au `VerticalAscentStage` **et** au `GravityTurnStage` (qui consomme du propergol étage 1 zenith pendant ces 10 s avant de débuter le pitch program).
- Les stages dits « Analytic… » sont en réalité **finite-thrust** : le ΔV cible est calculé analytiquement (vis-viva, Hohmann avec compensation J2), puis la durée du burn est dérivée via Tsiolkovsky (`Physics.java:46` : `dt = m·Isp·g0/F · (1 − exp(−ΔV/(Isp·g0)))`) et exécuté par un `ConstantThrustManeuver` Orekit.
- `GravityTurnManeuver` (`maneuver/GravityTurnManeuver.java:82-96`) **épuise déjà tout le propergol de l'étage 1** puis bascule sur l'étage 2 jusqu'à `transitionTime`. La logique d'épuisement existe donc partiellement.
- Les transitions de stage se font sur `DateDetector` / `NodeDetector` / `ApsideDetector`. `MassDepletionDetector` (`detector/MassDepletionDetector.java:13`) **n'est pas câblé** dans le pipeline.
- Pas de coast inter-étage : la séparation étage 1 / étage 2 est instantanée et la poussée étage 2 s'allume immédiatement.
- Architecture GEO : l'étage supérieur fait à la fois l'injection GTO et la circularisation GEO. Dans la pratique réelle, c'est le spacecraft (Apogee Kick Motor) qui circularise.

### 2.3 Optimisation actuelle

Source : `simulation/mission/runtime/MissionOptimizer.java`, `optimizer/problems/*`.

- CMA-ES par stage, 3 tentatives max (`CMAESTrajectoryOptimizer:49`), seed analytique (Hohmann) pour les transferts, heuristique pour le gravity turn.
- Variables optimisées :
  - `GravityTurnProblem` (2D) : `transitionTime`, `exponent` du profil de pitch.
  - `TransferProblem` / `TransferTwoManeuverProblem` (4D) : `t1` (offset de début de burn), `dt1` (durée du burn), `α1` (angle in-plane TNW), `β1` (angle out-of-plane).
- **La masse de propergol embarquée n'est jamais une variable d'optimisation.** Elle apparaît uniquement :
  - Comme borne supérieure de `dt1` : `(propergol_disponible × 0.90) / massFlow` (`TransferProblem:263`).
  - Comme terme de coût mineur dans le gravity turn : `W_P × (m_init − m_final) / m_init` avec `W_P = 9e-5` (poids ~négligeable).
- L'objectif (`OrbitInsertionObjective`) expose perigee, apogee, inclinaison cibles.

---

## 3. Analyse de réalisme et écarts au réel

### 3.1 Lanceur — comparaison avec Falcon Heavy

Valeurs publiques de Falcon Heavy (mode expendable, sources SpaceX / NSF / Wikipedia) :

| Élément              | Réalité Falcon Heavy                     | Modèle actuel        | Écart |
|----------------------|------------------------------------------|----------------------|-------|
| Core central + boosters (3 cœurs Merlin 1D, kerolox) | dry ~22 t × 3 = ~66 t / propergol ~411 t × 3 = ~1 233 t / poussée totale ~22 800 kN au sol, ISP ~282 s sea / 311 s vac | dry 27 t, propergol 425 t, poussée 8 400 kN, ISP 300 s | **Sous-dimensionné d'un facteur ~3** ; ISP plausible |
| Upper stage (1 Merlin Vacuum, kerolox) | dry ~4 t / propergol ~107 t / poussée 981 kN / ISP 348 s vac | dry 5 t / 134 t / 1 250 kN / 348 s | Ordres de grandeur **cohérents**, légèrement sur-dimensionné |
| Charge utile vers LEO | ~63 800 kg                              | non explicitée       | n/a   |
| Charge utile vers GTO | ~26 700 kg                              | non explicitée       | n/a   |

**Diagnostic** : le modèle actuel ressemble à un Falcon 9 « single-core » avec un étage 1 légèrement boosté plutôt qu'à un Falcon Heavy. Pour modéliser FH fidèlement, il faut soit :
- Agréger les 3 cœurs en un seul « étage 1 » virtuel (dry 66 t, prop 1 233 t, poussée 22 800 kN, ISP 282 s) — simple, mais ne reflète pas le jettison des side boosters.
- Modéliser un « étage 0 » (side boosters) qui se largue avant le core central — plus réaliste, demande une refonte mineure du `VehicleStack` pour supporter le jettison à mi-burn de l'étage 1.

> **Décision retenue (cf. AskUserQuestion) : Falcon Heavy d'abord, agrégé en stage 1 unique pour la première itération.** L'Ariane 5 (avec ses 2 boosters solides EAP non éteignables, son corps Vulcain LH2/LOX et un upper stage ESC-A) viendra dans une itération ultérieure et imposera la notion d'étage solide à épuisement.

### 3.2 Capacité vs. charge embarquée

Sémantique actuelle : `propellantCapacity` est utilisée à la fois comme **capacité maximale du réservoir** (donc indissociable de la dry mass) et comme **quantité réellement embarquée**.

Dans la pratique réelle :
- Le réservoir a une taille fixe → contribue à la dry mass de l'étage (et c'est fixé par la conception du lanceur).
- Le « propellant load » est variable mission par mission. Falcon 9 par exemple charge moins pour permettre une récupération du booster (RTLS ou ASDS), ou charge plein pour un GTO lourd en mode expendable.

**Recommandation** : introduire un champ supplémentaire `propellantLoad ≤ propellantCapacity`. La `dry mass` reste constante. C'est cette `propellantLoad` qui sera la variable de la boucle d'optimisation externe.

### 3.3 Profil de vol — comparaison avec un vol LEO/GEO réel

**Séquence LEO réelle typique** :
1. Tower clearance / ascension purement verticale (~7–15 s).
2. Pitch program initial (kick-over).
3. Gravity turn jusqu'à MECO (Main Engine Cut-Off).
4. Stage separation (1–3 s d'inertie).
5. Ignition étage 2.
6. SECO-1 à l'injection sur orbite de parking ou directement sur orbite cible.
7. (Optionnel) coast jusqu'au point de circularisation.
8. SECO-2 (circularisation).

**Séquence GEO réelle typique** :
1–7. Comme LEO jusqu'à l'orbite de parking.
8. Coast en parking (attente du nœud équatorial — déjà modélisé).
9. Réallumage étage 2 → injection GTO (burn périgée).
10. Séparation étage 2 / spacecraft.
11. Coast spacecraft jusqu'à l'apogée du GTO (~5 h).
12. Burn apogée (AKM — Apogee Kick Motor du spacecraft) : circularisation **et** changement de plan combinés.

**Écarts identifiés** :

| # | Écart | Conséquence |
|---|-------|-------------|
| E1 | Ascension verticale = 10 s en dur, partagée LEO/GEO | Pas réaliste pour comparer plusieurs lanceurs ; couplée artificiellement au gravity turn |
| E2 | Pas de coast entre MECO et ignition étage 2 | Légère sous-estimation des pertes gravitationnelles |
| E3 | Burns transfert/circularisation analytiques (durée déterministe Tsiolkovsky) | Les pertes de finite-burn ne sont pas optimisées ; on consomme « pile la durée » sans chercher le meilleur compromis |
| E4 | Pas de condition d'arrêt par épuisement de propergol au niveau du stage | Le `MassDepletionDetector` existe mais n'est jamais utilisé pour découper les stages |
| E5 | En GEO, l'étage 2 fait à la fois GTO **et** circularisation | Architecture irréaliste (un upper stage cryotechnique ne survit pas 5 h en coast pour la plupart des lanceurs ; et c'est typiquement l'AKM du satellite qui circularise) |
| E6 | Spacecraft a 0 kg de propergol mais 3 kN de poussée | Le moteur d'apogée du satellite n'est pas utilisable |
| E7 | Propergol embarqué = capacité max, identique LEO et GEO | Aberration physique : on emporte le même propergol pour une mission 200 km circulaire et pour GEO |

### 3.4 Optimisation — limites actuelles

- **Sur-emport de propergol** : puisque `propellantLoad = capacity` toujours, et que les burns analytiques calculent la durée exacte pour atteindre l'orbite, l'étage 2 finit sa mission avec un reste de propergol important. Cette masse résiduelle traînée pendant tout le vol pénalise les performances réelles (ΔV gaspillé pour accélérer du propergol qui ne sera jamais brûlé).
- **Pas de couplage perf ↔ propergol** : aucune boucle ne cherche à minimiser la masse de propergol embarquée sous contrainte de réussite de la mission.
- **Critère de fin de stage temporel et non massique** : les `DateDetector` figent une durée plutôt que de laisser le moteur s'éteindre par flame-out, ce qui est la pratique réelle (sauf pour les stages éteignables comme les liquides).

---

## 4. Recommandations fonctionnelles

### 4.1 Lanceur — modèle Falcon Heavy paramétré

- Refactoriser les factories `LaunchVehicle.getLauncherStage1Vehicle()` / `getLauncherStage2Vehicle()` pour qu'elles ne soient plus des constantes globales mais des **modèles de lanceurs nommés** (`Launchers.FALCON_HEAVY`, plus tard `Launchers.ARIANE_5`).
- Distinguer **capacité** (taille du réservoir, contribue à la dry mass) et **charge embarquée** (propergol effectivement chargé). Ajouter `propellantLoad` aux records `LaunchVehicle` et `Spacecraft`.
- Valeurs FH cibles (1ʳᵉ itération, agrégé) :
  - Étage 1 : dry 66 000 kg / capacité 1 233 000 kg / ISP 282 s sea (ou moyenne 296 s) / poussée 22 800 kN.
  - Étage 2 : dry 4 000 kg / capacité 107 500 kg / ISP 348 s / poussée 981 kN.
  - Spacecraft (générique GEO sat) : dry 2 000 kg / capacité propergol AKM à définir / ISP 320 s typique / poussée 400 N (moteur d'apogée bipergol).
- Corriger l'incohérence `VehicleTest` ligne 62.

### 4.2 Configuration propergol par mission

- La factory de mission (`LEOMission`, `GEOMission`) doit fournir un `propellantLoad` initial par étage, **différent de la capacité maximale et différent entre LEO et GEO**.
- Première heuristique pour initialiser : utiliser l'équation de Tsiolkovsky inverse à partir du ΔV total nominal de la mission (estimé analytiquement) + marge de sécurité 10 %.
- À terme, cette charge initiale est elle-même issue de la boucle d'optimisation externe (§ 4.5).

### 4.3 Profil de vol révisé

- **E1** — Ascension verticale : devient un paramètre du modèle de lanceur (`launcher.verticalAscentDuration()`), pas de la mission. Valeurs : FH ~7 s, A5 ~6 s. Pas de variable d'optimisation. **Découpler** du `GravityTurnStage` (qui ne reçoit plus cette durée).
- **E2** — Coast inter-étage : ajouter un `CoastingStage` court (1–3 s) entre MECO et ignition étage 2. Paramètre du modèle de lanceur.
- **E5/E6** — Refonte GEO :
  - L'étage 2 fait `AnalyticParkingInsertionStage` + `CoastingStage(stopAtNode)` + injection GTO finite-thrust → puis se sépare (jettison).
  - Le `Spacecraft` (avec propergol AKM dimensionné) coast jusqu'à l'apogée GTO et exécute son propre `AnalyticHohmannTransferStage` (circularisation + changement de plan).
  - Cela demande de transformer le `VehicleStack` en deux entités successives : le lanceur (largué après GTO) et le spacecraft seul.
- **E3/E4** — Burns à épuisement :
  - Pour chaque stage de transfert, introduire une variante « burn until propellant depletion » qui s'arrête sur `MassDepletionDetector` plutôt que sur `DateDetector`.
  - Le seed initial reste le résultat analytique actuel (durée Tsiolkovsky-déterministe avec direction Hohmann), mais l'optimisation peut maintenant explorer des burns plus longs en partant d'un `propellantLoad` plus grand, ou plus courts si la mission est sur-dotée.

### 4.4 Câblage de `MassDepletionDetector`

- L'utiliser comme **condition d'arrêt primaire** des stages propulsés (gravity turn, transferts, AKM) chaque fois que le scénario réel correspond à un flame-out (ce qui est la pratique pour les solides et fréquent pour les liquides en upper stage).
- Conserver `DateDetector` pour les coasts.
- Pour le gravity turn, le détecteur de fin de stage 1 (MECO) devient `MassDepletionDetector` sur l'étage 1, ce qui remplace le calcul actuel `burn1Duration = remaining_propellant / massFlow`.

### 4.5 Optimisation imbriquée du propergol embarqué (idée de l'utilisateur)

**Architecture proposée** :

```
Boucle EXTERNE : optimisation de la masse de propergol embarquée
  Variables : propellantLoad par étage (n variables, n = nb étages avec propulsion)
  Bornes : 0 ≤ propellantLoad[i] ≤ propellantCapacity[i]
  Objectif : minimiser Σ propellantLoad[i]
  Contrainte : la mission réussit (orbite cible atteinte dans la tolérance)
                  ↓
                  Boucle INTERNE : optimisation de trajectoire existante (CMA-ES par stage)
                    Variables : transitionTime, exponent, t1, dt1, α1, β1 …
                    Nouvelle condition d'arrêt sur les stages propulsés : épuisement (MassDepletionDetector)
                    Seed initial : résultat des burns analytiques actuels
                    Objectif : pénalité d'erreur orbitale
                  Retourne : succès/échec + ephemeris + cost
```

**Algo externe possible** : CMA-ES également (un seul niveau de framework), avec un coût en deux termes :
- Terme principal : `Σ propellantLoad[i] / propellantCapacity[i]` (à minimiser).
- Terme barrière : si la boucle interne échoue (ne converge pas dans la tolérance), pénalité massive.

**Démarrage** :
1. Premier passage avec `propellantLoad[i] = propellantCapacity[i]` (sur-doté), pour valider que la boucle interne converge avec la mission actuelle (ré-implémentée avec arrêt par épuisement).
2. Puis lancement de la boucle externe qui réduit progressivement la charge.

**Coûts computationnels** : la boucle interne est déjà lente (CMA-ES + propagation Orekit). Imbriquer une boucle externe va multiplier le temps. Pistes pour rester tractable :
- Boucle externe à dimension réduite (typiquement 2–3 étages → 2–3 variables) → CMA-ES converge vite.
- Population réduite en externe (8–12 individus) puisque le gradient effectif sur `propellantLoad` est monotone.
- Warm start : chaque évaluation externe seed la boucle interne avec le résultat de l'évaluation précédente.

**Remarques importantes** :
- Si on baisse trop `propellantLoad`, certaines missions deviennent infaisables (pas assez de ΔV) → la boucle externe doit gérer le « pénalité d'infaisabilité » sans s'y bloquer (sigma adaptatif, ou retries comme dans `CMAESTrajectoryOptimizer` actuel).
- La masse réservoir n'est physiquement pas réduite quand on charge moins ; on ne récupère pas de dry mass.
- Pour les boosters solides Ariane 5 (à venir) : `propellantLoad = propellantCapacity` toujours (pas d'éteignable, pas de charge variable) → ces étages restent fixes dans la boucle externe.

### 4.6 Points complémentaires à ajouter à l'étude

Au-delà des points soulevés par l'utilisateur, je suggère d'inclure dans le doc :

- **Pertes de finite-burn** : avec arrêt par épuisement et angles optimisés, on capture naturellement les pertes que l'approximation Hohmann analytique ignore. À tester quantitativement.
- **Steering losses** : le `GravityTurnProblem` actuel optimise le profil de pitch, mais avec un modèle `exponent` 1D. Évaluer si une représentation plus riche (cubic spline 2–3 nœuds) améliore la performance avant de re-dimensionner le propergol.
- **Coast LEO de circularisation** : aujourd'hui, le passage du gravity turn aux burns de transfert se fait sans coast. Vérifier que c'est cohérent avec une stratégie « direct-ascent » vs. « ascent-with-parking-coast ». Ariane 5 GTO ne fait pas de parking ; Falcon Heavy GTO non plus typiquement. Donc l'absence de coast peut être OK selon la stratégie modélisée.
- **Charge utile** : exposer `payload mass` comme paramètre de mission permet de quantifier « combien de propergol faut-il pour mettre X kg en orbite Y ». C'est l'usage normal d'un outil de mission planning.

---

## 5. Roadmap fonctionnelle

Ordre proposé, chaque lot livre de la valeur indépendamment.

| # | Lot | Description | Critère de fin |
|---|-----|-------------|----------------|
| L1 | **Refactor lanceur — capacité vs charge** | Introduire `propellantLoad`. Renommer `propellantCapacity` (devient la borne max). Mettre à jour `VehicleStack.resolveActiveStage` pour utiliser `propellantLoad`. Corriger test incohérent. | Tests vehicle passent, missions actuelles continuent de tourner avec `load = capacity`. |
| L2 | **Modèle Falcon Heavy** | Remplacer les valeurs en dur par les chiffres FH agrégés. Factory `Launchers.FALCON_HEAVY`. Mission charge un lanceur, pas une factory globale. | Test optim LEO 400 km passe avec FH ; valeurs cohérentes avec sources publiques. |
| L3 | **Charge propergol différenciée LEO / GEO** | `LEOMission` et `GEOMission` fournissent des `propellantLoad` initiaux différents (calcul heuristique via Tsiolkovsky inverse). | LEO 400 km consomme < 50 % du propergol max FH étage 2 ; GEO consomme près de 100 %. |
| L4 | **Câblage MassDepletionDetector** | Le `GravityTurnStage` utilise le détecteur de masse pour signaler MECO. Stages de transfert ont une variante « burn until depletion ». | Test : un stage avec `propellantLoad` insuffisant s'éteint proprement avant la date prévue. |
| L5 | **Refonte profil GEO réaliste** | Split lanceur / spacecraft AKM. Étage 2 fait GTO puis se sépare. Spacecraft (avec propergol AKM) fait la circularisation à l'apogée. Vertical ascent devient param du lanceur, coast inter-étage ajouté. | Test GEO actuel adapté ; vérification que la masse finale en GEO ≈ dry spacecraft. |
| L6 | **Optim trajectoire avec épuisement** | Adapter les `TrajectoryProblem` existants pour autoriser un mode « la durée est fixée par l'épuisement, pas par t1/dt1 ». Seed = résultat actuel. | Convergence sur LEO 400 km avec qualité équivalente (±7 % comme aujourd'hui). |
| L7 | **Optim externe propergol** | Nouvelle boucle CMA-ES externe sur `propellantLoad` par étage, appelant la boucle L6 en interne. Termes de coût et de pénalité d'infaisabilité. | Sur LEO 400 km, la charge propergol optimale est < charge actuelle ; mission réussit avec moins de propergol. |
| L8 | **(Futur) Ariane 5** | Modèle EAP (solides à épuisement, non éteignables) + EPC (Vulcain LH2/LOX) + ESC-A. Le `VehicleStack` gère le jettison des side boosters à mi-burn étage 1. Étages solides ont `propellantLoad = propellantCapacity` figé dans la boucle L7. | Test optim GTO Ariane 5 réussit. |
| L9 | **(Optionnel)** | Profil de pitch plus riche (spline), exposition de `payload mass` paramétrable, métriques de performance (ΔV total, pertes finite-burn, propergol résiduel). | Diagnostic complet exposé dans les résultats d'optim. |

---

## 6. Vérification

Pour valider chaque lot, end-to-end :

1. **Tests automatisés** : étendre `LEOMissionOptimizationTest` et `GEOMissionOptimizationTest` (sous `src/test/.../optimizer/`) avec des sweeps qui vérifient :
   - L1 : `load = capacity` reproduit le comportement actuel (régression).
   - L2 : avec valeurs FH, l'orbite cible LEO 400 km est atteignable.
   - L3 : `propellantLoad` LEO ≪ `propellantLoad` GEO.
   - L4 : le `MassDepletionDetector` se déclenche au bon instant ; un stage sous-doté s'éteint.
   - L5 : la masse finale en GEO correspond au spacecraft seul (lanceur largué).
   - L6 : qualité d'orbite préservée avec arrêt par épuisement.
   - L7 : `propellantLoad` optimisé est strictement inférieur à la capacité ; mission réussit.
2. **Lancement manuel** : `./gradlew test` (rapide) + lancement visuel via `OrbitLabApplication` pour observer la trajectoire et confirmer que le visuel reste cohérent (étage qui largue au bon moment, spacecraft seul en GEO).
3. **Métriques quantitatives à exposer** dans les résultats d'optim (utile pour ce doc et pour les futurs) :
   - ΔV total, ΔV par stage.
   - Propergol consommé / embarqué / résiduel.
   - Pertes gravitationnelles (différence ΔV idéal Hohmann vs ΔV finite-thrust).
   - Comparaison avec valeurs publiques de mission FH (charge utile vs charge embarquée).

---

## 7. Questions ouvertes (à trancher en cours d'implémentation)

- **Granularité du modèle FH** : étage 1 agrégé ou étage 0 (boosters latéraux) + étage 1 (core) avec jettison séparé ? Recommandation : agrégé en L2, séparé en L8 si besoin.
- **Tolérances d'orbite cible** : actuellement ±7 % d'altitude. À conserver ? À durcir ? Cela influe sur la borne min de `propellantLoad` que la L7 peut atteindre.
- **Propergol résiduel autorisé** : doit-on imposer un minimum de propergol non utilisé (marge de sécurité opérationnelle, typiquement 1–2 % en aérospatial) ? À mettre en contrainte de L7.
- **Plusieurs spacecraft différents** : sat GEO lourd vs sat GEO léger n'ont pas le même AKM. Faut-il déjà préparer un catalogue `Spacecrafts.*` ? À voir en L5.
