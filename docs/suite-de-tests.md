# docs/suite-de-tests.md — recensement des durées et état d'exécution

Photographie **mesurée** de ce que coûte la suite de tests et de ce qu'elle exécute
réellement, au **2026-09-10**, commit `e950178`. Ce document ne contient aucune décision :
il consigne des chiffres et deux constats de câblage. Les items qui méritent d'être
planifiés doivent être promus en fiche dans [`dette-technique.md`](dette-technique.md) ou
[`bugs.md`](bugs.md) — le §6 dit lesquels et pourquoi ils ne sont pas ici.

Il est né d'une demande simple : « lance tous les tests, y compris les longs ». Il a fallu
**7 h 05** de machine pour découvrir que la moitié de la réponse tenait en deux classes, et
que « tous » n'était pas atteignable par le build.

---

## 1. Conditions de mesure

| | |
|---|---|
| Date | 2026-09-10 |
| Commit | `e950178` « PHY-8: close the chantier, record what it leaves behind » |
| Branche | `PHY-8` |
| Arbre | propre, hors `docs/v2-preparation/__pycache__/` non suivi |
| JDK | GraalVM 21.0.5 |
| Machine | 12 cœurs logiques |
| Agent JaCoCo | **actif** (configuration normale de `test`) |
| Parallélisme | aucun — ni `forkEvery` ni `maxParallelForks` sur `test` |

Deux conséquences de la dernière ligne, et elles comptent pour lire les tableaux du §3 :
la somme des durées de classes **égale** le temps mur (17 018 s contre 4 h 43 min 52 s de
build), et une classe lente ne peut pas être masquée par une autre. Les durées absolues
portent en revanche le surcoût d'instrumentation de JaCoCo ; la hiérarchie, elle, n'en
dépend pas.

---

## 2. Les trois instruments

| Passe | Commande | Durée | Résultat |
|---|---|---|---|
| 1 — suite complète | `cleanTest test -Dorbitlab.slowTests=true` | 4 h 43 min 52 s | 1427 tests, **1 rouge**, 6 sautés |
| 2 — épinglages | `gateTest --rerun` | 3 min 21 s | 11 tests, **0 rouge**, 0 sauté |
| 3 — tests hors de portée | `test -I <init>` posant `orbitlab.fullTests` et `orekitDataZip` | 2 h 17 min 30 s, **interrompue** | 2 exécutés, **2 rouges**, 1 en vol à la coupure |

La passe 3 a été arrêtée à la demande, les tests visés ayant été déclarés obsolètes et à
reprendre. Ce qu'elle avait déjà produit est au §4.2 : c'est peu, et c'est suffisant.

---

## 3. Le recensement des durées

### 3.1 La distribution

| | Classes | Tests | Durée |
|---|---:|---:|---:|
| Derrière `@EnabledIfSystemProperty("orbitlab.slowTests")` | **11** | **29** | **16 843,7 s** (99,0 %) |
| Tout le reste | 195 | 1398 | 173,9 s (1,0 %) |
| **Total** | **206** | **1427** | **17 017,7 s** |

**Vingt-neuf tests portent 99 % du temps.** Les 1398 autres tiennent en 2 min 54 s, dont
177 classes sous la seconde qui cumulent 11,5 s à elles toutes. Activer `slowTests`
multiplie la suite par **98**.

### 3.2 Palier 1 — les deux monstres, 78 % du total

| Classe | Durée | Tests |
|---|---:|---:|
| `LEOMissionOptimizedTransferTest` | **1 h 53 min 25 s** | 3 |
| `ScenarioReplayTest` | **1 h 50 min 13 s** | 2 |

Cinq tests. 3 h 43 min.

### 3.3 Palier 2 — les lourds, 20 % du total

| Classe | Durée | Tests |
|---|---:|---:|
| `BoosterSplitBaselineTest` | 19 min 17 s | 6 |
| `LEOMissionOptimizationTest` | 13 min 55 s | 9 |
| `LunarFlybyFlightTest` | 8 min 21 s | 2 |
| `LunarOrbitFlightTest` | 5 min 11 s | 1 |
| `Ariane64MissionTest` | 4 min 50 s | 1 |
| `AscentBaselineN2Test` | 2 min 23 s | 2 |
| `GEOMissionOptimizationTest` | 1 min 27 s | 1 |
| `MeoMissionTest` | 1 min 25 s | 1 |
| `TranslunarBoundaryFlightTest` | 17 s | 1 |

### 3.4 Palier 3 — la suite par défaut, 2 min 54 s

Ce que paie qui lance `./gradlew test` sans rien. Seules neuf classes y dépassent 5 s, et
les huit premières font 78 % du palier :

