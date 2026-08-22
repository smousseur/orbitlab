# MIS-4 / L2 — La fenêtre lunaire

Lot **L2** du découpage ([`01-decoupage.md`](01-decoupage.md) §4), conçu sur les chiffres de
[`02-baseline-L0.md`](02-baseline-L0.md) et sur ce que [`03-conception-L1.md`](03-conception-L1.md)
lègue. Il rend vraie une seule propriété : **le tir est daté**.

`LunarLaunchWindowProblem` est la troisième implémentation de `LaunchWindowProblem`. Comme les deux
autres, elle n'ajoute aucun geste utilisateur et n'a aucun appelant de production : la mission la
consomme en `L4`, le wizard la branche en `L5`.

**Quatre énoncés du découpage sont corrigés ici.** Deux le sont par la géométrie — le nombre
d'opportunités par jour (§1.1) et le refus de Kourou (§1.3) —, un l'était déjà par la mesure de `L1`
— la forme du coût (§2.3) —, et le quatrième tombe de ce qui n'existe pas encore : `confirm()` ne
peut pas appeler l'étage d'injection (§3.3).

---

## 1. Ce que le code dit avant qu'on y touche

### 1.1 — Deux opportunités par jour sidéral, et non une

Le découpage ferme ce lot sur « la structure de la fenêtre (**une opportunité par jour sidéral**) ».
La géométrie en donne deux, et la raison tient à la nature de la cible.

`EarthLaunchWindowProblem` vise **un plan à RAAN fixé**. Les inclinaisons étant égales, il ne reste
qu'à faire coïncider deux nœuds : une égalité de vecteurs, satisfaite une fois par tour. Le problème
lunaire, lui, vise **une direction** — celle de la Lune à l'arrivée — et un plan contient une
direction bien plus souvent qu'il ne coïncide avec un autre plan.

Avec `ĥ = (sin i·sin Ω, −sin i·cos Ω, cos i)` et une Lune de déclinaison `δ` et d'ascension droite
`α` :

```
ĥ · ûM = sin i · cos δ · sin(Ω − α) + cos i · sin δ
```

Ce produit s'annule si et seulement si `|tan δ| ≤ tan i`, et il s'annule alors **deux fois par tour
de RAAN**, en `u = arcsin(−cot i · tan δ)` et en `180° − u`. Les deux opportunités sont donc
séparées de `180° − 2·|u|` de RAAN :

| Configuration | δ | Séparation |
|---|---|---|
| Lune à l'équateur | 0° | 180° de RAAN, soit 12 h |
| Maximum 2026 depuis Canaveral (L0 §5) | 28,415° contre `i` = 28,562° | ≈ 13° de RAAN, soit ≈ 50 min |
| Au-delà | `|δ| > i` | aucune : un seul minimum mou par jour, à `β = |δ| − i` |

**Les deux opportunités se rapprochent puis fusionnent** quand la déclinaison monte vers
l'inclinaison. Le second chiffre est très sensible aux deux angles près de la fusion — c'est
pourquoi le test du §5 le recalcule depuis l'éphéméride au lieu de le figer.

L0 §5 donne les cinq sites : Canaveral, Baïkonour, Vandenberg et Tanegashima sont dans le régime à
deux opportunités **100 % de la lunaison** ; Kourou 12,5 %.

### 1.2 — Il n'existe aucun refus disponible à la construction

Le découpage écrit « **Kourou est refusé à la construction**, comme `EarthLaunchWindowProblem`
refuse déjà une inclinaison inatteignable ». L'analogie ne tient pas. Cette classe refuse parce que
`sin A = cos i / cos φ > 1` n'a pas de solution ; ici `i = φ` en a toujours une, et le cas est même
écrit en dur — `sin A = 1` exactement, `A = π/2` par une branche explicite de
`LaunchPlane.launchAzimuth` (`:193-201`) posée pour que le plein est ne bouge pas d'un ulp.

Un refus de Kourou serait donc une **politique sur la latitude**, pas une géométrie. Le §1.3 dit ce
qu'il en advient.

