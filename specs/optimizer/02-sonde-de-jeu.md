# 02 — Sonde de jeu : les passes de raffinement sondent avant de bissecter

**Date** : 2026-08-06, mesures complétées le 2026-08-07
**Statut** : **implémenté, mesuré, conservé** — sonde de jeu et règle d'arrêt par coordonnée sont
toutes deux en production et mesurées isolément sur les deux profils.
**Périmètre** : `MultiStageLoadOptimizer`

---

## 1. Mesure de départ

Relevée dans les logs des deux runs d'intégration du 2026-08-06, sans lancer de calcul
supplémentaire — `MultiStageLoadOptimizer` journalise déjà le λ et la masse en fin de chaque passe.

| | passe 1 | passe 2 | part de la passe 2 |
|---|---|---|---|
| LEO | 10 min 57, 14 évals, **−108 510 kg** | 4 min 20, 12 évals, **0 kg** | **26 %** |
| GEO | 4 min 03, 14 évals, **−56 151 kg** | 2 min 10, 13 évals, **0 kg** | **34 %** |

Les quatre bissections de passe 2 sont revenues sur leur point de départ à la décimale près :
LEO `0.6828 → 0.6828` et `0.9125 → 0.9125`, GEO `0.7922 → 0.7922` et `0.9563 → 0.9563`.

**Le coût en ergols d'une réduction de passes est donc nul sur les deux profils.** L'arbitrage
temps/ergols envisagé n'existe pas ici : c'est du temps mort.

## 2. Décision

Plutôt que de ramener `maxPasses` à 1 — ce qui supprimerait la détection du couplage entre étages,
seule raison d'être des passes de raffinement — **sonder l'extrémité avant de rouvrir un bracket**,
à partir de la passe 2.

Avant de re-bissecter une coordonnée sur `[λmin, λ_courant]`, évaluer `λ_courant − tolerance` :

- **sonde infaisable** → le λ minimal faisable est dans `(λ_courant − tolerance, λ_courant]`, un
  intervalle dont la largeur *est* le critère de convergence de la bissection. Celle-ci aurait donc
  renvoyé `λ_courant`. La coordonnée est terminée, **une évaluation au lieu de six ou sept** ;
- **sonde faisable** → le couplage a ouvert du jeu réel ; la bissection se déroule normalement, **en
  repartant de la sonde**, qui est déjà un point faisable un cran plus bas. L'évaluation n'est pas
  perdue.

Cas limite : si `λ_courant − tolerance < λmin`, le bracket est déjà plus étroit que la tolérance —
coordonnée terminée sans dépenser la moindre évaluation.

**La passe 1 est exclue.** C'est elle qui décide λ\*, et sa suite de λ évalués reste inchangée, si
bien que les références LEO/GEO enregistrées restent comparables. λ\* ne peut donc bouger que si une
passe de raffinement trouve quelque chose — et sur les deux profils mesurés, elle ne trouve rien.

## 3. Ce que ça ne garantit pas

L'équivalence « sonde infaisable ⟹ même réponse que la bissection » suppose la **monotonie de la
faisabilité en λ**, que le javadoc de `PropellantLoadOptimizer.minimize` qualifie explicitement
d'approximative : l'optimiseur interne optimise la précision d'orbite, pas l'économie d'ergols, donc
le résidu à un λ donné est stochastique. Sous non-monotonie, une bissection complète pourrait tomber
sur un point faisable plus bas que la sonde ne verrait pas.

L'économie est donc gratuite **sous l'hypothèse que l'algorithme fait déjà partout ailleurs**, pas
dans l'absolu. C'est un choix assumé, pas un oubli.

## 4. Tests

Ajoutés à `MultiStageLoadOptimizerTest`, verts :

1. **`refinementPass_withoutSlack_costsOneProbeInsteadOfAFullBisection`** — sur une frontière
   séparable, la passe de raffinement ne trouve rien par construction. Assertions : aucun appel ne
   rouvre un bracket à λmin avec l'autre coordonnée à sa valeur finale (signature d'une
   re-bissection complète) ; un appel exactement à `λ_final − tolerance` existe (la sonde) ; et λ\*
   atteint toujours les deux seuils à la tolérance près.
2. **`refinementPass_withSlack_stillBisectsAndReclaimsIt`** — nouvel évaluateur `CoupledEvaluator`,
   faisable ssi `λ0 ≥ 0,55` et `λ1 ≥ 0,78 − 0,5·(1 − λ0)`. La passe 1 fixe λ1 à 0,78 alors que λ0
   vaut encore 1 ; une fois λ0 descendu à 0,55 la contrainte sur λ1 se relâche à 0,555, et la passe
   de raffinement doit aller la chercher. **Ce test passait déjà avant le changement** : c'est le
   contrôle qui vérifie que la sonde est un court-circuit et non un plafond.

