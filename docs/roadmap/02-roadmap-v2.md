# Roadmap OrbitLab v2.X.X — Réalisme : l'atmosphère, les objets, le retour

Deuxième version. Elle prend l'ancienne **phase 6** du plan à sept phases
(« réalisme et spectacle »), lui ajoute la **rentrée** — qui en était séparée
sans raison une fois l'ordre des versions décidé — et deux items neufs qui
étaient jusqu'ici des morceaux non nommés d'autres items.

Porte d'entrée du dossier : [`00-index.md`](00-index.md). Ce qui précède :
[v1](01-roadmap-v1.md). Ce qui suit : [v3](03-roadmap-v3.md),
[v4](04-roadmap-v4.md). L'ordonnancement de la dette :
[`05-roadmap-technique.md`](05-roadmap-technique.md).

---

## 1. Ce que v2 change

**Une phrase.** À la fin de v1, une trajectoire monte sans jamais être freinée,
une séparation d'étage est une chute de masse que rien ne montre, et une mission
GEO dessine un **Falcon Heavy en orbite géostationnaire** parce qu'aucun autre
objet n'existe. v2 allume la traînée, fait exister les objets qui se séparent,
et ramène un vaisseau au sol.

Trois faits mesurés qui cadrent la version :

- **La traînée n'est pas gratuite en calcul.** Entre 200 km et 130 km
  d'altitude initiale, une descente sous traînée passe de 452 pas d'intégration
  à **982 497** (`PHY-1 / L0` §2.3). Tout item qui vise ce régime doit borner
  son temps de calcul par construction, pas espérer qu'il tienne.
- **Le lanceur est le seul objet dessinable.** `MissionRenderer.modelPathFor()`
  renvoie le maillage du lanceur pour toute la vie de la mission, et le Javadoc
  de `modelPath()` l'assume : *« Fixed for its whole life — swapping the mesh of
  a live LodView is not supported »*. Aucun `PayloadModel` ne porte de maillage,
  et `models/vehicles/` ne contient que trois dossiers, tous des lanceurs.
- **Quatre items de cette version sont bloqués par des maillages, pas par du
  code.** C'est `AST-1`, et c'est la raison pour laquelle il ouvre la version au
  lieu de la clore.

---

## 2. Le plan

**L'ordre des lignes est l'ordre à tenir.** Les dépendances dures sont dans la
colonne de droite ; le reste peut glisser.

| ID | Item | ★ | ◆ | Taille | Après |
|---|---|:-:|:-:|:-:|---|
| `AST-1` | **Lot d'assets 3D** *(neuf, hors code)* | — | — | — | — (à lancer en premier : c'est un délai, pas un travail) |
| `PHY-8` | **Propulseurs séparés du corps : Falcon Heavy et Ariane 64** *(neuf)* | 4 | 4 | L | `AST-1` (maillage Ariane 64) |
| `J2` | Trois arbitrages du modèle atmosphérique | — | — | — | `PHY-8` |
| `PHY-2` | Atmosphère par défaut + recalibrage optimiseur | 5 | 4 | L | `J2`, `PHY-8` |
| `PHY-3` | Détecteurs MaxQ, télémétrie, UI de fidélité | 3 | 2 | M | `PHY-2` |
| `RND-5` | Repère d'affichage inertiel / tournant | 2 | 2 | S | — |
| `RND-6` | **Trace au sol** *(neuf)* | 3 | 2 | M | `RND-5` |
| `MIS-10` | Déorbitage contrôlé et rentrée atmosphérique | 5 | 3 | M | `PHY-2`, `PHY-3`, `RND-6`, `BUG-10` |
| `PHY-5` | Machinerie multi-objets + étages largués | 4 | 3 | L | `PHY-2`, `AST-1` |
| `PHY-6` | **Charge utile comme objet distinct** *(neuf)* | 4 | 2 | M | `PHY-5`, `AST-1` |
| `FX-3` | Particules de tuyère | 4 | 2 | M | — |
| `FX-4` | **Traînée plasma de rentrée** *(neuf)* | 3 | 2 | M | `MIS-10` |
| `NAV-5` | Hover « wow » planètes + orbites | 3 | 2 | M | — |

**Pourquoi `PHY-8` passe avant `PHY-2`, et non l'inverse.** Atmosphère,
recalibrage d'Isp et structure d'étagement font trois changements de
comportement sur la même trajectoire. Découper d'abord, sans traînée, laisse
`PHY-2` allumer et calibrer **une seule fois**, contre une ascension de forme
physique ; dans l'autre ordre, `PHY-2` calibrerait un mélange solide / cryo
qu'on s'apprête à supprimer, puis il faudrait tout refaire. Le coût du
découpage est le même quel que soit le moment où il est payé — donc le payer
avant la calibration est strictement meilleur
([`v2-preparation/00-preparation.md`](../v2-preparation/00-preparation.md) §2.3).

**Pourquoi `RND-5` et `RND-6` sont voisins, et dans cet ordre.** Ce sont les
deux moitiés de la même donnée : les sommets en repère lié au corps sont déjà
cuits une fois à la construction de l'éphéméride. `RND-5` les affiche à la place
des sommets inertiels ; `RND-6` les projette sur la surface. Livrer la trace au
sol sans la bascule de repère, c'est écrire deux fois la même conversion.

**Pourquoi `MIS-10` arrive après `RND-6` et pas avant.** Sa fiche dit que la
trace au sol *« cesse ici d'être un ornement »* — l'empreinte du point d'impact
en est le livrable visible. Prise dans l'autre ordre, `MIS-10` paie la
projection au sol au milieu d'un chantier de rentrée.

