# PHY-8 — Propulseurs séparés du corps central — découpage

Item roadmap : `PHY-8` (★4 ◆4 L), v2, à voler avant `J2` et `PHY-2`. Ce document ne
conçoit pas en détail : il **découpe**. Chaque lot y est défini par la propriété qu'il
rend vraie et par ce qui la prouve.

Le raisonnement qui a fait de `PHY-8` un item à part entière est dans
[`v2-preparation/00-preparation.md`](../v2-preparation/00-preparation.md) §2, et la fiche
est dans [`roadmap/02-roadmap-v2.md`](../roadmap/02-roadmap-v2.md). Le §2.2 ci-dessous
corrige cette fiche sur cinq points, dont un qui touche à ce que l'item promet de livrer.

---

## 1. Périmètre

**Dans `PHY-8`** — ce que les sept lots du §5 rendent vrai :

- la combustion parallèle des propulseurs et du corps central, sur les deux lanceurs ;
- un étage de propulseurs à multiplicité — 2 pour le Falcon Heavy, 4 pour l'Ariane 64 —
  et son largage comme séparation de pile ordinaire ;
- une fraction de poussée pour le corps pendant la phase partagée ;
- l'Ariane 64 en **remplacement** de l'Ariane 62 au catalogue, chaque étage portant ses
  propres Isp, poussée et section ;
- les hauteurs, par lanceur et par charge utile, et la mort de
  `SPACECRAFT_RADIUS_METERS` ;
- le catalogue des charges utiles : le cargo filtré hors des listes, un moteur pour le
  satellite d'observation, et le budget d'ergols qui va avec ;
- le re-baselining des épinglages que tout cela déplace.

**Hors `PHY-8`**, et à ne pas y laisser glisser :

1. **La séparation S2 en LEO et le transfert des combustions à la charge utile.** C'est
   la définition de `PHY-6`. La frontière retenue est *catalogue ici, chaîne là-bas* : le
   satellite reçoit ici son moteur et ses ergols, `PHY-6` lui donne les combustions à
   tirer. Conséquence assumée : la propulsion livrée par `L6` **ne sert à rien** jusqu'à
   `PHY-6`.
2. **La traînée.** C'est `PHY-2`, et l'ordre est délibéré : découper d'abord laisse
   `PHY-2` calibrer une seule fois, contre une ascension de forme physique.
3. **La dette `DT-13`.** Elle traverse l'item sans être entamée — voir §3.4, qui est le
   point où la fiche promet le contraire.
4. **La machinerie multi-objets et les débris dessinés** (`PHY-5`). `PHY-8` lui livre la
   section et la taille d'un propulseur largué ; il ne dessine rien.
5. **L'étranglement variable dans le temps.** Le vrai Falcon Heavy ré-accélère son corps
   central après le largage ; ici la fraction est constante pendant la phase partagée et
   pleine après.
6. **Une variable d'optimisation de plus.** L'étranglement est une donnée de catalogue,
   pas une dimension CMA-ES.

---

## 2. État des lieux

### 2.1 Ce que le code fait aujourd'hui

| Brique | Fichier | Mesure |
|---|---|---|
| Pile et étage actif | `mission/vehicle/VehicleStack.java` | 125 l., `resolveActiveStage` rend **un** étage par seuils de masse cumulée |
| Étage actif résolu | `mission/vehicle/ActiveStageInfo.java` | 84 l., porte propulsion **et** aérodynamique de l'étage attaché |
| Modèle d'étage | `model/stage/StageModel.java` | 74 l., record à 6 composants, 19 constructions dans 5 fichiers |
| Modèle de lanceur | `model/LauncherModel.java` | 65 l., `instantiate` assemble `[étage0 … étageN, charge utile]` |
| Profil d'ascension | `model/AscentProfile.java` | 29 l., record à 3 composants, 17 constructions dans 7 fichiers plus `LEGACY` |
| Catalogue lanceurs | `catalog/Launchers.java` | 161 l., **deux** entrées, chacune à **deux** étages |
| Budget d'ergols | `mission/vehicle/PropellantBudget.java` | 544 l., 6 points d'entrée rendant un `double[]` dimensionné sur le nombre d'étages |
| Durée de combustion | `mission/maneuver/GravityTurnManeuver.java:355` | `ergols restants / (poussée / (Isp·g₀))` — seul endroit du dépôt qui dérive une durée |
| Largage | `mission/stage/StageSeparationStage.java` | chute de masse à la référence de la pile au-dessus, refus si l'indice attendu n'est pas actif |
| Déplétion | `mission/detector/MassDepletionDetector.java` | `g = masse − plancher`, plancher = `sec_i + masseAu-dessus_i` |
| Taille dessinée | `states/mission/MissionRenderer.java:40` | `SPACECRAFT_RADIUS_METERS = 50.0` **en dur**, échelle de modèle *et* seuil LOD |

