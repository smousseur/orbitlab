# MIS-5 / L7 — La carte d'orbite lunaire

Lot **L7** du découpage ([`01-decoupage.md`](01-decoupage.md) §4), le dernier du chantier. Il se pose
sur la mission de [`07-conception-L5.md`](07-conception-L5.md) et dans le step d'onglets de
[`08-conception-L6.md`](08-conception-L6.md). Il rend vraie **une** propriété :

> **Une orbite lunaire se crée depuis le wizard, se sauvegarde, se rouvre, et affiche son écart.**

Aucune physique nouvelle, aucune étape nouvelle. `L5` a composé la mission mais ne pouvait pas
l'offrir : il lui manquait **une clé de champ**, que quatre sites attendaient et qu'un cinquième a
rejointe en `L6`. Ce lot l'écrit, et **les cinq refus nommant `L7` disparaissent tous** — c'est la
vérification que le périmètre est complet.

**Trois annonces du découpage tombent**, et une quatrième est déjà livrée. Elles sont au §1.2.

---

## 1. Inventaire mesuré

### 1.1 Ce que le lot touche

| Fichier | Ce qui bouge |
|---|---|
| `interface/wizard/icon-mission-lunar-orbit.png` | **neuve** — la Lune ceinte d'un anneau fermé |
| `ui/…/step/params/LunarOrbitDynamicParameters` | **neuf** — un curseur, et la seule arithmétique du panneau |
| `scenario/model/ScenarioMission.LunarOrbit` | **neuf** — le quatrième record du fichier scellé |
| `ui/mission/wizard/MissionProfile` | la septième constante, **et deux refus levés** (`of`, `defaultFor`) |
| `ui/mission/wizard/FormField` | la clé `LUNAR_ORBIT_ALT` |
| `ui/…/step/StepParameters` | la carte inscrite dans `dynamicParametersMap` |
| `operation/MissionFactory` | la branche `LUNAR_ORBIT` *(3ᵉ refus levé)* |
| `scenario/ScenarioMapper` | la branche et son miroir `toMissionValues` *(4ᵉ)* |
| `ui/mission/wizard/WizardPrefill` | la branche *(5ᵉ)* |
| `ui/mission/MissionTargetOrbit` | la cible lunaire, et l'absence de plan |
| `ui/mission/MissionResultText` | `formatMiss` sans le champ en degrés |
| `operation/LunarOrbitMission` | `DEFAULT_PARKING_ALTITUDE` |

Aucune physique touchée : `PropellantBudget`, `MissionComposer` et les douze étapes de `L5` sont
inchangés.

### 1.2 Ce que le découpage annonce et qui ne tient pas

**1. « Le refus de Kourou présenté comme un refus » n'a aucun mécanisme, et le modèle documente la
décision de ne pas en avoir.** Le javadoc de `LunarLaunchWindowProblem` est explicite : *« Nothing
refuses a site here.* Un pas de tir dont la latitude est sous la déclinaison lunaire — **depuis
Kourou, 87,5 % d'une lunaison** — n'atteint aucun plan contenant la Lune, mais **c'est prix plutôt
que déclaré** : le critère reste fini et rend un optimum qu'aucun budget n'accepte. » La recherche
ne rend alors pas de fenêtre, et `MissionWizardAppState.lunarWindow` journalise un avertissement en
gardant la date demandée. C'est déjà le comportement de la carte `LUNAR`, invisible pour
l'utilisateur : le trou est **commun aux deux cartes lunaires**, et le combler irait contre une
décision écrite en `MIS-4 / L2` §1.3. Voir §7.