| Classe | Durée | Tests |
|---|---:|---:|
| `GeoInclinationDriftTest` | 31,4 s | 1 |
| `LunarLaunchWindowFlightTest` | 28,2 s | 1 |
| `RingShadowMeasureTest` | 20,5 s | 1 |
| `TranslunarFiniteBurnTest` | 17,0 s | 2 |
| `CalibrationReadingTest` | 11,5 s | 3 |
| `TranslunarDepartureFlightTest` | 10,8 s | 1 |
| `PlanetMeshFrameFixtureTest` | 9,4 s | 3 |
| `HudMarkerBehindCameraMeasureTest` | 6,5 s | 4 |
| `OrbitElementsTest` | 6,3 s | 7 |

`GeoInclinationDriftTest` est en tête **délibérément** : son Javadoc dit qu'il est laissé
dans la suite par défaut plutôt que derrière `slowTests`.

### 3.5 Les méthodes

| Méthode | Durée |
|---|---:|
| `LEOMissionOptimizedTransferTest.testFalconHeavyOptimizedTransfer` *(rouge)* | 58 min 40 s |
| `ScenarioReplayTest.replayReachesTheOrbitTheOptimizationFound` | 55 min 18 s |
| `ScenarioReplayTest.replayedStagesReportNoEvaluation` | 54 min 55 s |
| `LEOMissionOptimizedTransferTest` `perigee=600_000m, apogee=800_000m` | 30 min 39 s |
| `LEOMissionOptimizedTransferTest` `targetAltitude=600000.0m` | 24 min 06 s |
| `LunarOrbitFlightTest` (méthode unique) | 5 min 11 s |
| `Ariane64MissionTest.ariane64_leo400km_insertsAndStagesWhereTheModelSaysItDoes` | 4 min 50 s |
| `LunarFlybyFlightTest` — « The budget's own sizing flies the same flyby » | 4 min 38 s |
| `BoosterSplitBaselineTest.falconHeavyLunarOrbit` | 4 min 31 s |
| `BoosterSplitBaselineTest.falconHeavyLunarFlyby` | 4 min 17 s |

Ces cinq méthodes **sont** le palier 1 — `LEOMissionOptimizedTransferTest` en a trois,
`ScenarioReplayTest` en a deux — et valent donc les mêmes **78,8 %** du run.

### 3.6 Les épinglages, isolés

`gateTest` lance les quatre non-régressions à `forkEvery = 1`, une JVM par classe.

| Classe | Durée | Tests |
|---|---:|---:|
| `AscentBaselineN2Test` | 2 min 34 s | 2 |
| `CentralBodyBaselineTest` | 20,5 s | 4 |
| `MissionPolylineBaselineTest` | 8,6 s | 1 |
| `EarthOrbitNonRegressionTest` | 7,1 s | 4 |

**Mesuré :** 11 tests, 0 sauté, 0 rouge, 3 min 21 s — contre 2 min 58 s le 2026-09-08, à
contenu identique. `AscentBaselineN2Test` coûte 2 min 34 s isolé contre 2 min 23 s dans la
suite complète : la classe n'est pas sensible à l'isolement, et les 62 frontières tiennent.

---

## 4. Les rouges

### 4.1 `LEOMissionOptimizedTransferTest.testFalconHeavyOptimizedTransfer`

