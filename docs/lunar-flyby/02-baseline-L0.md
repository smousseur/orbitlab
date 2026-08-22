# MIS-4 / L0 — Baseline mesurée

Lot **L0** du découpage (`01-decoupage.md` §4). Ce document ne contient aucune décision et aucune
ligne de production : il **consigne des chiffres**, mesurés avant que quoi que ce soit ne bouge,
pour que les lots suivants soient conçus sur des mesures et non sur des extrapolations.

Le découpage demandait quatre mesures. Il y en a cinq : la cinquième — la déclinaison lunaire
contre la latitude des pas de tir — a été ajoutée pendant la conception de ce lot, parce que la
chaîne que `L4` compose impose `i = φ du site` là où le plan d'injection porte une garde
`i ≥ |δ_Lune|`, et que personne n'avait comparé les deux.

**Quatre chiffres de ce relevé démentent un chiffre écrit ailleurs.** Ils sont rassemblés au §7.

---

## 1. Conditions de mesure

| | |
|---|---|
| Date | 2026-08-22, 19 h 12 – 19 h 14 |
| Commit | `68b721d` « Payload optimizer analysis » — arbre propre, plus la sonde non suivie |
| JDK | GraalVM 21.0.5, assertions actives (`-ea`) |
| Lancement | IntelliJ, une JVM, les trois mesures dans l'ordre 5 → 1 → 3 |
| Sonde | `LunarBaselineProbeTest`, `src/test/.../mission/maneuver/`, opt-in `orbitlab.probe` |
| Époque de mission | `2026-03-31T00:00:00.000Z` — celle sur laquelle `LunarTransferFlightTest` est épinglé |
| Graine | 42, explicite. Aucun étage n'est optimisable : CMA-ES ne tourne jamais |
| Durée totale | ~2 min de paroi |

**La sonde est jetable et doit être supprimée à la fermeture du lot.** Elle n'assère presque rien,
elle imprime. Le §4 du découpage l'exige : rien n'est commité dans `src/main`, et cette contrainte a
été tenue — y compris là où elle coûte quelque chose (§7, point 1).

Une seule chose y a été dupliquée depuis la production : le corps de
`TranslunarInjectionPlan.parkingState`, dont l'altitude est une constante alors que la mesure 1
existe pour la faire varier. Élargir la signature de production pour une sonde qui disparaît aurait
été le mauvais sens : c'est `L1` qui décidera de cette API, depuis une orbite de parking qu'il
**subit** plutôt qu'il ne fabrique.

---

## 2. Mesure 1 — la visée contre l'altitude de parking

`TranslunarInjectionPlan` est calibré à `PARKING_ALTITUDE = 185 km` et tous ses réglages — les
quatre jours de vol, l'angle de transfert de 170°, le bracket d'où part la bissection — y ont été
réglés. La question est de savoir si la chaîne lunaire doit emporter une constante à elle.

Cible : périlune 100 km. Époque 2026-03-31. Masse 1 700 kg, Isp du moteur de vaisseau.

| altitude | issue | Δv | périlune du plan | offset de visée | bissections | temps |
|---|---|---|---|---|---|---|
| **185 km** | convergé | 3 178,0 m/s | 100,4 km | 7 784 km | 11 | 8,5 s † |
| **250 km** | convergé | 3 161,4 m/s | 99,9 km | 7 673 km | 10 | 4,2 s |
| **300 km** | convergé | 3 148,8 m/s | 99,5 km | 7 590 km | 11 | 4,4 s |
| **400 km** | convergé | **3 124,0 m/s** | 99,9 km | 7 434 km | 12 | 4,6 s |

† rodage JIT : c'est la première propagation de la JVM. Le régime est à 4,2–4,6 s.

**Verdict : la chaîne lunaire n'a pas besoin d'une constante d'altitude à elle.** Les quatre
altitudes convergent de la même façon, en un nombre de bissections identique à une unité près, et
la visée atterrit dans le kilomètre autour de la cible partout. Le plafond de 400 km de
`MissionComposer.parkingAltitudeFor` peut donc être traversé sans précaution — ce que la
justification du découpage annonçait comme incertain (§7, point 5).

**Ce qui bouge avec l'altitude, c'est le coût.** 54 m/s séparent 185 km de 400 km, en faveur du plus
haut : la vitesse déjà acquise sur l'orbite de parking est plus grande que l'énergie potentielle
qu'il a fallu payer pour l'atteindre, sur cette plage. Le chiffre est *observé, pas asserté*, et il
ne dit rien du bilan complet — le coût d'ascension jusqu'à 400 km n'est pas dans ce tableau.

