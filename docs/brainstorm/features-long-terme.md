# Brainstorm — Features long-terme OrbitLab

> **Baseline (hypothèse)** : la visualisation est mature (rendu solaire et planétaire de qualité, caméras polies, HUD complet) **et** toutes les missions de [`missions.md`](./missions.md) sont implémentées et stables (LEO, GTO, polaire, SSO, MEO, GEO, Molniya, lunaire, rendezvous, Lagrange, interplanétaire…).
>
> **Question** : une fois ce socle en place, **quelles features transverses** apporteraient le plus ? Ce document **ne propose volontairement aucun nouveau type de mission ou de manœuvre** — ces idées vivent dans `missions.md`.
>
> **Critères de classement** : **valeur ajoutée** (utilité, immersion, rayonnement projet) × **difficulté technique** (briques manquantes, dépendances externes).
>
> **Légende des catégories** :
> - 🎮 **Ludique / gameplay**
> - 🔬 **Scientifique / réaliste**
> - 🛠 **Tooling / plateforme**
> - 📡 **Opérationnel / mission control**
> - 📚 **Éducatif**

---

## Tier 1 — Quick wins (forte valeur, faible difficulté)

### 1. 🛠 Format de scénario importable / exportable (JSON ou YAML)

- **Intérêt** : ★★★★★ — Reproductibilité, partage de missions, base pour scoring, replays, scénarios historiques, mode batch, plugin system. Une des features les plus *enabling* du document.
- **Difficulté** : ★★☆☆☆
- **Description** : Sérialiser un scénario complet (lanceur, payload, site, paramètres orbitaux, date, seed CMA-ES, version Orekit) dans un fichier texte versionnable. Charger un scénario depuis un fichier reproduit la mission au bit près.
- **Ce qui existe déjà** : `MissionContext`, `MissionEntry`, records de configuration immuables, wizard qui collecte déjà tous ces paramètres.
- **Ce qui manque** :
  - Schéma de scénario v1 (JSON Schema ou YAML) avec champ `formatVersion`.
  - Sérialiseurs/désérialiseurs (Jackson/SnakeYAML), `ScenarioLoader` + `ScenarioWriter`.
  - Hook dans la wizard : "Importer un scénario…" / "Exporter ce scénario…".
- **Notes** : C'est la première brique à poser. Tier 2/3 (#7 scénarios historiques, #11 défis, #2 batch) en dépendent presque tous.

---

### 2. 🛠 Mode batch / headless

