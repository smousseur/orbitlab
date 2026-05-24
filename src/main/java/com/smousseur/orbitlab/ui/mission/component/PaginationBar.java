package com.smousseur.orbitlab.ui.mission.component;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;

/**
 * Compact pagination chip with previous/next chevrons and a "Page X/Y" label. Reused by both the
 * mission display HUD and the mission roster modal.
 */
public final class PaginationBar {

  private static final float SIDE_PAD = 8f;
  private static final float LABEL_GAP = 4f;

  private final Container root;
  private final Container prevChevron;
  private final Container nextChevron;
  private final Label currentLabel;
  private final Label totalLabel;

  private Runnable onPrev = () -> {};
  private Runnable onNext = () -> {};
  private boolean prevEnabled = false;
  private boolean nextEnabled = false;

  public PaginationBar(float width, float height) {
    root = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(width, height, 0));
    TbtQuadBackgroundComponent bg = UiKit.wizardBg9("input", 8);
    bg.setMargin(0, 0);
    root.setBackground(bg);
    root.setInsetsComponent(new InsetsComponent(new Insets3f(0, SIDE_PAD, 0, SIDE_PAD)));

    prevChevron = buildChevron(height, true);
    root.addChild(prevChevron);

    root.addChild(UiKit.hSpacer(LABEL_GAP));

    Label pageLabel = new Label("Page ", FormStyles.STYLE);
    pageLabel.setFont(UiKit.sora(12));
    pageLabel.setColor(FormStyles.TEXT_SECONDARY);
    pageLabel.setTextVAlignment(VAlignment.Center);
    pageLabel.setPreferredSize(new Vector3f(pageLabel.getPreferredSize().x, height, 0));
    root.addChild(pageLabel);
    root.addChild(UiKit.hSpacer(LABEL_GAP));

    currentLabel = new Label("1", FormStyles.STYLE);
    currentLabel.setFont(UiKit.sora(12));
    currentLabel.setColor(FormStyles.TEXT_PRIMARY);
    currentLabel.setTextHAlignment(HAlignment.Left);
    currentLabel.setTextVAlignment(VAlignment.Center);
    currentLabel.setPreferredSize(new Vector3f(10f, height, 0));
    root.addChild(currentLabel);

    totalLabel = new Label("/1", FormStyles.STYLE);
    totalLabel.setFont(UiKit.sora(12));
    totalLabel.setColor(FormStyles.TEXT_SECONDARY);
    totalLabel.setTextHAlignment(HAlignment.Left);
    totalLabel.setTextVAlignment(VAlignment.Center);
    totalLabel.setPreferredSize(new Vector3f(18f, height, 0));
    root.addChild(totalLabel);

    root.addChild(UiKit.hSpacer(LABEL_GAP));

    nextChevron = buildChevron(height - 5, false);
    root.addChild(nextChevron);
    root.addChild(UiKit.hSpacer(SIDE_PAD));
  }

  public Container getNode() {
    return root;
  }

  public void setOnPrev(Runnable r) {
    this.onPrev = r != null ? r : () -> {};
  }

  public void setOnNext(Runnable r) {
    this.onNext = r != null ? r : () -> {};
  }

  /** Update the label and enable/disable chevrons based on the current page state. */
  public void refresh(int currentPage, int pageCount) {
    int total = Math.max(1, pageCount);
    int display = Math.min(Math.max(0, currentPage), total - 1) + 1;
    currentLabel.setText(String.valueOf(display));
    totalLabel.setText("/" + total);
    prevEnabled = currentPage > 0;
    nextEnabled = currentPage < total - 1;
    String texPrev = prevEnabled ? "icon-pager-prev" : "icon-pager-prev-disabled";
    String texNext = nextEnabled ? "icon-pager-next" : "icon-pager-next-disabled";
    prevChevron.setBackground(UiKit.flat(texPrev));
    nextChevron.setBackground(UiKit.flat(texNext));
    // prevLabel.setColor(prevEnabled ? FormStyles.TEXT_PRIMARY : FormStyles.TEXT_LO);
    // nextLabel.setColor(nextEnabled ? FormStyles.TEXT_PRIMARY : FormStyles.TEXT_LO);
  }

  private Container buildChevron(float size, boolean isPrev) {
    Container chevron = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    chevron.setPreferredSize(new Vector3f(size, size, 0));
    String glyph = isPrev ? "icon-pager-prev" : "icon-pager-next";
    chevron.setBackground(UiKit.flat(glyph));
    /*
       Label label = new Label(glyph, FormStyles.STYLE);
       label.setFont(UiKit.sora(14));
       label.setColor(FormStyles.TEXT_LO);
       label.setTextHAlignment(HAlignment.Center);
       label.setTextVAlignment(VAlignment.Center);
       label.setPreferredSize(new Vector3f(CHEVRON_WIDTH, size, 0));
    */
    // chevron.addChild(label);

    MouseEventControl.addListenersToSpatial(
        chevron,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
            boolean enabled = isPrev ? prevEnabled : nextEnabled;
            if (enabled) {
              chevron.setBackground(UiKit.flat(glyph + "-hover"));
            }
            // if (enabled) label.setColor(FormStyles.ACCENT_BRIGHT);
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
            boolean enabled = isPrev ? prevEnabled : nextEnabled;
            String tex = enabled ? glyph : glyph + "-disabled";
            chevron.setBackground(UiKit.flat(tex));
            // label.setColor(enabled ? FormStyles.TEXT_PRIMARY : FormStyles.TEXT_LO);
          }

          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            boolean enabled = isPrev ? prevEnabled : nextEnabled;
            if (enabled) {
              if (isPrev) onPrev.run();
              else onNext.run();
            }
            event.setConsumed();
          }
        });
    return chevron;
  }
}
