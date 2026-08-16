# MIS-7 — P2 : les orbites terrestres dans le wizard

> Statut : design validé, 2026-08-16. Suite directe de
> [`01-mission-terre-parametrable.md`](01-mission-terre-parametrable.md) §14, dont P1 est livré en
> entier (`P1.a` → `P1.d`).
> Périmètre : cartes du wizard, champ d'inclinaison, ordre des étapes, remontée du refus MEO.
> **Aucune physique n'est touchée** — §14.4 tient.

---

## 0. La promesse de P1, et ce qu'il en reste à faire

P1 s'est terminé sur une affirmation à vérifier : « tout ce qui suit est de la saisie de
paramètres ». La lecture du code la confirme, à une ligne près, qui porte même son propre
commentaire :

```java
// MissionFactory:96
LaunchPlane plane = LaunchPlane.dueEast(latitude);   // ← P2 remplace ceci
```

En aval de cette ligne, tout suit tout seul : `PropellantBudget` applique déjà l'assistance signée
à l'azimut, `MissionComposer` choisit déjà la chaîne sur l'apogée, le trim de plan entre déjà dans
la composition dès que le plan est commandé, et `MissionTargetOrbit` lit déjà
`spec.targetInclination()`. P2 n'a donc rien à construire en dessous du spec — seulement à cesser
d'écrire une constante à sa place.

---

## 1. La première décision de §14.2 : des profils, pas des types

§14.2 posait la question sans la trancher : quatre cartes derrière un seul `MissionType`, ou quatre
`MissionType` ? Elle donnait deux arguments en faveur des types. **Le premier est faux.**

**`requiresPayloadPropulsion()` ne s'applique pas au MEO.** `MissionComposer.composeHighOrbit`
refuse une cible hors de portée de l'ascension seulement quand l'étage supérieur *et* la charge
utile sont hors-jeu : `!stageHoldsTheCoast && !payloadCanTakeOver`. Un Ariane 62, qui déclare 6 h de
coast contre les 2,98 h exigées, vole donc un MEO avec une charge utile **inerte**. Déclarer
`requiresPayloadPropulsion = true` sur un type MEO filtrerait la liste de charges utiles de
`StepLauncher` et interdirait précisément cette mission-là. L'argument pointe dans l'autre sens.

**Le second — les défauts d'horizon — est réel, mais ne suffit pas.** Il concerne le seul MEO
(48 révolutions d'une orbite de 12 h font ~24 jours), et c'est une question d'**apogée**, pas de
carte : c'est déjà sur l'apogée que §6.1 décide de la chaîne. Voir §7.

Reste l'argument décisif, contre les types : `MissionSpec.EarthOrbit` renvoie aujourd'hui
`MissionType.LEO` en dur. Lui faire porter son type ajouterait un composant **redondant avec
`targetInclination`** — un `EarthOrbit(type = POLAR, i = 28°)` deviendrait représentable et
n'aurait aucun sens. C'est mot pour mot l'incohérence que §3.2 refuse pour `targetEccentricity` :
« ajouter un troisième paramètre redondant serait une source d'incohérence, pas une
généralisation ».

**Décision. `MissionType` reste `LEO` + `GEO`. `MissionSpec` ne bouge pas.** Les cartes sont des
**profils d'UI**.

---

## 2. `MissionProfile`

Un enum dans `ui/mission/wizard`, **sans dépendance Lemur** — ce qui le rend testable en headless,
contrairement aux étapes du wizard.

```java
enum MissionProfile { LEO, POLAR, SSO, MEO, GEO }
```

Chaque valeur porte ce qui distingue une carte d'une autre, et rien de plus :

| Donnée | Rôle |
|---|---|
| `MissionType missionType` | `LEO` pour les quatre premiers, `GEO` pour le dernier |
| titre, sous-titre, ligne de valeur, badge, chemin d'icône | la carte |
| bornes et défaut d'altitude, cible circulaire ou non | le panneau de paramètres |
| `InclinationMode` + valeur par défaut | le champ d'inclinaison |

```
LEO    i libre, défaut = latitude du site   200 – 2 000 km, périgée et apogée séparés
POLAR  i libre, défaut 90°                  200 – 2 000 km, périgée et apogée séparés
SSO    i DÉRIVÉE de l'altitude              600 –   800 km, circulaire
MEO    i libre, défaut 55°                  2 000 – 35 000 km, circulaire
GEO    sans objet                           inchangé (GEODynamicParameters)
```

### 2.0 L'absence de la clé est la non-régression

Le défaut du profil LEO est la latitude du site — c'est-à-dire le plein est, ce que toute mission
volait avant P2. Le publier serait pourtant une régression silencieuse : `LaunchPlane.dueEast(φ)`
construit son inclinaison depuis la latitude en double, quand un champ de formulaire en donne
l'arrondi affiché. Un écart de 0,004° passe sous le seuil de `commands()` — l'attitude reste donc
sur le chemin historique, et aucune assertion d'inclinaison ne bouge — mais il déplace l'azimut,
donc l'assistance signée de `PropellantBudget`, donc les chargements, **donc la trajectoire**. Le
plafond de non-régression tomberait sans qu'aucun test de plan ne s'en aperçoive.

Le champ d'inclinaison reprend donc le contrat que `MISSION_HORIZON_DAYS` a déjà : **son absence
est signifiante.** Tant que le profil LEO laisse le champ sur sa valeur dérivée, `getValues()`
n'écrit pas la clé, et `MissionFactory` reprend `LaunchPlane.dueEast(latitude)` — le spec d'avant
P2, au bit près. La clé n'apparaît qu'à partir du moment où l'inclinaison est une intention :
première frappe de l'utilisateur, ou choix d'un profil POLAR, SSO ou MEO, qui la publient toujours.

C'est aussi ce qui fait qu'une mission LEO rouverte revient sur son état dérivé au lieu de se figer
sur un nombre, sans qu'un second drapeau ait à voyager dans la map de valeurs.

**POLAR n'est pas un champ verrouillé à 90°, c'est un préréglage.** Le verrouiller ajouterait une
règle pour rien : dans le modèle, une polaire *est* un `EarthOrbit` avec i = 90°, exactement comme
§3.2 le dit. SSO est le seul profil dont le champ est en lecture seule, et pour une raison de fond :
l'inclinaison y est réellement **dérivée** (`LaunchPlane.sunSynchronous(altitude)`), pas choisie.
Elle se recalcule à chaque mouvement du curseur d'altitude.

### 2.1 La réouverture : dériver le profil, ne pas le stocker

Une mission rouverte doit rallumer la bonne carte. Le profil n'étant pas dans le spec, il s'en
déduit — une fonction pure, `MissionProfile.of(MissionSpec)` :

```
apogée > 2 000 km                                    → MEO   (le plafond de MissionComposer)
|i − sunSynchronousInclination(altitude)| < ε        → SSO
|i − 90°| < ε                                        → POLAR
sinon                                                → LEO
```

Le stockage d'un composant descriptif sur le spec (à l'image de `siteName`) a été écarté : il
pourrait contredire l'inclinaison, et la dérivation ne coûte rien. Son seul risque est cosmétique —
une SSO qui retomberait sur LEO afficherait la bonne inclinaison en saisie libre, ce qui est une
dégradation acceptable et non une perte.

