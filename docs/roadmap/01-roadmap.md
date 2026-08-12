# Roadmap OrbitLab — révision 2026-08-08

Ce document remplace `docs/roadmap/01-short-term.md` (supprimé : ses phases 0
et 1 sont soldées, cf. §1). Il est **la** porte d'entrée du dossier `docs/` :
chaque item renvoie vers la spec détaillée quand elle existe, et l'index
complet des documents est dans [`docs/README.md`](../README.md).

**Comment le lire.** Le plan, c'est le **§3** — six phases dans l'ordre. Le §4
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

**Reste ouvert de l'ancienne roadmap** : le feedback de progression pendant
l'optimisation, repris ici en `UI-2`. La vue détail avec résultats
d'optimisation, l'autre reliquat, est **résolue le 2026-08-10** (`UI-1`) — et
son diagnostic était trop optimiste : `AchievedOrbit` n'était pas seulement non
lu par `ui/`, il était **jeté** par l'orchestrateur, qui n'en gardait rien.

**Corrections d'hypothèses par rapport aux specs graphiques** — `effects-roadmap.md`
§1 décrit un rendu « tout `Unshaded`, aucun shader custom, pas de skybox ». Ce
n'est plus vrai : le projet possède désormais son propre shader d'éclairage.
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

**C'est cette section qui dit quoi faire, et dans quel ordre.** Six phases, à
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

### Phase 2 — Navigation, temps, caméra · ~2,5 semaines

> Rendre la scène et la timeline parcourables. C'est le bloc « timeline et
> navigation 3D » + « transitions de caméra ». `NAV-3` suit `NAV-2` ; `NAV-4`
> peut se faire à tout moment. `UI-4` passe **avant** `NAV-2` : la spec de la
> piste temporelle demande explicitement de recopier `MissionPanelTrigger`
> (style et `setEnabled`), donc dans l'autre ordre on duplique ce qu'il faut
> corriger.

| ID | Item | ★ | ◆ | Taille |
|---|---|:-:|:-:|:-:|
| ~~NAV-1~~ | ~~Transitions de caméra entre vues~~ — résolu | 4 | 2 | M |
| UI-4 | **Menu applicatif haut-gauche** (remplace le bouton « Missions ») | 3 | 2 | M |
| NAV-2 | Timeline indexée sur le temps + marqueurs d'événements | 4 | 3 | M |
| NAV-3 | Scrub continu (glisser sur la piste) | 3 | 2 | S |
| RND-4 | Ribbon billboardé (orbites + trajectoires) | 4 | 3 | M |
| NAV-4 | Breadcrumb de navigation 3D | 3 | 2 | M |

**Fin de phase quand** : on atteint n'importe quel instant d'une mission à la
souris, on change de corps focalisé sans cut, et le seul élément de HUD visible
en permanence ne détonne plus avec le reste de l'interface.

### Phase 3 — Socle physique et mission partagé · ~4,5 semaines

> **La phase pivot.** Rien ici n'est spectaculaire pris isolément ; tout est
> réclamé par les missions lunaires (phase 4) et par le rendez-vous (phase 5).
> C'est aussi la phase la plus risquée en estimation.
>
> Les deux derniers items sont de l'**outillage** plutôt que du socle physique,
> et c'est délibéré : ils rendent les phases 4 et 5 tenables au quotidien.

| ID | Item | ★ | ◆ | Taille | Sert à |
|---|---|:-:|:-:|:-:|---|
| MIS-7 | `EarthOrbitMission` paramétrable | 4 | 2 | M | MIS-2, MIS-6, + polaire/SSO/MEO gratuits |
| PHY-4 | Socle multi-corps (3ᵉ corps, SOI, repères) | 5 | 4 | L | MIS-4, MIS-5 |
| MIS-2 | Fenêtres de lancement | 4 | 3 | M | MIS-4, MIS-6 |
| MIS-3 | Solveur de Lambert + repère LVLH | 4 | 3 | M | MIS-6, ciblage lunaire |
| PHY-1 | Atmosphère : la brique, **off** par défaut | 4 | 3 | L | PHY-2, PHY-3 |
| UI-2 | Feedback de progression pendant l'optimisation | 3 | 2 | M | confort des phases 4 et 5 |
| UI-3 | Persistance des missions / format de scénario | 4 | 3 | M | **outil de dev** des phases 4 et 5 |

