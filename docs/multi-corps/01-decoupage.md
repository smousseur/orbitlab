# PHY-4 — Socle multi-corps : découpage haut niveau

Item roadmap : `PHY-4` (★5 ◆4 L), l'un des deux derniers verrous du haut du graphe
de dépendances avec `MIS-2`. Ce document ne conçoit rien : il **découpe**. Chaque
lot y est défini par la propriété qu'il rend vraie et par le test qui la ferme.
Le détail de conception (noms de classes, signatures, formules) viendra dans les
documents `02-…` et suivants, lot par lot.

> **Statut : brouillon de découpage.** Les tolérances numériques citées sont des
> ordres de grandeur à calibrer, pas des seuils arrêtés. Elles sont écrites parce
> qu'un test « on vérifiera que c'est cohérent » n'est pas un test.

---

## 1. Périmètre

**Dans PHY-4** — le patched-conic avec troisième corps :

- attraction du troisième corps (Lune, Soleil) dans les propagations concernées ;
- transition de sphère d'influence Terre → Lune : bascule du corps central,
  concaténation des arcs ;
- éphéméride de mission multi-arcs (un repère par arc) et son rendu ;
- cohabitation des deux échelles à l'écran (deux viewports, repère flottant).

**Hors PHY-4**, et à ne pas y laisser glisser :

- propagation N-corps complète (Terre + Lune + Soleil simultanément intégrés
  comme corps centraux) ;
- optimisation multi-arcs — un CMA-ES dont les variables traversent la bascule
  de SOI ;
- `TLIBurnStage`, `LunarInsertionStage`, `LunarOrbitObjective` : c'est `MIS-4` /
  `MIS-5` ;
- fenêtres de lancement (`MIS-2`). **PHY-4 n'en dépend pas** : tous les lots
  ci-dessous se testent avec une géométrie Terre-Lune prise telle qu'elle est à
  une date figée. C'est `MIS-4` qui aura besoin des deux.

---

## 2. État des lieux — ce que le code suppose aujourd'hui

Trois suppositions, toutes vérifiées dans le code, et ce sont elles qui
définissent les coutures du découpage.

**A. Un seul corps central, en dur, partout.** `createOptimizationPropagator`
fixe `mu = WGS84_EARTH_MU` et ajoute un champ 8×8 terrestre exprimé en ITRF
(`OrekitService.java:199-212`). Une trentaine de sites référencent la Terre
directement : `WGS84_EARTH_MU` dans `AchievedOrbit`, `PropellantBudget`,
`MissionHorizon`, `StageEndStateDiagnostic`, `Physics`, les problèmes
d'optimisation ; `gcrf()` dans `EarthMission`, `LaunchPlane`, `CoastingStage` et
les `NodeDetector` des étages analytiques ; `getEarthEllipsoid()` dans
`Mission.computeAltitudeMeters` et `TransferProblem`.

**B. Le propagateur d'un vol est construit à seize endroits, pas un.**
`StageChainRunner:192` est le seul constructeur du propagateur d'un *étage*, et
c'est aussi lui qui arme la garde de rentrée terrestre — mais les manœuvres
(`GravityTurnManeuver`, `TransferManeuver`, `TransfertTwoManeuver`,
`CircularizationBurnResolver`) et les six étages analytiques construisent chacun
les leurs pour leurs propagations de plan et leurs itérations Newton/sécante.
Paramétrer le corps central ne se fait donc pas en un point.

**C. La trajectoire est un tableau de `Vector3D` sans repère.**
`MissionEphemerisPoint` porte `position`, `velocity` et une `altitudeMeters`
calculée sur l'ellipsoïde terrestre — aucun champ de repère. En aval,
`MissionRenderer.renderContextFor` rend la mission dans
`RenderContext.planet(objective.body())`, **un corps pour toute la mission**, et
`FloatingOriginAppState:161` doit lire *exactement le même* contexte pour que
l'offset de la frame near et l'ancre du vaisseau s'annulent au bit près.

**Ce qui joue déjà en notre faveur.** La Lune est une `SolarSystemBody` de plein
droit : rayon, couleur, période, pas d'échantillonnage, budget d'orbite et
génération de dataset sont tous renseignés. Elle est rendue, elle a une
éphéméride. `RenderContext.Planet` est déjà paramétré par un corps. Et
`EarthOrbitNonRegressionTest` fournit le patron exact du test de non-régression
dont les premiers lots ont besoin : variables figées, pas de CMA-ES, rapide.

