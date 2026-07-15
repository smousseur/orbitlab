# Document conceptuel — Le lanceur génère le profil de vol

> **Périmètre** : document **conceptuel amont**. Il pose le modèle mental
> « le lanceur génère le profil de vol », d'où découlent jettisons, phases
> optimisables et dimensionnement du carburant. L'implémentation détaillée (records,
> refactors, roadmap par lots) reste dans `04-mission-realism.md`, auquel ce doc renvoie.
> **Hypothèse** : atmosphère négligée (cf. 04).

---

## 1. Thèse

> **Le profil de vol n'est pas une propriété de la mission ; c'est une conséquence du
> couplage entre l'objectif et l'architecture du lanceur.**

Aujourd'hui, l'inverse est codé : la `Mission` possède la séquence de phases
(`LEOMission.buildStages()` retourne une `List<MissionStage>` figée) et le lanceur est
un acteur passif, déduit *a posteriori* de la masse courante
(`VehicleStack.resolveActiveStage(currentMass)`). Il n'existe **aucun lien explicite**
« cet étage assure cette phase ». Les conséquences :

- Le même profil et le même lanceur (réservoirs pleins) servent LEO et GEO.
- Les jettisons sont des effets de bord de la masse, pas des décisions de profil.
- On ne peut pas répondre à la question d'ingénierie de base : *« pour mettre X kg sur
  l'orbite Y avec le lanceur Z, quelle est la séquence de phases, qui fait quoi, et
  combien de carburant faut-il embarquer par étage ? »*.

Ce document propose de **renverser la dépendance** : à partir d'un **objectif** et d'un
**lanceur décrit par les capacités de ses étages**, on **dérive** le profil (phases,
affectation étage→phase, jettisons, paramètres optimisables), puis on en **déduit** le
carburant juste nécessaire.

```
            (Objectif)            (Lanceur = étages + capacités)
                 \                        /
                  \                      /
                   v                    v
            ┌──────────────────────────────────┐
            │   Dérivation du profil de vol     │
            │  phases · attitude · terminaison  │
            │  affectation étage→phase          │
            │  jettisons · params optimisables  │
            └──────────────────────────────────┘
                            │
                            v
              Budget ΔV  →  Carburant par étage
              (dimensionnement « juste nécessaire »)
```

---

## 2. Modèle conceptuel

### 2.1 L'objectif

L'objectif décrit *ce qu'on veut atteindre*, indépendamment du moyen :

- Orbite cible : périgée / apogée / inclinaison (déjà `OrbitInsertionObjective`).
- **Masse de charge utile** (`payload mass`) — aujourd'hui implicite (spacecraft 150 kg).
  À promouvoir en entrée de premier ordre : c'est elle qui ancre le calcul de carburant
  (raisonnement top-down depuis la charge utile).