**Pourquoi `UI-3` est ici et pas en phase 6.** Il n'a aucune dépendance, et son
bénéfice principal à ce stade n'est pas la feature mais l'outillage : sans lui,
mettre au point une mission lunaire ou un rendez-vous impose de re-saisir la
mission dans le wizard **à chaque lancement de l'application**. Un save/load
livré avant les phases 4 et 5 se rembourse pendant celles-ci ; livré après, il
ne rembourse rien.

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

### Phase 6 — Réalisme et spectacle · ~2,5 semaines

| ID | Item | ★ | ◆ | Taille |
|---|---|:-:|:-:|:-:|
| PHY-2 | Atmosphère par défaut + recalibrage optimiseur | 5 | 4 | L |
| PHY-3 | Détecteurs MaxQ / interface + télémétrie + UI fidélité | 3 | 2 | M |
| FX-3 | Particules de tuyère | 4 | 2 | M |
| NAV-5 | Hover « wow » planètes + orbites | 3 | 2 | M |

**Note pour `PHY-2`** : les scénarios écrits en phase 3 par `UI-3` datent d'avant
la bascule du drag. Le champ « modèle d'atmosphère » du format doit donc exister
**dès `UI-3`**, même s'il ne vaut que `NONE` à ce moment-là — sinon les scénarios
d'avant deviennent silencieusement faux au moment du basculement.

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
| MIS-7 | `EarthOrbitMission` paramétrable → polaire / SSO / MEO | 4 | 2 | M | — |
| ~~RND-2~~ | ~~Filtrage anisotrope (MSAA déjà actif)~~ — résolu | 2 | 1 | S | — |
| FX-2 | Éclipses / pénombre inter-corps | 4 | 3 | M | — |
| FX-3 | Particules de tuyère | 4 | 2 | M | — |
| NAV-4 | Breadcrumb de navigation 3D | 3 | 2 | M | — |
| UI-2 | Feedback de progression pendant l'optimisation | 3 | 2 | M | — |
| UI-4 | Menu applicatif haut-gauche (remplace le bouton « Missions ») | 3 | 2 | M | — (à faire **avant** NAV-2) |
| NAV-2 | Timeline indexée sur le temps + marqueurs d'événements | 4 | 3 | M | — |
| NAV-3 | Scrub continu (glisser sur la piste) | 3 | 2 | S | NAV-2 |
| RND-4 | Ribbon billboardé (orbites + trajectoires) | 4 | 3 | M | — |
| MIS-3 | Solveur de Lambert + repère LVLH | 4 | 3 | M | — |
| MIS-2 | Fenêtres de lancement | 4 | 3 | M | MIS-7 |
| NAV-5 | Hover « wow » planètes + orbites | 3 | 2 | M | RND-4 (conseillé) |
| UI-3 | Persistance des missions / format de scénario | 4 | 3 | M | — |
| PHY-4 | Socle multi-corps (3ᵉ corps, SOI, repères) | 5 | 4 | L | — |
| MIS-5 | Mise en orbite lunaire (LOI) | 5 | 3 | M | MIS-4 |
| MIS-4 | Survol lunaire (TLI + flyby) | 5 | 4 | L | PHY-4, MIS-2 |
| PHY-1 | Atmosphère : brique drag, désactivée par défaut | 4 | 3 | L | — |
| PHY-3 | Détecteurs MaxQ / interface + télémétrie + UI fidélité | 3 | 2 | M | PHY-1 |
| PHY-2 | Atmosphère par défaut + recalibrage optimiseur | 5 | 4 | L | PHY-1 |
| MIS-6 | Rendezvous / phasing sur cible TLE | 5 | 5 | XL | MIS-2, MIS-3, MIS-7 |

---

