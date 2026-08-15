# docs/ — index

**Point d'entrée : [`roadmap/01-roadmap.md`](roadmap/01-roadmap.md).** Il porte la
priorisation, les notes valeur/difficulté et les dépendances. Les autres
documents sont le détail technique dans lequel il puise.

Les documents de ce dossier sont rédigés en **français** (le code et sa Javadoc
sont en anglais, cf. `CLAUDE.md`).

| Document | Rôle | Statut au 2026-08-10 |
|---|---|---|
| [`roadmap/01-roadmap.md`](roadmap/01-roadmap.md) | Roadmap générale, priorisée | À jour |
| [`bugs.md`](bugs.md) | Registre des bugs ouverts hors chantier | À jour — un item (`BUG-1`, jitter Pluton) |
| [`dette-technique.md`](dette-technique.md) | État de la dette technique mesuré, et règles pour ne pas l'aggraver | **Établi le 2026-08-10** au commit `b027d1d` — 11 items (`DT-1` à `DT-11`), aucun corrigé ; §6 (conseils) ne se périme pas |
| [`camera/01-view-transitions.md`](camera/01-view-transitions.md) | Design complet des transitions de caméra | **Implémenté le 2026-08-10** (item `NAV-1`) — valide **sauf §2, §3.5, §3.6 et §8**, voir bandeau du document |
| [`navigation/01-breadcrumb.md`](navigation/01-breadcrumb.md) | Design du breadcrumb HUD | **Révisé le 2026-08-12** (périmètre V1 réduit : hiérarchie seule, missions exclues, racine `Solar system` ; dropdown des fils en V2, §7 ; bande haute pleine largeur qui ancre le HUD sous elle, §5.5 — couplage à surveiller avec `UI-4`), **non commencé** (item `NAV-4`) |
| [`navigation/02-timeline-mission.md`](navigation/02-timeline-mission.md) | Design de la timeline de mission indexée sur le temps (widget séparé, marqueurs de phases) | **Rédigé le 2026-08-11**, non commencé (item `NAV-2`) — traitement visuel « capsule jumelle » retenu après maquettage (§6) ; §15 liste les arbitrages restants |
| [`menu/01-menu-applicatif.md`](menu/01-menu-applicatif.md) | Design du menu applicatif haut-gauche (remplace le bouton « Missions ») | **Rédigé et tranché le 2026-08-14**, non commencé (item `UI-4`) — trois variantes maquettées sur les assets existants (§4), variante « chip formulaire » retenue, libellé `ORBITLAB`, pas de raccourci clavier, une icône par entrée (§5, §8) ; aucune question ouverte |
| [`ui/01-surfaces-et-modalite.md`](ui/01-surfaces-et-modalite.md) | Classement des surfaces GUI (ancrée / fenêtre / modale), échelle de couches et pile de renvoi `ESC` | **Rédigé, tranché et codé le 2026-08-15** (item `UI-5`, découle d'`UI-4`) — le panneau de gestion est devenu une fenêtre non modale déplaçable, `ESC` ne quitte plus l'application et `Quit` est passé dans le menu (§5, §8). **Compilé, 34 tests au vert, mais non vérifié à l'écran** (modèles absents du dépôt) : §10 consigne six écarts, deux dettes assumées et ce qui reste à confirmer |
| [`graphics-effects/hover-effects.md`](graphics-effects/hover-effects.md) | Design du hover planète ↔ orbite | Valide, non implémenté (item `NAV-5`) |
| [`graphics-effects/spacecraft-view-artefacts.md`](graphics-effects/spacecraft-view-artefacts.md) | Diagnostic des trois artefacts de la vue spacecraft + correctifs | **Rediagnostiqué le 2026-08-09**, cause A corrigée, B et C ouvertes (item `RND-1`) |
| [`graphics-effects/mission-phase-encoding.md`](graphics-effects/mission-phase-encoding.md) | Lecture des phases de vol sur la trajectoire (marqueurs + paliers de saturation) | **Implémenté le 2026-08-10** (item `RND-3`) — remplace `effects-roadmap.md` §9.3.1 et §9.3.2 ; réglage fin du contraste reporté à `RND-4` |
| [`graphics-effects/ribbon-lines.md`](graphics-effects/ribbon-lines.md) | Orbites et trajectoires en ribbon : comparatif CPU / vertex shader, design retenu, effets débloqués | **Rédigé le 2026-08-12**, non commencé (item `RND-4`) — remplace `effects-roadmap.md` §9.4.1 ; traitement **GPU** retenu (§5–§6), §13 liste les inconnues |
| [`graphics-effects/effects-roadmap.md`](graphics-effects/effects-roadmap.md) | Backlog d'effets graphiques | Valide **sauf §1, 3 premiers items, §9.3.1–9.3.2 et §9.4.1** — voir bandeau du document |
| [`mission-detail/01-vue-detail.md`](mission-detail/01-vue-detail.md) | Vue détail mission : orbite atteinte, écart à la cible, stages, message d'erreur | **Implémentée le 2026-08-10** (item `UI-1`) — §7 consigne six écarts constatés en codant, dont un décompte de stages GEO faux dans la spec initiale |
| [`atmosphere/01-impacts-fonctionnels-techniques.md`](atmosphere/01-impacts-fonctionnels-techniques.md) | Étude d'impact du drag atmosphérique | Valide (items `PHY-1` à `PHY-3`) |
| [`launchers/01-ariane-62.md`](launchers/01-ariane-62.md) | Design de l'ajout d'Ariane 62 au catalogue des lanceurs | **Implémenté et mesuré le 2026-08-09** (item `MIS-1`) |
| [`brainstorm/leo-rendezvous-preparation.md`](brainstorm/leo-rendezvous-preparation.md) | Préparation du rendez-vous orbital TLE | Valide (item `MIS-6`) |
| [`brainstorm/missions.md`](brainstorm/missions.md) | Catalogue de types de missions candidats | Valide **sauf baseline** — voir bandeau du document |
| [`brainstorm/features-long-terme.md`](brainstorm/features-long-terme.md) | Backlog de features transverses | Exploratoire, valide |

## Conventions

- Un document de design décrit **un** chantier et survit à son implémentation
  (traçabilité des écarts). Quand il devient faux, on le corrige ou on le
  supprime — on ne laisse pas un document mentir.
- Ce dossier s'appelait `specs/` jusqu'au 2026-08-10 ; il a été fusionné dans
  `docs/`. Pour remonter l'historique antérieur au déplacement, interroger
  l'ancien chemin (`git log --follow -- specs/<fichier>`).
- Les figures vont dans `docs/<chantier>/images/`, nommées d'après le document
  qu'elles illustrent (`02-timeline-capsule-jumelle.png` pour
  `02-timeline-mission.md`). Une maquette porte une légende qui dit ce qui la
  sépare du rendu réel — sinon elle finit par être lue comme une capture.
- Les documents supprimés restent dans l'historique git ; les liens vers
  `docs/optimizer/*` et `docs/mission-rework/*` que l'on croise encore dans
  les anciens textes pointent vers des documents retirés au commit `73a3781`
  (`git log --diff-filter=D -- specs/` pour les retrouver — chemin d'époque).
- Les notes ★ (valeur) et ◆ (difficulté) n'ont de sens que dans la roadmap ;
  les tableaux locaux des specs plus anciennes ont leurs propres échelles,
  antérieures et non recalées.
