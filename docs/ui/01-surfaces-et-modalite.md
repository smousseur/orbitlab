# Spec — Surfaces, modalité et pile de renvoi (`UI-5`)

> **Rédigé et tranché le 2026-08-15**, à la suite immédiate d'`UI-4`
> (`menu/01-menu-applicatif.md`), non commencé. Item `UI-5` de la roadmap,
> phase 2, ★3 ◆2 M.
>
> **Question d'origine** — `UI-4` livré, le menu applicatif est injoignable dès
> qu'une fenêtre du menu est ouverte. Faut-il le faire passer au-dessus de tout ?
> **Réponse tranchée : non.** Le problème n'est pas la hauteur du menu dans la
> pile, c'est que trois surfaces de natures différentes portent le même
> `ModalBackdrop`. On reclasse les surfaces au lieu de surélever le menu (§2, §5).
>
> **Arbitrages tranchés le 2026-08-15** — le panneau de gestion devient une
> **fenêtre non modale déplaçable** ; `ESC` ne quitte plus l'application et
> devient uniformément « renvoie la surface du dessus » ; **`Quit` devient une
> entrée de menu avec confirmation**. Aucune question ouverte (§8).

---

## 1. Contexte — ce qui existe aujourd'hui

### 1.1 Un seul espace de `z`, écrit en dur dans cinq fichiers

`GuiGraph` attache cinq nœuds à `guiFrame` et **n'en translate aucun**. Le
commentaire `// topmost` sur `modalNode` décrit donc une intention, pas un
mécanisme : `modalNode` passe devant `missionPanelNode` uniquement parce que ses
`z` valent 100/101 contre 0 à 60. Le tri du bucket GUI est global sur le `z`
monde, en rendu **comme en picking** — `ConfirmDialog` s'appuie explicitement
dessus pour se poser devant une modale déjà ouverte
(`ui/form/ConfirmDialog.java:39-43`).

| Surface | Fichier | `z` |
|---|---|:-:|
| Timeline (et ses composants internes) | `ui/timeline/TimelineWidget.java:127,174` | 0 → 5 |
| Télémétrie | `ui/telemetry/TelemetryWidget.java:236` | 0 |
| Panneau d'affichage | `ui/mission/display/MissionDisplayPanelWidget.java:184` | 0 |
| Menu : catcher / titre / déroulé | `ui/menu/AppMenu.java:82-84` | 40 / 50 / 60 |
| Panneau de gestion | `ui/mission/panel/MissionPanelWidget.java:90,343` | 100 / 101 |
| Wizard | `ui/mission/wizard/MissionWizardWidget.java:82,280` | 100 / 101 |
| `ConfirmDialog` | `ui/form/ConfirmDialog.java:43-45` | 200 / 201 |

**Trois surfaces sont déjà à `z = 0`.** Elles ne se recouvrent pas aujourd'hui
par chance de placement — bande basse, coin, haut-gauche. Aucune règle ne le
garantit, et une fenêtre déplaçable supprime la chance.

### 1.2 Les trois défauts mesurés

| # | Défaut | Où |
|---|---|---|
| 1 | **Le menu est injoignable dès qu'une modale est ouverte.** `ModalBackdrop` consomme `click`, `mouseButtonEvent`, `mouseEntered` *et* `mouseMoved` sur tout l'écran, 40 unités de `z` devant le déroulé. Le bouton `ORBITLAB` reste visible sous un voile à 60 % : il a l'air cliquable et ne l'est pas. | `ui/form/ModalBackdrop.java:55-80` |
| 2 | **`ESC` quitte l'application au milieu d'un wizard.** `UI-4` a pris la touche à `SimpleApplication` en écrivant qu'elle appartient désormais au HUD, « c'est là que `NAV-4` et les futures surfaces viendront s'inscrire » (`menu/01-menu-applicatif.md` §9.3). Personne ne s'y est inscrit : si le menu n'est pas ouvert, `ESC` appelle `stop()`. Ni le wizard, ni le panneau de gestion, ni `ConfirmDialog` n'écoutent la touche. | `states/mission/MissionDisplayPanelAppState.java:140-150` |
| 3 | **Deux surfaces se ferment et se rouvrent l'une l'autre par événements.** `onEdit` ferme le panneau de gestion avant de publier `OpenMissionWizard` « sinon le wizard s'empilerait par-dessus », et `submit()` republie `OpenMissionManagement` pour revenir en arrière. | `MissionPanelWidget.java:182-186`, `MissionWizardAppState.java:252` |

