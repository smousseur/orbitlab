# 03 — Warm start λ→λ dans le balayage de charges

**Date** : 2026-08-06, mesuré et révoqué le 2026-08-07
**Statut** : **implémenté, mesuré, révoqué.** Le mécanisme fonctionne exactement comme conçu, et
c'est ce qui le condamne : il fait sortir CMA-ES plus tôt sur une solution à Δv excédentaire que le
balayage lit comme infaisable. Sur LEO, +33 % de temps mur et 2 784 kg repris en moins ; sur GEO,
effet nul à 0,1 % près. Code de production retiré (`git checkout` des trois fichiers) ; ce document
est conservé comme constat, avec le canal de défaillance nommé en §8, pour que l'idée ne soit pas
re-proposée sans lui.
**Périmètre** : `CMAESTrajectoryOptimizer`, `MissionOptimizer`, `MissionLoadEvaluator`

---

## 1. Pourquoi

Mesuré sur les runs du 2026-08-06 : la **passe 1** du balayage coûte 14 évaluations à ~47 s pièce sur
LEO, contre ~22 s en passe 2. C'est l'exploration depuis λ=1 vers le bas qui porte le temps.

Et la règle établie au passage (voir `02-sonde-de-jeu.md`) : **le temps mur suit le nombre
d'évaluations _faisables_**, pas le nombre d'évaluations — 64 s pour une évaluation qui optimise
vraiment, 13 s pour une qui échoue vite sur une garde analytique. Réduire le coût d'une évaluation
faisable est donc le seul levier qui reste sur la passe 1.

Or chaque évaluation ré-explore la boîte entière alors que deux λ consécutifs diffèrent de quelques
pourcents et que l'optimum ne bouge quasiment pas. Le javadoc de `MissionLoadEvaluator` notait déjà
la piste, différée « faute d'un hook de graine ».

## 2. Mécanisme

`OptimizationResult.bestVariables()` de l'évaluation précédente est réinjecté comme **point de départ
imposé d'un run d'exploration** de la première tentative, par étage, apparié sur
`OptimizableMissionStage.optimizationKey()`.

Le gain espéré passe par l'arrêt croisé : un run qui démarre à l'optimum termine sous
`acceptableCost` et fait avorter les trois autres, la phase d'exploration coûtant alors le temps de
ce seul run.

Chemin de données :

```
MissionLoadEvaluator (champ lastSolution)
  → MissionOptimizer (MissionOptimizerResult warmStart)
    → CMAESTrajectoryOptimizer (double[] warmStartSeed, par étage)
      → buildSeededStartPoints(attempt 0)
```

## 3. Trois décisions de conception, chacune imposée par le code existant

**La graine est ajoutée en QUEUE de liste, jamais en tête.** Les points de départ imposés remplissent
les runs d'exploration dans l'ordre, et le repli `run == 0` ne fournit `buildInitialGuess` que si la
liste est **vide**. Une insertion en tête ferait donc disparaître `buildInitialGuess` pour tout
problème sans `buildAnalyticalSeed` — c'est le cas du **gravity turn**, dont le seed analytique *est*
`buildInitialGuess`, recalculé pour chaque charge. L'ajout en queue déplace un run perturbé à la
place, ce qui préserve la diversité de points de départ dont dépend l'arrêt par consensus.

**La dernière solution *utilisable*, pas la précédente évaluation.** `MissionLoadEvaluator` garde un
champ `lastSolution` plutôt que de lire l'argument `previous` : celui-ci est nul au premier appel et
porte un résultat nul dès qu'une évaluation a échoué à optimiser — or un λ qui échoue est précisément
le voisin du prochain λ sondé. Le balayage étant séquentiel, un champ simple suffit.

**Une graine d'arité incorrecte est rejetée**, avec un WARN, dans `CMAESTrajectoryOptimizer` — seul
endroit où le nombre de variables du problème est connu. Sans ce garde-fou, `clampToBounds`
lèverait une exception d'indice.

