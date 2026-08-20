## Ma recommandation en une phrase

**Une sous-page `PLANNING` de l'étape des paramètres**, à deux échelles empilées — un axe de trois jours sidéraux qui situe les opportunités, un zoom à la minute qui montre le créneau retenu — dont le clic écrit l'instant optimal dans le champ `LAUNCH DATE` déjà existant.

Le reste du document justifie ce choix, pose l'écran, puis le câblage.

---

## 1. Où la timeline se pose, et pourquoi nulle part ailleurs

Le §10 du `01-basics.md` laissait le point ouvert en ces termes : « l'étape des paramètres est déjà à saturation verticale, donc la timeline ne peut pas y être une ligne de plus ». Le code le confirme sans ambiguïté. La racine de l'étape est épinglée à `FormStyles.CONTENT_HEIGHT = 468 px`, rien ne clippe dans ce wizard, et un débordement atterrit donc sur le footer. Le commentaire de `EarthOrbitDynamicParameters.buildInclinationRow` le dit déjà pour un cas antérieur : deux sliders d'altitude plus un bloc d'inclinaison à trois étages ne rentre pas — et c'est précisément pour cela que le RAAN a été tassé *sur* la ligne de l'inclinaison au lieu de prendre la sienne.

Trois places étaient possibles.

| Place | Ce qu'elle coûte |
|---|---|
| Une cinquième étape du wizard | Un écran facturé à **toutes** les missions, alors que seule celle qui vise un plan existant a un créneau à attendre. |
| Une fenêtre flottante | Une deuxième gestion `HudSurfaces` et un empilement modal, alors que le wizard est déjà modal. |
| **Une sous-page de `PARAMETERS`** | Un basculement de contenu — et le motif existe déjà dans le dépôt. |

`MissionPanelWidget` tient un `enum Screen { LIST, DETAIL }`, ses `showDetail()`/`showList()` échangent le contenu entre header et footer, et le retour est un `< BACK` posé dans la vue de détail elle-même (`MissionDetailView`). La sous-page n'invente donc pas un motif de navigation, elle en copie un.

**Un fait de séquence qui verrouille le choix** : depuis MIS-7 P2, `SITE` (index 1) précède `PARAMETERS` (index 2). Latitude, longitude et altitude du pas de tir sont connues quand l'utilisateur arrive sur les paramètres — la sous-page est donc le **premier endroit du wizard où une fenêtre est calculable**, et il n'y a rien à attendre de plus.

**Ce qui déménage, et ce qui reste.** Le RAAN passe sur la sous-page ; `LAUNCH DATE` reste sur la page principale. La date est le plancher de *toutes* les missions, y compris celles qui n'attendent aucun plan : la mettre derrière un bouton ferait payer un clic au champ le plus banal du formulaire pour servir le cas rare. En partant, le RAAN rend par ailleurs à la ligne d'inclinaison l'air que le commentaire cité plus haut lui refusait.

---

## 2. L'échelle : le fait qui décide de la forme

Le créneau mesuré au §9 fait **232 s** (3 min 52 s) et revient tous les **86 164 s**. Ce rapport de 1 à 371 est ce qui interdit un axe unique, et le chiffre devient brutal une fois posé sur la largeur dont le wizard dispose (`StepParameters.FIELD_W = 752 px`) :

| Ce que l'axe couvre | Largeur du créneau à l'échelle vraie |
|---|---|
| 26 h (le `SEARCH_SPAN` actuel) | **1,9 px** |
| 5 jours | **0,40 px** |

Aucun réglage ne sauve un axe unique : le créneau n'est pas petit, il est invisible. D'où les deux échelles.