**2. Le vrai refus que ce lot livre est celui des ergols, et il ne coûte pas une ligne d'UI.**
`PropellantBudget.loadsForLunarOrbit` lève quand la charge utile n'a pas de propulsion ou quand son
réservoir ne tient pas l'insertion, et son javadoc dit où ça ressort : *« The refusal surfaces
through the wizard's dry composition, which turns it into a worded refusal »* —
`MissionWizardWidget.compositionRefused` → `StepLauncher.showRefusal`. Le message est écrit par le
modèle, avec les kilos, le ΔV et la capacité. **Et il est atteignable au clavier** : l'orbiteur fait
2 000 kg à sec pour un réservoir de 800, l'insertion en demande ~650 à 100 km, et le javadoc de
`Payloads.LUNAR_ORBITER` chiffre la marge restante à **2 433 kg de masse sèche** — or la masse est un
champ texte libre.

**3. « Le filtre de charge utile qui ne propose que l'orbiteur » est déjà livré, par `L3`.**
`Payloads.forMissionType(LUNAR_ORBIT)` croise les deux axes et le catalogue répond par un modèle
unique ; `PayloadsTest` l'épingle depuis `L3`. Rien à écrire.

**4. La bande d'altitude n'est pas un choix de ce lot : elle est héritée du dimensionnement du
catalogue.** Le javadoc de `Payloads.LUNAR_ORBITER` dit que ses 800 kg couvrent « les **664 kg que
coûte l'insertion au plancher de la bande d'altitude** » et que ses 5 500 N tiennent la combustion
sous 5 % d'une révolution — « 4,83 % à 100 km, **5,08 % à 50 km** ». `L3` a donc dimensionné contre
un plancher de 50 km. Le calcul le recoupe : le ΔV d'insertion vaut ~820 m/s à 50 km, ~811 à 100,
~761 à 500 — il **baisse** quand l'orbite monte, donc c'est l'orbite basse qui coûte, et le
réservoir n'est jamais le facteur limitant sur la bande. La carte ne fait que déclarer 50–500.

### 1.3 Deux faits qui décident du panneau

**5. `FixedDuration` est un total depuis le décollage, `Revolutions` un coast terminal.**
`FixedDuration.finalCoastSeconds` vaut `seconds − ascent` (`MissionHorizon:225`), et c'est ce que le
champ « durée » du wizard écrit (`MissionFactory.horizonOrNull`). `Revolutions(n)` vaut `n × période`
de coast, ajouté à ce que le trajet a pris. Les deux coïncident pour les cartes existantes et
**divergent d'un facteur cinq ici** :

| | coast terminal | trajet avant | total |
|---|---|---|---|
| LEO, 12 rév. à 550 km | 1,32 j | ~10 min | 1,32 j |
| GEO, 3 rév. | 3,0 j | 5,3 h | 3,22 j |
| **Orbite lunaire, 12 rév. à 100 km** | **0,98 j** | **4,0 j** | **4,98 j** |

**6. La colonne du roster ne contraint pas le nom.** Le javadoc de `MissionProfile.LUNAR` invoque
`defaultMissionName`, qui compose `%s-%03d` sur le nom de la constante, pour justifier un nom court.
Mesuré : `COL_NAME` vaut 220 px, moins la pastille, le badge et leurs écarts il reste **184 px**, et
`LUNAR_ORBIT-001` en sora-13 en occupe **129**. La contrainte ne mord pas.

---

## 2. La carte

```java
LUNAR_ORBIT(
    MissionType.LUNAR_ORBIT, MissionDomain.LUNAR,
    "LUNAR ORBIT", "Circular Lunar Orbit", "50 - 500 km",
    "interface/wizard/icon-mission-lunar-orbit.png",
    new AltitudeRange(50, 500, 100),
    true, InclinationMode.NONE, Double.NaN,
    Availability.WINDOWED)
```

**Le titre est le seul en deux mots du catalogue**, et c'est assumé. Côte à côte dans l'onglet
`MOON`, `LUNAR` et `LUNAR ORBIT` se lisent sans glossaire — l'une passe, l'autre reste. Un code à
trois lettres (`LOI`) aurait mieux suivi le rythme de LEO, GEO, SSO et MEO, mais il nomme la
**manœuvre** là où les six autres nomment une destination, et c'est du jargon sur le premier écran du
wizard. La ligne de valeur montre la **bande**, comme LEO et SSO, les deux autres cartes dont le
paramètre en est une ; le flyby montre son défaut, et c'est lui l'exception.

