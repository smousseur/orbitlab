# PHY-8 / L4 — L'Ariane 64 remplace l'Ariane 62 — conception

Lot **L4** du découpage ([`01-decoupage.md`](01-decoupage.md) §5), au-dessus du mécanisme de `L1`,
du catalogue éclaté de `L2` et de l'étranglement de `L3`, mesuré contre
[`02-baseline-L0.md`](02-baseline-L0.md). Ce que le lot rend vrai : **le catalogue et l'écran volent
la même fusée.**

Ce document est plus long que ses trois prédécesseurs, et il faut dire pourquoi tout de suite.
`L1`, `L2` et `L3` étaient des lots de catalogue et de mécanisme. `L4` a commencé comme tel et la
mesure l'a fait déborder : **une Ariane 64 éclatée ne peut pas fermer une mission avec la chaîne
d'après-ascension que ce dépôt possède aujourd'hui.** Le §2.5 mesure pourquoi, le §3.3 décide de la
brique qui manque, et le §1 assume la frontière que cela fait traverser au chantier.

---

## 1. Périmètre

**Dans `L4`** : l'entrée `ARIANE_64` du catalogue, éclatée et calibrée, en remplacement de
l'`ARIANE_62` ; le Javadoc de `LauncherAssets`, qui décrit un défaut corrigé ; **deux manœuvres que
le transfert ne savait pas faire** — insérer depuis un arc sub-orbital, et abaisser un apogée ; un
terme de coût sur la profondeur de la remise des commandes ; et le second re-baseline du chantier.

**Hors `L4`** : les hauteurs (`L5`), les charges utiles (`L6`), la traînée (`PHY-2`), la dette
`DT-13` qui reste entière.

### 1.1 La dérive de périmètre, assumée

Le §1 du découpage énumère ce que `PHY-8` rend vrai, et il n'y a **aucune manœuvre** dans cette
liste. Le chantier découpe des étages ; `L4` livre deux manœuvres et un terme de fonction de coût.

Ce n'est pas une extension de confort, c'est la condition pour que le lot existe : sans elle,
l'Ariane 64 éclatée ne vole aucune mission, et le §5 du découpage exige *« une mission LEO 400 km
qui insère encore »*. Les trois options mesurées au §2.6 sont soit la même dérive écrite autrement,
soit l'abandon de l'éclatement pour l'Ariane — ce qui contredirait le §1, qui promet la combustion
parallèle **sur les deux lanceurs**.

Ce que la dérive rend en échange : la brique bute sur la précondition qui **gèle le profil polaire
de `CentralBodyBaselineTest` depuis `BUG-6`**. La même manœuvre débloque les deux.

---

## 2. Les mesures qui décident

### 2.1 `DT-12` est déjà fermé, et l'écran est en avance sur le catalogue

`LauncherAssets` ne pointe plus vers une Ariane 5. Sa table associe `ARIANE_62` à
`models/vehicles/ariane_64/ariane_64.gltf`, et le dossier `ariane/` n'existe plus : `AST-1` a livré
le maillage, en dix pièces dont **quatre propulseurs**, plus un `-after_boosters` et un
`-after_s1`.

Seul le Javadoc de la classe est resté en arrière. Ses deux paragraphes *« Known limitation … the
mesh paired with ARIANE_62 is an Ariane 5 »* décrivent un état qui n'existe plus.

**Le découpage lit donc la dépendance à l'envers.** Il écrit *« `LauncherAssets` cesse de décrire
une Ariane 5, `DT-12` se ferme »* ; en réalité l'application **dessine déjà une Ariane 64 à quatre
propulseurs** pendant que le catalogue en déclare une à deux, et c'est le catalogue qui rattrape
l'écran. `DT-12` ne se ferme pas dans ce lot : il s'y constate fermé.

### 2.2 L'Ariane 64 se décompose entièrement depuis l'entrée existante

| | Agrégat A62 | A64 éclatée |
|---|---|---|
| Masse sèche | 36 t | `4 × 11 + 14` = **58 t** |
| Ergols | 434 t | `4 × 141 + 152` = **716 t** |
| Section | 41,1 m² | `4 × 9,08 + 22,9` = **59,2 m²** au décollage |

