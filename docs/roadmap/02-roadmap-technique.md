# Roadmap technique — passe de robustesse · 2026-08-30

Ce document ordonne les **66 fiches ouvertes** des trois registres
([`bugs.md`](../bugs.md), [`dette-technique.md`](../dette-technique.md),
[`reliquats.md`](../reliquats.md)) en lots intégrables entre les features de
[`01-roadmap.md`](01-roadmap.md).

**Ce qu'il n'est pas.** Il ne décrit aucune fiche : chacune vit dans son
registre, avec son mécanisme, ses mesures et ce qui reste non vérifié. Ce
document ne dit que **quand** traiter un item et **pourquoi à ce moment-là**.
Un item qui change d'état se met à jour dans son registre, pas ici.

**Comment le lire.** Le §2 donne la vue d'ensemble ; les §3 à §7 détaillent un
jalon chacun. Si vous ne lisez qu'une chose, lisez le §2 puis le §3 — `J0` est
le seul lot dont rien ne dépend et dont tout le reste dépend.

---

## 1. Méthode et règle de rattachement

**Axe principal : le jalon bloquant.** Un item va au jalon de **la première
phase qui le rend coûteux**, pas à celle qui le rend visible. Un défaut de
rendu visible depuis six mois mais qui ne bloque rien n'a pas de jalon ; une
duplication invisible que la prochaine phase va payer une troisième fois en a
un.

**Axe secondaire : le sous-système.** À l'intérieur d'un jalon, les items sont
groupés par domaine, pour qu'un lot touche un jeu de fichiers cohérent et
n'exige qu'une baseline de non-régression plutôt que plusieurs.

**Ce qui ne bloque jamais rien sort des jalons** plutôt que d'être glissé en
fin de liste — même convention que `RND-5` et `UI-6` dans la roadmap
fonctionnelle. Le §7 leur donne des lots piochables, ce qui n'est pas la même
chose que planifiés.

### Le fait qui a motivé ce document

Une re-vérification de 9 fiches contre le code le 2026-08-30 en a trouvé
**4 périmées ou déplacées** :

| Fiche | Ce que le registre dit | Ce que le code dit |
|---|---|---|
| `DT-1` | « `build.gradle` déclare `plugins { id 'java' }` et rien d'autre » | **Faux** — spotless, PMD et jacoco sont branchés (§3, `J0-B`) |
| `DT-9` | Deux sites de code mort | `publishOpenWizard()` est appelé (`MissionDisplayPanelAppState:201`) ; seul `nearOrbitLayer` reste mort |
| `DT-8` | 4 sites de violation de langue | **5** — une troisième copie de `mission under-dotée` est apparue (`MinimizedLoadPlanner:154`) |
| `DT-11` | 3 TODO anonymes | 1 des 3 porte maintenant `TODO(DT-11)` |

S'y ajoute `REL-11`, écrit « non traité » le 2026-08-30 alors qu'il était
corrigé depuis le 2026-08-27. **Les registres sont bâtis sur des documents de
conception, qui décrivent l'état au moment de leur lot et non l'état du code.**
D'où `J0-D`, et d'où le fait que ce document range les fiches sans les croire
sur parole.

### Le dépôt a changé d'échelle depuis la photo de `dette-technique.md`

| Grandeur | Photo du 2026-08-10 | Mesuré le 2026-08-30 | Écart |
|---|---|---|---|
| Classes `src/main` | 266 | **368** | +38 % |
| SLOC `src/main` | 32 920 | **54 371** | +65 % |
| SLOC `src/test` | 12 738 | **29 689** | +133 % |
| Ratio test/main | 0,39 | **0,55** | en hausse |

Le ratio de test s'est **amélioré** pendant que le volume doublait : la
discipline tient. Ce qui n'a pas tenu, ce sont les items que rien ne
surveillait — voir `DT-5` au §4.

---

## 2. Vue d'ensemble