**Et il n'y a pas non plus de branche de nœud à choisir** : à `i = φ`, `launchAzimuth` rend `π/2`
pour la branche ascendante et `π − π/2` pour la descendante (`:202`). Les deux azimuts fusionnent,
là où l'Earth problem en fait deux recherches distinctes. La signature de `L2` ne prend donc pas de
`LaunchPlane`.

### 1.3 — Le refus de Kourou est démenti par la mesure, et par le critère lui-même

L0 §7 pt 2 avait déjà réécrit son motif : pas la rareté — 12,5 % d'une lunaison, en fenêtres allant
jusqu'à 41 h — mais le coût hors fenêtre. Or ce coût **est dans le critère** que ce lot construit.
Une recherche depuis Kourou hors fenêtre rend un optimum que nul budget n'accepte, donc zéro
fenêtre ; pendant ses 12,5 %, elle rend une vraie fenêtre au prix nominal.

**Décidé : personne ne refuse Kourou.** Le refus reste au budget de la recherche et à `confirm()` —
plancher de périlune, plancher d'épuisement —, c'est-à-dire aux deux endroits qui refusent pour une
raison volée plutôt que décrétée. Le découpage §4 et §6 pt 3 sont corrigés sur le comportement,
après l'avoir été sur le motif.

### 1.4 — `evaluate` ne refusera presque jamais

La projection du §2.1 de `L1` ne dégénère qu'à `β = 90°`, et `L1` §5 pt 2 borne β à `i + |δ|`,
soit 74,7° au pire (Baïkonour). L'angle de transfert 3D vrai, `acos(cos 170°·cos β)`, vaut alors
105° au lieu de 170° : le solveur de Lambert s'**éloigne** de sa singularité quand la géométrie se
dégrade.

Le critère est donc **fini partout**, avec des milliers de m/s de relief. Un refus d'`evaluate`
reste possible — une non-convergence de Lambert est une `OrbitlabException` — mais c'est un accident
et non un régime.

### 1.5 — Ce que `L1` lègue, et le trou qui reste entre les deux pièces

`departureFrom(SpacecraftState)` rend `Departure(coastDuration, injectionDate, arrivalDate,
injectionDirection, planeMisalignment)`, et `keplerianInjectionDeltaV(parking, arrivalDate)` chiffre
l'injection depuis un parking donné. Les deux sont en forme close, sans propagation.

**Mais `Departure` ne porte aucun état** — décision délibérée de `L1` §2.4, pour qu'on n'injecte pas
depuis un état képlérien décalé plutôt que depuis l'état réellement volé. La surcharge, elle, veut
un parking **au point d'injection**. C'est donc `L2` qui fabrique l'orbite circulaire depuis
`(ĥ, d̂, r)`. Quatre lignes, mais c'est lui qui tranche la **phase**, et cette phase est la seule
vraie question de conception du lot.

---

## 2. Le critère

### 2.1 — Ce dont `evaluate(t)` est la fonction

**`t` est la date de décollage**, et c'est elle que le plan atteignable lit. Rien d'autre dans la
chaîne n'en dépend : l'ascension coûte le même Δv à toute heure du jour, donc le critère est
l'injection seule.

```
1.  P   = position inertielle du pas de tir à t                (ITRF → GCRF)
2.  ĥ   = normalize(P × localHorizontalDirection(P, 90°))      le plan subi
3.  s₀  = orbite circulaire (ĥ, r̂₀ = normalize(P), r = Rₑ + h_parking), à t
4.  dep = TranslunarInjectionPlan.departureFrom(s₀)            coast, arrivée, d̂, β
5.  sᵢ  = la même circulaire, tournée sur d̂, à dep.injectionDate()
6.  Δv  = keplerianInjectionDeltaV(sᵢ, dep.arrivalDate())
```

Un solve de Lambert, quelques évaluations d'éphéméride, aucune propagation : l'`evaluate` reste dans
les microsecondes, comme les deux implémentations existantes.

**Le pas de tir est dans le plan par construction**, le normal étant bâti comme
`position × horizontale` : poser la phase de parking sur la direction du site ne demande aucune
projection. C'est aussi la phase physiquement juste — à `i = φ` en tir plein est, le site est le
point le plus septentrional de l'orbite.

