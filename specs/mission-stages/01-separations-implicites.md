# Expliciter les séparations implicites en phases de mission

> **Statut** : spécification technique avec cible recommandée.
> **Contrainte cadre** : non-régression numérique **dure** — le découpage
> doit être un refactor à trajectoire constante, pas une ré-optimisation.
> **Date** : 2026-08-03.

## 0. Résumé exécutif

Une séparation d'étage est aujourd'hui un **effet de bord d'un détecteur planté
à l'intérieur d'un stage**, et non une phase de mission. Le cas central est la
`GravityTurnStage`, qui encapsule *burn S1 + largage S1 + coast inter-étage +
burn S2* derrière un seul nom (`"Gravity turn"`) et une seule ligne de rapport.

Cette encapsulation a un coût déjà payé : l'invariant « le MECO doit tomber
après le largage » n'est tenu par **aucune structure**, seulement par une
pénalité de coût CMA-ES de 1e3 et un garde-fou levé à l'exécution — parce que
rien n'empêche de terminer la phase avant que le détecteur de largage n'ait tiré
(`GravityTurnProblem.java:59-76`, `GravityTurnStage.java:119-127`). Le mode de
défaillance est documenté et a été observé : 0,4 s de MECO manquant sur un burn
de 150 s a laissé 3,3 t coincées dans S1, fait larguer S1 à la place de S2 par
la séparation GEO suivante, et laissé S2 exécuter les burns du moteur d'apogée
de la charge utile.

GEO a montré la sortie : `StageSeparationStage` est une phase à part entière,
non-propulsive, vérifiable (`expectedStageIndex`), visible dans l'éphéméride, le
rapport et la télémétrie. La cible de ce document est d'appliquer le même
traitement à l'ascension.

**Cible retenue (option A)** : un `AscentPlan` calculé une fois et consommé à
l'identique par la passe d'optimisation et par la passe éphéméride, avec une
liste de stages **plate** :

```
Vertical Ascent → Gravity turn (S1) → S1 separation → Gravity turn (S2) → …
```

Variables et bornes CMA-ES **inchangées**. L'invariant d'étagement devient
structurel : la séparation est une phase, on ne peut plus l'omettre.

---

## 1. Périmètre

**Inclus** : recensement exhaustif des séparations et des transitions d'étage
implicites du modèle de mission ; conception détaillée de la cible pour
l'ascension (§4–§6) ; plan de migration et critères de non-régression (§7–§8).

**Exclus** (listés en §9 comme suites) : coiffe, boosters, séparation charge
utile en LEO, décomposition des stages analytiques à deux burns,
reparamétrisation des variables CMA-ES, pitch kick explicite.

**Vocabulaire.** On distingue trois familles, souvent confondues :

| Famille | Définition | Exemple |
|---|---|---|
| **Séparation implicite** | Une chute de masse par largage se produit **à l'intérieur** d'un stage, sans phase dédiée. | Le largage S1 dans `GravityTurnManeuver` |
| **Séparation fantôme** | Le code résout un « étage suivant » à un endroit où aucun largage ne peut avoir eu lieu — l'étagement est *supposé* mais jamais réalisé. | `stage2` des stages analytiques à 2 burns |
| **Séparation absente** | Un largage physiquement réel n'est pas modélisé du tout. | Coiffe, séparation charge utile en LEO |

---

## 2. Recensement

### 2.1 Vue d'ensemble

