# PHY-4 / L2 — Le troisième corps, en perturbation seulement

Lot **L2** du découpage (`01-decoupage.md` §4). Il suit `03-conception-L1.md`, dont
il réutilise la couture sans y toucher, et il se mesure contre `02-baseline-L0.md`
par l'intermédiaire du gate que L1 a laissé.

**Propriété rendue vraie.** Un étage peut demander l'attraction de la Lune et du
Soleil ; le corps central reste la Terre.

**Ce que ce lot ne fait pas.** Aucun étage de production ne demande quoi que ce
soit. Aucun chiffre de mission ne bouge — et pas par chance : sans perturbateur
déclaré, la liste des forces est identique à celle de L1, donc l'ordre de
sommation aussi.

> **Le lot le plus confortable du chantier**, à l'exact opposé de L1. Le diff de
> production tient dans deux fichiers, la non-régression est structurelle, et le
> risque a entièrement migré vers la conception des tests — d'où le poids du §5
> par rapport au reste.

---

## 1. Inventaire mesuré

Relevé au commit `26bbb33`, après le merge de L1.

**La couture de L1 est en place et personne ne s'en sert.**
`MissionStage.gravitationalContext(Mission)` hérite de
`Mission.gravitationalContext()`, et aucune classe de production ne la surcharge.
`StageChainRunner:193` la lit juste après `stage.maxStepSeconds(...)`.

**Les vingt sites de construction sont un seul et même appel.**

| | `src/main` | `src/test` |
|---|---|---|
| `createOptimizationPropagator(ctx, maxStep)` | **20** | 10 |
| `createTestPropagator(ctx, maxStep)` | **0** | 3 |
| `new GravitationalContext(...)` | **1** (son propre Holder) | 0 |

Deux conséquences directes, et ce sont elles qui décident du §2 :

- ce que porte le **contexte** arrive gratuitement aux vingt sites ; ce qui serait
  un **paramètre** de fabrique coûterait trente-trois signatures ;
- ajouter un composant au record ne casse aucun appelant, puisqu'il n'en a qu'un.

**Les ingrédients existent déjà.** `org.orekit.forces.gravity.ThirdBodyAttraction`
est bien dans l'Orekit 13.1.1 du dépôt. `OrekitService.body(SolarSystemBody)`
(`OrekitService.java:229`) mappe déjà `MOON` et `SUN` vers un `CelestialBody`
Orekit. Le cache `gravityModels` par corps (`:38`) est l'endroit où le second
cache s'installe.

**L'éphéméride couvre largement.** `orekit-data.zip` embarque un unique fichier
JPL, `DE-440-ephemerides/lnxp1990.440`, dont l'en-tête binaire donne une
couverture **1990,0 → 2150,1** au pas de 32 jours. Une propagation d'un an à
partir de 2026 est dans la plage sans marge à surveiller. Ce risque est clos, il
n'apparaît pas au §7.

### 1.1 Deux corrections au découpage, mesurées

**A. Le `7,3 × 10⁻⁶ m/s²` du §L2 est la valeur *linéarisée*, et elle est fausse de
19 % à la géométrie citée.** La formule `2·µ_L·r/d³` est un développement au
premier ordre en `r/d`. Ce que `ThirdBodyAttraction` calcule — et ce qui est
physiquement vrai — est la différence exacte
`µ_L·[(r_L−r)/|r_L−r|³ − r_L/|r_L|³]`. À r = 42 164 km, d = 384 400 km,
µ_L = 4,9029 × 10¹² m³/s², avec le vaisseau aligné sur la direction de la Lune :

| | m/s² | écart |
|---|---|---|
| linéarisé `2µr/d³` | 7,279 × 10⁻⁶ | — |
| exact, côté Lune | **8,679 × 10⁻⁶** | +19 % |
| exact, côté opposé | **6,235 × 10⁻⁶** | −14 % |

