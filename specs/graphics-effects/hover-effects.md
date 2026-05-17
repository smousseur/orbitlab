# Spec — Hover « wow » sur les planètes et leurs orbites

## 1. Contexte

L'effet de hover actuel est extrêmement discret : seul le **billboard 2D**
de la planète réagit, et uniquement par une saturation x3 de sa couleur.

- `engine/scene/body/lod/BillboardIconView.java:117` — sur `mouseEntered`,
  l'icône `dotIcon` voit sa couleur passée par `saturate(c, 3f)` (mélange
  vers gris × 3). Sur `mouseExited`, retour à la couleur de base.
- La taille du dot reste fixe (`ICON_SIZE = 16f`,
  `BillboardIconView.java:27`).
- **L'orbite ne réagit pas** : `OrbitLineFactory` produit un `Geometry`
  immuable avec couleur + largeur figées (`OrbitLineFactory.java:81-87`,
  `:143-148`). Aucun listener souris, aucun lien avec l'icône.
- Pas d'événement « hover » dans `engine/events/EventBus.java` (seuls
  `OrbitPathReady`, `MissionActionRequest`, `UiNavigationEvent`
  existent).

Résultat utilisateur : un survol passe quasiment inaperçu, et rien ne
fait le lien entre une icône de planète et sa trajectoire complète dans
le système solaire.

## 2. Objectif

Transformer le hover en un **moment de lecture visuelle** :

1. L'œil est immédiatement attiré sur l'icône survolée (croissance + halo
   lumineux).
2. **Simultanément**, l'orbite de ce corps émerge du fond : largeur
   boostée, brightness augmentée.
3. **Les autres orbites s'effacent** (alpha réduit) pour renforcer la
   relation « icône ↔ trajectoire ».

C'est le rapport impact / effort le plus élevé sans introduire de
shader custom : tout passe par le matériau `Unshaded` existant, le
GUI Lemur, et un simple événement sur le bus.

### Critères de succès

- Sur une vue système solaire, hover sur l'icône Mars doit :
  - Faire grossir l'icône de **×1 à ×1.4** en **150 ms** (ease-out).
  - Faire apparaître un halo additif derrière l'icône (alpha animé
    0 → 0.8 dans la même fenêtre).
  - Doubler la largeur visible de l'orbite martienne et augmenter sa
    brightness d'environ **+50 %** (vers blanc).
  - Réduire l'alpha de toutes les autres orbites à **0.30**.
- Sur `mouseExited`, retour à l'état nominal en **200 ms** (ease-in).
- L'effet doit aussi fonctionner si l'utilisateur survole *l'orbite*
  elle-même (cf. §6.2 — option, second jet).

## 3. Design

### 3.1 Icône — scale-up + soft glow

**Composants visuels.** L'icône actuelle est un `IconComponent`
(`textures/white-dot.png`, 16×16). On la complète par :

1. Un **deuxième `IconComponent` de halo**, layer en dessous du dot,
   sprite radial gaussien (`textures/glow.png`, taille 64×64, alpha
   = exp(-r²/σ²)). Tinté avec la couleur de la planète. Blending
   additif (`BlendMode.AlphaAdditive`) pour qu'il « rayonne » sur le
   fond noir.
2. Une **animation de scale** simultanée sur dot et halo via
   `IconComponent.setIconScale(...)` ou `setIconSize(...)`. Easing
   `out-cubic` à l'entrée, `in-cubic` au retour.

**État d'animation.** Le `BillboardIconView` gère son propre petit
state-machine `HoverState` :

```
enum HoverPhase { IDLE, ENTERING, HOVERED, EXITING }
```

Le temps écoulé dans la phase est avancé dans `updateScreenPosition(...)`
(déjà appelée à chaque frame par `LodView`). Pas besoin d'un nouvel
`AppState` dédié — la même méthode reçoit `tpf` indirectement via
`System.nanoTime()` ou via une signature étendue (`updateScreenPosition(Camera, Node, float tpf)`).

**Paramètres recommandés.**

