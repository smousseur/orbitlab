# MIS-5 / L2 — Les deux mesures terrestres en dur

Lot **L2** du découpage ([`01-decoupage.md`](01-decoupage.md) §4), conçu sur la baseline de
[`02-baseline-L0.md`](02-baseline-L0.md). Il rend vraie **une** propriété :

> **Une apside et une période se lisent sur le corps de l'arc, et non sur la Terre.**

Rien d'autre. Le lot ne change aucune trajectoire et n'ajoute aucun étage : il répare trois
lectures. Sa non-régression est **structurelle et non mesurée** — les deux constantes remplacées
sont, pour un arc terrestre, exactement les doubles que les nouvelles sources rendent.

`L2` est indépendant de `L1` et se fait à n'importe quel moment avant `L5`, qui est son premier
consommateur de production.

**Trois faits mesurés contredisent le découpage ou la baseline.** Ils sont au §1.2, et le premier
change le chiffre que `L5` fera tenir.

---

## 1. Inventaire mesuré

### 1.1 Ce que le lot touche

| Fichier | Taille | Ce qui bouge |
|---|---|---|
| [`OrbitElements`](../../src/main/java/com/smousseur/orbitlab/simulation/OrbitElements.java) | 143 l. | une constante retirée, un paramètre sur trois méthodes |
| [`AchievedOrbit`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/AchievedOrbit.java) | 105 l. | un paramètre sur `of` |
| [`MissionHorizon`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/MissionHorizon.java) | 262 l. | une ligne, un import retiré |
| [`MissionOptimizer`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionOptimizer.java) | 578 l. | deux lignes avant `:237` |
| [`DynamicParameters`](../../src/main/java/com/smousseur/orbitlab/ui/mission/wizard/step/params/DynamicParameters.java) | 363 l. | un paramètre sur `revolutionDays`, un import retiré |
| [`EarthOrbitDynamicParameters`](../../src/main/java/com/smousseur/orbitlab/ui/mission/wizard/step/params/EarthOrbitDynamicParameters.java) | 579 l. | un site d'appel |
| [`GEODynamicParameters`](../../src/main/java/com/smousseur/orbitlab/ui/mission/wizard/step/params/GEODynamicParameters.java) | 96 l. | un site d'appel, un import retiré |
| [`FlownBandAim`](../../src/main/java/com/smousseur/orbitlab/simulation/FlownBandAim.java) | 140 l. | un argument passé |

Huit fichiers, dont cinq d'une ou deux lignes. Les trois sites que le découpage nomme sont
`OrbitElements:44` (appliqué en `:127`), `MissionHorizon:155` et `DynamicParameters:158`.

### 1.2 Trois faits que le découpage et la baseline ne connaissent pas

**1. L'erreur de `Revolutions` est un facteur 25,3, et non 9,0.** Mesuré sur le chemin de
production, avec un état sélénocentrique circulaire à 100 km :

```
Revolutions(12).finalCoastSeconds  =   3 355,88 s
12 révolutions réelles             =  84 809,52 s   (T = 7 067,46 s)
rapport = 25,27       période rendue par le code = 279,66 s
```

Le 9,017 est l'arithmétique à `a` **fixé** — `√(µE/µM)`. Or
[`keplerianPeriodOf`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/MissionHorizon.java)
ne calcule pas `2π√(a³/µ)` : il reconstruit un `KeplerianOrbit` **depuis les PV** avec le µ
terrestre, ce qui effondre `a` en même temps. C'est exactement le piège que `AchievedOrbitTest`
documente déjà mot pour mot pour `AchievedOrbit` — « *the wrong µ does not shift the semi-major
axis, it halves it* ».

Le découpage §4 / `L2` écrit « *courte d'un facteur 9,0* » ; `L0` §7 mesure « *783–784 s, rapport
9,015 à 9,017* » et conclut « **juste** ». **Les deux sont faux, du même écart** : la baseline a
mesuré la formule, pas le chemin de code. Le périmètre du lot ne bouge pas ; le chiffre qu'il
répare, si.

