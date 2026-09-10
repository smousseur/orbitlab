# PHY-8 — Clôture

Ce document ferme le chantier. Il couvre les trois lots qui n'ont pas de document
à eux — `L5`, `L6` et la retombée de `L3` — et il énonce ce que le chantier laisse
derrière lui : les réserves, les limites, et les voies d'amélioration mesurées.

Il ne réécrit pas ce que `01-decoupage.md` à `06-conception-L4.md` disent déjà.

---

## 1. Ce que le chantier rend vrai

Le découpage promettait qu'*« un lanceur dont les propulseurs se séparent du corps
central le fasse dans le modèle »*. C'est le cas, et pour les deux lanceurs :

- le **Falcon Heavy** vole ses trois corps en deux entrées de catalogue, son corps
  étranglé à `f = 0,81` survit **29,8 s** aux propulseurs, et le largage est scindé ;
- l'**Ariane 64** a remplacé l'Ariane 62 : propulseurs secs à **130,0 s**, Vulcain à
  **480,0 s**, rapport de débits **13,70** ;
- le catalogue porte des **hauteurs** (70 m et 63 m) et le rendu les convertit, donc
  chaque pièce sort à sa taille ;
- une charge utile déclare sa **propulsion** générique et son budget ΔV, et le cargo
  a quitté les listes du wizard.

**Vérification finale** : `gateTest` vert, suite rapide **1 422 tests, 30 sautés,
0 échec** sur 206 classes, `pmdMain` / `pmdTest` / `spotlessCheck` verts, et les
**six cellules** de `BoosterSplitBaselineTest` insèrent.

---

## 2. `L5` — Les dimensions

Livré sans document de conception, à la demande. Ce qu'il faut en retenir.

**Le catalogue porte une hauteur, le rendu la convertit.** `LauncherModel.heightMeters`
— Falcon Heavy **70 m**, Ariane 64 **63 m** — et `MissionRenderer` passe la moitié là
où il écrivait `50,0`. Les deux maillages étant normalisés à **1,0000 unité** en Y,
base à l'origine, un seul nombre par lanceur suffit à sortir chaque pièce détachée à
sa fraction juste.

**Les deux hauteurs sont contrôlées, pas assumées** : la coiffe du Falcon fait 0,1878
de la pile, soit **13,1 m** à 70 m — sa longueur publiée à la décimale ; celle de
l'Ariane 0,3264, soit **20,6 m** à 63 m, ce qui identifie au passage la configuration
à coiffe longue.

**Le seuil LOD par objet n'a coûté aucune ligne** : `LodView` projette ce même rayon,
donc il suit la hauteur par construction. Le découpage le comptait comme un livrable ;
c'en est une conséquence.

**Le cadrage au clic est passé de 500 à 350 m**, soit cinq fois la hauteur du plus
grand lanceur — ce que 500 m étaient quand tout objet était dessiné à 100 m.

**Ce que `L5` a trouvé et n'a pas pu réparer** : les propulseurs de l'Ariane 64 sont
trop gros dans le maillage. Contrôle interne au dépôt, sans source externe : les
sections du catalogue donnent un rapport de diamètres propulseur/corps de **0,630**,
le maillage en donne **0,863** — 37 % de trop. Le même contrôle passe sur le Falcon
(0,995 contre 1,000). Aucune hauteur ne réconcilie les deux : atteindre les 13,5 m
d'un P120C demanderait une pile de 37 m. C'est [`DT-18`](../dette-technique.md#dt-18),
**dû avant `PHY-5`**, et `LauncherMeshProportionTest` épingle **l'écart** et non
l'accord — remplacer le maillage rend le test rouge, ce qui force la mise à jour.

`DT-12` (« mesh Ariane 6 absent ») ferme ici, son second volet compris : la convention
de maillage — nez sur `+Y`, échelle « ~1 unité » — n'était que supposée, elle est
désormais mesurée.

---

## 3. `L6` — Le catalogue des charges utiles

**Le renommage `akm*` est complet** : **90 identifiants → 0**, et la prose passée de
37 mentions d'`AKM` dans `src/main` à **9**, toutes sur une vraie combustion d'apogée.
Le découpage annonçait *« un diff mécanique qui se relit ligne à ligne sans rien
apprendre »* ; c'était vrai des identifiants et faux de la prose, où 37 sites
demandaient un arbitrage.

**Deux prédicats homonymes ont été démêlés plutôt que fondus.** `PayloadModel.hasAkm()`
demandait *« ce modèle peut-il être propulsé ? »*, `Spacecraft.hasApogeeKickMotor()`
*« cet exemplaire a-t-il des ergols à bord ? »*. Le second n'avait qu'un appelant, que
`L6` remplace ; il est supprimé, et il ne reste qu'un nom pour une question.

**Le cargo sort par un booléen de finalité**, `requiresRendezvous`, vrai sur lui seul.

**L'`EARTH_OBS_SAT` reçoit quatre nombres argumentés** : budget **15 m/s** (2,5 × le
pire trim LEO que `L0` a mesuré), capacité **100 kg** (couvre 15 m/s jusqu'à 13 032 kg
à sec), **Isp 220 s** — un monoergol, la façon la plus courte de dire que ce n'est pas
un moteur d'apogée — et **400 N**, qui garde le trim à 2,75 % d'une révolution.

