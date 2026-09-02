# BUG-3 — Orientation des modèles 3D des planètes : découpage

Fiche d'origine : [`BUG-3`](../bugs.md#bug-3--orientation-des-modèles-3d-des-planètes) (constaté le
2026-08-15, « Ouvert, diagnostic à faire »). Ce document **referme ce diagnostic** — la fiche
demandait la liste « corps → défaut (a) axe faux / (b) longitude fausse », elle est au §2.6 — puis
**découpe** le chantier qui en découle. Il ne conçoit pas : noms de classes, signatures et formules
viendront dans les `02-…` et suivants, lot par lot.

> **Statut : les cinq lots sont implémentés (2026-09-02).** Les mesures des §2 et §3 sont fermes et
> reproductibles (outil décrit au L0). Reste la **validation corps par corps à l'écran**, que
> l'auteur du projet fait lui-même avec l'instrument du L2 : c'est elle qui arrête les `λ0` du L3.
> Les sections des lots portent, en fin de section, ce que l'implémentation a mesuré — y compris
> deux énoncés de ce document qu'elle dément.

> **Deux contraintes posées par l'auteur du projet le 2026-09-01, qui cadrent tout le reste :**
>
> 1. **La Terre et la Lune sont correctes, et font référence.** C'est exactement l'observation que
>    le §2.6 attendait — la Lune étant en rotation synchrone, sa face visible atteste la longitude,
>    et la Terre l'axe. Le repère canonique n'est donc plus une convention à choisir : c'est
>    **celui que ces deux modèles portent déjà**, pôle `+Z`, `u=0` sur `−X`, correction identité.
> 2. **Tous les autres maillages sont susceptibles de changer.** Neuf corps sur onze sont des
>    assets provisoires. Les valeurs qui les concernent sont donc transitoires par nature, et
>    l'investissement va à l'instrument, pas aux constantes — voir §7.

> **Les mesures portent sur l'arbre de travail au 2026-09-01, pas sur `HEAD`.** Quatre corps y sont
> en cours de remplacement — `jupiter`, `mars` (renommé `scene.*` → `mars.*`), `uranus`, `venus`,
> plus dix textures neuves. Les chiffres ci-dessous décrivent les nouveaux assets.

---

## 1. Périmètre

**Dans le chantier :**

- l'orientation des onze maillages planétaires — quel axe du modèle est le pôle, quelle longitude
  corps-fixe porte la colonne `u=0` de sa texture ;
- le fait que, pour cinq corps, la couche **visible** ne tourne pas à la vitesse que le repère IAU
  d'Orekit fait tourner (§3) ;
- l'instrument qui permet de caler et de re-caler sans comparer des captures d'écran.

**Hors chantier**, et à ne pas y laisser glisser — chacun est un constat mesuré au cours du
diagnostic, listé au §8 pour ne pas être perdu :

- la **phase et le terminateur** (falloff `0.8`, ambiante à `0.03`) — c'est de l'éclairage, pas de
  l'orientation, et c'est ce qui a fait croire à un défaut de rotation au départ ;
- l'**aplatissement** des maillages — géométrie, pas repère ;
- l'**atmosphère de Vénus rendue opaque** par la re-matérialisation ;
- la **lune Miranda cuite dans l'asset d'Uranus** ;
- la transparence des anneaux.

---

## 2. Ce que la mesure établit

### 2.1 Une sphère UV équirectangulaire porte sa propre référence

Un maillage de ce type contient, dans ses seuls attributs `POSITION` et `TEXCOORD_0`, de quoi
retrouver son repère sans aucune image de référence et sans réglage à l'œil :

