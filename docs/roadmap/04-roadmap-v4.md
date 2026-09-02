# Roadmap OrbitLab v4.X.X — Rendez-vous et amarrage

Quatrième version, et la plus lourde du corpus. Elle porte l'ancienne **phase 5**
— le rendez-vous orbital — déplacée en dernier parce qu'elle en avait besoin :
s'approcher d'une station avec un lanceur entier n'a pas de sens, et c'est
`PHY-6` ([v2](02-roadmap-v2.md)) qui rend la charge utile pilotable.

Porte d'entrée du dossier : [`00-index.md`](00-index.md). Ce qui précède :
[v3](03-roadmap-v3.md). L'ordonnancement de la dette :
[`05-roadmap-technique.md`](05-roadmap-technique.md).

---

## 1. Ce que v4 change

**Une phrase.** Jusqu'ici toutes les missions visent une **orbite**. v4 est la
première à viser un **objet**, qui bouge, qu'on n'a pas mis là, et dont il faut
s'approcher assez pour s'y attacher.

**Cette version est seule dans sa taille, et c'est mesuré.**

- `MIS-6` est le **seul ◆5 et le seul XL** de tout le corpus.
- Son point le plus cher — décrocher l'API de l'enum `SolarSystemBody` pour lui
  substituer une abstraction `EphemerisTarget` — touche un symbole présent dans
  **77 fichiers et 460 occurrences** de `src/main`. Sa fiche appelait ça « le
  vrai coût de refactor du chantier » sans le chiffrer.
- **21 des fiches techniques du corpus** sont ordonnancées sur lui : cinq
  bloquantes, six préalables, dix d'accompagnement (§3).

C'est pour cette raison qu'elle est seule : toute autre chose partageant cette
version serait dominée par elle et glisserait.

**Et l'amarrage n'est pas dans `MIS-6`.** Sa spec (528 lignes) fixe le MVP à
*Δr < 10 km, ‖Δv_rel‖ < 10 m/s*, range **l'approche terminale HCW explicitement
hors MVP**, et la vue LVLH en *stretch*. Le contact, le corridor final, l'axe
d'amarrage n'apparaissent nulle part. C'est `MIS-12`, et sans lui la version
livre une démonstration qui s'arrête dix kilomètres avant son sujet.

---

## 2. Le plan

