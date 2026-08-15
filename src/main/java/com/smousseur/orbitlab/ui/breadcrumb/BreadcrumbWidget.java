package com.smousseur.orbitlab.ui.breadcrumb;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.smousseur.orbitlab.app.view.ViewMode;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.form.FormStyles;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The breadcrumb band that runs across the top of the HUD: {@code Solar system > Earth > Moon}.
 *
 * <p>Presentation only — it holds no focus truth and changes none. It is told what to display
 * through {@link #setFocus} and reports clicks through the two handlers given to its constructor;
 * {@code BreadcrumbWidgetAppState} owns both ends.
 *
 * <p><b>The band's height never depends on what it shows.</b> It is {@link
 * AppStyles#BREADCRUMB_BAND_HEIGHT_PX}, whatever the depth of the hierarchy displayed, because the
 * whole top-left HUD anchors under it: a band that grew with the focus would move the application
 * menu, and the mission panel under it, every time the camera changed body ({@code
 * docs/navigation/01-breadcrumb.md} §5.5).
 *
 * <p>Segments are measured and placed by hand rather than handed to a layout, the way the time
 * capsule places its own clusters. A layout would size the band to its content; here the band is a
 * fixed strip and the row is placed inside it, which is the only arrangement that keeps the two
 * independent.
 */
public final class BreadcrumbWidget implements AutoCloseable {

  private final Container band;
  private final Runnable onRootSelected;
  private final Consumer<SolarSystemBody> onBodySelected;

  /** Segments and separators, in reading order. Rebuilt whole on every focus change. */
  private final List<Panel> elements = new ArrayList<>();

  /**
   * Creates the widget. Nothing is displayed until {@link #setFocus} is called.
   *
   * @param onRootSelected invoked when the {@code Solar system} segment is clicked
   * @param onBodySelected invoked with the body of the segment clicked
   */
  public BreadcrumbWidget(Runnable onRootSelected, Consumer<SolarSystemBody> onBodySelected) {
    this.onRootSelected = Objects.requireNonNull(onRootSelected, "onRootSelected");
    this.onBodySelected = Objects.requireNonNull(onBodySelected, "onBodySelected");
    this.band = new Container(BreadcrumbStyles.BAND, FormStyles.STYLE);
  }

  /**
   * Attaches the band to a GUI node.
   *
   * @param parent the node to attach to
   */
  public void attachTo(Node parent) {
    Objects.requireNonNull(parent, "parent").attachChild(band);
  }

  /**
   * Stretches the band across the top of the screen and re-places its segments in it.
   *
   * @param screenWidth width of the render surface in pixels
   * @param screenHeight height of the render surface in pixels — the band's own top edge, since
   *     Lemur's GUI convention puts the translation at the element's top-left corner
   */
  public void layoutTopBand(int screenWidth, int screenHeight) {
    Vector3f size = new Vector3f(screenWidth, AppStyles.BREADCRUMB_BAND_HEIGHT_PX, 0f);
    band.setPreferredSize(size);
    band.setSize(size);
    band.setLocalTranslation(0f, screenHeight, UiLayers.HUD);
    placeElements();
  }

  /**
   * Rebuilds the chain for a focus. The root segment is always present and always first; the rest
   * is the ancestry of {@code body}, Sun excluded.
   *
   * <p>Which segment is "here" — highlighted and inert — follows the mode alone. In {@link
   * ViewMode#SPACECRAFT} the focus is the spacecraft, which has no segment of its own, so the body
   * it orbits is shown as clickable context: clicking it leaves the mission and focuses the planet
   * ({@code docs/navigation/01-breadcrumb.md} §4.2).
   *
   * @param mode the current view mode
   * @param body the body the view is centred on
   */
  public void setFocus(ViewMode mode, SolarSystemBody body) {
    clearElements();

    addSegment(BreadcrumbStyles.ROOT_LABEL, mode == ViewMode.SOLAR, onRootSelected);

    List<SolarSystemBody> ancestry = ancestryOf(body);
    for (int i = 0; i < ancestry.size(); i++) {
      SolarSystemBody segment = ancestry.get(i);
      boolean isFocus = i == ancestry.size() - 1 && mode == ViewMode.PLANET;
      addSeparator();
      addSegment(segment.displayName(), isFocus, () -> onBodySelected.accept(segment));
    }

    placeElements();
  }

  @Override
  public void close() {
    clearElements();
    band.removeFromParent();
  }

  /**
   * The chain from the root down to {@code body}, the Sun excluded: it is rendered as the {@code
   * Solar system} root rather than as a segment of its own. Focusing the Sun therefore yields an
   * empty chain, and a breadcrumb reduced to its root.
   */
  private static List<SolarSystemBody> ancestryOf(SolarSystemBody body) {
    Deque<SolarSystemBody> ancestry = new ArrayDeque<>();
    for (SolarSystemBody b = body; b != null && b != SolarSystemBody.SUN; b = b.parent()) {
      ancestry.addFirst(b);
    }
    return List.copyOf(ancestry);
  }

  private void addSegment(String text, boolean isFocus, Runnable action) {
    if (isFocus) {
      add(new Label(text, BreadcrumbStyles.CURRENT, FormStyles.STYLE));
      return;
    }
    Button button = new Button(text, BreadcrumbStyles.SEGMENT, FormStyles.STYLE);
    button.addClickCommands(source -> action.run());
    add(button);
  }

  private void addSeparator() {
    add(new Label(BreadcrumbStyles.SEPARATOR_TEXT, BreadcrumbStyles.SEPARATOR, FormStyles.STYLE));
  }

  /**
   * Sizes an element to its text and hangs it off the band. The size is applied here because no
   * layout manages these children; without it they would report zero and the row would collapse.
   */
  private void add(Panel element) {
    element.setSize(element.getPreferredSize());
    band.attachChild(element);
    elements.add(element);
  }

  /**
   * Lays the row end to end from the left, vertically centred in the band.
   *
   * <p>Left-aligned rather than centred, so the chain starts on the same vertical as the
   * application menu right below it and stays put as segments come and go — a centred row slides
   * sideways on every focus change, and reads as a different widget each time.
   */
  private void placeElements() {
    // What has to line up is the first segment's text, not its frame, so the row starts one segment
    // padding to the left of that vertical. The vertical itself is the menu chip's visible left
    // edge: the chip sits at the HUD margin and the form style draws its skin inside the button
    // insets, which is what puts its border — and its label with it — that far in. Derived from the
    // menu's own two constants rather than measured, so moving either one moves both widgets.
    float x = AppStyles.HUD_MARGIN_PX + FormStyles.BUTTON_INSET_X - BreadcrumbStyles.SEGMENT_PAD_X;
    for (Panel element : elements) {
      Vector3f size = element.getSize();
      float y = -(AppStyles.BREADCRUMB_BAND_HEIGHT_PX - size.y) * 0.5f;
      element.setLocalTranslation(x, y, 1f);
      x += size.x;
    }
  }

  private void clearElements() {
    for (Panel element : elements) {
      element.removeFromParent();
    }
    elements.clear();
  }
}
