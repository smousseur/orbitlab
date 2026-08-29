# MIS-5 / L6 — Les onglets du step type

Lot **L6** du découpage ([`01-decoupage.md`](01-decoupage.md) §4). Il est indépendant des cinq
autres — le découpage le dit lui-même — et ne touche ni `simulation/` ni aucun `MissionSpec`. Il rend
vraie **une** propriété :

> **Le premier écran du wizard passe à l'échelle, à catalogue de profils inchangé.**

Six cartes, deux onglets par destination : `EARTH` 5, `MOON` 1. Aucune mission nouvelle, aucun champ
nouveau. Ce que `L7` trouvera est un step qui sait déjà accueillir une septième carte.

**Six faits mesurés contredisent ce qui est écrit**, dans le découpage ou dans le code, et quatre
changent ce que le lot livre : la taille réelle de la fenêtre, la marge réelle du budget vertical, la
hauteur réelle d'une carte, et le nombre de textures neuves. Ils sont au §1.2.

---

## 1. Inventaire mesuré

### 1.1 Ce que le lot touche

| Fichier | Ce qui bouge |
|---|---|
| `ui/mission/wizard/MissionDomain` | **neuf** — l'onglet, son libellé, ses trois fonctions pures |
| `ui/mission/wizard/step/MissionDomainTabs` | **neuf** — la bande et la règle qu'elle compose |
| `interface/wizard/tab-active.png` | **neuve** — l'onglet ouvert, sans bord bas |
| `interface/wizard/tab-idle.png` | **neuve** — la même, au fond en retrait |
| `interface/wizard/tab-panel.png` | **neuve** — le cadre, sans bord haut |
| `ui/mission/wizard/MissionProfile` | `domain()`, `defaultFor()`, et le commentaire de `of()` |
| `ui/mission/wizard/step/StepMissionType` | la réécriture, la régression de sélection, les cotes |
| `ui/mission/wizard/MissionWizardWidget` | le repli de `initialProfile` |
| `simulation/…/catalog/Payloads` | `domainOf` passe `public`, pour un lecteur qui est un test |
| `ui/form/FormStyles` | le javadoc de `CONTENT_HEIGHT`, faux sur deux nombres |
| `ui/mission/wizard/MissionDomainTest` | **neuf** — huit cas |
| `ui/mission/wizard/MissionProfileTest` | deux cas de plus, pour `defaultFor` |

Rien dans `simulation/` au-delà du changement de visibilité, et **aucune texture existante n'est
modifiée**. `step-underline-active.png` et `step-underline-done.png` restent orphelines : la
conception retenue ne les emploie pas.

### 1.2 Six faits que le découpage — ou le code — ignore

**1. La fenêtre du wizard fait 880 × 680, pas 880 × 660.** `MissionWizardWidget:36` pose
`WINDOW_HEIGHT = 680` et `WizardFooter:18` pose `FOOTER_HEIGHT = 92`. Le javadoc de
`FormStyles.CONTENT_HEIGHT` annonçait « 660 − 120 − 72 » : la valeur (468) est juste, les deux
nombres qui l'expliquent étaient faux, et le commentaire écrit en `L5` dans `MissionProfile.of`
répétait le 660. Les deux sont corrigés.

**2. « L'onglet terrestre tient dans la hauteur exacte de la mise en page d'aujourd'hui » est vrai
des cartes et faux du step.** La grille actuelle consomme **421 px sur 424** :

| élément | px |
|---|---|
| titre `MISSION TYPE` (orbitron 13, `lineHeight=18`) | 18 |
| écart | 12 |
| sous-titre `// select the target orbit` (mono 11, `lineHeight=15`) | 15 |
| écart | 12 |
| rangée 1 (`CARD_H`) | 176 |
| `ROW_GAP` | 12 |
| rangée 2 | 176 |

Utile = 468 − 28 (inset haut du volet) − 16 (inset bas) = 424. **Il restait trois pixels.** Une bande
d'onglets ne s'ajoute donc pas : elle se paie. Trois commentaires du dépôt disaient déjà que rien ne
clippe ici et qu'un débordement atterrit sur le pied de page — `PlanningPage:40`,
`EarthOrbitDynamicParameters:166`, `StepParameters:262`.