**Le lot a démenti son propre critère de clôture.** Le découpage écrivait *« aucune
trajectoire ne bouge »*. La propulsion reste bien inerte — une chaîne LEO ne largue
jamais son étage supérieur, donc le trim est encore sa combustion — mais **les ergols
ne sont pas en apesanteur** : 76,745 kg de plus sur la charge utile, et l'étage
supérieur grossit de **0,81 %**. Aucune fixture ne l'attrape ; toute mission LEO
construite par le wizard bouge.

**Et il aurait transformé un refus en acceptation fausse.** Le réservoir rempli rend
`propellantLoad > 0` vrai, donc un satellite d'observation aurait composé une MEO à
20 200 km sur un réservoir de maintien à poste — 1 700 m/s demandés à 21. Les deux
portes, `MissionComposer` et `MissionFactory`, comparent désormais un **ΔV** au lieu
de tester une présence.

---

## 4. La retombée de `L3`

`BoosterSplitBaselineTest` n'avait pas tourné depuis `L0`. Au premier passage, **trois
des six cellules levaient**, toutes Falcon Heavy, toujours avec trop d'énergie à la
remise des commandes. `L3` §6 avait nommé ce risque : *« si une insertion sort de son
enveloppe, c'est un résultat du lot et non un test à faire taire »*.

### 4.1 L'attribution, par la mesure

Le même worktree vole la cellule LEO **verte à `f = 1`** et reproduit `L0` au chiffre —
MECO `t+158,981660 s`, `15 963,097 kg`, `400 115 × 419 337 m`, ΔV total `8 492,128`.
Donc `L2`, `L4` et `L5` n'ont rien changé sur cette cellule, et l'étranglement a tout
changé. `L6` est hors de cause : la cellule échouait à l'identique sur `HEAD`.

**Ce que l'étranglement achète est réel** — 44 t larguées à 157 s au lieu de 66 — et
cette cellule n'en avait aucun usage : à `f = 1` elle insère avec son étage supérieur
**jamais allumé** et 48 kg de reliquat.

### 4.2 Ce qui a été essayé et écarté, avec le chiffre

| Piste | Verdict mesuré |
|---|---|
| Retoucher `f` | La phase corps-seul dure `157·(1 − f)` s. Le bracket sourcé `[0,78 ; 0,83]` ne descend jamais sous 26,7 s ; seul `f → 1` la supprime |
| Rendre le corps commandable | **Aucun effet** : coût `73,76361806179528` contre `73,7636180617952`, même optimum. Libre de couper, l'optimiseur refuse |
| Vider le corps | Ne ferme qu'à `coreLeft = 0`, c'est-à-dire `f = 1`. **6,5 s** de phase corps-seul suffisent à mettre le périgée à −76 km |
| La loi de tangage | 88 points balayés, aucune remise que les 448 m/s de l'étage supérieur puissent fermer. La loi s'étire déjà sur le MECO : `alpha = (dt/transitionTime)^exposant` |

### 4.3 Ce qui ferme

**Le budget sous-dimensionnait l'étage supérieur d'un facteur quatre.** 1 963 kg à
bord, **448 m/s**, contre les 436 que le transfert et le trim dépensent — douze mètres
par seconde de marge, et seulement parce que la trajectoire tombait juste. Le seuil
mesuré est net : à 5 000 kg le transfert dépense `1 042,2 m/s`, tout le réservoir, et
échoue ; à 8 000 il en demande 1 302 sur 1 543 et l'orbite tombe à **53 m** de la
référence.

`PropellantBudget` réserve donc **1 300 m/s** d'insertion sur l'étage supérieur,
**additive et non plancher** — un `max()` clampe tout profil sous le plancher sur un
même nombre, et les dimensionnements polaire et plein-est sortaient identiques,
détruisant ce que `MIS-7` a construit.

**Et `AnalyticParkingInsertionStage` sait descendre**, les **deux poussées portées à
une apside**. Nier la première au point d'entrée a été essayé : le périgée part sous
terre et l'intégrateur abandonne, l'entrée étant à **7,39°** de pente là où l'étage
suppose 0 — une Ariane 64 remet les commandes à `vRad = 0,2 m/s` dans le même run, le
Falcon étranglé à **1 085,6**.

---

## 5. Les réserves

