# Bilan I1–I6 — état mesuré et points à traiter avant I7

> Périmètre : ce document est un **bilan d'étape**. Il synthétise ce que les incréments I1 à I6
> de [06-launcher-driven-profile-2.md](06-launcher-driven-profile-2.md) ont livré, les mesures de
> référence obtenues, les décisions de calibration actées en cours de route, et la liste des
> points à trancher ou corriger avant d'attaquer **I7 (boucle externe de carburant)**.

---

## 1. Ce qui est livré, incrément par incrément

| # | Livré | Briques clés |
|---|---|---|
| I1 | **Le lanceur génère le profil** : catalogue typé (`Launchers.FALCON_HEAVY`, capacités physiques par étage, `AscentProfile` 7 s / 3° / 2 s), missions dérivées de `LaunchConfiguration`, ISP S1 calibré 296 s, MECO sur masse réelle | [Launchers](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/Launchers.java), `model/`, `model/stage/`, [GravityTurnManeuver](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/GravityTurnManeuver.java) |
| I2 | **La charge utile est une entrée** : catalogue `Payloads` (cargo 15 t, obs 10 t, GEO sat 2 t + AKM), masse saisie réellement embarquée, `MissionFactory` testable, wizard piloté par les catalogues, `Spacecraft.LEGACY` | [Payloads](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/Payloads.java), [MissionFactory](../../src/main/java/com/smousseur/orbitlab/simulation/mission/operation/MissionFactory.java) |
| I3 | **Carburant juste nécessaire** : Tsiolkovsky inverse descendant (S1 plein en v1, S2/AKM dimensionnés, marge 10 %), `MissionPerformanceReport` par stage (instrument de calibration) | [PropellantBudget](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/PropellantBudget.java), [MissionPerformanceReport](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionPerformanceReport.java) |
| I4a | **Garde-fou d'épuisement** : `depletionFloor()`, `DepletionGuard` bruyant (replay) / silencieux (optimisation), armé sur tous les burns | [DepletionGuard](../../src/main/java/com/smousseur/orbitlab/simulation/mission/detector/DepletionGuard.java) |
| I4b | **Flame-out réel** : burn 1 du GT éteint par la masse (`DepletionStopTrigger`, g inversé — les triggers Orekit coupent sur g croissant), coast inter-étage actif, durées analytiques plafonnées (`computeBurnDurationCapped`) | [DepletionStopTrigger](../../src/main/java/com/smousseur/orbitlab/simulation/mission/detector/DepletionStopTrigger.java) |
| I5 | **Profil GEO réaliste** : injection GTO (`AnalyticGtoInjectionStage`), séparation explicite (`StageSeparationStage`, masse exacte), circularisation AKM centrée apogée avec compensation de sa propre dérive par simulation (`AnalyticApogeeCircularizationStage`), budget S2 sans le poste circularisation | [GEOMission](../../src/main/java/com/smousseur/orbitlab/simulation/mission/operation/GEOMission.java) |
| I6 | **Transfert optimisé branché** : `TransfertTwoManeuverStage` recréé, borne dt1 = épuisement exact (×0,90 supprimé), gardes silencieux dans les manœuvres de transfert, variante opt-in `LEOMission.withOptimizedTransfer` | [TransfertTwoManeuverStage](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/TransfertTwoManeuverStage.java) |

