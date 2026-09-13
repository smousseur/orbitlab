# OPT-1 / B2 — plancher de convergence GT — conception

Lot `B2` du backlog [`01-decoupage.md`](01-decoupage.md) §5.3, levier FAST. Soumis au protocole §4 :
un changement unique, mesuré au banc, jugé **verdict-neutre** (`REL-18`, ~19 km), gates re-baselinés.

**Rend vrai :** un run GT qui a déjà atteint le seuil `acceptableCost` et stagné n'est plus forcé de
tourner jusqu'à 100 générations — il complète plus tôt, ce qui déclenche l'arrêt croisé plus tôt et
fait avorter les autres runs plus tôt.

---

## 0. Ce que la lecture du checker établit

[`AdaptiveConvergenceChecker`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/AdaptiveConvergenceChecker.java)
est appelé une fois par génération (`iterationCount` par run), et pose trois gardes avant la
convergence standard :

- **Règle 1** (l. 56) : jamais avant `MIN_ITERS_BEFORE_CONVERGE = 100` gén. — **le levier**.
- **Règle 2** (l. 59-63) : `earlyKill` des mauvais bassins après 300 gén. si coût > 1,0.
- **Règle 3** (l. 66) : tant que `coût > acceptableCost` **et** < 500 gén., ne converge pas.

Trois conséquences décident du périmètre :

1. **B2 ne touche que les runs déjà ≤ `acceptableCost`.** Un run au-dessus du seuil est retenu à
   500 gén. par la Règle 3, quel que soit le plancher. Abaisser la Règle 1 (100→X) et « conditionner
   le plancher au seuil » sont donc **le même changement** : il ne libère que les runs déjà bons.
2. **B2 est un levier GT, pas transfert.** Le GT atteint son `acceptableCost` (drag). Le **transfert
   ne l'atteint jamais** (`TransferTuning.acceptableCost = 3e-3` inatteignable,
   [`REL-30`](../reliquats.md)/[`REL-32`](../reliquats.md)) → la Règle 3 le tient à 500 gén. quoi
   qu'il arrive. B2 agit donc sur la recherche GT (tous modes, effet le plus fort en FAST) et **pas**
   sur le raffinement de transfert (les 71 % du wall BALANCED).
3. **Il y a un plancher en dessous duquel B2 casse quelque chose.** `CMAESRunExecutor` (l. 28-30)
   documente le piège : un run *amorcé* sous `acceptableCost` qui déclare victoire immédiatement
   **rend la graine verbatim, angles inexplorés**. Le plancher de 100 gén. est une des protections.
   B2 ne peut donc **pas supprimer** le plancher — seulement l'abaisser à une valeur qui laisse une
   vraie optimisation se produire.

---

## 1. Le changement (un levier)

Dans `AdaptiveConvergenceChecker` :

- **Extraire** `MIN_ITERS_BEFORE_CONVERGE` en constante par défaut documentée et **lire un override
  optionnel** (propriété système `orbitlab.opt.minConvergeIters`, défaut = la constante, fail-fast si
  invalide), exactement comme C1 l'a fait pour les tolérances. Absent en production → comportement
  inchangé ; posé par le banc → balayage sans recompilation.
- **Ne touche pas** les Règles 2 et 3 (earlyKill 300, plafond seuil 500) : un seul levier.

**Défaut inchangé jusqu'au commit final.** Tant que la constante garde `100`, `src/main` se comporte
comme aujourd'hui — gates verts. Le commit de clôture pose la valeur retenue (et re-baseline).

---

## 2. Le balayage (mesure — premier livrable)

`OptBenchMain` gagne `--floorSweep=<valeurs>` (jumeau de `--tolSweep` de C1) : chaque cellule
sélectionnée est volée une fois par valeur de plancher (l'override posé/nettoyé autour du run), avec
une table comparative (wall, évals, **λ**, résidu, orbite moy., un `.jfr` par niveau).

- **On balaie FAST d'abord** (GT pur, ~cheap), plancher décroissant : **100** (défaut) → 50 → 30 →
  20 → 10.
- **Deux lectures, pas une** :
  - le **wall** (et les évals) qui baissent = le gain net de l'arrêt croisé se matérialise ;
  - un **garde anti-graine** : si les évals s'effondrent brutalement vers ~la graine (le run rend son
    point de départ sans optimiser, piège §0.3), la valeur est **trop basse** — à rejeter même si le
    wall est beau.
- Le knee retenu est le plancher **le plus bas** qui garde **(a)** l'orbite/λ dans `REL-18` **et
  (b)** une vraie optimisation (évals cohérentes avec une recherche, pas un retour graine).

---

## 3. Choix + confirmation du verdict

- Retenir la valeur au knee sur FAST, puis **confirmer** sur BALANCED et PRECISE : le verdict (λ\*,
  résidu, orbite) dans `REL-18`, et la convergence non dégradée. Comme B2 ne touche que la recherche
  GT, l'effet sur le verdict passe par la qualité du hand-off GT — c'est lui qu'on surveille.
- La valeur retenue devient la constante par défaut (commit de clôture).

---

## 4. Acceptation et preuve

- **Gain** (le livrable) : baisse du wall / des évals au banc, **net de l'arrêt croisé** (FAST en
  premier ; BALANCED/PRECISE où la part GT le permet).
- **Verdict-neutre** : λ\*, résidu, faisabilité dans `REL-18` sur les quatre missions de gate.
- **Gates re-baselinés** (la trajectoire bouge au bit près, protocole §4 point 6).
- **Pas de retour graine** : l'optimisation a bien lieu à la valeur retenue (§2).

---

## 5. Ce que B2 ne fait pas

- Ne touche pas la Règle 2 (earlyKill 300) ni la Règle 3 (plafond 500 au-dessus du seuil), ni le
  transfert (jamais ≤ son `acceptableCost` — c'est `B1`).
- Ne supprime pas le plancher (piège du retour graine, §0.3) : il l'abaisse.
- Aucun changement de verdict recherché ; une valeur qui *améliorerait* le verdict au-delà du bruit
  serait aussi hors barreau (verdict-neutre, découpage §0).
