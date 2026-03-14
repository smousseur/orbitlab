# OrbitLab — Rapport d'analyse qualité (type SonarQube)

**Date :** 2026-03-14
**Branche analysée :** `claude/sonar-code-report-NiKUY`
**Fichiers analysés :** 115 fichiers Java

---

## Résumé

| Sévérité   | Bugs | Code Smells | Sécurité | Couverture | Total |
|------------|------|-------------|----------|------------|-------|
| Critique   | 3    | 0           | 0        | 0          | **3** |
| Majeur     | 5    | 3           | 0        | 2          | **10**|
| Mineur     | 2    | 13          | 2        | 0          | **17**|
| Info       | 0    | 4           | 0        | 0          | **4** |
| **Total**  | **10**| **20**     | **2**    | **2**      | **32**|

---

## Bugs critiques

### BUG-01 — Fuite de ressource dans `DatasetEphemerisSource.BodyFile.open()`
- **Fichier :** `simulation/source/DatasetEphemerisSource.java`, lignes ~216-240
- **Sévérité :** Critique
- **Description :** Si une `IOException` est levée après l'ouverture du `FileChannel` (dans `readHeaderV1` ou `readIndexV1`), le channel n'est jamais fermé. Le bloc `catch` ne capture que `RuntimeException`, laissant fuir les handles de fichiers système.
- **Code problématique :**
  ```java
  FileChannel ch = FileChannel.open(path, StandardOpenOption.READ);
  try {
      HeaderV1 hdr = readHeaderV1(body, ch, path);
      IndexV1 idx = readIndexV1(ch, hdr);
      return new BodyFile(...);
  } catch (RuntimeException e) {  // IOException non capturée → fuite de FileChannel
      ch.close();
      throw e;
  }
  ```
- **Correction recommandée :** Utiliser un `try-with-resources` ou élargir le `catch` à `Exception`.

---

### BUG-02 — Race condition dans l'initialisation du singleton `AssetFactory`
- **Fichier :** `engine/AssetFactory.java`, lignes ~65-82
- **Sévérité :** Critique
- **Description :** La méthode `init()` vérifie `Holder.INSTANCE == null` sans synchronisation. En environnement multi-thread (ex. : JME3 au démarrage), deux threads peuvent passer la garde simultanément.
- **Code problématique :**
  ```java
  public static void init(AssetManager assetManager) {
      if (Holder.INSTANCE == null) {  // Non synchronisé → race condition
          Holder.INSTANCE = new AssetFactory(assetManager);
      }
  }
  ```
- **Correction recommandée :** Synchroniser `init()` ou utiliser un `AtomicReference.compareAndSet`.

---

### BUG-03 — Double-checked locking potentiellement insuffisant dans `OrekitService`
- **Fichier :** `simulation/OrekitService.java`, lignes ~104-128
- **Sévérité :** Critique
- **Description :** Les champs `fullGravityModel` et `lightGravityModel` sont `volatile`, ce qui est correct pour le double-checked locking. Toutefois, la portée du bloc `synchronized` mériterait une revue pour garantir la visibilité complète de l'objet construit avant publication.
- **Correction recommandée :** Vérifier que toutes les écritures internes aux constructeurs des modèles de gravité sont bien visibles avant la lecture via le champ `volatile`.

---

## Bugs majeurs

### BUG-04 — Accès hors-bornes potentiel dans `Mission.getCurrentStage()`
- **Fichier :** `simulation/mission/Mission.java`, lignes ~156-158
- **Sévérité :** Majeur
- **Description :** `stages.get(currentStageIndex)` est appelé sans vérifier que `currentStageIndex < stages.size()`. Si la mission est terminée, une `IndexOutOfBoundsException` peut être levée.
- **Code problématique :**
  ```java
  public MissionStage getCurrentStage() {
      return stages.get(currentStageIndex);  // Pas de vérification de bornes
  }
  ```

---

### BUG-05 — `propagator` potentiellement null dans `Mission.update()`
- **Fichier :** `simulation/mission/Mission.java`, lignes ~56-74
- **Sévérité :** Majeur
- **Description :** Si `start()` n'a jamais été appelé, `propagator` est null. L'appel à `propagator.propagate(currentTime)` provoquera une `NullPointerException`.

---

### BUG-06 — Mutation non synchronisée dans `VehicleStack.jettison()`
- **Fichier :** `simulation/mission/vehicle/VehicleStack.java`, lignes ~43-45
- **Sévérité :** Majeur
- **Description :** La liste `vehicles` est mutée directement sans synchronisation. En contexte multi-thread (propagation + UI), cela peut provoquer une `ConcurrentModificationException`.