**Mais ces deux nombres sont terrestres, et l'UI n'a pas le droit de les connaître.** `LaunchWindowProblem` porte déjà deux échelles — `coarseStep()` et `refinementPrecision()` — chacune justifiée par la même phrase, « que seul le problème peut connaître », et le Javadoc du premier écrit noir sur blanc qu'un balayage imposant son propre pas rendrait silencieusement « aucune fenêtre » sur un problème d'une autre échelle. Un horizon d'affichage de trois jours codé dans le widget commettrait exactement cette faute sur le premier problème non terrestre : la géométrie Terre-Lune est mensuelle, `TranslunarInjectionPlanWindowProblem` balaie déjà à **6 h** contre 1 h ici, et le §7 du `01-basics` parle d'une recherche de soixante jours. L'axe serait vide. Que l'interface ait anticipé la timeline est d'ailleurs écrit dans son propre Javadoc : `name()` est documenté « used in logs and in the wizard's window timeline ».

**Troisième échelle, donc, et sur le problème** : `Duration recurrence()`, l'intervalle après lequel la même opportunité revient — un jour sidéral ici, dérivé de `Constants.WGS84_EARTH_ANGULAR_VELOCITY` plutôt qu'écrit à la main, le **mois anomalistique** pour le translunaire, parce que ce que ce critère-là suit est la distance lunaire et que c'est le passage au périgée qui la ramène, comme le dit déjà le Javadoc de `TranslunarInjectionPlanWindowProblem`. Sans valeur par défaut : une méthode abstraite fait de l'oubli une erreur de compilation, là où un défaut ferait un axe faux. Son Javadoc doit dire que c'est un ordre de grandeur servant à dimensionner un horizon, et non l'affirmation que les créneaux sont exactement périodiques.

**L'UI ne compte alors plus qu'en opportunités.** Elle demande *trois créneaux*, pas *trois jours* ; le planificateur dérive l'horizon de la récurrence du problème, et l'axe se tend du plancher à la fermeture du dernier créneau rendu, plus une marge d'un douzième de récurrence. Aucune durée n'est écrite dans le widget — la seule constante qu'il garde est le nombre de marqueurs qu'il veut montrer, qui est bien une préférence d'affichage. Au niveau du jour, une opportunité est dessinée comme un **instant** — un marqueur, dont personne n'attend que la largeur signifie quelque chose, donc qui ne ment sur rien. La largeur vraie ne se montre qu'au niveau du zoom, où elle occupe environ 39 % d'un cadre de ± 5 min et redevient l'information qu'elle est : la marge opératoire.

**Une nuance qui abîme la prémisse du choix, et qu'il faut avoir en tête.** Le §10 mesure les alignements de deux jours consécutifs à moins d'un m/s les uns des autres. Une liste d'opportunités porterait donc la même durée et le même coût à chaque ligne, et le seul choix réel qu'elle offre est **le jour**, pour une raison opérationnelle — jamais pour un gain de Δv. C'est aussi ce qui a écarté le tableau au profit de l'axe : un tableau dont deux colonnes sur quatre sont constantes documente moins bien qu'un axe montrant le délai entre la date demandée et le créneau qui la sert.

---

## 3. L'écran

**Le déclencheur n'est pas un bouton, c'est un témoin.** Il se pose dans la colonne `LAUNCH DATE` de la page principale, avec la grammaire exacte de l'indicateur `auto` de `MISSION DURATION` (`StepParameters.buildAutoIndicator`) : un point d'état et un mot, sans fond ni insets. Le point est allumé quand un nœud cible est saisi — c'est-à-dire quand la date est gouvernée par un créneau et non par ce qui a été tapé. Le même contrôle dit donc l'état *et* ouvre la page qui l'explique, et le formulaire ne gagne aucun élément lourd. La ligne d'aide sous le champ porte la provenance, comme celle de la durée.

**La sous-page**, de haut en bas :

| Bloc | Contenu |
|---|---|
| Bandeau | `< BACK` accent sans chrome, puis le titre `PLANNING` et son sous-titre — grammaire de `MissionDetailView`. |
| `TARGET NODE (RAAN)` | Le champ déménagé, son unité, sa ligne d'aide. |
| `OPPORTUNITIES` | L'axe, tendu du plancher à la fermeture du dernier créneau rendu (§2) : le plancher en repère, un marqueur cliquable par opportunité, celui retenu en accent. |
| `SELECTED WINDOW` | Le zoom ± 5 min : l'intervalle ouvert, l'optimum, et la courbe de coût en V. |
| Ligne de lecture | Largeur du créneau, coût du plan à l'optimum, délai depuis la date demandée, récurrence. |
| `LAUNCH DATE` | L'écho, en lecture seule. |

