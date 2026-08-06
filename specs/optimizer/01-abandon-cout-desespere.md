# 01 — Abandon des candidats à coût désespéré : résultat négatif

**Date** : 2026-08-06
**Statut** : **implémenté, mesuré, révoqué.** Le mécanisme ne s'est jamais déclenché sur aucun
profil. Code de production retiré ; ce document est conservé comme constat, pour que l'idée ne soit
pas re-proposée sans les mesures qui suivent.
**Périmètre exploré** : `CMAESTrajectoryOptimizer`, `MissionOptimizer`, `MissionLoadEvaluator`

---

## 1. Point de départ

Une mission LEO en `OptimizationType.PRECISE` demandait environ 20 minutes (estimation de
l'utilisateur, pas une mesure). Objectif du chantier : faire baisser ce temps.

## 2. Anatomie du temps — **acquis, réutilisable**

Par optimisation d'étage, avec le budget par défaut de 40 000 évaluations
(`MissionLoadEvaluator.DEFAULT_OPTIMIZER_MAX_EVALUATIONS`) :

| phase | budget | exécution | temps mur |
|---|---|---|---|
| exploration | 40 % = 16 000 | 4 runs **parallèles** (`newFixedThreadPool`) | ~4 000 évals |
| raffinement | 60 % = 24 000 | 3 passes **séquentielles**, σ ×0,1 / ×0,03 / ×0,01 | jusqu'à 24 000 évals — **6×** |
| tentatives | ×3 (`DEFAULT_MAX_RETRIES` = 2) | 4 puis 6 puis 8 runs, σ ×1 / ×1,3 / ×1,6 | |

Une mission LEO optimisée enchaîne deux de ces optimisations : le gravity turn, puis le transfert.

Quatre sorties anticipées existent déjà : cible atteinte pendant l'exploration ; **consensus**
(≥ `CONSENSUS_MIN_RUNS` = 2 runs ayant descendu de plus de `CONSENSUS_DESCENT_RATIO` = 1 % et
s'accordant à `CONSENSUS_RELATIVE_EPS` = 1e-4 près) ; plateau de la cascade ; stagnation entre
tentatives.

La branche coûteuse en théorie est donc : coût au-dessus d'`acceptableCost` **et** pas de consensus
→ cascade complète → deux tentatives élargies. Jusqu'à ~3 × (4 000 + 24 000) = 84 000 évaluations de
temps mur contre 4 000 pour le chemin heureux, un facteur 21.

## 3. Échelle des coûts — **acquis, réutilisable**

| régime | valeur |
|---|---|
| transfert convergé | `TransferTuning.defaults().acceptableCost()` = **3e-3** |
| gravity turn convergé | `W_FPA_SOFT × (2,5°)²` = 25 × 0,0436² ≈ **0,048** |
| GT au plancher d'étagement du GEO (solution **retenue**, faisable) | 8,9e-3 |
| valeur intermédiaire réellement observée : GT à λ=0,3 sur GEO | **388** |
| pénalité de propagation / d'étagement | `failureBaseCost` = `STAGING_PENALTY_BASE` = **1e3** |
| échec de graduation du transfert | 1e6 |
| exception pendant l'évaluation | `EXCEPTION_PENALTY_COST` = 1e10 |

Deux enseignements qui survivent au chantier :

- **Il n'y a pas de bande vide** entre le convergé et la pénalité. Le 388 est un coût nominal.
- **Les deux problèmes ne partagent pas d'échelle** (facteur 16 entre leurs seuils d'acceptation) :
  tout critère les concernant doit être **relatif** à `getAcceptableCost()`, jamais absolu.
- Le critère naïf « coût > `acceptableCost` ⟹ infaisable » est **faux** : au GEO, le λ\* retenu
  (0,7922 sur S2) vit sur un gravity turn épinglé à 8,9e-3, durablement au-dessus de son seuil
  d'acceptation de 0,048.

## 4. Ce qui a été construit

Une constante `HOPELESS_COST_RATIO = 1000` : un candidat dont le meilleur coût, en fin de phase
d'exploration, atteignait 1000× le `getAcceptableCost()` de son problème sautait la **cascade de
raffinement** (polissage local, incapable de franchir trois décades) tout en **conservant ses
tentatives** (ré-exploration globale, seul mécanisme susceptible de le sauver). Seuils effectifs :
3,0 pour le transfert, 47,6 pour le gravity turn.

Opt-in, faux par défaut, activé au seul site `MissionLoadEvaluator` — donc `FAST` et `BALANCED`
intacts par construction.

## 5. Mesure — et pourquoi le mécanisme est mort

Runs de l'utilisateur sur `PropellantLoadOptimizerIntegrationTest` :

| profil | λ\* | référence | temps | évals |
|---|---|---|---|---|
| GEO | [0,95625 ; 0,7921875], −56 151 kg | identique | 6 min 21 | 29 |
| LEO | [0,9125 ; 0,6828], S2 1 340 kg, −108 510 kg | identique | 16 min 44 | 28 |

**Zéro occurrence de la trace `Hopeless cost` dans les logs.** Le raccourci ne s'est déclenché sur
aucun profil ; le λ\* inchangé n'a donc aucune valeur probante — le code ne s'est pas exécuté.

**La raison, et c'est le vrai résultat du chantier :** un λ trop bas ne produit pas un coût CMA-ES
désespéré, il échoue par un **canal entièrement différent**. Les gardes analytiques
(`AnalyticParkingInsertionStage`, `AnalyticGtoInjectionStage`, `StageSeparationStage`) lèvent une
exception que `MissionLoadEvaluator` rattrape comme « infaisable ». Ce fail-fast **existait déjà,
une couche au-dessus**, et il coupe plus tôt et plus fort que ce raccourci : il abandonne la mission
entière, là où le raccourci n'aurait économisé qu'une phase interne. Le chantier re-résolvait un
problème déjà résolu.

## 6. Les chiffres qui corrigent l'analyse initiale

L'estimation « ~120 s par éval » supposait ~10 évals ; le balayage est multi-étages. En réalité :

- **LEO : 28 évals à 36 s** (2 passes × 2 étages = 4 bissections)
- **GEO : 29 évals à 13 s** — recoupe exactement la moyenne déjà consignée pour ce profil

Or une mission LEO à transfert optimisé qui parcourt toute son échelle de tentatives coûte 292 à
820 s. À 36 s, la grande majorité des évals ne parcourt **ni la cascade ni les tentatives**. Le
plafond de ce que ce raccourci pouvait rapporter était petit dès le départ, et l'anatomie de la §2 —
correcte en elle-même — décrivait une branche que ces profils n'empruntent quasiment jamais.

**Conclusion opératoire : sur LEO le temps est dans le NOMBRE d'évaluations, pas dans leur coût
unitaire.**

## 7. Deux pièges de test, valables pour tout travail futur ici

Découverts en écrivant les tests, et contraignants pour quiconque voudra affirmer quelque chose sur
un nombre d'évaluations :

- **`crossRunStop` rend `OptimizationResult.evaluations()` non reproductible** dès qu'un problème
  passe sous son seuil d'acceptation : le premier run qui *termine* sous le seuil fait avorter les
  autres à leur évaluation suivante, ce qui dépend du temps mur. Un test qui compare des nombres
  d'évaluations doit utiliser un problème dont le coût reste au-dessus du seuil.
- **Le plancher de coût doit écraser le gradient** (descente < `CONSENSUS_DESCENT_RATIO` = 1 %),
  sinon le consensus conclut de lui-même et saute déjà la cascade : le test passerait alors pour la
  mauvaise raison.

## 8. Pistes écartées en cours de route

**Évaluer les λ en profil analytique, ne construire qu'à la fin en transfert optimisé.** Le profil
analytique n'est pas une approximation bruitée du profil optimisé, c'est un vol **systématiquement
plus gourmand** : 415 m/s de transfert contre 241 m/s, résidu S2 de 5,7 % contre 43,6 %.
L'implication ne va que dans un sens — « faisable en analytique » ⟹ « faisable en optimisé », jamais
l'inverse. Une minimisation a besoin du certificat inverse : elle descend λ jusqu'à ce que ça casse.
Sizer sur l'analytique récolterait les ~6 % de marge de l'analytique au lieu des ~44 % de l'optimisé.

**Le λ\* analytique comme bracket supérieur certifié.** Valide (λ\*ₐ ≥ λ\*ₒ) mais sans effet : la
bissection part de [0,3 ; 1] et converge bas, ses sondes ne montent jamais assez pour que le bracket
morde. Économie : une à deux évals.

**Sauter aussi les tentatives dans la bande désespérée.** Contredit l'invariant documenté « la
première tentative n'est jamais sautée ». Devenu sans objet : la bande n'est jamais atteinte.

**Plafonner les tentatives à 0 dans le balayage.** Aveugle : pénalise aussi les λ faisables dont la
convergence demandait une tentative.

## 9. Suite

> **Suite de l'histoire (2026-08-07) : le warm start λ→λ décrit ci-dessous a été implémenté, mesuré
> et révoqué à son tour** — voir `03-warm-start-lambda.md` §8. Il réduit bien le coût unitaire d'une
> évaluation faisable (−86 % sur le point mesuré), mais en dégradant la solution retournée, ce que le
> balayage paie en remontant λ. La §6 ci-dessus reste valide : sur LEO le temps est dans le nombre
> d'évaluations.

**Étape suivante décidée : warm start λ→λ.** Réinjecter le `bestVariables` de l'évaluation
précédente comme point de départ semé — la machinerie existe déjà (`buildSeededStartPoints`,
`getLowerBoundsForAttempt`), il manque le passage depuis `MissionLoadEvaluator`, différé faute d'un
hook de graine (voir le javadoc de la classe). Réduit le coût unitaire d'une éval.

**Mais au vu de la §6, cela ne suffira probablement pas** : il faudra aussi réduire le **nombre**
d'évaluations — tolérance et nombre de passes du balayage coordonné — au prix, cette fois, d'un
déplacement assumé de λ\*, donc d'un arbitrage explicite entre temps de calcul et ergols récupérés.
