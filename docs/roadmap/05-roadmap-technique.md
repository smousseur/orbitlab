# Roadmap technique — passe de robustesse · révision 2026-09-02

Ce document ordonne les **fiches ouvertes** des trois registres
([`bugs.md`](../bugs.md), [`dette-technique.md`](../dette-technique.md),
[`reliquats.md`](../reliquats.md)) en lots intégrables entre les features des
roadmaps de version — [v1](01-roadmap-v1.md), [v2](02-roadmap-v2.md),
[v3](03-roadmap-v3.md), [v4](04-roadmap-v4.md). Index :
[`00-index.md`](00-index.md).

**Ce qu'il n'est pas.** Il ne décrit aucune fiche : chacune vit dans son
registre, avec son mécanisme, ses mesures et ce qui reste non vérifié. Ce
document ne dit que **quand** traiter un item et **pourquoi à ce moment-là**.
Un item qui change d'état se met à jour dans son registre, pas ici.

**Comment le lire.** Le §2 donne la vue d'ensemble ; les §3 à §6 détaillent un
jalon chacun. Si vous ne lisez qu'une chose, lisez le §2 puis le §3 — `J-1.1`
est le seul lot dont rien ne dépend et dont tout le reste dépend.

---

## 1. Méthode et règle de rattachement

**Axe principal : le jalon bloquant.** Un item va au jalon de **la première
version qui le rend coûteux**, pas à celle qui le rend visible. Un défaut de
rendu visible depuis six mois mais qui ne bloque rien n'a pas de jalon ; une
duplication invisible que la prochaine version va payer une troisième fois en a
un.

**Axe secondaire : le sous-système.** À l'intérieur d'un jalon, les items sont
groupés par domaine, pour qu'un lot touche un jeu de fichiers cohérent et
n'exige qu'une baseline de non-régression plutôt que plusieurs.

### Ce qui a changé depuis la révision du 2026-08-30

**Le découpage par version a inversé deux jalons.** La révision précédente
ordonnait *avant `MIS-6`* (21 items), *avant `PHY-2`* (3 items), *avant
`MIS-10`/`MIS-11`* (7 items) — dans cet ordre, parce que le rendez-vous était la
phase 5 et le réalisme la phase 6. Les deux ont échangé leur place : le gros lot
de 21 items est maintenant le **dernier**, et les trois arbitrages
atmosphériques les **premiers**. Rien dans les fiches n'a changé ; leur ordre,
si.

**Le hors-jalon a presque disparu**, et c'est délibéré. `H-RND` et `H-UI`
étaient « piochables » faute de propriétaire ; v3 leur en donne un — la version
dont la maturité est le sujet. `H-DEC`, deux questions produit qui avaient perdu
leur jalon, est absorbé par `UI-8`, qui **est** le fichier de préférences dont
elles débattent. Un item qui ne bloque rien peut rester sans jalon ; deux
questions sans jalon se re-posent indéfiniment.

**Cinq fiches se sont fermées, deux se sont ouvertes.**

| Fiche | Mouvement |
|---|---|
| `BUG-6`, `BUG-9`, `BUG-15` | **Corrigées le 2026-08-31** (lot `J0-A`) |
| `REL-14`, `REL-23` | **Traitées le 2026-08-31** — les deux vols de clôture jamais lancés ont été exécutés, verts tous les deux |
| `BUG-19` | **Ouverte le 2026-09-02** — rotation propre des planètes externes aliasée par le pas de la fenêtre glissante ; Neptune à 4,1 % du taux vrai, Saturne et Uranus à l'envers. Cause racine établie |
| `BUG-20` | **Ouverte le 2026-09-02** — plan des anneaux désaligné : Saturne 13,51°, Uranus 9,93° hors du plan équatorial de leur propre globe. **Hors de portée du code** |

Le corpus compte donc **68 fiches, dont 63 ouvertes**.

**`BUG-20` révèle un motif, et il a maintenant un identifiant.** Quatre fiches
sont bloquées non par du code mais par des maillages absents : `BUG-20`
(anneaux à ré-exporter), `DT-12` (Ariane 6), et les besoins d'assets de `PHY-5`
et `PHY-6`. La révision précédente les traitait séparément, dont une comme
« bloquée, rien à planifier ». Elles sont désormais regroupées sous `AST-1` en
[v2](02-roadmap-v2.md), au motif qu'un approvisionnement se planifie — en délai,
pas en jours de travail.

### La leçon de la révision précédente, toujours valable

