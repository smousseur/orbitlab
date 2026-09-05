# v2 — préparation, avant découpage

**Ce document précède les découpages.** Il n'est ni un plan, ni une conception :
il enregistre les décisions prises en discussion le **5 septembre 2026**, avant
que `PHY-2` et `PHY-5` n'ouvrent leurs chantiers, plus les mesures qui les
fondent. Tout ce qui suit a été tranché en conversation ; le document
n'introduit aucune décision de son cru.

La version concernée est **v2.X.X** —
[`docs/roadmap/02-roadmap-v2.md`](../roadmap/02-roadmap-v2.md), *« Réalisme :
l'atmosphère, les objets qui se séparent, le retour sur Terre »*. Les deux
objectifs retenus pour elle sont **l'atmosphère et la traînée** d'une part, **les
vraies séparations et le suivi des débris** d'autre part.

---

## 1. Les assets 3D — `AST-1`

### 1.1 L'inventaire

Les quatre profils du catalogue — `LEO`, `GEO`, `LUNAR_FLYBY`, `LUNAR_ORBIT` —
ne demandent pas quatre jeux d'objets. Ils volent tous la même pile et ne
diffèrent que par la **date** de la seconde séparation. `GEO` et `LUNAR_ORBIT`
larguent déjà leur étage supérieur (`StageNames.UPPER_SEPARATION`, `"S2
separation"`) parce que la suite du vol revient à la charge utile ; `LEO` et
`LUNAR_FLYBY` ne le larguent pas, et c'est `PHY-6` qui leur apportera
l'événement manquant. **Le maximum d'objets simultanés est de trois**, quel que
soit le profil et quel que soit le lanceur.

**Onze maillages**, une fois le découpage du § 2 pris en compte :

| Maillage | Par | Rôle en vol |
|---|---|---|
| Propulseur d'appoint | lanceur | Instancié 2 ou 4 fois ; débris au largage des propulseurs |
| Corps central (S1) | lanceur | Porteur au décollage, puis débris |
| Étage supérieur (S2) | lanceur | Porteur après S1, puis débris |
| Coiffe (2 demi-coquilles) | lanceur | Habille la charge utile, puis masquée |
| Satellite Terre | domaine | `EARTH_OBS_SAT`, `GEO_SAT` |
| Sonde lunaire | domaine | `LUNAR_PROBE`, `LUNAR_ORBITER` |
| Module cargo | domaine | `CARGO_MODULE`, domaine `ANY` |

Soit 4 pièces × 2 lanceurs + 3 bus.

**Correction apportée en rédigeant.** La discussion avait arrêté **neuf**
maillages : trois pièces par lanceur (S1, S2, coiffe) plus trois bus. Ce compte
précédait la décision de séparer les propulseurs du corps central (§ 2), qui
scinde le « S1 » en deux pièces. Onze est la conséquence arithmétique de cette
décision, pas une décision nouvelle.

**La granularité des charges utiles est de trois, pas de cinq** : un bus
générique par `PayloadDomain`. Le défaut que nomme `PHY-6` — *« une mission
`GEO_SAT` calculée, volée et affichée dessine un Falcon Heavy en orbite
géostationnaire »* — disparaît avec n'importe quelle silhouette de satellite, et
à cent mètres dessinés la différence entre un satellite d'observation et un
satellite de télécommunications ne se lit pas.

### 1.2 Aucune chirurgie de maillage

**Décision : les deux lanceurs sont re-sourcés déjà séparés en nœuds**, plutôt
que découpés au plan. La contrainte devient un critère d'achat, pas une tâche de
production. `DT-12` — la silhouette d'Ariane 5 volée comme Ariane 62 — se
referme au passage, puisque le remplaçant est une Ariane 6.

Ce qui a conduit là est une mesure sur `heavy_falcon.gltf`. Après soudure par
position (23 120 sommets → 13 971), le fichier compte **176 coques connexes**,
et la répartition est l'inverse de l'intuition : **une seule coquille de 430
triangles couvre 98 % de la hauteur** — les trois corps, l'interétage, le S2 et
la coiffe soudés ensemble — pendant que les **25 138 triangles restants sont 175
disques de tuyères** en trois grappes, une par corps. Une séparation par
éléments détachés isolerait les tuyères, pas les étages.