**Fin de version quand** : le catalogue ne déclare plus un étage que le modèle
ne vole pas (`PHY-8`), une ascension coûte ce qu'elle coûte vraiment (`PHY-2`),
un vaisseau parti de la Terre y revient à un endroit choisi (`MIS-10`), une
séparation montre deux objets qui s'écartent (`PHY-5`), et un satellite en
orbite ressemble à un satellite (`PHY-6`).

---

## 3. Dette et robustesse

*Les fiches vivent dans [`bugs.md`](../bugs.md),
[`dette-technique.md`](../dette-technique.md) et
[`reliquats.md`](../reliquats.md) ; leur ordonnancement dans
[`05-roadmap-technique.md`](05-roadmap-technique.md). Cette section ne dit que
ce que v2 doit traiter et à quel moment.*

**`J2` — trois arbitrages, avant tout calibrage.** Ce n'est pas du travail de
code : c'est ce qu'il faut avoir **tranché** avant que `PHY-2` calibre quoi que
ce soit, sous peine de figer l'erreur dans le recalibrage.

| Item | Ce qu'il faut décider |
|---|---|
| `DT-13` | Les Isp « moyenne de trajectoire » absorbent **408 m/s** (Falcon Heavy S1) et **671 m/s** (Ariane 62 S1) de traînée implicite. Décider ce que le catalogue porte une fois la traînée allumée : Isp de vide partout, ou proxy conservé — et de combien |
| `DT-14` | 22,6 % d'écart entre Harris-Priester et NRLMSISE-00, tous deux déjà codés. Choisir la référence **avant** de calibrer dessus |
| `DT-15` | `Cd = 2,2` est déclaré valide au-dessus de 70 km ; le seul profil réel mesuré allume S2 à **58 km** |

Les trois se tranchent en une séance sur les mesures déjà disponibles dans
[`atmosphere/05-conception-L2.md`](../atmosphere/05-conception-L2.md).

> **Correction du 2026-09-08 — de retour à trois.** Ce paragraphe retirait
> `DT-13` de la liste, au motif que `PHY-8` *« rend à chaque étage son Isp réelle
> et dissout le double-comptage au lieu de le trancher »*. Le découpage de
> `PHY-8` a mesuré le contraire
> ([`etagement/01-decoupage.md`](../etagement/01-decoupage.md) §3.4) : la dette
> est la moyenne **sol / vide**, pas la moyenne solide / cryogénique, et séparer
> les propulseurs du corps ne la supprime pas — l'écart sol/vide d'un solide se
> compte en dizaines de secondes, celui d'un cryogénique en centaines. `PHY-8` la
> **localise** sur le corps central au lieu de la diluer dans un mélange : `J2`
> arbitrera donc une entrée par lanceur, ce qui rend l'arbitrage plus simple, pas
> inutile. C'est aussi pourquoi `J2` reste **après** `PHY-8`. La fiche du registre
> reste **ouverte** ; il n'y a rien à y passer en « fermé par `PHY-8` ».

**Avec `PHY-2` :** `REL-22` — la restauration d'un scénario dont l'atmosphère
n'est pas `NONE` est **incorrigible avant** que `PHY-2` existe ; elle est versée
à son périmètre.

**Avec `MIS-10`, et en premier :** `BUG-10` — `ReentryGuard` est **inopérant**
sous traînée. `PHY-1 / L0` §2.3 a rejoué quatre rentrées avec `armQuiet` armé
exactement comme `StageLegRunner` l'arme en vol : la garde ne change rien, pas
un pas d'intégration, pas une seconde sur la date d'échec. La cause est son
plancher `SUBSURFACE_FLOOR = −50 km`, choisi parce que l'altitude *sphérique*
d'un pas de tir est déjà négative (la Terre est aplatie de 21,4 km) ; or
l'intégrateur meurt à −9 km et −30 km, c'est-à-dire **au-dessus** du plancher.
Un détecteur inopérant dans le seul régime pour lequel il existe est le premier
livrable de `MIS-10`, pas un détail d'intégration.

**Remonté depuis le jalon de v4 :** `DT-17` — la performance du ruban n'a jamais
été profilée. C'était rangé avant `MIS-6` parce qu'un rendez-vous affiche deux
trajectoires ; `PHY-5` en affiche **une par objet largué**, ce qui arrive plus
tôt et va plus loin. L'item change donc de version.

---

## 4. Détail des items

### AST-1 — Lot d'assets 3D — *(neuf, hors code)*

**Pourquoi un identifiant pour quelque chose qui n'est pas du code.** Quatre
items de la roadmap sont bloqués non par une difficulté technique mais par un
maillage qui n'existe pas. Sans nom, ce blocage se découvre en cours de version,
au moment où le code est prêt et où l'asset ne l'est pas. Avec un nom, il se
lance en premier — un approvisionnement se mesure en délai, pas en jours de
travail.

| Ce qui manque | Bloque | État |
|---|---|---|
| Un Ariane 6 | `DT-12` | Le catalogue vole une Ariane 62, l'écran montre une **Ariane 5**. Seule la silhouette est fausse — masses, propulsion et profil d'ascension restent ceux du catalogue |
| Un maillage par étage | `PHY-5` | Il n'y a qu'**un maillage par lanceur** (`heavy_falcon.gltf`, `ariane/scene.gltf`), la pile entière d'un seul tenant |
| Un maillage par famille de charge utile | `PHY-6` | `PayloadModel` (7 composants) n'en porte **aucun**, et les cinq entrées du catalogue `Payloads` n'ont aucune représentation |