**L'écart plan / vol, lui, ne dépend pas de l'altitude** : la graine képlérienne à deux corps rate
son point de visée de **26 320 à 26 359 km** sous le modèle 8×8 + Lune + Soleil, soit 39 km d'écart
sur les quatre lignes. C'est le correcteur différentiel qui referme cet écart, pour 254 à 257 m/s
sur la vitesse d'injection.

---

## 3. Mesure 2 — les sites Terre-en-dur sur le chemin

Lecture du graphe d'appels, sans exécution. Les onze sites recensés par
`multi-corps/03-conception-L1.md` §4.1, rangés pour la chaîne que `L4` compose : ascension →
insertion en parking → coast de parking → TLI → coast translunaire.

Le compte n'est pas la question. La question est en trois catégories : **hors chemin**, **sur le
chemin et légitimement terrestre** (l'ascension part bien du sol terrestre, y lire la Terre est
juste), et **sur le chemin et appliqué à un arc lunaire**, seule catégorie qui déplacerait du
travail vers un lot antérieur.

| Site | Sur le chemin ? | Verdict |
|---|---|---|
| `EarthMission:65` — µ de l'état initial | oui, `L4` décolle | **Terre légitime** |
| `LaunchPlane:157` + `launchAzimuth` | oui | **Terre légitime** ; son propre javadoc l'annonçait |
| `GravityTurnConstraints:70`, `GravityTurnProblem:260` | **oui** | **Terre légitime**, mais voir ci-dessous |
| `PropellantBudget:44-45` | oui en `L5`, via `MissionFactory` | **Terre légitime** tant que le budget ne dimensionne que l'ascension |
| `StageEndStateDiagnostic:22-23` | oui | **légitime par géométrie** : le dernier étage propulsé de `L4` est le TLI, qui sort géocentrique |
| `AchievedOrbit:61` | oui | **déjà réparé** par `PHY-4 / L6` : lit le µ de l'état |
| `OrbitElements:130` — `RE` terrestre | oui, via `AchievedOrbit.of` | **légitime par accident**, voir §7 point 4 |
| `Physics:206`, `:247`, `hohmannTransferDuration` | non | hors chemin |
| `MissionHorizon:141` — `Revolutions` | non | hors chemin : `L4` vole un `FixedDuration` |
| `DynamicParameters:135` — période affichée | non | hors chemin, **et conditionnellement** : voir §7 point 4 |

**Réponse au deuxième risque du §5 du découpage : aucun travail ne se déplace de `L4` vers un lot
antérieur.** Aucun site n'est appliqué à tort à un arc lunaire.

**Une correction à `L1` §4.1.** Ce tableau rangeait `GravityTurnConstraints` et `GravityTurnProblem`
hors chemin au motif que « le §1 du découpage met l'optimisation multi-arcs hors `PHY-4` ». Le motif
tombe : `GEOMission:208` appelle `GravityTurnConstraints.forTarget`, le virage gravitationnel est un
`OptimizableMissionStage`, donc `MIS-4` **vole** CMA-ES sur son ascension. Le verdict, lui, tient —
ces deux sites lisent la Terre pour une ascension terrestre.

---

## 4. Mesure 3 — la sortie de la sphère d'influence

Cinq époques d'une lunaison, horizon porté à 10 j, tout le reste inchangé. Rayon de la sphère
lunaire à l'époque : **66 539 km**.

| jour | entrée | **sortie** | séjour | périlune | max sur l'arc lunaire | dernier arc | points | sommets |
|---|---|---|---|---|---|---|---|---|
| 0 | 3,08 j | **4,59 j** | 36,2 h | 100,4 km | 67 348 km | `EARTH` | 14 467 | 7 236 |
| 6 | 3,10 j | **4,58 j** | 35,5 h | 101,2 km | 67 676 km | `EARTH` | 14 466 | 7 236 |
| 12 | *refusé* | — | — | plancher **1 873 km** | — | — | — | — |
| 18 | 3,16 j | **4,51 j** | 32,4 h | 99,4 km | 61 711 km | `EARTH` | 14 466 | 7 236 |
| 24 | 3,12 j | **4,62 j** | 36,0 h | 100,6 km | 65 972 km | `EARTH` | 14 467 | 7 236 |

Les quatre vols sont `complete=true` et atteignent leur horizon à la seconde.

**La sortie arrive à 4,51–4,62 j.** L6 avait mesuré l'entrée et le périlune puis *extrapolé* « pas de
sortie avant 5,5 j » ; l'extrapolation est fausse d'environ un jour. Le séjour dans la sphère mesure
**32 à 36 h**, et non les 54 h que `multi-corps/06-conception-L4.md` §11.2 avait mesurées sur une
autre géométrie.

