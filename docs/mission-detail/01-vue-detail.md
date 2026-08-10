# UI-1 — Vue détail mission

> Statut : **implémentée et vérifiée à l'écran le 2026-08-10**.
> Fiche roadmap : [`docs/roadmap/01-roadmap.md`](../roadmap/01-roadmap.md) §6, `UI-1` — ★4 ◆2 M.
>
> **Corrections apportées à cette spec pendant l'implémentation** (§7) : le
> décompte des stages GEO était faux (12, pas 8), d'où une hauteur de fenêtre
> revue de 545 à 640 px ; `MissionTargetOrbit` a une forme différente de celle
> annoncée ; une classe non prévue, `MissionResultText`, a été ajoutée.

---

## 1. Le problème

L'application optimise des trajectoires sans jamais dire ce qu'elle a obtenu.
`PanelFooter` affiche l'identité de la mission sélectionnée (nom, statut, mode)
et ses attributs (type, date, site) — rien du résultat. Une mission qui échoue
affiche `FAILED` et rien d'autre : l'exception part dans le log.

La fiche roadmap résume la situation par « `MissionOptimizerResult` et
`AchievedOrbit` sont calculés et stockés — et aucun fichier de `ui/` ne les
lit ». L'exploration du code montre que c'est **plus grave que ça** sur trois
points, chacun ajoutant du travail hors de la couche `ui/`.

### 1.1 `AchievedOrbit` n'est pas seulement non lu : il est jeté

`MissionComputeResult` transporte bien `achievedOrbit` et `performanceReport`,
et sa Javadoc annonce explicitement « carried on the result rather than merely
logged **so the UI can display it** without recomputing ».

Mais `MissionOrchestratorAppState.submitForComputation()` ne retient que trois
des cinq composants :

```java
entry.setMission(result.mission());
entry.setOptimizerResult(result.optimizerResult());
entry.setEphemeris(result.ephemeris());
// result.achievedOrbit() et result.performanceReport() : perdus ici
```

Aucun champ de `MissionEntry` ne peut les accueillir. Le chantier commence donc
par **stocker**, pas par lire.

### 1.2 La durée d'un stage n'existe nulle part

`StagePerformance` porte `stageName`, `massIn`, `massOut`, `propellantConsumed`
et `deltaV`. Pas de durée. La « liste des stages avec durée et Δv » demandée par
la roadmap suppose donc une modification de la couche simulation, pas seulement
un affichage.

### 1.3 L'objectif d'une mission GEO n'est pas l'orbite GEO

`GEOMission` enregistre `new OrbitInsertionObjective(EARTH, parkingAltitude,
targetAltitude, toRadians(latitude))` : c'est la **GTO**, périgée sur l'orbite
de parking et inclinaison égale à la latitude du pas de tir.
`MissionPlanOptimizer` le sait et le contourne déjà pour la faisabilité — « GEO's
recorded objective is (parking, GEO); feasibility must be measured against the
flown circular GEO orbit ».

Afficher `mission.getObjective()` comme « cible » montrerait, sur une mission
GEO parfaitement réussie, un écart de périgée d'environ 35 000 km et un écart
d'inclinaison égal à toute la latitude du site. **La cible affichable se résout
depuis `MissionSpec`, jamais depuis l'objectif.**

### 1.4 La place disponible

La fenêtre du panel fait 720 × 520, le footer 78 px sur deux lignes, la zone
liste 354 px pour 5 lignes. Le décompte exact de la liste, marge à marge :

| Poste | Hauteur |
|---|---:|
| `PAD_Y` haut + bas | 40 |
| Barre d'actions | 32 |
| Espaceur | 12 |
| En-tête de colonnes | 14 |
| Espaceur + séparateur + espaceur | 13 |
| 5 lignes × 46 + 4 séparateurs | 234 |
| **Total** | **345** |

Soit 9 px de marge sur les 354 disponibles. Une mission GEO compte 12 stages
(voir §7.1) ; le tableau des stages plus les lignes d'orbite demandent 392 px.
**Le détail complet ne tient pas dans le footer**, quelle que soit la mise en
page.

---

## 2. Décisions