**2. `OrbitElements.mean()` refuse déjà un état sélénocentrique, et le refus est structurel.**
Mesuré vide à 100, 1 000, 5 000, 10 000, 11 000, 12 000, 15 000, 20 000, 30 000 et 50 000 km
d'altitude lunaire. La raison n'est pas la taille de l'orbite : la conversion rebase délibérément
sur le µ du fournisseur de potentiel, qui est terrestre, et un état sélénocentrique circulaire lu
avec le µ terrestre en ressort **quasi radial, à `e = 1 − µM/µE = 0,987700` — constant avec
l'altitude** (mesuré `0,987699` aux quatre altitudes vérifiées). C'est hors du domaine
d'Eckstein-Hechler partout. `L2` ne touche pas au µ de la conversion, donc le refus lui survit.

L'hypothèse contraire — qu'une orbite lunaire assez haute passerait à travers, puisque `a` effondré
finit par dépasser le rayon terrestre — a été formulée pendant cette conception et **démentie par la
mesure**. Elle est écrite ici pour ne pas être reformée.

**3. Un treizième site Terre-en-dur, hors du recensement.**
[`FlownBandAim:57-58`](../../src/main/java/com/smousseur/orbitlab/simulation/FlownBandAim.java)
porte `RE` et `J2`. Le tableau `multi-corps/03-conception-L1.md` §4.1 en nomme douze ; `L0` §7 avait
déjà corrigé le compte de onze à douze. Le vrai compte est treize. **Verdict inchangé : terrestre
légitime et hors chemin** — son unique appelant est `AnalyticTrimBurnStage:170`, un étage GEO, et un
centrage sur l'aplatissement J2 n'a pas de sens autour d'une Lune en masse ponctuelle. Il n'entre
dans ce lot que parce qu'il appelle `OrbitElements.mean`.

### 1.3 Ce qui existe déjà et qu'on n'écrira pas

- **La source du rayon.** `GravitationalContext.equatorialRadius()` rend
  `shape.getEquatorialRadius()`, et `OrekitService.getEarthEllipsoid():534` bâtit cet ellipsoïde sur
  `Constants.WGS84_EARTH_EQUATORIAL_RADIUS` lui-même.
- **La source du µ.** Tout propagateur d'`OrekitService` fait `setOrbitType(CARTESIAN)` puis
  `setMu(gravity.mu())` (`:341-342`), donc tout état que le lot rencontre porte le µ de son arc et
  `isOrbitDefined()` y est vrai.
- **La déclaration par étage.** `MissionStage.gravitationalContext(Mission)` existe depuis
  `PHY-4 / L1`, publique, avec le contexte de la mission pour défaut.
- **La sortie « horizon irrésoluble ».** `keplerianPeriodOf` rendant `0.0` fait déjà journaliser
  `finalCoastSeconds` et appliquer `UNRESOLVED_FALLBACK_SECONDS`.

---

## 2. Pourquoi la non-régression est une identité

Le découpage réclame cet argument pour le µ. **Il vaut aussi pour le rayon**, ce que le découpage ne
dit pas, et c'est ce qui rend le lot gratuit des deux côtés. Les deux égalités ont été vérifiées à
l'exécution, pas seulement lues :

| | vaut | mesuré |
|---|---|---|
| `GravitationalContext.earth().mu()` | `Constants.WGS84_EARTH_MU` | identique |
| `GravitationalContext.earth().equatorialRadius()` | `Constants.WGS84_EARTH_EQUATORIAL_RADIUS` | identique |
| `GravitationalContext.moon().equatorialRadius()` | — | 1 737 400 m |
| `GravitationalContext.moon().mu()` | — | 4,902 800 118 457 55e12 |

Une mission terrestre lit donc, après le lot, **le même double qu'avant, au bit près**. Ce n'est pas
une tolérance à choisir : c'est la même constante, atteinte par un autre chemin. C'est ce qui
distingue ce lot d'un refactor à mesurer, et ce qui autorise les épinglages à tolérance zéro du §6.

Deuxième moitié de l'argument, tout aussi nécessaire : **aucun étage de `LEOMission`, `GEOMission`
ni `LunarFlybyMission` ne redéfinit `gravitationalContext`.** Le seul remplacement du dépôt est
`LunarFlybyMission:182`, au niveau de la *mission*, et il rend encore un contexte terrestre. Toutes
les missions existantes retombent donc sur `GravitationalContext.earth()`.

