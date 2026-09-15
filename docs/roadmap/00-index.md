# Roadmap OrbitLab — index

**Porte d'entrée du dossier `docs/`.** Le plan est découpé par **version** ; ce
document dit laquelle porte quoi, et rien d'autre. Chaque roadmap de version est
autonome : elle contient son plan, ses fiches d'items et ses questions ouvertes.

| Document | Version | En une ligne | État |
|---|---|---|---|
| [`01-roadmap-v1.md`](01-roadmap-v1.md) | **v1.X.X** | Le système solaire, les missions Terre / GEO / lunaires | Livrée ; **1.1.X** close à 1.1.1, **1.2.0** close le 2026-09-05 |
| [`02-roadmap-v2.md`](02-roadmap-v2.md) | **v2.X.X** | Réalisme : l'atmosphère, les objets qui se séparent, le retour sur Terre | À faire |
| [`03-roadmap-v3.md`](03-roadmap-v3.md) | **v3.X.X** | Le retour de la Lune, et la maturité de l'interface | À faire |
| [`04-roadmap-v4.md`](04-roadmap-v4.md) | **v4.X.X** | Rendez-vous orbital et amarrage | À faire |
| [`05-roadmap-technique.md`](05-roadmap-technique.md) | toutes | Ordonnancement de la dette, des bugs et des reliquats | Vivant |

Les fiches de dette vivent dans [`bugs.md`](../bugs.md),
[`dette-technique.md`](../dette-technique.md) et
[`reliquats.md`](../reliquats.md). Ces trois registres sont **la source** ;
`05-roadmap-technique.md` n'en est que l'ordonnancement, et les sections
« dette et robustesse » des roadmaps de version n'en sont que le rappel. Une
divergence se tranche toujours en faveur du registre.

---

## Comment ce découpage a été décidé

Le document d'origine portait **sept phases** pour une version unique. Les
quatre premières sont livrées ; les trois autres ne l'étaient pas, et deux
raisons ont fait éclater le reste :

1. **L'ordre des deux dernières phases était inversé.** Le rendez-vous
   (ancienne phase 5) précédait le réalisme (ancienne phase 6). Or on ne
   s'amarre pas avec un lanceur entier : la séparation de charge utile, qui vit
   dans le réalisme, est le préalable du rendez-vous. Les deux phases ont donc
   échangé leur place.
2. **La plus grosse phase ne pouvait pas partager une version.** `MIS-6` est le
   seul ◆5 et le seul XL du corpus ; son refactor central touche un symbole
   présent dans 77 fichiers, et 21 fiches techniques sont ordonnancées sur lui.
   Tout ce qui aurait partagé sa version aurait glissé. Elle est seule.

Deux items qui figuraient « hors phases » (`UI-6`, `UI-7`) sont désormais
phasés : leur coût augmente avec le temps, ce qui est un argument pour les
planifier et non pour les laisser flotter. (`RND-5` l'avait été aussi, en paire
avec la trace au sol ; les deux sont repartis au [backlog](../backlog.md) le
2026-09-15.)

---

## Notation

Reprise du document d'origine, inchangée d'une version à l'autre.

