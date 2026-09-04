# Roadmap OrbitLab v1.X.X — le système solaire et les missions

> **Cette version est livrée.** Ses quatre phases sont soldées, la `v1.1.0` est
> taguée depuis le 2026-08-31 et la ligne de stabilisation **1.1.X est close à
> 1.1.1** depuis le 2026-09-03. Ce qui reste ouvert sous cet en-tête est une
> ligne neuve, **1.2.0** — la vue rapprochée et l'ombre des anneaux — **close à
> son tour le 2026-09-05**. Les deux sont au §4, et il ne reste rien d'ouvert
> sous cet en-tête.

Ce document remplace `docs/roadmap/01-roadmap.md`, qui portait sept phases pour
une version unique. Le découpage par version l'a scindé en quatre : la porte
d'entrée du dossier est désormais [`00-index.md`](00-index.md), et ce
document-ci ne couvre plus que **v1**.

**Comment le lire.** Le §3 est le compte rendu des quatre phases livrées et le
§4 porte les deux lignes de correction, dont la seule encore ouverte ; le §6 est
le recueil des fiches, à
ouvrir quand on veut savoir ce qu'un item a réellement livré et ce qu'il a
laissé derrière lui. Les fiches des items **non livrés** ont suivi leur version :
voir le §5.

---

## 1. État des lieux (ce qui a changé depuis la révision précédente)

**Livré et vérifié dans le code :**