Le défaut 2 n'est pas une régression d'`UI-4` — l'ancien binding JME faisait
déjà quitter — mais `UI-4` en a fait une touche du HUD sans lui donner de pile.
Le défaut 3 n'est pas un bug : c'est un contournement qui fonctionne, et c'est
précisément ce qui le rend intéressant. Il mesure le coût du défaut de
conception qui suit.

---

## 2. Le vrai problème : trois surfaces, un seul traitement

`ModalBackdrop` est appliqué indifféremment à trois surfaces qui n'ont pas la
même nature.

| Surface | Nature réelle | Doit-elle bloquer l'écran ? |
|---|---|---|
| `ConfirmDialog` | Question bloquante, sans autre sortie que répondre | **Oui**, par essence |
| Wizard | Formulaire en quatre étapes portant un état non enregistré | **Oui**, mais avec une sortie explicite |
| Panneau de gestion | Navigateur sur des données | **Non** — il bloque par héritage, pas par décision |

Le panneau de gestion est modal parce qu'il a reçu un `ModalBackdrop`, pas parce
qu'un arbitrage l'a voulu. C'est lui, et lui seul, qui force la chorégraphie du
défaut 3 : deux modales ne peuvent pas coexister, donc l'une doit se fermer pour
laisser passer l'autre, donc il faut la rouvrir après.

**D'où la reformulation de la question d'origine.** Surélever le menu au-dessus
de tout ne réparerait rien et coûterait quelque chose : les entrées du menu sont
des commandes de navigation ; un *New mission…* flottant au-dessus d'un wizard
ouvert est soit un no-op — un menu menteur, exactement le défaut 2 qu'`UI-4`
avait supprimé — soit une destruction silencieuse du formulaire en cours. Le
menu doit redevenir joignable **parce qu'on a cessé de bloquer l'écran pour
rien**, pas parce qu'on l'a monté d'un cran.

---

## 3. Décisions de conception

| Sujet | Décision |
|---|---|
| Panneau de gestion | **Fenêtre non modale déplaçable.** Quitte `modalNode` pour `missionPanelNode`, perd son `ModalBackdrop`, gagne un header saisissable. |
| Wizard | **Reste modal.** Il porte un état non enregistré ; c'est la seule surface de travail du HUD dans ce cas. |
| `ConfirmDialog` | **Reste modal et bloquant.** Le clic sur son fond continue de ne rien faire. |
| Menu | **Reste sous le wizard et le dialogue.** Il passe au-dessus de tout le non-modal, ce qu'il fait déjà. |
| Échelle de `z` | Une classe `UiLayers`, seule détentrice de l'échelle. Plus aucun littéral de profondeur dans un widget. |
| `ESC` | Uniformément « renvoie la surface du dessus ». **Ne quitte plus l'application**, même pile vide. |
| Ordre de renvoi | **C'est la couche de `UiLayers`.** La surface renvoyée est, par construction, celle qui est visuellement devant. Un seul classement pour les deux usages. |
| Quitter | Entrée `Quit` en fin de menu, séparateur avant, confirmation par `ConfirmDialog`. |
| `Manage missions…` | Devient **`Mission management`**, bascule à coche : ce n'est plus un dialogue qu'on invoque, c'est une surface qui est à l'écran ou ne l'est pas. |
| Position de la fenêtre | Centrée à la première ouverture, retenue **pour la session** ensuite, jamais persistée sur disque. |
| Assets | **Aucun nouveau**, comme `UI-4`. `Quit` prend `wizard/icon-close-red`. |
| Logique testable | Le registre de surfaces est un modèle pur, hors cycle de vie JME, testé unitairement — comme `AppMenuModel` et `MissionDisplayPanelRules` avant lui. |

---

## 4. Les options étudiées

### 4.1 Sort du panneau de gestion

- **A — fenêtre non modale** ✅ *(retenue)*. La scène reste manipulable, la
  chorégraphie du défaut 3 tombe d'elle-même : le wizard modal se pose
  par-dessus et la fenêtre est encore là en sortant.
