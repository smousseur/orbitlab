# UI-2 — Feedback de progression pendant l'optimisation

Spec du chantier `UI-2` de [`docs/roadmap/01-roadmap.md`](../roadmap/01-roadmap.md) §6.
Rédigée le 2026-08-21, après relevé du code existant.

La fiche de la roadmap tenait en trois lignes : indicateur **indéterminé** plutôt
que barre linéaire — le coût d'une évaluation variant d'un facteur ~5, une barre
en nombre d'évaluations serait par moments franchement fausse — plus un compteur
d'évaluations en texte. Ce document dit ce que cela veut dire une fois posé sur
le code réel, et corrige deux hypothèses de la fiche.

---

## 1. L'état des lieux, mesuré

### 1.1 Où vit le calcul

`MissionOrchestratorAppState.submitForComputation` (l. 179-231) passe le statut à
`COMPUTING` **sur le thread de rendu**, puis soumet la tâche à un exécuteur
**mono-thread** `mission-optimizer` (l. 49).

La chaîne traversée, du HUD jusqu'à la fonction objectif :

```
MissionOrchestratorAppState
  └─ MissionPlanOptimizer
       ├─ FixedLoadPlanner                    (FAST, BALANCED)
       └─ MinimizedLoadPlanner                (PRECISE)
            └─ MultiStageLoadOptimizer
                 └─ MissionLoadEvaluator
                      └─ MissionOptimizer     (aussi appelé directement par FixedLoadPlanner)
                           └─ CMAESTrajectoryOptimizer
                                └─ CMAESRunExecutor
```

### 1.2 Les cardinalités réelles

| Niveau | Valeur mesurée | Source |
|---|---|---|
| Étages optimisables par mission | **2 au plus** | `GravityTurnFirstBurnStage`, `TransfertTwoManeuverStage` sont les seules implémentations d'`OptimizableMissionStage` |
| Tentatives par étage | ≤ 3 | `CMAESTrajectoryOptimizer.DEFAULT_MAX_RETRIES = 2` |
| Runs d'exploration par tentative | 4, +2 ou +4 aux retries, **en parallèle** | `numExplorationRuns`, `RETRY_EXPLORATION_RUNS_BONUS` |
| Passes de raffinement | 3 | `REFINEMENT_PASSES` |
| Budget d'évaluations par étage | 40 000 | `MissionLoadEvaluator.DEFAULT_OPTIMIZER_MAX_EVALUATIONS` |
| Balayage de charge (PRECISE) | 3 passes × 45 évaluations, **chacune un `MissionOptimizer` complet** | `MultiStageLoadOptimizer.DEFAULT_MAX_PASSES`, `DEFAULT_MAX_EVALUATIONS` |

Toutes les évaluations passent par **un seul point** : la `MultivariateFunction`
de `CMAESRunExecutor.execute` (l. 108-136). C'est le seul endroit à instrumenter
pour compter — mais il s'exécute sur un pool de `availableProcessors() - 1`
threads (l. 358-362), donc le compteur doit être atomique.

### 1.3 Deux corrections à la fiche

**La file d'attente n'existe pas dans l'énoncé, elle existe dans le code.**
L'exécuteur est mono-thread. Une deuxième mission lancée pendant qu'une première
calcule est **en file**, pas en calcul — et elle affiche aujourd'hui exactement la
même chose que celle qui tourne. Un spinner posé tel quel tournerait sur une
mission où il ne se passe rien. Le chantier doit donc distinguer les deux états.

**Le compteur d'évaluations seul ne dit rien de plus que le spinner.**
L'argument de la fiche contre la barre linéaire vaut tout autant contre un
compteur nu : 40 000 est un plafond quasiment jamais atteint, le commentaire de
`CMAESTrajectoryOptimizer` l. 190-193 pose que les trois sorties anticipées
« sont l'issue normale, pas l'exception ». Un nombre qui monte sans dénominateur
ne dit que « je suis vivant », ce que le spinner dit déjà. Ce qui informe, c'est
la **position dans la séquence** : étage 1/2, tentative 2/3.

### 1.4 Les contraintes que la fiche ne mentionne pas