---

## 3. Principe du découpage

Trois règles, dictées par ce qui a coûté cher sur les chantiers précédents :

1. **Un changement de comportement à la fois.** Un lot qui refactore ne change
   pas la physique ; un lot qui change la physique ne refactore pas. Sinon une
   dérive constatée n'est attribuable à rien.
2. **Chaque lot se ferme sur un test exécutable**, pas sur une revue. Pour les
   lots de refactor, le test est une **égalité** avec la référence du lot
   précédent ; pour les lots de physique, c'est une **mesure** confrontée à une
   valeur connue par ailleurs.
3. **Le nouveau comportement est opt-in jusqu'au dernier lot.** Tant qu'un
   étage ne demande pas explicitement un troisième corps ou un changement de
   corps central, il vole ce qu'il volait. C'est ce qui protège les calibrations
   Falcon Heavy et Ariane 62.

---

## 4. Les lots

| Lot | Objet | Change le comportement ? | Test qui le ferme |
|---|---|---|---|
| **L0** | Baseline mesurée | non | la suite existante, chiffres consignés (verts depuis MIS-7) |
| **L1** | Corps central explicite | non (refactor pur) | égalité avec la baseline L0 |
| **L2** | Troisième corps en perturbation | oui, opt-in | accélération analytique + dérive GEO |
| **L3** | Éphéméride et rendu multi-arcs (un seul arc) | non | égalité géométrique + tests d'unité arcs |
| **L4** | Bascule de SOI, hors mission | oui | continuité à la traversée, aller-retour |
| **L5** | Rendu bi-échelle Terre-Lune | oui (affichage) | budget de profondeur + capture |
| **L6** | Arc lunaire de bout en bout | oui | altitude de périlune d'un survol injecté |

### L0 — Baseline mesurée

**Pourquoi un lot.** Sans référence chiffrée écrite *avant*, aucun des lots
suivants ne peut prouver qu'il n'a rien bougé.

**Le point de départ est bon, et il est frais.** MIS-7 vient de ré-enregistrer les
références `AscentBaselineN2Test` (2026-08-16, graine 42, tolérances inchangées)
et laisse la suite verte. PHY-4 démarre donc sur une baseline datée du jour, ce
qui est la meilleure situation possible — mais aussi une raison de la **recopier
ici** plutôt que d'y renvoyer : les chiffres de MIS-7 sont dans un bilan de
livraison, pas dans un point de référence destiné à être comparé pendant des
semaines.

**Un point à ne pas hériter sans le savoir.** Le même bilan mesure un étalement de
**19,2 km** de l'ensemble acceptable de CMA-ES sur le périgée LEO : deux candidats
que la fonction de coût juge équivalents sont distants de 19 km. Ce n'est pas un
sujet PHY-4, mais c'est le **plancher de bruit** de tout ce qui suit — une dérive
de quelques kilomètres constatée en L1 ou L2 ne prouvera rien. Les tests de
non-régression des lots suivants doivent donc porter sur des **états de fin
d'étage à variables figées**, pas sur des sorties d'optimiseur.

