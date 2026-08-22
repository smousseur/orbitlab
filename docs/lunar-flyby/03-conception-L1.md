# MIS-4 / L1 — L'injection depuis un plan de parking imposé

Lot **L1** du découpage ([`01-decoupage.md`](01-decoupage.md) §4), conçu sur les chiffres de
[`02-baseline-L0.md`](02-baseline-L0.md). Il rend vraie une seule propriété : **l'injection
translunaire part d'une orbite de parking subie, et non fabriquée pour convenir**.

Il n'ajoute aucun geste utilisateur, aucune mission, aucun étage. Le tableau du §4 le classe
« non (additif) », et cette conception le tient : les deux tests existants du chemin lunaire —
`LunarTransferFlightTest` et les cinq cas de `TranslunarInjectionPlanTest` — doivent passer **sans
une ligne d'édition**. Ce n'est pas une conséquence espérée, c'est le critère du lot.

**Trois énoncés du découpage sont corrigés ici** : la garde de déclinaison (§1.5), la borne du
coast de parking (§2.2) et la forme du critère de coût de `L2` (§6). Le premier a été tranché en
concevant ; les deux autres l'ont été par la mesure, en implémentant.

---

## 1. Ce que le code dit avant qu'on y touche

Quatre faits relevés dans `TranslunarInjectionPlan` (712 l.), qui décident de toute la suite.

**1.1 — `solve()` est déjà presque agnostique au plan.** Tout ce qu'il lit vient de l'état de
parking qu'on lui passe : `boundaryConditions` (`:465`), `keplerianSeedVelocity` (`:477`),
`attempt`, `bracket`, `perileneRadius`, `measurePlanVersusFlight`. **Un seul appel fait exception**,
`aimOffsetDirection` (`:660`), qui dérive son plan par `transferPlaneNormal(moonDirection)`
(`:663`) — une fonction qui **fabrique** un plan à 30° à partir de la seule direction lunaire et
**ignore l'état reçu**.

**1.2 — Aujourd'hui les deux coïncident exactement, et c'est un accident de construction.**
`parkingState` (`:144`) bâtit l'orbite de parking depuis ce même normal ; le vaisseau est donc dans
le plan que `aimOffsetDirection` refabrique. Depuis un plan subi, les deux divergent : la direction
d'offset serait posée dans un plan que le vaisseau ne vole pas.

**1.3 — Une hypothèse tacite survit dans le seed de Lambert.** `keplerianSeedVelocity` appelle
`solve(true, 0, …)` (`:480`) et son javadoc justifie le `true` par « le normal du plan de transfert
a une composante verticale positive par construction, `PARKING_INCLINATION` étant bien sous 90° ».
Cette constante ne gouverne plus rien une fois le plan subi : la justification devient une hypothèse
sur une entrée.

**1.4 — Les 170° sont un placement, pas une contrainte.** Rien en aval n'exige que l'angle de
transfert soit atteint : `solve` lit la position que porte l'état et Lambert relie ce point au point
de visée. `TRANSFER_ANGLE` n'est asserté qu'en `TranslunarInjectionPlanTest:78`, **sur la sortie de
`parkingState`**. Un coast qui tombe à une fraction de degré près coûte quelques m/s, pas un refus.
C'est ce fait qui autorise la forme close du §3.

### 1.5 — La correction au découpage

Le §4 écrit, pour L1 : « La bissection sur le périlune, le correcteur différentiel et **la garde de
déclinaison** ne bougent pas. » Les deux premiers ne bougent effectivement pas. La garde, elle, ne
peut pas rester sur le chemin de `solve` :

- elle refuserait des époques qu'un plan Baïkonour à 45,97° vole sans difficulté, puisqu'elle
  compare la déclinaison lunaire à une constante de 30° qui n'a plus de rapport avec l'orbite ;
- et elle ferait travailler `aimOffsetDirection` dans un plan qui n'est pas celui du vaisseau.

**Ce que L1 fait à la place** : la garde reste, intacte, **sur le chemin de `parkingState`** — la
démo et ses tests unitaires, que le découpage veut préserver. `solve` cesse de la traverser. Le fond
de l'énoncé est donc tenu ; sa lettre ne l'est pas, et l'écart est écrit ici plutôt que découvert au
vol.

---