**Conséquence immédiate sur la démo.** L'horizon de 4,5 j de `LunarTransferMission` tombe entre
**14 min et 2,9 h avant la sortie** selon l'époque — 2,2 h à celle que `LunarTransferFlightTest`
épingle. La séquence `[EARTH, MOON]` que ce test assère est donc vraie, mais par une marge qui n'est
pas celle que le javadoc de `MISSION_DURATION_SECONDS` annonce (§7, point 1).

**Conséquence sur `L3`, et c'est la plus importante.** Le dernier arc est `EARTH` sur les quatre
vols. `MissionLoadEvaluator.objectiveMet` ne mesure que l'arc dans lequel le coast terminal
**finit** (`finalCoastArcBody`, réparé par L6 §5.2) : avec l'horizon de ~7 j que `L4` prévoit, cet
arc est `EARTH`, et **l'arc lunaire n'est jamais regardé**. `FlybyObjective` doit donc sélectionner
son arc **par corps**, pas par position dans la séquence. C'est une correction au mécanisme décrit
au §2.3 du découpage, pas au diagnostic (§7, point 3).

**Le maximum sur l'arc lunaire** vaut 61 711 à 67 676 km, ce qui confirme l'ordre de grandeur du
§2.3 — l'altitude d'entrée dans la sphère, sur laquelle un objectif d'insertion classique noterait
un apogée. Deux valeurs dépassent le rayon de sphère lu à l'époque : la sphère respire avec la
distance Terre–Lune, et la sortie est détectée sur un rayon élargi par la bande morte ε.

**La trace est décimée d'un facteur 2** : 14 467 points pour 7 236 sommets, contre le budget de
8 192 de `TrajectoryPolyline`. À 1 447 points par jour, un horizon de 7 j donne ~10 130 points — ce
que le §6 pt 12 du découpage annonçait à quelques dizaines près.

**Un refus sur cinq époques**, au jour 12 : le plancher de périlune atteignable y vaut **1 873 km**
contre les 100 km demandés. C'est le comportement voulu — un refus explicite plutôt qu'une
trajectoire qui n'est pas celle qu'on a demandée — et c'est aussi la démonstration que le
`confirm()` de `L2` a un travail réel à faire : une date de tir sur cinq n'est pas volable à cette
cible.

---

## 5. Mesure 5 — la déclinaison lunaire contre les latitudes de site

Aucune propagation : éphéméride lunaire et trigonométrie sphérique, 47 s dont 44,5 pour le balayage
long.

Un plan d'inclinaison `i` contient les directions de déclinaison `±i` et pas au-delà. Un plan de
parking qui doit contenir la direction de la Lune à l'arrivée exige donc `i ≥ |δ|` — c'est la garde
de `TranslunarInjectionPlan.transferPlaneNormal`, et la raison pour laquelle sa constante vaut 30°
et non une latitude de tir. La chaîne que `L4` compose prend `i = φ du site`.

Quand `i` ne suffit pas, l'écart `|δ| − i` est l'angle minimal entre le plan atteignable et la
direction de la Lune, et il se paie en changement de plan à la vitesse de parking —
**7 793,2 m/s à 185 km**.

**Déclinaison extrême mesurée** : **28,415°** sur 2026 (le 26 février), **28,708°** sur un cycle de
18,6 ans (le 2043-09-12).

| Site | `i = φ` | pire désalignement 2026 | coût | pire sur le cycle | coût |
|---|---|---|---|---|---|
| Kourou | 5,236° | 23,179° | **3 131,3 m/s** | 23,472° | **3 170,3 m/s** |
| Canaveral | 28,562° | 0,000° | 0,0 m/s | **0,146°** | **19,9 m/s** |
| Tanegashima | 30,400° | 0,000° | 0,0 m/s | 0,000° | 0,0 m/s |
| Vandenberg | 34,632° | 0,000° | 0,0 m/s | 0,000° | 0,0 m/s |
| Baïkonour | 45,965° | 0,000° | 0,0 m/s | 0,000° | 0,0 m/s |

**Canaveral tient, et son plancher est chiffré.** Le choix d'Apollo laisse 0 m/s sur toute l'année
2026 et **19,9 m/s au pire du cycle de 18,6 ans**. C'est le même genre de plancher
qu'`EarthLaunchWindowProblem` porte déjà — 1,1 m/s depuis Kourou, 35,9 m/s depuis un pas de tir à
45° — donc un terme que le critère de `L2` doit connaître, et non un obstacle. Il ne déplace pas le
minimum du critère, il décale la courbe.