Aucune conversion de bornes n'est nécessaire : `runSinglePass` clampe déjà tout point de départ dans
la boîte courante. Ça compte, parce que **les bornes bougent avec la charge** — le plancher
d'étagement du gravity turn suit la durée du burn 1, qui suit la charge de S1.

## 4. Confinement

Le warm start n'est passé qu'au seul site `MissionLoadEvaluator`. `FixedLoadPlanner`, les tests
d'optimisation de mission et `AscentBaselineN2Test` gardent le constructeur à trois arguments : les
missions à charges fixes restent bit-identiques.

**λ\* va bouger.** Injecter un point de départ change le chemin CMA-ES, donc la solution de chaque
étage, donc la faisabilité au voisinage de la frontière. C'est attendu et ce n'est pas un critère
d'échec ici — contrairement à la sonde de jeu, ce chantier n'a pas d'invariant « λ\* identique ».

## 5. Tests

`CMAESTrajectoryOptimizerWarmStartTest`, vert. Les assertions sont **structurelles** : chaque point
de départ d'exploration est évalué une fois avant le lancement des runs, donc un problème qui
enregistre les vecteurs qu'on lui demande de propager peut dire exactement quels points ont servi.

1. le vecteur injecté est bien évalué comme point de départ ;
2. `buildInitialGuess` reste un point de départ malgré l'injection — le test qui protège le gravity
   turn ;
3. le `buildAnalyticalSeed` d'un problème qui en a un survit lui aussi ;
4. une graine nulle reproduit exactement le comportement historique.

Le quatrième test a d'abord échoué pour la raison déjà consignée en `01-abandon-cout-desespere.md`
§7 : comparer des nombres d'évaluations exige un problème dont le coût reste **au-dessus** du seuil
d'acceptation, sinon l'arrêt croisé — dépendant du temps mur — rend le compte non reproductible.
D'où `FlooredRecordingProblem`.

## 6. Mesure attendue

Sur `PropellantLoadOptimizerIntegrationTest`, les deux profils. Ce qu'il faut relever :

- le **temps mur**, en particulier celui de la passe 1 ;
- λ\*, qui va probablement bouger — vérifier qu'il reste faisable et que la masse récupérée ne
  **régresse** pas ;
- dans le log, la ligne `Warm start: previous solution injected as exploration run {n}` par étage et
  par évaluation, puis `Target reached during exploration` : c'est cette conjonction qui signe le
  gain.

**Réserve sérieuse sur l'ampleur du gain.** Il transite par l'arrêt croisé, qui exige d'atteindre
`acceptableCost`. Là où le coût plancher est *structurellement* au-dessus — le gravity turn du GEO
épinglé sur son plancher d'étagement, à tous les λ<1 — l'arrêt croisé ne peut pas se déclencher et le
warm start ne fera gagner aucun temps sur cette phase. **Le gain pourrait donc être franc sur LEO et
quasi nul sur GEO.**

Second effet à surveiller : un run démarré à l'optimum ne « descend » pas, donc ne compte pas pour le
consensus (`CONSENSUS_DESCENT_RATIO`, dont le javadoc mentionne explicitement ce cas). Avec
`CONSENSUS_MIN_RUNS` = 2 et trois autres runs qui descendent, le consensus doit continuer de se
former — à vérifier dans le log, car s'il cessait de se former, raffinement et tentatives
repartiraient et le temps augmenterait au lieu de baisser.

## 7. Rappel de dette

Indépendant de ce chantier, à traiter ensuite : **la règle d'arrêt du balayage ne peut pas voir un
gain sur un étage supérieur** (`02-sonde-de-jeu.md`, dernière section). Le gain se mesure sur la masse
des étages scalés, dominée par S1 ; S2 ne peut structurellement jamais peser 1 %.

