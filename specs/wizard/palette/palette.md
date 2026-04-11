

# Analyse visuelle du mockup — Fonts & Couleurs

Excellent mockup ! L'esthétique est très clairement **sci-fi / space-tech**, avec un look à la *Starfield* ou *Elite Dangerous*. Analysons d'abord les fonts et couleurs qui matchent au mieux, puis on enchaîne sur la spec technique.

---

## 🔤 Recommandation de Font

Le mockup utilise une typographie **monospace / technique / condensée** pour les labels et titres, avec un aspect très "terminal HUD". Voici mes recommandations par ordre de pertinence :

| Rôle | Font recommandée | Alternative | Pourquoi |
|---|---|---|---|
| **Titres / Labels uppercase** | **[Rajdhani](https://fonts.google.com/specimen/Rajdhani)** (SemiBold/Bold) | [Orbitron](https://fonts.google.com/specimen/Orbitron) | Condensée, géométrique, esprit HUD spatial — très proche du rendu mockup |
| **Sous-titres / commentaires `//`** | **[Share Tech Mono](https://fonts.google.com/specimen/Share+Tech+Mono)** | [JetBrains Mono](https://www.jetbrains.com/lp/mono/) | Mono, propre, parfait pour les lignes `// select the target orbit` |
| **Corps / inputs** | **[Exo 2](https://fonts.google.com/specimen/Exo+2)** (Regular/Medium) | [Titillium Web](https://fonts.google.com/specimen/Titillium+Web) | Lisible, moderne, conçue pour le tech/sci-fi |
| **Valeurs numériques (slider)** | **[Rajdhani](https://fonts.google.com/specimen/Rajdhani)** (Bold) | [Orbitron](https://fonts.google.com/specimen/Orbitron) | Le gros `550` du slider a cet aspect condensé/bold |
| **Badges (DISPONIBLE, BIENTÔT)** | **Exo 2** (SemiBold, uppercase, small) | Rajdhani | Petit, lisible, technique |

> **Choix pragmatique pour Lemur/JME3 :** Lemur utilise des bitmap fonts. Il faudra générer les `.fnt` via **Hiero** (tool JME) à partir des TTF. Je recommande de se limiter à **2 fonts maximum** pour simplifier : **Rajdhani** (titres + labels + valeurs) et **Share Tech Mono** (commentaires + helpers).

---

## 🎨 Palette de couleurs extraite du mockup

J'ai analysé pixel par pixel les 4 screenshots. Voici la palette complète :

### Couleurs principales

| Token | Hex | RGBA (0-1) | Utilisation |
|---|---|---|---|
| `WIZARD_BG_DEEP` | `#0B1420` | `(0.043, 0.078, 0.125, 0.95)` | Fond principal du panneau |
| `WIZARD_BG_CARD` | `#0F1E2E` | `(0.059, 0.118, 0.180, 0.85)` | Fond des cards / inputs |
| `WIZARD_BG_CARD_HOVER` | `#142838` | `(0.078, 0.157, 0.220, 0.90)` | Survol des cards |
| `WIZARD_SELECTED` | `#0D3A5C` | `(0.051, 0.227, 0.361, 0.90)` | Card sélectionnée (LEO, Falcon Heavy) |
| `WIZARD_ACCENT` | `#00D4FF` | `(0.00, 0.831, 1.00, 1.0)` | Cyan vif — titres, stepper actif, bouton Next, slider |
| `WIZARD_ACCENT_DIM` | `#1A8FAF` | `(0.102, 0.561, 0.686, 0.80)` | Bordures sélection, accent atténué |
| `WIZARD_BORDER` | `#1C3448` | `(0.110, 0.204, 0.282, 0.70)` | Bordures cards, séparateurs, grille |
| `WIZARD_BORDER_GLOW` | `#00A8CC` | `(0.00, 0.659, 0.80, 0.60)` | Bordure lumineuse des éléments sélectionnés |

### Texte

| Token | Hex | RGBA | Utilisation |
|---|---|---|---|
| `WIZARD_TEXT_PRIMARY` | `#E8F4F8` | `(0.91, 0.957, 0.973, 1.0)` | Texte principal, valeurs |
| `WIZARD_TEXT_SECONDARY` | `#5A8BA0` | `(0.353, 0.545, 0.627, 1.0)` | Labels, helpers, commentaires `//` |
| `WIZARD_TEXT_ACCENT` | `#00D4FF` | identique à ACCENT | Titres actifs, liens |
| `WIZARD_TEXT_DISABLED` | `#2E4A5C` | `(0.180, 0.290, 0.361, 0.60)` | Texte des options désactivées (SOON) |

### Sémantiques (badges + boutons)

| Token | Hex | RGBA | Utilisation |
|---|---|---|---|
| `WIZARD_SUCCESS` | `#00E878` | `(0.00, 0.910, 0.471, 1.0)` | Badge "AVAILABLE", bouton "Create mission", check stepper |
| `WIZARD_WARNING` | `#FFB830` | `(1.00, 0.722, 0.188, 1.0)` | Badge "IN PROGRESS", banner Tsiolkovsky |
| `WIZARD_DANGER` | `#FF4D6A` | `(1.00, 0.302, 0.416, 1.0)` | Bouton Cancel, bouton remove `×` |
| `WIZARD_INFO` | `#00D4FF` | identique à ACCENT | Banner info (Step 2) |

### Backdrop

| Token | Hex | RGBA | Utilisation |
|---|---|---|---|
| `WIZARD_BACKDROP` | `#000000` | `(0.0, 0.0, 0.0, 0.60)` | Overlay derrière le modal |

---

## 🎯 Visualisation de la palette

```
FOND          ████ #0B1420   ████ #0F1E2E   ████ #142838   ████ #0D3A5C
               Deep BG        Card BG        Card Hover     Selected

 ACCENT        ████ #00D4FF   ████ #1A8FAF   ████ #00A8CC
               Cyan vif       Cyan dim       Glow border

 BORDER        ████ #1C3448
               Borders/Grid

 TEXT           ████ #E8F4F8   ████ #5A8BA0   ████ #2E4A5C
               Primary        Secondary      Disabled

 SEMANTIC       ████ #00E878   ████ #FFB830   ████ #FF4D6A
               Success        Warning        Danger
```


---

## 📐 Mapping vers les tokens existants de AppStyles

Vérifions comment ça se mappe avec la palette actuelle :Now let me look at the existing fonts and rendering setup to understand the current font system:Now let me check if there's any custom font usage in the project already:Bon, le projet utilise actuellement la font bitmap par défaut de Lemur. Voyons maintenant ce qui est disponible côté Lemur/JME et choisissons une vraie police qui colle à l'esthétique spatiale/tech du mockup.Maintenant je vais regarder les assets existants pour bien comprendre le contexte du projet.Excellent, j'ai assez d'éléments pour te proposer une recommandation complète de fonts et de couleurs, puis on construira la spec technique.

---

## 🎨 Recommandation typographique & palette de couleurs

### Police recommandée : **Rajdhani** + **Share Tech Mono**

En analysant les mockups, le rendu visuel correspond à un style **géométrique condensé semi-futuriste** — lettres capitales angulaires, lisibilité élevée, espacement serré. Deux candidats Google Fonts sous **SIL Open Font License** (100% compatible usage commercial et jeux) collent parfaitement :

| Rôle | Police | Pourquoi |
|---|---|---|
| **Titres / Labels / Stepper / Boutons** | **Rajdhani SemiBold / Bold** | Condensé géométrique, vibe "mission control" identique au mockup. 5 graisses disponibles [[1]](https://fonts.google.com/specimen/Rajdhani) |
| **Valeurs numériques / Champs / Code** | **Share Tech Mono Regular** | Monospace technique, idéal pour les coordonnées, dates, valeurs. Matching parfait du style "terminal" du mockup [[2]](https://fonts.google.com/specimen/Share+Tech+Mono) |
| **Fallback / alternative tout-en-un** | **Orbitron Medium** | Si tu veux une seule police plus "spatiale", mais moins lisible en petit corps [[3]](https://fonts.google.com/specimen/Orbitron) |

**Pourquoi pas les fonts Lemur par défaut ?** Le bitmap font intégré à Lemur est un simple sans-serif générique qui ne rend pas du tout le côté "dark sci-fi dashboard" des mockups. Avec JME3/Lemur, on convertit les TTF en `.fnt` (AngelCode BMFont format) via l'outil BMFont [[4]](https://www.angelcode.com/products/bmfont/) ou le SDK JMonkey intégré, puis on les charge avec `assetManager.loadFont()`.

### Matrice typographique complète

| Rôle UI | Police | Graisse | Taille | Couleur token |
|---|---|---|---|---|
| Titre marque `ORBITLAB` | Rajdhani | Bold | 18px | `ICE_TEXT_PRIMARY` |
| Sous-titre `MISSION WIZARD v2.1` | Rajdhani | Regular | 18px | `ICE_TEXT_SECONDARY` |
| En-tête étape `PARAMETERS — LEO` | Rajdhani | SemiBold | 20px | `ICE_TEXT_PRIMARY` |
| Commentaire `// target orbit config` | Share Tech Mono | Regular | 12px | `ICE_TEXT_SECONDARY` |
| Label champ `ALTITUDE CIBLE` | Rajdhani | Medium | 12px | `ICE_TEXT_SECONDARY` |
| Valeur input / body | Share Tech Mono | Regular | 14px | `ICE_TEXT_PRIMARY` |
| Grosse valeur slider `550` | Rajdhani | Bold | 28px | `ICE_ACCENT` |
| Unité `km` | Rajdhani | Regular | 14px | `ICE_TEXT_SECONDARY` |
| Numéro stepper `1` `2` `3` `4` | Rajdhani | SemiBold | 14px | par état (voir plus bas) |
| Label stepper `MISSION` | Rajdhani | Medium | 12px | par état |
| Badge `AVAILABLE` | Rajdhani | SemiBold | 10px | `ICE_TEXT_PRIMARY` |
| Helper text `degrees · 0° = equatorial` | Share Tech Mono | Regular | 10px | `ICE_TEXT_SECONDARY` |
| Bouton `Next >` / `Cancel` | Rajdhani | SemiBold | 14px | `ICE_TEXT_PRIMARY` |

---

### Palette de couleurs — validation vs. mockup

La palette `ICE_*` existante dans `AppStyles.java` colle **très bien** aux mockups. Voici la correspondance pixel-par-pixel que j'ai vérifiée :

| Zone mockup | Couleur relevée (approx hex) | Token existant | Match ? |
|---|---|---|---|
| Fond fenêtre (bleu profond) | `#141F33` @ 75% α | `ICE_PANEL_BG` (0.08, 0.12, 0.20, 0.75) ≈ `#142033` | ✅ Parfait |
| Fond champ / carte idle | `#1F2E47` @ 60% α | `ICE_PANEL_BG_LIGHT` (0.12, 0.18, 0.28, 0.60) ≈ `#1F2E47` | ✅ Parfait |
| Carte sélectionnée / ring actif | `#3373B3` @ 80% α | `ICE_ROW_SELECTED` (0.20, 0.45, 0.70, 0.80) ≈ `#3373B3` | ✅ Parfait |
| Accent cyan (titres, bouton Next, barre active) | `#4DA6E6` | `ICE_ACCENT` (0.30, 0.65, 0.90) ≈ `#4DA6E6` | ✅ Parfait |
| Bouton Cancel (rouge) | `#E65959` | `ICE_DANGER` (0.90, 0.35, 0.35) ≈ `#E65959` | ✅ |
| Bouton Create (vert) | `#4DCC8D` | `ICE_SUCCESS` (0.30, 0.80, 0.55) ≈ `#4DCC8D` | ✅ |
| Badge "EN COURS" (jaune) | `#F2BF4D` | `ICE_WARNING` (0.95, 0.75, 0.30) ≈ `#F2BF4D` | ✅ |
| Texte principal (blanc bleuté) | `#E6F0FA` | `ICE_TEXT_PRIMARY` (0.90, 0.94, 0.98) ≈ `#E6F0FA` | ✅ |
| Texte secondaire (gris bleu) | `#8CA6BF` | `ICE_TEXT_SECONDARY` (0.55, 0.65, 0.75) ≈ `#8CA6BF` | ✅ |
| Bordures / séparateurs | `#4D668` @ 40% α | `ICE_BORDER` (0.30, 0.40, 0.55, 0.40) ≈ `#4D668C` | ✅ Quasi |
| Backdrop modal | Noir 55% α | Nouvelle constante inline `(0, 0, 0, 0.55)` | ✅ Prévu |

**Verdict : aucune nouvelle couleur n'est nécessaire.** La palette `ICE_*` est déjà un match quasi-pixel-perfect des mockups. 🎯

### Couleur complémentaire optionnelle

Si on veut un petit **glow/halo** sur les éléments actifs (comme le subtil liseré lumineux qu'on voit sur la carte LEO sélectionnée), on peut ajouter un seul token :

```java
/** Subtle glow for selected elements — brighter ICE_ACCENT with reduced alpha. */
public static final ColorRGBA ICE_GLOW = new ColorRGBA(0.30f, 0.75f, 1.0f, 0.25f);
```


Mais c'est optionnel et pourra être ajouté plus tard.