- le **pôle** est la direction moyenne des sommets à `v = 0` (en glTF, `v = 0` est la rangée du
  haut de l'image ; une carte planétaire met le nord en haut) ;
- le **méridien de la colonne 0** est la direction moyenne des sommets à `u = 0, v = 0,5` ;
- le **sens** se lit sur la pente de l'azimut autour du pôle en fonction de `u` ;
- la **validité** de tout ce qui précède se lit sur le résidu de l'ajustement latitude ↔ `v` : à
  0,0°, la carte est équirectangulaire exacte ; au-delà, elle ne l'est pas et rien n'est
  exploitable.

Les directions sont composées le long de la chaîne de nœuds glTF, donc exprimées dans le repère que
JME voit réellement, pas dans celui du maillage brut.

### 2.2 Relevé des onze modèles

| corps | maillage | pôle | dir. `u=0` | résidu lat/`v` | sens de `u` | correction actuelle |
|---|---|---|---|---|---|---|
| earth | sphère | +Z | −X | 0,0° | −360°/u | identité |
| mars | sphère | +Z | −X | 0,0° | −360°/u | identité |
| moon | sphère | +Z | −X | 0,0° | −360°/u | identité |
| saturn | sphère | +Z | −X | 0,0° | −360°/u | identité |
| **jupiter** | sphère | +Z | **−Y** | 0,0° | −360°/u | identité |
| sun | sphère | −Y | −Z | 0,0° | −360°/u | +90° /Y |
| neptune | sphère | −Y | −Z (à 4,5°) | 0,0° | −360°/u | +90° /Y |
| **pluto** | sphère | −Y | **−X** | 0,3° | −360°/u | +90° /Y |
| **venus** | sol + atmosphère | −Y | **−X** | 0,0° | −360°/u | **identité** |
| mercury | sphère | −Y à 22° près | obliques | 0,9° | −362,1°/u | +90° /Y |
| **uranus** | globe (`0`) | — | — | **49,3°** | non linéaire | identité |
| saturn | anneau | — | — | 40,6° | non linéaire | (hérite) |
| uranus | anneau, miranda | — | — | — | — | (hérite) |

Le résidu rejette de lui-même les géométries qui ne sont pas des cartes lat/long : les deux anneaux,
et le globe d'Uranus lui-même — dont la texture est d'ailleurs carrée (2048×2048) et non 2:1.

### 2.3 Deux familles de convention, et un invariant qui ne tient pas

Cinq modèles ont leur pôle sur **+Z** (earth, jupiter, mars, moon, saturn), cinq sur **−Y** (sun,
neptune, pluto, venus, mercury à 22° près). Or la chaîne applique à tous la même
`RenderTransform.meshCorrectionQ` (+90° autour de X). **Les deux familles ne peuvent pas être
justes en même temps** : cette rotation envoie +Z sur −Y et −Y sur −Z, deux résultats différents.
C'est un constat indépendant de toute convention de signe.

Conséquence directe sur le fichier existant.
[`PlanetMeshCorrection`](../../src/main/java/com/smousseur/orbitlab/engine/scene/PlanetMeshCorrection.java)
documente son invariant : « le pôle est l'axe *up* du modèle, donc un décalage de longitude est une
rotation autour de l'axe *up* seul ». **Cet invariant ne tient que pour la famille −Y.** Pour
earth, jupiter, mars, moon et saturn, une rotation autour de `UNIT_Y` *bascule le pôle de 90°* au
lieu de décaler la longitude. Les quatre valeurs non identité d'aujourd'hui portent toutes sur des
corps de la famille −Y, donc le fichier fonctionne par coïncidence — mais la prochaine personne qui
corrige Jupiter en recopiant la ligne de Mercure couche la planète.

### 2.4 Deux signatures à 90°, dont celle qui a déclenché le diagnostic

Indépendantes de toute convention de signe, puisqu'elles ne comparent que des corps entre eux :

- **Jupiter** a son `u=0` à 90° autour du pôle de earth/mars/moon/saturn, et porte pourtant la même
  correction qu'eux (identité). C'est le symptôme constaté à l'écran sur la Grande Tache Rouge.
- **Pluton et Vénus ont le même repère à 0,001 près** (pôle −Y, `u=0` sur −X) et reçoivent des
  corrections **différentes** (+90°/Y contre identité). L'une des deux est nécessairement fausse de
  90°. Et comme Pluton est à 90° de sun/neptune tout en portant leur correction, c'est Pluton qui
  sort du rang.

### 2.5 Le sens est uniforme — un seul signe global à vérifier

Une texture en miroir ne se rattrape par aucune rotation : il faut inverser une coordonnée UV. Le
risque existait donc onze fois. Mesuré : **les onze maillages sphériques ont exactement la même
chiralité**, −360°/u autour du pôle, linéaire (Mercure à −362,1°/u, cohérent avec son résidu de
0,9°). Aucun asset n'est miroir des autres.

Le sens n'est donc pas onze inconnues mais **une seule** : la famille entière correspond-elle à la
longitude est du repère corps d'Orekit, ou à son opposée ? **Tranché** — la Terre et la Lune sont
correctes, et toutes deux passent par cette chaîne ; le signe est donc juste, et comme la chiralité
est commune aux onze, il l'est pour tous.

### 2.6 La liste demandée par BUG-3

La fiche demandait de trancher, corps par corps, entre **(a)** axe de rotation faux et **(b)** axe
juste, longitude fausse. La mesure la produit — et la fiche fournit elle-même l'observation qui
l'arbitre : « la Lune est en rotation synchrone ; si sa face visible n'est pas tournée vers la
Terre, l'offset de méridien est faux ».

La Lune est dans la famille +Z, avec une correction identité, exactement comme la Terre. **Les deux
sont déclarées correctes** (en-tête, contrainte 1) : la Terre atteste l'axe, la Lune atteste la
longitude, puisqu'en rotation synchrone une face visible juste ne peut pas coexister avec un
méridien décalé.

Le repère canonique est donc défini **empiriquement, par ces deux modèles** — pôle `+Z`, `u=0` sur
`−X`, correction identité — et non par une convention choisie sur le papier. Tout le reste s'en
déduit :

| corps | défaut | nature | statut |
|---|---|---|---|
| earth, moon | aucun | **référence** | observé |
| mars, saturn | aucun *a priori* | repère identique à la référence, à 0,001 près | déduit |
| **jupiter** | **(b)** | `u=0` sur −Y au lieu de −X : 90° autour du pôle | déduit |
| **sun, neptune, mercury** | **(a)** | pôle sur −Y au lieu de +Z | déduit |
| **pluto** | **(a) + (b)** | pôle −Y, *et* 90° d'écart avec sun/neptune à correction égale | déduit |
| **venus** | **(a)** | pôle −Y ; sa longitude est cohérente avec Pluton, pas sa correction | déduit |
| **uranus** | indécidable | carte non équirectangulaire, résidu 49,3° | mesuré |

Pour Jupiter, le terme dérivé est une rotation de 90° autour de `+Z` — `fromAngleAxis(-HALF_PI,
UNIT_Z)`, pas autour de `UNIT_Y` comme le javadoc actuel de `PlanetMeshCorrection` le laisserait
écrire (§2.3). Dérivé, donc à confirmer à l'écran une fois, pas deviné.

Deux contrôles indépendants restent utiles, cités par la fiche : le **plan des anneaux de Saturne**
et l'**orientation des bandes de Jupiter**, tous deux perpendiculaires à l'axe. Saturne étant à
repère identique à la référence, ses anneaux doivent déjà être justes — c'est le contrôle qui
valide la déduction « repère identique ⇒ correct » sur un corps que personne n'a observé.

---

## 3. La deuxième inconnue : la couche visible ne suit pas toujours `W`

Le repère appliqué vient de `icrf.getTransformTo(bodyFrame, t)`
([`ChunkComputerV1.java:156`](../../src/main/java/com/smousseur/orbitlab/tools/ephemerisgen/ChunkComputerV1.java)),
donc du modèle IAU d'Orekit. Relevé dans `PredefinedIAUPoles` (Orekit 13.1.1) et confronté à ce que
la texture représente réellement :

| corps | `W` d'Orekit | période | couche texturée | dérive dans le repère appliqué |
|---|---|---|---|---|
| Mercure | 6,1385025 °/j | 1407 h | surface | **0 — constante exacte, pour toujours** |
| Terre | 360,9856235 °/j | 23 h 56 | surface | 0 |
| Lune | 13,1763582 °/j | 655 h | surface | 0 |
| Mars | 350,8919823 °/j | 24 h 37 | surface | 0 |
| Pluton | 56,3625225 °/j | 153 h | surface | 0 |
| Vénus (sol) | −1,4813688 °/j | 5832 h | radar | 0 |
| **Vénus (nuages)** | −1,4813688 °/j | — | super-rotation ~4,2 j | **≈ −84 °/j** (facteur 58) |
| **Jupiter** | 870,5360000 °/j | 9 h 55 (Système III) | nuages Système II (870,270 °/j) | **−0,266 °/j** |
| **Saturne** | 810,7939024 °/j | 10 h 39 (Système III) | Système I équatorial (~10 h 14) | **≈ +33 °/j** |
| **Uranus** | −501,1600928 °/j | 17 h 14 | nuages, rotation différentielle | +40 à +115 °/j |
| **Neptune** | 536,3128492 °/j | 16 h 07 | équateur ~18 h, différentiel | ≈ −56 °/j |
| **Soleil** | Carrington | 25,4 j | photosphère différentielle | fonction de la latitude |

Deux conséquences.

**Une correction constante n'est juste qu'à une date, pour cinq corps.** Jupiter dérive de 97°/an et
fait un tour complet en 3,71 ans : caler la Grande Tache Rouge aujourd'hui la laisse à 100° de sa
place dans un an de temps simulé. Vénus est le cas extrême, pas Jupiter — et son asset porte déjà un
maillage `atmosphere` distinct du maillage `venus`, la séparation des deux vitesses y est donc
directement représentable.

**Pour trois corps, le calage est inobservable.** Uranus, Neptune et le Soleil n'ont aucun détail
identifiable dans leur texture. Y produire un chiffre serait produire un chiffre invérifiable : ils
reçoivent une constante conventionnelle, assumée comme telle.

---

## 4. Principe retenu

Quatre règles, décidées en amont et non renégociées lot par lot.

### 4.1 La sonde est un instrument, partagé par l'outil hors ligne et le démarrage

Le même code de mesure sert deux usages : un outil sous `tools/`, joué à la demande quand un asset
change, et un contrôle au chargement du modèle. Avec la convention du §4.2, l'outil n'est plus
seulement un producteur de valeurs mais un **contrôle de conformité** : sur un asset neuf il répond
« conforme » ou « voici la rotation manquante », et c'est l'auteur de l'échange qui choisit alors
entre la corriger dans Blender ou la porter en constante. C'est ce partage qui rend le chantier
robuste au remplacement des maillages — le motif même pour lequel cette forme a été retenue plutôt qu'une
dérivation au chargement sans constante commitée : **sur un asset qui change, ce qu'on veut n'est
pas l'absorption silencieuse, c'est la détection.** Uranus est la preuve qu'un modèle « plus
fidèle » peut arriver avec une carte UV illisible ; une sonde sans attente écrite l'orienterait
depuis un ajustement qui n'a pas de sens.

### 4.2 Conformité à l'export par défaut, deux termes en code comme filet

**La voie normale est la conformité de l'asset**, pas la constante. Neuf maillages sur onze vont
passer par Blender de toute façon (en-tête, contrainte 2) ; les y orienter coûte le temps d'une
rotation, et un asset conforme n'a besoin d'aucune valeur dans le code.

> **Convention d'export.** Une fois la chaîne de nœuds glTF composée : arête `v = 0` de la texture
> sur `+Z`, colonne `u = 0` pointant vers `−X`, résidu latitude/`v` nul, chiralité `−360°/u`. C'est
> ce que portent `earth.gltf` et `moon.gltf`.

> **Corrigé le 2026-09-02, en implémentant le L2.** Cet énoncé disait « pôle **nord** sur `+Z` ».
> C'est faux, et la sonde ne pouvait pas le savoir : mesuré à travers toute la chaîne, `+Z` est
> peint au pôle **sud** du corps. Les textures de référence sont stockées rangée sud en premier —
> établi sur la carte de la Terre elle-même, dont la bande à `v = 0,2` est à 96 % océanique et ne
> peut donc être que 54° **sud**. La convention d'export est inchangée dans les faits (les fichiers
> conformes le sont toujours), mais l'énoncé l'était. Conséquence pratique, elle : une carte stockée
> à l'endroit sur un maillage par ailleurs conforme donnerait un corps **en miroir**, et c'est un
> défaut que ni la sonde ni la garde ne peuvent voir — seul le L2 le montre, et c'est pourquoi son
> graticule étiquette ses deux pôles.

