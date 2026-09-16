# PHY-5 — Machinerie multi-objets, étages largués et charge utile — découpage

Fiche de l'item : [`roadmap/02-roadmap-v2.md`](../roadmap/02-roadmap-v2.md) §4, `PHY-5`
(★4 ◆3 L, après `PHY-2` et `AST-1`). La roadmap a renommé l'item de « Étages
largués : objets propagés et modèles 3D » vers **« Machinerie multi-objets »** :
les étages largués restent le premier client, mais le livrable est la machinerie
— N objets propagés, N éphémérides, N vues. Le document nomme donc le chantier
par la machinerie, pas par le client.

> **`PHY-6` est absorbé par ce chantier (décision du 2026-09-15).** La roadmap fait
> de « la charge utile comme objet distinct » un item séparé, `PHY-6`, au motif
> que la machinerie et les maillages n'existent pas encore. Les deux prémisses
> sont fausses (§2.2) : les maillages de charge utile **existent**, et la charge
> utile **est déjà l'objet qui continue** après le dernier largage. Une fois que le
> primaire maigrit jusqu'à son maillage (L4), le « Falcon Heavy en orbite
> géostationnaire » — la raison d'être entière de `PHY-6` — est corrigé ici. Ce
> document consigne l'incohérence ; il ne réécrit pas la roadmap (l'auteur
> dépriorisera `PHY-6` et re-pointera la dépendance de `MIS-10` vers `PHY-5`).

Ce découpage enregistre des décisions prises en conversation, une à une. Il
n'introduit aucune décision qui n'y ait été tranchée.

---

## 1. Périmètre

**Ce que PHY-5 livre.**

- **La machinerie multi-objets** : le passage, de bout en bout (donnée →
  registre → renderer), de « un objet par mission » à « N objets ».
- **Les étages largués comme objets propagés** : chaque pièce larguée devient un
  objet dessiné, avec sa trajectoire propagée sous traînée, son maillage, sa
  couleur et son breadcrumb.
- **La séparation à l'écran** : une impulsion d'écartement, M corps qui divergent,
  l'objet principal qui **maigrit** à mesure qu'il largue.
- **La charge utile comme objet distinct** : le maigrissement va jusqu'au bout —
  le dernier objet dessiné est le satellite (`goes` / `landsat8` / `lro`), pas le
  lanceur. C'est le « Falcon Heavy en GEO » corrigé, et l'absorption de `PHY-6`.

**Décisions de périmètre (conversation du 2026-09-15).**

| Axe | Retenu | Écarté |
|---|---|---|
| Fin de trajectoire d'un débris | Plancher d'altitude franc + horizon temporel pour l'orbital ; **pas de marqueur d'impact au sol** | Le point d'impact projeté (→ `MIS-10`) ; ne pas propager du tout jusqu'à basse altitude |
| Identité d'un débris | **Dessiné, non focalisable** au premier cut : éphéméride, ruban, couleur, label propres | Débris sélectionnable / focalisable caméra / présent dans la télémétrie — **à voir plus tard** |
| Multiplicité d'un bloc | **M objets propagés** indépendamment (2 pour le Falcon Heavy, 4 pour l'Ariane 64), chacun sa section mono-pièce et son impulsion | Un seul débris agrégé ; une trajectoire propagée avec M maillages cosmétiques |
| Maillage de l'objet principal | Le principal **maigrit jusqu'à la charge utile** : `full → after_boosters → after_s1 → S2 → payload` | Principal figé à la pile entière ; ou s'arrêter à `S2` et laisser la charge utile à un `PHY-6` séparé |

**Ce que l'item s'interdit.**

- **L'optimiseur ne voit pas les débris.** Un objet largué est propagé pour
  l'affichage, jamais dans la boucle CMA-ES : sinon le coût d'une évaluation est
  multiplié par le nombre d'objets, pour un résultat qui n'entre dans aucune
  fonction objectif. C'est l'invariant du chantier (§4).
- **Pas de marqueur d'impact au sol** : livrable annoncé de `MIS-10`, et il suppose
  une terminaison de rentrée fiable — or `ReentryGuard` est inopérant sous traînée
  (`BUG-10`, réparé en tête de `MIS-10`, chantier postérieur).