**Correction à la fiche `PHY-5`.** Elle affirmait que
`src/main/resources/models/` *« n'est pas versionné »*, ce qui en faisait un
obstacle. **C'est faux** : `git ls-files` y compte **54 fichiers suivis** — les
onze planètes et les trois lanceurs — et `.gitignore` ne mentionne ni `models`
ni `resources`. Un maillage neuf se commite comme les 54 autres. Ce qui reste
vrai, et qui suffit à justifier cet item, c'est qu'il faut le **produire ou le
sourcer**, et que ça ne se planifie pas comme du code.

**Deux conventions que tout maillage ajouté doit respecter**, déjà écrites dans
le Javadoc de `LauncherAssets` et vérifiées par les deux assets existants :
après la transformation racine du GLTF, le nez pointe selon **`+Y`** — la
correction appliquée par `SpacecraftPresenter` est unique pour tous les
vaisseaux — et le véhicule est normalisé à **environ une unité de haut**, parce
que `Model3dView` le met à l'échelle depuis le seul rayon du vaisseau. Un
maillage qui casse l'une des deux vole de travers ou à la mauvaise taille.

**Ce que l'item n'est pas.** Il ne demande pas des maillages fidèles ni
texturés au niveau des lanceurs existants. Une silhouette juste et une
orientation correcte valent mieux qu'un modèle détaillé qui arrive après la
version.

---

### PHY-8 — Propulseurs séparés du corps : Falcon Heavy et Ariane 64 — ★4 ◆4 L *(neuf)*

**Il ne se conçoit pas ici.** Le raisonnement est dans
[`v2-preparation/00-preparation.md`](../v2-preparation/00-preparation.md) §2,
qui l'a tranché comme *« un item à part entière »* précédant les deux objectifs
de la version : il n'est ni une sous-tâche de `PHY-2` ni une sous-tâche de
`PHY-5`, il sert les deux, et c'est lui qui invalide toutes les références.
Cette fiche l'enregistre et ajoute les deux décisions prises depuis — la
combustion parallèle, et la fraction de poussée qu'elle rend nécessaire.

> **Le découpage est écrit** :
> [`etagement/01-decoupage.md`](../etagement/01-decoupage.md), sept lots. Il fait
> foi sur les décisions de conception ; cette fiche reste le résumé de l'item.
>
> **Corrigée contre la mesure le 2026-09-08**, en écrivant ce découpage, sur neuf
> points : 43 fichiers touchés et non 47 · l'invariant d'étage actif est
> **préservé**, pas cassé · `StageRole.BOOSTER` dans quatre fichiers de test et
> non cinq · 197 fichiers de test et non 196 · les quatre épinglages sont de
> trois natures, dont un seul coûte cher · **`DT-13` ne ferme pas** · le maillage
> Ariane 64 est livré, la dépendance est levée · la question 5 de la préparation
> n'est refermée qu'à moitié · 100 fichiers de modèles suivis et non 76. Le
> périmètre a par ailleurs gagné le **catalogue des charges utiles**.
>
> **`L0` est clos le 2026-09-08** :
> [`etagement/02-baseline-L0.md`](../etagement/02-baseline-L0.md), six cellules
> mesurées plus les quatre épinglages re-capturés, aucun fichier de `src/main`
> touché. Deux de ses résultats portent sur cette fiche. **Le « 157,0 s chacun »
> ci-dessous est juste pour toute mission budgétée** — `PropellantBudget` ne
> dimensionne que l'étage du haut, les autres volent pleins — **mais pas pour le
> profil de référence LEO-400 `LEGACY`**, à charges écrites à la main, qui éteint
> son S1 à 76,4 s : or c'est lui qu'épinglent les deux gates à tolérance zéro.
> Et sur Falcon Heavy **l'étage 0 porte 98 à 100 % du ΔV d'ascension** — 100 % en
> LEO 400, où l'étage supérieur ne s'allume pas du tout — donc l'étranglement de
> `L3` allonge la combustion qui porte tout. `L0` ferme aussi `BUG-7`.

**Pourquoi.** Les deux fictions du modèle sont la même fiction : l'agrégat
existe parce que `VehicleStack` *« resolves exactly one active stage »*, et
l'Isp « moyenne de trajectoire » existe parce qu'il n'y a pas de traînée.
`PHY-2` supprime la seconde ; n'en supprimer qu'une revient à recalibrer un
mélange contre de la vraie physique. Le symptôme est déjà écrit au catalogue :
l'agrégat Ariane 62, à 9 960 kN et 300 s, s'éteint à **T+128,2 s** — fidèle aux
P120C (~130 s), faux pour le Vulcain qui brûle réellement ~8 min. Le modèle **ne
vole donc jamais la phase corps-seul**, et aucune manette ne le corrige :
*« stretching the burn to 300 s would need the aggregate thrust below the
lift-off weight »*. Le catalogue déclare par ailleurs `CRYOGENIC` pour un bloc à
65 % solide, en l'assumant — *« a modelling convention, not a chemical claim »*.

**Ce qu'il livre.**

- **La combustion parallèle.** Les deux lanceurs allument propulseurs et corps
  **ensemble au décollage** ; la phase partagée tire la masse des deux, les
  propulseurs s'épuisent, sont largués, le corps continue. Le découpage y répond
  par un **bloc parallèle calculé** — poussée sommée, `Isp_eff` dérivée, plancher
  de déplétion propre au bloc — dont l'agrégation est *exacte* à poussées et Isp
  constantes. **L'invariant *« the active stage changes only by an explicit
  jettison »* est donc préservé, non pas cassé** : un seul étage actif à la fois,
  changé par le seul largage. C'est ce qui laisse en place les **43 fichiers de
  `src/main`** touchant `resolveActiveStage`, `VehicleStack`, `.stages()` ou
  `StageSeparation` — 43, pas 47, et 25 / 16 / 7 / 11 pris séparément.
  L'alternative — deux étages en série — a été écartée : elle échange la fiction
  actuelle contre une autre, exacte en impulsion sur l'Ariane et fausse en forme
  sur le Falcon Heavy, dont le premier étage durerait 314 s contre ~187 s réels.
