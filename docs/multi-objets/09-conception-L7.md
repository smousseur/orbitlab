# PHY-5 — L7 — Débris : visibilité et trace au sol *(conception)*

Lot fonctionnel, ajouté après la vérification visuelle de L5/L6 : les débris avec
leurs rubans inertiels instantanés font « fouilli », et le ruban ne répond pas à la
question utile — **d'où le débris est-il parti, et où atterrit-il ?** Ce lot les rend
**invisibles par défaut** et remplace le ruban par une **trace au sol** qui colle au
globe. Comme L6, il ne touche **pas la propagation** : tout est du rendu.

> **Décidé en conversation (2026-09-16).** Toggle **global** ; débris invisibles par
> défaut ; pour un débris **retombant**, toute la **courbe de chute au sol** en repère
> tournant ; pour un débris **orbital**, **icône seule** (inertiel).

---

## 1. Le défaut, mesuré

- Aujourd'hui un débris **s'allume à son largage et ne s'éteint plus** : `LodView` montre
  le maillage 3D quand il est assez gros (≥10 px), une **icône** sinon, **plus** son ruban,
  **toujours**. Aucun toggle. → icônes lointaines + N rubans = le « fouilli ».
- Le ruban est la trace **inertielle (GCRF)** de la chute.
- « D'où / où » est disponible dans l'éphéméride : `firstPoint()` = largage, `lastPoint()`
  = impact, et **`lastPoint().altitudeMeters() ≈ 0` ⇒ le débris a atterri** (il s'arrête
  soit au sol géodésique-0, soit à l'horizon — D5).
- **Le globe est tourné par `rotation_physique ∘ calibration_maillage`**
  (`PlanetPresenter.updatePose` → `RenderTransform.toRenderQuaternion(rotation,
  PlanetMeshCorrection.correctionFor(body, t))`), et **la Terre a une calibration**
  (`PlanetMeshCorrection` l.206). La rotation dessinée vit sur le **modelBucket** du globe.
- Un toggle de menu existe (`AppMenuItem.toggle` + `AppMenuModel`) → foyer du toggle.

---

## 2. Principe

- **Deux natures de débris.** Les boosters/cœur **retombent** (impact sol) ; le S2 en GEO
  (et tout débris haut) **n'atterrit jamais** (fin à l'horizon). La représentation dégrade
  proprement : trace au sol pour les premiers, rien pour les seconds.
- **Rien côté physique.** Le toggle et la trace sont du **rendu pur** ; `displayPointAt`,
  floating-origin, caméra, télémétrie, **gates** inchangés.

---

## 3. Décisions

### D1 — Visibilité : toggle global, débris invisibles par défaut

| | proche (maillage assez gros) | loin |
|---|---|---|
| **Défaut (OFF)** | maillage 3D | **rien** (ni icône ni trace) |
| **Toggle ON** | maillage 3D | **icône** + trace |

- Ajout à `LodView` : un mode **« pas d'icône de repli »** (réservé aux débris) — quand il
  n'est pas en 3D et que le mode est OFF, on **n'affiche rien** au lieu de l'icône.
- Toggle **« Afficher les débris »** dans le menu app (`AppMenuItem.toggle`), **global**
  (une seule bascule pour toutes les missions), état poussé dans les renderers.
- Le **primaire** (charge utile) n'est **jamais** affecté par ce toggle.

### D2 — Tri retombant / orbital

`lastPoint().altitudeMeters() < seuil` (~1 km) ⇒ **retombant**. Sinon **orbital**.

### D3 — Débris retombant : trace au sol dans le repère du globe dessiné

Toute la courbe de chute **largage → impact**, collée au sol :

- **Précalculée en coordonnées locales du globe** (une fois par piste, pas par frame) :
  chaque point GCRF `D(t)` est converti à l'échelle proche, puis **dé-tourné par `Q(t)`**
  = la rotation **dessinée** du globe à *son* instant `t`
  (`toRenderQuaternion(physique(t), correctionFor(EARTH, t))`, le même chemin que
  `PlanetPresenter`). On stocke `Q(t)⁻¹ · D_jme(t)`.
- **Accrochée sous le nœud repère-tournant de la Terre** (§D5) → elle hérite la rotation
  dessinée `Q(t_courant)` (+ position/échelle du globe). Conséquences, toutes voulues :
  - la trace **colle au sol** et tourne avec le globe ;
  - son bout — **l'impact** — reste **fixé à sa lat/lon** sur le bon continent dessiné (on
    suit la rotation *calibrée*, pas la physique seule, sinon décalage) ;
  - le **point courant** `t = t_courant` retombe exactement sur la **vraie position
    inertielle** (`Q(t_courant)·Q(t_courant)⁻¹·D_jme = D_jme`) → **pas de téléport** au
    largage, la trace part de sous le véhicule.
- **Marqueur d'impact** posé sur la surface (dernier point, altitude ≈ 0).
- Le **maillage/icône du débris reste à sa vraie position inertielle** (comme aujourd'hui,
  avec le correctif tremblement de L5/L6) ; **seule la trace** vit dans le repère tournant.

### D4 — Débris orbital : icône seule

À sa **vraie position (inertiel)**, **pas de trace**. (Rappel : pour un marqueur seul,
inertiel = tournant, même point ; on prend l'inertiel, sans transformation.)

### D5 — Seam : un nœud repère-tournant par corps dans `SceneGraph`

`SceneGraph` expose un **nœud « repère tournant »** par corps (au moins la Terre),
**mis à jour par `PlanetPoseAppState`** pour refléter la transformée du globe **dessiné**
(position proche + rotation calibrée `Q(t_courant)`), et **lu par `MissionRenderer`** pour
y accrocher la trace. Couplage **propre** (le rendu mission ne fouille pas les internes du
rendu planète) et **réutilisable** (trace au sol du primaire, sites de tir, autres
marqueurs sol plus tard). Écarté : lire directement le `modelBucket` de la `LodView` Terre.

---

## 4. Invariant préservé

Conforme au §4 du découpage. Les débris sont de l'affichage ; le toggle et la trace ne
touchent aucune donnée propagée. Les quatre gates volent `LEGACY` (0 séparation → aucun
débris) et, de toute façon, rien de stocké n'est modifié → **aucune re-baseline**.

---

## 5. Tests

- **Parties pures (TDD)** :
  - détection « a atterri » (seuil sur `lastPoint().altitudeMeters()`) ;
  - conversion `D(t)` → coordonnée locale-globe étant donnés `Q(t)` et l'échelle ;
  - propriété clé : sous `Q(t_courant)`, le **point courant** de la trace retombe sur la
    vraie position inertielle (pas de décalage au largage).
- **Logique du toggle** : `AppMenuModel` (déjà testé) ; la règle défaut/ON de visibilité
  des débris se teste hors rendu.
- Le **rendu** (trace collée au bon continent, impact au sol, désencombrement) se juge
  **à l'œil** (non testable ici).
