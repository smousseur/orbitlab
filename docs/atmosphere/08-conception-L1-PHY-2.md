# PHY-2 / L1 — Socle de faisabilité + câblage — conception

Conception détaillée du lot `L1` de `PHY-2`, découpé en
[`06-decoupage-PHY-2.md`](06-decoupage-PHY-2.md) §5. Le lot rend une ascension drag-on
**terminable**, tranche par mesure le modèle d'atmosphère à l'optimiseur, et ne bascule
aucun défaut : côté drag-off, rien ne bouge d'un bit.

> **Statut : conception arrêtée, écrite après exploration du code.** Elle **corrige trois
> points de forme du découpage** (§7) sans en rouvrir aucune décision de fond. Les valeurs
> de plancher et de coût citées sont des cibles de mesure, pas des seuils figés : `L1`
> tranche `a`/`c` et confirme la profondeur de cession **en volant**.

---

## 1. Périmètre du lot

**Dans `L1`.**

- un **arrêt d'altitude drag-conditionnel** qui rend une propagation drag-on terminable et
  **ferme [`../bugs.md` BUG-10](../bugs.md#bug-10--reentryguard-inopérant-en-présence-de-traînée)** ;
- la **règle de modèle à l'optimiseur**, tranchée par mesure, et le **câblage `DT-14`**
  écrit dessus (actif uniquement sous traînée) ;
- les mesures consignées qui deviennent les entrées de `L2`+.

**Hors `L1`** — et c'est le reste de `PHY-2` : le recalibrage de l'Isp (`L2`), de
l'ascension (`L3`), du dimensionnement (`L4`), et la **bascule du défaut** (`L5`). `L1`
laisse le défaut à `NONE`.

**La contrainte du lot.** `L1` est un **socle**, pas un lot de physique : comme le `L1`
de `PHY-1`, il pose ce qui allumera sans allumer. Tout ce qu'il ajoute est
`hasDrag()`-conditionnel, donc invisible tant qu'aucune mission ne déclare de traînée.

**Entrées.** `L0` ([`07-baseline-L0-PHY-2.md`](07-baseline-L0-PHY-2.md)) — les trois mesures
de physique héritées et les deux contraintes dures que ce lot lève — et la brique de traînée
déjà livrée par `PHY-1` (`DragForce` conditionnel dans les deux factories, choix porté par
`MissionSpec`).

---

## 2. État des lieux mesuré

L'exploration du code a produit cinq faits qui **précisent** le mécanisme du découpage.

### 2.1 La pénalité de coût existe déjà

`GravityTurnProblem.trajectoryCost` (lignes 264-277) grade un candidat dont la propagation
a échoué — état de retour ≈ état initial, `elapsed < 1 s` — par
`1e3 + underground / 1000`, où `underground` vient de `MinAltitudeTracker.getMinAltitude()`.
Le « candidat qui pique bas abandonné à coût faible » du découpage **n'est donc pas à
écrire**. Le seul livrable neuf est l'**arrêt précoce** : sans lui, ce coût faible n'est
assigné qu'**après** les pas d'intégration que la descente en air dense a coûtés.

### 2.2 « Relever le plancher » est impossible tel quel

`ReentryDetector.g()` (ligne 69) est une **altitude sphérique pure**, sans test de vitesse
radiale ; son plancher `ReentryGuard.SUBSURFACE_FLOOR = −50 km` est profond **précisément**
pour ne pas se déclencher sur les pas de tir — la Javadoc chiffre l'altitude sphérique
négative de chaque site (Plesetsk à **−17,0 km**). Or, sous traînée, l'intégrateur cède à
**−9/−30 km** (`BUG-10`), soit **au-dessus** du pas de tir le plus profond. Un plancher
sphérique unique ne peut donc pas être à la fois au-dessus de la cession et sous tous les
pas de tir. La levée est un **gate de descente**, non un plancher plus haut (§3.1).

### 2.3 L'arrêt est déjà armé partout, optimiseur compris

`ReentryGuard.armQuiet(propagator, GravitationalContext)` est appelé sur une vingtaine de
sites, dont `GravityTurnManeuver` (ligne 334, le foyer de propagation de
`GravityTurnProblem`), les étages analytiques, et `StageLegRunner` (lignes 211/213) pour le
runtime. `L1` n'ajoute donc **aucun site d'arming** : il fait porter à ces appels existants
une variante drag, via le contexte (§3.1).

### 2.4 Le point `DT-14` est uniforme, et HP lève à la propagation

`OrekitService.addDrag` (ligne 383) est **commun aux deux factories** — runtime (ligne 302)
et optimisation (ligne 348) — et monte le `DragForce` du modèle que le contexte porte, via
`atmosphereFor(model, gravity)` (ligne 421), qui construit `HarrisPriester` ou `NRLMSISE00`
en cache. **Aucune substitution NRLMSISE→HP n'existe** : c'est le câblage neuf de `DT-14`.
Et HP **ne lève pas à la construction** : c'est sa densité, interrogée sous 100 km à la
propagation, qui lève. Une optimisation d'ascension sous HP lèverait donc à chaque candidat.

### 2.5 Le suivi d'altitude est en place

`MinAltitudeTracker` (`ContinueOnEvent`, ne stoppe pas) enregistre les extrêmes d'altitude
et est déjà instancié par `AscentChainPropagation.propagate` (`new MinAltitudeTracker(0.0,
+∞)`) et lu par le coût. C'est lui qui alimente la pénalité graduée de §2.1.

---

## 3. Décisions de conception

### 3.1 L'arrêt descente-gardé

**Le détecteur.** Un **sibling** de `ReentryDetector`, laissé intact pour le drag-off : sa
fonction de commutation est `g = altitude sphérique − plancher`, et le **handler ne renvoie
`Action.STOP` que si `vRadial < 0`** (trajectoire descendante), sinon `CONTINUE`. C'est ce
gate qui autorise un plancher **haut** :

- l'**ascension** qui grimpe traverse le plancher en montant (`vRadial > 0`) → `CONTINUE` ;
- au **pas de tir**, à `t = 0`, le véhicule est immobile (`vRadial = 0`, gate strict) →
  pas de déclenchement, même si le pad démarre **sous** le plancher (les détecteurs Orekit
  agissent sur un changement de signe, pas sur le signe initial) ;
- un **orbite basse valide** (périgée ≥ plancher) ne traverse jamais le plancher → jamais
  coupée ;
- une **trajectoire qui rentre** le traverse en descendant → `STOP`, **9 km au-dessus** de
  la cession.

**Le plancher.** Descente-gardé, il vaut le **plancher de validité du modèle** (§3.3) :
~0 km sous NRLMSISE. Cela **garantit la terminaison** et alimente la pénalité existante.

**L'arming — le seul point structurel.** `ReentryGuard.armQuiet` gagne une surcharge prenant
un `FlightContext` : elle arme le `ReentryDetector` −50 km **comme aujourd'hui**, et **si
`hasDrag()`**, arme **aussi** le détecteur descente-gardé. Les ~25 sites passent alors
`context` au lieu de `context.gravity()` — mécanique. Drag-off : `hasDrag()` faux → chemin
byte-identique. Deux formes ont été **écartées** : co-localiser l'arming dans
`OrekitService.addDrag` (inversion de couche — `OrekitService`, bas niveau, dépendrait de
`mission.detector`) ; et armer par site (25 décisions dispersées, `hasDrag()` répété).

**Contingences, mesurées, non engagées d'avance.** Si le nombre de pas borné par la coupe à
0 km reste trop élevé sur la descente en air dense :

- **coupe au périgée osculateur** — stopper un candidat descendant dont le périgée osculateur
  est déjà sous terre, **avant** la plongée en air dense (le coût calcule déjà l'apogée et le
  périgée osculateurs) ;
- **pas max sous traînée**, sur le patron du `burnLimitedMaxStep` déjà en place.

### 3.2 Couplage avec le modèle (§3.3)

Le plancher d'arrêt doit être **≥ le plancher de validité du modèle**, sinon le modèle lève
avant que l'arrêt ne tire :

- **NRLMSISE** (0→1500 km, ne lève pas) → l'arrêt descente-gardé à ~0 km est **nécessaire**,
  c'est lui qui coupe les candidats qui retombent ;
- **HP** (lève à 100 km) → un candidat descendant sous HP **se termine tout seul** à 100 km
  (throw → chemin pénalité), *plus haut* qu'un arrêt à 0 km.

L'arrêt descente-gardé est donc l'outil du **régime NRLMSISE** ; sous HP, le throw fait le
travail. L'ascension (NRLMSISE dans `a` comme dans `c`) s'appuie sur l'arrêt ; le transfert
sous HP (`c`) s'appuie sur le throw. Cohérent, rien à ajouter.

### 3.3 Le modèle à l'optimiseur, et le câblage `DT-14`

`J2` a tranché « optim toujours Harris-Priester » (`DT-14`). Mais HP lève sous 100 km et
toute ascension part de 0 km : **HP ne peut pas voler l'ascension**. Le modèle se tranche
donc par mesure, entre deux règles (la troisième, HP planché sous 100 km, est écartée
d'avance — elle gèle ρ dans l'air dense, là où la traînée d'ascension est maximale) :

- **`a`** — NRLMSISE partout à l'optim = **aucune substitution** : l'optim monte le modèle de
  la mission tel quel. Trivial. Coûteux (×3,73–4,03 par propagation).
- **`c`** — composite : NRLMSISE le gravity-turn, HP le transfert. Garde la raison-coût de
  `DT-14` là où HP est valide (orbital ≥ 100 km).

**Le foyer de `c` est le résolveur, pas la factory** — et c'est un amendement au hint de
`DT-14`. `createOptimizationPropagator` est uniforme : il ignore si la propagation qu'il sert
est l'ascension ou le transfert. Une substitution posée là s'applique à tout. Le vrai foyer
d'un choix **par phase** est le résolveur `MissionStage.flightContext(entryState, mission)`
de `PHY-1` (§3.6 de [`04-conception-L1.md`](04-conception-L1.md)), en **variante optim** :
ascension → NRLMSISE, orbital → HP. Le résolveur runtime garde le modèle mission de bout en
bout. Dans les deux règles, « optim toujours HP » est **amendé** : HP ne vole pas
l'ascension.

**`a` d'abord, `c` seulement si nécessaire.** `a` est trivial quand `c` est du câblage, et le
budget compute est le seul juge. Donc la discipline « mesurer, escalader » : `L1` implémente
`a`, mesure le coût d'une optimisation d'ascension sous NRLMSISE contre le **+50 %** annoncé
par la fiche roadmap, et **n'écrit `c` que si `a` dépasse**. Pas de double câblage a priori.

---

## 4. Sorties

**Code.**

- le **détecteur descente-gardé** — sibling de `ReentryDetector` (`g = alt − plancher`,
  handler `STOP` ssi `vRadial < 0`) ; `ReentryDetector` reste intact ;
- **`ReentryGuard.armQuiet(FlightContext)`** — arme le −50 km comme aujourd'hui, plus la
  variante drag si `hasDrag()` ; les ~25 sites passent `context` ;
- le câblage **`a`** (résolveur d'optim = modèle mission), ou **`c`** (résolveur par phase)
  en cas d'escalade ;
- `FlightContext.hasDrag()` si le prédicat n'existe pas déjà.

**Mesures consignées — entrées de `L2`+.**

- coût de l'optimisation d'ascension sous NRLMSISE vs drag-off vs +50 % → la règle `a`/`c` ;
- profondeur de cession de l'intégrateur par modèle, confirmant que le plancher 0 km la
  dégage ;
- pas d'un candidat re-rentrant **avec** et **sans** l'arrêt (le 982 k de référence) ;
- l'**amendement `DT-14`** : HP ne vole pas l'ascension ; foyer de `c` = résolveur, non
  factory.

---

## 5. Fermeture — trois preuves

1. **Bit-exact drag-off — les quatre gates + un test structurel.**
   `CentralBodyBaselineTest`, `MissionPolylineBaselineTest`, `EarthOrbitNonRegressionTest`,
   `AscentBaselineN2Test` verts à tolérance `0.0`. Plus un **test structurel** : un
   propagateur drag-off arme exactement les détecteurs d'aujourd'hui — le `ReentryDetector`
   −50 km, **pas** d'arrêt descente-gardé — parce que `armQuiet(FlightContext)` avec
   `hasDrag()` faux prend le chemin inchangé. C'est ce test qui *démontre* le « au bit près »
   sur l'arming ; les gates le confirment. Procédure `cleanTest` + gates isolés
   ([`../bugs.md` BUG-7](../bugs.md), risque transverse du découpage §7).

2. **Ascension drag-on terminante — test gated + coût consigné.** Un test gated : une
   ascension NRLMSISE drag-on atteint le hand-off en **pas bornés, sans throw**. Le **coût
   compute** de l'optimisation — le juge `a`/`c` — est une **mesure consignée**, lancée par
   l'utilisateur (lent), non assertée.

3. **Candidat qui retombe coupé à coût faible = `BUG-10` fermé — test ciblé.** Une
   trajectoire drag-on qui rentre est **stoppée par le détecteur descente-gardé** au plancher
   (pas bornés, non 982 k), et son coût est la pénalité graduée existante. C'est *la*
   fermeture de `BUG-10` : la garde se déclenche enfin sous traînée.

---

## 6. Tranché en `L1` par mesure (2026-09-11)

- **`a` retenu, `c` reporté à `L3`.** La preuve 2 a fait tourner l'optim drag-on du Falcon
  Heavy LEO-400 analytique sous NRLMSISE : **elle termine** (le socle tient) en **52,8 s**,
  contre **~7 s** drag-off sur le même profil — **×7,5**. Décisif : c'est le chemin
  analytique, donc ces 52,8 s sont le **gravity turn seul**, et l'ascension est NRLMSISE-forcée
  dans `a` **comme dans** `c` (HP lève à 0 km) — `c` donnerait les mêmes 52,8 s. On fige donc
  **`a`** (zéro câblage). La seule valeur de `c` — couper les mauvais candidats de *transfert*
  sous HP (Axe 2) — n'est pas exerçable avant que `BUG-25` soit levé en `L3` ; elle s'y reporte.
- **Les contingences de §3.1 — non écrites, non nécessaires.** La preuve 2 a terminé sans
  coupe au périgée osculateur ni pas-max : l'arrêt descente-gardé à 0 km a suffi à borner les
  candidats. À ne rouvrir que si un profil futur en montre le besoin.

**Ce que la mesure a fait apparaître, et qui n'est pas de `L1` :** le ×7,5 est le coût de
l'ascension **nominale** sous NRLMSISE, pas un effet de mauvais candidat — intrinsèque, car
NRLMSISE est le seul modèle valide à 0 km et l'ascension traverse l'air dense en pas nombreux.
Ni `a`/`c` ni le pas-max ne l'abaissent. Le **+5 %/+50 %** annoncé par la roadmap est donc
faux pour l'ascension : c'est un **risque pour `L5`** (défaut-on, chaque optim paierait le
×7,5), qui demanderait un **levier neuf** — une atmosphère bon marché pour l'optim et NRLMSISE
au runtime, ou une étude de tolérance d'intégrateur — du design au-delà de ce lot, avant `L5`.

Le reste — l'arming, la forme du détecteur, le foyer du câblage, la fermeture — est arrêté
ci-dessus. **`L1` est livré** : socle terminant (preuve 2), `BUG-10` fermé (preuve 3), drag-off
au bit près (preuve 1).

---

## 7. Ce que cette conception corrige au découpage

Trois points de **forme**, aucun de fond ([`06-decoupage-PHY-2.md`](06-decoupage-PHY-2.md)
§3.2/§3.3) :

| § du découpage | Ce qu'il disait | Ce que le code impose |
|---|---|---|
| §3.2 | « détecteur d'arrêt **+** pénalité de coût », deux livrables neufs | la pénalité **existe déjà** (`GravityTurnProblem` 264-277) ; seul l'arrêt précoce est neuf |
| §3.2 | un arrêt « au-dessus de la profondeur de cession » | un plancher sphérique **ne peut pas** être relevé (contrainte pas de tir) ; l'arrêt est **descente-gardé** |
| §3.3 / `DT-14` | substituer NRLMSISE→HP « dans la factory d'optimisation » | la factory est uniforme ; le foyer d'un choix **par phase** (`c`) est le **résolveur par étage** |

---

*Document rédigé le 2026-09-11, après exploration du code et séance de conception en
conversation.*