- **Une fraction de poussée par étage pendant la phase parallèle.** Sans elle le
  Falcon Heavy n'a **pas** de phase corps-seul : ses trois corps étant
  identiques, poussée et ergols sont dans le même rapport 2:1 et les deux blocs
  se vident au même instant, **157,0 s** chacun. Le vrai véhicule s'en sort en
  étranglant son corps central. `PropulsionSystem` est un `record (isp, thrust)`
  figé et aucune notion d'étranglement n'existe dans `src/main` : c'est le seul
  concept neuf de l'item. L'Ariane 64 n'en a pas besoin : ses **quatre** P120C
  vident 564 t en ~130 s là où le Vulcain met ~8 min pour 152 t — un rapport de
  débits d'environ quatorze, qui l'étage toute seule.
- **Un étage de propulseurs à multiplicité** — 2 pour le Falcon Heavy, 4 pour
  l'Ariane 64. Agréger un solide et un cryogénique est une fiction ; agréger N
  solides identiques est exact : même Isp, allumage commun, extinction commune,
  largage commun. `StageRole.BOOSTER` existe déjà et n'apparaît aujourd'hui que
  dans **quatre fichiers de test** — `PropellantLoadOptimizerTest`,
  `LauncherModelTest`, `StageCapabilitiesTest`, `StageModelTest` — jamais dans
  `Launchers` : le modèle avait anticipé le cas, le catalogue ne s'en est jamais
  servi.
- **Ariane 64 remplace Ariane 62 au catalogue**, et chaque étage porte ses
  propres Isp, poussée et section. **La moyenne supprimée est celle des ergols,
  pas celle de l'altitude** : `DT-13` mesure un proxy **sol / vide** — 296 s dans
  [282, 311], 300 s dans [271, 331] — que la séparation ne touche pas. Elle le
  **localise** : l'écart sol/vide d'un solide se compte en dizaines de secondes,
  celui d'un cryogénique en centaines, donc la dette se concentre sur le corps au
  lieu de se diluer dans un mélange. Les étages atmosphériques gardent un proxy,
  les étages supérieurs portent leur Isp de vide
  ([découpage](../etagement/01-decoupage.md) §3.4).
- **Les dimensions du lanceur et de ses pièces**, au catalogue — voir plus bas.
- **Le catalogue des charges utiles**, entré au périmètre depuis : le module
  cargo **filtré hors des listes** du wizard — mettre une charge cargo en orbite
  n'a pas d'utilité opérationnelle, il n'en a qu'en rendez-vous, donc en v4 — et
  un **moteur pour le satellite d'observation**, avec le budget ΔV qui le
  dimensionne. La chaîne qui s'en servira reste à `PHY-6` : la propulsion livrée
  ici est inerte jusque-là. La frontière retenue entre les deux items est
  *catalogue ici, chaîne là-bas*
  ([découpage](../etagement/01-decoupage.md) §1).
- **Le re-baselining.** **45 des 197 fichiers de test** (23 %) référencent
  `FALCON_HEAVY` ou `ARIANE_62`. Les quatre épinglages cités ici comme étant « à
  tolérance zéro » sont en réalité de trois natures, et le coût n'est pas celui
  qu'annonçait cette fiche : `EarthOrbitNonRegressionTest` compare **deux
  compositions de la même mission dans le même run** et survit donc à n'importe
  quel catalogue ; `AscentBaselineN2Test` (tolérances mesurées, mode capture,
  écriture dans `build/baseline/`) et `MissionPolylineBaselineTest` (générateur
  imprimant ses constantes) portent **leur propre outil de re-enregistrement** ;
  seul `CentralBodyBaselineTest` coûte vraiment — 1 296 lignes de `Boundary` à
  égalité stricte de `double`, dont la justification meurt avec le premier
  changement de trajectoire.

**Ce que le découpage n'ajoute pas : une variable d'optimisation.**
`PropellantLoadOptimizer` masque déjà les étages `!variableLoad()` (lignes 334
et 367), et `variableLoad()` ne renvoie `false` que pour `SOLID` — *« Solids fly
full »*. Un étage de P120C n'entre donc pas dans le balayage λ. **Mais
l'asymétrie est réelle et le masque est à revoir, pas à hériter** : les
propulseurs du Falcon Heavy sont des corps kérolox, donc à charge variable, et
sous combustion parallèle faire varier indépendamment leur charge et celle du
corps n'a pas de sens — ils se vident ensemble.

**Les dimensions, et pourquoi elles sont ici.** `MissionRenderer:40` tient un
`SPACECRAFT_RADIUS_METERS = 50.0` **en dur**, qui sert à la fois d'échelle de
modèle et de seuil de bascule LOD : tout ce qui vole est dessiné 100 m de haut.
Le catalogue porte des **sections** — `AerodynamicProperties`, π·1,83² pour un
corps de 3,66 m — et **aucune longueur**. L'item les ajoute, par lanceur et par
pièce, et la constante disparaît.