**3. Le badge d'une carte mesure 35 px, et la carte n'a aucune place à céder.** C'est le fait qui a
coûté le plus cher, parce qu'il a fallu lire le bytecode de Lemur pour l'établir :
`TbtQuadBackgroundComponent.create(texture, échelle, x1, y1, x2, y2, …)` passe **`x1` et `y1` comme
marge**, et `calculatePreferredSize` ajoute `2 × marge` sur les deux axes. Un fond obtenu par
`UiKit.wizardBg9(nom, n)` grossit donc son conteneur de `2n`. Le `Badge` appelle `wizardBg9(tex, 7)`
avec des insets de 3 : sa hauteur est 15 + 6 + 14 = **35**. Une carte empile alors
48 + 8 + 18 + 2 + 15 + 2 + 15 + 10 + 35 = 153, plus le `vSpacer(10)` que `SelectableCard:96` ajoute à
la racine quand un badge existe, soit **163 px de contenu**.

Et la boîte qui les reçoit **ne fait pas 176 mais 152** : le fond de la carte est un
`wizardBg9(base, **12**)`, donc sa marge vaut 12 et la hauteur utile `CARD_H − 24`. Le contenu
dépasse déjà cette aire intérieure de 11 px à 176 et s'arrête **un pixel** avant le bord extérieur.
`SelectableCard.centerH` compense la moitié horizontale de cette marge à la main — c'est son `− 24`
ligne 137 — et **rien ne compense la verticale**. La carte n'a donc pas 13 px de gras : elle en a un.
Toute valeur sous 176 fait sortir le badge du cadre de la carte, ce qu'une première version de ce lot
a livré à 164 avant que le rendu ne le montre.

**4. La marge de ces fonds est un padding intérieur, et c'est ce qui rend le cadre gratuit.**
`reshape` construit le quad à la taille **pleine** avant de rentrer les enfants de la marge. La peau
est donc dessinée au bord de la boîte et le contenu à l'intérieur : `setMargin(x, y)` **est** le
padding. Le cadre et les onglets s'en servent, et n'ont aucun `InsetsComponent` — un inset par-dessus
aurait rembourré deux fois.

**5. La largeur des cartes n'est pas contrainte par le badge.** Le découpage refuse une carte de
192 px au motif que `LONG-COAST STAGE OR AKM` demanderait 256. Mesuré : `ibmplexmono-11` est
monospace à `xadvance=7`, donc 22 × 7 = 154, plus 16 d'insets et 14 de marge = **182 px**. Le badge
tient dans une carte de 192. C'est ce qui libère la place horizontale que le cadre consomme.

**6. Le sens des textures est déterminé, et deux fichiers du dépôt le prouvent.** Aucune 9-slice de
l'atlas n'est verticalement asymétrique — `card-mission` (rangée 0 ≡ rangée 27), `toggle-group`
(0 ≡ 17), `input`, `btn-ghost` le sont toutes à un bit d'antialiasing près — donc aucune ne dit dans
quel sens le chargeur pose une image. Mais `header-bg.png` porte sa ligne claire `#132c48` sur sa
**dernière** rangée PNG et elle apparaît **sous** le bandeau à l'écran ; `footer-bg.png` la porte sur
sa **première** et elle apparaît **au-dessus** du pied. **La rangée 0 d'un PNG est le haut à
l'écran.** Le risque qui aurait dû attendre l'essai manuel est levé avant d'écrire une ligne.

### 1.3 Ce qui existe déjà et qu'on n'écrira pas

- **La taxonomie.** `Payloads.domainOf(MissionType)` répond déjà `LUNAR_ORBIT → LUNAR` ; le §3 dit
  pourquoi on la duplique quand même, et sous quel test.
- **L'idiome de montage.** `content.clearChildren()/addChild()` dans `MissionWizardWidget.showStep`
  et `pageHost` dans `StepParameters.showPage` : un hôte qu'on vide et qu'on remplit. Les onglets
  s'y conforment.
- **Le siège testable.** `RefusedPage` — classe pure package-private plus `RefusedPageTest`, dont le
  javadoc se réclame de `MissionDisplayPanelRules` et `RaanEntry`. `MissionDomain` est le même
  découpage.