Les 36 t se décomposent exactement en deux P120C de 11 t et un LLPM de 14 t, comme les 434 t se
décomposaient en `2 × 141 + 152` — le Javadoc de l'entrée déclare *« 65 % of this block's propellant
is solid »* — et comme les 41,1 m² valent `π·2,7² + 2·π·1,7²`. **Aucune source externe n'est
nécessaire**, exactement comme pour le Falcon Heavy à `L2`.

### 2.3 Les deux axes de calibration sont orthogonaux

C'est la mesure qui rend le lot pilotable, et elle n'était pas acquise.

**Axe 1 — l'Isp à durées de combustion fixées.** La poussée suit, `F = ṁ·Isp·g₀` avec `ṁ` imposé
par la durée. Déplace le ΔV et la poussée ensemble ; ne déplace **ni** les durées, **ni** le
plancher
d'étagement, **ni** le calendrier de l'ascension. Amplitude sur la totalité des deux brackets, à 20
t
de charge utile : ΔV de **9 724 à 11 183 m/s**, poussée au décollage de **11,63 à 13,19 MN**, T/W de
**1,43 à 1,62**.

**Axe 2 — la poussée à Isp fixée.** Les durées suivent. Mesuré : doubler la poussée du corps fait
passer la phase corps-seul de **350,9 à 110,5 s** et le plancher d'étagement de **485,8 à 245,4 s**,
sans toucher au budget.

On peut donc calibrer la performance sans toucher à la forme de l'ascension, et réciproquement.

### 2.4 L'ancrage ne peut pas être la capacité absolue

**L'Ariane 62 déjà au catalogue sur-estime sa propre capacité LEO d'un facteur 1,94** : le modèle
place **19 988 kg** à 400 km là où la vraie A62 en place ~10 300. Ce n'est pas un défaut que `L4`
introduit, et ce n'est pas un défaut qu'il doit corriger : aucun code ne lit ce chiffre, les
missions
volent des charges bien plus petites, et le rendre juste serait rouvrir `DT-13` en entier.

**Le rapport entre les deux lanceurs, lui, est ancrable.**

| Isp propulseurs | Isp corps | A64 / A62 modélisé |
|---|---|---|
| 265 | 360 | 1,99 |
| **278,5** | **360** | **2,09** |
| 278,5 | 380 | 2,24 |
| 250 | 400 | 2,15 |

La réalité donne 21 600 / 10 300 = **2,10**.

Et le couple qui tombe juste est **exactement celui que le §3.4 du découpage prédisait sans le
chiffrer** : 278,5 s est l'Isp de vide réelle du P120C, donc **les propulseurs ne portent aucune
dette** ; 360 s est à 36 % du bracket `[320, 431]` du Vulcain, donc **le corps en abandonne 71**.
*« Le proxy des propulseurs est presque honnête et la dette se concentre sur le corps »* : mesuré,
et chiffré.

### 2.5 Les trois étages analytiques partagent une précondition, et l'A64 la viole

C'est la mesure qui décide du lot.

L'ascension d'une A64 éclatée rend les commandes sur un **arc sub-orbital très excentrique** :
apogée 371,6 km — soit la bonne altitude — et périgée **−3 536 km**. Or les trois étages
d'après-ascension du dépôt planifient tous leur combustion à un apogée trouvé en propageant vers
l'avant, et le disent chacun dans ses mots :

| Étage | Ce qu'il rend |
|---|---|
| `AnalyticHohmannTransferStage` | *No apogee found within one transfer half-period* |
| `AnalyticApogeeCircularizationStage` | *No apogee found for the circularization burn* |
| `AnalyticTrimBurnStage` | *no apogee detected within one period, skipping* |

Un véhicule dont le périgée est sous la surface n'a **aucun apogée devant lui** : il rentre avant.
Enchaînées à la main sur la remise des commandes de l'A64, les deux dernières le montrent en trois
temps :

