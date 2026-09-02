# Roadmap OrbitLab v3.X.X — Le retour et la maturité

Troisième version. Elle ferme la boucle des missions — **partir de la Terre,
contourner la Lune, revenir** — et solde ce que les versions précédentes ont
laissé mûrir : les deux items d'interface volontairement gardés hors phases
(`UI-6`, `UI-7`) et la décision de persistance qu'ils réclament, la passe de
rendu que rien ne bloquait, et la première confrontation de la simulation à une
référence extérieure.

Porte d'entrée du dossier : [`00-index.md`](00-index.md). Ce qui précède :
[v2](02-roadmap-v2.md). Ce qui suit : [v4](04-roadmap-v4.md). L'ordonnancement
de la dette : [`05-roadmap-technique.md`](05-roadmap-technique.md).

---

## 1. Ce que v3 change

**Une phrase.** À la fin de v2, une trajectoire part et revient sur Terre, mais
seulement si elle n'est jamais allée plus loin que l'orbite basse ; l'interface
est fonctionnelle et jamais mûre ; et rien, nulle part, ne vérifie que la
simulation dit la vérité. v3 ramène un vaisseau **de la Lune**, donne à l'UI la
finition qu'elle a repoussée trois versions durant, et confronte les
éphémérides à une source indépendante.

Deux faits qui cadrent la version :

- **`MIS-11` ne peut pas rentrer avec la pile entière.** Artemis, comme Apollo,
  rentre **en capsule**, module de service largué. C'est exactement le préalable
  que `PHY-6` livre en [v2](02-roadmap-v2.md) — et c'est pourquoi cette mission
  n'était plus à sa place à côté du lunaire de v1.
- **Rien ne valide la simulation contre une vérité extérieure**, et `BUG-19`
  vient de montrer le prix de cette absence : Neptune tournait à **4,1 % de son
  taux vrai**, Saturne et Uranus **à l'envers**, sans qu'aucun test, aucune
  assertion, aucun garde-fou ne le signale. C'est la classe de défaut qu'une
  référence externe attrape seule.

---

## 2. Le plan

| ID | Item | ★ | ◆ | Taille | Après |
|---|---|:-:|:-:|:-:|---|
| `MIS-11` | Artemis : survol lunaire et retour en capsule | 5 | 4 | L | `MIS-10`, `PHY-6` (v2), `BUG-12` |
| `UI-6` | Fenêtres déplaçables, empilement par focus | 3 | 2 | M | — |
| `UI-7` | Infobulles + socle de survol partagé (absorbe `BUG-4`) | 3 | 2 | M | `UI-6` (conseillé, pas dur) |
| `UI-8` | **Préférences utilisateur persistées** *(neuf)* | 3 | 2 | M | `UI-6`, `UI-7` |
| `PHY-7` | **Validation contre une référence externe** *(neuf)* | 4 | 3 | M | — |

**Pourquoi les trois `UI` dans cet ordre.** `UI-6` extrait le trio qui fait une
fenêtre et pose le registre d'empilement ; `UI-7` pose le socle de survol dont
l'infobulle est le premier client ; `UI-8` est le seul des trois qui écrit sur
disque, et il ne peut décider *quoi* persister qu'une fois les deux autres
livrés — position de fenêtre, ordre d'empilement, bascules d'affichage, délai
d'infobulle sont tous des candidats qui n'existent pas encore.

**`PHY-7` est indépendant de tout le reste** et peut se glisser n'importe quand.
Il est écrit ici parce que c'est la version où la maturité est le sujet, pas
parce qu'un item l'attend.

**Fin de version quand** : un vaisseau parti de la Terre contourne la Lune et
revient se poser où on l'a décidé (`MIS-11`), deux fenêtres se recouvrent sans
qu'on doive en fermer une (`UI-6`), aucun contrôle n'est muet (`UI-7`), l'écran
retrouve son état au lancement suivant (`UI-8`), et un écart aux éphémérides de
référence devient un test rouge plutôt qu'une découverte fortuite (`PHY-7`).

---

## 3. Dette et robustesse

*Les fiches vivent dans [`bugs.md`](../bugs.md),
[`dette-technique.md`](../dette-technique.md) et
[`reliquats.md`](../reliquats.md) ; leur ordonnancement dans
[`05-roadmap-technique.md`](05-roadmap-technique.md).*

**Avec `MIS-11`, et avant lui : `BUG-12`.** La bande morte ε de franchissement de
sphère d'influence n'a **jamais été calibrée** sur une trajectoire réellement
capturée. `PHY-4 / L6` l'a laissée ouverte faute d'un tel vol ; `MIS-5` en a
fourni un ; `MIS-11` sera le premier à franchir la frontière **dans le sens du
retour**, ce qui n'a encore jamais été exercé. C'est le risque n° 1 de l'item.