- **Le filet de 1 px.** `WizardStepper.buildConnector()` construit déjà un trait comme un `Container`
  sur un `QuadBackgroundComponent`. La règle sous les onglets n'invente rien.
- **La séparation contrôle / écran.** `ModeSegmentedControl` ne suit pas sa propre sélection : il la
  reçoit et notifie. `MissionDomainTabs` fait pareil.

---

## 2. Le modèle : `MissionDomain`

Deux constantes, dans `ui/mission/wizard` à côté de `MissionProfile` et comme lui **sans Lemur** :
`EARTH` libellée `"EARTH"`, `LUNAR` libellée `"MOON"`.

**Le libellé se détache du nom de la constante**, et c'est une nécessité : `MissionProfile.LUNAR`
titre déjà sa carte `"LUNAR"`. Une carte et son onglet portant le même mot à deux niveaux se lisent
comme une répétition et non comme une hiérarchie — et `L7` place une carte `LUNAR ORBIT` à côté.
L'onglet nomme la destination, les cartes nomment les orbites. Un test interdit qu'un libellé
d'onglet coïncide avec un titre de carte.

`MissionProfile.domain()` est un **argument de constructeur**, pas une dérivation de `missionType()` :
une carte qui l'omettrait ne compilerait pas, ce qui empêche `L7` d'ajouter une septième carte sans
dire dans quel onglet elle va.

Trois fonctions pures portées par l'enum :

- `profiles()` — les cartes de l'onglet, dans l'ordre de déclaration. **Calculée à chaque appel et
  non mise en cache dans un champ statique** : les constantes de `MissionProfile` nomment un
  `MissionDomain`, donc un initialiseur statique lisant `MissionProfile.values()` tournerait au
  milieu d'une initialisation circulaire et observerait un tableau inachevé. Les appelants
  construisent un step, et ils sont deux.
- `enabledUnderLock(MissionType)` — voir §6.

**Un piège consigné dans le javadoc et épinglé par un test** : `MissionDomain.EARTH.profiles()` rend
**cinq** profils, `MissionProfile.earthOrbitProfiles()` en rend **quatre**. La seconde filtre sur
`missionType() == LEO` pour choisir un panneau de paramètres et laisse GEO dehors. Deux méthodes
voisines de nom, de contenus différents, qu'un lecteur pressé unifierait.

### 2.1 Pourquoi la taxonomie est écrite deux fois

Le découpage proposait de rendre `Payloads.domainOf` public et de le lire depuis `MissionProfile` —
« une seule source de vérité ». Trois frictions mesurées l'ont écarté :

1. `PayloadDomain` porte un **troisième** constant, `ANY`, qui est un joker *charge utile* et non un
   lieu de vol. Une bande construite sur `values()` afficherait un onglet vide, et l'énumération à la
   main des deux qui sont des domaines rend la source unique moins unique qu'annoncé.
2. `domainOf` prend un `MissionType`, quand quatre des six cartes partagent `LEO` : l'appel passerait
   par un type qui n'est pas ce que la carte est.
3. `MissionProfile` importerait `vehicle.catalog.Payloads` et tirerait son initialisation statique
   dans `MissionProfileTest` — la classe que le dépôt garde délibérément propre.

La duplication est donc assumée **et rendue incapable de dériver en silence** : `MissionDomainTest`
compare, pour chaque carte, le domaine déclaré à celui que le catalogue donne à son type. C'est le
partage que `MissionDisplayPanelRules` pratique déjà — la décision d'un côté, son contrôle de
l'autre.

`domainOf` passe donc `public` **pour ce seul lecteur, qui est un test**, avec un javadoc qui le dit
et qui redirige le code de production vers `MissionProfile.domain()`. La voie qui évitait ce `public`
— passer par `forMissionType(type)` et vérifier que tout modèle non-`ANY` rendu porte le domaine
attendu — n'utilise que de l'API publique mais **passerait à vide** le jour où un type n'aurait plus
que des charges utiles `ANY`. Une assertion qui peut devenir creuse sans que personne ne le voie ne
vaut pas d'économiser un mot-clé.