### 2.2 — Les deux façons écartées

**Une avance nominale constante** — arrivée à `t + ascension + ½ révolution + ToF`, sans
`departureFrom`. Le critère devient lisse et unimodal, mais la date d'arrivée est fausse de ±44 min,
soit ±0,4° sur `ûM`, soit ±0,4° sur β : l'optimum se décale de **~3,4 min contre une fenêtre de
~11 min** (§2.4). Une erreur du tiers de ce qu'on cherche, en échange d'une commodité numérique.

**Un minimum sur `k` révolutions de coast** — physiquement le plus juste, et continu, puisque les
branches successives se recouvrent. Mais 5 302 + 5 292 = 10 594 s crèvent la fenêtre de rallumage de
7 200 s du Falcon Heavy S2 (découpage §4, lot `L6`) sans crever les 21 600 s de l'ULPM : il faudrait
connaître le lanceur ici, alors que `L5` ne l'a pas encore choisi.

### 2.3 — Le coût est le Δv de Lambert seul

Le découpage §4 écrit `2·v·sin(θ/2) + Δv d'injection Lambert`, « le terme de plan porte le relief,
le terme de Lambert départage ». `L1` §6 a mesuré que les deux moitiés sont fausses : le terme de
Lambert **porte déjà tout le coût de plan** — il relie la vitesse de parking réelle à la vitesse
d'injection réelle — et le terme de plan sous-lit le relief d'un facteur 3.

Le critère ne garde donc que Lambert. **β n'est plus un terme de coût mais la grandeur journalisée**
qui explique la ligne : un Δv à 5 300 m/s n'a de sens que si le désalignement qui l'a produit est à
côté.

### 2.4 — La forme du critère, et ce qu'elle vaut

En ajustant `coût² ≈ 3 184² + K·β²` sur les deux points que `L1` a mesurés — β = 0 → 3 184 m/s et
β = 5,028° → 5 343 m/s — on obtient `K = 2,39·10⁹`.

**C'est un ajustement à deux points, pas une mesure de la courbe**, et il ne sert qu'à dimensionner
le lot :

| | |
|---|---|
| Vitesse de balayage de β | `sin i · ω⊕` = **7,19 °/h** à Canaveral |
| β acceptable pour 50 m/s de marge | 0,66° |
| **Largeur de fenêtre** | **≈ 11 min** |

Le même ordre que les 3 min 52 s de l'Earth problem : le critère a du relief, et le §4 peut adopter
les mêmes échelles pour les mêmes raisons. La vraie courbe est mesurée par les tests 2 et 5 du §5.

### 2.5 — La discontinuité du premier passage, nommée plutôt que découverte

Le coast de `departureFrom` est le **premier passage** par le point d'injection (`L1` §2.2). Une
fois par jour sidéral, quand la direction du site croise ce point, il saute d'une révolution : la
date d'arrivée saute de 1,47 h, `ûM` de 0,81°, et β d'autant.

**Ce que ça coûte, sur l'ajustement du §2.4** : au voisinage de l'optimum, un saut de β de 0 à 0,81°
vaut **+74 m/s** ; à quelques degrés de β la pente locale du modèle atteint 685 m/s par degré, donc
plusieurs centaines de m/s — extrapolation d'un ajustement à deux points, à ne pas lire comme une
mesure.

Il n'y a **aucune raison** pour que « le site est au point d'injection » et « le plan contient la
Lune » coïncident : le saut tombe génériquement ailleurs que les deux optima du jour. Mais rien ne
l'interdit, et la section dorée du solveur suppose l'unimodalité dans son bracket. **Assumé, chiffré,
et le test 5 le cherche** au lieu d'espérer ne pas le rencontrer.

### 2.6 — La normale atteignable est extraite, pas réécrite

`reachablePlaneNormal` et le `TopocentricFrame` qui la porte descendent dans un helper
package-private de `window/problem`, consommé par les deux problèmes. C'est un déplacement sans
changement de comportement, et `EarthLaunchWindowProblemTest` — qui assère le plancher
géodésique/géocentrique à 1,1 et 35,9 m/s — en est la garde.

