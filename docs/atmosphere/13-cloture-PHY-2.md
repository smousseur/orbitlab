# PHY-2 — Clôture

Ce document ferme le chantier. Il ne réécrit pas ce que
[`06-decoupage-PHY-2.md`](06-decoupage-PHY-2.md) à
[`12-conception-L5-PHY-2.md`](12-conception-L5-PHY-2.md) disent déjà : il énonce ce que le
chantier rend vrai, ce que la mesure y a démenti, **ce qu'il ne livre pas**, et il consigne
la remarque du 2026-09-12 — faire de l'atmosphère une option — qui est hors de son périmètre
et trouve sa place en `PHY-3`.

---

## 1. Ce que le chantier rend vrai

Le découpage promettait d'*« allumer l'atmosphère par défaut, et payer tout ce que `PHY-1`
avait délibérément différé »*. C'est fait, sous cette forme :

- **une mission créée au wizard vole sous atmosphère** — `MissionFactory.DEFAULT_ATMOSPHERE
  = NRLMSISE`, l'unique origine d'un spec de production ; un spec monté à la main (tests,
  fixtures) reste à `NONE`, et c'est précisément ce qui fait que la bascule n'a re-baseliné
  aucun gate ;
- **une ascension drag-on termine** — l'arrêt d'altitude drag-conditionnel de `L1`, qui ferme
  [`BUG-10`](../bugs.md) et devient au passage le mécanisme de terminaison que `MIS-10`
  réclame ;
- **le bloc bas du Falcon Heavy ne double-compte plus sa traînée** — Isp `296 → 298`, dette
  résiduelle contre le vide **343 m/s**, consignée et non silencieuse ;
- **l'ascension a la main sur son corps central** — la coupure du cœur câblée sur
  `transitionTime` (`L3`), ce qui **répare** [`BUG-25`](../bugs.md) sans que le poids
  d'apogée ait eu à bouger ;
- **l'étage supérieur est dimensionné sur ce qu'il dépense** et non sur le pire cas —
  `MeasuredLoadPlanner` (`L4`) : **9 412 → 1 275 kg**, −86,4 % sur le Falcon Heavy LEO-400,
  en deux passes et ~5 s de surcoût ;
- **un scénario dont l'atmosphère n'est pas `NONE` se recharge** — `REL-22`, levée par `L5` ;
- **et le drag-on coûte ×2,4, pas ×7,5** — au niveau d'un calcul complet, ce qui **désarme**
  l'escalade que `L1` avait armée.

**Vérification finale.** `pmdMain` / `pmdTest` / `spotlessApply` verts, compile main + test,
fixtures rapides vertes. Les **quatre gates drag-off à tolérance `0.0`** et
`DefaultAtmosphereCostFlightTest` ont été relancés **par l'utilisateur après le correctif
`L4`** de [`12` §8.2](12-conception-L5-PHY-2.md) — verts (2026-09-12). L'essai manuel au
runtime est concluant sur le Falcon Heavy LEO drag-on.

---

## 2. Le nombre que chaque lot a posé

| Lot | Ce qu'il rend vrai | Le nombre qu'il pose |
|---|---|---|
| `L0` | La photo « avant », sans mesure neuve | Harris-Priester vaut sur **[100 km, 1 000 km]** et **lève** sous 100 km : aucune ascension ne peut le voler |
| `L1` | Une ascension drag-on terminable, `DT-14` amendé | Le surcoût drag-on est **×7,5** (52,8 s contre ~7 s), non le +50 % annoncé |
| `L2` | La traînée réelle, mesurée sur banc jetable | **51 m/s** (Falcon Heavy) et **230 m/s** (Ariane 64) — l'**inverse** des 396/64 que `DT-13` portait |
| `L3` | L'autorité sur le cœur, l'Isp posée, `DT-15` re-vérifié | Isp **298** ; `BUG-25` fermé à **`W_APOGEE_OVERSHOOT = 0,5` inchangé** ; airstart S2 à **~35 km**, en continu, assumé |
| `L4` | Le dimensionnement mesuré en vol | **−86,4 %** sur l'étage supérieur (9 412 → 1 275 kg), orbite déplacée de **4 m** |
| `L5` | La bascule du défaut, `REL-22`, les trois termes drag-conditionnels | **399,5 × 420,3 km** drag-on contre **399,6 × 420,4** drag-off, ratio **×2,4** |