`isCircular()` est vrai de l'orbite et `InclinationMode.NONE` de son plan, mais les deux sont
**inertes** : seuls `EarthOrbitDynamicParameters` les lit, et il n'est jamais construit pour ce
profil. Le badge, lui, se rend tout seul — `StepMissionType.badgeFor` bascule sur `availability()`,
donc `LAUNCH WINDOW REQUIRED` sans une ligne de plus.

### 2.1 L'icône

48 × 48 RGBA, à la palette relevée dans l'atlas et sans une couleur neuve : `#dfdfdf` la Lune,
`#a8a8a8` les cratères, `#ffffff` l'anneau, `#000000` le contour. La Lune est ceinte d'un **anneau
fermé** qui passe derrière en haut et devant en bas — exactement le procédé des cinq cartes
terrestres, appliqué à un corps gris.

Elle dit *orbite* avec le vocabulaire que la grille emploie déjà cinq fois, et *pas la Terre* par la
seule couleur du corps. Elle se distingue de sa voisine par ce qui distingue vraiment les deux
missions : `LUNAR` montre un **trajet**, celle-ci montre qu'on **reste**. La composition alternative
— Terre, boucle et Lune, comme le flyby, avec l'anneau fermé en plus — a été écartée sur la place :
le flyby occupe déjà ses 48 px avec deux corps et un chemin, et un anneau autour d'une Lune de 10 px
n'y serait plus une forme mais une tache.

---

## 3. Le panneau

`LunarOrbitDynamicParameters`, calqué sur `LunarDynamicParameters` : **un curseur**, `LUNAR ORBIT
ALTITUDE`, sur la bande du profil. Pas de perigée/apogée sur une cible circulaire par construction,
pas d'inclinaison — le plan atteint autour de la Lune est subi —, et pas d'altitude de parking.

Celle-ci est `LunarOrbitMission.DEFAULT_PARKING_ALTITUDE = 400_000`, **constante propre et non
lecture de celle du flyby**. Les deux portent le même nombre pour la même raison, mais une insertion
en orbite lunaire n'a rien à voir avec un survol : lire la constante de l'autre mission ferait suivre
cette chaîne, en silence, à une valeur changée pour celle-là. Et ce n'est pas un champ, sur la mesure
de `MIS-4 / L0` : la visée converge à l'identique de 185 à 400 km, donc un curseur là serait un choix
avec rien derrière.

### 3.1 La durée dérivée

`defaultHorizonDays()` rend **le total** — `TIME_OF_FLIGHT_SECONDS / 86 400 + revolutionDays(12,
altitude, moon())`, soit 4,98 j à 100 km et 5,32 j à 500 — et non les seules douze révolutions.

Ce n'est pas une entorse à ce que font les deux panneaux terrestres, c'est la même règle appliquée là
où elle mord (fait 5). Ce que le champ écrit est un `FixedDuration`, dont le contrat est un total
depuis le décollage. Publier 0,98 j pour une mission qui en dure 4,98 ne serait pas seulement faux à
l'affichage : le champ est prérempli avec sa propre valeur, donc **un utilisateur qui la confirme
créerait une mission s'arrêtant avant la Lune**.

L'arithmétique est **sortie en fonction statique** `horizonDays(double)`, package-private. C'est le
seul calcul du panneau, il n'est pas atteignable à travers Lemur, et c'est celui dont l'erreur
tronquerait une mission — la même raison qui a sorti `RefusedPage` de son step.

Le biais de même nature sur GEO — 0,22 j, 7 %, le transfert GTO non compté — est **consigné et non
corrigé** : `L7` n'a aucune raison de toucher un écran qui marche.

### 3.2 La fenêtre