```
remise des commandes  apogée 371,6 km, périgée −3 536 km, pente +0,2°
circularisation       plane et brûle 14 424 kg  →  −945 × 414 km
trim                  no-op complet, masse et orbite identiques au chiffre près
```

La circularisation fait **la moitié du travail** — elle pose l'apogée à 414 km pour une cible à 400,
et laisse le périgée à −945 km, ce que son propre Javadoc assume en le déléguant au trim. Le trim ne
peut pas s'exécuter, pour la raison qui vient d'être dite.

**Et les ergols sont là** : il reste 13 242 kg après la circularisation, soit 1 843 m/s, contre
~440 m/s pour remonter le périgée. Le véhicule peut le faire ; **la chaîne n'a aucun étage qui sache
l'exprimer.**

### 2.6 La brique manquante, mesurée avant d'être conçue

Une combustion prograde nue depuis la remise des commandes, **sans attendre d'apogée**, suffit à
mettre le véhicule en orbite :

| MECO | avant | après |
|---|---|---|
| `t = 560 s, n = 0,450` | −3 456 × 441 km | **+24,5 × 653,6 km** |
| `t = 520 s, n = 0,500` | −3 790 × 466 km | **+1,8 × 579,1 km** |
| `t = 560 s, n = 0,500` | −3 752 × 520 km | **+106,1 × 607,0 km** |

Le périgée devient positif, donc un apogée existe devant, donc toute la chaîne existante redevient
applicable.

**Et la même mesure donne la difficulté de la conception.** Le dimensionnement impulsionnel demande
1 723 m/s, ce qui fait **435 s de combustion réelle** au Vinci ; l'impulsion étalée sur un arc de
sept minutes monte l'apogée bien au-delà de la cible et laisse le périgée en retard — 24 × 654 km au
lieu d'une circulaire à 441. C'est la leçon que `MIS-4 / L6` a déjà payée quatre fois dans ce dépôt
:
**toute boucle doit évaluer ses candidats sur ce qui sera réellement volé**, jamais sur la forme
fermée.

### 2.7 Ce que le remplacement casse, et qui est sur le disque

`ScenarioVehicle` porte un `launcherId`, `ScenarioMapper` l'écrit sous `LAUNCHER_TYPE` dans
`~/.orbitlab/scenarios/<nom>.json`, et `Launchers.byId` **lève** un `IllegalArgumentException` sur
un id inconnu. Retirer `ARIANE_62` du catalogue rend donc illisible tout scénario déjà enregistré
qui la référence.

### 2.8 Deux choses que le découpage annonce et qui n'ont rien à faire

- **Le masque du balayage λ n'est pas à revoir.** Le découpage écrit *« le masque du balayage λ est
  revu, pas hérité »*. Mesuré : `lambdaScaledMask` ne met sous λ que l'étage du haut **et seulement
  s'il est à charge variable**, et `allVariableLoadMask` filtre sur `variableLoad()`. Les deux
  excluent un étage `SOLID` par construction. Déclarer les P120C solides suffit.
- **`ShutdownMode` n'est lu nulle part** hors de sa propre validation. Déclarer les P120C en
  `BURN_TO_DEPLETION` — ce que `StageCapabilities` impose à un solide — ne change aucun
  comportement.

---

## 3. Décisions de conception

### 3.1 L'Ariane 64 éclatée, calibrée sur le rapport entre lanceurs

**Décision : `[P120C ×4, LLPM, ULPM]`**, avec les masses et sections du §2.2, et le couple d'Isp
**278,5 s / 360 s** du §2.4. Les poussées se déduisent des durées de contrôle du découpage —
propulseurs ~130 s, Vulcain ~8 min — et non l'inverse.

**Ce que cette décision enregistre, et que le §3.4 du découpage lui demandait explicitement :
la dette est sur le corps.** Les propulseurs volent à leur Isp de vide réelle et ne compensent rien
;
le Vulcain abandonne 71 s de son bracket, et c'est là que `PHY-2` devra reprendre l'entrée.

**Refusé : ancrer sur la capacité absolue.** Elle est déjà fausse d'un facteur 1,94 sur l'entrée
existante (§2.4), aucun code ne la lit, et la corriger n'est pas ce lot.