- **Intérêt** : ★★★★★ — Permet sweeps de paramètres, Monte-Carlo, intégration CI, benchmarks de robustesse de l'optimiseur. Indispensable pour valider les évolutions de `MissionOptimizer`.
- **Difficulté** : ★★☆☆☆
- **Description** : Lancer une (ou N) mission(s) via CLI sans démarrer JME3. Sortie : un fichier de résultats (CSV/JSON) avec ΔV, masse résiduelle, écarts à la cible, durée d'optim, status convergence.
- **Ce qui existe déjà** : `MissionOptimizer` et `propagateMission()` de `AbstractTrajectoryOptimizerTest` sont déjà découplés du rendu. Le `tools/` contient déjà des mains standalone (ephemerisgen, orbitgen) → pattern réutilisable.
- **Ce qui manque** :
  - `tools/missionrunner/` avec `MissionRunnerMain` qui consomme un scénario (#1) ou une grille de paramètres.
  - Reporter CSV/JSON.
  - Mode "sweep" : produit cartésien sur paramètres, parallélisation via `Reactor` (déjà au classpath).
- **Notes** : Couple parfaitement avec #1 et #11. Permet aussi de tourner en CI sans serveur graphique.

---

### 3. 📊 Analytics & charts post-mission

- **Intérêt** : ★★★★☆ — Donne sens aux runs : ΔV cumulé, masse résiduelle, altitude/eccentricité au cours du temps, profil de poussée. Énormément de valeur pédagogique et de debug.
- **Difficulté** : ★★☆☆☆
- **Description** : Au lieu de seulement visualiser la trajectoire 3D, afficher des graphiques 2D : `altitude(t)`, `mass(t)`, `eccentricity(t)`, `acceleration(t)`, `ΔV par stage`. Exportable en PNG/CSV.
- **Ce qui existe déjà** : `MissionEphemeris` et `MissionEphemerisGenerator` produisent déjà la trajectoire échantillonnée. Le `TelemetryWidgetAppState` montre déjà certaines valeurs en temps réel.
- **Ce qui manque** :
  - Lib de charting compatible JME3/Lemur (ou export et ouverture externe).
  - `MissionAnalytics` qui post-traite `MissionEphemeris` et calcule les agrégats par stage.
  - Une vue "rapport de mission" accessible après l'insertion.
- **Notes** : Ajoute énormément de valeur scientifique. Une version *export-only* (CSV) est un quart de la difficulté pour 80% de la valeur.

---

### 4. 🎮 Replays cinématiques

- **Intérêt** : ★★★★☆ — Spectaculaire, démo-friendly, réutilise tout le rendu existant. Excellent pour communication / screenshots / GIFs.
- **Difficulté** : ★★☆☆☆
- **Description** : Après une mission, rejouer la trajectoire avec caméras automatiques sur les événements clés (décollage, MaxQ, séparation S1, MECO, éclipse, insertion). Slider de temps, vitesse variable, mode "highlights" qui enchaîne 5–10 secondes par moment fort.
- **Ce qui existe déjà** : `EventBus` peut déjà émettre des événements de stage transition. `MissionEphemeris` permet de scrubber la trajectoire. Caméras (`OrbitCameraAppState`) déjà multiples.
- **Ce qui manque** :
  - Catalogue d'événements clés enregistrés pendant la mission (timeline d'événements horodatés).
  - `CinematicCameraDirector` qui choisit caméra + cadrage selon l'événement.
  - UI de scrubber avec marqueurs d'événements (à intégrer dans `ui/timeline/`).
- **Notes** : Une fois en place, l'export vidéo (MP4) devient une suite naturelle.

---

### 5. 📚 Tutoriels interactifs guidés