---

### BUG-07 — `getChild()` peut retourner null dans `OrbitRuntimeAppState`
- **Fichier :** `states/orbits/OrbitRuntimeAppState.java`, ligne ~192
- **Sévérité :** Majeur
- **Description :** Le cast `(Geometry) n.getChild("OrbitLine-" + body.name())` sera levé en `NullPointerException` si l'enfant n'existe pas.

---

### BUG-08 — `println` au lieu du logger dans `EphemerisWorker.tickSafe()`
- **Fichier :** `simulation/ephemeris/EphemerisWorker.java`, lignes ~65-71
- **Sévérité :** Majeur
- **Description :** `t.printStackTrace()` contourne Log4j2 et écrit sur stderr. Les erreurs de propagation éphéméride peuvent passer inaperçues en production.
- **Correction recommandée :**
  ```java
  } catch (Throwable t) {
      logger.error("Unexpected error in ephemeris tick", t);
  }
  ```

---

## Code Smells majeurs

### CS-01 — Exceptions silencieuses dans `EphemerisAppState.cleanup()`
- **Fichier :** `states/ephemeris/EphemerisAppState.java`, lignes ~102-121
- **Sévérité :** Majeur
- **Description :** Le bloc `catch (Exception e)` est vide (ou avec commentaire `// ignore`). Les échecs de libération de ressources sont perdus sans trace.
- **Correction recommandée :** Ajouter au minimum `logger.warn("Failed to close clock subscription", e)`.

---

### CS-02 — Code dupliqué — interpolation de Hermite
- **Fichiers :** `SlidingWindowEphemerisBuffer.java` et `DatasetEphemerisSource.java`
- **Sévérité :** Majeur
- **Description :** L'algorithme d'interpolation de Hermite (hermite + slerp) est dupliqué dans deux classes distinctes. Violation du principe DRY — toute correction doit être reportée dans les deux endroits.
- **Correction recommandée :** Extraire dans une classe utilitaire `EphemerisInterpolator`.

---

### CS-03 — Lacunes de couverture de tests — `Physics.applyPitchKick()`
- **Fichier :** `simulation/Physics.java`, lignes ~71-115
- **Sévérité :** Majeur
- **Description :** Le calcul vectoriel 3D de la phase de gravity turn n'est pas couvert par des tests unitaires. Les cas limites (vecteur de vitesse nul, attitude singulière) peuvent produire des NaN silencieux.

---

### CS-04 — Lacunes de couverture — `OrbitPathCache` : chemins d'exception
- **Fichier :** `simulation/orbit/OrbitPathCache.java`, lignes ~58-59
- **Sévérité :** Majeur
- **Description :** Le comportement de rejet pour `SolarSystemBody.SUN` et la logique de fallback (propagateur de secours) ne sont pas testés.

---

## Code Smells mineurs

### CS-05 — Nombres magiques dans les problèmes d'optimisation
- **Fichiers :** `optimizer/problems/GravityTurnProblem.java`, `TransferTwoManeuverProblem.java`
- **Sévérité :** Mineur
- **Description :** Coefficients de coût (`8.0`, `100.0`, `30_000`, `9.e-5`) sans constante nommée ni commentaire explicatif.
- **Exemple :**
  ```java
  cost += 8.0 * sq((constraints.targetApogee() - apogee) / constraints.targetApogee());
  if (alt < 30_000) cost += 100.0 * sq((30_000 - alt) / 30_000);
  ```

---

### CS-06 — `ABS_SPEED` non final dans `TimelineWidget`
- **Fichier :** `ui/clock/TimelineWidget.java`, lignes ~14-32
- **Sévérité :** Mineur
- **Description :** Le tableau statique `ABS_SPEED` n'est pas déclaré `final`, permettant une réassignation externe accidentelle.

---

### CS-07 — Chemin de dataset en dur dans `EphemerisAppState`
- **Fichier :** `states/ephemeris/EphemerisAppState.java`, ligne ~78
- **Sévérité :** Mineur
- **Description :** `Path.of("dataset", "ephemeris")` est un chemin relatif codé en dur. Devrait être externalisé dans `SimulationConfig`.

---

### CS-08 — Cyclomatic complexity élevée dans `CMAESTrajectoryOptimizer.optimize()`
- **Fichier :** `simulation/mission/optimizer/CMAESTrajectoryOptimizer.java`, lignes ~96-154
- **Sévérité :** Mineur
- **Description :** La boucle d'exploration atteint 3-4 niveaux d'imbrication avec de nombreuses branches. Complexité cyclomatique estimée > 12.
- **Correction recommandée :** Extraire `runSingleExploration()` et `selectBestResult()`.