---

## 3. La conception visuelle : un dossier, pas une rangée de boutons

L'écran porte déjà trois familles de cliquables — le stepper numéroté, les cartes, les boutons du
pied. Un quatrième doit se lire au premier coup d'œil comme **une navigation** et non comme **une
action**.

La forme retenue est celle des onglets de dossier : la bordure entoure le contenu, remonte autour de
l'onglet ouvert, et **s'interrompt** sous lui. Elle bat le simple soulignement sur un argument qui
n'est pas esthétique : **l'onglet terrestre porte cinq cartes, le lunaire une** (deux après `L7`).
Sans cadre, basculer sur `MOON` vide les deux tiers de l'écran en fond de coque indifférencié — ça ne
se lit pas comme « ce dossier contient moins », ça se lit comme cassé. Un cadre de hauteur fixe rend
ce vide délibéré : le dossier fait la même taille, il tient moins de cartes.

Et sur la question de départ, la jonction fait mieux que le trait : **aucun bouton de l'application
n'est soudé à un panneau.**

### 3.1 Les trois textures

Toutes passent par `wizardBg9(nom, bord)`, dont le découpage symétrique suffit : l'asymétrie est dans
le **dessin**, pas dans la coupe.

| texture | taille / coupe | dessin |
|---|---|---|
| `tab-active` | 20 × 20, coupe 8 | coins hauts arrondis (r = 6), bord `#1a3a5c` à gauche/haut/droite (1,7 px), fond `#0f2847`, les huit rangées basses en pur remplissage |
| `tab-idle` | idem | la même géométrie, fond `#071526` |
| `tab-panel` | 28 × 28, coupe 12, comme `card-mission` | bord à gauche/droite/bas, coins bas arrondis (r = 7), les douze rangées hautes en pur remplissage |

Les deux couleurs sont celles de l'atlas, relevées au pixel : `#1a3a5c` est exactement
`FormStyles.BORDER`, `#0f2847` est le fond de `card-mission` et `#071526` celui de `toggle-group`.
Les trois fichiers sont donc **écrits exactement** et non relevés à l'œil, contrairement aux icônes
de mission ; les paramètres ci-dessus suffisent à les régénérer. Aucun générateur n'est commité, ce
qui reste la pratique du dépôt.

**`toggle-group` ne pouvait pas servir d'onglet inactif**, et c'est la correction qui a fait passer
le compte de deux textures à trois : il est arrondi sur ses **quatre** coins, et `wizardBg9` dessine
ses régions de coin sans les étirer. Un onglet ainsi rendu s'incurve en s'écartant de la règle : il
se lit comme une pastille **posée sur** le trait, quand un onglet de dossier passe **derrière**. Le
troisième PNG est le deuxième avec un aplat changé.

### 3.2 La règle, composée plutôt que recouverte

La bande est faite de deux rangs. Le premier porte les boîtes d'onglet et leurs écarts de 4 px. Le
second, **haut de 1**, porte la règle : un `QuadBackgroundComponent(FormStyles.BORDER)` sous chaque
onglet fermé, sous chaque écart et jusqu'au bord droit, et sous l'onglet ouvert un segment **peint au
fond du cadre** (`#0f2847`). Peint, et non laissé vide : un pixel transparent y laisse voir la coque
du wizard (`#0b1e35`, plus sombre que l'onglet comme que le cadre), ce qui redessine exactement le
trait que la jonction existe pour supprimer — le premier rendu l'a montré. Le cadre suit immédiatement, sans écart, et n'a pas de bord haut. La
ligne est donc continue partout **sauf** là où l'onglet ouvert rejoint son contenu.

**Rien ne se superpose.** L'alternative — garder un cadre bordé sur quatre côtés et descendre
l'onglet de 2 px par-dessus son bord haut — aurait fait dépendre la jonction d'un inset négatif et de
l'ordre de dessin entre frères. Le wizard porte déjà un tel inset (`WizardStepper:105`,
`Insets3f(-30, 0, 0, 0)`) et n'a pas besoin d'un second. Composer la règle coûte une texture de plus
et aucun tour de passe-passe.