**Le reste du lunaire**, à intercaler pendant `MIS-11` : `REL-8` (offset de
visée TLI mono-dimensionnel, plancher de périlune mesuré à 132 km), `REL-9`
(`ParkingCoastStage` ne gère pas un coast plus court qu'`ignitionLead`),
`REL-10` (ToF / parking / angle de transfert jamais balayés conjointement),
`REL-12` (la timeline du wizard lunaire crible mais ne confirme jamais : un
refus ne sort qu'à la validation ou en mission `FAILED`), `REL-16` (le refus de
masse sèche de charge utile lunaire n'a aucun test automatisé).

**`H-UI` va avec `UI-6` / `UI-7`**, et c'est la version où il cesse d'être
« piochable » : `REL-24` (extraire l'interaction de `MissionTimelineWidget`, 842
lignes), `REL-25` (aucun test sur la chaîne d'ancêtres du breadcrumb), `REL-26`
(breadcrumb jamais vérifié à l'écran), `REL-27` (descente vers les fils,
reportée en V2 par la spec), `REL-15` (horizon GEO sous-estimé d'environ 7 %),
`REL-20` (horizon MEO à 48 révolutions). Le reste de `DT-5` — `OrbitCameraAppState`,
`StepParameters` — se traite ici ou à la trace.

**`H-DEC` est absorbé par `UI-8`.** `REL-28` (auto-optimisation après création
d'une mission) et `REL-29` (persistance des bascules d'affichage) ne sont pas du
travail, ce sont deux questions — et toutes deux ont vu passer leur jalon prévu
sans être tranchées. `UI-8` leur en donne un qui ne peut plus glisser : il **est**
le fichier de préférences dont `REL-29` débat, et `REL-28` se décide dans la même
séance puisque c'est le même genre de choix.

**`H-RND` est la respiration de la version.** Dix fiches, un seul sous-système,
aucune dépendance croisée, et la seule passe du corpus dont le résultat se voit
à l'écran : `BUG-1` (jitter Pluton), `BUG-2` (sauts de skybox), `BUG-5` (pop du
modèle au changement de focus), `REL-1` (raccord terminal du ruban), `REL-2`
(`MUTING_STEP`), `REL-3` (fondu alpha et largeurs), `REL-4` (tone mapping),
`REL-5` (pénombre du vaisseau), `REL-6` (éclipse totale contre annulaire).
`BUG-3` en sort : ses cinq lots sont implémentés, sa validation à l'écran est
en [v1](01-roadmap-v1.md#4-la-ligne-11x--corrections-et-stabilisation). Bon
candidat juste après `MIS-11`, où rien n'est visible pendant des semaines.

---

## 4. Détail des items

### MIS-11 — Artemis : survol lunaire et retour en capsule — ★5 ◆4 L

**Pourquoi.** C'est la mission qui ferme la boucle : partir de la Terre, contourner
la Lune, revenir. Aucune autre mission du corpus ne revient.

**L'aller est presque acquis.** `MIS-4` livre le TLI de production et le survol ;
`PHY-4 / L6` a déjà volé un translunaire complet — parking à 185 km, injection sur
seed Lambert, bascule de sphère d'influence à 74 h, périlune volé à 100,4 km pour
100 visés. La branche aller de `MIS-11` n'est donc pas un chantier neuf, c'est un
`MIS-4` **contraint** : le survol y est visé pour que la branche retour existe, et
non pour lui-même.

**Une correction sur l'énoncé, et c'est elle qui décide de la version.** Artemis,
comme Apollo, ne repasse **pas** par une orbite basse au retour : c'est une
**entrée directe** depuis la trajectoire de retour, et ce qui entre n'est pas la
pile mais la **capsule**, module de service largué. Se mettre en LEO en revenant
de la Lune coûte ~3,1 km/s de freinage — donc un étage de plus à dimensionner —
ou une **aérocapture**, c'est-à-dire un passage atmosphérique dosé, qui exige la
traînée de `PHY-2` *et* un modèle thermique que personne n'a écrit ici. Les trois
lectures donnent trois missions différentes ; c'est la question ouverte n° 1 au
§5, à trancher avant d'écrire la spec.

Quelle que soit la lecture retenue, **la séparation de l'objet qui rentre est
commune aux trois**, et c'est `PHY-6` ([v2](02-roadmap-v2.md)) qui la fournit.
C'est la raison pour laquelle cet item n'est pas resté avec le lunaire de v1.

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
  ouverte n'a toujours pas de calibrage (`BUG-12`, §3).
- La séparation du module de service avant l'entrée, sur la machinerie de
  `PHY-5` / `PHY-6`.
- La rentrée finale : `MIS-10` tel quel. C'est la raison pour laquelle `MIS-10`
  est en v2 et pas ici — pris dans l'autre ordre, `MIS-11` paierait la
  terminaison de descente au milieu d'un chantier ◆4.

**Spec.** [`docs/brainstorm/missions.md`](../brainstorm/missions.md) §8 traite le
lunaire d'un bloc (TLI + LOI) et ne dit **rien** du retour ; à étendre avant de
commencer.

---

### UI-6 — Fenêtres déplaçables, empilement par focus, modalité du wizard — ★3 ◆2 M

> **Pourquoi il est phasé ici, alors qu'il était volontairement hors phases.**
> L'argument qui l'en tenait à l'écart était juste et se retourne : il n'est pas
> gratuit dans le temps, chaque surface livrée d'ici là étant une fenêtre de
> plus à reprendre. Le bon moment n'est donc pas « un jour de creux » mais
> « avant la prochaine fenêtre » — et v2 en livre au moins une (le sélecteur de
> fidélité de `PHY-3`). C'est un argument pour le **planifier**, pas pour le
> laisser flotter.

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
4. **Aucune persistance dans cet item.** Position et ordre repartent du défaut à
   chaque lancement. `UI-5` a déjà exclu la position sur disque, et la question
   plus large des préférences utilisateur a désormais un propriétaire — `UI-8`,
   plus loin dans cette même version. La traiter ici mélangerait deux
   chantiers ; l'y renvoyer garantit qu'elle sera traitée.

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
rangée en **§5, n° 2**.

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

### UI-7 — Infobulles sur les contrôles, et le socle de survol qui les porte — ★3 ◆2 M

> **Phasé ici pour la même raison qu'`UI-6`, en plus marqué.** Son coût est
> proportionnel au nombre de contrôles à reprendre — **22 sites de survol** au
> dernier comptage, un de plus à chaque widget livré entre-temps. Il ne bloque
> rien, mais c'est le seul item du corpus dont le prix augmente mécaniquement
> avec le temps ; le laisser « piochable » revenait à le renchérir à chaque
> version.

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

### UI-8 — Préférences utilisateur persistées — ★3 ◆2 M *(neuf)*

**Pourquoi.** Trois décisions ont été reportées avec la même formule — *« à
trancher au moment de X »* — et X est passé sans qu'elles le soient :

| Report | Devait être tranché | État |
|---|---|---|
| Persistance des bascules d'affichage (`REL-29`, question 6 de v1) | « au moment d'`UI-3` » | `UI-3` est clos depuis le 2026-08-21 |
| Position et ordre des fenêtres (`UI-5`, puis `UI-6`) | renvoyé à « la question plus large des préférences » | Cette question n'a jamais eu de propriétaire |
| Auto-optimisation après création (`REL-28`, question 3 de v1) | son préalable est levé depuis `UI-2` | La question elle-même reste ouverte |

Une question sans jalon ne se tranche pas ; elle se re-pose. `UI-8` est le jalon.

**Le fait qui rend l'item nécessaire, et pas seulement souhaitable.** `UI-3` a
livré un format de **scénario** — des données de mission, destinées à être
échangées. Une préférence d'écran n'a rien à y faire : rejouer le scénario d'un
tiers reconfigurerait l'interface de celui qui l'ouvre. Ce sont **deux fichiers,
pas un**, et le second n'existe pas.

**Ce que l'item livre.**
- Un fichier de préférences utilisateur, distinct du format de scénario, avec sa
  propre politique de version et une règle simple : **une préférence absente ou
  illisible retombe sur le défaut, sans erreur ni refus**. Un fichier de confort
  ne doit jamais empêcher l'application de démarrer.
- Les préférences elles-mêmes, décidées une par une : bascules d'affichage
  (`UI-4`), position et ordre des fenêtres (`UI-6`), état du panneau
  d'affichage, et ce que `UI-7` aura ajouté.
- **Une réponse écrite** à l'auto-optimisation après création — que ce soit oui,
  non, ou « oui, en préférence ». Écrire le non est un livrable au même titre
  que le oui : ce qui coûte, c'est de rouvrir la question tous les trois mois.

**Ce que l'item s'interdit.** Pas de profils multiples, pas de synchronisation,
pas de migration depuis un format antérieur (il n'y en a pas), et **aucune
préférence qui change un résultat de calcul** — la frontière est là : ce qui
modifie une trajectoire est une donnée de mission et va dans le scénario.

**Validation.** Testable sans contexte GL : le round-trip écriture / relecture,
la retombée sur défaut pour chaque champ absent, corrompu ou hors bornes, et
l'indépendance vis-à-vis du format de scénario (charger un scénario ne doit
toucher aucune préférence, et réciproquement).

---

### PHY-7 — Validation contre une référence externe — ★4 ◆3 M *(neuf)*

**Pourquoi, et le fait qui le déclenche.** Rien dans le dépôt ne compare la
simulation à une vérité extérieure. Les tests vérifient des **invariants**
(continuité d'arc à 0,000000 m, non-régression au bit près, un périlune volé
contre un périlune visé) — tous excellents, et tous aveugles à une erreur
présente dès l'origine. `BUG-19` l'a démontré le 2026-09-02 : la rotation propre
de Neptune tournait à **4,1 % de son taux vrai**, Saturne et Uranus **à
l'envers**, et 808 tests verts n'y ont rien vu. Un invariant confirme qu'on n'a
pas changé ; il ne dit pas qu'on a raison.

**Ce que c'est.** Un jeu de comparaisons contre une source indépendante — JPL
Horizons est le candidat naturel, il est déjà au backlog de v1 — sur trois
familles :

1. **Positions planétaires.** Un échantillon de dates couvrant l'horizon
   supporté, par corps, contre les éphémérides Horizons. C'est la famille qui
   attrape les défauts de la couche `simulation/source/` et du dataset.
2. **Rotation propre et orientation.** Le taux et le sens pour les onze corps,
   plus l'obliquité — précisément ce que `BUG-19` et `BUG-3` ont trouvé faux à
   la main, et qui ne doit plus se trouver à la main.
3. **Un arc de trajectoire.** Une orbite basse et un transfert connus, propagés
   ici et comparés à une propagation de référence, pour borner la dérive du
   propagateur.

**Comment le faire sans réseau dans les tests.** Un dataset de référence figé,
commité, produit une fois par un outil de `tools/` — la même forme que
`ephemerisgen/` et `orbitgen/` ont déjà. Un test qui appelle une API extérieure
est un test qui rougit un jour de panne : la référence doit être un fichier, et
sa mise à jour un geste explicite.

**Ce que l'item doit produire en plus des tests** : une **tolérance justifiée
par corps et par famille**, écrite avec sa provenance. Une tolérance choisie
pour faire passer le test est pire que pas de test — c'est le piège exact que
`REL-18` décrit du côté optimiseur, où l'étalement de ~19 km de l'ensemble
acceptable est le plancher de bruit de toute comparaison.

**Ce que l'item s'interdit.** Pas de validation des missions optimisées contre
des vols réels — l'ensemble acceptable du CMA-ES n'a pas de vérité unique à
comparer. Pas de couverture exhaustive : trois familles, un échantillon de
dates, et un critère explicite pour ce qui n'est pas couvert.

---

## 5. Questions ouvertes

1. **Entrée directe, capture propulsive ou aérocapture (`MIS-11`)** — héritée de
   v1, et à trancher **avant** d'écrire la spec : les trois ne décrivent pas la
   même mission, ni le même lanceur. L'entrée directe est la moins chère et la
   plus fidèle au nom de l'item. La capture propulsive en LEO coûte ~3,1 km/s,
   donc un étage de plus à dimensionner, et a un vrai intérêt pédagogique — elle
   montre *pourquoi* personne ne le fait. L'aérocapture est la plus
   spectaculaire et ajoute un modèle thermique à un chantier déjà ◆4.
   Recommandation : **entrée directe**, les deux autres restant des variantes à
   proposer une fois la première volée.
2. **Modalité du wizard de création (`UI-6`)** — héritée de v1. L'argument d'`UI-5`
   (état non enregistré, perdable derrière une autre fenêtre) tient toujours ; le
   coût de la bascule n'est pas dans le wizard mais dans la caméra, qui refuse
   aujourd'hui la souris tant que le wizard *existe* et non tant qu'il est
   *survolé* (`OrbitLabApplication:141`). À ne trancher que sur un besoin
   constaté — consulter la liste des missions pendant la création.
   Recommandation par défaut : le garder modal **et l'écrire**, plutôt que de
   laisser la question ouverte indéfiniment.
3. **Auto-optimisation après création d'une mission** — héritée de v1, et
   désormais portée par `UI-8`. `createMission()` ajoute l'entrée en `DRAFT` sans
   déclencher de calcul ; l'argument qui bloquait — déclencher automatiquement un
   calcul long sans indicateur serait pire que le clic actuel — ne tient plus
   depuis qu'`UI-2` a livré l'indicateur, jusqu'à la distinction entre une
   mission en file et une mission en calcul. La question n'est plus technique.
4. **Quelle source pour `PHY-7`** — JPL Horizons est le candidat évident, mais
   les fichiers SPICE de la NAIF ou les DE-ephemerides sont des alternatives que
   le projet consomme peut-être déjà indirectement via Orekit. **Comparer Orekit
   à sa propre source ne prouve rien** : le premier travail de l'item est de
   vérifier que la référence choisie est réellement indépendante de la chaîne
   testée.