**Les panneaux sont pilotés par diff de snapshot.** `MissionPanelWidget.update`
(l. 178-185) reconstruit tout le corps de la fenêtre dès qu'une `List<String>`
change. Un compteur d'évaluations placé dans ce snapshot reconstruirait toutes
les lignes à la cadence du compteur, pendant plusieurs minutes.

**Rien n'anime dans `ui/`.** Aucun widget n'accumule `tpf` : tous les
`update(float tpf)` du paquet l'ignorent ou s'en servent pour synchroniser un
slider. La progression sera le premier élément animé du HUD. `ProgressBar`
existe, mais ne sert qu'à la progression d'étapes du wizard (`WizardFooter:81`)
et n'anime rien.

**Les polices bitmap du HUD ne contiennent que l'ASCII.** Relevé sur les
fichiers `.fnt` : `ibmplexmono-regular-11` comme `sora-medium-13` ne déclarent
que les codepoints 0-127. Pas de braille, pas de `⟳`, pas de semi-graphiques —
un glyphe de spinner Unicode ne dessinerait rien, et en silence.

**Le panneau HUD filtre ce qui n'est pas `READY`.**
`MissionDisplayPanelWidget.buildSnapshot` l. 200 : une mission en calcul y est
invisible. La fenêtre de gestion étant **non modale** (`MissionPanelWidget:41`,
« pas de voile, la scène dessous reste vivante »), elle peut rester ouverte
pendant tout le calcul : le HUD n'a pas à être touché.

---

## 2. Ce qui est publié

Un paquet `simulation/mission/progress/`.

```java
public sealed interface MissionProgressEvent {
  record StageEntered(int index, int count) implements MissionProgressEvent {}
  record AttemptStarted(int attempt, int count) implements MissionProgressEvent {}
  record StepStarted(OptimizationStep step) implements MissionProgressEvent {}
  record SizingAdvanced(int pass, int passCount, int load, int loadBudget)
      implements MissionProgressEvent {}
}

public interface MissionProgressListener {
  void onProgress(MissionProgressEvent event);
  void onEvaluation();
}
```

Deux méthodes et non cinq. La voie **chaude** — `onEvaluation()`, appelée depuis
les threads du pool CMA-ES — reste sans allocation : un `incrementAndGet`. La
voie **froide** — quelques dizaines d'appels sur toute une mission — adopte
l'interface scellée, idiome déjà employé par `ClockEvent` et `PlanningState`.
Chaque producteur ne rapporte que ce qu'il sait, sans avoir à recomposer ce que
les autres savent.

Le précédent du dépôt pour un listener nullable traversant la chaîne de calcul
est `StageChainRunner.StageListener`, dont le Javadoc pose déjà la règle : tout
ce qui est mutable vit dans le listener de l'appelant.

### L'état lu par l'UI

```java
public final class MissionProgress implements MissionProgressListener {
  private final AtomicLong evaluations;
  private volatile State state;          // QUEUED | RUNNING
  private volatile ProgressPhase phase;  // scellée : Trajectory | Sizing
  private volatile long startedAtNanos;
}
```

Une instance par calcul, portée en `volatile` par `MissionEntry` — le motif de
thread-safety que la classe documente déjà en tête (l. 30) : champs volatils
écrits par le thread d'optimisation, lus par le thread JME.

`ProgressPhase` est scellée en deux formes :

```java
sealed interface ProgressPhase {
  record Trajectory(int stage, int stageCount, int attempt, int attemptCount,
                    OptimizationStep step) {}
  record Sizing(int pass, int passCount, int load, int loadBudget) {}
}
```

Ce qui **encode dans le type** la décision du §3.2 : en PRECISE il n'existe pas
de champ étage ou tentative à afficher, plutôt qu'un champ laissé vide. Aucun
composant nullable, aucun `Optional` en champ ; `MissionEntry.getProgress()`
renvoie un `Optional`, comme ses sept autres accesseurs.

---

## 3. Le câblage

### 3.1 Qui publie quoi

