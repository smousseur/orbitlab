# PHY-1 / L0 — Baseline mesurée et prototype

Lot `L0` du [découpage](02-decoupage.md). Aucun code de production n'a été écrit.
Le prototype qui a produit ces chiffres était un test jetable, supprimé après
mesure ; sa sortie brute est conservée sous [`baseline/prototype-L0.log`](baseline/prototype-L0.log).

**Mesuré le 2026-08-20**, sur `main` au commit `bf7fc0d`, JDK GraalVM 21.0.5,
Orekit 13.1.1, `orekit-data.zip` embarquée.

**Comment lire ce document.** Le §2 est le seul qui compte pour la suite : il
contient les trois mesures qui changent la façon d'écrire `PHY-2`. Le §1 est un
tableau de référence, le §3 la procédure d'exécution, le §4 ce qui reste à mesurer.

---

## 1. Densités des deux modèles

Équateur, longitude 0, au 2026-03-01T12:00:00Z. F10.7 quotidien lu par
`CssiSpaceWeatherData` à cette date : **158,7**.

| alt (km) | Harris-Priester (kg/m³) | NRLMSISE-00 (kg/m³) |
|---:|---|---|
| 0 | **lève** | 1,180292 × 10⁰ |
| 10 | **lève** | 4,239342 × 10⁻¹ |
| 30 | **lève** | 1,817574 × 10⁻² |
| 60 | **lève** | 3,291144 × 10⁻⁴ |
| 90 | **lève** | 3,807629 × 10⁻⁶ |
| 100 | 4,974000 × 10⁻⁷ | 6,907985 × 10⁻⁷ |
| 150 | 2,199931 × 10⁻⁹ | 2,062070 × 10⁻⁹ |
| 200 | 3,063968 × 10⁻¹⁰ | 3,266513 × 10⁻¹⁰ |
| 250 | 8,724831 × 10⁻¹¹ | 9,665025 × 10⁻¹¹ |
| 400 | 6,642444 × 10⁻¹² | 6,779314 × 10⁻¹² |
| 800 | 6,867693 × 10⁻¹⁴ | 3,644509 × 10⁻¹⁴ |
| 1000 | 1,535348 × 10⁻¹⁴ | 7,053947 × 10⁻¹⁵ |
| 1200 | **0,0** | 2,857376 × 10⁻¹⁵ |
| 1500 | **0,0** | 1,180740 × 10⁻¹⁵ |

**Contrôle de vraisemblance.** NRLMSISE-00 rend 1,180 kg/m³ au niveau de la mer
contre 1,225 de l'atmosphère standard — l'écart est celui attendu à l'équateur, où
il fait chaud. À 100 km, 6,9 × 10⁻⁷ est dans la fourchette publiée (≈ 5 × 10⁻⁷). Les
deux modèles se tiennent à moins de 10 % entre 150 et 400 km, et divergent d'un
facteur 2 au-delà de 800 km, Harris-Priester étant le plus dense — cohérent avec sa
nature de modèle statique calé sur une activité solaire moyenne à forte.

---

## 2. Les trois mesures qui comptent

### 2.1 Harris-Priester a une bande de validité étroite, et deux modes de défaillance opposés

`HarrisPriester.getMinAlt()` / `getMaxAlt()` renvoient **[100 km, 1000 km]**. Aux
deux bords, le comportement n'est pas le même :

- **En dessous de 100 km, il lève** une `OrekitException` — il ne clampe pas, il ne
  rend pas zéro ;
- **au-dessus de 1000 km, il rend 0,0 en silence.**

L'exception n'est pas levée là où on pourrait la rattraper. La trace mesurée passe
par `DragForce.acceleration` → `ForceModel.addContribution` →
`NumericalTimeDerivativesEquations.computeTimeDerivatives` →
`ExplicitRungeKuttaIntegrator.applyInternalButcherWeights` : elle survient **pendant
l'évaluation interne d'un pas d'essai**, avant tout contrôle de pas et avant tout
détecteur. C'est exactement le mode de défaillance que `OrekitService` documente
déjà pour la masse négative à l'allumage tardif.