- Éventuelles contraintes opérationnelles (tolérance d'orbite, marge de propergol réservée).

### 2.2 Le lanceur = une pile d'étages **+ leurs capacités physiques**

Le point central : un étage n'est pas qu'un triplet (masse sèche, capacité propergol,
propulsion). Sa **nature physique** contraint ce qu'il *peut* faire dans un profil. On
propose d'enrichir conceptuellement le modèle d'étage avec un **descripteur de capacités** :

| Capacité | Valeurs | Ce qu'elle impose au profil |
|---|---|---|
| Allumage | sol / vol | Un étage allumé au sol = phase initiale ; allumé en vol = étage supérieur |
| Rallumable | oui (n) / non | Conditionne coast + rallumage (parking → GTO, circularisation séparée) |
| Throttleable / coupable | oui / non | Coupure commandée (`DateDetector`) possible, ou non |
| Mode d'extinction | coupure commandée / **à épuisement** | Solides = à épuisement → terminaison sur `MassDepletionDetector` |
| Ergols | solide / liquide stockable / **cryogénique** | Cryo = **durée de coast limitée** → ne peut pas porter un coast GTO de ~5 h |
| Rôle | booster / sustainer / supérieur / **intégré charge utile (AKM)** | Oriente l'affectation phase et le point de séparation |
| Charge variable | oui / non | Solides : `propellantLoad = capacity` figé ; liquides : charge optimisable |

> Ces capacités sont **dérivables** des chiffres déjà présents pour partie, mais surtout
> elles formalisent ce qui est aujourd'hui implicite. Elles sont l'**entrée du dérivateur
> de profil**.

### 2.3 La phase comme unité de premier ordre

Une **phase** (≈ `MissionStage` actuel) est caractérisée par :

- une **loi d'attitude** (zénith, programme de pitch « gravity turn », pointage TNW) ;
- une **condition de terminaison** (date, **épuisement**, nœud, apside) ;
- un **étage propriétaire** (qui fournit la poussée) — aujourd'hui *implicite* ;
- un ensemble (possiblement vide) de **paramètres optimisables** + l'optimiseur associé ;
- un éventuel **événement de séparation** en sortie (jettison de l'étage propriétaire).

Taxonomie minimale (déjà présente en pièces détachées) :

| Phase | Attitude | Terminaison typique | Optimisable ? |
|---|---|---|---|
| Ascension atmosphérique (verticale + gravity turn) | zénith puis pitch program | épuisement étage / date | **oui** (profil de pitch) |
| Insertion orbite de parking | TNW | apside / cible | oui (timing, durée, angles) |
| Coast balistique | inertielle | nœud / apside / date | non (mais *durée* peut être paramètre) |
| Burn de transfert (Hohmann) | TNW | épuisement / durée | **oui** |
| Circularisation / trim | TNW | apside / épuisement | oui |
| Séparation (événement) | — | instantané | non |

### 2.4 La dérivation : (objectif + lanceur) → profil

Le cœur conceptuel. Étant donné l'objectif et la pile d'étages capacités-typées, on
**assemble** le profil par un raisonnement déterministe (pas encore optimisé) :

1. **Budget ΔV cible** (cf. §3) : combien de ΔV pour aller de l'aire de lancement à
   l'orbite, décomposé en grands postes (ascension + pertes, insertion, transfert, plan,
   circularisation).
2. **Affectation étage→postes de ΔV**, en respectant les capacités :
    - Le(s) booster(s) **à épuisement** prennent le ΔV initial **jusqu'à flame-out**, puis
      sont **jettisonnés sur `MassDepletionDetector`** (charge figée = capacité).
    - L'étage sustainer/principal (liquide, coupable) prend l'ascension/gravity turn
      jusqu'à MECO ; terminaison par **épuisement** ou coupure commandée selon la capacité.
    - L'étage supérieur **rallumable** peut enchaîner insertion → coast → transfert ; un
      étage supérieur **cryogénique** dont la durée de coast est dépassée par le coast
      requis **ne peut pas** porter la circularisation lointaine → ce poste **bascule sur
      le spacecraft** (AKM).
3. **Insertion des phases de service** : coast inter-étage court après chaque séparation,
   coast d'alignement (nœud/apside) là où la géométrie l'exige.
4. **Marquage des phases optimisables** et de leur condition de terminaison (date vs
   épuisement), selon la capacité de l'étage propriétaire.

Le profil n'est donc plus écrit à la main : il est le **produit** de la pile d'étages et
de l'objectif. Trois exemples contrastés montrent que **c'est bien le lanceur qui décide
qui fait quoi** :

**A. Bi-étage kérolox (type Falcon), upper rallumable**
```
[Étage 1 liquide]  Ascension + gravity turn  → MECO (épuisement) → JETTISON
[Étage 2 liquide]  Insertion → (coast) → transfert → circularisation → JETTISON
[Spacecraft]       Coast final (RCS)
```
L'upper rallumable absorbe tout le ΔV orbital ; le spacecraft n'a pas de propulsion utile.

**B. Boosters solides + corps cryo + upper cryo (type Ariane 5)**
```
[2× EAP solides]   Co-ascension, NON éteignables → épuisement → JETTISON (à mi-burn corps)
[EPC Vulcain cryo] Ascension/gravity turn (allumé au sol) → MECO → JETTISON
[ESC-A cryo]       Injection GTO ; coast limité → NE circularise PAS → JETTISON
[Spacecraft + AKM] Coast jusqu'à l'apogée GTO (~5 h) → circularisation + plan (AKM)
```
Ici l'architecture **force** : charge des solides figée, jettison sur épuisement, et
**circularisation déléguée au spacecraft** parce que le cryo ne survit pas au coast.
Le *même objectif GEO* produit un *profil différent* selon le lanceur.

**C. Variante B sans AKM mais avec étage d'apogée stockable**
La circularisation revient à un étage dédié rallumable storable → encore un autre
découpage. → *L'objectif est identique ; seul le lanceur change le profil.*

> **Conséquence de conception** : la résolution implicite par masse
> (`resolveActiveStage`) reste un excellent **mécanisme d'exécution** des jettisons, mais
> le **quel-étage-fait-quoi doit devenir explicite et dérivé** en amont, pas un effet de
> bord de la masse courante.

---

## 3. Le budget ΔV : le pont objectif ↔ lanceur ↔ carburant

C'est le maillon qui relie les trois inconnues du problème.

1. **Objectif → ΔV idéal** (analytique) : ΔV d'ascension (incl. estimation des pertes
   gravitationnelles/steering), ΔV de transfert (vis-viva / Hohmann avec compensation
   J2 — déjà calculé dans `AnalyticHohmannTransferStage`), ΔV de plan, ΔV de
   circularisation. Outils existants : `Physics`, `CircularizationBurnResolver`.