**L'invariant qui structure tout** est écrit dans `VehicleStack` : *« the active stage
changes only by an explicit jettison »*. Un étage s'arrête à son plancher de déplétion,
strictement au-dessus du seuil qui ferait basculer l'étage actif ; brûler ne change donc
jamais l'étage actif, seul un largage le fait. C'est cet invariant que la combustion
parallèle menace, et le §3.1 est la façon de ne pas le casser.

**Trois indices de pile sont écrits en dur** : `AscentSequence:46`
(`FIRST_STAGE_INDEX = 0`), `GEOMission:30` et `LunarOrbitMission:56`
(`UPPER_STAGE_INDEX = 1`).

### 2.2 Cinq points où la fiche du roadmap est fausse ou périmée

1. **« 47 fichiers de `src/main` »** touchant `resolveActiveStage`, `VehicleStack`,
   `.stages()` ou `StageSeparation`. **Mesuré : 43** — 25, 16, 7 et 11 pris séparément.
2. **« `StageRole.BOOSTER` n'apparaît que dans cinq fichiers de test »**. **Mesuré :
   quatre** — `PropellantLoadOptimizerTest`, `LauncherModelTest`, `StageCapabilitiesTest`,
   `StageModelTest`. Le fond reste vrai : le catalogue ne s'en sert jamais.
3. **« 45 des 196 fichiers de test »** référencent `FALCON_HEAVY` ou `ARIANE_62`.
   **Mesuré : 45 sur 197**, le total ayant bougé d'un fichier depuis `AST-1`.
4. **« Les épinglages à tolérance zéro d'`EarthOrbitNonRegressionTest`,
   `MissionPolylineBaselineTest`, `CentralBodyBaselineTest` et `AscentBaselineN2Test` ».**
   Les quatre ne sont pas de même nature, et le coût n'est pas celui qu'annonce la fiche :

   | Épinglage | Nature mesurée | Ce que `PHY-8` lui coûte |
   |---|---|---|
   | `EarthOrbitNonRegressionTest` | compare **deux compositions de la même mission dans le même run**, tolérance 0 | rien : il survit à n'importe quel catalogue |
   | `AscentBaselineN2Test` | valeurs enregistrées + **tolérances mesurées** (MECO ±1 kg, ±10 m, ±0,05 m/s, seed 42), mode capture, écrit `build/baseline/<profil>-n2.txt` à chaque run | re-enregistrement mécanique, l'outil est dans le test |
   | `MissionPolylineBaselineTest` | constantes littérales (`TRANSITION_TIME`, `RAW_POINTS = 9992`, liste de sommets) + un générateur qui imprime les nouvelles | idem |
   | `CentralBodyBaselineTest` | `Boundary` complets à **égalité stricte de `double`**, table littérale sur 1 296 l. | le seul vrai coût, et sa justification meurt (§6) |

5. **« Chaque étage porte son Isp réelle, donc plus rien à dé-double-compter pour
   `PHY-2` ».** Faux, et c'est le point 3 du §1. Voir §3.4.

### 2.3 Les mesures qui décident du découpage

**Le Falcon Heavy se décompose sans aucune source externe.** Son S1 agrège trois corps
identiques : 22,8 MN / 3 = **7,6 MN**, 1 233 t / 3 = **411 t**, 66 t / 3 = **22 t**,
31,6 m² / 3 = **10,5 m²** — la section que son S2 déclare déjà. Le débit vaut
7,6·10⁶ / (296 × 9,80665) = **2 618,2 kg/s**, donc 411 000 / 2 618,2 = **157,0 s**, le
chiffre que la fiche donne pour la combustion de chaque bloc.

**L'Ariane se décompose depuis son propre Javadoc.** Il déclare *« 65 % of this block's
propellant is solid »* sur 434 t, soit **282 t de solide et 152 t de LLPM** — 141 t par
P120C, donc **564 t** pour les quatre d'une A64. Les sections sont écrites de la même
façon : 41,1 m² = π·2,7² (LLPM) + 2 × π·1,7² (P120C), soit **9,08 m² par propulseur et
22,9 m² pour le corps**, et **59,2 m²** au décollage d'une A64.