| # | Famille | Localisation | Effet observable | Gravité |
|---|---|---|---|---|
| **S1** | Séparation implicite | `GravityTurnManeuver.configure` — `DateDetector` + `RESET_STATE` (`GravityTurnManeuver.java:150-170`) | Largage S1 invisible : ni phase, ni ligne de rapport, ni point d'éphéméride, ni changement de libellé télémétrie | **Haute** |
| **S2** | Comptabilité faussée par S1 | `MissionOptimizer.buildStagePerformance` (`MissionOptimizer.java:253-271`) | Le ΔV des deux burns est crédité au seul Isp de S1 (296 s au lieu de 348 s pour le burn 2) | **Moyenne** |
| **S3** | Comptabilité faussée par S1 | `MissionOptimizer.captureJettisonedResidual` (`MissionOptimizer.java:278-291`) | Le résidu de propergol largué avec S1 n'est **jamais** capturé : il n'est capturé que pour un `StageSeparationStage` | **Moyenne** |
| **S4** | Séparation fantôme | `AnalyticParkingInsertionStage.java:182,232`, `AnalyticHohmannTransferStage.java:230,394` | Un `stage2` est résolu depuis `massAfterBurn1` alors que la résolution ne peut structurellement pas changer d'étage sans largage | **Basse** (mort-né, mais trompeur) |
| **S5** | Séparation absente | LEO : `LEOMission.buildStages` (`LEOMission.java:230-243`) | La charge utile n'est **jamais** séparée : transfert, trim et coast final sont volés avec l'étage supérieur vide encore attaché | **Moyenne** |
| **S6** | Séparation absente | Catalogue : `Launchers.java:19-50` | Pas de coiffe, pas de boosters — `StageRole.BOOSTER` et `StageRole.KICK` n'apparaissent dans aucun code de production | **Basse** |
| **S7** | Phase implicite (adjacent) | Tous les `Analytic*Stage`, `TransfertTwoManeuverStage` | Un stage = burn 1 + coast + burn 2 ; le coast (jusqu'à ~5 h en GEO) n'a ni nom ni frontière | **Basse** |
| **S8** | Événement implicite (adjacent) | `GravityTurnStage.configure` (`GravityTurnStage.java:103-107`) | Le pitch kick modifie l'état initial du propagateur depuis `configure()`, pas depuis une frontière de phase | **Basse** (piégeux, cf. §9.4) |

### 2.2 S1 — Le largage S1 à l'intérieur du gravity turn

C'est le cas central. `GravityTurnManeuver.configure` monte, sur un **seul**
propagateur, la séquence complète :

```java
// GravityTurnManeuver.java:141-180 (condensé)
propagator.addForceModel(new Maneuver(null,
    new DepletionStopTrigger(kickDate.shiftedBy(1e-3), activeStage.depletionFloor()),
    /* propulsion S1 */));                                  // burn 1, flame-out

AbsoluteDate jettisonDate = kickDate.shiftedBy(1e-3)
    .shiftedBy(params.burn1Duration).shiftedBy(1e-3);
propagator.addEventDetector(new DateDetector(jettisonDate)
    .withHandler(/* RESET_STATE → oldState.withMass(massAfterJettison) */));  // largage S1

propagator.addForceModel(new ConstantThrustManeuver(
    jettisonDate.shiftedBy(interstageCoastDuration).shiftedBy(1e-3),
    params.burn2Duration, /* propulsion S2 */));            // burn 2
```

**Ce que cela coûte, concrètement :**

1. **L'invariant d'étagement n'est pas structurel.** La phase se termine sur un
   `DateDetector(kickDate + transitionTime)`. Si `transitionTime <
   burn1Duration + interstageCoast`, la propagation s'arrête **avant** que le
   détecteur de largage n'ait tiré : burn 1 est tronqué, S1 n'est jamais
   largué, et il reste l'étage actif pour toutes les phases suivantes. Le
   garde-fou est double et *a posteriori* :
   - à l'optimisation, une pénalité de coût (`STAGING_PENALTY_BASE = 1e3`,
     `GravityTurnProblem.java:72`, plus un gradient `W_STAGING_SHORTFALL`) ;
   - à l'exécution, une exception (`GravityTurnStage.java:119-127`).

   Les deux commentaires disent la même chose : *le problème est que rien
   n'empêche cette configuration d'exister*.

2. **La séquence est invisible.** L'éphéméride étiquette chaque point du nom du
   stage (`MissionEphemerisGenerator.java:80-87`) et la télémétrie affiche ce
   nom tel quel (`TelemetryWidget.java:217`). Sur les ~450 s d'ascension,
   l'utilisateur lit `GRAVITY TURN` en continu, alors qu'il se passe quatre
   choses distinctes dont un largage de 66 t.

3. **La comptabilité est approximative** (S2/S3, détaillés ci-dessous).

4. **Le max step est mutualisé.** `GravityTurnStage.maxStepSeconds` renvoie le
   pas dicté par le burn 2 (allumage tardif après le coast inter-étage,
   `GravityTurnManeuver.java:202-207`) et l'applique à toute la phase, burn 1
   compris — alors que burn 1 s'allume immédiatement et n'a aucun risque
   d'allumage tardif. C'est conservateur, donc correct, mais c'est une
   contrainte de la phase 4 imposée à la phase 1 faute de frontière entre
   elles.

**Chiffres de référence (Falcon Heavy, `Launchers.java:19-50`)** — utiles pour
lire les logs pendant la migration : S1 sec 66 000 kg / capacité 1 233 000 kg /
Isp 296 s / poussée 22,8 MN → débit ≈ 7 855 kg/s ; à pleine charge, après les
7 s d'ascension verticale, `burn1Duration` ≈ **150 s**. S2 sec 4 000 kg /
capacité 107 500 kg / Isp 348 s / poussée 981 kN. Coast inter-étage : 2 s
(`AscentProfile(7.0, 3.0, 2.0)`).

### 2.3 S2 — Un seul Isp pour deux étages

```java
// MissionOptimizer.java:253-271 (condensé)
double jettisonedDry = max(0, dryIn - dryOut);
double propellantConsumed = max(0, massIn - massOut - jettisonedDry);
double isp = vehicle.resolveActiveStage(massIn).propulsion().isp();   // ← Isp d'ENTRÉE
deltaV = isp * G0 * log(massIn / (massIn - propellantConsumed));
```

Le javadoc l'assume explicitement (« *ΔV uses the entry stage's Isp, an
approximation for stages spanning a jettison* »). Sur la GT, l'étage d'entrée
est S1 : le propergol du burn 2 est donc crédité à **296 s au lieu de 348 s**,
et les deux burns sont agrégés dans une seule équation de Tsiolkovsky qui
enjambe un largage de 66 t — ce que Tsiolkovsky ne sait pas faire. Le `ΔV total`
du rapport de performance en hérite.

Le correctif ne demande aucune ligne de comptabilité nouvelle : il suffit que
chaque burn soit sa propre phase, et le calcul existant devient exact.

### 2.4 S3 — Le résidu de S1 n'est jamais mesuré

`captureJettisonedResidual` ne se déclenche que sur un `StageSeparationStage`
(`MissionOptimizer.java:280-282`). Le largage S1 n'en est pas un : le résidu
éventuel de S1 est donc perdu. `VehicleStack.resolveStagePropellant` le dit
noir sur blanc (`VehicleStack.java:80-83`) : une fois la masse tombée, le
propergol jeté est indiscernable du propergol brûlé.