---

## 3. Ce que la mesure a démenti

Neuf énoncés du découpage ou des registres sont tombés en volant. C'est le rendement le plus
net du chantier, et la raison pour laquelle chaque lot a mesuré avant de construire.

| L'énoncé | Ce que la mesure a rendu |
|---|---|
| `DT-13` : traînée implicite de **396 m/s** (FH) / **64 m/s** (Ariane) | **51** / **230** — le classement est inversé ; 396 et 64 étaient le *déficit d'Isp*, pas la traînée (`L2`) |
| `DT-14` : « optim toujours Harris-Priester » | HP **lève à 0 km** : il ne vole pas une ascension. Candidat `a`, zéro câblage (`L1`) |
| `DT-15` : escalader vers un `Cd` par régime si l'airstart est en continu | Le critère est **rempli** (~35 km) et la mesure le **désarme** : le B-check passe avec `Cd = 2,2`, conservateur d'un facteur ~5,5 (`L3`) |
| `DT-19` : la réserve sur-provisionne de **+18 à +66 %** | Facteur **7,4** — et `dvTop` sur-provisionne **2,2×** à lui seul ; la réserve n'en est que les trois quarts (`L4`) |
| `DT-21` : le poids d'apogée doit être rééquilibré | **Inutile** : l'optimiseur va en région B seul dès qu'il commande le cœur (`L3`) |
| Découpage : « relever le `periapsisFloor` » | Il fallait le **retirer** sous traînée. Relevé, il est inatteignable ; laissé, il taxait **1 605,5 des 1 605,8** du seul hand-off qui vole (`L5` §7.4) |
| Découpage : « absorber les pertes dans `dt1MaxPhysical` » | La borne **est déjà** l'enveloppe d'extinction ; l'énoncé ne mord pas (`L5` §2.2) |
| Découpage : « suite complète re-baselinée drag-on » | **Faux** : les gros gates montent leur spec à la main et restent drag-off (`L5` §2.3) |
| `L1` : le ×7,5 est un risque pour la bascule | **×2,4** au niveau d'un calcul complet — le dimensionnement dilue le facteur par trois (`L5` §7.7) |

Deux erreurs de raisonnement, faites puis retirées pendant le chantier, sont consignées dans
[`12` §7.4 et §8.1](12-conception-L5-PHY-2.md) : une conclusion d'infaisabilité tirée de la
convergence de CMA-ES — la convergence dit où la recherche va, pas ce que le modèle peut
produire — et un dossier contre `buildInitialGuess()` qu'une sonde a démenti en 21 s.

---

## 4. La remarque du 2026-09-12 : l'atmosphère comme option

> *« Dans une utilisation très simple, le user n'a pas besoin de l'atmosphère. À la limite,
> on pourrait en faire une option activée par défaut (à voir pour la valeur par défaut). »*

**Hors périmètre de `PHY-2`, et déjà domiciliée.** Le découpage §1 range explicitement le
*« sélecteur Off / Statique / Réaliste au wizard »* dans `PHY-3` : `PHY-2` rend le modèle
*vivant*, il n'expose rien de neuf à l'écran. La remarque ne rouvre donc pas ce chantier —
elle **relève la valeur** de l'item suivant, et pose une question que `PHY-3` devra trancher.

**Ce qui est déjà en place**, et qui rend l'item petit :

- `AtmosphereModel` porte les trois valeurs `NONE` / `HARRIS_PRIESTER` / `NRLMSISE` ;
- `MissionSpec` porte le champ et son `withAtmosphere()`, symétrique de `withLauncherLoads()` ;
- le scénario le persiste et le restaure (`REL-22`) ;
- et le défaut vit à **un seul endroit**, `MissionFactory.DEFAULT_ATMOSPHERE`.

Il ne manque donc que **le champ dans `StepParameters`** et **la valeur initiale** — pas un
câblage.