> **Soldé.** Corrigé par la règle par coordonnée, mesurée le 2026-08-07 une fois ce chantier retiré —
> voir `02-sonde-de-jeu.md`. Elle n'était pas mesurable *avec* le warm start, pour la raison exposée
> en §8 ci-dessous.

---

## 8. Mesure — et pourquoi le mécanisme est révoqué

Runs de l'utilisateur sur `PropellantLoadOptimizerIntegrationTest`, les deux profils, contre la
référence « sonde de jeu seule » du 2026-08-06.

| | LEO base | LEO + warm start | GEO base | GEO + warm start |
|---|---|---|---|---|
| λ\* | [0,9125 ; 0,6628] | **[0,9144 ; 0,9034]** | [0,9563 ; 0,7922] | **identique** |
| masse reprise | −108 549 kg | **−105 765 kg** | −56 151 kg | **identique** |
| évals / passes | 24 / 2 | 18 / 2 | 18 / 2 | **identique** |
| temps mur | 19 min 45,5 | **26 min 22,6** | 4 min 37,5 | **4 min 37,8** |

Le critère d'acceptation posé en §4 — « vérifier que la masse récupérée ne **régresse** pas » —
n'est pas tenu : **−2 784 kg**. Et le temps, seul objectif du chantier, augmente de **33 %**.

### Le canal, nommé

Le mécanisme se déclenche partout (30 injections sur LEO, 17 sur GEO, aucune rejetée pour arité) et
la conjonction annoncée en §6 comme signature du gain est bien présente. Elle est aussi la cause de
la perte. À λ=[1,0 ; 0,825], avec le **même seed CMA-ES** (`-6657214946198437198`) dans les deux runs :

```
avec warm start                                    sans (référence)
Warm start: previous solution injected as run 2    Refinement cascade starting from cost=0.9689
Exploration 3/4: cost=0.0026804328979064852        Refinement 1/3 (sigma×0.1): cost=0.0013423
Target reached during exploration                  consumedΔv=280.5 m/s, excessΔv=95.8 m/s
consumedΔv=373.6 m/s, excessΔv=188.8 m/s           residual=366 kg (22.6%)  → feasible=TRUE
residual=0 kg (0.0%)     → feasible=FALSE
```

Le run 2 (0-indexé) affiché `Exploration 3/4` **est** la graine : c'est elle qui atteint la cible,
`crossRunStop` avorte les trois autres, et l'évaluation rend sa réponse en 10,5 s au lieu de 74,8 s.
Le gain de temps unitaire est donc réel — **−86 % sur cette évaluation**.

Mais `acceptableCost` est un seuil de **précision d'orbite**, et le terme propergol I7
(`W_PROPELLANT` = 0,005) ne pèse presque rien devant. Sortir plus tôt, c'est sortir sur la première
trajectoire qui vise juste, pas sur la moins gourmande : 373,6 m/s au lieu de 280,5. Le résidu tombe
à zéro, le balayage lit « infaisable » et **remonte** λ. Sur LEO, λ(S2) passe ainsi de 0,6828 à
0,9234, ce qui déplace tout le reste du balayage vers une région où *toutes* les évaluations sont
faisables — donc chères. La passe 1 passe de 11 min 43 à 13 min 20 alors même que le coût médian
d'une optimisation baisse (29,0 s → 25,7 s).

**Formulation générale, à retenir avant toute nouvelle tentative sur la passe 1 :** accélérer la
convergence de CMA-ES n'est neutre que si son critère d'arrêt est aligné sur le critère du balayage.
Ici il ne l'est pas — l'un mesure la précision d'orbite, l'autre la marge d'ergols. Tout raccourci
qui fait sortir plus tôt achète du temps en dégradant la solution retournée, et le balayage paie la
dégradation au centuple en remontant λ.

### Les deux réserves de la §6, tranchées

**Réserve 1 — « franc sur LEO, quasi nul sur GEO » : moitié confirmée, moitié infirmée, et le
mécanisme invoqué était faux.**