### 3.2 Les P120C sont déclarés `SOLID`

**Décision : `PropellantType.SOLID` et `ShutdownMode.BURN_TO_DEPLETION`.** C'est vrai du véhicule,
et
c'est ce qui rend `variableLoad()` enfin `false` sur un étage qui l'est vraiment — la seule chose
que
le découpage demandait au masque λ, obtenue sans le toucher (§2.8).

Le corps reste `CRYOGENIC` : il est mission-dimensionnable, et l'agrégat A62 le déclarait ainsi pour
cette raison.

### 3.3 La capacité manquante vit dans le transfert, pas dans un étage neuf

**Décision : `AnalyticHohmannTransferStage` gagne une branche sur le chemin où il lève
aujourd'hui.**
Quand aucun apogée n'est atteignable, il insère d'abord — une combustion depuis l'état remis — puis
replanifie le Hohmann ordinaire depuis l'orbite obtenue, qui en a un par construction.

**Ce que cette décision remplace, et pourquoi elle a changé.** Ce document a d'abord retenu un
`MissionStage` neuf, additif, présent dans toutes les chaînes et inerte quand il n'avait rien à
faire. Deux mesures l'ont démonté, dans cet ordre :

1. **Le critère d'inertie était faux.** Il testait « un apogée existe-t-il sur l'arc courant ? »,
par
   `AnalyticTrimBurnStage.detectStateAtApogee`. Ce n'est pas la précondition du transfert, qui
   cherche un apogée sur l'orbite **d'après sa propre première combustion**. Résultat mesuré :
   l'étage tirait sur le Falcon Heavy LEO-400, dont la remise des commandes est à 77 km, descendante
   — profondément sub-orbitale, et que le transfert traite sans peine depuis toujours en brûlant
   638 m/s avant de chercher son apogée.
2. **Et il coûtait cher.** Même profil, volé deux fois, une fois avec l'étage et une fois sans :
   **25 744 kg contre 37 196 kg** de masse finale pour la **même** orbite, `400,1 × 419,4 km`.
   **11 452 kg, soit 31 %.** L'explication est dans les deux plans de transfert : `dv1` passe de
   199 à 638 m/s. La remise des commandes du Falcon est basse mais son **apogée est déjà à 420 km**,
   et circulariser à 77 km jette cet apogée que l'ascension avait payé.

Le transfert est donc **intelligent sur l'apogée qu'il trouve**, et une insertion systématique le
rend aveugle. La capacité doit vivre là où le manque est — sur le chemin de la levée, qu'aucun
profil qui fonctionne n'atteint jamais. Conséquences mesurées : **aucune frontière nouvelle dans
aucune liste épinglée**, `MissionPolylineBaselineTest` et `PolarCoverageTest` verts sans être
touchés.

**Le prix, nommé.** Une levée devient un comportement. Aujourd'hui une chaîne qui remet la main dans
un état impossible **lève**, et c'est ce qui a fait consigner `BUG-6` plutôt que laisser un profil
mentir. La branche journalise donc ce qu'elle fait, à chaque fois.

### 3.4 Ce que l'insertion vise, et comment elle le résout

**Décision : elle remonte le périgée jusqu'au périgée de la mission**, ou jusqu'au rayon courant
quand celui-ci est plus bas — une combustion ne peut pas relever un périgée au-dessus du rayon où
elle a lieu. L'apogée reste au transfert, qui est fait pour ça.

Trois choses ont dû être corrigées en implémentant, chacune trouvée par la mesure, et elles valent
d'être écrites parce qu'elles sont contre-intuitives :

- **La direction n'est pas prograde.** Une remise des commandes après l'apogée est descendante, et
  la poussée prograde y relève l'apsis **d'en face**. Mesuré : un réservoir entier dépensé prograde
  a emmené un arc `−200 × 568 km` à `472 × 5 515 km` — cinq mille kilomètres d'apogée gagnés pendant
  que le périgée montait par effet de bord. La direction est `v_circulaire − v`, la construction que
  la seconde combustion de ce même étage et le trim utilisent déjà.
