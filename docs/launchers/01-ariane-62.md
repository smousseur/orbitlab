# Ariane 62 au catalogue des lanceurs (MIS-1)

**Statut** : design validé, implémenté et **mesuré le 2026-08-09 — issue 1** (§5.1) :
l'ascension boucle. Chantier terminé.
**Item roadmap** : [`MIS-1`](../roadmap/01-roadmap.md) — ★3 ◆1 S.

---

## 1. Objectif

`catalog/Launchers.java` ne contient que `FALCON_HEAVY`. Le câblage « profil de vol
dépendant du lanceur » — `AscentProfile` porté par `LauncherModel`, consommé par
`LEOMission` et `GEOMission` — existe mais n'est démontré par aucun second cas.

Ce chantier ajoute **une** entrée, Ariane 62, pour prouver ce câblage par un second
lanceur réellement différent. Il ne cherche pas à certifier une performance.

---

## 2. Décision structurante : fusionner les étages, ne pas dériver les étapes

Deux options étaient ouvertes :

1. **Fusion** — agréger les étages qui poussent pendant l'ascension en un seul `StageModel`,
   comme le catalogue le fait déjà pour Falcon Heavy (`"S1 (3 cores aggregated)"`).
2. **Dérivation** — détecter la structure du lanceur et recomposer les étapes de mission
   (`ascent → flame-out → séparation → suite de l'ascension`).

**Retenu : la fusion.** Trois raisons, par ordre de poids.

### 2.1 Le modèle n'a pas de poussée parallèle, et Ariane 6 est un lanceur parallèle

`VehicleStack.propulsion()` renvoie `vehicles.getFirst().propulsion()` et
`resolveActiveStage` désigne **un** étage actif à la fois. L'invariant est documenté dans
`VehicleStack` : *« the active stage changes only by an explicit jettison »*, et
*« intra-phase staging is not supported anywhere »*.

Or les P120C et le Vulcain d'Ariane 6 sont allumés ensemble au décollage : l'étagement est
**parallèle**, pas série. L'option 2 décrit une séquence série ; appliquée à Ariane 6, elle
ne modélise pas mieux le lanceur — elle produit un modèle faux d'une autre manière, pour
beaucoup plus cher. C'est l'argument qui tranche.

### 2.2 Le précédent existe et couvre exactement ce cas

Falcon Heavy est lui aussi un lanceur à étagement parallèle (27 Merlin allumés au sol,
boosters latéraux largués en vol), et le catalogue l'agrège déjà en un étage unique avec un
ISP moyen de trajectoire. Ariane 6 a la même topologie.

### 2.3 L'option 2 est un chantier, pas un ticket S

Elle demanderait de casser :

- `AscentSequence`, figé à trois phases avec `FIRST_STAGE_INDEX = 0` et une seule séparation ;
- la paire `GravityTurnFirstBurnStage` / `GravityTurnSecondBurnStage`, qui partage un
  `AscentPlanRef` unique ;
- surtout : **entre le MECO et le `Transfert`, aucune seconde séparation n'existe** dans la
  liste d'étapes de `LEOMission`. Un empilage à trois étages n'a aujourd'hui aucun moyen de
  larguer son étage intermédiaire, et le stage de transfert brûlerait le corps central.

L'option 2 reste le bon chantier — mais pour un lanceur réellement **série** (Soyouz-2 +
Fregat) ou pour les missions lunaires. Ariane 6 n'est pas ce cas.

---

## 3. Pourquoi Ariane 62 seule, et pas Ariane 64