**L'écho est en lecture seule** (`UiKit.makeReadOnly`, déjà employé pour l'inclinaison dérivée). Le plancher se modifie sur la page principale : le changer, c'est changer la question posée, alors que la sous-page ne fait qu'y répondre. Le champ n'a ainsi jamais deux propriétaires.

**Le budget vertical passe largement** : 40 + 60 + 60 + 80 + 40 + 60 ≈ **340 px sur 468**. C'est la seule page du wizard qui aura de la marge.

**Le recalcul est polled, pas événementiel.** `StepParameters.update(tpf)` tourne déjà à chaque frame avec des gardes no-op sur `setText` ; la sous-page suit le même idiome, mémoïsée sur le tuple d'entrées, donc ne recalcule rien tant que rien ne bouge. Ordre de grandeur d'un recalcul de trois opportunités terrestres, dérivé des 85 évaluations fermées mesurées au §9 pour une recherche d'un jour : **environ 250 évaluations**, chacune un angle entre deux vecteurs. L'axe suit donc la frappe du RAAN sans qu'un bouton « recalculer » soit nécessaire.

**Le footer n'apprend rien.** `Next` valide et part vers `LAUNCHER`, `Previous` recule vers `SITE`, depuis la sous-page comme depuis la page principale ; seul le `< BACK` ramène aux champs. Les valeurs sont publiées par les champs et non par ce qui est à l'écran, donc `getValues()` est déjà correct depuis la sous-page. Une règle jumelle : **on entre toujours dans une étape par sa page principale** — sauf quand c'est un refus qui y ramène (§5.F).

---

## 4. Ce que fait le clic

Le clic sur un marqueur écrit **l'instant optimal du créneau** dans `LAUNCH DATE`, au format que `TimeConverter.parseUtcDate` relit et que `TimeConverter.formatDate` produit. Aucune clé de formulaire nouvelle, aucun changement dans `MissionWizardAppState`.

**Ce qui l'autorise est le comportement de bord déjà écrit au §10** : une date posée *dans* un créneau est une borne de la plage de recherche, donc un minimum de la grille, et le solveur la retient. Une date posée exactement sur l'optimum se rend donc elle-même. Le champ garde ainsi un sens unique — c'est toujours un plancher —, le clic ne fait que le poser au bon endroit.

**Et la réouverture confirme le canal après coup.** `WizardPrefill` remet déjà `entry.getScheduledDate()` dans `LAUNCH_DATE` via `TimeConverter.formatDate`. Le wizard écrit donc **déjà** des instants de créneau calculés dans ce champ, à l'édition. Le clic ne fait à la création que ce que la réouverture fait depuis toujours : aucune sémantique nouvelle n'est introduite.

Les deux autres options ont été écartées. Écrire seulement le **jour** choisi laisse un piège de calendrier : un jour civil (86 400 s) est plus long qu'un jour sidéral (86 164 s), donc environ un jour sur douze en contient deux, et un plancher à minuit peut alors désigner l'autre que celui cliqué. Une **clé dédiée** créerait deux sources de vérité sur le « quand », un propriétaire à désigner, un retour à écrire dans `WizardPrefill`, et une règle de péremption — le créneau enregistré devenant faux dès que le nœud ou le site change.

**Une conséquence assumée** : l'axe part toujours du plancher, donc écrire la troisième opportunité la ramène en position zéro et découvre trois jours de plus. Le repère du plancher et le marqueur retenu se superposent alors, ce qui est l'image exacte de ce qui vient de se produire. L'alternative — mémoriser séparément la date d'origine pour garder l'axe stable — réintroduirait la deuxième source de vérité que le paragraphe précédent refuse.

---

## 5. Le câblage

