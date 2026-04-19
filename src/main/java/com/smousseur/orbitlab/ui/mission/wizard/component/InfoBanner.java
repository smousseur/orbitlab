package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class InfoBanner {

  public enum Variant {
    INFO,
    WARNING
  }

  private final Container root;

  public InfoBanner(String text, Variant variant) {
    ColorRGBA textColor =
        (variant == Variant.INFO)
            ? MissionWizardStyles.WIZARD_CYAN
            : MissionWizardStyles.WIZARD_WARNING;
    String iconName = (variant == Variant.INFO) ? "icon-info" : "lbl-warning";

    root = new Container(new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
    root.setPreferredSize(new Vector3f(0, 42, 0));
    root.setBackground(UiKit.wizardBg9("info-banner", 8));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(12, 14, 12, 14)));

    root.addChild(UiKit.wizardIcon(iconName, 14, 14));
    root.addChild(UiKit.hSpacer(10));

    Label msg = root.addChild(new Label(text, MissionWizardStyles.STYLE));
    msg.setFont(UiKit.ibmPlexMono(11));
    msg.setColor(textColor);
  }

  public Container getNode() {
    return root;
  }
}