2. **Profil → répartition du ΔV par étage** : l'affectation §2.4 dit *quel étage paye
   quel poste*. Les **rapports de masse** (staging) du lanceur déterminent comment ce ΔV
   se distribue.
3. **Tsiolkovsky inverse, top-down depuis la charge utile** : pour chaque étage, en
   partant de la masse finale (charge utile + étages supérieurs secs),
   `m_prop = m_final · (exp(ΔV_étage / (Isp·g0)) − 1)`. C'est le **carburant juste
   nécessaire** de l'étage. (Inverse exact du `Physics.computeBurnDuration` déjà utilisé.)
4. **Marges** : pertes finite-burn, gravité/steering, réserve opérationnelle (1–2 %).
   La somme `dry + m_prop` doit tenir sous `propellantCapacity` (sinon mission infaisable
   avec ce lanceur → signal clair à l'utilisateur).

Ce calcul fournit le **seed analytique** du `propellantLoad` par étage. Pour les étages à
charge figée (solides), l'étape 3 ne s'applique pas (`load = capacity`), et leur ΔV est
une *donnée d'entrée*, pas un degré de liberté.

---

## 4. Stratégie d'optimisation — quelle brique pour quoi

On distingue trois préoccupations **hiérarchisées par échelle de temps de calcul**.
Les fusionner dans un seul vecteur CMA-ES est tentant mais **déconseillé** : les variables
ont des échelles et des sensibilités très hétérogènes (un kg de propergol vs un degré de
pitch), ce qui dégrade la covariance adaptative de CMA-ES. On garde donc une **hiérarchie**.

### 4.1 Niveau interne — forme de trajectoire (CMA-ES existant, généralisé)

- Brique : `OptimizableMissionStage<T>` + `TrajectoryProblem` + `CMAESTrajectoryOptimizer`
  (avec ses retries/backups déjà en place).
- Aujourd'hui seul le gravity turn (2D) est optimisé ; les transferts sont analytiques.
  Le `TransferProblem`/`TransferTwoManeuverProblem` (4D : `t1, dt1, α1, β1`) **existe déjà
  mais n'est pas branché**.
- **Généralisation** : chaque phase dérivée *marquée optimisable* déclare son vecteur de
  décision et sa **condition de terminaison** ; quand l'étage propriétaire est à
  épuisement, la durée n'est plus une variable mais résulte du `MassDepletionDetector`
  (réduit la dimension du problème).
- Optimiseur : **on garde CMA-ES** ; pas de raison d'en changer pour ce niveau (problème
  non convexe, peu dimensionné, sans gradient analytique).

### 4.2 Niveau dimensionnement carburant — deux approches à explorer