L'alternative, réécrire six lignes chez le problème lunaire, ne duplique pas la partie dangereuse :
la base `(nord, est)` reste dans `Physics.localHorizontalDirection`, seule écriture de cette
convention. Elle laisse en revanche deux classes répondre à la même question, et c'est ce qui la
fait écarter.

---

## 3. Ce que le problème prend, et ce que `confirm()` fait

### 3.1 — Le constructeur

```java
LunarLaunchWindowProblem(
    double latitude, double longitude, double altitude,   // le pas de tir
    double parkingAltitude,                                // m
    double targetPerileneAltitude,                         // m
    Vehicle vehicle, double massAtInjection)               // pour confirm() seul
```

Le couple `(Vehicle, masse)` plutôt que trois nombres, parce que c'est **exactement ce que fait
l'étage** : `resolveActiveStage(previousState.getMass())` en tire l'Isp et le plancher d'épuisement
(`TranslunarInjectionStage:64`). En test, c'est le `Spacecraft(500, 1200, 1200,
spacecraftPropulsion())` de la démo, donc des chiffres directement comparables à L0 et L1.

**La masse est donnée, pas déduite** : l'ascension est hors modèle (§2.1), et c'est
`PropellantBudget` qui la sizera, en `L5`.

**L'altitude de parking est un paramètre** et non une constante lunaire : L0 §2 a mesuré que la
visée converge identiquement de 185 à 400 km.

### 3.2 — Pas de record de requête

`EarthLaunchWindowRequest` existe pour deux raisons — être une clé de mémoïsation pour la boucle
scrutée du wizard, et offrir `from(MissionSpec.EarthOrbit)` comme chemin de construction sûr contre
cinq `double` d'affilée. Aucune des deux n'a d'objet ici : `MissionSpec.Lunar` naît en `L4`, le
wizard en `L5`. Le record leur est **légué**, avec la note qu'il devra l'être.

### 3.3 — `confirm()` ne peut pas appeler l'étage

```
plan     = TranslunarInjectionPlan.solve(sᵢ, périlune visée, Isp·g₀, contexte)
injected = plan.applyTo(sᵢ, Isp·g₀)
si injected.masse < active.depletionFloor()  →  refus
sinon                                        →  candidat re-tarifé à |plan.deltaV()|
```

Le contexte est `GravitationalContext.earth().withPerturbers(MOON, SUN)`, la même ligne que
`LunarTransferMission:115`.

**Ces quatre lignes sont une recopie de `TranslunarInjectionStage.enter`, et c'est précisément le
défaut que le javadoc de `LaunchWindowProblem#confirm` nomme** — une ré-implémentation peut dériver,
et la dérive se verrait comme une mission qui échoue sur le thread d'optimisation après avoir été
planifiée. Elle est inévitable : appeler l'étage exige un `Mission`, et la mission lunaire du
produit n'existe qu'en `L4`. **Écrit en limitation avec sa fermeture nommée** : `L4` rend la
confirmation à l'étage, comme le problème translunaire existant le fait déjà.

Les deux modes d'échec sont repris tels quels de ce précédent : `OrbitlabException` → refus
journalisé en `info`, c'est de la donnée ; `RuntimeException` → refus journalisé en `warn`, c'est
une faute qu'on ne laisse pas avorter le balayage.

### 3.4 — Ce que la confirmation coûte, et pourquoi le seuil n'en souffre pas

~4,5 s par confirmation, ~3,9 s par refus (L0 §6). Le solveur ne confirme que les minima sous le
seuil, du moins cher au plus cher, et s'arrête dès qu'il le dépasse : deux à trois confirmations
pour une recherche de 26 h, soit ~15 s. L0 §6 concluait que « `L2` peut confirmer largement » ;
c'est vérifié.

Le seuil n'est pas corrompu par la surcharge de confirmation, parce que le solveur **ancre sa marge
sur l'étage d'écran des deux côtés** — décision déjà prise et écrite dans son javadoc, pour une
surcharge mesurée à 6-8 m/s sur le problème existant. `L2` re-mesure la sienne sur la géométrie à
plan subi ; c'est un des chiffres de fermeture du §5.