Les largeurs viennent de `Label.getPreferredSize().x` plus le padding, et les segments s'en déduisent.
Le second rang est reconstruit à la bascule : quatre segments au plus, sans état.

---

## 4. La mise en page, et ce qu'elle a coûté

| élément | px | changement |
|---|---|---|
| titre `MISSION TYPE` | 18 | — |
| écart | 10 | 12 → 10 |
| boîte d'onglet (18 d'orbitron + 4 + 4) | 26 | neuf |
| règle de 1 px = bord haut du cadre | 1 | neuf |
| padding haut du cadre | 6 | neuf |
| rangée 1 | 176 | **inchangée** |
| `ROW_GAP` | 12 | — |
| rangée 2 | 176 | |
| padding bas du cadre | 6 | neuf |
| **total** | **431 / 440** | contre 421 aujourd'hui |

**Deux budgets, et c'est le second qui fait foi.** L'aire polie du volet de contenu vaut
468 − 28 − 16 = 424, mais la racine du step est épinglée à `CONTENT_HEIGHT` et débordait déjà cet
inset bas avant `L6` : la limite réelle est **440**, ce qui reste avant le bandeau du pied. Le total
de 431 mange donc 7 px du rembourrage bas du volet et laisse **9 px** avant le pied.

**La carte ne bouge pas, et c'est le fait 3 qui l'impose.** `CARD_W` et `CARD_H` gardent 256 et 176 :
la carte n'a pas un pixel à céder, et c'est le **cadre** qui se paie tout seul, à 6 px de padding
vertical. Ce n'est pas maigre à l'œil — chaque carte insère déjà son propre contenu de 12 px, donc il
reste 18 px entre le cadre et ce qui est dessiné dans une carte.

Horizontalement, la grille fait 3 × 256 + 2 × 16 = **800**, le cadre 816, donc **8 px de padding
latéral**, dérivé et non choisi (`PANEL_PAD_X = (CONTENT_WIDTH − GRID_WIDTH) / 2`). Le `trailing` que
calculait `padRow` disparaît : il vaut zéro par construction. Les onglets, dans la police retenue,
mesurent `EARTH` 53 px et `MOON` 45 px, plus 14 de padding de chaque côté, soit 81 et 73 px ; la
règle couvre les 658 restants.

**Le sous-titre `// select the target orbit` disparaît**, et c'est sa place qui paie la bande. C'est
un **écart assumé** : les cinq pages du wizard portent leur ligne `// …`
(`StepLauncher`, `StepLaunchSite`, `StepParameters`, `PlanningPage`, et celle-ci). Il est écrit ici
pour qu'il ne se lise pas, dans six mois, comme un oubli — les onglets disent ce que cette ligne
disait.

**Le cadre garde ses 364 px utiles quel que soit l'onglet monté**, les rangées empilées depuis le
haut. L'onglet lunaire montre donc une carte au-dessus de 188 px de cadre vide. Les deux autres
options — centrer la rangée, ou laisser le cadre s'ajuster — ont été écartées au profit d'un écran
qui ne change pas de masse à la bascule.

### 4.1 La police des onglets

**Orbitron 13**, la voix des titres, contre `ibmPlexMono 11`, la voix des annotations. C'est un choix
révisable au rendu et il tient dans une constante : mono 11 rend deux pixels et un registre plus
discret, en une ligne. L'argument pour la voix des annotations était qu'aucun bouton du dépôt ne la
parle ; l'argument contre est que 11 px pour la navigation principale d'un écran est petit.

---

## 5. Les cartes, construites une fois

Les six `SelectableCard` sont construites au démarrage du step et tenues dans une `EnumMap`. La
bascule d'onglet **monte et démonte des conteneurs de rangée** ; elle ne reconstruit rien.
Reconstruire perdrait l'état des cartes et enregistrerait un second écouteur sur chaque carte de
l'onglet rouvert.

C'est aussi ce qui fait fonctionner la réparation du §6 : la carte à éteindre peut vivre dans
l'onglet démonté, et l'`EnumMap` la tient quand même.

---

## 6. Les deux réparations

### 6.1 La régression de sélection