**Le rapport de débits que la fiche annonce se retrouve.** 564 t sur ~130 s = 4 338 kg/s
côté propulseurs, 152 t sur ~8 min = 317 kg/s côté Vulcain : **13,7**, les deux durées
étant celles que la fiche cite. L'Ariane s'étage donc d'elle-même et n'a besoin d'aucun
étranglement.

**L'étranglement du Falcon n'est pas un paramètre libre.** Ses trois corps étant
identiques, poussée et ergols sont dans le même rapport et les deux blocs se videraient au
même instant. Si le corps brûle à `f` pendant la phase partagée, il vole
`157 + 157·(1 − f)` secondes en tout ; pour les ~187 s réelles (chiffre externe, à
sourcer), **f ≈ 0,81**, ce qui lui laisse 78 t et **30 s** de phase corps-seul.

**Le trim LEO n'est chiffré nulle part.** Le dépôt donne le trim **MEO** à 57,8 m/s
(passe d'optimisation) et 60,6 m/s (passe d'éphéméride), et le plane trim GEO à 4,2 m/s
pour 3,3 kg ([`multi-corps/02-baseline-L0.md`](../multi-corps/02-baseline-L0.md)). Le trim
LEO, lui, est un résidu de forme du transfert de Hohmann analytique — J3+ et pertes de
combustion finie — connu seulement après propagation, avec un seuil de saut à 1 m/s.
Aucune forme fermée ne le donne : c'est `L0` qui le mesure.

**Ce que `AST-1` a livré et qui dispense de sourcer des longueurs par pièce.** Les
maillages portent la hauteur normalisée de chaque pièce — `ariane_64-core` à 0,5718 de la
pile, `-S2` à 0,2143, `-booster1` à 0,3624 ; côté Falcon 0,6873, 0,2133, 0,6531. Le rendu
appliquant une échelle brute unique par lanceur, **un seul nombre par lanceur suffit** pour
que chaque pièce sorte à la bonne taille.

**Le nom `akm` est déjà faux avant qu'on y touche.** `LUNAR_ORBITER` range dans
`akmPropulsion` un moteur d'**insertion lunaire** de 5 500 N, et `StageRole.KICK` se
documente *« Payload-integrated apogee motor (AKM) »*. Empreinte du nom : **70 occurrences
dans 15 fichiers**, dont 8 de `src/main` — jusqu'au Javadoc de `PayloadDomain` et au
composant `GeoLoads.akmLoad`.

**Le cargo, mesuré avant de le filtrer.** `CARGO_MODULE` sert de charge utile neutre dans
**cinq fixtures** — `LunarFlybyFlightTest:367`, `LunarLaunchWindowFlightTest:108` et
`:136`, `PropellantBudgetTest:91`, `MissionFactoryTest:143` — qui l'instancient
directement, sans passer par `forMissionType` : le filtrer ne les touche pas. Il tient
aussi une ligne du tableau des coefficients balistiques de `PHY-2`
([`atmosphere/04-conception-L1.md`](../atmosphere/04-conception-L1.md) l. 393). Et
`PayloadDomain.ANY` n'est pas porté que par lui : c'est le **défaut** de `PayloadModel`
quand aucun domaine n'est déclaré, donc il survit quoi qu'on décide du cargo.

**Le rendez-vous est en v4.** `MIS-6` figure dans
[`roadmap/04-roadmap-v4.md`](../roadmap/04-roadmap-v4.md) et dépend lui-même de `PHY-6`.
Réserver le cargo aux missions de rendez-vous le rend donc éligible à **aucun** type de
mission pendant toute la v2 et toute la v3.

---

## 3. Décisions de conception

### 3.1 Le bloc parallèle est un étage synthétique, calculé

**Décision : la pile expose un `ActiveStageInfo` unique pour la phase partagée**, dont la
poussée est la somme, l'Isp la dérivée `ΣFᵢ / Σ(Fᵢ/Ispᵢ)`, la section la somme, et le
plancher de déplétion la masse à l'extinction des propulseurs. Au largage, le bloc se
dissout et le corps continue avec ses propres chiffres.