Deux précisions qui ne sont pas des détails :

- **Ce n'est pas la structure de la scène qui fait convention, c'est le repère composé.** La Terre
  tient en un nœud à rotation identité, la Lune en une chaîne Sketchfab de cinq nœuds
  (180°/X, 90°/X, −90°/X) ; elles atterrissent au même endroit. Personne n'a à reproduire une
  scène Blender particulière.
- **L'énoncé porte sur le `.gltf`, jamais sur la scène Blender.** La case « +Y Up » de
  l'exportateur, active par défaut, cuit la conversion Z-up → Y-up dans les données du maillage
  sans laisser de nœud témoin : le même objet exporté deux fois avec deux réglages donne deux
  repères, et **rien dans le fichier ne dit lequel a été fait**. Une consigne côté Blender serait
  donc ambiguë. Le contrôle tourne sur l'artefact.

**Le filet reste en code**, sous la forme d'une correction en deux termes qui ne sont jamais fondus :

```
correction = rotate(λ0, autour du pôle canonique)                ← la donnée humaine
           ∘ align(pôle mesuré, u=0 mesuré → repère canonique)    ← la mesure
```

Pour un asset conforme, `align` vaut l'identité et il n'y a rien à commiter. Pour un asset qu'on ne
veut ou ne peut pas ré-exporter — un modèle téléchargé qu'on préfère garder intact — le terme
absorbe l'écart au lieu de bloquer. C'est la seule raison d'être du filet, et elle suffit à le
garder.

La propriété qui justifie de séparer les deux termes : **un échange de maillage ne touche que
`align`.** `λ0` appartient à la texture, pas au maillage ; un modèle plus fidèle qui réutilise la
même texture le conserve intact. La conception actuelle, à un quaternion unique par corps, invalide
tout d'un coup — c'est ce qu'on remplace.

