# PHY-2 / L0 — Baseline consolidée

Lot `L0` de `PHY-2`, découpé en [`06-decoupage-PHY-2.md`](06-decoupage-PHY-2.md) §5. Aucun
code de production. Ce document est un **log de mesure**, pas une conception : il réunit en
un seul endroit la référence chiffrée *avant* que `PHY-2` ne change quoi que ce soit, pour
que les quatre re-baselines de `L2`–`L5` aient une photo « avant ».

> **Méthode — aucune mesure neuve n'a été lancée pour ce document.** Les entrées de physique
> sont **héritées de `PHY-1`** et inchangées : elles portent sur les modèles d'atmosphère et
> le comportement de l'intégrateur, pas sur les lanceurs, donc `PHY-8` ne les a pas
> déplacées. L'état des profils est **lu dans les références épinglées** au commit de départ
> (`5848e62`, *J2 : atmosphere decisions*). La seule entrée **pas encore en main** — le
> surcoût compute d'une optimisation drag-on — revient à `L1` (sa preuve 2), le vol drag-on
> d'une ascension étant infaisable tant que le socle `L1` n'existe pas (§6).

---

## 1. Les trois mesures de physique héritées (inchangées)

Mesurées par `PHY-1 / L0` ([`03-baseline-L0.md`](03-baseline-L0.md)) le 2026-08-20, et non
re-mesurées ici parce que rien du code d'atmosphère ni de l'intégrateur n'a bougé depuis.

**1.1 Harris-Priester a une bande étroite, deux défaillances opposées** ([03 §2.1](03-baseline-L0.md)).
`getMinAlt/getMaxAlt` = **[100 km, 1000 km]** ; **lève une exception sous 100 km**, rend
**0,0 en silence au-dessus de 1000 km**. L'exception survient *pendant l'évaluation d'un pas
d'essai*, avant tout détecteur — irrattrapable proprement. Conséquence structurante : une
ascension part de 0 km, **HP ne peut pas la voler**. C'est ce qui force la mesure `a`/`c` de
`L1` et amende « optim toujours HP » ([08 §3.3](08-conception-L1-PHY-2.md)).

**1.2 La traînée est gratuite en pas au-dessus de 200 km, catastrophique en dessous**
([03 §2.2](03-baseline-L0.md)). Parking NRLMSISE, B = 101 kg/m² :

| alt initiale | pas | issue |
|---:|---:|---|
| ≥ 200 km | 431–452 | OK (identique au drag-off) |
| 150 km | 474 | échec à −8,3 km |
| **130 km** | **982 497** (**487 s**) | échec à −30,1 km |

Un seul candidat CMA-ES qui pique bas fait passer une évaluation de 0,2 s à 8 min : activer
la traînée sans borne d'altitude rend l'optimisation **non terminante**, pas seulement lente.