**Constat clé** : à profil et trajectoire fixés, la relation `propellantLoad[i]` ↦
faisabilité est quasi **monotone** (plus de propergol ⇒ plus facile d'atteindre la cible,
jusqu'à saturation). On veut le **plus petit** `propellantLoad` qui réussit encore.

**Qu'est-ce que la « bisection 1-D » ici (niveau conceptuel)** : c'est une recherche par
dichotomie de ce plus petit propergol faisable. La monotonie garantit qu'il existe un
**seuil** : en dessous, la mission échoue (pas assez de ΔV) ; au-dessus, elle réussit (et
tout surplus est du poids mort traîné inutilement). La bisection cherche ce seuil :

```
charge ┤ infaisable        |        faisable (surplus = poids mort)
       ┼───────────────────●──────────────────────────────►
       0                  seuil                       capacité
                       (cible : juste au-dessus)
```

1. **Encadrer** : une borne basse connue infaisable (p. ex. 0, ou la masse sèche seule) et
   une borne haute connue faisable (le seed analytique §3, voire la capacité max).
2. **Tester le milieu** : on charge la valeur médiane et on demande à la boucle interne
   (4.1) si la mission atteint la cible dans la tolérance.
3. **Resserrer** : si faisable, la médiane devient la nouvelle borne haute (on peut
   descendre encore) ; sinon elle devient la borne basse (il faut remonter).
4. **Répéter** jusqu'à ce que l'écart haut/bas passe sous un grain choisi (en kg ou en %),
   en gardant une marge de réserve au-dessus du seuil exact.

Chaque test = une évaluation de la boucle interne ; la dichotomie converge en un nombre
**logarithmique** d'essais (≈ une dizaine pour atteindre ~0,1 % de l'intervalle), d'où son
intérêt face à un CMA-ES externe quand les étages sont **découplés** (un seuil par étage,
résolu indépendamment). Le test « faisable ? » est exactement la condition de succès de
mission déjà utilisée par l'optim de trajectoire — pas un nouveau critère.

**Approche (a) — Hybride (recommandée comme défaut)**
```
1. Seed analytique : propellantLoad[i] ← Tsiolkovsky inverse (§3) + marge.
2. Pour chaque étage à charge optimisable, indépendamment :
     bisection 1-D sur propellantLoad[i] (cf. ci-dessus)
       critère : la boucle interne (4.1) atteint la cible dans la tolérance
       → on descend vers la plus petite charge faisable.
3. Warm-start : chaque évaluation seed l'optim interne avec le résultat précédent.
```
- Avantage : exploite la monotonie → **bien moins cher** qu'un CMA-ES externe ; robuste ;
  borne min faisable bien définie.
- Optimiseur : **pas de CMA-ES externe** — une bisection suffit par étage indépendant.
- Limite : suppose des étages **découplés**. Faux quand le *partage* du ΔV entre étages
  est lui-même un degré de liberté (ex. combien de ΔV donner à l'ESC-A vs à l'AKM).

**Approche (b) — CMA-ES imbriqué (pour le cas couplé)**
```
Boucle EXTERNE : CMA-ES sur le vecteur propellantLoad[] (n = étages à charge variable)
   coût = Σ propellantLoad[i]/capacity[i]   (à minimiser)
        + pénalité_barrière si la boucle interne échoue (infaisable)
   pop réduite (8–12), warm-start interne entre évaluations
        │
        └── Boucle INTERNE : optim de trajectoire (4.1), terminaisons à épuisement
```
- Avantage : gère le **couplage inter-étages** (répartition du ΔV upper/AKM, marges
  croisées) qu'une bisection par étage ne capture pas.
- Optimiseur : **réutilise le CMA-ES existant** (un seul framework dans le code), pas une
  nouvelle dépendance.
- Coût : multiplie le temps (interne déjà lent). Atténuations : faible dimension externe,
  population réduite, warm-start, gradient effectif quasi-monotone.

**Recommandation** : démarrer en **hybride** (seed analytique + bisection par étage) pour
les profils à étages découplés (LEO bi-étage). **Basculer en CMA-ES imbriqué** uniquement
pour les profils où l'allocation du ΔV entre étages est un choix (GEO avec AKM séparé).
Les deux partagent la **même boucle interne** et la **même condition de succès** — c'est
un continuum, pas deux implémentations disjointes.

### 4.3 Synthèse « quel optimiseur »

| Niveau | Variables | Optimiseur | Justification |
|---|---|---|---|
| Trajectoire (interne) | pitch, timing, durées, angles de burn | **CMA-ES** (existant, généralisé) | non convexe, peu dimensionné, sans gradient |
| Charge carburant, étages découplés | `propellantLoad[i]` indép. | **Bisection 1-D** par étage | relation monotone ⇒ dichotomie optimale |
| Charge carburant, étages couplés | `propellantLoad[]` (vecteur) | **CMA-ES externe** (imbriqué) | capture le partage de ΔV inter-étages |
| Tout fusionné | (rejeté) | — | échelles hétérogènes ⇒ CMA-ES dégradé |