**A. Une entrée étroite pour le planificateur.** `EarthLaunchWindowPlanner.nextOpportunity` prend un `MissionSpec.EarthOrbit`, que seul `MissionFactory.specFromWizardValues` sait construire — et qui résout `Launchers.byId`/`Payloads.byId` puis dimensionne les ergols, donc qui lève ; c'est ce que `MissionWizardWidget.compositionRefused` rattrape à l'étape 4. Faire passer la timeline par là la mettrait en panne pour des raisons de propulsion qui ne la regardent pas. La fenêtre n'a besoin que de six nombres, d'où un `record EarthLaunchWindowRequest(latitude, longitude, altitude, LaunchPlane plane, double targetRaanDeg, double semiMajorAxis)` dans `simulation/mission/window/problem/`, la méthode existante devenant un adaptateur au-dessus. Ce qui tranche : **l'`equals` du record est la clé de mémoïsation du §3**, et `LaunchPlane` étant déjà un record, l'égalité par valeur est gratuite.

**B. La liste, sans toucher au chemin de création.** `nextOpportunities(request, earliest, count)` rend la `List<LaunchWindow>` triée par date. **Un compte, pas une durée** : l'appelant dit combien d'opportunités il veut voir, et le planificateur dimensionne la plage sur `problem.recurrence()` (§2), plus un douzième de récurrence de marge. Sur une période exactement constante, `count · recurrence` suffirait — la marge couvre le créneau dont l'ouverture précède la fin de plage alors que son optimum la dépasse. C'est ce qui garde toute arithmétique de durée hors du widget.

**Un piège découvert à l'implémentation, et qui vaut d'être écrit.** `LaunchWindowSolver` trie les créneaux fusionnés **par coût** avant de tronquer à `maxWindows` — jamais par date. Or cette marge d'un douzième fait que la plage contient `count + 1` opportunités dès que la première y tombe, si bien que demander exactement `count` laisse le solveur en jeter une, la plus chère. Sur un critère dont les alignements consécutifs diffèrent de moins d'un m/s, ce choix est arbitraire et peut retirer **la plus proche** — précisément celle que la timeline existe pour montrer. La fabrique demande donc `count + 1` (la plage ne peut pas en contenir davantage) et la coupe chronologique se fait chez l'appelant. Le tri par coût est la bonne réponse générale du solveur ; il n'est simplement pas la question que pose une timeline.

La création, elle, garde son `SEARCH_SPAN = 26 h` **intact**, et `nextOpportunity` reste une entrée distincte plutôt qu'un `nextOpportunities(1).findFirst()`. La raison n'est pas la prudence de principe : le solveur ancre son seuil d'acceptation sur l'époque la moins chère de toute la recherche, et le §10 mesure les alignements consécutifs à moins d'un m/s les uns des autres — assez pour déplacer le seuil, donc les bords du créneau, de quelques secondes. La date programmée est `best.epoch()` et ne bougerait pas ; mais « ne bougerait pas » est une affirmation qu'il vaut mieux ne pas avoir à défendre sur le chemin qui crée les missions. Les deux valeurs sont d'ailleurs voisines sans être égales — 26 h contre les 25 h 56 min que donnerait `recurrence + recurrence/12` —, ce qui suffit à faire de l'unification une mesure et non un nettoyage (§8).

**C. Le site remonte par un fournisseur étroit.** `StepParameters` reçoit déjà `stepLaunchSite::currentLatitude`. `StepLaunchSite` gagne un `currentSite()` rendant les trois nombres, sur la même lecture que son `getValues()`. Pas un fournisseur de la carte de valeurs entière : ce serait rouvrir exactement le couplage que A ferme.

**D. Un seul constructeur de la requête.** `StepParameters` possède la sous-page, donc c'est lui qui assemble la requête — site du fournisseur, altitudes et plan de `dynamicParameters`, nœud lu sur la page planning, plancher lu sur `launchDateField`. La page ne reçoit qu'un fournisseur de requête et un callback d'écriture de date ; elle ne connaît ni le site ni les altitudes.

**E. Les valeurs ne bougent qu'en producteur.** `TARGET_RAAN` garde sa clé, son type et son contrat d'absence signifiante. Seul son producteur déménage : `EarthOrbitDynamicParameters` le perd de son `getDynamicValues()` et de son `applyValues()`, la page planning le gagne, et `StepParameters.getValues()` fusionne la carte de la page comme il fusionne déjà celle des paramètres dynamiques. `WizardPrefill`, `MissionFactory.targetRaanOrNull` et `MissionSpec.EarthOrbit` ne changent pas, et la garde anti-doublon de `getAllValues()` reste satisfaite : un seul producteur.