- **B — elle reste modale, on répare seulement la pile** *(écartée)*. `ESC`
  renvoie au lieu de quitter, échelle de `z` explicite, menu grisé sous modale
  plutôt que faussement cliquable. Périmètre bien plus petit, mais le défaut 3
  reste, et avec lui la raison pour laquelle deux surfaces se pilotent
  mutuellement par événements.
- **C — fusion avec le panneau d'affichage** *(écartée)*. Une seule vue
  « missions » : liste, détail et actions. Le plus gros chantier, et il enlève
  une surface au lieu d'en reclasser une — ce qui est séduisant, mais fait
  disparaître la distinction entre un HUD permanent compact et un plan de
  travail. À rouvrir seulement si les deux surfaces se mettent à diverger.

### 4.2 Placement de la fenêtre, une fois A retenue

- **A1 — déplaçable, ouverte au centre** ✅ *(retenue)*.
- **A2 — ancrée, non déplaçable** *(écartée)*. Zéro infrastructure nouvelle,
  placement déterministe. Mais 720×640 sous le panneau d'affichage est
  intenable en hauteur (34 + 8 + 240 + 8 + 640 = 930 px avant marges), donc ce
  serait un dock à droite — et l'utilisateur ne peut toujours pas dégager la
  fenêtre de ce qu'il veut regarder.
- **A3 — fixe au centre, moins le voile** *(écartée)*. Le plus petit diff
  possible, et le pire résultat : une fenêtre non modale qui occupe le centre
  de la vue 3D et qu'on ne peut ni bouger ni contourner. Autant garder la
  modale.

### 4.3 Ce que `ESC` fait quand la pile est vide

- **B2 — il ne quitte plus ; `Quit` passe dans le menu, avec confirmation**
  ✅ *(retenue)*. C'est la sémantique qu'`UI-4` §3 donnait déjà à la touche
  (« une sortie de surface, pas un raccourci à mémoriser »), et le menu est
  l'hôte prévu pour ce genre d'entrée.
- **B1 — il quitte, comme aujourd'hui** *(écartée)*. Comportement préservé,
  mais deux `ESC` rapides pour fermer un wizard ferment l'application au
  second.
- **B3 — il ne quitte plus et rien ne le remplace** *(écartée)*. Une
  application plein écran sans sortie clavier ni sortie dans son propre menu.

---

## 5. Décision

**A + A1 + B2**, tranchées le 2026-08-15.

Trois raisons, par ordre de poids :

1. **Elle supprime un contournement au lieu d'en ajouter un.** Le défaut 3 est
   la mesure du problème : deux surfaces qui se rouvrent mutuellement par
   événements parce que la modalité les empêche de coexister. A les rend
   indépendantes ; B les aurait laissées telles quelles en réparant seulement
   les symptômes visibles.
2. **Elle répond à la question d'origine par le bas de la pile.** Le menu
   redevient joignable en permanence sans qu'aucune de ses entrées n'ait à
   mentir sur ce qu'elle fait.
3. **Elle ne coûte presque rien en infrastructure.** Lemur fournit déjà le
   glisser (§6.3) ; l'échelle de `z` et le registre de surfaces sont deux
   petites classes pures qui remplacent des littéraux éparpillés dans cinq
   fichiers.

**Le défaut 1 n'est pas supprimé, il est réduit à son cas légitime.** Wizard ou
dialogue ouvert, le menu reste sous le voile et non cliquable — mais le voile
dit alors la vérité : l'écran *est* bloqué, et le rester est le but de ces deux
surfaces. Ce qui disparaît, c'est le cas courant : consulter ses missions
n'immobilise plus l'application.

Ce qui rouvrirait le sujet : décider que la vue « missions » doit être unique
(option C). Ce chantier ne l'interdit pas — il rend même la fusion plus facile,
les deux surfaces étant alors de même nature.

---

## 6. Ce que ça implique dans le code

### 6.1 `UiLayers` — l'échelle de profondeur

Une classe de constantes dans `ui/`, à côté de `AppStyles` et `UiKit`.