- **Pas de sélection ni de focus caméra sur un débris** au premier cut. (La charge
  utile, elle, est focalisable — mais parce qu'elle *est* le primaire, pas un
  débris ; c'est gratuit, §2.2.)
- **Pas de largage de coiffe.** La coiffe n'a **ni masse ni événement** dans le
  modèle (§2.2) ; lui en donner un changerait la masse d'ascension et
  **re-baselinerait les gates** — c'est un changement physique/catalogue de la
  famille `PHY-8`, pas un item de rendu. Les débris, eux, sont gratuits pour
  l'optimiseur parce que leur masse est *déjà* comptée par `StageSeparationStage`.
- **Pas d'attitude de charge utile, pas de déploiement de panneaux** : c'est
  `MIS-12` (v4), et les coefficients balistiques du catalogue supposent
  explicitement des panneaux repliés.

> **Élargissement assumé par rapport à la fiche.** Deux points n'étaient pas dans
> les quatre puces « À faire » de `PHY-5` : que l'objet **principal** change de
> maillage, et que le maigrissement aille **jusqu'à la charge utile**. Les deux
> ont été ajoutés en conversation parce que les maillages `after_boosters` /
> `after_s1` **et** `payloads/*.gltf` livrés par `AST-1` les préparent
> explicitement (§2.2), et parce que cela dissout `PHY-6` dans ce chantier.

> **Rétrécissement assumé par rapport à la fiche.** La fiche dit qu'un débris
> *« garde son identité dans le breadcrumb et la télémétrie »*. Au premier cut,
> « identité » se réduit, **pour les débris**, à : dessiné, avec ruban / breadcrumb
> / couleur / label propres — pas de présence dans le widget de télémétrie ni de
> cible de sélection. La partie télémétrie/sélection des débris est différée
> jusqu'à ce qu'un besoin la réclame.

---

## 2. État des lieux

### 2.1 La colonne « un objet par mission »

Tout, de la donnée au pixel, suppose aujourd'hui **une** trajectoire par mission.

| Maillon | Fait mesuré |
|---|---|
| Donnée | [`MissionEntry`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/context/MissionEntry.java) tient **un** `MissionEphemeris` (champ `volatile ephemeris`, l. 44) |
| Registre | [`ApplicationContext`](../../src/main/java/com/smousseur/orbitlab/app/ApplicationContext.java) : `Map<MissionId, MissionRenderer>` (l. 42) — **un** renderer par mission |
| Renderer | [`MissionRenderer`](../../src/main/java/com/smousseur/orbitlab/states/mission/MissionRenderer.java) tient **une** `LodView`/`SpacecraftPresenter` + **un** `MissionTrajectoryRenderer` (un ruban) |
| Boucle | [`MissionOrchestratorAppState.update`](../../src/main/java/com/smousseur/orbitlab/states/mission/MissionOrchestratorAppState.java) (l. 67) : une mission = un point (`eph.displayPointAt`) = un ruban |
| Génération | [`MissionEphemerisGenerator.generate`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/ephemeris/MissionEphemerisGenerator.java) appelé **une seule fois**, dans le replay de [`MissionOptimizer`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionOptimizer.java) (l. 327) — **hors** boucle CMA-ES |
| Séparation | [`StageSeparationStage.enter`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/StageSeparationStage.java) (l. 107) : une chute de masse (`previousState.withMass(info.massAfterJettison())`), rien de plus — l'étage abandonné disparaît à l'instant où il devient intéressant |

Le maillage est baké dans la `LodView` à la construction : `MissionRenderer`
verrouille *« Fixed for its whole life — swapping the mesh of a live LodView is
not supported »* (l. 184-192). Un changement de lanceur passe aujourd'hui par la
destruction/recréation du renderer.

### 2.2 Où la fiche du roadmap est fausse ou périmée

- **`AST-1`, colonne « État » : fausse sur les pièces de lanceur.** Elle affirme
  *« un seul maillage par lanceur, la pile entière d'un seul tenant »*. `git
  ls-files` montre des **maillages par pièce**, non câblés :

  ```
  ariane_64-booster1.gltf … booster4.gltf   heavy_falcon-booster1.gltf, booster2.gltf
  ariane_64-core.gltf   -S2.gltf   -fairing1/2.gltf   -after_boosters.gltf   -after_s1.gltf
  heavy_falcon-core.gltf -S2.gltf  -fairing1/2.gltf   -after_boosters.gltf   -after_s1.gltf
  ```

  Les débris ont donc leur maillage (`booster<i>`, `core`, `S2`), et la pile
  **qui continue** a le sien après chaque largage (`after_boosters`, `after_s1`).

- **`AST-1` / `PHY-6` : faux sur les charges utiles.** La fiche `AST-1` dit *« les
  cinq entrées du catalogue Payloads n'ont aucune représentation »* et la question
  ouverte n°2 demande *« quelles familles méritent un maillage, à trancher avant de
  commander »*. Or **trois maillages existent déjà** : `payloads/goes/goes.gltf`,
  `payloads/landsat8/landsat8.gltf`, `payloads/lro/lro.gltf`. Ils couvrent GEO
  (`goes` → `GEO_SAT`), observation terrestre (`landsat8` → `EARTH_OBSERVATION_SAT`)
  et lunaire (`lro` → `LUNAR_PROBE`/`LUNAR_ORBITER`). Seule `CARGO_MODULE`, filtrée
  du wizard par `PHY-8`, n'en a pas.

- **La charge utile est déjà l'objet qui continue.**
  [`LaunchConfiguration`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/LaunchConfiguration.java)
  la place *« on top of the stack »* comme un `Spacecraft` distinct avec un
  `payloadId` (l. 15-20) ; le Javadoc de `StageSeparationStage` confirme *« the
  payload's kick motor once the upper stage separates »*. Après le dernier largage,
  le véhicule actif **est** la charge utile — il n'y a pas de nouvel événement de
  séparation à inventer pour PHY-6, seulement un maillage à dessiner. Et le primaire
  étant déjà focalisable (clic vaisseau), la charge utile devient l'objet cliquable
  **sans travail de focus**.

- **« jusqu'à l'impact » ne peut pas s'entendre littéralement.** `BUG-10` :
  `ReentryGuard` est inopérant sous traînée, l'intégrateur mourant à −9 km / −30 km
  au-dessus de son plancher `SUBSURFACE_FLOOR = −50 km`. Sa réparation est le
  premier livrable de `MIS-10`, **postérieur** à PHY-5. « Impact » se lit donc ici
  comme « descend jusqu'à un plancher d'altitude et s'y arrête » (D5), sans marqueur
  sol.

- **La coiffe n'existe pas comme objet largable.** Elle n'apparaît que comme
  géométrie — une fraction de la hauteur de pile (`heightMeters` *« fairing
  included »*) et des maillages `fairing1/2`. Aucune masse au catalogue, aucun
  `FairingSeparationStage`. La modéliser est un changement de physique, pas de rendu
  (§1, s'interdit).

### 2.3 Ce que `PHY-8` a déjà préparé, et que PHY-5 consomme

- **La donnée d'un débris est en main à la séparation.** À
  `StageSeparationStage.enter`, [`ActiveStageInfo`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/ActiveStageInfo.java)
  donne la masse larguée (`previousState.getMass() − massAfterJettison()`) et **la
  section propre de la pièce** via `aerodynamics()` (l. 39) — la
  `AerodynamicProperties` que `PHY-8` a attachée à chaque propulseur largué
  (π·1,7² = 9,1 m² pour un P120C), *« ce dont PHY-5 a besoin de toute façon »*.
  Position et vitesse sont continues au largage (seule la masse change).
- **Une propagation ballistique se monte comme un étage.** La traînée est une
  `DragForce(Atmosphere, IsotropicDrag(section, Cd))` construite depuis le
  `FlightContext` ; un débris réutilise ce montage, sans poussée.
- **Le repère-par-échantillon se réutilise tel quel.** `MissionEphemeris` porte
  déjà un `TrajectoryArc[]`, un repère par échantillon (`PHY-4 / L3`). Un booster
  suborbital reste un arc terrestre unique ; « N trajectoires » est le même exercice
  que `PHY-4` a fait pour « une trajectoire, N repères », sur l'autre axe.
- **Les hauteurs par pièce sont au catalogue** (`PHY-8` §3.8), ce qui donne à chaque
  configuration du principal — et à chaque débris — sa taille dessinée sans nombre à
  réinventer. **Réserve** : la hauteur *de charge utile* reste à vérifier (le
  catalogue porte une section, pas forcément une hauteur — §7).

---

## 3. Décisions de conception

### 3.1 D1 — Un « objet suivi » = une trajectoire + un maillage qui peut changer de phase

Un objet dessiné est un `TrackedObject` : un `MissionEphemeris` (réutilisé tel
quel) + une identité (couleur, label) + une **clé de maillage par phase**.

- Le **primaire** a une clé qui change aux séparations, jusqu'à la charge utile
  (`full → after_boosters → after_s1 → S2 → payloadId`), avec la hauteur de la
  configuration (catalogue). Sa trajectoire reste l'éphéméride volée unique.
- Un **débris** a une clé constante (`booster<i>`, `core`, `S2`) et sa propre
  éphéméride, produite par le `DebrisGenerator` (D3).

La résolution clé → maillage réutilise le patron de `LauncherAssets` : une table par
pièce de lanceur (`booster<i>`, `core`, `after_*`, `S2`) et une table par famille de
charge utile (`payloadId → payloads/*.gltf`).

Côté portage : `MissionEntry` garde l'éphéméride primaire (comme aujourd'hui) et
gagne une `List<DebrisTrack>` ; `MissionComputeResult` transporte les débris + la
chronologie de silhouette du primaire ; `MissionOrchestratorAppState` les pose sur
l'entrée là où il pose déjà l'éphéméride. Les débris sont **affichage seul,
recalculés au replay, jamais sauvés** dans un scénario.

### 3.2 D2 — Le renderer : une vue par objet, avec échange de maillage

On extrait de `MissionRenderer` (400 lignes, deux responsabilités aujourd'hui) un
`TrackedObjectView` : `LodView` + `MissionTrajectoryRenderer` + `updateFromPoint`.
`MissionRenderer` devient un **coordinateur** :

- une **vue primaire**, qui garde les concerns de niveau mission (clic /
  `onSpacecraftSelected`, occulteur d'éclipse, échelle) **et** l'échange de maillage
  (jusqu'à la charge utile) ;
- une `List<TrackedObjectView>` **débris**, allégées : maillage fixe + ruban, ni
  clic, ni occulteur d'éclipse.

Le registre `Map<MissionId, MissionRenderer>` est **inchangé** : `MissionId` reste la
clé unique de caméra, télémétrie, visibilité et focus. Les débris vivent *dans* la
mission ; la charge utile *est* le primaire. Rien dans la caméra ni la boucle
orchestrateur par-mission (statut / visible / détection d'édition wizard) n'a à
distinguer un objet suivi d'un autre.

On ajoute à [`Model3dView`](../../src/main/java/com/smousseur/orbitlab/engine/scene/body/lod/Model3dView.java) /
`LodView` **la capacité que le Javadoc dit manquer** : détacher l'ancien spatial,
attacher le neuf, au franchissement d'une phase du primaire. La clé-de-maillage-par-
phase de D1 pilote l'échange ; un débris, à clé constante, ne s'en sert pas.

### 3.3 D3 — La génération : événements de largage → `DebrisGenerator`, hors CMA-ES

Le replay ([`MissionOptimizer`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionOptimizer.java)
l. 327) émet, en plus de l'éphéméride primaire, une liste d'**événements de largage**
au passage d'un `StageSeparationStage` : état de séparation, pièce(s) larguée(s),
multiplicité M, spec d'impulsion. Un `DebrisGenerator` post-passe monte, pour chaque
pièce, un propagateur **traînée seule** (`IsotropicDrag` depuis sa
`AerodynamicProperties`, pas de poussée), l'échantillonne en un `MissionEphemeris`, et
l'emballe en `DebrisTrack`.

`StageSeparationStage` **reste une chute de masse, inchangé** : les débris sont un
concern du chemin de replay. La passe d'optimisation et les quatre gates à tolérance
zéro ne sont pas touchés (§4).

### 3.4 D4 — L'impulsion de séparation

Appliquée **aux débris seuls** : le primaire est la trajectoire volée / optimisée,
intouchable (la modifier changerait le résultat que les gates épinglent). Défaut : un
Δv constant **~1 m/s** dans une direction d'écartement par objet — les M boosters
répartis radialement autour de l'axe véhicule — pour qu'ils divergent visiblement.
Pas de donnée catalogue neuve ; l'alternative (un Δv par lanceur) demanderait de
sourcer un nombre que personne n'a. **Valeur à confirmer en `L0`.**

### 3.5 D5 — Terminaison des débris *(re-cadré après mesure L0 §5.2)*

Deux bornes, aucune garde événementielle (`ReentryGuard` est cassé, `BUG-10`) :

1. **Plancher d'altitude géodésique = 0 km**, détecteur `STOP` — la borne
   structurante. Géodésique et non sphérique, pour ne pas retomber dans le piège du
   `SUBSURFACE_FLOOR = −50 km` (l'altitude sphérique d'un pas de tir est déjà
   négative, la Terre étant aplatie de 21,4 km). **Validé en L0** : l'intégrateur
   atteint le sol à toutes les tolérances, aucune mort d'intégrateur — la mort de
   `BUG-10` ne survient pas, le plancher coupe avant le sous-sol.
2. **Horizon temporel** pour le débris en orbite **haute** qui ne rentrerait pas
   avant des semaines (un S2 en GTO, un débris à 400 km) : sinon on le propagerait
   indéfiniment. Défaut : l'horizon de restitution de la mission.

> **Ce que L0 a démenti (spec [`02-baseline-L0.md`](02-baseline-L0.md) §5.2).** Ce
> paragraphe faisait de la **tolérance grossière** la « parade centrale » contre
> l'explosion de pas de `PHY-1` (452 → 982 497). La mesure l'infirme : cette
> explosion **ne se reproduit pour aucun débris de lanceur** — trop dense (BC élevé),
> il plonge ou décroît net, ≤ ~330 pas même propagé jusqu'au sol. La tolérance ne
> change le compte que de ~4,5× sur des comptes minuscules. Elle **reste disponible**
> comme optimisation bon marché (un propagateur d'affichage à tolérance lâche coûte
> un peu moins), mais elle n'est **pas** une borne du chantier : les deux bornes
> ci-dessus suffisent, et le coût débris mesuré est de +7 % (GEO) à +64 % (Ariane
> LEO, replay bon marché × K=5) du replay, imperceptible en temps utilisateur.

### 3.6 D6 — `DT-18`, précondition d'asset et non de code

Le booster Ariane est 37 % trop large dans son maillage ([`DT-18`](../dette-technique.md)) ;
l'écart se voit dès qu'il vole à côté du corps. Ré-export externe, **pas un bloqueur
de code** : PHY-5 avance, le Falcon Heavy (trois corps justes) sert de profil de
démonstration, l'Ariane se corrige en parallèle. `LauncherMeshProportionTest`
**épingle la valeur fausse** ; PHY-5 n'y touche pas, il rougira au ré-export (par
conception — le test oblige à revenir mettre à jour `DT-18`).

---

## 4. Principe du découpage

**L'invariant.** Les débris sont produits **au seul point de replay**
(`MissionOptimizer` l. 327), hors boucle CMA-ES. La passe d'optimisation et les
**quatre gates à tolérance zéro** ne sont donc pas touchés par tout le chantier — le
maigrissement du primaire non plus : il ne change que le maillage dessiné, pas la
trajectoire volée. `L0` l'acte comme référence ; chaque lot suivant le vérifie.

**Un changement de comportement à la fois.** `L0` mesure. `L1` monte la machinerie
avec un débris inerte. `L2` la charge (impulsion, multiplicité, identité). `L3` fait
maigrir le primaire sur les pièces de lanceur. `L4` fait **livrer la charge utile**
par une LEO propulsée (séparation S2 + trim charge utile, une trajectoire — voir le
re-cadrage §5). `L5` prolonge le maigrissement jusqu'au maillage de la charge utile —
c'est là que « le Falcon Heavy en GEO » disparaît. `L6` (ajouté après la vérif
visuelle de L5) place chaque pièce à son **siège de rendu** pour que les débris et
les rubans collent, sans toucher la propagation.

---

## 5. Les lots

### L0 — Baseline mesurée *(aucun `src/main` touché)*

- Acter l'invariant : optimisation + quatre gates à tolérance zéro **intacts**.
- Mesurer, par profil (Falcon Heavy LEO-400 ; Ariane 64 en LEO **et** GEO) : le
  nombre de `StageSeparationStage` et le **M** par largage, le coût du replay actuel,
  le rendu mono-objet en référence.
- Mesurer le coût d'**une** rentrée de booster sous traînée à **tolérance grossière +
  plancher 0 km** : confirmer que ça effondre l'explosion de pas (D5) et dimensionner
  K × coût. Confirmer / ajuster `⟨D4 ~1 m/s⟩` et `⟨D5 plancher 0 km⟩`.

### L1 — La machinerie multi-objets, un débris inerte

- Modèle D1 (`TrackedObject` / `DebrisTrack`, `MissionEntry` gagne
  `List<DebrisTrack>`, `MissionComputeResult` les porte) ; extraction D2
  (`TrackedObjectView`, `MissionRenderer` fanne — **maillage constant, pas encore
  d'échange**).
- `DebrisGenerator` D3 : **un** débris à **une** séparation, propagateur
  traînée-seule à tolérance lâche, plancher + horizon (D5), **pas d'impulsion**,
  dessiné avec `<lanceur>-booster1.gltf`.
- Prouve N éphémérides + N vues + la couture de rendu + la borne compute. `DT-17`
  (perf du ruban, N rubans) mordu et mesuré ici. Les quatre gates restent verts.

### L2 — La séparation complète : impulsion + M objets + identité

- Impulsion d'écartement par objet (D4) ; fan-out de multiplicité (bloc → **M**
  débris, chacun `booster<i>` + sa section mono-pièce) ; le cœur et l'S2 largués
  deviennent aussi des débris.
- Identité « non focalisable » : couleur, label, breadcrumb propres par débris. C'est
  le moment « deux (ou quatre) corps qui s'écartent ».

### L3 — Le principal maigrit (pièces de lanceur)

- Capacité d'échange de maillage sur `Model3dView` / `LodView` ; silhouette du
  primaire par phase de lanceur (`full → after_boosters → after_s1 → S2`) + hauteur
  décroissante, pilotée par la chronologie des largages.
- Isolé après la machinerie multi-objets : il réutilise la clé-de-maillage-par-phase
  de D1 et dépend d'une capacité neuve (échange de maillage), dont le risque ne doit
  pas contaminer la machinerie.

> **Re-cadrage du 2026-09-15 (en cours de L4).** L'ancien L4 « la charge utile
> comme objet distinct » supposait que le seul geste manquant était un maillage à
> dessiner. Une redirection l'a démenti : une mission **LEO** doit *livrer* sa
> charge utile (sa raison d'être), ce qui demande une **séparation S2
> supplémentaire** et un **trim porté par la charge utile** — un changement de
> **trajectoire**, pas de maillage. L4 est donc scindé en deux lots, un
> comportement à la fois (§4) : **L4** fait la physique, **L5** (ex-L4) fait le
> rendu.

### L4 — La LEO livre sa charge utile *(physique)*

- Détail : [`06-conception-L4.md`](06-conception-L4.md). Une LEO **à charge utile
  propulsée** largue son S2 après le transfert (`StageSeparationStage(UPPER)`,
  comme le GEO) et la charge utile fait son **trim final** (~6 m/s) sur sa propre
  propulsion. Le transfert optimisé **reste sur le S2** (CMA-ES intouché) ; le
  plane trim **reste sur le S2** (au-delà du budget charge utile).
- **Conditionnel à l'ergol utilisable** (`Spacecraft.hasUsablePropellant()`) : une
  charge inerte / legacy garde la chaîne d'aujourd'hui **au bit près**. Les quatre
  gates volent toutes une charge à 0 ergol → **invariant préservé, aucune
  re-baseline**.

### L5 — La charge utile comme objet dessiné *(rendu, absorbe `PHY-6`)*

- Dernière phase du maigrissement : `after_s1 → payloadId → payloads/*.gltf`. Table
  de maillage par famille (`goes`/`landsat8`/`lro`), taille de charge utile (§7),
  repli pour une charge sans maillage (cargo, ou payload custom).
- Effet : une mission `GEO_SAT` dessine un **satellite** en GEO, pas un Falcon
  Heavy — et, la séparation S2 LEO de L4 aidant, une charge d'observation en LEO
  aussi. La focalisabilité est acquise (la charge utile est le primaire). C'est le
  livrable de `PHY-6`, rendu ici parce que la machinerie et les maillages sont là.

### L6 — Les pièces collent : sièges de rendu *(ajouté après vérif visuelle de L5)*

- Détail : [`08-conception-L6.md`](08-conception-L6.md). La vérification visuelle de
  L5 a montré que **toutes les pièces sont recentrées base = 0 dans leur glTF**, donc
  base-à-l'ancre les empile au point propagé : au largage, le débris et la silhouette
  restante se chevauchent, et le reste « se téléporte ». Correctif **100 % rendu** : un
  **siège** en repère corps (règle « on aligne les nez ») ajouté à la position dessinée
  du maillage **et** de la pointe du ruban, pour que les pièces — débris et rubans —
  collent.
- **La propagation n'est pas touchée** : le CoM ponctuel reste la trajectoire volée.
  Un premier essai qui poussait l'offset dans la propagation des débris (point-1) a été
  **annulé**. L'invariant du §4 tient ; les quatre gates sont saufs par construction.

### L7 — Débris : visibilité + trace au sol *(fonctionnel, après vérif L5/L6)*

- Détail : [`09-conception-L7.md`](09-conception-L7.md). Les débris + rubans inertiels
  font « fouilli ». Correctif fonctionnel, **100 % rendu** : débris **invisibles par
  défaut** (3D si proche, rien sinon), **toggle global** « Afficher les débris » ; et le
  ruban instantané est remplacé par une **trace au sol** qui répond à « d'où / où » —
  courbe de chute complète en **repère tournant** (collée au globe dessiné, impact fixé au
  sol) pour un débris **retombant**, **icône seule** (inertiel) pour un débris **orbital**.
- Nouveau seam : un **nœud repère-tournant** par corps dans `SceneGraph`, mis à jour par
  `PlanetPoseAppState`, lu par `MissionRenderer`. Physique et gates intacts.

---

## 6. Ordonnancement et risques

- **Aucune dépendance dure d'asset.** Les maillages par pièce de lanceur **et** les
  trois maillages de charge utile existent (contre `AST-1` / `PHY-6`, §2.2). Seul
  `DT-18` reste (booster Ariane trop large), ré-export externe **en parallèle** ; le
  Falcon Heavy sert de profil de démonstration.
- **`LauncherMeshProportionTest` épingle la valeur fausse** de `DT-18`. PHY-5 n'y
  touche pas ; il rougira au ré-export, par conception.
- **Risque compute : K débris × rentrée.** La tolérance grossière (D5.1) est la
  parade, **validée en `L0` avant `L1`**. Si `L0` la dément, le chantier s'arrête sur
  la borne avant de monter la machinerie.
- **`DT-17`** (perf du ruban jamais profilée) remonte en v2 parce que PHY-5 affiche N
  rubans. Mineur ; mesuré en `L1`, traité seulement s'il mord.
- **`L3`/`L4` dépendent d'une capacité neuve** (échange de maillage sur `LodView`) que
  le code déclare non supportée. Isolés en fin de chantier pour que leur risque ne
  contamine pas la machinerie multi-objets.
- **Décision de mapping à acter en `L4`** : cinq charges utiles, trois maillages —
  `goes → GEO_SAT`, `landsat8 → EARTH_OBSERVATION_SAT`, `lro → LUNAR_PROBE` **et**
  `LUNAR_ORBITER` ; `CARGO_MODULE` sans maillage (hors wizard). Le mapping est presque
  imposé par les fichiers existants ; reste à le figer.

---

## 7. Limitations assumées, et ce qui reste à sourcer

- **Pas de télémétrie ni de sélection de débris** au premier cut (§1) — différé.
- **Pas de marqueur d'impact au sol** — c'est `MIS-10`.
- **Impulsion de séparation par défaut, non sourcée** (~1 m/s, D4) : cosmétique
  raisonnable, à confirmer en `L0`, jamais une prétention physique.
- **Hauteur de charge utile à vérifier / sourcer** : le catalogue porte une section,
  pas nécessairement une hauteur ; L4 en a besoin pour ne pas dessiner un satellite de
  2 t à la taille du lanceur (`PHY-8` avait rangé ce point dans `PHY-6`).
- **Coiffe** : maillages `fairing1/2` présents mais largage non modélisé — hors
  périmètre (changement de physique, §1).
- **`DT-18`** : le rendu Ariane des débris n'est juste qu'après ré-export du maillage.

---

## 8. Ce que PHY-5 lègue

- **À `MIS-10`** : la cohérence de produit *« on déorbite un satellite, pas un Falcon
  Heavy »* (via L4), **et** le propagateur d'affichage traînée-seule à tolérance
  grossière + sa borne d'altitude, dont `MIS-10` étend la terminaison en un marqueur
  d'impact projeté une fois `BUG-10` réparé. La dépendance que la roadmap écrit
  `MIS-10 → PHY-6` devient `MIS-10 → PHY-5`.
- **À la roadmap** : `PHY-6` n'a plus de contenu propre (§2.2). Reste, si l'auteur le
  souhaite, une coquille pour la seule question ouverte n°2 — déjà quasi tranchée par
  les trois maillages existants.
