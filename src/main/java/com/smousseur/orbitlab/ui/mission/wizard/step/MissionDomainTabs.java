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
import com.simsilica.lemur.component.QuadBackgroundComponent;
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
 * The domain tabs of the wizard's first step, and the rule they sit on (MIS-5 / L6 §5).
 *
 * <p><b>The open tab is welded to the panel below it.</b> {@code tab-active} carries a border on
 * its left, top and right and none at its bottom, and its fill is the panel's own; the panel, in
 * turn, has no top border. The line between them is drawn <em>here</em>, as a second row one pixel
 * high: a coloured segment under every closed tab, under every gap and across the remaining width,
 * and a transparent one under the open tab. The rule is therefore continuous everywhere except
 * where the open tab meets its content, which is what makes it read as a folder rather than as a
 * row of buttons resting on a line.
 *
 * <p><b>Nothing overlaps.</b> The alternative was to keep a fully bordered panel and pull the tab
 * two pixels down over its top edge, which would have made the joint depend on a negative inset and
 * on sibling draw order — the wizard already carries one such inset in {@code WizardStepper} and
 * does not need a second. Composing the rule costs one texture more and no layout trick.
 *
 * <p>Vertical budget: a tab box is one orbitron-13 line (18 px) plus {@link #TAB_PAD_Y} above and
 * below, so 26 px, and the rule adds one — 27 px for the strip, of the 424 the content pane offers.
 */
final class MissionDomainTabs {

  /** Height of the rule that becomes the panel's top border. */
  private static final float LINE_HEIGHT = 1f;

  private static final float TAB_PAD_X = 14f;
  private static final float TAB_PAD_Y = 4f;

  /** Space between two tabs, filled by the rule like any other gap. */
  private static final float TAB_GAP = 4f;

  /** 9-slice corner inset of {@code tab-active} and {@code tab-idle}, both 20x20. */
  private static final int TAB_BORDER = 8;

  /**
   * The fill of {@code tab-active} and {@code tab-panel} (#0f2847), read off the atlas.
   *
   * <p>The row under the open tab has to be <em>painted</em> with it, not left empty: a transparent
   * pixel there shows the wizard shell behind (#0b1e35, darker than either), which drew exactly the
   * dark line the joint exists to remove.
   */
  private static final ColorRGBA PANEL_FILL = new ColorRGBA(0.059f, 0.157f, 0.278f, 1f);

  private final Container root;
  private final Container lineRow;
  private final float width;
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
    this.width = width;

    root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    root.setBackground(null);

    Container tabRow = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    tabRow.setBackground(null);
    MissionDomain[] domains = MissionDomain.values();
    for (int i = 0; i < domains.length; i++) {
      if (i > 0) {
        tabRow.addChild(UiKit.hSpacer(TAB_GAP));
      }
      MissionDomain domain = domains[i];
      Container box = buildTab(domain);
      boxes.put(domain, box);
      tabRow.addChild(box);
    }

    lineRow = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    lineRow.setBackground(null);

    applyStates();
  }

  private Container buildTab(MissionDomain domain) {
    Container box = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    Label label = new Label(domain.label(), FormStyles.STYLE);
    label.setFont(UiKit.orbitron(13));
    box.addChild(label);
    labels.put(domain, label);

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
    rebuildLine();
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

  /**
   * Lays the rule under the strip, leaving a hole under the open tab.
   *
   * <p>The hole is a painted segment, not an absent one — see {@link #PANEL_FILL}.
   *
   * <p>Rebuilt on every change rather than resized: the segments are as many as the tabs plus the
   * remainder, and their widths follow the label widths, which the font decides.
   */
  private void rebuildLine() {
    lineRow.clearChildren();
    float used = 0f;
    MissionDomain[] domains = MissionDomain.values();
    for (int i = 0; i < domains.length; i++) {
      if (i > 0) {
        lineRow.addChild(segment(TAB_GAP, FormStyles.BORDER));
        used += TAB_GAP;
      }
      MissionDomain domain = domains[i];
      float tabWidth = boxes.get(domain).getPreferredSize().x;
      lineRow.addChild(
          domain == active ? segment(tabWidth, PANEL_FILL) : segment(tabWidth, FormStyles.BORDER));
      used += tabWidth;
    }
    if (width - used > 0f) {
      lineRow.addChild(segment(width - used, FormStyles.BORDER));
    }
  }

  private static Container segment(float segmentWidth, ColorRGBA color) {
    Container line = new Container();
    line.setPreferredSize(new Vector3f(segmentWidth, LINE_HEIGHT, 0));
    line.setBackground(new QuadBackgroundComponent(color));
    return line;
  }
}