La non-régression de la passe 1 est déjà couverte par le test existant
`sweepStartsFromTheTopStage`, qui affirme que le deuxième appel de la séquence ouvre le bracket à
λmin : si la sonde tirait en passe 1, cet appel serait à `1 − tolerance`.

## 5. Gain attendu et validation

Estimation à partir des durées par évaluation relevées en passe 2 (LEO ~21,7 s, GEO ~10 s) :

| profil | passe 2 aujourd'hui | passe 2 attendue | gain sur le run |
|---|---|---|---|
| LEO | 4 min 20 (12 évals) | ~45 s (2 évals) | **−22 %** |
| GEO | 2 min 10 (13 évals) | ~20 s (2 évals) | **−29 %** |

### Résultat mesuré (2026-08-06)

**Les deux profils se comportent différemment, et le second est le plus instructif.**

**GEO — exactement le comportement visé.** λ\* = [0,95625 ; 0,7921875], **identique à la
référence**. Les deux sondes de passe 2 sont infaisables, une évaluation chacune : passe 2 en
**12 s au lieu de 2 min 10**, 2 évals au lieu de 13.

**LEO — la sonde a trouvé du jeu que la bissection manquait.** La sonde de passe 2 sur S2 était
*faisable* ; la bissection s'est déroulée et a rendu λ(S2) = 0,6628 au lieu de 0,6828.

| | run précédent | avec la sonde |
|---|---|---|
| λ\* | [0,9125 ; 0,6828] | [0,9125 ; **0,6628**] |
| charge S2 | 1 340 kg | **1 301 kg** |
| récupéré | −108 510 kg | **−108 549 kg** |
| évals | 28 | 24 |
| résidu S2 | 49 kg (3,6 %) | 57 kg (4,4 %) |

**Mécanisme, et il n'est pas flatteur pour l'algorithme.** La bissection sur `[0,3 ; 0,6828]` visite
des milieux — 0,4914, 0,5871, 0,6350, 0,6589, 0,6709 — et **ne teste jamais 0,6628**. La sonde teste
exactement `λ − tolerance`, un point que le calendrier de la bissection ne visite pas. Qu'il soit
faisable alors que ses voisins de part et d'autre ne le sont pas est une manifestation directe de la
**non-monotonie** documentée en §3. Les 39 kg sont réels, mais relèvent du tirage, pas d'une
supériorité systématique de la sonde.

**Conséquence sur le temps : la passe 2 LEO est devenue productive, donc plus longue** — 9 min 06
contre 6 min 44 sur la portion comparable (fin de la première bissection → fin du run). Sur LEO la
sonde n'a pas fait gagner de temps, elle a acheté 39 kg.

**Règle générale que ce run établit : le temps mur suit le nombre d'évaluations _faisables_, pas le
nombre d'évaluations.** Les 6 évals de cette bissection ont coûté 64 s pièce, contre 13 s pour les 6
évals de l'ancienne passe 2, qui échouaient vite sur les gardes analytiques.

**La référence LEO est périmée** : λ(S2) 0,6828 → 0,6628, S2 1 301 kg.

### Défaut révélé par ce run : la règle d'arrêt ne peut pas voir un gain sur un étage supérieur

La passe 2 a récupéré **3 % de la charge de S2**, et `minPassGain` a mesuré ce gain à **0,0035 %**,
donc très en dessous du plancher de 1 % — d'où l'arrêt.

Parce que le gain se mesure sur la masse des étages scalés, soit 1,126 M kg dont S1 fait 1,125 M.
**S2, avec ses 1 301 kg, ne peut structurellement jamais peser 1 % de ce total.** C'est exactement la
domination que le javadoc de la classe invoque pour écarter un λ global — elle est revenue par la
porte de la règle d'arrêt. Aucun gain sur un étage supérieur, quelle que soit son ampleur relative,
ne peut déclencher une passe supplémentaire.

Il est donc possible qu'une passe 3 aurait encore trouvé quelque chose sur S2. La règle a coupé
avant.

**Correctif — IMPLÉMENTÉ le 2026-08-06.** Le gain est désormais lu **par coordonnée, rapporté à la
charge de cette coordonnée** : la passe suivante est lancée dès qu'*une* coordonnée a repris au moins
`minPassGain` de sa propre charge. Comme la charge d'une coordonnée vaut `λ·heuristique`, son gain
relatif se réduit au rapport des λ et les charges s'éliminent — le critère ne dépend d'aucune masse.
Même raisonnement que celui qui fait rejeter un λ global, appliqué à la règle d'arrêt.