**Contenu.** Rejouer la suite (l'utilisateur lance lui-même les tests
d'optimisation, qui sont lents) et consigner dans ce dossier : orbites atteintes
LEO/GEO/MEO/polaire, masses restantes, durées de calcul. Rien d'autre. Aucun code
de production.

**Fermeture.** Un tableau de référence versionné : **`02-baseline-L0.md`**, mesuré
le 2026-08-16 au commit `8f54206`, avec les logs bruts sous `baseline/`. Il couvre
cinq profils (LEO-400, GEO, MEO, Ariane 62, polaire), distingue les deux qui ont
un état MECO enregistré de ceux qui n'ont qu'un agrégat, et consigne six
anomalies héritées qu'il ne faut pas attribuer à PHY-4 — dont deux qui changent
la façon d'écrire les tests des lots suivants : sur MEO les passes d'optimisation
et d'éphéméride divergent de façon **reproductible**, et les comptes
d'évaluations CMA-ES ne sont, eux, **pas** reproductibles.

### L1 — Le corps central devient explicite

**Propriété rendue vraie.** Le corps central d'une propagation est une donnée
portée par l'étage, plus une constante lue au fond d'une fabrique.

**Contenu.** Une notion de contexte gravitationnel (corps, µ, repère inertiel
centré, champ de gravité, ellipsoïde ou sphère de référence) ; les fabriques de
propagateurs en prennent une ; `MissionStage` la déclare, par défaut la Terre ;
les seize sites de construction du §2-B et les sites Terre-en-dur du §2-A la
consultent au lieu de la constante. `computeAltitudeMeters` et la garde de
rentrée suivent le corps de l'étage.

**Ce que ça ne fait pas.** Aucun étage ne déclare autre chose que la Terre. La
physique est identique.

**Fermeture — c'est le lot le plus important à tester, et le plus facile.** Un
test à variables figées sur le modèle d'`EarthOrbitNonRegressionTest` doit rendre
la **même trajectoire que L0**, à la tolérance du bruit d'intégration près
(viser l'égalité stricte des états de fin d'étage ; si elle n'est pas atteignable,
la raison doit être écrite, pas absorbée par une tolérance). Plus les tests
d'optimisation, qui doivent retomber sur les chiffres de L0.

### L2 — Le troisième corps, en perturbation seulement

**Propriété rendue vraie.** Un étage peut demander l'attraction de la Lune et du
Soleil ; le corps central reste la Terre.

**Contenu.** `ThirdBodyAttraction` ajoutée aux forces d'un étage qui le déclare,
**désactivée par défaut**. Décider explicitement si l'ascension et le LEO y ont
droit — recommandation : non, pour ne pas déplacer les calibrations et parce que
l'effet y est sous le bruit du modèle 8×8. Mesurer le surcoût CPU sur une
optimisation.

**Fermeture, deux niveaux.**
- *Unité, exacte et instantanée* : l'accélération perturbatrice à une géométrie
  imposée, confrontée à la formule de marée `2·µ_L·r/d³`. À r = 42 164 km,
  aligné avec une Lune à 384 400 km, cela vaut ≈ 7,3 × 10⁻⁶ m/s².
- *Intégration, physique connue* : une GEO circulaire équatoriale propagée avec
  Lune + Soleil voit son inclinaison croître d'environ **0,85 °/an** (valeur de
  domaine, à retrouver à ±20 % sur une propagation de quelques dizaines de
  jours). Sans troisième corps, elle ne bouge pas — et cette seconde moitié est
  aussi le test de non-régression du lot.

### L3 — Éphéméride et rendu multi-arcs, avec un seul arc

**Propriété rendue vraie.** Une trajectoire est une **suite d'arcs**, chacun avec
son corps central et son repère ; le renderer sait en dessiner une. Il n'y en a
toujours qu'un seul en pratique.

**Contenu.** Le repère devient explicite dans `MissionEphemerisPoint` /
`MissionEphemeris` (au point ou au segment — à trancher au raffinement) ;
`TrajectoryPolyline` (budget 8192 sommets) apprend la frontière d'arc ;
`MissionRenderer.renderContextFor` et `FloatingOriginAppState` cessent de lire
`objective.body()` pour lire le corps de l'arc courant, **sans jamais cesser de
lire la même chose l'un que l'autre** — l'annulation au bit près de l'offset near
et de l'ancre est un invariant à préserver explicitement, pas une conséquence.
L'altitude portée par le point devient relative au corps de l'arc.

**Ce que ça ne fait pas.** Rien ne produit encore un second arc.

**Fermeture.** Tests d'unité sur la concaténation et la conversion d'arcs
(y compris un arc unique, cas dégénéré) ; `NearFrameOriginTest` et
`MissionEphemerisDisplayPointTest` verts ; et une égalité géométrique : le
polyline d'une mission LEO est identique à celui de L0.

### L4 — La bascule de sphère d'influence, sans mission

**Propriété rendue vraie.** Une propagation qui traverse la SOI lunaire se coupe
en deux arcs continus.

**Contenu.** Rayon de SOI (Laplace : a·(m_L/m_T)^{2/5} ≈ 66 200 km pour la Lune),
un détecteur de traversée, et l'orchestration côté chaîne d'étages : fermer
l'arc, convertir l'état dans le nouveau repère centré, ouvrir l'arc suivant.
Prévoir l'hystérésis — une trajectoire rasante peut retraverser la frontière
plusieurs fois.