| Jalon | Déclencheur | Items | Contenu en une ligne |
|---|---|:-:|---|
| [**`J0`**](#3-j0--rendre-le-dépôt-mesurable) | Maintenant | 11 | Instrument au vert, outillage bloquant, registres vérifiés |
| [**`J1`**](#4-j1--avant-mis-6-phase-5) | Avant `MIS-6` | 21 | Trois rangs : 5 bloquants, 6 préalables, 10 d'accompagnement |
| [**`J2`**](#5-j2--avant-phy-2-phase-6) | Avant `PHY-2` | 3 | Trois arbitrages du modèle atmosphérique et du catalogue |
| [**`J3`**](#6-j3--avant-mis-10--mis-11-phase-7) | Avant `MIS-10`/`MIS-11` | 7 | Rentrée et lunaire |
| [**hors jalon**](#7-hors-jalon--trois-lots-piochables) | — | 18 | `H-RND` (10), `H-UI` (6), `H-DEC` (2) |
| [sortis du périmètre](#8-ce-qui-sort-du-périmètre) | — | 6 | 3 versés à d'autres chantiers, 1 actif, 1 suivi ailleurs, 1 bloqué |

**Le chemin critique est court.** Cinq items séparent le dépôt de l'ouverture
de `MIS-6` (`J1!`), plus les onze de `J0`. Tout le reste s'intercale.

---

## 3. `J0` — Rendre le dépôt mesurable

> **Aucun item de `J0` ne touche la chaîne de trajectoire.** C'est ce qui le
> rend intégrable n'importe quand, y compris au milieu d'autre chose.

Quatre sous-lots, dans l'ordre d'exécution.

### `J0-A` — Instrument au vert · sous-système : tests et fixtures

| Item | Ce qu'il faut faire |
|---|---|
| `BUG-9` | `ParkingCoastStageTest:75` asserte la sémantique d'avant `MIS-4 / L6` — comparer à la durée jusqu'à l'**allumage**, pas à `coastDuration()` |
| `BUG-6` | La fixture `PolarCoverageTest` vole le plane trim hors de son enveloppe et affiche 10 349 kg au lieu d'un coût de mission réel |
| `BUG-15` | Le log `ERROR … upstream mass accounting is wrong` tombe sur un rejet correct et fait chercher un bug qui n'existe pas |
| `REL-14` | `LunarOrbitFlightTest` (`orbitlab.slowTests`) — le vol de clôture de `MIS-5`, jamais lancé |
| `REL-23` | `ScenarioReplayTest` et l'essai manuel d'`UI-3`, jamais lancés |

**En premier parce que tout le reste s'appuie dessus.** Un lot de robustesse
qui démarre avec un test rouge et une fixture fausse ne peut rien prouver de
ce qu'il corrige.

### `J0-B` — Outillage · sous-système : build

`DT-1` est requalifié : l'analyseur **est** branché, il ne sert simplement à
rien. `pmd { ignoreFailures = true }`, et le rapport compte **610
violations** — dont plus de la moitié sanctionnent les conventions que
`CLAUDE.md` impose :

| Règle | Occ. | Verdict |
|---|:-:|---|
| `GuardLogStatement` | 141 | Bruit — Log4j2 paramétré en `{}` n'a pas besoin de garde |
| `AvoidLiteralsInIfCondition` | 109 | Bruit — ce sont des seuils physiques |
| `NullAssignment` | 89 | **Contredit `CLAUDE.md`** — la règle `Optional` impose des champs nullables |
| `AvoidFieldNameMatchingMethodName` | 74 | **Contredit `CLAUDE.md`** — un record a `x()` pour champ `x` |
| `UseVarargs`, `LooseCoupling` | 80 | À trancher |
| `AvoidDuplicateLiterals` | 17 | **C'est `DT-11`** — l'outil le trouve seul |
| `PreserveStackTrace` | 17 | À trier — famille de `DT-6` |
| `CompareObjectsWithEquals` | 11 | **À trier sérieusement** — 8 fichiers dont `ArcTransition` et `TrajectoryArc` |
| `UnusedPrivateField` | 9 | **Trouvaille** — 3 fichiers, du code mort que `DT-9` ignorait |

**Le travail, dans cet ordre :** désactiver les quatre règles de bruit ; trier
le résidu (~30 findings réels, dont les 9 `UnusedPrivateField` et les 11
`CompareObjectsWithEquals` qui peuvent contenir de vrais défauts) ; puis
`ignoreFailures = false`.

**C'est le seul item du corpus qui empêche les autres de revenir.** Le dépôt a
grossi de 65 % sans garde-fou mécanique, et `DT-8` a gagné une copie pendant
que le document qui la proscrit était déjà écrit.

> Un `ignoreFailures = false` posé **avant** la curation casserait le build sur
> les records et sur la règle `Optional`. L'ordre n'est pas négociable.

### `J0-C` — Nettoyages · sous-système : divers, guidé par `J0-B`

`DT-4` (singleton `OrbitLabApplication.app`, un seul appelant), `DT-6` (trois
sites d'exception large), `DT-8` (cinq sites de langue), `DT-9`
(`nearOrbitLayer` + les 9 `UnusedPrivateField` trouvés par PMD), `DT-11`
partiel (les noms d'étapes en constantes — `"Coasting"`, `"S2 separation"` —
qui sont les seuls littéraux dupliqués à porter un vrai risque d'appariement
silencieux).

**Après `J0-B` et pas avant** : l'outil curé en trouve une partie tout seul, et
surtout il interdit leur retour. C'est le §6.1 de `dette-technique.md`
appliqué à la lettre.

### `J0-D` — Vérification des registres · aucun code

Re-vérifier les 66 fiches contre le code. Parallélisable avec tout le reste,
et à faire au fil de l'eau plutôt qu'en bloc.

**Ce que la passe doit produire** : pour chaque fiche, soit une confirmation
datée, soit une correction, soit une clôture. Sans quoi le prochain lot qui
s'appuie sur ce corpus repart avec ~40 % de fiches douteuses.

---

## 4. `J1` — Avant `MIS-6` (phase 5)

> `MIS-6` est le **seul item ◆5** de la roadmap fonctionnelle, et le seul XL.
> C'est aussi le premier chantier dont le sujet même est le **timing** d'une
> trajectoire — ce qui change la sévérité de plusieurs items qui dormaient.

Vingt-et-un items, en trois rangs. Le rang dit **quand**, le sous-lot dit
**avec quoi**.

### Rang `J1!` — bloquant · 5 items

À faire **avant d'ouvrir `MIS-6`**. Sans eux, le chantier travaille sur un
instrument qui ment.

| Item | Sous-système | Pourquoi bloquant |
|---|---|---|
| `BUG-7` | Instrument | Les gates de non-régression sont verts ou rouges **selon le filtre `--tests`**. On ne lance pas un ◆5 avec un filet dont le verdict dépend de l'ordre d'exécution |
| `BUG-11` | Optimiseur | L'optimiseur saute les coasts que le vol rejoue — **~2 770 s d'écart**, près d'une demi-orbite. Sur un chantier de phasing, c'est un défaut de premier ordre, plus une curiosité de baseline |
| `DT-7` | Couverture | `planner` construit le `MissionPlan` que `MIS-6` va étendre : 7 classes, 1 test |
| `REL-17` | Couverture | `T1b` (inclinaison après insertion complète) n'a jamais été écrit ; seul `MeoMissionTest` en donne un équivalent partiel |
| `REL-18` | Instrument | L'étalement de ~19 km de l'ensemble acceptable du CMA-ES est le **plancher de bruit de toute comparaison de références**. À caractériser — pas nécessairement à corriger |

### Rang `J1` — préalable · 6 items

Moins cher **avant** que pendant. Rien n'interdit de les faire après, mais
chacun coûtera davantage.

| Item | Sous-lot | Pourquoi maintenant |
|---|---|---|
| `DT-2` | `J1-C` Stages | La roadmap annonce **deux nouveaux stages** pour `MIS-6`. Livrer le *template method* après, c'est payer la copie une troisième fois — et l'invariant de pas d'intégration de `CLAUDE.md` est déjà réimplémenté six fois sans garantie mécanique d'accord |
| `BUG-17` | `J1-B` Optimiseur | `acceptableCost` mal calé depuis le terme I7 : aucun arrêt « Target reached » ne peut jouer, donc **chaque mesure coûte plus cher** que nécessaire pendant tout le chantier |
| `BUG-8` | `J1-D` Wizard | `MIS-6` étend le wizard (cible TLE) — réparer avant d'ajouter |
| `BUG-13` | `J1-D` Fenêtres | Idem, sur la recherche de fenêtre que `MIS-6` réutilise |
| `BUG-14` | `J1-D` Fenêtres | Deux portées de recherche divergentes (`SEARCH_SPAN` en dur contre `recurrence()`) |
| `BUG-18` | `J1-D` Scénario | `UI-3` est **l'outillage de développement** de `MIS-6` : mettre au point un rendezvous sans pouvoir voir pourquoi un scénario a été rejeté coûte cher tous les jours |

### Rang `J1~` — accompagnement · 10 items

De l'amélioration, pas du déblocage. À intercaler **pendant** `MIS-6`, un lot
à la fois.

| Sous-lot | Items | Note |
|---|---|---|
| `J1-B` Optimiseur | `BUG-16`, `REL-30`, `REL-31`, `REL-32`, `DT-3`, `DT-5` (partiel) | `REL-30` et `REL-32` se traitent **ensemble** : `ABS_ERR_SCALE` est le multiplicateur de l'hypothèse osculateur/moyen |
| `J1-C` Stages | `DT-16` | `nRev = 0` figé partout — à remonter en `J1` si le phasing réclame du multi-révolution |
| `J1-D` Wizard | `REL-19`, `REL-21` | `REL-21` (annulation d'un calcul) gagne en valeur avec des calculs de rendezvous longs |
| `J1-E` Rendu | `DT-17` | La performance du ruban n'a jamais été profilée, et `MIS-6` affiche **deux** trajectoires simultanées |

> **`BUG-16` doit être isolé.** Déplacer `t1Max` renormalise toute la recherche
> CMA-ES et perturbe des missions qui ne saturent pas — les bornes ne sont pas
> des contraintes. C'est le pire item du corpus à traiter au milieu d'un autre
> chantier ; il lui faut sa propre re-mesure de références.

**`DT-5` mérite une mention à part : c'est l'item qui s'est le plus dégradé**
pendant que personne ne le regardait.

| Classe | Photo du 2026-08-10 | Aujourd'hui |
|---|---|---|
| `TransferProblem` | 427 | **889** (×2,1) |
| `CMAESTrajectoryOptimizer` | 398 | **706** |
| `MultiStageLoadOptimizer` | 336 | **586** |
| `OrbitCameraAppState` | 324 | **515** |

Les trois premières sont exactement les classes que `J1-B` va rouvrir. Le
reste de `DT-5` part en `H-UI` (§7).

---

## 5. `J2` — Avant `PHY-2` (phase 6)

**Trois arbitrages, pas trois corrections.** `J2` n'est pas du travail de
code : c'est ce qu'il faut avoir **tranché** avant que `PHY-2` calibre quoi que
ce soit, sous peine de figer l'erreur dans le recalibrage.

| Item | Ce qu'il faut décider |
|---|---|
| `DT-13` | Les Isp du catalogue absorbent déjà **408 m/s** (Falcon Heavy S1) et **671 m/s** (Ariane 62 S1) de traînée implicite — au-dessus des 100-300 m/s que la traînée réelle est censée coûter. Allumer l'une par-dessus l'autre **double-compte** |
| `DT-14` | 22,6 % d'écart entre Harris-Priester et NRLMSISE-00, tous deux déjà codés. Choisir la référence avant de calibrer dessus, pas pendant |
| `DT-15` | `Cd = 2,2` est déclaré valide au-dessus de 70 km ; le seul profil réel mesuré allume S2 à **58 km** |

Tous trois vivent dans le même sous-système — modèle atmosphérique et
catalogue de lanceurs — et se tranchent en une séance sur les mesures déjà
disponibles dans [`atmosphere/05-conception-L2.md`](../atmosphere/05-conception-L2.md).

> **`REL-22` a quitté ce jalon.** La restauration d'un scénario dont
> l'atmosphère n'est pas `NONE` est **incorrigible avant** `PHY-2` : rien ne
> peut remonter un modèle qui n'existe pas encore. Il est versé au périmètre
> de `PHY-2` (§8).

---

## 6. `J3` — Avant `MIS-10` / `MIS-11` (phase 7)

Sept items, tous de rentrée ou de lunaire.

| Item | Sous-système | Pourquoi ce jalon |
|---|---|---|
| `BUG-10` | Rentrée | `ReentryGuard` est inopérant sous traînée — l'intégrateur meurt à −9/−30 km, **au-dessus** de son plancher de −50 km. `MIS-10` **est** la rentrée : c'est là que le garde doit marcher |
| `BUG-12` | Multi-corps | La bande morte ε de franchissement de SOI n'a jamais été calibrée sur une trajectoire réellement capturée. `MIS-11` (retour) est le premier vol à la stresser |
| `REL-8` | Lunaire | Offset de visée TLI mono-dimensionnel, plancher de périlune mesuré à 132 km |
| `REL-9` | Lunaire | `ParkingCoastStage` ne gère pas un coast plus court que `ignitionLead` |
| `REL-10` | Lunaire | ToF / parking / angle de transfert jamais balayés conjointement ; bande de périlune testée sur un seul cas |
| `REL-12` | Wizard lunaire | La timeline crible mais ne confirme jamais : un refus ne sort qu'à la validation ou en mission `FAILED` |
| `REL-16` | Couverture | Le refus de masse sèche de charge utile lunaire n'a jamais de test automatisé |

**`BUG-10` était initialement classé en `J2`** et a été descendu ici : il se
déclenche avec la traînée, mais il ne **sert** qu'à la rentrée. Le corriger au
moment où `PHY-2` allume la traînée, sans mission de rentrée pour le valider,
reviendrait à écrire un garde qu'aucun vol n'exerce.

---

## 7. Hors jalon — trois lots piochables

Rien ici ne bloque quoi que ce soit. Ces trois lots existent pour être
**pris entre deux chantiers**, pas pour être planifiés.

### `H-RND` — Passe de rendu · 10 items

`BUG-1` (jitter Pluton), `BUG-2` (sauts de skybox), `BUG-3` (orientation des
modèles de planètes), `BUG-5` (pop du modèle au changement de focus), `REL-1`
(raccord terminal du ruban), `REL-2` (`MUTING_STEP`), `REL-3` (fondu alpha et
largeurs), `REL-4` (tone mapping), `REL-5` (pénombre du vaisseau), `REL-6`
(éclipse totale contre annulaire).

**C'est le lot le plus cohérent du hors-jalon** : un seul sous-système, aucune
dépendance croisée, et **la seule passe du corpus dont le résultat se voit à
l'écran**. Bon candidat juste après un chantier physique où rien n'est
visible.

Deux d'entre eux (`BUG-3`, `REL-6`) demandent d'abord une **observation**, pas
du code : établir la liste corps par corps, et regarder une éclipse annulaire.

### `H-UI` — Confort et couverture · 6 items

`REL-24` (extraire l'interaction de `MissionTimelineWidget`, 842 lignes),
`REL-25` (aucun test sur la chaîne d'ancêtres du breadcrumb), `REL-26`
(breadcrumb jamais vérifié à l'écran), `REL-27` (descente vers les fils,
reportée en V2), `REL-15` (horizon GEO sous-estimé de ~7 %), `REL-20`
(horizon MEO à 48 révolutions).

Le reste de `DT-5` (`OrbitCameraAppState`, `StepParameters`) se traite ici ou
à la trace, au fil des passages.

### `H-DEC` — Décisions produit · 2 items

`REL-28` (auto-optimisation après création d'une mission) et `REL-29`
(persistance des bascules d'affichage). **Ce n'est pas du travail, ce sont
deux questions.** Toutes deux ont vu passer leur jalon prévu — `REL-29`
devait être tranchée « au moment d'`UI-3` », clos depuis le 2026-08-21.

Elles sont ici pour ne pas disparaître, pas pour être codées.

---

## 8. Ce qui sort du périmètre

Six fiches quittent la roadmap technique. Aucune n'est perdue : chacune est
rangée là où elle coûte.

| Fiche | Va où | Raison |
|---|---|---|
| `REL-7` | Périmètre de `MIS-6` | Le near viewport ne montre jamais deux corps 3D. Un rendezvous, c'est le vaisseau **et** sa cible à l'écran : c'est la fonctionnalité, pas son préalable. `multi-corps/08-conception-L6.md` §12.5 le lui renvoyait déjà nommément |
| `REL-13` | Périmètre de `MIS-6` | Aucun CMA-ES sur l'injection translunaire — reporté à `MIS-6` par `lunar-flyby/01-decoupage.md` §6 pt 5 |
| `REL-22` | Périmètre de `PHY-2` | Incorrigible avant que `PHY-2` existe (§5) |
| `DT-10` | Reste au registre | Commentaires redondants : le document conclut lui-même qu'**il ne faut pas y toucher**, la densité est un actif |
| `BUG-4` | Suivi comme `UI-7` | Déjà promu en item de roadmap fonctionnelle |
| `DT-12` | Bloqué | Mesh Ariane 6 : bloqué par un asset externe absent du dépôt, pas par du code. Rien à planifier tant qu'il n'existe pas |

**Ces trois versements réduisent `MIS-6` et `PHY-2` d'aucune ligne de travail**
— ils déplacent seulement où ce travail est compté. C'est délibéré : un
chantier dont le découpage ignore ses propres préalables les découvre en cours
de route.

---

## 9. Entretien de ce document

Ce document est un **ordre**, pas un état. Il se périme moins vite que les
registres qu'il ordonne, mais il se périme :

- quand une phase de [`01-roadmap.md`](01-roadmap.md) est livrée, son jalon
  disparaît et ses reliquats éventuels se rattachent ailleurs ;
- quand `J0-D` corrige une fiche, son rang peut changer — une fiche périmée
  n'a plus de jalon ;
- quand un item de `J1~` se révèle bloquant en cours de `MIS-6`, il remonte en
  `J1!` et le document le dit.

**Ce qu'il ne faut pas faire** : recopier ici le contenu d'une fiche. Les trois
registres sont la source ; ce document n'en est que l'ordonnancement. Une
divergence entre les deux se tranche **toujours** en faveur du registre.