| Paramètre | Valeur |
|---|---|
| `ICON_SIZE` nominal | 16 px (inchangé) |
| Scale hover | 1.4 |
| Halo size | 64 px → 64 × scale courant |
| Halo alpha cible | 0.8 |
| Durée entrée | 150 ms |
| Durée sortie | 200 ms |
| Easing entrée | out-cubic (`1 - (1-t)^3`) |
| Easing sortie | in-cubic (`t^3`) |
| Saturation dot | conservée (`saturate(c, 1.6f)` plus doux que 3f, le halo prend le relais) |

### 3.2 Orbite — boost ciblée + dim des autres

**Mécanisme.** Les `Geometry` d'orbites portent déjà tout ce qu'il
faut :

- `mat.setColor("Color", color)` (`OrbitLineFactory.java:144`) — on
  peut overrider la couleur courante sans rebuild du mesh.
- `mat.getAdditionalRenderState().setLineWidth(w)`
  (`OrbitLineFactory.java:145`) — on peut overrider la largeur (avec la
  réserve drivers OpenGL — cf. §6.1).

Pour chaque orbite, on conserve deux états logiques :

```
record OrbitVisualState(ColorRGBA baseColor, float baseWidth) {}
```

Mémorisés à la construction (dans `OrbitInitAppState` /
`OrbitRuntimeAppState`). Sur hover d'un corps :

- **Orbite cible** : `color = lerp(baseColor, WHITE, 0.35)`,
  `width = baseWidth * 2.0`.
- **Autres orbites** : matériau en alpha-blending, alpha multiplié par
  0.30 (passe par `mat.setColor("Color", new ColorRGBA(r, g, b, 0.30f))`
  avec `getAdditionalRenderState().setBlendMode(AlphaAdditive)` ou
  `Alpha`). Il faut **passer les matériaux d'orbite en `alpha()` au
  setup** (cf. `AssetFactory.alphaMaterial()` déjà disponible).

**Réversibilité.** Sur `mouseExited`, on restaure les `baseColor` /
`baseWidth` sur **toutes** les orbites.

**Animation.** Idéalement linéaire sur la même fenêtre (150 / 200 ms)
pour rester synchrone avec l'icône. Si simple, faisable en
`AppState.update()` qui interpole une variable `hoverIntensity ∈ [0, 1]`
et la propage à tous les matériaux.

### 3.3 Coordination — événement de bus

Pour découpler l'icône (GUI 2D, dans `BillboardIconView`) de la
mise à jour des orbites (3D, dans `OrbitRuntimeAppState` / scene graph),
on ajoute un événement dans `EventBus` :

```java
public sealed interface BodyHoverEvent permits BodyHoverEnter, BodyHoverExit {
  SolarSystemBody body();
}
public record BodyHoverEnter(SolarSystemBody body) implements BodyHoverEvent {}
public record BodyHoverExit(SolarSystemBody body) implements BodyHoverEvent {}
```

**Publisher** — `BillboardIconView` reçoit en plus de `onClick` un
`Consumer<BodyHoverEvent>` (ou une référence à `EventBus` via
`BodyRenderConfig` / un nouveau paramètre). Dans `mouseEntered` /
`mouseExited`, il publie l'évènement.

**Consumer** — nouvel `AppState` `OrbitHoverAppState` (dans
`states/orbits/`, conformément à la convention) qui :

1. Drain les `BodyHoverEvent` à chaque `update(tpf)`.
2. Maintient `Optional<SolarSystemBody> hovered`.
3. Interpole `hoverIntensity` (0 ↔ 1) avec le timing §3.1.
4. Applique les couleurs/largeurs aux matériaux d'orbite via
   `SceneGraph.orbits().orbitNode(body)`.

> **Règle** : aucun appel à `getState(...)` (cf. `CLAUDE.md`).
> `BillboardIconView` n'a pas accès au scene graph 3D, et
> `OrbitHoverAppState` n'a pas accès aux GUI listeners — le découplage
> par bus est non négociable.

## 4. Code touché