| Question | Décision | Pourquoi |
|---|---|---|
| Emplacement | Hybride : 3ᵉ ligne compacte dans le footer + vue détail dédiée | Le résultat est visible dès la sélection ; le détail lourd ne compresse pas la liste |
| Convention d'écart | Osculateur **et** moyen affichés, chacun avec son écart contre les mêmes chiffres de cible | Aucune table type-mission → convention à maintenir ; §4.2 |
| Message d'erreur | Message brut de l'exception (`SimpleName: message`) | Fidèle, zéro maintenance, identique au log ; un cas mal classé mentirait |
| Source de la cible | `MissionSpec`, par `switch` sur l'interface scellée | §1.3 ; le compilateur signale un 3ᵉ type de mission |
| Hauteur de fenêtre | 520 → **640** px, footer 78 → 100 px | Garde les 5 lignes de liste (§1.4) **et** la chaîne GEO complète (§7.1) |
| Entrée dans la vue détail | Bouton texte `DETAILS >` dans le footer | Les icônes d'action sont des textures `icon-action-*.png` ; aucune n'existe pour ce geste. Révisable si une icône est produite plus tard |
| Pagination du tableau des stages | Aucune, plafond à **16** lignes | Les 12 stages GEO tiennent dans les 412 px utiles ; le plafond reste un garde-fou, pas une limite active |

---

## 3. Plomberie : stocker ce qui est déjà calculé

### 3.1 Trois champs sur `MissionEntry`

Mêmes garanties que les champs voisins : `volatile`, écrits par le thread
`mission-optimizer`, lus par le thread JME.

| Champ | Accesseur | Source |
|---|---|---|
| `achievedOrbit` | `Optional<AchievedOrbit> getAchievedOrbit()` | `MissionComputeResult.achievedOrbit()` |
| `performanceReport` | `Optional<MissionPerformanceReport> getPerformanceReport()` | `MissionComputeResult.performanceReport()` |
| `lastError` | `Optional<String> getLastError()` | les deux sites qui passent en `FAILED` |

`Optional` en type de retour uniquement, champs nullables — règle `CLAUDE.md`,
et forme déjà retenue par `getOptimizerResult()` / `getEphemeris()` /
`getScheduledDate()` dans le même fichier.

`publish()` doit invalider `achievedOrbit` et `performanceReport` en plus de
`optimizerResult` et `ephemeris`. Sans ça, un toggle de mode ou une édition
wizard laisse le footer afficher l'orbite d'une composition abandonnée, alors
que la mission est repassée en `DRAFT`.

### 3.2 Alimentation de `lastError`

Un helper statique unique sur `MissionEntry` :

```java
static String describeFailure(Throwable e)  // SimpleName + ": " + message
```

avec repli sur `toString()` quand `getMessage()` est nul (fréquent sur les
exceptions Orekit reconstruites). Appelé aux **deux** sites que la roadmap
identifie, où l'exception n'est aujourd'hui que loguée :

| Site | Contexte |
|---|---|
| `MissionEntry.recompose()` | `catch (RuntimeException)` — échec de composition sur toggle de mode ou édition |
| `MissionOrchestratorAppState.submitForComputation()` | `catch (Exception)` — échec de calcul sur le thread optimiseur |

`lastError` est **effacé au passage en `COMPUTING`**, pas au succès : sinon une
mission relancée garde son erreur précédente affichée pendant tout le calcul.

### 3.3 Durée de stage

`StagePerformance` gagne `double durationSeconds`. Les deux appels à
`buildStagePerformance` dans `MissionOptimizer` (branche optimisable et branche
non optimisable) ont les deux dates sous la main : l'état d'entrée avant
propagation, `propagated.getDate()` après.

C'est le seul point de construction du record dans tout le dépôt.
`MissionLoadEvaluatorTest` construit des `MissionPerformanceReport` avec
`List.of()` en guise de stages : il n'est pas touché.

---

## 4. Ce qu'on affiche

### 4.1 La cible — `MissionTargetOrbit`

Record dans `ui/mission/`, aux côtés de `MissionColorPalette` et
`MissionPhaseShading` puisque le footer et la vue détail le partagent.

```java
public record MissionTargetOrbit(
    double perigeeAltitude, double apogeeAltitude, double inclination) {
  public static MissionTargetOrbit of(MissionSpec spec) { … }
  public static Optional<MissionTargetOrbit> forEntry(MissionEntry entry) { … }
}
```

Deux entrées plutôt qu'une : `of(MissionSpec)` porte toute la résolution et se
teste sans construire de `MissionEntry` — donc sans composer une mission, donc
sans Orekit ; `forEntry` n'est que le `map` sur le spec de l'entrée, et c'est
lui qui rend l'absence (entrée legacy) visible.

Résolution par `switch` sur `MissionSpec` :

| Spec | Cible affichée |
|---|---|
| `Leo` | `(perigeeAltitude, apogeeAltitude, i = latitude)` — c'est aussi ce que porte l'objectif |
| `Geo` | `(targetAltitude, targetAltitude, i = finalInclination)` — l'orbite circulaire finale, **pas** la GTO de l'objectif |
| absente (entrée legacy) | `Optional.empty()` : on n'affiche que l'atteint |