**Ce que la convention d'export ne règle pas.** Elle porte sur l'axe et sur la direction de la
colonne 0 — mécanique, lisible dans le fichier. Elle ne dit rien de `λ0`, qui est une propriété de
l'**image** : aucune inspection de fichier ne peut établir quelle longitude la colonne 0
représente, seul l'instrument du L2 le mesure. La convention supprime donc les constantes de **L1**,
pas le lot **L3**.

### 4.3 Ce qu'on commite, ce sont les directions mesurées, pas le quaternion

`pôle (0,0,+1)`, `u=0 (0,−1,0)`, résidu `0,00°`, `λ0`, identité de la texture. Ces valeurs se
relisent ; un quaternion non. La composition vit dans le code et se teste sans aucun asset. Le
résidu voyage avec la valeur, pour que le relecteur sache ce qu'il peut en croire.

C'est la forme déjà pratiquée dans le dépôt : le javadoc de `PlanetPoseAppState.SUN_GLOW` documente
les médianes de canaux relevées sur la texture, pas seulement la couleur retenue.

### 4.4 `λ0` vient de trois sources, dans cet ordre

1. **La convention.** Les cartes planétaires placent λ=0 en `u=0` ou au centre : `λ0 ∈ {0°, 180°}`.
   Un choix binaire, pas un ajustement continu. Point de départ par défaut.

   > **Tranché par la mesure le 2026-09-02 : c'est 180°.** La chaîne peint la colonne `u = 0` d'un
   > actif à correction identité à la longitude corps-fixe **180°**, et la Terre et la Lune sont
   > déclarées correctes : leurs cartes ont donc bien leur bord gauche à 180° O, le standard des
   > cartes planétaires. Ce qui explique enfin ce qui passait pour une coïncidence — *pourquoi*
   > l'identité suffit à ces deux corps. `λ0 = 180°` est donc la valeur neutre, et non `0`.
2. **Le point sub-solaire, à l'écran.** L'instrument du L2 : l'écart lu **est** la correction à
   appliquer à `λ0`, en degrés, et il ne dépend pas de la caméra.
3. **Le point de référence IAU, pour les corps solides** — exact par définition, mesuré une fois,
   vrai pour toujours :

| corps | ancrage du méridien origine |
|---|---|
| Mars | cratère **Airy-0** (λ = 0 par définition) |
| Mercure | cratère **Hun Kal** (λ = 20° O par définition) |
| Vénus (sol) | pic central du cratère **Ariadne** |
| Lune | point sub-terrestre moyen (Mösting A en repère historique) |
| Pluton | méridien sub-Charon |
| Terre | méridien de référence IERS |
| Jupiter, Saturne, Uranus, Neptune, Soleil | aucun — méridien radio arbitraire |

---

## 5. Les lots

| Lot | Objet | Nouveau mécanisme ? | Test qui le ferme |
|---|---|---|---|
| **L0** | La sonde, son rapport et son verdict de conformité | oui — l'instrument de mesure | le rapport reproduit le §2.2 ; Terre et Lune déclarées conformes |
| **L1** | Le repère par corps, en deux termes, plus la garde | oui — la décomposition | Terre et Lune inchangées, à l'identité près |
| **L2** | L'instrument de calage à l'écran | oui — graticule + point sub-solaire | l'écart lu à deux azimuts de caméra est le même |
| **L3** | `λ0` arrêté corps par corps | non — L2 appliqué | les six corps solides calés sur leur ancrage IAU |
| **L4** | La dérive de la couche visible | non — un terme de plus dans la même composition | l'écart lu au L2 est stable entre deux dates éloignées |

Les cinq sont implémentés au 2026-09-02. Le L3 est le seul dont l'implémentation ne suffit pas à le
fermer : il demande une passe à l'œil, corps par corps, avec l'instrument du L2.

### L0 — La sonde, son rapport et son verdict de conformité

**Propriété rendue vraie.** Depuis les seuls `.gltf`/`.bin`, on obtient pour chaque corps : pôle,
direction `u=0`, résidu latitude/`v`, sens — sans ouvrir Blender et sans regarder une capture
d'écran.

**L'identité de la texture, dimensions comprises, appartient au L1**, pas ici : c'est la garde qui
en a besoin, et la séparer de la mesure garde `MeshFrame` purement géométrique. Un `MeshFrame` ne
parle que de sommets et d'UV, et n'a donc rien à dire quand la texture change sous un maillage
inchangé — ce qui est précisément le trou que la garde du L1 doit fermer.

**Contenu.** Un outil sous `tools/`, aux côtés de `ephemerisgen` et `orbitgen`. Lecture des
accesseurs `POSITION` et `TEXCOORD_0`, composition de la chaîne de nœuds, les quatre mesures du
§2.1. Sortie : un rapport texte destiné à être recopié en javadoc, **pas du code généré** — du code
généré se relit mal, ajoute une étape de build, et le javadoc de provenance est justement ce qu'on
veut écrire à la main.

**Verdict de conformité, en plus des mesures.** Pour chaque corps, l'outil confronte le repère
mesuré à la convention du §4.2 et répond « conforme » ou donne **la rotation manquante**, sous une
forme directement utilisable des deux côtés : un angle et un axe, à appliquer dans Blender avant
ré-export, ou à porter en constante `align` si l'asset ne doit pas être touché. C'est ce verdict,
et non la liste des mesures, qui rend un remplacement de maillage bon marché.

**Ce que ça ne fait pas.** Aucune correction appliquée, aucun changement de rendu. La sonde ne sait
rien de `λ0` : elle mesure où est `u=0` dans le maillage, jamais quelle longitude il représente.

**Fermeture.** Le rapport rejoue les valeurs du §2.2 sur les onze modèles, résidus compris, et
signale Uranus et les deux anneaux comme non exploitables plutôt que de leur inventer un repère.
**Fixture permanente : la Terre et la Lune**, figées et déclarées références — la sonde doit leur
retrouver pôle `+Z`, `u=0` sur `−X`, résidu `0,0°`. C'est le seul point du rapport qui ne bougera
pas quand les autres assets changeront, donc le seul sur lequel un test peut s'appuyer sans être
réécrit à chaque remplacement.

