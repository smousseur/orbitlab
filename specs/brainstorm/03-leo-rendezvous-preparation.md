# Brainstorm — Rendez-vous orbital LEO avec un objet TLE

> **Statut** : Réflexion préparatoire. Pas d'implémentation à ce stade.
>
> **Objectif** : Identifier les briques manquantes (physiques, algorithmiques, UI) pour
> simuler dans OrbitLab un rendez-vous avec un objet en orbite basse défini par un fichier TLE
> (typiquement l'ISS).
>
> **Périmètre MVP envisagé** : rendez-vous impulsif balistique. Pas de docking, pas de GNC
> temps-réel.

---

## 1. Contexte

OrbitLab sait aujourd'hui placer un véhicule sur une orbite **statique cible** (LEO circulaire,
GTO) via :
- une montée en gravity turn,
- un (ou deux) burn(s) de transfert dont les paramètres sont optimisés par CMA-ES contre
  un `OrbitInsertionObjective` (perigée / apogée / inclinaison).

La cible est aujourd'hui purement **géométrique** : un jeu d'altitudes et d'angles, sans objet
physique à rattraper. Le rendez-vous introduit deux changements de fond :

1. **La cible bouge** : sa position est définie par un TLE et propagée par SGP4/SDP4.
2. **L'optimisation devient cinématique** : le coût ne porte plus uniquement sur la forme de
   l'orbite mais sur l'écart relatif (position + vitesse) avec la cible à un instant donné.

Ce doc cherche à éclairer deux décisions amont avant tout codage :
- **Où** brancher les éphémérides TLE dans l'architecture existante ?
- **Comment** étendre le pipeline d'optimisation pour gérer la dynamique relative ?

Et à recenser les sujets connexes qui se posent en chemin.

---

## 2. L'objet cible — éphémérides TLE

### 2.1 Propagation TLE avec Orekit

Orekit fournit nativement `TLEPropagator` (SGP4/SDP4). Caractéristiques pertinentes :

- Entrée : deux lignes de 69 caractères (NORAD format).
- Précision : ~1 km à J+0, dégradation de **1–3 km par jour** après l'époque du TLE.
- Sortie : `SpacecraftState` (position + vitesse) dans le repère TEME, à convertir vers
  ICRF/GCRF via les transformations Orekit standard.
- Adapté pour les phases de **phasing** et **transfer** (échelle ≥ km). **Insuffisant** pour
  les derniers mètres d'un docking (où il faudrait des mesures radar/optiques temps-réel).

À noter : aucune référence à `TLEPropagator` dans le repo aujourd'hui — c'est une brique
entièrement nouvelle.

### 2.2 Options d'intégration dans l'architecture éphémérides

Le système actuel (`simulation/source/`, `simulation/ephemeris/`) suppose un ensemble fermé de
corps (`SolarSystemBody` enum) avec des datasets `.bin` précalculés dans `dataset/ephemeris/`,
chargés par `DatasetEphemerisSource` et bufferisés par `SlidingWindowEphemerisBuffer`.

**Option A — Dataset précalculé livré avec l'app, comme les planètes**
- *Pros* : réutilise toute l'infra existante (`BodyFile`, format V1 zstd, LRU, prefetch,
  sliding window, worker), zéro friction côté chemin chaud.
- *Cons* : `SolarSystemBody` est fermé (sealed enum, 11 corps) ; un TLE se périme en quelques
  jours donc le dataset doit être régénéré régulièrement ou il devient faux ; mauvais fit pour
  "n'importe quel objet du catalogue".
- *Cas d'usage type* : une à deux cibles emblématiques fournies par défaut (ISS, Hubble).

**Option B — Chargement à la demande depuis l'UI**
- *Pros* : user-driven, données toujours fraîches, n'importe quel objet (Starlink, débris,
  satellites scientifiques).
- *Cons* : nouvelle infra à construire (catalogue TLE local, ou fetch HTTP type Celestrak, ou
  import fichier user), nouvelle étape wizard, gestion d'erreur / format invalide / TLE périmé.
- *Cas d'usage type* : utilisateur qui colle un TLE dans une boîte de dialogue, ou choisit
  dans une liste catégorisée (stations, weather, sciences…).

**Option C — Hybride (recommandée à terme)**
- L'ISS livrée précalculée pour démarrage à froid sans config.
- Import à la demande pour le reste.
- Évolution naturelle du MVP.