| Couche | `z` | Surface | Nature |
|---|:-:|---|---|
| `HUD` | 0 | timeline, télémétrie, billboards | ancrée, permanente |
| `PANEL` | 10 | panneau d'affichage | ancrée, basculable |
| `WINDOW` | 20 | fenêtre de gestion | déplaçable, non modale |
| `MENU_CATCHER` / `MENU_TITLE` / `MENU_DROPDOWN` | 40 / 50 / 60 | menu applicatif | permanent, au-dessus du non-modal |
| `MODAL_BACKDROP` / `MODAL` | 100 / 101 | wizard | modal |
| `DIALOG_BACKDROP` / `DIALOG` | 200 / 201 | `ConfirmDialog` | modal bloquant |

Deux valeurs bougent réellement : le panneau d'affichage passe de 0 à 10, la
fenêtre de gestion de 101 à 20. Tout le reste est la transcription de ce qui
existe. L'écart de 10 entre `HUD` et `PANEL` n'est pas décoratif : la timeline
empile ses propres composants jusqu'à `z = 5` en local (piste, remplissage,
graduations, playhead), donc la bande basse occupe réellement 0 → 5.

### 6.2 `HudSurfaces` — le registre et la pile de renvoi

`ESC` est possédé par `MissionDisplayPanelAppState`, le wizard par
`MissionWizardAppState`, la fenêtre par `MissionPanelWidgetAppState`. La règle
« pas de `getState()` » de `CLAUDE.md` interdit que l'un interroge les autres :
la vérité passe par `ApplicationContext`.

```java
public record HudSurface(String name, float layer, BooleanSupplier isOpen, Runnable dismiss) {}

public final class HudSurfaces {
  AutoCloseable register(HudSurface surface);   // fermer la poignée désinscrit
  Optional<HudSurface> topmostOpen();           // ce que ESC doit renvoyer
}
```

`register` rend un `AutoCloseable`, comme les abonnements à `SimulationClock` :
chaque `AppState` s'inscrit dans son `initialize()` et ferme la poignée dans son
`cleanup()`.

**La priorité de renvoi est la couche de §6.1.** Rien à maintenir à part.

`ESC` devient donc `topmostOpen()` → `dismiss()`, et **rien** si la pile est
vide. Surfaces inscrites, de la plus basse à la plus haute :

| Couche | Surface | `dismiss` |
|:-:|---|---|
| `WINDOW` | fenêtre de gestion | fermeture directe |
| `MENU_DROPDOWN` | déroulé du menu | `AppMenuModel.close()` (déjà en place) |
| `MODAL` | wizard | **confirmation d'abandon**, voir ci-dessous |
| `DIALOG` | `ConfirmDialog` | son `onCancel` |

**Le panneau d'affichage ne s'inscrit pas**, délibérément. C'est un élément de
HUD que l'on allume et que l'on éteint depuis le menu, pas une surface que l'on
« renvoie » : `ESC` sur une scène nue le ferait disparaître sans que rien ne
l'ait appelé. La règle qui en sort : s'inscrit ce qui a été **ouvert par-dessus
le travail en cours**, pas ce qui est allumé en permanence.

Trois conséquences :

1. `MissionDisplayPanelAppState` garde le mapping de la touche — il l'a pris à
   `SimpleApplication` — mais n'appelle plus `stop()`.
2. `ConfirmDialog` s'inscrit à son tour, avec `dismiss = onCancel`. Cela
   contredit une phrase de son Javadoc (« les deux seules sorties sont les deux
   boutons ») : **à mettre à jour**, `ESC` devient l'équivalent clavier de
   *Cancel*. Le clic sur le fond continue de ne rien faire. L'inscription est
   faite par celui qui ouvre le dialogue — `MissionPanelWidget` pour la
   suppression, `MissionDisplayPanelAppState` pour `Quit` — qui ferme la
   poignée en même temps que le dialogue.
3. **`ESC` sur le wizard passe par une confirmation d'abandon**, pas par une
   fermeture sèche : c'est le scénario qui a motivé B2. Le bouton *Cancel* du
   wizard reste immédiat, lui : il est déjà explicite. La confirmation est
   demandée **systématiquement**, sans suivi de « formulaire modifié » — le
   suivi de saleté est un mécanisme à écrire et à maintenir pour épargner un
   clic dans le seul cas où l'utilisateur ouvre un wizard et en sort sans rien
   saisir.

### 6.3 La fenêtre déplaçable

