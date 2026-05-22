package com.smousseur.orbitlab.ui.mission.display;

import com.jme3.input.event.MouseButtonEvent;
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
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;

final class DisplayPanelFooter {

  static final float HEIGHT = 30f;

  private final Container root;
  private final Label counter;
  private final Container hideAllButton;
  private final Label hideAllLabel;
  private Runnable onHideAll = () -> {};

  DisplayPanelFooter(float totalWidth) {
    root = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(totalWidth, HEIGHT, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(0, 12, 0, 12)));
    root.setBackground(null);

    counter = root.addChild(new Label("0 visible / 0 total", FormStyles.STYLE));
    counter.setFont(UiKit.sora(12));
    counter.setColor(FormStyles.TEXT_SECONDARY);
    counter.setTextHAlignment(HAlignment.Left);
    counter.setTextVAlignment(VAlignment.Center);
    float counterWidth = totalWidth - 12 - 12 - 75;
    counter.setPreferredSize(new Vector3f(counterWidth, HEIGHT, 0));

    hideAllButton = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    hideAllButton.setBackground(null);
    hideAllButton.setPreferredSize(new Vector3f(75f, HEIGHT, 0));
    hideAllLabel = hideAllButton.addChild(new Label("Hide all", FormStyles.STYLE));
    hideAllLabel.setFont(UiKit.sora(12));
    hideAllLabel.setColor(FormStyles.TEXT_SECONDARY);
    hideAllLabel.setTextHAlignment(HAlignment.Right);
    hideAllLabel.setTextVAlignment(VAlignment.Center);
    hideAllLabel.setPreferredSize(new Vector3f(55f, HEIGHT, 0));

    MouseEventControl.addListenersToSpatial(
        hideAllButton,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            onHideAll.run();
            event.setConsumed();
          }
        });
    root.addChild(hideAllButton);
  }

  void setOnHideAll(Runnable r) {
    this.onHideAll = r != null ? r : () -> {};
  }

  void refresh(int visibleCount, int totalCount) {
    counter.setText(visibleCount + " visible / " + totalCount + " total");
    hideAllLabel.setColor(visibleCount > 0 ? FormStyles.TEXT_PRIMARY : FormStyles.TEXT_LO);
  }

  Container getNode() {
    return root;
  }
}