Chaque maillon gagne un `MissionProgressListener` **nullable**, toujours par
surcharge — nouveau constructeur pour les six premiers, nouvelle surcharge de
`minimize` pour la balayeuse de charge, dont la seule instanciation de production
passe par son constructeur sans argument. Les neuf sites de test qui construisent
aujourd'hui un `MissionOptimizer` ou un `CMAESTrajectoryOptimizer` compilent sans
retouche. Quand le listener est `null`, aucun décorateur n'est même installé : le
chemin FAST/BALANCED est identique à l'octet près à ce qu'il était.

| Classe | Ce qu'elle publie |
|---|---|
| `MissionPlanOptimizer` | rien — elle transporte |
| `FixedLoadPlanner`, `MinimizedLoadPlanner` | rien — elles transportent |
| `MultiStageLoadOptimizer` | `SizingAdvanced` |
| `MissionLoadEvaluator` | transporte vers le `MissionOptimizer` interne, **sans** relayer ses phases trajectoire |
| `MissionOptimizer` | `StageEntered(k, n)`, `n` = nombre d'`OptimizableMissionStage` de la mission |
| `CMAESTrajectoryOptimizer` | `AttemptStarted`, `StepStarted` |
| `CMAESRunExecutor` | `onEvaluation()`, dans la fonction objectif |

`MultiStageLoadOptimizer` n'a **aucune comptabilité à inventer** : il compte déjà
ses évaluations contre son budget de 45 et ses passes contre 3 (l. 232, 240-248).
Le listener se pose sur les incréments existants.

### 3.2 Ce que PRECISE affiche

La progression **s'arrête au niveau charge**. Les niveaux étage et tentative sont
masqués : ils recyclent jusqu'à 135 fois et clignoteraient sans rien apprendre.
Le balayage de charge est en revanche borné et monotone — c'est la seule fraction
honnête de tout le système — et le compteur d'évaluations cumulé garde le signal
de vie.

---

## 4. La lecture par l'UI

**La progression n'entre pas dans le snapshot.** C'est le point qui décide du
reste : pendant un calcul le statut ne bouge pas, `buildSnapshot()` rend la même
liste, et **aucune ligne n'est reconstruite**. Les widgets de progression sont
mutés en place. La reconstruction ne revient qu'à la fin, quand le statut passe à
`READY` ou `FAILED` : le snapshot change une fois, et le spinner disparaît avec
la ligne qui le portait.

Cela demande une rétention que le code n'a pas : `MissionListView:175` crée ses
`MissionRow` et n'en garde que le `getNode()`. Il retiendra les cinq lignes de la
page courante (`PAGE_SIZE = 5`) et exposera un `update(float tpf)` qui les
parcourt ; chaque ligne relit `entry.getProgress()` et rafraîchit son spinner et
son libellé.

### 4.1 Ce qui s'affiche

La colonne de statut fait 130 px (`COL_STATUS`) et porte aujourd'hui
`status.name()` en `ibmPlexMono(11)`. Pendant un calcul : spinner de 16 px +
libellé court — `QUEUED`, `1/2`, ou `LOAD 7/45`. Le plus long tient en ~80 px.

Le pied de page reprend la ligne détaillée de la mission sélectionnée, à la place
du `"Computing..."` actuel :

```
1/2  attempt 1/3  exploration       12 480 evals      00:42
LOAD 7/45  pass 2/3                 41 820 evals      03:12     (PRECISE)
QUEUED                                                          (en file)
```

### 4.2 Deux cadences

Le spinner avance d'un pas de 30° à **10 Hz** — une révolution en 1,2 s, et le
pas égale l'écart entre deux rayons de l'icône (§5).

Le texte est réécrit à **4 Hz** seulement : réécrire un `Label` Lemur remesure
ses glyphes et invalide la mise en page de son conteneur. Deux accumulateurs de
`tpf` portés par le widget, et la parade habituelle du dépôt contre le reflow —
un `setPreferredSize` explicite sur le libellé, comme `MissionRow` le fait déjà
pour chacune de ses cellules — pour qu'un changement de texte ne puisse pas
déplacer les colonnes voisines.

---

## 5. Le spinner