Vérification sur les données réelles : la passe 2 LEO passe λ(S2) de 0,6828 à 0,6628, soit **2,93 %**
de sa propre charge — au-dessus du plancher de 1 %, donc une passe 3 aurait été lancée. GEO, dont les
deux coordonnées n'ont pas bougé, s'arrête à la passe 2 exactement comme avant.

Deux tests dans `MultiStageLoadOptimizerTest` :
`passGain_isMeasuredPerCoordinate_notOnTheStackDominatingStage` (charges 1 000 000 / 1 000 comme le
vrai déséquilibre S1/S2, évaluateur couplé : la coordonnée haute reprend ~29 % de sa charge, ~0,04 %
du total — l'ancienne règle s'arrêtait, la nouvelle enchaîne) et
`passGain_stillStopsWhenNoCoordinateMoves` (le plancher n'est pas désactivé pour autant).

Le journal expose maintenant les deux lectures : la masse reprise sur la passe, puis
« Stage {i} reclaimed {x}% of its own load this pass » ou le motif d'arrêt.

**Conséquence à attendre à la prochaine mesure LEO** : le balayage ira jusqu'à la passe 3 et λ\*
descendra probablement encore un peu sur S2. Le coût est faible grâce à la sonde — une passe
improductive ne vaut plus qu'une évaluation par coordonnée.

### Mesure de la règle par coordonnée, isolée du warm start (2026-08-07)

Première mesure exploitable de ce correctif. Les runs du 2026-08-06 qui l'embarquaient portaient
aussi le warm start λ→λ (`03-warm-start-lambda.md`), lequel déplaçait λ(S2) à 0,9034 et supprimait le
cas d'usage : dans ces runs, **tous** les gains tombaient sur S1, si bien que l'ancienne lecture et la
nouvelle donnaient la même décision à chaque frontière de passe. Le mécanisme s'exécutait sans jamais
trancher. Après retrait du warm start, run LEO du 2026-08-07 (`leo-stop_rule.log`) :

| | base (sonde seule, ancienne règle) | sonde + règle par coordonnée |
|---|---|---|
| λ\* | [0,9125 ; 0,6628125] | **identique** |
| masse reprise | −108 549 kg | **identique** |
| S2 / résidu | 1 301 kg / 57 kg (4,4 %) | **identique** |
| orbite finale | a=6788,5 km, e=0,00154, i=5,24° | **identique** |
| évals / passes | 24 / 2 | **26 / 3** |
| temps mur | 19 min 45,5 | **20 min 26,3** (+3,4 %) |

**Les 14 évaluations communes sont identiques au kilogramme près**, mêmes verdicts, même ordre. La
règle se déclenche exactement là où la vérification sur données réelles l'annonçait :

```
Pass 2 done: scaled-stage mass 1126453 → 1126414 kg (39 reclaimed), λ=[0.9125, 0.6628]
Stage 1 reclaimed 2.93% of its own load this pass (floor 1.0%) — sweeping again
```

2,93 % contre les 0,0035 % qu'aurait lus l'ancienne règle. C'est la première décision du chantier où
les deux lectures divergent.

**La passe 3 n'a rien trouvé**, et c'est un résultat en soi : les deux sondes ressortent infaisables,
λ(S2)=0,6428 (résidu 0 kg sur 1 262 kg) et λ(S1)=0,8925. **Le 0,6628 est le plancher à la résolution
de la bissection** — la prédiction « λ\* descendra probablement encore un peu sur S2 » est
**infirmée**, et le gain non-monotone de 39 kg décrit plus haut était un coup unique, sans jeu
résiduel en dessous.

**Coût mesuré du correctif : la passe 3 entière, soit 37,5 s, 2 évaluations, 0 kg** — 3,2 % du run.
Les phases communes totalisent 19 min 48,8 contre 19 min 45,5, soit +0,28 % : le surcoût est
*uniquement* la passe supplémentaire, rien d'autre n'a bougé. Sans la sonde de jeu, cette même passe
aurait coûté deux bissections complètes, ~12 évaluations et 6 à 10 minutes pour le même zéro : **les
deux mécanismes de ce document se paient l'un l'autre.**

### Ce que la règle est réellement, et ce qu'elle coûte réellement

Trois corrections à la lecture immédiate qu'on peut faire du run ci-dessus.

**Ce n'est pas un détecteur de couplage, c'est une passe de confirmation.** Le critère lit « S2 a
bougé de 2,93 % à la passe 2 », pas « S2 peut encore bouger ». C'est un indicateur *retardé* : il se
déclenchera après quasiment toute passe productive, quelle que soit la suite. En pratique la règle ne
détecte donc rien, elle **ajoute une passe de vérification** — elle transforme « on s'arrête quand
une passe n'a rien donné » en « on vérifie que λ\* est un point fixe du balayage à la résolution de
la bissection ». Le résultat de ce run n'est pas « la règle n'a rien trouvé » mais « λ\* =
[0,9125 ; 0,6628] est désormais un point fixe vérifié », ce qu'il n'était pas auparavant.

**Le coût n'est pas borné à 37 s.** Ce run a eu de la chance : ses deux sondes sont ressorties
infaisables, d'où 2 évaluations. Une sonde ressortie *faisable* rouvre une bissection complète — le
run du 2026-08-06 qui portait aussi le warm start l'a montré, sa passe 3 a coûté **9 min 36** pour
les mêmes deux coordonnées. Le vrai plafond est `maxPasses` = 3, pas la sonde : la règle ajoute au
plus une passe sur ce profil, et cette passe vaut **entre 40 s et 10 minutes selon le tirage**.

**Une sonde sur deux était redondante.** La sonde étage 0 de la passe 3 porte sur λ=[0,8925 ; 0,6628]
— exactement le vecteur déjà évalué par la sonde étage 0 de la passe 2, la sonde étage 1 de la
passe 3 étant ressortie infaisable et n'ayant donc rien déplacé entre les deux. 6,2 s à recalculer
une mission complète pour retrouver un verdict écrit 37 s plus tôt dans le même journal.

> Correctif identifié, **non implémenté** : un `Set` des vecteurs λ connus infaisables, consulté
> avant de dépenser une sonde. Légitime — `MissionLoadEvaluator.evaluate` reconstruit la mission
> depuis les seuls `lambdas` avec un `seed` fixe et n'utilise pas son argument `previous`, donc
> l'évaluation est une fonction pure de λ ; le run ci-dessus le confirme empiriquement. Ne **pas**
> mettre en cache l'`Evaluation` elle-même : elle porte un `MissionComputeResult`, donc une
> éphéméride de ~94 000 points, et un balayage en compte jusqu'à 45. Gain : 0,5 % du run — du
> rangement, pas de l'optimisation. Son intérêt réel est de rendre la passe de confirmation
> proportionnelle aux coordonnées qui *pouvaient* avoir bougé plutôt qu'au nombre d'étages scalés.

Décision : **conservé**, mais pour la raison de fond et non pour son rendement. L'ancienne règle
rapportait le gain à un total dominé à 99,9 % par S1 : c'est un ratio dont le numérateur et le
dénominateur ne parlent pas de la même chose, pas un seuil mal réglé. Remplacer une métrique cassée
par une métrique correcte se justifie indépendamment de ce que ça rapporte le jour de la mesure.

Ce que la mesure n'établit pas : qu'elle rapporte des ergols un jour — sur ce profil elle en achète
zéro. **Ce qui ferait rouvrir le dossier** : un lanceur à plus de deux étages scalés, où une passe de
confirmation coûte N sondes et où une seule ressortie faisable rouvre une bissection. La question
redeviendrait alors un arbitrage temps/ergols explicite, et non un correctif de métrique.

GEO reste à 2 passes, `Best per-coordinate gain 0.00% below the 1.0% floor`, λ\*, évals et temps
inchangés — non-régression confirmée. GEO n'exerce jamais la règle : aucune de ses passes de
raffinement n'a jamais rien repris.

### Plancher de bruit, mesuré

À trajectoire de λ identique, deux runs LEO diffèrent de **±5 à 7 % par phase mais de 0,3 % sur le
run complet** — les écarts se compensent. Tout écart de phase sous ~7 %, ou de run sous ~1 %, n'est
pas interprétable.

Et un résultat non prévu : **la non-reproductibilité de la faisabilité observée dans les runs du
warm start venait du warm start** (même λ=[0,9144 ; 0,9234] rendu à 0 kg de résidu puis à 397 kg).
Sans lui, `crossRunStop` ne fait plus basculer le run vainqueur et le balayage est déterministe. Le
piège consigné en `01-abandon-cout-desespere.md` §7 reste réel sur les *compteurs d'évaluations* ;
il ne contamine pas les *verdicts de faisabilité* tant qu'aucune graine n'est injectée.

## 6. Suite

Le gisement principal reste la **passe 1** : 14 évaluations à ~47 s pièce sur LEO, contre ~22 s en
passe 2. C'est l'exploration depuis λ=1 vers le bas qui coûte.

**Le warm start λ→λ était la réponse prévue à ce gisement ; il a été mesuré et révoqué** (voir
`03-warm-start-lambda.md`) : il réduit bien le coût d'une évaluation faisable, mais en faisant sortir
CMA-ES plus tôt sur une solution à Δv excédentaire que le balayage lit comme infaisable. La passe 1
reste donc ouverte, et toute nouvelle tentative devra expliquer d'abord **pourquoi elle ne dégrade
pas la qualité de la solution retournée** — c'est là que le chantier précédent est mort, pas sur le
temps.