**L'agrégation est exacte, pas approchée.** À poussées et Isp constantes, la poussée
totale et le débit total sont constants, donc un seul moteur équivalent `(F, Isp_eff)`
reproduit l'histoire de masse et l'accélération sans perte. Ce qui est faux aujourd'hui
n'est donc pas l'agrégation : c'est qu'elle est **écrite à la main** — 300 s « pondérée » —
et qu'elle **ne s'arrête jamais**. Le bloc calculé supprime les deux défauts sans toucher
au modèle de propagation.

**Ce que cela préserve** : un `ConstantThrustManeuver` par phase, une masse scalaire
propagée par Orekit, un étage actif à la fois, l'invariant du §2.1, et les 43 fichiers qui
lisent `resolveActiveStage`.

**Ce que cela change, et qui est le but** : l'Isp cesse d'être écrite à la main, la masse
tombe en deux fois au lieu d'une, la section change au largage des propulseurs (41,1 →
22,9 m² sur l'Ariane), et l'étranglement est une grandeur qu'aucune entrée du catalogue ne
porte aujourd'hui.

> **L'invariant neuf, celui qu'il ne faut pas rater.** Le plancher de déplétion du bloc
> **n'est plus** `sec_i + masseAu-dessus_i` : le bloc doit s'éteindre quand les
> *propulseurs* sont vides, alors que le corps a encore des ergols au-dessus de ce
> plancher. Or `MassDepletionDetector.g` est littéralement `masse − plancher`, et
> `DepletionGuard` comme `DepletionStopTrigger` lisent ce plancher. Passer la formule
> habituelle laisserait le bloc brûler dans les ergols du corps **avec la section des
> propulseurs encore attachée**, sans rien signaler.

**Corollaire** : `resolveStagePropellant` répartit la masse consommée par le bloc au
prorata des débits — exact, puisque les deux débits sont constants — au lieu de la porter
au seul étage actif.

**Les deux autres approches, et pourquoi elles n'ont pas été retenues.** Un *ensemble
actif* (`resolveActiveStages` rendant plusieurs étages, chaque consommateur apprenant à
lire N étages) dit la vérité dans le modèle, mais calcule exactement la même physique pour
le prix des 43 fichiers. Des *masses par étage en état additionnel Orekit* seraient la
seule voie vers un étranglement variable dans le temps, mais font sortir du
`ConstantThrustManeuver` qui sert partout et obligent à réconcilier la masse unique
d'Orekit avec une comptabilité par étage.

### 3.2 La pile porte les propulseurs, avec une multiplicité, et les indices viennent du rôle

**Décision : `[propulseurs ×N, corps, S2, charge utile]`**, l'étage de propulseurs
agrégeant N exemplaires identiques — 2 pour le Falcon Heavy, 4 pour l'Ariane 64. Agréger un
solide et un cryogénique est une fiction ; agréger N solides identiques est exact : même
Isp, allumage commun, extinction commune, largage commun.

C'est la seule forme où **le largage des propulseurs reste ce qu'il est déjà**, une
`StageSeparationStage` ordinaire, et où `PHY-5` reçoit un objet de pile auquel accrocher
les N débris. Ranger les propulseurs *dans* le `StageModel` du S1 obligerait à inventer une
chute de masse partielle que `StageSeparationStage` ne sait pas exprimer ; en faire un
étage par propulseur donnerait quatre entrées rigoureusement identiques et un `double[]` de
charges dont trois cases ne peuvent qu'être égales.

**Les trois indices en dur deviennent des recherches par `StageRole`.** `StageRole` porte
déjà `BOOSTER`, `CORE`, `UPPER` : `AscentSequence` demande l'étage `CORE`, `GEOMission` et
`LunarOrbitMission` l'étage `UPPER`. Décaler les constantes d'un cran déplacerait la classe
de bug ; les dériver du rôle la referme.

**Conséquence mécanique** : `PropellantBudget` rend trois charges au lieu de deux, sur ses
six points d'entrée et dans toutes les fixtures qui en construisent un à la main.

### 3.3 L'étranglement vit sur `AscentProfile`

**Décision : un champ sur `AscentProfile`** — la fraction de poussée que le corps applique
pendant la phase partagée — à côté de la montée verticale, du coup de tangage et du coast
interétage. Le record est déjà défini comme *« flight-profile parameters imposed by the
launcher »*, et un étranglement pendant la phase partagée est exactement cela : un
programme de vol, pas une propriété de moteur. Un seul nombre suffit tant qu'un bloc
parallèle a un corps et un jeu de propulseurs.

La fraction n'est appliquée que par le bloc parallèle : au largage des propulseurs, le corps
retrouve sa poussée pleine sans que rien ne l'écrive.