`ariane/scene.gltf` est l'inverse : 257 nœuds, 106 maillages, 9 matériaux, et
une arborescence dont le premier niveau *est* le découpage physique
(`Rocket_main` / `Rocket_secondary`, avec `Fairing_3` en deux demi-coquilles et
`stage_sup_thruster_place` pour l'étage supérieur). Mais 355 212 triangles, et
une silhouette d'Ariane 5.

**Le budget de triangles part au même endroit dans les deux fichiers : les
tuyères.** `thruster_Vulcan` pèse à lui seul 163 132 triangles, **45,9 %** de
l'Ariane ; les tuyères du Falcon en font **98 %**. Sur un candidat, regarder
d'abord où passent les triangles : c'est la seule dépense qui s'allège sans
toucher à la silhouette.

### 1.3 Ce qu'une pièce doit respecter

Les deux premières règles étaient déjà dans le Javadoc de `LauncherAssets`. Les
trois suivantes n'existaient pas, parce qu'aucun maillage n'avait jamais été une
*pièce*.

1. **Le fichier se décompose par nœuds, sans toucher à la géométrie.** La règle
   qui prime, parce qu'elle ne se rattrape pas : les autres défauts se corrigent
   à l'export, celui-là condamne le fichier.

2. **Le nez pointe selon +Y après la transformation racine.**
   `SpacecraftPresenter` applique une correction unique — 90° autour de X — à
   tous les vaisseaux, non paramétrable par maillage.

3. **C'est la pile entière qui fait une unité, pas la pièce.**
   `AssetFactory.loadModel` fait un `setLocalScale(scale)` **brut**, sans
   renormaliser sur la boîte englobante : une pièce ré-exportée « à une unité de
   haut » serait dessinée à la taille de la fusée entière. La normalisation se
   fait une fois, sur la pile assemblée.

4. **L'origine d'une pièce est sur la pièce.** `LodView` pose l'ancre à la
   position propagée (`farAnchor.setLocalTranslation`) et fait tourner le bucket
   qui porte le modèle (`getModelBucket().setLocalRotation`). Il n'y a rien
   entre les deux : **l'origine du fichier est à la fois le point posé à la
   position propagée et le pivot de la rotation**. Une origine hors du corps se
   paie deux fois — l'objet est dessiné à côté de là où il est, et il pivote
   autour d'un point qui n'est pas lui.

   L'échelle donne l'ordre de grandeur : `2 × SPACECRAFT_RADIUS_METERS /
   PLANET_METERS_PER_UNIT` = 0,1 unité proche, et une unité proche vaut un
   kilomètre. **Une unité de maillage vaut donc 100 m** ; un décalage de 0,05
   unité fait 5 m à l'écran.

5. **La coiffe est un groupe même si elle n'est jamais larguée.** Ni `PHY-5` ni
   `PHY-6` ne demandent de largage de coiffe, mais si la charge utile devient un
   objet distinct, il faut pouvoir masquer l'enveloppe qui la contenait.

6. **Le budget de triangles n'est plus par mission, il est par objet.** `PHY-5`
   multiplie le coût par le nombre de débris, et `DT-17` note que la performance
   du rendu n'a jamais été profilée.

**Une conséquence à traiter dans `PHY-6`, pas ici.** `MissionRenderer` tient un
unique `SPACECRAFT_RADIUS_METERS = 50.0`, qui sert à la fois d'échelle de modèle
et de seuil de bascule LOD : tout ce qui vole est dessiné 100 m de haut.
Réutilisé tel quel, un `GEO_SAT` de deux tonnes serait aussi grand que le Falcon
Heavy qui l'a lancé. Les maillages de charge utile n'ont de sens qu'avec un
rayon par objet.

### 1.4 L'Ariane 64 candidate

Un lot de neuf fichiers (pile complète, S2 + coiffe, et sept débris) a été passé
à la grille du § 1.6 le 5 septembre. **Verdict : accepté.**

| Contrôle | Ariane 64 candidate | Falcon actuel | Ariane 5 actuelle |
|---|---|---|---|
| C1 coques soudées | 39 % | **98 % — refus** | 57 % |
| C2 séparation par nœuds | 8 groupes nommés | **aucune — refus** | 2 + 5 groupes |
| C3 nez +Y | élancement 6,1:1 | 5,7:1 | 3,9:1 |
| C4 hauteur normalisée | 1,0000 | 0,9994 | 0,9759 |
| C5 base à l'origine | +0,0000 | −0,0072 | +0,0009 |
| C6 triangles | 133 750 | 25 568 | 355 212 |
| C7 textures | 33 mat., 2 img. | 1 mat., 1 img. | 9 mat., 2 img. |

**Le remontage est exact.** Chaque fichier a été comparé au groupe
correspondant de la pile : S1 et les quatre propulseurs sont en place au 10⁻⁴
près, mêmes bornes et mêmes comptes de triangles, et la somme des huit groupes
fait exactement les 133 750 triangles de la pile.

Deux constats que le relevé a ajoutés :

- **`ariane_64_S2.gltf` est l'étage 2 *plus* la coiffe** — 28 706 = 25 572 + 2 ×
  1 567 — et c'est bien ce qu'il faut : entre la séparation S1 et la séparation
  de charge utile, l'objet volé *est* S2 + coiffe. Les trois sont des racines de
  scène séparées, donc la coiffe se détache par son nom le moment venu. Aucun
  fichier ne manque.
- **Quatre fichiers ont leur origine sur l'axe de la pile et non sur la leur.**
  Les quatre propulseurs sont à 5,9 m de leur propre axe à l'échelle de rendu ;
  S1, S2 et les deux coiffes sont corrects. **Correction en Blender, en x et z
  seulement** — une règle de recentrage automatique au chargement toucherait
  aussi la pile complète, dont l'origine est aujourd'hui à sa base comme celle
  de tous les lanceurs livrés, et déplacerait le point d'ancrage de toutes les
  missions existantes pour un problème qui ne concerne que quatre fichiers.

Une fois les propulseurs recalés, leur position dans la pile n'est plus dans le
fichier. Si `PHY-5` veut faire apparaître le débris exactement là où il était à
l'instant du largage, il lui faut cette table, mesurée sur la pile, en unités de
maillage :

```
booster1  (-0.0416, +0.1863, -0.0416)
booster2  (+0.0416, +0.1863, +0.0416)
booster3  (-0.0416, +0.1863, +0.0416)
booster4  (+0.0416, +0.1863, -0.0416)
```

Les quatre forment deux paires diamétralement opposées : `booster1` avec
`booster2`, `booster3` avec `booster4`.

**Fidélité de la silhouette.** Le rapport propulseur / corps du maillage vaut
**0,698**, contre 0,630 dans les sections du catalogue (3,4 m pour 5,4 m).
Écart +11 %. L'Ariane 5 actuelle est à 0,54, écart −14 % dans l'autre sens :
c'est meilleur, sans être exact.

**Deux points pratiques.** La copie versionnée sous
`src/main/resources/models/vehicles/ariane_64/` ne contient que la pile et deux
textures — les pièces n'y sont pas. Et les fichiers de `debris/` référencent
`../textures/…` : la structure de dossiers résout correctement, mais rien ne dit
que l'`AssetManager` de JME3 accepte un `..` dans un URI GLTF. À vérifier au
premier chargement.

### 1.5 Le Falcon Heavy re-sourcé

Un lot de neuf fichiers a été passé à la grille du § 1.6 le 6 septembre.
**Verdict : accepté.**

| Contrôle | Falcon Heavy candidat | Falcon actuel |
|---|---|---|
| C1 coques soudées | 62 % | **98 % — refus** |
| C2 séparation par nœuds | 6 groupes nommés | **aucune — refus** |
| C3 nez +Y | élancement 5,7:1 | 5,7:1 |
| C4 hauteur normalisée | 1,0000 | 0,9994 |
| C5 base à l'origine | −0,0002 | −0,0072 |
| C6 triangles | 274 398 | 25 568 |
| C7 textures | 35 mat., 4 img. | 1 mat., 1 img. |

Ce n'est pas une variante du fichier du dépôt, c'est un autre modèle : les deux
verrous tombent d'un coup, et les six groupes de scène sont exactement les pièces
que le § 1.1 demande — `First Stage Center_16`, `Booster Left_31`, `Booster
Right_42`, `Second Stage_18`, `Left Fairing_20`, `Right Fairing_19`. `DT-12` se
referme donc aussi côté Falcon, pour une autre raison que l'Ariane : ici la
silhouette était bonne, c'est le maillage qui ne se découpait pas.

**Décision : un fichier par objet dessiné, aucune manipulation de nœuds GLTF dans
le code.** Elle est ce qui fixe le nombre de fichiers, et elle a une conséquence
sur la grille : **C2 cesse d'être un verrou sur une pièce.** Le contrôle n'existe
que pour dire si une coiffe peut être détachée de l'étage qui la porte ; sans
détachement à l'exécution, une pièce doit seulement *contenir* la bonne
géométrie. C2 ne reste éliminatoire que sur la pile.

**Conséquence sur l'inventaire du § 1.1.** Ce tableau compte des maillages —
quatre par lanceur — quand un fichier par objet dessiné en demande huit pour le
seul Falcon : trois configurations de vol et cinq pièces. Le décompte « onze
maillages » reste juste sur ce qu'il compte ; ce n'est simplement pas le nombre
de fichiers à livrer. Arithmétique de la décision ci-dessus, pas décision
nouvelle.

Les objets dessinables se lisent sur la chronologie du vol, et c'est elle qui
donne la liste :

| Phase | Porteur | Débris qui apparaît |
|---|---|---|
| Décollage → largage propulseurs | pile complète | — |
| → séparation S1 | corps + S2 + coiffe | propulseur × 2 |
| → séparation charge utile | S2 + coiffe | corps central |
| après séparation charge utile | charge utile (bus) | S2 nu |

Le « S2 nu » vient de la règle 5 du § 1.3 : la coiffe n'est jamais larguée, mais
dès que la charge utile devient un objet distinct, l'enveloppe qui la contenait
ne peut plus être dessinée autour d'un étage vide.

```
heavy_falcon.gltf                274 398 tri   h = 1,0000    9,85 Mo
heavy_falcon-after_boosters      105 394       h = 1,0000    4,13
heavy_falcon-after_s1             20 574       h = 0,3832    0,80
heavy_falcon-core                 84 820       h = 0,6873    3,34
heavy_falcon-S2                   18 216       h = 0,2133    0,73
heavy_falcon-booster_left         83 750       h = 0,6531    3,28
heavy_falcon-booster_right        83 750       h = 0,6531    3,28
heavy_falcon-fairing_left          1 303       h = 0,1878    0,05
heavy_falcon-fairing_right         1 055       h = 0,1878    0,03
textures                                                     3,64
                                                            29,13 Mo
```

**Le remontage est exact.** La pile fait 86 324 + 2 × 83 750 + 18 216 + 1 303 +
1 055 = 274 398 au triangle près, et chaque pièce retombe sur son groupe : les
propulseurs et les demi-coiffes au triangle, `-S2` sur `Second Stage_18` à
dy = +0,6166. Les deux fichiers ré-exportés ont été comparés sommet à sommet dans
les **deux sens** — 800 sommets échantillonnés chacun — et l'écart maximal au
plus proche voisin vaut **0,085 mm** à l'échelle de rendu : la quantification
float32 du ré-export, rien de perdu ni d'ajouté.

**Les bras d'attache.** Le corps central porte quatre nœuds `Retractable Arm`,
1 504 triangles (504 + 504 + 248 + 248), tendus jusqu'à z = ±0,060 — exactement
là où les propulseurs sont posés. **Décision : ils disparaissent dès qu'il n'y a
plus de propulseurs**, donc ils ne vivent que dans la pile. Ce qui compte n'est
pas leur présence mais la stabilité du compte : le corps fait 84 820 dans
`-after_boosters` *et* dans `-core`, donc il ne change pas de géométrie en
devenant débris. Le seul saut est au largage des propulseurs, à l'instant précis
où les bras cessent de tenir quelque chose.

**Les origines sont bonnes, contrairement à l'Ariane 64.** Les propulseurs sont
recentrés sur leur propre axe et basés à y = 0 ; le corps et le S2, qui sont sur
l'axe, ne sont translatés qu'en y. La correction Blender du § 1.4 n'a pas
d'équivalent ici. Table de replacement pour `PHY-5`, en unités de maillage :

```
core           ( 0.0000,  0.0000,  0.0000)   deja en place
S2             ( 0.0000, +0.6166,  0.0000)
booster_left   (+0.0017, -0.0002, +0.0581)
booster_right  (-0.0011, -0.0002, -0.0579)
fairing_left   (+0.0012, +0.8120, +0.0179)
fairing_right  (+0.0012, +0.8120, -0.0176)
```

**`booster_right` est un doublon exact** — `booster_left` tourné de 180° autour
de Y, mesuré à 10⁻⁶ unité près, soit 0,1 mm dessiné. Il est **gardé
délibérément** : 3,2 Mo pour éviter une orientation initiale codée par instance,
ce qui serait exactement la logique que la décision ci-dessus exclut.

**Le budget est dans les baies moteur, et il dépasse.** 274 398 triangles, 2,3 ×
le plafond C6, et le pic dessiné n'est pas au décollage mais juste après le
largage des propulseurs : 105 394 + 2 × 83 750 = **272 894 simultanés**. La
répartition tranche la question de ce qu'il faudrait décimer :

| | triangles | part |
|---|---|---|
| sous y = 0,050 — les trois baies moteur | 176 422 | **64,3 %** |
| grilles, pattes d'atterrissage, hydraulique, bras | 15 136 | 5,5 % |

Cinq mètres sur cent portent les deux tiers du fichier. C'est la leçon du § 1.2
avec d'autres chiffres — 64 % ici contre 98 % sur l'ancien Falcon, parce que la
cellule s'est enrichie, pas parce que les tuyères ont maigri. Décimer se ferait
une fois sur la pile puis se repropagerait aux huit autres fichiers : les comptes
ci-dessus sont les cibles à retrouver.

**Deux points pratiques, à l'inverse de ceux du § 1.4.** Les neuf fichiers sont à
plat dans un seul dossier : aucun `..` dans un URI, la question ouverte n° 4 ne
se pose pas sur ce lot. Et les neuf référencent la même
`textures/Material_022_metallicRoughness.png` — 3,48 Mo des 3,64 — qui résout
vers la **même clé** d'`AssetManager` : chargée une fois, pas neuf. À préserver
si le lot est un jour réorganisé en sous-dossiers.

### 1.6 L'outil de tri

Le défaut qui condamne un maillage — une coque unique soudée — **ne se voit pas
dans un aperçu**. Il ne se lit ni sur la silhouette, ni sur le compte de
triangles, ni sur la liste des matériaux.
[`gltf-triage.py`](gltf-triage.py) passe sept contrôles sur un `.gltf` ou un
`.glb`, sans dépendance hors bibliothèque standard :

```
python docs/v2-preparation/gltf-triage.py mon-candidat.glb
python docs/v2-preparation/gltf-triage.py --piece debris/booster1.gltf
```

| | Contrôle | Ce qui disqualifie |
|---|---|---|
| C1 | Coques soudées, composantes connexes après soudure par position | Une coquille couvre plus de 85 % de la hauteur |
| C2 | Séparation par nœuds, racines de scène comprises | Aucun niveau ne sépare la pile |
| C3 | Nez selon +Y après la transformation racine | Axe long ≠ Y, ou pointe vers −Y |
| C4 | Hauteur normalisée | Hors de [0,9 ; 1,1] — corrigeable à l'export |
| C5 | Base à l'origine | \|y<sub>min</sub>\| > 0,02 — corrigeable à l'export |
| C6 | Budget de triangles, et sa répartition par groupe | Au-delà de ~120 000, ou concentré dans un détail invisible |
| C7 | Matériaux et fichiers de texture | Une image référencée est absente |

**C1 et C2 sont les seuls verrous.** C3 à C5 se corrigent en une transformation
à l'export, C6 en décimant les tuyères, C7 en récupérant la texture manquante.
C1 et C2, non.

`--piece` s'applique à une pièce détachée : **C1, C3, C4 et C5 sont des critères
de *pile*** et n'ont aucun sens sur un fragment — un étage n'a pas de nez, ne
doit surtout pas faire une unité de haut, et un propulseur est légitimement une
coque unique couvrant sa propre hauteur. C3 à C5 sont alors remplacés par les
bornes monde, à comparer avec celles du groupe correspondant dans la pile ; C1
est rapporté sans verdict. **C2 continue de compter** : c'est lui qui dit si une
coiffe peut être détachée de l'étage avec lequel elle est livrée.

L'outil a été calibré sur les trois maillages du dépôt, et **l'Ariane 64
candidate a révélé deux défauts dans ses propres mesures**, tous deux corrigés
avant son adoption : C1 comparait des hauteurs en coordonnées locales sans
appliquer les transformations de nœuds, et C2 ne cherchait la séparation que
sous un nœud unique, jamais au niveau des racines de scène — ce qui produisait
un **faux refus** sur un fichier parfaitement séparé.

---

## 2. Le découpage propulseurs / corps central

**Décision : c'est un item à part entière, et il précède les deux objectifs de
la version.** Il n'est ni une sous-tâche de `PHY-2` ni une sous-tâche de
`PHY-5` : il sert les deux, il est le plus gros changement de la version, et
c'est lui qui invalide toutes les références. Rangé dans `PHY-2`, il serait
mesuré comme du travail d'atmosphère, et ce n'en est pas.

### 2.1 Pourquoi

**Les deux fictions du modèle sont la même fiction.** L'agrégat existe parce que
`VehicleStack` résout *un seul* étage actif. L'Isp « trajectoire moyenne » existe
parce qu'il n'y a pas de traînée — le Javadoc de `Launchers` le dit tel quel :
*« with no atmosphere modeled, 296 s is the proxy for real ascent losses »*.
`PHY-2` supprime la seconde. N'en supprimer qu'une revient à recalibrer un
mélange contre de la vraie physique : à ajuster un nombre qui n'a plus de
référent.

**Le symptôme est déjà écrit dans le catalogue.** L'agrégat Ariane 62, à 9 960
kN et 300 s, s'éteint à **T+128,2 s** — fidèle aux P120C (~130 s), faux pour le
Vulcain, qui brûle réellement ~8 min. Le modèle **ne vole donc jamais la phase
corps-seul**. Sans traînée c'est une erreur de forme ; avec traînée c'est une
erreur d'intégrale, la traînée s'intégrant le long de toute l'ascension. Et le
Javadoc précise qu'aucune manette ne peut le corriger : *« stretching the burn
to 300 s would need the aggregate thrust below the lift-off weight »*.

**La section frontale est déjà documentée comme une grandeur par largage.**
`ActiveStageInfo` dit qu'elle change *« at a jettison, and only there »*.
L'Ariane 62 S1 déclare 41,1 m² = 22,9 (corps) + 18,2 (deux P120C) pour toute la
combustion ; faute de largage de propulseurs, elle ne retombe jamais à 22,9.
Séparés, les deux phases ont leur bonne section, et chaque propulseur largué
hérite de la sienne (π·1,7² = 9,1 m²) — **ce dont `PHY-5` a besoin de toute
façon** pour propager le débris. Sans le découpage, il faudrait l'inventer.

**La machinerie est déjà construite et testée.** `StageCapabilities` interdit
déjà à un solide de se rallumer ou de s'éteindre sur commande, et
`variableLoad()` retourne `false` pour `SOLID` — *« Solids fly full »*.
`StageRole.BOOSTER` existe et n'apparaît que dans **cinq fichiers de test**,
jamais dans `Launchers` : le modèle a anticipé le cas, le catalogue ne s'en est
jamais servi. Le Javadoc de `StageCapabilities` annonce d'ailleurs qu'il est
*« the input of the future profile derivation »*.

**Et le catalogue cesserait de mentir.** L'Ariane 62 déclare `CRYOGENIC` pour un
bloc à 65 % solide, en assumant que c'est *« a modelling convention, not a
chemical claim »*, uniquement parce que `SOLID` gèlerait la charge hors du
balayage multi-λ. Séparés, geler la charge du P120C est exactement juste. Point
qui compte pour l'optimiseur : **le découpage ajoute un étage mais pas une
variable**, la charge d'un solide n'étant pas libre.

**Le bénéfice n'est pas le même sur les deux lanceurs.** L'agrégat Ariane
mélange deux ergols d'Isp très différentes — c'est la fiction. L'agrégat Falcon
Heavy mélange trois corps **identiques** : sur l'Isp il est exact, et son erreur
est ailleurs, dans la forme de l'étagement et dans la section, qui passe de
trois corps à un au largage des propulseurs latéraux.

**La décomposition en ergols est déjà dans le dépôt.** 65 % de 434 t = 282 t de
solide, soit **141 t par propulseur**, ce qui tombe sur la capacité réelle d'un
P120C ; le reste, 152 t, est le LLPM. Le partage des **36 t de masse sèche**
entre propulseurs et corps, et les Isp et poussées individuelles, ne s'y
trouvent pas : c'est la donnée à sourcer.

### 2.2 Ce qu'il coûte

**29 fichiers de `src/main` touchent la notion d'étage actif**, et l'invariant
est explicite dans `VehicleStack` : *« the active stage changes only by an
explicit jettison »*, *« resolves exactly one active stage »*. La combustion
parallèle le casse. C'est le vrai coût, et il est identique quel que soit le
moment où il est payé — donc le payer avant la calibration est strictement
meilleur que de le payer après.

`StageSeparationStage` a déjà un mode d'échec de cette famille : le contrôle
`expectedStageIndex` existe parce que, sur le profil GEO, *« a lighter upper
stage makes the gravity turn stop before S1 is dry, leaving S1 active »*, et un
« S2 separation » non vérifié larguait alors S1. Avec un étage de plus, il y a
plus de façons pour la comptabilité de masse de se tromper d'étage.

### 2.3 L'ordre retenu

Atmosphère, recalibrage d'Isp et structure d'étagement font trois changements de
comportement sur la même trajectoire. Pour préserver l'attribution :

1. **Découper d'abord, sans traînée**, chaque étage portant son Isp vide réelle.
2. **Puis `PHY-2`** allume la traînée et calibre une seule fois, contre une
   ascension de forme physique.

L'état intermédiaire est délibérément faux, et faux dans une direction
**prévue** : l'ascension doit sur-performer d'à peu près les **408 m/s** (Falcon
Heavy) et **671 m/s** (Ariane 62) que l'Isp actuelle absorbe — les chiffres de
`DT-13`. Ce n'est pas une régression à cacher, c'est la mesure de ce que la
traînée devra fournir. **Prédiction, pas mesure** : elle est à vérifier au
premier vol découpé, et un écart franc serait une information, pas un échec.

### 2.4 Un étage de propulseurs à multiplicité

**Agréger un solide et un cryogénique est une fiction ; agréger N solides
identiques est exact.** Les P120C partagent la même Isp, s'allument ensemble,
brûlent jusqu'à extinction ensemble et se larguent ensemble. Un étage unique
portant N × poussée, N × ergols et N × π·1,7² de section n'approxime rien : il
décrit le bloc. Donc **un étage de propulseurs, avec une multiplicité** — 2 pour
l'Ariane 62, 4 pour l'Ariane 64 — et non N étages.

La tension avec `PHY-5` se règle du même coup : **un étage, N débris**. Le
largage produit N objets dont les états ne diffèrent que par la direction de
l'impulsion latérale du § 3.

### 2.5 Ce qu'il rouvre : Ariane 64

Le Javadoc de `Launchers` écarte l'Ariane 64 : *« with four boosters the
distortion grows until the model separates A64 from A62 by 3 % where reality
separates them by a factor 2.5 in GTO capacity »*.

**Cette exclusion est un produit de l'agrégation.** Dans l'agrégat, ajouter deux
P120C ajoute masse et poussée à peu près proportionnellement : le rapport de
masses bouge à peine, le bloc s'éteint toujours vers 128 s. Or l'avantage réel
de l'A64 vient de ce que les propulseurs supplémentaires portent un empilement
plus lourd jusqu'au largage, après quoi le corps continue seul à haute Isp
pendant huit minutes — mécanisme que l'agrégat détruit, puisqu'il ne vole jamais
cette phase.

**L'exclusion est donc à remesurer, pas à hériter.** D'autant que le « 3 % » et
le « facteur 2,5 » viennent de `docs/launchers/01-ariane-62.md` §3 — un document
que le Javadoc cite deux fois et **qui n'existe pas dans le dépôt**.

Une fois le découpage fait, le coût d'ajout de l'A64 est un entier : même corps,
même étage supérieur, même coiffe, seule la multiplicité change. Et le **même
maillage sert les deux lanceurs** : `LauncherAssets` les fait pointer sur le
même fichier, et la seule différence est le nombre de nœuds `Booster.*` détachés
au chargement — deux pour l'A62, zéro pour l'A64.

---

## 3. L'impulsion de séparation

**Décision : oui, mais son intérêt n'est pas où on l'attend, et la version
physiquement honnête ne se voit pas.**

Sans impulsion, les deux objets ne restent pas ensemble longtemps : le porteur
allume et tire à 8–15 m/s², donc en dix secondes ils sont à des centaines de
mètres. Ce n'est pas l'impulsion qui les sépare — **sauf pendant le coast
interétage**. `AscentProfile` en donne 2 s pour le Falcon Heavy et 5 s pour
l'Ariane 62, ce dernier étant le refroidissement du Vinci. Pendant ce coast, les
deux objets sont balistiques avec un état identique : exactement superposés,
interpénétrés, et c'est le moment où la caméra regarde. Le seul travail de
l'impulsion est de remplir ce coast.

**Et une impulsion axiale honnête ne le remplit pas.** Un pousseur réel donne
~1 m/s ; sur les 5 s de l'Ariane, 5 m, soit 5 % d'une longueur de véhicule
dessinée. Sur les 2 s du Falcon, 2 m. Deux choses achètent l'image :

- **L'impulsion latérale des propulseurs.** La séparation réelle les pousse vers
  l'extérieur, perpendiculairement au vol, et ils partent déjà à 5,9 m de l'axe.
  Quelques m/s latéraux sur 5 s les mettent à 15–20 m de l'axe contre un
  véhicule de 100 m. C'est visible, et c'est le mouvement réel.
- **La rotation propre du débris.** `SpacecraftPresenter` aligne *tout* objet sur
  son vecteur vitesse (`lookAt` + lissage) : un débris volerait nez en avant,
  comme un véhicule piloté, ce qui est la chose la plus fausse visuellement.
  Donner aux débris une rotation propre coûte moins qu'une impulsion et rapporte
  plus.

**Contrainte dure : l'impulsion est à sens unique.** Une séparation réelle
impose une réaction au porteur ; l'ajouter changerait son état au largage, donc
tous les profils calibrés et tous les épinglages à tolérance zéro, pour ~0,01
m/s de physique. Aujourd'hui `StageSeparationStage` ne touche que la masse — il
pose la masse de référence de la pile au-dessus et coaste. **Cela doit rester
vrai.** L'impulsion vit sur l'objet débris, propagé à part, que l'optimiseur ne
voit explicitement jamais (`PHY-5` : *« L'optimiseur ne voit pas les débris »*).

---

## 4. Ce que le relevé dément

| Énoncé | Où | Ce que la mesure dit |
|---|---|---|
| *« un maillage générique par `PayloadDomain` (deux : `EARTH`, lunaire) »* | `02-roadmap-v2.md` §5 q. 2 | L'énumération en compte **trois** — `ANY` est un domaine à part entière, porté par `CARGO_MODULE` et documenté comme tel. L'arbitrage était 3 contre 5 |
| *« `models/vehicles/` ne contient que trois dossiers, tous des lanceurs »* | fiche `PHY-6` | Exact sur le compte, trompeur sur le contenu : `falcon/` et `heavy_falcon/` sont le **même maillage** (25 568 triangles, même texture), et `falcon/` n'est référencé par aucune ligne de code. Trois dossiers, **deux véhicules** |
| *« Seule la silhouette est fausse »* | fiche `AST-1`, `DT-12` | Vrai, et de combien : les propulseurs de l'Ariane 5 courent sur **59,4 %** de la hauteur, et leur diamètre relatif au corps vaut **0,54** contre 0,63 dans les sections du catalogue |
| `docs/launchers/01-ariane-62.md` §2 et §3 | Javadoc de `Launchers` | Le répertoire `docs/launchers/` **n'existe pas**. Les cotes utilisées ici viennent des `AerodynamicProperties` du catalogue, seule source de géométrie encore présente |

---

## 5. Ce qui reste ouvert

1. **La longueur relative des propulseurs de l'Ariane 64 candidate.** Le
   maillage les donne à **36,2 %** de la hauteur de la pile. Une référence hors
   dépôt pour un P120C sur A62 donne plutôt 21–24 %. Le dépôt ne peut pas
   trancher : le catalogue porte des sections, pas des longueurs. À vérifier sur
   la documentation ESA, ou à assumer.
2. **Comment refermer l'écart quatre propulseurs / Ariane 62 au catalogue.**
   Masquer deux nœuds au chargement, exporter une variante à deux propulseurs,
   ou faire revenir l'A64 au catalogue (§ 2.5). La discussion penche vers le
   masquage de nœuds, qui sert aussi l'A64 avec un seul mécanisme, mais la
   décision n'est pas prise.
3. **Panneaux repliés ou déployés sur le bus « satellite Terre ».** `PHY-6`
   s'interdit le *déploiement* et les coefficients balistiques du catalogue
   supposent des panneaux repliés — mais ni l'un ni l'autre ne dit dans quelle
   configuration figée le maillage doit être livré. Un satellite GEO au poste,
   replié, ressemble à une boîte ; or le critère de fin de version est
   précisément *« un satellite en orbite ressemble à un satellite »*.
4. **`..` dans un URI GLTF côté JME3** (§ 1.4).
5. **Le partage des 36 t de masse sèche de l'Ariane 62** entre propulseurs et
   corps, et les Isp et poussées individuelles (§ 2.1).
6. **Le dépassement du budget C6 sur le Falcon Heavy re-sourcé.** 274 398
   triangles, 2,3 × le plafond, dont 64,3 % dans les trois baies moteur
   (§ 1.5). Une décimation se fait une fois sur la pile et impose de refaire
   les huit autres fichiers du lot. Non tranché.
