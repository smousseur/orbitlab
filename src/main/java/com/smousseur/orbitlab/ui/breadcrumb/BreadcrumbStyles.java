package com.smousseur.orbitlab.ui.breadcrumb;

import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.ElementId;
import com.simsilica.lemur.style.Styles;
import com.smousseur.orbitlab.ui.form.FormStyles;

/**
 * Lemur selectors of the breadcrumb band, declared once in the {@code form} style.
 *
 * <p>The widget builds plain {@code Button} and {@code Label} elements carrying the element ids
 * below and touches no skin attribute at construction — the rule that came out of {@code UI-4} (see
 * {@code docs/menu/01-menu-applicatif.md} §6.1, and {@code docs/navigation/01-breadcrumb.md} §3):
 * what a widget of the form style needs beyond that style is a selector more, never an override.
 *
 * <p>Element ids are hierarchical and read right to left, so each of these inherits the {@code
 * button} or {@code label} selector of {@link FormStyles} — Sora 13 and the form's text colours —
 * and only states what differs.
 */
public final class BreadcrumbStyles {

  /**
   * Label of the root segment. The Sun is never rendered as a segment named "Sun": it <em>is</em>
   * the root, and the whole system is what clicking here goes back to.
   */
  public static final String ROOT_LABEL = "SOLAR SYSTEM";

  /** Text drawn between two segments. */
  public static final String SEPARATOR_TEXT = ">";

  /** The full-width band itself. */
  public static final ElementId BAND = new ElementId("breadcrumb.band.container");

  /** A clickable segment: the root, or any ancestor of the focused body. */
  public static final ElementId SEGMENT = new ElementId("breadcrumb.segment.button");

  /** The segment the focus is on — highlighted, and not a button at all. */
  public static final ElementId CURRENT = new ElementId("breadcrumb.current.label");

  /** The {@code >} between two segments. */
  public static final ElementId SEPARATOR = new ElementId("breadcrumb.separator.label");

  /**
   * Horizontal padding of a segment. It is also what separates a segment from the {@code >} next to
   * it, since the elements are laid end to end with no gap of their own.
   *
   * <p>Public because the widget has to back the row out by exactly this much to put the first
   * segment's <i>text</i>, rather than its frame, on the intended vertical.
   */
  public static final float SEGMENT_PAD_X = 8f;

  private static final float SEPARATOR_PAD_X = 2f;

  /**
   * Flat tint of the band. Deliberately dark and mostly transparent: the band is permanent chrome
   * over a scene that is nearly black in places and washed out in others, and the segments have to
   * stay readable in both without the strip itself reading as a title bar.
   *
   * <p>This is the one value the spec left to the mock-up ({@code docs/navigation/01-breadcrumb.md}
   * §5.5). It is a single line here precisely so that settling it later costs nothing.
   */
  private static final ColorRGBA BAND_TINT = new ColorRGBA(0.024f, 0.055f, 0.094f, 0.1f);

  /**
   * Skin of a clickable segment: no paint, but geometry. An element with a null background has no
   * quad, and something with no quad cannot be picked — the mouse would only find the letters of
   * the label, so the hover would come and go between two glyphs. Same reasoning, and same trick,
   * as the menu entries of {@code FormStyles}.
   */
  private static final ColorRGBA SEGMENT_HIT_AREA = new ColorRGBA(1f, 1f, 1f, 0f);

  private BreadcrumbStyles() {}

  /** Registers every breadcrumb selector. Called once from {@code AppStyles.init}. */
  public static void init() {
    Styles styles = GuiGlobals.getInstance().getStyles();

    Attributes band = styles.getSelector(BAND, FormStyles.STYLE);
    band.set("background", new QuadBackgroundComponent(BAND_TINT));

    Attributes segment = styles.getSelector(SEGMENT, FormStyles.STYLE);
    segment.set("background", new QuadBackgroundComponent(SEGMENT_HIT_AREA));
    segment.set("color", FormStyles.TEXT_SECONDARY);
    segment.set("highlightColor", FormStyles.TEXT_PRIMARY);
    segment.set("insets", new Insets3f(0, SEGMENT_PAD_X, 0, SEGMENT_PAD_X));

    Attributes current = styles.getSelector(CURRENT, FormStyles.STYLE);
    current.set("color", FormStyles.ACCENT_BRIGHT);
    current.set("insets", new Insets3f(0, SEGMENT_PAD_X, 0, SEGMENT_PAD_X));

    Attributes separator = styles.getSelector(SEPARATOR, FormStyles.STYLE);
    separator.set("color", FormStyles.TEXT_LO);
    separator.set("insets", new Insets3f(0, SEPARATOR_PAD_X, 0, SEPARATOR_PAD_X));
  }
}