> **Changement assumé de périmètre.** La préparation §1.3 rangeait ce point dans
> `PHY-6` — *« une conséquence à traiter dans `PHY-6`, pas ici »*. Il vient ici
> parce que c'est le catalogue qui est ouvert, et parce que deux items en aval
> l'attendent : `PHY-5` a besoin de la taille d'un propulseur largué pour le
> dessiner, `PHY-6` de celle d'un satellite de deux tonnes pour qu'il ne soit
> pas aussi grand que le lanceur qui l'a mis là. Effet de bord : la question
> ouverte n° 1 de la préparation — la longueur relative des propulseurs A64,
> 36,2 % du maillage contre 21–24 % attendus — devient **vérifiable depuis le
> dépôt**, alors qu'elle ne l'était pas (*« le catalogue porte des sections, pas
> des longueurs »*).

**Sa seule dépendance dure était le maillage Ariane 64. Elle est levée.**
`AST-1` a livré les deux lots — 24 fichiers suivis sous
`models/vehicles/ariane_64/`, 22 sous `models/vehicles/heavy_falcon/` — et
`LauncherAssets` pointe déjà l'A62 du catalogue sur `ariane_64/ariane_64.gltf`.
Les hauteurs normalisées de chaque pièce que ce lot apporte dispensent d'ailleurs
de sourcer une longueur par pièce : une hauteur par lanceur suffit
([découpage](../etagement/01-decoupage.md) §3.8).

Reste le mode d'échec silencieux qui justifiait cette dépendance :
`LauncherAssets.MODEL_PATHS` est indexé par **id de catalogue**, et
`modelPath` retombe sur `DEFAULT_MODEL_PATH` pour un id inconnu — donc une
Ariane dessinée en Falcon Heavy, sans message. La table doit changer dans le même
lot que l'id.

**Ce qu'il ferme.**

