# I7 — Préparation technique (boucle externe de dimensionnement propergol)

> Ce document consolide **tout ce qui a été trouvé, décidé ou préparé** pendant le suivi du
> [bilan I1–I6](08-bilan-i1-i6.md) en vue d'attaquer I7 sur de bonnes bases. Il ne remplace pas le
> design d'origine ([spec 06 §S5 / I7](06-launcher-driven-profile-2.md)) — il le complète avec les
> décisions actées, la tâche technique bloquante (maxStep des stages analytiques) et les points
> d'attention découverts.

---

## 1. Objectif d'I7

Fermer la boucle « le lanceur dimensionne le propergol » : au lieu de voler les charges
heuristiques de `PropellantBudget` (marge 10 %), **rechercher le chargement minimal qui atteint
encore l'objectif**. But produit : une simulation au plus proche du réel, temps de calcul assumé.

**Design retenu (inchangé, spec 06 §S5)** : `runtime/PropellantLoadOptimizer` par **bisection
scalaire sur λ** appliquée aux loads heuristiques (`load_i = λ · load_i^heuristique`, clampé).

| Paramètre | Valeur |
|---|---|
| Bornes | `λ ∈ [0,3 ; 1,0]` |
| Tolérance | 2 % |
| Budget | ≤ 10 évaluations externes |
| Chaque évaluation | `MissionOptimizer.optimize()` complet sur une mission reconstruite avec `loads(λ)` |
| Hors λ | étages `SOLID` et AKM (l'AKM garde son dimensionnement analytique, déjà serré) |
| Succès | objectif dans tolérance **et** résiduel ≥ 1 % par étage liquide |
| Warm-start | `bestVariables` de l'évaluation précédente réinjectés dans le CMA-ES interne |
| Bascule v2 | CMA-ES externe seulement si le résiduel post-bisection dépasse 5 % de la capacité sur un étage |

---

## 2. Décision actée — boucle interne = transfert optimisé (§3.2)

I7 tourne sa boucle interne sur le **transfert optimisé** (`TransfertTwoManeuver` / spec 06 I6),
pas sur le transfert analytique. Motif : objectif réalisme, précision ×2 et ΔV −46 % (bilan §2.1),
et charges plus serrées atteignables.

**Conséquences :**
- **Coût CPU** : chaque évaluation de bisection = une optimisation LEO complète (~1,5 min) →
  jusqu'à ~15 min par mission I7. → l'utilisateur est prévenu (**progressbar / feedback UI**),
  et le test I7 est marqué **lent / nightly**.
- Le **warm-start** compte double : la bisection rappelle l'optim ≤ 10 fois → un seed fiable
  évite des évaluations qui repartent de loin (voir §5, warm-start).

---

## 3. Prérequis — état

Les prérequis durs (spec 06 §S5 / bilan §4) sont **tous ✅** : bornes dt1 = épuisement dur,
MECO flame-out, seed analytique des charges, échec propre d'une mission sous-dotée, warm-start
CMA-ES, boucle interne rapide (consensus), rapport de performance. Les points §3 du bilan sont
tous résolus ou tranchés — voir [bilan §5](08-bilan-i1-i6.md). **I7 peut démarrer.**

Reste une seule dette technique à solder d'abord : la **tâche 0** ci-dessous.

---

## 4. Tâche 0 (bloquant technique) — maxStep dynamique des stages analytiques

### 4.1 Pourquoi c'est bloquant pour I7

I7 fait **varier les charges**. L'invariant « late ignition » (bilan §3.1) dit qu'un burn qui
s'allume après un coast fait redémarrer l'intégrateur avec le pas hérité du coast ; si ce pas
d'essai peut rendre la masse négative, **Orekit jette l'exception pendant l'évaluation d'essai**,
avant le contrôle de pas et avant tout détecteur (coupure, garde d'épuisement). L'invariant :

```
maxStep < masse(allumage) / débit    (débit = poussée / (Isp · g0))
```

Le 30 s codé en dur est calibré pour la S2 de Falcon Heavy (~16 t → 56 s). **Mais une charge
légère I7 abaisse la masse d'allumage** : si elle descend sous ~`30 s × débit` (≈ 8,6 t pour la
S2), alors `burnToZero < 30 s` et **le 30 s rouvre le crash**. C'est un scénario que la boucle I7
va explorer par construction.

### 4.2 Outillage déjà en place (à réutiliser)

Fait pendant le suivi du bilan, dans
[OrekitService](../../src/main/java/com/smousseur/orbitlab/simulation/OrekitService.java) :

- surcharges `createSimplePropagator(double maxStep)` / `createOptimizationPropagator(double maxStep)` ;
- `record BurnSpec(thrust, isp, massAtIgnition)` + `static double burnLimitedMaxStep(BurnSpec…)` ;
- constantes `COAST_MAX_STEP` (300 s, propagations sans burn) et `SAFE_MAX_STEP` (30 s) ;
- `MissionStage.maxStepSeconds(state, mission)` (défaut : `isPropulsive() ? SAFE : COAST`), que le
  générateur d'éphéméride interroge par stage.

**Sémantique « Version B »** de `burnLimitedMaxStep` : plafonné à `SAFE_MAX_STEP`, il **ne
resserre en-dessous de 30 s que si un burn est trop violent** (masse d'allumage <
~`SAFE_MAX_STEP × 1,5 × débit`). Corollaire capital : **Falcon Heavy reste à 30 s partout** →
le câblage est **FH-neutre**, les sweeps LEO/GEO doivent rester **identiques**.

Déjà câblés : **gravity turn** et **transfert optimisé** (optim + replay éphéméride).

### 4.3 Ce qui reste — les stages analytiques

Non câblés, encore au 30 s statique. Concernés (chemin **GEO** + **LEO analytique par défaut**) :

- `AnalyticGtoInjectionStage`, `AnalyticParkingInsertionStage` — burns S2 (à risque sur charge légère) ;
- `AnalyticHohmannTransferStage` — burns S2 (LEO analytique) ;
- `AnalyticApogeeCircularizationStage`, `AnalyticTrimBurnStage`, `AnalyticPlaneTrimAtNodeStage` —
  burns AKM (doux → resteront à 30 s, mais à câbler pour l'uniformité et la robustesse).

**Le périmètre est plus large qu'il n'y paraît** : chaque stage héberge un burn dans **plusieurs
propagateurs**, tous en no-arg (30 s) aujourd'hui :

1. son `propagateStandalone` (avancement d'état par l'optimiseur) ;
2. ses **propagateurs de plan** Newton/sécante :
   [`AnalyticApogeeCircularizationStage.simulateCenteredBurn`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/AnalyticApogeeCircularizationStage.java),
   [`AnalyticHohmannTransferStage.simulateBurn1AndFindApogee`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/AnalyticHohmannTransferStage.java) ;
3. son propagateur d'éphéméride, déjà couvert dès que `maxStepSeconds` est surchargé.

→ ~15–20 edits sur 6 stages, **tous FH-neutres**.

### 4.4 Comment appliquer

Pour chaque stage analytique :

```java
@Override
public double maxStepSeconds(SpacecraftState entryState, Mission mission) {
  ActiveStageInfo stage = mission.getVehicle().resolveActiveStage(entryState.getMass());
  PropulsionSystem p = stage.propulsion();
  return OrekitService.burnLimitedMaxStep(
      new OrekitService.BurnSpec(p.thrust(), p.isp(), stage.depletionFloor()));
}
```

- **Masse d'allumage worst-case = `depletionFloor`** du stage actif (la plus petite masse à
  laquelle un burn peut s'allumer, borne l'invariant pour tous ses burns).
- Passer la **même valeur** aux `create*Propagator(...)` des propagateurs standalone **et** de
  plan (points 1–2 ci-dessus), pas seulement à `maxStepSeconds`. Sinon l'optim/plan crashe sur
  charge légère alors que l'éphéméride est protégée.
- Envisager un helper `protected static` dans `MissionStage` pour ne pas dupliquer le bloc (couple
  `MissionStage` à `vehicle`, acceptable).

### 4.5 Validation

- **FH-neutre** : `LEOMissionOptimizationTest`, `LEOMissionOptimizedTransferTest`,
  `GEOMissionOptimizationTest` doivent rester **identiques** (Version B plafonne à 30 s). Toute
  divergence = à investiguer.
- **Idéalement**, valider aussi sur un **cas de charge légère** (une entrée `Payloads` légère ou
  un `LaunchConfiguration` sous-dimensionné) : c'est le seul cas qui *exerce* réellement le
  resserrement — sinon on valide un no-op. **C'est pourquoi cette tâche gagne à être faite en
  ouverture d'I7**, où les charges variables la testent naturellement.

---

## 5. Points d'attention pour I7

**Warm-start fiable (§3.3 / §3.4, déjà faits en `a8fb39e`).** Le seed Hohmann du `TransferProblem`
part désormais de l'apoapse quand le périgée n'est pas volable (hand-off sub-orbital) et reste
borné à `t1Max` ; la formule de faisabilité `dv2` est corrigée. Hérité par le transfert optimisé.
Important pour I7 : la bisection martèle l'optim → un seed hors bornes rallongerait chaque éval.

**Invariant maxStep — règle générale.** Ne **jamais** passer un grand maxStep brut à un propagateur
qui hébergera un burn. Utiliser `burnLimitedMaxStep(...)` (voir CLAUDE.md § OrekitService).

**Limite perf connue — coast borné par un burn.** Dans le transfert, le long coast (~2 500 s
avant burn 2) partage le propagateur du burn → il reste borné à 30 s (l'invariant s'applique tant
qu'un burn peut s'allumer plus loin). On **ne peut pas** l'accélérer sans *splitter* la
propagation coast/burn — chantier séparé, à considérer seulement si les ≤ 10 évals × ~1,5 min
d'I7 s'avèrent trop lentes en pratique.

**Ne PAS refaire l'asymétrie FPA (§3.6).** Rendre `W_FPA_SOFT` asymétrique (ne pénaliser que le
FPA descendant) a été implémenté puis **reverté** : ça dégrade la circularité finale ~6×
(ecc 1,4e-4 → 8,5e-4) même sur le transfert optimisé, car le tir vers un hand-off à plat sert la
précision. Le terme symétrique + `acceptableCost` relevé (~0,048) est le compromis retenu. (Déjà
consigné dans le code de `GravityTurnProblem`.)

