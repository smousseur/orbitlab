# Spec — Breadcrumb de navigation

> **Révisé le 2026-08-12.** Le périmètre V1 a été réduit : le breadcrumb
> n'affiche plus que la **hiérarchie du corps courant**. Le dropdown des fils
> (`PopupList`) passe en V2 (§7), et les **missions n'apparaissent plus du tout
> dans le breadcrumb** — elles ont leurs widgets dédiés (panneau de liste,
> panneau de détail, télémétrie). Le nœud racine s'appelle **`Solar system`**.
> Le widget occupe une **bande dédiée en haut d'écran**, sous laquelle le menu
> applicatif d'`UI-4` et le reste du HUD s'ancrent (§5.5).

## 1. Contexte

Aujourd'hui, dans OrbitLab, l'utilisateur ne dispose que de deux moyens de
navigation :

- **Click 3D** sur l'icône billboard d'un corps
  (`engine/scene/body/lod/BillboardIconView.java:105-114` →
  `states/scene/PlanetPoseAppState.java:93-99` `onSelectPlanet`) ou sur un
  spacecraft (`states/mission/MissionRenderer.java:113-118`
  `onSpacecraftSelected`) pour le focus.
- **Touche `R`** (`states/camera/ViewModeAppState.java:35-70`) pour reset à la
  vue solaire (`focusView.reset()`).

Limites :

- Une fois sur la Lune, il faut de-zoomer/chercher la Terre dans la vue 3D pour
  cliquer dessus.
- Aucune indication textuelle d'où on est (pas de fil d'Ariane, pas de
  contexte hiérarchique).

Objectif V1 : ajouter un **widget HUD persistant** ("breadcrumb") qui