- **★ Valeur** — apport perçu dans l'application, tous publics confondus
  (lisibilité, réalisme, spectacle, déblocage d'autres features).
  ★1 = à peine perceptible → ★5 = change ce qu'OrbitLab *est*.
- **◆ Difficulté** — ◆1 = quelques lignes localisées → ◆5 = R&D, refonte d'un
  sous-système, plusieurs semaines.
- **Taille** — S (< 1 j), M (1–3 j), L (1–2 semaines), XL (au-delà).
- Les identifiants (`RND-1`, `MIS-4`…) sont **stables** : les versions peuvent
  bouger, les identifiants non. Un identifiant retiré n'est jamais réattribué.

---

## Ce qui reste à faire, tous items confondus

Trié par version, puis par ordre d'exécution à l'intérieur. Les items livrés
sont dans [v1](01-roadmap-v1.md) §3 et §6.

| Version | ID | Item | ★ | ◆ | Taille |
|---|---|---|:-:|:-:|:-:|
| **1.1.X** | — | ~~toute la ligne~~ — **close le 2026-09-03** à 1.1.1 : `J0`, `BUG-19`, et `BUG-3` requalifié « accepté, avec raison » | — | — | — |
| **1.2.0** | — | ~~toute la ligne~~ — **close le 2026-09-05** : `BUG-1`, `BUG-2`, `BUG-5`, `BUG-22`, `BUG-20`, `FX-5`, plus `BUG-23` pris en cours de route. Deux fiches neuves en sont sorties, `BUG-23` (close) et `BUG-24` (ouverte) | — | — | — |
| **v2** | `AST-1` | Lot d'assets 3D *(hors code)* | — | — | — |
| **v2** | ~~`PHY-8`~~ | ~~Propulseurs séparés du corps : Falcon Heavy et Ariane 64~~ — **livré le 2026-09-10**, `L0` à `L6` ; bilan et réserves dans [`etagement/07-cloture.md`](../etagement/07-cloture.md) | — | — | — |
| **v2** | ~~`PHY-2`~~ | ~~Atmosphère par défaut + recalibrage optimiseur~~ — **livré le 2026-09-12**, `L0` à `L5` ; bilan, réserves et restes dans [`atmosphere/13-cloture-PHY-2.md`](../atmosphere/13-cloture-PHY-2.md) | — | — | — |
| **v2** | ~~`OPT-1`~~ | ~~Temps de calcul des trajectoires~~ — **livré le 2026-09-14** ([`optimization/13-cloture.md`](../optimization/13-cloture.md)) ; reliquat → `OPT-2` (backlog) | — | — | — |
| **v2** | ~~`PHY-3`~~ | ~~Bricks instrumentation atmosphère (interface Kármán + fonction Q)~~ — **livré le 2026-09-14** (commit `2969a86`) ; moitié visible → `PHY-9` (backlog) | — | — | — |
| **v2** | `PHY-5` | Machinerie multi-objets + étages largués | 4 | 3 | L |
| **v2** | `PHY-6` | Charge utile comme objet distinct | 4 | 2 | M |
| **v2** | `MIS-10` | Déorbitage contrôlé et rentrée atmosphérique | 5 | 3 | M |
| **v2** | `FX-3` | Particules de tuyère | 4 | 2 | M |
| **v2** | `FX-4` | Traînée plasma de rentrée | 3 | 2 | M |
| **v2** | `NAV-5` | Hover « wow » planètes + orbites | 3 | 2 | M |
| **v3** | `MIS-11` | Artemis : survol lunaire et retour en capsule | 5 | 4 | L |
| **v3** | `UI-6` | Fenêtres déplaçables, empilement par focus | 3 | 2 | M |
| **v3** | `UI-7` | Infobulles + socle de survol partagé | 3 | 2 | M |
| **v3** | `UI-8` | Préférences utilisateur persistées | 3 | 2 | M |
| **v3** | `PHY-7` | Validation contre une référence externe | 4 | 3 | M |
| **v4** | `MIS-6` | Rendezvous / phasing sur cible TLE | 5 | 5 | XL |
| **v4** | `RND-8` | Profondeur : deux corps dans le même cadre | 3 | 3 | M |
| **v4** | `RND-7` | Vue relative LVLH | 4 | 2 | M |
| **v4** | `MIS-12` | Approche terminale et amarrage | 5 | 4 | L |
| *aucune* | `MIS-9` | Éphéméride de mission hors mémoire — conditionnel, rattaché à [v4](04-roadmap-v4.md) | — | — | — |

---

## Graphe de dépendances inter-versions

Seules les arêtes qui **traversent une frontière de version** sont ici ; les
dépendances internes sont dans chaque roadmap.

```
v1 (livré)
  MIS-8 (horizon)     ✔ ──── MIS-11 (horizon de 10 jours)
  PHY-4 (multi-corps) ✔ ──── MIS-11 (troisième arc, retour)
  MIS-2 (fenêtres)    ✔ ──── MIS-6  (fenêtre de rendez-vous)
  MIS-4 (survol)      ✔ ──── MIS-11 (branche aller)
  PHY-1 (brique drag) ✔ ──── PHY-2

v2
  PHY-8 ✔ ── PHY-2                       (découper avant de calibrer)
  PHY-2 ──┬── PHY-5 ──── PHY-6 ──┬──── MIS-10 ──── MIS-11  (v3 : la capsule d'Artemis qui rentre)
          │                      └──── MIS-12          (v4 : l'objet qui s'amarre)
          └── FX-4   (via MIS-10)
  PHY-3 ──── MIS-10
  AST-1 ──┬── PHY-5
          ├── PHY-6
          └── PHY-8 ✔ ── DT-12 ✔ (maillage Ariane 64 ; DT-18 lui succède)

v3
  UI-6 ──── UI-7 ──── UI-8
  (PHY-7 n'a aucun amont et aucun aval)

v4
  MIS-6 ──┬── RND-7 ──── MIS-12
          ├── RND-8
          └── MIS-9  (conditionnel, à son tarif marginal)
```

**Deux arêtes commandent tout le reste.** `PHY-2` ouvre la moitié de v2 et toute
la queue jusqu'à v3 ; `PHY-6` est la seule chose qui sépare une charge utile
d'un lanceur, et sans elle `MIS-10` déorbite un lanceur entier, tandis que
`MIS-11` et `MIS-12` n'ont pas de sujet. Ce sont les deux items dont un retard
décale une version entière.

**Et `PHY-8` est en amont de la première.** Il ne débloque rien à lui seul, mais
il précède `PHY-2` pour une raison d'attribution : découper les étages sans
traînée, puis allumer la traînée et calibrer une seule fois, contre une
ascension de forme physique. Pris dans l'autre ordre, le calibrage de `PHY-2`
serait fait sur un agrégat solide / cryogénique qu'on s'apprête à supprimer,
donc à refaire.

**`AST-1` n'est pas du code**, et c'est justement pourquoi il est dans le
graphe : quatre items l'attendent, et un approvisionnement de maillages se
mesure en délai, pas en jours de travail.

---

## Backlog non planifié

Les items identifiés sans créneau vivent désormais dans le registre
[`backlog.md`](../backlog.md) : les fiches `RND-5`, `RND-6` (trace au sol 3D) et
`RND-9` (planisphère 2D), la liste « backlog non planifié » (rendu, profondeur,
missions, rentrée 3ᵉ palier, plateforme, optimiseur), et les pointeurs vers
`OPT-2`, `PHY-9` et `MIS-9`, qui restent fichés dans leur roadmap de version. Le
détail long terme est toujours dans
[`docs/brainstorm/features-long-terme.md`](../brainstorm/features-long-terme.md)
et [`docs/brainstorm/missions.md`](../brainstorm/missions.md).

---

## Entretien de ce dossier

- Une version livrée garde sa roadmap **telle quelle**, comme compte rendu.
  C'est ce que fait [v1](01-roadmap-v1.md) : ses phases barrées et datées valent
  plus que leur suppression.
- Un item qui change de version change de **fichier**, jamais d'identifiant.
- Ce document-ci ne décrit aucun item : il ne dit que dans quelle version il est
  et de quoi il dépend. Une fiche recopiée ici est une fiche qui divergera.