| Fichier | Modification |
|---|---|
| `engine/events/EventBus.java` | Ajouter `sealed interface BodyHoverEvent` + queue + `publishHover(...)` / `pollHover()`. |
| `engine/scene/body/lod/BillboardIconView.java` | Recevoir un `Consumer<BodyHoverEvent>` (en plus de `onClick`). Ajouter halo `IconComponent`, animation de scale + alpha halo dans `updateScreenPosition`. Adoucir `saturate(c, 3f)` → `1.6f`. |
| `engine/scene/body/lod/LodView.java` | Propager le `hoverPublisher` à `BillboardIconView`. |
| `engine/scene/body/BodyRenderConfig.java` | Aucun changement (la couleur de base suffit). |
| `engine/scene/OrbitLineFactory.java` | Ajouter `Material#getAdditionalRenderState().setBlendMode(Alpha)` au build pour permettre l'alpha-fade des orbites « non-hovered ». La géométrie reste inchangée. |
| `states/orbits/OrbitHoverAppState.java` | **Nouveau**. Consomme les events, anime `hoverIntensity`, applique couleurs/largeurs sur les matériaux. |
| `states/orbits/OrbitInitAppState.java` / `OrbitRuntimeAppState.java` | Exposer un moyen de récupérer `(Geometry, baseColor, baseWidth)` par body (ou maintenir le mapping dans `OrbitHoverAppState` en scannant le scene graph). |
| `OrbitLabApplication.java` | Enregistrer `OrbitHoverAppState`. Câbler le `hoverPublisher` à `EventBus#publishHover`. |
| `assets/textures/glow.png` | **Nouveau** asset (gaussien radial 64 px, transparent). Documenté hors-dépôt (cf. §7.2 de l'effects-roadmap). |

## 5. Algorithme d'animation (détaillé)

```
state := IDLE
intensity := 0.0   // 0 = nominal, 1 = pleinement hovered

on event BodyHoverEnter(b):
  hovered := b
  state := ENTERING
  enterStart := now

on event BodyHoverExit(b) where hovered == b:
  state := EXITING
  exitStart := now

update(tpf):
  switch state:
    ENTERING:
      t := (now - enterStart) / 150ms
      if t >= 1: state := HOVERED; intensity := 1
      else: intensity := outCubic(t)
    HOVERED:
      intensity := 1
    EXITING:
      t := (now - exitStart) / 200ms
      if t >= 1: state := IDLE; hovered := null; intensity := 0
      else: intensity := 1 - inCubic(t)
    IDLE:
      intensity := 0

  applyIcon(hovered, intensity)
  applyOrbits(hovered, intensity)

applyIcon(body, k):
  scale := lerp(1.0, 1.4, k)
  haloAlpha := lerp(0.0, 0.8, k)
  dotColor := lerp(base, saturate(base, 1.6), k)
  (forwarded to BillboardIconView of `body`)

applyOrbits(body, k):
  for each orbit o:
    if o.body == body:
      o.color := lerp(o.base, white, 0.35 * k)
      o.width := lerp(o.baseWidth, 2 * o.baseWidth, k)
      o.alpha := 1.0
    else:
      o.color := o.base
      o.width := o.baseWidth
      o.alpha := lerp(1.0, 0.30, k)
```

> **Note timing croisé** — Si l'utilisateur sort puis re-rentre
> rapidement, ne pas redémarrer `enterStart` depuis 0 : initialiser
> `intensity` à sa valeur courante et calculer un `enterStart` virtuel
> tel que la courbe reprenne là où elle s'est arrêtée. Évite les
> à-coups.

## 6. Limites et edge cases

### 6.1 `glLineWidth` sur drivers modernes

Le boost de largeur (×2) suppose que `setLineWidth(w)` est respecté.
**C'est non garanti** (Mesa core, NVIDIA core profile plafonnent à
1 px). Trois fallbacks acceptables :

1. **Accepter le plafond** sur les drivers limités — le boost de
   couleur (vers blanc) suffit à faire ressortir la ligne.
2. **Doubler la géométrie** : sur hover, attacher un second
   `Geometry` plus large en additif. Plus robuste, sans nouveau
   shader.
3. **Switcher vers un ribbon** (cf. effects-roadmap §9.4.1) — gros
   chantier, à reporter.

Recommandation : démarrer avec (1) + (2) si nécessaire après test
visuel.

### 6.2 Hover direct sur la ligne d'orbite (optionnel, v2)