## 5. Graphe de dépendances (l'essentiel)

```
MIS-8 (horizon de mission) ✔ résolu — ses trois aval sont débloqués
   ├── NAV-2 (piste temporelle) ── NAV-3 (scrub)
   ├── MIS-4 / MIS-5 (lunaire : coast TLI ~3 j > horizon actuel)
   └── MIS-6 (rendezvous : phasing sur N révolutions)

MIS-7 (mission Terre paramétrable)
   └── MIS-2 (fenêtres de lancement)
          ├── MIS-4 (survol lunaire) ──── MIS-5 (orbite lunaire)
          │      ▲
          │   PHY-4 (multi-corps)
          └── MIS-6 (rendezvous) ◄── MIS-3 (Lambert + LVLH)
                                  ◄── source éphéméride TLE

PHY-1 (drag off par défaut) ── PHY-2 (drag par défaut + recalibrage)
                            └─ PHY-3 (MaxQ, télémétrie)

RND-4 (ribbon) ── NAV-5 (hover)

UI-4 (menu haut-gauche) ┄┄ NAV-2 (son toggle est spécifié « sur le motif
                        ┊    de MissionPanelTrigger » → le faire après duplique)
                        ┄┄ UI-3 (ses deux entrées de menu ont besoin d'un hôte)
```

Les traits pointillés autour d'`UI-4` ne sont pas des dépendances techniques :
`NAV-2` et `UI-3` se font sans lui. C'est un ordre qui évite de refaire deux
fois le même travail.