**Contrainte résiduel ≥ 1 %.** Le prédicat de succès de la bisection est « objectif atteint **et**
résiduel ≥ 1 % de la charge par étage liquide » — trivial à ajouter, s'appuie sur
`MissionPerformanceReport.totalPropellantResidual()` (déjà par-étage).

**Durcissement des tolérances après I7.** `ORBIT_MARGIN_RATIO` (±7 %) conditionne le λ* atteignable
— à ne resserrer qu'après I7.

**Précision GEO acquise (§3.5).** Le trim de plan au nœud (`AnalyticPlaneTrimAtNodeStage`) amène
l'inclinaison finale à ~3e-5° (tolérance test 0,05°) : la boucle I7 GEO ne doit pas régresser
là-dessus.

---

## 6. Plan d'implémentation proposé

| Ordre | Tâche | Validation |
|---|---|---|
| **0** | maxStep dynamique des stages analytiques (§4) | sweep LEO + GEO **identiques** ; idéalement 1 cas charge légère |
| 1 | `runtime/PropellantLoadOptimizer` : bisection sur λ, bornes `[0,3 ; 1]`, tol 2 %, ≤ 10 évals, warm-start | test unitaire de la bisection (monotonie, budget) |
| 2 | Reconstruction de mission avec `loads(λ)` (SOLID/AKM hors λ) + prédicat succès (objectif **et** résiduel ≥ 1 %) | — |
| 3 | Feedback UI (progressbar) sur la boucle externe | — |
| 4 | Test d'intégration I7 (LEO 400 km : `Σ load(λ*) < Σ load_heuristique`, mission réussie), **marqué lent/nightly** | critères L7 |

**Critère de sortie I7** : sur LEO 400 km, `Σ load(λ*) < Σ load_heuristique` et mission réussie.
Si résidu > 5 % sur un étage → ouvrir la v2 CMA-ES externe (spec courte dédiée).

---

## 7. Risques

| Risque | Impact | Mitigation |
|---|---|---|
| Coût CPU (chaque éval = optim complète ~1,5 min) | temps de test | bisection ≤ 10 évals, warm-start, test lent/nightly, progressbar UI |
| Crash « late ignition » sur charge légère | exception avant tout détecteur | **tâche 0** (maxStep dynamique) faite en premier |
| Charges initiales trop justes (heuristique) → échecs internes | bisection qui ne trouve pas de λ faisable | marge 10 % absorbe ; sinon recalibrer via `MissionPerformanceReport` |
| Régression FH silencieuse par la tâche 0 | sweep faussé | Version B plafonne à 30 s → sweeps identiques ; toute divergence investiguée |
