# PHY-2 — Atmosphère par défaut + recalibrage — découpage

Item roadmap : `PHY-2` (★5 ◆4 L), phase 3. Ce document ne conçoit pas en détail :
il **découpe**. Chaque lot y est défini par la propriété qu'il rend vraie, par ce
qu'il consomme, par ce qu'il produit et par le test qui le ferme. Il continue le
chantier atmosphère commencé par [`PHY-1`](02-decoupage.md), dont il consomme le legs
explicite (§8 de ce découpage-là).

> **Statut : découpage arrêté, conception à écrire lot par lot.** Les valeurs
> numériques citées comme cibles de mesure sont des ordres de grandeur à confronter,
> pas des seuils arrêtés. Plusieurs lots ne posent leur nombre — Isp, poids d'apogée,
> modèle d'ascension — qu'**en volant, traînée en main** : le découpage fixe la
> direction et le test, pas la valeur.

---

## 1. Périmètre

**Dans PHY-2** — allumer l'atmosphère par défaut, et payer tout ce que `PHY-1` avait
délibérément différé pour livrer la brique éteinte :

- **la faisabilité de l'optim sous traînée** : une ascension drag-on qui termine en
  pas bornés, une garde de rentrée opérante sous traînée, un modèle valide à 0 km ;
- **le câblage du modèle** que `J2` a tranché ([`dette-technique.md`](../dette-technique.md)
  `DT-14`) : Harris-Priester à l'optim, modèle de la mission au runtime, substitution
  active uniquement sous traînée ;
- **le recalibrage catalogue de l'Isp** (`DT-13`) : les deux proxys « moyenne de
  trajectoire » ramenés au lapse sol/vide, la traînée devenant explicite ;
