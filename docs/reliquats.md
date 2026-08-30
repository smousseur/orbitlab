# docs/reliquats.md — registre des reliquats de chantier

Ce registre couvre ce qui n'est ni un bug comportemental (`bugs.md`) ni de la
dette de code (`dette-technique.md`) : des décisions volontairement reportées,
des hypothèses non vérifiées, des limitations assumées à la clôture d'un
chantier. Chaque item vient d'un document de conception existant sous `docs/`
et n'y est pas re-démontré — la fiche ici renvoie à la section source pour le
mécanisme complet, et se limite au constat et à pourquoi c'est encore ouvert.

**Convention.** `REL-n` dans l'ordre de découverte, jamais réattribué. Un item
sort d'ici traité, promu en item de roadmap, ou requalifié en « accepté » avec
la raison — comme dans `bugs.md` et `dette-technique.md`.

**Origine de cette revue.** Établi le 2026-08-30 par une lecture complète de
`roadmap/01-roadmap.md` et des documents de conception par chantier, en
préparation d'une passe de robustesse avant la Phase 5 (`MIS-6`). Voir
`bugs.md` (`BUG-9` à `BUG-18`) et `dette-technique.md` (`DT-12` à `DT-17`)
pour ce qui a été promu hors de cette revue plutôt que laissé ici.

---

## Rendu et effets

### REL-1 — Raccord terminal du ruban de trajectoire, sans propriétaire

Le dernier segment du ruban (`RND-4`) pivote d'environ 2° à chaque pas
d'échantillonnage. Déjà reversé une fois de `RND-1` à `RND-4` faute de
propriétaire ; `RND-4` a traité les jointures *entre* segments mais rien sur
le pivotement du segment de tête. Toujours sans identifiant.
Source : `roadmap/01-roadmap.md` fiche `RND-1` ; confirmé non traité dans
[`graphics-effects/ribbon-lines.md`](graphics-effects/ribbon-lines.md) §13.

### REL-2 — `MUTING_STEP` du ruban, réglage à l'œil jamais fait

Le contraste de couleur entre phases (`RND-3`) doit se régler à l'œil sur un
trait de 3,5 px, l'obstacle technique (ligne GL à 1 px) étant levé par
`RND-4`. Le réglage lui-même n'a pas été fait.
Source : `roadmap/01-roadmap.md` fiche `RND-3`.

### REL-3 — Ruban : fondu alpha et largeurs de départ jugés à l'œil seulement

La cohabitation du fondu alpha avec `FilterPostProcessor` (spec §7.7,
« seul inconnu technique du lot ») et les largeurs de départ (2,5 px orbite /
3,5 px trajectoire) ne sont validées que par une session de 24 minutes sans
exception au log — aucun critère chiffré, aucun test.
Source : [`graphics-effects/ribbon-lines.md`](graphics-effects/ribbon-lines.md) §13.

### REL-4 — Tone mapping du halo solaire (`FX-1`), toujours ouvert

Le gain de §4.5 de l'ancien `effects-roadmap.md` porte sur le reste de la
scène, pas sur le halo lui-même. N'apparaît plus dans le backlog courant de
la roadmap — orphelin plutôt que fermé.
Source : `roadmap/01-roadmap.md` fiche `FX-1`.

### REL-5 — Pénombre du vaisseau peu lisible (`FX-2`), limitation connue