**Recommandation MVP** : Option A, ISS seule. Permet d'arriver vite à une démo intégrée. Option C
en évolution.

### 2.3 Refactor nécessaire dans le code

Que l'on choisisse A, B ou C, l'API actuelle est couplée à `SolarSystemBody` (clé enum partout :
`EphemerisService.trySampleIcrf(SolarSystemBody, AbsoluteDate)`, `EphemerisWorker` indexé par
enum, `BodyFile` nommé par `body.name()`).

Pistes :
- Introduire une abstraction `EphemerisTarget` (sealed) avec deux variantes : `SolarBody` et
  `TleTarget` (porte le NORAD ID + les deux lignes).
- Généraliser `EphemerisService.trySampleIcrf` à `EphemerisTarget`.
- Ajouter un `TleEphemerisSource` wrappant `TLEPropagator`, **bufferisé** comme les planètes
  (cf. §2.4 ci-dessous, contrainte timeline).
- Le dataset `.bin` shippé sur disque n'est pertinent **que pour l'option A** (cible figée
  livrée avec l'app, type ISS). Pour l'option B / C, la propagation alimente directement la
  fenêtre glissante à partir des deux lignes — pas de fichier intermédiaire.

### 2.4 Bufferisation et contrainte de la timeline

SGP4 est rapide en absolu (~µs par évaluation). Naïvement on pourrait l'appeler à la volée à
chaque sample. **Ce n'est pas viable** dans OrbitLab à cause de la timeline :

- `SimulationClock` autorise des **multiplicateurs de vitesse extrêmes** (lecture rapide,
  rewind). À x10⁵–x10⁶, l'orbite cible défile **entièrement entre deux frames** de rendu.
- Le rendu d'une ligne d'orbite n'est pas un sample isolé : il faut **100–500 points** le long
  de la trajectoire, à reconstruire à chaque rafraîchissement quand l'orbite précesse ou que la
  fenêtre temporelle visible se déplace.
- Le scrubbing manuel de la timeline (`SimulationClock` supporte le saut arbitraire) impose une
  disponibilité **instantanée** à n'importe quel instant — pas de latence acceptable.
- Multiplicité : si on a un jour plusieurs cibles TLE (constellation, débris), c'est N fois la
  charge à chaque frame.

→ Conclusion : on **doit** réutiliser l'infrastructure `SlidingWindowEphemerisBuffer` +
`EphemerisWorker`. Le worker thread calcule SGP4 en arrière-plan et alimente une grille
pré-échantillonnée ; le thread de rendu fait juste de l'interpolation, comme pour les planètes
aujourd'hui.

**Spécificité TLE vs planètes** : un TLE n'est valable que **quelques jours autour de son
époque** (au-delà, l'erreur SGP4 explose). Donc :

- Pas de précalcul "1990–2101" comme les planètes. La fenêtre maximale a un sens **borné** par
  la validité physique du TLE (typiquement ±3 à ±7 jours autour de l'époque, paramétrable).
- À la **chargement** d'un TLE, on génère la fenêtre complète en une passe (rapide : quelques
  millions d'évaluations SGP4 ≈ quelques secondes), on la met en cache mémoire. Pas besoin de
  re-générer en cours de simulation tant que la timeline reste dans la fenêtre.
- Si l'utilisateur scrubbe hors fenêtre, on **clampe** ou on **avertit** (TLE périmé hors
  domaine de validité — cf. §4.2).
- Format : pas de `.bin` sur disque pour les TLE chargés à la volée. Pour le cas A (ISS shippée),
  on peut soit shipper un `.bin` pré-généré, soit shipper juste les deux lignes et régénérer
  la fenêtre au démarrage (probablement plus simple et négligeable en coût).

En résumé : on garde toute la **couche tampon** (sliding window, worker, interpolation) du
système actuel, on remplace juste la **source** (SGP4 au lieu de lecture fichier zstd), et on
borne la fenêtre par la validité du TLE.

---

## 3. Algorithmique du rendez-vous

### 3.1 Briques manquantes vs ce qui existe

| Brique                                | État aujourd'hui                                       | À faire                                  |
|---------------------------------------|--------------------------------------------------------|------------------------------------------|
| Objectif "atteindre une orbite"       | `OrbitInsertionObjective` (perigée/apogée/inclinaison) | Étendre vers `RendezvousObjective`       |
| Notion de **cible mobile** dans le coût | Aucune                                                | Nouvelle (voir 3.4)                      |
| Stages multi-burn paramétrés          | Gravity turn + transfer 2-burn                          | Ajouter phasing + transfer Lambert       |
| Seed analytique de l'optimiseur       | Hohmann seed dans `TransferProblem.buildAnalyticalSeed` | Ajouter seed Lambert (TPBVP analytique)  |
| Approche terminale fine               | Aucune                                                  | Optionnel : équations Clohessy-Wiltshire |
| Repère relatif (LVLH)                 | Aucun usage dans le rendu / le coût                     | Cf. §3.5                                 |
| Fenêtre de lancement                  | Aucune (date manuelle)                                  | Pré-calcul RAAN ↔ date                   |

### 3.2 Décomposition en stages réutilisant l'architecture multi-stage

L'architecture `Mission` → `MissionStage[]` → `OptimizableMissionStage` est bien adaptée. Pipeline
visé pour une mission RDV :

1. **Ascent (gravity turn)** — réutilise `GravityTurnStage` / `GravityTurnProblem`.
2. **Insertion en parking orbit** — réutilise `TransferTwoManeuverProblem` (ou une variante
   ciblant une orbite légèrement plus basse que la cible, pour bénéficier du catch-up).
3. **Phasing** (nouveau) — N révolutions sur orbite catch-up + un burn de raise pour
   synchroniser l'arrivée avec la position de la cible. Variables typiques : N (entier petit,
   1–10), Δv du burn de transfert. Cible : être au bon endroit pour le burn de Lambert.
4. **Transfer (nouveau, seed Lambert)** — burn qui amène le chaser à proximité de la cible
   dans un TOF donné. Variables : TOF, Δv. Le seed analytique vient de la résolution Lambert
   (position chaser actuelle, position cible projetée à T+TOF).
5. **Approche terminale (optionnel, hors MVP)** — petits burns guidés par HCW, géométrie
   V-bar ou R-bar.

### 3.3 Quel type d'optimisation ? La question Pontryagin

L'utilisateur a évoqué le **maximum de Pontryagin** (PMP). Petit cadrage :

- **Méthode directe (paramétrique)** : on décrit la trajectoire par un petit vecteur de
  paramètres (timings, durées, angles, ΔV), on propage, on évalue un coût scalaire, on confie
  ça à un optimiseur (CMA-ES, gradient, etc.). **C'est ce qu'OrbitLab fait déjà.**
- **Méthode indirecte (Pontryagin)** : on écrit le Hamiltonien du problème de commande
  optimale, on dérive les conditions nécessaires d'optimalité (équations adjointes sur les
  costates), et on résout un **TPBVP** (two-point boundary value problem) par shooting ou
  collocation. La commande optimale tombe analytiquement (par exemple bang-bang en min-temps,
  ou continue en min-énergie).