### L1 — Le repère par corps, en deux termes, plus la garde

**Propriété rendue vraie.** Chaque corps porte un repère mesuré et non un quaternion réglé à
l'aveugle ; l'invariant faux du §2.3 disparaît ; un asset remplacé est **signalé** au lieu d'être
absorbé en silence.

**Contenu.** `PlanetMeshCorrection` cesse de renvoyer un quaternion écrit à la main et compose les
deux termes du §4.2 à partir des directions commitées. **L'identité devient la valeur attendue, et
une constante `align` non identité devient l'exception** — celle d'un asset qu'on a choisi de ne pas
ré-exporter, et qui doit dire lequel et pourquoi dans son javadoc. `λ0` reste à sa valeur
conventionnelle (§4.4, source 1) : ce lot ne cale rien, il pose la structure.

La sonde tourne au chargement du modèle, sur le fil asynchrone existant, et journalise un `WARN`
nommant le corps et l'écart si la mesure diverge de l'attente. Un test rejoue la sonde et assène
l'égalité ; les modèles étant suivis par git (52 fichiers), il fonctionne sur un clone neuf, sans le
`Assumptions.assumeTrue` que `EphemerisDatasetSmokeTest` doit employer pour `dataset/**`.

La garde porte aussi l'**identité de la texture** : dimensions vérifiées au démarrage — gratuit,
l'image est déjà décodée — et empreinte complète vérifiée dans le test, où le coût est indifférent.
Sans cela, remplacer une texture en gardant le maillage laisse la garde muette alors que `λ0` est
devenu faux. C'est le bon partage : contrôle bon marché là où ça tourne toujours, contrôle cher là
où il ne coûte rien.

**Ce que ça ne fait pas.** Ne cale aucune longitude — `λ0` reste conventionnel, c'est L3 qui le
pose. Ne ré-exporte aucun asset : L1 rend la conformité *exigible et vérifiable*, il ne la produit
pas. Uranus reste hors périmètre (§6), sans repère écrit à la main.

**Fermeture.** **La Terre et la Lune sont inchangées, au quaternion près.** C'est le test le plus
fort du chantier : ces deux corps sont les références, leur correction est l'identité et doit le
rester ; si la nouvelle chaîne leur produit autre chose que l'identité, elle est fausse, et ça se
vérifie sans regarder l'écran. Contrôle visuel complémentaire : la Lune montre toujours sa face
visible.

**Livré le 2026-09-01**, plus une réparation faite le lendemain par le L2 :

- `MeshFrameProbe`, `MeshFrame`, `MeshConformance`, `PlanetMeshCalibration`, la table de
  `PlanetMeshCorrection`, `MeshGuard` + `MeshDivergence` câblés dans `PlanetPoseAppState`, et le
  test qui rejoue la sonde sur les dix actifs calibrés.
- **Le signe de `λ0` était inversé, et son zéro n'était pas le bon.** Le terme tournait dans le sens
  qui *diminue* la longitude peinte, et il était compté depuis `0` au lieu de 180° (§4.4). Aucune
  valeur n'avait encore été calibrée contre lui, donc rien d'autre n'est à reprendre — c'est
  exactement le risque que le §7 attribuait au L1 (« une erreur de composition s'y propage
  silencieusement »), et il a été pris par le premier lot qui pouvait le voir.
- L'**empreinte complète de la texture** dans le test n'est pas faite : neuf actifs sur onze sont
  provisoires, une empreinte commitée passerait au rouge à chaque ré-export légitime, et un test qui
  casse à chaque changement normal finit désactivé. Les dimensions, elles, sont vérifiées au
  démarrage. À trancher quand les actifs seront figés.

### L2 — L'instrument de calage à l'écran

**Propriété rendue vraie.** On lit l'erreur de longitude d'un corps **en degrés**, sur un instrument
qui ne dépend pas de la position de la caméra.

**Contenu.** Un mode de debug qui superpose au globe un graticule tracé depuis les UV du maillage,
chaque méridien étiqueté avec la longitude que l'application croit qu'il porte, plus le point
sub-solaire calculé depuis Orekit (direction du Soleil transformée dans le repère corps). Les deux
sources sont indépendantes : l'étiquette vient de la croyance de l'application, le centre de
l'hémisphère éclairé vient de la physique. **L'écart entre les deux est la correction à appliquer à
`λ0`.**

**Ce que ça ne fait pas.** Ne corrige rien tout seul ; ne dit rien des trois corps sans détail
identifiable, où il n'y a de toute façon rien à observer.

**Fermeture.** L'écart lu sur un même corps à la même date, depuis deux azimuts de caméra très
différents, est le même — c'est ce qui distingue cet instrument de la comparaison de captures, où
tourner la caméra suffit à faire coïncider n'importe quelle longitude.

**Livré le 2026-09-02.** Touche **G**, sur le corps focalisé : `MeshCalibrationAppState`,
`GraticuleView`, `GraticuleMesh`, `TexturePainting`, `CalibrationReading`.

- **Le graticule est construit sur le repère mesuré, pas sur les axes de référence.** C'est ce qui
  le fait chevaucher la texture : un modèle tourné emporte sa grille avec lui, et l'écart devient
  visible au lieu de rester invisible. Une grille bâtie sur les axes aurait l'air juste sur un corps
  faux.
- **La caméra n'entre nulle part dans le calcul**, ce qui rend la fermeture structurelle plutôt que
  constatée. Le test la remplace par la variable qui, elle, bouge vraiment : la date. `column 0` est
  lue identique à cent jours d'écart, la Terre ayant tourné cent fois entre les deux.
- **Le nombre à regarder en premier n'a besoin d'aucun œil** : `chain offset`, l'écart entre `λ0` et
  la longitude à laquelle la chaîne peint réellement la colonne 0. Il doit valoir zéro ; s'il ne le
  vaut pas, le défaut est dans le code et rien de ce qu'on voit sur le globe ne veut dire quoi que
  ce soit. C'est lui qui a trouvé l'erreur de signe du L1.