### 2.2 En édition

`MissionEntry.applySpec` ne refuse qu'un changement de **type**. Passer de LEO à POLAR sur une
mission existante est donc légitime pour le modèle, et c'est bien ce qu'éditer veut dire — changer
l'inclinaison d'une mission. Les quatre cartes terrestres restent donc cliquables en édition d'une
mission `EarthOrbit`, la carte GEO étant désactivée ; et l'inverse pour une mission `Geo`.

---

## 3. Le panneau de paramètres

`LEODynamicParameters` devient `EarthOrbitDynamicParameters`, construit **depuis un profil** plutôt
que depuis deux bornes d'altitude. Il garde ses deux curseurs (un seul quand le profil est
circulaire) et gagne une ligne d'inclinaison.

`StepParameters` indexe ses panneaux par profil au lieu de les indexer par type. Le profil lui
parvient par un callback du widget — **pas** par `MissionContext`, qui est du simulation-layer et
n'a pas à connaître un enum d'UI. `MissionContext.setSelectedMissionType` continue d'être écrit par
`StepMissionType`, pour `StepLauncher` qui n'a besoin que du type.

---

## 4. La validation : une seule implémentation de la règle

`StepParameters` **n'implémente pas** `[|φ|, 180° − |φ|]`. Il appelle
`LaunchPlane.ofDegrees(i).requireReachableFrom(latitude)` dans un `try/catch` et affiche le message
de l'exception, qui nomme déjà la plage atteignable et le minimum. Une seule écriture de la règle,
et ce que lit l'utilisateur est exactement ce que dit le modèle.

Le nouveau `validateInclination()` suit le contrat de `validateLaunchDate()` : il renvoie un
`Optional<String>`, marque le champ, et `parametersRefused()` l'ajoute à la liste sans
court-circuiter les autres — un utilisateur qui a une date fautive *et* une inclinaison fautive doit
voir les deux d'un coup.

Le refus reste par ailleurs porté par le constructeur compact de `MissionSpec.EarthOrbit` : l'UI
avance la détection, elle ne la remplace pas.

---

## 5. L'ordre des étapes

§14.3 le signalait : l'inclinaison se saisit avant la latitude qui la borne. L'ordre devient

```
MISSION → SITE → PARAMETERS → LAUNCHER
```

qui se lit d'ailleurs mieux : d'où l'on part, puis où l'on va.