- **Le périgée atteint n'est pas monotone en durée de combustion.** Il monte, atteint un maximum
près
  de la circularisation impulsionnelle, puis **redescend** : une combustion de sept minutes est
  volée
  dans une direction figée et se remet à excentrer l'orbite. Une bissection y converge sur la
  mauvaise branche. C'est donc un balayage puis un raffinement sur la branche montante.
- **Et le dimensionnement impulsionnel ne suffit pas** — la règle que `MIS-4 / L6` a payée quatre
  fois. La boucle évalue ses candidats **en les volant**.

### 3.5 L'Ariane 62 disparaît, sans migration

**Décision : remplacement sec.** `ARIANE_62` sort du catalogue, `ARIANE_64` la remplace, et **aucun
filet n'est posé** pour les scénarios déjà enregistrés : un fichier qui référence l'ancien id lèvera
au chargement (§2.7).

C'est la lecture littérale du découpage, et c'est un choix explicite plutôt qu'un oubli. La
fonctionnalité de scénarios est récente, le nombre de fichiers concernés sur les disques est
supposé nul ou négligeable, et une table de correspondance d'ids périmés est une dette qui ne se
referme jamais.

**Ce que la calibration rendait possible et qui n'est pas retenu.** Le Javadoc de l'entrée A62
justifie l'absence d'A64 par le fait que *« le modèle sépare A64 et A62 de 3 % là où la réalité les
sépare d'un facteur 2,5 »*. Le §2.4 supprime cette raison — le rapport tombe à 2,09 contre 2,10 —
donc garder les deux lanceurs serait devenu défendable. Écarté : cela doublerait le catalogue à
éclater et les cellules à re-baseliner, dans un lot qui a déjà grossi d'un étage de mission.

### 3.6 Le plafond du virage est relevé, et il fallait le faire

**Décision : `transitionTimeMax` prend un plancher à `plancher d'étagement + 0,7 × capacité de
combustion de l'étage supérieur`.**

Ce document a d'abord laissé la question ouverte, en écrivant que le plafond n'avait peut-être plus
rien à changer. **C'était faux, et la mesure le dit sans appel** : sur l'Ariane 64 calibrée
l'optimiseur saturait à **100 % de sa boîte**, rendait `transitionTime = 520,0` pour un plancher
d'étagement à 479,8 — donc **41 s** de Vinci — et remettait la main à **−4 788 m d'altitude**, sous
terre, pour un coût de **3 262,9** contre 0,048 acceptable. Relevé, le même profil descend à
**0,088**.

Le supplément est une **fraction de la combustion propre à l'étage supérieur**, pas une constante :
toute constante assez grande pour l'Ariane relève aussi la boîte du Falcon Heavy, et déplacer une
borne renormalise une recherche qui ne demandait rien. `0,7` est à l'intérieur du bracket mesuré
`[0,07 ; 0,89]` — en dessous l'Ariane reste plafonnée, au-dessus le profil GEO du Falcon commence à
bouger.

### 3.7 L'ascension paie ce qu'elle a déjà perdu sous son apogée

**Décision : un terme de coût sur `apogée atteint − altitude à la remise`, actif quand le véhicule
descend, de poids 1,0.**

Il existe parce que l'Ariane 64 rendait la main **138 km sous son propre apogée**, et qu'aucune
combustion en aval ne rattrape cela à bon compte. Le sens de la pente ne sépare rien : **tous** les
profils du dépôt rendent la main juste après leur apogée — un Falcon Heavy LEO-400 de 0,15 km, une
Ariane 62 de 0,37 — et l'interdire déplacerait toutes les missions. C'est la **profondeur** qui
sépare, d'un facteur 350.

Une pénalité de coût et non une borne : c'est la doctrine que ce dépôt s'est écrite, l'invariant
d'étagement étant déjà porté ainsi.