| Chantier | Preuve |
|---|---|
| Seek timeline par saisie de date | `ui/timeline/components/ClockDisplay.java` |
| Étiquette de type dans le panel | `ui/mission/panel/MissionTypes.java` lit `MissionSpec.type()` |
| Filet d'erreur sur le changement de mode | `MissionEntry.setOptimizationType` |
| Action « Edit » du panel + préremplissage wizard | commit `6baff0d` |
| Confirmation avant suppression | commit `686b7e2` |
| `MissionId` (UUID) et registre de renderers centralisé | commits `cedda8e`, `1b1d7a6` |
| **Skybox étoilée** | `states/scene/SkyboxAppState.java`, cubemap sous `resources/textures/skybox/` |
| **Éclairage Lambert avec terminator** | `MatDefs/Light/WrapLighting.j3md` + `AssetFactory.applyLambert` |
| **MSAA 4×** | `OrbitLabApplication.java:58` — `settings.setSamples(4)` |
| **`RND-1` — artefacts de la vue spacecraft** (3 causes racines) | `FloatingOriginAppState`, `NearCameraSyncAppState.nearPlane`, `MissionTrajectoryRenderer.update` ; `NearFrameOriginTest`, `NearFrustumDepthTest`, `MissionTrajectoryOriginTest` |
| **`FX-1` — halo du Soleil** (couronne géométrique + bloom résiduel) | `engine/scene/body/CoronaView.java`, `MatDefs/Fx/Corona.*`, `states/fx/PostFxAppState.java`, `states/fx/SmoothBloomFilter.java`, `AssetFactory.applyGlow` |
| **`MIS-1` — deuxième lanceur** (Ariane 62 au catalogue + mesh choisi par lanceur) | `vehicle/catalog/Launchers.java` (commit `f9ea80c`), `engine/scene/spacecraft/LauncherAssets.java`, `MissionRenderer.modelPathFor` |
| **`UI-1` — vue détail mission** (orbite atteinte, écart à la cible, stages, erreur lisible) | `ui/mission/MissionTargetOrbit.java`, `ui/mission/MissionResultText.java`, `ui/mission/detail/`, `PanelFooter.addResultLine`, `MissionEntry.lastError` ; specs `mission-detail/01-vue-detail.md` |
| **`NAV-1` — transitions de caméra** (pivot, distance et orientation animés, entrées bloquées) | `states/camera/CameraTransitionAppState.java`, `CameraTransition.java`, `CameraOrientation.java`, `engine/CameraTransitionConfig.java`, `engine/Easing.java`, `app/view/TransitionTarget.java` ; `CameraTransitionTest`, `CameraOrientationTest` |
| **`UI-5` — surfaces, modalité et pile de renvoi `ESC`** (fenêtre non modale déplaçable, `ESC` renvoie la surface du dessus, `Quit` en entrée de menu) | `app/HudSurface.java`, `app/HudSurfaces.java`, `ui/UiLayers.java`, `ui/form/WindowDragHandler.java` ; `HudSurfacesTest`, `AppMenuModelTest`, `WindowDragHandlerTest` |
| **`NAV-2` — timeline indexée sur le temps** (capsule jumelle, barre de phases, marqueurs déclusterisés, graduations `T+`, seek au clic) | `ui/timeline/mission/`, `states/time/MissionTimelineAppState.java`, `states/time/MissionTimelineVisibility.java` ; `TimeAxisTest`, `TimeAxisTickTest`, `PhaseMarkerClusterTest`, `MissionTimelineVisibilityTest`, `SpeedStepperIndexTest` |
| **`NAV-3` — scrub continu** (drag sur la piste, seuil de 3 px, émission étranglée à 10 Hz, état de lecture restauré au lâcher) | `ui/timeline/mission/ScrubGesture.java` + câblage du listener de `MissionTimelineWidget` ; `ScrubGestureTest` |
| **`RND-4` — ribbon billboardé** (orbites et trajectoires en rubans face-caméra, largeur en pixels, fondu d'un pixel exact, expansion en vertex shader) | `MatDefs/Fx/Ribbon.j3md` + `.vert` / `.frag`, `engine/scene/RibbonMeshBuilder.java`, `OrbitLineFactory`, `MissionTrajectoryRenderer`, `AssetFactory.createRibbon` ; `RibbonMeshBuilderTest` |
| **`NAV-4` — breadcrumb** (bande haute permanente, chaîne d'ancêtres du corps focalisé, remontée en un clic) | `ui/breadcrumb/BreadcrumbWidget.java` + `BreadcrumbStyles.java`, `states/scene/BreadcrumbWidgetAppState.java`, `GuiGraph.getBreadcrumbNode`, `AppStyles.BREADCRUMB_BAND_HEIGHT_PX` / `HUD_TOP_OFFSET_PX` |
| **`MIS-7` — `EarthOrbitMission` paramétrable** (plan d'ascension commandé, SSO calculée, cinq profils au wizard) | `mission/operation/EarthOrbitMission.java` (ex-`LEOMission`), `LaunchPlane.java`, `NodeBranch.java`, `MissionSpec.EarthOrbit`, `Physics.sunSynchronousInclination`, `ui/mission/wizard/MissionProfile.java`, `step/params/EarthOrbitDynamicParameters.java` (commits `27f4a60`, `56eb362`) ; `EarthOrbitNonRegressionTest`, `AscentPlaneControlTest`, `SunSynchronousInclinationTest`, `SunSynchronousPrecessionTest`, `PolarCoverageTest`, `MeoMissionTest`, `MissionProfileTest`, `WizardPrefillTest` |
| **`MIS-2` — fenêtres de lancement** (balayage grossier + section dorée, trois échelles portées par le problème, timeline à deux échelles dans le wizard) | `simulation/mission/window/` (`LaunchWindowProblem`, `LaunchWindowSearch`, `LaunchWindowSolver`, `LaunchWindow`, `LaunchWindowCandidate`), `window/problem/` (`EarthLaunchWindowProblem`, `EarthLaunchWindowRequest`, `EarthLaunchWindowPlanner`, `TranslunarInjectionPlanWindowProblem`), `ui/mission/wizard/step/planning/` (`PlanningPage`, `PlanningModel`, `PlanningState`, `LaunchWindowTimeline`, `RaanEntry`, `ZoomScale`), `step/RefusedPage.java`, `step/LaunchDateProvenance.java` ; `LaunchWindowSolverTest`, `LaunchWindowSearchTest`, `EarthLaunchWindowProblemTest`, `EarthLaunchWindowPlannerTest`, `PlanningModelTest`, `RaanEntryTest`, `RefusedPageTest`, `ZoomScaleTest`, `LaunchDateProvenanceTest` |
| **`UI-2` — feedback de progression** (spinner géométrique, position dans la séquence, compteur d'évaluations, distinction file/calcul) | `simulation/mission/progress/` (`MissionProgress`, `MissionProgressEvent`, `MissionProgressListener`, `ProgressPhase`, `OptimizationStep`), listener nullable câblé de `MissionPlanOptimizer` à `CMAESRunExecutor`, `ui/mission/MissionProgressText.java`, `ui/mission/component/SpinnerIcon.java` + `SpinnerRotation.java`, `MatDefs/Ui/IconMask.j3md`, `MissionEntry.getProgress` ; `MissionProgressTest`, `MissionProgressTextTest`, `SpinnerRotationTest` ; spec [`mission-progress/01-feedback-optimisation.md`](../mission-progress/01-feedback-optimisation.md) |

**Reliquats de l'ancienne roadmap : les deux sont soldés.** La vue détail avec
résultats d'optimisation est **résolue le 2026-08-10** (`UI-1`) — et son
diagnostic était trop optimiste : `AchievedOrbit` n'était pas seulement non lu
par `ui/`, il était **jeté** par l'orchestrateur, qui n'en gardait rien. Le
feedback de progression pendant l'optimisation est **résolu le 2026-08-21**
(`UI-2`), et son énoncé était incomplet sur deux points : l'exécuteur étant
mono-thread, une deuxième mission lancée est en **file** et non en calcul, ce que
l'affichage ne distinguait pas ; et un compteur d'évaluations nu n'apprend rien
de plus qu'un spinner, faute de dénominateur atteignable.

**Corrections d'hypothèses par rapport aux specs graphiques** — `effects-roadmap.md`
§1 décrit un rendu « tout `Unshaded`, aucun shader custom, pas de skybox ». Ce
n'est plus vrai, et de loin : le dépôt compte désormais **trois** matériaux
maison — `WrapLighting` (éclairage), `Corona` (halo stellaire) et `Ribbon`
(orbites et trajectoires).
Deux conséquences notées plus bas : les éclipses (`FX-2`) descendent de ◆4 à ◆3,
et l'item « Lambert sur les planètes » disparaît de la roadmap (fait).

---

## 2. Notation

- **★ Valeur** — apport perçu dans l'application, tous publics confondus
  (lisibilité, réalisme, spectacle, déblocage d'autres features).
  ★1 = à peine perceptible → ★5 = change ce qu'OrbitLab *est*.
- **◆ Difficulté** — ◆1 = quelques lignes localisées → ◆5 = R&D, refonte d'un
  sous-système, plusieurs semaines.
- **Taille** — S (< 1 j), M (1–3 j), L (1–2 semaines), XL (au-delà).
- Les identifiants (`RND-1`, `MIS-4`…) sont **stables** : les phases peuvent
  bouger, les identifiants non.

---

## 3. Les quatre phases livrées

**C'est le compte rendu de v1**, et non un plan : les quatre phases sont
soldées, dans l'ordre où elles ont été prises. Chacune garde son énoncé
d'origine, sa table d'items et son « fin de phase » — c'est ce qui permet, plus
tard, de savoir ce qui avait été promis et ce qui a réellement été livré. Les
renvois à une « phase 5 », « phase 6 » ou « phase 7 » qu'on y lit sont ceux du
plan d'origine à sept phases, conservés tels quels ; le §5 dit ce que chacune
est devenue.

Trois phases figuraient dans le document d'origine et n'ont jamais été
entamées ; elles ont changé de version plutôt que de disparaître. Le §5 dit
laquelle est allée où.

### ~~Phase 1 — Hygiène visuelle, horizon de mission, dette panel~~ · **soldée le 2026-08-10**

> Que ce qui est déjà à l'écran soit propre, honnête et dise quelque chose,
> avant d'ajouter quoi que ce soit. `MIS-8` était le seul item de la phase qui
> soit un préalable pour d'autres : il fixe le substrat temporel sur lequel
> `NAV-2` (phase 2) et les missions longues (phases 4 et 5) vont s'appuyer.
>
> **Les sept items sont livrés.** La phase 2 peut démarrer sans reliquat.

| ID | Item | ★ | ◆ | Taille |
|---|---|:-:|:-:|:-:|
| ~~MIS-8~~ | ~~**Horizon de mission explicite** (fin de mission codée en dur)~~ — **résolu le 2026-08-09** | 5 | 2 | M |
| ~~RND-1~~ | ~~Corriger les artefacts visuels de la vue spacecraft~~ — **résolu le 2026-08-09** | 4 | 2 | M |
| ~~FX-1~~ | ~~Bloom sur le Soleil~~ — **résolu le 2026-08-09** | 3 | 1 | S |
| ~~RND-2~~ | ~~Filtrage anisotrope~~ — **résolu le 2026-08-09** | 2 | 1 | S |
| ~~MIS-1~~ | ~~Deuxième lanceur au catalogue~~ — **résolu le 2026-08-09** | 3 | 1 | S |
| ~~RND-3~~ | ~~Couleur par stage + passé/futur + marqueur « now »~~ — **résolu le 2026-08-10** | 4 | 2 | M |
| ~~UI-1~~ | ~~**Vue détail mission** (orbite atteinte, message d'erreur)~~ — **résolu le 2026-08-10** | 4 | 2 | M |

**Fin de phase — atteinte.** Plus aucun scintillement en vue vaisseau et le
modèle 3D y reste visible quelle que soit la vitesse d'horloge (`RND-1`) ; une
mission calculée affiche ce qu'elle a atteint, orbite et écart à la cible, et
dit pourquoi quand elle échoue (`UI-1`) ; sa durée est une décision explicite
plutôt qu'une constante (`MIS-8`).

### ~~Phase 2 — Navigation, temps, caméra~~ · **soldée le 2026-08-15**

> Rendre la scène et la timeline parcourables. C'est le bloc « timeline et
> navigation 3D » + « transitions de caméra ». `NAV-3` suit `NAV-2` ; `NAV-4`
> peut se faire à tout moment. `UI-4` est passé **avant** `NAV-2`, et c'était
> l'ordre à tenir : la spec de la piste temporelle demandait de recopier
> `MissionPanelTrigger` (style et `setEnabled`), qui n'existe plus. `NAV-2`
> trouve à la place un sélecteur Lemur et une sémantique de coche.
>
> **Les sept items sont livrés.** La phase 3 peut démarrer sans reliquat.

| ID | Item | ★ | ◆ | Taille |
|---|---|:-:|:-:|:-:|
| ~~NAV-1~~ | ~~Transitions de caméra entre vues~~ — résolu | 4 | 2 | M |
| ~~UI-4~~ | ~~**Menu applicatif haut-gauche** (remplace le bouton « Missions »)~~ — **résolu le 2026-08-14** | 3 | 2 | M |
| ~~UI-5~~ | ~~**Surfaces, modalité et pile de renvoi `ESC`** — découle d'`UI-4`~~ — **résolu le 2026-08-15** | 3 | 2 | M |
| ~~NAV-2~~ | ~~Timeline indexée sur le temps + marqueurs d'événements~~ — **résolu le 2026-08-15** | 4 | 3 | M |
| ~~NAV-3~~ | ~~Scrub continu (glisser sur la piste)~~ — **résolu le 2026-08-15** | 3 | 2 | S |
| ~~RND-4~~ | ~~Ribbon billboardé (orbites + trajectoires)~~ — **résolu le 2026-08-15** | 4 | 3 | M |
| ~~NAV-4~~ | ~~Breadcrumb de navigation 3D~~ — **résolu le 2026-08-15** | 3 | 2 | M |

> **Les bascules d'affichage ne sont pas dans `UI-4`.** La table portait une
> seconde ligne `UI-4` — « menu HUD + bascules d'affichage (labels, orbites) » —
> antérieure à la spec ; celle-ci a arrêté un périmètre v1 de trois entrées
> (*Mission panel*, *Manage missions…*, *New mission…*) et rien d'autre. La
> ligne est retirée pour ne pas laisser un même identifiant à la fois livré et
> à faire. Ce qu'`UI-4` garantit, c'est l'**hôte** : couper les labels ou les
> orbites est désormais une entrée à ajouter, plus un chantier d'UI.

**Pourquoi `UI-4` était ici.** C'est la phase où l'on commence à *regarder* la
scène pour de bon, et où couper les labels ou les orbites devient ce qu'on veut
faire pour cadrer une transition (`NAV-1`) ou lire une trajectoire (`RND-4`).
Il n'avait aucune dépendance et aurait pu glisser ailleurs sans coût, mais plus
tard il aurait coûté plus cher : chaque fonction globale livrée sans contenant
se pose en bouton supplémentaire à côté du bouton « Missions ». Le contenant
existe maintenant.

**Fin de phase — atteinte.** On atteint n'importe quel instant d'une mission à la
souris, au clic comme au glisser (`NAV-2`, `NAV-3`) ; on change de corps focalisé
sans cut (`NAV-1`), et désormais sans passer par la vue 3D pour retrouver le
parent (`NAV-4`) ; le HUD permanent a un contenant, une modalité et une pile de
renvoi plutôt qu'une collection de boutons (`UI-4`, `UI-5`), et une trajectoire
reste lisible à toute distance (`RND-4`).

> **Ce que la phase laisse pour plus tard**, et qui n'appartient à aucun de ses
> items : le réglage à l'œil de `MUTING_STEP` sur le ruban (fiche `RND-3`), la
> section interaction de `MissionTimelineWidget` qui mériterait d'être extraite
> (fiche `NAV-3`), et la descente vers les fils du breadcrumb, explicitement
> reportée en V2 par sa spec (fiche `NAV-4`).

### ~~Phase 3 — Socle physique et mission partagé~~ · **soldée le 2026-08-21**

> **La phase pivot.** Rien ici n'est spectaculaire pris isolément ; tout est
> réclamé par les missions lunaires (phase 4) et par le rendez-vous (phase 5).
> C'est aussi la phase la plus risquée en estimation.
>
> Les deux derniers items sont de l'**outillage** plutôt que du socle physique,
> et c'est délibéré : ils rendent les phases 4 et 5 tenables au quotidien.
>
> **Les sept items sont clos** — six livrés, un dissous. La phase 4 peut
> démarrer sans reliquat.

| ID | Item | ★ | ◆ | Taille | Sert à |
|---|---|:-:|:-:|:-:|---|
| ~~MIS-7~~ | ~~`EarthOrbitMission` paramétrable~~ — **résolu le 2026-08-16** | 4 | 2 | M | MIS-2, MIS-6, + polaire/SSO/MEO gratuits |
| ~~PHY-4~~ | ~~Socle multi-corps (3ᵉ corps, SOI, repères)~~ — **résolu le 2026-08-18** | 5 | 4 | L | MIS-4, MIS-5 |
| ~~MIS-2~~ | ~~Fenêtres de lancement~~ — **résolu le 2026-08-20** | 4 | 3 | M | MIS-4, MIS-6 |
| ~~MIS-3~~ | ~~Solveur de Lambert + repère LVLH~~ — **dissous le 2026-08-20** | 4 | 3 | M | — (reversé en `MIS-4` et `MIS-6`) |
| ~~PHY-1~~ | ~~Atmosphère : la brique, **off** par défaut~~ — **résolu le 2026-08-21** | 4 | 3 | L | PHY-2, PHY-3 |
| ~~UI-2~~ | ~~Feedback de progression pendant l'optimisation~~ — **résolu le 2026-08-21** | 3 | 2 | M | confort des phases 4 et 5 |
| ~~UI-3~~ | ~~Persistance des missions / format de scénario~~ — **résolu le 2026-08-21** | 4 | 3 | M | **outil de dev** des phases 4 et 5 |

**Pourquoi `UI-3` était ici et pas en phase 6.** Il n'avait aucune dépendance, et son
bénéfice principal à ce stade n'est pas la feature mais l'outillage : sans lui,
mettre au point une mission lunaire ou un rendez-vous impose de re-saisir la
mission dans le wizard **à chaque lancement de l'application**. Un save/load
livré avant les phases 4 et 5 se rembourse pendant celles-ci ; livré après, il
ne rembourse rien.

**Pourquoi `MIS-3` a disparu de la phase.** Il n'a pas été livré, il a été
**dissous** : `PHY-4` a livré Lambert au passage, et la moitié `LVLH` n'a aucun
consommateur avant `MIS-6`. Ce qui restait a été reversé dans les deux items qui
en ont besoin. Le raisonnement complet est dans la fiche `MIS-3` au §6.

**Fin de phase — atteinte.** Une trajectoire peut sortir de la sphère
d'influence terrestre (`PHY-4`), une date de lancement est choisie parce
qu'elle est bonne (`MIS-2`), et une mission survit à la fermeture de
l'application (`UI-3`).

### ~~Phase 4 — Missions lunaires~~ · **soldée le 2026-08-29**

> Aller à la Lune, s'y mettre en orbite, et voir les corps qui s'alignent
> s'occulter réellement.
>
> **Les trois items sont livrés.** La phase 5 peut démarrer sans reliquat.

| ID | Item | ★ | ◆ | Taille |
|---|---|:-:|:-:|:-:|
| ~~MIS-4~~ | ~~Survol lunaire (TLI + flyby)~~ — **résolu le 2026-08-28** | 5 | 4 | L |
| ~~MIS-5~~ | ~~Mise en orbite lunaire (LOI)~~ — **résolu le 2026-08-29** | 5 | 3 | M |
| ~~FX-2~~ | ~~Éclipses / pénombre inter-corps~~ — **résolu le 2026-08-29** | 4 | 3 | M |

`FX-2` était ici et pas en phase 6 pour la raison qui a fini par se vérifier :
c'est le moment où la scène a enfin des corps alignés qui s'occultent pour de
bon, et où l'effet se voit — confirmé à l'écran sur l'éclipse solaire réelle
du 12/08/2026.

**Fin de phase — atteinte.** Un survol lunaire part et revient (`MIS-4`), une
mission peut se mettre en orbite lunaire (`MIS-5`), et les trois corps qui
s'alignent projettent une ombre qui se voit — vaisseau, Lune, et tache
d'ombre sur la Terre (`FX-2`).

---

## 4. Les lignes de correction : 1.1.X et 1.2.0, closes toutes deux

### 4.1 La ligne 1.1.X — corrections et stabilisation

> **C'est la seule chose qui reste à faire dans ce document.** `v1.1.0` est
> taguée depuis le 2026-08-31 (`960f168`) et `build.gradle:10` porte
> `1.1.0-SNAPSHOT` ; la stabilisation commence donc à **1.1.1**.

La ligne 1.1.X ne livre aucune fonctionnalité. Elle ferme le jalon `J0` de
[`05-roadmap-technique.md`](05-roadmap-technique.md) — *rendre le dépôt
mesurable* — et corrige ce qui se voit à l'écran sur du contenu déjà livré.

| Item | Registre | Ce qui reste |
|---|---|---|
| `J0-B` | `DT-1` | **Fait le 2026-09-02** — `ignoreFailures = false`, `main` et `test` au vert (`5419f63`, `4cf19e1`). Reste un résidu documentaire : les 15 exclusions du ruleset ne portent aucune raison écrite |
| `J0-C` | `DT-4`, `DT-6`, `DT-8`, `DT-9`, `DT-11` | **Fait le 2026-09-02** — champ singleton supprimé, exceptions larges traitées, langue corrigée, code mort disparu, noms d'étapes réunis dans `StageNames`. `DT-11` reste partiel : 2 TODO non tracés |
| `J0-D` | les 69 fiches | **Fait le 2026-09-02** — le corpus en compte 69 et non 66. Une seule fiche fausse sur le fond (`REL-20`), quatre citant un symbole disparu, et une quinzaine de références de ligne à recaler. Ce sont les **chiffres** qui avaient dérivé, pas les mécanismes |
| `BUG-3` | `bugs.md` | Les cinq lots d'[`orientation-planetes/01-decoupage.md`](../orientation-planetes/01-decoupage.md) sont implémentés le 2026-09-02 (`82ba2ff`). Reste **la validation à l'écran corps par corps**, qui arrête les `λ0` de `L3` |
| `BUG-19` | `bugs.md` | Rotation propre des planètes externes aliasée par le pas de la fenêtre glissante — Neptune à **4,1 % du taux vrai**, Saturne et Uranus **à l'envers**. Cause racine établie, ampleur mesurée |

**`J0-A` est fait** : `BUG-6`, `BUG-9` et `BUG-15` sont corrigés le 2026-08-31,
et `REL-14` / `REL-23` — les deux vols de clôture jamais lancés — ont été
exécutés le même jour, verts tous les deux.

**`J0-B`, `J0-C` et `J0-D` sont faits le 2026-09-02.** Le jalon `J0` est clos :
de la ligne 1.1.X il ne reste que `BUG-3` et `BUG-19`.

**Ce qui ne pouvait pas être dans 1.1.X.** `BUG-20` (plan des anneaux désaligné —
Saturne 13,51°, Uranus 9,93° hors du plan équatorial de leur propre globe) est
**hors de portée du code** : il demande un ré-export d'asset. Il a rejoint `AST-1`
en [v2](02-roadmap-v2.md) avec les trois autres items bloqués sur des maillages
absents — puis l'a quitté le 2026-09-03 pour le §4.2, la sonde `meshProbe`
imprimant l'angle et l'axe de la rotation à faire. Une fiche dont le correctif est
mesuré n'attend pas un approvisionnement.

**Fin de version quand** : PMD casse le build au lieu de le décorer, les trois
registres sont datés d'une vérification contre le code, et les onze planètes
tournent au bon taux dans le bon sens.

**Close le 2026-09-03, et son critère était atteint.** « Les onze planètes
tournent au bon taux dans le bon sens » est exactement ce que `BUG-19` a livré en
1.1.1 ; ce qui restait de `BUG-3` est la **longitude**, que ce critère ne demande
pas.

`BUG-3` est donc requalifié **« accepté, avec raison »**, comme les registres le
permettent. La raison : la passe `λ0` est sciemment jetable sur sept maillages
sur onze — `git log` les donne inchangés depuis le 2026-08-24, seuls `jupiter`,
`mars` et `venus` ayant été ré-exportés le 2026-09-02 — et leur remplacement
n'est planifié nulle part, `AST-1` n'ayant pas de contenu arrêté. Attendre aurait
été attendre une décision sans propriétaire. La passe se rouvrira **asset par
asset**, quand un asset sera figé ; la Terre, la Lune et Jupiter sont déjà faits.

**La v1 est donc close à 1.1.1.** Ce qui suit est une ligne nouvelle, pas la fin
de celle-ci.

---

### 4.2 La ligne 1.2.0 — la vue rapprochée, et l'ombre des anneaux

> **Close le 2026-09-05.** Les six fiches annoncées sont faites, plus une
> septième prise en route, `BUG-23`. L'argument de la version a tenu : six
> fiches, deux chantiers de code et une séance d'assets.
>
> **Ce que la ligne a appris, et qui n'était dans aucune fiche.** Trois des sept
> ont été corrigées ailleurs que là où leur fiche les situait. `BUG-20` n'était
> pas un problème Blender mais un export : une échelle nulle rend la matrice de
> l'objet singulière et l'exportateur en tire une rotation de nœud fausse — la
> moitié Uranus a d'abord échoué en empirant, de 9,93° à 20,25°. `FX-5` a démenti
> le chiffre que sa propre fiche demandait de vérifier : c'est le rayon
> *dessiné* qui coule l'ombre, pas l'équatorial, et cinq corps portent le rayon
> moyen, pas quatre. `BUG-23` visait un générateur hors-ligne dont le
> dépassement était transitoire, et le correctif qu'il proposait aurait effacé
> les deux tiers de l'orbite de Pluton.
>
> **Une fiche neuve en est sortie**, `BUG-24` : la largeur du ruban n'est tenue
> qu'aux sommets, et le gonflement vaut `(L/2)/d`. C'est la cause que `BUG-23`
> cherchait — sa propre section « Non vérifié » avait raison de douter.
>
> **Ouverte le 2026-09-03**, sur une séance de mesure des fiches `BUG-1`, `BUG-2`
> et `BUG-5`, suivie d'une vérification à l'écran qui a produit un défaut neuf,
> `BUG-22`, et démenti le diagnostic de `BUG-1`.

**Une phrase.** La 1.2.0 répare ce que la vue rapprochée d'un corps montre de
faux, et donne aux anneaux l'ombre de leur planète.

**Pourquoi une mineure et non une 1.1.2.** Le contenu ci-dessous tombe
littéralement sous la définition de la ligne 1.1.X — « ne livre aucune
fonctionnalité, corrige ce qui se voit à l'écran sur du contenu déjà livré » — à
une fiche près : `FX-5` ajoute un effet qui n'existe pas. C'est elle qui décide
du numéro.

#### Contenu — six fiches, trois mécanismes

| | fiches | ce que c'est |
|---|---|---|
| **Le basculement de focus à la dernière frame** | `BUG-1`, `BUG-5` | Un seul changement : recentrer le repère flottant sur le pivot interpolé au lieu d'attendre `CameraTransitionAppState.finish`. Le tremblement de l'approche et le pop du modèle en découlent tous les deux |
| **Les clamps d'`updateFrustum`** | `BUG-2`, `BUG-22` | Deux correctifs indépendants dans le même fichier : l'invariant « la caméra far n'est jamais observable avec un `near` sans son `top` », et un garde de face-arrière qui teste le signe au lieu d'une profondeur normalisée |
| **Les anneaux** | `BUG-20`, `FX-5` | Deux rotations de nœud dont `./gradlew meshProbe` donne l'angle et l'axe, puis un occulteur par géométrie qui réutilise `eclipseIllumination` tel quel |

Trois paires, et dans chacune la seconde fiche est presque gratuite une fois la
première comprise. C'est l'argument de la version : **six fiches, mais deux
chantiers de code et une séance d'assets.**

Les mécanismes, les amplitudes et les seuils sont dans [`bugs.md`](../bugs.md) ;
cette section ne les répète pas. Ce qu'elle ajoute est ce qu'aucune fiche ne
pouvait dire seule : `BUG-1` et `BUG-5` demandent le **même** changement, `BUG-2`
et `BUG-22` vivent dans le **même fichier**, et `FX-5` a besoin de `BUG-20`.

#### Ordre

`BUG-20` **précède** `FX-5` : une ombre juste sur un anneau à 13,51° de son plan
reste fausse, et une bande d'ombre rend justement ce désalignement plus lisible.
La version ne se clôt donc pas sans une opération dans Blender — l'angle et l'axe
sont donnés par la sonde, et `MeshGuard` signale dès la frame suivante si le
ré-export a déplacé autre chose.

Les deux chantiers de code sont indépendants l'un de l'autre et des anneaux.

#### Ce qui n'y est pas, et pourquoi

- **Les six `REL` de rendu** (`REL-1` à `REL-6`) restent dans la passe `H-RND` de
  [v3](03-roadmap-v3.md). Ce sont des réglages à l'œil, et `REL-6` demande
  d'abord d'observer une éclipse annulaire : leur durée n'est pas prévisible, et
  une version dont la fin dépend d'une séance de goût n'a pas de fin.
- **La marge de 10 000 km** d'`updateFrustum` (`max(near·2 ; 0,01)`, absolue et
  exprimée en unités solaires alors qu'elle s'applique à une vue dont le sujet
  fait 2 376 km) : c'est elle qui arme `BUG-22`, mais la corriger déplace la
  plage de profondeur du viewport far pour tous les corps. Le correctif retenu
  supprime le cas sans y toucher ; la marge reste un constat écrit dans la fiche,
  pas un item.
- **« Le ciel doit-il suivre la FoV du tout ? »** — la deuxième question de
  `BUG-2`, chiffrée (les étoiles changent d'échelle d'un facteur **4,39** sur la
  plage de zoom) mais pas tranchée. `BUG-2` corrige le saut, pas le choix de
  conception.
- **`BUG-3`**, clos par verdict au §4.1.

#### `FX-5` — L'ombre de la planète sur ses anneaux — ★3 ◆2 S

> **Livré et vérifié à l'écran le 2026-09-04.** Un occulteur par géométrie :
> `Model3dView.isolateRing` retient les `Geometry` de l'anneau pendant le chargement — jamais leurs
> `Material`, qu'`applyLambert` remplace juste après — et `setRingSunlight` leur pousse la planète
> comme occulteur. `PlanetPoseAppState` n'envoie que le Soleil ; le centre et le rayon ne quittent
> pas la vue, qui est seule à savoir où son globe est dessiné et à quelle taille.
>
> **Le chiffre à vérifier s'est retourné.** La fiche demandait de passer au rayon équatorial, et
> comptait quatre géantes sur le rayon moyen : c'est **cinq**, Mars aussi (3 389,5 contre 3 396,2 km).
> Surtout, `PlanetRadius.radiusFor` est ce qui **dimensionne le globe dessiné** — `loadModel`
> échelonne par `2 · radiusMeters / PLANET_METERS_PER_UNIT`. Un occulteur au rayon équatorial
> projetterait une ombre 3,4 % plus large que la silhouette qui la coule. L'ombre prend donc le
> rayon dessiné, et l'expression est partagée avec celle qui échelonne le modèle pour qu'elles ne
> puissent pas diverger.
>
> **Ce que le test de mesure a dit et qu'aucun raisonnement n'avait vu.** La bande fait `2R` de
> large à toute époque — la section du cylindre d'ombre ignore la façon dont le plan le coupe — mais
> sa **portée** vaut `R / sin(ouverture)`. D'où :
>
> | corps | ouverture 2026 | l'ombre s'arrête à | anneau | verdict |
> |---|---|---|---|---|
> | Saturne | 7,25° | 7,89 R | 1,638–1,965 R | couvre tout l'anneau, jusqu'au solstice de 2032 |
> | Uranus | 72,97° | 1,05 R | 1,603–1,706 R | **rate l'anneau**, et le rate jusque vers 2042 |
>
> Le corps sur lequel l'effet se voit est donc une propriété de la **date**, pas du corps — et c'est
> Saturne, dont l'anneau est pourtant à 4,8 % de sa luminosité de solstice. La pénombre est
> négligeable : 56 à 59 km sur Saturne, 10 à 11 km sur Uranus, contre une bande de 116 464 km.
>
> **La vérification.** Sur un anneau plat sous une lumière directionnelle, `N·L` est constant sur
> toute la circonférence, et la texture de Saturne est uniforme en azimut à 5-6 % près (sondée à 360
> échantillons par rayon) : toute variation le long de l'anneau est donc l'occultation, et rien
> d'autre. Confirmé à l'écran au 2032-09-10 en laissant tourner l'horloge — **la texture défile avec
> la planète, un tour en 10,5 s à `+1h/s`, et le coin sombre ne bouge pas.**
>
> **Épinglé par** `RingShadowFixtureTest` (5 tests : le nom de nœud committé, le globe non attrapé
> par le préfixe, l'alignement de l'anneau hérité de `BUG-20`, la capture avant `applyLambert`, et
> l'exclusion de l'anneau dans `setOccluder`) et mesuré par `RingShadowMeasureTest`.
>
> **Deux constats ouverts, hors item, tous deux visibles dans la même image.** *(a)* L'anneau est
> éclairé comme une surface solide alors que c'est une dalle de particules : entre l'ouverture
> d'aujourd'hui et le solstice, `N·L · smoothstep` impose un écart de **9,3×** là où la diffusion
> simple en donne 2,1 à 2,3 — on assombrit d'un facteur ~4 de trop, et précisément à la géométrie
> actuelle. *(b)* La texture d'anneau de Saturne est un 512×512 déplié en carré vu de face, ce qui
> laisse **42 px de résolution radiale** pour tout le système : la division de Cassini y est bien
> (creux à `r/rMax = 0,84`, contraste 2×) mais large de quatre pixels.

**Pourquoi.** Saturne éclairée uniformément sur tout son anneau ne lit pas comme
une planète à anneaux : c'est la bande d'ombre qui donne au disque son épaisseur
et sa position dans l'espace. L'item vient du backlog non planifié de
[`00-index.md`](00-index.md), où il traînait sans identifiant.

**Ce que le dépôt porte déjà, mesuré le 2026-09-03.** Trois des quatre morceaux
sont là :

- **les maillages.** `saturn.gltf` porte un nœud `Circle_ring_0` (matériau
  `ring`, texture `ring_diffuse.jpg`) et `uranus.gltf` un `Circle_Material.003_0`.
  Jupiter et Neptune n'ont aucune géométrie d'anneau. La moitié « disque + texture
  alpha » de l'ancien [`effects-roadmap.md`](../graphics-effects/effects-roadmap.md)
  §5.3 est donc périmée ;
- **la formule.** `eclipseIllumination` dans
  [`WrapLighting.frag`](../../src/main/resources/MatDefs/Light/WrapLighting.frag)
  calcule la fraction du disque solaire occultée par une **sphère** vue depuis un
  fragment, pénombre comprise. L'ombre d'une planète sur ses propres anneaux est
  exactement cette primitive, avec la planète pour occulteur ;
- **le matériau séparé.** `AssetFactory.applyLambert` instancie un `Material` par
  géométrie, donc l'anneau peut porter ses propres uniformes sans toucher à ceux
  du globe.

**Ce qui manque** est donc un seul mécanisme : `Model3dView.setOccluder` écrit
aujourd'hui sur *toutes* les géométries du bucket et écraserait celui de l'anneau.
Il faut un occulteur par géométrie, avec la sélection par préfixe de nœud qui
existe déjà (`ShellSpin.isolate`, utilisée pour la couche nuageuse de Vénus).

**Ce que ça ne fait pas.** L'ombre **des anneaux sur la planète** est un occulteur
annulaire, que ce shader ne sait pas représenter — §5.3 de l'ancien document
confond les deux. Elle reste hors item.

**Réserve, et elle est levée par l'ordre.** [`BUG-20`](../bugs.md) bloque la
justesse, pas l'implémentation : les plans d'anneaux sont à 13,51° (Saturne) et
9,93° (Uranus) de l'équateur de leur propre globe, et une bande d'ombre rend ce
désalignement **plus** lisible, pas moins. C'est pourquoi `BUG-20` est dans la
même version et **précède** cette fiche : l'ombre n'est jugée qu'une fois, sur une
géométrie juste.

**Un chiffre à vérifier avant de coder.** La Javadoc de `PlanetRadius` annonce un
rayon équatorial, mais quatre géantes portent le rayon **moyen** : Saturne
58 232 km contre 60 268 équatorial, soit **3,4 % de moins**. Seule la Terre est sur
une valeur équatoriale (WGS84). Or c'est le rayon équatorial qui fixe la largeur de
l'ombre dans le plan des anneaux.

#### Fin de version quand

Quatre énoncés à l'écran :

1. aucune icône n'est dessinée pour un corps situé derrière la caméra, à
   n'importe quel zoom et sur n'importe quel focus ;
2. rien ne tremble pendant une transition de focus, Pluton compris ;
3. le modèle 3D grossit continûment au lieu d'apparaître ;
4. les anneaux de Saturne et d'Uranus sont dans le plan équatorial de leur globe,
   et l'ombre de la planète y tombe.

Et deux chiffres, relus avec le harnais qui a servi à ouvrir la version : le
résidu de quantification **sous 1 px** au cadrage d'arrivée de Pluton — il vaut
40,7 px aujourd'hui — et le garde de face-arrière qui rejette **10 corps sur 10**
en focus Pluton, alors qu'il en laisse passer 10 sur 10 aujourd'hui.

---

## 5. Ce que v1 a débloqué, et où sont partis les items non livrés

Trois nœuds commandaient tout le reste du document d'origine : **`MIS-8`** (le
plus en amont, et le moins cher — tout ce qui durait plus d'un jour simulé
butait dessus), **`PHY-4`** (sans lui, rien de lunaire) et **`MIS-2`** (sans
fenêtre de lancement, ni la Lune ni un rendez-vous ne convergent : la cible
n'est jamais au bon endroit). **Les trois sont fermés** — `MIS-8` le
2026-08-09, `PHY-4` le 2026-08-18, `MIS-2` le 2026-08-20.

Un quatrième a disparu du graphe sans être livré : `MIS-3` (Lambert + LVLH),
**dissous le 2026-08-20** parce qu'il n'avait plus de contenu propre. Sa fiche
au §6 dit où chaque moitié a été reversée.

Les items jamais entamés ont suivi leur version. Le graphe de dépendances
inter-versions est dans [`00-index.md`](00-index.md) :

| Item | Va en | Pourquoi là |
|---|---|---|
| `PHY-2`, `PHY-3`, `PHY-5`, `FX-3`, `NAV-5` | [v2](02-roadmap-v2.md) | L'ancienne phase 6, « réalisme et spectacle » |
| `MIS-10` | [v2](02-roadmap-v2.md) | Un satellite se déorbite **entier** : c'est la mission qui rend `PHY-2` vérifiable |
| `RND-5` | [v2](02-roadmap-v2.md) | Phasé, alors qu'il était volontairement hors phases — il fait paire avec la trace au sol (`RND-6`) |
| `MIS-11` | [v3](03-roadmap-v3.md) | Artemis rentre **en capsule** : il attend la séparation de charge utile livrée en v2 |
| `UI-6`, `UI-7` | [v3](03-roadmap-v3.md) | Phasés, eux aussi — l'argument qui les tenait hors phases (« leur coût augmente avec le temps ») est un argument pour les planifier |
| `MIS-6` | [v4](04-roadmap-v4.md) | L'ancienne phase 5, déplacée derrière la séparation de charge utile |
| `MIS-9` | [v4](04-roadmap-v4.md) | Non planifié et conditionnel ; sa fiche le range derrière la généralisation d'`EphemerisSource` que `MIS-6` fait |

**Pourquoi l'atmosphère a été coupée en deux, et pourquoi ça a tenu.** `PHY-1`
livrait la brique de traînée **désactivée par défaut** — *drag off ⇒
trajectoire identique au bit près* — et `PHY-2` bascule le défaut une fois les
types de missions posés. L'alternative était de livrer l'atmosphère complète
avant les nouvelles missions : un seul recalibrage aussi, mais la Lune reculait
de plusieurs semaines derrière un chantier ◆4 risqué. La contrainte du bit près
a tenu sur toute la phase 3, et c'est ce qui permet à v2 de ne recalibrer
qu'une fois, sur un périmètre connu.

---

## 6. Détail des items

*Fiches des items **livrés** en v1, dans l'ordre des familles d'identifiants.
Les fiches des items non livrés ont suivi leur version (§5).*

### RND — Rendu et lisibilité

#### ~~RND-1 — Corriger les artefacts visuels de la vue spacecraft — ★4 ◆2 M~~ — **RÉSOLU le 2026-08-09**

**Pourquoi.** C'était le défaut le plus visible de l'application, et un
**prérequis d'hygiène** : tout enrichissement de la ligne (RND-3, RND-4) ou de
l'approche planétaire (missions lunaires) empilait du travail sur un rendu qui
bataillait déjà.

**Trois causes racines indépendantes, pas une** — rediagnostiquées puis
corrigées le 2026-08-09, chacune par un mécanisme distinct :

- ~~**A — le modèle 3D du vaisseau disparaît en accéléré.**~~ `MissionOrchestrator`
  était mis à jour *avant* `FloatingOrigin` : le calcul de LOD lisait une
  position monde en retard d'une frame, donc croyait le vaisseau à `v × Δt_sim`
  de l'origine, et basculait en icône au-delà de ~×100. Ce n'était pas du
  z-fighting. `FloatingOrigin` est désormais propriétaire de l'offset, le dérive
  de l'éphéméride et passe en premier.
- ~~**B — la ligne de trajectoire saute.**~~ Sommets en GCRF absolu (~6778 en
  unités km) contre une translation `−p` de même ordre : annulation
  catastrophique en `float32`. Les sommets sont maintenant relatifs au vaisseau,
  soustraction en `double` avant le cast, et la géométrie porte `+p` par le même
  chemin de conversion que l'ancre — l'annulation est donc exacte.
- ~~**C — la ligne scintille sur le disque terrestre.**~~ `near` et `far`
  collés à leurs planchers (10 m / 100 000 km) donnaient **~274 km** de
  résolution de profondeur à la Terre, pas 300–500 m comme estimé
  précédemment. Facteur near à `0.2f` en vue spacecraft, plafonné à 1 km :
  ~27 km par pas, ×10.

**Vérification.** Trois classes de test verrouillent les invariants sans OpenGL
(`NearFrameOriginTest`, `NearFrustumDepthTest`, `MissionTrajectoryOriginTest`),
et elles ont d'abord **mesuré** les deux chiffres dérivés par la spec : 273,8 km
par pas de profondeur, et 0,19 m de décalage pour un sommet à 1,5 km du
vaisseau. Le protocole d'observation en application (§8 de la spec) a été passé
ensuite et est concluant.

**Deux correctifs annoncés par l'ancienne version étaient sans effet**, et n'ont
pas été faits : baisser `FAR_MIN` (`Δz ∝ z²/near`, le far ne compte pas) et
`setPolyOffset` sur la ligne (JME n'active que `GL_POLYGON_OFFSET_FILL`, jamais
`..._LINE`).

**Reliquat, hors périmètre de cet item.** Deux améliorations de la ligne restent
ouvertes, aucune n'étant un artefact de la vue spacecraft au sens ci-dessus :
le raccord terminal qui pivote d'environ 2° à chaque pas d'échantillonnage
(spec §4.3), et `setDepthWrite(false)` contre les batailles ligne ↔ ligne aux
croisements de boucles (spec §6).

**Où ils en sont, `RND-4` étant clos à son tour.** `setDepthWrite(false)` est
**fait** : le matériau `Ribbon` l'impose, deux rubans qui se croisent se mélangent
au lieu que l'un efface l'autre. Le raccord terminal, lui, **reste ouvert et sans
identifiant** : le ruban traite les jointures *entre segments* (spec ribbon §7.5)
et ne dit rien du pivotement du dernier segment, qu'il ne corrige ni n'aggrave.
C'est la deuxième fois qu'il est reversé faute de propriétaire ; il lui en faut un
le jour où il gêne — densification de l'échantillonnage de tête, ou interpolation
de la tangente terminale.

**Spec.** [`docs/graphics-effects/spacecraft-view-artefacts.md`](../graphics-effects/spacecraft-view-artefacts.md)
§9.1 à §9.3 (ce qui a été fait, et les écarts avec ce qui était prévu).

#### ~~RND-2 — Filtrage anisotrope — ★2 ◆1 S~~ — **RÉSOLU le 2026-08-09**

**Pourquoi.** Complète le MSAA déjà actif. Effet réel mais modeste : les
textures planétaires vues en biseau.

**Attention — ce que le MSAA ne fait pas.** Il n'antialiase pas les lignes GL de
manière fiable, et `glLineWidth > 1` est silencieusement plafonné à 1 px sur les
drivers en profil core. L'aliasing des orbites ne se règle donc **pas** par les
réglages `AppSettings` : c'est `RND-4` (ribbon) qui l'a réglé, en remplaçant la
primitive par un ruban qui porte son propre fondu de bord. Ne pas attendre de
`RND-2` qu'il « nettoie les orbites ».

**Ce qui a été fait.** Une ligne au boot,
`renderer.setDefaultAnisotropicFilter(8)` dans `OrbitLabApplication`, à côté du
`setSamples(4)` du MSAA. Le niveau 8 est délibéré alors que le pilote autorise
16 : au-delà, le rendement décroît vite pendant que le nombre d'échantillons
continue de doubler sur les fragments obliques, déjà les plus chers.

**Le réglage global a suffi parce qu'on l'a mesuré d'abord.** `TextureDiagnostics`
(`engine/`) rapporte le plafond du pilote, le `MinFilter` réellement posé par le
chargeur GLTF et le niveau propre de chaque texture. Verdict sur les 13 textures
planétaires : `Trilinear` partout, aucun niveau propre — donc rien à faire texture
par texture. Le rapport reste en DEBUG pour le jour où un asset change ; la ligne
de plafond, en INFO, sert désormais à confirmer que le réglage a bien pris.

**Deux pièges que la mesure a écartés.** L'anisotropie est un état de *texture*,
pas de matériau : le `Material.setFloat("AnisotropicFilter", 8)` que proposait
[`effects-roadmap.md`](../graphics-effects/effects-roadmap.md) §3.4 n'existe pas
en JME3. Et le contrôle naïf des mipmaps (`Image.hasMipmaps()`) répond à une
question plus étroite qu'il n'y paraît — il ne dit que si la chaîne venait du
fichier, et vaut faux pour un PNG dont le renderer génère les mips à l'upload.
Le signal décisif est `MinFilter.usesMipMapLevels()`.

#### ~~RND-3 — Couleur par stage, passé / futur, marqueur « now »~~ — **RÉSOLU le 2026-08-10**

**Pourquoi.** `MissionEphemerisPoint` porte déjà un `stageName` que rien
n'exploitait visuellement : on ne voyait pas où finit l'ascension verticale, où
commence la gravity turn, où le transfert s'allume.

**Ce qui a été fait.** Les phases descendent jusqu'au sommet sous forme de
*runs* (`PhaseRun`, segments contigus de même étape) portés par
`TrajectoryPolyline` ; `MissionPhaseShading` en dérive une couleur par run ;
`MissionTrajectoryRenderer` les pousse dans un `VertexBuffer.Type.Color` écrit
**une fois par trail** ; `PhaseNodeMarkers` dessine un point par transition
franchie.

**Trois écarts avec ce que cet item annonçait.**

1. **Pas de table `stageName → ColorRGBA`.** Les noms d'étapes sont des chaînes
   libres décidées mission par mission ; une table aurait été à étendre à chaque
   nouveau type de mission et aurait échoué en silence sur une faute de frappe.
   La couleur vient du **rang du run dans le vol**, plus `isPropulsive()`.
2. **L'alpha passé / futur est abandonné, pas reporté.** Il n'a rien à moduler :
   `MissionOrchestratorAppState` borne le tracé à `indexUpTo(now)`, donc rien
   n'est dessiné en avant du vaisseau. Dessiner la trajectoire *planifiée* est
   une fonctionnalité à part entière, pas un effet de rendu — à ouvrir sous son
   propre identifiant si elle est voulue.
3. **Le marqueur « now » n'a pas lieu d'être** : la position courante porte déjà
   le modèle 3D du lanceur, son icône billboard et son libellé (`LodView`).

**Ce qui n'était pas demandé et s'est avéré être le canal principal.** Les
marqueurs de transition. Les poussées font ~2 % de la durée d'un vol et moins de
1 % de la longueur d'arc dessinée : les colorer, c'est peindre trois traits de
deux pixels sur une spirale de deux mille. La couleur ne peut porter que ce qui
est long ; l'évènementiel passe par le marqueur.

**Un défaut trouvé à l'écran, pas en test.** La première règle ne classait que
les coasts. Or seuls `CoastingStage` et `StageSeparationStage` sont non
propulsifs, donc une LEO n'a qu'un seul vrai coast : la règle ne produisait que
deux couleurs, et l'ascension — la partie qu'on regarde le plus — était plate.
Le test de lisibilité passait parce qu'il était écrit sur un profil à trois
coasts qu'une LEO ne produit jamais. Corrigé en classant **tous** les runs, à pas
fixe compté à rebours depuis l'orbite finale, et doublé d'un test de contraste
entre voisins qui aurait attrapé le défaut.

**Reste à affiner** — et l'obstacle est levé. Le contraste entre phases était
modeste sur une ligne GL d'un pixel ; `RND-4` a livré le ruban, donc la surface
nécessaire pour juger `MUTING_STEP`. Le réglage lui-même n'est pas fait : il se
décide à l'œil sur un trait de 3,5 px, avec le test de contraste existant pour
arbitre.

**Spec.** [`mission-phase-encoding.md`](../graphics-effects/mission-phase-encoding.md),
qui remplace [`effects-roadmap.md`](../graphics-effects/effects-roadmap.md)
§9.3.1 et §9.3.2.

#### ~~RND-4 — Ribbon billboardé — ★4 ◆3 M~~ — **RÉSOLU le 2026-08-15**

**Pourquoi.** Seule vraie réponse au plafonnement de `glLineWidth` : épaisseur
stable, antialiasing par alpha-fade des bords, lisibilité à distance. Débloque
ensuite les tirets animés, le halo additif et le hover (`NAV-5`).

**Ce qui a été fait.** Les deux consommateurs de lignes passent au ruban de
triangles face-caméra, **expansé en vertex shader** comme le recommandait la spec
§6 : `MatDefs/Fx/Ribbon.j3md` + `.vert` / `.frag` (3ᵉ matériau maison, après
`WrapLighting` et `Corona`), `engine/scene/RibbonMeshBuilder.java` pour la
construction du maillage, `OrbitLineFactory` et `MissionTrajectoryRenderer` pour
les deux appelants, `AssetFactory.createRibbon` pour l'état de rendu.
`RibbonMeshBuilderTest` couvre les six propriétés du §10 de la spec.

**L'arbitrage GPU contre CPU a tenu**, et c'est le seul point qui méritait d'être
tranché : le coût par frame des dix orbites planétaires reste **nul**, leur
buffer étant toujours écrit à la seule reconstruction de fenêtre. Une expansion
côté CPU aurait créé de toutes pièces 81 920 sommets recalculés soixante fois par
seconde pour une donnée inchangée.

**Deux écarts avec la spec, tous deux des ajouts.**

1. **La profondeur est plafonnée au near plane**, extrait de la matrice de
   projection, au lieu d'utiliser `−z` en espace vue tel quel. La spec §4.0
   signale que le sommet passant derrière la caméra est un cas nominal en vue
   spacecraft, mais ne donne pas de parade : `−z` y devient négatif, retourne la
   demi-largeur et vrille le quad qui enjambe le near plane.
2. **La tangente de repli préserve le sens de la polyligne.** Sur deux points
   confondus — cas réel, le sommet de tête de la trajectoire de mission — prendre
   la première corde non nulle venue peut donner une tangente inversée, donc un
   nœud papillon au sommet.

**Ce qui reste à juger à l'œil, et ne peut pas l'être autrement.** La
cohabitation du fondu alpha avec le `FilterPostProcessor` (spec §7.7, le seul
inconnu technique du lot), et les largeurs de départ — 2,5 px pour une orbite,
3,5 px pour une trajectoire (spec §7.4, lot 4). Ce qui *a* été vérifié : une
session de 24 minutes en profil Core / GLSL 1.50, orbites planétaires **et**
trajectoire d'une LEO calculée puis rendue, sans une seule exception au log —
donc shaders compilés, uniforms liés, et les deux consommateurs exercés en
conditions réelles.

**Ce que cela ouvre**, et qui était la vraie valeur de l'item : la largeur et la
couleur sont des uniforms, donc `NAV-5` et la mise en avant de la mission active
sont des `setFloat` ; l'abscisse curviligne est déjà dans le maillage, donc les
tirets animés sont trois lignes de fragment. Voir la spec §11.

**Spec.** [`effects-roadmap.md`](../graphics-effects/effects-roadmap.md) §9.4.1,
remplacé par [`ribbon-lines.md`](../graphics-effects/ribbon-lines.md).

### NAV — Caméra, timeline, navigation

#### ~~NAV-1 — Transitions de caméra~~ — ★4 ◆2 M — **résolu le 2026-08-10**

> Spec de départ : [`docs/camera/01-view-transitions.md`](../camera/01-view-transitions.md).
> Le texte ci-dessous est conservé tel qu'écrit avant le chantier ; ce qui suit recense
> ce que l'implémentation a démenti ou ajouté.
>
> **Écarts à la spec.**
> - **Distance interpolée géométriquement**, pas linéairement (spec §3.5). Les cibles
>   couvrent neuf ordres de grandeur (800 unités solaires pour le système, `5e-7` pour un
>   spacecraft) : un lerp linéaire passe l'essentiel de sa durée près de la grande valeur
>   puis s'effondre sur quelques frames.
> - **Le pivot spacecraft inclut l'offset orbital.** La spec §3.6 le disait sub-pixel ;
>   il vaut ~7000 km, quatre ordres de grandeur au-dessus de la distance d'arrivée. En
>   visant le parent, `PLANET → SPACECRAFT` plongeait au centre de la Terre.
> - **Pivots lus sur les translations locales**, pas `getWorldTranslation()`, et pivot
>   source pris sur la caméra plutôt que supposé à l'origine (pour ne pas casser une vue
>   pannée).
> - **Orientation animée** (v2, hors spec initiale qui la déclarait « conservée ») :
>   alignée sur la direction de trajet pour Planet/Spacecraft, retour à l'orientation par
>   défaut pour Solar, convergence sur les 35 % premiers de la durée. Sans elle la
>   transition vise du vide pendant tout le trajet.
>
> **Trois bugs révélés — pas créés — par le chantier**, tous corrigés :
> 1. `LodView` promouvait en 3D n'importe quel corps sur le seul critère de la taille
>    projetée, et retirait son icône ; or `SceneGraph.showBodySpatial` cule tous les
>    ancres near sauf celui du corps focus. Une planète visée par une transition
>    disparaissait donc complètement sur la dernière seconde de l'approche. D'où le
>    paramètre `allow3d` de `BodyView.updateScreen`.
> 2. `farFloor` était posé par branche de mode : pendant un fly-in le mode reste SOLAR,
>    le plan far suivait la distance jusqu'à son plancher de 10 unités et balayait le
>    Soleil et les orbites hors du viewport. `FocusView` connaît désormais la destination
>    en cours et `isPlanetScale()` répond pour l'un ou l'autre bout.
> 3. **`OrbitCameraAppState` était attaché en dernier**, donc après ses consommateurs :
>    jME rafraîchit les transformées monde à la demande, si bien que `PlanetHudMarkers` et
>    `MissionOrchestrator` appariaient une position fraîche avec la caméra de la frame
>    précédente. Latent tant que la caméra ne bougeait qu'à la souris. Il est maintenant
>    attaché juste après `FloatingOriginAppState`.
>
> **Reste ouvert.** Le pop icône → modèle 3D à l'arrivée (8 px → ~220 px) est inhérent au
> « un seul corps, sur l'origine » du near viewport. Les missions n'apparaissent qu'à la
> dernière frame d'un `SOLAR → PLANET`, volontairement : leur trajectoire est dessinée
> dans le near viewport, dont l'origine est encore la source. Et voir
> [`docs/bugs.md`](../bugs.md) pour le jitter sur Pluton.

**Pourquoi.** Aujourd'hui tout changement de vue est un cut sec en un frame :
l'utilisateur perd le fil spatial entre « d'où je viens » et « où je suis ».

**État.** La spec est complète et prête à coder : `CameraTransitionConfig`,
`Easing`, `TransitionTarget` scellé, `CameraTransitionAppState`, blocage centralisé
des entrées, ordre d'attachement des AppStates. Rien n'existe encore côté code
(`states/camera/` ne contient que floating origin, near sync, orbit cam, view mode).

**Piège principal**, déjà identifié par la spec : `CameraTransitionAppState` doit
être attaché **avant** `FloatingOriginAppState`, sinon un sursaut apparaît au
basculement de mode.

**Spec.** [`docs/camera/01-view-transitions.md`](../camera/01-view-transitions.md).

#### ~~NAV-2 — Timeline indexée sur le temps + marqueurs d'événements — ★4 ◆3 M~~ — **RÉSOLU le 2026-08-15**

> **Livré conforme à la spec, sans écart de conception.** Une seconde capsule de
> 600 × 72 posée 8 px au-dessus de la capsule temporelle, montrant la mission du
> focus télémétrie sur un axe **linéaire** : barre de phases teintée par
> `MissionPhaseShading` — la même table que la trajectoire 3D, jamais une
> seconde —, marqueurs de transition déclusterisés, graduations `T+`, tête de
> lecture, pastille d'écart quand `now` sort de la fenêtre, tooltip au survol et
> `seek` au clic. Sans éphéméride, le widget ne s'affiche pas ; le toggle partage
> la condition de présence de la télémétrie et n'a donc **pas** d'état grisé.
>
> **Le seul code existant modifié** est celui qu'annonçait la spec §12.1 :
> `TimelineWidget` s'abonne désormais à `SpeedChanged` et redérive son index
> depuis l'horloge, via un `SpeedStepper.speedToIndex(double)` ajouté pour ça.
> Le défaut était latent — la capsule n'écoutait personne — et ce chantier
> aurait été le premier à le déclencher, en remettant la vitesse à ×1.
>
> **Quatre défauts trouvés en cours de route**, dont deux que ni le compilateur
> ni les tests ne pouvaient voir : `UiKit.mono(9)` retombe **en silence** sur la
> police par défaut de Lemur (share-tech-mono n'est fourni qu'en 10/11/12/14), et
> `U+2212 MINUS SIGN` n'a pas de glyphe dans le `.fnt` — le `T−` de la pastille
> épinglée se serait affiché `T `. Les deux autres étaient un repli de choix de
> pas qui violait son propre invariant sur une fenêtre de 400 jours, et un
> débordement `int` dans le compte de graduations au-delà de ~68 ans.
>
> **Constaté à l'écran** sur une LEO à horizon 3 jours : la barre est quasi
> monochrome et les six transitions tiennent en une grappe `×3` collée au bord
> gauche. C'est le comportement que le §2 de la spec annonce, pas un défaut de
> rendu — et c'est exactement ce que la déclusterisation existe pour rattraper.

**Pourquoi.** `ScrubberTrack` n'avait aucune notion de date : ses 21
graduations sont décoratives et indexées sur la **vitesse**. Une timeline de
simulation orbitale qui ne représente pas le temps est une anomalie.

**Dépendait de `MIS-8`, désormais levé.** La fenêtre représentée par la piste,
c'est la durée de la mission : tant que cette durée était une constante
arbitraire, la piste l'était aussi et aurait été à refaire. `MissionHorizon`
donne maintenant cette durée, donc la piste peut s'indexer dessus directement.

**À faire, dans l'ordre.**
1. Trancher la fenêtre représentée : durée de la mission sélectionnée, ou
   fenêtre glissante autour de `now()` ? (cf. question ouverte n°2.)
2. Fonction temps ↔ position, puis marqueurs aux transitions de stages de la
   mission sélectionnée.
3. Hover → tooltip (stage + timestamp), click → `clock.seek(...)`.

Synergie forte avec `RND-3` : mêmes frontières de stages, mêmes couleurs — la
timeline et la trajectoire 3D doivent partager la table de couleurs, pas en
avoir deux.

**Spec.** [`docs/navigation/02-timeline-mission.md`](../navigation/02-timeline-mission.md) —
le fait qui commande le design (les phases propulsées font ~2 % d'une GEO, §2),
la fonction temps ↔ position (§5), l'anatomie de la capsule jumelle et ses trois
traitements maquettés (§6, §15), la déclusterisation des marqueurs (§8) et le
piège de désynchronisation de la vitesse (§12.1).

#### ~~NAV-3 — Scrub continu — ★3 ◆2 S~~ — **RÉSOLU le 2026-08-15**

**La mise en garde de cette fiche était fausse, et c'est ce qui a fait le
chantier.** Elle annonçait que « chaque `seek` reconstruit toute la fenêtre
éphéméride (`EphemerisWorker.onSeek`) ». Le mécanisme existe, le débit non :
`onSeek` ne fait que poser la date dans un `AtomicReference`, et le tick du
worker — 200 ms fixes, thread démon — commence par `pendingSeek.getAndSet(null)`.
Les positions intermédiaires sont donc **déjà écrasées par construction** : 60
seeks en une seconde en produisent 5, et seule la dernière compte. L'application
n'a par ailleurs que deux abonnés à l'horloge, dont un qui ne fait que ce
transfert. L'étranglement que cette fiche demandait d'écrire était déjà là, une
couche plus bas.

Le throttle a été gardé quand même, à ~10 Hz, mais comme **borne délibérée** et
non comme rustine : le widget ne dépend plus de ce que cette coalescence reste
vraie, et les futurs consommateurs de `seek` héritent d'un débit sain.

**La cohabitation piste-vitesse / piste-temps ne s'est jamais posée** : `NAV-2`
l'avait close en séparant les widgets (cf. question ouverte n°2). Le
`ScrubberTrack` de la capsule reste indexé sur la vitesse et n'est pas touché.

**Livré.** `ScrubGesture` (sans dépendance JME, 14 tests) est un automate
`IDLE`/`PRESSED`/`DRAGGING` : sous un seuil de **3 px** le relâchement rejoue le
clic discret de `NAV-2` — rail *ou* grappe, avec sa sémantique de première
transition intacte — au-delà le geste s'engage en drag et n'en revient jamais.
Pendant le glissement la tête de lecture suit le curseur **à la fréquence
d'image**, pas l'horloge : la laisser suivre l'horloge la faisait revenir à la
dernière position émise entre deux émissions, donc trembler à 10 Hz. L'horloge
est mise en pause au franchissement du seuil et **rend au lâcher l'état de
lecture qu'elle avait trouvé** ; la vitesse n'est jamais touchée, contrairement
au bouton « début de mission », parce qu'un scrub ne doit pas écraser un réglage
posé par l'utilisateur à chaque geste.

**Un défaut fermé en passant.** Un appui arrivant alors qu'un drag est encore
ouvert — bouton relâché hors fenêtre, le cas que `ScrubberTrack` ne gère pas non
plus — aurait laissé l'horloge en pause sans que rien ne glisse à l'écran. Le
geste est donc abandonné avant chaque nouvel appui, ce qui rend « en pause » et
« en train de glisser » indissociables.

**Reste ouvert.** `MissionTimelineWidget` est passé de 689 à 842 lignes ; sa
section interaction forme désormais un bloc qui aurait sa place en collaborateur
à côté de `PhaseBar`, `PhaseMarkers` et `TimelineTooltip`. Non fait, hors
périmètre de l'item.

#### ~~NAV-4 — Breadcrumb de navigation 3D — ★3 ◆2 M~~ — **RÉSOLU le 2026-08-15**

**Livré.** `ui/breadcrumb/BreadcrumbWidget.java` (la bande et ses segments),
`ui/breadcrumb/BreadcrumbStyles.java` (quatre sélecteurs Lemur déclarés dans le
style `form`, aucun override d'attribut à la construction) et
`states/scene/BreadcrumbWidgetAppState.java` (poll de `(mode, body)`,
reconstruction sur changement seul). Côté hôte : un `breadcrumbNode` dans
`GuiGraph`, et deux constantes dans `AppStyles` —
`BREADCRUMB_BAND_HEIGHT_PX` et `HUD_TOP_OFFSET_PX`, dont dépendent maintenant le
menu applicatif, le panneau des missions et la télémétrie.

**`NAV-1` avait déjà réglé la moitié du travail.** La spec §5.2 demandait de
rendre public `PlanetPoseAppState.onSelectPlanet` ; entre-temps ce clic 3D a été
routé vers `CameraTransitionAppState.requestPlanet`, qui est devenu le point
d'entrée unique du changement de focus. Le widget s'y branche tel quel :
`PlanetPoseAppState` n'est pas touché, et un segment cliqué est **exactement** le
clic 3D — même animation, mêmes gardes — comme le segment racine est exactement
la touche `R`.

**Trois décisions prises à l'écran, contre la lettre de la spec.**

1. **Segments alignés à gauche**, pas centrés (spec §3 et §5.1). Une rangée
   centrée glisse latéralement à chaque changement de focus et se relit comme un
   autre widget ; alignée, elle démarre sur la verticale du menu et n'en bouge
   plus. Le texte — pas le cadre — est posé sur le bord gauche visible de la
   pastille `ORBITLAB`, dérivé de `HUD_MARGIN_PX + FormStyles.BUTTON_INSET_X`
   plutôt que mesuré, l'inset du bouton étant sorti en constante pour cela.
2. **L'écart sous la bande est `HUD_STACK_GAP_PX` (8 px)**, quand la table de la
   spec §5.5 posait `BREADCRUMB_BAND_HEIGHT_PX + HUD_MARGIN_PX`. Ce qui sépare la
   bande de la ligne du dessous est un empilement de widgets, pas un bord d'écran.
3. **Le fond de bande est tranché** — bleu très sombre à 10 % d'alpha : c'est la
   seule valeur que la spec §5.5 renvoyait explicitement au maquettage. Elle tient
   en une ligne du sélecteur.

**Un point de la spec étendu.** §5.5 ne cadrait que la chaîne haut-gauche ; la
télémétrie, ancrée haut-droite à `screenHeight − HUD_MARGIN_PX`, serait passée
sous la bande. Elle mesure désormais depuis la même constante. Le seul rescapé
est le plafond de `WindowDragHandler`, laissé à la marge HUD **délibérément** :
réserver toute la chaîne d'ancrage sur toute la largeur ne laissait que deux
pixels de course verticale à la fenêtre de gestion, et ce compromis est déjà
écrit dans `clamp`. La bande peut donc recouvrir le coin gauche d'un en-tête
traîné tout en haut.

**Vérifié.** Application lancée, captures aux trois profondeurs (`Solar system`,
`> Earth`, `> Earth > Moon`) : hauteur de bande constante, menu et panneau posés
dessous sans trou ni recouvrement, segment courant en accent, ancêtres en
secondaire. **Non vérifié faute de souris scriptable** : le clic sur un segment
et le cas `SPACECRAFT` (§6, points 4, 5 et 7 de la spec).

**Reste ouvert.** La chaîne d'ancêtres et la règle « qui est cliquable » n'ont
pas de test unitaire, alors qu'elles sont du modèle pur et se testeraient comme
`AppMenuModelTest`. Et la descente vers les fils reste la V2 de la spec (§7) :
c'est elle qui rendra le widget vraiment utile une fois les missions lunaires en
place, `Solar system > Earth > Moon` étant déjà exactement ce qu'il affiche.

**Spec.** [`docs/navigation/01-breadcrumb.md`](../navigation/01-breadcrumb.md) —
à relire avant la V2 : ses §3 et §5.1 décrivent encore des segments centrés, et
son §5.2 un `PlanetPoseAppState` à modifier.

### FX — Effets graphiques

#### ~~FX-1 — Bloom sur le Soleil — ★3 ◆1 S~~ — **RÉSOLU le 2026-08-09**

**Pourquoi.** Le Soleil était un disque mat au centre d'une scène qui avait
désormais une skybox et un éclairage directionnel : c'était l'élément qui
détonnait.

**Livré en deux morceaux, et pas comme prévu.** Le pari « `BloomFilter` seul sur
le viewport far » ne tenait pas ; trois hypothèses de la rédaction d'origine se
sont révélées fausses à la mesure. Détail technique complet en
[`effects-roadmap.md` §3.3](../graphics-effects/effects-roadmap.md) — ici le
résumé et les corrections.

- **Le viewport est `near`, pas `far`.** `LodView` coupe un corps en deux : une
  ancre de position sans géométrie sous `farBodiesNode`, le modèle GLTF sous
  `nearBodiesNode`. Le viewport far ne dessine que les lignes d'orbite ; un
  processor posé dessus n'aurait rien eu à faire briller.
- **Le halo est de la géométrie, pas du post-process.** Un flou écran travaille
  en pixels, donc sa portée et son plancher de résolution sont le même nombre :
  assez large pour un gros plan imposait un plancher d'une quinzaine de pixels,
  et tout astre projetant plus petit recevait un **carré** au lieu d'un halo.
  D'où `engine/scene/body/CoronaView.java` + `resources/MatDefs/Fx/Corona.*` —
  quad face-caméra, décroissance radiale procédurale, masquage gratuit par le
  depth test. Le bloom `GlowMode.Objects` reste, réduit au débord vers
  l'intérieur qui adoucit le limbe.
- **`GlowColor` n'existe pas sur le matériau du Soleil.** Le GLTF est chargé en
  `PBRLighting.j3md`, dont la technique `Glow` lit `Emissive`.
  `AssetFactory.applyGlow` gère les deux orthographes.

**Le tone mapping n'en fait pas partie, et ne l'aurait pas aidé.** Les passes
internes de `BloomFilter` sont en `RGBA8` : le glow est écrêté à 1 avant d'être
flouté, donc pousser le Soleil au-delà ne lui donne rien. Le gain de §4.5 porte
sur le reste de la scène, pas sur le halo — item toujours ouvert côté
`effects-roadmap.md`.

**Preuve.** `states/fx/PostFxAppState.java` (chaîne de post-process et
framebuffer partagé, réutilisable par `FX-2` et les god-rays),
`states/fx/SmoothBloomFilter.java`, `engine/scene/body/CoronaView.java`,
`resources/MatDefs/Fx/Corona.{j3md,vert,frag}`, `AssetFactory.applyGlow`.

#### ~~FX-2 — Éclipses / pénombre inter-corps — ★4 ◆3 M~~ — **RÉSOLU le 2026-08-29**

**Pourquoi.** Un vaisseau qui traverse le cône d'ombre de la Terre, la Lune qui
s'éteint en entrant dans l'ombre terrestre : un phénomène que la simulation
calcule déjà correctement — les positions, via l'éphéméride — et que le rendu
ignorait.

**Réévaluation par rapport à `effects-roadmap.md` §6.3 (qui la classait ◆4),
confirmée.** `MatDefs/Light/WrapLighting.frag` est notre seul shader
d'éclairage, et son terme diffus tient en une ligne — un point d'injection
unique, partagé par les planètes et par le vaisseau de chaque mission.

**Le découpage en « deux niveaux » de l'énoncé initial n'a pas survécu à
l'implémentation, et c'est un mieux.** Un scalaire CPU pour les petits corps
puis un chemin fragment séparé pour la Terre aurait dupliqué la géométrie de
l'occulteur (position, rayon) que les deux cas calculent de toute façon. Le
découpage retenu — [`docs/eclipses/01-decoupage.md`](../eclipses/01-decoupage.md)
— construit un seul mécanisme (test sphère/rayon par fragment, formule de
recouvrement de deux disques avec une vraie pénombre, pas un bord dur) dès le
premier lot ; les trois lots qui suivent n'ajoutent chacun qu'un occulteur de
plus, jamais une nouvelle technique :

- **L1** — le vaisseau s'assombrit dans l'ombre du corps central de son arc
  courant (Terre ou Lune selon la mission).
- **L2** — la Lune s'assombrit dans l'ombre de la Terre, avec dégradé si
  l'éclipse est partielle (gratuit, le mécanisme étant déjà par-fragment).
- **L3** — la Terre montre la tache d'ombre de la Lune (éclipse solaire) — le
  seul des trois qui n'ajoute que du câblage, aucun nouveau code shader.
  Confirmé à l'écran sur l'éclipse réelle du 12/08/2026 (trace Groenland →
  Islande → Espagne) : un cœur nettement plus sombre (l'umbra) se détache de
  la pénombre environnante au zoom, pas un simple lavis uniforme.

**Découverte en cours de route : Orekit porte déjà un détecteur d'éclipse,
inutilisé dans ce dépôt.** `org.orekit.propagation.events.EclipseDetector` /
`OccultationEngine` — déjà une dépendance — donnent une géométrie d'éclipse
réelle (séparation angulaire, rayons apparents), évaluable hors propagation à
partir d'une position brute. Sert d'oracle indépendant pour L1/L2
(`EclipseGeometryOrekitAgreementTest`, `MoonEclipseOrekitAgreementTest`)
plutôt que d'une vérité à réécrire soi-même.

**Deux écarts avec le découpage, tous deux des corrections trouvées en
implémentant.** Réutiliser `lightDir` (la direction lumière déjà calculée)
pour le test d'occultation, comme le découpage le prévoyait, s'est révélé
impossible : cette direction est en repère vue (c'est ainsi que JME
l'envoie), alors que le test compare des positions en repère monde pour
éviter une dépendance à la matrice caméra au point d'appel — d'où un
quatrième uniform (`m_SunDirection`, monde, calculé côté CPU) en plus des
trois prévus. Et l'occulteur du vaisseau (L1) n'est pas systématiquement
`-point.position()` : ce n'est exact que quand la caméra regarde le corps que
le vaisseau orbite réellement ; le cas général passe par
`TrajectoryArc.convertPosition` pour rester exact même en regardant un autre
corps pendant qu'une mission vole ailleurs.

**Limitation connue, non résolue : le rendu de la pénombre sur le vaisseau
est correct mais peu lisible.** Le vaisseau (50 m) est minuscule devant le
cône d'ombre terrestre : toute sa surface visible traverse la pénombre au même
instant, donc il s'assombrit en bloc plutôt que de montrer un dégradé — pas de
ligne de terminateur comme sur un grand corps. Constaté à l'écran : un lavis
gris terne et peu contrasté plutôt qu'un assombrissement qui se lit clairement
comme « entre dans l'ombre ». Cause probable : le terme ambiant (`AmbientSum`,
non affecté par le facteur d'éclipse) reste constant pendant que le terme
diffus chute, et son plancher devient proportionnellement plus dominant à
mesure que le vaisseau s'enfonce dans la pénombre — ce qui aplatit le
contraste de la texture plutôt que de simplement l'assombrir. Pistes non
essayées :
- réduire aussi l'ambiant pendant la transition (pas jusqu'à zéro — il
  représente un plancher de lumière diffusée plausible — mais
  proportionnellement à l'éclipse), pour préserver le contraste de texture
  plus loin dans la transition ;
- appliquer une courbe non linéaire (un gamma < 1, ou un `smoothstep` plus
  raide) au facteur d'éclipse avant qu'il multiplie le diffus, pour que la
  transition se lise comme un bord plutôt que comme un fondu progressif
  uniforme ;
- poser un plancher de luminosité perçue plutôt qu'une pure multiplication
  linéaire — l'œil ne perçoit pas l'assombrissement de façon linéaire.

**Spec.** [`docs/eclipses/01-decoupage.md`](../eclipses/01-decoupage.md).

### PHY — Physique

#### ~~PHY-1 — Atmosphère : la brique, désactivée par défaut — ★4 ◆3 L~~ — **RÉSOLU le 2026-08-21**

**Périmètre.** Trois lots : baseline mesurée et prototype jetable, la brique
éteinte (record `AerodynamicProperties` au catalogue agrégé sur l'étage actif,
enum `AtmosphereModel` avec cache, `DragForce` conditionnel, choix porté par
`MissionSpec`), puis **un vol réellement volé avec traînée**. Le lot de
renommage `GravitationalContext` → `FlightContext` initialement prévu a été
dissous le 2026-08-20 : `FlightContext` **compose** `GravitationalContext`
`(gravity, drag)` au lieu de le remplacer, donc il n'y a rien à renommer.

**Contrainte non négociable.** `AtmosphereModel.NONE` ⇒ propagation **identique
au bit près** à aujourd'hui. C'est ce qui permet de livrer la brique sans
toucher aux baselines, et donc de la livrer tôt. La composition la rend
**structurelle** : `drag == null` ⇒ aucun `DragForce` monté, donc la liste de
forces est identique par construction et les gates ne font que le confirmer.

**Deux affirmations de l'étude d'impacts sont périmées** et ne doivent pas être
reprises : le chantier ne se réduit pas à `GravityTurnManeuver` et
`TransfertTwoManeuver` (21 sites de construction de propagateur, mais un sillon
commun installé par `PHY-4`), et le piège Orekit de la séparation d'étage
n'existe pas ici — `StageLegRunner` construit un propagateur par étage, donc une
surface figée à l'entrée d'étage est exacte. Le détail est au §2.2 du découpage.

**Ce que PHY-1 s'interdit et lègue à `PHY-2`** : reprendre les ISP « proxy » du
catalogue (296 s Falcon Heavy, 300 s Ariane 62), qui compensent déjà la traînée
dans la mauvaise variable. PHY-1 en chiffre la dette sans y toucher.

**Livré.** Les trois lots sont fermés. `L2` a volé le chemin drag-on et l'a confronté à
l'analytique : accélération exacte **au bit** contre `0,5·ρ·v_rel²·Cd·S/m` sur deux états
(l'oubli de la co-rotation vaut +13,7 % au module à l'équateur et 3,57° de direction au
pôle), décroissance d'un parking 250 km sur 24 h à **1 %** de la formule séculaire, sanity
800 km à deux bornes. Le lot n'a écrit **aucune ligne de production** : le câblage de `L1`
suffisait.

**Ce que `PHY-2` reçoit** : l'écart Harris-Priester / NRLMSISE-00 (**22,6 %**), la dette
des ISP proxy (**408 m/s** Falcon Heavy S1, **671 m/s** Ariane 62 S1 — au-dessus des
100–300 m/s de traînée annoncés, donc le proxy paie plus que la seule traînée), les deux
contraintes dures de `L0` (une borne d'altitude est nécessaire à la terminaison,
`ReentryGuard` est inopérant avec traînée) et trois approximations de catalogue. Le
surcoût compute demandé n'est **pas** un pourcentage : mesuré le 2026-08-21, une
optimisation LEO-400 drag-on ne traîne pas, elle devient **infaisable** — pleins
dimensionnés sans atmosphère, S2 épuisé avant coupure, transfert impossible.

**Spec.** Découpage : [`docs/atmosphere/02-decoupage.md`](../atmosphere/02-decoupage.md)
(amendé le 2026-08-20, §9). Baseline mesurée :
[`docs/atmosphere/03-baseline-L0.md`](../atmosphere/03-baseline-L0.md). Conception de
la brique éteinte : [`docs/atmosphere/04-conception-L1.md`](../atmosphere/04-conception-L1.md).
Le vol allumé : [`docs/atmosphere/05-conception-L2.md`](../atmosphere/05-conception-L2.md).
Étude d'impacts (2026-05-05, à lire avec le §2.2 du découpage) :
[`docs/atmosphere/01-impacts-fonctionnels-techniques.md`](../atmosphere/01-impacts-fonctionnels-techniques.md).

#### ~~PHY-4 — Socle multi-corps — ★5 ◆4 L~~ — **RÉSOLU le 2026-08-18**

**Pourquoi.** C'était le prérequis dur des deux missions lunaires. Tout était
propagé dans un repère central unique et purement gravitationnel autour d'un
corps.

**À faire.**
- `ThirdBodyAttraction` (Lune, Soleil) dans les propagateurs concernés.
- Orchestration des transitions de sphère d'influence Terre → Lune : bascule de
  repère central, concaténation des arcs.
- Adaptation de `MissionEphemeris` à une trajectoire multi-arcs (repères
  différents selon le segment) — impacte `MissionTrajectoryRenderer`, qui suppose
  aujourd'hui un repère unique.
- Rendu : la trajectoire lunaire traverse deux échelles ; vérifier la cohabitation
  avec les deux viewports et la floating origin (cf. `spacecraft-view-artefacts.md`
  §5.3.3, qui propose un troisième viewport « mid » — à considérer ici, pas avant).

**Prudence sur l'estimation.** ◆4 / L couvre le patched-conic avec 3ᵉ corps, pas
une propagation N-corps complète ni l'optimisation multi-arcs. Si `MIS-4`
demande plus, c'est ici que ça se verra.

**Découpage.** [`docs/multi-corps/01-decoupage.md`](../multi-corps/01-decoupage.md)
— six lots, chacun fermé par un test exécutable : baseline mesurée, corps central
explicite (refactor à trajectoire identique), 3ᵉ corps en perturbation, éphéméride
multi-arcs à un seul arc, bascule de SOI testée **hors mission**, rendu bi-échelle,
puis un arc lunaire de bout en bout. Le document relève aussi les trois coutures
que le code impose : la Terre en dur sur une trentaine de sites, les **seize**
endroits qui construisent un propagateur de vol, et une éphéméride dont les points
ne portent aucun repère.

### Ce qui a été livré

Les six lots, chacun avec son document de conception dans `docs/multi-corps/` et
sa section de fermeture chiffrée. Le dernier
([`08-conception-L6.md`](../multi-corps/08-conception-L6.md)) vole un transfert
translunaire complet : orbite de parking à 185 km, injection impulsionnelle sur
un seed Lambert visé en plan B, bascule Terre → Lune à 74 h, **périlune volé à
100,4 km pour 100 visés**, discontinuité de **0,000000 m** à la frontière d'arc.
Suite complète : **808 tests, 0 échec**.

**Les deux gates ont tenu du premier au dernier lot** —
`CentralBodyBaselineTest` à `0.0` sur ses 62 frontières et
`MissionPolylineBaselineTest` à l'identique. C'était la promesse du découpage et
c'est ce qui a rendu le chantier sûr : chaque lot pouvait bouger la couture sans
risquer une mission terrestre.

**Le troisième viewport n'a pas été nécessaire** (question tranchée n° 3, §7) :
`L5` §5.3 l'a écarté sur mesure, `L6` §12.5 l'a confirmé à l'écran sur la
première trajectoire lunaire réelle.

**Trois estimations du découpage démenties**, toutes dans le sens de la
simplicité : la Terre en dur ne concernait que **deux** sites sur le chemin d'un
vol lunaire et non une trentaine ; le rendu bi-échelle s'est fait sans découper
le ruban ; et l'architecture « un corps garé sur l'origine », annoncée comme
l'obstacle, s'est révélée être le mécanisme.

**Ce qui reste ouvert** : la bande morte ε de la bascule n'est pas calibrée — il
faut pour cela une trajectoire **capturée**, qui reste dans la sphère, donc
`MIS-4`. Et
[`BUG-7`](../bugs.md#bug-7--les-gates-de-non-régression-tombent-quand-un-test-lunaire-les-précède-dans-le-même-jvm),
trouvé pendant `L6` mais antérieur à lui : les gates tombent quand un test
lunaire les précède dans le même JVM.

---

### MIS — Missions

#### ~~MIS-1 — Deuxième lanceur au catalogue — ★3 ◆1 S~~ — **RÉSOLU le 2026-08-09**

**Pourquoi.** `catalog/Launchers.java` ne contenait que `FALCON_HEAVY`.
`AscentProfile` était déjà un champ de `LauncherModel` consommé par `LEOMission`
et `GEOMission` : tout le câblage « profil de vol dépendant du lanceur »
existait mais **n'était démontré par aucun second cas**.

**Ce qui a été livré, en deux temps.**

- **Le lanceur** (commit `f9ea80c`) — `ARIANE_62`, boosters P120C et corps
  central agrégés en un seul étage faute de représentation du fonctionnement en
  parallèle, avec son propre `AscentProfile` (montée verticale plus courte à 6 s,
  coast interétage de 5 s pour le chill-down du Vinci). Le câblage est donc
  désormais exercé par deux lanceurs aux profils réellement distincts. Le coût de
  l'agrégation est chiffré dans le Javadoc de `Launchers.ARIANE_62` et dans
  [`docs/launchers/01-ariane-62.md`](../launchers/01-ariane-62.md) : la forme de
  l'ascension n'est pas celle de ce lanceur, mais la mission se ferme (LEO 400 km
  à moins de 1,2 km de la cible, 21,7 % de l'ULPM en réserve).
- **Le rendu** — `engine/scene/spacecraft/LauncherAssets.java` associe chaque id
  du catalogue à son mesh GLTF, et `MissionRenderer.modelPathFor` le résout depuis
  le `MissionSpec` de la mission au lieu du chemin en dur vers le Falcon Heavy.
  Le lanceur est lu sur le **spec** et non sur la `Mission` : celle-ci ne garde
  que le `VehicleStack` assemblé, où l'identité du lanceur est déjà dissoute en
  masses et propulsion. `MissionOrchestratorAppState` détruit et recrée le
  renderer quand une édition du wizard change de lanceur — le mesh est figé dans
  le `LodView` à la construction, et l'entrée garde son identité à travers
  l'édition, donc rien d'autre ne l'aurait reconstruit.

**Limite connue — le mesh de l'Ariane est un Ariane 5, pas un Ariane 6.** Aucun
modèle 3D d'Ariane 6 n'était disponible. Seule la silhouette est fausse : les
masses, la propulsion et le profil de vol restent ceux de l'Ariane 62 du
catalogue, et rien de fonctionnel n'en dépend — mais l'écran ne montre pas le
lanceur qui vole. Le remplacement est une ligne de `LauncherAssets.MODEL_PATHS`
et l'asset, le jour où un mesh Ariane 6 existe. Deux conventions à vérifier sur
tout mesh candidat, parce que le code les suppose sans les mesurer : nez sur
**+Y** après la transformation racine du GLTF (`SpacecraftPresenter` applique une
correction unique pour tous les vaisseaux) et vaisseau normalisé à ~1 unité
(`Model3dView` l'échelonne à partir du seul rayon).

**Note pour la suite** : le besoin d'origine mentionnait « avant les missions
lunaires, qui voudront un étage supérieur cryogénique » — l'ULPM/Vinci de
l'Ariane 62 le fournit, avec un coast déclaré compatible d'une remontée jusqu'à
l'apogée GTO (5 h 15) là où l'étage du Falcon Heavy s'arrête à 2 h.

#### ~~MIS-7 — `EarthOrbitMission` paramétrable — ★4 ◆2 M~~ — **RÉSOLU le 2026-08-16**

**Pourquoi.** Trois types de mission — polaire, SSO, MEO — sont à ★4/★5
d'intérêt et quasi gratuits en physique : il ne manquait que l'inclinaison comme
paramètre au lieu de valeurs implicites Kourou. C'était en plus un **prérequis
du rendez-vous** (matcher le plan de la cible, c'est exactement choisir un azimut
et une inclinaison).

**Spec.** [`docs/earth-orbit/01-mission-terre-parametrable.md`](../earth-orbit/01-mission-terre-parametrable.md)
(P1, physique) et [`02-wizard-orbites-terrestres.md`](../earth-orbit/02-wizard-orbites-terrestres.md)
(P2, UI). Les deux portent leur bilan de livraison.

**Ce qui a été livré, en deux temps.**

- **P1 — la physique** (commit `27f4a60`). `LEOMission` devient
  `EarthOrbitMission`, `MissionSpec.Leo` devient `MissionSpec.EarthOrbit` ;
  `LaunchPlane` et `NodeBranch` portent le plan visé ; l'attitude gagne un mode
  à **plan commandé** et le trim de plan entre dans la composition dès que le
  plan est commandé ; `Physics.sunSynchronousInclination` calcule la SSO ;
  `PropellantBudget` applique une assistance de rotation **signée**.
- **P2 — le wizard** (commit `56eb362`). `MissionProfile` — cinq profils
  (LEO, polaire, SSO, MEO, GEO) **dérivés du spec, pas stockés** —, champ
  d'inclinaison, réordonnancement des étapes, refus MEO remonté à l'écran.
  Aucune physique touchée.

**Le résultat contredit l'énoncé d'origine sur un point, et c'est le plus
instructif.** « Il ne manque que `launchAzimuth` » était faux : la mesure a montré
que **l'azimut n'avait aucune autorité réelle sur le plan orbital** (le kick ne
tourne presque rien, la suite le fige). C'est le pilotage à plan commandé, et non
un paramètre d'azimut, qui donne les 94-96 % d'autorité mesurés. Trois autres
estimations de la spec ont été démenties par la mesure (§13 de la spec P1) : le
terme de pilotage explicite n'est pas dû, les « 1,8 % de repère » valent 0,01 %,
et le MEO n'était pas qu'une affaire de coast.

**Un résidu structurel, assumé.** La poussée reste dans le plan cible, donc elle
n'annule jamais la vitesse hors plan : le résidu d'inclinaison a une forme fermée
et il est **maximal au polaire**. `AscentPlaneControlTest` assère ce modèle, pas
une borne — la tolérance de 1° qu'espérait la spec est inatteignable par
construction.

**Non-régression tenue sans toucher une tolérance.** `EarthOrbitNonRegressionTest`
garde 7 cibles × 2 modes en ascension identique **au bit près**, et une clé
d'inclinaison absente continue de produire `LaunchPlane.dueEast(latitude)`. Les
références `AscentBaselineN2Test` ont été **ré-enregistrées** (graine 42), à
tolérances inchangées ; leur écart de 19,2 km sur le périgée LEO n'est pas une
dérive MIS-7 mais l'**étalement de l'ensemble acceptable de CMA-ES** — deux
candidats jugés équivalents par la fonction de coût sont distants de 19 km. C'est
une propriété de la fonction de coût, antérieure à MIS-7, et qui mérite sa propre
fiche.

**Reporté, pas oublié** : la branche de nœud n'est pas exposée à l'UI (toute
mission créée part sur `ASCENDING`, ce qui est la bonne branche pour une SSO) ;
le catalogue de sites de lancement n'est pas extrait ; le défaut d'horizon MEO
reste à 48 révolutions (~24 jours), honnête mais perfectible — le changer
déplacerait la bande d'altitude que mesure `MeoMissionTest`. Enfin `T1b`
(inclinaison après insertion complète) n'est pas écrit ; `MeoMissionTest` en donne
l'équivalent sur le seul profil MEO.

#### ~~MIS-2 — Fenêtres de lancement — ★4 ◆3 M~~ — **RÉSOLU le 2026-08-20**

**Pourquoi.** Sans elle, ni le rendez-vous ni la Lune ne convergent : la cible
n'est jamais au bon endroit au moment du lancement. C'est aussi ce qui donne
enfin un sens au champ « date de lancement » du wizard.

**À faire.** `LaunchWindowSolver` qui balaie une plage temporelle et liste les
créneaux (alignement de plan, RAAN cible, précession J2 ~5°/jour pour l'ISS,
géométrie Terre-Lune ~mensuelle). UI : timeline des créneaux ouverts dans le
wizard, avec le Δv associé.

**Ce qui a été livré.** Le solveur en trois passes — balayage grossier au pas du
problème, encadrement par triplet, section dorée puis bissection sur le seuil
d'acceptation — avec **une seule implémentation** et un point d'extension unique,
`LaunchWindowProblem`, qui porte ses trois échelles (pas de balayage, précision
d'affinage, récurrence). Deux critères : l'alignement de plan terrestre, fermé,
et l'injection translunaire, criblée en forme fermée puis confirmée par l'étape
de vol. Côté wizard, une sous-page `PLANNING` de l'étape des paramètres, à deux
échelles empilées, dont le clic écrit l'instant optimal dans le champ
`LAUNCH DATE` — lu comme un **plancher** à la création, l'aller-retour étant
mesuré à 0,0 s de dérive.

Mesures principales : créneau de **3 min 52 s** à 51,6° depuis Kourou, récurrence
d'**un jour sidéral**, plancher de coût non nul (1,1 m/s depuis Kourou, 35,9 m/s
depuis 45°) dû à l'écart entre latitudes géodésique et géocentrique — ce qui rend
le seuil **relatif** indispensable. Specs :
[`docs/mission-window/01-basics.md`](../mission-window/01-basics.md) et
[`02-timeline-wizard.md`](../mission-window/02-timeline-wizard.md).

**Ce qui n'a pas été livré, et pourquoi ce n'est pas un reliquat.** La
**précession J2** citée ci-dessus n'est pas modélisée : un RAAN saisi est un
*nombre*, pas une orbite, et le plan qu'il décrit reste fixe. Un plan réellement
en orbite régresse de 5,00°/jour à 400 km et 51,6°, soit ~570 m/s d'écart sur les
26 h que balaie le planificateur — dix fois la marge qui définit le créneau. Le
champ veut donc dire « le plan tel qu'il sera au décollage ». Suivre une cible qui
précesse est une **seconde implémentation de l'interface**, dont la normale est
fonction de l'époque, et elle arrive avec la source éphéméride TLE — c'est-à-dire
en `MIS-6`, où le graphe de dépendances la range. La couture qui l'accueillera est
en place.

#### ~~MIS-3 — Solveur de Lambert + repère LVLH — ★4 ◆3 M~~ — **DISSOUS le 2026-08-20**

> **Dissous, pas livré, et pas abandonné non plus** : les deux moitiés ont été
> reversées, l'une parce qu'elle existe déjà, l'autre parce qu'elle n'a pas
> encore de consommateur. La fiche est conservée pour que la décision ne se
> re-pose pas, et pour corriger deux affirmations fausses qu'elle portait.

**Ce que la fiche annonçait** : deux briques partagées, « à écrire une fois » —
un solveur de Lambert servant de seed analytique au CMA-ES et au ciblage
lunaire, et un service exprimant un état chaser en `(δr, δv)` relatif dans
`LOFType.LVLH`.

**Deux mesures ont vidé la fiche de son contenu.**

1. **Lambert n'est pas à écrire : il est en production depuis `PHY-4`.** La
   fiche citait `org.orekit.utils.IodLambert` — c'est l'outil d'*initial orbit
   determination*, pas celui qu'utilise le dépôt. Le lot L6 de `PHY-4` s'appuie
   sur `org.orekit.control.heuristics.lambert`, plus récent :
   `TranslunarInjectionPlan` y prend son seed keplérien
   ([:478](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/TranslunarInjectionPlan.java))
   puis le reconverge sous propagateur numérique par
   `LambertDifferentialCorrector`
   ([:534](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/TranslunarInjectionPlan.java)).
2. **Le multi-révolution n'est pas à implémenter non plus.** La fiche prévoyait
   Izzo 2014 « à défaut » ; la signature native est
   `solve(boolean posigrade, int nRev, LambertBoundaryConditions)` — `nRev` est
   un paramètre du solveur. Le dépôt le passe à `0` en dur, ce qui rend le
   multi-révolution **non vérifié**, mais vérifier n'est pas implémenter.

**Ce qui restait, et où c'est parti.**

- **Le seed Lambert générique** → **`MIS-4`**. Ce qui reste est une extraction :
  l'appel est aujourd'hui noyé dans une classe lunaire de 711 lignes qui fixe
  `posigrade = true` et `nRev = 0`. Le généraliser sans le consommateur qui en
  fixera la forme reviendrait à deviner une signature, et `MIS-4` est
  précisément ce consommateur — son `TLIBurnStage` de production en a besoin.
- **Le repère LVLH** → **`MIS-6`**. Orekit fournit `LOFType.LVLH` (et ses
  variantes `LVLH_CCSDS`, `VVLH`), et le dépôt pratique déjà l'idiome
  `LOFType` + `LofOffset` en quatre endroits, tous en `TNW`. La conversion tient
  en une dizaine de lignes ; ce qui manque n'est pas le code mais la **décision
  de forme** — δr/δv bruts pour le coût terminal, figure à tracer pour le rendu,
  ou entrée HCW — et elle appartient aux consommateurs, qui sont tous en `MIS-6`
  ou hors MVP (§3.2 pt 5 et §3.5 pt 5 de la spec).

**Le report ne coûte rien**, et c'est ce qui distingue ce cas de `UI-6` / `UI-7`
(désormais en [v3](03-roadmap-v3.md)) : aucune couture ne s'accumule d'ici là,
puisque le repère est fourni par la
bibliothèque et qu'aucun état relatif n'existe dans `simulation/`.
[`trajectory-display-frame.md`](../graphics-effects/trajectory-display-frame.md)
§8 disait déjà la même chose du côté rendu.

**Spec.** [`docs/brainstorm/leo-rendezvous-preparation.md`](../brainstorm/leo-rendezvous-preparation.md) §3.5, §3.6 — toujours la référence, désormais lue depuis `MIS-4` et `MIS-6`.

#### ~~MIS-4 — Survol lunaire (TLI + flyby) — ★5 ◆4 L~~ — **RÉSOLU le 2026-08-28**

> **Ce qui a été livré.** Sept lots (`L0` à `L6`,
> [`docs/lunar-flyby/`](../lunar-flyby/)), du seed patched-conic jusqu'à la
> poussée finie de production : `TLIBurnStage` remplace la démo `PHY-4`,
> `inject()` calibre le burn par sécante sur l'énergie spécifique de
> l'impulsion, fenêtre de lancement datée par
> `TranslunarInjectionPlanWindowProblem`. Vol de clôture : périlune
> **100,2 km** pour 100 visés, surcharge de poussée finie réelle **+8,1 m/s**.
>
> **Cinq écarts mesurés, aucun anticipé par le découpage** — détail dans les
> fiches de lot : la sortie de sphère d'influence lunaire arrive à **4,5 j** et
> non 5,5 j (`L0`) ; le « dix minutes » de marge annoncé par `L2` était faux
> d'un facteur 5 (`L4`) ; et la règle qui a coûté le plus cher à apprendre à
> `L6` — toute boucle de calibration doit évaluer ses candidats sur ce qui sera
> **réellement volé** (la combustion finie), pas sur l'impulsion qu'elle
> approxime — a été violée à quatre niveaux indépendants, chacun invisible à la
> lecture et passant les assertions partielles.
>
> **Reste ouvert, sciemment.** L'offset de visée ne corrige qu'une dimension
> d'une distance ratée qui en a deux ; à certaines géométries le périlune volé
> a un plancher (132 km mesuré) au-dessus de la cible — chercher aussi la
> direction de visée serait un lot en soi, non ouvert. `ParkingCoastStage` ne
> gère pas un coast plus court que `ignitionLead`.

**Pourquoi.** Premier objectif au-delà de l'orbite terrestre. Fort en spectacle
(la trajectoire traverse l'échelle Terre-Lune), fort en pédagogie.

**Le socle est là.** `PHY-4` est fermé depuis le 2026-08-18 et son dernier lot a
livré un transfert translunaire complet — orbite de parking, injection
impulsionnelle sur seed Lambert, bascule de sphère d'influence, périlune à 100 km
— volé par les tests et affiché à l'écran. Ce que `MIS-4` ajoute à cela n'est
donc plus la physique multi-arcs mais la **mission** : un `TLIBurnStage` de
production à poussée finie, la fenêtre de lancement, et l'optimisation.

**À faire.** `TLIBurnStage` (depuis l'apogée d'une orbite de parking),
coast ~3 jours sous influence lunaire croissante, objectif de survol
(altitude de périlune visée, distance minimale d'approche). Seed patched-conic,
correction CMA-ES. Le timing du TLI est très contraint : sans `MIS-2`,
l'optimiseur cherchait dans le vide — c'est levé depuis le 2026-08-20, et
`TranslunarInjectionPlanWindowProblem` est déjà en place pour dater l'injection.

**Hérité de `MIS-3`, dissous le 2026-08-20 : le seed Lambert générique.**
L'appel existe mais il est enfermé dans `TranslunarInjectionPlan`, avec
`posigrade = true` et `nRev = 0` en dur. C'est ici qu'il devient une brique
partagée, parce que c'est ici qu'il a son premier consommateur de production —
la forme de l'API se lit sur `TLIBurnStage`, pas dans l'abstrait. Le
multi-révolution (`nRev ≥ 1`) reste à vérifier ; il est natif, il n'est pas testé.

**Spec.** [`docs/lunar-flyby/01-decoupage.md`](../lunar-flyby/01-decoupage.md)
à [`08-conception-L6.md`](../lunar-flyby/08-conception-L6.md) — voir son §2.2 :
**cinq affirmations de cette fiche y sont corrigées**, dont la
« correction CMA-ES » annoncée ci-dessus et le seed Lambert reversé ici depuis
`MIS-3`. Le §8 de [`docs/brainstorm/missions.md`](../brainstorm/missions.md)
reste la source d'origine, antérieure à `PHY-4`.

#### ~~MIS-5 — Mise en orbite lunaire (LOI) — ★5 ◆3 M~~ — **RÉSOLU le 2026-08-29**

> **Ce qui a été livré.** Huit lots (`L0` à `L7`,
> [`docs/lunar-orbit/`](../lunar-orbit/)), directement sur `MIS-4` :
> `LunarInsertionStage` (burn rétrograde à l'arrivée), `LunarOrbitObjective`
> (périlune, inclinaison lunaire), jusqu'à la septième carte du wizard, son
> panneau à un curseur, la branche de fabrique, le scénario, le préremplissage
> et la cible affichée.
>
> **Quatre faits que le découpage ne disait pas.** Le refus de Kourou n'a
> **aucun mécanisme** — comme pour `MIS-4`, le vrai refus du lot est celui des
> ergols (`compositionRefused`). `FixedDuration` est un total depuis le
> décollage et `Revolutions` un coast terminal, deux notions qui coïncident en
> LEO/GEO et **divergent d'un facteur 5** en lunaire (0,98 j de coast contre
> 4,0 j de trajet). La bande d'altitude était déjà décidée par `L3` : le
> catalogue `Payloads.LUNAR_ORBITER` est dimensionné contre 50 km, et ce qui
> limite n'est jamais le réservoir mais la masse sèche. Et le nom sur disque
> d'un `ScenarioMission` vit dans une annotation que le compilateur n'exige
> pas — l'oublier compile, écrit, et échoue à la relecture.
>
> **Reste ouvert.** L'essai manuel, et le vol de clôture `LunarOrbitFlightTest`
> (posé à `L5`), jamais lancé — ces vérifications restent à la charge de
> l'utilisateur, comme les autres missions d'optimisation longues.

Directement sur `MIS-4` : `LunarInsertionStage` (burn rétrograde à l'arrivée),
`LunarOrbitObjective` (altitude de périlune, inclinaison lunaire). L'essentiel
du coût est dans `MIS-4` ; ici on ajoute un stage et un objectif. Rapport
valeur/effort excellent une fois le survol acquis — raison pour laquelle les
deux sont séparés.

**Spec.** [`docs/lunar-orbit/01-decoupage.md`](../lunar-orbit/01-decoupage.md)
à [`09-conception-L7.md`](../lunar-orbit/09-conception-L7.md).

#### ~~MIS-8 — Horizon de mission explicite — ★5 ◆2 M~~ — **RÉSOLU le 2026-08-09**

> **Ce qui a été livré.** `MissionHorizon` (interface scellée,
> `simulation/mission/`) porte la décision hors du générateur d'éphéméride, en
> trois formes : `Revolutions` (le défaut dérivé du type de mission),
> `FixedDuration` et `TrailingCoast`, bornées à 30 jours. Le wizard expose le
> réglage via `FormField.MISSION_HORIZON_DAYS` avec bascule auto / manuel, le
> défaut dérivé pré-rempli et une validation (`validateHorizon()`). Le pas
> d'échantillonnage est devenu variable : chaque stage annonce le sien, au lieu
> du `DEFAULT_STEP_SECONDS = 1.0` global. Les deux constantes de la fiche
> ci-dessous — `DEFAULT_COAST_DURATION_SECONDS` et `FALLBACK_DURATION_SECONDS` —
> n'existent plus, commentaire faux compris. Les quatre points du « À faire »
> sont donc couverts.
>
> Le reste de la fiche est conservé tel quel : il documente le diagnostic
> d'origine et le raisonnement de dimensionnement, qui restent la référence pour
> les phases 4 et 5.

**Pourquoi.** La date de fin d'une mission était alors une **constante**, et
elle était arbitraire à deux endroits :

| Constante | Valeur | Rôle |
|---|---|---|
| `MissionEphemerisGenerator.DEFAULT_COAST_DURATION_SECONDS` | `86_164.0` | coast final, appliqué au **dernier stage** de la chaîne |
| `StageChainRunner.FALLBACK_DURATION_SECONDS` | `7200.0` | filet pour un stage sans cutoff configuré |

Trois problèmes distincts, du plus bénin au plus bloquant.

1. **Le commentaire ment.** `86_164.0` est annoté `// 90 min (one LEO orbit)` :
   c'est un **jour sidéral**, seize fois la valeur commentée. Quelqu'un a écrit
   une intention et une autre valeur. On ne peut pas raisonner sur un horizon
   dont la documentation est fausse d'un facteur 16.
2. **Le symptôme est visible aujourd'hui.** Passé cet horizon,
   `MissionOrchestratorAppState` bascule sur la branche « clock after
   ephemeris » : le vaisseau est figé sur `lastPoint()` avec sa traînée
   complète, et `TelemetryWidget` affiche `COMPLETE`. Un satellite correctement
   inséré en LEO **s'arrête donc de tourner au bout de ~23 h 56 de temps
   simulé** et reste parqué là. Avec une timeline qui monte à ×10⁵, on y arrive
   en quelques secondes de temps réel.
3. **C'est un blocage dur pour les phases 4 et 5**, pas une finition. Un coast
   TLI vers la Lune dure ~3 jours : il est **tronqué avant l'arrivée** par un
   horizon d'un jour. Un phasing de rendez-vous sur N révolutions se heurte au
   même mur. Aucune des deux familles de missions ne peut être écrite tant que
   l'horizon est une constante.

**Ce qui rend l'item plus qu'un remplacement de constante.** L'échantillonnage
est à pas fixe (`DEFAULT_STEP_SECONDS = 1.0`), et `MissionEphemeris` garde tout
en mémoire dans des tableaux parallèles de `AbsoluteDate` / `Vector3D` (~160 o
par point, références comprises) :

| Horizon | Pas fixe 1 s | Pas variable (1 s en burn, 60 s en coast) |
|---|---|---|
| aujourd'hui (1 j) | 86 k pts ≈ 14 Mo | ~5 k pts ≈ 0,8 Mo |
| lunaire (3 j) | 260 k pts ≈ 42 Mo | ~9 k pts ≈ 1,5 Mo |
| dérive (30 j) | 2,6 M pts ≈ **420 Mo** | ~45 k pts ≈ 7 Mo |

Le pas variable achète donc ~60× et rend tenable tout horizon réaliste — c'est
lui, et pas un stockage hors mémoire, qui est la réponse au problème de taille
(cf. `MIS-9`). S'ajoute une dépense inutile par frame :
`MissionOrchestratorAppState:94` appelle `eph.positionsUpTo(now)` **à chaque
frame et par mission visible**, ce qui alloue une `ArrayList` neuve de cette
taille à chaque fois.

**Le vrai défaut de conception sous-jacent : un seul tableau sert deux
consommateurs aux besoins incompatibles.**

- L'**enregistreur de vol** — télémétrie, analytics, verdict de complétude —
  veut de la fidélité là où la dynamique est rapide, et se moque du reste.
- La **polyligne d'affichage** veut au plus quelques milliers de points :
  l'écran fait ~2000 px de large.

Preuve que la tension est déjà là : `MissionTrajectoryRenderer.MAX_POINTS =
8192`, et `update()` parcourt le tableau **à rebours depuis la fin**
(`currentPositions.get(size - i)`). La traînée dessinée est donc *les 8192
derniers échantillons*, soit ≈ 2 h 17 de temps mission au pas actuel : sur toute
mission plus longue, l'ascension **disparaît silencieusement de la ligne**. Ce
n'est pas une décimation, c'est une troncature par le début, et personne ne l'a
décidée. Séparer les deux produits est plus utile — et moins cher — que de
sortir l'éphéméride de la mémoire. Effet de bord agréable du pas variable : à
nombre de points constant, la fenêtre dessinée couvre bien plus de temps.

**À faire.**

1. **Politique d'horizon, dérivée et non constante.** Le bon horizon dépend du
   type de mission : *N périodes orbitales après insertion* pour une mise en
   orbite (la période se déduit de l'orbite atteinte, déjà calculée), *arrivée
   + N révolutions* pour un transfert. Porter la décision sur `MissionSpec` /
   `Mission` plutôt que dans le générateur d'éphéméride, qui n'a pas à
   connaître l'intention.
2. **Exposer le réglage** dans le wizard (« durée de mission » / « propager
   jusqu'à ») avec le défaut dérivé pré-rempli — c'est le geste que l'absence
   d'horizon explicite empêche aujourd'hui.
3. **Pas d'échantillonnage variable par phase.** 1 s pendant les burns (où la
   dynamique est rapide et où la précision compte), nettement plus grossier
   pendant les coasts. Sans quoi le point 1 est impayable.
4. **Corriger le commentaire faux**, et pendant qu'on y est le
   `positionsUpTo` par frame (cache invalidé sur changement de `now`, ou vue
   sans copie).

**Attention à ne pas confondre deux horizons.** Celui-ci est l'horizon *de
restitution* (jusqu'où on échantillonne et affiche). Il ne doit pas changer les
trajectoires optimisées : le coast final est postérieur au dernier stage
optimisé, donc l'allonger ou le raccourcir ne doit toucher **aucune** baseline
d'optimiseur. C'est ce qui permet de faire ce chantier en phase 1 sans risque.
À vérifier explicitement par un test de non-régression avant de toucher au
`FALLBACK_DURATION_SECONDS`, lui **est** dans le chemin des stages.

**Fichiers.** `simulation/mission/ephemeris/MissionEphemerisGenerator.java`,
`simulation/mission/runtime/StageChainRunner.java`,
`simulation/mission/ephemeris/MissionEphemeris.java`,
`states/mission/MissionOrchestratorAppState.java`,
`states/mission/MissionTrajectoryRenderer.java`,
`simulation/mission/operation/MissionSpec.java`,
`ui/mission/wizard/step/StepParameters.java`.

**Spec.** [`docs/mission-horizon/01-horizon-explicite.md`](../mission-horizon/01-horizon-explicite.md).
Elle tranche la question ouverte n°1 (dérivé *et* champ wizard), et elle corrige deux
constats de cette fiche : le coast final n'est **jamais** volé pendant l'optimisation
(la passe d'optim emprunte `StageChainRunner.plain()`, dont le coast final vaut zéro),
donc l'invariant est structurel et non seulement souhaité ; et `FALLBACK_DURATION_SECONDS`
est bien atteignable, mais par un `CoastingStage(stopAtNode = true)` dont le nœud n'arrive
pas — d'où la décision de ne pas y toucher.

### UI — Panel et plomberie mission

#### ~~UI-1 — Vue détail mission~~ — ★4 ◆2 M — **résolu le 2026-08-10**

> Spec détaillée : [`docs/mission-detail/01-vue-detail.md`](../mission-detail/01-vue-detail.md).
> Elle corrige trois hypothèses de cette fiche : `AchievedOrbit` n'est pas stocké mais
> **jeté** par l'orchestrateur, `StagePerformance` ne porte **aucune durée**, et l'objectif
> d'une mission GEO est la **GTO**, pas l'orbite GEO.
>
> Le texte qui suit est conservé tel qu'écrit avant le chantier, pour la traçabilité ; son
> §7 recense ce que l'implémentation a démenti, dont un décompte de stages GEO (12, pas 8)
> qui a coûté une hauteur de fenêtre.

**Pourquoi.** `MissionOptimizerResult` et `AchievedOrbit` sont calculés et
stockés — et **aucun fichier de `ui/` ne les lit**. L'application optimise des
trajectoires sans jamais dire ce qu'elle a obtenu. `PanelFooter` affiche
identité et attributs (type, date, site), rien du résultat.

**À faire.** Zone de détail sur sélection : altitude et inclinaison atteintes,
écart à la cible (`AchievedOrbit` expose déjà `hasOsculating()` /
`formatOsculating()` / équivalents moyens), liste des stages avec durée et Δv.
Et pour `FAILED` : un message lisible — ce qui suppose d'**ajouter le champ**
(`MissionEntry.lastError`, absent aujourd'hui) et de l'alimenter aux deux
endroits qui passent en `FAILED` (`MissionEntry.setOptimizationType` et
`MissionOrchestratorAppState`), où l'exception n'est aujourd'hui que loguée.

#### ~~UI-2 — Feedback de progression pendant l'optimisation — ★3 ◆2 M~~ — **RÉSOLU le 2026-08-21**

> **Ce qui a été livré.** Un paquet `simulation/mission/progress/` porté par un
> `MissionProgressListener` **nullable** câblé de `MissionPlanOptimizer` jusqu'à
> `CMAESRunExecutor` — voie froide à événements scellés pour les transitions,
> voie chaude sans allocation pour le compteur, qui s'incrémente depuis les
> threads du pool CMA-ES. Dans la fenêtre de gestion : un spinner géométrique et
> la position dans la séquence en colonne de statut, la ligne détaillée au pied
> de la mission sélectionnée. Le HUD n'est pas touché, la fenêtre étant non
> modale. 23 tests, tous hors JME.
>
> **Quatre écarts à cet énoncé**, détaillés au §1.3, §3.2 et §5.2 de la spec.
> (1) La fiche demandait « spinner + compteur » ; le compteur seul ne dit rien de
> plus que le spinner, faute d'un dénominateur atteignable — 40 000 est un
> plafond que les trois sorties anticipées rendent, de l'aveu du code lui-même,
> normalement inatteignable — donc ce qui est affiché est la **position dans la
> séquence**, le compteur restant comme signal de vie. (2) L'exécuteur est
> mono-thread : une deuxième mission lancée est en **file**, et l'affichage le
> dit désormais, sans que `MissionStatus` gagne une valeur (15 sites l'auraient
> traitée à l'identique de `COMPUTING`). (3) En mode PRECISE la progression
> s'arrête au niveau **charge** — la seule fraction bornée et monotone du
> système — les niveaux étage et tentative recyclant jusqu'à 135 fois.
> (4) `icon-spinner.png` est en RGB noir pur : `Unshaded` multipliant sa couleur
> par le texel, la teinte était impossible telle quelle, d'où un **quatrième
> matériau maison**, `MatDefs/Ui/IconMask`, qui lit la texture comme masque
> d'alpha. Repeindre l'asset en blanc reste l'alternative, non retenue.
>
> **Ce qui reste ouvert** : l'annulation d'un calcul. Elle n'était pas dans la
> fiche et le mécanisme existe à moitié — il faudrait retenir le `Future` que
> l'orchestrateur jette, et un drapeau lu dans la fonction objectif à côté du
> `crossRunStop` qui y vit déjà.

**Contrainte mesurée à respecter** : le coût d'une évaluation varie d'un facteur
~5 et n'est pas prévisible → une barre linéaire en nombre d'évaluations sera
par moments franchement fausse. Indicateur **indéterminé** (spinner) + compteur
d'évaluations en texte. Devient plus important à mesure que les optimisations
s'allongent (lunaire, rendez-vous) — d'où son placement en phase 3, avant
elles.

**Spec.** [`docs/mission-progress/01-feedback-optimisation.md`](../mission-progress/01-feedback-optimisation.md)

#### ~~UI-3 — Persistance / format de scénario — ★4 ◆3 M *(ajout)*~~ — **RÉSOLU le 2026-08-21**

> **Ce qui a été livré.** Les cinq lots `L0` à `L4` : schéma v1 avec
> `formatVersion`, (dé)sérialiseurs, `ScenarioStore`, `ScenarioSession.capture`
> (préremplissage wizard), `ScenarioBrowserWidget` / `ScenarioBrowserModel`
> (charger / enregistrer) et les deux entrées de menu, avec leurs icônes.
>
> **Écarts au découpage, pris en connaissance de cause.**
> `ScenarioBrowserWidget` ne connaît pas `ScenarioBrowserModel` — l'`AppState`
> pousse lignes, sélection et activation, comme `AppMenu` ↔ `AppMenuModel` ; un
> import direct `ui → states` aurait été le premier du dépôt. `restore` refuse
> une mission dont l'atmosphère n'est pas `NONE` (rien ne peut la remonter
> avant `PHY-2`). Et une mesure a démenti la conception :
> `TimeConverter.toUtcIsoString` reposait sur `LocalDateTime.toString()`, qui
> **omet les secondes à la minute pile** — une date sur soixante ne se relisait
> pas elle-même ; corrigé par un formateur explicite.
>
> **Trou connu.** Les rejets de `ScenarioLoadReport` ne sont que journalisés,
> aucun écran ne les affiche.
>
> **Reste ouvert.** `ScenarioReplayTest` (opt-in, tests lents) et l'essai
> manuel, seul juge de fin de phase, restent à la charge de l'utilisateur.

**Pourquoi.** Les missions ne survivent pas à la fermeture de l'application. Au
delà du confort, un format de scénario sérialisable (lanceur, payload, site,
paramètres, date, seed CMA-ES) est la feature la plus *enabling* du brainstorm
long terme : reproductibilité, partage, mode batch, scénarios historiques,
défis en dépendent tous.

**Ce qui existe.** `MissionSpec` est immuable et sérialise déjà les paramètres
du wizard — le plus dur est fait. Manquent le schéma v1 avec `formatVersion`,
les (dé)sérialiseurs et deux entrées de menu.

**Ce qu'on persiste, et ce qu'on ne persiste pas.** Le point est structurant,
autant le poser ici :

| Donnée | Persistée ? | Pourquoi |
|---|---|---|
| `MissionSpec` (type, lanceur, payload, site, date, paramètres) | **oui** | quelques centaines d'octets, versionnable, diffable, lisible |
| Résultat d'optimisation (le petit vecteur de paramètres, pas la trajectoire) | **oui** | évite de rejouer un CMA-ES de plusieurs minutes au chargement |
| `MissionEphemeris` (les points échantillonnés) | **non** | produit **dérivé**, 14 à 420 Mo par mission (cf. `MIS-8`), et périmé dès que le propagateur change |

Autrement dit : on recharge une mission en **régénérant** son éphéméride depuis
le spec et les paramètres optimisés. C'est ce qui garde `UI-3` à ◆3 et le rend
indépendant de toute question de mémoire.

**Couplage avec `PHY-2`, désormais certain.** `UI-3` est en phase 3, la bascule
du drag en [v2](02-roadmap-v2.md) : tous les scénarios écrits d'ici là le seront **sans**
atmosphère. Le champ « modèle d'atmosphère » doit donc figurer dans le format
dès la v1, même s'il ne vaut que `NONE` — sans lui, un scénario d'avant la
bascule se rejoue après avec une physique différente et personne ne le voit
passer. C'est aussi la raison pour laquelle stocker la trajectoire échantillonnée
serait un piège : elle deviendrait fausse sans que rien ne le signale.

**Spec.** [`docs/scenario/01-persistance-missions.md`](../scenario/01-persistance-missions.md)
— voir son §11 : quatre affirmations de cette fiche y sont corrigées par des mesures.

#### ~~UI-4 — Menu applicatif haut-gauche — ★3 ◆2 M *(ajout)*~~ — **RÉSOLU le 2026-08-14**

> **Ce qui a été livré.** Un menu `ORBITLAB` ancré haut-gauche — bouton-titre
> `menu.title.button` et déroulé `wizard-shell` — avec les trois entrées v1
> (*Mission panel* à coche, *Manage missions…*, *New mission…*, qui donne enfin
> un appelant à `publishOpenWizard`). `AppMenuModel` porte la logique hors JME,
> couverte par `AppMenuModelTest`. `MissionPanelTrigger` et son `setEnabled`
> menteur sont supprimés, et l'ancrage du panneau d'affichage passe par
> `AppStyles.HUD_MENU_HEIGHT_PX` / `HUD_STACK_GAP_PX` au lieu de ses trois
> constantes locales : les deux widgets empilés partagent enfin une marge.
>
> Trois écarts au document de spec, tous détaillés dans son §9 : le
> bouton-titre a son propre sélecteur au lieu d'être un `Button` nu (le fond
> `btn-ghost` disparaissait sur un ciel sombre), `AppMenuItem` porte un
> `separatorBefore`, et `ESC` a dû être repris à `SimpleApplication` qui le
> liait à la sortie de l'application.

**Pourquoi.** Le haut-gauche n'avait qu'un point d'entrée : `MissionPanelTrigger`,
un bouton « Missions » qui bascule le panneau d'affichage. Trois défauts, dont
un seul est cosmétique.

1. **Le skin.** Le bouton est construit avec `FormStyles.STYLE`, puis réécrit à
   la main les quatre attributs que ce style fournit — `background`, `color`,
   `font`, `insets` (`ui/mission/panel/MissionPanelTrigger.java:26-30`). Là où
   le sélecteur `button` de `FormStyles` pose un fond `btn-ghost`, il porte un
   aplat `UiKit.gradientBackground(AppStyles.ICE_ACCENT)`. C'est le seul bouton
   de l'application dans ce cas, et c'est le seul élément de HUD visible en
   permanence : d'où l'impression d'un élément importé d'une autre interface.
2. **L'état grisé dit le contraire de ce qu'il fait.** Le Javadoc de
   `setEnabled(boolean)` présente le grisé comme « le panneau est déjà ouvert »
   (l. 45-48), alors que `MissionDisplayPanelAppState.togglePanel()` grise
   quand le panneau se **ferme** (l. 82-91). L'un des deux est faux depuis
   toujours. Un menu n'a de toute façon pas à porter cet état : c'est une coche
   sur une entrée, pas une opacité sur le bouton d'ouverture.
3. **Un bouton unique ne tient pas la charge à venir.** `UI-3` a besoin de
   « deux entrées de menu » (charger / enregistrer un scénario) et il n'existe
   aucun hôte pour les recevoir. Symptôme du même manque :
   `MissionDisplayPanelAppState.publishOpenWizard()` est écrit et **n'est
   appelé de nulle part** (l. 97-99) — la création de mission n'a pas d'entrée
   depuis le HUD, seulement depuis le panneau de gestion.

**À faire.**

- Un menu ancré haut-gauche (bouton-titre + liste déroulante) à la place du
  trigger, habillé par un **sélecteur Lemur déclaré dans `FormStyles`**, pas
  par des overrides à la construction. La règle qui sort de ce chantier :
  un widget qui adopte `FormStyles.STYLE` n'en réécrit pas les attributs ; s'il
  lui faut une autre allure, c'est un sélecteur de plus.
- Entrées v1 : *Afficher le panneau des missions* (bascule, avec coche),
  *Gérer les missions…* (`OpenMissionManagement`), *Nouvelle mission…*
  (`OpenMissionWizard` — ce qui donne enfin un appelant à `publishOpenWizard`).
  Les entrées d'`UI-3` s'y ajouteront ensuite sans nouveau chantier d'UI.
- Aligner l'ancrage du panneau d'affichage sur celui du menu :
  `MissionDisplayPanelWidget` code aujourd'hui `MARGIN_PX = 5f` et une hauteur
  de déclencheur devinée `TRIGGER_HEIGHT = 28f` (l. 34-35), tandis que le
  trigger se pose à `AppStyles.HUD_MARGIN_PX = 16f`. Deux marges différentes
  pour deux éléments empilés : à remplacer par une constante partagée.
- Un test sur la logique du menu (ouverture, fermeture, état coché) séparée du
  cycle de vie JME, comme `MissionDisplayPanelRules` l'a fait pour le panneau.

**Ordre — tenu.** Il fallait passer **avant** `NAV-2`, dont la spec demande que
son toggle soit construit « sur le motif de `MissionPanelTrigger` : même style,
même `setEnabled(boolean)` »
([`navigation/02-timeline-mission.md`](../navigation/02-timeline-mission.md) §11).
Ce motif n'existe plus : `NAV-2` trouvera un sélecteur Lemur et une coche, et
n'aura donc ni skin ni sémantique inversée à recopier. Ce §11 est à relire au
moment de faire `NAV-2`.

**Ce qu'on ne fait pas.** Pas de barre de menus complète de type application de
bureau (Fichier / Édition / Affichage…) : un seul point d'entrée déroulant.
Savoir si le toggle de la piste temporelle devient une entrée du menu ou reste
un bouton local à son widget se tranche en faisant `NAV-2`, pas ici.

**Spec.** [`docs/menu/01-menu-applicatif.md`](../menu/01-menu-applicatif.md) —
trois variantes maquettées sur les textures déjà présentes dans le dépôt, la
« chip formulaire » retenue le 2026-08-14 (§5) avec le libellé `ORBITLAB`, une
icône par entrée et aucun raccourci clavier (§8), et le périmètre exact du diff
(§6.6).

#### ~~UI-5 — Surfaces, modalité et pile de renvoi `ESC` — ★3 ◆2 M~~ — **RÉSOLU le 2026-08-15**

> **Livré et vérifié à l'écran.** Le panneau de gestion est devenu une fenêtre
> non modale déplaçable par son bandeau, bornée à l'écran et sous le bouton du
> menu ; `ESC` renvoie la surface du dessus via un registre porté par
> `ApplicationContext` et ne quitte plus l'application ; `Quit` est une entrée de
> menu avec confirmation. `UiLayers` porte seul l'échelle de profondeur, et cette
> échelle **est** l'ordre de renvoi. La chorégraphie qui faisait se rouvrir
> mutuellement le panneau et le wizard a disparu.
>
> 34 tests unitaires au vert (`HudSurfaces` 11, `AppMenuModel` 12,
> `WindowDragHandler` 11). Les trois points que la spec §9 renvoyait à un essai
> plutôt qu'à un test — le glisser, le clamp aux bords, le picking après
> changement de couche — sont confirmés. La spec §10 consigne six écarts, dont
> deux que la conception n'avait pas vus : un `AppState` a besoin de **lire** la
> surface d'un autre (d'où `HudSurfaces.isOpen(String)`), et la position
> d'ouverture centrée dépassait sa propre borne haute en 1280×720.
>
> **Suite constatée en livrant `NAV-2`.** Le Javadoc de `UiLayers.HUD` affirmait
> que la timeline empile ses composants jusqu'à `z = 5` « d'où le palier suivant
> à 10 ». La piste de mission monte à 10, donc sa tooltip est à égalité avec
> `PANEL` en `z` monde. L'égalité n'est pas observable — le panneau d'affichage
> est ancré en haut-gauche, la piste est une bande de 600 px en bas-centre — mais
> le commentaire, lui, était devenu faux et a été corrigé.

**Pourquoi.** `UI-4` livré, le menu applicatif est injoignable dès qu'une des
fenêtres qu'il ouvre est à l'écran. La question posée était « faut-il le faire
passer au-dessus de tout ? » ; la réponse est non, et le diagnostic est ailleurs :
`ModalBackdrop` est appliqué indifféremment à trois surfaces de natures
différentes. `ConfirmDialog` est bloquant par essence, le wizard porte un état
non enregistré, mais le **panneau de gestion est un navigateur sur des données**
qui ne bloque l'écran que par héritage. C'est lui qui force le contournement
visible aujourd'hui : `onEdit` ferme le panneau avant d'ouvrir le wizard « sinon
il s'empilerait par-dessus », et `MissionWizardAppState.submit()` republie
`OpenMissionManagement` pour revenir en arrière. Deux surfaces qui se rouvrent
mutuellement par événements, faute de pouvoir coexister.

Deux défauts s'y ajoutent, mesurés en écrivant la spec :

1. **`ESC` quitte l'application au milieu d'un wizard.** `UI-4` a pris la touche
   à `SimpleApplication` en écrivant qu'elle appartient désormais au HUD ;
   personne ne s'y est inscrit, donc `MissionDisplayPanelAppState` appelle
   `stop()` dès que le menu est fermé.
2. **L'échelle de `z` est écrite en dur dans cinq fichiers, et trois surfaces
   sont à `z = 0`.** Elles ne se recouvrent pas par chance de placement.
   `GuiGraph` porte un commentaire `// topmost` sur `modalNode` qui décrit une
   intention que rien n'applique : le tri du bucket GUI est global sur le `z`
   monde, pas sur l'ordre d'attache.

**À faire.** Le panneau de gestion devient une fenêtre non modale déplaçable
(Lemur fournit déjà `DragHandler` ; il reste à le borner). `ESC` devient
uniformément « renvoie la surface du dessus », par un registre porté par
`ApplicationContext` — la règle « pas de `getState()` » interdit que les trois
`AppState` concernés s'interrogent. Quitter devient une entrée de menu avec
confirmation. Une classe `UiLayers` reprend l'échelle de profondeur, et l'ordre
de renvoi **est** cette échelle : la surface que `ESC` ferme est, par
construction, celle qui est devant.

**Ce qu'on ne fait pas.** Pas de gestionnaire de fenêtres (redimensionnement,
minimisation, empilement par focus, position persistée sur disque). Pas de
fusion du panneau de gestion et du panneau d'affichage — elle devient plus
facile après ce chantier, mais elle n'en fait pas partie.

**Spec.** [`docs/ui/01-surfaces-et-modalite.md`](../ui/01-surfaces-et-modalite.md) —
trois options pesées pour le panneau de gestion et trois pour le sort d'`ESC`
(§4), décision et raisons (§5), échelle de couches et registre de surfaces
(§6.1–6.2), périmètre exact du diff (§6.6), et trois risques à vérifier à
l'écran plutôt que par un test (§9).

---


## 7. Questions tranchées pendant v1

*Les questions restées ouvertes ont suivi la version qui les paie ; elles sont
listées en fin de tableau.*

1. **Horizon de mission (`MIS-8`)** — purement dérivé du type de mission, ou
   réglable dans le wizard avec ce dérivé comme défaut ? **Tranchée le
   2026-08-09 : les deux.** `MissionHorizon.Revolutions` fournit le dérivé,
   pré-rempli comme défaut, et le wizard laisse basculer en manuel
   (`FixedDuration`) — la bascule revenant au dérivé quand on la relâche.
2. **Fenêtre de la piste temporelle (`NAV-2`)** — durée de la mission
   sélectionnée, ou fenêtre glissante autour de `now()` ? **Tranchée le
   2026-08-11 : la durée de la mission, dans un widget séparé.** La piste
   temporelle quitte la capsule ; sa fenêtre est celle de l'éphéméride de la
   mission suivie, et sans éphéméride le widget ne s'affiche pas. La capsule
   garde son `ScrubberTrack` indexé sur la vitesse, ce qui clôt aussi la
   cohabitation que `NAV-3` redoutait. Voir
   [`docs/navigation/02-timeline-mission.md`](../navigation/02-timeline-mission.md).
3. **Troisième viewport** — **tranchée le 2026-08-18 : non.** `PHY-4 / L5` §5.3
   l'a écarté sur mesure — un seul globe est dessiné, dans la région de
   l'origine où le pas de profondeur vaut 27 km, et le bout lointain du trait ne
   dispute la profondeur qu'à lui-même — puis `L6` §12.5 a confirmé sur la
   première trajectoire lunaire réelle. **La question ne rouvre qu'avec deux
   globes dans le même cadre**, c'est-à-dire `MIS-6` : elle est devenue `RND-8`
   en [v4](04-roadmap-v4.md).

**Les six questions encore ouvertes**, et où elles se paient :

| Question d'origine | Va en | Portée par |
|---|---|---|
| Auto-optimisation après création d'une mission | [v3](03-roadmap-v3.md) | `UI-8` (`REL-28`) |
| Persistance des bascules d'affichage | [v3](03-roadmap-v3.md) | `UI-8` (`REL-29`) |
| Modalité du wizard de création | [v3](03-roadmap-v3.md) | `UI-6` |
| Jusqu'où va la rentrée (arrêt à l'interface / plasma / désintégration) | [v2](02-roadmap-v2.md) | `MIS-10` et `FX-4` |
| Entrée directe, capture propulsive ou aérocapture | [v3](03-roadmap-v3.md) | `MIS-11` |
| Cible du rendez-vous (ISS en dur ou import TLE) | [v4](04-roadmap-v4.md) | `MIS-6` |