**Réordonner ne suffit pas** — le stepper autorise les sauts d'étape, et les champs de coordonnées
sont éditables après le choix du cosmodrome. `StepParameters` reçoit donc un accès à la latitude
**vive** (`StepLaunchSite.currentLatitude()`, qui relit le champ texte) et re-borne le champ
d'inclinaison à chaque affichage, plutôt qu'une fois pour toutes à la construction.

---

## 6. Le refus MEO, remonté à l'écran

Second piège de §14.3 : une cible à 20 200 km n'est réfutable qu'une fois le lanceur choisi. Le
message que lève `MissionComposer` est déjà parfait — il nomme l'étage, la durée exigée et la durée
déclarée — mais il finit aujourd'hui dans le log de `MissionWizardAppState`, et l'utilisateur ne
voit rien : le wizard se ferme et aucune mission n'apparaît.

Sur `goNext()` depuis LANCEUR, avant `onSubmit`, le wizard **compose à blanc** : `specFromWizardValues`
puis `MissionComposer.compose`. Aucune propagation n'a lieu, seulement le dimensionnement analytique
et le montage des étages ; le coût est négligeable. Sur `OrbitlabException`, le wizard reste ouvert
et `StepLauncher` affiche le message tel quel.

---

## 7. Ce que P2 ne fait pas, et pourquoi

**La branche de nœud n'est pas exposée.** Toute mission créée par le wizard part sur `ASCENDING`.
C'est la bonne branche pour une SSO — l'azimut y vaut −8,2°, nord-nord-ouest — donc rien n'est
bloqué ; et comme aucune clé n'est publiée, il n'y a rien à prefiller et l'aller-retour reste
stable. §14.1 la demandait ; elle est reportée, pas oubliée.

**Le catalogue de sites n'est pas extrait.** `StepLaunchSite` garde son `record SiteData` privé.
§12 le prévoit, mais il n'est prérequis d'aucun des points ci-dessus.

**Le défaut d'horizon du MEO reste à 48 révolutions**, soit ~24 jours à 12 h de période, ~34 000
points d'éphéméride — sous le plafond de 30 jours de `MissionHorizon.MAX_COAST_SECONDS`. Ce n'est
pas un bon défaut, mais c'est un défaut **honnête** : le champ affiche « 23,94 » et dit d'où le
nombre vient, et l'utilisateur peut en écrire un autre.

L'alternative — rendre le défaut fonction de l'apogée, 3 révolutions au-dessus du plafond de
2 000 km comme le GEO — tient en dix lignes, mais elle **déplace la bande d'altitude que mesure
`MeoMissionTest`** : les 19 637 – 20 202 km de §11.1 sont mesurés sur 24 jours de coast et le
seraient sur 1,5 jour. Un test lent à relancer et un chiffre publié à corriger, pour une question
qui n'est pas celle de P2. Elle mérite sa propre fiche.

---

## 8. Les tests

Aucun n'instancie de widget : les étapes du wizard demandent JME et Lemur, et c'est précisément
pourquoi `MissionProfile` est écrit sans eux.

| Test | Assertion |
|---|---|
| `MissionProfileTest` | `of(spec)` retrouve le profil sur les cinq formes ; bornes et défauts cohérents avec le mode d'inclinaison |
| `MissionFactoryTest` (ajouts) | inclinaison honorée ; **absente ⇒ plein est**, au bit près comme avant P2 (§2.0) ; inatteignable ⇒ `OrbitlabException` |
| `WizardPrefillTest` (ajout) | une mission polaire rouverte reste polaire — inclinaison et profil ; une mission plein est rouverte **ne publie pas** la clé |

La porte de non-régression est la même qu'en P1, et pour la même raison : `EarthOrbitNonRegressionTest`,
`LEOMissionOptimizationTest`, `GEOMissionOptimizationTest`, `AscentBaselineN2Test` doivent rester
verts **sans qu'une tolérance soit touchée**. Le chemin qui les protège est celui de `MissionFactory` :
une clé d'inclinaison absente doit continuer de produire `LaunchPlane.dueEast(latitude)`, donc
exactement le spec d'avant P2.

---

## 9. Ordre d'exécution

| Lot | Contenu | Sortie |
|---|---|---|
| **P2.a** | `MissionProfile` + `MissionProfileTest` | l'enum et sa dérivation, rien de câblé |
| **P2.b** | `FormField`, `MissionFactory`, `WizardPrefill` + tests | **le spec sait porter une inclinaison venue du formulaire** |
| **P2.c** | ordre des étapes, latitude vive, `EarthOrbitDynamicParameters`, validation | **polaire et SSO volent depuis l'application** |
| **P2.d** | 5 cartes dans `StepMissionType`, règle d'édition | les profils sont choisissables |
| **P2.e** | composition à blanc et affichage du refus dans `StepLauncher` | **le MEO est refusé lisiblement, ou volé** |

`P2.b` avant `P2.c` est délibéré : la couture est testable sans une ligne d'UI, et c'est elle qui
porte la non-régression.