Une re-vérification de 9 fiches contre le code le 2026-08-30 en avait trouvé
**4 périmées ou déplacées**. **Les registres sont bâtis sur des documents de
conception, qui décrivent l'état au moment de leur lot et non l'état du code.**
D'où `J0-D`, toujours à faire, et d'où le fait que ce document range les fiches
sans les croire sur parole.

Cette révision-ci en ajoute un exemple : la fiche `PHY-5` affirmait que
`src/main/resources/models/` *« n'est pas versionné »*. `git ls-files` y compte
**54 fichiers suivis**, et `.gitignore` ne mentionne ni `models` ni `resources`.

---

## 2. Vue d'ensemble

| Jalon | Déclencheur | Items | Contenu en une ligne |
|---|---|:-:|---|
| [**`J-1.1`**](#3-j-11--rendre-le-dépôt-mesurable) | Maintenant | 4 lots | Instrument au vert, outillage bloquant, registres vérifiés |
| [**`J-v2`**](#4-j-v2--avant-et-pendant-la-v2) | Avant `PHY-2` | 6 | Arbitrages atmosphériques, garde de rentrée, assets, ruban |
| [**`J-v3`**](#5-j-v3--pendant-la-v3) | Avec `MIS-11` et l'UI | 18 | Lunaire, confort et couverture UI, passe de rendu |
| [**`J-v4`**](#6-j-v4--avant-et-pendant-mis-6) | Avant `MIS-6` | 21 | Trois rangs : 5 bloquants, 6 préalables, 10 d'accompagnement |
| [hors jalon](#7-ce-qui-reste-hors-jalon) | — | 1 | `DT-10`, à ne pas toucher |

**Le chemin critique est court, et il est au début.** Quatre lots séparent le
dépôt de l'ouverture de v2. Le gros morceau — les 21 items de `J-v4` — n'arrive
qu'en dernier, ce qui laisse trois versions pour le grignoter au lieu de le
subir d'un bloc.

---

## 3. `J-1.1` — Rendre le dépôt mesurable

> **Aucun item de ce jalon ne touche la chaîne de trajectoire.** C'est ce qui le
> rend intégrable n'importe quand, y compris au milieu d'autre chose.

C'est le contenu de la ligne **1.1.X** de [v1](01-roadmap-v1.md) §4.

### `J0-A` — Instrument au vert · **fait le 2026-08-31**

`BUG-9` (`ParkingCoastStageTest` asserte la sémantique d'avant `MIS-4 / L6`),
`BUG-6` (la fixture `PolarCoverageTest` vole le plane trim hors de son
enveloppe — coût réel mesuré à **141 kg**, pas 10 349), `BUG-15` (log `ERROR`
trompeur de `DepletionGuard` sur un rejet correct) : corrigés. `REL-14`
(`LunarOrbitFlightTest`) et `REL-23` (`ScenarioReplayTest` et l'essai manuel
d'`UI-3`) : lancés, verts.

**C'était le premier lot parce que tout le reste s'appuie dessus.** Un lot de
robustesse qui démarre avec un test rouge et une fixture fausse ne peut rien
prouver de ce qu'il corrige.

### `J0-B` — Outillage · sous-système : build · **à moitié fait**

`DT-1` a été requalifié : l'analyseur **est** branché, il ne servait simplement
à rien. Le lot a curé les règles — les quatre familles de bruit désactivées, dont
les deux qui contredisent `CLAUDE.md` (`NullAssignment`, que la règle `Optional`
impose ; `AvoidFieldNameMatchingMethodName`, qu'un record produit par
construction) — et il est commité (`960f168`, tag `v1.1.0`).

**Ce qui manque est le troisième temps, et c'est celui qui compte :**
`build.gradle:113` porte toujours `pmd { ignoreFailures = true }`. Tant qu'il y
est, l'analyseur décore le build au lieu de le casser.

> Un `ignoreFailures = false` posé **avant** la curation aurait cassé le build
> sur les records et sur la règle `Optional`. L'ordre n'était pas négociable ; il
> a été tenu, il reste à le conclure.

**C'est le seul item du corpus qui empêche les autres de revenir.** Le dépôt a
grossi de 65 % sans garde-fou mécanique, et `DT-8` a gagné une copie pendant que
le document qui la proscrit était déjà écrit.

### `J0-C` — Nettoyages · sous-système : divers, guidé par `J0-B`

`DT-4` (singleton `OrbitLabApplication.app`, un seul appelant), `DT-6` (trois
sites d'exception large), `DT-8` (cinq sites de langue), `DT-9`
(`nearOrbitLayer` + les 9 `UnusedPrivateField` trouvés par PMD), `DT-11` partiel
(les noms d'étapes en constantes — `"Coasting"`, `"S2 separation"` — qui sont les
seuls littéraux dupliqués à porter un vrai risque d'appariement silencieux).

**Après `J0-B` et pas avant** : l'outil curé en trouve une partie tout seul, et
surtout il interdit leur retour. C'est le §6.1 de `dette-technique.md` appliqué à
la lettre.

### `J0-D` — Vérification des registres · aucun code

Re-vérifier les 68 fiches contre le code. Parallélisable avec tout le reste, et à
faire au fil de l'eau plutôt qu'en bloc.

**Ce que la passe doit produire** : pour chaque fiche, soit une confirmation
datée, soit une correction, soit une clôture. Sans quoi les jalons de v2 partent
sur un corpus dont ~40 % est douteux — c'est le taux mesuré, pas une crainte.

---

## 4. `J-v2` — Avant et pendant la v2

> Ce jalon était le plus petit du document précédent (3 items, avant `PHY-2`).
> Il est passé **en tête** avec l'inversion des versions, et a gagné trois items
> qui étaient ailleurs.

### Avant `PHY-2` — trois arbitrages, pas trois corrections

Ce n'est pas du travail de code : c'est ce qu'il faut avoir **tranché** avant
que `PHY-2` calibre quoi que ce soit, sous peine de figer l'erreur dans le
recalibrage.

| Item | Ce qu'il faut décider |
|---|---|
| `DT-13` | Les Isp du catalogue absorbent déjà **408 m/s** (Falcon Heavy S1) et **671 m/s** (Ariane 62 S1) de traînée implicite — au-dessus des 100-300 m/s que la traînée réelle est censée coûter. Allumer l'une par-dessus l'autre **double-compte** |
| `DT-14` | 22,6 % d'écart entre Harris-Priester et NRLMSISE-00, tous deux déjà codés. Choisir la référence avant de calibrer dessus, pas pendant |
| `DT-15` | `Cd = 2,2` est déclaré valide au-dessus de 70 km ; le seul profil réel mesuré allume S2 à **58 km** |

Tous trois vivent dans le même sous-système — modèle atmosphérique et catalogue
de lanceurs — et se tranchent en une séance sur les mesures déjà disponibles
dans [`atmosphere/05-conception-L2.md`](../atmosphere/05-conception-L2.md).

### Avec `PHY-2`

`REL-22` — la restauration d'un scénario dont l'atmosphère n'est pas `NONE` est
**incorrigible avant** que `PHY-2` existe : rien ne peut remonter un modèle qui
n'existe pas encore. Versée à son périmètre.

### Avec `MIS-10`, et en premier

`BUG-10` — `ReentryGuard` est **inopérant** en présence de traînée :
l'intégrateur meurt à −9 km et −30 km, c'est-à-dire **au-dessus** de son
plancher de −50 km. Il a été descendu ici depuis le jalon atmosphérique de la
révision précédente, et le raisonnement tient toujours : il se déclenche avec la
traînée, mais il ne **sert** qu'à la rentrée. `MIS-10` **est** la rentrée.

### Avec `AST-1`

`DT-12` (maillage Ariane 6) et `BUG-20` (anneaux désalignés) quittent tous deux
le statut de « bloqué, rien à planifier ». Ils sont le contenu d'`AST-1`, avec
les besoins d'assets de `PHY-5` et `PHY-6`.

### Remonté depuis `J-v4`

`DT-17` — la performance du ruban n'a jamais été profilée. Il était rangé avant
`MIS-6` parce qu'un rendez-vous affiche **deux** trajectoires ; `PHY-5` en
affiche **une par objet largué**, ce qui arrive plus tôt et va plus loin.

---

## 5. `J-v3` — Pendant la v3

Dix-huit items, en trois familles qui correspondent aux trois axes de la
version.

### Lunaire — avec `MIS-11` · 6 items

| Item | Pourquoi ici |
|---|---|
| `BUG-12` | La bande morte ε de franchissement de SOI n'a jamais été calibrée sur une trajectoire réellement capturée. `MIS-11` sera le premier vol à franchir la frontière **dans le sens du retour** — jamais exercé. C'est le risque n° 1 de l'item, et il se traite avant lui |
| `REL-8` | Offset de visée TLI mono-dimensionnel, plancher de périlune mesuré à 132 km |
| `REL-9` | `ParkingCoastStage` ne gère pas un coast plus court que `ignitionLead` |
| `REL-10` | ToF / parking / angle de transfert jamais balayés conjointement ; bande de périlune testée sur un seul cas |
| `REL-12` | La timeline du wizard lunaire crible mais ne confirme jamais : un refus ne sort qu'à la validation ou en mission `FAILED` |
| `REL-16` | Le refus de masse sèche de charge utile lunaire n'a jamais de test automatisé |

### UI — avec `UI-6`, `UI-7`, `UI-8` · 8 items

`REL-24` (extraire l'interaction de `MissionTimelineWidget`, 842 lignes),
`REL-25` (aucun test sur la chaîne d'ancêtres du breadcrumb), `REL-26`
(breadcrumb jamais vérifié à l'écran), `REL-27` (descente vers les fils,
reportée en V2 par sa spec), `REL-15` (horizon GEO sous-estimé d'environ 7 %),
`REL-20` (horizon MEO à 48 révolutions), plus le reste de `DT-5`
(`OrbitCameraAppState`, `StepParameters`), qui se traite ici ou à la trace.

**`REL-28` et `REL-29` sont absorbés par `UI-8`.** Ce ne sont pas du travail, ce
sont deux questions — auto-optimisation après création, et persistance des
bascules d'affichage. Toutes deux avaient vu passer leur jalon prévu ; `REL-29`
devait être tranchée « au moment d'`UI-3` », clos depuis le 2026-08-21. `UI-8`
leur en donne un qui ne peut plus glisser, parce qu'il **est** le fichier dont
elles débattent.

### Rendu — la passe `H-RND` · 9 items

`BUG-1` (jitter Pluton), `BUG-2` (sauts de skybox), `BUG-5` (pop du modèle au
changement de focus), `REL-1` (raccord terminal du ruban), `REL-2`
(`MUTING_STEP`), `REL-3` (fondu alpha et largeurs), `REL-4` (tone mapping),
`REL-5` (pénombre du vaisseau), `REL-6` (éclipse totale contre annulaire).

**C'est le lot le plus cohérent du corpus** : un seul sous-système, aucune
dépendance croisée, et **la seule passe dont le résultat se voit à l'écran**.
Bon candidat juste après `MIS-11`, où rien n'est visible pendant des semaines.

`REL-6` demande d'abord une **observation** et non du code : regarder une
éclipse annulaire. `BUG-3` a quitté ce lot — ses cinq lots sont implémentés
(`82ba2ff`) et sa validation à l'écran est en `J-1.1`. `BUG-19`, découvert au
même moment et dans le même sous-système, est également en `J-1.1` : sa cause
racine est établie et son ampleur mesurée, ce qui en fait une correction et non
une passe.

---

## 6. `J-v4` — Avant et pendant `MIS-6`

> `MIS-6` est le **seul item ◆5** du corpus, et le seul XL. C'est aussi le
> premier chantier dont le sujet même est le **timing** d'une trajectoire — ce
> qui change la sévérité de plusieurs items qui dormaient.

Vingt-et-un items, en trois rangs. Le rang dit **quand**, le sous-lot dit
**avec quoi**.

### Rang bloquant · 5 items

À faire **avant d'ouvrir `MIS-6`**. Sans eux, le chantier travaille sur un
instrument qui ment.

| Item | Sous-système | Pourquoi bloquant |
|---|---|---|
| `BUG-7` | Instrument | Les gates de non-régression sont vertes ou rouges **selon le filtre `--tests`**. On ne lance pas un ◆5 avec un filet dont le verdict dépend de l'ordre d'exécution |
| `BUG-11` | Optimiseur | L'optimiseur saute les coasts que le vol rejoue — **~2 770 s d'écart**, près d'une demi-orbite. Sur un chantier de phasing, c'est un défaut de premier ordre, plus une curiosité de baseline |
| `DT-7` | Couverture | `planner` construit le `MissionPlan` que `MIS-6` va étendre : 7 classes, 1 test |
| `REL-17` | Couverture | `T1b` (inclinaison après insertion complète) n'a jamais été écrit ; seul `MeoMissionTest` en donne un équivalent partiel |
| `REL-18` | Instrument | L'étalement de ~19 km de l'ensemble acceptable du CMA-ES est le **plancher de bruit de toute comparaison de références**. À caractériser — pas nécessairement à corriger |

### Rang préalable · 6 items

Moins chers **avant** que pendant. Rien n'interdit de les faire après, mais
chacun coûtera davantage.

| Item | Sous-lot | Pourquoi maintenant |
|---|---|---|
| `DT-2` | Stages | La version annonce **au moins trois nouveaux stages** (phasing, transfert Lambert, approche terminale). Livrer le *template method* après, c'est payer la copie une quatrième fois — et l'invariant de pas d'intégration de `CLAUDE.md` est déjà réimplémenté six fois sans garantie mécanique d'accord |
| `BUG-17` | Optimiseur | `acceptableCost` mal calé depuis le terme I7 : aucun arrêt « Target reached » ne peut jouer, donc **chaque mesure coûte plus cher** que nécessaire pendant tout le chantier |
| `BUG-8` | Wizard | `MIS-6` étend le wizard (cible TLE) — réparer avant d'ajouter |
| `BUG-13` | Fenêtres | Idem, sur la recherche de fenêtre que `MIS-6` réutilise |
| `BUG-14` | Fenêtres | Deux portées de recherche divergentes (`SEARCH_SPAN` en dur contre `recurrence()`) |
| `BUG-18` | Scénario | `UI-3` est **l'outillage de développement** de cette version : mettre au point un rendez-vous sans pouvoir voir pourquoi un scénario a été rejeté coûte cher tous les jours |

### Rang accompagnement · 10 items

De l'amélioration, pas du déblocage. À intercaler **pendant** `MIS-6`, un lot à
la fois.

| Sous-lot | Items | Note |
|---|---|---|
| Optimiseur | `BUG-16`, `REL-30`, `REL-31`, `REL-32`, `DT-3`, `DT-5` (partiel) | `REL-30` et `REL-32` se traitent **ensemble** : `ABS_ERR_SCALE` est le multiplicateur de l'hypothèse osculateur/moyen |
| Stages | `DT-16` | `nRev = 0` figé partout — à remonter en préalable si le phasing réclame du multi-révolution |
| Wizard | `REL-19`, `REL-21` | `REL-21` (annulation d'un calcul) gagne en valeur avec des calculs de rendez-vous longs |

> **`BUG-16` doit être isolé.** Déplacer `t1Max` renormalise toute la recherche
> CMA-ES et perturbe des missions qui ne saturent pas — les bornes ne sont pas
> des contraintes. C'est le pire item du corpus à traiter au milieu d'un autre
> chantier ; il lui faut sa propre re-mesure de références.

**`DT-5` mérite une mention à part : c'est l'item qui s'est le plus dégradé**
pendant que personne ne le regardait.

| Classe | Photo du 2026-08-10 | Dernier comptage |
|---|---|---|
| `TransferProblem` | 427 | **889** (×2,1) |
| `CMAESTrajectoryOptimizer` | 398 | **706** |
| `MultiStageLoadOptimizer` | 336 | **586** |
| `OrbitCameraAppState` | 324 | **515** |

Les trois premières sont exactement les classes que ce jalon rouvre. La
quatrième part en `J-v3`, avec l'UI.

### Deux fiches versées au périmètre de `MIS-6`

`REL-7` (le near viewport ne montre jamais deux corps 3D — c'est la
fonctionnalité, pas son préalable ; devenu l'item `RND-8`) et `REL-13` (aucun
CMA-ES sur l'injection translunaire, reporté ici par
`lunar-flyby/01-decoupage.md` §6 pt 5).

**Ce versement ne réduit `MIS-6` d'aucune ligne de travail** — il déplace
seulement où ce travail est compté. C'est délibéré : un chantier dont le
découpage ignore ses propres préalables les découvre en cours de route.

---

## 7. Ce qui reste hors jalon

Une seule fiche, et c'est un refus argumenté : **`DT-10`** (commentaires
redondants). Le document conclut lui-même qu'**il ne faut pas y toucher** — la
densité de commentaires est un actif dans ce dépôt, pas une dette.

`BUG-4` n'est pas ici non plus : il est **promu** en item de roadmap
fonctionnelle (`UI-7`, [v3](03-roadmap-v3.md)), selon la convention de
`docs/bugs.md` — un bug qui s'avère être un chantier devient un item.

---

## 8. Entretien de ce document

Ce document est un **ordre**, pas un état. Il se périme moins vite que les
registres qu'il ordonne, mais il se périme :

- quand une version est livrée, son jalon disparaît et ses reliquats éventuels
  se rattachent ailleurs ;
- quand `J0-D` corrige une fiche, son rang peut changer — une fiche périmée n'a
  plus de jalon ;
- quand un item d'accompagnement se révèle bloquant en cours de chantier, il
  remonte de rang et le document le dit.

**Ce qu'il ne faut pas faire** : recopier ici le contenu d'une fiche. Les trois
registres sont la source ; ce document n'en est que l'ordonnancement, et les
sections « dette et robustesse » des roadmaps de version n'en sont que le
rappel. Une divergence se tranche **toujours** en faveur du registre.