**Ce que la mesure apporte au choix du défaut**, et qui n'existait pas avant ce chantier :

| | drag-off | drag-on | écart |
|---|---:|---:|---|
| Calcul complet, Falcon Heavy + 10 t @ 400 km | **73–80 s** | **183–192 s** | **×2,4** d'attente |
| Ariane 64 + 10 t @ 400 km, masse au but | 16 077,7 kg | 19 608,4 kg | **+3 532 kg** de masse morte |
| Orbite atteinte | 399,6 × 420,4 km | 399,5 × 420,3 km | indiscernable à l'œil |

**L'argument de la remarque est donc mesuré, et il est fort** : aujourd'hui la traînée coûte
un facteur 2,4 d'attente pour une orbite que l'utilisateur ne distingue pas, et **elle n'a
aucune sortie visible** — ni `Q(t)`, ni traînée instantanée, ni marque d'interface. Ce qu'elle
achète est de la **justesse** (un gravity-turn sans air atteint son apogée avec moins d'ergols
qu'un vrai lanceur, ce qui est la raison d'être du chantier) et de l'**honnêteté catalogue**
(la baisse de capacité de l'Ariane, §5.1). Les deux sont invisibles tant que rien ne les
montre.

**Recommandation : poser la valeur par défaut *avec* `PHY-3`, pas avant.** C'est `PHY-3` qui
donne à la traînée une sortie visible ; le jour où le profil `Q(t)` existe, payer ×2,4 cesse
d'être un coût sans contrepartie perceptible, et le défaut se tranche sur un usage réel plutôt
que sur une intuition. D'ici là le défaut reste `NRLMSISE` — c'est ce que `L5` a livré et
mesuré — et l'opt-out existe déjà par le scénario, pas encore par l'écran.

**Un piège du sélecteur, à ne pas livrer tel quel.** Les trois valeurs promises par la fiche
`PHY-3` sont `Off` / `Statique` / `Réaliste`, soit les trois de l'énumération. Or `L1` a
mesuré que **Harris-Priester lève sous 100 km**, et rien dans le code ne l'interdit à une
mission : `MissionStage.flightContext` monte le modèle du spec tel quel. Un utilisateur qui
choisirait « Statique » sur une mission d'ascension obtiendrait une exception d'intégration,
pas une simulation dégradée. Le sélecteur doit donc soit n'offrir que deux valeurs sur un
profil qui part du sol, soit substituer, soit refuser à la saisie — c'est une décision de
`PHY-3`, et elle n'est pas dans sa fiche.

---

## 5. Ce que le chantier ne livre pas

### 5.1 Un livrable déclaré de `L2` qui n'a pas été posé

`L2` a tranché **`A` pour l'Ariane 64** ([`09` §3.2](09-conception-L2-PHY-2.md)) : *« le
Vulcain va à son lapse physique — sa moyenne thrust-weighted sol/vide, dans [320, 431], à
poser en volant, sans plus la tirer vers le bas pour le ratio »*, la traînée devenant
explicite et la capacité baissant d'environ **166 m/s** qu'aucune Isp ne restaure.

`L3` ne l'a pas implémenté et l'a **reporté à `L5`** — sa §1 range *« la matérialisation de la
baisse Ariane »* dans `L5`, et la fiche [`DT-13`](../dette-technique.md#dt-13--isp-catalogue-déjà-en-double-comptage-latent-avec-la-traînée-à-venir)
l'écrit noir sur blanc : *« l'Ariane Vulcain (décision `A`) n'est pas touché ici : il reste à
360 et part à `L5` »*. **`L5` ne l'a pas porté** : le lot a basculé le défaut, levé `REL-22`
et posé les trois termes de coût, sans toucher au catalogue — le mot « Vulcain » n'apparaît
dans aucun des documents `L3`, `L4` et `L5`.

**État réel du catalogue** : `Launchers` ligne 181, `new PropulsionSystem(360, 1_118_000)` —
la valeur que `L2` décrit comme **tirée vers le bas pour caler le ratio FH/Ariane à 2,10**,
pas comme un lapse physique. Conséquence : l'Ariane vole drag-on avec une Isp que le chantier
a lui-même qualifiée d'artefact de calibration, et `DT-13` n'est honnêtement fermée que sur sa
moitié Falcon Heavy. C'est un **reste de `PHY-2`**, pas une dette héritée.

**Mesuré le 2026-09-12, et la question se referme.**
`LauncherDragConvergenceProbe.ariane64_coreIspSweep_whatTheVulcainLapseWouldBuy` vole le profil
à `360` (catalogue), `395` et `431` — le pur vide, qu'aucun lanceur ne dépasse — **sans toucher
au catalogue**, en reconstruisant le `LauncherModel` avec l'Isp voulue sur l'étage `CORE` :

| Isp cœur | orbite atteinte | charge S2 retenue | résidu S2 | masse finale |
|---:|---|---:|---:|---:|
| **360** (catalogue) | 399,3 × 420,3 km | **3 804 kg** | **92,8 %** | 19 608,4 kg |
| **395** | 399,3 × 420,3 km | **215 kg** | 4,9 % | 16 087,3 kg |
| **431** (pur vide) | 399,3 × 420,3 km | 353 kg | 47,3 % | 16 243,8 kg |

**L'orbite ne dépend pas de l'Isp du cœur** : la mission ferme, à la décimale, dans les trois
cas. Le lanceur n'est donc pas bridé par son `360` sur ce profil, et relever l'Isp n'achète
**aucune capacité mesurable** : au plafond absolu (431, le pur vide) le résultat est *moins*
bon qu'à 395. Il n'y a pas de pente à suivre — le lapse du Vulcain reste une question
d'**honnêteté du catalogue**, plus une question de performance, et son prix (re-baseliner tous
les profils Ariane, gates MEO et polaire compris) est sans contrepartie volée. **Il se verse en
dette**, et c'est maintenant une conclusion mesurée et non un défaut d'arbitrage.

**Une hypothèse à moi, écrite ici la veille et démentie par ces trois vols.** J'avais écrit que
l'Ariane était « pénalisée deux fois » et que l'Isp était *« le seul candidat sérieux pour
expliquer les +3 532 kg »* de §5.3. C'est faux dans le mécanisme : si c'était un déficit de
capacité, 431 serait le meilleur des trois, et il est le deuxième. Ce que l'Isp déplace, c'est
la **convergence de la boucle de dimensionnement** (§5.3), pas la capacité du lanceur.

**Limite de l'instrument, à garder en tête** : les charges de départ des trois vols sont celles
que `PropellantBudget` a dimensionnées à l'Isp catalogue. C'est ce qui rend les trois points
comparables — une seule variable bouge — mais un catalogue réellement à 395 partirait d'une
autre graine et pourrait converger autrement. Ce que la sonde établit de façon robuste, c'est
que l'orbite est atteinte dans tous les cas ; le reste est une mesure de sensibilité.

### 5.2 Ce qui n'a jamais volé

1. **GEO et elliptique drag-on** ([`12` §7.7](12-conception-L5-PHY-2.md)). Le plancher
   d'altitude de hand-off les concerne : un parking GEO à 200 km plafonne la fenêtre d'apogée
   à 200 km, et un hand-off à 100 km pourrait s'y révéler incompatible.
2. **`EarthOrbit` au-dessus du seuil de parking.** Le dimensionnement deux passes s'applique à
   tout `MissionSpec.EarthOrbit` — chaîne parking comprise, donc les MEO créées depuis le type
   LEO du wizard. Le balayage de `L5` §8.1 est allé à 1 500 km ; au-delà, jamais.
3. **Le lanceur qu'un lot ne mesure pas n'est pas le lanceur qu'il ne touche pas.** Le paquet
   de `L5` ne contraint que le Falcon Heavy ; l'Ariane rend la main 165 km plus haut sans
   qu'on le lui demande. Les deux sondes qui l'ont établi restent dans le dépôt, gatées
   `orbitlab.probe`.

### 5.3 Deux défauts vus et non réparés

- **`AnalyticHohmannTransferStage.insertThenTransfer` abaisse sous traînée l'orbite qu'elle
  veut relever** : `−11,7 × 1 093 km → −32,4 × 532 km` mesuré pendant `L5`. La branche n'est
  plus atteinte depuis que le hand-off est redressé, mais elle n'est pas corrigée. Mérite une
  fiche `bugs.md`.
- **La boucle de dimensionnement n'est pas stable, et c'est elle qui coûte les 3 532 kg.** Le
  balayage d'Isp de §5.1 l'a mise à nu : sur **le même lanceur, le même profil et la même
  cible**, une perturbation de 10 % de l'Isp du cœur fait passer la charge S2 retenue de
  **3 804 kg à 215 kg — un facteur 17,7** — pour une orbite identique à la décimale. Trois
  points, trois régimes : à 360 la boucle diverge (66,5 % → 92,8 % → 0,0 %) et le repli de
  `L4` rend la passe à 92,8 %, à 395 elle converge dans la bande (4,9 %), à 431 elle finit
  au-dessus (47,3 %). La bande `[1 %, 25 %]` n'est atteinte que dans **un cas sur trois**.

  **Et les deux dernières passes encadrent la réponse.** À 360, la passe 2 charge 3 804 kg et
  n'en brûle que 274 ; la passe 3 charge 246 kg et finit à sec. La bonne charge est donc dans
  `[246, 3 804]`, et près de **~290 kg** — ce que le vol à 395 confirme indépendamment
  (215 kg, résidu 4,9 %).

  **Réparé le 2026-09-12**, l'encadrement étant la seule information que l'échec produise.
  `MeasuredLoadPlanner` garde désormais la charge la plus riche qui a mis l'étage à sec en
  plus de la moins chère qui ne l'a pas fait, et **dès que les deux extrémités existent, elles
  gouvernent** : la formule de ΔV est mise de côté, parce qu'elle extrapole depuis une
  trajectoire que la charge suivante va changer, là où l'encadrement ne dit que ce qui a été
  volé. La bisection est **géométrique** — une charge d'ergols est une échelle, et l'encadrement
  qu'une boucle en échec ouvre couvre un ordre de grandeur : c'est le *rapport* qui doit
  diminuer, pas la largeur.

  Le budget est en deux moitiés, et c'est ce qui rend la réparation gratuite pour les profils
  sains : `MAX_SIZING_PASSES = 3` reste ce que les passes pilotées par la formule dépensent, et
  `MAX_BRACKET_PASSES = 3` n'est ouvert **que** par un extinction — un profil qui converge dans
  la bande n'ouvre jamais d'encadrement et ne paie rien. Le Falcon Heavy est donc intouché par
  construction, comme il l'était déjà pour le repli.

  **La réparation ne peut pas dégrader le résultat**, et c'est démontrable sur le code plutôt
  que mesuré : le plan rendu reste la passe faisable la moins chère observée, et la bisection
  n'ajoute que des passes faisables *sous* elle, jamais au-dessus. Une sonde qui revient à sec
  ne fait que remonter l'extrémité basse de l'encadrement.

  **Volé le 2026-09-12, et la prédiction tient au kilogramme.** Les trois sondes atterrissent
  exactement où l'arithmétique les place — 967,4, puis 487,8, puis **346,5 kg** — et les trois
  sont faisables :

  | | avant | après |
  |---|---:|---:|
  | charge S2 retenue | 3 804 kg | **346 kg** |
  | résidu | 92,8 % | 27,0 % |
  | masse finale | 19 608,4 kg | **16 170,3 kg** |
  | orbite | 399,3 × 420,3 km | **399,3 × 420,3 km** |
  | calcul | 139,9 s | 265,1 s |

  **3 438 kg de propergol mort restitués pour la même orbite à la décimale**, au prix de
  125 s de calcul.

  **Et cela referme le corollaire ci-dessus, proprement cette fois.** Le drag-off de ce même
  profil vole 16 077,7 kg et n'est pas touché par la réparation (sa passe 3 à 0,37 % est
  faisable, aucun encadrement ne s'ouvre). Le prix réel de la traînée sur l'Ariane 64 est donc
  de **+92,6 kg**, pas de +3 532 : la comparaison ne croise plus deux variables, et le *« prix
  assumé de l'asymétrie »* de [`12` §8.2](12-conception-L5-PHY-2.md) était bien l'artefact à
  98 %.

  **Un reste connu, chiffré, non pris.** Les trois sondes ont mené le rapport de 15,5 à 1,41,
  au-dessus du seuil de resserrement (1,25) : la boucle s'est arrêtée sur le **budget**, pas
  parce qu'il n'y avait plus rien à mesurer, et la passe retenue est à 27,0 % de résidu — juste
  au-delà de la bande. Une quatrième sonde volerait **292,0 kg** et devrait y entrer, pour
  ~54 kg et un vol de plus. `MAX_BRACKET_PASSES = 4` le prend ; la valeur inscrite est celle
  qui a été volée. Au passage, la ligne de journal dit désormais si la boucle a **convergé** ou
  si elle a épuisé son budget — les deux imprimaient la même chose.

  **Ce que cela dit du prix de la traînée sur l'Ariane, et avec quelle réserve.** Une passe
  convergée donne ~16,08 t de masse finale dans les deux cas mesurés — drag-off à 360
  (16 077,7 kg) et drag-on à 395 (16 087,3 kg). Les 19,6 t du drag-on à 360 seraient donc
  l'artefact, et non *« le prix assumé de l'asymétrie »* comme `12` §8.2 le conclut. La
  comparaison croise deux variables (l'environnement et l'Isp) : elle **indique**, elle ne
  prouve pas. Le juge propre serait un drag-on à 360 avec un dimensionnement qui converge —
  c'est-à-dire exactement ce que la réparation ci-dessus produirait.

### 5.4 Un levier ouvert, désarmé, laissé fermé

Le candidat **`c`** — substituer Harris-Priester à NRLMSISE **au transfert seulement**, où HP
est valide — a été reporté de `L1` à `L3`, puis de `L3` à `L5` comme *pure optimisation de
coût compute*. `L5` a mesuré ×2,4 au lieu de ×7,5 : l'escalade n'est pas déclenchée et le
levier reste **fermé, disponible**. Il redevient intéressant si `MIS-10` ou `PHY-3` rendent le
coût drag-on visible à l'utilisateur.

---

## 6. Ce que `PHY-2` lègue

**À `PHY-3`** — un modèle vivant : le sélecteur se branche sur le champ déjà en place (§4), le
piège Harris-Priester est à trancher, et `AtmosphericInterfaceDetector` réutilise l'arrêt
d'altitude de `L1`.

**À `MIS-10`** — l'arrêt d'altitude *est* le mécanisme de terminaison que la rentrée réclame,
et l'ascension reprovisionnée lui donne un lanceur crédible.

**À `PHY-5`** — la traînée fait décroître un étage largué ; sans elle, une séparation ne
montrerait pas deux objets qui s'écartent.

**Registres.** Fermés par le chantier : `BUG-10` (`L1`), `BUG-25`, `DT-15`, `DT-20`, `DT-21`
(`L3`), `DT-19` (`L4`, au runtime `EarthOrbit` — GEO et lunaire gardent la réserve), `REL-22`
(`L5`). **Amendés** : `DT-13` (moitié Falcon Heavy implémentée, moitié Ariane **ouverte**,
§5.1), `DT-14` (HP ne vole pas l'ascension). **Ouvert, mesuré au passage** : `DT-22` (les
étages bas volent pleins, le cœur largue 24 t). **Candidates à fiche** : le **lapse du
Vulcain**, versé en dette sur mesure (§5.1), et le défaut d'`insertThenTransfer` (§5.3).
L'instabilité de la boucle de dimensionnement, elle, n'a pas eu besoin de fiche : elle est
**réparée** (§5.3), et il ne reste qu'à voler le chiffre.

**Trois approximations du catalogue restent assumées**, comme le découpage §3.7 l'autorisait :
pas de pic transsonique de `Cd`, pas d'Isp dépendante de la pression, pas de table de `Cd` par
Mach.

---

*Document rédigé le 2026-09-12, à la clôture du chantier.*