**Une nuance mesurée à la relecture, parce que « rien ne change » était trop fort.** Le champ vivait dans `EarthOrbitDynamicParameters`, dont il existe **une instance par carte** de profil orbite terrestre. Saisir un nœud sur LEO puis basculer sur POLAR montrait donc un champ vide et ne publiait rien. Le champ étant désormais au niveau de l'étape, la même suite de clics conserve la valeur et la publie. C'est la conséquence voulue du déménagement — un nœud cible n'est pas une propriété de la carte — mais c'est bien un endroit où les mêmes gestes produisent une autre carte de valeurs, et il fallait l'écrire. Côté GEO, la publication devient également possible ; elle est inerte parce que `MissionFactory` ne lit `targetRaanOrNull` que dans sa branche LEO et que `MissionSpec.Geo` n'a pas de composante de nœud. Cette inertie porte du poids : elle est ce qui rend le déménagement sûr, donc elle est documentée sur `PlanningPage.getValues()` plutôt que laissée à découvrir.

**F. Le refus s'ouvre lui-même — mais décider et révéler sont deux gestes.** `validateTargetPlane()` couvre aujourd'hui l'inclinaison et le nœud ensemble ; il se scinde, la vérification du nœud suivant son champ. L'étape possède ses pages, donc elle possède la décision de laquelle un refus révèle — mais elle ne la prend pas dans les validateurs.

La première écriture le faisait, et elle était fausse de deux façons. **D'abord parce qu'entrer dans une étape la remet sur sa page principale** : depuis l'étape `LAUNCHER`, qui re-teste les paramètres parce que le stepper permet de les survoler, le `showStep(PARAMETERS)` qui suit le refus écrase la page que le refus venait de choisir. **Ensuite parce que seul le contrôle du plan avait appris la règle** : une date de lancement invalide saisie depuis la page planning peignait en rouge un champ démonté, et le bouton Next paraissait mort.

D'où la séparation. Les validateurs marquent, sans rien monter. Un `RefusedPage.choose(date, durée, inclinaison, nœud)` — fonction pure, testable sans `AssetManager`, à côté de `RaanEntry` et sur le précédent de `MissionDisplayPanelRules` — dit quelle page révéler. Et un `revealRefusal()` la monte, en lisant les marques laissées plutôt qu'en relançant les contrôles ; `MissionWizardWidget` l'appelle après le `showStep`, donc après le retour à la page principale.

**La page des champs l'emporte quand elle porte un refus**, même si le nœud en porte un aussi : deux pages ne peuvent pas être montrées à la fois, donc une marque est toujours cachée, et s'éloigner d'un champ rouge que l'utilisateur voit déjà est le pire des deux choix. Le nœud est révélé à la pression suivante, une fois les champs propres.

**G. GEO n'a pas de nœud.** `MissionSpec.Geo` porte `finalInclination` mais ni RAAN ni `NodeBranch`, et `scheduledDateFor` teste `instanceof EarthOrbit`. Le témoin et la sous-page n'existent donc que pour les profils orbite terrestre — **absents** sur la carte GEO, et non grisés : rien sur cette page ne pourrait jamais les allumer.

---

## 6. Les cas de bord

**Nœud vide — le cas courant, et il ne doit rien coûter.** Les deux cadres sont dessinés vides plutôt que masqués, avec une ligne sourde : la page ne se réorganise pas quand un nœud est tapé, elle se remplit. Aucun refus, la date n'est pas touchée.

**Nœud illisible — refusé, et l'axe s'efface avec lui.** Le §10 tranche déjà le refus : un nœud qu'on n'a pas su lire est une intention, pas une préférence. S'y ajoute que l'axe **efface** ce qu'il montrait — un créneau calculé sur un nœud périmé, affiché sous un champ rouge, dirait que la mission est planifiée alors qu'elle ne l'est pas.

