# Spec — Breadcrumb de navigation

## 1. Contexte

Aujourd'hui, dans OrbitLab, l'utilisateur ne dispose que de deux moyens de navigation :

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
- Depuis le Soleil, viser une planète éloignée à la souris est lent et
  imprécis.
- Aucune indication textuelle d'où on est (pas de fil d'Ariane, pas de
  contexte hiérarchique).

Objectif : ajouter un **widget HUD persistant** ("breadcrumb") qui

1. montre où l'on est dans la hiérarchie céleste,
2. permet en 1 click de remonter à un parent,
3. permet en 1–2 clicks de descendre vers un fils (corps ou mission).

---

## 2. Solutions étudiées et choix

| Solution | 1-click parent | 1-click fils | Couvre missions | Surface écran | Effort | Découvrable |
|---|---|---|---|---|---|---|
| **Breadcrumb hiérarchique** ✅ | ✓ | ✓ (dropdown) | ✓ | Faible | Moyen | ✓ |
| Boutons Back/Forward (historique) | ✗ | ✗ | ✓ | Très faible | Faible | ✓ |
| Bouton Home seul | ✓ Soleil | ✗ | ✗ | Très faible | Très faible | ~ |
| Command palette ("Go to…") | ✗ (2 clicks) | ✓ | ✓ | Faible (popup) | Moyen | ~ |
| Toolbar planètes (9 icônes) | ✓ planètes | ✓ planètes | ✗ | Moyenne | Faible | ✓ |
| Tree view latéral | ✓ | ✓ | ✓ | Importante | Important | ✓ |

**Choix : breadcrumb seul** pour la V1. Pas de Back/Forward, pas de palette,
pas de toolbar. Reconsidérer si l'usage / le nombre de missions actives le
justifient.

Justifications :

- Double rôle *contexte + navigation* dans un seul widget.
- Lecture naturelle : `Soleil > Terre > Lune` se lit comme une adresse.
- La hiérarchie OrbitLab est peu profonde (≤ 2 niveaux), donc l'arbre tient
  dans une ligne.
- S'étend naturellement aux missions (feuilles ancrées sous une planète).

---

## 3. Décisions de conception

| Sujet | Décision |
|---|---|
| Périmètre V1 | Breadcrumb seul. Pas de bouton Back, pas de command palette. |
| Affichage des fils | **Dropdown** via `PopupList`, **fils de premier niveau uniquement** (non récursif). |
| Missions | Les missions actives apparaissent comme fils de leur `parentBody` dans le dropdown. |
| Position écran | Haut centré (HUD persistant). |
| Visibilité | Toujours visible (pas de toggle V1). |
| Click sur "Soleil" | Reset complet, équivalent touche `R` (`focusView.reset()`). |
| Distance caméra à l'arrivée | Même logique que le click 3D (réutilise `PlanetPoseAppState.onSelectPlanet` / `MissionRenderer.onSpacecraftSelected`). |
| Style des segments | Texte + séparateur `>`. Focus courant en surbrillance, non cliquable. Parents et fils cliquables. |
| Mise à jour | Poll de `FocusView` + `MissionContext` dans `update(tpf)` du `AppState`. |

---

## 4. Comportement détaillé

### 4.1 Forme générale