**Conséquence pour `PHY-2`, et elle est structurante.** Une ascension part du pas de
tir, à 0 km. **Harris-Priester ne peut donc pas voler une ascension** — ni même
l'approcher. Or l'ascension est précisément la phase où la traînée compte
(l'étude d'impacts l'estime à 100–300 m/s sur un lanceur lourd), et c'est là que le
catalogue compense aujourd'hui par ses ISP « proxy ».

La recommandation de l'étude d'impacts — *« Harris-Priester pour l'optimisation,
NRLMSISE-00 pour le playback »* — n'est donc pas seulement privée de sa couture
(le 8×8 / 50×50 n'existe plus, cf. découpage §2.2) : elle est **inapplicable** au
seul endroit qui motive le chantier. Soit `PHY-2` retient NRLMSISE-00 partout, soit
il faut une bascule de modèle selon l'altitude — décision qui lui revient, mais
qu'il ne peut plus prendre en croyant les deux modèles interchangeables.

### 2.2 La traînée est gratuite en nombre de pas — jusqu'à ce qu'elle ne le soit plus du tout

Parking circulaire i = 51,6°, B = m/(Cd·S) = 101 kg/m², NRLMSISE-00, 24 h demandées.

| alt initiale | pas | t atteint | alt finale | temps | issue |
|---:|---:|---:|---:|---:|---|
| 400 km | 431 | 86 400 s | 392,6 km | 171 ms | OK |
| 300 km | 441 | 86 400 s | 299,2 km | 196 ms | OK |
| 250 km | 446 | 86 400 s | 250,4 km | 288 ms | OK |
| 200 km | 452 | 86 400 s | 183,7 km | 226 ms | OK |
| 175 km | 722 | 63 360 s | **−9,2 km** | 474 ms | pas minimum atteint |
| 150 km | 474 | 16 140 s | **−8,3 km** | 307 ms | pas minimum atteint |
| 130 km | **982 497** | 43 080 s | **−30,1 km** | **487 366 ms** | pas minimum atteint |

Sans traînée, la même propagation à 250 km prend **446 pas** — c'est-à-dire
*exactement* le compte mesuré avec traînée. **Au-dessus de 200 km, la traînée ne
coûte aucun pas d'intégration supplémentaire.** Le risque §6.2 de l'étude d'impacts
(« le pas adaptatif doit gérer ») est donc infondé dans le régime que les missions
volent.

**Il est en revanche parfaitement fondé en dessous.** À 130 km, la même propagation
demande **982 497 pas et 487 secondes** — huit minutes de temps mural pour un vol qui
en prend 0,2 à 200 km, soit un facteur **2 174× en pas** et **2 100× en temps**. Et
elle échoue quand même.

C'est le chiffre le plus important de `L0`. Un optimiseur CMA-ES explore des milliers
de candidats ; il suffit qu'**un seul** pique bas pour qu'une évaluation passe de
0,2 s à 8 minutes. Activer la traînée sans borne d'altitude ne rend pas
l'optimisation plus lente, elle la rend **non terminante**.

### 2.3 `ReentryGuard`, tel qu'il est réglé, n'arrête pas une rentrée par traînée

Les quatre cas basses altitudes ont été rejoués avec `ReentryGuard.armQuiet` armé —
ce que fait `StageLegRunner` sur chaque leg de chaque étage en production.

| alt initiale | sans garde | avec garde |
|---:|---|---|
| 200 km | 452 pas, OK | 452 pas, OK |
| 175 km | 722 pas, échec à 63 360 s | **722 pas, échec à 63 360 s** |
| 150 km | 474 pas, échec à 16 140 s | **474 pas, échec à 16 140 s** |
| 130 km | 982 497 pas, échec à 43 080 s | **982 497 pas, échec à 43 080 s** |

**La garde ne change rien du tout** — pas un pas, pas une milliseconde, pas une
seconde de date d'échec. La cause est son plancher : `ReentryGuard.SUBSURFACE_FLOOR`
vaut **−50 km**, choisi parce que l'altitude *sphérique* d'un pas de tir est déjà
négative (la Terre est aplatie de 21,4 km). Or l'intégrateur meurt à −9 km et
−30 km, c'est-à-dire **au-dessus du plancher**. La garde est franchie par le bas
avant d'avoir eu l'occasion de se déclencher.

Aujourd'hui c'est sans conséquence : sans atmosphère, une trajectoire qui descend
descend vite et traverse les −50 km en quelques pas. Avec traînée, elle s'y attarde
et l'intégrateur cède d'abord. **`PHY-2` hérite donc d'un détecteur inopérant dans
le seul régime pour lequel il existe**, et devra soit relever le plancher, soit
ajouter une borne haute distincte — celle que le §2.2 réclame de toute façon pour
des raisons de temps de calcul.

---

## 3. Procédure d'exécution fiable

Deux pièges, tous deux mesurés pendant ce lot.

**`BUG-7`.** Les gates `CentralBodyBaselineTest` et `MissionPolylineBaselineTest`
tombent au dernier bit quand un test lunaire les précède dans le même JVM. Les
exécuter isolément.

**`cleanTest` et le vert menteur.** Relancer le même filtre `--tests` après un
succès rend la tâche `UP-TO-DATE` et n'exécute rien, en affichant `BUILD SUCCESSFUL`.
Toute mesure passe par `cleanTest`.

**Un troisième piège, découvert ici.** Sur Windows, `cleanTest` échoue avec
`Unable to delete directory … test-results\test\binary` tant qu'une exécution
précédente tient encore le fichier — y compris une exécution qu'on croit terminée.
Le symptôme est un `BUILD FAILED` sans rapport avec le test. Attendre la fin
effective du run précédent, ou supprimer `build/test-results/test` à la main.

Le JDK est imposé :

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew cleanTest test --tests "*CentralBodyBaselineTest*"
```

---

## 4. Ce qui reste à mesurer

**La baseline des profils existants** — orbites atteintes, masses restantes et
durées de calcul pour LEO-400, GEO, MEO, Ariane 62 et polaire — n'est pas dans ce
document. Elle demande la suite d'optimisation, qui est lente et que l'utilisateur
lance lui-même. Elle est à ajouter ici en §5 avant d'ouvrir `L1`.

Cette absence ne bloque pas `L1` : le renommage `GravitationalContext` →
`FlightContext` se ferme sur la compilation et sur les quatre gates, dont aucun ne
dépend des chiffres manquants.

---

## 5. Ce que `L0` a changé au découpage

Deux points du [découpage](02-decoupage.md) sont modifiés par ces mesures :

- **§6 question 2** (« le plancher d'application du modèle ») n'est plus une question
  ouverte : la bande est mesurée, [100 km, 1000 km] pour Harris-Priester, et le
  comportement aux deux bords est connu. Ce qui reste ouvert, et qui est plus grave,
  c'est le choix de modèle pour l'ascension (§2.1 ci-dessus).
- **§8** gagne une entrée léguée à `PHY-2` : `ReentryGuard` est inopérant avec
  traînée (§2.3), et une borne d'altitude est nécessaire pour que l'optimisation
  reste terminante (§2.2).

Aucune des quatre décisions du §3 du découpage n'est remise en cause.

---

*Mesuré et rédigé le 2026-08-20.*
