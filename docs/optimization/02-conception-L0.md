# OPT-1 / L0 — Référence mesurée — conception

Lot `L0` du découpage [`01-decoupage.md`](01-decoupage.md) §5. Ce document conçoit le **banc
de mesure** ; les chiffres qu'il produira vivront dans `03-baseline-L0.md`, écrit après
exécution (par l'utilisateur — les runs d'optim sont lents et lui appartiennent).

**Ce que L0 rend vrai :** on connaît, à froid, hors jacoco, drag-on, où part le temps de
chaque mode, et on tient les contrefactuels qui ordonnent le backlog. Aucun changement de
`src/main` qui bouge un résultat.

---

## 1. Le banc

Un outil autonome sous `src/main/java/com/smousseur/orbitlab/tools/optbench/`, sur le modèle
d'`orbitgen` / `ephemerisgen` : un `main()` qui initialise Orekit, construit chaque mission de
référence, la calcule en la chronométrant, et écrit un rapport. **Hors Gradle → pas de
jacoco.**

**Invocation headless, sans toucher `src/main`** (le chemin réel de l'application) :

```
MissionEntry entry = new MissionEntry(spec);      // compose en FAST par défaut
entry.setOptimizationType(mode);                  // recompose (spec présente)
MissionPlan plan = new MissionPlanOptimizer(entry, launchDate, listener).compute();
```

`MissionPlanOptimizer` accepte déjà un `MissionProgressListener` : le banc en attache un qui
**horodate chaque événement**, et enveloppe l'appel `compute()` pour le wall-clock total.
Rien de plus n'est requis côté `src/main`.

**Contraintes de mesure** (découpage §2.1, fiche §Pièges) :

- **JDK 21** (`JAVA_HOME` GraalVM 21, cf. mémoire projet) ; drag-on (défaut runtime) ;
- hors jacoco (conséquence directe du lancement hors Gradle) ;
- la machine et sa charge sont notées dans le rapport (le split ~55/45 du découpage §3 est
  établi sur 6c/12t) ;
- **le log d'août est disqualifié** (antérieur à `PHY-8` et au drag-on par défaut) : L0
  re-mesure tout sur le code courant.

---

## 2. Matrice de référence

| Mission | Modes | Rôle |
|---|---|---|
| Falcon Heavy + 10 t (`EARTH_OBSERVATION_SAT`) @ 400 km circ., Kourou, drag-on | FAST, BALANCED, PRECISE | la référence des **183-192 s** (cloture PHY-2 §4) ; les trois modes |
| GEO SAT (`GEO_SAT`), FAST | FAST | profil analytique GEO (`FixedLoadPlanner`, pas de transfert CMA-ES) |
| **Ariane 64 + 10 t (`EARTH_OBSERVATION_SAT`) @ 400 km circ., Kourou, FAST** | FAST | cas **symptomatique du dimensionnement** : `MeasuredLoadPlanner` diverge jusqu'à **6 vols** de bracket (découpage §5.3, `MAX_SIZING_PASSES + MAX_BRACKET_PASSES`). C'est là que le poids bascule de la propagation vers la recherche — il **valide (ou dément) la réserve du modèle FAST** (découpage §3) |

La charge utile est `EARTH_OBSERVATION_SAT` = **10 000 kg** (`catalog/Payloads.java:66`), pas
`Spacecraft.LEGACY` (150 kg). Les missions sont construites par le **même chemin que le
wizard** (spec → `MissionComposer`), pour que la baseline colle à ce que l'application vit.

---

## 3. Ce qui est mesuré

### 3.1 Wall-clock et timeline (événements de progression)

- **Total** par cellule : wrapping de `compute()`.
- **Frontières de phase** : le listener horodate `StageEntered` (une par vol pour FAST, le GT
  étant le seul étage optimisable), `AttemptStarted`, `StepStarted(EXPLORATION/REFINEMENT)`,
  `SizingAdvanced` (PRECISE). Le **nombre de vols** de `MeasuredLoadPlanner` se reconstruit en
  comptant les `StageEntered` (il n'émet pas `SizingAdvanced`, qui est propre au balayage λ de
  PRECISE).
- **Coût GT par vol** (chiffre de `D2`) : temps entre `StageEntered(GT)` et la fin de la
  recherche de ce vol.

### 3.2 Le split FAST recherche / propagation (JFR)

Enregistrement **JFR programmatique** (`jdk.jfr.Recording` démarré/arrêté autour de chaque
cellule → un `.jfr` par cellule), pour n'enregistrer que la fenêtre calculée, pas l'init
Orekit.

**Attribution par thread**, plus robuste que par région de code :

- les threads du **pool d'optimiseur** (exploration + raffinement) n'exécutent que des
  évaluations de candidats → leur temps CPU **est** la recherche GT (~55 % attendu) ;
- le **thread de calcul principal** (single-thread) exécute la boucle d'étages — donc la
  propagation du **transfert analytique** (étage non optimisable) puis la **génération
  d'éphéméride** — pendant que le GT tourne, il est *bloqué* sur `Future.get` (état `park`,
  pas un échantillon d'exécution). Ses échantillons d'exécution sont donc la propagation
  hors-recherche (~45 % attendu), scindée transfert-vs-éphéméride par méthode
  (`MissionEphemerisGenerator` vs les étages analytiques).

Statistique mais suffisant : ~19 000 échantillons sur 192 s résolvent un 55/45 sans peine.
Le JFR profile aussi **une évaluation** (NRLMSISE, `MinAltitudeTracker`, `DepletionGuard`,
`ReentryGuard`, conversions géodésiques) — **c'est lui qui ordonne les lots `C`** du backlog.

### 3.3 Livrables factuels annexes

- **Correspondance mode → (planner, budget/vol, nb vols)** : FAST/BALANCED → `MeasuredLoadPlanner`,
  40 000 évals/vol, 2-6 vols ; PRECISE → `MinimizedLoadPlanner`, 8 000 évals/vol de λ, N vols
  de balayage. (Corrige le faux paradoxe de budget, découpage §2.2.)
- **Coût de la passe 3** (préparation de `B3`) : mesuré depuis la timeline de la baseline
  PRECISE — chaque passe de raffinement est datée. Le **contrefactuel de verdict** — PRECISE
  rejoué *sans* passe 3, jugé sur λ\*/résidu/faisabilité — **n'est pas dans L0** : le désactiver
  exige une manette (`REFINEMENT_PASSES` est `private static final`, sans paramètre), donc un
  changement de `src/main` que L0 s'interdit. Il rejoint le lot `B3`, qui porte cette manette.
  (Correction : la fiche et le découpage §5 le rangeaient dans L0 ; il y était infaisable sans
  toucher au comportement.)

---

## 4. Assurance bit-identité pour L1

Procédure, pas du code du banc : lancer `gateTest` **N fois** (`cleanTest` d'abord à chaque
reprise de filtre) et vérifier que les quatre gates sont **bit-identiques** d'un run à
l'autre. Elles le sont déjà attendu — sous un ordonnancement non déterministe des 4 runs
d'exploration et de l'arrêt croisé — et le confirmer **dé-risque `A1`** avant de l'écrire
(découpage §5.2). Un écart entre runs ici invaliderait l'argument de bit-identité de L1 et
serait à comprendre **avant** L1.

---

## 5. Sortie

Un **rapport Markdown** écrit par le banc (une table par cellule : total, nb vols, timeline
des phases, split JFR, et les chemins des `.jfr`), prêt à alimenter `03-baseline-L0.md`. Les
`.jfr` sont conservés à côté pour ré-analyse.

---

## 6. Ce que L0 ne fait pas

- Aucun changement de `src/main` (le banc est sous `tools/`, la mesure passe par le listener
  et le JFR déjà disponibles).
- Aucune optimisation : L0 mesure, il n'accélère rien. Le premier gain est `L1`.
- Ne tranche pas l'ordre des lots `C` par lui-même : il **fournit** le profil JFR qui le
  tranchera au moment d'entamer le backlog.
- Ne joue pas le contrefactuel « sans passe 3 » : il demande une manette de comportement,
  réservée au lot `B3` (voir §3.3).

---

## 7. Ce que L0 alimente

- `03-baseline-L0.md` — les chiffres, une fois le banc exécuté.
- L'**ordre des lots `C`** du backlog (découpage §5.3), par le profil d'une évaluation.
- La **cible chiffrée** de chaque lot du backlog (le total et le split par mode sont les
  dénominateurs contre lesquels chaque accélération se mesure).
- La confirmation — ou l'infirmation — du **split ~55/45** dérivé au découpage §3, dont
  dépend le poids relatif du parallélisme et des pistes `C` sur FAST.