Le `switch` sur interface scellée fait **échouer la compilation** si un
troisième type de mission arrive. C'est ce qui distingue ce point de la table de
correspondance écartée pour les messages d'erreur : celle-là serait une
association à l'exécution, périmable en silence.

### 4.2 Les deux conventions, et le piège à ne pas reproduire

La Javadoc d'`AchievedOrbit` est explicite : sur une insertion visée circulaire
à 400 km, mesure du 2026-08-05, l'osculateur donne 400 000 × 400 114 m et le
moyen 390 612 × 409 712 m. **Ces ~9,4 km ne sont pas un raté d'insertion**, et
une UI qui ne montrerait que le moyen ferait passer un ciblage parfait pour un
échec.

S'y ajoute que les deux types de mission ne visent pas dans la même convention :
`AnalyticTrimBurnStage` vise le périgée demandé **en éléments moyens**, tandis
que le coût du gravity turn LEO traite l'objectif comme osculateur.

D'où la décision : les deux lignes, chacune avec son écart contre les mêmes
chiffres de cible, étiquetées, plus une légende grise qui nomme le phénomène.
Le footer, qui ne tient qu'une ligne, montre l'**osculateur** — convention de
précision lanceur, celle qui démontre la qualité du ciblage.

### 4.3 Footer — 3ᵉ ligne

`PanelFooter.HEIGHT` 78 → 100, `MissionPanelWidget.WINDOW_HEIGHT` 520 → 640
(§7.1). La zone contenu passe de 354 à 452 px : la liste garde ses 5 lignes,
avec une marge devenue confortable.

| État | Ligne 3 |
|---|---|
| `READY` avec orbite | `ORBIT 400000 x 400114 m i=51.60 deg   MISS +0/+114 m` |
| `FAILED` | `ERROR  <message>`, tronqué à la largeur ; texte complet dans la vue détail |
| `COMPUTING` | `Computing...` |
| `DRAFT` ou sans résultat | `No result yet - run Compute` |

Bouton texte `DETAILS >` à droite de la ligne, actif seulement s'il y a un
résultat ou une erreur à montrer.

Tout le texte reste en **ASCII pur** : les polices bitmap embarquées ne portent
que les glyphes 32–127, donc `deg` et non `°`, `x` et non `×` — contrainte déjà
documentée en tête de `PanelFooter`.

### 4.4 Vue détail

Nouveau paquet `ui/mission/detail/`, deux fichiers pour garder des unités
courtes : `MissionDetailView` (racine, blocs orbite et totaux) et
`DetailStageTable` (le tableau).

```
< BACK   Falcon LEO 400   [ READY ]

TARGET       400000 x 400000 m   i=51.6000 deg
OSCULATING   400000 x 400114 m   i=51.6012 deg   miss     +0 / +114 m   +0.0012 deg
MEAN         390612 x 409712 m   i=51.5994 deg   miss  -9388 / +9712 m  -0.0006 deg
mean and osculating cannot both be circular - J2 short-period, not an insertion miss

TOTAL DV 9412 m/s    LOADED 1235.8 t    RESIDUAL 284 kg (0.02%)

STAGE               DUR        DV         PROP
Vertical ascent     0:12       412 m/s     38.1 t
Gravity turn        2:31      5980 m/s    402.4 t
Separation          0:00         0           -
Coast               8:04         0           -
Circularization     0:41      1204 m/s     12.3 t
```

Budget hauteur 392 px sur 412 disponibles, les 12 stages GEO compris (§7.1).
Au-delà de 16 lignes, le tableau s'arrête et mentionne le débordement — aucune
mission du catalogue actuel n'y arrive.

**Ajouter un stage à un profil de mission est donc aussi une modification de
mise en page** : `BoxLayout` en `FillMode.None` ne rogne ni ne coupe ses
enfants, donc une zone contenu trop courte ne défile pas, elle dessine
par-dessus le footer.

En `FAILED`, les blocs orbite et stages cèdent la place au texte d'erreur
complet, réparti sur plusieurs lignes.

### 4.5 Navigation

`MissionPanelWidget` gagne un état `LIST | DETAIL` et un `detailMissionId`.
`refresh()` attache l'une ou l'autre vue entre le header et le footer. Le retour
à `LIST` se fait par le bouton `< BACK`, et **automatiquement** si la mission
visée disparaît (suppression) ou repart en calcul — le même filet que celui qui
remet `selectedMissionId` à `null` dans `update()`.

---

## 5. Cadence de redraw

`MissionPanelWidget.buildSnapshot()` reconstruit la vue quand la clé change ;
elle vaut aujourd'hui `id:status:visible:mode`. Deux transitions échappent à
cette clé une fois le détail affiché :