Le rapport `r/d` vaut 0,11 en GEO : la linéarisation n'y est plus une
approximation acceptable pour un test. Un test qui aurait assert 7,3 × 10⁻⁶ « à
quelques pour cent » aurait échoué. Le §5.1 en tire l'inverse d'un
assouplissement : en épinglant l'expression exacte, il s'assert au flottant près
au lieu de négocier une tolérance.

*Confirmé par la fixture.* À son époque, la Lune est à **360 486 km** et non
384 400 ; la linéarisation y est **17,2 % basse** côté Lune et **17,9 % haute**
côté opposé. Le tableau ci-dessus reste écrit à la géométrie du découpage pour
qu'il soit comparable à lui ; la fixture, elle, calcule tout depuis la position
réelle du jour et logue son propre écart.

**B. « Une propagation de quelques dizaines de jours » est le point fragile du
second test.** Les 0,85 °/an sont un taux *séculaire*. Sur trente jours, la
contribution lunaire porte un terme périodique d'environ quatorze jours du même
ordre que le séculaire accumulé : la valeur mesurée dépendrait de la date de
départ. La durée de propagation n'est donc pas un réglage de confort, c'est ce
qui décide si le test mesure la physique ou une oscillation. Traité au §5.2.

---

## 2. L'objet : un sixième composant

```java
public record GravitationalContext(
    SolarSystemBody body,
    double mu,
    Frame inertialFrame,
    Frame bodyFixedFrame,
    OneAxisEllipsoid shape,
    Set<SolarSystemBody> perturbers) {
```

Le §8 de L1 laissait la question ouverte en observant que « le troisième corps est
une force, pas un corps central ». C'est exact, et ce n'est pas une objection : le
record ne s'appelle pas `CentralBody`, il s'appelle `GravitationalContext`. Son
sens s'élargit de « le corps autour duquel on vole » à « l'environnement
gravitationnel d'une propagation » sans renommage, donc sans diff parasite.

**Ce qui a décidé, plus que le nom.** En L4, un arc lunaire déclarera
`central = Lune, perturbateurs = {Terre, Soleil}` : le corps central et ses
perturbateurs voyagent ensemble, par étage, à travers la même couture. Les séparer
en L2 obligerait à les réunir en L4.

### 2.1 `Set`, et un `EnumSet` en interne

L'ordre d'ajout des forces à un `NumericalPropagator` décide de l'ordre de
sommation des accélérations, donc du dernier bit. Un `EnumSet` donne un ordre
canonique par ordinal, indépendant de la façon dont l'appelant a écrit sa liste :
`withPerturbers(SUN, MOON)` et `withPerturbers(MOON, SUN)` produisent le **même**
propagateur, au flottant près.

Une `List` ferait dépendre un résultat numérique de l'ordre des arguments. C'est
le genre de non-déterminisme qui ne se diagnostique qu'après des heures, et il n'y
a rien à gagner en échange : personne n'a de raison de vouloir un ordre de
sommation particulier.

Le constructeur compact recopie défensivement dans un `EnumSet` non modifiable.

### 2.2 Le corps central est rejeté, il n'est pas ignoré

Déclarer la Terre comme perturbateur d'une propagation géocentrique n'est pas un
no-op à absorber poliment : c'est un bug d'appelant. Et c'est le bug le plus facile
à commettre en L4, en recopiant un contexte terrestre pour l'adapter à un arc
lunaire. `IllegalArgumentException` dans le constructeur compact.

### 2.3 `earth()` reste ce qu'il était

Le Holder de L1 est inchangé, avec un ensemble vide. Un
`withPerturbers(SolarSystemBody...)` dérive une variante. Le Holder n'est donc plus
l'unique instance du contexte terrestre, mais il reste l'unique instance **sans
perturbateur** — et c'est celle que volent toutes les missions existantes, ce qui
est exactement ce dont le §5.5 de L1 a besoin pour continuer d'exiger `0.0`.

---

## 3. Le câblage : rien de nouveau, et c'est le but

### 3.1 La déclaration d'étage existe déjà