- **le recalibrage de l'ascension** (`DT-21`, `DT-20`) : l'optimiseur reprend la main
  sur la coupure du cœur, ce qui **répare la régression** du Falcon Heavy en transfert
  optimisé ([`../bugs.md` BUG-25](../bugs.md#bug-25--falcon-heavy-ne-vole-plus-en-transfert-optimisé-depuis-phy-8)) ;
- **le dimensionnement en deux passes** (`DT-19`) : la réserve d'insertion universelle
  remplacée par le ΔV que chaque mission demande réellement ;
- **la re-vérification du Cd du S2** (`DT-15`) sur l'ascension reprovisionnée ;
- **la bascule du défaut** : `periapsisFloor` relevé, pertes absorbées dans
  `dt1MaxPhysical`, défaut `NONE → NRLMSISE`, suite complète re-baselinée ;
- **la restauration d'un scénario dont l'atmosphère n'est pas `NONE`**
  ([`reliquats.md` REL-22](../reliquats.md)), incorrigible avant que ce chantier existe.

**Hors PHY-2**, et à ne pas y laisser glisser :

- `MaxQDetector`, `AtmosphericInterfaceDetector`, télémétrie Q et traînée instantanée,
  sélecteur Off / Statique / Réaliste au wizard — c'est `PHY-3`. PHY-2 rend le modèle
  *vivant* ; il n'expose rien de neuf à l'écran ;
- une **Isp dépendante de la pression** : `PropulsionSystem` est un `record (isp, thrust)`
  figé ; un modèle de poussée par contre-pression est un item à part (`DT-13` le dit) ;
- une **table de Cd fonction du Mach** : corriger le pic transsonique absent (§3.7)
  demande cette table, et c'est un chantier propre ;
- atmosphère non terrestre, portance, échauffement, ablation, pression de radiation.

**La contrainte que PHY-2 lève, et qui date de PHY-1.** `PHY-1` tenait
`AtmosphereModel.NONE ⇒ propagation identique au bit près`. C'est cette contrainte
que PHY-2 retire — délibérément, et pas d'un coup : le socle `L1` la tient encore, les
lots de physique l'éteignent un par un (§3.6). C'est l'échéance que v1 avait achetée ;
elle arrive.

---

## 2. État des lieux

### 2.1 La fiche roadmap de `PHY-2` est périmée, et de beaucoup

La fiche [`roadmap/02-roadmap-v2.md`](../roadmap/02-roadmap-v2.md) `PHY-2` liste cinq
gestes : relever `periapsisFloor`, absorber dans `dt1MaxPhysical`, re-baseliner la suite,
basculer le défaut, et la répartition « Harris-Priester pour l'optimisation,
NRLMSISE-00 pour le runtime ». C'est **environ le tiers** du périmètre réel.

Tout le reste a été versé à `PHY-2` par le §3 de la roadmap et par les fiches de dette,
**sans jamais remonter dans la fiche** : la séance `J2` (`DT-13`/`DT-14`/`DT-15`), les
trois fiches d'ascension (`DT-19`/`DT-20`/`DT-21`), la régression `BUG-25`, et `REL-22`.
Ce découpage est l'endroit où ces apports se réconcilient en un seul périmètre.

Une affirmation de la fiche est même **fausse au niveau du câblage**, corrigée en `J2` :
l'atmosphère n'est pas choisie par type de propagateur. Optim et runtime lisent tous
deux `context.drag().model()` (`OrekitService.addDrag`) — le legs 8×8 / 50×50 sur lequel
la répartition s'appuyait a disparu. La substitution NRLMSISE→HP à l'optim est donc un
**câblage neuf**, dû à ce chantier (`DT-14`).

### 2.2 Ce que `PHY-1` lègue, et qui est déjà chiffré

Le §8 de [`02-decoupage.md`](02-decoupage.md) est le cahier des charges d'entrée. Quatre
entrées **déjà mesurées** : le surcoût compute réel, l'écart entre les deux modèles
(22,6 % de densité), la dette des Isp proxy, et le nombre de pas d'intégration à basse
altitude. Plus **deux contraintes dures**, découvertes par la mesure en `L0` de PHY-1,
qui ne sont pas des entrées à consulter mais des obstacles à lever avant de basculer le
défaut :

- **une borne d'altitude est nécessaire à la terminaison de l'optim.** Au-dessus de
  200 km la traînée ne coûte aucun pas ; à 130 km la même propagation demande
  **982 497 pas et 487 s** au lieu de 452 pas et 0,2 s, puis échoue
  ([`03-baseline-L0.md`](03-baseline-L0.md) §2.3). Un seul candidat CMA-ES qui pique bas
  suffit à rendre une évaluation 2 000 fois plus longue ;
- **`ReentryGuard` est inopérant avec traînée** ([`../bugs.md` BUG-10](../bugs.md#bug-10--reentryguard-inopérant-en-présence-de-traînée)).
  Son plancher est à −50 km et l'intégrateur cède **au-dessus**, à −9/−30 km : le garde
  ne se déclenche jamais dans le seul régime où une rentrée réaliste est en jeu.

### 2.3 Ce que la mesure de PHY-1 a déjà établi sur le modèle

- **Harris-Priester est valide sur `[100 km, 1000 km]`** : il **lève une exception**
  en dessous de 100 km et rend `0,0` en silence au-dessus de 1000 km
  ([`03-baseline-L0.md`](03-baseline-L0.md) §2.1). **NRLMSISE-00 couvre 0 → 1500 km.**
- **Le vol drag-on d'une ascension à l'optim est infaisable aujourd'hui**
  ([`05-conception-L2.md`](05-conception-L2.md) §4.2) : casse à 3 min 15, S2 allumé à
  58 km sur une ascension qui n'atteint pas l'orbite.
- **Coût par propagation** : HP ×1,35–1,92, NRLMSISE ×3,73–4,03 contre le propagateur
  nu.

Ces trois faits, ensemble, disent que la première chose que `PHY-2` doit rendre vraie
n'est pas un recalibrage : c'est qu'une ascension drag-on **s'optimise et termine**.
Sans cela, aucun des recalibrages promis « traînée en main » n'a de socle.

---

## 3. Décisions de conception

Sept décisions, prises avant le découpage parce qu'elles le déterminent.

### 3.1 Le socle de faisabilité ouvre le chantier

Les recalibrages de `DT-13`, `DT-21`, `DT-19` promettent tous un réglage « traînée en
main ». Or §2.3 montre qu'une ascension drag-on ne s'optimise pas encore. Le premier lot
de production (`L1`) n'est donc **pas** un lot de physique : c'est un **socle** qui rend
une ascension drag-on terminable, et qui ne bascule aucun défaut. C'est la forme du `L1`
de PHY-1 — la brique posée, éteinte — appliquée à la faisabilité plutôt qu'à la brique.

### 3.2 Terminaison de l'optim et garde de rentrée sont le même livrable

Les deux obstacles durs de §2.2 sont un seul mécanisme vu à deux endroits : l'un veut
arrêter une propagation d'optim avant l'explosion de pas, l'autre veut arrêter une
descente avant que l'intégrateur ne cède. La réponse est **un arrêt d'altitude
drag-conditionnel** :

- un détecteur qui **stoppe la propagation** au-dessus de la profondeur mesurée de
  cession de l'intégrateur, alimentant une **pénalité de coût** ;
- **une pénalité, pas une borne déplacée** — déplacer une borne renormalise la recherche
  CMA-ES et perturbe tous les profils ; une pénalité de coût laisse la recherche
  intacte et se contente de rendre chère la région à éviter ;
- **armé uniquement si `hasDrag()`** — drag-off ne le voit jamais, donc le « au bit
  près » reste vrai par construction, exactement le patron du `DragForce` conditionnel
  de PHY-1.

Il **remplace** `SUBSURFACE_FLOOR` dans le régime drag (un mécanisme distinct, plus haut,
et non un décalage de sa valeur — ce que `BUG-10` réclamait sans le chiffrer). `L1`
mesure la profondeur de cession, par modèle, pour placer l'arrêt au-dessus.

### 3.3 Le modèle d'ascension se tranche par mesure, et `DT-14` sera amendé

`J2` a tranché « **optim toujours Harris-Priester** » (`DT-14`). Mais HP lève sous
100 km et toute ascension part de 0 km : HP **ne peut pas voler l'ascension**. Le vol
drag-on d'une ascension se fait donc d'abord sous **NRLMSISE**, seul modèle qui atteint
le pas de tir. Trois règles candidates pour l'optim, que `L1` **départage par le coût
compute mesuré** :

| | Règle à l'optim, sous traînée | Verdict |
|---|---|---|
| **a** | NRLMSISE partout | correct, valide 0 km ; coûteux (×3,73–4,03 par propagation, sur une optim déjà lente) |
| **b** | HP planché sous 100 km | **écartée d'avance** : gèle ρ à travers l'atmosphère dense 0–100 km, là où la traînée d'ascension est maximale — fausse la crédibilité même que vise le chantier |
| **c** | composite **par problème** : NRLMSISE le gravity-turn, HP le transfert | valide partout, garde la raison-coût de `DT-14` là où elle s'applique (orbital ≥ 100 km) ; deux modèles, un raccord à 100 km |

La mesure qui décide est le surcoût compute d'une ascension optimisée sous NRLMSISE :
s'il tient dans le +50 % annoncé, **a** gagne (simple, correct) ; sinon **c**. Dans les
deux cas, « optim toujours HP » est **amendé** — HP ne vole pas l'ascension — et le
câblage `DT-14` s'écrit sur la règle retenue. L'amendement est consigné en clôture de
`L1`.

### 3.4 L'autorité sur le cœur élargit `transitionTime`, sans variable neuve

Aujourd'hui `transitionTime` ne dimensionne que `burn2` (étage supérieur) ; `burn1` et
le cœur courent à extinction, alors que le cœur est déclaré `ShutdownMode.COMMANDED`
([`../dette-technique.md` DT-20](../dette-technique.md#dt-20--lascension-na-aucune-prise-sur-son-corps-central)).
La mesure de `DT-20` porte sa propre solution : `transitionTime = 170` (sous le
`stagingCompleteTime = 181,8`) rend un apogée de **407,9 km**, la cible. La prise existe
donc **déjà dans la variable** ; il suffit d'élargir son domaine vers le bas :

1. **retirer la barrière** `STAGING_PENALTY_BASE = 1e3` qui interdit
   `transitionTime < stagingCompleteTime` (coût nominal 73,76, la barrière la rend
   inatteignable) ;
2. **câbler la coupure du cœur sur `transitionTime`** dans cette région ;
3. **rééquilibrer `W_APOGEE_OVERSHOOT`** pour que l'optimiseur *choisisse* de couper.

Le problème reste **à deux variables** — `transitionTime` gagne seulement un domaine
sous `stagingCompleteTime`. C'est plus propre qu'une troisième dimension, qui
renormaliserait la recherche sur tous les profils. Les trois gestes vont **ensemble** :
la clôture PHY-8 a mesuré que cœur commandable sans barrière retirée ne change rien, et
barrière retirée sans rééquilibrage n'insère pas (`72 551 × 400 178 m`, étage supérieur
vide).

### 3.5 Le dimensionnement passe en deux passes, le vol de mesure reste nominal

La réserve additive de 1 300 m/s sur `sizeTopStage` est remplacée par un dimensionnement
en deux passes : dimensionner (chaîne de ΔV idéal) → **voler** pour lire le ΔV
d'insertion réel → redimensionner. Deux garde-fous :

- **le vol de mesure est nominal, pas optimisé** — sinon chaque dimensionnement lance une
  CMA-ES et le coût explose ;
- **deux passes, résidu mesuré** — `DT-19` affirme que deux passes donnent le nombre
  « exactement » ; comme le redimensionnement change la masse, donc l'ascension, `L4`
  **mesure le résidu** après la passe 2 et n'itère que s'il le faut.

Un `max(raw, plancher)` a été mesuré et rejeté en amont : il clampe polaire et plein-est
sur un nombre identique — l'erreur que `MIS-7` chiffre à 529 m/s.

### 3.6 L'invariant « au bit près » est retiré à partir de `L2`, volontairement

Après `L2`/`L3`/`L4`, un `NONE` ne rend **plus** la trajectoire d'avant `PHY-2` :
l'Isp, les poids et le dimensionnement ont changé. Le « drag off ⇒ identique au bit »
était une propriété de **PHY-1 et du socle `L1`** ; les lots de physique l'éteignent, un
motif à la fois. L'opt-out `NONE` reste possible et vole sans traînée, mais ce n'est pas
un retour à l'état ante — c'est correct, et écrit ici pour qu'on ne le lise pas comme une
régression au moment des re-baselines.

### 3.7 Les trois approximations du catalogue sont assumées, pas corrigées

`PHY-1` a fait entrer trois approximations au catalogue, léguées « à corriger ou
assumer » ([`02-decoupage.md`](02-decoupage.md) §8). `PHY-2` les **assume et les
documente**, n'en corrige aucune :

- **pic transsonique absent** (un Cd unique par étage ne représente pas Mach 1) — le
  corriger demande une table Mach, hors périmètre (§1) ;
- **trois géométries de bus conventionnelles** pour les charges utiles, sans matériel
  publié derrière — rien à mesurer ;
- **Cd libre-moléculaire du S2 en régime continu** — c'est `DT-15`, re-vérifié en `L3`
  sur l'ascension reprovisionnée, et escaladé en Cd par régime **seulement si**
  nécessaire.

---

## 4. Principe du découpage

Trois règles, reprises de `PHY-1` et `PHY-4`, avec **une différence assumée** sur la
troisième :

1. **Un changement de comportement à la fois.** Chaque lot de physique re-baseline pour
   **une seule cause** — Isp, puis autorité d'ascension, puis dimensionnement, puis
   défaut. Un dérapage constaté est alors attribuable à un lot et un seul.
2. **Chaque lot se ferme sur un test exécutable**, pas sur une revue. Pour le socle,
   c'est une **égalité** (les gates, drag-off inchangé) ; pour les lots de physique,
   c'est une **mesure** confrontée à une valeur connue par ailleurs, plus le
   re-enregistrement des baselines.
3. **Le défaut bascule à la fin, et l'opt-in n'est pas préservé jusqu'au bout.** C'est
   la différence avec `PHY-1`, qui restait opt-in à la dernière ligne. Ici, comme au
   dernier lot de `PHY-4`, le chantier allume ; et dès `L2` l'invariant « au bit près »
   est éteint (§3.6). Seuls `L0` et `L1` ne touchent aucune trajectoire drag-off.

---

## 5. Les lots

| Lot | Objet | Change le drag-off ? | Test qui le ferme |
|---|---|---|---|
| **L0** | Baseline consolidée | non (aucun code de prod) | un document |
| **L1** | Socle de faisabilité + câblage | **non** (structurel, `hasDrag()`-conditionnel) | 4 gates + ascension drag-on terminante + candidat bas abandonné |
| **L2** | Isp recalibrée (`DT-13`) | oui — attribuable à l'Isp | mesure : dette explicitée, profils re-baselinés |
| **L3** | Autorité d'ascension (`DT-21`+`DT-20`) = fix `BUG-25` | oui — attribuable à l'arbitrage | `testFalconHeavyOptimizedTransfer` ré-activé + gates re-baselinés + airstart S2 (`DT-15`) |
| **L4** | Dimensionnement deux passes (`DT-19`) | oui — attribuable au dimensionnement | mesure : réserve retirée, résidu passe 2 |
| **L5** | Bascule du défaut + `REL-22` | oui — le moment « on » | défaut basculé, suite complète re-baselinée drag-on, restauration opérante |

### L0 — Baseline consolidée

**Propriété rendue vraie.** Les entrées de `PHY-2` sont réunies et chiffrées en un seul
endroit, avant tout changement.

**Entrées.** La suite verte au commit de départ, et le §8 de [`02-decoupage.md`](02-decoupage.md).

**Sorties.** Un document consolidant les quatre entrées mesurées de PHY-1 (§2.2), les
deux contraintes dures, l'état des profils au départ, et la procédure d'exécution qui
donne un vert fiable malgré `BUG-7` (gates isolés, `cleanTest` systématique). Aucun code
de production.

**Fermeture.** Le document.

### L1 — Socle de faisabilité + câblage

**Propriété rendue vraie.** Une ascension drag-on *termine* en pas bornés, la règle de
modèle à l'optim est tranchée par mesure, et le défaut reste `NONE` — rien n'a bougé d'un
bit côté drag-off.

**Entrées.** `L0`, et la brique de traînée déjà livrée par PHY-1 (`DragForce`
conditionnel dans les deux factories, choix porté par `MissionSpec`).

**Sorties.**

- **l'arrêt d'altitude drag-conditionnel** (§3.2) — détecteur d'arrêt + pénalité de
  coût, armé uniquement si `hasDrag()`, placé au-dessus de la profondeur mesurée de
  cession ; **ferme `BUG-10`** ;
- **la règle de modèle à l'optim** (§3.3), tranchée par mesure, et le **câblage `DT-14`**
  écrit dessus (substitution NRLMSISE→HP active uniquement sous traînée) ;
- les **mesures consignées** : surcoût de l'ascension sous NRLMSISE, profondeur de
  cession par modèle, règle retenue et **amendement de `DT-14`**.

**Fermeture — trois preuves.**

1. **Les quatre gates verts** à tolérance actuelle, `0.0` comprise — drag-off intact,
   l'arrêt et le câblage étant `hasDrag()`-conditionnels.
2. **Une ascension drag-on qui termine** en pas bornés sur un profil, sous le modèle
   retenu.
3. **Un candidat qui pique bas abandonné à coût faible** — l'arrêt tire au-dessus de la
   cession, pas bornés (non 982 k), pénalité assignée.

### L2 — Isp recalibrée (`DT-13`)

**Propriété rendue vraie.** L'Isp catalogue des deux étages qui la portent encore — bloc
bas Falcon Heavy (296 s) et Vulcain (360 s) — ne représente plus que le lapse sol/vide ;
la traînée porte le reste, explicitement.

**Entrées.** `L1` (le vol drag-on rend le réglage possible), et la direction tranchée en
`J2` (proxy conservé, re-calibré « lapse seule »).

**Sorties.** Les deux Isp re-posées **en volant**, traînée en main : l'ancien proxy
sur-comptait de **396 m/s** (Falcon Heavy) et **64 m/s** (Vulcain/Ariane 64), à retirer
maintenant que la traînée est explicite. `PropulsionSystem(isp, thrust)` reste figé — une
Isp par pression serait un moteur neuf, hors périmètre.

**Fermeture.** Mesure : dette d'Isp rendue explicite, profils re-baselinés, décalage
**attribuable à l'Isp seule**.

**Pourquoi avant `L3`.** Recalibrer les poids d'ascension sur une Isp encore en
double-comptage, puis corriger l'Isp, obligerait à tout re-régler.

### L3 — Autorité d'ascension (`DT-21` + `DT-20`) = fix `BUG-25`

**Propriété rendue vraie.** L'optimiseur commande la coupure du cœur ; le Falcon Heavy
vole 400 circulaire en transfert optimisé.

**Entrées.** `L2` (les poids se calibrent contre la vraie Isp), et le socle `L1`.

**Sorties.**

- la **barrière** `STAGING_PENALTY_BASE` retirée, le **domaine de `transitionTime`**
  élargi sous `stagingCompleteTime`, la **coupure du cœur** câblée dessus (§3.4) ;
- **`W_APOGEE_OVERSHOOT` rééquilibré**, posé **par mesure** : 0,5 est hors domaine (à
  4 596 km), 3,0 a acheté une remise 148 km sous la cible — la valeur juste est entre les
  deux, trouvée en volant ;
- **`DT-15`** : l'altitude d'airstart du S2 re-mesurée sur l'ascension reprovisionnée ;
  escalade vers un Cd par régime **seulement si** l'airstart y reste en continu.

**Fermeture.** **`testFalconHeavyOptimizedTransfer` ré-activé** (le `@Disabled` levé,
400 ±7 %) + gates re-baselinés + airstart S2 consigné.

### L4 — Dimensionnement deux passes (`DT-19`)

**Propriété rendue vraie.** L'étage supérieur porte le ΔV que *cette* mission demande,
non la réserve additive universelle de 1 300 m/s.

**Entrées.** `L3` — le cœur devenu commandable change ce que l'étage supérieur porte
(il s'allume désormais), donc le dimensionnement se fait contre l'ascension corrigée.

**Sorties.** `sizeTopStage` en deux passes (§3.5), le vol de mesure nominal, le résidu
après passe 2 mesuré.

**Fermeture.** Mesure : réserve retirée, sur-provisionnement (+18–66 % de charge d'étage
supérieur) supprimé, profils re-baselinés, décalage **attribuable au dimensionnement
seul**.

### L5 — Bascule du défaut + `REL-22`

**Propriété rendue vraie.** La traînée est on par défaut ; une mission vole sous
atmosphère sauf opt-out explicite ; un scénario dont l'atmosphère n'est pas `NONE` se
restaure.

**Entrées.** `L4` — le dimensionnement final avant de basculer.

**Sorties.**

- `periapsisFloor` relevé (100 km n'a plus de sens, un périgée bas décroît), aligné sur
  l'arrêt d'altitude de `L1` ;
- pertes absorbées dans `dt1MaxPhysical` (l'enveloppe du premier burn s'élargit sous
  traînée) ;
- défaut `MissionSpec` basculé `NONE → NRLMSISE` (le palier « Réaliste » de `DT-14`),
  l'opt-out `NONE` restant possible ;
- suite complète re-baselinée drag-on ;
- **`REL-22`** : le chemin de restauration débloqué — le champ existe déjà sur
  `MissionSpec` depuis PHY-1, rien à inventer.

**Fermeture.** Défaut basculé, suite complète re-baselinée drag-on, restauration
`REL-22` opérante.

---

## 6. Ce qui reste à trancher au raffinement

Ce que chaque lot tranche **par mesure**, et qui n'est donc pas figé ici :

1. **Le modèle d'ascension à l'optim** — `a` (NRLMSISE partout) ou `c` (composite par
   problème), selon le surcoût compute mesuré en `L1`. `b` est écartée d'avance (§3.3).
2. **Les deux valeurs d'Isp** — posées en volant en `L2`, contre la perte totale
   d'ascension connue.
3. **`W_APOGEE_OVERSHOOT`** — posé en volant en `L3`, entre 0,5 (hors domaine) et 3,0
   (sur-enchère mesurée).
4. **La convergence du dimensionnement** — deux passes, ou itération si le résidu mesuré
   en `L4` ne se referme pas.

---

## 7. Ordonnancement et risques

`L0 → L1 → L2 → L3 → L4 → L5`, strictement séquentiel : `L2` a besoin du vol drag-on de
`L1` ; `L3` des poids calibrés contre la vraie Isp de `L2` ; `L4` du cœur commandable de
`L3` ; `L5` du dimensionnement final de `L4`.

**Le risque majeur est le coût compute de l'optim drag-on**, et c'est celui que PHY-1 §8
a nommé sans le lever. Si l'ascension optimisée sous NRLMSISE dépasse le +50 % annoncé,
toute la boucle CMA-ES de `PHY-2` est en cause. `L1` le **mesure sur du jetable** avant
que le chantier s'y engage — l'apprendre sur quarante lignes plutôt que sur une
optimisation.

**Le rééquilibrage du poids (`L3`) est délicat** : 3,0 a surenchéri 148 km sous la cible,
0,5 est hors domaine. La calibration peut demander plusieurs passes, et elle touche tous
les profils du dépôt.

**`BUG-7` est le risque transverse.** Les gates 0.0 tombent au dernier bit quand un test
lunaire les précède dans le même JVM. Les quatre re-baselines (`L2`–`L5`) passent par
`cleanTest` et exécution isolée des gates, faute de quoi chaque lot débattra d'un rouge
qui n'est pas le sien. Piège associé : relancer le même filtre `--tests` après un succès
rend la tâche `UP-TO-DATE` et n'exécute rien ; toute mesure passe par `cleanTest`.

**Contrainte de méthode.** Les tests d'optimisation et de mission sont lents et c'est
l'utilisateur qui les lance.

---

## 8. Ce que PHY-2 lègue

**À `PHY-3`** — un modèle désormais *vivant* : le sélecteur Off / Statique / Réaliste se
branche sur le champ `AtmosphereModel` de `MissionSpec` déjà en place ; `MaxQDetector` et
la télémétrie lisent l'atmosphère montée ; `AtmosphericInterfaceDetector` **réutilise
l'arrêt d'altitude** livré par `L1`.

**À `MIS-10`** — la rentrée contrôlée : l'arrêt d'altitude de `L1` *est* le mécanisme de
terminaison qu'elle réclame, et l'ascension reprovisionnée lui donne un lanceur crédible.

**À `PHY-5`** — la traînée fait décroître un étage largué et retomber un booster
suborbital ; sans elle, une séparation ne montrerait pas deux objets qui s'écartent.

**Dette éteinte** — `DT-13`, `DT-14`, `DT-15` (calibrés), `DT-19`, `DT-20`, `DT-21`
(recalibrage), `BUG-10` (garde de rentrée), `BUG-25` (régression), `REL-22`
(restauration).

**Dette assumée, consignée** — les trois approximations du catalogue (§3.7), non
corrigées ; `DT-14` **amendé** (HP ne vole pas l'ascension) ; et, si un besoin futur
l'exige, une Isp dépendante de la pression et une table de Cd par Mach, tenues hors
périmètre.

---

*Document rédigé le 2026-09-10, après la séance de découpage en conversation.*