Le vaisseau (50 m) traverse toute la pénombre terrestre d'un bloc, sans
dégradé perceptible — contrairement à un grand corps. Trois pistes
identifiées (ambiant proportionnel à l'éclipse, courbe non linéaire sur le
facteur d'éclipse, plancher de luminosité perçue), **aucune essayée**.
Explicitement titré « limitation connue, non résolue » dans la source.
Source : `roadmap/01-roadmap.md` fiche `FX-2`.

### REL-6 — Distinction éclipse totale/annulaire jamais vérifiée à l'écran

Signalée comme non garantie tant qu'elle n'est pas observée, au moment de la
conception. Aucun document ultérieur du dépôt ne confirme qu'elle l'a été.
Source : [`eclipses/01-decoupage.md`](eclipses/01-decoupage.md) §5 pt4.

### REL-7 — Le near viewport ne montre jamais deux corps 3D à la fois

Limite structurelle : une seule origine, un seul corps non culé à la fois.
Confirmée inchangée après le premier arc lunaire réel de `PHY-4`, renvoyée
explicitement à `MIS-6`. Voisine de `BUG-5` (pop au changement de focus) mais
distincte : `BUG-5` porte sur la transition, ceci sur l'impossibilité
structurelle d'une cohabitation, transition ou pas.
Source : [`multi-corps/07-conception-L5.md`](multi-corps/07-conception-L5.md)
§1.1-1.4 ; [`multi-corps/08-conception-L6.md`](multi-corps/08-conception-L6.md) §12.5.

---

## Missions lunaires (`MIS-4`, `MIS-5`)

### REL-8 — Offset de visée TLI mono-dimensionnel

La correction de visée du survol lunaire ne joue que sur une dimension de
l'écart ; à certaines géométries le périlune volé a un plancher physique
mesuré à **132 km** au-dessus de la cible. Chercher aussi la direction de
visée est identifié comme un lot à part, non ouvert. Marqué « reste ouvert,
sciemment » dans la source.
Source : `roadmap/01-roadmap.md` fiche `MIS-4`.

### REL-9 — `ParkingCoastStage` ne gère pas un coast plus court que `ignitionLead`

Distinct de `BUG-9` (qui est le test resté sur l'ancienne sémantique) : ceci
est un trou fonctionnel dans la classe elle-même pour le cas d'un coast plus
court que le délai d'allumage.
Source : `roadmap/01-roadmap.md` fiche `MIS-4`.

### REL-10 — Paramètres TLI du wizard non balayés conjointement

ToF (4 j), altitude de parking (400 km) et angle de transfert (170°) sont des
constantes du wizard dont le domaine de convergence conjoint n'a jamais été
mesuré. La bande de périlune visée (50-500 km) n'a été testée que sur un cas
unique flown ; un refus mesuré montre un plancher atteignable jusqu'à
**1 873 km** selon l'époque.
Source : [`lunar-flyby/07-conception-L5.md`](lunar-flyby/07-conception-L5.md) §8 pts 1, 3.

### ~~REL-11~~ — Gel du thread de rendu de 10-15 s à la création d'une mission lunaire — **RÉSOLU**

> Corrigé par le fix principal dans `MissionWizardAppState`, révision
> `9e3b8ad6b38be6c3d930bbeedd5940ac9287ed2b`. La classe pose désormais
> `MissionStatus.CREATING` de façon synchrone dès la validation du wizard : la
> mission apparaît immédiatement dans la liste du panel de management, et ses
> actions restent bloquées (`MissionRow.editableStatus` exclut `COMPUTING` et
> `CREATING`) tant que la création n'est pas terminée. Le statut bascule
> ensuite en `DRAFT`. Effet immédiat pour la plupart des missions ; ~20 s en
> `CREATING` pour les missions lunaires — le calcul lui-même n'a pas changé de
> durée, c'est le thread de rendu qui n'est plus bloqué pendant.
>
> Vérifié encore présent à `HEAD` le 2026-08-30 : `MissionStatus.CREATING`,
> `MissionStatusColor.CREATING` et le garde-fou de `MissionRow` existent tous
> les trois.
>
> **Nuance non éclaircie.** `docs/lunar-flyby/07-conception-L5.md`, écrit dans
> ce même commit, liste pourtant ce gel comme une « limitation assumée » (§8
> pt 2) et le lègue explicitement à la clôture du chantier (§10) plutôt que de
> le documenter comme résolu. Le correctif et le constat cohabitent dans le
> même commit sans que le second ne référence le premier — a priori le
> document décrit l'état *avant* le dernier ajustement de `MissionWizardAppState`
> dans ce commit, mais ça n'a pas été retracé précisément. Sans conséquence
> pour la clôture de cet item : le comportement à `HEAD` est celui décrit
> ci-dessus, mesuré directement dans le code.

Contre ~40 ms pour une mission terrestre, à l'origine. Connu, traité.
Source : [`lunar-flyby/07-conception-L5.md`](lunar-flyby/07-conception-L5.md) §8 pt 2.

### REL-12 — Le wizard lunaire ne fait qu'un écran de faisabilité

Il ne confirme jamais réellement la faisabilité : un refus de périlune ou de
déplétion ne surgit qu'à la validation de l'étape suivante, ou plus tard
comme une mission `FAILED`.
Source : [`lunar-flyby/07-conception-L5.md`](lunar-flyby/07-conception-L5.md) §8 pt 1.

### REL-13 — Aucun CMA-ES sur l'injection translunaire (TLI)

Lambert + correction fermée (secante/bissection) seulement, jamais optimisé.
Explicitement reporté à `MIS-6`.
Source : [`lunar-flyby/01-decoupage.md`](lunar-flyby/01-decoupage.md) §6 pt 5,
confirmé inchangé dans [`multi-corps/08-conception-L6.md`](multi-corps/08-conception-L6.md).

### REL-14 — Vol de clôture de `MIS-5` jamais lancé

`LunarOrbitFlightTest` (derrière `orbitlab.slowTests`) et l'essai manuel de
fin de phase restent à la charge de l'utilisateur, jamais exécutés à ce jour
selon la source.
Source : [`lunar-orbit/09-conception-L7.md`](lunar-orbit/09-conception-L7.md) §9.

### REL-15 — Horizon par défaut du wizard GEO sous-estime la durée totale d'environ 7 %

Le calcul par défaut ne compte pas la branche de transfert GTO. Mesuré,
sciemment non corrigé pour ne pas déplacer la bande mesurée par les tests
existants.
Source : [`lunar-orbit/09-conception-L7.md`](lunar-orbit/09-conception-L7.md) §3.1, §7.

### REL-16 — Refus de masse sèche de charge utile lunaire jamais testé automatiquement

Chemin de refus atteignable manuellement, seul un contrôle manuel était
prévu — jamais un test automatisé.
Source : [`lunar-orbit/09-conception-L7.md`](lunar-orbit/09-conception-L7.md) §8 risque 1.

---

## Terre paramétrable (`MIS-7`)

### REL-17 — `T1b` (inclinaison après insertion complète) jamais écrit

Seul `MeoMissionTest` en donne un équivalent partiel, pour le seul profil
MEO. Confirmé par recherche dans le code : le nom n'existe que dans la
documentation.
Source : [`earth-orbit/01-mission-terre-parametrable.md`](earth-orbit/01-mission-terre-parametrable.md) §11.

### REL-18 — Étalement de ~19 km dans l'ensemble « acceptable » du CMA-ES pour LEO

Repéré en re-capture pendant `MIS-7`, signalé comme méritant sa propre
investigation. Confirmé encore vrai le 2026-08-16, après la clôture de
`MIS-7` — c'est le plancher de bruit de toute comparaison de références sur
ce profil.
Source : mémoire de session `mis7-earth-orbit-measures` /
`i7-maxstep-analytic-stages-followup`, et
[`earth-orbit/01-mission-terre-parametrable.md`](earth-orbit/01-mission-terre-parametrable.md) §11.

### REL-19 — Branche de nœud (ascendant/descendant) non exposée au wizard

Toute mission part actuellement sur `ASCENDING`. Déféré délibérément.
Source : [`earth-orbit/02-wizard-orbites-terrestres.md`](earth-orbit/02-wizard-orbites-terrestres.md) §7.

### REL-20 — Horizon MEO par défaut à 48 révolutions (~24 j), perfectible

Auto-décrit comme « honnête mais perfectible » — le changer déplacerait la
bande mesurée par `MeoMissionTest`. À ne pas confondre avec l'horizon LEO
(également « 48 révolutions », mais ≈ 3,2 j à 550 km) : deux constantes
distinctes qui partagent la même valeur nominale par coïncidence, pas le
même défaut.
Source : [`earth-orbit/02-wizard-orbites-terrestres.md`](earth-orbit/02-wizard-orbites-terrestres.md) §7.

---

## UI, panel, timeline

### REL-21 — Annulation d'un calcul en cours, à moitié faite (`UI-2`)

Le `Future` que l'orchestrateur possède est aujourd'hui jeté plutôt que
retenu ; il faudrait le garder et lire un drapeau dans la fonction objectif,
à côté de `crossRunStop` qui existe déjà pour un autre usage.
Source : `roadmap/01-roadmap.md` fiche `UI-2`.

### REL-22 — Restauration de scénario refusée si atmosphère ≠ `NONE` (`UI-3`)

Limitation actuelle, déjà en production : un scénario sauvegardé avec un
modèle d'atmosphère non nul ne peut pas être rechargé aujourd'hui — rien ne
peut le remonter avant `PHY-2`.
Source : [`scenario/01-persistance-missions.md`](scenario/01-persistance-missions.md).

### REL-23 — Tests longs et essai manuel de `UI-3` jamais lancés

`ScenarioReplayTest` (opt-in) et l'essai manuel de fin de phase, seul juge
réel de clôture, restent non exécutés.
Source : [`scenario/01-persistance-missions.md`](scenario/01-persistance-missions.md).

### REL-24 — Section interaction de `MissionTimelineWidget` à extraire

Le widget est passé de 689 à 842 lignes au fil de `NAV-2`/`NAV-3` ; sa
section interaction aurait sa place en collaborateur séparé, à côté de
`PhaseBar`/`PhaseMarkers`/`TimelineTooltip`.
Source : `roadmap/01-roadmap.md` fiche `NAV-3`.

### REL-25 — Breadcrumb : aucun test unitaire sur la chaîne d'ancêtres

La logique de modèle pur (quel segment est cliquable) n'a aucun test alors
qu'elle se prêterait au même traitement qu'`AppMenuModelTest`.
Source : `roadmap/01-roadmap.md` fiche `NAV-4`.

### REL-26 — Breadcrumb : clic sur segment et cas `SPACECRAFT` jamais vérifiés à l'écran

Faute de souris scriptable pour le protocole d'observation manuelle — les
autres profondeurs ont été capturées, celles-ci non.
Source : `roadmap/01-roadmap.md` fiche `NAV-4`.

### REL-27 — Breadcrumb : descente vers les fils reportée en V2

Déjà su au moment de la conception, listé ici pour l'exhaustivité du
registre plutôt que comme découverte.
Source : `roadmap/01-roadmap.md` fiche `NAV-4`.

### REL-28 — Auto-optimisation après création de mission, jamais tranchée

Le préalable technique (l'indicateur de progression, `UI-2`) est levé depuis
le 2026-08-21, mais la décision produit — déclencher ou non un calcul
automatique à la création d'une mission — n'a jamais été prise.
Source : `roadmap/01-roadmap.md` §8, question 3.

### REL-29 — Persistance des bascules d'affichage, jamais tranchée malgré son jalon passé

Le texte de la roadmap dit explicitement « à trancher au moment d'`UI-3` » ;
`UI-3` est clos depuis le 2026-08-21 et sa fiche ne mentionne nulle part
cette décision.
Source : `roadmap/01-roadmap.md` §8, question 6.

---

## Optimiseur (`docs/optimization/bilan.md`)

### REL-30 — Hypothèse survivante : notation sur éléments osculateurs contre cible moyenne

Le coût du transfert grade des apsides osculatrices contre une cible
implicitement en éléments moyens ; sous J2 l'écart est structurel et
proportionnel — cohérent avec le biais relatif constant mesuré (rapport
apogée/périgée 2,79, une signature de forme). Correctif proposé (grader les
éléments moyens, probablement via une conversion Eckstein-Hechler par
évaluation) — coût même pas chiffré, rien tenté.
Source : [`optimization/bilan.md`](optimization/bilan.md), piste 1.

### REL-31 — Exemption « boîte élargie » de la règle de retry, trou connu non couvert

Si la cascade descend **et** que le problème élargit sa boîte au retry
(saturation β1 réelle), la règle actuelle peut sauter un retry qui avait du
terrain neuf à explorer. Jamais déclenché sur les 7 λ mesurés à ce jour —
non vérifié en pratique, pas seulement en théorie.
Source : [`optimization/bilan.md`](optimization/bilan.md), piste 5.

### REL-32 — `ABS_ERR_SCALE = 50 km` mal dimensionné pour la LEO

Fait de `apoAbs` le premier terme du coût (47,8 %) pour une erreur qui ne
vaut que 1,4 % en relatif. À réexaminer en même temps que `REL-30`, dont
c'est le multiplicateur.
Source : [`optimization/bilan.md`](optimization/bilan.md), piste 3.

---

## Note de lecture

`REL-13` (aucun CMA-ES sur le TLI) et `BUG-11`/`BUG-12` (coasts sautés par
l'optimiseur, ε jamais calibrée) sont liés sans être identiques : les trois
tiennent au même défaut de fond — l'optimiseur et le vol réellement rejoué
divergent dès que la trajectoire sort du gabarit terrestre simple — mais
`BUG-11`/`BUG-12` sont des défauts mesurés avec un mécanisme identifié, alors
que `REL-13` est un scope non fait. Les regrouper au moment de l'arbitrage a
du sens ; les fiches restent séparées parce que leur nature (bug vs scope) et
leur registre diffèrent.