L'icône existe déjà au dépôt : `resources/interface/missions/roster/icon-spinner.png`,
64×64 RGBA. Mesures faites sur le fichier, parce qu'elles décident de
l'implémentation :

| Propriété | Valeur mesurée | Conséquence |
|---|---|---|
| Rayons | **12**, espacés de ~30° | un pas de rotation de 30° est la cadence naturelle |
| Alpha par rayon | 255, 237, 219, 166, 112, puis huit à 76 | comète à 5 rayons, tête en haut, traîne anti-horaire → rotation **horaire** |
| Emprise du dessin | bbox 14→48, rayon ~17 px dans un canevas de 32 | motif **inscrit dans un cercle** : la rotation ne rogne jamais les coins |
| Couleur | RGB **noir pur** (293 pixels visibles), forme portée par l'alpha seule | la teinte est obligatoire, **et elle ne peut pas être multiplicative** — voir §5.2 |

### 5.1 Le widget

`ui/mission/component/SpinnerIcon.java`, à côté de `PaginationBar` dans le paquet
des widgets partagés du domaine mission :

- un `Node` pivot au centre d'une boîte de 16 px, portant une `Geometry` de
  `Quad` décalée de −8 px sur x et y, matériau `Unshaded` texturé, en
  `BlendMode.Alpha`. Le pivot est la raison d'être de la `Geometry` : un fond
  `QuadBackgroundComponent` ne se tourne pas, et la rotation d'un `Container`
  Lemur se ferait autour de son coin. Le précédent d'un enfant attaché à la main
  dans un widget est `ProgressBar` (`root.attachChild(fill)`).
- `setTint(ColorRGBA)` — **indispensable** : la texture est du noir pur dont
  seule l'alpha porte la forme, non teintée elle est invisible sur le panneau.
- Deux valeurs de teinte : `FormStyles.WARNING` en `RUNNING` — la teinte même que
  `PanelFooter.statusColor` donne au texte `COMPUTING`, spinner et libellé disent
  donc la même chose — et `FormStyles.TEXT_LO` en `QUEUED`, pour que l'attente se
  lise comme une attente et non comme un calcul.
- `advance(float tpf)` : accumule, et tous les 0,1 s applique −30° autour de Z.

**Deux valeurs réglées à l'œil**, et le document le dit plutôt que de les
présenter comme dérivées. La **taille** : 24 px de boîte dans la liste, 18 px au
pied de page où la ligne ne fait que 20 px de haut. Elle se règle contre la
texture et non contre la boîte — le dessin est inscrit dans un cercle de rayon
17 px dans un canevas de 32, donc à peine plus de la moitié de la boîte est de
l'encre : à 24 px le spinner visible fait ~13 px, à côté d'un texte de 11 px ; à
16 px il en faisait neuf et passait pour une poussière. Le **calage vertical** :
`SPINNER_LIFT_PX = 5 px` vers le haut. Les métriques de la police n'en demandent
aucun — `ibmplexmono-11` a `lineHeight=15`, `base=12`, et un chiffre est encré de
`yoffset=3` sur 10 px, soit un centre à un demi-pixel de celui de la boîte — mais
toutes les icônes de cette ligne en portent un comparable
(`MissionRow.centerVertically` retranche 6, `RowActionIcons.vCenter` retranche 5,
et le spinner tombe sur la même valeur que la seconde). C'est un réglage, pas une
dérivation, et le constructeur l'expose comme tel.

### 5.2 Correction : la teinte ne peut pas être multiplicative

**Ce document a d'abord annoncé que la teinte passerait par la couleur du
matériau `Unshaded`**, par transposition de ce que `RowActionIcons.tintedFlat`
fait sur les icônes d'action. C'est faux, et la mesure le dit :

| Texture | RGB des pixels visibles |
|---|---|
| `icon-action-compute.png`, `mode-fast-active.png` | **(245, 158, 11)** — ambre, déjà colorées |
| `icon-spinner.png` | **(0, 0, 0)** — noir pur |