**Le jour où un lanceur étranglera autre chose que son corps, le champ déménagera sur
`StageModel`.** C'est le prix assumé de ne pas ajouter un septième composant à un record
que tout le catalogue construit, pour y écrire `1,0` sur des étages qui ne s'étranglent
jamais.

### 3.4 Les Isp restent des proxy sur les étages atmosphériques

**Décision : les propulseurs et le corps gardent une Isp « moyenne trajectoire », les
étages supérieurs portent leur Isp de vide.** Les missions continuent de boucler entre
`PHY-8` et `PHY-2`, et l'item ne prétend pas régler une dette qu'il ne règle pas.

**Ce que la fiche promet et que le découpage ne tiendra pas.** Elle écrit *« plus aucune
moyenne solide / cryogénique, donc plus rien à dé-double-compter pour `PHY-2` »*. Or
`DT-13` mesure une moyenne **sol/vide**, pas une moyenne solide/cryogénique : 296 s dans
[282, 311] et 300 s dans [271, 331], absorbant `g₀·ΔIsp·ln R` — soit **408 m/s sur le
Falcon Heavy S1 et 671 m/s sur l'Ariane 62 S1**, épinglés par `IspProxyDebtTest`. Séparer
les propulseurs du corps supprime la seconde moyenne et laisse la première intacte.

**Ce que la séparation change quand même, et qui aide `PHY-2`** : l'écart sol/vide d'un
propulseur solide se compte en dizaines de secondes, celui d'un cryogénique en centaines —
le `[271, 331]` de l'agrégat Ariane est large **à cause du Vulcain**. Séparé, le proxy des
propulseurs est presque honnête et la dette **se concentre sur le corps** au lieu de se
diluer. `PHY-2` aura une entrée à reprendre par lanceur, pas un mélange. Les brackets par
étage restent à sourcer ; le lot qui écrit le catalogue enregistrera lequel porte la dette.

### 3.5 Le cargo sort des listes par un axe de finalité

**Décision : un troisième axe sur `PayloadModel`** — la finalité — faux partout sauf sur le
module cargo, et un `.filter` de plus dans `Payloads.forMissionType`.

Mettre une charge cargo en orbite n'a pas d'utilité opérationnelle : ce qu'on met en orbite,
ce sont des satellites, qui corrigeront la trajectoire que la traînée altère. Le cargo n'a
de sens qu'en mission de rendez-vous — d'où l'absence délibérée de maillage 3D pour lui
dans le lot `AST-1`, et le filtre ici.

**Pourquoi pas le domaine.** L'axe `PayloadDomain` répond à *« où la charge utile vole »*.
Un module cargo vole bien en orbite terrestre : son domaine est `EARTH`, et ce qui le
contraint est sa **finalité**, pas son lieu. Lui inventer un domaine `RENDEZVOUS`
corromprait l'axe « où » pour y ranger un « pour quoi faire ».

**Pourquoi pas le retrait du catalogue.** Le précédent existe — `LUNAR_ORBITER` est arrivé
*avec* le type de mission qui le vole, en `MIS-5 / L3` — mais le retrait coûterait cinq
fixtures à repointer et laisserait une ligne orpheline dans le tableau balistique de
`PHY-2`, pour une entrée que `MIS-6` réintroduira.

**Effet de bord favorable** : le cargo étant la seule entrée sans maillage, la table charge
utile → maillage devient **totale** sur tout ce que le wizard peut proposer, et `PHY-6`
n'a pas de cas de repli à écrire.

### 3.6 L'axe `akm*` devient une propulsion générique

**Décision : `propulsion`, `propellantCapacity`, `hasPropulsion()`**, et `GeoLoads.akmLoad`
avec. Le champ décrit déjà trois moteurs différents — kick d'apogée, insertion lunaire à
5 500 N, et demain maintien à poste — dont le seul dénominateur commun est *« la charge
utile a sa propre propulsion »* : exactement ce que `MissionType.requiresPayloadPropulsion()`
demande depuis le début, et qui est déjà, lui, correctement nommé.

`Spacecraft.hasApogeeKickMotor()` et le Javadoc de `StageRole.KICK` suivent le même
renommage. `PHY-8` étant l'item du catalogue, c'est ici ou jamais : 70 occurrences dans 15
fichiers, un diff mécanique qui se relit ligne à ligne sans rien apprendre, et une dette qui
ne se rouvrirait plus.

