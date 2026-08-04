# 03 — Garde de rentrée atmosphérique

**Date** : 2026-08-04
**Statut** : validé, en implémentation
**Suite de** : `01-separations-implicites.md`, `02-baseline-n2.md`

---

## 1. La classe de défauts

Rien, dans la chaîne de phases de mission, n'arrête une propagation numérique dont la
trajectoire rentre dans l'atmosphère. Quand ça arrive, l'intégrateur suit la trajectoire
sous la surface, le pas adaptatif s'effondre quand `r → 0`, et la propagation ne revient
jamais.

### 1.1 L'instance observée (2026-08-04)

Sur `PropellantLoadOptimizerIntegrationTest#geoMultiStage_shrinksEveryVariableLoadStage`,
à la sonde `λ(S1) = 0,3` :

1. le `DepletionGuard` s'arrête sur « Gravity turn (S2) » en laissant la pile exactement
   sur sa masse à vide ;
2. `AnalyticParkingInsertionStage` planifie `dv1 = 372 m/s / dt1 = 0,0 s` et
   `dv2 = 107 m/s / dt2 = 0,0 s` — `Physics.computeBurnDurationCapped` plafonne à la durée
   de dépletion, donc 0 ;
3. la phase **vole** ce plan : 2 666 s de balistique pure depuis 36 km à 7 603 m/s,
   c'est-à-dire une rentrée.

Le test a tourné **4 h** avant annulation.

### 1.2 Ce qui a déjà été fait, et pourquoi ça ne suffit pas

`AnalyticParkingInsertionStage` a reçu `BURN_CAPACITY_TOLERANCE` + `requireDeliverable()` :
il refuse désormais un plan dont les poussées ont été plafonnées par l'ergol restant. Le
test d'intégration est repassé de 4 h à **6 min 03 s**, λ*=[0,9344 ; 0,8141], 28 évals,
2 passes, −82 890 kg — identique à la référence du 25/07.

Cela ferme **une instance**. La classe reste ouverte : n'importe quelle autre phase
analytique peut produire une rentrée et rebloquer.

---

## 2. Le périmètre réel

Les phases analytiques ne créent pas *un* propagateur, elles en créent **deux familles** :

| Famille | Sites | Visible depuis `configure()` ? |
|---|---|---|
| **Volée** | `StageChainRunner.run()`, chaque `propagateStandalone` | oui / non |
| **Planification** | `AnalyticGtoInjectionStage.coastForward` + `timeToNextNode`, `AnalyticHohmannTransferStage.simulateBurn1AndFindApogee`, `AnalyticTrimBurnStage.detectStateAtApogee`, `CircularizationBurnResolver.detectTimeToApoapsis`, `AnalyticPlaneTrimAtNodeStage` | **non** |

La seconde famille est tout aussi bloquante. Le commentaire l.346-350 de
`AnalyticGtoInjectionStage` le documente déjà : un aim à 177 000 km « made the downstream
propagation grind for tens of minutes ». Un hook posé uniquement sur `MissionStage` ne
couvrirait pas ces sites.

---

## 3. Décisions de conception

### 3.1 Plancher : −50 km sous le rayon équatorial WGS84, critère sphérique

**Le piège n'est pas l'ascension, c'est le pas de tir.**

La fonction `g` de `MinAltitudeTracker` est sphérique-équatoriale
(`|r| − WGS84_EARTH_EQUATORIAL_RADIUS`). Or la Terre est aplatie de 21,4 km
(`Re − Rp = 6 378 137 − 6 356 752`). Une altitude sphérique est donc **structurellement
négative** sur un pas de tir non équatorial, *avant même le décollage* :

| Site | Latitude | Altitude sphérique au sol |
|---|---|---|
| Kourou | 5,2° | −0,16 km |
| Canaveral | 28,5° | −4,9 km |
| Vandenberg | 34,7° | −7,1 km |
| Baïkonour | 45,9° | −11,1 km |
| Plessetsk | 62,9° | −17,0 km |

Un plancher à 0 m ne « se déclencherait pas à l'allumage » : il serait **déjà franchi sur
le pas de tir**. À −50 km, la marge est de ≥ 33 km sous le pire pas de tir terrestre, et
l'ascension a `g > 0` croissant dès `t = 0`. Aucune exclusion de phase n'est nécessaire.

**Le coût de la profondeur est nul.** Le but n'est pas de dater la rentrée, c'est d'arrêter
avant l'effondrement du pas — qui est en `r → 0`, soit 6 328 km plus bas. Tomber de 0 à
−50 km à 7,6 km/s coûte quelques secondes de propagation ; le blocage observé, c'est
2 666 s puis l'infini.

