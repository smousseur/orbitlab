# Backlog OrbitLab — items sans créneau

**Registre des items identifiés mais non planifiés dans une version.** Comme
[`bugs.md`](bugs.md), [`dette-technique.md`](dette-technique.md) et
[`reliquats.md`](reliquats.md), ce fichier est **une source**, pas un
ordonnancement : l'index [`roadmap/00-index.md`](roadmap/00-index.md) y renvoie
et n'en recopie rien. Un item qui gagne un créneau quitte ce fichier pour la
roadmap de sa version, en gardant son identifiant.

Notation identique à celle de l'index (★ valeur, ◆ difficulté, taille S/M/L/XL).

---

## Items fichés

### RND-5 — Repère d'affichage inertiel / tournant — ★2 ◆2 S

**Sorti de v2 le 2026-09-15.** Une bascule globale à deux valeurs dans le menu
applicatif, défaut inertiel : en repère tournant, la trace d'une mission repart
du pas de tir et s'y ancre, au prix de la lecture orbitale (l'ellipse devient un
enroulement). Le repère tournant est **physiquement exact** — c'est la trace
ITRF, là où le vaisseau est réellement au-dessus du sol ; sa faiblesse n'est pas
la justesse mais la valeur.

**Pourquoi il quitte v2.** Trois raisons, toutes déjà écrites dans sa propre
spec :

- sa valeur n'est pas mesurée à l'écran
  ([`graphics-effects/trajectory-display-frame.md`](graphics-effects/trajectory-display-frame.md)
  §9) — un ★2 non vérifié ;
- l'intitulé de menu devient faux le jour où une mission lunaire arrive (§9), et
  le repère body-fixed d'un corps non terrestre n'a pas de lecture naturelle ;
- il est **probablement subsumé** par une vraie trace au sol : §8 la nomme
  « l'autre réponse au même besoin », et §9 note que « si la trace au sol est
  livrée un jour, une partie du besoin qui motive cette bascule disparaît ».

**Condition de réouverture.** Si `RND-6`/`RND-9` sont livrés et qu'un besoin de
comparer la lecture orbitale (inertielle) et la lecture au sol persiste à
l'usage.

**Spec.** [`graphics-effects/trajectory-display-frame.md`](graphics-effects/trajectory-display-frame.md)
(inchangée ; l'identifiant `RND-5` reste stable et ses renvois croisés restent
valides).

---

### RND-6 — Trace au sol (globe 3D) — ★3 ◆2 M

**Sorti de v2 le 2026-09-15.** La projection sur la surface du corps central des
sommets déjà exprimés en repère lié au corps — un ruban de plus (`RND-4` a livré
la primitive), plaqué au sol à altitude nulle, suivant la même sémantique passé /
futur que la trajectoire.

**Pourquoi il quitte v2.** Son seul aval de version, `MIS-10`, ne le consomme
plus : la rentrée se termine désormais sur une **borne d'altitude + un marqueur
de point d'impact** (projection géodésique d'un point unique), pas sur un ruban
de trace au sol (voir [`roadmap/02-roadmap-v2.md`](roadmap/02-roadmap-v2.md)
fiche `MIS-10`). Il garde une valeur propre — vérifier visuellement la couverture
des profils SSO / polaire de `MIS-7`, déjà livré — mais cette valeur attend avec
lui.

**Un piège connu, déjà payé une fois.** Le pôle inertiel n'est pas le pôle
terrestre — 0,145° d'écart en 2026 — et une inclinaison de 90° en GCRF éloigne
visiblement la trace au sol du pôle. La trace se calcule en repère **lié à la
Terre** (ITRF), jamais en projetant naïvement une orbite inertielle.

**Couplé à `RND-9`.** Trace au sol 3D et planisphère 2D consomment la même
donnée (sommets en repère lié au corps) : à concevoir ensemble le jour où l'un
des deux gagne un créneau.

**Ce qu'on ne fait pas.** Pas de cercle de visibilité, pas d'empreinte de
capteur, pas de trace au sol pour les corps autres que le corps central de l'arc
courant.

---

### RND-9 — Trace au sol sur planisphère 2D — *(neuf, 2026-09-15)*

