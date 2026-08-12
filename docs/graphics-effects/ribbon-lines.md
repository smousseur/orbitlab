# Spec — Orbites et trajectoires en ribbon

Item roadmap : `RND-4`. Ce document **remplace**
[`effects-roadmap.md`](effects-roadmap.md) §9.4.1, qui posait l'alternative
CPU / GPU en une phrase (« plus simple côté CPU pour un nombre de points
limité ») sans la mesurer. Elle se mesure, et elle ne tombe pas de ce côté-là :
§2 et §5.

## 1. Contexte — ce qui est dessiné aujourd'hui

Deux consommateurs, une seule primitive GL.

| | Orbites planétaires | Trajectoires de mission |
|---|---|---|
| Producteur | `engine/scene/OrbitLineFactory.java` | `states/mission/MissionTrajectoryRenderer.java` |
| Primitive | `LineLoop` (dataset disque) / `LineStrip` (runtime) | `LineStrip` |
| Viewport | far (`farOrbitsNode`) | near (`nearOrbitsNode`) |
| Sommets | 4 096 par corps × 10 corps = **40 960** (`OrbitWindowConfig.defaultSolarSystem()`, `SimulationConfig.defaultSolarSystem()`) | **≤ 8 193** (`TrajectoryPolyline.MAX_POINTS` + la tête interpolée) |
| Écriture du buffer | à la reconstruction de fenêtre seulement — garde de version, `OrbitRuntimeAppState.java:124-131` | **à chaque frame**, `MissionTrajectoryRenderer.update(…)` |
| Couleur | uniforme par corps (`PlanetColors`) | `VertexBuffer.Type.Color`, une nuance par run de phase (`RND-3`) |
| Largeur demandée | `setLineWidth(1f)` (`OrbitInitAppState.java:59`) | `setLineWidth(2f)` (`MissionTrajectoryRenderer.java:36`) |

Et le fait qui motive l'item : **ces deux largeurs sont probablement ignorées**.
En profil core, `glLineWidth > 1` est ramené à 1 px sans erreur — c'est déjà
consigné dans le code (`OrbitLabApplication.java:64-67`). Le MSAA à quatre
échantillons ne rattrape rien d'utile ici : sur un trait d'un pixel il ne peut
produire que quatre niveaux de couverture, ce qui déplace le crénelage sans le
supprimer, et il ne rend pas le trait plus épais pour autant. Une orbite
lointaine reste un fil d'un pixel qui scintille dès que la caméra bouge.

Trois conséquences concrètes, toutes visibles à l'écran :

1. **Lisibilité** — sur la vue système solaire, dix orbites d'un pixel se
   confondent avec le bruit de la skybox.
2. **Le codage de phase de `RND-3` est sous-exploité.**
   [`mission-phase-encoding.md`](mission-phase-encoding.md) §7 a explicitement
   reporté le réglage fin du contraste à cet item : un trait fin et clair se
   désature perceptivement, quelle que soit la couleur qu'on lui donne.
3. **Le hover `NAV-5` n'a pas de levier.**
   [`hover-effects.md`](hover-effects.md) §2 promet de « doubler la largeur
   visible de l'orbite » — doubler 1 px clampé à 1 px ne donne rien.

## 2. Le fait qui commande le design

Les deux consommateurs n'ont pas le même profil de charge, et **c'est l'orbite,
pas la mission, qui décide**.

Un ribbon face-caméra dépend de la caméra. C'est toute la différence avec une
ligne GL : la géométrie d'une ligne ne dépend que des données, celle d'un ribbon
dépend aussi du point de vue. Étendre la ligne **côté CPU**, c'est donc décider
que **toute la géométrie de ligne de l'application devient dynamique**.

- **La trajectoire de mission ne perd presque rien à ce changement**, parce
  qu'elle est déjà dynamique : son buffer de positions est réécrit intégralement
  à chaque frame (le repère des sommets est la tête mobile, cf. le Javadoc de
  `update(…)`). Un ribbon CPU y double un coût déjà payé.
- **Les dix orbites planétaires y perdent tout.** Elles sont écrites une fois
  par reconstruction de fenêtre — un évènement rare, gardé par un numéro de
  version — et ne coûtent aujourd'hui **rien** par frame. Un ribbon CPU y
  *crée* un coût qui n'existe pas : 81 920 sommets, ~1 Mo de buffer, recalculés
  et ré-uploadés soixante fois par seconde **parce que la caméra a bougé d'un
  pixel**.

L'ordre de grandeur du surcoût CPU, chiffré sur le chemin de conversion
existant : `RenderTransform.scaleMetersToUnits` et `AxisConvention.icrfToJme`
allouent chacun un `Vector3D`, et l'expansion en ajoute (tangente, produit
vectoriel, décalage). Compter trois à cinq allocations par sommet, sur 81 920
sommets, à 60 fps : **de l'ordre de 20 millions d'objets par seconde**, contre
~1,5 million aujourd'hui pour la seule trajectoire de mission. Ce n'est pas
mesuré (personne n'a profilé l'application) et le chiffre exact importe peu :
c'est un ordre de grandeur qui change de régime, pas une marge à grignoter.