**Écarté — `AltitudeDetector` + `OneAxisEllipsoid`.** Donnerait une vraie altitude
géodésique et autoriserait un plancher propre à −10 km. Plus honnête physiquement, mais
introduit une dépendance à l'ITRF dans un détecteur qui doit rester trivial, et n'achète
rien : on ne se sert jamais de la valeur, seulement du signe.

**Écarté — critère sur le signe de la vitesse radiale.** Rendrait `g` discontinu, alors
qu'Orekit cherche des racines d'une fonction continue. Détecteur fragile pour un problème
que le plancher profond règle sans condition. Idem pour un `activeFrom` à la
`MinAltitudeTracker` : équivalent à une exclusion de phase, donc oubliable.

### 3.2 `ReentryGuard` neuf, pas `MinAltitudeTracker` + handler STOP

`MinAltitudeTracker` porte un état mutable (`minAltitude` / `maxAltitude`) et son
`altitudeThreshold` sert **à la fois** d'offset de `g` et de barre de violation lue par
`hasViolatedMin()`. Lui greffer un handler STOP coupleraient deux rôles sur un seul seuil :
impossible de mettre la barre de mesure à 100 km et le plancher d'arrêt à −50 km.

Structure retenue, jumelle de la paire existante :

```
detector/MassDepletionDetector  →  detector/ReentryDetector   (AbstractDetector, sans état)
detector/DepletionGuard         →  detector/ReentryGuard      (final, arm / armQuiet)
```

### 3.3 Portée : câblage explicite, `StageChainRunner` comme point unique de la chaîne volée

- `StageChainRunner.run()` arme le garde sur chaque propagateur de phase, avant
  `stage.configure()` → couvre **toutes** les phases de **toutes** les missions, sur la
  passe *optimize* et la passe *ephemeris*, en une ligne. Une phase ajoutée demain est
  couverte d'office.
- Chaque `propagateStandalone` et chaque propagateur de planification arme en `quiet`, à
  côté du `DepletionGuard.armQuiet` déjà présent quand il y en a un.

**Écarté — armer dans `OrekitService.createOptimizationPropagator`.** Une ligne pour tout
couvrir (l'usine n'est appelée que par le code mission — vérifié), mais elle rend
impossible la scission loud/quiet, et deux gardes STOP armés sur la même racine se
disputent le handler. Trop implicite pour un mécanisme dont on veut pouvoir **prouver**
l'inactivité.

### 3.4 Loud / quiet

| Chemin | Variante | Niveau |
|---|---|---|
| `StageChainRunner.sampling(...)` — passe ephemeris/replay | `arm` | **WARN** |
| `StageChainRunner.plain()` — chemin CMA-ES | `armQuiet` | — |
| `propagateStandalone` de toute phase | `armQuiet` | — |
| Propagateurs de planification | `armQuiet` | — |
| Manœuvres (`GravityTurn`, `Transfer`, `TransfertTwo`) | `armQuiet` | — |

Le runner distingue les deux par son champ `abortOnFailure` déjà présent
(`false` ⟺ sampling, `true` ⟺ plain).

**Pourquoi WARN et pas ERROR.** `DepletionGuard.arm` logue en ERROR parce que son
déclenchement signifie littéralement « la comptabilité de masse amont est fausse » — un
bug. Une rentrée, non : c'est un candidat physiquement infaisable, verdict légitime que la
machinerie aval sait lire. Loguer en ERROR un résultat nominal de l'exploration brouillerait
le signal.

Volume attendu sur le chemin loud : une ligne par évaluation de la boucle externe
(≈ 28 sur `geoMultiStage`), pas un déluge.

---

## 4. Intégration aval — rien à réinventer

Un STOP prématuré remonte déjà par :

```
StageChainRunner.StageRun.shortfallSeconds() > 0
  → MissionEphemerisGenerator marque complete=false
  → MissionLoadEvaluator lit ephemerisComplete=false
  → infaisable
```

Le garde n'a donc qu'à arrêter proprement.

### 4.1 Cas des propagateurs de planification

Un STOP y produit un plan tronqué plutôt qu'un blocage. Vérifié site par site — aucun ne
corrompt silencieusement :

- `simulateBurn1AndFindApogee` → `throw new IllegalStateException("No apogee found…")`
- `detectStateAtApogee` → `null`, que l'appelant transforme en `IllegalStateException`
- `timeToNextNode` → `NaN`, traité par un `warn` + injection non-nudgée
- `coastForward` → état précoce ; l'aim en aval le refuse via la vérification de capacité

Dans tous les cas : exception propre ou refus explicite, que l'optimiseur lit comme une
infaisabilité. Strictement mieux qu'un blocage.

---

## 5. Non-régression