**Transverse optimiseur** : détection de plateau (raffinements + retries), **arrêt par consensus**
(≥ 2 explorations *descendues* vers le même coût ⇒ conclusion immédiate ; le critère de descente
protège le retry d'évasion), seed déterministe, candidats infaisables tronqués physiquement.

---

## 2. Mesures de référence (seed 42, FH budget, LEO 400 km, sat 10 t)

### 2.1 Transfert analytique vs optimisé (résultat I6)

| | Analytique (Hohmann + trim) | **Transfert optimisé (CMA-ES)** |
|---|---|---|
| Coast final | 396,9 – 413,9 km | **394,2 – 406,6 km** |
| Inclinaison (cible 45,96°) | 45,82° | **45,963°** (erreur 0,003°) |
| Excentricité | 9,7e-4 | **4,4e-4** |
| ΔV transfert + trim | ~420 m/s | **~229 m/s (−46 %)** |
| Trim résiduel | 19–30 m/s | **2,4 m/s** |
| Coût optim transfert | — (analytique) | 4 576 évals, convergé en exploration |

L'optimiseur trouve un burn hors de portée de l'analytique : coast au-delà de l'apogée
(t1 ≈ 2 057 s), angle in-plane marqué (α1 ≈ −0,72 rad). C'est le dividende attendu de E3/L6.

### 2.2 GEO (profil splitté I5)

- Masse finale en GEO ≈ 2,4–2,5 t (payload + résiduel AKM) — l'étage 2 sépare bien.
- Apogée tenue à ±5 km par le Newton du plan de circularisation (seuil relâché exprès :
  la sécante sur β a une pente ~10× plus faible que l'estimation impulsionnelle).
- **Plancher de plan ~0,25°** : la rotation de plan étalée sur ~3 h de burn laisse un résidu
  vectoriel incorrigible hors nœud (démontré : 1,57° de visée pour 0,03° d'effet). Tolérance
  test actée à 0,30° (historique : 0,10° → 0,15° → 0,30° au fil des changements d'architecture).
  → **MàJ (§3.5 fait)** : trim de plan au nœud (`AnalyticPlaneTrimAtNodeStage`) → inclinaison
  finale ~3e-5° sur la mission de référence, tolérance test resserrée à 0,05°.

### 2.3 Calibrations actées

| Constante | Valeur | Dérivation |
|---|---|---|
| `ASCENT_LOSSES_MS` | 1 260 m/s | point fixe sur 2 mesures du rapport (1 600 → 37,9 % de résiduel S2, 1 400 → 26,2 %, pente conso/charge ≈ 0,31) ; pertes réelles mesurées ≈ 1 100 |
| `SAFETY_MARGIN` budget | 10 % | marge résiduelle S2 ≈ 12 % sur le point de référence |
| `acceptableCost` GT | ~0,048 *(MàJ)* | relevé à `W_FPA_SOFT·(2,5°)²` au-dessus du plancher (§3.6 étape 1) |
| Tolérance inclinaison GTO | 0,05° *(MàJ)* | ~3e-5° atteint via le trim de plan au nœud (§3.5) |
| maxStep propagateurs optim/simple | dynamique *(MàJ)* | `burnLimitedMaxStep`, plafonné à 30 s (§3.1) |

---

## 3. Points à traiter avant / pendant I7

> **MàJ post-bilan :** la plupart de ces points sont désormais résolus ou tranchés — voir §5
> (ci-dessous) pour le récapitulatif de résolution.

### 3.1 ✅ Réglé mais à connaître — l'invariant maxStep (crash « late ignition »)

Un burn qui s'allume longtemps après le début d'une propagation fait redémarrer l'intégrateur
avec le pas hérité du coast ; si ce pas d'essai peut rendre la masse négative, **Orekit jette
l'exception pendant l'évaluation d'essai**, avant le contrôle de pas et avant tout détecteur
(coupure, garde d'épuisement — aucun correctif événementiel ne peut agir). Invariant :
`maxStep < masse(allumage) / débit` pour le burn le plus violent pouvant s'allumer en cours de
propagation (S2 : ~16 t à 287 kg/s → 56 s). D'où **maxStep = 30 s** sur
`createOptimizationPropagator` et `createSimplePropagator`. Régression verrouillée par
[LateIgnitionReproTest](../../src/test/java/com/smousseur/orbitlab/simulation/mission/maneuver/LateIgnitionReproTest.java).
Reste ouvert : `createDefaultPropagator` (maxStep 300) — hors pipeline mission aujourd'hui, à
borner si des burns y passent un jour.

### 3.2 Décision : profil LEO par défaut, analytique ou optimisé ?

Les chiffres du §2.1 plaident pour la bascule (précision ×2, ΔV −46 %) ; le coût est le temps
d'optimisation (~1,5 min par mission vs quasi-instantané en analytique) sur le sweep et au
runtime. Recommandation : décision séparée, après un sweep complet en mode optimisé sur
plusieurs altitudes ; en attendant, `withOptimizedTransfer` reste opt-in. Noter que **I7
bénéficierait de la version optimisée comme boucle interne** (charges plus serrées atteignables),
au prix d'une bisection plus lente.

### 3.3 Seed Hohmann du TransferProblem hors bornes en hand-off sub-orbital

`guessT1 = 4 186 s > t1Max = 2 599 s` : la branche « raising ⇒ départ au périapse » n'a pas de
sens quand le périgée est à 30 km (le coast vers le périapse s'écrase). CMA-ES l'a surmonté,
mais le seed devrait viser l'apoapse quand le périgée est sous un plancher sûr — convergence
plus rapide et warm-start I7 plus fiable. Correctif ~10 lignes dans
[TransferProblem](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/TransferProblem.java).

### 3.4 Formule de faisabilité `dv2Hohmann` fausse

`vCircAtTarget − vTransferAtDeparture` compare des vitesses à des rayons différents (log
`dv2≈−494` sur le cas I6). Inoffensif (`max(0, ·)` l'écrase) mais faux : le check de
faisabilité sous-estime le ΔV requis. À corriger avec §3.3.

### 3.5 Précision de plan GEO : trim au nœud (candidat « I5b »)

Si les 0,25° résiduels comptent avant I7 : un petit stage « plane trim at node »
(`CoastingStage(stopAtNode)` existe + burn hors-plan court au nœud) permettrait de revenir vers
0,10–0,15°. Sinon, l'alternative long terme est le multi-burn d'apogée.

### 3.6 Plancher de coût du gravity turn vs `acceptableCost`

Le coût GT plancher (~0,034, entièrement le terme `W_FPA_SOFT·fpa²` à fpa ≈ 2,1°) reste au-dessus
de l'acceptable (1e-3) : chaque optimisation GT logge un WARN et ne s'arrête que par consensus.
Options : relever l'acceptable GT (~0,05), recalibrer le kick FH (les paramètres de profil sont
des données du lanceur depuis I1), ou repenser `W_FPA_SOFT` maintenant que le transfert optimisé
absorbe très bien un FPA de hand-off non nul (cf. §2.1). À trancher avant I7 : la bisection
appellera l'optim GT à chaque évaluation, autant qu'elle conclue vite et sans bruit.

### 3.7 Hygiène (non bloquant)

- Race bénigne : `GravityTurnManeuver.lastAltitudeTracker` partagé entre explorations
  parallèles (le stub de test équivalent a été corrigé en `ThreadLocal` ; le pattern
  `lastResult` des problèmes de transfert est déjà thread-local).
- CLAUDE.md et javadocs de mission périmés par la refonte (profils, liste des stages).
- Le rapport de performance approxime le ΔV des stages multi-Isp (GT) — documenté, suffisant
  pour la calibration.

---

## 4. Prérequis I7 — état

| Prérequis (spec 06 §S5/I7) | État |
|---|---|
| Bornes dt1 = épuisement dur (pas de recalcul de fenêtres quand la charge varie) | ✅ I6 |
| MECO par flame-out (burn 1 GT) + plafonnement analytique | ✅ I4b |
| Seed analytique des charges (`PropellantBudget`, calibré) | ✅ I3 |
| Échec propre d'une mission sous-dotée (garde + cap ⇒ objectif raté, pas d'exception) | ✅ I4 |
| Warm-start CMA-ES (`seed`/`initialGuess` existants) | ✅ |
| Boucle interne rapide sur les cas simples (consensus) | ✅ |
| Rapport de performance pour le critère « résiduel ≥ 1 % » | ✅ I3 |

Rappel du design I7 retenu (spec 06 §S5) : `PropellantLoadOptimizer` par **bisection scalaire
sur λ** appliqué aux charges heuristiques (`load_i = λ·load_i^heuristique`, clampé), λ ∈ [0,3 ; 1],
tolérance 2 %, ≤ 10 évaluations externes, étages `SOLID` et AKM hors λ, succès ⇔ objectif atteint
**et** résiduel ≥ 1 % par étage liquide. Bascule CMA-ES externe (v2) seulement si le résiduel
post-bisection dépasse 5 % de la capacité sur un étage.

---

## 5. État de résolution (post-bilan)

Mises à jour depuis l'instantané ci-dessus. Les prérequis durs de la section 4 restaient tous ✅ —
**I7 peut démarrer.**

| Point | État | Résolution |
|---|---|---|
| §3.1 maxStep | ✅ fait | invariant rendu dynamique : `OrekitService.burnLimitedMaxStep(BurnSpec…)` + `MissionStage.maxStepSeconds`. Coasts burn-free au `COAST_MAX_STEP` ; burns bornés par leur masse d'allumage réelle, plafonné à `SAFE_MAX_STEP` (30 s) → stepping Falcon Heavy inchangé, resserrement auto sur charge légère I7. **Reste** : appliquer aux stages analytiques (en cours). |
| §3.2 défaut LEO / boucle I7 | ✅ tranché | I7 tourne sa boucle interne sur le **transfert optimisé** (`TransfertTwoManeuver`) — objectif « simu au plus proche du réel », temps de calcul assumé (progressbar utilisateur). |
| §3.3 seed Hohmann | ✅ fait (`a8fb39e`) | départ à l'apoapse quand le périgée n'est pas volable + seed borné à `t1Max` ; testé (`TransferProblemSeedTest`). Hérité par le transfert optimisé. |
| §3.4 faisabilité `dv2Hohmann` | ✅ fait (`a8fb39e`) | les deux vitesses prises à `rTarget`. |
| §3.5 plan GEO | ✅ fait | `AnalyticPlaneTrimAtNodeStage` (burn hors-plan court au nœud) → inclinaison finale ~3e-5° (contre ~0,25°), tolérance test 0,05°. |
| §3.6 plancher coût GT | ✅ fait (étape 1) | terme `W_FPA_SOFT` symétrique conservé, `acceptableCost` relevé à ~0,048. L'étape 2 (asymétrie) a été implémentée puis **revertée** : elle dégradait la circularité finale ~6× (ecc 1,4e-4 → 8,5e-4) même sur le transfert optimisé. |
| §3.7 race tracker | ✅ fait | `GravityTurnManeuver.lastAltitudeTracker` → `ThreadLocal`. |
| §3.7 docs / rapport perf | ⬜ en cours | CLAUDE.md + javadocs à jour ; l'approx ΔV multi-Isp du rapport reste documentée (suffisante). |