`windowInputs` réutilise le critère du flyby, sur le javadoc que `L5` a écrit dans
`MissionWizardAppState.lunarOrbitWindow` : « la périlune visée **est** l'altitude d'orbite lunaire,
donc le critère est celui du flyby ». Le nœud est ignoré et n'est pas un champ de cette carte : ce
qu'une mission lunaire attend est une **direction** que son plan de parking doit contenir, pas un
plan dont elle doit rejoindre le nœud ascendant.

---

## 4. La chaîne : fabrique, scénario, préremplissage

`FormField.LUNAR_ORBIT_ALT`, en **kilomètres** comme ses voisines. C'est la clé que `L5` a refusé
d'inventer sans écran : quatre sites la lisent ou l'écrivent, et un fichier de scénario en porte le
nom.

**L'ordre de la branche de `MissionFactory` est contraint, et c'est celui du flyby renversé.**

```java
LunarOrbitLoads loads = PropellantBudget.loadsForLunarOrbit(
    launcher, payloadModel, payloadMass,
    LunarOrbitMission.DEFAULT_PARKING_ALTITUDE, orbitAlt, latitude, FastMath.PI / 2);
Spacecraft payload = payloadModel.toSpacecraft(payloadMass, loads.insertionLoad());
```

Le flyby construit son `Spacecraft` **avant** de dimensionner, avec zéro ergol d'insertion, et passe
l'engin à `loadsForLunar`. Ici `loadsForLunarOrbit` prend le *modèle* et la masse sèche et **rend** la
charge d'insertion, dont l'engin est ensuite fait : on ne peut pas connaître la charge avant de la
dimensionner, ni bâtir la charge utile avant de connaître la charge. Aucune vérification de
propulsion à côté — le budget fait la sienne et formule le refus (§1.2 fait 2).

`ScenarioMission.LunarOrbit` reprend `Lunar` composant pour composant, `periluneKm` devenant
`orbitAltitudeKm`, et omet le parking pour la même raison que lui.

> **Le scellement ne suffit pas à étendre le format.** Le nom sur disque vit dans
> `@JsonSubTypes.Type(value = ScenarioMission.LunarOrbit.class, name = "LUNAR_ORBIT")`, une
> annotation que le compilateur n'exige pas. L'oublier compile, écrit un fichier, et échoue à la
> relecture. C'est le seul endroit du lot où l'exhaustivité du `switch` ne protège de rien.

---

## 5. La cible affichée

`MissionTargetOrbit.of` rend la cible circulaire en altitudes **au-dessus de la Lune**, avec
`Double.NaN` en inclinaison. C'est comparable parce que `MIS-5 / L2` rapporte l'orbite atteinte
contre le corps de l'arc et que le coast terminal de `L5` déclare l'arc lunaire.

`hasInclination()` rend `!Double.isNaN(inclination)` — `NaN` et non un composant nul, sur le marqueur
que `MIS-5` emploie déjà pour exactement cette absence (`OrbitInsertionObjective.inclination()`,
`MissionOptimizer.resolveTargetAltitude`). `formatMiss` omet alors le champ en degrés : `miss +0 /
+114 m` au lieu de `miss +0 / +114 m NaN deg`.

C'est la promesse que `L5` avait laissée en commentaire — *« L7 brings the card and the reader »* —
et l'objection qu'il opposait était datée et le disait : *« dans un lot où aucun écran ne peut être
regardé »*. **Les deux appelants ne changent pas** (`MissionDetailView:206`, `PanelFooter:279`), tous
deux gérant déjà l'absence, et la chaîne des cibles terrestres ne bouge pas d'un caractère.

---

## 6. Les tests

**`MissionDomainTest` change par une suppression.** Son `continue` sur `LUNAR_ORBIT` tombe, et la
partition, l'accord avec le catalogue et l'invariant de verrouillage couvrent désormais quatre types
et sept cartes sans exception. Le test ne gagne pas un cas : il en perd une.

- `MissionProfileTest` : la septième constante avec les deux valeurs dont dépend le dimensionnement
  de `L3` (le plancher à 50 km, le défaut à 100), et les deux refus devenus des réponses.