Ajouter un **second** système de propulsion à la charge utile — un AKM *et* des propulseurs
de maintien à poste, comme sur le vrai matériel — a été écarté : `Vehicle.propulsion()` en
résout un seul et rien dans la chaîne ne saurait choisir, donc le second champ serait inerte
et non résolvable.

### 3.7 La charge d'ergols de la charge utile se dimensionne sur un budget ΔV déclaré

**Décision : le catalogue déclare un budget ΔV par charge utile**, converti par Tsiolkovsky
inverse avec la marge de 10 % existante et plafonné par la capacité du réservoir — le code
de `loadsForHighOrbit` réutilisé tel quel :
`raw = masseSèche · (exp(Δv/vₑ) − 1) · 1,10`, puis `min(raw, capacité)`.

C'est ainsi qu'un satellite réel est spécifié — en ΔV de mission — et ça place le nombre
exactement là où `PHY-2` voudra le relever quand la compensation de traînée deviendra
réelle. `L0` mesure alors le trim LEO pour **vérifier** que le budget le couvre, au lieu
d'en dépendre pour exister.

Voler le réservoir plein aurait introduit une seconde convention chez les charges utiles —
`GEO_SAT` est dimensionné — dans une classe qui existe précisément pour qu'on ne vole pas
plein. Figer le trim mesuré en constante aurait figé un résidu qui dépend de la cible, de
l'inclinaison et du lanceur.

### 3.8 Le catalogue porte des hauteurs, le rendu les convertit

**Décision : une hauteur totale sur `LauncherModel`, une dimension sur `PayloadModel`**, et
`MissionRenderer` passe `hauteur / 2` là où il écrit `50,0`.

La hauteur est la grandeur physique vraie, la seule sourçable et vérifiable ;
`BodyRenderConfig.radiusMeters` garde son contrat de rayon, donc les planètes ne sont pas
touchées ; et les pièces ne demandent **aucune donnée supplémentaire**, la normalisation des
maillages livrée par `AST-1` s'en chargeant (§2.3).

**Deux effets recherchés.** Le seuil de bascule LOD suivant le même nombre, un satellite de
quelques mètres passe à son billboard bien plus près qu'un lanceur — ce qu'un `50,0` unique
interdit, et ce dont `PHY-6` a besoin pour qu'un `GEO_SAT` de deux tonnes ne soit pas dessiné
aussi grand que le Falcon Heavy qui l'a mis là. Et la question ouverte n° 1 de la préparation
— les propulseurs A64 à 36,2 % de la hauteur du maillage contre 21–24 % attendus — devient
**arbitrable depuis le dépôt** : `0,3624 × hauteur` est une longueur en mètres, comparable à
une référence.

---

## 4. Principe du découpage

**Le mécanisme d'abord, inerte ; puis les données, un lanceur à la fois ; et chaque
changement de trajectoire isolé dans son propre lot.**

Ce qui rend ce principe applicable, plutôt que souhaitable, c'est que les chiffres du dépôt
offrent **deux preuves d'iso-trajectoire gratuites** :

1. Le mécanisme entier — bloc parallèle, multiplicité, indices par rôle, champ
   d'étranglement — peut être livré **sans qu'aucun catalogue ne déclare de propulseurs**.
   Rien ne vole différemment, et les quatre épinglages le prouvent sans être touchés.
2. Le Falcon Heavy éclaté **à f = 1 est iso-trajectoire, exactement**. Ses trois corps étant
   identiques, `Isp_eff` vaut 296 s au chiffre près, la poussée du bloc 22,8 MN, le débit
   7 854,7 kg/s, et propulseurs et corps se vident **au même instant**, 157,0 s des deux
   côtés. À deux conditions : que le largage retire les deux entrées en un seul événement,
   et que la charge dimensionnée du S1 se répartisse au prorata 2/3 – 1/3.

Ces deux preuves séparent *« le code marche »* de *« les nombres changent »*. Après elles,
il ne reste que **deux** re-baselines, chacun attribuable à une cause unique : l'étranglement,
puis l'Ariane 64.

Le prix assumé : `L2` livre un état que personne ne fera voler — un Falcon éclaté non
étranglé — et l'item compte sept lots.

---

## 5. Les lots

### L0 — Baseline mesurée

**Rend vrai** : on sait ce que le dépôt fait avant d'y toucher, et on a les chiffres qui
diront si `PHY-8` a réussi.