Les `Geometry` d'orbite ne sont pas pickables à la souris (lignes
sans épaisseur en world-space). Pour un hover symétrique
(« je survole l'orbite, l'icône s'allume »), il faudrait :

- Soit picking proximité en screen-space (parcourir les segments
  projetés, calculer la distance au curseur, seuil = 8 px).
- Soit un raycast élargi (cylindre fin autour de la ligne).

Hors scope v1 — à ouvrir dans une note séparée si la demande remonte.

### 6.3 Mode focus 3D (Model3dView)

Quand la caméra est proche du corps, c'est `Model3dView` qui est
affichée (`LodView.java`). Le hover de l'icône 2D n'est alors pas
visible. Décision v1 : **désactiver l'effet** quand le 3D model est
visible (l'utilisateur est déjà focus). Si on veut un retour visuel
en 3D, on ajoutera plus tard un tint emissive (cf. effects-roadmap
§3.2 / §4.4 — `Lighting.j3md`).

### 6.4 Icône hors écran

`updateScreenPosition` cull déjà l'icône hors-frustum
(`BillboardIconView.java:93-95`). Si l'utilisateur sort de l'écran
avec la souris dessus, `mouseExited` est appelé par Lemur — l'animation
de sortie joue normalement.

### 6.5 Plusieurs hovers simultanés

Lemur ne lève qu'un hover à la fois sur les `Container`. Si deux
icônes se superposent visuellement, Lemur choisit la plus haute en
z. Pas de gestion spéciale nécessaire — un seul `hovered` à la fois.

### 6.6 Pendant la transition (ENTERING + nouveau ENTER sur un autre corps)

Si on hover A puis très vite B sans passer par `mouseExited` A,
publier explicitement un `BodyHoverExit(A)` avant `BodyHoverEnter(B)`
côté Lemur — déjà le comportement par défaut (`mouseExited` toujours
émis quand on quitte le container). Côté `OrbitHoverAppState`,
chaîner exit puis enter même si la première animation n'est pas finie
(reprendre depuis l'intensité courante).

## 7. Validation

### 7.1 Tests automatisés

- **Unit test** `OrbitHoverAppStateTest` :
  - Publier `BodyHoverEnter(EARTH)`, avancer 75 ms (mi-animation),
    vérifier `intensity ≈ outCubic(0.5) ≈ 0.875` (à ε près).
  - Publier `BodyHoverExit(EARTH)` après 200 ms `HOVERED`, avancer
    200 ms, vérifier `intensity == 0`.
  - Couvrir le cas hover-A-puis-hover-B (chaînage).
- **Unit test** `BillboardIconViewHoverTest` :
  - Mock du publisher, vérifier que `mouseEntered` publie
    `BodyHoverEnter(body)`.

### 7.2 Validation visuelle

- Vue système solaire, survoler successivement chaque planète et
  vérifier :
  - Grossissement + halo de la bonne couleur.
  - Orbite cible bien plus visible que les autres.
  - Retour propre sur `mouseExited`.
- Vue zoomée (Earth approche) : l'effet ne se déclenche pas
  (Model3dView affiché). Aucun glitch.

## 8. Plan d'attaque

1. Ajouter `BodyHoverEvent` + publish/poll dans `EventBus`.
2. Étendre `BillboardIconView` (halo `IconComponent`, animation
   scale/alpha, publish des events).
3. Asset `assets/textures/glow.png`.
4. Créer `OrbitHoverAppState` + enregistrement dans
   `OrbitLabApplication`.
5. Adapter `OrbitLineFactory` pour ouvrir l'alpha-blending (et
   indexer les matériaux par body si besoin).
6. Tests unitaires sur l'AppState.
7. Validation visuelle, ajustement des paramètres (durées, courbes,
   facteurs).
8. (Optionnel) v2 — picking proximité sur la ligne (§6.2).

## 9. Liens

- Backlog général : `specs/graphics-effects/effects-roadmap.md`
  (sections §3.2 « Lambert » et §9.5.3 « mise en avant mission
  sélectionnée » partagent la même idée de highlight / dim).
- Convention AppState et bus : `CLAUDE.md` § « ApplicationContext »
  et « Pas de getState() ».