> **Formulé autrement.** La question n'est pas « le CPU sait-il faire un
> ribbon ? » — il sait, et pour la seule trajectoire de mission ce serait même
> le chemin le plus court. La question est « accepte-t-on de rendre dynamique
> une géométrie statique de 40 960 sommets ? ». Tout le §5 en découle.

## 3. Objectif

Remplacer les primitives `LineStrip` / `LineLoop` par un ruban de triangles
face-caméra, pour les deux consommateurs, avec :

- une **épaisseur stable, exprimée en pixels**, quelle que soit la distance et
  quel que soit le viewport (far à l'échelle 1 unité = 10⁹ m, near à 1 unité =
  10³ m) ;
- un **antialiasing par fondu d'alpha** sur le dernier pixel de chaque bord,
  indépendant du MSAA ;
- une **largeur et une couleur pilotables sans reconstruire la géométrie**,
  puisque c'est précisément ce que `NAV-5` et le highlight de mission
  demanderont ;
- **aucune régression du coût par frame** sur les orbites planétaires.

### Critères de succès

- Sur la vue système solaire, une orbite reste lisible à toute distance de
  caméra ; sa largeur apparente ne varie pas quand on zoome.
- Le bord d'un ruban ne crénelle pas, MSAA désactivé.
- Aucun trou ni pincement visible aux jointures de segments (§7.5).
- La trajectoire de mission passe correctement derrière le corps central, comme
  aujourd'hui.
- Le coût par frame des orbites planétaires reste **nul** hors reconstruction de
  fenêtre.
- La différence de nuance entre deux runs de phase voisins (`RND-3`) devient
  lisible sans toucher à `MUTING_STEP`.

## 4. Les deux traitements possibles

### 4.0 Le calcul est le même dans les deux cas

Il vaut la peine de l'écrire une fois, parce que **CPU et GPU calculent
exactement la même chose** : la question porte sur *où*, pas sur *quoi*. Ce qui
rend d'ailleurs le prototypage CPU légitime avant de descendre dans le shader —
mais ne rend pas le CPU acceptable comme état final.

Pour chaque sommet de la polyligne, de tangente unitaire `t` :

```
d      = direction sommet → œil
n      = normalize(cross(t, d))          # perpendiculaire au trait ET face caméra
demi_l = px × profondeur × 2 / (résolution_y × P[1][1])
sommet_gauche = p + n × demi_l
sommet_droit  = p − n × demi_l
```

`P[1][1] = 1 / tan(fovY/2)` : la demi-largeur en unités monde qui projette
exactement `px` pixels à la profondeur donnée. La profondeur est `−z` en espace
vue, **pas** la distance radiale — sinon le ruban s'élargit sur les bords de
l'écran.

**Le décalage est appliqué en espace vue, avant projection — jamais en NDC après
division par `w`.** C'est le seul point de la formule qui mérite d'être défendu :
la version « écran » (projeter, diviser par `w`, décaler en NDC) est celle qu'on
trouve le plus souvent, et elle explose quand un sommet passe derrière la caméra
(`w ≤ 0`). Ce cas n'est pas théorique ici : en vue spacecraft la caméra est à
quelques centaines de mètres du vaisseau et le trait lui passe littéralement à
travers. Un décalage 3D appliqué avant projection reste géométriquement correct,
et le découpage au near plane, fait par le pipeline en espace clip, interpole ce
qu'il faut.

**Cas dégénéré à traiter** : quand on regarde le long du trait, `t` et `d` sont
colinéaires, `cross` s'annule et la normalisation produit un NaN — un ruban qui
disparaît, ou pire, un triangle qui part à l'infini. Il faut la garde
`si ‖cross‖ < ε alors n = n'importe quel vecteur perpendiculaire à t` : à cet
angle le ruban est vu par la tranche, l'orientation choisie est sans
conséquence.

### 4.1 Traitement CPU — expansion dans le renderer

Chaque frame, pour chaque géométrie de ligne : lire la position de la caméra,
dérouler la formule du §4.0 sur tous les sommets, écrire un `TriangleStrip` de
`2N` sommets, marquer le buffer `setUpdateNeeded()`.

**Ce qu'il a pour lui.**

- **Aucun shader.** Le ruban est de la géométrie ordinaire ; `Unshaded` avec
  `VertexColor` le dessine tel quel — le matériau ne change même pas pour la
  trajectoire de mission, qui l'utilise déjà ainsi.
