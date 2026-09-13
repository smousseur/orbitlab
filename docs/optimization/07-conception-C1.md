# OPT-1 / C1 — tolérances de l'intégrateur — conception

Lot `C1` du backlog [`01-decoupage.md`](01-decoupage.md) §5.3, **premier levier `C`** fixé par le
profil JFR de L0 ([`03-baseline-L0.md`](03-baseline-L0.md) §4). Soumis au protocole §4 du découpage :
un changement unique, mesuré au banc, jugé **verdict-neutre** (`REL-18`, ~19 km), gates re-baselinés
là où la trajectoire bouge au bit près.

**Rend vrai :** chaque évaluation CMA-ES coûte moins cher, parce que l'intégrateur prend moins de
pas — donc appelle NRLMSISE (une fois par pas) moins souvent. Aide **tous les modes** : la
propagation par candidat vit **dans** la recherche (L0 §2), pas dans le bucket `calc` 2 %.

---

## 0. Ce que L0 a établi, et la question que C1 doit trancher

- **Un seul propagateur en production.** Tout le vol — recherche, replay, éphéméride — passe par
  `OrekitService.createOptimizationPropagator` (l. 331) ; il n'existe **pas** de propagateur 50×50
  « Default » dans le code (CLAUDE.md périmé). **Le verdict est donc calculé sur le propagateur même
  que C1 desserre** : aucune re-vérification haute-fidélité ne l'isole → la neutralité de verdict se
  **mesure**, elle ne se présume pas.
- **Tolérances actuelles** : `absTol = 1e-8`, `relTol = 1e-10` **scalaires**, dupliquées en dur dans
  `createOptimizationPropagator` **et** `createTestPropagator`. Sur un état cartésien, `relTol`
  gouverne : position ~7e6 m × 1e-10 = **~0,7 mm**, soit **~7 ordres** sous le bruit `REL-18` de la
  comparaison de verdict. Énorme marge.
- **Le gain visé** : NRLMSISE ~17 % + ses transcendantes (~34 %) ≈ la moitié des échantillons FAST
  (L0 §4), appelé **une fois par pas d'intégration**.
- **La question ouverte de L0**, que C1 tranche en premier : le pas est-il limité par la
  **tolérance** (alors C1 gagne) ou par le **plafond** (`SAFE_MAX_STEP = 30` s en combustion,
  `COAST_MAX_STEP = 300` s en coast) et la **détection d'événements** (alors C1 est un no-op sur ces
  phases) ? Le profil JFR ne tranche pas seul (interpolateur DP853 ~1 %). Un A/B de tolérance au banc
  le révèle directement : si le wall chute, c'était tol-borné ; s'il reste plat, cap-borné.

---

## 1. Le changement (un levier)

Dans `OrekitService` :

- **Extraire** les deux tolérances de `createOptimizationPropagator` en **constantes nommées**
  (`DEFAULT_OPT_ABS_TOL`, `DEFAULT_OPT_REL_TOL`), avec la rationale (la valeur retenue par le
  balayage §3) en Javadoc.
- **Lire un override optionnel** au moment de construire l'intégrateur : propriétés système
  `orbitlab.opt.absTol` / `orbitlab.opt.relTol`, **défaut = les constantes**. Absentes en production
  → comportement inchangé ; posées par le banc → balayage sans recompilation. C'est le seul point
  d'entrée du balayage : zéro plomberie à travers les étages.
- **Ne touche pas** : `createTestPropagator` (test-only, ses valeurs sont épinglées par des tests) ;
  `minStep = 0.001` ; le ratio `relTol = absTol × 1e-2` (conservé de l'actuel → balayage à un
  paramètre) ; la vectorisation (scalaire, décidé).

**État par défaut inchangé jusqu'au commit final.** Tant que la constante garde `1e-8`/`1e-10`, le
`src/main` se comporte exactement comme aujourd'hui — gates verts. Le commit de clôture de C1 pose la
valeur retenue dans la constante (et re-baseline les gates).

---

## 2. Le balayage (mesure — premier livrable)

`OptBenchMain` gagne un argument `--tolSweep=<paires absTol/relTol>`. Pour la ou les cellules
sélectionnées, il exécute **chaque niveau** (en posant l'override avant chaque run) et émet une
**table comparative** : wall, évals, résidu, orbite atteinte (moy.), + un `.jfr` par niveau.

- **On balaie FAST d'abord** (~65 s/niveau, cheap et cible primaire), par **décades à ratio
  constant** : **(1e-8, 1e-10)** actuel → (1e-7, 1e-9) → (1e-6, 1e-8) → (1e-5, 1e-7) → (1e-4, 1e-6).
- **Lecture** : le wall qui **chute** = pas **tol-borné**, C1 gagne ; le wall qui **reste plat** =
  **cap-borné** (30 s combustion / 300 s coast / événements), C1 est un no-op sur ces phases — la
  réserve de L0 est alors levée par la mesure, pas par l'argument.
- **Confirmation du mécanisme** : le poste NRLMSISE au JFR baisse proportionnellement au nombre de
  pas. (Un compteur de pas explicite n'est pas ajouté : invasif à travers les propagateurs construits
  en profondeur ; le couple wall + JFR répond fonctionnellement. À n'ajouter que si le résultat wall
  est ambigu.)

---

## 3. Choix de la valeur + confirmation du verdict

- Retenir le niveau **le plus lâche** dont l'**orbite atteinte** (λ\*, résidu, faisabilité) reste
  dans `REL-18` (~19 km) du verdict L0 sur FAST.
- **Confirmer ce niveau** sur `BALANCED` puis surtout `PRECISE` — le mode à risque : le raffinement
  travaille à σ×0,01 et un coût plus **bruité** peut dégrader sa convergence. Exigence : verdict dans
  `REL-18` **et** convergence non dégradée (résidu, nombre d'évals du même ordre).
- Si FAST gagne mais PRECISE se dégrade au même niveau, retenir le niveau qui satisfait **les deux**
  (le plus contraignant des modes fixe la valeur — un seul levier global).

La valeur retenue devient la constante par défaut (commit de clôture).

---

## 4. Acceptation et preuve

- **Gain** (le livrable) : chute du wall au banc sur `FH_LEO400_FAST`, `FH_LEO400_BALANCED`,
  `FH_LEO400_PRECISE`, comparée à la colonne L0/L1a.
- **Verdict-neutre** : λ\*, résidu (kg), faisabilité dans `REL-18` sur les quatre missions de gate.
- **Gates re-baselinés** là où la trajectoire bouge au bit près (protocole §4 point 6), par l'outil
  de re-enregistrement que chaque gate porte ; `CentralBodyBaselineTest` est le re-baseline coûteux.
- **PRECISE non bruité** : convergence et verdict tenus au niveau retenu (§3).

---

## 5. Ce que C1 ne fait pas

- Pas de `createTestPropagator` (test-only), pas de `minStep`, pas de vectorisation.
- `C2` (Harris-Priester au transfert) et `C4` (coast à `COAST_MAX_STEP`) restent **conditionnés au
  constat de C1** : si le pas de coast est cap-borné, `C4` est sans effet et change de nature.
- `BUG-26` (échec `minStep` sur l'éphéméride GEO sous traînée) est **hors périmètre**. Des pas plus
  grands *pourraient* l'aider incidemment (l'intégrateur descend moins vers le plancher `minStep`),
  mais C1 ne le chasse pas ; à ne pas confondre avec un gain de C1.
- Aucun changement de verdict recherché : un niveau qui *améliorerait* le verdict au-delà du bruit
  serait aussi hors barreau (verdict-neutre, découpage §0).