**Pourquoi.** La trace au sol d'une mission en orbite terrestre se lit sur une
carte, pas sur un globe : c'est la place standard du domaine — le tableau des
usages de [`graphics-effects/trajectory-display-frame.md`](graphics-effects/trajectory-display-frame.md)
§2 la range « presque toujours sur une carte 2D séparée ». Un planisphère
équirectangulaire montre la couverture, les stations, le repassage à heure solaire
constante d'une SSO, d'un seul coup d'œil.

**Un revirement assumé.** La spec `RND-5` §2 affirme : « OrbitLab n'a pas de carte
2D et n'en veut pas : la trace 3D enroulée est notre substitut de carte. » Ce
ticket **renverse délibérément** ce parti-pris. La raison : la trace 3D enroulée
(`RND-5`) s'est révélée non mesurée et probablement peu lisible, et la pratique du
domaine — le même §2 — soutient la carte 2D. À réaffirmer dans la conception, pour
ne pas ré-ouvrir le débat.

**Couplé à `RND-6`.** Même donnée que la trace au sol 3D (sommets en repère lié à
la Terre / ITRF) ; les deux se conçoivent ensemble.

**Périmètre.** Missions dont le corps central est la **Terre** uniquement — un
planisphère n'a pas de sens pour un arc lunaire ou héliocentrique.

---

## Backlog non planifié

Gardé hors versions, à remonter si le besoin se manifeste :

- **Rendu** — god-rays, normal maps, lumières de villes côté nuit, halo
  atmosphérique Fresnel, ombres portées, enveloppe d'incertitude autour du
  nominal. *(L'ombre du corps sur ses anneaux a quitté cette liste le
  2026-09-03 : elle est devenue `FX-5` — les maillages d'anneaux existant déjà,
  il ne restait de cette entrée que l'ombre. La trace au sol a ses fiches propres
  ci-dessus, `RND-6` et `RND-9`.)*
- **Profondeur** — les options écartées deux fois par `PHY-4` (troisième
  viewport « mid », depth buffer logarithmique, reverse-Z). *Ce n'est plus
  vraiment du backlog : c'est `RND-8`, avec sa condition de réouverture.*
- **Missions** — Molniya / HEO, déploiement de constellation, points de
  Lagrange, interplanétaire, gravity assist, désamarrage et retour depuis une
  station. *Le déorbitage et la rentrée ont quitté cette liste : `MIS-10`.*
- **Rentrée, troisième palier** — désintégration réelle (flux thermique,
  ablation, fragmentation). C'est de la R&D ; `PHY-1 / L1` §4 note que
  l'approximation « panneaux repliés » du coefficient balistique cesse d'être
  vraie avant même le flux thermique.
- **Plateforme** — mode batch headless, analytics et graphes post-mission,
  replays cinématiques, catalogue de débris TLE, import de TLE arbitraire (voir
  la question ouverte n° 1 de [v4](roadmap/04-roadmap-v4.md)), scripting.
- **Optimiseur** — mode CMA-ES pour la composition GEO (les 3 modes composent
  aujourd'hui la même `GEOMission` analytique ; seul le levier ergols agit
  réellement sur GEO).

Détail et notation dans
[`brainstorm/features-long-terme.md`](brainstorm/features-long-terme.md) et
[`brainstorm/missions.md`](brainstorm/missions.md).

---

## Fichés ailleurs, tirés à la demande

Items sans créneau qui vivent dans une roadmap de version parce que leur contexte
y est — référencés ici, jamais recopiés :

- **`OPT-2`** — temps de calcul des trajectoires (suite d'`OPT-1`, cible
  transfert BALANCED/PRECISE). Fiche en
  [`roadmap/02-roadmap-v2.md`](roadmap/02-roadmap-v2.md) §4.
- **`PHY-9`** — courbe `Q(t)` et sélecteur de fidélité atmosphère (moitié
  visible re-carvée de `PHY-3`). Fiche en
  [`roadmap/02-roadmap-v2.md`](roadmap/02-roadmap-v2.md) §4.
- **`MIS-9`** — éphéméride de mission hors mémoire, conditionnel. Fiche complète
  en [`roadmap/04-roadmap-v4.md`](roadmap/04-roadmap-v4.md) §6 avec ses
  conditions de déclenchement.