**Le poids est une calibration, et il a dû être mesuré deux fois.** À 8,0 — le poids d'un apogée
trop bas — la recherche achetait « pas de creux » en **dépassant l'apogée**, que
`W_APOGEE_OVERSHOOT` ne facture que 0,5 : elle rendait la main à 505 km sur un apogée de 568 pour
une cible à 400. À 1,0, le terme vaut `1,7·10⁻⁸` sur le creux d'un Falcon — invisible, quatre ordres
sous le coût acceptable — et `0,119` à 138 km, soit deux fois et demie l'acceptable, donc décisif
sans écraser la fenêtre d'apogée.

### 3.8 Le transfert apprend à descendre

**Décision : quand l'orbite dépasse déjà sa cible, `AnalyticHohmannTransferStage` coaste jusqu'au
**périgée** et y brûle rétrograde pour abaisser l'apogée.** C'est le miroir exact de ce qu'il fait
déjà à l'apogée, et il se loge dans le plan existant sans le déformer : `dt1 = 0`, un coast, la
combustion en seconde position.

Il le faut parce que **l'insertion ne peut pas retirer les 67 km qui restaient**, et la raison est
géométrique : une combustion produit une orbite **qui passe par le point où elle a lieu**, et
467 km n'est pas sur un cercle à 400. La meilleure orbite atteignable en une combustion avec un
périgée à 400 est exactement le `400 × 467` sur lequel le véhicule se trouve déjà. Descendre demande
une seconde combustion, au périgée.

Mesuré sur l'Ariane 64 : `467,0 → 400,0 km` pour **23,8 m/s** de rétrograde, en 4,2 s, à un périgée
atteint 2 308 s plus tard. Le trim finit à 5,9 m/s.

**Refusé : renforcer le terme de dépassement d'apogée pour que l'ascension ne dépasse pas.** Son
asymétrie — 8,0 pour un apogée trop bas, 0,5 pour un trop haut — est délibérée et documentée par une
mesure ancienne où un plafond trop cher poussait l'optimiseur à cabrer et à insérer 148 km sous la
cible. Y toucher rouvrirait cet arbitrage sur tous les lanceurs pour corriger un symptôme ; la
capacité manquante, elle, sert tout profil qui dépasse sa cible.
## 4. Ce que le lot touche

**`src/main`, six fichiers.**

| Fichier | Ce qui change |
|---|---|
| `vehicle/catalog/Launchers` | `ARIANE_64` éclatée et calibrée remplace `ARIANE_62` (§3.1, §3.2) |
| `stage/AnalyticHohmannTransferStage` | l'insertion (§3.3, §3.4) et la descente (§3.8) |
| `stage/AnalyticGtoInjectionStage` | lève lui-même là où le helper partagé rendait la main |
| `optimizer/problems/GravityTurnProblem` | le terme de creux (§3.7) et le plancher du plafond (§3.6) |
| `maneuver/GravityTurnManeuver` | expose la capacité de combustion de l'étage supérieur (§3.6) |
| `engine/scene/spacecraft/LauncherAssets` | le Javadoc cesse de décrire un défaut corrigé (§2.1) |

**`src/test`, seize fichiers**, dont `Ariane62MissionTest` renommé et les quatre épinglages.

**`docs/dette-technique.md`** : `DT-12` passe à « fermé », constaté et non fait par ce lot.

---

## 5. Ce qui ferme le lot

### 5.1 Les contrôles physiques du découpage, volés

```
[A64] boosters dry at T+130.01 s, core at T+479.99 s
[A64] Stage 0: 2.9e-11 kg left of 564 000 kg   Stage 1: 0.0 kg of 152 000 kg
      Stage 2: 5 641 kg left of 25 202 kg loaded (22.4 %)
```

Les trois chiffres que le découpage demande : **extinction des propulseurs à 130,0 s**, **corps à
480,0 s** — soit 8,0 minutes — et un **rapport de débits de 13,70**. Le reliquat de 22,4 % sur
l'étage supérieur est à comparer aux 21,7 % que l'Ariane 62 laissait : la marge de la mission n'a
pas changé de nature.

**Et la chaîne va au bout.** L'ascension remet la main sur `−200,1 × 544,5 km`, l'insertion la porte
à `399,7 × 467,0 km` pour 53,8 s de Vinci sur 199 disponibles, le transfert descendant ramène
l'apogée de `467,0` à `400,0 km` pour 23,8 m/s, le trim finit à 5,9 m/s. C'est la condition de
clôture que le §2.5 rendait impossible avant les deux manœuvres.