- **Ce que l'instrument ne peut pas faire, et ce n'est pas une limite d'implémentation** : dire si
  l'image est dessinée là où elle prétend. `λ0` est une propriété de l'image, et deux cartes aux
  pixels différents et à la géométrie identique peuvent diverger de n'importe quel angle. D'où le
  L3 en passe corps par corps, à l'œil.

### L3 — `λ0` arrêté corps par corps

**Propriété rendue vraie.** Les six corps solides portent la longitude que leur ancrage IAU leur
impose ; les cinq autres portent une constante assumée comme conventionnelle.

**Contenu.** Application de L2 corps par corps, et pour les six solides recoupement avec l'ancrage
du §4.4. Chaque valeur est commitée avec sa provenance : quel ancrage, quelle date d'observation,
quel écart lu.

**Le seul lot qui se fait à la pièce, et le seul qu'on peut avoir à refaire.** Neuf maillages sur
onze sont provisoires (en-tête, contrainte 2) : `λ0` ne se calibre qu'une fois l'asset concerné
figé, corps par corps, jamais en bloc. La Terre et la Lune sont déjà faites par construction — ce
sont les références. Uranus est hors périmètre.

**Ce que ça ne fait pas.** Ne traite pas la dérive : les valeurs posées ici sont justes **à la date
d'observation** pour Jupiter, Saturne et Vénus-nuages, et le resteront pour les corps solides.

**Fermeture.** Sur chaque corps solide traité, l'écart lu au L2 est nul à la tolérance de lecture
près, et recoupe son ancrage IAU.

**État au 2026-09-02.** La structure est en place et deux corps sont clos ; les neuf autres
attendent la passe à l'écran, qui est le travail que ce lot *est*.

| corps | `λ0` | provenance |
|---|---|---|
| Terre, Lune | 180° | **mesuré.** Déclarés corrects, et la chaîne peint leur colonne 0 à 180° : c'est donc bien l'origine de leurs cartes |
| les neuf autres | 180° | **conventionnel.** Le standard des cartes planétaires, point de départ et non résultat |
| Uranus | — | hors périmètre, pas de calibration du tout |

Ancrages à recouper au moment de la passe, exacts par définition et non par observation : Airy-0
pour Mars, Hun Kal à 20° O pour Mercure, le pic central d'Ariadne pour le sol de Vénus, le point
sub-terrestre moyen pour la Lune, le méridien sub-Charon pour Pluton. Les géantes et le Soleil n'en
ont aucun — leur méridien origine est une convention radio sans détail visible dessus — et leur
`λ0` restera une valeur maison documentée.

### L4 — La dérive de la couche visible

**Propriété rendue vraie.** Jupiter, Saturne et l'atmosphère de Vénus gardent leur calage quand le
temps simulé avance de plusieurs mois.

**Contenu.** Un terme de dérive par corps, en °/j, composé dans la même chaîne — c'est-à-dire une
correction fonction de la date, là où la signature actuelle `correctionFor(SolarSystemBody)` ne peut
pas l'exprimer. Les taux du §3, mesurés et non postulés : le même instrument L2 joué à deux dates
éloignées donne le taux par différence. Vénus demande en plus de dissocier les deux maillages
`venus` et `atmosphere`, qui ne tournent pas à la même vitesse.

**Ce que ça ne fait pas.** Ne modélise pas la rotation différentielle en latitude (Neptune, Soleil)
ni la dérive propre de la Grande Tache Rouge à l'intérieur du Système II — un taux unique par corps,
pas un champ de vitesses. Uranus, Neptune et le Soleil restent sur une constante.

**Fermeture.** L'écart lu au L2 sur Jupiter est stable entre deux dates séparées de plusieurs mois
de temps simulé, là où il dériverait de ~97°/an sans ce lot.

**Livré le 2026-09-02.** `correctionFor(corps, date)` remplace la signature sans date ; le terme
vit dans `PlanetMeshCalibration.visibleLayerDriftDegPerDay`, et Vénus dans `ATMOSPHERE_SHELLS`.

- **Les tests ne regardent jamais les constantes.** Ils mesurent la vitesse à laquelle la texture
  tourne *en repère inertiel*, à travers toute la chaîne de rendu, et la comparent au taux publié
  de la couche que la carte représente : Système II pour Jupiter, Système I pour Saturne, les 4,2
  jours rétrogrades des sommets de nuages pour Vénus. C'est la seule vérification qui distingue une
  constante juste d'une constante plausible. Le corps témoin est la Terre, dont la carte montre son
  propre sol et qui doit donc tourner exactement au taux d'Orekit.
- **Saturne change visiblement de vitesse** : +33,5 °/j, sa carte étant son pont de nuages
  équatorial, qui fait un tour en 10 h 14 contre 10 h 39 pour la période radio. C'est le seul
  changement de comportement notable de ce lot sur un corps qui n'était pas signalé faux.
- **Vénus a désormais deux couches.** `ShellSpin` insère un pivot au-dessus du nœud `atmosphere` de
  l'actif et le fait tourner de l'excédent — 58 fois le taux du sol, en sens inverse. Le morceau
  délicat n'est pas la vitesse mais le **changement de base** : l'axe est connu dans les axes du
  modèle, le pivot est greffé au milieu d'une chaîne de nœuds qui ont leurs propres rotations, et se
  tromper là fait *pencher* la coquille au lieu de la faire tourner — ce qui, sur un pont de nuages
  sans détail, est invisible. Trois tests couvrent la greffe.
- **La dérive a forcé une réparation numérique.** `λ0(t)` croît sans borne : le pont de Saturne est à
  quelque 320 000° de son époque en 2026, où le pas d'un `float` vaut déjà 0,03°. Passer la
  différence brute en `float` quantifiait donc l'orientation du corps, de plus en plus grossièrement
  à mesure que la date avance. Le tour est ramené dans [−180, 180] avant la conversion. C'est le
  test de taux, à 0,001 °/j près, qui l'a vu.

---

## 6. Ce qui reste à trancher

Rien de ceci ne bloque L0.

**Tranché le 2026-09-01** — la Terre et la Lune déclarées correctes et références :