- Les quatre épinglages re-capturés tels quels — `AscentBaselineN2Test` en mode capture
  écrit `build/baseline/<profil>-n2.txt`, `MissionPolylineBaselineTest` imprime ses
  constantes.
- Par lanceur et par profil (LEO 400, GEO, MEO, lunaire) : durée de combustion S1, date et
  masse au MECO, précision d'insertion, T/W au décollage.
- **Le ΔV du trim LEO**, que le dépôt ne connaît pas et dont le §3.7 a besoin comme témoin.
- Le T/W au décollage des deux lanceurs, qui devient un *résultat* une fois les poussées
  écrites par étage, alors qu'il est aujourd'hui une entrée implicite du réglage de la montée
  verticale.

**Ferme le lot** : aucun fichier de `src/main` touché.

### L1 — Le mécanisme, inerte

**Rend vrai** : le modèle sait faire brûler deux étages ensemble, et personne ne s'en sert.

- Les trois indices de pile dérivés de `StageRole`.
- La multiplicité sur `StageModel`, la fraction d'étranglement sur `AscentProfile`.
- Le bloc parallèle dans `VehicleStack` : agrégation exacte, plancher de déplétion propre au
  bloc (§3.1), `resolveStagePropellant` au prorata des débits, largage groupé.
- **Aucune entrée de `Launchers` ne déclare de propulseurs.**

**Ferme le lot** : les quatre épinglages restent verts **sans avoir été modifiés**.

### L2 — Le Falcon Heavy éclaté, à f = 1

**Rend vrai** : le catalogue décrit trois étages là où il en décrivait deux, et la fusée vole
exactement pareil.

- `FALCON_HEAVY` passe à `[propulseurs ×2, corps, S2]` : 44 t / 822 t / 15,2 MN / 21,0 m²
  et 22 t / 411 t / 7,6 MN / 10,5 m², Isp 296 des deux côtés.
- `PropellantBudget` répartit la charge du S1 au prorata 2/3 – 1/3.
- L'étranglement reste à 1,0.

**Ferme le lot** : mêmes épinglages, toujours verts — la seconde preuve d'iso-trajectoire.

### L3 — L'étranglement du corps central

**Rend vrai** : le Falcon Heavy a une phase corps-seul.

- `f ≈ 0,81` sur l'`AscentProfile` du Falcon, à confirmer par la durée réelle de combustion
  du corps une fois sourcée.

**Ferme le lot** : **premier re-baseline**, attribuable à une cause unique. Contrôles
physiques : combustion du corps ~187 s, phase corps-seul ~30 s, propulseurs inchangés à
157,0 s.

### L4 — L'Ariane 64 remplace l'Ariane 62

**Rend vrai** : le catalogue et l'écran volent la même fusée.

- `ARIANE_64` : `[P120C ×4, LLPM, ULPM]`, 564 t de solide et 152 t de cryogénique, sections
  9,08 / 22,9 / 22,9 m², Isp et poussées par étage (proxy sur les deux étages atmosphériques,
  §3.4).
- `PropellantBudget` : les solides volent pleins — `variableLoad()` rend enfin `false` sur un
  étage qui l'est vraiment — et le corps prend le reste. Le masque du balayage λ est revu, pas
  hérité.
- `LauncherAssets` cesse de décrire une Ariane 5, `DT-12` se ferme.

**Ferme le lot** : **second re-baseline**. Contrôles physiques : extinction des propulseurs
~130 s, Vulcain ~8 min, rapport de débits ~14, et une mission LEO 400 km qui insère encore.

### L5 — Les dimensions

**Rend vrai** : ce qui vole est dessiné à sa taille.

- Hauteur sur `LauncherModel`, dimension sur `PayloadModel`, `SPACECRAFT_RADIUS_METERS`
  supprimé, seuil LOD par objet (§3.8).

**Ferme le lot** : la longueur des propulseurs A64 calculée depuis la hauteur et comparée à
la référence externe — la question ouverte n° 1 de la préparation, tranchée ou explicitement
assumée.

### L6 — Le catalogue des charges utiles

**Rend vrai** : le wizard n'offre que des charges utiles volables, et le satellite
d'observation a un moteur.

- Renommage `akm*` → propulsion générique (§3.6).
- Axe de finalité et filtre cargo (§3.5).
- Propulsion et budget ΔV d'`EARTH_OBS_SAT`, dimensionnement dans `loadsForLeo` (§3.7).