Un étage qui veut la Lune surcharge la méthode que L1 a installée :

```java
@Override
public GravitationalContext gravitationalContext(Mission mission) {
  return GravitationalContext.earth().withPerturbers(MOON, SUN);
}
```

Aucune seconde méthode sur `MissionStage`, aucun site de construction touché : la
couture de L1 transporte le composant sans avoir à le savoir.

### 3.2 Les fabriques montent les forces dans un ordre figé

Le champ non central d'abord, puis les perturbateurs dans l'ordre canonique de
l'`EnumSet`. Le §7 de L1 avait déclaré porteur l'ordre `setOrbitType` → `setMu` →
`addForceModel` ; il s'étend d'une ligne, et se documente de la même façon.

> **Corrigé à l'implémentation.** Ce paragraphe disait « le champ central
> d'abord ». C'est faux, et la mesure l'a montré : un `NumericalPropagator` porte
> **toujours** une `NewtonianAttraction` centrale à lui, qu'il rend **en dernier**
> quoi qu'on ait ajouté. La liste réelle est donc :
>
> | contexte | `getAllForceModels()` |
> |---|---|
> | 8×8, sans perturbateur | `HolmesFeatherstone`, `Newtonian` |
> | 8×8 + Lune + Soleil | `HolmesFeatherstone`, `ThirdBody(Soleil)`, `ThirdBody(Lune)`, `Newtonian` |
> | newtonien seul | `Newtonian` |
> | newtonien + Lune | `ThirdBody(Lune)`, `Newtonian` |
>
> Pas de double comptage : `HolmesFeatherstoneAttractionModel` n'expose que la
> partie non centrale du potentiel (`nonCentralPart(...)`), le terme central étant
> justement cette `NewtonianAttraction` ; et dans la fabrique newtonienne, notre
> `addForceModel(new NewtonianAttraction(mu))` **remplace** celle du propagateur au
> lieu de s'y empiler. Ce montage est antérieur à PHY-4.
>
> Ce que ça ne change pas : les perturbateurs restent entre le champ non central
> et le terme central, dans l'ordre canonique, et un ensemble vide n'ajoute
> toujours rien du tout. L'argument de non-régression structurelle du §4.1 tient
> intégralement.

**Les deux fabriques honorent le composant**, `createTestPropagator` comprise. Elle
n'a aujourd'hui aucun appelant dans `main` (§1), mais un contexte doit vouloir dire
la même chose partout : une fabrique qui ignorerait silencieusement les
perturbateurs serait un piège pour le premier test qui s'en servirait.

### 3.3 Un second cache par corps, à côté du premier

`Map<SolarSystemBody, ForceModel> thirdBodyModels`, même `computeIfAbsent` que le
§3.3 de L1, et le même argument : atomique, fonction de mapping évaluée au plus une
fois par clé, aucun verrou explicite à réintroduire.

Ce n'est pas une optimisation. Les explorations CMA-ES tournent en parallèle
(`CMAESTrajectoryOptimizer:314`) et c'est ce cache qui garantit **une instance
unique par corps** plutôt qu'une simple valeur cohérente.

---

## 4. Ce que L2 ne touche pas

Les vingt sites de construction, les six étages analytiques, la garde de rentrée,
`computeAltitudeMeters`, l'éphéméride de mission, le rendu : rien. Le diff de
production tient dans `GravitationalContext` et `OrekitService`.

### 4.1 La question 3 du §5 du découpage, tranchée

> « Le troisième corps pendant l'ascension et en LEO : autorisé ou interdit ? »

**Aucun étage de production ne déclare de perturbateur en L2**, ni pendant
l'ascension, ni en LEO, ni ailleurs. C'est la règle 3 du §3 du découpage appliquée
à la lettre — « le nouveau comportement est opt-in jusqu'au dernier lot » — et
elle a trois conséquences qu'on veut toutes :

1. la non-régression est **structurelle** et non mesurée : à ensemble vide, aucun
   `addForceModel` supplémentaire, donc les 62 frontières de
   `CentralBodyBaselineTest` restent à `0.0` sans qu'on ait à y toucher ;