**Le taux d'atteignabilité**, sur une lunaison depuis l'époque épinglée, pas de 10 min :

| Site | fraction de la lunaison où la déclinaison tient dans `i` | plus longue opportunité continue |
|---|---|---|
| Kourou | **12,5 %** | **41,33 h** |
| Canaveral, Tanegashima, Vandenberg, Baïkonour | 100 % | toute la lunaison |

**Kourou est atteignable 12,5 % du mois, par fenêtres allant jusqu'à 41 h** — soit environ
3,4 jours par lunaison, en deux paquets autour des passages de la Lune par le plan équatorial. Ce
n'est pas « quelques heures par mois » (§7, point 2).

---

## 6. Mesure 4 — les temps de paroi

| | mesuré |
|---|---|
| Un `solve()` convergent | **4,2 – 4,6 s** en régime (8,5 s au premier, rodage JIT) |
| Un `solve()` refusé | **3,9 s** |
| Un vol complet, horizon 10 j | **9,2 – 11,8 s** |
| Le balayage de déclinaison sur 18,6 ans | 44,5 s |

**Un vol complet coûte deux `solve()`, pas un.** Les journaux le montrent : la marche d'étages de
`MissionOptimizer` appelle `propagateStandalone` → `enter()`, puis la passe d'éphéméride refait le
même chemin par `StageChainRunner`, avec des traces de bissection identiques au kilomètre près.
`TranslunarInjectionStage` n'a pas de branche de rejeu, donc rien ne mémorise le plan. Le §5 du
découpage écrit « `L4` en appelle un par vol » : c'est un facteur 2.

**Ce que `L2` peut se permettre.** Un `confirm()` coûte un `solve()`, soit ~4,5 s. Un balayage
horaire sur 26 h qui confirmerait **tous** ses candidats coûterait ~2 min ; un qui ne confirme que
l'optimum raffiné coûte 5 s. La contrainte est donc molle, et le risque que le §5 du découpage
appelait « le risque principal » n'en est pas un : **`L2` peut confirmer largement**. Le chiffre à
surveiller n'est pas le `confirm()` mais le vol de `L4`, qui paiera 2 × 4,5 s à chaque composition.

---

## 7. Ce que ces chiffres corrigent

Cinq énoncés écrits ailleurs, que la mesure dément. Ils sont ici pour qu'aucun ne soit recopié.

**1. « An exit cannot happen before ~5,5 d » — `LunarTransferMission.MISSION_DURATION_SECONDS`.**
Mesuré : 4,51 à 4,62 j. Le javadoc justifie l'horizon de 4,5 j par une marge d'un jour qui n'existe
pas ; la marge réelle va de **14 min à 2,9 h** selon l'époque. Le même javadoc s'appuie sur un séjour
de 54 h dans la sphère (`L4` §11.2) là où la mesure donne 32 à 36 h.
**Décidé : consigné, pas corrigé.** `L0` reste à zéro ligne de production, et c'est `L4` qui
corrigera le javadoc en même temps qu'il posera son propre horizon — un seul changement de
comportement à la fois, et la correction arrive avec son contexte.

**2. « 5,24° ne contient la Lune que quelques heures par mois » — découpage §6 pt 3.** Mesuré :
**12,5 % d'une lunaison, en fenêtres jusqu'à 41,33 h**, soit ~3,4 jours par mois. L'énoncé est faux
d'un facteur ~20.
**Décidé : le refus de Kourou tient, son motif est réécrit.** Ce qui le justifie n'est pas la rareté
de la fenêtre mais **le coût hors fenêtre** — 3 131 m/s en 2026, 3 170 m/s au pire du cycle. Le
comportement prévu par `L2` et `L5` ne bouge pas ; seule sa raison devient vraie. Que Kourou garde
une capacité étroite et réelle devient une limitation assumée, réouvrable par un lot ultérieur.

**3. « Le max est l'altitude d'entrée dans la sphère » — découpage §2.3.** Le diagnostic est juste
(61 711 à 67 676 km mesurés) mais le mécanisme a changé sous lui : `objectiveMet` **ne mélange plus
les arcs** depuis L6 §5.2, il ne mesure que l'arc où le coast finit. Et comme la sortie de sphère
arrive avant l'horizon de `L4`, cet arc est `EARTH` : la faille n'est pas un maximum aberrant, c'est
que **l'arc lunaire n'est pas mesuré du tout**. `FlybyObjective` doit sélectionner par corps.