## 2. La géométrie de `departureFrom`

![Deux vues du même problème. À gauche, le plan de parking vu de face : l'orbite circulaire, la Terre au centre, l'état d'entrée r̂₀ en vert, le point d'injection d̂ en orange 170° en arrière de p̂ (la projection de la direction lunaire dans le plan), l'arc Δ entre r̂₀ et d̂ qui est le coast de parking, et l'arc de transfert en rouge qui quitte d̂ vers la Lune. À droite, le même plan vu par la tranche : le normal ĥ dressé, la direction lunaire ûM inclinée de l'angle β au-dessus du plan, et sa projection p̂ couchée dans le plan. En bas, la boucle de point fixe qui résout la durée de coast.](images/03-point-injection.svg)

**Entrée** : l'état de parking `s₀` à `t₀`, dont le plan est subi. **Sortie** : la durée de coast qui
mène au point d'injection.

Le plan imposé est lu sur le moment cinétique, `ĥ = normalize(r₀ × v₀)`.

### 2.1 — Le point d'injection

La direction d'arrivée ne se trouve plus dans le plan de parking. « À 170° en arrière de la
direction d'arrivée, dans le plan de parking » demande donc à être tranché, et **L1 projette** :

```
β  = asin(ĥ · ûM)                 désalignement signé, vers +ĥ
p̂  = normalize(ûM − (ûM·ĥ)·ĥ)     la direction lunaire, couchée dans le plan
d̂  = R(ĥ, −170°) · p̂              le point d'injection
```

**Trois raisons.** C'est toujours défini — la seule dégénérescence est la Lune au pôle du plan, et le
§5 montre qu'elle est inatteignable. À β = 0 le résultat est **exactement** celui de `parkingState`,
donc la démo ne bouge pas. Et quand β croît, l'angle de transfert 3D vrai s'éloigne de la
singularité des 180° au lieu de s'en rapprocher, puisque `cos θ = cos 170° · cos β`.

**L'alternative écartée** était la lecture littérale : chercher le point du plan situé à exactement
170° en 3D de la direction lunaire. Elle n'a de solution que si `cos β ≥ |cos 170°|`, soit
**β ≤ 10°** — L1 aurait acquis un refus géométrique là où le désalignement atteint 33,9° depuis
Kourou (L0 §5). Ce refus aurait doublé celui que L2 et L5 doivent porter, et pour un motif différent
du leur.

### 2.2 — Le point fixe

Deux circularités : la direction lunaire dépend de la date d'arrivée, qui dépend de la durée de
coast ; et l'angle à parcourir dépend de la position képlérienne à l'instant d'injection. **Une seule
boucle les referme toutes les deux.**

```
τ ← 0
répéter :
    ûM = direction lunaire à t₀ + τ + ToF
    d̂  = le point d'injection, par §2.1
    r̂  = position képlérienne à t₀ + τ, normalisée
    Δ  = angle orienté de r̂ vers d̂ autour de ĥ
         ramené dans [0, 2π) à la première passe, dans (−π, π] ensuite
    τ  ← τ + Δ / n
jusqu'à |Δ| < 1e-9 rad, plafonné à cinq passes
```

**Pourquoi elle converge, et vite.** Les deux couplages sont fortement contractants. Le point
d'injection balaie **244,9 °/h** (période 5 291,8 s à 185 km) contre **0,549 °/h** pour la direction
d'arrivée (mois sidéral) : rapport **0,0022** par passe. L'excentricité résiduelle du parking coûte
`O(e) ≈ 1e-3` sur la conversion angle → temps. Une passe laisse au plus 11,9 s d'erreur, soit 0,81°
de phase ; deux la ramènent à 0,0018°. Trois suffisent, **et aucune propagation n'est faite** : seule
l'éphéméride lunaire est évaluée.

**Le wrap de la première passe fait le travail d'une garde.** Partir de τ = 0 et ramener Δ dans
`[0, 2π)` sélectionne **le premier passage** par le point d'injection, sans rien à vérifier.

**Mais ce premier passage déborde la révolution d'une douzaine de secondes.** Le point d'injection
n'est pas fixe : il dérive vers l'avant avec la Lune pendant qu'on le rejoint — les mêmes 0,549 °/h,
soit 0,81° de phase de parking, soit **11,9 s** sur un tour. Un départ situé juste *après* son point
d'injection attend donc presque un tour, et le point a avancé entre-temps : **mesuré 5 302,0 s pour
une période de 5 291,5 s**. L'énoncé du découpage — « le coast de parking avant injection vaut au
plus une révolution (5 292 s), sous les 7 200 s du S2 », §4 lot `L6` et non §6 comme cette
conception l'a d'abord cité — est **faux à la lettre et vrai au fond** : la borne
est une révolution **plus la dérive**, et 5 302 s reste très en deçà de la fenêtre de rallumage du
S2 comme des 21 600 s de l'ULPM. Les deux contraintes d'étage tiennent donc, mais pas pour la raison
écrite.

**À β = 0 le point fixe est immobile.** Sur la démo, `parkingState` a déjà posé le vaisseau au point
d'injection : Δ = 0 dès la première passe, τ = 0 exactement, `d̂ = r̂₀`.

### 2.3 — Un piège Orekit, nommé plutôt que découvert

La position képlérienne ne peut **pas** venir de `s₀.getOrbit().shiftedBy(τ)`. Un état sortant d'un
propagateur numérique porte une accélération non képlérienne, et `Orbit.shiftedBy` ajoute alors un
terme quadratique en `dt²` explicitement destiné aux petits décalages
(sources Orekit 13.1.1 : `Orbit:562-573`, `CartesianOrbit.shiftPV:431`) ; sur 5 292 s il est
absurde.

Il faut reconstruire une orbite depuis **position et vitesse seules** —
`new KeplerianOrbit(new PVCoordinates(r₀, v₀), frame, t₀, mu)` — dont l'accélération nulle éteint le
drapeau (`Orbit.hasNonKeplerianAcceleration:190-215`) et ramène `shiftedBy` à une pure avance
d'anomalie moyenne (`KeplerianOrbit:1063-1072`) — ces quatre références sont dans Orekit, pas dans
le dépôt.

**Ce piège ne casse rien : il décale.** C'est pourquoi le §4 lui consacre une assertion.

### 2.4 — Ce que le record porte

`TranslunarInjectionPlan.Departure`, record imbriqué :

| Composant | |
|---|---|
| `coastDuration` | la durée de coast de parking (s) |
| `injectionDate` | `t₀ + τ` |
| `arrivalDate` | `t₀ + τ + ToF` |
| `injectionDirection` | `d̂`, unitaire, en GCRF |
| `planeMisalignment` | `β` signé (rad) |

**Pas d'état.** Exposer l'état képlérien décalé inviterait à injecter depuis lui plutôt que depuis
l'état réellement propagé, et les deux diffèrent du demi-degré du §5. Les deux consommateurs se
contentent de la direction : L4 coaste et lit sa propre position, L2 fabrique une orbite circulaire
depuis la direction et le rayon.

**`β` est exposé parce qu'il est déjà là.** Il tombe de la projection du §2.1, et c'est exactement le
terme de plan du critère de L2. Le recalculer ailleurs serait écrire la même trigonométrie à deux
endroits — ce que `LaunchPlane` a explicitement refusé pour `asin(cos i / cos φ)`.

---

## 3. La chirurgie dans `solve`

Trois changements, et rien d'autre sur ce chemin.

**3.1 — `aimOffsetDirection` cesse de fabriquer son plan.** La ligne `:663` devient le normal propre
de l'arc, gelé sur la visée provisoire au centre lunaire :

```
n̂_arc = normalize(r_parking × r_Lune(arrivée))
```

Le reste de la méthode ne bouge pas : même solve de Lambert provisoire, même vitesse relative, même
choix de signe le long de la vitesse lunaire.

**Pourquoi le plan de l'arc et non le plan de parking.** C'est le plan que la trajectoire vole
réellement après l'impulsion. Dès que β > 0 le vaisseau quitte le plan de parking à l'injection :
poser l'offset dans ce plan inclinerait le survol par rapport à son propre arc — exactement ce que le
javadoc de la méthode dit vouloir éviter. Il n'est jamais dégénéré ici, l'angle de transfert 3D vrai
restant sous 170° donc loin de 0° et de 180°.

**Pourquoi il est gelé et non re-dérivé à chaque tentative.** Le recalculer pour chaque offset ferait
dépendre la direction d'offset de l'offset lui-même, et la monotonie sur laquelle la bissection
repose — « viser plus loin passe plus loin », `:349` — ne serait plus garantie par construction. Or
c'est précisément la propriété qui avait fait abandonner la sécante (`:236-244`). Le gel est le même
procédé que le code applique déjà à la circularité de la vitesse relative.

**Sur la démo les deux vecteurs sont exactement parallèles** : `r_inj` et `r_Lune` sont tous deux dans
le plan à 30°, séparés de 170° ∈ (0°, 180°), donc leur produit vectoriel pointe le long de `+n̂`.
L'écart est du bruit flottant. **Et le choix de signe rend la substitution insensible au sens du
normal** — seule sa direction compte, ce qui retire au changement toute possibilité d'inverser le
survol.

**3.2 — `transferPlaneNormal` reste, et cesse de mentir.** Elle n'est plus appelée que par
`parkingState`. Son javadoc doit cesser de l'appeler « le plan de transfert » au sens général : c'est
le plan **fabriqué** de la démo, avec sa garde de déclinaison et sa constante de 30°.

**3.3 — `keplerianSeedVelocity` dérive son drapeau.** Le `true` de `:480` devient le signe de
`(r₁ × r₂)·z`, lu sur les conditions aux limites, qui portent déjà les deux positions. **Pas de
changement de signature** : `keplerianInjectionDeltaV` (`:327`) et le test `:115` ne bougent pas. Sur
la démo, `h_z = cos 30° > 0` donne `true`, donc rien ne bouge non plus.

Une ligne, et elle supprime l'hypothèse tacite du §1.3 au lieu de la déplacer. `nRev = 0` reste, en
revanche : c'est la limitation assumée du découpage §6 pt 7, et le deuxième consommateur qui fixerait
la forme de l'API est `MIS-6`.

**3.4 — Un ajout de journal, pas de garde.** `solve` logue β à côté du Δv. Un Δv qui saute de 3 178 à
6 000 m/s parce que le plan est désaligné de 23° doit se lire dans la ligne, pas se déduire.

**Ce qui ne change pas** : `bracket`, `attempt`, `boundaryConditions`, `perileneRadius`,
`measurePlanVersusFlight`, la bissection, sa tolérance de 1 km et son bracket par doublement, le
correcteur différentiel. Sur ces trois-là le §4 du découpage tient à la lettre.

### 3.5 — La surcharge close

`keplerianInjectionDeltaV(injectionDate, mass)` fabrique son propre parking par `parkingState` : L2
ne peut pas s'en servir. L1 livre la surcharge `keplerianInjectionDeltaV(parking, arrivalDate)`, même
géométrie fermée, un solve de Lambert, aucune propagation.

**C'est un élargissement, et il est assumé.** Le découpage écrit ce travail dans L2. Deux raisons de
le faire ici : c'est le compagnon direct de `departureFrom`, et **le test volé de L1 en a besoin**
pour comparer le coût à β > 0 au coût à β = 0. L2 n'hérite alors que de son critère, pas d'un bout de
mécanique.

---

## 4. Les tests de fermeture

Le dépôt sépare déjà la géométrie fermée du vol. L1 se range dans cette séparation plutôt que d'en
créer une.

### 4.1 — Les propriétés fermées, dans `TranslunarInjectionPlanTest`

La classe déclare exactement ce rôle dans son javadoc : « la géométrie de l'injection translunaire,
sans aucune propagation ». Sept assertions, en millisecondes, sur quatre plans imposés — Canaveral
28,56°, Baïkonour 45,97°, Kourou 5,24°, plus un plan délibérément quelconque.

| | Ce qui est asserté |
|---|---|
| 1 | `d̂ · ĥ = 0` — le point d'injection est **dans le plan subi** |
| 2 | l'angle orienté de `d̂` vers `p̂` autour de `ĥ` vaut **170°** |
| 3 | la position képlérienne décalée de τ **atteint** `d̂` — cohérence interne du point fixe |
| 4 | `τ ∈ [0, période + 12 s]` — **le premier passage**, jamais un tour de plus |
| 5 | à β = 0, depuis `parkingState(t₀, m)` : τ = 0 et `d̂ = r̂₀` |
| 6 | `β = asin(ĥ·ûM)` recoupé contre `90° − angle(ĥ, ûM)` |
| 7 | un état portant une accélération non képlérienne fabriquée donne le **même τ** qu'un état construit depuis position et vitesse seules |

L'assertion 5 est celle qui dit que **L1 n'a pas déplacé la démo**. L'assertion 7 est celle sans
laquelle la régression du §2.3 est invisible.

**L'assertion 4 porte les 12 s du §2.2, et c'est la mesure qui l'a écrite ainsi.** Formulée
`τ ∈ [0, période)`, elle est fausse au bord : le cas qui l'exerce vraiment — un départ posé juste
après son point d'injection — rend **5 302,0 s contre 5 291,5 s de période**. Le test porte donc une
tolérance de 20 s et un fixture qui traverse réellement le wrap, faute de quoi l'assertion se serait
contentée de cas qui ne l'atteignent jamais.

**Un fixture qui impose β au centième de degré.** Faire tourner le normal de la démo d'un angle γ
autour de `ĥ × ûM` donne `ĥ' = ĥ·cos γ + ûM·sin γ`, donc `ĥ'·ûM = sin γ`. L'égalité `β = γ` est
exacte **à la date d'arrivée de la démo**, celle où `ûM` a servi à construire l'axe ; or
`departureFrom` relit `ûM` à la date d'arrivée qui suit le coast, jusqu'à 1,47 h plus tard, soit les
0,81° de mouvement lunaire déjà comptés au §2.2. Le résidu est de cet ordre : **mesuré 5,028° pour
γ = 5°, −4,971° pour γ = −5°, 20,027° pour γ = 20°**. Le test impose donc son désalignement à
±0,03° au lieu de le subir — ce qui distingue largement un β imposé d'un β fabriqué — et l'assertion
porte une bande plutôt qu'une égalité.

### 4.2 — Le cas volé, dans `TranslunarDepartureFlightTest`

Classe neuve, à côté de `LunarTransferFlightTest` — la même frontière rapide/lent que le dépôt tient
déjà. **Un seul cas** : γ = 5°, `departureFrom`, parking construit au point d'injection, `solve` à
100 km de périlune. Coût attendu, d'après L0 §6 : un `solve()` convergent vaut 4,2 à 4,6 s.

Ce qu'il assère : **la visée converge encore, et le périlune est dans la bande**. Le Δv et β sont
relevés et journalisés, **pas assertés** — le relief reste le critère de fermeture de L2.

**Pourquoi ce cas existe.** La convergence de la bissection sur un plan désaligné n'a jamais été
volée : `solve` a toujours travaillé à β = 0 par construction. C'est le risque neuf que L1 introduit,
et L0 a posé la discipline de le mesurer plutôt que de l'affirmer.

**Pourquoi cinq degrés.** C'est ce que L2 rencontrera **en convergeant**, pas à l'optimum. L0 §5
mesure le plancher de Canaveral à 0,146° sur le cycle de 18,6 ans, mais un tir hors fenêtre est
désaligné de bien davantage.

### 4.3 — Et deux tests passent sans édition

`LunarTransferFlightTest` et les cinq cas existants de `TranslunarInjectionPlanTest`. C'est le
critère du lot, au même titre que les tests neufs.

**Contrainte de méthode**, rappelée du découpage §3 : c'est l'utilisateur qui lance ces tests.

---

## 5. Limitations assumées

Six, toutes décidées pendant la conception, aucune découverte plus tard.

**1. Le demi-degré J2.** Le point volé manquera le point planifié d'environ 0,5° : la régression
nodale vaut ≈ −0,33 °/h à 185 km et 28,56°, soit ≤ 0,49° sur la révolution unique du coast, plus la
dérive de l'argument de latitude. Assumé, parce que les 170° sont un placement et non une contrainte
(§1.4), et parce que la forme close est ce qui garde le `evaluate` de L2 gratuit. **Chiffre calculé,
pas mesuré** — c'est L4 qui le mesurera en volant la chaîne.

**2. Aucune borne sur β.** L1 ne refuse jamais un plan désaligné, il le paie. Le refus reste à L2
(Kourou) et L5. Et le domaine est plus large que L0 ne le laisse voir : **L0 §5 a mesuré le plancher
sur la RAAN**, `max(0, |δ| − i)`, alors que la RAAN d'un plan subi est libre et que le plafond vaut
`i + |δ|` :

| Site | `i = φ` | plafond de β sur la RAAN |
|---|---|---|
| Kourou | 5,24° | 33,9° |
| Canaveral | 28,56° | 57,3° |
| Baïkonour | 45,97° | 74,7° |

À 74,7° la projection reste bien conditionnée (`|p| = cos β = 0,26`) mais l'angle de transfert 3D
vrai tombe à ~105° et l'injection devient absurdement chère. C'est le domaine que le balayage de L2
traverse, et il fallait qu'il soit écrit.

**3. `nRev = 0` et le seed toujours enfermé** dans la classe — découpage §6 pt 7, inchangé.

**4. Le drapeau posigrade dérivé n'est vérifié que par la géométrie fermée.** Aucun vol rétrograde
n'existe dans le dépôt, et L1 n'en crée pas.

**5. Deux façons de produire une orbite de parking coexistent** dans `TranslunarInjectionPlan` :
`parkingState` la fabrique, `departureFrom` la subit. C'est ce que le découpage demande, et **ça ne
se referme pas à la fin de `MIS-4`** : la démo `mission.lunarDemo` survit à L4.

**6. ToF à 4 j et angle à 170° restent des constantes couplées** — découpage §6 pt 1, non touché.

---

## 6. Ce que L1 lègue

**À `L2`, les trois pièces de son critère, toutes en forme close** : la durée de coast pour son
`t + coast + ToF`, `β` pour son terme de plan, et `keplerianInjectionDeltaV(parking, arrivée)` pour
son terme de Lambert. Son `evaluate` reste dans les microsecondes, comme les deux implémentations
existantes de `LaunchWindowProblem`.

**Et un avertissement mesuré sur la forme de ce critère.** Le découpage §4 écrit le coût de `L2`
`2·v·sin(θ/2) + Δv d'injection Lambert`, « le terme de plan porte le relief, le terme de Lambert
départage ». Le cas volé du §4.2 dément les deux moitiés de l'énoncé :

- **le terme de plan sous-lit le relief d'un facteur ~3.** À β = 5,02° l'injection close coûte
  **5 343 m/s contre 3 184 m/s** à β = 0, soit **+2 159 m/s**, là où `2·v·sin(β/2)` à 7 797 m/s n'en
  annonce que 682. Le mécanisme est celui du §2.1 : l'arc doit rejoindre une cible hors plan en
  170°, donc la rotation qu'il subit réellement vaut `asin(sin β / sin 170°) ≈ 30°` — une
  amplification de `1/sin 170° = 5,76`, d'autant plus forte qu'on est près de la singularité de
  l'angle de transfert ;
- **et les deux termes ne s'additionnent pas.** Le Δv de Lambert est déjà le coût complet, plan
  compris : il relie la vitesse de parking réelle à la vitesse d'injection réelle. Les sommer
  compterait le désalignement deux fois.

`L2` tranchera la forme qu'il donne à son coût ; ce que `L1` lui lègue, c'est le chiffre qui interdit
de reconduire celle du découpage sans la vérifier.

**À `L4`, un piège mesuré.** Le coast de parking ne peut **pas** être un `CoastingStage` ordinaire.
`CoastingStage` n'écrase pas `propagateStandalone`, qui retombe donc sur `MissionStage.enter`
(`:65`) — lequel **rend l'état inchangé, sans avancer d'une seconde**. Sur la passe d'optimisation
(`MissionOptimizer:228`), `TranslunarInjectionStage.enter` résoudrait alors l'injection depuis l'état
**à l'insertion en parking** : mauvaise phase, mauvaise date, et aucune erreur levée.

Le §2.3 du découpage décrivait ce mécanisme pour le coast **terminal**, où il est bénin — le mérite
d'une mission LEO ou GEO est l'orbite d'insertion. **Au milieu d'une chaîne il ne l'est pas.** L4 doit
livrer un coast de parking qui écrase `propagateStandalone` ; le précédent est `TranslunarCoastStage`,
la classe qui existe pour une ligne.

**À `L3` et à `L6`, rien.** Ils ne dépendent pas de ce lot, et l'ordonnancement du découpage §5 le dit
déjà.