---

## 5. Câblage sur les outils existants

Le modèle proposé **ne demande presque aucune brique nouvelle** ; il rend explicite et
pilote ce qui existe déjà :

| Élément conceptuel | Outil existant à réutiliser |
|---|---|
| Affectation étage→phase, jettison | `VehicleStack.resolveActiveStage`, `ActiveStageInfo.massAfterJettison` (déjà utilisé dans `GravityTurnManeuver`) |
| Terminaison de phase par épuisement | `MassDepletionDetector` (existe, **jamais câblé** aujourd'hui) |
| Phase optimisable + son problème | `OptimizableMissionStage<T>`, `TrajectoryProblem`, `CMAESTrajectoryOptimizer` |
| Transfert optimisable (au lieu d'analytique) | `TransferProblem` / `TransferTwoManeuverProblem` (existent, non branchés) |
| Capacité vs charge embarquée | champ `propellantLoad` à introduire (cf. 04, lot L1) |
| Budget ΔV / Tsiolkovsky | `Physics.computeBurnDuration` (inverse), `CircularizationBurnResolver`, vis-viva |
| Objectif déclaratif | `OrbitInsertionObjective` (+ `payload mass` à exposer) |
| Orchestration | `MissionOptimizer` (à étendre : dérive le profil, puis boucle 4.1/4.2) |

> Le glissement conceptuel principal est de **déplacer la construction du profil hors de
> `Mission.buildStages()`** vers un dérivateur qui consomme (objectif + lanceur typé).
> 04 détaille les refactors ; ce doc fixe le *pourquoi* et le *quoi*.

---

## 6. Points complémentaires à préciser

- **Charge utile en entrée** : sans `payload mass` explicite, « combien de carburant pour
  X kg sur l'orbite Y » n'a pas de sens. À promouvoir au niveau objectif.
- **Contrainte de durée de coast cryogénique** : capacité de premier ordre, car elle
  *force* la délégation de la circularisation (B vs A). À modéliser comme un attribut
  d'étage qui invalide certaines affectations.
- **Étages à charge figée** (solides) : exclus du dimensionnement (4.2) ; leur ΔV est une
  entrée, leur jettison est sur épuisement.
- **Pertes finite-burn et steering** : le seed analytique (§3) les sous-estime ; la boucle
  interne (4.1) les capture naturellement via la propagation. Quantifier l'écart pour
  calibrer la marge du seed.
- **Marge de propergol réservée** : contrainte de la boucle 4.2 (ne pas descendre sous
  1–2 % résiduel) plutôt que viser zéro.
- **Faisabilité** : si `dry + m_prop_requis > capacity`, le lanceur ne peut pas la mission
  → remonter un diagnostic explicite (et non un échec silencieux d'optim).

---

## 7. Questions ouvertes

- **Granularité du descripteur de capacités** : jusqu'où le formaliser (enum simple vs
  modèle riche) avant que ça ne devienne sur-ingénierie pour 2–3 lanceurs cibles ?
- **Dérivation déterministe vs recherche** : l'affectation §2.4 est-elle toujours unique,
  ou existe-t-il des cas où plusieurs découpages sont valides et où il faut *choisir*
  (mini-planificateur) ? (Hors périmètre première itération.)
- **Frontière hybride/imbriqué** : quel critère décide qu'un profil est « couplé » et
  bascule en CMA-ES externe ? (Présence d'au moins deux étages à charge variable se
  partageant un même poste de ΔV ?)
- **Catalogue de lanceurs et de spacecraft** : où vivent les définitions typées
  (Falcon Heavy, Ariane 5, sat + AKM) ? (cf. 04 §4.1, `Launchers.*`).

---

## 8. Lien avec l'implémentation

Ce document fixe le **modèle** (le lanceur génère le profil, d'où le carburant). La
**roadmap d'implémentation** (introduction de `propellantLoad`, modèle Falcon Heavy,
câblage `MassDepletionDetector`, refonte GEO/AKM, optim externe carburant, Ariane 5) est
décrite dans **`04-mission-realism.md`, lots L1–L9**. Ce doc ajoute, en amont de cette
roadmap, la **brique de dérivation du profil** et la **clarification de la hiérarchie
d'optimiseurs** (CMA-ES interne généralisé · bisection 1-D ou CMA-ES externe pour le
carburant).