### 5.2 Le témoin : ce qui n'a pas bougé

| | |
|---|---|
| `EarthOrbitNonRegressionTest` | 4/4, **sans être touché** |
| `MissionPolylineBaselineTest` | 1/1, **sans être touché** |
| `CentralBodyBaselineTest` LEO, GEO, polaire | verts, **sans être touchés** |
| `PolarCoverageTest` | vert, **sans être touché** |

C'est la propriété que le §3.3 achetait en refusant l'étage additif : la branche vit sur le chemin
de la levée, donc **aucune frontière nouvelle dans aucune liste épinglée**, et les quatre fixtures à
variables fixes ne bougent pas d'un chiffre.

### 5.3 Ce qui a bougé, et de combien

| | Pourquoi | Ampleur |
|---|---|---|
| `CentralBodyBaselineTest.meo` | c'est le profil Ariane, devenu une A64 | 11 → 13 frontières, et des variables neuves (§7.9) |
| `AscentBaselineN2Test` ×2 | le paysage a gagné le terme de creux | **0,20 s** et **0,17 s** de temps de transition |

Les deux décalages du Falcon sont à l'intérieur de `TRANSITION_TIME_TOLERANCE_S`, et les écarts de
masse qu'ils entraînent — 58,8 et 48,7 kg — sont le décalage multiplié par les 287,46 kg/s de
l'étage supérieur, **au gramme près**. C'est le cas que le Javadoc de cette fixture désigne comme
appelant un ré-enregistrement plutôt qu'une enquête.

### 5.4 La suite

`gateTest` vert. Suite rapide **1 407 tests, 30 sautés, 0 échec, 0 erreur** sur 205 classes.
`pmdMain`, `pmdTest` et `spotlessCheck` passent.

Reste, hors de la boucle courte : les six cellules de `L0` à relancer et à differ contre
`build/baseline/phy8/`, dont trois changent de lanceur.

---

## 6. Les risques que la conception ne lève pas

**La charge utile de référence.** Mesuré : à 5 t, l'A64 est si surdimensionnée que l'optimiseur
refuse d'allumer l'étage supérieur et rend un coût de 14,64 contre un acceptable de 0,0476. À 20 t
le même profil descend à 0,0529. **Les fixtures d'Ariane devront changer de charge utile en même
temps que de lanceur**, sans quoi elles mesureront une fusée hors de son domaine.

**Le périgée obtenu par la brique est bas.** Les orbites du §2.6 ont des périgées de 1,8 à 106 km :
légales dans un modèle sans traînée, insoutenables avec. `PHY-2` les fera tomber, et c'est une
raison
de plus pour que l'étage vise sur le vol.

**Le second re-baseline est plus large que le premier.** `L3` déplaçait 42 frontières sur 64 ; `L4`
déplace les profils Ariane *et* ajoute une frontière à tous les autres.

---

## 7. Ce que la conception corrige au découpage

1. **`DT-12` est déjà fermé** et la dépendance est inversée : l'écran dessine une A64 depuis
   `AST-1`, c'est le catalogue qui rattrape (§2.1).
2. **Le masque du balayage λ n'a rien à revoir** : les deux masques excluent déjà `SOLID` par
   construction (§2.8).
3. **Le découpage ne prévoit aucune manœuvre dans `PHY-8`**, et `L4` ne peut pas exister sans en
   livrer une (§1.1).
4. **La capacité absolue du catalogue est fausse d'un facteur 1,94 sur l'entrée existante** ; seul
   le rapport entre lanceurs est ancrable, et il tombe juste à 2,09 (§2.4).
5. **Le §3.4 du découpage est confirmé et chiffré** : proxy honnête sur les propulseurs, 71 s de
   dette sur le corps.

**Ce que l'implémentation a corrigé dans ce document même.** Ce lot est le premier du chantier où la
conception a dû être réécrite en cours d'implémentation, trois fois, et chaque fois parce qu'une
mesure a démenti une décision. C'est écrit ici plutôt que lissé, parce que la trace vaut plus que
l'apparence d'un plan tenu.

