# docs/bugs.md — registre des bugs ouverts

Bugs constatés à l'usage, hors du périmètre d'un chantier en cours. Un item
sort d'ici soit corrigé, soit promu en item de roadmap quand il s'avère être un
chantier à part entière.

**Convention.** `BUG-n` dans l'ordre de découverte, jamais réattribué. Chaque
fiche dit ce qu'on observe, ce qu'on croit savoir, et ce qui reste à vérifier —
la frontière entre les deux derniers doit rester lisible.

| ID | Titre | Constaté | Statut |
|---|---|---|---|
| [`BUG-1`](#bug-1--jitter-du-billboard-et-de-lorbite-de-pluton) | Jitter du billboard et de l'orbite de Pluton | 2026-08-10 | Ouvert, non diagnostiqué |

---

## BUG-1 — Jitter du billboard et de l'orbite de Pluton

**Constaté.** Depuis la vue solaire, cliquer sur Pluton. Pendant l'approche puis
une fois arrivé, son icône et sa ligne d'orbite tremblent d'une frame à l'autre.

**Ce n'est pas un bug du chantier caméra** (`NAV-1`, résolu). La transition ne
fait que maintenir la vue assez longtemps sur une configuration où le problème
est visible ; le même défaut doit être atteignable à la molette, et le tremblement
persiste après l'arrivée. À vérifier en premier, justement : reproduire **sans**
transition, en zoomant manuellement sur Pluton depuis la vue solaire. Si le
tremblement est là aussi, la piste ci-dessous tient ; sinon elle est fausse et
il faut repartir de zéro.

### Piste principale : précision du `float` dans le repère far

Le repère far stocke tout en unités solaires (1 unité = 1e9 m), en `float`. Le
quantum de représentation dépend donc de l'éloignement au Soleil, et Pluton est
le corps où il est le plus grossier :

| Corps | Distance au Soleil | ulp du `float` | Cadrage à l'arrivée (5 rayons) | Quantum / cadrage |
|---|---|---|---|---|
| Terre | ~150 unités | 2⁻¹⁶ ≈ 15 km | 31 855 km | 0,05 % |
| Pluton | 5900 à 7400 unités | 2⁻¹¹ ≈ 488 km | 5 942 km | **~8 %** |

Même code, même chemin de conversion : c'est un problème de **magnitude**, pas
de logique. Pluton cumule les deux extrêmes — le corps le plus lointain et l'un
des plus petits rayons du catalogue (1188 km, cf. `PlanetRadius`), donc le
cadrage le plus serré.

**Pourquoi ça tremble au lieu d'être un simple décalage fixe.** La position
rendue vaut `anchor.localTranslation + farRoot.localTranslation`, et les deux
sont réécrites à chaque frame depuis des valeurs qui bougent
(`PlanetPoseAppState`, `FloatingOriginAppState`). L'arrondi des deux termes
change donc à chaque frame, et le résidu se déplace. C'est aussi pour ça que le
modèle 3D de Pluton, lui, ne tremble pas une fois focalisé :
`FloatingOriginAppState` pose exactement l'opposé du `localTranslation` du corps
focus, donc la somme retombe à zéro **exactement**. Tout le reste hérite du
bruit.

**Pour l'orbite en particulier**, le recentrage n'y peut rien : les sommets sont
figés en héliocentrique au moment de la construction —
`OrbitLineFactory` les convertit par
`RenderTransform.toRenderUnitsJmeAxes(pHelio, null, RenderContext.solar())`, donc
un sommet d'orbite plutonienne est un `float` de magnitude ~6000 qui a déjà perdu
ses bits de poids faible. Translater le nœud parent ensuite ne les rend pas.

### Deux directions à explorer

1. **Stocker la géométrie far relative au corps focus** plutôt qu'héliocentrique,
   et la reconstruire au changement de focus. C'est ce que le near viewport fait
   déjà pour les trajectoires de mission via `RenderContext.planet(body)`. Coûteux
   (rebuild à chaque changement de focus), mais c'est la seule qui règle le
   problème à la racine plutôt que de le repousser d'une décade.
2. **Garder la double précision jusqu'au décalage** et ne convertir en `float`
   qu'après soustraction de l'origine courante. `RenderTransform` travaille déjà
   en `Vector3D` ; c'est la conversion finale qui arrive trop tôt pour les sommets
   d'orbite. Moins invasif, mais ne résout rien pour un corps qu'on regarde sans
   l'avoir focalisé.

### À regarder

- `engine/scene/OrbitLineFactory.java` — construction des `FloatBuffer` de sommets
  (lignes ~66, ~130, ~188 : les trois chemins de conversion).
- `engine/scene/body/lod/BillboardIconView.updateScreenPosition` — projection de
  l'icône, qui lit `anchor.getWorldTranslation()`.
- `states/camera/FloatingOriginAppState` — la négation exacte qui explique
  pourquoi le corps focus est le seul épargné.

### Non vérifié

Tout ce qui précède est un raisonnement sur les magnitudes, pas une mesure. Rien
n'a été instrumenté. Avant de coder quoi que ce soit : loguer sur quelques frames
la position monde de l'ancre de Pluton et celle d'un sommet de son orbite, et
confirmer que l'amplitude du tremblement est bien de l'ordre de 500 km et non de
plusieurs milliers — auquel cas la cause serait ailleurs.