- GEO : confirmée, et plus fort que « quasi nul » — *exactement* nul. Temps CMA-ES cumulé 237,5 s
  contre 237,6 s, séquence de λ identique caractère pour caractère, coûts identiques à 10 chiffres
  significatifs (`0.06819343451879903` vs `...451877528`). Les quatre runs d'exploration convergeaient
  déjà au même point : la graine remplace un run qui atteignait le même optimum, elle n'apporte
  aucune information.
- **Le mécanisme énoncé est inexact.** Il disait : coût structurellement au-dessus d'`acceptableCost`,
  donc `crossRunStop` ne peut pas tirer. Or `Target reached during exploration` apparaît **10 fois
  sur 18** sur GEO (coûts 0,038 / 0,022 / 0,0156 / 0,0126… contre un plancher GT ≈ 0,048). La vraie
  raison est un partage net : les évaluations qui atteignent la cible durent déjà 0,4 à 6 s — rien à
  y gagner ; celles qui portent le temps mur (0,068 / 0,075 / 0,082 / 501,9) sont au-dessus du seuil
  et se terminent par consensus, pas par arrêt croisé. Les deux moitiés pointent dans le même sens,
  mais pas pour la raison écrite.
- LEO : **infirmée**. Le gain n'est pas franc, il est négatif. La spec avait raison sur la mécanique
  et tort sur le signe.

**Réserve 2 — le consensus : effet réel confirmé, danger écarté.** Le run démarré à l'optimum ne
compte effectivement pas comme descendu ; les lignes `Consensus: {n} independent explorations
descended` tombent de 4 à 3, parfois 2 :

| run | 2 runs | 3 runs | 4 runs |
|---|---|---|---|
| LEO base | — | — | 10 |
| LEO + warm start | — | 5 | 6 |
| GEO base | — | — | 8 |
| GEO + warm start | — | 1 | 7 |

Ligne qui tranche : `Consensus: 3 independent explorations descended` sur GEO là où la référence dit
`4`, à évaluation et coût final identiques. `CONSENSUS_MIN_RUNS` = 2 n'est jamais franchi et le
nombre de `Refinement cascade starting` ne bouge pas (1 / 1 / 0). Une seule `Retry 1/2 triggered`
apparaît, absente de la référence, sur l'évaluation la plus chère de tout le chantier (**532,7 s**,
contre un maximum de 160,3 s à la référence) — mais à un λ que la référence ne visite pas : effet
indirect du déplacement de λ\*, pas la graine prise en défaut.

### Dommage collatéral : la faisabilité cesse d'être reproductible

Le même point λ=[0,9144 ; 0,9234] rend un résidu de **0 kg** dans un run et de **397 kg** dans un
autre, avec le même code. Le piège déjà consigné (`01-abandon-cout-desespere.md` §7) — `crossRunStop`
dépend du temps mur — remontait jusqu'ici au niveau du *compteur d'évaluations* ; le warm start le
fait remonter jusqu'au **verdict de faisabilité**, parce que le run vainqueur change selon lequel
termine le premier. Vérifié après retrait : sans graine injectée, les 14 évaluations communes de deux
runs LEO sont identiques au kilogramme près. **Le déterminisme du balayage était une propriété de ce
qui existait ; ce chantier la détruisait.**

### Ce que ces mesures n'établissent pas

- Que le warm start ne pourrait pas fonctionner **si `crossRunStop` refusait une solution à résidu
  nul**. C'est la seule voie de sauvetage que les logs suggèrent, et elle porte sur le critère
  d'arrêt, pas sur la graine. Autre chantier.
- Le signe du gain unitaire en général : le seul point contrôlé (λ=[1,0 ; 0,825], 74,8 s → 10,5 s)
  fait un échantillon de un, et pour une solution moins bonne.
- Rien sur la reproductibilité des λ\* mesurés ici : aucune configuration n'a été lancée deux fois.