| | |
|---|---|
| `DT-12` | Le catalogue cesse de déclarer une Ariane 62 dessinée en Ariane 5 : le lanceur devient une Ariane 64 et porte son propre maillage. *(La fiche du registre conclut par ailleurs que `src/main/resources/models/` est gitignored ; `AST-1` l'a déjà réfuté, `git ls-files` y compte 100 fichiers suivis.)* |
| ~~`DT-13`~~ | **Ne ferme pas.** La séparation supprime la moyenne solide / cryogénique et laisse intacte la moyenne sol / vide, qui *est* la dette. Elle la localise sur le corps au lieu de la diluer — un progrès, pas une fermeture. `DT-13` est donc **revenu dans `J2`** (§3), qui repasse à trois arbitrages |
| préparation §5, q. 2 | *« Comment refermer l'écart quatre propulseurs / Ariane 62 »* — par la troisième option : l'A64 revient au catalogue, et l'A62 en sort |
| préparation §5, q. 5 | **À moitié.** Le partage des **ergols** se lit dans le catalogue — 65 % de 434 t = 282 t de solide, soit **141 t par P120C**, donc 564 t pour quatre et 152 t de LLPM — mais le partage des **36 t de masse sèche**, qui est ce que la question demande, ne s'en déduit pas et reste à sourcer ([découpage](../etagement/01-decoupage.md) §7) |

**L'état intermédiaire est délibérément faux, et faux dans une direction
prévue** (préparation §2.3). La traînée n'étant pas encore allumée, l'ascension
doit sur-performer. **Prédiction, pas mesure**, et à re-prédire : les 408 m/s et
671 m/s de `DT-13` sont ceux des agrégats, qui n'existeront plus — mais le proxy
étant **conservé** sur les étages atmosphériques, la compensation ne disparaît
pas avec eux, elle se redistribue. Deux chiffres à relever au premier vol
découpé, un par lanceur, et non un écart à zéro.

**Ce qu'il s'interdit.** Pas de traînée : c'est `PHY-2`, et l'ordre du §2.3
l'exige. Pas de débris propagés : c'est `PHY-5`, dont l'item prépare la donnée
sans la consommer — chaque propulseur largué hérite de sa propre section
(π·1,7² = 9,1 m² pour un P120C), *« ce dont `PHY-5` a besoin de toute façon »*,
et qu'il faudrait sinon inventer. Pas de largage de coiffe, pas d'impulsion de
séparation.

**Les scénarios sauvegardés : le retrait est prévu, le changement d'arité ne
l'est pas.** `ScenarioSession.restore` met déjà de côté, avec son motif, une
mission *« whose launcher left the catalog »* — l'A62 qui sort ne casse donc
rien, elle produit un rejet lisible. Ce qui n'est pas couvert est l'autre
moitié : `ScenarioSolution.launcherLoads` est un `double[]` **par étage**, et
`LauncherModel.instantiate` **lève** si la longueur ne correspond plus. Un
scénario Falcon Heavy sauvegardé à deux étages ne rejoue pas sur un lanceur qui
en a trois, et il ne le dit pas de la même façon. À traiter dans l'item, faute
de quoi la version rend illisibles des scénarios que la précédente écrivait.

**Un mode d'échec à surveiller, parce qu'il s'est déjà produit une fois.**
`StageSeparationStage` porte un contrôle `expectedStageIndex` parce que, sur le
profil GEO, *« a lighter upper stage makes the gravity turn stop before S1 is
dry, leaving S1 active »*, et un « S2 separation » non vérifié larguait alors
S1. Avec un étage de plus et une combustion parallèle, il y a plus de façons
pour la comptabilité de masse de se tromper d'étage.

---

### PHY-2 — Atmosphère par défaut + recalibrage — ★5 ◆4 L

Harris-Priester pour l'optimisation, NRLMSISE-00 pour la propagation runtime —
cohérent avec la philosophie 8×8 / 50×50 déjà en place. Relever le
`periapsisFloor` (100 km n'a plus de sens avec du drag), absorber les pertes
dans `dt1MaxPhysical`, re-baseliner `LEOMissionOptimizationTest` et la suite
paramétrique. C'est **le** chantier qui rend la simulation crédible : sans drag,
un gravity turn atteint son apogée avec moins d'ergols qu'un vrai lanceur.

Coût compute attendu : +5 % (Harris-Priester) à +50 % (NRLMSISE-00) sur une
optimisation CMA-ES.

**Le recalibrage est groupé, et c'est le prix de la stratégie de v1.** `PHY-1`
avait livré la brique **off** par défaut sous une contrainte tenue jusqu'au
bout — *drag off ⇒ trajectoire identique au bit près*. Chaque type de mission
écrit depuis l'est donc **sans** traînée, et bascule ici en une fois, sur un
périmètre connu. C'est l'échéance que v1 avait achetée ; elle arrive.

---

### PHY-3 — Détecteurs, télémétrie, UI de fidélité — ★3 ◆2 M

`MaxQDetector`, `AtmosphericInterfaceDetector` (ligne de Kármán — hook direct
pour `MIS-10`), extension de `TelemetryWidgetAppState` avec Q et drag
instantané, sélecteur Off / Statique / Réaliste dans `StepParameters`. Le profil
`Q(t)` est le meilleur objet pédagogique que l'atmosphère apporte.

**`AtmosphericInterfaceDetector` est le livrable que `MIS-10` consomme**, et
c'est la raison de l'ordre des deux items : la rentrée a besoin d'une marque
d'entrée avant d'avoir besoin d'une terminaison.

---

### RND-5 — Repère d'affichage des trajectoires — ★2 ◆2 S

**Pourquoi.** Une trajectoire de mission est dessinée en repère inertiel (GCRF)
pendant que le globe tourne sous elle : six heures après le décollage, le pied de
l'ascension est à 90° de longitude du pas de tir. **Ce n'est pas un défaut** —
vérifié dans le code, les trois maillons sont corrects — mais rien dans l'image ne
dit au lecteur laquelle des deux choses bouge.

**Ce que c'est.** Une bascule globale à deux valeurs dans le menu applicatif,
**défaut inertiel** (la convention de tous les outils de trajectoire, et le
comportement actuel). En repère tournant, la trace repart du pas de tir et s'y
ancre, au prix de la lecture orbitale : l'ellipse devient un enroulement.

**Ce qui rend l'item petit** : les sommets en repère lié au corps sont cuits une
fois à la construction de l'éphéméride, et l'affichage n'ajoute qu'une rotation de
nœud par frame. La tête de la traînée retombe exactement sur le vaisseau sans
traitement particulier, donc ni la caméra, ni le globe, ni le repère flottant ne
sont touchés.

**Il était volontairement hors phases**, comme un confort à piocher un jour de
creux. Il est phasé ici parce que `RND-6` a besoin exactement de la même donnée :
laissé de côté, il serait réécrit à l'intérieur de la trace au sol.

**Spec.** [`trajectory-display-frame.md`](../graphics-effects/trajectory-display-frame.md).

---

### RND-6 — Trace au sol — ★3 ◆2 M *(neuf)*

**Pourquoi.** La trace au sol figurait au backlog de v1 comme un ornement de
rendu. `MIS-10` la transforme en besoin : une rentrée sans **empreinte du point
d'impact** ne répond pas à la seule question qu'on lui pose — *où ça tombe ?* Sa
fiche le dit dans ces termes ; l'extraire en item propre évite qu'elle soit
écrite au milieu d'un chantier de rentrée et n'y serve qu'une fois.

**Ce que c'est.** La projection sur la surface du corps central des sommets déjà
exprimés en repère lié au corps — ceux que `RND-5` vient de rendre affichables.
Un ruban de plus (`RND-4` a livré la primitive), plaqué au sol à altitude nulle,
suivant la même sémantique passé / futur que la trajectoire elle-même.

**Deux clients immédiats**, et c'est ce qui la sort du décoratif :

- **`MIS-10`** — l'empreinte du point d'impact, et la fenêtre d'entrée qui la
  précède.
- **`MIS-7`, déjà livré** — les profils polaire et SSO n'ont aujourd'hui aucune
  vérification visuelle de leur couverture. Une trace au sol est *la* façon de
  voir qu'une orbite héliosynchrone repasse au même endroit à la même heure
  solaire. La version rembourse donc l'item sur du contenu déjà en place, et pas
  seulement sur `MIS-10`.

**Un piège connu, déjà payé une fois.** Le pôle inertiel n'est pas le pôle
terrestre — **0,145° d'écart en 2026** — et une inclinaison de 90° en GCRF
éloigne visiblement la trace au sol du pôle. La trace se calcule donc en repère
**lié à la Terre** (ITRF), jamais en projetant naïvement une orbite inertielle.

**Ce qu'on ne fait pas.** Pas de cercle de visibilité, pas d'empreinte de
capteur, pas de trace au sol pour les corps autres que le corps central de
l'arc courant.

---

### MIS-10 — Déorbitage contrôlé et rentrée atmosphérique — ★5 ◆3 M

**Promu du backlog de v1.** Une fiche de brainstorm existe déjà —
[`docs/brainstorm/missions.md`](../brainstorm/missions.md) §6, cotée ★5 ◆3 —
mais elle est **périmée sur un point** : elle range `ReentryDetector` dans « ce
qui manque », alors qu'il existe
([`ReentryDetector.java`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/detector/ReentryDetector.java))
et qu'il est armé en production sur chaque leg d'étage.

**La garde de rentrée est à réparer avant tout le reste** — c'est `BUG-10`, dont
le mécanisme mesuré est au §3.

**À faire.**
- `DeorbitBurnStage` : burn rétrograde abaissant le périgée sous ~80 km. La brique
  existe — c'est un `ConstantThrustStage` en direction opposée.
- `ReentryObjective` : point d'impact visé, fenêtre d'entrée, heure de rentrée.
- Terminaison propre : `AtmosphericInterfaceDetector` (livré par `PHY-3`) pour
  marquer l'entrée, et une borne d'altitude qui arrête la propagation **avant** que
  l'intégrateur ne cède — la même borne que `PHY-1 / L0` §2.2 réclame déjà pour des
  raisons de temps de calcul.
- L'empreinte au sol du point d'impact, sur le ruban livré par `RND-6`.

**Le coût compute est le risque, et il est mesuré.** Entre 200 km et 130 km
d'altitude initiale, une descente sous traînée passe de 452 pas d'intégration à
**982 497** (`PHY-1 / L0` §2.3). Un déorbitage vise précisément ce régime : l'item
doit borner son temps de calcul par construction, pas espérer qu'il tienne.

**Un satellite se déorbite entier**, et c'est ce qui range cet item en v2 et non
en v3 : il ne demande aucune séparation, donc ni `PHY-5` ni `PHY-6` ne sont ses
préalables. `MIS-11`, qui rentre en capsule, ne peut pas en dire autant — d'où
sa version.

**La désintégration n'est pas dans le périmètre** — voir la question ouverte
n° 1 au §5, qui sépare les trois paliers et leurs coûts très inégaux.

---

### PHY-5 — Machinerie multi-objets et étages largués — ★4 ◆3 L

**Pourquoi.** Aujourd'hui un étage largué **n'existe pas**. `StageSeparationStage`
est une chute de masse et rien d'autre : l'état passe à la masse de référence de
la pile au-dessus, `resolveActiveStage` active le véhicule suivant, et l'étage
abandonné disparaît de la simulation à l'instant précis où il devient intéressant.
La séparation est le seul moment d'un vol où les images réelles montrent deux
objets ; chez nous, elle ne montre rien.

**À faire.**
- **Un objet propagé par étage largué** : masse sèche, coefficient balistique
  propre, état initial hérité de l'état de séparation, propagation autonome
  jusqu'à l'impact ou la décroissance.
- **Une éphéméride par objet.** C'est ici qu'est le coût réel, et il n'est pas
  dans la physique : `MissionEphemeris` décrit **une** trajectoire, et le renderer
  en suppose une par mission. Le passage à N est le même exercice que `PHY-4 / L3`
  a fait pour les arcs (un repère par échantillon), mais sur l'autre axe.
- **Des maillages par étage** — c'est `AST-1`.
- **La séparation elle-même** : impulsion de séparation, deux corps qui s'écartent,
  et un débris qui garde son identité dans le breadcrumb et la télémétrie.

**Pourquoi `PHY` et pas `RND`.** La moitié visible est du rendu, mais ce qui manque
n'est pas un dessin : c'est un **objet propagé**. Tant que la simulation n'a qu'une
trajectoire par mission, le rendu n'a rien à montrer.

**Ce que l'item s'interdit.** L'optimiseur ne voit pas les débris. Un étage largué
est propagé pour l'affichage et la pédagogie, jamais dans la boucle CMA-ES : sinon
le coût d'une évaluation est multiplié par le nombre d'étages, pour un résultat qui
n'entre dans aucune fonction objectif.

**Pourquoi il est après `PHY-2`.** Sans traînée, un étage largué en orbite ne
redescend jamais et un booster suborbital retombe sans ralentir : on obtiendrait
deux points qui s'écartent, pas une séparation.

**Le nom de l'item a changé de moitié.** Il s'appelait « Étages largués : objets
propagés et modèles 3D ». Les étages largués restent son premier client, mais
son livrable est la **machinerie multi-objets** — N objets propagés, N
éphémérides, N vues — dont `PHY-6` est le second client dans la même version. Le
nommer par la machinerie plutôt que par le client évite qu'elle soit écrite deux
fois.

---

### PHY-6 — Charge utile comme objet distinct — ★4 ◆2 M *(neuf)*

**Pourquoi, et c'est déjà vrai aujourd'hui.** Une mission `GEO_SAT` calculée,
volée et affichée dessine un **Falcon Heavy en orbite géostationnaire**. Ce
n'est pas un défaut d'esthétique : la charge utile n'existe pas comme objet.
Trois mesures :

- `PayloadModel` (7 composants) ne porte **aucun maillage**, et
  `models/vehicles/` ne contient que trois dossiers, tous des lanceurs.
- `MissionRenderer.modelPathFor()` renvoie
  `LauncherAssets.modelPath(spec.configuration().launcher().id())` — le maillage
  du lanceur, pour toute la mission. Le Javadoc de `modelPath()` verrouille :
  *« Fixed for its whole life — swapping the mesh of a live LodView is not
  supported »*.
- Aucun stage ne sépare la charge utile : le répertoire `stage/` en compte
  quinze plus `ascent/`, et pas un `PayloadSeparationStage`.

**Ce que l'item livre.**
- **La séparation de charge utile comme événement de la mission** : à la fin du
  dernier étage utile, la charge utile devient l'objet volé, et l'étage qu'elle
  quitte devient un débris — c'est-à-dire un client de plus de la machinerie de
  `PHY-5`.
- **Un maillage par famille de charge utile**, associé au `PayloadModel` par une
  table du même genre que `LauncherAssets` et pour la même raison : le catalogue
  décrit ce qu'une charge utile *est*, la couche de rendu décide comment on la
  dessine. Les maillages eux-mêmes sont dans `AST-1`.
- **Le suivi de l'objet actif** dans la télémétrie, le breadcrumb et la caméra :
  après séparation, « le vaisseau » désigne la charge utile.

**Pourquoi ce n'est pas un gros item, une fois `PHY-5` livré.** La partie chère
— N objets propagés, N éphémérides, N vues, et le renderer qui cesse de supposer
un maillage unique et figé — est précisément ce que `PHY-5` construit. `PHY-6`
en est le second client, et le delta se réduit à un événement de séparation, une
table de maillages et un changement de cible pour trois consommateurs.

**Ce que l'item s'interdit.** Pas de propulsion de charge utile au-delà de
l'AKM déjà modélisé (`GEO_SAT` en porte un), pas de déploiement de panneaux —
les coefficients balistiques du catalogue supposent explicitement des panneaux
repliés — et **pas d'attitude de charge utile** : pointer un satellite est du
ressort de `MIS-12` en [v4](04-roadmap-v4.md), qui en a besoin pour s'amarrer.

**Ce qu'il débloque plus loin.** C'est le préalable de l'amarrage : s'approcher
d'une cible avec la pile entière n'a pas de sens. Il est ici et pas en v4 parce
que le défaut qu'il corrige existe **déjà**, sur toutes les missions Terre et
GEO livrées en v1.

---

### FX-3 — Particules de tuyère — ★4 ◆2 M

Les vaisseaux glissent en silence, et rien à l'écran ne distingue une phase
propulsée d'un coast. `ParticleEmitter` (built-in) attaché au node du vaisseau,
blending additif, débit modulé par la magnitude de poussée, activation pilotée
par la phase courante via `MissionContext`. Synergie directe avec `RND-3` (code
couleur thrust/coast sur la trajectoire) : même information, deux canaux.

---

### FX-4 — Traînée plasma de rentrée — ★3 ◆2 M *(neuf)*

**Pourquoi maintenant.** La question ouverte n° 8 de v1 séparait trois paliers
dans le mot « désintégration » et recommandait explicitement le second **comme
item `FX` distinct une fois `MIS-10` volé**. Il l'est ici, et cette version le
clôt. La spec existe :
[`effects-roadmap.md`](../graphics-effects/effects-roadmap.md) §5.5, cotée
difficulté 4 / wow 5 dans le barème de ce document — attention, son ★ est la
difficulté, à l'inverse d'ici.

**Ce que c'est, et ce que ce n'est pas.** C'est un **effet**, pas une physique :
une enveloppe lumineuse et une traînée attachées au vaisseau pendant qu'il est
sous l'interface atmosphérique, modulées par la pression dynamique que `PHY-3`
calcule déjà. Aucun flux thermique, aucune ablation, aucune fragmentation — ce
troisième palier reste au backlog et relève de la R&D.

**Il est après `MIS-10` et pas dedans**, parce qu'un effet sans trajectoire de
rentrée n'a rien à décorer, et qu'une trajectoire de rentrée sans effet reste
une mission valide.

---

### NAV-5 — Hover « wow » — ★3 ◆2 M

Spec complète, et **le préalable est levé** : le boost d'épaisseur à ×2 sur hover
reposait sur `setLineWidth`, sans effet sur les drivers en profil core. `RND-4`
étant livré, la largeur et l'alpha sont des uniforms du matériau `Ribbon` — la
spec est applicable telle qu'écrite, et les deux animations de 150 ms qu'elle
demande sont deux `setFloat` par frame, sans reconstruction de géométrie.

**Attention à la collision avec `UI-7`** ([v3](03-roadmap-v3.md)) : les deux
posent des écouteurs de survol. `UI-7` mutualise les 22 sites de `ui/` derrière
un socle unique ; `NAV-5` survole des objets **3D**, pas des widgets Lemur, donc
les deux ne se marchent pas dessus. À vérifier tout de même au moment de `UI-7`,
qui arrive après.

**Spec.** [`docs/graphics-effects/hover-effects.md`](../graphics-effects/hover-effects.md).

---

## 5. Questions ouvertes

1. **Jusqu'où va la rentrée (`MIS-10`)** — héritée de v1. La « désintégration »
   recouvre trois paliers de coûts sans commune mesure. (a) La trajectoire
   s'arrête à l'interface atmosphérique et on affiche le point d'impact prédit :
   c'est le périmètre écrit dans la fiche `MIS-10`. (b) La traînée plasma, qui
   est un **effet** et non une physique : c'est `FX-4`, et le fait de lui donner
   un identifiant dans cette version **répond à la moitié de la question**.
   (c) La désintégration réelle — flux thermique, ablation, fragmentation — qui
   est de la R&D et reste au backlog. `PHY-1 / L1` §4 note d'ailleurs que
   l'approximation « panneaux repliés » du coefficient balistique cesse d'être
   vraie pour une rentrée de fin de vie : la physique de (c) commence donc avant
   le flux thermique. **Reste à confirmer** que (a) + (b) suffisent pour cette
   version.
2. **Quelles familles de charge utile méritent un maillage propre (`AST-1`,
   `PHY-6`)** — le catalogue en compte cinq. Un maillage générique par
   `PayloadDomain` (deux : `EARTH`, lunaire) est beaucoup moins cher qu'un par
   entrée, et suffit probablement à faire disparaître le Falcon Heavy en GEO.
   À trancher **avant** de commander les assets, pas après.
3. **La bascule de repère (`RND-5`) est-elle globale ou par mission ?** Sa fiche
   dit « bascule globale dans le menu applicatif ». Avec plusieurs missions
   affichées, une bascule globale rend certaines traces illisibles pour en
   rendre une lisible. À constater à l'usage avant de payer le réglage par
   mission.