**4. « Hors chemin » — `multi-corps/03-conception-L1.md` §4.1, deux entrées.**
`GravityTurnConstraints` et `GravityTurnProblem` **sont** sur le chemin d'un vol `MIS-4` : le motif
invoqué (« l'optimisation multi-arcs est hors `PHY-4` ») ne dit rien de l'ascension, qui est
optimisée. Le verdict tient, le motif est faux. Deux autres entrées sont justes pour une raison plus
fragile qu'il n'y paraît, et qui doit être écrite comme telle :
- `OrbitElements:130` soustrait le rayon **terrestre** aux apsides, et `AchievedOrbit.of` est bien
  appelé sur le chemin — mais la marche d'étages s'arrête après le TLI, donc l'état est géocentrique
  et le calcul est juste. Il devient faux dès qu'un étage propulsé lunaire existe : **`MIS-5`**.
- `DynamicParameters:135` reste hors chemin **tant que le profil lunaire garde un horizon
  `FixedDuration`**. C'est une contrainte que `L5` hérite, pas un choix libre.

**5. « `parkingAltitudeFor` plafonne à 400 km » — découpage §4, mesure 1.** La justification est
inexacte : ce plafond n'a que deux appelants (`MissionComposer.composeHighOrbit` et
`MissionFactory:187`), et la chaîne que le découpage écrit lui-même pour `L4` pose **185 km en dur**
dans `AnalyticParkingInsertionStage`. Le 400 km n'arriverait que si `MissionSpec.Lunar` choisissait
de réutiliser cette aide. La mesure reste utile — et son résultat, §2, est que ce choix est libre.

---

## 8. Observations gratuites

Trois choses vues dans les journaux, qui ne changent rien à `L0` mais qui ont un lecteur ailleurs.

**Le bracket dégénéré coûte 20 bissections pour rien.** Au jour 12, le bracket se referme sur
`[1 738, 1 738] km` après huit resserrements, puis vingt bissections tournent sur un intervalle de
largeur nulle en réaffichant le même 1 873 km. C'est ~2,7 s des 3,9 s du refus. Un test de largeur
nulle rendrait le refus quasi instantané, ce qui intéresse `L2` : son `confirm()` refusera souvent.

**Le plancher de périlune du jour 12 vaut 1 873 km, pas 135 km.** Le javadoc de
`LunarTransferFlightTest.theAimConvergesOrRefusesAcrossALunarMonth` annonce « measured at 135 km at
one epoch of this month » sur le même échantillon d'époques. Les deux chiffres ne peuvent pas être
vrais du même vol ; celui de ce relevé est daté et reproductible, l'autre ne l'est pas. Non résolu.

**Le correcteur différentiel d'Orekit n'a pas rendu son chiffre au jour 6**
(`NullPointerException`), cas déjà prévu et journalisé par le plan : seule la ligne de diagnostic
« plan versus flight » perd sa conversion en m/s, aucun vol ne s'en sert.

Pour référence, l'ellipse translunaire atteinte à l'époque épinglée, telle que la passe
d'optimisation la rapporte : **169 528 × 433 293 919 m**, `e = 0,9707`, `i = 30,0000°`, Δv 3 178 m/s,
masse 1 700 → 577 kg, résidu 77 kg (6,4 % de la charge). L'orbite moyenne est *unavailable*, ce qui
est attendu à cette excentricité.

---

## 9. Ce que cette baseline ne dit pas

- **Rien sur un vol depuis le sol.** Aucune chaîne ascension → parking → TLI n'existe avant `L4` ;
  les temps de paroi du §6 sont ceux d'une mission qui **part déjà en orbite de parking**. Un vol
  `L4` y ajoutera l'ascension et son CMA-ES, non mesurés ici.
- **Rien sur une trajectoire capturée.** Les cinq vols sortent de la sphère ou refusent. La
  calibration de ε réclamée par `PHY-4 / L6` §5.5 reste l'échéance de `MIS-5` — mais ce relevé
  apporte la moitié qui manquait : **la sortie est volée**, quatre fois, ce qu'aucun lot de `PHY-4`
  n'avait fait.
- **Rien sur la reproductibilité.** Une seule exécution, une seule JVM. Les temps de paroi sont
  *observés*, jamais assertés, et le premier `solve()` de chaque JVM paiera son rodage.
- **Rien sur les autres cibles de périlune.** Tout est mesuré à 100 km. Le plancher atteignable
  varie fortement avec l'époque — 1 873 km au jour 12 — et sa dépendance à la cible n'est pas
  balayée.