**Ferme le lot** : aucune trajectoire ne bouge — la propulsion livrée reste inerte jusqu'à
`PHY-6` — et le cargo n'apparaît dans aucune liste du wizard.

---

## 6. Ordonnancement et risques

**L'ordre est `L0 → L1 → L2 → L3 → L4 → L5 → L6`.** `L5` et `L6` sont **indépendants de
`L1`–`L4`** et pourraient passer en premier ; ils sont placés après pour garder le gros diff
mécanique du renommage loin des deux re-baselines, où toute ligne modifiée doit être
attribuable.

**Risque 1 — la prémisse de `CentralBodyBaselineTest` meurt en `L3`.** Son Javadoc justifie
l'égalité stricte de `double` par *« the refactor keeps the same constant, the same cached
frame instances and the same shared 8×8 gravity model, so the floating-point operations
happen in the same order »*. Vrai en `L1` et `L2` — c'est ce qui en fait des preuves — faux
dès `L3`. La table de 1 296 lignes est à régénérer, et sa justification à réécrire : elle
épinglera désormais un vol différent, pas le même vol.

**Risque 2 — rien ne garantit qu'un LEO 400 km boucle encore après `L4`.** L'A64 n'est pas
une A62 avec deux propulseurs de plus : c'est un autre véhicule, et la phase corps-seul de
~8 min n'a jamais été volée par le modèle. `L0` donne le point de comparaison ; si la mission
ne boucle plus, c'est un résultat de `L4`, pas une régression à chercher ailleurs.

**Risque 3 — le dimensionnement des solides peut faire échouer une séparation.** Les
propulseurs volant pleins et le corps prenant le reste, la charge du corps change ; or
`StageSeparationStage` **refuse** de larguer si l'étage attendu n'est pas actif, et le bilan
10 §6 rappelle ce que ce garde-fou a déjà attrapé — un étage supérieur plus léger arrêtant la
turn avant que le S1 ne soit sec. À surveiller en `L4`.

**Risque 4 — les nombres externes ne sont pas encore là.** Voir §7.

---

## 7. Limitations assumées, et ce qui reste à sourcer

**Hors du dépôt, et sans quoi `L3` et `L4` ne peuvent pas être écrits :**

1. Les **Isp et poussées par étage** des deux lanceurs, avec leur bracket sol/vide — le
   dépôt ne donne que les agrégats et un ratio de 65 %.
2. Le **partage des 36 t de masse sèche** de l'Ariane entre propulseurs et corps : question
   ouverte n° 5 de la préparation, toujours ouverte.
3. La **durée réelle de combustion du corps central du Falcon** (~187 s), dont `f` se déduit.
4. Les **hauteurs** des deux lanceurs.

**Assumé dans le modèle :**

- **La poussée d'un solide est constante.** Un P120C a une courbe régressive ; à poussée
  constante, choisir la poussée *est* choisir la durée, puisque `t = m·Isp·g₀/F`. On ne peut
  pas tenir la poussée de pointe et la durée réelle en même temps, et le lot choisira laquelle
  des deux il tient — avec la conséquence sur le T/W au décollage mesurée en `L0`.
- **`DT-13` traverse l'item** (§3.4). La dette se localise sur le corps, elle ne disparaît pas.
- **L'étranglement est constant** pendant la phase partagée (§1, point 5).
- **La propulsion d'`EARTH_OBS_SAT` ne sert à rien avant `PHY-6`** (§1, point 1).

---

## 8. Ce que `PHY-8` lègue

- À **`PHY-2`** : une ascension de forme physique — propulseurs et corps ayant chacun leur
  durée — et une section qui change au largage, donc une traînée à intégrer sur la bonne
  géométrie. Plus une dette `DT-13` localisée sur une entrée par lanceur au lieu d'un mélange.
- À **`PHY-5`** : un propulseur est une entrée de pile avec sa masse sèche, sa section
  (9,08 m² sur l'Ariane, 10,5 m² sur le Falcon) et sa taille dessinée — les trois grandeurs
  qu'il faut pour propager et dessiner un débris.
- À **`PHY-6`** : une taille par objet, donc un satellite qui n'est plus dessiné aussi grand
  que son lanceur ; une charge utile propulsée et dotée d'ergols, prête à recevoir les
  combustions que la chaîne LEO lui confiera ; et une table charge utile → maillage totale,
  sans cas de repli.
- À **`MIS-6`** (v4) : le module cargo intact au catalogue, qu'il suffira de rendre éligible
  en ajoutant le type de mission qui le vole.