- `MissionResultTextTest` : la ligne d'écart **inchangée au caractère** avec une inclinaison, et sans
  le champ en degrés sans elle. C'est la seule modification du lot qui touche un écran en service.
- `MissionTargetOrbitTest` : `lunarOrbitTargetIsAbsent` devient son contraire.
- `LunarOrbitDynamicParametersTest` : la durée dérivée, épinglée à 4,98 j à 100 km et à sa dérive
  avec l'altitude, avec l'assertion explicite que publier les révolutions seules tronquerait la
  mission.
- `ScenarioRoundTripTest` : le quatrième type de l'aller-retour, avec **deux** charges à ne pas
  perdre — celle du lanceur et celle du réservoir d'insertion, cette dernière étant la seule que le
  format n'écrit pas et que la fabrique doit redimensionner à l'identique.

**Un test existant a échoué et a eu raison de le faire.** `onlyGeoAndLunarProfilesCarryTheirOwnType`
rangeait toute constante non nommée sous `MissionType.LEO` ; la septième carte l'a démenti. Il est
renommé et étendu, et ce qu'il garde vraiment tient : `earthOrbitProfiles()` en rend toujours
**quatre**, ce qui dit que le filtre par type de `MIS-4 / L5` continue de tenir un septième constant
à l'écart d'un panneau de perigée/apogée.

**Puis l'essai manuel** : la carte et son icône dans l'onglet `MOON`, le curseur, la durée à ~5 jours,
le refus de masse sèche au-delà de 2 433 kg, et la ligne d'écart sans degrés.

---

## 7. Ce que `L7` ne fait pas

**Le trou de fenêtre reste ouvert, et c'est délibéré.** Le §1.2 fait 1 en donne la raison : la
décision est écrite dans `LunarLaunchWindowProblem` et dans `MIS-4 / L2` §1.3, elle touche les deux
cartes lunaires à l'identique, et la renverser est un lot à soi — il faudrait décider ce que le
wizard fait d'une date sans fenêtre, et cette question n'appartient pas à la carte qui arrive.

Trois écarts connus, consignés et non corrigés : le biais de 7 % sur la durée dérivée de GEO (§3.1),
la ligne de valeur du flyby qui montre son défaut là où LEO et SSO montrent leur bande (§2), et la
contrainte de nommage que le javadoc de `MissionProfile.LUNAR` invoque et que la mesure dément (§1.2
fait 6) — laissée telle quelle plutôt que réécrite, faute d'avoir mesuré ce qui l'a motivée à
l'origine.

---

## 8. Risques

1. **Le refus de masse sèche est à portée de clavier** : défaut 2 000 kg, refus vers 2 433. Son
   chemin — `PropellantBudget` → `compositionRefused` → `showRefusal` — n'a jamais été emprunté pour
   ce type. C'est le premier point de l'essai manuel.
2. **La durée par défaut est de ~5 jours**, la plus longue du catalogue. Créer la mission depuis le
   wizard lance l'optimiseur sur une ascension complète puis cinq jours de propagation : ce n'est pas
   neuf, mais c'est la carte la plus lente à répondre.
3. **`formatMiss` est partagé** avec LEO, GEO et les quatre préréglages. Le test le tient au
   caractère près, mais c'est la seule modification du lot qui touche un écran existant.

---

## 9. Ce que le chantier laisse

`MIS-5` est clos sur ses sept lots. Ce qui reste ouvert et nommé ailleurs : le **vol de clôture**
`LunarOrbitFlightTest`, derrière `orbitlab.slowTests`, qui porte les trois assertions et les cinq
mesures de `L5` ; la dette **ε** de `PHY-4 / L6` §5.5, close par verdict en `L5` §8 et rouverte pour
`MIS-11`, dont le retour arme un détecteur de sortie qui tire ; et le trou de fenêtre du §7, qui est
désormais le seul écart entre ce que le wizard laisse créer et ce que la géométrie autorise.