**Mesuré.** Échec à
[`LEOMissionOptimizedTransferTest.java:57`](../src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/LEOMissionOptimizedTransferTest.java#L57),
assertion levée dans `AbstractTrajectoryOptimizerTest.testMission` (ligne 165) :

```
Max flown altitude 1271091 m not within 28000 m of target apogee 400000 m
```

L'apogée volée est à **1 271 km pour une cible de 400 km** — un facteur 3, pas une tolérance
qui frotte. Les deux autres méthodes de la classe (600/800 km et 600 km) passent. C'est le
**seul** rouge de la suite normale.

**Inféré, non vérifié.** La branche `PHY-8` vient de déplacer la réservation de Δv
d'insertion sur l'étage supérieur (`61ab2bb`). Le lien avec cet écart est plausible et n'a
pas été instruit.

### 4.2 `PropellantLoadOptimizerIntegrationTest` — les deux LEO

**Mesuré.** Les deux tests échouent sur la même assertion, `result.feasible()` :

| Test | Durée | Résultat consigné |
|---|---:|---|
| `leo400km_shrinksHeuristicLoads_andStaysFeasible` ([:173](../src/test/java/com/smousseur/orbitlab/simulation/mission/runtime/PropellantLoadOptimizerIntegrationTest.java#L173)) | 61 min 56 s | `feasible=false, λ*=1.0, evals=1` |
| `leoMultiStage_shrinksEveryVariableLoadStage` ([:400](../src/test/java/com/smousseur/orbitlab/simulation/mission/runtime/PropellantLoadOptimizerIntegrationTest.java#L400)) | 61 min 54 s | `λ(S1)=1,0000`, puis `feasible=false` |

Deux choses à retenir. D'abord **l'échec est en amont de ce que le test mesure** : les
charges heuristiques elles-mêmes ne sont plus faisables, donc la boucle de rétrécissement
n'a rien à rétrécir — le test ne dit rien de `PropellantLoadOptimizer`, il dit que son point
de départ est mort. Ensuite `evals=1` : **une seule évaluation coûte l'heure**.

Le cas GEO à λ simple était en vol au moment de la coupure ; sa sonde de borne basse est
consignée :

```
Probe λ=0.3 (lower bound): feasible=false
[GTO injection] injection out of reach: burning all 904 kg left in the active stage
(3.146 s at full thrust, Δv 2393 m/s) still leaves the apogee 33775 km short of the
35786 km target — the stage cannot perform this injection
```

`geoMultiStage_shrinksEveryVariableLoadStage` n'a jamais démarré.

**Tranché le 2026-09-10 :** ces quatre tests sont obsolètes et à reprendre. Ils ne sont donc
pas une fiche `bugs.md` — voir §6.

---

## 5. Ce que « tous les tests » n'atteint pas

### 5.1 `orbitlab.fullTests` n'est câblé nulle part

**Mesuré.** `PropellantLoadOptimizerIntegrationTest` porte
`@EnabledIfSystemProperty(named = "orbitlab.fullTests", matches = "true")`. Or le bloc
`test` de [`build.gradle`](../build.gradle) ne forwarde que deux propriétés au JVM de test :

```groovy
systemProperty 'orbitlab.slowTests', System.getProperty('orbitlab.slowTests', 'false')
systemProperty 'orbitlab.probe',     System.getProperty('orbitlab.probe', 'false')
```

Une propriété passée à Gradle ne descend pas d'elle-même dans le JVM forké. **Ces quatre
tests sont donc injoignables par Gradle, quelle que soit la ligne de commande** ; seul l'IDE,
qui pose le `-D` directement sur le JVM de test, peut les lancer. C'est la raison pour
laquelle ils ont pourri sans qu'aucun rouge n'apparaisse jamais.

**Contournement sans toucher au dépôt**, utilisé pour la passe 3 :

```groovy
// full-tests.gradle, passé par ./gradlew test -I full-tests.gradle
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'orbitlab.fullTests', 'true'
        systemProperty 'orekitDataZip', new File(rootDir, 'src/main/resources/orekit-data.zip').absolutePath
    }
}
```

**Inféré.** La garde `orbitlab.probe`, elle, *est* forwardée ; aucune classe de test ne la
lit. C'est le motif inverse, et il n'a pas été instruit.

### 5.2 Les deux smoke tests ephemerisgen

**Mesuré.** `EphemerisDatasetSmokeTest` et `EphemerisDatasetFileSmokeTest` sautent sur
`Assumption failed: Missing -DorekitDataZip=...`, alors que le zip attendu est tracké à
`src/main/resources/orekit-data.zip`. Ils sont deux des six sautés de la passe 1, les quatre
autres étant ceux du §5.1. La passe 3 a été coupée avant de les atteindre : **ils restent
non mesurés à ce jour.**

---

## 6. Ce que ce document ne fait pas, et ce qu'il corrige

**Il n'ouvre aucune fiche.** Trois constats mériteraient d'être promus, et la décision n'est
pas la sienne :

1. le câblage manquant de `orbitlab.fullTests` (§5.1) — candidat `dette-technique.md`, c'est
   un défaut d'outillage, pas de comportement ;
2. l'assumption silencieuse des deux smoke tests (§5.2) — même famille, moindre gravité ;
3. le rouge de `testFalconHeavyOptimizedTransfer` (§4.1) — candidat `bugs.md`, mais il faut
   d'abord instruire le lien avec `61ab2bb`.

Les quatre `PropellantLoadOptimizerIntegrationTest` ne sont dans aucune de ces trois lignes :
ils ont été déclarés à reprendre, ce qui est une décision de conception et non un défaut à
consigner.

**Corrections apportées en écrivant.** Le premier relevé oral de ce recensement portait
deux erreurs, toutes deux redressées dans les tableaux du §3 :

- il rangeait `TranslunarBoundaryFlightTest` dans la suite par défaut, alors qu'il est
  derrière `slowTests`. Le palier 3 compte donc **195** classes et non 196, et ses huit
  premières en font **78 %** et non 74 % ;
- il annonçait « cinq méthodes = 85 % du run ». La somme exacte est **78,8 %** ; les 85 %
  étaient ceux des *trois* premières classes, `BoosterSplitBaselineTest` compris.