- **Intérêt** : ★★★★☆ — Onboarding immédiat, pédagogie, accessibilité aux non-experts. Multiplie l'audience potentielle du projet.
- **Difficulté** : ★★☆☆☆
- **Description** : Surcouche de la wizard existante avec annotations contextuelles ("Pourquoi 200 km ?", "C'est quoi un gravity turn ?"), objectifs progressifs ("Mission 1 : insère un satellite à 400 km"), validations automatiques.
- **Ce qui existe déjà** : `ui/mission/wizard/` est déjà organisé en steps avec un `WizardStepper`. `Badge`, `PopupList`, `ProgressBar` sont des composants réutilisables.
- **Ce qui manque** :
  - Système d'overlays/highlights sur des éléments UI (style "coach mark").
  - Scénarios pédagogiques pré-faits (utilise #1).
  - Système de progression (utilisateur + persistance).
- **Notes** : Très complémentaire de #1 (les leçons sont des scénarios) et #11 (les défis prolongent les tutos).

---

## Tier 2 — Forte valeur, difficulté moyenne

### 6. 🔬 Catalogue de débris spatiaux & TLE

- **Intérêt** : ★★★★★ — Visuellement marquant (les ~30 000 débris de l'orbite basse forment un "nuage" autour de la Terre). Pédagogiquement essentiel (problème de Kessler). Permet une feature majeure : analyse de conjonction.
- **Difficulté** : ★★★☆☆
- **Description** : Importer le catalogue TLE de CelesTrak / Space-Track, propager via SGP4 (Orekit le fournit), afficher les objets en orbite, calculer le risque de conjonction avec la mission active.
- **Ce qui existe déjà** : Orekit fournit `TLE` et `TLEPropagator`. Le pattern `EphemerisSource` est extensible. Rendu de nombreux points : à voir si le LOD existant (`BillboardIconView`) tient à 30k objets.
- **Ce qui manque** :
  - `TleEphemerisSource`, fetcher CelesTrak (offline + cache).
  - Optimisations rendu (instancing, batching) pour scaler à 30k objets.
  - Détecteur de conjonction (Orekit `EncounterLOIDetector` ou équivalent).
- **Notes** : Énorme effet "wow". Mode démo idéal pour le projet.

---

### 7. 🎮 Scénarios historiques

- **Intérêt** : ★★★★★ — "Rejouez Apollo 11", "Lancez Voyager 1", "Insérez JWST en L2". Histoire, pédagogie, spectacle. Très viral.
- **Difficulté** : ★★★☆☆
- **Description** : Bibliothèque de scénarios pré-faits avec données réelles : Apollo 11, Voyager 1/2, Hubble, JWST, Curiosity, Galileo, Cassini, Rosetta, Chang'e, Mars Pathfinder. Chacun documenté (date, site, lanceur, payload, profil).
- **Ce qui existe déjà** : Toutes les missions matures (baseline du document) couvrent les architectures de ces scénarios.
- **Ce qui manque** :
  - Pré-requis : #1 (format scénario).
  - Données vérifiées (NASA NSSDC, ESA, JPL Horizons).
  - Représentation 3D fidèle des engins (Saturn V, Voyager bus avec antenne…).
  - Validation : comparer la trajectoire propagée à la trajectoire réelle (JPL Horizons en référence) → recouvre #12.
- **Notes** : Probablement la feature la plus *marketable* du document après #6.

---

### 8. 🎮 Pannes & contingences (failure injection)

- **Intérêt** : ★★★★☆ — Tension dramatique, gameplay riche, inspiration directe Apollo 13. Permet aussi de tester la robustesse des stages/optimiseur.
- **Difficulté** : ★★★☆☆
- **Description** : Injection d'événements imprévus : extinction prématurée d'un moteur, fuite propergol, anomalie attitude, perte de télémétrie temporaire, MMOD impact. Le joueur (ou l'IA) doit replanifier.
- **Ce qui existe déjà** : Détecteurs Orekit (`MassDepletionDetector`, `MinAltitudeTrackerDetector`) — pattern à étendre. `EventBus` pour notifier l'UI.
- **Ce qui manque** :
  - Catalogue de pannes paramétrables (type, probabilité, instant, sévérité).
  - "Mode mission control" : si une panne survient, la mission est mise en pause, l'utilisateur a un budget de temps pour reconfigurer la suite.
  - Mode roguelike : une panne aléatoire par run.
- **Notes** : Couple bien avec #11 (scoring "did you save the mission?") et #17 (mission control).

---

### 9. 📡 Calcul de fenêtres de lancement

- **Intérêt** : ★★★★☆ — Fondamental pour le réalisme : on ne lance pas vers la Lune ou Mars n'importe quand. Donne sens à la date dans le wizard.
- **Difficulté** : ★★★☆☆
- **Description** : Étant donné une cible (orbite, corps céleste), calculer les fenêtres ouvertes sur les N prochains jours/mois en respectant : alignement orbital, contraintes site, météo, range safety, plane change minimum.
- **Ce qui existe déjà** : Orekit fournit la mécanique (RAAN target, alignment angle), `OrekitService` est prêt.
- **Ce qui manque** :
  - `LaunchWindowSolver` qui scanne une plage temporelle et liste les fenêtres.
  - UI : timeline des fenêtres ouvertes dans la wizard, avec ΔV requis pour chaque.
  - Porkchop plot pour les transferts interplanétaires (couple avec #3).
- **Notes** : Pour les missions interplanétaires (Hohmann Mars), c'est presque obligatoire — sinon la mission est infaisable hors fenêtre.

---

### 10. 📡 Ground tracks 2D

- **Intérêt** : ★★★★☆ — Vue iconique des centres de contrôle. Très lisible pour comprendre la couverture sol, l'inclinaison, la précession.
- **Difficulté** : ★★★☆☆
- **Description** : Carte 2D Mercator (ou autre projection) avec la trace au sol du satellite, le terminator solaire, les stations sol, les zones de visibilité.
- **Ce qui existe déjà** : Position spacecraft accessible via `MissionEphemeris`. Le calcul lat/lon est trivial via Orekit.
- **Ce qui manque** :
  - Pipeline de rendu 2D (Lemur supporte les images, ou viewport orthographique JME3 dédié).
  - Texture carte du monde (Earth) — déjà probablement dans les assets planètes existants.
  - Représentation de stations sol (DSN, ESTRACK).
- **Notes** : Préparation idéale pour #6 (visualiser les passages débris au-dessus de zones), #17 (panneau mission control), #12 (comparer trace ISS réelle).

---

### 11. 🎮 Système de scoring & défis

- **Intérêt** : ★★★★☆ — Engagement, rejouabilité, communauté. Transforme OrbitLab en *learning game* sans perdre le sérieux scientifique.
- **Difficulté** : ★★★☆☆
- **Description** : Score multi-critères par mission (ΔV consommé vs optimum théorique, précision insertion, masse payload livrée, temps mission). Défis pré-faits ("LEO 400 km avec moins de 9.4 km/s de ΔV"). Leaderboard local + export.
- **Ce qui existe déjà** : `MissionOptimizerResult` calcule déjà des métriques de qualité. Format scénario (#1) sert de support de défi.
- **Ce qui manque** :
  - Définition formelle des métriques de scoring.
  - Catalogue de défis avec contraintes paramétrables.
  - Persistance locale + format exportable de "run cartouche" (replay-able via #1).
- **Notes** : Très complémentaire de #5 et #7. Une version "leaderboard online" est un Tier 4 (#20-like).

---

### 12. 🔬 Validation contre données réelles

- **Intérêt** : ★★★★★ — Crédibilité scientifique, base pour publi/présentations, garde-fou contre régressions de précision.
- **Difficulté** : ★★★☆☆
- **Description** : Comparer les trajectoires propagées par OrbitLab à des trajectoires de référence : ISS (TLE en temps réel), Apollo 11 (NSSDC), Voyager (DSN/JPL Horizons), JWST. Rapport d'écart radial / cross-track / along-track.
- **Ce qui existe déjà** : Orekit a un haut niveau de précision et est utilisé par l'ESA. `MissionEphemeris` permet la comparaison.
- **Ce qui manque** :
  - Importeur Horizons (JPL) — API REST publique.
  - Importeur TLE (#6).
  - `TrajectoryComparator` qui calcule les écarts position/vitesse échantillon par échantillon.
  - Tests d'intégration "non-régression de précision".
- **Notes** : Pré-requis quasi-obligé pour #7 (les scénarios historiques doivent être validés).

---

## Tier 3 — Forte valeur, difficulté élevée

### 13. 🔬 Modèles de force avancés

- **Intérêt** : ★★★★☆ — Réalisme étendu : SRP indispensable au-dessus de 1000 km, drag NRLMSISE-00 pour la décroissance LEO, lunisolaire pour HEO/GEO. Permet de modéliser le station-keeping et l'usure orbitale.
- **Difficulté** : ★★★★☆
- **Description** : Activer dans le propagateur Orekit les forces : Solar Radiation Pressure, atmospheric drag NRLMSISE-00 (avec sec section + Cd), perturbations solides Lune/Soleil, marées terrestres.
- **Ce qui existe déjà** : `OrekitService` expose déjà 3 propagateurs (Simple/Optimization/Default). Orekit fournit `SolarRadiationPressure`, `DragForce`, `ThirdBodyAttraction`. Gravity 50×50 déjà actif.
- **Ce qui manque** :
  - Modèle de spacecraft enrichi (surface effective, Cd, Cr, ratio masse/surface).
  - Données environnementales (flux solaire F10.7, indices Kp) → couple avec #22.
  - Recalibration des bornes CMA-ES pour absorber la complexité.
  - Mode "fidélité réglable" dans l'UI.
- **Notes** : La réentrée (#15) et la décroissance orbitale long-terme en dépendent.

---

### 14. 🔬 Propagation N-corps & patched conics

- **Intérêt** : ★★★★★ — Pré-requis structurel pour faire des missions interplanétaires *correctes* (pas juste de la pseudo-mécanique). Permet trajectoires lunaires précises, fly-bys de Jupiter, etc.
- **Difficulté** : ★★★★☆
- **Description** : Au lieu de propager dans un référentiel central unique, gérer les transitions de SOI (Earth → Moon, Earth → Sun, Sun → Mars). Soit patched conics simple, soit propagation N-corps complète sur les transitions critiques.
- **Ce qui existe déjà** : Orekit fournit `NumericalPropagator` multi-body. `EphemerisServiceRegistry` connaît tous les corps. `SolarSystemBody` enum complet.
- **Ce qui manque** :
  - `MultiSoiPropagator` qui orchestre les transitions et bascule de référentiel central.
  - Visualisation des SOI dans la 3D.
  - Adaptation de `MissionEphemeris` pour gérer la trajectoire concaténée.
  - CMA-ES sur trajectoires multi-SOI : nouvelle classe de problèmes.
- **Notes** : La feature la plus structurellement lourde mais celle qui débloque le plus d'extensions futures.

---

### 15. 🔬 Réentrée atmosphérique avec heating

- **Intérêt** : ★★★★☆ — Spectaculaire (plasma, bow-shock, ablation), pédagogique, indispensable pour missions de retour (Apollo, Soyouz, Crew Dragon, Mars EDL).
- **Difficulté** : ★★★★☆
- **Description** : Modèle de heating (Sutton-Graves), ablation de bouclier, blackout radio entre Mach ~12 et Mach ~6. Effets visuels (plasma trail, glow). Contrainte de g-load et corridor d'entrée.
- **Ce qui existe déjà** : `MissionStage` extensible, `MinAltitudeTrackerDetector` peut être étendu pour détecter le passage d'interface atmosphérique.
- **Ce qui manque** :
  - `ReentryStage` avec modèle aéro-thermo.
  - Atmosphère NRLMSISE (recouvre #13).
  - Effets visuels (shaders, particules JME3).
  - Détecteur de g-load max et de corridor.
- **Notes** : Forte valeur démo. Couple avec #14 pour Mars EDL.

---

### 16. 🛠 API scripting (Groovy / Python via GraalVM)

- **Intérêt** : ★★★★☆ — Permet à un utilisateur avancé d'automatiser des missions, de prototyper des stages custom, d'orchestrer des sweeps. Démultiplie l'usage.
- **Difficulté** : ★★★★☆
- **Description** : Exposer un sous-ensemble du domaine (`Mission`, `Stage`, `Vehicle`, `Optimizer`) à un moteur de script. L'utilisateur écrit `mission.gvy` ou `mission.py` qui pilote la simulation.
- **Ce qui existe déjà** : Groovy 2.4.12 est déjà dans les dépendances Gradle. GraalVM permettrait Python.
- **Ce qui manque** :
  - Façade scripting stable (anti-leak vers les internes).
  - Sandbox de sécurité (un script ne doit pas exfiltrer / planter la JVM).
  - Documentation API.
  - Hook dans le CLI batch (#2) pour `--script foo.gvy`.
- **Notes** : Couple parfaitement avec #2 et #21. Effort important mais déverrouille l'extensibilité massive.

---

### 17. 📡 Tableau de bord mission control multi-écran

- **Intérêt** : ★★★★☆ — Immersion centre de contrôle, idéal pour démos publiques, valorise toutes les autres features (télémétrie, ground tracks, événements, panneaux).
- **Difficulté** : ★★★★☆
- **Description** : Layout type "MCC Houston" : grille de panneaux configurables (3D, ground track, charts, console événements, télémétrie textuelle, horloge GMT/MET, étapes mission). Multi-fenêtre / multi-écran.
- **Ce qui existe déjà** : `TelemetryWidgetAppState`, `MissionPanelWidgetAppState`, `ui/timeline/`, `EventBus`. Lemur sait gérer des layouts riches.
- **Ce qui manque** :
  - Système de fenêtrage multi-écran (JME3 supporte, mais demande du plomberie).
  - Panneaux composables (drag & drop, redimensionnables, sauvegarde de layout).
  - Console d'événements horodatés (s'appuie sur `EventBus` et le travail de #4).
- **Notes** : Pas urgent mais couronne magnifiquement #4, #6, #8, #10, #18.

---

### 18. 🔬 Délai de communication interplanétaire

- **Intérêt** : ★★★☆☆ — Réalisme fort pour missions Mars (4–24 min one-way), Jupiter (35–52 min), Voyager (>20h). Renforce l'immersion mission control.
- **Difficulté** : ★★★★☆ (en intégration UI plus qu'en physique)
- **Description** : Toute commande envoyée arrive `r/c` plus tard ; toute télémétrie reçue date de `r/c` plus tôt. Mode "live" vs mode "historisé". Le joueur doit anticiper, comme la NASA pour Curiosity.
- **Ce qui existe déjà** : Distance corps-Terre triviale via `EphemerisServiceRegistry`. `SimulationClock` event-driven peut buffer.
- **Ce qui manque** :
  - File de commandes différées avec timestamps.
  - File de télémétrie avec délai d'affichage.
  - UI "à l'instant T sur Terre, voici ce qu'on sait du satellite à T-r/c".
  - Cohérence avec #8 (panne arrive après le délai côté joueur).
- **Notes** : Niche mais impressionnante. Excellent pour scénarios Mars/Jupiter.

---

## Tier 4 — Stretch / Innovation

### 19. 🎮 Mode VR / AR

- **Intérêt** : ★★★★☆ — Immersion incomparable, voir Saturne ou la Lune en 1:1 dans un casque est inoubliable. Excellent pour communication.
- **Difficulté** : ★★★★★
- **Description** : Support OpenXR via JME3-VR. Caméra "à l'épaule" du satellite, vue cockpit LM, vue panoramique solaire.
- **Ce qui existe déjà** : JME3 a un support VR (jme3-vr).
- **Ce qui manque** :
  - Refonte du pipeline de caméras pour HMD.
  - UI Lemur en VR (très différent du desktop).
  - Optimisations performance (90/120 Hz nécessaires).
- **Notes** : Énorme effort pour une niche. Mais "OrbitLab VR" est une feature qui attire.

---

### 20. 🎮 Multi-joueurs coopératif

- **Intérêt** : ★★★☆☆ — Niche mais potentiellement viral ("CDR + Pilot" comme Apollo). Pédagogie en classe.
- **Difficulté** : ★★★★★
- **Description** : Deux joueurs : un planifie (CMA-ES, paramètres), un pilote (override manuel pendant le burn, gestion contingence). Sync horloge + état réseau.
- **Ce qui existe déjà** : `SimulationClock` event-driven facilite la sync. JME3 a `jme3-networking`.
- **Ce qui manque** :
  - Architecture client/serveur, autorité, lag compensation.
  - Anti-désync sur des simulations déterministes.
  - UI multi-rôles.
- **Notes** : Ambitieux. Probablement justifiable seulement après une maturité totale.

---

### 21. 🛠 Plugin system

- **Intérêt** : ★★★★☆ — Communauté, écosystème, longévité projet. Quelqu'un d'autre peut ajouter une mission Starship sans toucher au core.
- **Difficulté** : ★★★★★
- **Description** : Charger dynamiquement des `MissionStage`, `Maneuver`, `Objective`, `LaunchVehicle` depuis des JAR/dossiers externes. Manifest, isolation classloader, API stable.
- **Ce qui existe déjà** : Sealed interfaces (`MissionObjective`) imposent une fermeture qu'il faudra ouvrir avec discernement (extension points dédiés).
- **Ce qui manque** :
  - Définition d'API publique (vs internal).
  - Mécanisme de découverte (`ServiceLoader` ou dossier `plugins/`).
  - Sandboxing.
  - Versionning d'API.
- **Notes** : Couple intimement avec #16. À envisager seulement après stabilisation longue du core.

---

### 22. 🔬 Météo spatiale

- **Intérêt** : ★★★☆☆ — Renforce le réalisme de #13 (drag réel varie avec F10.7/Kp). Permet de simuler une CME qui chauffe la thermosphère et fait re-rentrer un satellite.
- **Difficulté** : ★★★★☆
- **Description** : Données indices Kp/F10.7 (NOAA SWPC), événements CME, intégration au modèle de drag.
- **Ce qui existe déjà** : Rien de dédié. #13 est pré-requis.
- **Ce qui manque** :
  - Importeur NOAA.
  - Coupling avec atmosphère NRLMSISE.
  - Représentation visuelle des événements (aurores, alertes mission control).
- **Notes** : Très niche. À envisager dans une orientation "réalisme extrême".

---

### 23. 🎮 Mode « What if » Hollywood

- **Intérêt** : ★★★☆☆ — Marketing pur. *Armageddon* (déviation d'astéroïde par DART-like), *Don't Look Up*, *sauver Apollo 13*, *atterrir sur 67P comme Philae*.
- **Difficulté** : ★★★★☆
- **Description** : Scénarios extraordinaires ouvertement gamifiés, sans prétention scientifique stricte mais cohérents avec la physique sous-jacente.
- **Ce qui existe déjà** : Une fois #14 (multi-SOI) et #1 (scénarios) en place, c'est principalement du content-design.
- **Ce qui manque** :
  - Modélisation NEO (catalogue d'astéroïdes, propriétés physiques).
  - Modèle d'impact de déviation (kinetic impactor, traction gravitationnelle).
  - Storytelling / scripting cinématique.
- **Notes** : Couplé à #4 et #11, c'est *le* mode de démo grand public.

---

## Synthèse — Matrice valeur × difficulté

| Difficulté ↓ / Valeur → | **Élevée**                                                    | **Moyenne**                  | **Faible**           |
|-------------------------|---------------------------------------------------------------|------------------------------|----------------------|
| **Faible (★–★★)**       | #1, #2, #3, #4, #5                                            |                              |                      |
| **Moyenne (★★★)**       | #6, #7, #9, #10, #11, #12                                     | #8                           |                      |
| **Élevée (★★★★+)**      | #13, #14, #15, #16, #17, #19, #21                             | #18, #22, #23                | #20                  |

> **Lecture** : la diagonale "haute valeur / faible difficulté" en haut à gauche (#1–#5) est le quadrant *quick wins*. Le quadrant en bas à gauche (#13–#17) est l'investissement long-terme à plus fort impact.

---

## Roadmap suggérée

1. **Poser le socle d'extensibilité** : #1 (scénario sérialisable), puis #2 (batch headless) et #3 (analytics). Ces trois features débloquent presque toutes les autres.
2. **Construire la valeur scientifique** : #12 (validation), puis #13 (forces avancées) → ouvre la porte à #14 (multi-SOI) et #15 (réentrée).
3. **Construire la valeur expérience** : #4 (replays), #5 (tutoriels), #7 (scénarios historiques), #11 (scoring). Ce sont eux qui génèrent l'attrait grand public.
4. **Couronner avec l'opérationnel** : #6 (TLE/débris), #10 (ground tracks), #17 (mission control). Une fois tout cela en place, OrbitLab est devenu un outil de classe mondiale.
5. **Stretch** : VR, multi-joueur, plugin system — uniquement si la communauté et le besoin émergent.

---

## Fichiers existants à réutiliser

| Feature(s)        | Brique existante                                                                       |
|-------------------|-----------------------------------------------------------------------------------------|
| #1, #7, #11       | `simulation/mission/runtime/MissionContext`, `MissionEntry`, configs immuables          |
| #2                | `simulation/mission/runtime/MissionOptimizer`, pattern `tools/ephemerisgen`             |
| #3, #4, #18       | `simulation/mission/ephemeris/MissionEphemeris(Generator)`, `engine/events/EventBus`    |
| #4, #8, #17       | `engine/events/EventBus`, `states/mission/`                                             |
| #5                | `ui/mission/wizard/`, `WizardStepper`, `Badge`                                          |
| #6, #12           | `simulation/source/` (`OrekitPvSource`, `DatasetEphemerisSource`, `LruCache`)           |
| #8                | `simulation/mission/detector/MassDepletionDetector`, `MinAltitudeTrackerDetector`        |
| #9, #13, #14, #15 | `simulation/OrekitService` (3 propagateurs déjà disponibles)                            |
| #10               | `engine/scene/`, viewport orthographique JME3                                            |
| #16, #21          | Groovy 2.4.12 déjà au classpath, sealed interfaces de `MissionObjective`                |

---

*Ce document est un brainstorm exploratoire. Chaque idée retenue mérite sa propre spec d'implémentation (cf. `01-cmaes-retry-acceptance.md`, `02-gto-mission-integration.md` comme modèles).*