- Le fondu des bords s'obtient **sans shader non plus**, par une petite texture
  de rampe alpha (4×4 suffit) échantillonnée en travers du ruban via
  `TexCoord.y ∈ [0,1]`, avec `BlendMode.Alpha`. C'est moins précis qu'un
  `smoothstep` en pixels (le fondu devient une fraction fixe de la largeur au
  lieu d'un pixel exact) mais c'est visuellement correct.
- Tout est en Java, donc pas à pas dans un debugger et testable en JUnit sans
  contexte GL — ce que le dépôt sait déjà faire (`MissionPhaseShadingTest`,
  `TrajectoryPolylineTest`).
- La largeur variable **par sommet** est gratuite : c'est un scalaire de plus
  dans la boucle. Côté GPU elle demande un attribut supplémentaire.

**Ce qu'il coûte.**

- Le §2, qui est rédhibitoire : 81 920 sommets d'orbite recalculés par frame
  pour une donnée qui n'a pas changé.
- Une **invalidation à écrire et à ne jamais rater** si l'on veut éviter ce
  recalcul quand rien ne bouge. Le prédicat n'est pas « la caméra a-t-elle
  bougé » mais « la caméra, le repère flottant, le nœud d'orbite du satellite
  (repositionné chaque frame sur son parent, `OrbitRuntimeAppState.java:135-141`),
  le mode de vue ou la fenêtre d'orbite ont-ils bougé » — cinq sources, dont
  trois changent en pratique à chaque frame dès que le temps avance. La
  mitigation ne mitige presque rien.
- **Chaque effet ultérieur repasse par le CPU.** Les tirets animés deviennent
  une réécriture d'UV par frame, le halo une seconde géométrie complète, le
  hover un recalcul de largeur — là où le GPU les traite en uniform. C'est le
  point qui compte le plus sur la durée : ce lot n'est pas une fin, il ouvre
  §9.4.2, §9.4.3, §9.5.3 et `NAV-5`.
- Il **charge le thread de rendu**, qui est aussi celui qui exécute tous les
  `AppState`. Le budget de 16,6 ms est partagé avec la mission, l'éphéméride et
  l'UI Lemur.

### 4.2 Traitement GPU — expansion en vertex shader

La polyligne est dupliquée **une fois pour toutes** en `2N` sommets : chaque
point de la courbe apparaît deux fois, avec un attribut `side = ±1`. Le vertex
shader applique la formule du §4.0 et décale le sommet ; le fragment shader
calcule la couverture du bord. Le buffer ne dépend plus que des données —
**redevient statique pour les orbites**.

Le squelette, vérifié contre les capacités réellement présentes dans
`jme3-core-3.9.0-beta1` (§7.3) :

```glsl
// Ribbon.vert
attribute vec3 inPosition;   // point de la polyligne, dupliqué pour les deux bords
attribute vec3 inNormal;     // tangente unitaire au point, espace modèle
attribute vec2 inTexCoord;   // (side = ±1, abscisse curviligne)
attribute vec4 inColor;

uniform mat4  g_WorldViewMatrix;
uniform mat4  g_ProjectionMatrix;
uniform vec2  g_Resolution;
uniform float m_WidthPx;

varying vec4  vColor;
varying float vSide;
varying float vArc;

void main() {
    vec4 posView = g_WorldViewMatrix * vec4(inPosition, 1.0);
    vec3 tanView = normalize(mat3(g_WorldViewMatrix) * inNormal);
    vec3 eyeDir  = normalize(-posView.xyz);

    vec3  side = cross(tanView, eyeDir);
    float len  = length(side);
    // Vu par la tranche : l'orientation choisie n'a aucune conséquence visible,
    // mais une normalisation par zéro en aurait une.
    side = (len > 1e-6) ? side / len : vec3(1.0, 0.0, 0.0);

    float halfPx    = 0.5 * m_WidthPx + 1.0;   // +1 px de marge pour le fondu
    float halfWorld = halfPx * (-posView.z) * 2.0
                    / (g_Resolution.y * g_ProjectionMatrix[1][1]);

    posView.xyz += side * (inTexCoord.x * halfWorld);
    gl_Position  = g_ProjectionMatrix * posView;

    vColor = inColor;
    vSide  = inTexCoord.x;
    vArc   = inTexCoord.y;
}
```

```glsl
// Ribbon.frag — couverture d'un pixel exactement, quelle que soit la largeur
void main() {
    float halfPx = 0.5 * m_WidthPx + 1.0;
    float distPx = abs(vSide) * halfPx;                       // distance à l'axe, en pixels
    float cover  = clamp(0.5 * m_WidthPx + 0.5 - distPx, 0.0, 1.0);
    gl_FragColor = vec4(vColor.rgb, vColor.a * cover);
}
```

**Ce qu'il a pour lui.**

- Coût par frame **nul** sur les orbites : le buffer ne bouge plus.
- La largeur est un **uniform** : `NAV-5`, le highlight de mission et
  l'atténuation des orbites non survolées deviennent des appels `setFloat` /
  `setColor`, sans reconstruction ni seconde géométrie.
- Le fondu est un pixel exact, indépendant de la largeur et du MSAA.
- L'abscisse curviligne est déjà là dans `inTexCoord.y` : les tirets animés
  (§9.4.2) coûtent trois lignes de fragment et l'uniform `g_Time`, déjà lié par
  JME.
- Le halo (§9.4.3) se fait **dans le même passage**, comme un second profil
  transverse, au lieu d'une seconde géométrie plus large.

**Ce qu'il coûte.**

- Un `.j3md` et deux shaders à écrire et à maintenir. Ce serait le **troisième**
  matériau maison du dépôt, après `MatDefs/Light/WrapLighting.*` et
  `MatDefs/Fx/Corona.*` — la compétence et le précédent existent, c'est
  d'ailleurs ce que le bandeau de `effects-roadmap.md` acte déjà (« nous en
  avons un et nous le maîtrisons »).