Trois nœuds commandaient tout le reste : **MIS-8** (le plus en amont, et le
moins cher — tout ce qui durait plus d'un jour simulé butait dessus), **PHY-4**
(sans lui, rien de lunaire) et **MIS-2** (sans fenêtre de lancement, ni la Lune
ni un rendez-vous ne convergent — la cible n'est jamais au bon endroit).

`MIS-8` étant livré, il n'en reste que deux : **PHY-4** et **MIS-2** sont
désormais les seuls verrous du haut du graphe.

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
croisements de boucles (spec §6). `RND-3` étant clos sans les traiter, ils
relèvent désormais de `RND-4`, ou d'un identifiant propre le jour où l'un des
deux gêne.

**Spec.** [`docs/graphics-effects/spacecraft-view-artefacts.md`](../graphics-effects/spacecraft-view-artefacts.md)
§9.1 à §9.3 (ce qui a été fait, et les écarts avec ce qui était prévu).

#### ~~RND-2 — Filtrage anisotrope — ★2 ◆1 S~~ — **RÉSOLU le 2026-08-09**

**Pourquoi.** Complète le MSAA déjà actif. Effet réel mais modeste : les
textures planétaires vues en biseau.

**Attention — ce que le MSAA ne fait pas.** Il n'antialiase pas les lignes GL de
manière fiable, et `glLineWidth > 1` est silencieusement plafonné à 1 px sur les
drivers en profil core. L'aliasing des orbites ne se règle donc **pas** par les
réglages `AppSettings` : c'est `RND-4` (ribbon) qui le règle. Ne pas attendre de
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

**Reste à affiner, sous `RND-4`.** Le contraste entre phases reste modeste sur
une ligne GL de 2 px ; le réglage fin (`MUTING_STEP`) attend le ribbon
billboardé, qui donnera la surface nécessaire pour le juger.

**Spec.** [`mission-phase-encoding.md`](../graphics-effects/mission-phase-encoding.md),
qui remplace [`effects-roadmap.md`](../graphics-effects/effects-roadmap.md)
§9.3.1 et §9.3.2.

#### RND-4 — Ribbon billboardé — ★4 ◆3 M

**Pourquoi.** Seule vraie réponse au plafonnement de `glLineWidth` : épaisseur
stable, antialiasing par alpha-fade des bords, lisibilité à distance. Débloque
ensuite les tirets animés, le halo additif et le hover (`NAV-5`).

**Spec.** [`ribbon-lines.md`](../graphics-effects/ribbon-lines.md) — remplace
[`effects-roadmap.md`](../graphics-effects/effects-roadmap.md) §9.4.1. Traitement
retenu : expansion en **vertex shader**, pas côté CPU (comparatif chiffré en §5
du document) ; les orbites planétaires sont une géométrie statique de 40 960
sommets qu'un ribbon CPU rendrait dynamique.

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

#### NAV-2 — Timeline indexée sur le temps + marqueurs d'événements — ★4 ◆3 M

**Pourquoi.** `ScrubberTrack` n'a aujourd'hui aucune notion de date : ses 21
graduations sont décoratives et indexées sur la **vitesse**. Une timeline de
simulation orbitale qui ne représente pas le temps est une anomalie.

**Dépendait de `MIS-8`, désormais levé.** La fenêtre représentée par la piste,
c'est la durée de la mission : tant que cette durée était une constante
arbitraire, la piste l'était aussi et aurait été à refaire. `MissionHorizon`
donne maintenant cette durée, donc la piste peut s'indexer dessus directement.

**À faire, dans l'ordre.**
1. ~~Trancher la fenêtre représentée~~ **tranché** : durée de l'éphéméride de la
   mission suivie (focus télémétrie), dans un **widget séparé** de la capsule.
   La question ouverte n°2 est close par là.
2. Fonction temps ↔ position, puis marqueurs aux transitions de stages de la
   mission sélectionnée.
3. Hover → tooltip (stage + timestamp), click → `clock.seek(...)`.

Synergie forte avec `RND-3` : mêmes frontières de stages, mêmes couleurs — la
timeline et la trajectoire 3D doivent partager la table de couleurs, pas en
avoir deux.

**Spec.** [`docs/navigation/02-timeline-mission.md`](../navigation/02-timeline-mission.md).

#### NAV-3 — Scrub continu — ★3 ◆2 S

Subordonné à NAV-2. Attention au débit : chaque `seek` reconstruit toute la
fenêtre éphéméride (`EphemerisWorker.onSeek`) — n'émettre qu'au relâchement, ou
étrangler. La cohabitation piste-vitesse / piste-temps ne se pose plus : la
piste temporelle vit dans son propre widget (`NAV-2`), la capsule garde la
sienne.

#### NAV-4 — Breadcrumb de navigation 3D — ★3 ◆2 M

Spec complète et non commencée (`ui/breadcrumb/` et
`states/scene/BreadcrumbWidgetAppState.java` absents). Périmètre V1 réduit
le 2026-08-12 à la seule hiérarchie du corps courant, missions exclues
(elles ont leurs widgets dédiés) : `Solar system > Earth > Moon`. Devient
nettement plus utile une fois les missions lunaires en place, où cette
hiérarchie prend sa profondeur. Le dropdown des fils est reporté en V2.

**Couplage avec `UI-4`.** Le breadcrumb occupe une **bande pleine largeur en
haut d'écran**, sous laquelle tout le HUD haut-gauche s'ancre — le menu
applicatif d'`UI-4` n'est donc plus collé au bord haut. Les deux items restent
faisables séparément, mais la constante de marge partagée qu'`UI-4` doit
introduire (en remplacement de `MissionDisplayPanelWidget.MARGIN_PX = 5f` et du
`TRIGGER_HEIGHT = 28f` deviné) doit prévoir ce décalage d'origine, sinon les
mêmes lignes se reprennent deux fois. Chaîne d'ancrage détaillée en
[`navigation/01-breadcrumb.md`](../navigation/01-breadcrumb.md) §5.5.

**Spec.** [`docs/navigation/01-breadcrumb.md`](../navigation/01-breadcrumb.md).

#### NAV-5 — Hover « wow » — ★3 ◆2 M

Spec complète. À faire **après** `RND-4` : le boost d'épaisseur à ×2 sur hover
repose sur `setLineWidth`, qui ne marchera pas sur les drivers en profil core.
Avec le ribbon, la spec devient applicable telle qu'écrite.

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

#### PHY-1 — Atmosphère : la brique, désactivée par défaut — ★4 ◆3 L

**Périmètre.** Phases 0 à 3 de la spec atmosphère : prototype isolé, record
`AerodynamicProperties` (optionnel sur `Spacecraft` / `LaunchVehicle`, agrégé par
`VehicleStack` sur l'étage actif), `AtmosphereModel` enum + factories
`OrekitService` surchargées avec cache, câblage du contexte aéro dans
`GravityTurnManeuver.propagateForOptimization` et
`TransfertTwoManeuver.propagateForOptimization`.

**Contrainte non négociable.** `AtmosphereModel.NONE` ⇒ propagation **identique
au bit près** à aujourd'hui. C'est ce qui permet de livrer la brique sans
toucher aux baselines, et donc de la livrer tôt.

**Piège Orekit signalé par la spec** : à la séparation d'étage, le `DragForce`
doit voir la nouvelle surface. Un `IsotropicDrag` construit une fois pour toutes
ne le verra pas.

**Spec.** [`docs/atmosphere/01-impacts-fonctionnels-techniques.md`](../atmosphere/01-impacts-fonctionnels-techniques.md) §5 phases 0–3.

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

#### PHY-4 — Socle multi-corps — ★5 ◆4 L

**Pourquoi.** C'est le prérequis dur des deux missions lunaires. Aujourd'hui
tout est propagé dans un repère central unique et purement gravitationnel autour
d'un corps.

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

#### MIS-7 — `EarthOrbitMission` paramétrable — ★4 ◆2 M *(ajout)*

**Pourquoi.** Trois types de mission — polaire, SSO, MEO — sont à ★4/★5
d'intérêt et quasi gratuits en physique : il ne manque que `launchAzimuth` et
`targetInclination` comme paramètres au lieu de valeurs implicites Kourou.
`missions.md` recommandait déjà ce refactor avant d'écrire ces missions ; il est
en plus un **prérequis du rendez-vous** (matcher le plan de la cible, c'est
exactement choisir un azimut et une inclinaison).

**À faire.** Généraliser `LEOMission` en mission Terre paramétrée par
`(launchSite, launchAzimuth, targetAltitude, targetInclination, targetEccentricity)`,
contrainte d'inclinaison dans le coût (`beta1` est déjà partiellement disponible
dans `TransferTwoManeuverProblem`), formule analytique SSO
`cos(i) = -((Re+h)^{7/2} · n_prec) / (3/2 · J2 · Re² · √µ)`.

**Bonus mesurable** : une fois fait, les cartes wizard polaire / SSO / MEO sont
essentiellement de la saisie de paramètres.

#### MIS-2 — Fenêtres de lancement — ★4 ◆3 M

**Pourquoi.** Sans elle, ni le rendez-vous ni la Lune ne convergent : la cible
n'est jamais au bon endroit au moment du lancement. C'est aussi ce qui donne
enfin un sens au champ « date de lancement » du wizard.

**À faire.** `LaunchWindowSolver` qui balaie une plage temporelle et liste les
créneaux (alignement de plan, RAAN cible, précession J2 ~5°/jour pour l'ISS,
géométrie Terre-Lune ~mensuelle). UI : timeline des créneaux ouverts dans le
wizard, avec le Δv associé.

#### MIS-3 — Solveur de Lambert + repère LVLH — ★4 ◆3 M

Deux briques partagées, à écrire une fois :

- **Lambert** — `org.orekit.utils.IodLambert` couvre le mono-révolution ; le
  multi-révolution est à vérifier et, à défaut, à implémenter (Izzo 2014, court
  et robuste). Sert de **seed analytique** au CMA-ES exactement comme le seed
  Hohmann aujourd'hui : l'optimiseur corrige J2, poussée finie et masse variable
  au lieu de découvrir le transfert depuis rien. Sert aussi au ciblage lunaire.
- **LVLH** — service qui transforme un état chaser en `(δr, δv)` relatif via
  `LOFType.LVLH`. Utile au coût terminal, au rendu, et plus tard à HCW.

**Spec.** [`docs/brainstorm/leo-rendezvous-preparation.md`](../brainstorm/leo-rendezvous-preparation.md) §3.5, §3.6.

#### MIS-4 — Survol lunaire (TLI + flyby) — ★5 ◆4 L

**Pourquoi.** Premier objectif au-delà de l'orbite terrestre. Fort en spectacle
(la trajectoire traverse l'échelle Terre-Lune), fort en pédagogie, et c'est le
palier qui valide `PHY-4` sur un cas réel.

**À faire.** `TLIBurnStage` (depuis l'apogée d'une orbite de parking),
coast ~3 jours sous influence lunaire croissante, objectif de survol
(altitude de périlune visée, distance minimale d'approche). Seed patched-conic,
correction CMA-ES. Le timing du TLI est très contraint : sans `MIS-2`,
l'optimiseur cherche dans le vide.

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

**Tranché dans la spec, à ne pas rouvrir** : pas de Pontryagin (dans le cas
impulsif il ne rapporte rien sur une méthode directe), pas d'approche terminale
HCW au MVP, ISS seule comme cible.

**Stretch à fort rendement** : la vue LVLH dédiée. En repère inertiel, un
rendez-vous est une spirale illisible ; en LVLH, c'est une figure compacte
autour de la cible. C'est le bénéfice visuel n°1 de la feature.

**Spec.** [`docs/brainstorm/leo-rendezvous-preparation.md`](../brainstorm/leo-rendezvous-preparation.md).

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

#### UI-2 — Feedback de progression pendant l'optimisation — ★3 ◆2 M

**Contrainte mesurée à respecter** : le coût d'une évaluation varie d'un facteur
~5 et n'est pas prévisible → une barre linéaire en nombre d'évaluations sera
par moments franchement fausse. Indicateur **indéterminé** (spinner) + compteur
d'évaluations en texte. Devient plus important à mesure que les optimisations
s'allongent (lunaire, rendez-vous) — d'où son placement en phase 3, avant
elles.

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

#### UI-4 — Menu applicatif haut-gauche — ★3 ◆2 M *(ajout)*

**Pourquoi.** Le haut-gauche n'a qu'un point d'entrée : `MissionPanelTrigger`,
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

**Ordre.** À faire **avant** `NAV-2` : la spec de la piste temporelle demande
que son toggle soit construit « sur le motif de `MissionPanelTrigger` : même
style, même `setEnabled(boolean)` »
([`navigation/02-timeline-mission.md`](../navigation/02-timeline-mission.md) §11).
Dans l'ordre inverse, `NAV-2` recopie le skin *et* la sémantique inversée, et
il y a deux boutons à reprendre au lieu d'un.

**Ce qu'on ne fait pas.** Pas de barre de menus complète de type application de
bureau (Fichier / Édition / Affichage…) : un seul point d'entrée déroulant.
Savoir si le toggle de la piste temporelle devient une entrée du menu ou reste
un bouton local à son widget se tranche en faisant `NAV-2`, pas ici.

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
- **Missions** — Molniya / HEO, déorbitage et rentrée, déploiement de
  constellation, points de Lagrange, interplanétaire, gravity assist.
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
3. **Auto-optimisation après création** — toujours ouverte depuis la révision
   précédente. Aujourd'hui `createMission()` ajoute l'entrée en `DRAFT` sans
   déclencher de calcul. `UI-2` (progression) est un préalable raisonnable :
   déclencher automatiquement un calcul long sans indicateur serait pire que le
   clic actuel.
4. **Troisième viewport** — le décide-t-on avec `PHY-4` (les missions lunaires
   exposent d'un coup trois échelles) ou attend-on de constater les artefacts ?
   Recommandation : attendre. La mesure de comparaison existe désormais —
   `NearFrustumDepthTest` chiffre le budget de profondeur du viewport near, et
   c'est le test à rejouer avec la Lune dans le cadre.
5. **Cible du rendez-vous** — ISS livrée en dur (option A de la spec) suffit au
   MVP. L'import de TLE arbitraire (option B) est une feature UI à part entière,
   à ne pas glisser dans `MIS-6`.
