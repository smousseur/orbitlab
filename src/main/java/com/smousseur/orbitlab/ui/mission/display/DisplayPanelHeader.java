package com.smousseur.orbitlab.ui.mission.display;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;

final class DisplayPanelHeader {

  static final float HEIGHT = 36f;

  private final Container root;

  DisplayPanelHeader(float totalWidth, Runnable onManage) {
    root = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(totalWidth, HEIGHT, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(0, 12, 0, 10)));
    root.setBackground(null);

    Label title = root.addChild(new Label("MISSIONS", FormStyles.STYLE));
    title.setFont(UiKit.sora(13));
    title.setColor(FormStyles.TEXT_PRIMARY);
    title.setTextHAlignment(HAlignment.Left);
    title.setTextVAlignment(VAlignment.Center);
    float titleWidth =
        totalWidth - 12 - 10 - DisplayRowIcons.ICON_SIZE - 6 - manageLabelWidth();
    title.setPreferredSize(new Vector3f(titleWidth, HEIGHT, 0));

    root.addChild(DisplayRowIcons.vCenter(DisplayRowIcons.manageIconButton(onManage), HEIGHT));
    root.addChild(UiKit.hSpacer(6));

    Label manageLabel = root.addChild(new Label("Manage", FormStyles.STYLE));
    manageLabel.setFont(UiKit.sora(12));
    manageLabel.setColor(FormStyles.TEXT_SECONDARY);
    manageLabel.setTextHAlignment(HAlignment.Left);
    manageLabel.setTextVAlignment(VAlignment.Center);
    manageLabel.setPreferredSize(new Vector3f(manageLabelWidth(), HEIGHT, 0));
  }

  private static float manageLabelWidth() {
    return 56f;
  }

  Container getNode() {
    return root;
  }
}