---

## 3. Le rayon : une couture de trois maillons

Contrairement au µ, **le rayon n'est porté par rien** : un `Orbit` Orekit porte un µ et un repère,
jamais un rayon de corps. Il faut donc l'acheminer, et la couture fait trois maillons.

### 3.1 `OrbitElements` — le rayon devient un paramètre, pas une composante

La constante `RE` (`:44`) et son import disparaissent. `elementsOf` prend le rayon de référence ;
les deux entrées publiques le relaient :

```java
public static OrbitElements osculating(Orbit orbit, double referenceRadius)
public static Optional<OrbitElements> mean(Orbit orbit, double referenceRadius)
```

**Aucun défaut terrestre n'est conservé**, sur aucune des deux. Un défaut serait ici *faux* et non
seulement approximatif — c'est précisément le défaut que le lot répare — et il rouvrirait la porte
au niveau que `L5` traverse. Le prix est de deux fichiers de `main` et six de `test` à toucher,
tous d'un argument.

**Le record ne change pas.** Cinq composantes, et `format()` inchangé : l'épinglage caractère par
caractère de `PolarCoverageTest:94,102` reste vert par construction, et les cinq sites d'affichage
qui consomment le record — `MissionResultText`, `MissionDetailView`, `PanelFooter` — ne voient rien
passer. Faire du rayon une sixième composante aurait permis à `format()` de dire par rapport à quoi
l'altitude est comptée ; cela a été écarté pour ne pas élargir un record de reporting au bénéfice
d'une ligne de journal.

Le javadoc « *Altitude convention* » cesse de nommer WGS84 et nomme le corps de l'arc. La convention
elle-même — sphérique-équatoriale, `a(1±e) − R`, non géodésique — ne bouge pas.

### 3.2 `AchievedOrbit` — le rayon d'un côté, le µ de l'autre, et pourquoi

```java
public static AchievedOrbit of(SpacecraftState state, double referenceRadius)
```

Le µ continue de venir de l'**état**, comme `PHY-4 / L6` l'a fait. Les deux sources sont donc
différentes dans la même méthode, et le commentaire de huit lignes qui vit déjà là gagne la phrase
qui manque : **le µ est ce que l'intégrateur a intégré, le rayon est ce à quoi un lecteur rapporte
une altitude.** Ce ne sont pas deux réponses à une question, ce sont deux questions.

C'est aussi pourquoi la signature porte un `double` et non un `GravitationalContext`. Un contexte
mettrait `context.mu()` à portée de main **à la ligne même** dont le javadoc dit qu'elle ne doit pas
le lire — le piège des deux µ, écrit à trois endroits du dépôt (`AchievedOrbit:64-68`,
`GravitationalContext:154-157`, `Physics:204`). Un `double referenceRadius` ne peut être confondu
avec rien.

`UNAVAILABLE` et les quatre accesseurs ne bougent pas.

### 3.3 `MissionOptimizer` — d'où le rayon vient

Deux lignes avant `:237`, l'unique site de production qui construit un `AchievedOrbit` :

```java
List<MissionStage> stages = mission.getStages();
GravitationalContext arc =
    stages.isEmpty() ? mission.gravitationalContext() : stages.getLast().gravitationalContext(mission);
```