**Lemur fournit déjà le glisser de fenêtre.**
`com.simsilica.lemur.event.DragHandler` est présent dans le jar 1.16, traite
explicitement le cas du bucket GUI en 1:1, et expose un `draggableLocator` qui
sert exactement à ça — on attrape le header, c'est la racine qui bouge. Il n'y a
donc pas de handler à écrire, seulement à **borner** : il ne clampe rien et
laisse sortir la fenêtre de l'écran.

D'où `WindowDragHandler` dans `ui/form/`, une sous-classe qui contraint la
translation après coup :

- le header reste **entièrement** à l'écran, en largeur et en hauteur, donc la
  fenêtre est toujours rattrapable ;
- son bord haut ne monte pas au-dessus de
  `topOffset + HUD_MENU_HEIGHT_PX + HUD_STACK_GAP_PX` — sinon le header passe
  sous le bouton `ORBITLAB`, qui est 30 unités de `z` devant, et devient
  inattrapable. On réutilise la chaîne d'ancrage d'`UI-4` §6.3 telle quelle.

Trois conséquences dans `MissionPanelWidget` :

1. **`centerOnScreen()` est appelé à chaque frame** (`MissionPanelWidget.java:149`).
   En l'état il annulerait le glisser à la frame suivante. Il devient :
   placement au premier affichage, puis re-clamp **uniquement** sur changement
   de taille d'écran.
2. `PanelHeader` devient la zone de prise. Sa croix de fermeture garde son
   propre écouteur — les enfants sont piqués avant leur parent.
3. La racine perd son `ModalBackdrop` mais **garde** son consommateur de clics
   (`MissionPanelWidget.java:115-122`) : sans voile derrière elle, un clic dans
   la fenêtre atteindrait la scène 3D et sélectionnerait un corps.

La position est retenue **par `MissionPanelWidgetAppState`**, pas par le widget :
celui-ci est construit et détruit à chaque ouverture
(`MissionPanelWidgetAppState.java:48-67`) et ce cycle reste inchangé. Première
ouverture centrée, réouvertures là où on l'avait laissée, remise à zéro au
redémarrage de l'application.

### 6.4 Le menu

Deux changements, l'un induit, l'autre nouveau.

**`Manage missions…` devient `Mission management`, bascule à coche.**
Conséquence directe de A : ce n'est plus un dialogue qu'on invoque et qu'on
subit, mais une surface qui est à l'écran ou ne l'est pas — exactement le statut
de *Mission panel*. Les points de suspension, qui annoncent « ouvre un dialogue
demandant une saisie », ne s'appliquent plus. Le menu porte alors deux bascules
cohérentes entre elles au lieu d'une bascule et d'un faux dialogue. La coche se
lit sur l'existence de la fenêtre, comme celle de *Mission panel* se lit sur
`MissionDisplayPanelWidget.isVisible()` — source de vérité unique dans les deux
cas.

**`Quit`, en fin de liste, séparateur avant**, icône `wizard/icon-close-red` —
aucun asset à créer, conformément à la contrainte que s'était donnée `UI-4`.
Clic → `ConfirmDialog("Quit OrbitLab ?")` → `stop()`. L'entrée s'ajoute à
`MENU_ITEMS` et à `AppMenuModel` sans rien changer d'autre : c'est le troisième
cas d'usage qui valide la liste déclarative d'`UI-4`.

Le grisage (`AppMenuModel.isEnabled` / `AppMenu.setEnabled`), écrit dans `UI-4`
et inutilisé, **reste inutilisé ici** : avec A, aucune entrée n'est
indisponible selon le contexte. Il attend toujours `UI-3`.

### 6.5 Classes

| Classe | Emplacement | Rôle |
|---|---|---|
| `UiLayers` | `ui/` | Échelle de profondeur, seule détentrice des `z`. |
| `HudSurface` | `app/` | Record d'une surface renvoyable : nom, couche, prédicat d'ouverture, action de renvoi. |
| `HudSurfaces` | `app/` | Registre porté par `ApplicationContext`. Logique pure, testable sans JME. |
| `WindowDragHandler` | `ui/form/` | `DragHandler` de Lemur, borné à l'écran et sous la chaîne d'ancrage du HUD. |

### 6.6 Périmètre exact du diff

- **Ajouté** — `ui/UiLayers`, `app/HudSurface`, `app/HudSurfaces`,
  `ui/form/WindowDragHandler`, `HudSurfacesTest`, deux cas dans
  `AppMenuModelTest`.