**Le 1 300 m/s est le pire cas, payé par tous.** Un profil qui remet bien les commandes
dépense 430 au transfert et 6 au trim. La réserve étant universelle, toutes les missions
budgétées la portent : +18 à +66 % de charge d'étage supérieur. C'est
[`DT-19`](../dette-technique.md#dt-19).

**L'ascension n'a aucune prise sur son propre corps central.** `transitionTime` ne
pilote que la seconde combustion ; `burn1Duration` et `coreBurnDuration` sortent des
ergols restants. Un étage déclaré `COMMANDED` que le code brûle toujours jusqu'à
déplétion, et une barrière de coût de `1e3` qui interdit exactement la région où sont
les bonnes remises. C'est [`DT-20`](../dette-technique.md#dt-20), et c'est la
découverte structurelle du chantier.

**`W_APOGEE_OVERSHOOT = 0,5` est calibré hors de son domaine.** Son Javadoc justifie le
poids sur des remises **distantes de 91 km** en apogée, *« absorbed by the trim burn at
the next apside for nothing measurable »*. À 4 596 km d'apogée on est cinquante fois
hors de cette mesure. C'est [`DT-21`](../dette-technique.md#dt-21).

**L'étage de parking s'est élargi au-delà du besoin.** Il ne demande qu'une apoapside
au-dessus de la cible, donc il vole aussi une descente pure — un cercle de 600 km
ramené à 100. Il est partagé par les chaînes GEO et les deux lunaires. C'est épinglé et
nommé comme tel dans `AnalyticParkingInsertionStageTest`, pas découvert plus tard.

**Le refus qui restait joignable a changé de nature.** Quand `dv1Raw < 0` la branche
descendante est prise avant que `dv2Raw` ne soit évalué ; la seule garde encore
atteignable est celle de capacité, un plan que les ergols ne peuvent pas voler.

---

## 6. Les limites

**Les orbites lunaires ne sont validées contre rien d'externe.** Le survol atteint
`380 361 × 433 439 124 m` à `i = 32,47°` là où `L0` donnait `517 083 × 431 560 200` et
`31,83°` : c'est une autre trajectoire, pas une meilleure. L'orbite lunaire, elle,
retombe à `99 392 × 99 657 m` sélénocentriques contre `99 103 × 99 366` — cohérent.

**La cellule LEO de l'Ariane est la moins bonne des quatre terrestres** :
`390 106 × 419 691 m`, soit 9,9 km sous la cible au périgée. Dans la barre des ±7 %
que le dépôt s'est donnée, mais c'est le pire des quatre.

**`f = 0,81` reste une inférence.** Il vient de la chronologie du vol inaugural —
séparation à T+2:33, MECO du corps à T+3:04 — qui ne le contraint pas mieux que
`[0,78 ; 0,83]`. Et aucune valeur du bracket ne supprime la phase corps-seul.

**Trois cellules budgétées sur six ont changé de lanceur** en cours de chantier, donc
`L0` ne leur sert de référence que sur les deux Falcon LEO et lunaires.

**La dette d'Isp que `PHY-2` hérite a changé deux fois** : Falcon Heavy **408 → 396 m/s**
(la réserve alourdit l'étage supérieur, donc le rapport de masses du premier étage),
Ariane **671 → 64 m/s** (l'éclatement dissout un mélange solide/cryogénique). Les
chiffres de `J2` sont à relire avant arbitrage.

---

## 7. Les voies d'amélioration, par ordre de valeur mesurée

1. **Une réserve par mission plutôt qu'universelle.** Le nombre juste est le ΔV que
   l'insertion de *cette* mission demandera, et le dimensionnement ne connaît pas l'état
   de remise des commandes. Un dimensionnement en deux passes — dimensionner, voler,
   redimensionner — le donnerait exactement. C'est ce qui supprime `DT-19`.

2. **Rendre le corps commandable *et* lever la barrière d'étagement.** Les deux
   ensemble, jamais l'une sans l'autre : le corps seul ne change rien, la barrière seule
   n'a pas de sens. Mesuré : la bonne région existe — `transitionTime = 170`,
   exposant `0,634` rend un apogée de **407,9 km**, la cible — et la barrière de `1e3`
   l'interdit. Levée avec le corps commandable, la cellule cesse de lever mais insère à
   `72 551 × 400 178 m` : il manque encore un rééquilibrage de la fonction de coût.

3. **Laisser le budget dimensionner le corps central**, et pas seulement l'étage du
   haut. `L0` avait mesuré l'anomalie sans en tirer la conséquence : un Falcon Heavy ne
   vole pas 10 t en orbite basse réservoirs pleins.

4. **Partager la détection d'apside entre l'insertion en parking et le transfert.** Les
   deux étages résolvent maintenant le même problème avec deux implémentations. `L4` a
   écrit la première, la retombée de `L3` la seconde.

5. **Ré-exporter le maillage de l'Ariane 64** — `DT-18`, dû avant `PHY-5`.

---

## 8. Ce qui ferme, et ce qui reste ouvert

| | |
|---|---|
| `PHY-8` | **fermé** |
| `DT-12` — mesh Ariane 6 absent | **fermé** par `L5` |
| `DT-18` — propulseurs A64 surdimensionnés | ouvert, **dû avant `PHY-5`** |
| `DT-19` — réserve d'insertion universelle | ouvert |
| `DT-20` — l'ascension sans prise sur son corps | ouvert |
| `DT-21` — `W_APOGEE_OVERSHOOT` hors domaine | ouvert |
| `DT-13` — dette d'Isp | ouvert, **chiffres révisés** : 396 et 64 m/s |
| `J2` | inchangé dans son principe, à relire sur les nouveaux chiffres |