**Le dernier étage, et non la mission.** `Mission.gravitationalContext()` rend `earth()`, et
`LunarFlybyMission:182` la redéfinit *encore* en terrestre-avec-perturbateurs : le contexte de
mission est terrestre même sur une mission lunaire, parce qu'une mission décolle de la Terre. C'est
l'**étage** qui déclare l'arc — cinquième lecture de la forme que `MissionStage` porte déjà quatre
fois (`maxStepSeconds`, `gravitationalContext`, `soiTransitions`, et `soiCrossingEndsStage` que `L1`
vient d'ajouter), toutes justifiées par la même phrase : « *a phase is the unit that knows what it
flies around* ».

**Le dernier, et non « le dernier qui a avancé ».** Le coast terminal n'avance pas — son
`propagateStandalone` est le défaut de `MissionStage:85-87` — mais il déclare l'arc qu'il
s'apprête à voler, qui est celui de `mission.getCurrentState()`. Les deux coïncident, et la lecture
est d'un seul terme au lieu d'une variable entretenue dans la boucle.

**Le ternaire plutôt qu'un `getLast()` nu.** C'est la ligne dont le javadoc dit sur huit lignes
qu'elle ne doit jamais lever : `MissionLoadEvaluator` traduit toute `RuntimeException` échappée
d'`optimize()` en « lambda infaisable », et une exception ici déplacerait la charge d'ergols retenue
par le balayage de dimensionnement. Une liste d'étages vide est impossible aujourd'hui ; la totalité
coûte un ternaire et se garde.

Sur `MIS-5 / L5`, le dernier étage est le `CoastingStage` sélénocentrique terminal — l'un des trois
qui déclarent le contexte lunaire. Sur toute mission existante, la chaîne retombe sur
`GravitationalContext.earth()`, dont le rayon est la constante d'avant.

### 3.4 `FlownBandAim` — un mot ajouté

`FlownBandAim:98` passe son propre `RE`, qu'il porte déjà en `:57` pour la formule J2. Le site reste
terrestre légitime (§1.2 pt 3) et ne lit d'ailleurs jamais les apsides du résultat : il n'en tire
que le demi-grand axe.

---

## 4. Le µ : une réparation interne, deux sites d'appel couverts

`MissionHorizon.Revolutions.keplerianPeriodOf` lit le µ de l'état :

```java
state.isOrbitDefined() ? state.getOrbit().getMu() : 0.0
```

`isOrbitDefined()` est vérifié présent en Orekit 13.1.1 (`SpacecraftState:401`, `return orbit !=
null`), et `getOrbit()` lève `OrekitIllegalStateException(UNDEFINED_ORBIT)` quand l'orbite manque
(`:580-585`) — la garde est donc exactement celle que le motif du javadoc actuel appelle.

**L'orbite continue d'être bâtie depuis les PV**, ce qui reste ce qui protège du PVA absolu. Seule
la source du µ change. `import org.orekit.utils.Constants` disparaît du fichier : c'était son unique
usage.

### 4.1 Le repli n'est pas une seconde constante

Quand l'état ne porte pas d'orbite, la méthode rend `0.0` et **prend la sortie qui existe déjà** :
`finalCoastSeconds` journalise « *no Keplerian period* » et applique
`UNRESOLVED_FALLBACK_SECONDS` (3 j), dont le javadoc dit qu'il fait dégrader un horizon irrésoluble
« *vers un horizon plausible plutôt que vers rien* ». Un µ illisible **est** ce cas.

Le découpage écrit « *avec repli sur la constante* ». C'est écarté : ajouter un second repli à côté
de celui qui existe donnerait deux réponses à une question, et replanterait silencieusement la
constante terrestre que le lot vient d'arracher — sur un arc lunaire, elle rendrait à nouveau
3 356 s sans un mot. Le repli retenu est bruyant.

La branche est **inatteignable en production aujourd'hui** (§1.3), des deux côtés : le choix ne
change aucun comportement et décide seulement de ce qui arrivera le jour où elle sera atteinte.

### 4.2 Les deux sites d'appel, sans rien acheminer

L'horizon est résolu en **deux** endroits de production — `MissionOptimizer:265` et
[`MissionEphemerisGenerator:52`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/ephemeris/MissionEphemerisGenerator.java).
Le µ étant réparé *à l'intérieur* de `MissionHorizon`, les deux sont couverts sans un paramètre de
plus. C'est l'asymétrie du lot : le µ voyage sur l'état, le rayon doit être porté à la main.

### 4.3 Ce que ça répare, chiffré

`Revolutions(12)` sur l'orbite lunaire de `L5` passe de **3 356 s à 84 810 s**, soit 23,6 h. C'est
l'hypothèse même du décompte de `L5` : ~7 235 points, sous le budget de 8 192 sommets de
`TrajectoryPolyline`. Sans ce lot, la mission montrerait 0,93 h d'orbite lunaire après quatre jours
de transfert.

---

## 5. Le wizard : le rayon remonte dans la méthode

```java
protected static double revolutionDays(int revolutions, double altitudeMeters, GravitationalContext body)
```

`a = body.equatorialRadius() + altitudeMeters`, période `2π√(a³/body.mu())`.

**Le paramètre change de sens** — altitude, non plus demi-grand axe — et c'est ce qui fait
disparaître la duplication au lieu d'en créer une troisième en `L7` : le découpage ne compte que le
µ, mais le rayon terrestre est aux **deux sites d'appel**, pas dans la méthode.
`EarthOrbitDynamicParameters:470` et `GEODynamicParameters:77` perdent tous deux leur
`WGS84_EARTH_EQUATORIAL_RADIUS +` et passent `GravitationalContext.earth()`.

**Ici la signature porte le contexte, et non deux doubles nus** — le contraire du choix du §3.2. La
raison qui interdisait le contexte là ne se transporte pas : `revolutionDays` a besoin du µ **et**
du rayon, du même corps, et c'est exactement la paire que `GravitationalContext` est. Aucune ligne
voisine n'a de raison de lire un autre µ.

Effet de bord mesuré : `import org.orekit.utils.Constants` disparaît de **deux des trois fichiers**
— `DynamicParameters` (unique usage `:158`) et `GEODynamicParameters` (unique usage `:77`).
`EarthOrbitDynamicParameters` le garde pour `:391`, qui n'est pas de ce lot.

Le coût de couplage — `simulation.gravity` entre dans `ui/` — est réel et assumé. Il ne s'accompagne
pas du coût de test qu'on pouvait lui prêter : **quatre classes de test UI bootstrappent déjà
Orekit** (`MissionProfileTest`, `WizardPrefillTest`, `PlanningModelTest`, `MissionTargetOrbitTest`),
avec le même `Assumptions.assumeTrue` sur `orekit-data.zip`.

---

## 6. `mean()` : rien en production, la raison écrite

Aucune ligne de production, et un javadoc qui porte le fait mesuré du §1.2 pt 2 : la conversion
rebase sur le µ du fournisseur de potentiel, qui est terrestre, donc tout état sélénocentrique en
ressort à `e = 1 − µM/µE = 0,9877` constant, hors du domaine d'Eckstein-Hechler à toute altitude.
Le refus est **structurel et total**.

**Une nuance à écrire, sans quoi la signature ment** : `mean()` reçoit désormais un rayon de
référence qu'il n'utilise **pas** pour la théorie, seulement pour `elementsOf` après conversion. Un
rayon non terrestre y est donc toujours apparié à un résultat vide — et le jour où quelqu'un rendra
la théorie contextuelle, c'est cette phrase qui lui dira ce qu'il change.

**Aucune garde explicite n'est ajoutée.** Elle serait une règle de plus à tenir pour un cas qui ne
se produit pas, et le comportement d'aujourd'hui est déjà celui qu'on voudrait. Ce que le lot ajoute
à la place est un test : la propriété « une mission lunaire n'affiche pas de ligne d'éléments
moyens » repose aujourd'hui sur une exception levée par une bibliothèque tierce, et rien ne le dit.

---

## 7. Les tests qui ferment

Huit cas, quatre fichiers. Aucun ne propage : tous s'exécutent en millisecondes sur des orbites
bâties à la main.

### 7.1 `OrbitElementsTest` — trois cas

1. **`osculating_countsApsidesFromTheArcsBody`.** État sélénocentrique, `a = 1 737 400 + 100 000`,
   `e = 1e-4` : périlune et apolune près de 100 km. La lecture d'avant `L2` est **dérivée dans le
   test** — le décalage `RE − RM = 4 640 737 m`, qui rendait `−4 540 921 x −4 540 553 m` — pour que
   la barre soit visible et que l'assertion échoue franchement sur l'ancien code.
2. **`osculating_isBitIdenticalOnAGeocentricState`.** Le même calcul avec
   `Constants.WGS84_EARTH_EQUATORIAL_RADIUS`, **à tolérance zéro**.
3. **`mean_refusesASelenocentricState`.** Vide à 100 km et à 50 000 km, avec `e = 0,9877` écrit
   comme la raison.

> **Sur la tolérance zéro.** Le dépôt s'est brûlé dessus : `CentralBodyBaselineTest` est vert ou
> rouge selon le filtre `--tests`, parce que son égalité stricte repose sur des caches de repères et
> un modèle de gravité 8×8 partagés par la JVM (`MIS-5 / L1` §11.3). **Ici il n'y en a pas** —
> arithmétique pure sur une orbite bâtie à la main, aucun cache, aucun potentiel, aucun état global.
> La distinction est écrite à côté de l'assertion, sans quoi le prochain lecteur la prendra pour la
> même erreur.

### 7.2 `MissionHorizonTest` — deux cas

4. **`revolutions_readTheMuOffTheState`.** État sélénocentrique, `Revolutions(12)` → 84 810 s, avec
   les 3 356 s d'avant `L2` dérivées dans le test. Plus la non-régression terrestre, qui est une
   identité et non une tolérance.
5. **`revolutions_fallBackWhenTheStateCarriesNoOrbit`.** Un `SpacecraftState` en PVA absolu →
   `UNRESOLVED_FALLBACK_SECONDS`.

`revolutions_fallsBackOnAnUnboundOrbit:87` couvre déjà la branche hyperbolique et ne bouge pas.

### 7.3 `DynamicParametersTest` — nouveau, deux cas

6. **`revolutionDays_onEarth_isUnchanged`**, à tolérance zéro contre l'arithmétique d'avant le lot.
7. **`revolutionDays_onTheMoon`** : douze tours à 100 km ≈ 0,982 j.

`revolutionDays` étant `static`, aucun `Container` Lemur n'est construit et le test est sans
contexte JME. Le `@BeforeAll` est celui des quatre autres tests UI.

### 7.4 `AchievedOrbitTest` — un cas renforcé

Son cas lunaire `:116` n'assertait que le demi-grand axe, faute de pouvoir asserter l'altitude —
`PHY-4 / L6` avait réparé le µ et laissé le rayon. `L2` lui permet d'asserter le périlune à 100 km.
**C'est le seul test du lot qui traverse le chemin de production**, et c'est la raison de le
renforcer plutôt que d'en écrire un neuf ailleurs.

### 7.5 Et des tests relancés inchangés

`PolarCoverageTest` épingle `OrbitElements.format()` caractère par caractère et doit rester vert au
caractère près : le record et le format ne bougent pas. Les épinglages de trajectoire — `MIS-4`,
`GEO`, `LEO` — ne peuvent pas bouger : le lot ne touche aucune propagation, et `MissionHorizon` ne
peut pas déplacer une baseline d'optimiseur (son propre javadoc explique pourquoi, structurellement :
la passe d'optimisation ne vole jamais le coast final).

---

## 8. Ce que `L2` ne fait pas

1. **Les autres sites Terre-en-dur ne bougent pas.** Sur les douze rangés par `L0` §7, cinq sont
   « terrestre légitime » (`EarthMission`, `LaunchPlane`, `GravityTurnConstraints`,
   `GravityTurnProblem`, `StageEndStateDiagnostic`), deux « hors chemin » (`TransferProblem`,
   `Physics`) et un déjà réparé (`AchievedOrbit`, dont `L2` ne touche que la signature). Les quatre
   restants sont les trois de ce lot et celui de `L3`. `FlownBandAim`, treizième, est terrestre
   légitime.
2. **`mean()` ne devient pas contextuel.** Il n'existe pas de théorie d'éléments moyens lunaire au
   dépôt, et une Lune en masse ponctuelle n'a pas de termes courte-période à retirer.
3. **`PropellantBudget:47-48` reste** — quatrième site appliqué à un arc lunaire, et c'est `L3` qui
   le prend, comme le découpage §4 l'écrit.
4. **Aucun panneau lunaire n'est créé.** `revolutionDays` accepte un contexte ; personne ne lui en
   passe un lunaire avant `L7`.
5. **`LunarDynamicParameters.HORIZON_DAYS = 7.0` reste.** Le profil de survol garde son horizon
   `FixedDuration`, que `MIS-4 / L0` §7 pt 4 a figé pour sa propre raison. `L2` lève la contrainte
   que son javadoc invoque — « *revolutionDays carries an Earth µ* » — sans l'exercer.
6. **Le §6 pt 11 du découpage n'est pas traité.** `MissionOptimizer.resolveTargetAltitude` rendra
   une altitude lunaire pour un diagnostic d'ascension terrestre dès que `L5` portera un objectif
   d'insertion. C'est une ligne de journal fausse, introduite par `L5` et léguée à la clôture du
   chantier.

---

## 9. Risques

**Le seul risque du lot est un oubli de site d'appel**, et le compilateur le supprime : aucune
signature ne conserve de surcharge à défaut terrestre, donc tout appelant non converti est une
erreur de compilation. C'est précisément ce que la décision du §3.1 achète.

**Ce qui n'est pas un risque** : la non-régression terrestre, qui est une identité de constante et
non une mesure (§2) ; le déplacement d'une baseline d'optimiseur, structurellement impossible
(§7.5) ; et le comportement de `mean()`, mesuré identique avant et après (§6).

---

## 10. Ce que `L2` lègue

- **À `L5`** : une apside et une période qui se lisent sur le corps de l'arc — donc les 23,6 h
  d'orbite lunaire et les ~7 235 points sur lesquels son décompte repose, et un rapport d'orbite
  atteinte qui dit 100 km au lieu de −4 541 km.
- **À `L7`** : `revolutionDays` prêt pour un troisième appelant, sans un `RE +` de plus à écrire.
- **À `MIS-6`** : les deux mesures terrestres réparées, et rien d'autre — ce que le découpage §7
  annonce.
- **À `MIS-10`** : `OrbitElements` comptant ses apsides sur le corps de l'arc, et un précédent de
  réparation à coût nul pour les sites terrestres qui restent.
- **Au recensement** : un treizième site, et la règle que `L0` §7 avait appliquée une fois de
  trop — **mesurer le chemin de code, pas la formule qu'on croit qu'il calcule**.

---

## 11. Fermeture — `L2` est implémenté

**Verdict : la propriété du §1 est vraie, et les huit cas la tiennent.** Suite rapide du lot :
**30 tests, 0 échec, 0 ignoré** sur les cinq classes touchées — `OrbitElementsTest` (7),
`MissionHorizonTest` (13), `AchievedOrbitTest` (3), `DynamicParametersTest` (2, neuf) et
`FlownBandAimTest` (5, non-régression). Le lot a bougé huit fichiers de `main` et sept de `test`,
dont onze d'une à trois lignes.

### 11.1 Un écart au plan, et il est mineur

Le §3.3 écrivait deux lignes en ligne dans `optimize()`. L'implémentation les a mises dans un
`reportingRadius()` privé : les trois raisons du choix — l'étage plutôt que la mission, le dernier
plutôt que le dernier qui avance, le ternaire plutôt qu'un `getLast()` nu — sont un javadoc, et un
javadoc n'a pas de place au milieu d'une méthode de cent lignes. L'expression est celle du §3.3, au
mot près.

### 11.2 Un rouge préexistant, trouvé en passant

`spotlessApply` reformate **huit fichiers que ce lot ne touche pas** : `SoiCrossingDetector`,
`MissionStage`, `StageLegRunner`, `CoastingStage`, `TranslunarCoastStage`,
`TranslunarBoundaryFlightTest`, `SoiTerminatingStageTest`, `TranslunarCoastStageTest`. **Ce sont
les fichiers de `L1`**, commités au `c9c8c08` sans passer le formateur — imports non triés et
retours à la ligne à 100 colonnes, rien de sémantique.

Le reformatage a été **annulé** : ce n'est pas le travail de ce lot, et le plier dans son diff le
rendrait illisible. `./gradlew spotlessCheck` échoue donc sur ces huit fichiers, exactement comme
avant `L2`, et sur aucun des quinze de ce lot. Un `spotlessApply` en tête du prochain commit `L1`
suffit.