**Pourquoi ce lot se teste sans aucune mission lunaire, et c'est tout son
intérêt.** On injecte un état sur une trajectoire Terre → Lune connue et on
vérifie trois choses :
- la traversée est détectée au bon rayon et à la bonne date ;
- **la continuité** : position et vitesse de part et d'autre de la bascule,
  ramenées dans un repère commun, coïncident (viser le millimètre et le µm/s —
  c'est une transformation de repère, pas une approximation physique) ;
- **l'aller-retour** : Terre → Lune → Terre revient sur l'état inertiel de
  départ.

C'est le cœur technique de PHY-4 et il est entièrement testable en isolation.

### L5 — Les deux échelles à l'écran

**Propriété rendue vraie.** Une trajectoire qui va de 200 km à 400 000 km se lit.

**Contenu.** Cohabitation du tracé multi-arcs avec les deux viewports et le
repère flottant ; comportement du suivi caméra à la bascule d'arc. La question du
**troisième viewport « mid »** (question ouverte n° 4 de la roadmap) se tranche
**ici et sur mesure**, pas par anticipation : la roadmap recommande d'attendre,
et `NearFrustumDepthTest` est l'instrument désigné pour chiffrer le budget de
profondeur avec la Lune dans le cadre.

**Fermeture.** `NearFrustumDepthTest` rejoué avec la géométrie lunaire, et une
capture d'écran comparée — c'est un lot dont une partie du verdict est visuelle,
autant l'assumer.

### L6 — Un arc lunaire de bout en bout

**Propriété rendue vraie.** PHY-4 tient sur un cas réel.

**Contenu.** Pas une mission au sens du wizard, pas d'optimiseur : un TLI
**injecté analytiquement** (seed patched-conic) depuis une orbite de parking,
propagé jusqu'au périlune. C'est la recette d'acceptation de PHY-4 et la graine
de `MIS-4`.

**Fermeture.** Altitude de périlune atteinte dans une fourchette annoncée, arcs
continus, trajectoire complète (`MissionEphemeris.isComplete()`), et le tracé
visible à l'écran des deux côtés de la bascule.

---

## 5. Ce qui reste à trancher au raffinement

Aucune de ces questions ne bloque le début (L0 et L1 sont insensibles aux six).

1. **Repère d'un arc.** Recommandation : inertiel centré sur le corps central de
   l'arc, axes ICRF — GCRF pour l'arc terrestre, l'équivalent lunaire ensuite.
   À confronter à ce que `RenderFrame` sait exprimer (il n'a aujourd'hui que
   `HELIOCENTRIC_ICRF` et `PLANETOCENTRIC_RELATIVE_ICRF`).
2. **Critère de bascule** : sphère de Laplace géométrique, ou rapport des forces ?
   La première est la convention patched-conic et se teste ; la seconde est plus
   juste et plus chère.
3. **Le troisième corps pendant l'ascension et en LEO** : autorisé ou interdit ?
   Recommandation ci-dessus : interdit, pour ne pas déplacer les calibrations.
   À écrire comme une décision, pas comme un oubli.
4. **Granularité du repère dans l'éphéméride** : par point, ou par segment
   d'arc ? Le second est plus compact (8192 sommets) mais impose que la frontière
   soit un sommet.
5. **Où vit l'orchestration de la bascule** : dans `StageChainRunner`, ou dans un
   niveau au-dessus des étages ? Un étage qui change de corps central au milieu
   est un objet nouveau dans cette architecture.
6. **Troisième viewport** : tranché en L5 sur mesure (cf. supra).

---

## 6. Ordonnancement

`L0 → L1 → L2` et `L0 → L1 → L3` sont indépendants après L1 et peuvent
s'intercaler. `L4` demande L1 (corps central explicite) et L3 (arcs) ; `L5`
demande L3 ; `L6` demande tout.

Le lot le plus risqué est **L1** : il touche une trentaine de sites sans avoir le
droit de rien changer. Le lot le plus incertain en effort est **L5**, parce que
son verdict est en partie visuel. **L4** est le cœur de l'item, mais c'est aussi
le mieux isolé et le mieux testable des six.