- Un bug de shader se debugge par bissection visuelle, pas au pas à pas.
- Le doublement des sommets est **payé en mémoire GPU** : ~2,6 Mo statiques
  pour les dix orbites (§7.2). Sans intérêt à ce niveau, mais il faut le dire.
- Il **exige GLSL 150**, donc un profil OpenGL 3.2+. `Corona.j3md` l'exige déjà :
  ce lot n'ajoute aucune contrainte de plateforme.

### 4.3 Variante écartée — geometry shader

Un geometry shader consommerait la `LineStrip` telle quelle et émettrait le
quad : **aucune duplication de sommets, aucun changement du côté producteur**.
C'est la solution la plus élégante sur le papier, et JME la supporte
(`Shader.ShaderType.Geometry`, `Caps.GeometryShader`).

Écartée pour trois raisons cumulées : les geometry shaders ont un débit
notoirement médiocre sur une bonne partie du parc (le goulet est
l'amplification, exactement notre usage) ; ils sont peu empruntés dans JME,
donc peu de terrain balisé en cas de problème ; et le gain — ne pas dupliquer
des sommets — porte sur une ressource dont §4.2 montre qu'elle ne manque pas.
On échangerait un risque de performance réel contre 1,3 Mo.

### 4.4 Variante écartée — hybride CPU pour les missions, GPU pour les orbites

Défendable sur le papier (chaque consommateur prend le traitement adapté à son
profil de charge), et à rejeter quand même : cela ferait **deux implémentations
du même ruban**, donc deux comportements à faire coïncider sur la largeur, le
fondu, le blending et la profondeur, et deux endroits à modifier pour chaque
effet du §11. C'est le scénario dont [`dette-technique.md`](../dette-technique.md)
§6.3 décrit précisément le coût.

## 5. Tableau de décision

`✅` favorable · `⚠` acceptable sous condition · `❌` défavorable.
Les deux premières lignes sont celles qui tranchent ; le reste confirme.

| Critère | CPU (expansion dans le renderer) | GPU (expansion en vertex shader) |
|---|---|---|
| **Coût par frame — orbites (40 960 pts)** | ❌ **créé de toutes pièces** : 81 920 sommets recalculés + ~1 Mo ré-uploadé par frame, pour une donnée inchangée | ✅ **nul** — buffer statique, écrit à la reconstruction de fenêtre comme aujourd'hui |
| **Coût de chaque effet ultérieur** (§11) | ❌ chaque effet est du code Java par frame ou une géométrie de plus | ✅ chaque effet est un uniform ou 3 lignes de GLSL |
| Coût par frame — mission (≤ 8 193 pts) | ⚠ ×2 du volume déjà écrit, plus un `cross` + 2 normalisations par sommet | ⚠ ×2 du volume écrit, aucun calcul Java supplémentaire |
| Pression GC | ❌ ordre de 20 M d'allocations/s (§2) | ⚠ inchangée pour la mission, nulle pour les orbites |
| Mémoire GPU | ✅ 3 floats/sommet | ⚠ 12 floats/sommet → ~2,6 Mo statiques pour les orbites |
| Nouveau code shader | ✅ aucun — `Unshaded` + rampe alpha 4×4 | ⚠ un `.j3md` + 2 shaders ; 3ᵉ matériau maison du dépôt |
| Épaisseur constante en pixels | ✅ | ✅ |
| Qualité du fondu de bord | ⚠ fraction fixe de la largeur (texture filtrée) | ✅ un pixel exact, indépendant de la largeur |
| Robustesse au near plane | ✅ décalage 3D avant projection | ✅ idem — même formule (§4.0) |
| Largeur variable par sommet (taper) | ✅ un scalaire dans la boucle | ⚠ un attribut de plus |
| Hover `NAV-5` / highlight mission | ⚠ gratuit *parce que* tout est déjà recalculé chaque frame | ✅ `setFloat` sur un uniform |
| Tirets animés (§9.4.2) | ❌ réécriture d'UV par frame | ✅ `g_Time` × abscisse déjà présente |
| Halo additif (§9.4.3) | ❌ seconde géométrie complète | ✅ second profil dans le même fragment |
| Ajout d'un 3ᵉ consommateur (trace au sol, tube d'incertitude) | ❌ le coût par frame se cumule | ✅ coût par frame plat |
| Charge du thread de rendu | ❌ partagée avec tous les `AppState` | ✅ déportée |
| Debug | ✅ pas à pas Java | ⚠ bissection visuelle |
| Testable hors contexte GL | ✅ la géométrie entière | ⚠ seule la construction du maillage (ce qui suffit, §10) |
| Contrainte de plateforme | ✅ aucune | ✅ GLSL 150 — déjà exigé par `Corona.j3md` |
| Effort initial | S–M | M |
| **Verdict** | ❌ | ✅ **retenu** |