En pratique le burn 1 tourne jusqu'à la panne sèche (`DepletionStopTrigger`),
donc le résidu S1 est nul **quand tout va bien** — mais c'est exactement le cas
où la mesure ne sert à rien. Le cas où elle sert est celui où burn 1 est tronqué,
c'est-à-dire précisément le mode de défaillance de S1. Aujourd'hui, ce cas est
muet côté rapport.

### 2.5 S4 — Les séparations fantômes des stages analytiques

Les stages analytiques à deux burns résolvent un `stage2` depuis la masse
prédite après burn 1 :

```java
// AnalyticParkingInsertionStage.java:180-183 (idem AnalyticHohmannTransferStage:213-231)
double massAfterBurn1 = state.getMass() * exp(-dv1 / g0Ve);
ActiveStageInfo stage2 = vehicle.resolveActiveStage(massAfterBurn1);
```

**Ce `stage2` est toujours égal à `stage1`.** `VehicleStack.resolveActiveStage`
retient le plus bas étage tel que `currentMass > massAbove[i]`
(`VehicleStack.java:63-66`) ; or le plancher de déplétion vaut `dryMass_i +
massAbove[i]`, strictement supérieur à `massAbove[i]` dès que l'étage a une
masse sèche non nulle. Une combustion, même menée jusqu'à la panne sèche, ne
peut donc **jamais** faire changer d'étage actif : seul un largage explicite le
peut. Et `computeBurnDurationCapped` borne justement la durée au propergol
restant.

Ce n'est pas un bug — c'est du code défensif qui n'a pas de chemin d'exécution.
Mais il **écrit dans le code une affirmation fausse** : « ici, l'étage peut
changer ». Un lecteur en déduit qu'un étagement intra-stage est prévu et
supporté, ce qui n'est le cas nulle part. À supprimer ou à commenter comme
invariant (§9.3).

### 2.6 S5 — La charge utile n'est jamais séparée en LEO

GEO sépare S2 avant que le moteur d'apogée de la charge utile ne prenne le
relais (`GEOMission.java:157-158`). LEO ne sépare rien (`LEOMission.java:230-243`
et les deux variantes optimisées, `LEOMission.java:122-134` et `:170-183`) : la
séquence est `Vertical Ascent → Gravity turn → Transfert → Trim → Coasting`, et
S2 — vide — reste attaché à la charge utile pendant le transfert, le trim et le
coast final.

Conséquences : la masse propagée sur toute la fin de mission est surestimée de
la masse sèche de S2 (4 000 kg sur FH) ; le trim est calculé et exécuté avec la
propulsion de S2 ; la « charge utile en orbite » affichée n'est pas la charge
utile. C'est une omission de modélisation, pas un bug de structure — mais elle
sera facile à corriger une fois `StageSeparationStage` généralisé, et elle
**changera les trajectoires LEO** : elle est donc explicitement hors de la
phase 1 (cf. §9.2).

### 2.7 S6 / S7 / S8 — Le reste

- **S6 — coiffe et boosters.** Aucune occurrence de « fairing » dans le code.
  `StageRole.BOOSTER` et `StageRole.KICK` (`StageRole.java:4-13`) n'apparaissent
  que dans les tests ; le seul lanceur catalogué agrège ses trois cœurs en un
  étage S1 unique. Rien à corriger tant que le catalogue n'a qu'un lanceur, mais
  à garder en tête : le jour où un 2ᵉ lanceur arrive, la mécanique de séparation
  doit déjà être une phase, sinon chaque nouveau largage rouvrira le chantier.