---

## 4. Les trois échelles

| | Valeur | Motif |
|---|---|---|
| `coarseStep` | 1 h | Le critère est lisse à l'heure, et il n'y a que le minimum à **encadrer**, pas à résoudre : douze échantillons par demi-jour. |
| `refinementPrecision` | 1 s | La fenêtre fait ~11 min quand le pas fait 1 h. Le défaut d'un dixième de pas demanderait 6 min — plus grossier que ce qu'il cherche, la forme silencieuse du faux. |
| `recurrence` | demi-jour sidéral | §1.1. Dérivé de `π / ω⊕` sur `WGS84_EARTH_ANGULAR_VELOCITY`, comme l'Earth problem dérive le sien, pour que les 86 164 s n'aient qu'une source. |

---

## 5. Les tests de fermeture

Même partage qu'en `L1` : la géométrie fermée d'un côté, le vol de l'autre.

### 5.1 — Fermés, dans `LunarLaunchWindowProblemTest`, en millisecondes

**1. La structure, assérée contre sa forme close plutôt que comptée.** Les positions des minima
trouvés par balayage sont recoupées contre `180° − 2·|arcsin(cot i · tan δ)|`, à deux époques : une
Lune proche de l'équateur (≈ 12 h de séparation) et le maximum 2026 du 26 février (≈ 50 min). C'est
une assertion plus forte que « deux opportunités » : elle dit **pourquoi** il y en a deux, et elle
capture la fusion. Au second cas le balayage horaire n'en voit qu'un seul, ce qui est le
comportement correct et doit être écrit plutôt que subi.

**2. Le relief.** Le pire instant du jour, `β = i + |δ|`, contre l'optimum : chiffré, journalisé,
et asséré comme un rapport large plutôt que comme un nombre ajusté. C'est le pendant du
`theFarSideOfTheDayCostsAFullPlaneChange` terrestre.

**3. L'optimum recoupe la baseline.** À β ≈ 0 depuis Canaveral, 185 km de parking, le critère doit
rendre ~3 178 m/s — **ce que L0 §2 a mesuré sur le plan fabriqué**. C'est le raccord du lot : un
plan subi qui contient la Lune coûte ce que le plan fait sur mesure coûtait.

**4. Kourou est tarifé, pas refusé.** Hors fenêtre le critère est fini et vaut plusieurs fois
l'optimum ; à une époque où `|δ| ≤ 5,236°`, il rend un optimum comparable à Canaveral. **Ce test
remplace celui du refus** que le découpage prévoyait, et il est le seul endroit où les 12,5 % de
L0 §5 deviennent exécutables.

**5. Le saut du premier passage ne trompe pas le solveur.** Balayage de force brute au pas de 60 s
sur 26 h — 1 560 évaluations fermées —, comparé à ce que `LaunchWindowSolver` rend : les deux optima
doivent coïncider à la minute. Le plus grand saut entre deux échantillons consécutifs et sa distance
à l'optimum sont **mesurés et journalisés** : c'est le chiffre qui dira si la discontinuité du §2.5
est un défaut ou une curiosité.

**6. Les trois échelles**, sur le patron de `theRecurrenceIsOneSiderealDay` et
`theSearchAdoptsBothScales`.

### 5.2 — Volé, dans `LunarLaunchWindowFlightTest`

Classe neuve à côté de `TranslunarDepartureFlightTest`, la même frontière rapide/lent que le dépôt
tient déjà.

**7. Une fenêtre datée et confirmée depuis Canaveral.** `LaunchWindowSolver.solve` sur 26 h,
`confirm()` actif. Assère qu'il y a au moins une fenêtre, que `solve` converge sur son optimum et
que le périlune tombe dans la bande. Journalise la date, la largeur du créneau, β à l'optimum, et
**l'écart écran/confirmation** — le pendant des 6-8 m/s du problème existant, mesuré ici sur la
géométrie à plan subi. ~15 s.

**Contrainte de méthode**, rappelée du découpage §3 : c'est l'utilisateur qui lance ce test.