## 6. Recommandation

**Expansion GPU en vertex shader (§4.2).**

Le CPU est le chemin le plus court pour livrer *ce lot-ci*, et le mauvais choix
pour tous les suivants. Il transforme une géométrie statique de 40 960 sommets
en géométrie dynamique, et il fait payer en Java par frame ce que le GPU fait
en uniform — alors que la valeur de `RND-4` est justement dans ce qu'il ouvre
(§11), pas dans le ruban seul.

**Ce qui ferait changer d'avis** — deux cas, et un seul est plausible :

1. **Si le périmètre se réduisait aux seules trajectoires de mission**, les
   orbites planétaires restant en lignes GL, l'argument du §2 disparaîtrait :
   la trajectoire est déjà réécrite chaque frame et le CPU redeviendrait
   défendable. Ce n'est pas le périmètre demandé, et ce serait un demi-lot :
   les orbites sont ce qu'on voit le plus longtemps à l'écran.
2. **Si le fondu du ruban se révélait impossible à faire cohabiter avec le
   `FilterPostProcessor`** (§7.7) — pas d'indice en ce sens, mais c'est le seul
   inconnu technique du lot ; il se lève au premier prototype, avant d'écrire
   quoi que ce soit d'autre.

## 7. Design retenu

### 7.1 Disposition du maillage

`Mesh.Mode.TriangleStrip`, sommets écrits par paires
`(gauche₀, droit₀, gauche₁, droit₁, …)`. Une bande de `2N` sommets donne
`2(N−1)` triangles **sans buffer d'index** : le ruban est un strip par
construction, ce qui évite un buffer et un mode d'appel de plus.

L'indexation est d'ailleurs impossible ici, et il vaut mieux le savoir avant
d'essayer : les deux sommets d'une paire portent la même position et diffèrent
par leur seul attribut `side`. Un buffer d'index les ferait pointer sur le
*même* sommet, donc sur le même `side`. La duplication n'est pas un raccourci,
c'est la seule forme possible.