**1.3 `ReentryGuard`, tel qu'il est réglé, n'arrête pas une rentrée par traînée**
([03 §2.3](03-baseline-L0.md), [`../bugs.md` BUG-10](../bugs.md#bug-10--reentryguard-inopérant-en-présence-de-traînée)).
Plancher à −50 km, l'intégrateur cède **au-dessus** (−9/−30 km) : rejoué armé, il ne change
ni un pas ni une milliseconde.

Ces trois mesures sont les **entrées consommées par la conception `L1`** ; `08` les lève.

---

## 2. Les deux contraintes dures

Ce ne sont pas des entrées à consulter mais des obstacles à lever avant la bascule du défaut,
et `L1` les traite ensemble par **un seul arrêt d'altitude drag-conditionnel**
([08 §3.1](08-conception-L1-PHY-2.md)) :

1. **une borne d'altitude est nécessaire à la terminaison de l'optim** (§1.2) ;
2. **`ReentryGuard` est inopérant sous traînée** (§1.3).

---

## 3. L'état des profils au commit de départ (post-`PHY-8`)

C'est le seul contenu que `PHY-8` a déplacé, et donc le seul qui distingue cette baseline de
celle de `PHY-1`. Lu dans les références épinglées à `5848e62`.

**Les deux profils de référence** (`AscentBaselineN2Test`, épinglés, `orbitlab.slowTests`) :

| Profil | `transitionTime` | MECO | Orbite atteinte | incl. |
|---|---:|---|---|---:|
| Falcon Heavy LEO-400 | 314,8657 s | t+321,87 s / 38 335,5 kg | **400 311,8 × 419 162,6 m** | 5,3027° |
| Falcon Heavy GEO | 343,3690 s | t+350,37 s / 69 059,6 kg | **35 786 249 × 35 791 192 m** (e 3,4×10⁻⁵) | 89,6 (marge) |

**Les autres profils épinglés** : `CentralBodyBaselineTest` (62 frontières × 4 profils × 2
passes, tolérance `0.0`), plus `MeoMissionTest`, `Ariane64MissionTest`. La **cellule LEO de
l'Ariane 64 est la moins bonne des quatre terrestres** — `390 106 × 419 691 m`, 9,9 km sous
la cible au périgée, dans la barre ±7 % ([`etagement/07-cloture.md`](../etagement/07-cloture.md) §4).

**Ce que `PHY-8` (#106) a déplacé depuis le L0 de `PHY-1`** — la raison d'une baseline fraîche :

- **découpage propulseurs/cœur, cœur étranglé à f = 0,81** : masse au MECO **+1 908,6 kg**
  (LEO) / **+4 431,0 kg** (GEO), orbites finales **inchangées** (2,9 m LEO, 1,4 m GEO) ;
- **le profil MEO de `CentralBodyBaselineTest` a dû être ré-enregistré** ([`../dette-technique.md` DT-19](../dette-technique.md#dt-19)) ;
- **ciblage moyen `MIS-7`** (antérieur) : périgée LEO déplacé de 19,2 km.

---

## 4. La dette d'Isp héritée, chiffrée (post-`PHY-8`)

L'entrée que `L2` consomme, et elle a **changé deux fois** et est devenue **asymétrique**
([`../dette-technique.md` DT-13](../dette-technique.md#dt-13), [clôture §6](../etagement/07-cloture.md)) :

- **Falcon Heavy : 408 → 396 m/s** (bloc bas, un seul moteur à 296 s — rien de dissous, la
  réserve d'insertion a alourdi la pile) ;
- **Ariane 64 : 671 → 64 m/s** (l'éclatement dissout un mélange solide/cryogénique ; la dette
  se localise sur le seul Vulcain).

`PHY-2` ne peut donc plus les calibrer d'une seule passe. `L2` pose les deux valeurs en
volant, traînée en main.

---

## 5. Procédure d'exécution (anti-`BUG-7`)

Toute mesure de re-baseline de `L2`–`L5` passe par là, sous peine de débattre d'un rouge qui
n'est pas le sien :

- **exécution isolée des gates** — [`BUG-7`](../bugs.md#bug-7--les-gates-de-non-régression-tombent-quand-un-test-lunaire-les-précède-dans-le-même-jvm)
  fait tomber les gates `0.0` au dernier bit quand un test lunaire les précède dans le même
  JVM (`gateTest`, `forkEvery = 1`) ;
- **`cleanTest` systématique** — relancer le même filtre `--tests` après un succès rend la
  tâche `UP-TO-DATE` et n'exécute rien en affichant un vert ([[gradle-test-filter-up-to-date]]) ;
- **JDK 21** — `JAVA_HOME` sur GraalVM 21.0.5, l'env par défaut (17) échoue.

Les tests d'optimisation et de mission sont lents, et c'est **l'utilisateur qui les lance**.

---

## 6. Ce que L0 ne mesure pas, et pourquoi

**Le surcoût compute d'une optimisation drag-on.** C'est la 4ᵉ entrée que `PHY-1 / L0`
annonçait, et la seule pas encore chiffrée : le vol drag-on d'une ascension à l'optim est
infaisable aujourd'hui ([`05-conception-L2.md`](05-conception-L2.md) §4.2). Elle revient donc
à **`L1`**, une fois le socle posé — c'est sa preuve 2, et c'est elle qui tranche `a`
(NRLMSISE partout) contre `c` (composite par phase) ([08 §3.3](08-conception-L1-PHY-2.md)). La
fiche roadmap annonce +5 % (HP) à +50 % (NRLMSISE) ; c'est cette borne que `L1` confronte.

---

*Document rédigé le 2026-09-11 — consolidation des mesures héritées de `PHY-1` et de l'état
épinglé au commit `5848e62`, sans mesure neuve.*