2. L3, L4, L5 et L6 continuent de se mesurer contre `02-baseline-L0.md`, qui reste
   valide pour tout le chantier ;
3. on ne déplace aucun chiffre GEO ou MEO sans avoir de valeur de référence
   externe pour juger le nouveau résultat — ce qui serait changer des nombres
   contre rien.

Le premier déclarant réel sera **L6**, sur son arc lunaire.

### 4.2 Les plans analytiques restent képlériens

À écrire pour L6, pas à traiter ici. `AnalyticHohmannTransferStage`,
`AnalyticGtoInjectionStage` et leurs pareils calculent leur burn par des formules
à deux corps, puis le volent sous le modèle de forces du propagateur. Faire
déclarer un perturbateur à ces étages ferait diverger le plan du vol. Ce n'est pas
une erreur en soi — c'est un écart qu'il faudra **mesurer** au lieu de le
découvrir.

---

## 5. Les deux tests

Le risque de ce lot est entièrement ici.

### 5.1 L'unité : la formule exacte, à travers notre fabrique

**`ThirdBodyPerturbationTest`**, dans un nouveau `simulation/gravity/`.

Il construit `earth().withPerturbers(MOON)`, demande un propagateur à
`createOptimizationPropagator`, **ressort le `ThirdBodyAttraction` de
`propagator.getAllForceModels()`** et l'évalue à une géométrie imposée. Passer par
la fabrique plutôt que par un `new ThirdBodyAttraction(...)` est tout l'intérêt du
test : ce qui est vérifié est le câblage, pas Orekit.

**La géométrie est imposée en plaçant le vaisseau, pas la Lune.** À une époque
figée, on lit la position réelle de la Lune en GCRF et on pose le vaisseau à
r = 42 164 km sur cette direction, puis sur la direction opposée. La distance `d`
est celle du jour — la Lune se promène entre 363 000 et 405 000 km — et non les
384 400 km du découpage. L'attendu est donc calculé depuis la position réelle, pas
recopié.

**Ce que l'assertion vaut, et ce qu'elle ne vaut pas.** L'attendu est écrit à la
main par `µ_L·[(r_L−r)/|r_L−r|³ − r_L/|r_L|³]`, avec le µ_L d'Orekit. Cela ne
prouve pas la constante. Cela prouve les quatre choses qui peuvent réellement être
fausses : le bon corps, le bon repère, le bon signe, et surtout **la présence du
terme indirect** `−r_L/|r_L|³`. C'est ce terme qui fait d'une attraction une
perturbation de marée ; l'oublier — en câblant un `NewtonianAttraction` par
inadvertance — donnerait une accélération environ cent fois trop grande et
orientée tout autrement. Égalité au flottant, aucune tolérance négociée.

**Une assertion sur la liste des forces elle-même.** `earth()` seul ne monte
**aucun** troisième corps ; `withPerturbers(MOON, SUN)` en monte deux, Soleil puis
Lune, et `withPerturbers(SUN, MOON)` donne la même liste. C'est la non-régression
du « vide par défaut » plus la garantie du §2.1, et c'est instantané.

Les 7,3 × 10⁻⁶ du découpage restent dans le test, mais comme **repère d'ordre de
grandeur logué**, avec les écarts du §1.1-A écrits à côté — pour qu'un lecteur qui
compare au découpage sache pourquoi ça ne colle pas.

### 5.2 L'intégration : quatre propagations, pas deux

**`GeoInclinationDriftTest`**, à côté de `SunSynchronousPrecessionTest`, dont il
reprend la forme : une dérive mesurée sur une propagation, plus un contrôle qui ne
doit pas dériver.

Le découpage en demande deux. Le lot en fait quatre, et c'est le choix de
conception qui compte le plus :