6. **La brique devait être un étage neuf ; c'est une branche du transfert** (§3.3). Le critère
   d'inertie que ce document avait retenu testait le mauvais prédicat, et l'étage tirait sur le
   Falcon Heavy LEO-400 — dont la chaîne fonctionnait — pour **11 452 kg, 31 % de la masse finale**.
7. **Le plafond du virage devait n'avoir rien à changer ; il était contraignant** (§3.6). Le §3.6
   d'origine s'appuyait sur une mesure faite avec les Isp provisoires ; avec le catalogue calibré,
   l'optimiseur saturait à 100 % de sa boîte et remettait la main sous terre.
8. **La cible de l'insertion était fausse dans sa direction, sa méthode et sa valeur** (§3.4). Elle
   brûlait prograde là où il fallait tuer une descente ; elle bissectait une réponse **non
   monotone**
   ; et elle visait une circularisation là où le périgée de la mission suffisait.
9. **Le profil MEO de `CentralBodyBaselineTest` a dû changer de variables**, et ce n'était pas un
   choix : son littéral gelé `378,663107` tombe **sous** le plancher d'étagement de l'A64 (478,98
   s),
   et l'ascension le refuse. Remplacé par l'optimum que `MeoMissionTest` retient pour ce profil,
   `498,913955 / 0,293836`, qui converge 500 fois sous le coût acceptable à 63 % de sa boîte. C'est
   la deuxième fois du chantier — `L3` l'avait fait pour le GEO, pour la même raison.
10. **`IspProxyDebtTest` mesurait la mauvaise chose sur un bloc éclaté.** Il passait un couple
    proxy/vide d'agrégat ; le bloc en a désormais deux, très différents. Il calcule maintenant les
    Isp effectives `ΣF/Σ(F/Isp)` des étages allumés au sol, et le résultat est un chiffre à donner à
    `PHY-2` : **la dette de l'Ariane s'effondre de 671 à 64 m/s**. Les 671 étaient, pour
    l'essentiel,
    l'artefact du mélange d'un solide honnête et d'un cryogénique endetté dans un seul proxy de
    300 s. Celle du Falcon Heavy ne bouge pas : 408 m/s.
11. **La charge utile des fixtures Ariane s'est mesurée, pas raisonnée.** À 5 t le lanceur est si
    surdimensionné que l'optimiseur cesse d'allumer l'étage supérieur (coût 300 fois l'acceptable) ;
    à 40 t — la capacité en ΔV idéal que le §2.4 calcule — il ne fait pas orbite et remet la main
    sur
    le plancher de la garde de rentrée. **Le chiffre idéal ignore toutes les pertes que le vol
    paie**,
    donc la capacité volée est bien en dessous, et 20 t est le point où ce profil se comporte.

---

## 8. Ce que `L4` lègue

**À `PHY-2`** : une entrée de catalogue où la dette d'Isp est **localisée** — 0 s sur les
propulseurs, 71 s sur le corps — au lieu d'être diluée dans un agrégat, et **dix fois plus petite
qu'annoncée** : 64 m/s contre les 671 que `DT-13` porte encore pour l'Ariane (§7.10). C'est plus que
ce que le §3.4 du découpage espérait de l'éclatement — il prévoyait une dette mieux placée, pas une
dette qui s'évapore.

**Au chantier multi-corps** : le profil polaire de `CentralBodyBaselineTest`, gelé depuis `BUG-6`
faute d'un étage sachant planifier depuis un arc sub-orbital, redevient volable. `L4` ne le
dégèle pas — ce serait re-mesurer une référence pour rien — mais il retire l'obstacle.

**À `PHY-5`** : quatre propulseurs largués ensemble, avec leur section et leur masse déclarées par
exemplaire, et un maillage `-after_boosters` qui attend déjà dans les assets.

**À `L5`** : un catalogue où les deux lanceurs sont éclatés de la même façon, donc une seule règle
de
hauteur à écrire.