Le calcul a été fait pour les deux versions avant de trancher. Hypothèses communes :
chiffres publics approximatifs, ISP moyen pondéré par la masse d'ergols, poussée au niveau
de la mer (convention de l'entrée Falcon Heavy existante).

| Agrégat S1 | ergols | sec | poussée | ISP moyen | flame-out |
|---|---:|---:|---:|---:|---:|
| A62 (2 P120C + LLPM) | 434 t | 36 t | 9 960 kN | ~300 s | ~128 s |
| A64 (4 P120C + LLPM) | 718 t | 58 t | 18 960 kN | ~286 s | ~106 s |

ΔV idéal total **à charge utile égale** (4,5 t) : A62 ≈ 11 710 m/s, A64 ≈ 12 070 m/s, soit
**+355 m/s pour 284 t d'ergols et 22 t de structure en plus**. Dans la réalité, l'A64
emporte ~11,5 t en GTO contre ~4,5 t pour l'A62 — un facteur 2,5. Le modèle fusionné les
sépare de 3 %.

Deux causes, toutes deux imputables à la fusion :

- la part d'ergols solides passe de 65 % à 79 %, ce qui tire l'ISP moyen de 300 à 286 s ;
- les **44 t de structure des boosters**, larguées à ~130 s sur le vrai lanceur, sont
  traînées jusqu'au MECO — près de quatre fois la charge utile GTO qu'elles doivent aider
  à placer.

**Conclusion : l'A64 fusionné n'est pas un A64, c'est un A62 lesté.** Une entrée catalogue
qui afficherait un gros lanceur incapable de faire visiblement mieux que le petit serait
trompeuse. Si l'A64 arrive un jour, sa place est après le chantier « ascension à N étages »,
où le largage des boosters à 130 s redevient exprimable — c'est là qu'il paiera.

---

## 4. Le modèle

### 4.1 Table d'étages

Chiffres publics approximatifs, à figer à l'implémentation.

| | ergols | sec | poussée (SL) | ISP |
|---|---:|---:|---:|---:|
| 2 × P120C | 2 × 142 t | 2 × 11 t | 2 × 4 500 kN | ~250 s SL / ~278 s vide |
| LLPM (Vulcain 2.1) | 150 t | 14 t | 960 kN | ~310 s SL / ~431 s vide |
| **S1 agrégé** | **434 000 kg** | **36 000 kg** | **9 960 000 N** | **300 s** |
| S2 — ULPM (Vinci) | 31 000 kg | 6 000 kg | 180 000 N | 457 s |

**Règle d'ISP moyen**, explicitée ici parce qu'elle est reconstituée depuis Falcon Heavy et
non documentée ailleurs : l'ISP retenu est placé dans le bracket [SL, vide] de l'agrégat au
même endroit relatif que les 296 s de FH dans son propre bracket [282, 311], soit ~48 %.
Pour l'A62, bracket [271, 331] → **300 s**. Aucun bouton libre : le seul réglage global du
modèle, `PropellantBudget.ASCENT_LOSSES_MS = 1 260`, est déjà calibré sur mesure et n'est
pas rejoué pour un lanceur.

### 4.2 Profil d'ascension

```java
new AscentProfile(6.0, 3.0, 5.0)   // Falcon Heavy : (7.0, 3.0, 2.0)
```

- **`verticalAscentDuration = 6.0`** — l'A62 décolle à T/W ≈ 1,99 contre ≈ 1,65 pour Falcon
  Heavy. Il dégage le pas de tir plus vite, la montée verticale est plus courte.
- **`pitchKickAngleDeg = 3.0`** — identique à FH et à `AscentProfile.LEGACY`.
  **Volontairement non différencié** : aucune base ne justifie un écart, et inventer un
  chiffre pour rendre la démonstration plus jolie mettrait une valeur sans fondement dans le
  catalogue.
- **`interstageCoastDuration = 5.0`** — le Vinci est cryogénique et demande une mise en froid
  avant allumage, là où le Merlin Vacuum rallume vite. C'est le champ le plus visible du
  profil : il est consommé deux fois, par `GravityTurnFirstBurnStage` et par
  `AscentSequence.separation()`.

Deux champs sur trois diffèrent de Falcon Heavy : c'est la démonstration recherchée.

### 4.3 Capacités — déclaratives

**Constat préalable** : hors du record lui-même, rien dans `src/main` ne lit `canCoastFor`,
`restartCount`, `ShutdownMode`, `IgnitionMode` ni `StageRole`. Le Javadoc de
`StageCapabilities` le dit — c'est *« the input of the future profile derivation »*. La
seule capacité réellement consommée est `variableLoad()`.

| | S1 agrégé | S2 (ULPM) |
|---|---|---|
| `ignition` | `GROUND` | `AIRSTART` |
| `restartCount` | 0 | 4 |
| `shutdown` | `COMMANDED` | `COMMANDED` |
| `propellant` | `CRYOGENIC` | `CRYOGENIC` |
| `maxCoastDuration` | 0.0 | 21 600.0 (6 h) |
| `role` | `CORE` | `UPPER` |

**Pourquoi `CRYOGENIC` et non `SOLID` sur le S1**, alors que 65 % de ses ergols sont
solides : `variableLoad()` mord à deux endroits — `StageModel.toVehicle` lève si la charge
diffère de la capacité, et `PropellantLoadOptimizer.allVariableLoadMask` exclut du balayage
multi-λ tout étage non variable. Déclarer `SOLID` gèlerait un étage qui est à 35 % liquide
et dont l'extinction est commandée par le Vulcain. Falcon Heavy déclare déjà son agrégat
kérolox en `CRYOGENIC` : le champ se lit « liquide, coast fini », pas comme une nature
chimique. **C'est une convention de modélisation et le Javadoc doit le dire.**

`maxCoastDuration` doit être fini sur un cryogénique (le validateur l'impose) ; 6 h
reflètent l'ULPM conçu pour les missions longues.

**Tension résiduelle, signalée et non corrigée ici** : `COMMANDED` recopie Falcon Heavy,
mais `AscentSequence` brûle le S1 jusqu'au flame-out — `BURN_TO_DEPLETION` serait plus vrai,
pour les deux lanceurs. Toucher à FH est hors périmètre ; garder les deux entrées
comparables vaut mieux qu'une correction cosmétique asymétrique.

### 4.4 Ordre du catalogue

`ARIANE_62` s'ajoute **après** `FALCON_HEAVY` dans `CATALOG` : `Launchers.all()` alimente
directement la liste du wizard (`StepLauncher`), et garder FH en tête évite de déplacer par
effet de bord ce que l'UI présente en premier.

---

## 5. Le risque assumé, et le point de mesure qui le tranche

À 9 960 kN et 300 s, le débit vaut ~3 385 kg/s : **le S1 agrégé s'éteint vers 128 s**.
C'est fidèle aux P120C (~130 s) et faux pour le corps central, qui brûle en réalité ~8 min.
Le Vinci reprend donc la main à 128 s sur ~41,5 t, soit un rapport poussée/poids de **0,44**
— contre ~0,83 pour le S2 de Falcon Heavy, qui est le cas sur lequel `ASCENT_LOSSES_MS` a
été calibré.

**Ce n'est pas réglable dans l'option fusion** : étirer la combustion à 300 s demanderait de
descendre la poussée agrégée à ~4 260 kN, sous le poids au décollage — le lanceur ne
décolle plus. Les deux seuls curseurs (ISP moyen, poussée agrégée) ne peuvent pas déplacer
ce flame-out.

Le design intègre donc **un point de mesure bloquant** : monter l'entrée, lancer une mission
LEO 400 km, observer. Trois issues, toutes trois valides :

1. **L'ascension boucle** → on documente le flame-out à 128 s dans le Javadoc du
   `StageModel`, comme l'ISP moyen l'est pour FH. Ticket terminé.
2. **Elle boucle avec une capacité très en dessous des ~4,5 t GTO réels** → on le documente
   comme limite connue du modèle à deux étages. Ticket terminé : son objectif est de
   démontrer le câblage, pas de certifier une performance.
3. **Elle ne boucle pas** → on tient la preuve mesurée qu'Ariane 6 exige l'ascension à N
   étages. MIS-1 devient le déclencheur documenté de ce chantier au lieu d'une intuition,
   et **c'est un résultat, pas un échec**.

### 5.1 Mesure du 2026-08-09 — issue 1

`Ariane62MissionTest`, LEO 400 km, charge utile 5 t, loads issus de `PropellantBudget`
(S1 434 000 kg pleins, S2 **6 699 kg** dimensionnés), graine 42.

| | mesuré |
|---|---|
| Flame-out S1 | **T+128,20 s** (prédit ~128 s) |
| Bande d'altitude parcourue | 399,64 – 400,85 km (cible 400) |
| Insertion (moyenne) | 409 694 × 409 917 m, i = 5,294° |
| Résidu S2 | 1 452 kg, **21,7 %** de sa charge |
| ΔV total | 8 067 m/s |

**Le calcul de flame-out était juste au dixième de seconde. Le calcul de T/W était faux.**
Les 0,44 annoncés ci-dessus supposaient un ULPM plein à 31 t ; or `PropellantBudget`
dimensionne l'étage supérieur et n'en charge que 6,7 t. La masse à la séparation est donc
17 699 kg et non ~41 500, et le Vinci reprend à **T/W ≈ 1,04**. Le risque quantifié au §5
valait pour un empilage plein — que les missions ne volent pas. C'est la raison de fond pour
laquelle l'issue 1 l'emporte, et elle n'avait pas été vue au design.

Ce qui reste vrai : la déformation de la **forme** d'ascension (128 s contre ~490 s réels)
est confirmée et demeure la limite documentée de cette entrée. Elle n'empêche pas la mission
d'aboutir à cette charge utile. La capacité maximale n'a pas été sondée, et le profil GEO
non plus — §8 s'applique toujours.

Note d'observation, non poursuivie : l'optimiseur remonte `exponent saturated (LOW)`
(valeur 0,140, bornes [0,1 ; 3,0]) sur le virage gravitationnel de l'A62 — le même
épinglage au plancher que celui déjà diagnostiqué comme étant le vrai optimum sur d'autres
profils.

---

## 6. Tests

### 6.1 Verrous catalogue (`LaunchersTest`, calqués sur l'existant)

- `byId_ariane62_returnsCatalogConstant`
- `ariane62_knownFigures` — masses, ISP, poussées, capacités, sur le modèle de
  `falconHeavy_knownFigures`
- `all()` : remplacer `all_containsFalconHeavy` par
  `assertEquals(List.of(FALCON_HEAVY, ARIANE_62), Launchers.all())` — plus fort, et force une
  mise à jour consciente au troisième lanceur.
- test de coast du S2, **avec un Javadoc explicite sur son statut**.

> ⚠️ **Piège à ne pas propager.** Le test existant
> `falconHeavy_upperStageCoast_allowsParkingButNotGtoCoast` affirme *« GTO coast to apogee
> must delegate to the AKM »* : ça se lit comme une propriété comportementale, mais rien ne
> l'applique — le profil GEO scindé est codé en dur dans `GEOMission`, pas dérivé de
> `canCoastFor`. Le test symétrique A62 doit dire par son nom et son Javadoc qu'il verrouille
> une **déclaration**. Sinon un lecteur futur conclura que l'A62 circularise avec son propre
> étage supérieur, ce qui est faux aujourd'hui.

### 6.2 La démonstration — l'objet du ticket

- Ajouter un cas A62 à `MissionAscentWiringTest.profiles()` : la propriété « ascension en
  trois phases » cesse d'être tenue par un seul lanceur, pour zéro propagation.
- **Test neuf** — `LauncherProfileWiringTest` : construire la même mission LEO depuis Falcon
  Heavy et depuis l'A62, et vérifier que les étapes portent **le profil de leur lanceur**.
  Rien ne l'assurait — c'est précisément le trou que MIS-1 comble.

  *Observable retenu, corrigé après implémentation.* La réserve du design pariait sur le
  coast de séparation à défaut de la durée de montée verticale ; c'est l'inverse.
  `StageSeparationStage.separationCoastDuration` est **privé** et hors de portée, tandis que
  `ConstantThrustStage.duration` est **`protected`** : un test placé dans le package
  `...mission.stage.ascent` (comme `AscentSequenceTest`) le lit sans qu'aucun accesseur de
  production n'ait à être ajouté. L'assertion porte donc sur la durée de montée verticale,
  6,0 s contre 7,0 s.

  Le test compare chaque mission au profil **déclaré par son propre lanceur** plutôt qu'à un
  littéral, pour ne pas dériver quand les chiffres du catalogue bougent — au prix d'être
  vacuous si les deux lanceurs déclaraient le même profil. Un second test
  (`theTwoLaunchersDeclareDistinguishableProfiles`) ferme ce trou.

  **Il est passé au vert du premier coup** : le câblage fonctionnait déjà, ce que le ticket
  postulait sans pouvoir le montrer. C'est le résultat attendu, pas un test manqué — sa
  valeur est d'être désormais le verrou de cette propriété. Le rouge qui a piloté le code
  est venu des *valeurs* du profil (`ariane62_ascentProfile_differsFromFalconHeavy`, sur une
  entrée délibérément laissée sur `AscentProfile.LEGACY` le temps d'un cycle).

### 6.3 Le point de mesure (§5)

`Ariane62MissionTest` — un run LEO 400 km sur A62 (charge utile 5 t, loads issus de
`PropellantBudget`), calqué sur `LEOMissionOptimizationTest`.

Il est né **en rapport, sans aucune assertion de précision** : poser un seuil deviné avant
d'avoir mesuré aurait été la faute déjà interdite sur les bornes CMA-ES. Une fois l'issue 1
établie (§5.1), il a été converti — d'où son nom, la « sonde » ayant fait son travail :

- la précision passe par `testMission(…)`, **le critère de bande parcourue de tous les
  autres tests LEO, appliqué inchangé** : un second lanceur ne mérite pas sa tolérance
  sur mesure ;
- l'instant de largage est assorti d'une assertion propre, 128,2 s ± 10 s. Il ne dépend que
  des chiffres du catalogue, et c'est la seule distorsion documentée de cette entrée — une
  affirmation portée par un seul Javadoc n'est vérifiée par rien ;
- les résidus par étage restent en log.

---

## 7. Hors périmètre

- Ariane 64 (§3).
- Le chantier « ascension à N étages » / dérivation des étapes depuis le véhicule (§2.3).
- Toute modification de l'entrée `FALCON_HEAVY`, y compris le `ShutdownMode` discuté en §4.3.
- Toute modification de `ASCENT_LOSSES_MS`, réglage global calibré sur mesure.
- L'UI : `Launchers.all()` alimente déjà `StepLauncher`, aucune ligne d'UI n'est nécessaire.

---

## 8. Réserve d'honnêteté sur les chiffres

Les valeurs de §3, §4.1 et §5 sont des calculs de coin de table sur des chiffres publics
approximatifs. Ils ont servi à **comparer les deux agrégations entre elles** et à
dimensionner un risque, pas à prédire une capacité de mission. Le point de mesure de §5 est
le seul juge du sort de l'entrée A62.