---

## 6. Limitations assumées

Onze, dont huit appartiennent à ce lot.

**Les deux biais systématiques, du même ordre et refermables au même endroit**

1. **L'ascension est hors modèle.** Le modèle pose l'insertion en parking à `t`, sur la direction du
   site ; la vraie insertion arrive ~10 min plus tard et ~20° plus loin. La date d'arrivée est donc
   lue 0,137° trop tôt sur la Lune, et l'optimum se décale de **68 s**. `L2` n'a aucun moyen de
   mesurer cette constante — c'est `L4` qui vole l'ascension.
2. **J2 n'est pas dans le plan atteignable.** Le plan subi régresse de ≤ 0,49° pendant l'unique
   révolution de coast (`L1` §5 pt 1), et le critère lit le plan à `t` sans le compter :
   `sin i · 0,49° = 0,23°` sur β, soit **~115 s** sur la date optimale. C'est le plus gros des deux,
   et il n'était nommé nulle part avant cette conception.

Ensemble, ~3 min sur une fenêtre de ~11 min. Écrits ici pour qu'aucun ne passe pour une dérive
quand `L4` volera la chaîne.

**Ce que la forme du critère coûte**

3. **La discontinuité du premier passage** (§2.5) : une par jour sidéral, +74 m/s près de l'optimum,
   plusieurs centaines de m/s loin de lui. Le test 5 la mesure au lieu de l'espérer absente.
4. **`i = φ`, plein est, pas de branche de nœud** — découpage §6 pt 2, rendu visible dans la
   signature : à `i = φ` les deux azimuts fusionnent, donc il n'y a pas de `LaunchPlane` à passer.
   L'inclinaison adaptative reste ce que le découpage en dit.
5. **`recurrence()` ment quand `|δ| > i`** : elle annonce un demi-jour là où il n'y a qu'un
   minimum quotidien, donc `forOpportunities` dimensionne un span deux fois trop court en nombre
   d'opportunités. Sans effet pratique — aucune de ces opportunités n'est dans un budget.

**Ce qui est recopié ou reporté**

6. **Le plancher d'épuisement est une recopie de quatre lignes** de `TranslunarInjectionStage.enter`
   (§3.3), faute d'un `Mission` à qui les demander. `L4` rend la confirmation à l'étage.
7. **Kourou n'est plus refusé** (§1.3). Le découpage §4 et §6 pt 3 sont corrigés : capacité étroite
   et réelle, 12,5 % de la lunaison, tarifée par le critère.
8. **Ni record de requête, ni planner, ni mémoïsation** (§3.2) → `L5`, sur le patron
   `EarthLaunchWindowRequest` / `EarthLaunchWindowPlanner`.

**Héritées, non touchées**

9. **ToF à 4 j et angle de transfert à 170° restent des constantes couplées** — découpage §6 pt 1.
   L'altitude de parking, elle, devient un paramètre (§3.1).
10. **Le seed de Lambert reste enfermé** dans `TranslunarInjectionPlan`, à `nRev = 0` — découpage
    §6 pt 7.
11. **Rien n'est optimisé** — découpage §6 pt 5.

---

## 7. Ce que `L2` lègue

**À `L4`** — une date de tir confirmée, et le constat que la chaîne doit prendre son plan du site
plutôt que le fabriquer. Plus deux mesures à faire en volant : les 68 s de l'ascension et les 115 s
de J2. Le piège que `L1` lui a légué reste entier par ailleurs — le coast de parking doit écraser
`propagateStandalone`, sans quoi le TLI se résout depuis l'insertion en parking sans qu'aucune
erreur ne soit levée.

**À `L5`** — le record de requête et le planner, et **un changement de libellé**. Le découpage §4
prévoit « le refus de Kourou présenté comme un refus et non comme une exception » : il n'y a plus de
refus à présenter. Ce que `L5` montre est « aucune fenêtre sur l'horizon », qui est vrai quand ça
l'est et faux 3,4 jours par lunaison.

**À `L3` et `L6`** — rien, comme l'ordonnancement du découpage §5 le dit déjà.