**Aucune borne sur l'angle, et c'est un non-changement délibéré.** Le contrôle demande un nombre, pas un intervalle. Refuser 370° serait du pédantisme : le critère est périodique et `Vector3D.angle` encaisse n'importe quel réel.

**Site ou plancher illisible — écran vide avec sa raison, et une divergence assumée.** `currentLatitude()` rend 0 sur un champ illisible, ce qui est documenté et sans danger : une borne d'inclinaison retombée à 0 ne fait qu'élargir ce qui est permis. Une fenêtre calculée à latitude 0 est en revanche une **réponse fausse présentée comme juste**. Le constructeur de requête rend donc du vide dès qu'un des trois nombres du site — ou le plancher — ne se lit pas, au lieu d'un zéro.

**Liste vide sur trois jours — un message, pas un refus.** Un jour sidéral contient toujours un alignement, donc une liste vide est une configuration cassée et non une malchance ; c'est déjà ce que dit le `logger.warn` de `scheduledDateFor`. La page l'écrit en clair, la date reste celle qui était saisie, et la mission se crée — cohérent avec le modèle plutôt qu'un refus de plus.

---

## 7. Ce que je testerais

Trois tests sur le modèle, un sur la décision d'affichage.

| Test | Ce qu'il garde |
|---|---|
| `nextOpportunities(…, 3)` rend trois créneaux, dates strictement croissantes, espacées d'une récurrence à 60 s près | La liste, au tolérancement de la mesure de récurrence du §9. |
| Un problème factice de récurrence délibérément autre rend le même compte, sur une plage proportionnellement autre | **Que l'horizon vienne du problème et non de l'UI** (§2) — le seul test qui garde la question posée par la revue. |
| `nextOpportunity` égale le premier de `nextOpportunities` | La fidélité géométrique de l'adaptateur — la non-régression de la décision §5.B. |
| Le demi-grand axe rendu par `from(...)`, sur une cible **elliptique** | Ce que le test précédent ne peut pas voir : le coût vaut `2·v·sin(θ/2)` avec `v` constant, donc le demi-grand axe est un facteur d'échelle positif qui ne déplace pas l'argmin — une valeur fausse d'un facteur 17 laisse la date **inchangée à 0,0 s près** (mesuré). Et la cible doit être elliptique, sinon `0,5·(p+a)` et `p` sont le même nombre. |
| Reprendre le `best().epoch()` du créneau *k* comme plancher rend ce même instant à la seconde | **Le contrat qui autorise le §4.** Le §10 l'affirme en prose pour le cas de bord ; il devient ici une assertion. |
| La décision d'affichage, extraite en objet nu | L'état montré (vide, raison, créneaux) et la page qu'un refus ouvre, testés sans JME — le précédent est `MissionDisplayPanelRules`. |

Ce qui ne sera **pas** testé : la mise en page Lemur, les proportions de l'axe, le rendu du zoom. Le dépôt n'a pas de quoi, et cette partie se vérifie en lançant l'application.

---

## 8. Ce qui reste ouvert

**Unifier les deux plages de recherche.** Le §5.B laisse `nextOpportunity` sur son `SEARCH_SPAN = 26 h` littéral pendant que `nextOpportunities` dérive la sienne de `recurrence()`. Les deux ne diffèrent que de quelques minutes, et il est probable que les dériver toutes deux ne déplace aucune date programmée — mais « probable » ne suffit pas sur le chemin qui crée les missions. C'est une mesure à faire, pas un nettoyage à décider.

**La cible TLE**, qui fournirait le nœud au lieu de le faire saisir, reste hors de ce chantier — le §10 la range dans MIS-6, derrière une mission de rendez-vous, et la couture qui l'accueillera est déjà en place. Elle changera cependant ce que la timeline dessine : un plan réellement en orbite précesse de ~5 °/jour, si bien que les trois marqueurs de l'axe ne seront plus la même opportunité répétée mais trois opportunités distinctes, dont le coût diffère. Le jour où cette source existera, le §2 de ce document sera à relire — et l'écart entre marqueurs cessera d'être décoratif.