| ID | Item | ★ | ◆ | Taille | Après |
|---|---|:-:|:-:|:-:|---|
| `J1!` | Cinq fiches bloquantes | — | — | — | — (avant d'ouvrir `MIS-6`) |
| `MIS-6` | Rendezvous / phasing sur cible TLE | 5 | 5 | XL | `J1!`, `PHY-6` (v2) |
| `RND-8` | **Profondeur : deux corps dans le même cadre** *(neuf)* | 3 | 3 | M | `MIS-6` (la cible doit exister pour que le cas se produise) |
| `RND-7` | **Vue relative LVLH** *(neuf, ex-stretch de `MIS-6`)* | 4 | 2 | M | `MIS-6` |
| `MIS-12` | **Approche terminale et amarrage** *(neuf)* | 5 | 4 | L | `MIS-6`, `RND-7`, `PHY-6` |

**`RND-7` avant `MIS-12`, et ce n'est pas un confort.** En repère inertiel, une
approche terminale est une spirale illisible ; en LVLH, c'est une figure compacte
autour de la cible. Mettre au point un corridor d'approche sans pouvoir le
*voir* revient à déboguer à l'aveugle — c'est l'outil de développement de
`MIS-12` autant que sa vitrine.

**Fin de version quand** : un vaisseau lancé depuis le sol rejoint un objet qu'il
n'a pas mis en orbite, s'en approche dans un corridor tenu, et s'y attache.

---

## 3. Dette et robustesse

*Les fiches vivent dans [`bugs.md`](../bugs.md),
[`dette-technique.md`](../dette-technique.md) et
[`reliquats.md`](../reliquats.md) ; leur ordonnancement dans
[`05-roadmap-technique.md`](05-roadmap-technique.md). C'est la version qui en
porte le plus, et de loin.*

`MIS-6` est le premier chantier dont le sujet même est le **timing** d'une
trajectoire, ce qui change la sévérité de plusieurs items qui dormaient.

**`J1!` — bloquant, avant d'ouvrir `MIS-6`.** Sans eux, le chantier travaille
sur un instrument qui ment.

| Item | Pourquoi bloquant |
|---|---|
| `BUG-7` | Les gates de non-régression sont vertes ou rouges **selon le filtre `--tests`**. On n'ouvre pas un ◆5 avec un filet dont le verdict dépend de l'ordre d'exécution |
| `BUG-11` | L'optimiseur saute les coasts que le vol rejoue — **~2 770 s d'écart**, près d'une demi-orbite. Sur un chantier de phasing, c'est un défaut de premier ordre |
| `DT-7` | `planner` construit le `MissionPlan` que `MIS-6` va étendre : 7 classes, 1 test |
| `REL-17` | `T1b` (inclinaison après insertion complète) n'a jamais été écrit ; seul `MeoMissionTest` en donne un équivalent partiel |
| `REL-18` | L'étalement de ~19 km de l'ensemble acceptable du CMA-ES est le **plancher de bruit de toute comparaison de références**. À caractériser — pas nécessairement à corriger |

**Préalables — moins chers avant que pendant.** `DT-2` (le *template method* des
stages : cette version en ajoute au moins trois, et l'invariant de pas
d'intégration de `CLAUDE.md` est déjà réimplémenté six fois sans garantie
mécanique d'accord), `BUG-17` (`acceptableCost` mal calé depuis le terme ergols :
aucun arrêt « Target reached » ne peut jouer, donc **chaque mesure coûte plus
cher** pendant tout le chantier), `BUG-8` et `BUG-13` (le wizard et la recherche
de fenêtre, que `MIS-6` étend tous les deux), `BUG-14` (deux portées de recherche
divergentes), `BUG-18` (les rejets de scénario seulement journalisés — le
scénario est **l'outillage de développement** de cette version).

**Accompagnement, à intercaler un lot à la fois :** `BUG-16`, `REL-30`, `REL-31`,
`REL-32`, `DT-3`, `DT-5` (partie optimiseur), `DT-16` (`nRev = 0` figé partout —
à remonter en préalable si le phasing réclame du multi-révolution), `REL-19`,
`REL-21` (annulation d'un calcul, qui gagne en valeur avec des calculs de
rendez-vous longs).

> **`BUG-16` doit être isolé.** Déplacer `t1Max` renormalise toute la recherche
> CMA-ES et perturbe des missions qui ne saturent pas — **les bornes ne sont pas
> des contraintes**. C'est le pire item du corpus à traiter au milieu d'un autre
> chantier ; il lui faut sa propre re-mesure de références.

**Deux fiches versées au périmètre de `MIS-6`**, et comptées ici plutôt qu'ailleurs :
`REL-7` (le near viewport ne montre jamais deux corps 3D — c'est la
fonctionnalité, pas son préalable ; devenu `RND-8`) et `REL-13` (aucun CMA-ES sur
l'injection translunaire, reporté ici par `lunar-flyby/01-decoupage.md` §6 pt 5).

**`DT-5` mérite une mention.** Les trois classes qui s'étaient le plus dégradées
au dernier comptage — `TransferProblem` (427 → **889** lignes), 
`CMAESTrajectoryOptimizer` (398 → **706**), `MultiStageLoadOptimizer` (336 →
**586**) — sont exactement celles que cette version rouvre.

---

## 4. Détail des items

### MIS-6 — Rendezvous / phasing sur cible TLE — ★5 ◆5 XL

Le plus gros item du corpus, et le mieux préparé : la spec dédiée fait 528
lignes et a déjà tranché l'essentiel.

> **Deux morceaux de cette fiche ont pris leur autonomie**, et il faut le lire
> en la lisant : la vue LVLH, qui y était un *stretch*, est devenue `RND-7` ; et
> l'approche terminale, qu'elle range hors MVP, est devenue `MIS-12`. Ce qui
> reste sous cet identifiant est le rendez-vous au sens de la spec — **arriver
> à Δr < 10 km avec ‖Δv_rel‖ < 10 m/s**, et rien de plus.

**Décomposition.**
1. **Source TLE bufferisée.** `TLEPropagator` (SGP4) derrière
   `SlidingWindowEphemerisBuffer` — **non négociable** : à ×10⁵ de vitesse
   d'horloge, l'orbite cible défile entièrement entre deux frames, et une ligne
   d'orbite demande 100–500 points par rafraîchissement. Fenêtre bornée par la
   validité physique du TLE (±3 à 7 jours), pas de dataset 1990-2101.
2. **Abstraction `EphemerisTarget`** (scellée : `SolarBody` | `TleTarget`) —
   l'API est aujourd'hui couplée à l'enum `SolarSystemBody` de bout en bout.
   C'est le vrai coût de refactor du chantier.
3. **Stages** phasing (N révolutions entières, `Δa`) puis transfert Lambert.
4. **Coût** `‖Δr‖ + ‖Δv‖ + ΣΔv + corridor + ergols`, cible MVP Δr < 10 km,
   `‖Δv_rel‖` < 10 m/s.
5. **Rendu de la cible** : `TargetObjectRenderer`, aujourd'hui inexistant.
6. **Repère LVLH**, hérité de `MIS-3` dissous le 2026-08-20. `LOFType.LVLH` est
   fourni par Orekit et le dépôt pratique déjà `LOFType` + `LofOffset` ailleurs :
   le code tient en une dizaine de lignes. Ce qui appartient à cet item est la
   **forme** que prend le service — δr/δv bruts pour le coût terminal du point 4,
   ou figure relative pour `RND-7` — et elle ne se décide qu'ici, faute de
   consommateur avant.

**Tranché dans la spec, à ne pas rouvrir** : pas de Pontryagin (dans le cas
impulsif il ne rapporte rien sur une méthode directe), pas d'approche terminale
HCW au MVP, ISS seule comme cible.

**Ce que la spec range hors MVP et qui n'a pas disparu** : l'approche terminale
HCW est `MIS-12`, la vue LVLH est `RND-7`. Toutes deux sont dans cette version —
c'est le sens de la version — mais pas sous cet identifiant.

**Spec.** [`docs/brainstorm/leo-rendezvous-preparation.md`](../brainstorm/leo-rendezvous-preparation.md).

---

### RND-7 — Vue relative LVLH — ★4 ◆2 M *(neuf, ex-stretch de `MIS-6`)*

**Pourquoi il cesse d'être un stretch.** Sa fiche d'origine le rangeait en
« stretch à fort rendement », et le qualifiait de *bénéfice visuel n° 1 de la
feature*. Avec `MIS-12` dans la même version, il change de nature : une approche
terminale se met au point en regardant la géométrie relative, et il n'existe
aucune autre façon de la regarder. Un stretch qu'un autre item ne peut pas
laisser tomber n'est plus un stretch.

**Ce que c'est.** Une vue dédiée, centrée sur la cible, axes LVLH — V-bar,
R-bar, H-bar — où le chaser est un point qui se déplace dans un plan lisible.
La trajectoire relative y devient une figure compacte au lieu d'une spirale de
plusieurs milliers de kilomètres.

**Ce qui rend l'item petit.** `LOFType.LVLH` est fourni par Orekit, et le dépôt
pratique déjà `LOFType` + `LofOffset` ailleurs : la conversion tient en une
dizaine de lignes. Le travail est dans la **vue** — un mode d'affichage de plus
à côté de `FocusView`, avec son échelle propre (les mètres, pas les milliers de
kilomètres) et sa grille de repères.

**Ce que `MIS-6` en garde.** Le service LVLH lui-même, dont `MIS-6` a besoin pour
ses δr / δv terminaux, appartient à `MIS-6`. `RND-7` en est le consommateur
d'affichage. La frontière est celle-là : `MIS-6` produit les nombres, `RND-7`
les met en figure.

---

### RND-8 — Profondeur : deux corps dans le même cadre — ★3 ◆3 M *(neuf)*

**Pourquoi maintenant, et pas plus tôt.** La question du troisième viewport (ou
d'un depth buffer logarithmique / reverse-Z) a été **tranchée par la négative le
2026-08-18**, sur mesure : `PHY-4 / L5` §5.3 a montré qu'un seul globe est
dessiné, dans la région de l'origine où le pas de profondeur vaut 27 km, et que
le bout lointain du trait ne dispute la profondeur qu'à lui-même ; `L6` §12.5 l'a
confirmé à l'écran sur la première trajectoire lunaire réelle. La décision
portait sa propre condition de réouverture : **deux globes dans le même cadre**,
c'est-à-dire `MIS-6`.

`REL-7` disait la même chose depuis l'autre bout : le near viewport n'a jamais
montré deux corps 3D, et un rendez-vous, c'est le vaisseau **et** sa cible à
l'écran. La roadmap technique l'avait déjà versé au périmètre de `MIS-6` ; cet
item lui donne un identifiant.

**Ce qu'il faut faire, dans cet ordre.**
1. **Mesurer avant de coder.** Le plan near est aujourd'hui piloté par la
   distance à l'origine, ce qui suppose que le contenu le plus proche s'y
   trouve — hypothèse qui tombe précisément quand une cible est à quelques
   centaines de mètres et la Terre à 400 km. Constater le ratio far/near réel
   sur un rendez-vous volé est le premier livrable : il se peut que le cas ne se
   pose pas, comme il ne s'est pas posé en lunaire.
2. **Choisir seulement ensuite**, entre les trois options déjà nommées :
   troisième viewport « mid », depth buffer logarithmique, reverse-Z.

**Ce que l'item s'interdit.** Ouvrir le chantier avant la mesure. Le
raisonnement de `L5` a été juste deux fois de suite ; il pourrait l'être une
troisième.

---

### MIS-12 — Approche terminale et amarrage — ★5 ◆4 L *(neuf)*

**Pourquoi il existe comme item.** L'énoncé du besoin est *« s'amarrer sur un
rendez-vous spatial »*, et `MIS-6` ne le fait pas : sa spec fixe le MVP à
**Δr < 10 km, ‖Δv_rel‖ < 10 m/s**, range l'approche terminale HCW explicitement
**hors MVP**, et ne dit rien du contact. Dix kilomètres et dix mètres par
seconde, c'est un rendez-vous réussi et un amarrage impossible.

**Le préalable est en [v2](02-roadmap-v2.md), et il est non négociable.**
S'approcher d'une station avec un lanceur entier n'a pas de sens — ni
physiquement (la masse et la géométrie sont celles de la pile), ni visuellement.
`PHY-6` fait de la charge utile un objet distinct, pilotable et dessiné pour
lui-même ; sans lui, cet item n'a pas de sujet.

**À faire.**
- **Équations de Clohessy-Wiltshire** pour le mouvement relatif proche, et les
  petits burns guidés qu'elles permettent. C'est ce que la spec de `MIS-6`
  nomme et repousse.
- **Un corridor d'approche** : géométrie V-bar ou R-bar, cône d'approche, vitesse
  maximale décroissante avec la distance. C'est la contrainte qui distingue un
  amarrage d'une collision.
- **L'attitude du vaisseau**, qui n'existe pas pour une charge utile aujourd'hui.
  Le dépôt a des fournisseurs d'attitude (`attitude/`, gravity-turn et
  zenith-thrust) tous conçus pour une ascension ; pointer un axe d'amarrage vers
  un port qui bouge est un troisième cas, et le premier qui suive une cible.
- **Un critère de capture** : position, vitesse relative et alignement angulaire
  simultanément dans leurs bornes, à un même instant. C'est le seul objectif du
  corpus à contraindre les trois à la fois.
- **La fin de mission** : l'amarrage est un état terminal, pas une orbite
  atteinte. `MissionHorizon` et l'orbite atteinte affichée par `UI-1` supposent
  tous deux une mission qui se termine sur une orbite ; ce cas-là n'en a pas.

**Ce que l'item s'interdit.** Pas de mécanique de contact ni de modèle de
capture physique (ressorts, loquets) — le critère de capture est géométrique et
cinématique. Pas de transfert d'équipage ou de fluides. Pas de désamarrage ni de
retour : ce serait une mission de plus, et elle réutiliserait `MIS-10`.

**Le risque, et il est de coût de calcul.** Un corridor terminal se parcourt en
minutes avec des burns de quelques centimètres par seconde, dans une mission qui
dure des heures. L'optimiseur ne peut pas traiter les deux échelles avec le même
pas ni la même fonction de coût. Le découpage devra dire — avant de coder — si
l'approche terminale est un stage optimisé comme les autres, ou une phase
**guidée** rejouée à pas fin, hors de la boucle CMA-ES. La seconde est
recommandée : c'est le geste que `PHY-5` a déjà posé pour les débris, et pour la
même raison.

---


## 5. Questions ouvertes

1. **Cible du rendez-vous (`MIS-6`)** — héritée de v1. L'ISS livrée en dur
   (option A de la spec) suffit au MVP. L'import de TLE arbitraire (option B)
   est une feature UI à part entière — sélection d'un objet dans un catalogue,
   validation de fraîcheur du TLE, gestion des cibles périmées — et ne doit pas
   être glissée dans `MIS-6`.
2. **L'approche terminale est-elle optimisée ou guidée (`MIS-12`)** — à trancher
   avant le découpage, pour la raison de coût de calcul décrite dans la fiche.
   Recommandation : **guidée**, rejouée à pas fin hors de la boucle CMA-ES.
3. **Que devient la mission une fois amarrée** — un état terminal « amarré » qui
   fige la mission, ou un vaisseau qui reste attaché et continue de se propager
   avec la cible ? La seconde lecture ouvre la porte à un désamarrage et à un
   retour, donc à une mission de plus ; la première ferme la boucle proprement.
   À trancher au moment de la spec de `MIS-12`, pas avant.
4. **`MIS-9` — éphéméride de mission hors mémoire.** Non planifié et
   conditionnel, il est rattaché à cette version sans y être planifié : sa fiche
   conclut que le geste juste est de **généraliser `EphemerisSource` /
   `EphemerisTarget` une fois**, ce que `MIS-6` fait de toute façon. Si l'une de
   ses trois conditions de déclenchement se vérifie — et **mesurée**, pas
   supposée — il se fait *après* `MIS-6` et à son tarif marginal. Sa fiche
   complète est ci-dessous.

---

## 6. Rattaché sans être planifié

### MIS-9 — Éphéméride de mission hors mémoire — **non planifié, conditionnel**

> Corollaire naturel de `MIS-8`, délibérément **non retenu dans une version**. Ce
> n'est pas un refus : c'est un item dont la condition de déclenchement n'est
> pas remplie aujourd'hui, et qui coûterait cher s'il était fait trop tôt.

**L'idée.** Ne plus garder toute la trajectoire en mémoire : la générer en flux
vers le disque, et n'en charger qu'une fenêtre.

**Pourquoi ce n'est pas la bonne réponse *maintenant*.** Le tableau de `MIS-8`
montre que le pas d'échantillonnage variable rend tenable tout horizon
réaliste (30 jours ≈ 7 Mo). Le stockage hors mémoire achèterait le même
résultat pour dix fois le travail : format, versionnement, IO hors du fil de
rendu, fenêtre glissante, invalidation à la ré-optimisation, cycle de vie des
fichiers temporaires. Et il ne réglerait pas le vrai défaut — les deux
consommateurs aux besoins incompatibles (cf. `MIS-8`), qui restera entier
quelle que soit la localisation des octets.

**Conditions de déclenchement** (au moins une, et **mesurée**, pas supposée) :

1. un cas d'usage réel demande une résolution fine sur un horizon long — par
   exemple une décroissance orbitale sur des mois après `PHY-2`, ou une analyse
   post-mission qui veut chaque seconde de l'ascension **et** 30 jours de
   dérive ;
2. le nombre de missions simultanément visibles fait de la somme des
   éphémérides un poste mémoire mesuré, pas redouté ;
3. le mode batch (backlog) veut produire des trajectoires sans les afficher.

**Comment le faire le jour venu — et surtout, ce qu'il ne faut pas faire.**
Ne pas inventer un format de trajectoire mission sur disque. Le projet a déjà
toute la machinerie pour ça : `SlidingWindowEphemerisBuffer`,
`EphemerisWorker`, le format V1 zstd de `simulation/source/`, `LruCache`,
prefetch. Et `MIS-6` conclut déjà que la cible TLE doit passer par cette même
couche. Trois consommateurs convergent donc — planètes, cibles TLE,
trajectoires longues — et le geste juste est de **généraliser
`EphemerisSource` / `EphemerisTarget` une fois**, la trajectoire de mission
devenant une source parmi d'autres. Ce refactor est déjà compté dans `MIS-6` :
si `MIS-9` se déclenche, il se fait *après* lui et à son tarif marginal, pas
comme un chantier séparé.
