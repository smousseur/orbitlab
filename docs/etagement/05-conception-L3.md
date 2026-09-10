# PHY-8 / L3 — L'étranglement du corps central — conception

Lot **L3** du découpage ([`01-decoupage.md`](01-decoupage.md) §5), au-dessus du mécanisme livré par
[`03-conception-L1.md`](03-conception-L1.md) et du catalogue éclaté par
[`04-conception-L2.md`](04-conception-L2.md), mesuré contre
[`02-baseline-L0.md`](02-baseline-L0.md). Ce que le lot rend vrai : **le Falcon Heavy a une phase
corps-seul.**

C'est le lot le plus petit du chantier dans `src/main` — **deux fichiers**, dont un pour un libellé
d'écran — et l'un des plus gros dans les tests. Il est aussi le premier qui déplace des chiffres :
`L1` était inerte, `L2` iso-trajectoire, `L3` re-baseline. Toute la conception porte donc sur une
seule question : **comment prouver que ce qui a bougé a bougé pour la raison qu'on croit.** Le §5.1
donne la réponse, et elle était déjà dans le dépôt.

---

## 1. Périmètre

**Dans `L3`** : la valeur `coreThrottle` de l'`AscentProfile` du Falcon Heavy ; la poussée annoncée
au décollage, qui cesse d'être la somme des poussées installées ; le re-baseline des épinglages que
cela déplace, et la reprise des fixtures que l'étranglement empêche de voler telles quelles.

**Hors `L3`** : l'Ariane 64 (`L4`), les hauteurs (`L5`), les charges utiles (`L6`). Et deux choses
que la source du §2.1 permettrait de corriger et que ce lot laisse délibérément en place — voir
§3.6, qui est le point où « attribuable à une cause unique » se paie.

---

## 2. Les mesures qui décident

### 2.1 La source existe, et elle ne fixe pas `f` plus serré que le découpage