- un recalcul `FAILED` → `FAILED` avec une **erreur différente** ;
- l'arrivée d'une orbite atteinte sans changement de statut visible.

La clé intègre donc la présence d'orbite atteinte et l'erreur courante. Le coût
reste une comparaison de chaînes par frame et par mission, comme aujourd'hui.

Les nouveaux champs sont `volatile` : écrits par le thread optimiseur, lus par
le thread JME, exactement le contrat déjà en place pour `optimizerResult`.

---

## 6. Tests

Unitaires purs, sans JME ni Orekit lourd :

| Test | Ce qu'il verrouille |
|---|---|
| `MissionTargetOrbitTest` | Résolution `Leo`, `Geo` — **en vérifiant explicitement que la cible GEO est l'orbite circulaire et non la GTO de l'objectif** — et entrée legacy sans spec |
| `MissionEntryFailureTest` | `lastError` posé sur échec de recompose, effacé par un `publish()` réussi, et invalidation d'`achievedOrbit` / `performanceReport` par `publish()` |

Les tests d'optimisation LEO et GEO ne sont **pas** relancés dans ce chantier :
ils sont lents et l'utilisateur les lance lui-même. Le seul risque qu'ils
courent est le changement de signature de `StagePerformance`, qui se manifeste à
la compilation et non par un décalage numérique.

---

## 7. Écarts constatés à l'implémentation

Consignés ici pour la traçabilité voulue par [`docs/README.md`](../README.md) :
ce document décrit un chantier et survit à son implémentation.

### 7.1 Une mission GEO compte 12 stages, pas 8

L'erreur la plus coûteuse de cette spec. `GEOMission.buildStages` enchaîne :
`Vertical Ascent`, les **trois** phases d'`AscentSequence.gravityTurn` (premier
burn, séparation, second burn), puis `Parking`, `Coasting parking`,
`GTO injection`, `S2 separation`, `Circularization`, `Trim`, `Plane trim` et
`Coasting`. Douze.

Conséquences, toutes corrigées ci-dessus : le plafond du tableau était une
limite active qui tronquait **toutes** les missions GEO du catalogue, et le
budget de hauteur ne fermait pas — 392 px requis contre 317 disponibles dans une
fenêtre de 545. La fenêtre passe à 640 px et le plafond à 16.

### 7.2 `MissionResultText`, classe non prévue

La spec plaçait le formatage dans les widgets. Ceux-ci exigent un
`AssetManager` et un contexte JME : rien n'y est testable. Tout ce qui peut être
faux — arrondis, signes, unités, le tiret qui signifie « sans objet » — est donc
regroupé dans `ui/mission/MissionResultText`, couvert par 11 tests, et les
widgets ne sont plus que des assemblages de labels.

### 7.3 `publish()` efface aussi `lastError`

Le §3.1 ne demandait que `achievedOrbit` et `performanceReport`. Une mission
recomposée repart en `DRAFT` : une erreur décrivant une composition disparue
n'a plus de référent.

### 7.4 Retour à la liste quand la mission repart en calcul

Le §4.5 le demandait, la première implémentation ne couvrait que la
suppression. `submitForComputation` vide l'éphéméride mais **pas**
`achievedOrbit` ni `performanceReport` : une vue détail laissée ouverte
dessinait l'orbite et les stages du run précédent sous un en-tête `COMPUTING`,
sans rien qui signale qu'ils étaient périmés.

### 7.5 Ordre de publication du statut d'échec

`Mission.status` est un champ nu. L'écriture volatile de `lastError` est donc la
seule arête de publication du thread optimiseur : elle doit venir **après**
`setStatus(FAILED)`. Dans l'autre sens, le thread de rendu pouvait lire la
nouvelle erreur à côté d'un `COMPUTING` périmé et afficher « Computing... »
indéfiniment.

### 7.6 La branche d'échec de `recompose` reste non testée

`MissionEntryFailureTest` couvre `describeFailure`, les accesseurs et
l'invalidation par `publish()`. Forcer `MissionComposer.compose` à lever
demanderait une fixture construite pour casser, qui testerait surtout la
fixture. Les deux `catch` font deux lignes chacun.

---

## 8. Hors périmètre

- `UI-2` — feedback de progression pendant l'optimisation (spinner + compteur
  d'évaluations). Chantier distinct, déjà fiché.
- Nouvelles textures d'icônes : le geste « détail » passe par un bouton texte
  tant qu'aucune icône n'est produite.
- Persistance des résultats entre sessions : rien dans `MissionEntry` n'est
  sérialisé aujourd'hui, et ce chantier ne l'introduit pas.
