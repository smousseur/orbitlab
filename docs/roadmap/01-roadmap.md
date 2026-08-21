# Roadmap OrbitLab — révision 2026-08-08

Ce document remplace `docs/roadmap/01-short-term.md` (supprimé : ses phases 0
et 1 sont soldées, cf. §1). Il est **la** porte d'entrée du dossier `docs/` :
chaque item renvoie vers la spec détaillée quand elle existe, et l'index
complet des documents est dans [`docs/README.md`](../README.md).

**Comment le lire.** Le plan, c'est le **§3** — sept phases dans l'ordre. Le §4
est un classement valeur/difficulté qui sert à arbitrer, pas à planifier ; le
§6 est un recueil de fiches par item, à ouvrir au moment de coder. Si vous ne
lisez qu'une section, lisez le §3.

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

## 3. La roadmap

**C'est cette section qui dit quoi faire, et dans quel ordre.** Sept phases, à
prendre dans l'ordre ; à l'intérieur d'une phase, l'ordre des lignes est
indicatif — les items d'une même phase sont volontairement peu couplés entre
eux. Le §4 sert à arbitrer un échange, pas à planifier.

Les durées sont des ordres de grandeur pour une personne, sans marge de
découverte.

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

### Phase 3 — Socle physique et mission partagé · ~4,5 semaines

> **La phase pivot.** Rien ici n'est spectaculaire pris isolément ; tout est
> réclamé par les missions lunaires (phase 4) et par le rendez-vous (phase 5).
> C'est aussi la phase la plus risquée en estimation.
>
> Les deux derniers items sont de l'**outillage** plutôt que du socle physique,
> et c'est délibéré : ils rendent les phases 4 et 5 tenables au quotidien.

| ID | Item | ★ | ◆ | Taille | Sert à |
|---|---|:-:|:-:|:-:|---|
| ~~MIS-7~~ | ~~`EarthOrbitMission` paramétrable~~ — **résolu le 2026-08-16** | 4 | 2 | M | MIS-2, MIS-6, + polaire/SSO/MEO gratuits |
| ~~PHY-4~~ | ~~Socle multi-corps (3ᵉ corps, SOI, repères)~~ — **résolu le 2026-08-18** | 5 | 4 | L | MIS-4, MIS-5 |
| ~~MIS-2~~ | ~~Fenêtres de lancement~~ — **résolu le 2026-08-20** | 4 | 3 | M | MIS-4, MIS-6 |
| ~~MIS-3~~ | ~~Solveur de Lambert + repère LVLH~~ — **dissous le 2026-08-20** | 4 | 3 | M | — (reversé en `MIS-4` et `MIS-6`) |
| ~~PHY-1~~ | ~~Atmosphère : la brique, **off** par défaut~~ — **résolu le 2026-08-21** | 4 | 3 | L | PHY-2, PHY-3 |
| ~~UI-2~~ | ~~Feedback de progression pendant l'optimisation~~ — **résolu le 2026-08-21** | 3 | 2 | M | confort des phases 4 et 5 |
| UI-3 | Persistance des missions / format de scénario | 4 | 3 | M | **outil de dev** des phases 4 et 5 |

> **`UI-3` est le dernier item de la phase.** Les six autres sont soldés — cinq
> livrés, un dissous — et la fin de phase ne tient plus qu'à la survie d'une
> mission à la fermeture de l'application.

**Pourquoi `UI-3` est ici et pas en phase 6.** Il n'a aucune dépendance, et son
bénéfice principal à ce stade n'est pas la feature mais l'outillage : sans lui,
mettre au point une mission lunaire ou un rendez-vous impose de re-saisir la
mission dans le wizard **à chaque lancement de l'application**. Un save/load
livré avant les phases 4 et 5 se rembourse pendant celles-ci ; livré après, il
ne rembourse rien.

**Pourquoi `MIS-3` a disparu de la phase.** Il n'a pas été livré, il a été
**dissous** : `PHY-4` a livré Lambert au passage, et la moitié `LVLH` n'a aucun
consommateur avant `MIS-6`. Ce qui restait a été reversé dans les deux items qui
en ont besoin. Le raisonnement complet est dans la fiche `MIS-3` au §6.

**Fin de phase quand** : une trajectoire peut sortir de la sphère d'influence
terrestre, une date de lancement est choisie parce qu'elle est bonne, et une
mission survit à la fermeture de l'application.

### Phase 4 — Missions lunaires · ~2 semaines

| ID | Item | ★ | ◆ | Taille |
|---|---|:-:|:-:|:-:|
| MIS-4 | Survol lunaire (TLI + flyby) | 5 | 4 | L |
| MIS-5 | Mise en orbite lunaire (LOI) | 5 | 3 | M |
| FX-2 | Éclipses / pénombre inter-corps | 4 | 3 | M |

`FX-2` est ici et pas en phase 6 : c'est le moment où la scène a enfin trois
corps alignés qui s'occultent, et où l'effet se voit.

### Phase 5 — Rendezvous / phasing · ~3 semaines

| ID | Item | ★ | ◆ | Taille |
|---|---|:-:|:-:|:-:|
| MIS-6 | Rendezvous / phasing sur cible TLE | 5 | 5 | XL |

Seul item de sa phase parce qu'il en vaut plusieurs : source éphéméride TLE
bufferisée, abstraction `EphemerisTarget`, deux nouveaux stages, nouveau coût,
rendu de la cible. Découpage détaillé en §6.

### Phase 6 — Réalisme et spectacle · ~4 semaines

| ID | Item | ★ | ◆ | Taille |
|---|---|:-:|:-:|:-:|
| PHY-2 | Atmosphère par défaut + recalibrage optimiseur | 5 | 4 | L |
| PHY-3 | Détecteurs MaxQ / interface + télémétrie + UI fidélité | 3 | 2 | M |
| FX-3 | Particules de tuyère | 4 | 2 | M |
| PHY-5 | Étages largués : objets propagés et modèles 3D | 4 | 3 | L |
| NAV-5 | Hover « wow » planètes + orbites | 3 | 2 | M |

**Note pour `PHY-2`** : les scénarios écrits en phase 3 par `UI-3` datent d'avant
la bascule du drag. Le champ « modèle d'atmosphère » du format doit donc exister
**dès `UI-3`**, même s'il ne vaut que `NONE` à ce moment-là — sinon les scénarios
d'avant deviennent silencieusement faux au moment du basculement.

**Note pour `PHY-5`** : il est ici et pas plus tôt parce que sa valeur dépend de
`PHY-2`. Sans traînée, un étage largué en orbite ne redescend jamais et un booster
suborbital retombe sans ralentir : on obtiendrait deux points qui s'écartent, pas
une séparation. Sa vraie contrainte de date n'est cependant pas technique — voir
la fiche au §6 : les maillages par étage n'existent pas, et ce n'est pas du code.

### Phase 7 — Retour sur Terre · ~2,5 semaines

> La phase qui ferme la boucle. Jusqu'ici une trajectoire part et ne revient
> jamais ; ces deux items la ramènent. Tous deux sont **strictement en aval de
> `PHY-2`** — sans traînée par défaut, une rentrée n'est pas une rentrée, c'est
> une collision avec une sphère. C'est la seule raison pour laquelle `MIS-11` ne
> tient pas compagnie au reste du lunaire en phase 4.

| ID | Item | ★ | ◆ | Taille |
|---|---|:-:|:-:|:-:|
| MIS-10 | Déorbitage contrôlé et rentrée atmosphérique | 5 | 3 | M |
| MIS-11 | Mission de type Artemis (survol lunaire et retour) | 5 | 4 | L |