```
Soleil  >  Terre  >  Lune
```
(Lune n'a pas de fils — bouton ▼ masqué.)

```
Soleil  >  Terre  ▼
                  ├── Lune
                  └── Mission Apollo-X
```

```
Soleil  ▼
        ├── Mercure
        ├── Vénus
        ├── Terre
        ├── Mars
        ├── Jupiter
        ├── Saturne
        ├── Uranus
        ├── Neptune
        ├── Pluton
        └── (+ missions héliocentriques actives)
```

### 4.2 Cas par mode `FocusView`

| Mode courant | Breadcrumb | Dropdown ▼ |
|---|---|---|
| `SOLAR` (Soleil) | `Soleil` | Mercure → Pluton + missions héliocentriques |
| `PLANET` = Terre | `Soleil > Terre` | Lune + missions ancrées Terre |
| `PLANET` = Lune | `Soleil > Terre > Lune` | (vide → bouton ▼ masqué) |
| `PLANET` = Mars | `Soleil > Mars` | Missions ancrées Mars (▼ masqué si vide) |
| `SPACECRAFT` (mission `X` parent=Terre) | `Soleil > Terre > X` | (vide) |

### 4.3 Actions et bindings

- **Click sur "Soleil"** (depuis n'importe quel focus) → `focusView.reset()`.
  Strictement équivalent à la touche `R`.
- **Click sur un parent** (segment intermédiaire) → reproduit la logique du
  click 3D sur ce corps (`PlanetPoseAppState.onSelectPlanet(body)` : règle
  la distance par défaut puis `focusView.viewPlanet(body)`).
- **Click sur le ▼** → ouvre le `PopupList` listant les fils directs du focus
  courant.
  - Fils **corps** → `PlanetPoseAppState.onSelectPlanet(child)`.
  - Fils **mission** → équivalent `MissionRenderer.onSpacecraftSelected` :
    `focusView.setCameraDistance(SPACECRAFT_FOCUS_DISTANCE_SOLAR_UNITS)` puis
    `focusView.viewSpacecraft(name, parent)`.
- **Click extérieur au popup** → ferme le popup.
- **Focus courant non cliquable** (état "ici").

### 4.4 États visuels

- **Segment focus courant** : couleur d'accent (`FormStyles.ACCENT_BRIGHT`),
  non cliquable.
- **Segments parents** : couleur secondaire (`FormStyles.TEXT_SECONDARY`),
  hover → couleur primaire.
- **Séparateur `>`** : couleur tertiaire/discrète.
- **Bouton ▼** : icône simple, masqué quand le focus n'a aucun fils.
- **Dropdown** : style `PopupList` existant
  (`ui/mission/wizard/component/PopupList.java`), une ligne par fils, hover
  surligné.

### 4.5 Mise à jour temps réel

- Le breadcrumb se rafraîchit à chaque changement de focus.
- V1 : poll de `FocusView` dans `update(tpf)` du `BreadcrumbWidgetAppState`.
  Si l'état (mode, body, mission) change → reconstruire le widget.
- V1 : poll de `MissionContext` à la même cadence pour rafraîchir la liste
  des missions filles.
- Évolution possible (hors V1) : émettre un event `FocusChanged` sur
  l'`EventBus` pour passer en push.

---

## 5. Architecture cible

### 5.1 Fichiers à créer

- `src/main/java/com/smousseur/orbitlab/ui/breadcrumb/BreadcrumbWidget.java`
  - Container Lemur. Méthodes :
    `setFocus(ViewMode, SolarSystemBody body, String missionName, List<ChildEntry> children)`,
    `layoutTopCenter(int screenWidth)`,
    `attachTo(Node)`.
- `src/main/java/com/smousseur/orbitlab/ui/breadcrumb/BreadcrumbStyles.java`
  *(optionnel)* — couleurs et tailles dérivées de `FormStyles` / `AppStyles`.
- `src/main/java/com/smousseur/orbitlab/states/scene/BreadcrumbWidgetAppState.java`
  - `AbstractAppState` (calqué sur `TimelineWidgetAppState`).
  - `initialize` : crée le widget, l'attache à
    `context.guiGraph().getBreadcrumbNode()`, le pose en haut centré.
  - `update(tpf)` : détecte un changement (mode/body/mission/missions actives)
    et reconstruit le breadcrumb.
  - Câble les click handlers vers `PlanetPoseAppState` (parent + fils corps)
    et `MissionRenderer` / `FocusView` (fils mission).

### 5.2 Fichiers à modifier

- `src/main/java/com/smousseur/orbitlab/core/SolarSystemBody.java`
  - Ajouter `public List<SolarSystemBody> children()` qui filtre `values()` par
    `parent() == this`. Cohérent avec `isSatellite()` déjà présent.
- `src/main/java/com/smousseur/orbitlab/engine/scene/graph/GuiGraph.java`
  - Ajouter un `breadcrumbNode` (entre `timelineNode` et `modalNode`) +
    getter.
- `src/main/java/com/smousseur/orbitlab/OrbitLabApplication.java`
  - Enregistrer le nouveau `BreadcrumbWidgetAppState`.
- `src/main/java/com/smousseur/orbitlab/states/scene/PlanetPoseAppState.java`
  - Exposer `onSelectPlanet(SolarSystemBody)` ou un proxy public utilisable
    depuis le widget (actuellement `private`). Méthode existante à rendre
    accessible via `ApplicationContext` ou via un nouvel utilitaire
    `FocusController`.
- `src/main/java/com/smousseur/orbitlab/states/mission/MissionRenderer.java`
  *(ou équivalent côté missions)*
  - Exposer une méthode publique pour focuser une mission donnée (réutiliser
    la logique privée existante).

### 5.3 Pas modifié en V1

- `engine/events/EventBus.java` (le poll suffit pour V1).
- `app/view/FocusView.java` (pas de listener pour V1).
- `app/ApplicationContext.java` (rien à exposer en plus si on garde le poll).

### 5.4 Réutilisations

- `ui/mission/wizard/component/PopupList.java` pour le dropdown des fils.
- `ui/AppStyles`, `ui/FormStyles`, `ui/UiKit` pour fonts et palettes.
- Pattern d'attachement HUD : `states/time/TimelineWidgetAppState` +
  `ui/timeline/TimelineWidget`.
- Pattern de click Lemur :
  `button.addClickCommands(s -> handler())`
  (cf. `ui/timeline/components/TransportControls.java`).

---

## 6. Vérification (test end-to-end)

1. Lancer l'app → breadcrumb visible en haut centré, affichant `Soleil`,
   bouton ▼ ouvre la liste des planètes (+ missions héliocentriques).
2. Click sur `Terre` (3D ou via dropdown) → breadcrumb devient
   `Soleil > Terre`, ▼ liste `Lune` (+ missions ancrées Terre).
3. Click sur `Lune` (via dropdown) → `Soleil > Terre > Lune`, ▼ masqué (pas
   de fils).
4. Click sur `Terre` dans le breadcrumb → revient sur Terre avec la distance
   par défaut.
5. Click sur `Soleil` → vue solaire (équivalent `R`), breadcrumb redevient
   `Soleil`.
6. Avec une mission active sur Terre : la mission apparaît dans ▼ de Terre.
   Click → focus spacecraft, breadcrumb `Soleil > Terre > <nom mission>`.
7. Pendant le focus mission, click sur `Terre` dans le breadcrumb → repasse
   en mode `PLANET` Terre.
8. Touche `R` pendant un focus profond → breadcrumb se met à jour sur
   `Soleil`.
9. Démarrer/arrêter une mission → la liste des fils du parent se met à jour
   dans la ▼ au refresh suivant.