La question ouverte n° 3 du découpage — *« la durée réelle de combustion du corps central du Falcon
(~187 s), dont `f` se déduit »* — se ferme sur la chronologie publiée du vol inaugural
([Spaceflight Now, 6 février 2018](https://spaceflightnow.com/2018/02/06/launch-timeline-for-falcon-heavys-maiden-flight/)) :

| Événement | T+ |
|---|---|
| Extinction des propulseurs latéraux (BECO) | 2 min 29 s — **149 s** |
| Séparation des propulseurs | 2 min 33 s — **153 s** |
| MECO du corps central | 3 min 04 s — **184 s** |
| Séparation d'étages | 3 min 07 s — 187 s |

**Le découpage lit 187 s au mauvais événement.** 187 s est la séparation d'étages ; le MECO du corps
est à 184 s. L'écart est de trois secondes et ne change pas la conclusion, mais il change ce qu'on
peut prétendre en déduire.

Car le modèle brûle ses propulseurs en **157,0 s** — huit de plus que le BECO réel. Cet écart vient
des ergols et de la poussée du catalogue, c'est-à-dire du proxy Isp que `DT-13` porte ; ce n'est pas
un choix de `L3`. Selon la paire d'événements sur laquelle on ancre, `f` prend trois valeurs :

| Ancrage | Phase corps-seul | `f` |
|---|---|---|
| Combustion totale du corps = 184 s | 27,0 s | **0,828** |
| Corps-seul = MECO − séparation des propulseurs = 31 s | 31,0 s | **0,803** |
| Corps-seul = MECO − BECO = 35 s | 35,0 s | **0,777** |

`0,81` est à l'intérieur des trois. **La source confirme le chiffre à la précision qu'elle supporte
et ne l'affine pas** : elle ne départage pas trois lectures qui s'écartent de 0,05, sur un véhicule
dont le profil varie d'une mission à l'autre et dont le vrai corps central est étranglé en fonction
du temps, pas à fraction constante (hors périmètre, découpage §1.5).

### 2.2 Tout Falcon Heavy du dépôt est aujourd'hui un bloc groupé

`coreLeft = charge[corps] − charge[propulseurs] · (débit corps étranglé / débit propulseurs)`, soit
`charge[corps] − 0,5 · charge[propulseurs]` à `f = 1`. Les trois formes de charge qui existent dans
le dépôt donnent **exactement zéro** :

- `fullyLoaded` — 411 000 − 0,5 × 822 000 ;
- les charges écrites à la main `{400 000, 200 000, 100 000}` — 200 000 − 0,5 × 400 000 ;
- toute charge budgétée, les étages bas volant pleins (`02-baseline-L0.md` §3, point 1).

**Aucune ascension du dépôt n'a jamais volé les cinq phases** en dehors de la fixture étranglée de
`ParallelBlockAscentTest`. C'est pourquoi `L2` a pu être iso-trajectoire, et c'est ce que `L3`
renverse d'un coup, sur tous les profils Falcon à la fois.

### 2.3 Les charges budgétées ne bougeront pas, et c'est démontrable plutôt qu'à mesurer

`PropellantBudget` **ne lit jamais `thrust()`** — aucune occurrence dans le fichier. Et l'étage
équivalent que `StagingPlan.foldParallelBlock` lui présente porte une Isp `ΣF / Σ(F/Isp)` qui vaut
**296 s exactement pour tout `f`**, les deux entrées du bloc déclarant la même Isp : le facteur `f`
multiplie la poussée et le débit du corps de la même façon, et sort du quotient.

L'étage replié à `f = 0,81` ne diffère donc de celui à `f = 1` que par son champ de poussée, que le
budget ne lit pas. **`L3` déplace des trajectoires, pas des charges**, et
`PropellantBudgetParallelBlockTest` reste vert sans être touché.

### 2.4 Des quatre épinglages, un est immunisé, trois bougent, et l'un d'eux doit grandir

| Épinglage | Sort |
|---|---|
| `EarthOrbitNonRegressionTest` (4) | **Immunisé.** Ses quatre tests sont des A/B dans un même run — chaîne composée contre constructeur direct, Δ = 0 — donc les deux côtés bougent ensemble. Pas touché. |
| `CentralBodyBaselineTest` (4) | Trois profils Falcon bougent ; le MEO est une Ariane 62 et ne bouge pas. **42 frontières sur 64**, qui deviennent **54**. |
| `MissionPolylineBaselineTest` (1) | 8 sommets épinglés qui deviennent 10, plus `RAW_POINTS` et `TRAIL_SIZE`. |
| `AscentBaselineN2Test` (2) | Deux cellules Falcon, tolérances sur un CMA-ES rejoué : à ré-enregistrer. |

Les listes de `CentralBodyBaselineTest` ne se re-mesurent pas seulement, elles **gagnent deux
lignes** : `assertPinned` commence par *« the chain gained or lost a stage »*, et l'ascension passe
de trois phases à cinq. Six listes, deux lignes chacune.

| Profil | Lanceur | Frontières | Bloc / corps-seul |
|---|---|---|---|
| LEO-400 | Falcon, charges à la main | 12 → 16 | 76,4 s / 14,5 s |
| GEO | Falcon, pleine charge | 22 → 26 | 157,0 s / 29,8 s |
| Polaire | Falcon, pleine charge | 8 → 12 | 157,0 s / 29,8 s |
| MEO | **Ariane 62** | 22, immobiles | — |

### 2.5 Une classe ne peut plus voler du tout

`GravityTurnManeuver.configure` **lève** sur `plan.hasCorePhase()` — le refus que `L1` §4 a écrit
délibérément plutôt que de dupliquer la chaîne à cinq phases dans un second endroit.

Deux précisions que `L1` ne fait pas. D'abord, le refus est **plus étroit que sa propre prose** :
`L1` §4 parle d'« une pile déclarant un bloc parallèle », alors que le code ne refuse que le bloc
**éclaté**. C'est exactement pourquoi rien n'est rouge aujourd'hui. Ensuite,
`GravityTurnReplayConsistencyTest` est le **seul** usage de ce chemin dans tout le dépôt — quatre
sites — et il vole le profil GEO du catalogue. Trois de ses sept tests lèveraient, dont
`gravityTurnExit_matchesTheRecordedPreSplitBaseline`, qui est la référence numérique étape 0 du
chantier de l'étagement explicite, sans rapport avec `PHY-8`.

À noter au passage, sans en faire une décision : le Javadoc de `GravityTurnManeuver` justifie de
garder ce chemin par *« étape 5 still has a behaviour change to measure from that reference »*.
`docs/mission-stages/` n'existe plus — les documents de conception sont éphémères — et la mémoire du
chantier dit que l'étape 5 a été mesurée puis annulée. La justification écrite est donc
probablement périmée ; ce n'est pas ce qui fonde le §3.2.

### 2.6 Le gate iso-trajectoire de `L1` et `L2` ne se construit plus

`ParallelBlockAscentTest.AGGREGATED` emprunte `Launchers.FALCON_HEAVY.ascentProfile()`. À `f < 1`
sur un lanceur dont le seul étage bas porte le rôle `CORE`, `StagingPlan.checkStructure` le refuse à
la construction : un étranglement que rien ne lirait. Et trois de ses tests affirment le largage
groupé, les trois phases historiques et l'égalité au bit **sur le catalogue**, ce qui devient faux
par construction.

### 2.7 Baisser `f` ne peut casser aucune fixture

Le coefficient de `coreLeft` passe de `0,5` à `0,405`. Baisser `f` **augmente** `coreLeft`, donc le
refus « le corps s'assèche avant les propulseurs » ne peut que s'éloigner. Aucun tableau de charges
existant ne peut devenir illégal, et la propriété est monotone : elle vaudra encore pour toute
valeur de `f` qu'un lot ultérieur voudrait essayer.

### 2.8 La carte du wizard annoncerait 1,4 MN de trop

`LauncherModel.liftOffThrust()` somme les étages allumés au sol à pleine poussée — la règle que `L2`
§3.4 a retenue parce qu'elle survit à `L4`. À `f = 1` cette somme *était* la poussée au décollage. À
`f = 0,81` le bloc produit `15,2 + 0,81 × 7,6 = 21,4 MN` et la carte annoncerait toujours 22,8. Le
champ s'appelle « Lift-off thrust » : son nom porte une affirmation sur un instant de vol.

Le T/W au décollage suit, et reste partout au-dessus de 1 :

| Profil | T/W à `f = 1` | T/W à `f = 0,81` |
|---|---|---|
| FH GEO, pleine charge | 1,644 | **1,540** |
| FH LEO 400, charge utile 10 t | 1,768 | **1,656** |
| FH LEO-400 `LEGACY`, charges à la main | 3,019 | **2,827** |

---

## 3. Décisions de conception

### 3.1 `f = 0,81`, et la source confirme sans affiner

**Décision : `coreThrottle = 0,81` sur l'`AscentProfile` du Falcon Heavy**, la valeur que le
découpage écrit. Le §2.1 la trouve à l'intérieur des trois lectures possibles de la source, et
au-dessous de la précision qui les sépare.

Ce que cela produit, sans qu'aucun de ces nombres soit écrit dans le code : le bloc brûle
**157,0 s**, il reste **78 090 kg** au corps, sa phase seule dure **29,8 s** et il vole **186,8 s**
en tout.

Le refus qui va avec : ne pas ancrer `f` sur la combustion totale. Cet ancrage donnerait 0,828 et
une phase corps-seul de 27 s contre 31 réelles — il ferait absorber par `f` les huit secondes
d'excédent des propulseurs, qui sont une dette d'Isp et appartiennent à `PHY-2`. **Une erreur connue
sur une grandeur ne doit pas être silencieusement compensée par une autre.**

### 3.2 Le chemin mono-propagateur garde un Falcon à pleine poussée

**Décision : `GravityTurnReplayConsistencyTest` se donne son propre `AscentProfile` à `f = 1`**,
bâti sur les étages du catalogue. Aucun changement de `src/main`, et ses constantes `REF_*` restent
valides au bit.

Ce n'est pas un contournement, et la nuance est ce qui rend la décision défendable : cette classe
est la référence numérique étape 0 d'un **autre** chantier, enregistrée sur un Falcon ni éclaté ni
étranglé. Geler son véhicule, c'est ce que cette référence *est*. La retirer parce que `PHY-8` a
rendu le catalogue inexprimable par le propagateur unique reviendrait à fermer d'autorité un
chantier voisin depuis celui-ci.

**Refusé : enseigner la phase corps-seul au propagateur unique.** `L1` §4 l'a déjà refusé, pour la
raison qui n'a pas bougé — ce serait la chaîne à cinq phases écrite une seconde fois, et c'est
précisément la duplication qu'`AscentChainPropagation` existe pour empêcher.

### 3.3 Le gate iso-trajectoire est retourné, et gagne le vol jumeau

**Décision : `ParallelBlockAscentTest` change de côté une seconde fois.**
`throttledFalconHeavy(0,81)` disparaît — le catalogue *est* étranglé — et son miroir `fullThrust()`
apparaît, bâti de la même façon sur les étages du catalogue. `AGGREGATED` reçoit ce profil à pleine
poussée.

La preuve au bit que l'éclatement est inerte survit donc à `L3` comme elle a survécu à `L2`. Elle le
mérite : l'arithmétique du bloc — agrégation des poussées, `Isp_eff`, plancher d'extinction, prorata
de consommation — reste vivante après ce lot, et sera exercée autrement par l'Ariane 64, avec quatre
propulseurs et deux Isp qui ne s'annuleront pas dans le quotient. Une preuve d'inertie sur le seul
cas où l'inertie est vérifiable **au bit** vaut tant que ce code vit.

Et la classe gagne ce qui manquait à `L3` : **le vol jumeau**. Même pile, mêmes charges, mêmes
variables fixes, volée à `f = 1` puis à `f = 0,81` dans un même run, écart au MECO rapporté. C'est
l'instrument qui rend le re-baseline attribuable au lieu de le déclarer tel.

### 3.4 La carte annonce la poussée étranglée

**Décision : `liftOffThrust()` multiplie la poussée du corps par `coreThrottle` quand la pile
déclare un bloc parallèle.** La carte annonce 21,4 MN.

La règle de `L2` — somme des étages allumés au sol — survit intacte : elle gagne un facteur qui vaut
1 partout ailleurs, y compris sur l'Ariane 64 de `L4`, qui ne s'étrangle pas. L'alternative
consistait à annoncer la poussée *installée*, un chiffre de catalogue comparable d'un lanceur à
l'autre ; elle a été écartée parce que le nom du champ ne dit pas cela, et parce que c'est le seul
chiffre de la carte qu'un lecteur peut recouper avec la trajectoire — le T/W au décollage, que
`PHY-8` vient précisément de rendre vérifiable.

### 3.5 Les trois profils Falcon de `CentralBodyBaselineTest` sont re-épinglés

**Décision : re-mesurer, sur le véhicule étranglé.** 42 frontières régénérées, qui deviennent 54.

L'alternative avait un précédent maison, et c'est le profil polaire de ce test qui le porte : quand
`BUG-6` s'est refermé le 2026-08-31 et que sa trajectoire est redevenue physique, il n'a
délibérément *pas* suivi — *« Re-pinning would cost a fresh measurement, buy no guard strength, and
break the continuity with the L1 reference. »*

Ce précédent ne se transpose pas. Le polaire a été gelé parce que le re-épingler n'achetait rien :
ce qu'il garde est l'arithmétique de vingt sites de construction de propagateur, et une trajectoire
figée le garde qu'elle soit volable ou non. Ici le re-épinglage achète exactement ce que `L3` doit
au chantier — **un point fixe à tolérance zéro sur le véhicule étranglé**. Geler les trois profils
laisserait l'éclatement gardé au bit et l'étranglement gardé par rien.

### 3.6 Ce que `L3` ne change pas, et qui est maintenant sourcé

La chronologie du §2.1 met deux autres écarts sous les yeux, et le lot les laisse tous les deux :

- le vrai vol sépare ses propulseurs **4 s** après le BECO ; le coast de séparation de `L1` vaut
  **1 ms** ;
- il sépare ses étages **3 s** après le MECO ; le coast interétage du catalogue vaut **2 s**.

Les corriger ferait entrer deux causes de plus dans le même re-baseline, et « attribuable à une
cause unique » cesserait de vouloir dire quelque chose. Le 1 ms reste par ailleurs un choix motivé
de `L1` — une temporisation de tassement, dont la perte mesurée est de l'ordre de 0,01 m/s — et non
une approximation de la séparation réelle.

---

## 4. Ce que le lot touche

**`src/main`, deux fichiers.**

| Fichier | Ce qui change |
|---|---|
| `vehicle/catalog/Launchers` | `coreThrottle = 0,81` sur l'`AscentProfile` du Falcon Heavy (§3.1) |
| `vehicle/model/LauncherModel` | `liftOffThrust()` applique l'étranglement au corps (§3.4) |

**`src/test`, sept fichiers au moins** — la liste est celle que la conception prévoit ; le vol dira
s'il en manque.

| Fichier | Ce qui change |
|---|---|
| `maneuver/GravityTurnReplayConsistencyTest` | fixture à `f = 1` (§3.2) |
| `operation/ParallelBlockAscentTest` | retourné, plus le vol jumeau (§3.3) |
| `operation/CentralBodyBaselineTest` | 42 frontières re-mesurées, 54 après (§3.5) |
| `ephemeris/MissionPolylineBaselineTest` | 10 sommets, `RAW_POINTS`, `TRAIL_SIZE` |
| `optimizer/AscentBaselineN2Test` | deux cellules ré-enregistrées |
| `operation/MissionAscentWiringTest` | trois ou cinq phases selon le plan d'étagement (§7.5) |
| `vehicle/LaunchersTest` | poussée au décollage à 21 356 000 N, étranglement déclaré |
| `operation/LunarOrbitMissionTest` | la chaîne lunaire compte quatorze étapes (§7.9) |

---

## 5. Ce qui ferme le lot

### 5.1 Le témoin était déjà dans le test

`CentralBodyBaselineTest` vole quatre profils, et **le MEO est une Ariane 62**. Ses 22 frontières
doivent rester identiques **au chiffre près** pendant que les 43 autres bougent.

L'attribution à une cause unique n'est donc pas une affirmation de ce document : c'est une assertion
à tolérance zéro, dans le même run, sur le même code, à la même seconde. Un lot qui aurait déplacé
autre chose que l'étranglement — un ordre d'opérations flottantes, une frontière de phase, une
construction de propagateur — le montrerait sur l'Ariane, qui n'a pas de bloc parallèle et n'a rien
à changer.

`EarthOrbitNonRegressionTest`, immunisé pour la raison inverse (§2.4), joue le même rôle du côté du
câblage : ses quatre tests restent verts sans qu'on y touche, ou bien `L3` a cassé une chaîne.

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew gateTest
```

**Mesuré, 3 min 18 s :**

| Épinglage | Résultat |
|---|---|
| `EarthOrbitNonRegressionTest` | `tests=4 skipped=0 failures=0 errors=0` |
| `MissionPolylineBaselineTest` | `tests=1 skipped=0 failures=0 errors=0` |
| `CentralBodyBaselineTest` | `tests=4 skipped=0 failures=0 errors=0` |
| `AscentBaselineN2Test` | `tests=2 skipped=0 failures=0 errors=0` |

**Et le témoin a tenu au caractère près.** Les blocs `MEO_REPLAY` et `MEO_STANDALONE` du fichier
re-mesuré sont **identiques octet pour octet** à ceux de `HEAD` — vérifié par diff, pas par
lecture. Vingt-deux frontières de huit doubles chacune n'ont pas bougé d'un chiffre pendant que
quarante-deux bougeaient toutes, dans le même run et sur le même code.

`EarthOrbitNonRegressionTest` est resté vert **sans être touché**, comme le §2.4 le prévoyait.

**Suite complète** : 1 406 tests, 30 sautés, 0 échec, 0 erreur sur 205 classes — contre 1 404 sur
205 à la clôture de `L2`. `pmdMain`, `pmdTest` et `spotlessCheck` passent.

### 5.2 Le vol jumeau

`ParallelBlockAscentTest` vole la même pile à `f = 1` et à `f = 0,81` et rapporte l'écart au MECO.
Aucun littéral épinglé des deux côtés : la comparaison survivra à `L4` sans être touchée, comme
celle de `L2`.

```
Twin flight, f = 1 vs f = 0.81, same MECO at t+431.8 s: block burn 157.0 s unchanged,
core-only 29.8 s, Δpos 96541.4 m, Δvel 458.053 m/s, Δmass -8574.5 kg
```

Les deux assertions tiennent : les propulseurs s'éteignent au même instant des deux côtés à 1 µs
près, et la phase corps-seul vaut `burn1 · (1 − f)` à 0,05 s près. La masse est **plus grande** de
8,6 t du côté étranglé, et ce n'est pas un gain : au même MECO, l'étage supérieur a brûlé 29,8 s
de moins parce que l'étagement s'est terminé plus tard. Le gain réel se lit au §7.11, sur les deux
profils que l'optimiseur re-converge.

Et la preuve d'iso-trajectoire de `L1` et `L2` est toujours exactement nulle :

```
Catalog (split) vs aggregated Falcon Heavy at MECO: Δpos 0.000e+00 m, Δvel 0.000e+00 m/s, Δmass 0.000e+00 kg
```

### 5.3 Les contrôles physiques, par profil

Le découpage annonce « combustion du corps ~187 s, phase corps-seul ~30 s, propulseurs inchangés à
157,0 s ». **Ces trois chiffres supposent un S1 plein**, et ne valent donc pas sur les deux
épinglages les plus stricts, qui volent les charges écrites à la main — l'encadré du §3 de
`02-baseline-L0.md` l'avait déjà signalé pour le 157,0 s.

| Profil | Bloc | Corps-seul | Corps au total |
|---|---|---|---|
| Charges à la main `{400 000, 200 000, 100 000}` | 76,4 s | 14,5 s | 90,9 s |
| Pleine charge, et toute charge budgétée | 157,0 s | 29,8 s | 186,8 s |

Le 76,4 s n'est pas une prédiction : c'est déjà le sommet `S1 separation` épinglé à
`t = 76,39164210526314` dans `MissionPolylineBaselineTest`. Il ne doit pas bouger — l'étranglement
ne touche pas les propulseurs.

### 5.4 Les six cellules de `L0`

`BoosterSplitBaselineTest` relancé et ses rapports diffés contre `build/baseline/phy8/`, comme `L0`
l'a conçu. C'est là que les profils budgétés — donc le 157,0 / 29,8 / 186,8 — se lisent, et là que
le §6 se vérifie. Dix minutes derrière `orbitlab.slowTests`, hors de la boucle courte.

---

## 6. Le risque que la conception ne peut pas lever

Les tests d'insertion à tolérance physique volent tous le Falcon : LEO 400 à ±7 %, GEO, MEO, les
fenêtres lunaires. L'étranglement échange de la **perte gravitationnelle tôt** — 21,4 MN au lieu de
22,8 pendant 157 s — contre un **meilleur étagement tard** : 44 t de masse sèche larguées à 157 s au
lieu de 66, le corps volant ses trente dernières secondes sans porter les propulseurs vides. Le
total d'impulsion est inchangé ; le sens dans lequel penche la trajectoire, non.

Aucune forme fermée ne le donne, et la conception ne le devine pas. **Si une insertion sort de son
enveloppe, c'est un résultat du lot et non un test à faire taire** : ré-enregistrer une référence
mesurée après un changement assumé est légitime, élargir une tolérance pour faire passer un test ne
l'est pas.

---

## 7. Ce que la conception corrige au découpage

1. **Le MECO du corps est à 184 s, pas 187.** Le découpage lit 187 s à la séparation d'étages. La
   conclusion survit : `f = 0,81` est à l'intérieur des trois ancrages possibles, qui s'écartent de
   0,05 (§2.1).
2. **Les contrôles physiques de `L3` ne valent pas sur les gates que `L3` re-baseline.** Ils
   supposent un S1 plein ; les deux épinglages à tolérance zéro volent 76,4 / 14,5 / 90,9 (§5.3).
3. **Le prorata n'est pas la seule chose que `L3` ne recalcule pas : les charges budgétées ne
   bougent pas du tout**, `Isp_eff` valant 296 s exactement pour tout `f` (§2.3). Le lot déplace des
   trajectoires, pas des charges.
4. **Le découpage ne prévoit pas que `L3` est le lot qui déclenche le refus écrit par `L1` §4.**
   Aucun des deux documents ne pouvait le voir : `L1` a écrit le refus sans savoir quel lot le
   ferait mordre, et `L2` est resté groupé (§2.5).
5. **`MissionAscentWiringTest` porte une propriété que `L3` rend fausse.** Son commentaire dit
   *« the three-phase ascent is a property of every mission, not a shape Falcon Heavy's figures
   happen to produce »*, et une Ariane 62 y a été ajoutée exprès pour tenir cette propriété qu'un
   seul lanceur ne pouvait pas tenir. Après `L3`, la forme de l'ascension **est** une propriété des
   chiffres du lanceur : trois phases pour l'Ariane, cinq pour le Falcon. Le test devient « chaque
   profil vole l'ascension que son plan d'étagement déclare », ce qui est plus vrai et moins fort.
6. **Le profil polaire de `CentralBodyBaselineTest` vole un Falcon pleine charge, pas une Ariane.**
   `L3` re-épingle donc trois profils sur quatre, dont celui dont le Javadoc plaidait pour le gel
   (§3.5) — et le témoin du §5.1 est le MEO, qui est l'Ariane.

**Ce que l'implémentation a ajouté.**

7. **Le §2.4 comptait une frontière de trop.** 64 épinglées et non 65 : `POLAR_STANDALONE` en
   porte quatre, comme `POLAR_REPLAY`. Le compte est donc 42 qui deviennent 54.
8. **`ascentThenPlaneTrim` sélectionnait sa chaîne par index.** `stages.subList(1, 4)` prenait
   « les trois phases d’ascension » ; avec cinq phases il a pris le premier brûlage, la
   séparation des propulseurs et le brûlage corps, puis **a sauté la séparation du corps et tout
   le second brûlage** pour enchaîner le plane trim depuis la fin de la phase corps-seul. Il n’a
   rien levé : il a rendu quatre frontières là où six étaient dues. C’est la forme de défaut que
   `L2` avait déjà payée — un index de position là où une identité était voulue — et la
   correction est la même : sélectionner jusqu’au second brûlage par son type.
9. **`LunarOrbitMissionTest` indexait de la même façon**, et il est le seul du dépôt à l’avoir
   fait hors de la liste du §4 : douze étapes devenues quatorze, et les cinq phases lunaires lues
   aux indices absolus 6 à 10. Comptées depuis la fin, elles deviennent insensibles à la longueur
   du préfixe d’ascension, qui est l’affaire du lanceur.
10. **Le profil GEO a dû changer de variables, et ce n’était pas un choix.** Aux littéraux
    d’avant `L3` (`329,124209 / 0,177424`), la passe STANDALONE lève *« No apogee found within
    one transfer half-period »* à l’injection GTO : l’ascension étranglée lui remet un état d’où
    la chaîne analytique ne sait pas planifier, et une passe qui lève ne s’épingle pas du tout.
    Fait notable, la passe REPLAY a volé ses treize frontières aux **anciennes** variables — le
    défaut n’était visible que d’un côté. Le remplacement est mesuré et non choisi : c’est
    l’optimum qu’`AscentBaselineN2Test` a ré-enregistré sur ce même véhicule et cette même cible
    dans ce lot, `343,538381 / 0,193846`.
11. **Ce que l’étranglement rapporte, mesuré — le §6 tranché du bon côté.** Les deux profils que
    l’optimiseur re-converge gagnent de la masse au MECO sans déplacer leur orbite finale :

    | Profil | Masse au MECO | Périgée final |
    |---|---|---|
    | LEO 400 | 36 368,1 → **38 276,7 kg** (+1 908,6) | 400 314,5 → 400 311,6 m |
    | GEO | 64 579,9 → **69 010,9 kg** (+4 431,0) | 35 786 247,8 → 35 786 249,2 m |

    Le périgée bouge de 2,9 m et de 1,4 m — quatre ordres de grandeur à l’intérieur de la
    tolérance relative. L’échange que le §6 décrit penche donc du côté de l’étagement : ne plus
    porter 44 t de propulseur vide pendant les trente dernières secondes du premier étage vaut
    plus que les 1,4 MN abandonnés au décollage. Le rapport de vol chiffre la phase corps-seul à
    **764 m/s** (LEO) et **1 306 m/s** (GEO).
12. **Une non-reproductibilité du profil GEO, à surveiller et non attribuée.** Trois exécutions
    du gate ré-enregistré ont rendu le candidat épinglé **deux fois**, et une fois un candidat
    distant de 0,259510 s — dont les 74,6 kg d’écart de masse valent exactement
    `0,259510 × 287,46 kg/s`, le débit de l’étage supérieur. La cause est structurelle et
    antérieure au lot : l’exploration CMA-ES est parallèle et les runs se coupent mutuellement par
    un signal partagé, donc le candidat retenu dépend d’une course. Une évaluation à cinq phases
    est plus lente, ce qui change cette course — mais **je n’ai pas relancé le gate d’avant `L3`
    plusieurs fois**, donc je ne peux pas dire que le lot a créé ce risque. Ce qui est certain :
    la tolérance de 1 kg sur la masse ne le couvre pas, et **elle n’a pas été élargie**.

---

## 8. Ce que `L3` lègue

**À `L4`** : un catalogue où l'étranglement est une valeur vécue et non un champ mort, et la preuve
qu'une chaîne à cinq phases traverse les quatre profils de `CentralBodyBaselineTest`, les deux
cellules d'`AscentBaselineN2Test` et la polyligne dessinée. L'Ariane 64 n'aura pas à découvrir cela
en même temps que ses quatre propulseurs et ses deux Isp.

**À `PHY-2`** : les huit secondes d'excédent des propulseurs, isolées et nommées (§3.1). C'est le
proxy Isp vu sous un autre angle que `g₀·ΔIsp·ln R`, et sur une grandeur — une durée de combustion —
que la source externe donne directement.

**Au chantier de l'étagement explicite** : la constatation que son propagateur unique ne peut plus
exprimer le véhicule du catalogue, et que sa justification écrite est probablement périmée (§2.5).
`L3` ne tranche pas ; il consigne, et gèle le véhicule de la fixture pour ne pas trancher par
accident.