- ~~le signe global (§2.5)~~ — juste, et commun aux onze puisque la chiralité l'est. Cela ferme du
  même coup le *sanity check* de `BUG-3` (« si la convention Hipparchus/JME ne compense pas le sens
  de `ICRF → body`, toutes les planètes tournent à l'envers ») : les deux questions regardaient le
  même signe, l'une dans le temps, l'autre dans l'espace.
- ~~l'hypothèse du §2.6~~ — observée, plus supposée. Le repère canonique est celui de ces deux
  modèles.
- ~~le sort d'Uranus~~ — l'asset étant lui aussi voué au remplacement, aucun repère écrit à la main :
  **le corps sort du périmètre jusqu'à ce qu'un maillage exploitable arrive**, et la sonde le
  signalera à ce moment-là. Écrire un repère à la main pour un asset qu'on va jeter est du travail
  perdu deux fois.

**Tranché par la mesure le 2026-09-02, en implémentant :**

- ~~`λ0` des géantes, entre convention publiée et chiffre maison~~ — la question du *zéro* est
  réglée pour tous (180°, §4.4) ; celle du **méridien origine des géantes** ne l'est pas et ne peut
  pas l'être par mesure, faute d'ancrage : elle reste ouverte, ci-dessous.
- ~~l'orientation verticale des cartes~~ — les textures de référence sont stockées rangée sud en
  premier (§4.2). Ce n'était pas une question posée : c'était une hypothèse tacite, et elle était
  fausse.

**Restent ouverts :**

1. **Le réglage d'export des assets de référence.** `earth.gltf` a une rotation de nœud identité et
   un pôle sur `+Z`, alors qu'une sphère UV Blender a naturellement son pôle sur `+Z` : l'explication
   la plus simple est qu'ils ont été exportés **avec « +Y Up » décochée**. C'est une hypothèse, pas
   un fait — le fichier ne conserve aucune trace du réglage. À confirmer en ré-exportant un corps et
   en lui passant la sonde, ce qui est de toute façon la première chose à faire avant d'appliquer la
   convention à un asset réel.
2. **Le méridien origine des géantes.** Arbitraire par nature (§3) : leur méridien IAU est une
   convention radio sans détail visible dessus, donc aucune passe à l'œil ne peut le poser. À
   décider si on adopte une convention publiée (Système II pour Jupiter, par exemple) ou si on
   assume un chiffre maison documenté. Sans objet tant que leurs assets ne sont pas stabilisés.
3. **L'empreinte complète de la texture dans le test du L1.** Prévue par ce document, non faite,
   pour la raison donnée en fin de section L1. À trancher quand les actifs seront figés.
4. **La promotion de BUG-3.** `docs/bugs.md` prévoit qu'un item « sort d'ici soit corrigé, soit
   promu en item de roadmap quand il s'avère être un chantier à part entière ». Cinq lots : c'en
   est un. L'attribution d'un identifiant de roadmap n'a pas été décidée.

**Tranché le 2026-09-01, après la contrainte 2 de l'en-tête** : ~~où atterrit la correction~~ —
**conformité à l'export par défaut, structure à deux termes conservée en filet** (§4.2). Les neuf
maillages provisoires passeront par Blender de toute façon ; les y orienter est moins cher
qu'entretenir neuf constantes qui churnent, et le filet couvre l'asset qu'on ne veut pas toucher.

---

## 7. Ordonnancement

Strictement séquentiel : **L0 → L1 → L2 → L3 → L4.** Chaque lot consomme le précédent — L1 a besoin
des mesures de L0, L3 a besoin de l'instrument de L2 — à une exception près : **L4 ne dépend pas de
L3.** Un taux de dérive est indépendant de l'offset qu'il fait dériver ; on peut poser le terme et
vérifier sa stabilité dans le temps avec un `λ0` encore faux.

Cette indépendance devient importante avec la contrainte 2 de l'en-tête, qui **sépare le chantier
en deux moitiés de durées de vie opposées** :

| | durable | transitoire |
|---|---|---|
| **L0** sonde et rapport | ✔ l'instrument sert à chaque nouvel asset | |
| **L1** structure en deux termes + garde | ✔ la décomposition survit aux échanges | |
| **L2** instrument de calage | ✔ c'est lui qui rend un échange bon marché | |
| **L3** `λ0` par corps | (les 6 solides, une fois leurs assets figés) | ✘ jeté à chaque ré-export |
| **L4** dérive de la couche visible | ✔ physique, propriété du corps et non de l'asset | |

Conclusion pratique : **L0, L1, L2 et L4 se font maintenant ; L3 attend, corps par corps, que
l'asset concerné soit figé.** Caler la longitude d'un maillage qu'on va remplacer est le seul
travail de ce document dont on sait d'avance qu'il sera à refaire.

**Préalable hors code :** les quatre assets en cours de remplacement (voir l'en-tête) doivent être
stabilisés et commités avant L0 — sans quoi le rapport de référence décrit une cible mouvante.
La Terre et la Lune, elles, sont figées : elles peuvent servir de fixture au test dès L0.

Le lot le plus risqué est **L1** : c'est la seule fois où la décomposition est neuve, et une erreur
de composition s'y propage silencieusement à tout le reste. **L2 porte la valeur du chantier** —
c'est lui qui remplace « comparer à une capture NASA », méthode qui ne peut rien prouver puisque
l'azimut de la caméra est un degré de liberté libre, qui permet de faire coïncider n'importe quelle
longitude.

**Le coût unitaire d'un remplacement de maillage se joue en L0 et L2**, et nulle part ailleurs : le
verdict de conformité (§4.2) règle l'axe en une commande, l'instrument de calage règle `λ0` en une
lecture. Avec neuf assets appelés à bouger, c'est là que se trouve le rendement du chantier — pas
dans les valeurs elles-mêmes, dont on sait d'avance qu'une partie sera jetée.

### Ce qu'il faut faire quand un `.gltf` change

1. **Remplacer les fichiers** sous `models/planets/<corps>/`, en gardant le nom `<corps>.gltf`.
   Ne rien toucher au code à ce stade : si on ne fait rien de plus, l'application le **dit** au
   démarrage — la garde du L1 journalise un `WARN` nommant le corps, l'écart angulaire et, le cas
   échéant, le changement de taille de texture. C'est la détection du §4.1, et c'est le filet qui
   rend les étapes suivantes non urgentes.
2. **`./gradlew meshProbe`** et lire la ligne du corps. Le verdict tranche :
   - `conforming` — le repère est bon, rien à corriger ;
   - `rotate X deg about (…)` — deux fins possibles, au choix (§4.2) : appliquer la rotation dans
     Blender et ré-exporter, ce qui est la voie normale et ne laisse aucune constante ; ou recopier
     la ligne en l'état, ce qui garde l'actif intact ;
   - `MIRRORED` — **aucune rotation ne peut le réparer**, il faut retourner une coordonnée UV ;
   - `NOT A LAT/LONG MAP` — le corps sort du périmètre, comme Uranus, et n'a pas d'entrée du tout.
3. **Si on corrige dans Blender, ne pas traduire l'axe à la main.** L'axe du verdict est exprimé
   dans les axes du `.gltf`, et la case « +Y Up » de l'exportateur cuit une conversion sans laisser
   de témoin (§4.2) : le raisonnement sur le repère Blender est donc faux la moitié du temps.
   Appliquer, ré-exporter, re-sonder, jusqu'à `conforming`. La boucle est de quelques secondes.
4. **Recopier la ligne** dans `PlanetMeshCorrection.CALIBRATIONS` : pôle, `u=0`, résidu, `deg/u` et
   la taille de texture, dans cet ordre. Un actif conforme prend `referenceFrame()`.
5. **`λ0` ne bouge que si l'image a changé.** C'est toute la raison de la structure à deux termes
   (§4.2) : un modèle plus fidèle qui réutilise la même texture conserve sa longitude intacte. Si
   la texture change, `λ0` est à re-poser — d'où sa présence dans la garde.
6. **Trois tests ferment la reprise**, tous sans écran : `PlanetMeshFrameFixtureTest` (l'actif sur
   disque porte bien ce qui a été commité), `PlanetMeshCorrectionTest` (l'alignement ramène le
   repère mesuré sur la référence), `TexturePaintingTest` (`chain offset` nul sur tous les corps).
7. **Puis seulement l'œil**, avec l'instrument du L2, et uniquement si la texture a changé.

---

## 8. Constats annexes, mesurés au cours du diagnostic

Hors périmètre. Consignés ici pour ne pas être reperdus ; aucun n'a été arbitré.

1. **La phase, pas la rotation.** C'est le constat d'origine : sur la capture qui a lancé le
   diagnostic, le disque de Jupiter n'a aucun terminateur — la caméra est quasiment sur l'axe
   Soleil-Jupiter, alors que la référence NASA est à ~80° de phase. Aucune rotation du modèle ne
   crée une nuit qui n'existe pas à l'écran. L'éclairage lui-même est juste :
   [`LightningAppState:51`](../../src/main/java/com/smousseur/orbitlab/states/fx/LightningAppState.java)
   oriente sur `corps − Soleil` avec les positions du repère lointain, et le terme diffus de
   `WrapLighting.frag` ancre l'ombre à `N·L = 0` strictement.
2. **Falloff hors plage documentée.** `PlanetPoseAppState:157` passe `0.8f` à `applyLambert`, alors
   que `WrapLighting.j3md` documente « 0.1 – 0.3 » et vaut 0.3 par défaut (`MissionRenderer` utilise
   bien 0.3). À 0,8 la rampe s'étale sur 53° : un point situé 10° **avant** le terminateur, côté
   jour, est déjà à 12 % de luminosité. Tout jugement « ce détail est-il dans la nuit ? » est faussé
   de 10 à 20° de longitude — ce qui concerne directement le calage du L3.
3. **Nuit quasi noire.** `AmbientSum = m_Ambient × g_AmbientLightColor` = `0.1 × 0.3` = **0,03**. Un
   détail passé du côté nuit disparaît au lieu de s'assombrir.
4. **L'atmosphère de Vénus est rendue opaque.** Le glTF la déclare `alphaMode=BLEND`, alpha 0,72 ;
   `AssetFactory.applyLambert` reconstruit un matériau `WrapLighting` sans mode de mélange ni report
   de l'alpha. La carte de sol 8192×4096 n'est donc jamais visible.
5. **Aplatissement à l'envers.** Saturne (aplatissement réel 0,098, la plus aplatie) et Jupiter
   (0,065) sont des sphères parfaites — boîte englobante cubique mesurée, mise à l'échelle uniforme.
   Pluton (aplatissement réel ≈ 0) a un maillage aplati de 1 %, Uranus de 2 %. Corrigeable par une
   échelle non uniforme dans le repère du maillage, sans ré-export.
6. ~~**Miranda est cuite dans l'asset d'Uranus**~~ — **caduc le 2026-09-01.** L'asset a été
   ré-exporté pendant le chantier (commit `a225025`) : la lune et sa texture ont disparu, les nœuds
   sont passés de 12 à 8. Le résidu du globe, lui, n'a pas bougé — 49,37° avant comme après — donc
   Uranus reste hors périmètre pour la raison du §6. Consigné parce que c'est la démonstration de la
   « cible mouvante » du §7 : la sonde a suivi le changement sans une ligne à retoucher.
7. **Le facteur de couleur de base est perdu** à la re-matérialisation — Uranus déclare 0,499, et
   est rendu à pleine intensité.
8. **La chaîne peint l'arête `v = 0` au pôle sud, et la colonne `u = 0` à 180°** (mesuré le
   2026-09-02). Les deux ensemble font que les cartes standard tombent juste sous la correction
   identité. Consigné ici parce que c'est le genre de fait qu'on redécouvre à ses dépens : il est
   épinglé par `TexturePaintingTest`, mais un lecteur qui raisonne sur la composition sans le savoir
   conclura que la Terre est à l'envers.
9. **Le résidu commité pour Vénus était celui de l'atmosphère** (0,02°) et non celui du globe que la
   garde retient (0,01°) — recopié de la mauvaise ligne du rapport au L1. Sans effet : ni la
   correction ni la garde ne lisent le résidu. Corrigé le 2026-09-02, et signalé parce que c'est
   précisément le genre d'erreur de transcription que le §4.3 veut rendre visible.