**`MIS-10` d'abord**, et pas seulement parce qu'il est le moins cher des deux :
c'est lui qui répare la garde de rentrée et pose la terminaison de descente, que
`MIS-11` réutilise tels quels pour sa dernière heure de vol. Pris dans l'autre
ordre, `MIS-11` paie le même travail au milieu d'un chantier ◆4.

**Fin de phase quand** : un vaisseau parti de la Terre y revient, à un endroit
qu'on a choisi.

### Pourquoi l'atmosphère est coupée en deux, à cheval sur les phases 3 et 6

Le drag renchérit **l'ascension**, donc *toutes* les missions, lunaires et
rendez-vous compris. Activer le drag par défaut invalide les baselines
d'optimiseur : chaque type de mission écrit avant devra être recalibré après.
Deux stratégies, une seule tient :

- livrer l'atmosphère complète *avant* les nouvelles missions → un seul
  recalibrage, mais la Lune recule de plusieurs semaines derrière un chantier
  ◆4 risqué ;
- livrer la **brique** tôt (`PHY-1`, drag activable par mission, **off** par
  défaut, aucune trajectoire existante modifiée) et **basculer le défaut**
  (`PHY-2`) une fois les types de missions posés → un seul recalibrage groupé,
  en fin de parcours, sur un périmètre connu.

La seconde est retenue. `PHY-1` doit donc être écrit avec cette contrainte
explicite : *drag off ⇒ trajectoire identique au bit près*.

---

## 4. Vue d'ensemble — arbitrage