`StepMissionType.select()` affectait `selectedProfile = profile` **avant** de désélectionner, si bien
que `cards.get(selectedProfile)` et `cards.get(profile)` désignaient la même carte : **l'ancienne
restait allumée**, et deux cartes portaient la peau `SELECTED`. Le survol ne la rattrapait pas non
plus, `SelectableCard` ignorant `mouseExited` sur un état sélectionné.

Ce n'est pas un défaut d'origine : avant `MIS-7 P2` (#99, 16/08/2026) les deux cartes LEO et GEO
désélectionnaient explicitement *l'autre*. La généralisation à six cartes a inversé l'ordre. Le
correctif capture le profil sortant avant que le champ ne bouge.

### 6.2 Le repli de `initialProfile`

`MissionWizardWidget.initialProfile` retombait sur `getSelectedMissionType() == GEO ? GEO : LEO` :
un type `LUNAR_FLYBY` sélectionné redescendait silencieusement sur `LEO`. Inoffensif tant que les
cartes formaient une grille ; avec des onglets, cette réponse décide **lequel s'ouvre**.

Elle passe par `MissionProfile.defaultFor(MissionType)`, un `switch` exhaustif posé à côté de
`of(MissionSpec)` : `LEO → LEO`, `GEO → GEO`, `LUNAR_FLYBY → LUNAR`, et `LUNAR_ORBIT` lève le refus
nommant `L7`. Ce cinquième refus est inatteignable pour la même raison que les quatre autres :
`setSelectedMissionType` n'a que deux appelants — `StepMissionType` et `MissionWizardAppState:402`,
qui lit `spec.type()` — et ni `MissionFactory` ni `ScenarioMapper` ne construisent un
`MissionSpec.LunarOrbit`. Le compilateur pointera dessus le jour où `L7` ouvrira le chemin.

---

## 7. L'édition

Le step s'ouvre sur `initialProfile.domain()`. Un onglet dont `enabledUnderLock(type)` est faux passe
en `TEXT_LO`, sans écouteur ni survol ; ses cartes restent construites et inertes.

**La règle s'énonce « au moins une carte sélectionnable »**, et non « c'est le domaine de la mission
éditée ». Les deux coïncident aujourd'hui, mais seule la première survit à un domaine portant deux
types dont un seul est édité — ce que l'onglet lunaire devient en `L7`.

Ce que le verrouillage donne, carte par carte :

| mission éditée | onglet `EARTH` | onglet `LUNAR` |
|---|---|---|
| LEO / POLAR / SSO / MEO (type `LEO`) | 4 vivantes, GEO inerte | **1 inerte** |
| GEO | GEO vivante, 4 inertes | **1 inerte** |
| LUNAR (type `LUNAR_FLYBY`) | **5 inertes** | 1 vivante |
| LUNAR_ORBIT (après `L7`) | **5 inertes** | 1 vivante, LUNAR inerte |

**L'invariant qui rend la désactivation sûre** : en édition, exactement un onglet est entièrement
inerte, et **jamais celui sur lequel le step s'ouvre**. Un utilisateur ne peut donc pas atterrir sur
une page dont l'onglet est grisé et n'avoir nulle part où aller. Le test l'épingle pour les six
profils.

L'option retenue écarte au passage le piège de `FormStyles:165` — « a row with no geometry cannot be
picked » : chaque onglet portant une texture, tous ont une géométrie et sont cliquables. Un onglet
en libellé nu aurait demandé une surface de clic transparente.

---

## 8. Les tests

`MissionDomainTest`, huit cas, sans Lemur, sans `AssetManager` et sans Orekit :

1. **La partition** — l'union des `profiles()` est exactement `MissionProfile.values()`, sans
   doublon.
2. **L'ordre des cartes** dans chaque onglet suit l'ordre de déclaration.
3. **L'accord avec le catalogue** — chaque carte déclare le domaine que `Payloads.domainOf` donne à
   son type. C'est ce qui autorise la duplication du §2.1.
4. **Les libellés** — `MOON` et `EARTH`, et aucun ne coïncide avec un titre de carte.
5. **La distinction épinglée** — `EARTH.profiles()` en rend cinq, `earthOrbitProfiles()` quatre, et
   GEO est dans la première et pas dans la seconde.
6. **Le verrouillage** — pour chaque type édité, les onglets activés sont exactement ceux portant un
   profil de ce type.
7. **L'invariant du §7** — un seul onglet inerte, jamais celui qu'on occupe, pour les six profils.
8. **Deux onglets**, et l'appartenance de deux cartes témoins.

`MissionProfileTest` gagne deux cas : `defaultFor` rend une carte du bon type pour les trois types
qui en ont une, et refuse `LUNAR_ORBIT` en nommant `L7`.

**Puis l'essai manuel**, que le découpage exige : la jonction et l'interruption de la règle, le sens
des textures (déterminé au §1.2 fait 6, mais jamais vu à l'écran), la bascule qui préserve la
sélection, les deux cartes qui ne restent plus allumées, l'onglet désactivé en édition, et les
419 px qui ne débordent pas.

---

## 9. Ce que `L6` ne fait pas

- **Pas de `MissionProfile.LUNAR_ORBIT`** : c'est `L7`. Les quatre refus qui le nomment restent en
  place et deviennent **cinq** avec `defaultFor`.
- **Pas de conversion des deux pages de `StepParameters` en onglets.** C'est le seul second appelant
  plausible de la bande, et c'est pourquoi `MissionDomainTabs` naît dans `step/` et non dans
  `component/` : les six widgets de `component/` ont tous au moins deux utilisateurs — `PopupList` en
  a trois et sort du wizard — tandis que `ModeSegmentedControl`, à utilisateur unique, est resté
  package-private dans `panel/`. `component/` est l'endroit où un widget *arrive*, pas celui où il
  naît.
- **Pas de compte sur les libellés** (`EARTH · 5`). Un libellé qui porte un nombre se lit comme un
  filtre, donc comme un contrôle. Ça se rajoutera sans rien casser quand un troisième domaine rendra
  l'écran large.
- **Pas d'icône d'onglet** : il aurait fallu deux PNG de plus, ou le réemploi d'`icon-brand-globe`,
  déjà à l'écran en 18 × 18 dans le bandeau de marque.
- **Rien dans `simulation/`** au-delà du `public` sur `domainOf`.

---

## 10. Risques

1. **Le budget vertical repose sur les `lineHeight` lus dans les `.fnt`.** Si Lemur ajoute au `Label`
   une hauteur au-delà de sa ligne, les cinq px de coussin se réduisent. Vingt et un px restent avant
   le bord du pied de page, et rien ne clippe : un dépassement se verrait, il ne casserait rien.
2. **La police des onglets est un pari de rendu** (§4.1), pris comme tel. C'est une constante.
3. **Le sens des textures est déterminé mais non vu.** Le fait 6 l'établit par deux fichiers
   asymétriques dont l'orientation est visible à l'écran, et la chaîne 9-slice emploie le même
   chargeur ; si l'essai le dément, le correctif est un retournement des trois PNG, pas un changement
   de conception.
4. **La carte est pleine à un pixel près, et l'était avant `L6`.** 163 px de contenu pour une aire
   utile de 152 : tout ajout — une seconde ligne de badge, un pictogramme — sort du cadre de la
   carte sans que rien ne le signale. Le prochain lot qui touche `SelectableCard` doit relire le
   fait 3 avant d'ajouter une ligne, et corriger la marge verticale plutôt que la contourner.

---

## 11. Ce que `L6` lègue à `L7`

Un step qui accueille une septième carte sans rien déplacer : `MissionDomain.LUNAR.profiles()` en
rendra deux, elles tiendront sur une rangée du cadre existant, et l'onglet lunaire cessera d'être
celui qui n'a qu'une carte. Ce que `L7` doit fournir est la carte elle-même — `MissionProfile`
`LUNAR_ORBIT` en `Availability.WINDOWED`, son `domain()` valant `LUNAR`, son `AltitudeRange` et son
icône — plus la clé de champ du wizard, qui est ce que les cinq refus attendent.

Les tests de `MissionDomainTest` n'auront pas à changer : ils énoncent des propriétés sur
`MissionProfile.values()` et sur `MissionType.values()`, pas des listes. Le cas 6 devra seulement
perdre son `continue` sur `LUNAR_ORBIT`.