**Contrainte ferme** : les profils calibrés Falcon Heavy LEO et GEO ne rentrent jamais. Le
garde doit être démontrablement inactif sur eux — aucun résultat numérique ne doit bouger.

Références à préserver :

| Profil | Référence |
|---|---|
| `geoMultiStage` | λ*=[0,9344 ; 0,8141] / 28 évals / 2 passes / −82 890 kg / ~6 min |
| LEO simple-λ | λ*≈0,4313 |

**Prédiction** : un détecteur Hipparchus dont `g` ne change jamais de signe **ne modifie
pas l'intégration**. La détection d'événement s'exécute par interpolation sur les pas déjà
acceptés ; seul un événement détecté tronque un pas. Sur les profils calibrés, `g` reste
≥ 250 km partout (LEO à 400 km, GEO à 35 786 km, ascension ≥ +50 km dès le pas de tir) —
pas de racine, pas de troncature, pas de repositionnement. Résultats attendus
**bit-identiques**, pas « proches ».

**VÉRIFIÉ 2026-08-04 sur `geoMultiStage`** — la prédiction tient :

| | Référence 25/07 | Avec le garde |
|---|---|---|
| λ* | [0,934375 ; 0,8140625] | [0,934375 ; 0,8140625] |
| évals / passes | 28 / 2 | 28 / 2 |
| Masse échelonnée | 1 243 619 → 1 160 729 kg | idem |
| Δ | −82 890 kg (−6,7 %) | −82 890 kg (−6,7 %) |
| Durée | 6 min 03 | 6 min 26 |

Aucun chiffre ne bouge. Le seul écart est +23 s de temps mur, compatible avec le coût des
évaluations de `g` toutes les 10 s sur les longs coasts GEO — mais sur un échantillon unique
il n'est pas séparable du bruit machine, et il ne justifie aucune action.

Reste à relancer le LEO simple-λ (référence λ*≈0,4313).

**Rappel de la leçon enregistrée** : ne jamais faire respecter un invariant d'optimiseur en
déplaçant une borne de recherche CMA-ES (Hipparchus normalise l'espace par la largeur de la
boîte, ce qui change la trajectoire de recherche à graine identique, même là où la borne ne
mord pas) — passer par une pénalité de coût. Ici le garde est un **détecteur**, pas une
borne : il n'entre pas dans la normalisation. Mais l'exigence d'invariance numérique est la
même.

---

## 6. Vérification

`ReentryGuardTest` — 5 tests, `skipped="0"`, 7,9 s. Toutes les propagations sont enveloppées
dans un `assertTimeoutPreemptively(Duration.ofSeconds(30), …)` : sans le garde le mode
d'échec est un **blocage**, et un test bloqué n'apprend rien ; le timeout le retransforme en
échec de test.

**Le garde arrête une rentrée**

1. **`reentringCoast_stopsInsteadOfHanging`** — l'état exact du bilan (36 km,
   7 603 m/s horizontal, périgée ~800 km *sous* la surface), coast de 2 666 s. Arrêt à
   440 s, sur le plancher à ±1 km.
2. **`stageChainRunner_flagsTheReentringStageTruncated`** — le contrat de bout en bout : la
   phase revient `propagationFailed=false` et `shortfallSeconds() > 0`, c'est-à-dire
   **tronquée** et non en échec. Le `WARN` du chemin loud est bien émis.

**Le garde est inactif sur tout ce qui est nominal**

3. **`nominalCircularOrbit_neverTriggers`** — 400 km circulaire, 1 h. Position finale
   **exactement identique** à celle d'un propagateur non armé — `assertEquals(…, 0.0)`,
   delta zéro, pas une tolérance. C'est la preuve d'inactivité au niveau détecteur, et elle
   passe : elle confirme qu'un détecteur sans changement de signe ne touche pas
   l'intégration (§5).
4. **`launchPadLatitudes_clearTheFloorBeforeLiftoff`** — `g > 0` avec ≥ 25 km de marge à
   0°, 5,2°, 28,5°, 34,7°, 45,9°, 62,9° et 90°, positions construites via le vrai
   `OneAxisEllipsoid` WGS84 pour que l'aplatissement absorbé soit le vrai. C'est le test qui
   verrouille §3.1.
5. **`verticalClimbFromTheWorstPad_neverTriggers`** — montée depuis le pas de tir à 62,9°
   (pire cas, −17 km sphériques) : les 60 s sont volées intégralement.

**Suites voisines** — 22 suites, 152 tests, 0 skipped, 0 failure
(`mission.stage.*`, `mission.detector.*`, `mission.vehicle.*`, `mission.attitude.*`,
`mission.operation.*`).

**Reste à faire, côté utilisateur** : relancer `geoMultiStage` et le LEO simple-λ pour
confirmer la prédiction d'invariance du §5 sur les profils calibrés.