Les mêmes items, triés par rapport valeur / difficulté décroissant. Ce tableau
**ne donne pas l'ordre d'exécution** (c'est le §3) : il sert à piocher un item
opportuniste, ou à décider quoi sacrifier quand une phase déborde.

| ID | Item | ★ | ◆ | Taille | Dépend de |
|---|---|:-:|:-:|:-:|---|
| ~~MIS-8~~ | ~~Horizon de mission explicite~~ — résolu | 5 | 2 | M | — |
| ~~RND-1~~ | ~~Corriger les artefacts visuels de la vue spacecraft~~ — résolu | 4 | 2 | M | — |
| ~~FX-1~~ | ~~Bloom sur le Soleil~~ — résolu (le tone mapping n'en faisait pas partie, voir détail) | 3 | 1 | S | — |
| ~~MIS-1~~ | ~~Deuxième lanceur au catalogue~~ — résolu (mesh Ariane 5 faute d'Ariane 6, voir détail) | 3 | 1 | S | — |
| ~~RND-3~~ | ~~Couleur par stage + passé/futur + marqueur « now »~~ — résolu | 4 | 2 | M | — |
| ~~UI-1~~ | ~~Vue détail mission (orbite atteinte, message d'erreur)~~ — résolu | 4 | 2 | M | — |
| ~~NAV-1~~ | ~~Transitions de caméra entre vues~~ — résolu | 4 | 2 | M | — |
| ~~MIS-7~~ | ~~`EarthOrbitMission` paramétrable → polaire / SSO / MEO~~ — résolu (branche de nœud et catalogue de sites reportés, voir détail) | 4 | 2 | M | — |
| ~~RND-2~~ | ~~Filtrage anisotrope (MSAA déjà actif)~~ — résolu | 2 | 1 | S | — |
| FX-2 | Éclipses / pénombre inter-corps | 4 | 3 | M | — |
| FX-3 | Particules de tuyère | 4 | 2 | M | — |
| ~~NAV-4~~ | ~~Breadcrumb de navigation 3D~~ — résolu | 3 | 2 | M | — |
| ~~UI-2~~ | ~~Feedback de progression pendant l'optimisation~~ — **résolu le 2026-08-21** (file d'attente distinguée, quatrième MatDef maison, voir détail) | 3 | 2 | M | — |
| ~~UI-4~~ | ~~Menu applicatif haut-gauche (remplace le bouton « Missions »)~~ — résolu | 3 | 2 | M | — |
| ~~UI-5~~ | ~~Surfaces, modalité et pile de renvoi `ESC`~~ — résolu | 3 | 2 | M | UI-4 (livré) |
| ~~NAV-2~~ | ~~Timeline indexée sur le temps + marqueurs d'événements~~ — résolu | 4 | 3 | M | — |
| ~~NAV-3~~ | ~~Scrub continu (glisser sur la piste)~~ — résolu (le débit redouté n'existait pas, voir détail) | 3 | 2 | S | NAV-2 |
| ~~RND-4~~ | ~~Ribbon billboardé (orbites + trajectoires)~~ — résolu | 4 | 3 | M | — |
| ~~MIS-3~~ | ~~Solveur de Lambert + repère LVLH~~ — **dissous le 2026-08-20** (Lambert déjà livré par `PHY-4`, LVLH sans consommateur avant `MIS-6`, voir détail) | 4 | 3 | M | — |
| ~~MIS-2~~ | ~~Fenêtres de lancement~~ — résolu (cible TLE et précession J2 reportées en `MIS-6`, voir détail) | 4 | 3 | M | MIS-7 (livré) |
| PHY-5 | Étages largués propagés + maillages par étage | 4 | 3 | L | — (mais sans valeur avant PHY-2) |
| NAV-5 | Hover « wow » planètes + orbites | 3 | 2 | M | RND-4 (livré) |
| UI-3 | Persistance des missions / format de scénario | 4 | 3 | M | — |
| ~~PHY-4~~ | ~~Socle multi-corps (3ᵉ corps, SOI, repères)~~ — **résolu le 2026-08-18** | 5 | 4 | L | — |
| MIS-5 | Mise en orbite lunaire (LOI) | 5 | 3 | M | MIS-4 |
| MIS-10 | Déorbitage contrôlé et rentrée atmosphérique | 5 | 3 | M | PHY-2, PHY-3 |
| MIS-4 | Survol lunaire (TLI + flyby) | 5 | 4 | L | PHY-4 (livré), MIS-2 (livré) |
| MIS-11 | Mission Artemis : survol lunaire et retour | 5 | 4 | L | MIS-4, MIS-10 |
| ~~PHY-1~~ | ~~Atmosphère : brique drag, désactivée par défaut~~ — **résolu le 2026-08-21** | 4 | 3 | L | — |
| PHY-3 | Détecteurs MaxQ / interface + télémétrie + UI fidélité | 3 | 2 | M | PHY-1 (livré) |
| PHY-2 | Atmosphère par défaut + recalibrage optimiseur | 5 | 4 | L | PHY-1 (livré) |
| MIS-6 | Rendezvous / phasing sur cible TLE | 5 | 5 | XL | MIS-2 (livré), MIS-7 (livré) |
| RND-5 | Repère d'affichage des trajectoires (bascule inertiel / tournant) — *confort, hors phases* | 2 | 2 | S | — |
| UI-6 | Fenêtres déplaçables, empilement par focus, modalité du wizard — *hors phases* | 3 | 2 | M | UI-5 (livré) |
| UI-7 | Tooltips sur les contrôles + socle de survol partagé (absorbe `BUG-4`) — *hors phases* | 3 | 2 | M | — |

**`RND-5` n'est dans aucune phase, délibérément.** C'est un confort de lecture, pas
un préalable : rien n'en dépend, il ne corrige aucun défaut (§6, fiche `RND-5`), et
il est là pour être pioché un jour de creux. Le laisser hors des phases est plus
honnête que de le glisser en fin de phase 6 où il ferait semblant d'être planifié.

**`UI-6` non plus, et pour une raison différente.** Il ne dépend de rien et rien
n'en dépend, mais il n'est pas gratuit dans le temps : chaque surface livrée d'ici
là est une fenêtre de plus à reprendre. Le bon moment n'est donc pas « un jour de
creux » mais « avant la prochaine fenêtre », et c'est `UI-3` (persistance) qui la
posera vraisemblablement. À piocher à ce moment-là, ou plus tôt si le nombre de
fenêtres ouvertes simultanément devient gênant à l'usage.

**`UI-7` obéit à la même logique, en plus marqué.** Son coût est proportionnel au
nombre de contrôles à reprendre — 22 sites de survol aujourd'hui, un de plus à
chaque widget livré entre-temps. Il ne bloque rien, mais c'est le seul item de la
liste dont le prix augmente mécaniquement avec le temps.

---

## 5. Graphe de dépendances (l'essentiel)

```
MIS-8 (horizon de mission) ✔ résolu — ses trois aval sont débloqués
   ├── NAV-2 (piste temporelle) ✔ ── NAV-3 (scrub) ✔ — les deux résolus
   ├── MIS-4 / MIS-5 (lunaire : coast TLI ~3 j > horizon actuel)
   └── MIS-6 (rendezvous : phasing sur N révolutions)

MIS-7 (mission Terre paramétrable) ✔ résolu — MIS-2 est débloqué
   └── MIS-2 (fenêtres de lancement) ✔ résolu — MIS-4 et MIS-6 sont débloqués
          ├── MIS-4 (survol lunaire) ──── MIS-5 (orbite lunaire)
          │      ▲
          │   PHY-4 (multi-corps)
          └── MIS-6 (rendezvous) ◄── source éphéméride TLE

PHY-1 (drag off par défaut) ✔ résolu ── PHY-2 (drag par défaut + recalibrage)
                            └─ PHY-3 (MaxQ, télémétrie)

PHY-2 + PHY-3 ── MIS-10 (déorbitage + rentrée)
                     └── MIS-11 (Artemis : survol lunaire + retour)
                            ▲
                         MIS-4 (survol lunaire)

PHY-5 (étages largués) — aucun amont dur, mais sans PHY-2 il ne montre rien

RND-4 (ribbon) ✔ résolu ── NAV-5 (hover) — débloqué : la largeur est un uniform
```

Trois nœuds commandaient tout le reste : **MIS-8** (le plus en amont, et le
moins cher — tout ce qui durait plus d'un jour simulé butait dessus), **PHY-4**
(sans lui, rien de lunaire) et **MIS-2** (sans fenêtre de lancement, ni la Lune
ni un rendez-vous ne convergent — la cible n'est jamais au bon endroit).

**Les trois sont fermés** : `MIS-8` le 2026-08-09, `PHY-4` le 2026-08-18,
`MIS-2` le 2026-08-20. `PHY-4` s'est d'ailleurs fermé sans jamais dépendre de
`MIS-2` — aucun de ses six lots ne réclamait de fenêtre de lancement, tous se
testaient à géométrie Terre-Lune imposée (découpage §1).

`MIS-4` attendait les deux et n'attend donc plus personne : il est le prochain
item que rien n'a en amont. `MIS-6` n'attend plus qu'une **source éphéméride
TLE** — celle-ci reste bien de son ressort, et non un reliquat de `MIS-2`, pour
la raison écrite au §10 de
[`docs/mission-window/01-basics.md`](../mission-window/01-basics.md).

**Le quatrième nœud a disparu du graphe sans être livré.** `MIS-3` (Lambert +
LVLH) y figurait comme un amont de `MIS-6` ; il a été **dissous le 2026-08-20**
parce qu'il n'avait plus de contenu propre — le solveur de Lambert est arrivé
comme sous-produit de `PHY-4`, et le repère LVLH n'a aucun consommateur avant
`MIS-6` lui-même. Un nœud qui ne bloque plus rien ne doit pas rester dans le
graphe : la fiche au §6 dit où chaque moitié a été reversée.

**Le graphe a désormais une queue, et elle est entièrement commandée par `PHY-2`.**
`MIS-10` et `MIS-11` sont les deux premiers items du document à dépendre de
l'atmosphère *allumée* et non de la brique : `PHY-1` leur donne le modèle de
traînée, il ne leur donne pas une trajectoire qui redescend. `PHY-5`, lui, n'a
aucun amont dur — son calendrier est décidé par la disponibilité de maillages par
étage, qui ne sont pas du code et n'apparaissent donc dans aucun graphe.

---

## 6. Détail des items

*Fiches de référence, dans l'ordre des familles d'identifiants — pas dans
l'ordre d'exécution. Pour savoir par quoi commencer, voir le §3.*

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

#### RND-5 — Repère d'affichage des trajectoires — ★2 ◆2 S

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

**Indépendant de `RND-4`**, dans les deux sens : le ruban change la primitive
dessinée, cette bascule change le contenu du buffer.

**Spec.** [`trajectory-display-frame.md`](../graphics-effects/trajectory-display-frame.md).

---

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

#### NAV-5 — Hover « wow » — ★3 ◆2 M

Spec complète, et **le préalable est levé** : le boost d'épaisseur à ×2 sur hover
reposait sur `setLineWidth`, sans effet sur les drivers en profil core. `RND-4`
étant livré, la largeur et l'alpha sont des uniforms du matériau `Ribbon` — la
spec est applicable telle qu'écrite, et les deux animations de 150 ms qu'elle
demande sont deux `setFloat` par frame, sans reconstruction de géométrie.

**Spec.** [`docs/graphics-effects/hover-effects.md`](../graphics-effects/hover-effects.md).

---

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

#### FX-2 — Éclipses / pénombre inter-corps — ★4 ◆3 M

**Pourquoi.** Un vaisseau qui traverse le cône d'ombre de la Terre, la Lune qui
s'éteint en entrant dans l'ombre terrestre : c'est un phénomène *que la
simulation calcule déjà correctement* et que le rendu ignore.

**Réévaluation par rapport à `effects-roadmap.md` §6.3 (qui la classait ◆4).**
Le document supposait un pipeline `Unshaded` sans shader maison. Ce n'est plus
le cas : `MatDefs/Light/WrapLighting.frag` est notre shader, et son terme
d'éclairage tient en une ligne (`color += DiffuseSum.rgb * lightColor.rgb *
diffuseColor.rgb * diff * lightDir.w`). Deux niveaux de mise en œuvre :

- **Niveau 1 — facteur scalaire par corps (◆2).** Un uniform `m_EclipseFactor`
  multiplie `diff`. Calcul CPU analytique sphère/cône (Orekit fournit la
  géométrie), une valeur par corps et par frame. Couvre le vaisseau dans l'ombre
  de la Terre et la Lune éclipsée — les cas où l'occulteur couvre tout le corps.
- **Niveau 2 — occultation par fragment (◆3).** Passer position et rayon de
  l'occulteur en uniforms et calculer la fraction occultée dans le fragment
  shader. Nécessaire pour la **tache d'ombre lunaire sur la Terre** (éclipse
  solaire vue de l'espace), qui est l'image qui vaut le chantier.

Livrer le niveau 1 d'abord ; le niveau 2 réutilise le même point d'injection.

#### FX-3 — Particules de tuyère — ★4 ◆2 M *(ajout)*

Les vaisseaux glissent en silence, et rien à l'écran ne distingue une phase
propulsée d'un coast. `ParticleEmitter` (built-in) attaché au node du vaisseau,
blending additif, débit modulé par la magnitude de poussée, activation pilotée
par la phase courante via `MissionContext`. Synergie directe avec `RND-3` (code
couleur thrust/coast sur la trajectoire) : même information, deux canaux.

---

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

#### PHY-2 — Atmosphère par défaut + recalibrage — ★5 ◆4 L

Harris-Priester pour l'optimisation, NRLMSISE-00 pour la propagation runtime —
cohérent avec la philosophie 8×8 / 50×50 déjà en place. Relever le
`periapsisFloor` (100 km n'a plus de sens avec du drag), absorber les pertes
dans `dt1MaxPhysical`, re-baseliner `LEOMissionOptimizationTest` et la suite
paramétrique. C'est **le** chantier qui rend la simulation crédible : sans drag,
un gravity turn atteint son apogée avec moins d'ergols qu'un vrai lanceur.

Coût compute attendu : +5 % (Harris-Priester) à +50 % (NRLMSISE-00) sur une
optimisation CMA-ES.

#### PHY-3 — Détecteurs, télémétrie, UI de fidélité — ★3 ◆2 M

`MaxQDetector`, `AtmosphericInterfaceDetector` (ligne de Kármán — hook direct
pour une future rentrée), extension de `TelemetryWidgetAppState` avec Q et drag
instantané, sélecteur Off / Statique / Réaliste dans `StepParameters`. Le profil
`Q(t)` est le meilleur objet pédagogique que l'atmosphère apporte.

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

**Le troisième viewport n'a pas été nécessaire** (question ouverte n° 4, §7) :
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

#### PHY-5 — Étages largués : objets propagés et modèles 3D — ★4 ◆3 L *(ajout)*

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
- **Des maillages par étage.** Il n'y a qu'**un mesh par lanceur** aujourd'hui
  (`LauncherAssets` : `heavy_falcon.gltf`, `ariane/scene.gltf`), la pile entière
  d'un seul tenant. Et `src/main/resources/models/` n'est pas versionné : les
  modèles par étage sont un livrable **hors code**, à produire ou à sourcer avant
  que l'item soit démarrable. C'est cette contrainte qui décide de sa date, pas sa
  difficulté.
- **La séparation elle-même** : impulsion de séparation, deux corps qui s'écartent,
  et un débris qui garde son identité dans le breadcrumb et la télémétrie.

**Pourquoi `PHY` et pas `RND`.** La moitié visible est du rendu, mais ce qui manque
n'est pas un dessin : c'est un **objet propagé**. Tant que la simulation n'a qu'une
trajectoire par mission, le rendu n'a rien à montrer.

**Ce que l'item s'interdit.** L'optimiseur ne voit pas les débris. Un étage largué
est propagé pour l'affichage et la pédagogie, jamais dans la boucle CMA-ES : sinon
le coût d'une évaluation est multiplié par le nombre d'étages, pour un résultat qui
n'entre dans aucune fonction objectif.

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
(§4) : aucune couture ne s'accumule d'ici là, puisque le repère est fourni par la
bibliothèque et qu'aucun état relatif n'existe dans `simulation/`.
[`trajectory-display-frame.md`](../graphics-effects/trajectory-display-frame.md)
§8 disait déjà la même chose du côté rendu.

**Spec.** [`docs/brainstorm/leo-rendezvous-preparation.md`](../brainstorm/leo-rendezvous-preparation.md) §3.5, §3.6 — toujours la référence, désormais lue depuis `MIS-4` et `MIS-6`.

#### MIS-4 — Survol lunaire (TLI + flyby) — ★5 ◆4 L

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

**Spec.** [`docs/brainstorm/missions.md`](../brainstorm/missions.md) §8 (à
étendre : la spec traite TLI+LOI d'un bloc, le flyby seul est un palier
intermédiaire moins cher qui mérite d'être livré d'abord).

#### MIS-5 — Mise en orbite lunaire (LOI) — ★5 ◆3 M

Directement sur `MIS-4` : `LunarInsertionStage` (burn rétrograde à l'arrivée),
`LunarOrbitObjective` (altitude de périlune, inclinaison lunaire). L'essentiel
du coût est dans `MIS-4` ; ici on ajoute un stage et un objectif. Rapport
valeur/effort excellent une fois le survol acquis — raison pour laquelle les
deux sont séparés.

#### MIS-6 — Rendezvous / phasing sur cible TLE — ★5 ◆5 XL

Le plus gros item du document, et le mieux préparé : la spec dédiée fait 528
lignes et a déjà tranché l'essentiel.

**Décomposition.**
1. **Source TLE bufferisée.** `TLEPropagator` (SGP4) derrière
   `SlidingWindowEphemerisBuffer` — **non négociable** : à ×10⁵ de vitesse
   d'horloge, l'orbite cible défile entièrement entre deux frames, et une ligne
   d'orbite demande 100–500 points par rafraîchissement. Fenêtre bornée par la
   validité physique du TLE (±3 à 7 jours), pas de dataset 1990-2101.
2. **Abstraction `EphemerisTarget`** (scellée : `SolarBody` | `TleTarget`) —
   l'API est aujourd'hui couplée à l'enum `SolarSystemBody` de bout en bout.
   C'est le vrai coût de refactor du chantier.
3. **Stages** phasing (N révolutions entières, `Δa`) puis transfert Lambert.
4. **Coût** `‖Δr‖ + ‖Δv‖ + ΣΔv + corridor + ergols`, cible MVP Δr < 10 km,
   `‖Δv_rel‖` < 10 m/s.
5. **Rendu de la cible** : `TargetObjectRenderer`, aujourd'hui inexistant.
6. **Repère LVLH**, hérité de `MIS-3` dissous le 2026-08-20. `LOFType.LVLH` est
   fourni par Orekit et le dépôt pratique déjà `LOFType` + `LofOffset` ailleurs :
   le code tient en une dizaine de lignes. Ce qui appartient à cet item est la
   **forme** que prend le service — δr/δv bruts pour le coût terminal du point 4,
   ou figure relative pour le stretch ci-dessous — et elle ne se décide qu'ici,
   faute de consommateur avant.

**Tranché dans la spec, à ne pas rouvrir** : pas de Pontryagin (dans le cas
impulsif il ne rapporte rien sur une méthode directe), pas d'approche terminale
HCW au MVP, ISS seule comme cible.

**Stretch à fort rendement** : la vue LVLH dédiée. En repère inertiel, un
rendez-vous est une spirale illisible ; en LVLH, c'est une figure compacte
autour de la cible. C'est le bénéfice visuel n°1 de la feature.

**Spec.** [`docs/brainstorm/leo-rendezvous-preparation.md`](../brainstorm/leo-rendezvous-preparation.md).

#### MIS-10 — Déorbitage contrôlé et rentrée atmosphérique — ★5 ◆3 M *(ajout)*

**Promu du backlog.** L'item figurait au §7 depuis l'origine (« Molniya / HEO,
déorbitage et rentrée, … ») ; il en sort parce que son préalable est enfin daté.
Une fiche de brainstorm existe déjà —
[`docs/brainstorm/missions.md`](../brainstorm/missions.md) §6, cotée ★5 ◆3 — mais
elle est **périmée sur un point** : elle range `ReentryDetector` dans « ce qui
manque », alors qu'il existe
([`ReentryDetector.java`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/detector/ReentryDetector.java))
et qu'il est armé en production sur chaque leg d'étage.

**La garde de rentrée est à réparer avant tout le reste.** `PHY-1 / L0` §2.3 a
rejoué quatre rentrées par traînée avec `ReentryGuard.armQuiet` armé, exactement
comme `StageLegRunner` l'arme en vol : la garde **ne change rien** — pas un pas
d'intégration, pas une seconde sur la date d'échec. La cause est son plancher,
`SUBSURFACE_FLOOR = −50 km`, choisi parce que l'altitude *sphérique* d'un pas de
tir est déjà négative (la Terre est aplatie de 21,4 km) ; or l'intégrateur meurt à
−9 km et −30 km, c'est-à-dire **au-dessus** du plancher. Un détecteur inopérant
dans le seul régime pour lequel il existe est le premier livrable de l'item, pas un
détail d'intégration.

**À faire.**
- `DeorbitBurnStage` : burn rétrograde abaissant le périgée sous ~80 km. La brique
  existe — c'est un `ConstantThrustStage` en direction opposée.
- `ReentryObjective` : point d'impact visé, fenêtre d'entrée, heure de rentrée.
- Terminaison propre : `AtmosphericInterfaceDetector` (livré par `PHY-3`) pour
  marquer l'entrée, et une borne d'altitude qui arrête la propagation **avant** que
  l'intégrateur ne cède — la même borne que `PHY-1 / L0` §2.2 réclame déjà pour des
  raisons de temps de calcul.
- L'empreinte au sol du point d'impact ; la trace au sol du §7 cesse ici d'être un
  ornement.

**Le coût compute est le risque, et il est mesuré.** Entre 200 km et 130 km
d'altitude initiale, une descente sous traînée passe de 452 pas d'intégration à
**982 497** (`PHY-1 / L0` §2.3). Un déorbitage vise précisément ce régime : l'item
doit borner son temps de calcul par construction, pas espérer qu'il tienne.

**La désintégration n'est pas dans le périmètre** — voir la question ouverte n°8,
qui sépare les trois paliers et leurs coûts très inégaux.

#### MIS-11 — Mission de type Artemis : survol lunaire et retour — ★5 ◆4 L *(ajout)*

**Pourquoi.** C'est la mission qui ferme la boucle : partir de la Terre, contourner
la Lune, revenir. Aucune autre mission du document ne revient.

**L'aller est presque acquis.** `MIS-4` livre le TLI de production et le survol ;
`PHY-4 / L6` a déjà volé un translunaire complet — parking à 185 km, injection sur
seed Lambert, bascule de sphère d'influence à 74 h, périlune volé à 100,4 km pour
100 visés. La branche aller de `MIS-11` n'est donc pas un chantier neuf, c'est un
`MIS-4` **contraint** : le survol y est visé pour que la branche retour existe, et
non pour lui-même.

**Une correction sur l'énoncé.** Artemis, comme Apollo, ne repasse **pas** par une
orbite basse au retour : c'est une **entrée directe** depuis la trajectoire de
retour. Se mettre en LEO en revenant de la Lune coûte ~3,1 km/s de freinage — donc
un étage de plus à dimensionner — ou une **aérocapture**, c'est-à-dire un passage
atmosphérique dosé, qui exige la traînée de `PHY-2` *et* un modèle thermique que
personne n'a écrit ici. Les trois lectures donnent trois missions différentes ;
c'est la question ouverte n°9, à trancher avant d'écrire la spec.

**À faire.**
- `FreeReturnObjective` : viser le périlune **et** le périgée de la branche retour
  d'un seul coup. C'est la contrainte qui définit la mission, et elle n'a aucun
  équivalent dans les objectifs actuels, tous mono-orbite.
- Un horizon de mission de l'ordre de dix jours — `MissionHorizon` (livré par
  `MIS-8`) le porte déjà, c'est l'objet même de cet item.
- Une trajectoire à **trois** arcs (Terre → Lune → Terre) là où le lunaire en a
  deux. `PHY-4 / L3` a posé le repère par échantillon, donc le troisième arc ne
  devrait rien coûter — à vérifier : ce sera la première bascule de sphère
  d'influence dans le sens du retour, et la bande morte ε que `PHY-4 / L6` laisse
  ouverte n'a toujours pas de calibrage.
- La rentrée finale : `MIS-10` tel quel. C'est la raison de l'ordre des deux items
  dans la phase.

**Spec.** [`docs/brainstorm/missions.md`](../brainstorm/missions.md) §8 traite le
lunaire d'un bloc (TLI + LOI) et ne dit **rien** du retour ; à étendre avant de
commencer.

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

#### MIS-9 — Éphéméride de mission hors mémoire — **non planifié, conditionnel**

> Corollaire naturel de `MIS-8`, délibérément **non retenu dans une phase**. Ce
> n'est pas un refus : c'est un item dont la condition de déclenchement n'est
> pas remplie aujourd'hui, et qui coûterait cher s'il était fait trop tôt.

**L'idée.** Ne plus garder toute la trajectoire en mémoire : la générer en flux
vers le disque, et n'en charger qu'une fenêtre.

**Pourquoi ce n'est pas la bonne réponse *maintenant*.** Le tableau de `MIS-8`
montre que le pas d'échantillonnage variable rend tenable tout horizon
réaliste (30 jours ≈ 7 Mo). Le stockage hors mémoire achèterait le même
résultat pour dix fois le travail : format, versionnement, IO hors du fil de
rendu, fenêtre glissante, invalidation à la ré-optimisation, cycle de vie des
fichiers temporaires. Et il ne réglerait pas le vrai défaut — les deux
consommateurs aux besoins incompatibles (cf. `MIS-8`), qui restera entier
quelle que soit la localisation des octets.

**Conditions de déclenchement** (au moins une, et **mesurée**, pas supposée) :

1. un cas d'usage réel demande une résolution fine sur un horizon long — par
   exemple une décroissance orbitale sur des mois après `PHY-2`, ou une analyse
   post-mission qui veut chaque seconde de l'ascension **et** 30 jours de
   dérive ;
2. le nombre de missions simultanément visibles fait de la somme des
   éphémérides un poste mémoire mesuré, pas redouté ;
3. le mode batch (backlog) veut produire des trajectoires sans les afficher.

**Comment le faire le jour venu — et surtout, ce qu'il ne faut pas faire.**
Ne pas inventer un format de trajectoire mission sur disque. Le projet a déjà
toute la machinerie pour ça : `SlidingWindowEphemerisBuffer`,
`EphemerisWorker`, le format V1 zstd de `simulation/source/`, `LruCache`,
prefetch. Et `MIS-6` conclut déjà que la cible TLE doit passer par cette même
couche. Trois consommateurs convergent donc — planètes, cibles TLE,
trajectoires longues — et le geste juste est de **généraliser
`EphemerisSource` / `EphemerisTarget` une fois**, la trajectoire de mission
devenant une source parmi d'autres. Ce refactor est déjà compté dans `MIS-6` :
si `MIS-9` se déclenche, il se fait *après* lui et à son tarif marginal, pas
comme un chantier séparé.

---

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

#### UI-3 — Persistance / format de scénario — ★4 ◆3 M *(ajout)*

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
du drag en phase 6 : tous les scénarios écrits d'ici là le seront **sans**
atmosphère. Le champ « modèle d'atmosphère » doit donc figurer dans le format
dès la v1, même s'il ne vaut que `NONE` — sans lui, un scénario d'avant la
bascule se rejoue après avec une physique différente et personne ne le voit
passer. C'est aussi la raison pour laquelle stocker la trajectoire échantillonnée
serait un piège : elle deviendrait fausse sans que rien ne le signale.

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

#### UI-6 — Fenêtres déplaçables, empilement par focus, modalité du wizard — ★3 ◆2 M *(ajout)*

**Pourquoi.** `UI-5` a livré la mécanique et ne l'a appliquée qu'à une surface :
le panneau de gestion se déplace par son bandeau, borné par `WindowDragHandler`.
Sa fiche range explicitement l'**empilement par focus** dans son « ce qu'on ne
fait pas ». C'est cet écart qu'on solde ici, plus la généralisation du glisser aux
autres fenêtres.

État des lieux — cinq natures de surface, trois comportements :

| Surface | Couche | Déplaçable | Bandeau |
|---|---|---|---|
| Panneau de gestion (`MissionPanelWidget`) | `WINDOW` (20) | oui | `PanelHeader`, 88 px |
| Panneau d'affichage (`MissionDisplayPanelWidget`) | `PANEL` (10) | non — ré-ancré haut-gauche (`:183`) | `DisplayPanelHeader`, 36 px |
| Wizard (`MissionWizardWidget`) | `MODAL` (101) | non — modal, centré (`:281`) | — |
| `ConfirmDialog` | `DIALOG` (201) | non, et c'est correct | — |
| HUD (capsule, timeline, télémétrie, breadcrumb) | `HUD` (0) | non, et c'est correct | — |

« Toutes les fenêtres » désigne donc les deux panneaux, plus le wizard s'il cesse
d'être modal. Le HUD est ancré par nature et un dialogue bloquant n'a pas à fuir.
Il faut un critère mécanique plutôt qu'un arbitrage par widget : **est une fenêtre
ce qui porte un bandeau de préhension.** Poser ce critère dans le code — une
interface, ou un composant `Window` qui impose le bandeau — évite d'avoir à
retrancher la question à chaque nouveau widget.

**Le point dur : « actualiser le z » casse un invariant énoncé.** Dans
`UiLayers`, l'échelle de profondeur **est aussi** l'ordre de renvoi d'`ESC` —
`HudSurfaces.topmostOpen()` classe sur `layer()`, et `UI-5` en a fait une
propriété revendiquée (« one ordering, two uses »). Deux conséquences immédiates :

1. Remonter une fenêtre au clic change la cible d'`ESC`.
2. `HudSurface.layer()` est un `float` figé dans le record, fixé une fois pour
   toutes à l'enregistrement (`HudSurface.java:18-19`). Un `z` qui bouge à chaud
   laisserait le registre classer sur une valeur périmée — donc `layer` doit
   devenir une valeur lue à la demande, comme `openCheck` l'est déjà, ou être
   remplacée par la lecture du `z` réel du spatial.

Sur le fond, deux issues :

- **(a) assumer** que les deux ordres restent le même : `ESC` renvoie ce qui est
  devant, et ce qui est devant est ce qu'on vient de cliquer. Cohérent, et ne
  coûte que le point 2 ci-dessus.
- **(b) dissocier** ordre de rendu et ordre de renvoi. Contredit `UI-5` et double
  l'état à tenir, pour un gain qui reste à formuler.

Recommandation : **(a)**, avec une contrainte non négociable — *le rehaussement
réordonne à l'intérieur d'une bande, jamais entre bandes.* Une fenêtre ne doit
jamais passer devant le menu applicatif ni devant un modal. Concrètement
`z = WINDOW + k`, avec `k` un ordinal compact borné par l'écart à la bande
suivante (`MENU_CATCHER − WINDOW = 20`), soit vingt fenêtres empilables — très
au-delà du besoin. Corollaire : si le panneau d'affichage devient déplaçable, il
doit **rejoindre la bande `WINDOW`**. Laisser deux fenêtres qui peuvent se
recouvrir dans deux bandes distinctes, c'est garder un ordre figé sous un
mécanisme censé le libérer.

**À faire.**

1. **Extraire le trio qui fait une fenêtre** — bandeau, `WindowDragHandler`,
   placement initial puis clamp au redimensionnement — hors de
   `MissionPanelWidget`. Il en existe un exemplaire, il en faut deux, et la
   troisième copie est prévisible (le wizard) : c'est exactement le cas où
   `dette-technique.md` §6.3 demande de factoriser à la deuxième.
2. **Rendre le panneau d'affichage déplaçable**, en corrigeant le piège déjà
   rencontré : il se **repositionne** sur son ancrage
   (`MissionDisplayPanelWidget:183`), donc il reviendrait se coller en haut à
   gauche à chaque reconstruction de liste. La règle inverse est déjà écrite et
   appliquée à l'autre fenêtre — placer à la première frame, ne plus y toucher
   (`MissionPanelWidget:395`).
3. **Registre d'empilement** porté par `ApplicationContext` (la règle « pas de
   `getState()` » interdit que les `AppState` concernés s'interrogent) : il
   connaît les fenêtres ouvertes, attribue les `k`, les réattribue au clic et les
   compacte à la fermeture. Le clic doit être capté sur la **racine** de la
   fenêtre et sans consommer l'événement : cliquer une ligne de mission doit
   remonter la fenêtre *et* sélectionner la ligne.
4. **Aucune persistance.** Position et ordre repartent du défaut à chaque
   lancement. `UI-5` a déjà exclu la position sur disque, et la question plus
   large des préférences utilisateur est ouverte en §8.6 — la rouvrir ici
   mélangerait deux chantiers.

**La modalité du wizard.** Deux faits, pas une préférence :

- **Pour la garder modale** — le wizard porte un état non enregistré ; c'est
  l'argument qu'`UI-5` a retenu et rien ne l'a périmé. Une fenêtre non modale
  qu'on peut perdre derrière une autre avec un formulaire à moitié rempli est un
  piège, et `confirmDiscard` ne protège que la fermeture explicite.
- **Contre** — `OrbitLabApplication:141` coupe l'entrée souris de la caméra tant
  que `isWizardVisible()`. Tel quel, un wizard non modal **gèlerait la navigation
  3D** pendant toute sa présence à l'écran, c'est-à-dire précisément quand on
  voudrait regarder la scène. Démodaliser impose donc de revoir cette condition :
  la caméra doit céder la souris quand le curseur survole une surface, pas quand
  une surface existe.

Le besoin derrière la question — *consulter la liste des missions pendant la
création* — mérite d'être constaté avant de payer les deux changements. S'il ne
se manifeste pas, garder le wizard modal est la réponse la moins chère ; et
l'écrire noir sur blanc évite de rouvrir le débat tous les trois mois. Question
rangée en **§8.7**.

**Ce qu'on ne fait pas.** Redimensionnement, réduction en barre, ancrage
magnétique aux bords, positions persistées sur disque, fusion des deux panneaux
mission. Même périmètre exclu qu'`UI-5`, à ceci près que l'empilement par focus
en sort.

**Validation.** Testables sans contexte GL : l'attribution des ordinaux (bande
respectée, pas de collision, compactage stable à la fermeture d'une fenêtre du
milieu) et le classement de `HudSurfaces` sous un `layer` devenu mobile. Le clamp
l'est déjà (`WindowDragHandlerTest`, 11 tests). À vérifier à l'écran, comme
`UI-5` l'avait fait pour les mêmes raisons : que le picking suive le nouveau `z`
sans reconstruction, qu'une fenêtre remontée passe bien **sous** le menu
applicatif, et que deux fenêtres qui se recouvrent supportent le clic alterné
sans scintillement d'ordre.

**Spec.** À écrire avant de coder — `docs/ui/02-fenetres-et-empilement.md`. Les
deux points qui la justifient : le choix (a)/(b) sur l'unicité de l'ordre, et le
sort du wizard. Le reste est de l'exécution.

---

#### UI-7 — Tooltips sur les contrôles, et le socle de survol qui les porte — ★3 ◆2 M *(ajout)*

**Pourquoi.** Plusieurs contrôles n'ont pour toute étiquette qu'une icône, et
rien ne dit ce qu'ils font tant qu'on ne les a pas cliqués : les trois icônes de
ligne du panneau d'affichage (télémétrie, visibilité, engrenage —
`DisplayRowIcons`), les actions de ligne du panneau de gestion (`RowActionIcons`),
les chevrons de pagination (`PaginationBar`), et surtout le contrôle segmenté
*Fast / Balanced / Precise* (`ModeSegmentedControl`), dont les trois pictogrammes
n'ont aucune chance d'être devinés. Le critère est celui-là, et il vaut mieux
qu'une liste : **un contrôle doit une infobulle dès que son étiquette est une
icône, ou qu'elle est tronquée.** Les entrées de menu, qui portent un libellé,
n'en ont pas besoin.

**« Mutualiser ce comportement » est en réalité le cœur de l'item.** Une
infobulle se déclenche sur le couple `mouseEntered` / `mouseExited` — exactement
celui que 22 fichiers de `ui/` recâblent déjà à la main pour leurs effets de
survol ([`BUG-4`](../bugs.md#bug-4--hover-des-widgets-non-uniforme)). Livrer les
infobulles sur leur propre listener donnerait 23 sites au lieu de 22, et deux
écouteurs concurrents sur les mêmes spatiaux. Les deux chantiers n'en font donc
qu'un, et l'ordre est imposé : **le helper de survol partagé d'abord, l'infobulle
comme premier client de ce helper.** À ce titre `UI-7` absorbe `BUG-4`, selon la
convention de `docs/bugs.md` (un bug qui s'avère être un chantier est promu en
item de roadmap).

**L'antériorité existe, mais elle n'est pas réutilisable telle quelle.**
`TimelineTooltip` (dans `ui/timeline/mission/`) est une infobulle qui marche et
qui a été pensée — mais pour un seul emplacement. Quatre de ses choix sont
locaux, et chacun est un point de conception à reprendre :

| Choix de `TimelineTooltip` | Pourquoi il ne se généralise pas |
|---|---|
| Classe *package-private*, attachée à la racine du widget avec un `z` **local** (`Z_TOOLTIP = 10f`) | Le bucket GUI trie sur le `z` **monde**. Une infobulle héritant du `z` de son panneau (`PANEL` = 10) passerait derrière la fenêtre de gestion (`WINDOW` = 20). |
| La carte s'ouvre **vers le haut**, et le Javadoc explique pourquoi (`:59-64`) | Le raisonnement est juste, et il est propre à la bande la plus basse de l'écran. Ailleurs, il faut décider selon la place disponible. |
| Largeur estimée au caractère (`CHAR_WIDTH = 5.4f`) | Exact au pixel pour la police bitmap monospace à 10 px, faux pour toute autre. L'UI en utilise plusieurs (IBM Plex Mono 11, Sora 13). |
| Reconstruite seulement quand le texte change (`:80-83`) | Celui-là se garde : c'est la protection contre une allocation de labels par frame, et une infobulle ancrée au contrôle en aura d'autant moins besoin. |

**Décisions à prendre — c'est la spec, pas l'implémentation.**

1. **Une couche à elle.** Une infobulle doit passer devant tout, y compris devant
   un dialogue bloquant dont elle décrit un bouton : `UiLayers.TOOLTIP` au-dessus
   de `DIALOG` (201), donc 300. **Et elle ne s'enregistre pas dans
   `HudSurfaces`** : `UI-5` a posé que la couche *est* l'ordre de renvoi d'`ESC`,
   or `ESC` n'a pas à « fermer » une infobulle — elle disparaît d'elle-même. C'est
   la première surface devant tout et absente du registre ; le noter dans le
   Javadoc d'`UiLayers` évite qu'on l'y inscrive par symétrie.
2. **Ancrée au contrôle, pas au curseur.** `TimelineTooltip` suit le curseur parce
   qu'elle décrit une *position* sur une piste. Une infobulle de bouton décrit le
   bouton : elle doit se poser à côté de lui et ne plus bouger, sans quoi elle
   tremble sous la main. Placement avec bascule (au-dessus / en dessous / à
   gauche / à droite) selon la place restante, plutôt qu'un clamp — un clamp la
   ferait glisser sous le curseur.
3. **Le délai, et donc qui tient l'horloge.** Une infobulle immédiate est
   agressive, un survol de traversée ne doit rien déclencher : ~500 ms d'attente,
   et une période « chaude » où passer d'un bouton au voisin affiche sans
   attendre. Les listeners Lemur ne reçoivent pas de `tpf` : soit un `AppState`
   unique fait avancer le gestionnaire d'infobulles, soit on échantillonne
   `nanoTime` dans les événements. Le premier est plus honnête et reste un seul
   `AppState`.
4. **Mesure du texte.** Sortir de l'estimation au caractère, ou la rendre
   dépendante de la police. **Piège connu** : les polices bitmap du HUD échouent
   *en silence* sur une taille non embarquée ou un glyphe absent — le signe moins
   U+2212 en particulier. Un texte d'infobulle est du texte arbitraire, donc
   c'est exactement là que ça se reproduira ; fixer le jeu de caractères autorisé,
   ou vérifier la police retenue, fait partie de la décision.

**À faire.**

1. Le helper de survol partagé (le composant que `BUG-4` réclame) : un point
   d'entrée unique qui pose *un* listener et distribue à ses clients — skin de
   survol, infobulle, curseur. Contrat d'états à écrire d'abord : *idle /
   survolé / actif-sélectionné / désactivé / focus*, lesquels s'excluent, et ce
   que chacun modifie.
2. Le gestionnaire d'infobulles : une instance, portée par `ApplicationContext`
   (pas de `getState()`), qui détient la carte unique, son délai et son
   placement. Une seule infobulle à l'écran à la fois — c'est aussi ce qui évite
   d'en avoir deux orphelines quand un panneau se reconstruit sous le curseur.
3. Migration des 22 sites, et pose des textes sur les contrôles listés plus haut.
4. **Un contrôle désactivé garde son infobulle**, et c'est même là qu'elle sert le
   plus — elle peut dire *pourquoi* il est désactivé. À noter parce que le code
   actuel fait l'inverse à un endroit : `ModeSegmentedControl:105-108` sort avant
   de poser le moindre listener quand le segment est inerte.

**Ce qu'on ne fait pas.** Pas d'infobulle riche (mise en forme, icône, lien),
pas d'aide contextuelle ni de tour guidé, pas de raccourci clavier affiché dans
la carte tant qu'il n'existe pas de table de raccourcis, pas de traduction.

**Validation.** Testable sans contexte GL : la machine à états du délai (rien
avant 500 ms, affichage après, période chaude entre deux contrôles voisins), le
choix de placement selon la place restante (les quatre bascules, aux quatre
coins de l'écran), et l'unicité de la carte. À l'écran : qu'une infobulle
ouverte sur un bouton de dialogue passe bien devant le dialogue, et qu'un
panneau reconstruit sous le curseur n'en laisse pas une derrière lui.

**Spec.** À écrire — `docs/ui/03-survol-et-infobulles.md`. Le contrat d'états du
§1 est le livrable qui compte : sans lui, la mutualisation reproduira les
divergences actuelles avec de nouvelles valeurs.

---

## 7. Backlog non planifié

Gardé hors phases, à remonter si le besoin se manifeste :

- **Rendu** — god-rays, normal maps, lumières de villes côté nuit, halo
  atmosphérique Fresnel, anneaux de Saturne, ombres portées, trace au sol
  (ground track), enveloppe d'incertitude autour du nominal.
- **Profondeur** — logarithmic depth buffer ou reverse-Z, troisième viewport
  « mid ». `RND-1` suffit aujourd'hui ; à rouvrir une fois les missions lunaires
  en place (Terre + Lune + vaisseau dans le même cadre est précisément le cas
  qui fait exploser le ratio far/near). Le plan near y est piloté par la
  distance à l'origine, ce qui suppose que le contenu le plus proche s'y trouve —
  hypothèse qui tombe justement dans ce cas-là.
- **Missions** — Molniya / HEO, déploiement de constellation, points de
  Lagrange, interplanétaire, gravity assist. **Le déorbitage et la rentrée ont
  quitté cette liste** : ils sont devenus `MIS-10`, en phase 7.
- **Plateforme** — mode batch headless, analytics et graphes post-mission,
  replays cinématiques, catalogue de débris TLE, validation contre données
  réelles (JPL Horizons), scripting.
- **Éphéméride hors mémoire** — `MIS-9`, fiche complète en §6 avec ses
  conditions de déclenchement. Rangé ici et non dans une phase : le pas
  variable de `MIS-8` le rend inutile pour tout horizon réaliste, et il devra
  passer par la généralisation d'`EphemerisSource` faite en `MIS-6`.
- **Optimiseur** — mode CMA-ES pour la composition GEO (les 3 modes composent
  aujourd'hui la même `GEOMission` analytique ; seul le levier ergols agit
  réellement sur GEO).

Détail et notation dans [`docs/brainstorm/features-long-terme.md`](../brainstorm/features-long-terme.md)
et [`docs/brainstorm/missions.md`](../brainstorm/missions.md).

---

## 8. Questions ouvertes

1. ~~**Horizon de mission (MIS-8)** — purement dérivé du type de mission, ou
   réglable dans le wizard avec ce dérivé comme défaut ?~~ **Tranchée le
   2026-08-09 : les deux.** `MissionHorizon.Revolutions` fournit le dérivé,
   pré-rempli comme défaut, et le wizard laisse basculer en manuel
   (`FixedDuration`) — la bascule revenant au dérivé quand on la relâche.
2. ~~**Fenêtre de la piste temporelle (NAV-2)** — durée de la mission
   sélectionnée, ou fenêtre glissante autour de `now()` ?~~ **Tranchée le
   2026-08-11 : la durée de la mission, dans un widget séparé.** La piste
   temporelle quitte la capsule ; sa fenêtre est celle de l'éphéméride de la
   mission suivie, et sans éphéméride le widget ne s'affiche pas — donc pas de
   fenêtre glissante, pas de bascule. La capsule garde son `ScrubberTrack`
   indexé sur la vitesse, ce qui clôt aussi la cohabitation que `NAV-3`
   redoutait. Voir [`docs/navigation/02-timeline-mission.md`](../navigation/02-timeline-mission.md).
3. **Auto-optimisation après création** — toujours ouverte, mais **son préalable
   est levé depuis le 2026-08-21**. Aujourd'hui `createMission()` ajoute l'entrée
   en `DRAFT` sans déclencher de calcul ; l'argument qui bloquait — déclencher
   automatiquement un calcul long sans indicateur serait pire que le clic actuel
   — ne tient plus, `UI-2` ayant livré l'indicateur, et jusqu'à la distinction
   entre une mission en file et une mission en calcul. Reste à trancher la
   question elle-même, qui n'est plus technique.
4. ~~**Troisième viewport**~~ — **tranchée le 2026-08-18 : non.** `PHY-4 / L5`
   §5.3 l'a écarté sur mesure — un seul globe est dessiné, dans la région de
   l'origine où le pas de profondeur vaut 27 km, et le bout lointain du trait ne
   dispute la profondeur qu'à lui-même — puis `L6` §12.5 a confirmé sur la
   première trajectoire lunaire réelle : un globe et un trait tiennent dans la
   near viewport à l'échelle Terre-Lune, sans reverse-Z ni depth log. La question
   ne rouvre qu'avec **deux globes dans le même cadre**, c'est-à-dire `MIS-6`.
5. **Cible du rendez-vous** — ISS livrée en dur (option A de la spec) suffit au
   MVP. L'import de TLE arbitraire (option B) est une feature UI à part entière,
   à ne pas glisser dans `MIS-6`.
6. **Persistance des bascules d'affichage (UI-4)** — volatiles, remises à leur
   défaut à chaque lancement, ou conservées ? Si conservées, elles n'ont rien à
   faire dans le fichier de scénario de `UI-3` : ce sont des préférences
   **utilisateur**, pas des données de mission — deux fichiers, pas un, sans
   quoi rejouer le scénario d'un tiers reconfigure l'écran de celui qui
   l'ouvre. À trancher au moment de `UI-3` ; `UI-4` peut être livré volatile
   sans créer de dette.
7. **Modalité du wizard de création (UI-6)** — reste-t-il modal ? L'argument
   d'`UI-5` (état non enregistré, perdable derrière une autre fenêtre) tient
   toujours ; le coût de la bascule n'est pas dans le wizard mais dans la
   caméra, qui refuse aujourd'hui la souris tant que le wizard *existe* et non
   tant qu'il est *survolé* (`OrbitLabApplication:141`). À ne trancher que sur
   un besoin constaté — consulter la liste des missions pendant la création.
   Recommandation par défaut : le garder modal et l'écrire, plutôt que de
   laisser la question ouverte indéfiniment.
8. **Jusqu'où va la rentrée (MIS-10)** — la « désintégration » recouvre trois
   paliers de coûts sans commune mesure. (a) La trajectoire s'arrête à
   l'interface atmosphérique et on affiche le point d'impact prédit : c'est le
   périmètre écrit dans la fiche. (b) La traînée plasma, qui est un **effet** et
   non une physique — elle est déjà spécifiée dans
   [`docs/graphics-effects/effects-roadmap.md`](../graphics-effects/effects-roadmap.md)
   §5.5, cotée difficulté 4 / wow 5 dans le barème de ce document (attention, son
   ★ est la difficulté, à l'inverse d'ici). (c) La désintégration réelle — flux
   thermique, ablation, fragmentation — qui est de la R&D. Recommandation : (a)
   dans `MIS-10`, (b) comme item `FX` distinct une fois `MIS-10` volé, (c) au
   backlog. `PHY-1 / L1` §4 note d'ailleurs que l'approximation « panneaux
   repliés » du coefficient balistique cesse d'être vraie pour une rentrée de fin
   de vie — la physique de (c) commence donc avant le flux thermique.
9. **Entrée directe, capture propulsive ou aérocapture (MIS-11)** — l'énoncé
   demandait « retour en LEO puis retour sur Terre », ce qui n'est ni Apollo ni
   Artemis. L'entrée directe est la moins chère et la plus fidèle au nom de
   l'item ; la capture propulsive en LEO coûte ~3,1 km/s, donc un étage de plus à
   dimensionner, et a un vrai intérêt pédagogique — elle montre *pourquoi*
   personne ne le fait ; l'aérocapture est la plus spectaculaire et ajoute un
   modèle thermique à un chantier déjà ◆4. À trancher avant la spec : les trois
   ne décrivent pas la même mission, ni le même lanceur.
