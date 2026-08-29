package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.MissionDomain;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The domain tabs of the wizard's first step (MIS-5 / L6 §5).
 *
 * <p><b>Every pixel of the rule belongs to the object that sits on it.</b> The strip is a single
 * row as tall as a tab, and the line that becomes the panel's top edge is drawn on its bottom pixel
 * by whichever child covers that column:
 *
 * <ul>
 *   <li>a closed tab draws it in its own texture — {@code tab-idle} carries a bottom border;
 *   <li>the open tab draws its own fill there instead — {@code tab-active} has none, which is the
 *       joint;
 *   <li>the gaps between tabs and the width beyond them are filled by {@link #rule} columns, whose
 *       bottom pixel is the same opaque line.
 * </ul>
 *
 * <p>The panel below carries no top border, so nothing else draws in that band.
 *
 * <p><b>Three constructions were tried before this one, and the two that failed failed for the same
 * reason</b>: they made one object's pixel depend on another's. The first composed a full-width
 * rule in a separate row and painted a hole under the open tab, which required a colour to match
 * the panel's exactly and a width recovered from a layout that had not necessarily run. The second
 * let the panel rise two pixels under the strip and reordered the two by depth, which made the
 * frame vanish outright. Here the open tab's bottom pixel is the open tab's own fill: there is no
 * seam between two objects to get right, because there is no seam.
 */
final class MissionDomainTabs {

  /** One orbitron-13 line plus {@link #TAB_PAD_Y} above and below. */
  static final float TAB_HEIGHT = 26f;

  /** 9-slice corner inset of {@code tab-rule}, an 8x8 texture. */
  private static final int RULE_BORDER = 3;

  private static final float TAB_PAD_X = 14f;
  private static final float TAB_PAD_Y = 4f;

  /** Space between two tabs, where the rule runs on unbroken. */
  private static final float TAB_GAP = 4f;

  /** 9-slice corner inset of {@code tab-active} and {@code tab-idle}, both 20x20. */
  private static final int TAB_BORDER = 8;

  private final Container root;
  private final Set<MissionDomain> enabled;
  private final Map<MissionDomain, Container> boxes = new EnumMap<>(MissionDomain.class);
  private final Map<MissionDomain, Label> labels = new EnumMap<>(MissionDomain.class);
  private MissionDomain active;
  private Consumer<MissionDomain> onSelected = domain -> {};

  /**
   * Builds the strip.
   *
   * @param initial the tab to open on
   * @param enabled the tabs that can be reached; a tab left out is drawn but inert
   * @param width the width the rule has to span, which is the panel's outer width
   */
  MissionDomainTabs(MissionDomain initial, Set<MissionDomain> enabled, float width) {
    this.active = initial;
    this.enabled = EnumSet.noneOf(MissionDomain.class);
    this.enabled.addAll(enabled);

    root = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    root.setBackground(null);

    float used = 0f;
    MissionDomain[] domains = MissionDomain.values();
    for (int i = 0; i < domains.length; i++) {
      if (i > 0) {
        root.addChild(rule(TAB_GAP));
        used += TAB_GAP;
      }
      MissionDomain domain = domains[i];
      Container box = buildTab(domain);
      boxes.put(domain, box);
      root.addChild(box);
      used += box.getPreferredSize().x;
    }
    if (width - used > 0f) {
      root.addChild(rule(width - used));
    }

    applyStates();
  }

  private Container buildTab(MissionDomain domain) {
    Container box = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    Label label = new Label(domain.label(), FormStyles.STYLE);
    label.setFont(UiKit.orbitron(13));
    box.addChild(label);
    labels.put(domain, label);
    // Sized here rather than left to the layout: the width is arithmetic — the label plus its
    // padding — and reading it back off the box answered the bare label until the background, which
    // carries that padding as its margin, had been installed.
    box.setPreferredSize(new Vector3f(label.getPreferredSize().x + 2 * TAB_PAD_X, TAB_HEIGHT, 0));

    if (enabled.contains(domain)) {
      MouseEventControl.addListenersToSpatial(
          box,
          new DefaultMouseListener() {
            @Override
            public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
              if (domain != active) {
                label.setColor(FormStyles.TEXT_PRIMARY);
              }
            }

            @Override
            public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
              if (domain != active) {
                label.setColor(FormStyles.TEXT_SECONDARY);
              }
            }

            @Override
            public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
              onSelected.accept(domain);
            }
          });
    }
    return box;
  }

  /**
   * A column as tall as a tab, transparent but for the rule along its bottom.
   *
   * <p>A column and not a one-pixel row laid beneath the tabs: kept in the same row, its line lands
   * on the same scanline as a closed tab's bottom border, with no chance of being off by one.
   *
   * <p><b>Drawn from a texture, not from a colour.</b> A flat one-pixel quad of {@code #1a3a5c} is
   * the right hue and still reads as a different line: every border in this atlas is drawn 1.7 px
   * wide with antialiasing — a full pixel then one at about 70 % — so a hard single pixel looks
   * thinner and brighter beside the frame it is supposed to continue. {@code tab-rule} carries that
   * same profile, from the same generator.
   *
   * @param columnWidth the width to span
   * @return the column
   */
  private static Container rule(float columnWidth) {
    Container column = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    column.setPreferredSize(new Vector3f(columnWidth, TAB_HEIGHT, 0));
    column.setBackground(UiKit.wizardBg9("tab-rule", RULE_BORDER));
    return column;
  }

  Container getNode() {
    return root;
  }

  /**
   * Sets what to do when a reachable tab is clicked.
   *
   * <p>The strip does not move itself: the step mounts the grid and calls {@link #setActive}, the
   * same split {@code ModeSegmentedControl} uses so that the control never holds a selection the
   * screen has not applied.
   *
   * @param action the listener, called with the clicked domain
   */
  void setOnSelected(Consumer<MissionDomain> action) {
    this.onSelected = action != null ? action : domain -> {};
  }

  void setActive(MissionDomain domain) {
    if (domain == active || !enabled.contains(domain)) {
      return;
    }
    active = domain;
    applyStates();
  }

  private void applyStates() {
    for (MissionDomain domain : MissionDomain.values()) {
      boolean open = domain == active;
      // The 9-slice's margin is the tab's padding: Lemur draws the skin at the box's full size and
      // lays the label inside the margin, so an InsetsComponent on top would pad it twice.
      TbtQuadBackgroundComponent skin =
          UiKit.wizardBg9(open ? "tab-active" : "tab-idle", TAB_BORDER);
      skin.setMargin(TAB_PAD_X, TAB_PAD_Y);
      boxes.get(domain).setBackground(skin);
      labels.get(domain).setColor(colorOf(domain, open));
    }
  }

  /**
   * Three states rather than two: the open tab, a closed one that can be opened, and one that
   * cannot because every card behind it is inert. The last is what edit mode produces, and saying
   * so in the label spares the click that would otherwise have discovered it.
   */
  private ColorRGBA colorOf(MissionDomain domain, boolean open) {
    if (open) {
      return FormStyles.TEXT_PRIMARY;
    }
    return enabled.contains(domain) ? FormStyles.TEXT_SECONDARY : FormStyles.TEXT_LO;
  }
}