- **Avantages PMP** : optimalité garantie (au moins localement), espace de décision plus petit
  (les costates, dim faible), pas besoin de paramétrer la trajectoire à la main.
- **Inconvénients PMP** : TPBVP **extrêmement sensible** aux conditions initiales sur les
  costates (qui n'ont pas de signification physique évidente → guess difficile), méthodes
  numériques lourdes (continuation, homotopie), code plus complexe.

**Verdict pour OrbitLab** :

- Pour un **rendez-vous impulsif** (ce qu'on cible au MVP), PMP **n'apporte rien de plus**
  qu'une méthode directe : dans le cas impulsif, le Hamiltonien dégénère et l'optimum direct
  est déjà l'optimum PMP. CMA-ES + seed Lambert est la bonne approche, cohérente avec le code
  existant.
- PMP devient **réellement utile en propulsion continue / low-thrust** (futur Hall thruster,
  voile solaire, transferts longs sur des semaines/mois), où la **structure** de la commande
  optimale (bang-bang, ou continue avec arcs singuliers) ne peut pas être devinée par un
  paramétrage discret raisonnable.

→ **PMP : hors scope MVP**, à reconsidérer si un module low-thrust est ajouté un jour.

### 3.4 Fonction de coût pour le rendez-vous

Au lieu de mesurer un écart à une orbite cible, on mesure un écart à un **état cible** (position
+ vitesse de la cible TLE à l'instant final). Structure suggérée du coût :

```
J = w_pos * ||r_chaser(t_f) - r_target(t_f)||
  + w_vel * ||v_chaser(t_f) - v_target(t_f)||
  + w_dv  * Σ |Δv_i|
  + pénalités (corridor anti-collision, altitude min, propergol épuisé, ...)
```

Notes pratiques :
- Le **timing** est une variable d'optimisation (pas juste un paramètre fixe). Le moment du
  burn de transfer détermine la géométrie Lambert, qui détermine `t_f`.
- Pondérations à équilibrer : un poids vitesse trop fort tue le poids position et inversement.
  Démarrer avec des poids normalisés à des valeurs "naturelles" (10 km pour la position,
  10 m/s pour la vitesse).
- Le seed Lambert garantit une fonction de coût quasi-nulle au point de seed → CMA-ES travaille
  dans un voisinage favorable, comme aujourd'hui avec le seed Hohmann.

### 3.5 Le repère LVLH (Local Vertical Local Horizontal)

Comme c'est central pour le rendez-vous et que la définition mérite d'être posée, on prend
quelques lignes pour la détailler.

**Définition.** LVLH est un repère **attaché à la cible** (le satellite à rattraper), qui
tourne avec son mouvement orbital. Pour une orbite circulaire keplérienne, il tourne uniformément
à la vitesse angulaire `n = sqrt(μ/a³)`. Trois axes orthogonaux :

- **R-bar (radial)** : le long de la verticale locale, de la cible vers le centre de la Terre.
  (Convention courante. Certaines littératures utilisent l'opposé, vers le zénith — toujours
  vérifier la convention du texte.)
- **V-bar (along-track)** : le long de la vitesse orbitale (pour une orbite circulaire). Sur
  une orbite elliptique, c'est l'axe perpendiculaire à R-bar et à H-bar, donc proche mais pas
  exactement la vitesse.
- **H-bar (cross-track)** : perpendiculaire au plan orbital, dans le sens du moment cinétique.

Ce trièdre est aussi appelé **Hill frame** ou **RSW / RTN frame** selon les conventions
(R = radial, S/T = transverse/along-track, W/N = normal/cross-track). Orekit le fournit
nativement via `LOFType.LVLH` et ses variantes.

**Pourquoi c'est central pour le rendez-vous.**

1. **Équations de Clohessy-Wiltshire (HCW)** : les équations du mouvement du chaser **relativement
   à la cible**, linéarisées autour d'une orbite circulaire et exprimées en LVLH, se simplifient
   en un système linéaire à coefficients constants. La solution est **analytique** (closed-form,
   pas de propagation numérique). Domaine de validité : distances petites devant le rayon orbital
   (typiquement < quelques dizaines de km autour de la cible). C'est l'outil canonique pour la
   phase d'approche.

2. **Trajectoires naturelles caractéristiques** : en LVLH, la trajectoire libre (sans poussée) du
   chaser n'est pas n'importe quoi. Exemples :
   - **Football orbit** : un chaser en orbite quasi-circulaire de même période que la cible
     mais légèrement décalée trace une ellipse 2:1 (allongée le long de V-bar) autour de la
     cible — figure compacte, immédiatement lisible.
   - **Walking orbit / drift** : un chaser plus bas en altitude que la cible avance le long de
     V-bar (catch-up) ; un chaser plus haut recule. La pédagogie de "monter pour ralentir,
     descendre pour accélérer" devient visible.

3. **Profils d'approche standard** définis en LVLH :
   - **V-bar approach** : le chaser arrive le long de V-bar (de derrière, à même altitude).
     C'est l'approche ISS depuis Dragon/Cygnus.
   - **R-bar approach** : le chaser arrive par-dessous (le long de R-bar), profil utilisé par
     Soyuz historiquement. Plus stable passivement (chute libre relative).
   - Les corridors de sécurité (KOS — Keep-Out Sphere de 200 m autour de l'ISS, AS —
     Approach Sphere de 2 km) sont définis dans LVLH.

4. **Pédagogie / rendu** : en repère inertiel (ICRF), la trajectoire d'un chaser autour de la
   cible est une **spirale complexe** difficile à interpréter (les deux orbitent la Terre, et la
   différence est noyée dans le mouvement orbital commun). En LVLH, on **soustrait** le mouvement
   orbital de la cible : la trajectoire relative devient une figure **compacte autour de
   l'origine** (la cible). Pour un utilisateur de l'app, c'est la différence entre "je vois deux
   objets tourner autour de la Terre" et "je vois clairement comment le chaser approche la
   cible".

5. **Conséquence pour le rendu OrbitLab** : c'est la justification d'un **second viewport** (ou
   d'un mode de vue) centré sur la cible et exprimé en LVLH. La trajectoire optimisée serait
   tracée dans ce repère, et la convergence du rendez-vous deviendrait visuellement évidente.
   Stretch goal, mais à très fort retour pédagogique — sans doute le bénéfice visuel #1 de cette
   feature.

**Brique nécessaire côté code** : un `LvlhFrame` calculé à partir de la cible (position +
vitesse), wrapping le `LOF` Orekit. Un service qui transforme un `SpacecraftState` du chaser en
état relatif `(δr, δv)` LVLH. Réutilisable pour la fonction de coût terminale, le rendu, et
plus tard HCW.

### 3.6 Lambert — principes et utilité

**Énoncé du problème.** Étant donnés deux vecteurs position `r1` et `r2` exprimés dans un repère
inertiel, et un temps de vol `TOF`, trouver l'orbite keplérienne qui relie `r1` à `r2` en
exactement `TOF` secondes. Les inconnues sont les vitesses `v1` (au départ) et `v2` (à l'arrivée).
C'est un **problème aux deux bouts** (TPBVP) — on impose les positions aux extrémités, pas l'état
complet, et le solveur en déduit les vitesses correspondantes.

**Caractère du problème.**
- Pour `r1`, `r2`, `TOF` donnés, l'orbite peut suivre la **voie courte** (transit < 180° autour
  du foyer) ou la **voie longue** (> 180°). Choix discret à fournir au solveur.
- Pour `TOF` > une période orbitale, plusieurs solutions existent : **multi-révolution**, indexées
  par `k = 0, 1, 2…` (le chaser fait `k` tours complets avant d'atteindre `r2`). Pour chaque `k ≥ 1`,
  deux branches sont possibles (low-energy / high-energy).
- Résolution : itération 1D sur un paramètre interne (formulations Battin, Lancaster-Blanchard,
  Izzo). Très rapide en absolu (< 1 ms par appel), donc utilisable en boucle d'optimisation et
  même pour des balayages (cf. pork-chop, §3.9).
- Restriction : modèle **purement keplérien**. Pas de J2, pas de traînée, pas de troisième corps.
  La solution Lambert est exacte dans son modèle, mais dérive de la réalité dès qu'on intègre la
  trajectoire dans un propagateur complet.

**Pourquoi c'est central pour le rendez-vous.**

1. **Sortie directement actionnable.** Pour un transfer, on connaît `r1` (position du chaser à
   l'instant du burn) et `r2` (position **future** de la cible à l'instant d'arrivée, fournie par
   la propagation TLE bufferisée). Lambert renvoie `v1` ⇒ `Δv₁ = v1 − v_chaser_actuelle` (vecteur
   du burn de départ) et `Δv₂ = v_target(t_f) − v2` (vecteur du burn d'arrivée pour annuler la
   vitesse relative). En une résolution, on a les **deux burns** du transfer, sans tâtonner.

2. **Seed analytique pour CMA-ES.** Comme aujourd'hui le seed Hohmann dans
   `TransferProblem.buildAnalyticalSeed`, Lambert place CMA-ES dans un voisinage où la fonction
   de coût est déjà proche de zéro. L'optimiseur sert alors à **corriger** les effets non modélisés
   par Lambert (J2, durée finie du burn, biais TLE, masse variable), pas à découvrir le transfer
   depuis zéro. Convergence plus rapide et plus robuste, en quelques générations.

3. **Pork-chop chart** (stretch UI, cf. §3.9). Lambert est suffisamment rapide pour balayer un
   quadrillage `(t_départ, TOF)` en une fraction de seconde et générer une carte de `ΔV` total.
   C'est l'outil standard d'analyse de fenêtre de transfert en mission planning. Pour le RDV, ça
   matérialise visuellement les zones de coût faible et donne à l'utilisateur un repère intuitif.

4. **Multi-révolution = phasing implicite.** Pour les `TOF` longs, un Lambert avec `k ≥ 1` couvre
   à la fois la mise à jour temporelle (k tours) et le rendez-vous géométrique. Si la séparation
   phasing/transfer est artificielle dans certains cas, le multi-rev permet de fusionner les deux
   en une seule manoeuvre paramétrique.

**Disponibilité Orekit.** `org.orekit.utils.IodLambert` couvre le cas `k = 0` (single-rev). Pour
le multi-rev, vérifier la disponibilité native ; à défaut, implémenter une variante (l'algorithme
Izzo 2014 est l'état de l'art, court à coder, robuste, et gère uniformément `k = 0..N`).

### 3.7 Stage Phasing (nouveau) — détail

**Objectif.** Synchroniser **temporellement** chaser et cible. Après l'insertion en parking orbit,
les deux objets peuvent être sur des plans (presque) confondus mais à des anomalies très
différentes — on est au "bon endroit géométrique" mais pas "au bon moment".

**Mécanisme physique.** Différence de **période orbitale**. Le chaser sur une orbite légèrement
plus basse que la cible a une période plus courte (`T = 2π √(a³/μ)`) et **rattrape** la cible le
long de V-bar (cf. §3.5). Plus haute ⇒ il **prend du retard**. Le taux de catch-up (en degrés par
révolution) est directement proportionnel au `Δa` entre les deux orbites.

**Paramétrisation pour l'optimisation.** Variables typiques :
- `N` : nombre entier de révolutions sur l'orbite de phasing (1 à ~10). Entier ⇒ variable discrète,
  à gérer dans CMA-ES (arrondi + post-traitement, ou décomposition : un sous-problème continu par
  valeur de `N`, on garde le meilleur).
- `Δa_phasing` : delta de demi-grand axe par rapport à la cible (négatif = plus bas = rattrape).
  Borné par contraintes opérationnelles (altitude minimale, marge atmosphérique).
- Optionnel : décomposition en **deux burns** (insertion sur orbite de phasing, puis raise après
  `N` révolutions) plutôt qu'un seul. Plus de degrés de liberté, plus de propergol, mais permet de
  rejoindre exactement la condition initiale du Lambert.

**Contraintes.**
- Altitude périgée ≥ seuil de sécurité atmosphérique (> ~200 km pour éviter la traînée notable).
- `N` borné en haut par budget de propergol restant et durée totale acceptable de mission
  (chaque révolution ajoute ~90 min de mission).
- Phase finale : à la sortie du phasing, le **rendez-vous angulaire** doit placer le chaser dans
  la fenêtre Lambert acceptable (cf. §3.8). On peut formuler cela soit comme contrainte dure
  (rejet de la solution), soit comme partie du coût global de la mission (pénalité douce).

**Sortie utile au stage suivant.** L'état `(r_chaser, v_chaser)` à `t_fin_phasing`, qui devient
`r1` pour le Lambert du transfer.

**Cas dégénéré.** Si l'orbite d'insertion est déjà bien phasée (par chance ou par choix d'une
fenêtre de lancement précise, cf. §4.1), `N = 0` et le stage devient un passe-plat. Le pipeline
doit le gérer proprement sans crash ni overhead.

### 3.8 Stage Lambert Transfer (nouveau) — détail

**Objectif.** Amener le chaser depuis sa position courante (fin de phasing) jusqu'à un point
d'arrivée à proximité immédiate de la cible, en un temps de vol `TOF` choisi.

**Mécanisme.** Deux burns impulsifs encadrant un arc keplérien :
- **Burn 1 (départ)** : `Δv₁ = v_lambert(t₀) − v_chaser(t₀)`. Place le chaser sur l'orbite de
  transfert calculée par Lambert.
- **Burn 2 (arrivée)** : `Δv₂ = v_target(t_f) − v_lambert(t_f)`. Annule la vitesse relative à
  l'arrivée — sans ce burn, le chaser passerait à proximité puis dériverait sur sa propre orbite.

**Variables d'optimisation.**
- `TOF` (temps de vol) : continu, borné. Pour le RDV LEO typique, ordre de grandeur 0,5 à 2
  périodes orbitales (~45 min à 3 h).
- `branche` : voie courte / voie longue (discret, 2 valeurs).
- `k` : nombre de révolutions Lambert (entier, 0 à 2 typiquement pour LEO). Pour `k = 0` le
  transfer est rapide ; pour `k ≥ 1` on bénéficie du multi-rev qui fusionne partiellement avec
  le phasing.
- Optionnel : petits offsets de timing autour du seed Lambert pour absorber les perturbations J2
  que Lambert ignore (typiquement quelques secondes à quelques dizaines de secondes).

**Fonction de coût locale.**
```
J_transfer = ‖Δv₁‖ + ‖Δv₂‖
           + w_arrival  * ‖r_chaser(t_f) − r_target(t_f)‖
           + w_velocity * ‖v_chaser(t_f) − v_target(t_f)‖
           + corridor + propergol
```
Avec un seed Lambert exact, le terme `‖r_chaser − r_target‖` est microscopique au point de seed —
c'est l'intégration réelle (avec J2, masse variable, finite burn) qui crée l'écart résiduel que
CMA-ES corrige.

**Hand-off au stage d'approche terminale.** Pour le MVP, le stage Lambert termine la mission. Si
on ajoute plus tard l'approche HCW (cf. §3.5), le critère de sortie devient "chaser dans la KOS de
2 km autour de la cible avec `‖Δv_rel‖` < 1 m/s" puis un stage HCW prend le relais en LVLH.

### 3.9 Pistes d'intégration UI

L'introduction du RDV ajoute des paramètres physiques et des choix d'optimisation que
l'utilisateur doit pouvoir contrôler ou observer. Ébauche, à raffiner au moment du design UI.

**Sélection de la cible (wizard, nouvelle étape).**
- Choix dans une liste catégorisée (stations / satellites scientifiques / débris notables) — pour
  le MVP, ISS uniquement.
- À terme : import d'un TLE collé dans une zone de texte, ou fetch depuis Celestrak via URL.
- Affichage de l'**âge du TLE** et avertissement si > 7 jours (cf. §4.2).

**Fenêtre de recherche temporelle.**
- L'utilisateur définit une plage `[t_min, t_max]` pour la date de lancement (ou laisse l'algo
  proposer un créneau à partir du site de lancement et de l'orientation orbitale de la cible,
  cf. §4.1).
- Slider de **`TOF` total** (durée totale mission) avec recalcul du `ΔV` à la volée — Lambert est
  suffisamment rapide pour ça.
- Stretch : afficher un **diagramme pork-chop** `(date_lancement × TOF)` colorisé par `ΔV` total.
  Visualisation iconique en mission planning, et naturelle ici grâce au coût négligeable de
  Lambert.

**Paramètres du phasing.**
- Borne `N_max` (révolutions maximum) — directement liée à la durée acceptable de mission. Slider
  ou input numérique simple.
- Borne `Δa_max` (écart d'altitude maximum sur l'orbite de phasing) — pédagogique, sinon
  l'optimiseur peut choisir des orbites peu réalistes.

**Paramètres du transfer.**
- Bornes sur `TOF_transfer` (min/max) — souvent dérivables de `TOF` total et de `N_max`, donc pas
  forcément à exposer.
- Toggle "autoriser le multi-révolution Lambert" (`k ≥ 1`) — affecte le nombre de branches
  explorées.
- Choix du **profil d'approche** (V-bar / R-bar / direct), même si pour le MVP "direct" suffit
  (l'approche fine sort du scope).

**Visualisation pendant l'optimisation.**
- Tracer chaser + cible dans le viewport principal (ICRF) avec des couleurs distinctes et un
  pictogramme de cible reconnaissable.
- Afficher les **moments des burns** (départ phasing, arrivée phasing, burn 1 Lambert, burn 2
  Lambert) comme marqueurs sur la timeline.
- Stretch : viewport LVLH dédié (cf. §3.5 et §4.8), où la convergence du chaser sur la cible
  devient visuellement évidente.

**Affichage des résultats.**
- `ΔV` total, masse propergol restante, écart final position + vitesse, durée totale de mission.
- Marqueurs sur la trajectoire pour chaque burn (déjà partiellement présent dans
  `MissionRenderer`).

**Wizard et `MissionContext`.**
- Nouveau type de mission `RDV` dans `StepMissionType`, avec étape "Cible" supplémentaire dans le
  wizard.
- `MissionEntry` étendu d'un `Optional<EphemerisTarget>` (cf. §4.6).
- Réutiliser les patterns existants (`StepParameters`, formulaire, validation), pas de nouveau
  framework UI.

---

## 4. Autres sujets à explorer

Sans entrer dans le détail, points qui n'entrent pas dans §2/§3 mais qu'il faudra trancher :

1. **Fenêtre de lancement.** Le plan orbital de la cible précesse à cause de J2 (~5°/jour pour
   l'ISS). Le RAAN du lanceur dépend de la date/heure (rotation terrestre). Le lanceur doit
   attendre que la cible passe au-dessus du plan de lancement. → Nouveau pré-calcul, et UI qui
   propose des créneaux acceptables.

2. **Erreur de TLE dans le temps.** Précision dégradée de plusieurs km après quelques jours.
   Stratégies : afficher l'âge du TLE dans l'UI, recommander un refresh, warning si > 7 jours.

3. **Corridor de sécurité.** Pendant les phases de phasing et transfer, la trajectoire ne doit
   pas passer trop près de la cible avant l'instant prévu. Soit pénalité douce dans le coût,
   soit contrainte dure (rejection des solutions qui violent une distance min hors phase
   d'approche).

4. **Détecteurs Orekit.** Réutiliser l'infra `detector/` : un `DistanceDetector` (ou détecteur
   custom basé sur la position cible) pour signaler l'événement "arrivée à proximité" et
   stopper la propagation. Cohérent avec `MassDepletionDetector` / `MinAltitudeTracker`
   existants.

5. **UI wizard.** Nouveau type de mission `RDV` (à côté de LEO/GEO), nouvelle étape "Cible"
   dans le wizard (NORAD ID, ou import fichier TLE, ou choix dans une liste catégorisée).
   Pattern à reprendre de `StepMissionType` / `StepParameters`.

6. **Modèle de données.** `MissionEntry` n'a pas de champ "cible" aujourd'hui. À ajouter
   (`Optional<EphemerisTarget> target`). Le `MissionRenderer` actuel rend uniquement le
   spacecraft de la mission — il faudra un `TargetObjectRenderer` (qui mime `MissionRenderer`
   mais alimenté par la propagation TLE) pour rendre la cible visible dans la scène.

7. **Référence pédagogique.** Le profil rendez-vous ISS NASA (NCC, NSR, NPC, TI, MC1–4) est une
   bonne checklist pour vérifier qu'on couvre toutes les phases conceptuelles. Pas une obligation
   d'implémenter chaque manœuvre, mais un guide.

8. **Vue duale LVLH.** Stretch goal mais à fort retour pédagogique (cf. §3.5). Soit on
   reconfigure dynamiquement la caméra near-viewport pour suivre la cible et exprimer la
   trajectoire en LVLH, soit on ajoute un troisième viewport.

---

## 5. Recommandation MVP

| Question                  | Choix MVP                                                                   |
|---------------------------|-----------------------------------------------------------------------------|
| Cible                     | ISS, TLE shippé en dataset (option A)                                       |
| Source des éphémérides    | `TleEphemerisSource` wrappant `TLEPropagator`, **bufferisé** via `SlidingWindowEphemerisBuffer` (cf. §2.4) |
| Pipeline optimisation     | Multi-stage : insert + phasing (§3.7) + transfer Lambert (§3.6, §3.8) + CMA-ES par stage |
| Coût terminal             | ‖Δr‖ + ‖Δv‖ + ΔV total + corridor + propergol                               |
| Approche terminale HCW    | **Hors MVP** ; objectif d'arrivée : Δr < 10 km, |Δv_rel| < 10 m/s           |
| Pontryagin / PMP          | **Hors MVP** ; à reconsidérer pour un futur module low-thrust               |
| Vue LVLH                  | Stretch goal, fort impact pédagogique                                        |
| Fenêtre de lancement      | Calcul de base inclus dans le MVP (sans quoi le RDV ne converge pas)        |

---

## 6. Vérification (post-implémentation, hors scope du présent doc)

À titre indicatif, validation future :
- Test d'intégration `LEORendezvousOptimizationTest` calqué sur `LEOMissionOptimizationTest` :
  propager une mission RDV ISS, vérifier que l'écart final est sous les seuils ci-dessus.
- Démo visuelle dans l'UI : afficher chaser + ISS, observer la convergence.
- Stretch : ajouter une vue LVLH et vérifier visuellement la trajectoire d'approche.