- **Modifié** — `MissionPanelWidget` (plus de backdrop, couche `WINDOW`, header
  saisissable, placement une fois au lieu d'à chaque frame) ·
  `MissionPanelWidgetAppState` (attache à `missionPanelNode`, mémorise la
  position, s'inscrit au registre) · `MissionWizardAppState` et
  `MissionWizardWidget` (inscription, `ESC` → confirmation d'abandon) ·
  `MissionDisplayPanelAppState` (`ESC` délègue au registre et ne quitte plus ;
  entrées `Mission management` et `Quit`) · `MissionDisplayPanelWidget`,
  `AppMenu`, `ConfirmDialog`, `TimelineWidget`, `TelemetryWidget` (lisent leur
  `z` dans `UiLayers`) · `ApplicationContext` (porte le registre) ·
  `GuiGraph` (le commentaire `// topmost` sur `modalNode` décrit une intention
  que rien n'applique : à corriger ou à retirer).
- **Supprimé** — la chorégraphie du défaut 3 : `onEdit` n'a plus à fermer le
  panneau avant d'ouvrir le wizard, et `MissionWizardAppState.submit()` n'a plus
  à republier `OpenMissionManagement` pour revenir en arrière.

---

## 7. Ce qu'on ne fait pas

- **Pas de gestionnaire de fenêtres généralisé** : redimensionnement,
  minimisation, empilement par focus, mémorisation sur disque. Une fenêtre
  déplaçable et bornée, rien de plus.
- **Pas de `PopupState` de Lemur.** Lemur a son propre mécanisme de popups
  modales ; l'adopter ici demanderait de reprendre les trois surfaces
  existantes et leur `ModalBackdrop`, pour un bénéfice nul sur le problème
  traité.
- **Pas de suivi « formulaire modifié » dans le wizard** (§6.2, point 3).
- **Pas de fusion du panneau de gestion et du panneau d'affichage** —
  option C de §4.1, hors périmètre.
- **Pas de reprise du wizard** au-delà de son inscription au registre et de sa
  confirmation d'abandon.
- **Pas de raccourci clavier nouveau.** `ESC` change de sens, aucune autre
  touche n'est ajoutée ; `R` (`ViewModeAppState`) reste le seul autre binding.

---

## 8. Arbitrages tranchés

Aucune question n'est ouverte. Les trois qui l'étaient à l'ouverture du sujet
ont été tranchées le 2026-08-15, le jour même.

1. ~~**Le menu doit-il passer au-dessus de tout ?**~~ **Non.** On reclasse les
   surfaces au lieu de surélever le menu ; il reste sous les deux seules
   surfaces qui ont une raison de bloquer l'écran (§2, §5).
2. ~~**Le panneau de gestion : fenêtre, modale ou fusion ?**~~ **Fenêtre non
   modale déplaçable**, ouverte au centre, position retenue pour la session
   (§4.1, §4.2).
3. ~~**Que fait `ESC` quand plus rien n'est ouvert ?**~~ **Rien.** Quitter
   devient une entrée de menu avec confirmation (§4.3, §6.4).

---

## 9. Risques à vérifier à l'implémentation

Trois points que la conception ne peut pas trancher sur pièces, et qui devront
être vérifiés à l'écran plutôt que par un test.

1. **Cohabitation `MouseEventControl` / `CursorEventControl`.** La racine de la
   fenêtre porte déjà un `MouseEventControl` qui consomme les clics, et le
   glisser passe par `CursorEventControl`. Les deux familles sont alimentées par
   la même `PickEventSession`. Si la consommation du bouton étouffe le
   `cursorButtonEvent` du header, il faut restreindre le consommateur au corps
   de la fenêtre plutôt qu'à sa racine.
2. **Le clamp.** Bornes en fenêtré, en plein écran, et au redimensionnement
   pendant qu'une fenêtre est posée près d'un bord.
3. **Le picking après changement de couche.** Le panneau d'affichage passe de
   `z = 0` à `z = 10` et la fenêtre de 101 à 20 : vérifier qu'aucune surface du
   HUD ne devient inatteignable, en particulier là où la fenêtre peut être
   glissée par-dessus la timeline et la télémétrie.
