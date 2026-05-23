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

### 3.6 Lambert solver

- Disponible dans Orekit (`org.orekit.utils.IodLambert`, plus les outils analytiques de la
  bibliothèque). Étant données `r1`, `r2`, TOF, retourne `v1` et `v2` (vitesses requises au
  départ et à l'arrivée), donc les Δv des deux burns du transfert.
- **Multi-revolution Lambert** : pour les TOF longs (> 1 période), plusieurs solutions existent
  (k = 0, 1, 2…). Important pour les phasings longs ; à exposer comme paramètre de l'optimiseur.
- Sert d'initial guess analytique au CMA-ES du stage Transfer, comme le seed Hohmann actuel
  dans `TransferProblem.buildAnalyticalSeed`.

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
| Pipeline optimisation     | Multi-stage : insert + phasing + transfer (Lambert seed) + CMA-ES par stage |
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
