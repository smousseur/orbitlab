# PHY-2 / L3 — Autorité d'ascension (`DT-20` + `DT-21`) = fix `BUG-25` — conception

Conception du lot `L3` de `PHY-2`, découpé en
[`06-decoupage-PHY-2.md`](06-decoupage-PHY-2.md) §5. Le lot rend à l'optimiseur la
commande de la coupure du cœur, ce qui **répare** le Falcon Heavy en transfert optimisé
([`BUG-25`](../bugs.md#bug-25--falcon-heavy-ne-vole-plus-en-transfert-optimisé-depuis-phy-8)).
C'est le deuxième lot de physique de `PHY-2`, et le plus lourd : il porte plus que son
intitulé.

> **Statut : conception + implémentation livrées et vérifiées (2026-09-11).** `L3` est plus
> large que ne le disait le découpage (écrit le 2026-09-10) : `L1` et `L2` lui ont **versé**
> trois choses de plus — l'implémentation de l'Isp FH et la re-baseline (repliées de `L2`
> [`09` §6](09-conception-L2-PHY-2.md)), la décision du candidat `c` (reportée de `L1`,
> [`DT-14` amendé](../dette-technique.md#dt-14--écart-harris-priester--nrlmsise-00-non-arbitré)),
> et `DT-15`. Le découpage sous-comptait donc le lot ; ce document le reconstitue.
>
> **Ce que la mesure a tranché** (§5) : `BUG-25` **fermé par le seul mécanisme, `W = 0,5`
> inchangé** — l'optimiseur va en région B tout seul et coupe le cœur (le poids n'a pas eu à
> monter, §3.2) ; **B-check confirmé** (FH drag-on à 298 → 400,3 × 419,2 km, capacité
> préservée) ; **`DT-15` : pas d'escalade** — le S2 devient actif en continu (~35 km) mais le
> B-check passe avec `Cd = 2,2` en place, donc on l'assume (§3.4).

---

## 1. Périmètre du lot

**Dans `L3`.**

- **l'autorité sur le cœur** (`DT-20`) : la coupure du corps central câblée sur
  `transitionTime`, la barrière d'étagement abaissée, sans variable neuve ;
- **le rééquilibrage de `W_APOGEE_OVERSHOOT`** (`DT-21`), posé en volant, qui fait *choisir*
  la coupure ;
- **la pose de l'Isp FH** (implémentation de `DT-13`, repliée de `L2`) : `296 → ~298` au
  catalogue, et la re-baseline FH ;
- **le B-check drag-on** (preuve de la décision `B` de `L2`), enfin possible une fois le
  hand-off redressé ;
- **`DT-15`** : l'altitude d'airstart S2 lue sur l'ascension reprovisionnée ;
- **la ré-activation de `testFalconHeavyOptimizedTransfer`** (`@Disabled` levé) qui **ferme
  `BUG-25`**.

**Hors `L3`, et versé plus loin.**

- **le candidat `c`** — substitution NRLMSISE→HP au transfert — **reporté à `L5`** (§3.4) :
  pure optimisation de coût compute, sans enjeu de justesse tant que le drag-on n'est pas
  le défaut ;
- le dimensionnement en deux passes (`DT-19`, `L4`), la bascule du défaut et la
  matérialisation de la baisse Ariane (`L5`).

---

## 2. État des lieux mesuré

### 2.1 L'ascension n'a aucune prise sur son cœur, et une barrière en interdit la région utile

Dans [`GravityTurnManeuver.plan()`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/GravityTurnManeuver.java) des
trois durées du plan, une seule dépend d'une variable — `burn2` :

```
burn1Duration    = getBurn1Duration()      // boosters jusqu'à extinction
coreBurnDuration = getCoreBurnDuration()   // cœur jusqu'à extinction — FIXE
burn2Duration    = max(0, transitionTime − stagingCompleteTime)
```

Le cœur du FH est déclaré `ShutdownMode.COMMANDED` au catalogue, mais le code le brûle
**toujours** jusqu'au plancher de déplétion (`DT-20`). Et
[`GravityTurnProblem`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/GravityTurnProblem.java)
ajoute `STAGING_PENALTY_BASE = 1e3` dès que `transitionTime < stagingCompleteTime` (coût
nominal ~73), rendant la région **inatteignable**. Or `DT-20` y a mesuré la solution :
`transitionTime = 170` (sous `stagingCompleteTime = 181,8`) avec exposant `0,634` rend un
apogée de **407,9 km**, la cible — contre 4 596 km à l'optimum retenu.

### 2.2 La barrière n'est plus une garde, c'est un régularisateur — et c'est ce qui change

Le Javadoc de `STAGING_PENALTY_BASE`
([`GravityTurnProblem:60-87`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/GravityTurnProblem.java))
est explicite : **la garde d'étagement d'origine a disparu** (le largage est une phase à
lui, plus un `DateDetector` planté dans le burn). Ce qui reste est un **régularisateur de
recherche** contre un plateau dégénéré, et l'« étape 5 » l'a chiffré : le retirer coûte
**+47 % d'évaluations, +57 % de wall-clock**, et la solution retenue cesse d'être
reproductible au seed fixe.

Ce plateau existe **parce que** `transitionTime` ne commande rien sous `stagingCompleteTime`
aujourd'hui (le cœur brûle à extinction quoi qu'il arrive). Le câblage de `L3` **dissout ce
plateau** dans la région ouverte — d'où §3.1.3.

### 2.3 `W_APOGEE_OVERSHOOT = 0,5` est hors de son domaine, et pour une raison que `L3` retourne

Le poids d'un apogée au-dessus de la fenêtre vaut `0,5` contre `8,0` en dessous. Le Javadoc
([`GravityTurnProblem:98-114`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/GravityTurnProblem.java))
documente pourquoi il a été **baissé** de 3,0 : quand le premier étage sur-délivre, *« the
gravity turn cannot lower its apogee by thrusting less — its only lever is to pitch up »*,
fausse économie que 3,0 achetait (remise 148 km sous la cible). Cette prémisse — *« pas de
levier pour baisser l'apogée sauf cabrer »* — est **exactement ce que §3.1 détruit**.

### 2.4 La casse `BUG-25`, mesurée, exige les trois gestes ensemble

Vol réel du 2026-09-10 : `testFalconHeavyOptimizedTransfer` rend **416 × 1271 km** pour une
cible 400 circulaire (apogée 3× trop haut). L'ascension sur-délivre (~1 100 m/s de trop),
le transfert prograde ne rabaisse pas l'apogée. La clôture PHY-8 a mesuré que **ni le cœur
commandable ni le poids ne suffisent seuls** : cœur commandable sans barrière abaissée ne
change rien ; barrière abaissée sans rééquilibrage **n'insère pas** (`72 551 × 400 178 m`,
étage supérieur vide — coupe trop tôt, sous-délivre). Les trois gestes forment l'insertion.

---

## 3. Décisions de conception

### 3.1 Autorité sur le cœur (`DT-20`) : trois gestes liés, zéro machinerie neuve

**3.1.1 — Câbler la coupure sur `transitionTime`, foyer `plan()`.** Un seul geste :

```
coreBurnDuration = min(getCoreBurnDuration(), transitionTime − coreIgnitionOffset)
```

Le cap se propage seul : `coreBurnDuration` raccourci →
[`AscentPlan.coreJettisonDate()`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/ascent/AscentPlan.java)
avancé → la phase
[`GravityTurnCoreBurnStage`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/ascent/GravityTurnCoreBurnStage.java)
(dont `endDate = coreJettisonDate`) coupe là. **Aucun détecteur neuf, aucun étage neuf** —
la coupure est le cap d'une durée dans le seul endroit où les dates sont calculées, sens
littéral du découpage §3.4 (« la prise existe déjà dans la variable »). Le
`DepletionStopTrigger` de la phase reste : il ne tire simplement plus (la propagation
s'arrête avant le plancher), ce qui est sa fonction de garde.

**3.1.2 — La comptabilité de masse est indépendante de la coupure.** Le largage tombe sur
`massAfterJettison()` (masse de référence de la pile **au-dessus** du cœur), que le cœur ait
brûlé à sec ou soit coupé tôt : l'ergol résiduel part avec l'étage largué — physiquement
correct pour un arrêt commandé. **Rien à changer côté masse.**

**3.1.3 — La barrière s'abaisse, conditionnelle au cœur commandable.** `STAGING_PENALTY_BASE`
passe de son seuil `stagingCompleteTime` au **temps de séparation boosters**
(`burn1 + BOOSTER_SEPARATION_COAST`), et **seulement si `coreStage != null`**. En dessous,
`transitionTime` ne commande toujours rien (cœur pas encore allumé) → le régularisateur y
garde le rôle mesuré en §2.2. Au-dessus — la région que `DT-20` réclame — chaque candidat
vole un cœur différent, plus de plateau, la barrière n'a plus lieu d'être. Pour un lanceur
sans cœur (`coreStage == null`), le seuil reste à `stagingCompleteTime` : comportement
d'aujourd'hui, intact.

**Point d'attention d'implémentation.** `maneuver.getStagingCompleteTime()` — l'ancre du
plafond `transitionTimeMax` et le calcul de `burn2Duration` — doit **rester sur le cœur
plein** (frontière de la région A). Seuls `coreBurnDuration` du plan et les dates qui en
dérivent rétrécissent, et le cap doit poser `coreJettisonDate ≈ mecoDate` au epsilon près,
dans la discipline d'arithmétique littérale que
[`AscentPlan`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/ascent/AscentPlan.java)
documente déjà.

### 3.2 Rééquilibrer `W_APOGEE_OVERSHOOT` (`DT-21`) : le poids monte parce que §3.1 lui rend un sens

La raison d'être du `0,5` — ne pas récompenser la fausse économie du cabrage — **n'existe
plus** dès que le cœur coupé sur `transitionTime` est un **vrai levier** pour baisser
l'apogée (§2.3). Le poids **monte donc, posé par mesure, le levier en place**. Le bracket
`[0,5 ; 3,0]` et le « 3,0 a surenchéri 148 km sous la cible » sont des mesures **d'avant le
levier** (l'optimiseur ne pouvait alors réduire l'apogée qu'en cabrant) : ils **orientent,
ne lient pas** ; la valeur juste se retrouve en volant sur le FH étranglé.

**Un seul poids global — l'argument quadratique, et son trou trouvé à l'implémentation.** La
pénalité est en `(dépassement/cible)²` : un dépassement de 91 km coûte `≈ 0,05·W`, un de
4 196 km `≈ 110·W` — un rapport de 2 000. L'énorme domine (fait couper le cœur), le petit reste
sous le seuil acceptable (le trim l'absorbe), et un profil **déjà à la cible** ne bouge pas
(`0 × W` reste 0). Mais cet argument ne couvre **que** les profils à la cible : un profil qui
**sur-délivre sans levier de coupure** (`coreStage == null`) n'a que le cabrage pour baisser
l'apogée, et monter `W` y **rachète la fausse économie** — exactement ce que
`GravityTurnProblemTest.computeCost_prefersTheHandOffTheMissionSurvives` épingle sur un stack
sans cœur. Pour le catalogue actuel c'est sans effet (FH et Ariane ont tous deux une phase de
cœur → l'optimiseur s'échappe en région B), mais le trou est réel pour un futur lanceur sans
cœur.

**Conséquence sur l'ordre (implémentation, 2026-09-11).** `W` **reste à `0,5`** pour l'instant,
et la première mesure vole le **seul déverrouillage de la région B** (§3.1) : un dépassement de
4 596 km coûte déjà ~55 à `0,5`, et couper le cœur pour viser la cible coûte ~0, donc
l'optimiseur *pourrait* déjà couper. Un changement à la fois — si
`testFalconHeavyOptimizedTransfer` atteint 400 ±7 % à `0,5`, le poids ne monte pas ; sinon la
mesure dit de combien, et on traite alors le test unitaire ci-dessus. Le `72 551 × 400 178` de
PHY-8 (barrière levée, sous-délivre) avertit que `0,5` peut ne pas suffire : c'est ce que la
mesure tranche.

> **La mesure a tranché : `0,5` suffit** (2026-09-11). `testFalconHeavyOptimizedTransfer` est
> vert à `0,5` (400,9 × 419,6 km osc., ~410 moyen), avec `transitionTime = 176,2` en région B,
> `burn2 = 0`, cœur coupé (23 s, résidu 17,8 t largué), S2 encore plein à 82,6 %. Le seul
> déverrouillage de la région B suffit — l'optimiseur préfère couper (coût ~0) à sur-délivrer
> (~55) — donc **le poids ne monte pas**, le test unitaire de la fausse économie n'est **pas**
> réécrit, et le trou de l'argument quadratique reste consigné mais sans effet pour `L3`.

**Le risque assumé** ([`06` §7](06-decoupage-PHY-2.md)) : ce poids touche tous les profils
du dépôt, donc la calibration peut demander plusieurs passes, et la re-baseline des 4 gates
est ce qui attrape tout profil qui aurait glissé.

### 3.3 Poser l'Isp d'abord, calibrer une fois — l'ordre d'option 3

L'implémentation de l'Isp (repliée de `L2`) et le rééquilibrage sont **couplés** : poser
l'Isp change le rapport de masses, donc l'ascension que les poids calibrent. On les
enchaîne **en passe jointe** :

1. **poser l'Isp `296 → ~298` en dur au catalogue d'emblée** — c'est une valeur mesurée par
   `L2`, pas à trouver en volant. Arithmétique : `ΔIsp = 51 / (g₀·ln R)` avec la traînée
   mesurée 51 m/s et le rapport de masse FH ≈ 16 (ln 16 ≈ 2,77) → `ΔIsp ≈ 1,9 s` →
   **297,9 ≈ 298**, cohérent avec la marge L2 (296→311 ≈ +407 m/s). Les deux entrées FH
   ([`Launchers:48,70`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/catalog/Launchers.java))
   prennent la même valeur (même moteur) ;
2. **toute la calibration volée** (poids, hand-off) se fait **une seule fois** par-dessus,
   **une seule re-baseline FH**.

C'est sûr parce que la perturbation Isp est minuscule (296→298 ≈ +51 m/s d'équivalent-ΔV)
devant l'arbitrage d'autorité (des milliers de m/s d'apogée) : l'Isp ne bouge quasiment pas
les poids, alors que les poids décident si le transfert vole. Retenu contre « Isp d'abord
puis autorité » (deux re-baselines FH) et « autorité d'abord puis Isp » (poids calibrés
contre la mauvaise Isp).

### 3.4 Le drag-on : B-check à `L3`, candidat `c` à `L5`

À `L3` le défaut reste `NONE` (drag-off) ; le drag-on devient **possible** ici (hand-off
redressé) mais n'est le **défaut** qu'à `L5`. `L3` produit donc deux vols de natures
distinctes, et ne garde du drag-on que ce qui a un **enjeu de justesse ici** :

- **Vol drag-off (les gates, le défaut).** Le FH vole drag-off à l'Isp neuve (298). C'est ce
  que ré-enregistrent les 4 gates + `AscentBaselineN2Test`, et ce qui ré-active
  `testFalconHeavyOptimizedTransfer` (400 ±7 %, ferme `BUG-25`).
- **Vol drag-on (le B-check), gardé à `L3`.** Traînée allumée explicitement, FH à 298 +
  traînée : la capacité drag-on ≈ capacité drag-off-ancien-proxy (296). C'est la preuve de
  `B` que `L2` §5.1 voulait. On le **garde à `L3` parce qu'il valide la valeur 298 là où on
  la pose** : sinon `L3` livre une Isp non vérifiée, et si `L5` la trouve fausse on
  re-baseline le FH deux fois — le double-travail qu'option 3 fuyait. C'est un vol
  **drag-on, donc ×7,5** (opt-in, lent).
- **Candidat `c` reporté à `L5`.** La substitution NRLMSISE→HP au transfert est une **pure
  optimisation de coût compute**, dont le bénéfice n'existe qu'à `L5` (drag-on par défaut) :
  aucun enjeu de justesse à `L3`, il défère proprement.

**`DT-15`** est dans le spine drag-off : l'**altitude** d'airstart S2 se lit sur l'ascension
reprovisionnée (propriété de la forme d'ascension) ; l'escalade vers un **Cd par régime**
(0,4 continu sous ~90 km, 2,2 au-dessus) n'a lieu **que si** l'airstart y reste en continu.
Ne rien construire avant de savoir que c'est nécessaire.

> **Mesuré : pas d'escalade.** Le S2 devient la surface active à la séparation S1, **~35 km
> (continu)** sur l'ascension reprovisionnée. Mais le `Cd = 2,2` (libre-moléculaire) y
> **sur-estime** la traînée du S2 d'un facteur ~5,5 — conservateur — et le B-check est passé
> **avec ce 2,2 en place** (capacité préservée, ci-dessous). L'écart est donc immatériel pour
> la capacité : on **assume `Cd = 2,2`**, sans escalade, ce que §3.7 du découpage autorise
> (« approximations assumées, pas corrigées »). Le vrai domaine du S2 reste l'orbite, où 2,2
> est juste.

### 3.5 L'attribution tient par la région, pas par la séparation des lots

`L3` change le drag-off pour **deux causes** — Isp *et* autorité — ce que la règle §4.1 du
découpage (« un changement à la fois ») réservait à deux lots. La réconciliation est **par
nature de profil**, et mesurable :

- l'Isp (296→298) décale **tous** les profils FH drag-off d'un montant **arithmétique
  calculable** (rapport de masse), uniforme ;
- l'autorité (coupure du cœur) ne touche **que** les profils en **région B** — cœur
  étranglé, sur-délivrant = le transfert optimisé — **pas** les profils de gate en région A
  (budgétés, cœur à extinction, dimensionnement compensé, `DT-20`) ;
- le poids relevé (§3.2) touche les profils de région A par ~0 (argument quadratique).

Donc les **gates (région A) se décalent pour l'Isp seule** — soustractible arithmétiquement
— et **l'autorité n'apparaît que dans `testFalconHeavyOptimizedTransfer` (région B)**, qui
n'est pas un épinglage 0.0 mais une cible ±7 %. La re-baseline **vérifie** cette
séparation : un gate qui bouge de plus que l'arithmétique Isp est un signal.

---

## 4. Sorties

- **FH bloc bas** : Isp `296 → ~298` (statique) ; la traînée deviendra explicite à `L5`.
- **Autorité** : coupure du cœur câblée sur `transitionTime` (cap dans `plan()`), barrière
  abaissée au temps de séparation boosters (conditionnelle `coreStage != null`),
  `W_APOGEE_OVERSHOOT` relevé (posé en volant).
- **Gates + `AscentBaselineN2Test`** re-baselinés drag-off à l'Isp 298.
- **`testFalconHeavyOptimizedTransfer`** ré-activé (`@Disabled` levé), 400 ±7 % → **`BUG-25`
  fermé**.
- **B-check drag-on consigné** : capacité FH préservée (preuve `B`).
- **`DT-15`** : altitude d'airstart S2 consignée ; Cd par régime seulement si <90 km continu.

---

## 5. Fermeture

1. **Les 4 gates + N2 verts** à tolérance actuelle (`0.0` comprise), re-baselinés drag-off à
   298 via un **mode record** `-Dorbitlab.recordBaseline` (ajouté aux 3 gates), `cleanTest` +
   gates isolés (`BUG-7`), décalage **attribuable à l'Isp seule** sur les profils de région A —
   le profil MEO (Ariane) n'a pas bougé, ce qui **prouve** l'attribution (§3.5). Stragglers
   hors-gate re-baselinés aussi (`ParallelBlockAscentTest`, `GravityTurnReplayConsistencyTest`).
2. **`testFalconHeavyOptimizedTransfer` ré-activé et vert** (400 ±7 %, atteint 410 km circ.) —
   la fermeture de `BUG-25`, **par le seul mécanisme à `W = 0,5`** : l'optimiseur va en région
   B tout seul (`transitionTime = 176,2`, cœur coupé), le poids n'a pas eu à monter (§3.2).
3. **B-check drag-on vert** : FH LEO-400 drag-on à 298 → **400,3 × 419,2 km**, capacité
   préservée (`AscentDragTerminationTest.falconHeavyLeo_dragOnAt298_preservesCapacity`) —
   preuve de `B`.
4. **`DT-15`** : S2 actif à ~35 km (continu), mais capacité préservée avec `Cd = 2,2` → **pas
   d'escalade**, 2,2 assumé (§3.4).

---

## 6. Ordonnancement

**Ordre interne de `L3`** : poser l'Isp 298 (édition statique) → coder §3.1 + §3.2 →
calibrer `W_APOGEE_OVERSHOOT` en volant sur le FH étranglé (la partie itérative) →
re-baseliner les gates drag-off → ré-activer `testFalconHeavyOptimizedTransfer` → B-check
drag-on → lire `DT-15`.

**Place dans le chantier.** `L3` dépend de `L2` (les poids se calibrent contre la vraie Isp)
et du socle `L1` (l'ascension drag-on termine). Il **lègue** à `L4` le cœur commandable —
l'étage supérieur s'allume enfin, le dimensionnement deux passes se fait contre cette
ascension corrigée — et à `L5` le candidat `c`, la bascule du défaut, et la matérialisation
de la baisse de capacité Ariane au drag-on.

**Dette éteinte** : `DT-20`, `DT-21`, `BUG-25`, `DT-13` (implémentation Isp FH — la dette
résiduelle contre le vide tombe à 343 m/s), `DT-15` (mesuré, `Cd = 2,2` assumé sans escalade).
**Reste** : `DT-14`/candidat `c` → `L5`.

**Risques** ([`06` §7](06-decoupage-PHY-2.md)) : le rééquilibrage du poids touche tous les
profils (plusieurs passes possibles) ; `BUG-7` impose `cleanTest` + gates isolés à chaque
re-baseline ; les tests d'optim et de mission sont lents et c'est l'utilisateur qui les
lance.

---

*Document rédigé le 2026-09-11 (séance de conception en conversation).*