1. montre où l'on est dans la hiérarchie céleste,
2. permet en 1 click de remonter à un parent (jusqu'à la vue solaire).

La descente vers un fils reste, en V1, l'affaire du click 3D. Elle est traitée
en V2 (§7).

---

## 2. Solutions étudiées et choix

| Solution | 1-click parent | 1-click fils | Surface écran | Effort | Découvrable |
|---|---|---|---|---|---|
| **Breadcrumb hiérarchique** ✅ | ✓ | V2 (dropdown) | Faible | Faible | ✓ |
| Boutons Back/Forward (historique) | ✗ | ✗ | Très faible | Faible | ✓ |
| Bouton Home seul | ✓ vue solaire | ✗ | Très faible | Très faible | ~ |
| Command palette ("Go to…") | ✗ (2 clicks) | ✓ | Faible (popup) | Moyen | ~ |
| Toolbar planètes (9 icônes) | ✓ planètes | ✓ planètes | Moyenne | Faible | ✓ |
| Tree view latéral | ✓ | ✓ | Importante | Important | ✓ |

**Choix : breadcrumb seul** pour la V1. Pas de Back/Forward, pas de palette,
pas de toolbar. Reconsidérer si l'usage le justifie.

Justifications :

- Double rôle *contexte + navigation* dans un seul widget.
- Lecture naturelle : `Solar system > Earth > Moon` se lit comme une adresse.
- La hiérarchie OrbitLab est peu profonde (≤ 2 niveaux sous la racine), donc
  l'arbre tient dans une ligne.

---

## 3. Décisions de conception

| Sujet | Décision |
|---|---|
| Périmètre V1 | **Hiérarchie de l'objet courant uniquement.** Pas de dropdown, pas de `PopupList`, pas de bouton Back, pas de command palette. |
| Affichage des fils | **Hors V1** — reporté en V2 (§7). |
| Missions | **Absentes du breadcrumb**, à tous les niveaux. Elles sont couvertes par leurs widgets dédiés (`MissionPanelWidgetAppState`, `MissionDisplayPanelAppState`, `TelemetryWidgetAppState`). |
| Nœud racine | Libellé **`Solar system`** (et non « Sun »). Nœud par défaut, **toujours présent**, toujours le premier segment. |
| Langue des libellés | Anglais, comme le reste de l'UI. Les segments corps utilisent `SolarSystemBody.displayName()` (`Earth`, `Moon`, …) ; la racine est le libellé fixe `Solar system`. |
| Position écran | **Bande dédiée en haut d'écran, pleine largeur**, segments centrés dedans. La bande est le premier élément du HUD : **tout le reste s'ancre en dessous**, à commencer par le menu applicatif d'`UI-4` (haut-gauche), qui n'est donc plus collé au bord haut. Voir §5.5 pour la chaîne d'ancrage. |
| Visibilité | Toujours visible (pas de toggle V1). La bande est donc réservée en permanence : aucun autre widget ne peut occuper cette hauteur, y compris quand le breadcrumb se réduit au seul segment `Solar system`. |
| Habillage | Un **sélecteur Lemur déclaré dans `FormStyles`**, jamais des overrides d'attributs à la construction. C'est la règle qui sort d'`UI-4` (cf. le cas `MissionPanelTrigger`) : un widget qui adopte `FormStyles.STYLE` n'en réécrit pas `background` / `color` / `font` / `insets`. |
| Click sur `Solar system` | Reset complet, équivalent touche `R` (`focusView.reset()`). |
| Distance caméra à l'arrivée | Même logique que le click 3D (réutilise `PlanetPoseAppState.onSelectPlanet`). |
| Style des segments | Texte + séparateur `>`. Segment du focus courant en surbrillance et non cliquable. Segments ancêtres cliquables. |
| Mise à jour | Poll de `FocusView` dans `update(tpf)` de l'`AppState`. Pas de poll de `MissionContext` (les missions ne sont plus affichées). |

---

## 4. Comportement détaillé

### 4.1 Forme générale

Le breadcrumb est la **chaîne des ancêtres du corps focalisé**, racine incluse :

```
Solar system
Solar system  >  Earth
Solar system  >  Earth  >  Moon
Solar system  >  Mars
```

Aucun bouton ▼, aucun popup en V1.

### 4.2 Cas par mode `FocusView`

`FocusView.viewSpacecraft(missionId, parentBody)` conserve `body` sur le corps
orbité (cf. Javadoc de `FocusView`). Le breadcrumb s'appuie **uniquement sur
`getBody()`** et ignore `getFocusedMission()` : suivre une mission ne change
donc pas le fil d'Ariane par rapport au corps parent.

| Mode courant | Breadcrumb | Segment non cliquable |
|---|---|---|
| `SOLAR` | `Solar system` | `Solar system` (on y est déjà) |
| `PLANET` = Earth | `Solar system > Earth` | `Earth` |
| `PLANET` = Moon | `Solar system > Earth > Moon` | `Moon` |
| `PLANET` = Mars | `Solar system > Mars` | `Mars` |
| `SPACECRAFT` (mission ancrée Earth) | `Solar system > Earth` | *(aucun — voir ci-dessous)* |

En mode `SPACECRAFT`, aucun segment ne correspond exactement au focus (le focus
est le spacecraft, qui n'est pas représenté). Le dernier segment — le corps
parent — est donc affiché comme **contexte cliquable** : le click quitte le
suivi de mission et repasse en `PLANET` sur ce corps. C'est le seul cas où le
dernier segment est actionnable.

### 4.3 Actions et bindings

- **Click sur `Solar system`** (depuis n'importe quel focus) → `focusView.reset()`.
  Strictement équivalent à la touche `R`.
- **Click sur un segment corps** (ancêtre, ou corps parent en mode
  `SPACECRAFT`) → reproduit la logique du click 3D sur ce corps
  (`PlanetPoseAppState.onSelectPlanet(body)` : règle la distance par défaut
  puis `focusView.viewPlanet(body)`).
- **Segment du focus courant non cliquable** (état « ici »), sauf le cas
  `SPACECRAFT` de §4.2.

### 4.4 États visuels

- **Segment focus courant** : couleur d'accent (`FormStyles.ACCENT_BRIGHT`),
  non cliquable.
- **Segments ancêtres** : couleur secondaire (`FormStyles.TEXT_SECONDARY`),
  hover → couleur primaire.
- **Séparateur `>`** : couleur tertiaire/discrète.
- Aucun bouton ▼ en V1.

### 4.5 Mise à jour temps réel

- Le breadcrumb se rafraîchit à chaque changement de focus.
- V1 : poll de `FocusView` dans `update(tpf)` du `BreadcrumbWidgetAppState`.
  Si `(mode, body)` change → reconstruire le widget. `focusedMission` n'entre
  pas dans la clé de comparaison : passer de `PLANET` Earth à `SPACECRAFT` sur
  une mission terrestre ne change que l'état cliquable du dernier segment, et
  le changement de `mode` suffit à le détecter.
- Évolution possible (hors V1) : émettre un event `FocusChanged` sur
  l'`EventBus` pour passer en push.

---

## 5. Architecture cible

### 5.1 Fichiers à créer

- `src/main/java/com/smousseur/orbitlab/ui/breadcrumb/BreadcrumbWidget.java`
  - Container Lemur occupant la bande haute. Méthodes :
    `setFocus(ViewMode mode, SolarSystemBody body)`,
    `layoutTopBand(int screenWidth)` — étire la bande sur toute la largeur et
    centre les segments dedans,
    `attachTo(Node)`.
  - Expose `AppStyles.BREADCRUMB_BAND_HEIGHT_PX` (hauteur fixe, indépendante
    du contenu) : c'est la constante dont dépend tout le reste du HUD (§5.5).
  - Construit la chaîne d'ancêtres en remontant `SolarSystemBody.parent()`
    depuis `body`, puis préfixe le segment racine `Solar system`. Le corps
    `SUN` n'est **jamais** rendu comme segment nommé « Sun » : il *est* la
    racine.
- `src/main/java/com/smousseur/orbitlab/ui/breadcrumb/BreadcrumbStyles.java`
  — déclare le **sélecteur Lemur** des segments (état courant / ancêtre /
  séparateur) dans `FormStyles`, plus la constante du libellé racine
  `Solar system`. Non optionnel : c'est ce qui évite de refaire l'erreur
  d'habillage relevée par `UI-4` sur `MissionPanelTrigger`.
- `src/main/java/com/smousseur/orbitlab/states/scene/BreadcrumbWidgetAppState.java`
  - `AbstractAppState` (calqué sur `TimelineWidgetAppState`).
  - `initialize` : crée le widget, l'attache à
    `context.guiGraph().getBreadcrumbNode()`, le pose en bande haute.
  - `update(tpf)` : détecte un changement `(mode, body)` et reconstruit le
    breadcrumb.
  - Câble les click handlers vers `PlanetPoseAppState` (segments corps) et
    `FocusView.reset()` (segment racine).

### 5.2 Fichiers à modifier

- `src/main/java/com/smousseur/orbitlab/engine/scene/graph/GuiGraph.java`
  - Ajouter un `breadcrumbNode` **attaché juste après `timelineNode`**, donc
    *avant* `missionPanelNode` et `modalNode` (`GuiGraph.java:23-27` — l'ordre
    d'attache fait le z-order, le dernier est au-dessus) + getter.
  - La bande ne recouvre rien par construction, mais l'ordre doit être fixé
    quand même : le menu d'`UI-4` et son déroulé, comme toute surface
    transitoire, passent **au-dessus** du chrome permanent si un déroulé
    déborde jamais vers le haut. `modalNode` reste topmost.
- `src/main/java/com/smousseur/orbitlab/OrbitLabApplication.java`
  - Enregistrer le nouveau `BreadcrumbWidgetAppState`.
- `src/main/java/com/smousseur/orbitlab/states/scene/PlanetPoseAppState.java`
  - Exposer `onSelectPlanet(SolarSystemBody)` ou un proxy public utilisable
    depuis le widget (actuellement `private`). Méthode existante à rendre
    accessible via `ApplicationContext` ou via un nouvel utilitaire
    `FocusController`.

### 5.3 Pas modifié en V1

- `core/SolarSystemBody.java` — la remontée se fait avec `parent()`, déjà
  présent. Le `children()` n'est utile qu'au dropdown, donc en V2 (§7).
- `states/mission/MissionRenderer.java` — les missions ne sont pas dans le
  breadcrumb, rien à exposer.
- `simulation/mission/context/MissionContext` — non consulté par le widget.
- `engine/events/EventBus.java` (le poll suffit pour V1).
- `app/view/FocusView.java` (pas de listener pour V1).
- `app/ApplicationContext.java` (rien à exposer en plus si on garde le poll).

### 5.4 Réutilisations

- `ui/AppStyles`, `ui/FormStyles`, `ui/UiKit` pour fonts et palettes — via un
  sélecteur, pas par overrides (§3).
- `AppStyles.HUD_MARGIN_PX` pour la marge, partagée avec le menu d'`UI-4`
  (§5.5).
- Pattern d'attachement HUD : `states/time/TimelineWidgetAppState` +
  `ui/timeline/TimelineWidget`.
- Pattern de click Lemur :
  `button.addClickCommands(s -> handler())`
  (cf. `ui/timeline/components/TransportControls.java`).

### 5.5 Chaîne d'ancrage vertical du HUD

La bande devient l'origine verticale du HUD haut-gauche. L'empilement cible :

```
┌────────────────────────────────────────────────┐
│            Solar system  >  Earth              │  bande breadcrumb (pleine largeur)
├────────────────────────────────────────────────┤
│ [ Menu ▾ ]                                     │  menu UI-4
│ ┌──────────────┐                               │
│ │ panneau des  │                               │  MissionDisplayPanelWidget
│ │ missions     │                               │
```

Chaque élément se pose sous le précédent via une constante, jamais une valeur
devinée :

| Élément | Ancrage vertical |
|---|---|
| Bande breadcrumb | bord haut de l'écran, hauteur `AppStyles.BREADCRUMB_BAND_HEIGHT_PX` |
| Menu `UI-4` | `BREADCRUMB_BAND_HEIGHT_PX + AppStyles.HUD_MARGIN_PX` |
| `MissionDisplayPanelWidget` | sous le menu, via la constante partagée qu'`UI-4` doit introduire |

**Conséquence sur l'ordre des chantiers.** `NAV-4` et `UI-4` étaient réputés
indépendants ; ils ne le sont plus tout à fait. `UI-4` prévoit déjà de
remplacer les deux marges divergentes (`MissionDisplayPanelWidget.MARGIN_PX =
5f` et `TRIGGER_HEIGHT = 28f` devinée, contre `AppStyles.HUD_MARGIN_PX = 16f`
côté trigger) par une constante partagée. Si `UI-4` passe en premier, cette
constante doit être écrite en prévoyant un décalage d'origine — sinon la bande
du breadcrumb repoussera un menu qui se croit collé au bord haut, et il faudra
reprendre les mêmes lignes une seconde fois. Le couplage est de l'ordre du
« ne pas faire deux fois », pas d'une dépendance technique : chacun des deux
se fait sans l'autre.

**Ce qui n'est pas décidé ici.** Le fond de la bande (aplat discret ou
transparent) sort du sélecteur `BreadcrumbStyles` et se tranche au maquettage,
comme le traitement « capsule jumelle » de `NAV-2`. La seule contrainte ferme :
la hauteur est fixe et ne dépend pas de la profondeur de la hiérarchie
affichée, sans quoi tout ce qui s'ancre en dessous bougerait au changement de
focus.

---

## 6. Vérification (test end-to-end)

1. Lancer l'app → bande breadcrumb visible en haut d'écran sur toute la
   largeur, affichant `Solar system` centré, segment non cliquable, aucun
   bouton ▼.
2. Click 3D sur Earth → breadcrumb devient `Solar system > Earth`, `Earth` en
   surbrillance non cliquable.
3. Click 3D sur Moon → `Solar system > Earth > Moon`.
4. Click sur `Earth` dans le breadcrumb → revient sur Earth avec la distance
   par défaut, breadcrumb `Solar system > Earth`.
5. Click sur `Solar system` → vue solaire (équivalent `R`), breadcrumb
   redevient `Solar system`.
6. Touche `R` pendant un focus profond → breadcrumb se met à jour sur
   `Solar system`.
7. Avec une mission active sur Earth, focus spacecraft → breadcrumb reste
   `Solar system > Earth`, **le nom de la mission n'apparaît nulle part** ;
   `Earth` devient cliquable et le click repasse en `PLANET` Earth.
8. Démarrer/arrêter une mission → le breadcrumb ne change pas.
9. **Ancrage (§5.5)** : le menu applicatif et, sous lui, le panneau des
   missions se posent bien **sous** la bande, sans la recouvrir ni laisser de
   trou. Vérifier aux deux extrêmes de profondeur — `Solar system` seul puis
   `Solar system > Earth > Moon` : la hauteur de bande ne bouge pas, donc rien
   dessous ne bouge non plus.
10. Ouvrir le déroulé du menu → il passe **au-dessus** de la bande si jamais il
    la recouvre (ordre `GuiGraph`, §5.2), et le wizard (`modalNode`) reste
    au-dessus de tout.

---

## 7. Hors scope V1 — descente vers les fils (V2)

Reportée telle quelle, à rouvrir quand la V1 est en place :

- Bouton ▼ sur le dernier segment, ouvrant un `PopupList`
  (`ui/mission/wizard/component/PopupList.java`) avec les **fils de premier
  niveau uniquement** (non récursif) :
  - depuis la racine → les planètes (`Mercury` → `Pluto`) ;
  - depuis `Earth` → `Moon` ;
  - depuis un corps sans fils → bouton ▼ masqué.
- Click sur un fils → `PlanetPoseAppState.onSelectPlanet(child)`.
- Click extérieur au popup → ferme le popup.
- Ajout de `public List<SolarSystemBody> children()` sur `SolarSystemBody`
  (filtre `values()` par `parent() == this`), cohérent avec `isSatellite()`.

Les missions restent exclues de cette liste : la décision de §3 vaut aussi
pour la V2.