---

### CS-09 — Création répétée de `MersenneTwister` dans `perturbLocal/perturbGlobal`
- **Fichier :** `simulation/mission/optimizer/CMAESTrajectoryOptimizer.java`, lignes ~223-228
- **Sévérité :** Mineur
- **Description :** Un nouvel objet `MersenneTwister` est instancié à chaque appel. Sur des milliers d'itérations, cela génère une pression GC inutile.
- **Correction recommandée :** Utiliser un `ThreadLocal<MersenneTwister>` ou un champ d'instance.

---

### CS-10 — Code commenté dans `MissionPlayer.play()`
- **Fichier :** `simulation/mission/runtime/MissionPlayer.java`, ligne ~38
- **Sévérité :** Mineur
- **Description :** `// mission.start(startDate);` est commenté sans explication. Implémentation incomplète ou résidu de débogage.

---

### CS-11 — TODO non résolu dans `TimelineWidget`
- **Fichier :** `ui/clock/TimelineWidget.java`, ligne ~100
- **Sévérité :** Mineur
- **Description :** Commentaire `// TODO resync` indiquant une fonctionnalité non implémentée. Devrait être tracé dans le système de tickets.

---

### CS-12 — Division par zéro silencieuse dans `Physics.getLaunchAzimuth()`
- **Fichier :** `simulation/Physics.java`, lignes ~53-59
- **Sévérité :** Mineur
- **Description :** Si `launchLatitude = π/2` (pôle), `cos(launchLatitude) = 0`, provoquant une division par zéro résultant en `NaN` silencieux.
- **Correction recommandée :**
  ```java
  double cosLat = FastMath.cos(launchLatitude);
  if (FastMath.abs(cosLat) < 1e-10) throw new IllegalArgumentException("Latitude trop proche du pôle");
  result = FastMath.asin(FastMath.cos(targetInclination) / cosLat);
  ```

---

### CS-13 — Logging excessif toutes les 10 s dans `Mission.update()`
- **Fichier :** `simulation/mission/Mission.java`, lignes ~62-72
- **Sévérité :** Info
- **Description :** Les logs d'altitude en simulation longue peuvent saturer les fichiers de log. Envisager un niveau DEBUG ou un intervalle configurable.

---

### CS-14 — Absence de JavaDoc sur les méthodes publiques critiques
- **Fichiers :** `SimulationClock`, `Mission`, `OrbitPathCache`, `DatasetEphemerisSource`
- **Sévérité :** Info
- **Description :** Les méthodes publiques complexes manquent de documentation contractuelle (préconditions, postconditions, exceptions levées).

---

## Problèmes de sécurité (mineurs)

### SEC-01 — Construction de chemin de fichier sans canonicalisation
- **Fichier :** `simulation/source/DatasetEphemerisSource.java`, ligne ~72
- **Sévérité :** Mineur
- **Description :** `datasetDir.resolve(b.name() + ".bin")` est sécurisé car `b` est une enum. Toutefois, le pattern serait risqué si la source des noms devenait user-controlled. Ajouter `.normalize()` comme défense en profondeur.

---

### SEC-02 — Possibilité d'épuisement de ressources dans l'optimiseur
- **Fichier :** `simulation/mission/optimizer/CMAESTrajectoryOptimizer.java`
- **Sévérité :** Mineur
- **Description :** Aucune limite de temps absolute n'est imposée sur l'optimisation globale. En cas de convergence très lente, l'optimiseur peut monopoliser le CPU indéfiniment si `maxEvaluations` n'est pas correctement calibré.

---

## Recommandations prioritaires

| Priorité | Action |
|----------|--------|
| P1 | Corriger la fuite de `FileChannel` dans `DatasetEphemerisSource.BodyFile.open()` |
| P2 | Synchroniser `AssetFactory.init()` |
| P3 | Remplacer `t.printStackTrace()` par `logger.error(...)` dans `EphemerisWorker` |
| P4 | Ajouter une vérification de bornes dans `Mission.getCurrentStage()` |
| P5 | Corriger la gestion de `null` de `propagator` dans `Mission.update()` |
| P6 | Logger les exceptions dans `EphemerisAppState.cleanup()` |
| P7 | Extraire la logique d'interpolation dupliquée dans une classe utilitaire |
| P8 | Ajouter des tests unitaires pour `Physics.applyPitchKick()` et `OrbitPathCache` |
| P9 | Valider la division par zéro dans `Physics.getLaunchAzimuth()` |
| P10 | Externaliser le chemin `dataset/ephemeris` vers `SimulationConfig` |
