# PHY-2 / L4 — Dimensionnement deux passes (`DT-19`) — conception

Conception du lot `L4` de `PHY-2`, découpé en
[`06-decoupage-PHY-2.md`](06-decoupage-PHY-2.md) §5. Le lot remplace la réserve
d'insertion universelle de 1 300 m/s portée par l'étage supérieur ([`DT-19`](../dette-technique.md#dt-19--réserve-dinsertion-universelle-sur-létage-supérieur))
par un dimensionnement qui **mesure**, en volant, le ΔV que l'insertion de *cette*
mission demande réellement. C'est le troisième lot de physique de `PHY-2`.

> **Statut : conception arrêtée, implémentation livrée et mesurée (2026-09-12).**
> Le lot est plus étroit et d'une autre nature que ne le laissait entendre le découpage
> (écrit le 2026-09-10), et deux de ses affirmations sont corrigées ici :
>
> - le deux-passes est un **mécanisme runtime**, au niveau du *planner*, pas une retouche
>   de `PropellantBudget` (qui ne vole pas, §2.1) ni un calibrage one-shot ;
> - le périmètre est **`MissionSpec.EarthOrbit` seul** (LEO direct + parking-chain
>   LEO/MEO), en FAST **et** BALANCED — GEO et lunaire gardent la réserve (§3.1) ;
> - **les gates ne re-baselinent pas** : ils dimensionnent et volent hors du planner, donc
>   ne voient jamais le deux-passes (§3.5). Le *« profils re-baselinés »* de la clôture du
>   découpage était **de trop** pour un lot runtime-only ; L4 se ferme sur de **nouveaux
>   tests planner**, une mesure et non un ré-enregistrement.
>
> **Ce que la mesure a tranché** (§5) : **9 412 → 1 275 kg, soit −86,4 %** sur le FH LEO-400, en
> **2 passes**, résidu à **7,5 %**, orbite déplacée de **4 m**, gates verts et inchangés. `DT-19`
> annonçait « +18 à +66 % » : le vrai facteur est **7,4**, et le `dvTop` de la chaîne idéale
> **sur-provisionne à lui seul de 2,2×** — la réserve n'en est que les trois quarts. Le coût du
> deux-passes est de **~5 s** (une passe FAST dure 5,4 s), donc le budget réduit est inutile.

---

## 1. Périmètre du lot

**Dans `L4`.**

- **le planner deux-passes** : un `MissionPlanner` qui dimensionne l'étage supérieur en
  volant (dimensionner → voler → mesurer le ΔV d'insertion → redimensionner), armé pour
  les missions `MissionSpec.EarthOrbit` en FAST/BALANCED ;
- **`sizeTopStage` prend le ΔV d'insertion en argument** à la place de la constante
  `TOP_STAGE_INSERTION_RESERVE_DV` ; la réserve **survit comme graine** de la première
  passe, pas comme valeur volée ;
- **la persistance des charges finales** dans les `MissionSolutions`, pour que le replay
  d'un scénario vole le véhicule dimensionné et non la graine sur-provisionnée ;
- **les nouveaux tests planner** qui ferment le lot par la mesure.

**Hors `L4`, et versé plus loin.**

- **GEO et lunaire** gardent la réserve : leur étage supérieur porte une injection
  (GTO, translunaire) de forme fermée connue, et l'amélioration de leur dimensionnement se
  fait **sans vol** — c'est un reliquat séparé, pas un deux-passes runtime (§3.1) ;
- **PRECISE** est intact : il dimensionne déjà en volant par balayage
  ([`MinimizedLoadPlanner`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/planner/MinimizedLoadPlanner.java)) ;
  seule **sa graine** bouge, du fait que la réserve n'est plus la valeur volée en FAST
  (mais elle reste la valeur analytique que la graine lit) ;
- **la bascule du défaut** drag-on et `REL-22` → `L5`.

---

## 2. État des lieux mesuré

### 2.1 La réserve est une constante analytique, et `PropellantBudget` ne peut pas voler

[`PropellantBudget`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/PropellantBudget.java)
est hors-vol par construction — *« runs before any propagation and never sees an arc »*,
constantes tirées d'Orekit et non des contextes (`:76-96`). La réserve
`TOP_STAGE_INSERTION_RESERVE_DV = 1_300` (`:55`) est ajoutée dans la boucle de point fixe
de `sizeTopStage` (`:474-476`) :

```
raw     = finalMass · (exp(dvTop / vₑ) − 1) · (1 + SAFETY_MARGIN)
reserve = finalMass · (exp(1300 / vₑ) − 1)
topLoad = min(raw + reserve, capacité)
```

Le « voler » de [`06` §3.5](06-decoupage-PHY-2.md) **ne peut donc pas vivre dans
`sizeTopStage`**. Il vit au-dessus, dans une orchestration qui vole ; `sizeTopStage`
reçoit un ΔV d'insertion **mesuré** en argument à la place de la constante.

### 2.2 Le coût de la réserve, et pourquoi il n'est plus fonctionnellement urgent

`DT-19` chiffre le surcoût : **+18 à +66 %** de charge d'étage supérieur, portée comme
ergol mort. Sur le Falcon Heavy LEO-400, l'étage supérieur ne s'allume pas pendant
l'ascension (le cœur fait 100 %), puis ne dépense que **~436 m/s** (transfert 430 + trim 6)
là où la réserve en provisionne **1 300** — le pire cas mesuré sur le cœur étranglé à la
plus petite charge qui ferme la mission.

> **Corrigé par la mesure de `L4` (§5).** Ce « +18 à +66 % » ne décrit pas ce profil : la réserve y
> gonfle le dimensionnement analytique de **+222 %**, et le sur-provisionnement total contre ce que
> l'étage brûle vaut **7,4×** (1 755 m/s provisionnés contre 272 délivrés). La fourchette est
> vraisemblablement une plage inter-profils tirée des cellules où l'étage sommet fait une vraie
> injection (MEO/GEO, ~2 400 m/s), qui diluent le cas LEO — non vérifié, faute d'avoir remesuré ces
> cellules.

Cadrage : la **casse** (le FH rendant 416 × 1271 km, [`BUG-25`](../bugs.md#bug-25--falcon-heavy-ne-vole-plus-en-transfert-optimisé-depuis-phy-8))
a été **réparée par `L3`** (autorité sur le cœur,
[`10` §3.1](10-conception-L3-PHY-2.md)). Le sur-provisionnement ne casse plus aucune
orbite. `L4` est donc un lot de **réalisme et de capacité juste**, pas une réparation :
- résidus de mission réalistes plutôt qu'un étage supérieur plein d'ergol mort ;
- **capacité honnête** sur un lanceur marginal, où le poids mort grignote directement ce
  que le lanceur peut porter — le gain devient fonctionnel là (une petite charge à la
  limite peut être rendue infaisable par la réserve) ;
- décorrélation de la calibration d'Isp : `DT-19` mesure que la dette d'Isp du FH bouge de
  **408 → 396 m/s** selon la réserve, le rapport de masses du premier étage changeant.

### 2.3 Le ΔV d'insertion est dépendant du vol, et c'est une question de degré

L'ascension (gravity turn) sur-délivre — c'est tout le sujet de `BUG-25`. La part
d'**injection** (GTO, transfert) est analytique et connue ; la **sur-délivrance de
l'ascension**, elle, est dépendante du vol pour **toutes** les familles. La différence est
de degré :

- **LEO direct** : l'ascension idéale livre droit à la cible, donc l'insertion est
  *entièrement* la part incertaine — le transfert + trim que l'étage supérieur fait pour
  rattraper une remise imparfaite. Aucune forme fermée. C'est le cas où voler apporte le
  plus, et le cœur du raisonnement de `DT-19` (*« le dimensionnement ne connaît pas l'état
  de remise des commandes »*) ;
- **parking-chain LEO/MEO, GEO, lunaire** : une injection connue **plus** une petite
  correction absorbée en orbite de parking. Le nombre juste y est calculable **sans vol**.

C'est ce qui délimite le périmètre : le deux-passes **runtime** ne vaut que là où le vol
est nécessaire à connaître le nombre — `MissionSpec.EarthOrbit` (§3.1). Il n'existe pas de
vol à ascension figée : toute mission terrestre optimise son gravity turn même en FAST
([`MissionComposer`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/operation/MissionComposer.java) `:266`),
donc « vol de mesure nominal » veut dire **vol à charges fixes** (`FixedLoadPlanner`), par
opposition au **balayage de charges** PRECISE, pas « ascension figée ».

### 2.4 La machinerie de vol et de mesure existe déjà

- [`MissionOptimizer.optimize()`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionOptimizer.java)
  produit un [`MissionPerformanceReport`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionPerformanceReport.java),
  qui se décrit lui-même comme *« instrument for calibrating the analytic propellant
  budget »* : `deltaV` par étape de mission
  ([`StagePerformance`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/StagePerformance.java)),
  et surtout le résidu **par étage physique** (`stagePropellants()`), qui est la vraie
  marge de l'étage dimensionné là où le ratio du stack entier ne dit rien.
- Le **replay** d'un vecteur connu (0 évaluation) existe (`MissionOptimizer` `:311-329`) ;
  la **recomposition** aux charges d'un candidat existe (`spec.withLauncherLoads(...)`,
  utilisée par [`ReplayPlanner`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/planner/ReplayPlanner.java)
  `:88-93` et `MinimizedLoadPlanner`). Le deux-passes **ne crée aucune machinerie de vol
  neuve** — il orchestre l'existant.

---

## 3. Décisions de conception

### 3.1 Périmètre : `MissionSpec.EarthOrbit`, la frontière propre au niveau du code

Le deux-passes runtime est armé pour les missions `MissionSpec.EarthOrbit` (LEO direct +
parking-chain LEO/MEO), en FAST et BALANCED. C'est **une seule frontière de type** :
`MissionSpec.Geo` (GEO) et `MissionSpec.Lunar` / `LunarOrbit` (lunaire) sont des types
distincts, exclus. Ce n'est pas une ligne arbitraire « direct + parking mais pas GEO » :
c'est là où le ΔV d'insertion n'est pas analytiquement connu (§2.3), et c'est le cas
d'usage des lanceurs légers vers lesquels le catalogue va (petites charges LEO).

GEO/lunaire gardent la réserve : leur over-provisioning est réel mais leur nombre juste est
un Hohmann fermé, améliorable **sans vol** — un reliquat séparé, à ne pas mêler ici (un
changement à la fois).

### 3.2 Ce qu'on mesure et comment on redimensionne

**La réserve est rétrogradée en graine de la première passe.** La passe 1 vole aux charges
analytiques actuelles (réserve comprise) : sur-provisionnée, mais elle **vole**, ce qui est
tout ce dont on a besoin pour mesurer. La charge *volée finale* vient de la mesure. C'est ce
qui résout le piège d'une graine réserve-nulle : sur un lanceur qui sur-délivre, `dvTop → 0`
donc l'étage supérieur serait dimensionné à ~0 et la passe 1 échouerait faute d'ergol pour
l'insertion.

**Grandeur mesurée : le ΔV d'insertion réellement délivré par l'étage supérieur** — la
somme des `deltaV` des étapes de mission propulsives volées par l'étage sommet physique
(transfert + trim en direct ; insertion parking + injection en parking-chain). Le choix du
**ΔV** plutôt que des kilos consommés est délibéré : le ΔV est mass-invariant, les kilos se
re-scaleraient d'une passe à l'autre quand la masse change.

**Redimensionnement.** `sizeTopStage` reçoit ce ΔV mesuré à la place de la constante :

```
topLoad = finalMass · (exp(ΔV_mesuré / vₑ) − 1) · (1 + SAFETY_MARGIN)
```

Le terme `reserve` disparaît de la valeur volée — le ΔV mesuré le subsume, car il *est* ce
que l'étage supérieur a réellement dû faire, sur-délivrance de l'ascension comprise. La
marge `SAFETY_MARGIN = 0,10` (`PropellantBudget:28`) reste, appliquée au ΔV mesuré.

### 3.3 Le critère d'arrêt : la bande de résidu, un détecteur de flame-out

L'arrêt se lit sur le résidu **propre** de l'étage dimensionné, contre **sa propre** charge
(`MissionLoadEvaluator.residualSufficient`, `:447-462`), pas sur le résidu du stack entier
(noyé par ce qui est au-dessus, typiquement un kick motor). Le plancher n'est **pas un
curseur** : mesuré sur le FH LEO (`MissionLoadEvaluator:78-88`, bilan 11 §3.4), le résidu de
l'étage dimensionné ne décroît pas continûment quand on resserre la charge — il tombe d'une
falaise (**10,3 %** de résidu à la bonne charge, coupure commandée ; **0 %** un pas plus bas,
déplétion ; objectif atteint des deux côtés). C'est un **détecteur binaire de flame-out** :
« l'étage a-t-il fini avec du rab, ou tiré jusqu'à la panne sèche ? »

Un étage correctement dimensionné sur le ΔV mesuré ·1,10 atterrit ~à la marge (≈ 10 % de
résidu). La bande :

- résidu **au-dessus** de la marge → sur-provisionné → redimensionne vers le bas, re-vole ;
- résidu dans la bande → **stop** ;
- résidu **sous le plancher** (flame-out) → sous-provisionné → redimensionne vers le haut,
  re-vole.

Ça unifie les deux « conditionnelles » : la passe 1 s'arrête seule si la graine était déjà
bonne (rare en direct, la réserve sur-provisionne → passe 2 quasi systématique) ; et on
**n'itère au-delà de la passe 2 que si le résidu reste hors bande** — le redimensionnement a
bougé la masse, donc l'ascension, donc le ΔV d'insertion (l'avertissement de `DT-19`). **Cap
≈ 3 itérations** ; `DT-19` parie 2, parfois 1, rarement 3. La borne haute de la bande et le
cap se posent **en volant** à l'implémentation.

### 3.4 L'orchestration : un planner qui réutilise `FixedLoadPlanner`, et le sort de BALANCED

Le deux-passes vit dans l'axe *load-handling* de
[`MissionPlanOptimizer`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/planner/MissionPlanOptimizer.java)
(`:89-98`) — au **calcul**, en tâche de fond (l'exécuteur `mission-optimizer`,
[`MissionOrchestratorAppState:196-209`](../../src/main/java/com/smousseur/orbitlab/states/mission/MissionOrchestratorAppState.java)),
jamais à la création (le wizard affiche la mission tout de suite). `planner()` choisit le
deux-passes quand : mode ∈ {FAST, BALANCED}, spec présent, `spec instanceof EarthOrbit` ;
sinon `FixedLoadPlanner` comme aujourd'hui ; PRECISE → `MinimizedLoadPlanner`. La boucle :

```
loads ← charges analytiques du spec (graine, réserve comprise)
répéter (cap ≈ 3) :
    mission ← MissionComposer.compose(spec.withLauncherLoads(loads), FAST)
    plan    ← new FixedLoadPlanner(mission, …).plan()      // 1 vol, GT ré-optimisé
    lire ΔV d'insertion + résidu étage sommet dans le rapport du plan
    si résidu dans la bande → renvoyer plan
    loads[sommet] ← sizeTopStage(ΔV mesuré)                // §3.2
renvoyer le dernier plan
```

Le dernier vol **est** le plan renvoyé — aucun vol gaspillé.

**On dimensionne toujours en FAST, puis le mode demandé vole une fois aux charges
obtenues.** Le dimensionnement est une propriété du **véhicule**, pas du mode de vol :
FAST et BALANCED volent le même véhicule, et le transfert optimisé de BALANCED ne dépense
pas plus que l'analytique pour la même cible. Donc BALANCED = *(boucle deux-passes en FAST)
+ (1 vol BALANCED final)* — il ne paie **jamais** deux vols de transfert CMA-ES. Hypothèse
assumée : *transfert optimisé ≤ transfert analytique en ΔV* — la marge de 10 % l'absorbe,
et un dépassement serait un signal.

**Replay différé.** La passe 2 **ré-optimise** le gravity turn (pas de replay). Le replay du
vecteur de la passe 1 aux charges redimensionnées est le moins sûr **précisément** sur les
lanceurs légers — l'étage supérieur y est une grosse fraction de la masse, donc le
redimensionnement bouge la masse assez pour que le vecteur rejoué laisse une remise
non-optimale et que le ΔV mesuré devienne faux. Or ce sont ces lanceurs qui déclenchent la
passe 2. Le replay-quand-c'est-sûr (changement de masse sous un seuil) est une optimisation
de coût à ajouter **plus tard** si le +1 vol fait mal, jamais sur l'identité du lanceur.

**Persistance.** Les charges finales sont enregistrées dans les `MissionSolutions`, sinon un
replay de scénario re-volerait la graine sur-provisionnée. `ReplayPlanner` applique déjà des
charges lanceur volées via le spec — on s'y branche, aucun format neuf.

### 3.5 Où mord la recalibration : le runtime seul, et les gates ne bougent pas

Fait mesuré qui contredit le découpage : les gates (les tests d'optimisation de mission)
dimensionnent en appelant `PropellantBudget.loadsForLeo` **directement**
([`LEOMissionOptimizedTransferTest:57-58`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/LEOMissionOptimizedTransferTest.java))
et volent via `new MissionOptimizer(...).optimize()` **directement**
([`AbstractTrajectoryOptimizerTest:93-94`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/AbstractTrajectoryOptimizerTest.java))
— aucun ne passe par le planner. Comme le deux-passes vit dans le planner (§3.4) et que
`PropellantBudget` garde sa réserve (§3.2), **aucun gate ne bouge**. Le *« profils
re-baselinés, décalage attribuable au dimensionnement seul »* de la clôture du découpage
présupposait que `L4` change ce que volent les profils analytiques ; un `L4` runtime-only ne
le fait pas.

`PropellantBudget` sert trois rôles ; le deux-passes n'en corrige qu'un :

1. **charges volées au runtime** (wizard → planner) — corrigé ;
2. **charges volées par les tests/gates** (appels directs) — contournent le planner,
   inchangées ;
3. **affichage wizard (dry composition) + graine PRECISE** — analytique, inchangé.

Conséquence assumée : le wizard **affiche** des charges avec réserve pendant que la mission
**vole** plus léger — un décalage affiché/volé déjà présent pour PRECISE (charges heuristiques
affichées vs balayées). `L4` l'accepte. Corriger l'affichage et la graine PRECISE (rôle 3)
et fermer GEO/lunaire (§3.1) sont des reliquats analytiques séparés.

`L4` se ferme donc par **de nouveaux tests planner** : une mesure (la charge sommet chute du
seed vers le ΔV mesuré, résidu en bande), pas un ré-enregistrement de baseline. Ces tests
sont peu sensibles à [`BUG-7`](../bugs.md) — ce sont des mesures, pas des épinglages `0.0`.

---

## 4. Sorties

- **Un `MissionPlanner` deux-passes**, réutilisant `FixedLoadPlanner`, branché dans
  `MissionPlanOptimizer.planner()` pour FAST/BALANCED + `EarthOrbit` + spec présent.
- **`sizeTopStage` prend le ΔV d'insertion en argument** ; la réserve reste le défaut/seed,
  documentée comme telle (valeur de départ de la passe 1, plus la valeur volée en runtime
  EarthOrbit).
- **Extraction du ΔV d'insertion** depuis le `MissionPerformanceReport` (Σ `deltaV` des
  étapes de l'étage sommet).
- **Dimensionnement toujours en FAST**, le mode demandé volant aux charges obtenues.
- **Persistance des charges finales** dans les `MissionSolutions` (replay du véhicule
  dimensionné).
- **Nouveaux tests planner** (mesure) qui ferment le lot.

---

## 5. Fermeture

Une **mesure**, pas un ré-enregistrement — la nature du lot (§3.5). **Volée le 2026-09-12 sur le
profil Falcon Heavy LEO-400 depuis Kourou** (`MeasuredLoadPlannerFlightTest`, vert), S2 à 4 000 kg à
sec et Isp 348 s, charge utile 10 000 kg.

**1 — La charge sommet chute, et bien plus que la fiche ne l'annonçait.**

| Terme | ΔV | Charge |
|---|---|---|
| `dvTop` idéal (marge incluse) | 593 m/s | 2 920 kg |
| `reserve` universelle | 1 300 m/s | 6 492 kg |
| **budgété** | **1 755 m/s** | **9 412 kg** |
| **délivré par le S2** | **272 m/s** | 1 179 kg consommés |
| **dimensionné par `L4`** | 297 m/s | **1 275 kg** |

Soit **−86,4 %**, un facteur 7,4. La décomposition tombe au kilo près (2 920 + 6 492 = 9 412), ce qui
la valide. Et **deux termes sur-provisionnent, pas un** : la réserve fait 74 % du gaspillage, mais le
`dvTop` de la chaîne idéale vaut **à lui seul 2,2× le besoin réel** — retirer la seule réserve aurait
laissé 2 920 kg là où 1 275 suffisent. `L4` fait donc strictement plus que la voie de sortie de
`DT-19` ne promettait.

**2 — Convergence en 2 passes**, le pari de `DT-19`. Le cap de 3 n'est pas atteint.

**3 — L'objectif reste atteint, à 4 mètres près.** Orbite osculatrice 400 108 × 419 339 m en passe 1,
**400 104 × 419 339 m** en passe 2 ; moyenne 409 686 → 409 682 × 409 915 m. Retirer 8 137 kg d'ergol
mort déplace l'orbite de 4 m : il était purement parasite.

**4 — Non-régression des gates : verts et inchangés**, ce qui **prouve** que le deux-passes est resté
dans son périmètre — ils dimensionnent et volent hors du planner (§3.5).

**Bornes validées, et ce sont des séparateurs, pas des curseurs.** Résidu de l'étage dimensionné :
**81,0 %** en passe 1 (→ redimensionne), **7,5 %** en passe 2 (dans la bande → arrêt). L'écart entre
les deux est si large que `RESIDUAL_BAND_UPPER_RATIO = 0,25` est quasi-binaire, exactement comme le
plancher de flame-out dont le javadoc dit déjà qu'il n'est pas un réglage.

**Le ΔV est mass-invariant, mesuré** — la justification de §3.2, confirmée. Le S2 délivre **271,6 m/s**
depuis 23 412 kg d'allumage et **274,1 m/s** depuis 15 275 kg : 0,9 % d'écart pour 35 % de masse en
moins, alors que les **kilos consommés chutent de 34 %** (1 791 → 1 179). Dimensionner sur un ΔV et
non sur des kilos était le bon choix, et la mesure le démontre plutôt que de l'argumenter.

**La marge de 10 % n'est pas du rembourrage : elle est consommée à 82 %.** Le dimensionnement sans
marge donnait 1 159,7 kg, la consommation réelle a été 1 179 kg — sans elle, **flame-out à 20 kg
près**. Elle ne doit pas être resserrée à cette échelle.

**Le coût est négligeable, et l'arbitrage de conception le surestimait.** Une passe FAST dure
**5,4 s** ; le deux-passes coûte donc **~5 secondes** de plus qu'un vol simple, pas un doublement
douloureux. La question « faut-il descendre les passes de dimensionnement au budget réduit de 8 000
évaluations ? » est tranchée : **non, inutile**.

---

## 6. Ordonnancement

**Ordre interne de `L4`** : écrire le planner deux-passes (réutilise `FixedLoadPlanner`) →
le brancher dans `MissionPlanOptimizer` → extraction du ΔV d'insertion →
`PropellantBudget.loadsForMeasuredTopStage`, sœur de `sizeTopStage` (§7.3) → persistance des charges finales → nouveaux tests planner →
régler bande de résidu + cap **en volant**.

**Place dans le chantier.** `L4` dépend de `L3` : le cœur devenu commandable fait que
l'étage supérieur s'allume enfin, donc le dimensionnement se fait contre l'ascension
corrigée. Il **lègue à `L5`** proprement — quand le défaut bascule drag-on, le vol de mesure
mesure le ΔV d'insertion **traînée comprise**, donc le dimensionnement drag-on est gratuit,
sans mécanisme neuf ; l'arrêt d'altitude drag-conditionnel de `L1` reste dans l'ascension
optimisée à chaque passe.

**Dette.** `DT-19` **fermée** pour le runtime `EarthOrbit` ; la réserve survit comme graine ;
**GEO/lunaire la paient encore** (reliquat séparé, sur-provisionnement analytiquement connu), comme
l'affichage wizard et la graine PRECISE (rôle 3, §3.5). Et la mesure **ouvre**
[`DT-22`](../dette-technique.md#dt-22--les-étages-bas-volent-pleins-et-le-cœur-en-largue-24-t) : en
allégeant le sommet de 8 137 kg, le gaspillage s'est **déplacé vers le cœur**, qui largue désormais
24 448 kg non brûlés (5,9 % de sa charge) — trois fois ce que `L4` vient de retirer. Le javadoc de
`PropellantBudget` justifiait « les étages bas volent pleins » par *« the gravity turn consumes them
entirely anyway »* : faux depuis que `L3` a rendu la coupure du cœur commandable, et corrigé.

**Risques.**

- **sous-provisionnement** : réel, et mesuré de près — la marge de 10 % est consommée à **82 %**
  (§5), donc elle ne doit pas être resserrée ; si le resize sous-provisionne, la bande de résidu
  itère vers le haut et le fallback est le dernier vol faisable ;
- **non-convergence** (masse ↔ ascension ↔ ΔV) → cap ~3 + fallback ;
- **coût** : mesuré à **~5 s** par mission (une passe FAST dure 5,4 s), en tâche de fond — le
  doublement douloureux qu'on redoutait n'existe pas ;
- **hypothèse** transfert optimisé ≤ analytique en ΔV (§3.4) — marge absorbe, sinon signal ;
- les tests d'optimisation et de mission sont lents et c'est l'utilisateur qui les lance.

---

## 7. Corrections consignées

Cinq affirmations posées en conversation ou dans le découpage, corrigées ici :

1. **« L'insertion GEO/parking-chain est analytiquement connue, LEO non »** était une
   dichotomie trop tranchée. La part d'injection est analytique partout ; la sur-délivrance
   de l'ascension est dépendante du vol partout. La différence est de **degré** (§2.3) —
   dominante en LEO direct, correction sur une injection connue ailleurs. Le périmètre tient
   sur le degré et la frontière de type `EarthOrbit`, pas sur la dichotomie.
2. **Le *« profils re-baselinés »* de la clôture du découpage** était de trop : les gates
   contournent le planner (§3.5), un `L4` runtime-only ne les touche pas. `L4` se ferme sur
   une mesure via de nouveaux tests planner, pas sur un ré-enregistrement de baseline.
3. **`sizeTopStage` n'a pas été paramétré**, contrairement à ce qu'annonçaient §1 et §4.
   L'implémentation a ajouté une **méthode sœur**, `loadsForMeasuredTopStage` : le ΔV mesuré
   **subsume** le reste-de-chaîne *et* la réserve au lieu de remplacer un terme à l'intérieur du
   point fixe, et une fois ce ΔV connu il n'y a plus rien à itérer. La réserve reste le défaut de
   `sizeTopStage`, intact pour GEO, lunaire et tout appelant hors-vol.
4. **Le ΔV mesuré est le ΔV *total* délivré par l'étage sommet, pas « l'insertion » seule.** Sur un
   Falcon Heavy l'étage haut s'allume aussi pendant l'ascension (burn 2) ; une charge qui l'omettrait
   le dimensionnerait court. La formule de §3.2 — Σ des `deltaV` des étapes volées par l'étage
   sommet — l'impliquait déjà : c'est le *nom* « ΔV d'insertion » qui était approximatif.
5. **Le coût a été surestimé pendant la séance.** L'arbitrage de §3.4 — replay différé, passe 2
   conditionnelle — a été construit sur l'idée de vols chers, et la mesure donne **5,4 s** par passe
   FAST. Le mécanisme retenu reste le bon, mais la raison invoquée pour l'économiser pesait beaucoup
   moins qu'on ne le croyait. À l'inverse, **la borne haute de la bande de résidu et le cap n'ont pas
   eu à être réglés** : 81 % contre 7,5 % est un écart si large que la borne est un séparateur
   quasi-binaire, et la convergence tient en 2 passes comme `DT-19` le pariait.

---

*Document rédigé le 2026-09-12 (séance de conception en conversation), complété le même jour des
mesures du vol de clôture.*