| Cas | Attendu | Rôle |
|---|---|---|
| sans perturbateur | l'inclinaison ne bouge pas | contrôle |
| Lune seule | ≈ 0,48 °/an | logué |
| Soleil seul | ≈ 0,27 °/an | logué |
| Lune + Soleil | ≈ 0,85 °/an | **la mesure assertée** |

**La raison est un mode de panne précis.** Un câblage à moitié faux — un seul des
deux corps monté, ou le même monté deux fois — produit une valeur qui **rentre
dans une tolérance de ±20 %** posée sur le seul total. Séparer les contributions
rend ce mode de panne visible pour le coût d'une propagation supplémentaire.

**Fidélité et durée.** La propagation vole `createOptimizationPropagator`, la
fabrique que les vingt sites de production appellent. Une variante newtonienne
serait bien moins chère et resterait physiquement défendable (à i ≈ 0 le J2
n'exerce aucun couple hors plan), mais elle validerait un chemin qu'aucune mission
n'emprunte.

**Le span, choisi par balayage.** Mesuré à l'époque de la fixture, cas Lune +
Soleil :

| span | 30 j | 60 j | 90 j | 180 j | 270 j | 365 j | 547 j | 730 j |
|---|---|---|---|---|---|---|---|---|
| °/an | **1,2178** | 1,0637 | 0,9209 | **0,9570** | 0,9246 | 0,9487 | 0,9449 | 0,9418 |

Le plateau commence vers 180 jours et tient à 2 % près jusqu'à deux ans. **Et la
première colonne est le §1.1-B chiffré** : les « quelques dizaines de jours » du
découpage auraient mesuré 1,22 °/an, donc échoué à la tolérance de ±20 % que la
même phrase proposait.

Retenu : **180 jours**, le premier point du plateau. Les quatre propagations
prennent ensemble ≈ 33 s, ce qui reste « une trentaine de secondes » : le test
demeure dans la suite par défaut plutôt que sous
`@EnabledIfSystemProperty(named = "orbitlab.slowTests")`. Le mettre en opt-in
reviendrait à ne plus jamais l'exécuter, et c'est le seul garde-fou physique du
lot.

**Une réserve sur la valeur de référence, désormais mesurée et non plus invoquée.**
Les 0,85 °/an sont une valeur *moyenne* : la contribution lunaire dépend de
l'orientation du nœud de la Lune, qui régresse en 18,6 ans. En rejouant le cas
combiné à span fixe depuis des époques successives :

| époque | 2026 | 2027 | 2028 | 2030 | 2032 | 2035 |
|---|---|---|---|---|---|---|
| °/an | 0,9570 | 0,9279 | 0,9050 | 0,8583 | 0,7834 | 0,7574 |

Le taux balaie toute la bande 0,75–0,95 en une décennie, et 0,85 en est le milieu.
Les ±20 % ne sont donc pas de la prudence : c'est **la largeur physique de la
cible**, et un lot ultérieur qui voudrait resserrer ce test changerait sa nature,
pas seulement sa tolérance. L'époque de la fixture, 2026, est près d'un maximum du
cycle — d'où une valeur mesurée dans la moitié haute de la bande.

### 5.3 Le rapport CPU

Le découpage demande de « mesurer le surcoût CPU sur une optimisation ». Comme
aucun étage de production ne déclare de perturbateur (§4.1), il n'y a pas
d'optimisation qui en vole : il faut choisir ce qu'on mesure.

**Ce qu'on cherche est un rapport, pas un temps.** Un coût par évaluation de force
se multiplie par les milliers d'évaluations d'un CMA-ES ; c'est ce facteur qui
intéressera MIS-4.

Une même chaîne GEO nominale est donc volée deux fois par `StageChainRunner` — une
fois avec un contexte sans perturbateur, une fois avec Lune + Soleil — et le
rapport des temps de paroi est consigné dans ce document. Un doublon
d'optimisation CMA-ES complète serait plus proche de la réalité de MIS-4 mais
coûterait des dizaines de minutes pour un chiffre bruité : deux passes ne font pas
le même nombre d'évaluations, et le §6 de la baseline déclare ces comptes non
reproductibles — le rapport mélangerait le coût unitaire et la chance de
l'exploration. Un micro-benchmark d'appels à `acceleration()`, à l'inverse, ne
verrait jamais l'effet du troisième corps sur le **nombre de pas** retenus par le
contrôle d'erreur adaptatif.

**Mesuré : ×1,31, ×1,40, ×1,47 sur trois passes consécutives**, soit environ
**+40 %** de temps de paroi pour monter Lune + Soleil sur une chaîne GEO complète.
Le rapport monte d'une passe à l'autre parce que c'est la passe sans
perturbateur qui accélère sous le JIT, pas l'autre qui ralentit.

Chiffre de machine, à ne comparer qu'à lui-même (§7.4 de la baseline). Ce que
MIS-4 doit en retenir : un CMA-ES sur une chaîne perturbée coûtera de l'ordre de
1,4 fois le même CMA-ES non perturbé — pas un ordre de grandeur, mais pas
négligeable sur une optimisation qui se compte déjà en dizaines de minutes.

---

## 6. Ordre d'exécution

1. **Le composant, le cache, les fabriques**, sans aucun déclarant.
   `CentralBodyBaselineTest` doit rester vert à `0.0` — et ce n'est pas une mesure
   chanceuse : ensemble vide ⇒ aucun `addForceModel` supplémentaire ⇒ même ordre
   de sommation, structurellement.
2. **Le test d'unité** (§5.1). Il ferme le câblage exactement, en une seconde.
3. **Le test de dérive** (§5.2) : mesurer d'abord — span, quatre valeurs —
   épingler la tolérance ensuite.
4. **Le rapport CPU** (§5.3), consigné ici.
5. **Suite complète.**

Contrairement à L1, le test ne peut pas précéder le mécanisme : il testerait une
API qui ne compile pas. Le filet est ailleurs, et il existe déjà — c'est le gate
de L1.

---

## 7. Risques identifiés

**Le repère de propagation, et c'est une contrainte pour L4.**
`ThirdBodyAttraction` exprime la position du corps perturbateur dans le repère de
propagation, et suppose ce repère centré sur le corps central. Nos propagations
sont en GCRF centré Terre : correct aujourd'hui. Un arc lunaire qui propagerait
encore en GCRF aurait un terme indirect faux — et faux silencieusement, puisque
l'ordre de grandeur resterait plausible. À écrire dans la conception de L4.

**Le coût par évaluation.** Deux interpolations d'éphéméride JPL par appel de
force, multipliées par les milliers d'évaluations d'un CMA-ES. Le §5.3 le chiffre.
C'est un avertissement pour MIS-4 plus qu'un risque pour L2, qui ne vole rien avec
perturbateurs hors de ses tests.

**Le terme périodique de quatorze jours**, traité par le span du §5.2. Le risque
résiduel est qu'un span choisi trop court passe quand même à l'écriture, par
chance sur la date, et devienne fragile plus tard. D'où le §5.2 : les quatre cas
sont logués, pas seulement le cas asserté.

Ce lot n'a **pas** de risque de non-régression, au sens où L1 en avait un. C'est
la contrepartie du §4.1, et c'est délibéré.

---

## 8. Ce que L2 laisse ouvert

- **L4** fera déclarer un autre corps central. Le contexte porte désormais les deux
  moitiés dont il aura besoin, et le §7 lui donne sa première contrainte : le
  repère de propagation doit suivre le corps central.
- **L6** sera le premier déclarant réel. Il devra mesurer l'écart entre les plans
  analytiques képlériens et le vol perturbé (§4.2).
- **MIS-4** héritera du rapport CPU du §5.3 comme facteur multiplicatif sur ses
  optimisations.
- La question de savoir si un jour l'ascension et le LEO doivent y avoir droit
  reste ouverte, mais elle est désormais **tranchée pour la durée de PHY-4** (§4.1)
  et ne se rouvrira que sur une mesure montrant que l'effet y sort du bruit du
  modèle 8×8.

---

## 9. Fermeture — L2 est implémenté

Mesuré le **2026-08-17**, GraalVM 21.0.5.

### 9.1 Le verdict

| | |
|---|---|
| Diff de production | 2 fichiers — `GravitationalContext`, `OrekitService` |
| Sites de construction touchés | **0**, comme prévu au §4 |
| `ThirdBodyPerturbationTest` | 7 tests, verts |
| `GeoInclinationDriftTest` | 1 test, 4 propagations, vert en ≈ 33 s |
| Gate L1 `CentralBodyBaselineTest` | 4 tests, **62 frontières à `0.0`**, vert |
| Sous-suite de vérification | **39 tests, 0 échec, 0 erreur** |

La sous-suite couvre, en plus des deux nouveaux tests : le gate L1,
`EarthOrbitNonRegressionTest`, `OrekitServiceTest`, `SunSynchronousPrecessionTest`,
et les fixtures qui construisent des propagateurs (`ReentryGuardTest`,
`DepletionGuardTest`, `DepletionStopTriggerTest`, `GravityTurnReplayConsistencyTest`,
`LateIgnitionReproTest`). **Les tests d'optimisation restent à lancer par
l'utilisateur** ; L2 ne les fait pas bouger par construction (§4.1), et le gate L1
vert à `0.0` en est la preuve la plus directe.

### 9.2 Les chiffres de physique

| | mesuré | attendu |
|---|---|---|
| Accélération exacte, côté Lune (d = 360 486 km) | 1,0657 × 10⁻⁵ m/s² | — |
| Accélération exacte, côté opposé | 7,488 × 10⁻⁶ m/s² | — |
| Linéarisé `2µr/d³` à la même géométrie | 8,826 × 10⁻⁶ m/s² | −17,2 % / +17,9 % |
| Erreur relative contre Orekit | 4,9 × 10⁻¹⁶ / 2,6 × 10⁻¹⁶ | égalité au flottant |
| Dérive GEO, sans perturbateur | **0,0125 °/an** | contrôle, plat |
| Dérive GEO, Lune seule | **0,6887 °/an** | — |
| Dérive GEO, Soleil seul | **0,2678 °/an** | ≈ 0,27 |
| Dérive GEO, Lune + Soleil | **0,9570 °/an** | 0,85 ± 0,17 |

**Le Soleil seul tombe sur 0,2678 contre 0,27 attendu.** C'est la mesure la plus
convaincante du lot : elle ne doit rien à la tolérance de ±20 %, et elle ne peut
pas être un hasard de câblage.

La tolérance du test d'unité a été **resserrée depuis la mesure** de 1 × 10⁻¹² à
1 × 10⁻¹⁴, ce qui laisse vingt fois l'erreur observée — de la place pour une autre
géométrie lunaire, pas pour une formule fausse : un terme indirect oublié se
trompe d'un facteur cent.

### 9.3 Deux écarts au plan, tous deux mesurés

1. **Le §3.2 disait « le champ central d'abord ».** C'est faux : Orekit somme
   toujours son terme central en dernier. Corrigé dans le corps du document, avec
   la liste réelle des forces. La non-régression structurelle n'en dépendait pas.
2. **Le §5.2 laissait le span à calibrer** et évoquait un éventuel passage en
   `slowTests`. Le balayage donne 180 jours et ≈ 33 s, donc le test reste dans la
   suite par défaut. Le balayage a aussi chiffré la réserve sur les 0,85 °/an, qui
   n'était jusque-là qu'une valeur de domaine invoquée.

### 9.4 Ce que L2 n'a pas eu besoin de faire

Aucun étage n'a été modifié. Aucun des vingt sites de construction n'a été
touché. Le pari du §2 — faire voyager les perturbateurs dans le contexte plutôt
que dans une signature — s'est vérifié exactement : le seul code de production
modifié est celui qui définit le contexte et celui qui monte les forces.

L3 et L4 peuvent démarrer.