`Unshaded` — et donc `QuadBackgroundComponent.setColor`, qui s'appuie dessus —
**multiplie** sa couleur par le texel. Cela teinte une icône claire ; cela laisse
une icône noire noire, quelle que soit la couleur demandée. Les icônes d'action
fonctionnent parce qu'elles portent déjà leur couleur ; le spinner ne le fait
pas.

D'où un quatrième matériau maison, `MatDefs/Ui/IconMask.j3md` (+ `.vert`,
`.frag`) : il jette le RGB de la texture et ne lit que son alpha comme
couverture, la couleur venant de l'uniforme. Une dizaine de lignes de GLSL, dans
la convention d'un dépôt qui compte déjà `WrapLighting`, `Corona` et `Ribbon`, et
réutilisable par toute icône monochrome future. Il est exposé par
`UiKit.iconMaskMaterial(name, tint)`.

**L'alternative écartée** : repeindre `icon-spinner.png` en blanc, ce qui rendrait
la teinte multiplicative opérante et supprimerait le shader. Elle reste ouverte —
elle touche un asset fourni, pas le code.

---

## 6. La file d'attente

`submitForComputation` crée le `MissionProgress` en `QUEUED` et le pose sur
l'entrée **avant** le `submit()` ; la première instruction de la tâche le passe en
`RUNNING` et arme le chrono.

`MissionStatus` **ne gagne pas de valeur `QUEUED`**. Sur les 18 sites qui lisent
le statut dans `ui/` et `states/`, douze testent `== READY` ou `!= READY` — une
mission en file s'y comporterait exactement comme une mission en calcul — et les
trois qui testent `== COMPUTING` (`MissionRow:134`, `PanelFooter:199`,
`MissionPanelWidget:322`) doivent traiter les deux à l'identique : icônes
grisées, pas de ligne de résultat, garde anti-résultat-périmé. Quinze sites
modifiés pour zéro différence de comportement. L'état vit donc dans la
progression, qui est volatile et de forte churn, et non dans le cycle de vie de
la mission.

À la fin — succès comme échec — l'orchestrateur remet `setProgress(null)` **en
dernier**, après le statut et après `lastError`, dont l'ordre de publication est
déjà commenté l. 216-219. L'invariant côté UI est donc : *progression absente
pendant `COMPUTING` est un état légal*, et l'affichage retombe alors sur le
`"Computing..."` d'aujourd'hui.

---

## 7. Tests

Sur le motif de `MissionDisplayPanelRules` et `AppMenuModelTest` : la logique
sort de JME et se teste seule.

| Test | Ce qu'il couvre |
|---|---|
| `MissionProgressTest` | l'assemblage : `StageEntered` + `AttemptStarted` → forme `Trajectory` ; `SizingAdvanced` → bascule en `Sizing` ; compteur monotone sous incrémentation concurrente (N threads × M incréments, total exact) |
| `MissionProgressTextTest` | le formatage, extrait en classe pure `ui/mission/MissionProgressText` à côté de `MissionResultText` : les trois lignes du §4.1, le chrono, la séparation des milliers |
| `SpinnerRotationTest` | la cadence seule — un accumulateur pur `tpf → nombre de pas`, séparé de la `Geometry`, vérifié à 0,05 s / 0,1 s / 0,25 s |

---

## 8. Ce que le chantier ne fait pas

**L'annulation.** Elle n'est pas dans la fiche `UI-2`, et elle demanderait de
retenir le `Future` que `submitForComputation` jette aujourd'hui plus une
coopération du CMA-ES : un drapeau lu dans la fonction objectif, à côté du
`crossRunStop` qui y vit déjà. Le mécanisme existe donc à moitié — c'est un
reliquat identifié, pas une impasse.

**Le HUD.** Le panneau d'affichage garde son filtre `READY` : la fenêtre de
gestion est non modale et peut rester ouverte pendant le calcul. Lever le filtre
imposerait des lignes HUD dont la pastille de couleur, la bascule de visibilité
et le focus télémétrie n'ont aucun sens avant `READY`.

**L'auto-optimisation après création**, question 3 ouverte du §7 de la roadmap,
qui attendait `UI-2` comme préalable. Elle reste à trancher séparément : ce
chantier lui fournit l'indicateur qui lui manquait, il ne la déclenche pas.
