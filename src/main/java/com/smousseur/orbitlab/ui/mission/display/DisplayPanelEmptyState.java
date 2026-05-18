package com.smousseur.orbitlab.ui.mission.display;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
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
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;

final class DisplayPanelEmptyState {

  static final float HEIGHT = 110f;

  private final Container root;
  private Runnable onCreate = () -> {};

  DisplayPanelEmptyState(float totalWidth) {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(totalWidth, HEIGHT, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(14, 12, 14, 12)));
    root.setBackground(null);

    Label message = root.addChild(new Label("No mission computed yet.", FormStyles.STYLE));
    message.setFont(UiKit.sora(12));
    message.setColor(FormStyles.TEXT_SECONDARY);
    message.setTextHAlignment(HAlignment.Center);
    message.setTextVAlignment(VAlignment.Center);
    message.setPreferredSize(new Vector3f(totalWidth - 24, 28, 0));

    root.addChild(UiKit.vSpacer(12));

    Button createButton = new Button("+ Create mission", FormStyles.STYLE);
    createButton.setFont(UiKit.sora(12));
    createButton.setColor(FormStyles.TEXT_PRIMARY);
    createButton.setBackground(UiKit.gradientBackground(AppStyles.ICE_ACCENT));
    createButton.setInsetsComponent(new InsetsComponent(new Insets3f(8, 16, 8, 16)));
    createButton.setTextHAlignment(HAlignment.Center);
    createButton.setTextVAlignment(VAlignment.Center);
    createButton.addClickCommands(src -> onCreate.run());

    Container centerWrap = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    centerWrap.setBackground(null);
    centerWrap.setPreferredSize(new Vector3f(totalWidth - 24, 36, 0));
    float buttonWidth = 140f;
    float side = Math.max(0f, ((totalWidth - 24) - buttonWidth) * 0.5f);
    centerWrap.addChild(UiKit.hSpacer(side));
    centerWrap.addChild(createButton);
    centerWrap.addChild(UiKit.hSpacer(side));
    root.addChild(centerWrap);

    MouseEventControl.addListenersToSpatial(
        createButton,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            onCreate.run();
            event.setConsumed();
          }
        });
  }

  void setOnCreate(Runnable r) {
    this.onCreate = r != null ? r : () -> {};
  }

  Container getNode() {
    return root;
  }
}