Pour une orbite fermée (`LineLoop` aujourd'hui), la paire du premier point est
répétée en fin de bande pour refermer le ruban.

Le dessin d'un préfixe — ce que fait la trajectoire de mission avec `upTo` —
reste ce qu'il est aujourd'hui : on écrit `2 × (upTo + 1)` sommets et
`updateCounts()` dérive le compte du buffer de positions.

### 7.2 Attributs par sommet

| Attribut | Buffer JME | Comp. | Contenu |
|---|---|:-:|---|
| position | `Position` | 3 | le point de la polyligne, dupliqué pour les deux bords |
| tangente | `Normal` | 3 | `normalize(p[i+1] − p[i−1])`, calculée une fois à la construction |
| bord + abscisse | `TexCoord` | 2 | `(side = ±1, abscisse curviligne normalisée)` |
| couleur | `Color` | 4 | la nuance de run (`RND-3`) ; **omis** pour les orbites planétaires, dont la couleur est uniforme et reste un paramètre de matériau |

`Normal` est détourné de son sens habituel pour porter la tangente. C'est le
seul buffer à trois composantes disponible sans passer aux `TexCoord2…8` (qui
acceptent un nombre de composantes libre mais ne se lisent nulle part ailleurs
dans le dépôt) ; le shader étant maison, l'attribut n'a de sens que pour lui, et
un commentaire dans le `.j3md` le dit.

Le `side` et l'abscisse partagent un même `vec2` : le premier sert au vertex
shader, le second au fragment (tirets, §11.4). Un seul buffer pour deux usages
qui n'entrent jamais en conflit.

Bilan mémoire : 12 floats = 48 octets par sommet, × 81 920 sommets d'orbite =
**~3,9 Mo** avec la couleur, **~2,6 Mo** sans — et sans, puisque les orbites
n'en ont pas besoin. Statique.

### 7.3 Le matériau `MatDefs/Fx/Ribbon.j3md`

Paramètres : `Color` (couleur uniforme, pour les orbites), `WidthPx` (largeur
en pixels), `VertexColor` (booléen, pour les trajectoires de mission).

`WorldParameters` requis : `WorldViewMatrix`, `ProjectionMatrix`, `Resolution`.
Les trois sont des `UniformBinding` réels de JME 3.9 — vérifié dans le jar, et
`Resolution` est même déjà consommée par un matériau livré
(`Common/MatDefs/Misc/DashedLine.j3md`), ce qui confirme au passage qu'elle est
liée **par viewport**. C'est ce qui fait que le même matériau donne la même
largeur en pixels dans le viewport far et dans le near, sans code de bascule.

`g_Time` s'ajoutera au moment des tirets ; il est lié de la même façon.

**Pas de technique `Glow`**, comme `Corona.j3md` et pour la même raison :
`PostFxAppState` tourne en `GlowMode.Objects`, qui re-rend la scène à travers
la technique `Glow` des matériaux. Un matériau qui n'en déclare pas est
simplement ignoré par la passe — c'est ce qu'on veut. En déclarer une reviendrait
à faire flouter puis rajouter le ruban par-dessus lui-même.

### 7.4 Largeur

Une constante par famille, en pixels, dans le matériau : ~2,5 px pour une orbite
planétaire, ~3,5 px pour une trajectoire de mission. Les valeurs se règlent à
l'œil au premier rendu — c'est le premier réglage que le ribbon rend enfin
possible.

Un **plancher** est utile et n'existe pas aujourd'hui : sous ~1,5 px un ruban
redevient un fil crénelé. Le fondu du §7.6 le gère naturellement pour les
largeurs fractionnaires (la couverture décroît au lieu que le trait disparaisse),
ce qui est exactement le comportement souhaité si l'on décide plus tard de faire
maigrir les orbites lointaines.

### 7.5 Jointures — et pourquoi il n'y a pas de miter à faire

Une tangente **moyennée** au sommet (`p[i+1] − p[i−1]`) fait que deux quads
consécutifs **partagent leurs deux sommets** : il n'y a aucun trou par
construction, et donc aucun joint à calculer. Le seul défaut résiduel est un
léger pincement du ruban dans un virage, d'un facteur `cos(θ/2)` où `θ` est
l'angle de braquage entre segments.

Ce facteur est négligeable sur nos données, et ce n'est pas une chance : nos
polylignes sont des courbes lisses densément échantillonnées, pas des chemins
polygonaux à angles vifs.

| Cas | Angle par segment | Pincement |
|---|---|---|
| Orbite planétaire (4 096 pts / période) | 0,09° | < 0,001 % |
| Coast LEO échantillonné à 60 s (période ~5 550 s) | 3,9° | 0,06 % |
| Coast LEO décimé, stride 5 (horizon d'un mois) | ~20° | 1,5 % |

Le pire cas atteint 1,5 % de largeur perdue au sommet d'un virage : invisible.
**On ne code donc ni miter, ni bevel, ni round join.** C'est un pan entier de la
littérature « polyline rendering » que la nature de nos données rend inutile — à
condition d'écrire dans le code *pourquoi*, sinon quelqu'un le rajoutera.

Deux exceptions à traiter explicitement, aux extrémités : le premier et le
dernier sommet n'ont pas de voisin des deux côtés, leur tangente est le segment
unique disponible.

### 7.6 Bord fondu

`cover = clamp(WidthPx/2 + 0.5 − distPx, 0, 1)` où `distPx` est la distance à
l'axe en pixels. La géométrie est étendue d'un pixel au-delà de la largeur
nominale (`halfPx = WidthPx/2 + 1`), et ce pixel excédentaire porte la
transition. Résultat : **un pixel de fondu, exactement, quelle que soit la
largeur et quel que soit le zoom** — ce qu'une rampe de texture ne sait pas
faire.

### 7.7 Profondeur et blending

- `BlendMode.Alpha`, bucket `Transparent`.
- **`depthTest` reste actif** : la trajectoire doit continuer à disparaître
  derrière le corps central, qui écrit la profondeur dans le même viewport near.
- **`depthWrite` est désactivé.** Deux rubans qui se croisent se mélangent au
  lieu que l'un efface l'autre — sur des traits fins c'est le comportement
  souhaitable, et cela évite de dépendre du tri par géométrie de JME, qui ne
  peut de toute façon pas trier deux courbes qui s'entrelacent.

`Renderer.setAlphaToCoverage(boolean)` existe dans JME 3.9 mais est un état
**global du renderer**, pas un état de matériau (absent de `RenderState`) : il
faudrait un `AppState` pour le basculer autour du rendu des rubans. Écarté — le
fondu par alpha du §7.6 rend l'A2C inutile ici.

Le seul inconnu du lot est la cohabitation avec le `FilterPostProcessor` de
`PostFxAppState`, qui assemble sky → far → near dans un même framebuffer
multiéchantillonné. Rien n'indique de problème (le blending alpha y est
ordinaire), mais c'est **la première chose à vérifier au prototype**, avant
d'écrire la moindre ligne du reste (§6, cas 2).

### 7.8 Les deux viewports et le repère flottant

Rien de spécial à faire, et c'est le bénéfice discret du choix GPU :

- **Échelles** — la largeur est calculée en espace vue à partir de la
  profondeur ; que 1 unité vaille 10⁹ m ou 10³ m ne change rien à l'arithmétique.
- **Repère flottant** — l'expansion travaille sur `g_WorldViewMatrix`, donc
  après application des translations de `nearFrame` / `farFrame`. Le décalage
  transversal est de l'ordre du pixel : aucun risque d'annulation de grands
  opérandes du type de celui documenté dans
  [`spacecraft-view-artefacts.md`](spacecraft-view-artefacts.md) §4.
- **Origine partagée avec les marqueurs de phase** — inchangée. `PhaseNodeMarkers`
  reçoit toujours l'origine et la translation calculées par
  `MissionTrajectoryRenderer`, et les marqueurs restent des `Points` : ils ne
  passent pas au ruban dans ce lot (§12).

### 7.9 Ce que devient le code

- **Nouveau `engine/scene/RibbonMeshBuilder.java`** — pur, sans JME au-delà de
  `Mesh` : prend `(positions, couleurs?, fermé?)` et produit le maillage étendu
  (positions dupliquées, tangentes, `side`, abscisse). C'est le
  `LineMeshBuilder` que `effects-roadmap.md` §9.7 appelait de ses vœux, sous sa
  forme utile.
- **`OrbitLineFactory`** délègue la construction au builder et pose le matériau
  `Ribbon`. Ses deux `buildBodyRelativeLineStrip(…)` gardent leur signature,
  moins le paramètre `lineWidth` qui ne veut plus rien dire.
  `updateGeometryPositionsHelioMeters(…)` doit réécrire positions **et**
  tangentes : c'est le seul endroit qui gagne réellement en complexité.
- **`MissionTrajectoryRenderer`** garde son architecture (allocation unique,
  écriture d'un préfixe, garde d'identité sur la polyline pour les couleurs) et
  écrit deux sommets par point. Le buffer de couleurs suit le même doublement.
- Le `TODO` `sampleIcrfSafe` de `OrbitLineFactory.java:125`
  ([`dette-technique.md`](../dette-technique.md) DT-11) est sur le chemin : à
  traiter ou à tracer en passant, pas à recopier tel quel.

## 8. Ce que le ribbon coûte par frame, et le levier disponible

Après ce lot, le coût par frame se résume à la trajectoire de mission :
**2 × 8 193 sommets** écrits au lieu de 8 193, soit ~197 Ko par frame et
~50 000 allocations `Vector3D`. C'est le double d'aujourd'hui, et cela reste
petit — mais c'est le seul poste qui grossit, alors autant savoir qu'il y a un
levier, et lequel.

Ce buffer est réécrit intégralement chaque frame **parce que l'origine des
sommets est la tête mobile**. Or la précision recherchée n'exige pas que
l'origine soit *exactement* la tête : elle exige qu'elle soit *proche* des
sommets dessinés. Un **ré-ancrage paresseux** — ne changer d'origine que
lorsque la tête s'en est éloignée de plus d'un seuil — rendrait le buffer
append-only : on n'écrirait plus que les sommets nouvellement franchis, plus le
sommet de tête. Le coût par frame passerait de 16 386 sommets à une poignée.

**Non retenu dans ce lot** : c'est un changement de la logique de précision, qui
mérite son propre raisonnement et son propre test, et le ribbon n'en dépend pas.
Consigné ici parce que c'est le ribbon qui rend la question intéressante.

## 9. Découpage en lots

1. **Prototype** — un ruban, une orbite, sur le viewport far. Vérifie la
   largeur en pixels, le fondu, et **la cohabitation avec le
   `FilterPostProcessor`** (§7.7). C'est le lot qui lève l'inconnu ; il ne
   produit rien d'autre.
2. **`RibbonMeshBuilder` + orbites planétaires** — les dix orbites passent au
   ruban, `OrbitInitAppState` et `OrbitRuntimeAppState` compris (le chemin de
   mise à jour des tangentes est ici).
3. **Trajectoires de mission** — `MissionTrajectoryRenderer`, avec les couleurs
   de run doublées. Et le réglage de contraste que
   [`mission-phase-encoding.md`](mission-phase-encoding.md) §7 attendait.
4. **Réglage** — largeurs, fondu, éventuel plancher.

Les lots 2 et 3 sont indépendants l'un de l'autre une fois le 1 fait.

## 10. Tests

Le shader ne se teste pas en JUnit ; la construction du maillage, si — et c'est
là que sont les erreurs qui coûtent cher (`RibbonMeshBuilderTest`) :

- pour `N` points, la bande contient `2N` sommets, et `2(N−1)` triangles ;
- les deux sommets d'une paire portent la **même position** et des `side`
  opposés ;
- la tangente d'un sommet intérieur est colinéaire à `p[i+1] − p[i−1]` ; celles
  des extrémités au segment unique disponible ;
- une polyligne fermée referme la bande (dernière paire = première paire) ;
- l'abscisse curviligne est croissante et normalisée sur `[0,1]` — c'est elle
  que les tirets consommeront, une abscisse fausse ne se verra qu'à ce
  moment-là ;
- deux points **confondus** (une tangente nulle) ne produisent ni NaN ni
  triangle dégénéré. Cas réel : la trajectoire de mission a un sommet de tête
  écrit à `Vector3D.ZERO` qui coïncide avec le dernier point échantillonné dès
  que la tête atteint la fin de la polyline.

Une vérification visuelle reste nécessaire pour le fondu, la largeur et la
profondeur ; elle est notée comme telle, pas déguisée en test.

## 11. Effets débloqués — impactants et simples

Classés par rapport impact / effort une fois le ribbon en place. Les quatre
premiers sont, chacun, quelques lignes de shader ou un appel de matériau : c'est
la vraie valeur de `RND-4`.

**11.1 Largeur et couleur pilotables à chaud — effort XS.**
`mat.setFloat("WidthPx", …)` / `setColor("Color", …)`, sans reconstruire quoi que
ce soit. C'est le prérequis mécanique de tout ce qui suit, et il est acquis
d'office.

**11.2 Hover planète ↔ orbite (`NAV-5`) — effort S.**
[`hover-effects.md`](hover-effects.md) §2 demande de doubler la largeur de
l'orbite survolée et de descendre les autres à alpha 0,30. Avec le ribbon :
deux uniforms, animés sur 150 ms. Sans lui, la promesse est intenable (§1).

**11.3 Mise en avant de la mission sélectionnée (§9.5.3) — effort S.**
Même mécanisme, branché sur la sélection de `MissionContext` : la mission active
gagne 1,5 px et son alpha plein, les autres retombent.

**11.4 Tirets animés / flow-along (§9.4.2) — effort S.**
L'abscisse curviligne est déjà dans `inTexCoord.y` et `g_Time` est un
`UniformBinding` de JME. Trois lignes de fragment :
`fract(vArc × densité − g_Time × vitesse) < rapport_cyclique`. Donne le **sens de
parcours** sans aucune géométrie supplémentaire — et rend inutile l'item
« flèches directionnelles » (§9.4.5).

**11.5 Halo additif (§9.4.3) — effort S.**
Pas une seconde géométrie : un second profil transverse dans le même fragment.
Le ruban est étendu de quelques pixels, le cœur garde sa couverture nette, et
au-delà l'alpha décroît en `smoothstep`. Un seul passage, un seul maillage.

**11.6 Fondu de la queue de traîne — effort S.**
La trajectoire de mission est une traîne qui pousse derrière le vaisseau. Un
`alpha` décroissant vers les sommets les plus anciens — via l'abscisse, déjà
présente — donne la lecture « d'où il vient » immédiatement, et allège
visuellement les missions longues. Variante à peine plus chère : le
**fenêtrage temporel ±N h** de §9.5.5.

**11.7 Ruban qui s'affine vers la queue (taper) — effort S–M.**
Un facteur de largeur par sommet (attribut supplémentaire, §7.2) : le trait naît
fin et s'épaissit jusqu'à la tête. Redondant avec 11.4 sur le sens de parcours,
mais plus discret ; utile pour les captures d'écran.

**11.8 Réglage fin du codage de phase (`RND-3`) — effort XS.**
[`mission-phase-encoding.md`](mission-phase-encoding.md) §7 avait explicitement
reporté ce réglage à `RND-4` : sur un trait de 3 px porteur de couleur, la
différence entre deux runs voisins devient lisible, et `MUTING_STEP` peut
probablement *baisser* — ce qui rendrait en prime de la séparation entre
missions. Le test de contraste existant arbitre.

**11.9 Trace au sol (§9.4.4) — effort M.**
Ne dépend pas du ribbon, mais le réutilise entièrement : même builder, même
matériau, une couleur et une largeur différentes. Le ribbon fait passer cet item
de « un second système de lignes » à « un second appel du même ».

**11.10 Plancher de lisibilité à distance — effort XS.**
Une orbite lointaine cesse de scintiller : le fondu du §7.6 dégrade la
couverture au lieu de faire disparaître le trait, et un plancher sur `WidthPx`
garantit qu'elle reste visible. Gratuit, mais il faut décider de la valeur.

## 12. Hors périmètre

- **Marqueurs de phase.** `PhaseNodeMarkers` reste en `Mesh.Mode.Points`. Le
  repli « mesh de quads dans le même nœud 3D » qu'évoque
  [`mission-phase-encoding.md`](mission-phase-encoding.md) §5.4 devient plus
  facile une fois le builder écrit, mais c'est un autre lot et un autre arbitrage
  visuel.
- **Trajectoire future.** Toujours pas dessinée ; cf.
  `mission-phase-encoding.md` §3. Le ribbon n'y change rien — mais 11.6 et §9.5.5
  deviendront nettement plus intéressants le jour où elle le sera.
- **Ré-ancrage paresseux du buffer de mission** (§8).
- **Tube d'incertitude** (§9.6.1) : une enveloppe volumétrique n'est pas un
  ruban face-caméra, et ne se déduit pas de ce lot.

## 13. Reste ouvert

- **Aucun chiffre de performance de ce document n'est mesuré.** Ni les
  allocations par seconde du §2, ni le coût par frame du §8 : ce sont des
  produits d'arithmétique sur des tailles de buffer réelles. La décision du §6
  ne tient pas à leur précision — elle tient à un rapport de un à zéro sur les
  orbites — mais toute décision *ultérieure* qui s'appuierait dessus demande une
  mesure.
- **Les largeurs en pixels (§7.4) sont des valeurs de départ**, à régler à l'œil
  au lot 4.
- **La cohabitation ruban / `FilterPostProcessor` est le seul inconnu
  technique** (§7.7). Elle se lève au lot 1, et c'est pour cela que le lot 1
  existe.
