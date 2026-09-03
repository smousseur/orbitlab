# Roadmap OrbitLab — index

**Porte d'entrée du dossier `docs/`.** Le plan est découpé par **version** ; ce
document dit laquelle porte quoi, et rien d'autre. Chaque roadmap de version est
autonome : elle contient son plan, ses fiches d'items et ses questions ouvertes.

| Document | Version | En une ligne | État |
|---|---|---|---|
| [`01-roadmap-v1.md`](01-roadmap-v1.md) | **v1.X.X** | Le système solaire, les missions Terre / GEO / lunaires | Livrée ; **1.1.X** close à 1.1.1, **1.2.0** ouverte (vue rapprochée et anneaux) |
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

Trois items qui figuraient « hors phases » (`RND-5`, `UI-6`, `UI-7`) sont
désormais phasés : leur coût augmente avec le temps, ce qui est un argument pour
les planifier et non pour les laisser flotter.

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
| **1.2.0** | `BUG-22` | Icônes des corps derrière la caméra, dessinées en miroir | — | — | S |
| **1.2.0** | `BUG-2` | Sauts de la skybox au zoom, en vue solaire | — | — | S |
| **1.2.0** | `BUG-1` + `BUG-5` | Jitter de la transition et pop du modèle — un seul changement | — | — | M |
| **1.2.0** | `BUG-20` | Anneaux à remettre dans le plan équatorial *(hors code)* | — | — | S |
| **1.2.0** | `FX-5` | Ombre de la planète sur ses anneaux | 3 | 2 | S |
| **v2** | `AST-1` | Lot d'assets 3D *(hors code)* | — | — | — |
| **v2** | `PHY-2` | Atmosphère par défaut + recalibrage optimiseur | 5 | 4 | L |
| **v2** | `PHY-3` | Détecteurs MaxQ, télémétrie, UI de fidélité | 3 | 2 | M |
| **v2** | `RND-5` | Repère d'affichage inertiel / tournant | 2 | 2 | S |
| **v2** | `RND-6` | Trace au sol | 3 | 2 | M |
| **v2** | `MIS-10` | Déorbitage contrôlé et rentrée atmosphérique | 5 | 3 | M |
| **v2** | `PHY-5` | Machinerie multi-objets + étages largués | 4 | 3 | L |
| **v2** | `PHY-6` | Charge utile comme objet distinct | 4 | 2 | M |
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
  PHY-2 ──┬── MIS-10 ──── MIS-11        (v3 : la rentrée finale d'Artemis)
          ├── PHY-5  ──── PHY-6 ──┬──── MIS-11  (v3 : la capsule qui rentre)
          │                       └──── MIS-12  (v4 : l'objet qui s'amarre)
          └── FX-4   (via MIS-10)
  PHY-3 ──── MIS-10
  RND-5 ──── RND-6 ──── MIS-10
  AST-1 ──┬── PHY-5
          ├── PHY-6
          └── DT-12  (maillage Ariane 6)

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
d'un lanceur, et sans elle ni `MIS-11` ni `MIS-12` n'ont de sujet. Ce sont les
deux items dont un retard décale une version entière.

**`AST-1` n'est pas du code**, et c'est justement pourquoi il est dans le
graphe : quatre items l'attendent, et un approvisionnement de maillages se
mesure en délai, pas en jours de travail.

---

## Backlog non planifié

Gardé hors versions, à remonter si le besoin se manifeste :

- **Rendu** — god-rays, normal maps, lumières de villes côté nuit, halo
  atmosphérique Fresnel, ombres portées, enveloppe d'incertitude autour du
  nominal. *La trace au sol a quitté cette liste : elle est devenue `RND-6` ;
  l'ombre du corps sur ses anneaux, `FX-5`, le 2026-09-03 — les maillages
  d'anneaux existant déjà, il ne restait de cette entrée que l'ombre.*
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
  la question ouverte n° 1 de [v4](04-roadmap-v4.md)), scripting.
- **Éphéméride hors mémoire** — `MIS-9`, fiche complète en
  [v4](04-roadmap-v4.md) §6 avec ses conditions de déclenchement.
- **Optimiseur** — mode CMA-ES pour la composition GEO (les 3 modes composent
  aujourd'hui la même `GEOMission` analytique ; seul le levier ergols agit
  réellement sur GEO).

Détail et notation dans
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