- **S7 — coasts implicites.** Tous les stages analytiques et
  `TransfertTwoManeuverStage` encapsulent `burn 1 + coast + burn 2` derrière un
  seul nom. Le cas extrême est GEO : le coast de mise à poste vers l'apogée
  GTO (~5 h 15) est *à l'intérieur* de la phase « Circularization ». Même
  problème d'observabilité que S1, sans le problème d'invariant (aucun largage
  n'y est en jeu). Traitement possible plus tard, sur le même patron.
- **S8 — le pitch kick.** Il est appliqué depuis `configure()` et va jusqu'à
  **réécrire l'état initial du propagateur** (`GravityTurnStage.java:103-107`).
  L'endroit naturel — `enter()` — a été essayé et abandonné : le générateur
  d'éphéméride écrase le résultat de `enter()` par l'`entryState` sauvegardé à
  l'optimisation (`MissionEphemerisGenerator.java:60-62`), donc un kick appliqué
  là serait perdu. C'est un piège documenté (bilan 11 §3.9), à ne pas rouvrir
  dans la phase 1.

---

## 3. Le modèle explicite de référence : `StageSeparationStage`

Ce que GEO fait déjà bien, et qui sert de gabarit :

| Propriété | Mise en œuvre |
|---|---|
| Phase à part entière | `extends MissionStage`, dans la liste de stages (`GEOMission.java:157-158`) |
| Chute de masse à l'entrée | `enter()` renvoie `previousState.withMass(info.massAfterJettison())` (`StageSeparationStage.java:95`) |
| Non-propulsive | `isPropulsive() → false` : le rapport ne compte ni propergol ni ΔV (`StageSeparationStage.java:70-73`, `MissionOptimizer.java:254-256`) |
| Vérifiable | `expectedStageIndex` : refuse de larguer le mauvais étage et échoue franchement (`StageSeparationStage.java:78-89`) |
| Coast de stabilisation porté par la phase | `configure()` propage `max(separationCoastDuration, 1e-3)` (`StageSeparationStage.java:99-113`) |
| Observable | nom propre dans l'éphéméride, la télémétrie et le rapport ; résidu capturé (`MissionOptimizer.java:278-291`) |

**Point clé pour la cible** : cette classe porte **déjà** le coast inter-étage.
Le découpage de l'ascension n'a donc pas besoin d'un `CoastingStage`
supplémentaire — `StageSeparationStage("S1 separation", interstageCoast, 0)`
reproduit exactement la séquence largage-puis-coast d'aujourd'hui.

---

## 4. Cible retenue

### 4.1 Comparaison des options

| | **A — Plan partagé, phases plates** | B — Stage composite | C — Phases indépendantes |
|---|---|---|---|
| Structure | `AscentPlan` calculé une fois, 3 phases plates dans la liste | `CompositeStage` portant l'optimisation, contenant des sous-phases | Chaque burn est son propre `OptimizableMissionStage` |
| Invariant d'étagement | structurel | structurel | structurel |
| Variables / bornes CMA-ES | **inchangées** | inchangées | refonte |
| Impact `MissionEphemerisGenerator` | nul (boucle déjà plate) | récursion ou aplatissement à ajouter | nul |
| Impact UI / rapport | 3 lignes au lieu d'une | nouveau concept hiérarchique à rendre | 3 lignes |
| Non-régression | tenable | tenable | exclue |
| Coût | moyen | élevé | élevé |

**Retenu : A.** B n'apporte de valeur que si l'UI a besoin de regrouper les
phases sous un parent — besoin qui n'existe pas aujourd'hui (le panneau
n'affiche même pas encore la liste des stages, cf. roadmap 1.1) — et il
introduirait un second niveau de hiérarchie dans `MissionStage`, l'éphéméride et
le rapport. C est incompatible avec la contrainte de non-régression.

### 4.2 Séquences avant / après

**LEO (mode `FAST`)**

```
avant : Vertical Ascent → Gravity turn ────────────────────→ Transfert → Trim → Coasting
                          [burn S1 | largage | coast | burn S2]

après : Vertical Ascent → Gravity turn (S1) → S1 separation → Gravity turn (S2) → Transfert → Trim → Coasting
                          [burn S1]           [largage+coast]  [burn S2]
```

**GEO** — même substitution en tête, le reste inchangé :

```
après : Vertical Ascent → Gravity turn (S1) → S1 separation → Gravity turn (S2)
        → Parking → Coasting parking → GTO injection → S2 separation
        → Circularization → Trim → Plane trim → Coasting
```

GEO gagne au passage une symétrie lisible : `S1 separation` et `S2 separation`
sont désormais deux instances de la même classe, avec `expectedStageIndex` 0 et
1 respectivement.

### 4.3 Correspondance exacte des dates

Le découpage doit reproduire le calage actuel **à la milliseconde près**. Table
de correspondance, avec `t0 = kickDate` :

| Événement | Aujourd'hui | Après découpage |
|---|---|---|
| Allumage burn 1 | `t0 + 1e-3` (déclencheur `DepletionStopTrigger`) | idem, dans `Gravity turn (S1)` |
| Fin de burn 1 | panne sèche, ou `jettisonDate` si plus tôt | fin de phase à `t0 + 1e-3 + burn1Duration + 1e-3` |
| Largage S1 | `DateDetector(jettisonDate)`, `RESET_STATE` | `StageSeparationStage.enter()` à la même date |
| Coast inter-étage | implicite entre `jettisonDate` et l'allumage 2 | `configure()` de la phase de séparation, durée `interstageCoastDuration` |
| Allumage burn 2 | `jettisonDate + interstageCoast + 1e-3` | idem, début de `Gravity turn (S2)` |
| MECO | `DateDetector(t0 + transitionTime)` | idem, fin de `Gravity turn (S2)` |

`burn1Duration` reste calculé exactement comme aujourd'hui
(`GravityTurnManeuver.getBurn1Duration()`, propergol restant / débit) et
`burn2Duration = transitionTime − burn1Duration − interstageCoast`, borné à 0.

---

## 5. Conception détaillée

### 5.1 Nouveaux types

**`AscentPlan`** — record immuable, `simulation/mission/stage/ascent/` :

```java
public record AscentPlan(
    AbsoluteDate kickDate,          // ancre de la loi de pitch
    double transitionTime,          // variable CMA-ES 0
    double exponent,                // variable CMA-ES 1
    double burn1Duration,           // analytique, propergol restant / débit
    double interstageCoast,         // AscentProfile
    double burn2Duration,           // transitionTime − burn1 − coast, borné à 0
    double maxStepSeconds,          // partagé par les 3 phases (cf. §5.5)
    ActiveStageInfo firstStage,
    ActiveStageInfo secondStage) {

  public AbsoluteDate jettisonDate() { … }
  public AbsoluteDate secondIgnitionDate() { … }
  public AbsoluteDate mecoDate() { … }
}
```

Il est produit par une seule méthode — `GravityTurnManeuver.plan(entryState,
variables)` — qui remplace l'actuel `decode()` en le complétant des dates. **Il
n'y a qu'un seul endroit dans le code où ces dates sont calculées**, ce qui est
la propriété qui rend le découpage sûr.

**`AscentPlanRef`** — porteur mutable minimal, partagé par les trois phases :

```java
public final class AscentPlanRef {
  private volatile AscentPlan plan;
  public void set(AscentPlan p) { … }
  public AscentPlan require(String stageName) { /* lève si null */ }
}
```

Une phase qui `configure()` sans plan doit échouer bruyamment, exactement comme
`GravityTurnStage` échoue aujourd'hui sans `optimizationResult`
(`GravityTurnStage.java:93-96`).

**`AscentSequence`** — fabrique, garantit que les trois phases partagent la même
référence :

```java
public static List<MissionStage> gravityTurn(
    AscentProfile profile, GravityTurnConstraints constraints,
    double launchLatitude, double targetInclination) {
  AscentPlanRef ref = new AscentPlanRef();
  return List.of(
      new GravityTurnBurnStage("Gravity turn (S1)", ref, /* burn 1 */),
      new StageSeparationStage("S1 separation", profile.interstageCoastDuration(), 0),
      new GravityTurnBurnStage("Gravity turn (S2)", ref, /* burn 2 */));
}
```

`LEOMission.buildStages` et `GEOMission.buildStages` appellent cette fabrique au
lieu de construire un `GravityTurnStage`.

### 5.2 Répartition des responsabilités

| Phase | Classe | Rôle |
|---|---|---|
| `Gravity turn (S1)` | `GravityTurnBurnStage` (variante burn 1), `implements OptimizableMissionStage<GravityTurnProblem>` | Applique le pitch kick, construit et possède le problème CMA-ES, publie le plan dans `AscentPlanRef` depuis `applyOptimization()` (§5.6), configure le burn 1 (sémantique flame-out inchangée), termine à `jettisonDate` |
| `S1 separation` | `StageSeparationStage` **inchangée**, `expectedStageIndex = 0` | Largue S1, porte le coast inter-étage |
| `Gravity turn (S2)` | `GravityTurnBurnStage` (variante burn 2) | Lit le plan, configure le `ConstantThrustManeuver` du burn 2, termine au MECO |

`optimizationKey()` de la phase burn 1 reste **`"Gravity turn"`** : la carte de
résultats (`MissionOptimizerResult`, clés `String`) et tout ce qui l'indexe
restent valides, y compris les résultats déjà en mémoire pendant une session.

### 5.3 Le piège n°1 : l'ancre de la loi de pitch

`GravityTurnAttitudeProvider` calcule `alpha = clamp(dt / transitionTime)` avec
`dt = date − kickDate` (`GravityTurnAttitudeProvider.java:71-73`). Les trois
phases **doivent** instancier ce fournisseur avec le **même `kickDate` et le
même `transitionTime`** — ceux du plan, pas ceux de leur propre état d'entrée.

Une phase burn 2 qui s'ancrerait sur sa propre date de début repartirait à
`alpha = 0`, c'est-à-dire **poussée au zénith après le largage** : la trajectoire
serait détruite, et pas subtilement. Comme le fournisseur est une pure fonction
de la date, l'ancrer correctement rend le découpage exact : la loi d'attitude
vue par les trois propagations est bit-identique à celle d'aujourd'hui.

La phase de séparation est non-propulsive, mais son coast doit tout de même
porter le même fournisseur d'attitude pour que l'orientation ne saute pas d'une
frontière à l'autre — sans effet dynamique (pas de poussée), mais nécessaire
pour la cohérence de l'état rendu.

### 5.4 Une seule séquence pour les deux passes

C'est la condition qui empêche de rouvrir la divergence optimize-vs-éphéméride
fermée par le bilan 11 §3.9 (épinglée par `GravityTurnReplayConsistencyTest`).

Aujourd'hui, les deux passes propagent différemment :

- optimisation : `GravityTurnManeuver.propagateForOptimization` monte **un**
  propagateur et propage la GT d'un coup (`GravityTurnManeuver.java:213-236`) ;
- éphéméride : `MissionEphemerisGenerator` propage **stage par stage**, un
  propagateur par stage (`MissionEphemerisGenerator.java:50-147`).

Si on découpe l'éphéméride sans découper l'optimisation, les deux passes ne
voient plus la même séquence de redémarrages d'intégrateur. **Il faut donc
extraire le parcours de chaîne et l'utiliser des deux côtés** :

```java
// simulation/mission/runtime/StageChainRunner.java
public SpacecraftState run(List<MissionStage> chain, SpacecraftState entry,
                           Mission mission, StepSampler samplerOrNull) { … }
```

Le corps est celui de la boucle existante du générateur d'éphéméride, moins
l'échantillonnage (rendu optionnel via `samplerOrNull`) : `enter()` → propagateur
au `maxStepSeconds` du stage → `configure()` → propagation jusqu'à
`getConfiguredEndDate()`.

- `GravityTurnProblem.propagate(variables)` : écrit le plan, puis
  `runner.run(les 3 phases, entryState, mission, null)` → l'état MECO, sur
  lequel `computeCost` est évalué sans changement.
- `MissionEphemerisGenerator` : délègue sa boucle au même runner avec un
  échantillonneur. Une seule implémentation du parcours, donc plus de dérive
  possible entre les deux passes.

**Attention concurrence** : `CMAESTrajectoryOptimizer` explore en parallèle
(cf. les `ThreadLocal` de `GravityTurnManeuver.lastAltitudeTracker` et
`GravityTurnProblem.stagingShortfall`). Le runner doit être **sans état** et
l'`AscentPlanRef` partagée ne doit pas être écrite par les évaluations
concurrentes. Règle : pendant l'optimisation, `propagate()` construit un plan
**local** et le passe explicitement à des instances de phase locales à
l'évaluation ; `AscentPlanRef` n'est écrite qu'une fois, par
`applyOptimization()`, sur le fil appelant. C'est le même contrat que
`TransfertTwoManeuverStage.configure`, qui re-propage sur le fil appelant pour
cette raison exacte (`TransfertTwoManeuverStage.java:90-94`,
`MissionOptimizer.java:142-146`).

### 5.5 Le max step

`GravityTurnStage.maxStepSeconds` renvoie aujourd'hui
`GravityTurnManeuver.maxStepSeconds()`, dimensionné sur le burn 2 à l'allumage
tardif, et l'applique à toute la GT.

**En phase 1, les trois phases gardent cette même valeur** — portée par
`AscentPlan.maxStepSeconds`. Laisser la phase burn 1 retomber sur le défaut de
`MissionStage.maxStepSeconds` (`SAFE_MAX_STEP`, 30 s, pour un stage propulsif)
serait un piège : `burnLimitedMaxStep` est *capé* à `SAFE_MAX_STEP`
(`MissionStage.java:99-114`), donc le défaut est **supérieur ou égal** à la
valeur actuelle — sur charge lourde (FH) il est identique, mais sur une charge
légère il **relâcherait** le pas. Dans les deux cas la séquence de pas change,
donc la trajectoire aussi. Affiner le pas par phase est une optimisation de
phase 2, pas un effet de bord à accepter dans un refactor iso-trajectoire.

La phase de séparation est non-propulsive et son coast dure 2 s : le défaut
`COAST_MAX_STEP` de `MissionStage` suffit et est sans effet observable sur 2 s.
À vérifier tout de même à l'étape 2 de la migration.

### 5.6 Impact sur `MissionOptimizer`

La difficulté : la boucle de `MissionOptimizer` avance l'état par
`problem.propagate(bestVariables)` pour un stage optimisable
(`MissionOptimizer.java:209-211`). Or ici, `propagate` traverse **les trois
phases** — puis la boucle continuerait sur les phases 2 et 3, qui seraient
rejouées depuis le MECO. Double comptage garanti.

**Correctif** : un opt-in explicite sur l'interface.

```java
public interface OptimizableMissionStage<P extends TrajectoryProblem> {
  …
  /**
   * Whether this stage's problem propagates a chain of stages that the mission
   * loop will itself replay. When true, the loop must NOT advance the mission
   * state from problem.propagate(): it advances stage by stage as usual.
   */
  default boolean advancesByReplay() { return false; }
}
```

Dans la boucle : si `advancesByReplay()`, on optimise, on stocke le résultat, on
appelle **immédiatement** `applyOptimization(result)`, puis on avance par
`stage.propagateStandalone(...)` comme pour un stage non optimisable — et les
phases 2 et 3 suivent normalement. `false` pour tous les stages existants :
**aucun changement de comportement** pour `TransfertTwoManeuverStage` ni
`TransfertManeuverStage`.

**Le moment de l'injection est un point dur.** Aujourd'hui,
`applyOptimization()` n'est appelée qu'**après** la boucle
(`MissionOptimizer.java:226-232`), pour préparer le replay éphéméride. Mais ici
les phases 2 et 3 sont rejouées *dans* la boucle, juste après l'optimisation de
la phase 1 : sans injection immédiate, elles trouveraient une `AscentPlanRef`
vide et échoueraient. D'où l'appel anticipé. La boucle d'injection de fin reste
en place et devient idempotente pour ces stages (même résultat, même plan) — on
ne la supprime pas, elle sert toujours aux stages `advancesByReplay() == false`.

Le résultat est identique par construction : la chaîne rejouée par la boucle est
la même que celle propagée par le problème, avec le même plan.

### 5.7 Impact sur le rapport de performance

Aucun changement de code — les mécanismes existants deviennent simplement
exacts :

| Phase | `isPropulsive` | Ce que le rapport calcule |
|---|---|---|
| `Gravity turn (S1)` | `true` | propergol S1 brûlé, ΔV à Isp 296 s — **correct**, plus de largage enjambé |
| `S1 separation` | `false` | 0 kg, 0 m/s ; et `captureJettisonedResidual` mesure enfin le résidu S1 (S3 fermé) |
| `Gravity turn (S2)` | `true` | propergol S2 brûlé, ΔV à Isp 348 s — **correct** (S2 fermé) |

C'est le changement de sortie le plus visible, et il est **volontaire** : le
`ΔV total` du rapport va bouger. Voir §7.2.

---

## 6. Ce qui devient inutile — et qu'on garde quand même en phase 1

Une fois la séparation devenue une phase, deux garde-fous perdent leur objet :

1. la pénalité d'étagement de `GravityTurnProblem`
   (`STAGING_PENALTY_BASE`, `W_STAGING_SHORTFALL`, le `ThreadLocal`
   `stagingShortfall`, `GravityTurnProblem.java:59-84,149-161`) ;
2. le contrôle levant une exception dans `GravityTurnStage.configure`
   (`GravityTurnStage.java:119-127`).

Structurellement, un `transitionTime` trop court donne désormais
`burn2Duration = 0` — une phase burn 2 de durée nulle — **mais le largage a
quand même lieu**, puisqu'il est une phase et non un détecteur intra-phase. Le
mode de défaillance disparaît.

**Ils sont néanmoins conservés en phase 1.** La pénalité vaut 1e3 sur des
candidats qui, aujourd'hui, sont écartés ; la retirer rend ces candidats
recevables et **modifie le paysage de coût vu par CMA-ES**, donc potentiellement
l'optimum retenu. C'est un changement de comportement, à isoler dans son propre
commit avec re-run des tests d'optimisation (étape 5 de la migration). Le
principe est le même que celui qui a fait choisir une pénalité plutôt qu'une
borne à l'origine : on ne touche pas à la géométrie de la recherche en même
temps qu'à autre chose.

---

## 7. Non-régression

### 7.1 Ce qui doit rester identique

**« Iso-trajectoire » ne peut pas vouloir dire bit-identique.** Couper une
propagation en trois redémarre l'intégrateur adaptatif à chaque frontière : la
séquence de pas change, donc l'arithmétique flottante aussi. La non-régression
se définit par des tolérances.

| Niveau | Critère | Seuil |
|---|---|---|
| **N1 — dur** | `LEOMissionOptimizationTest` et `GEOMissionOptimizationTest` passent **sans que leurs seuils soient touchés** | seuils actuels (±7 % LEO) |
| **N1 — dur** | `GravityTurnReplayConsistencyTest` passe, étendu à la chaîne découpée | tolérances actuelles du test |
| **N2 — mesuré** | État au MECO : date, masse, position, vitesse | Δdate < 1 ms ; Δmasse < 1 kg ; Δ\|r\| < 10 m ; Δ\|v\| < 0,05 m/s |
| **N2 — mesuré** | Orbite finale : périgée, apogée, inclinaison | écart relatif < 0,1 % ; Δi < 0,01° |
| **N2 — mesuré** | Variables CMA-ES retenues (`transitionTime`, `exponent`) à graine fixée (`DEFAULT_SEED = 42`) | Δ`transitionTime` < 0,5 s |
| **N3 — surveillé** | Temps de calcul d'une optimisation complète | pas de dégradation > 20 % |

N2 doit être **relevé avant** le refactor (étape 0) et comparé après chaque
étape. C'est le point sur lequel la mémoire de non-régression insiste : une
baseline chiffrée, et un seul changement de comportement à la fois.

### 7.2 Ce qui change légitimement

À consigner comme attendu, pas comme régression :

1. **`ΔV total` du rapport** : le burn 2 est enfin crédité à l'Isp de S2. La
   valeur augmente (le même propergol rend plus de ΔV à 348 s qu'à 296 s). Le
   rapport devient plus juste ; toute comparaison à un chiffre historique doit
   être re-basée.
2. **Résidu de propergol par étage** : l'indice 0 (S1) reflète désormais le
   résidu réellement largué, au lieu de sortir du modèle de masse.
3. **Nombre de lignes du rapport et de noms de phases** : 3 au lieu de 1 pour
   l'ascension. La télémétrie affiche `GRAVITY TURN (S1)`, `S1 SEPARATION`,
   `GRAVITY TURN (S2)`.
4. **Points d'éphéméride** : deux points terminaux supplémentaires (un par
   frontière de phase, `MissionEphemerisGenerator.java:131-137`). Le rendu de
   trajectoire n'en est pas affecté (les points sont co-localisés dans le temps),
   mais un test qui compterait les points le verrait.

### 7.3 Ce que le découpage ferme définitivement

- Un MECO antérieur au largage **ne peut plus** laisser S1 attaché : le mode de
  défaillance de S1 disparaît, y compris pour les charges légères qui l'avaient
  révélé.
- Le résidu de S1 devient mesurable (S3).
- Le ΔV d'ascension devient exact (S2).
- La séquence d'étagement devient lisible à l'écran sans lire les logs.

---

## 8. Plan de migration

Chaque étape est committable et vérifiable seule. **L'ordre compte** : la
baseline avant tout, la comptabilité en dernier.

| Étape | Contenu | Vérification |
|---|---|---|
| **0** | Relever la baseline N2 (§7.1) sur FH LEO 400 km et FH GEO, graine 42. Étendre `GravityTurnReplayConsistencyTest` avec les états de référence. | La baseline est écrite dans le test / un fichier de référence |
| **1** | Extraire `AscentPlan` + `GravityTurnManeuver.plan()` et `StageChainRunner`. **Aucun changement de liste de stages** : la `GravityTurnStage` actuelle consomme le plan et le générateur d'éphéméride délègue au runner. | N1 + N2 inchangés (le refactor est purement interne) |
| **2** | Découper en trois phases (`GravityTurnBurnStage` ×2 + `StageSeparationStage`), ajouter `advancesByReplay()`, brancher `AscentSequence`, câbler la passe d'optimisation sur le runner. | N1 vert, N2 dans les seuils, N3 relevé |
| **3** | Brancher `LEOMission` et `GEOMission` sur `AscentSequence` (les 3 variantes LEO + GEO). | N1 vert sur les deux profils |
| **4** | Comptabilité : constater et documenter les écarts de §7.2 ; ajuster les tests qui figeraient un `ΔV total`. | Les écarts constatés correspondent aux écarts attendus |
| **5** | *(comportement — commit séparé)* Retirer la pénalité d'étagement et le garde-fou d'exécution devenus sans objet (§6). | Re-run complet des tests d'optimisation ; N2 re-relevé et **comparé**, écart documenté |
| **6** | *(nettoyage)* Supprimer les résolutions `stage2` fantômes des stages analytiques (S4), remplacées par un commentaire d'invariant. | Compilation + N1 |

Les étapes 0 à 4 sont iso-comportement. L'étape 5 est le seul changement de
comportement, isolé — conformément à la règle « un changement de comportement à
la fois ». L'étape 6 est purement lexicale.

**Tests d'optimisation** : longs, lancés manuellement par l'utilisateur. Le plan
doit donc minimiser le nombre de points de contrôle qui les exigent : les étapes
1, 2, 3 et 5 en demandent un ; les étapes 0, 4 et 6 non.

---

## 9. Hors périmètre — suites possibles

### 9.1 Décomposer les stages analytiques (S7)

Même patron, appliqué à `AnalyticHohmannTransferStage`,
`AnalyticParkingInsertionStage`, `AnalyticApogeeCircularizationStage`,
`TransfertTwoManeuverStage` : `burn 1 → coast → burn 2` en trois phases, pilotées
par le `BurnPlan` que ces classes calculent déjà en interne. Le gain est
principalement d'observabilité (le coast GEO de 5 h 15 mériterait son nom à
l'écran) ; il n'y a pas d'invariant en jeu, donc pas d'urgence.

### 9.2 Séparation de la charge utile en LEO (S5)

`StageSeparationStage("S2 separation", coast, 1)` entre `Trim` et `Coasting`.
**Change les trajectoires LEO** (masse propagée réduite de 4 t sur le coast
final) : à traiter comme un changement de comportement à part entière, jamais
groupé avec le découpage de l'ascension.

### 9.3 Nettoyer les séparations fantômes (S4)

Étape 6 de la migration. À remplacer par un commentaire d'invariant sur
`VehicleStack.resolveActiveStage` : *un étage actif ne change que par largage
explicite ; une combustion, même jusqu'à la panne sèche, s'arrête au plancher de
déplétion qui reste strictement au-dessus de la masse des étages supérieurs.*

### 9.4 Pitch kick explicite (S8)

Tentant une fois les phases en place, mais c'est le terrain miné du bilan 11
§3.9 : le générateur d'éphéméride écrase le résultat de `enter()` par
l'`entryState` sauvegardé. Une phase `PitchKickStage` dédiée serait la sortie
propre — mais elle demande de revoir le contrat `getEntryState()`, donc un
chantier à elle seule.

### 9.5 Coiffe et boosters (S6)

À ouvrir au moment où un 2ᵉ lanceur entre au catalogue, pas avant.
`StageRole.BOOSTER` / `KICK` existent déjà et attendent un consommateur ;
une coiffe demanderait un élément de pile sans propulsion, ce que
`VehicleStack` ne modélise pas encore (`resolveActiveStage` suppose un étage
propulsif à chaque niveau).

---

## 10. Fichiers touchés (phase 1, étapes 1–4)

**À créer**

- `simulation/mission/stage/ascent/AscentPlan.java`
- `simulation/mission/stage/ascent/AscentPlanRef.java`
- `simulation/mission/stage/ascent/AscentSequence.java`
- `simulation/mission/stage/ascent/GravityTurnBurnStage.java`
- `simulation/mission/runtime/StageChainRunner.java`

**À modifier**

- `simulation/mission/maneuver/GravityTurnManeuver.java` — `plan()` remplace
  `decode()` ; `configure()` éclaté en `configureBurn1` / `configureBurn2` ;
  le `DateDetector` de largage disparaît
- `simulation/mission/stage/ascent/GravityTurnStage.java` — supprimée en fin
  d'étape 3 (remplacée par `AscentSequence`)
- `simulation/mission/optimizer/problems/GravityTurnProblem.java` — `propagate()`
  passe par le runner ; la pénalité d'étagement reste jusqu'à l'étape 5
- `simulation/mission/OptimizableMissionStage.java` — ajout d'`advancesByReplay()`
- `simulation/mission/runtime/MissionOptimizer.java` — respect d'`advancesByReplay()`
- `simulation/mission/ephemeris/MissionEphemerisGenerator.java` — délègue au runner
- `simulation/mission/operation/LEOMission.java` — 3 sites de construction de stages
- `simulation/mission/operation/GEOMission.java` — 1 site

**Inchangés (et c'est le but)**

- `simulation/mission/stage/StageSeparationStage.java` — réutilisée telle quelle
- `simulation/mission/attitude/GravityTurnAttitudeProvider.java`
- `simulation/mission/vehicle/**` — le modèle de véhicule ne bouge pas
- `simulation/mission/planner/**`, `runtime/MissionLoadEvaluator.java` — la boucle
  de dimensionnement de charge lit l'objectif sur le dernier point d'éphéméride,
  pas les noms de phases
- toute l'UI — la télémétrie affiche le nom de phase courant sans le connaître
  (`TelemetryWidget.java:217`)

**Tests à étendre**

- `simulation/mission/maneuver/GravityTurnReplayConsistencyTest.java` — la chaîne
  optimisée et la chaîne rejouée doivent produire le même état final
- nouveau : un test de séquence vérifiant que l'ascension composée contient bien
  une `StageSeparationStage` d'index attendu 0 entre les deux burns, et que
  `Gravity turn (S2)` refuse de se configurer sans plan
