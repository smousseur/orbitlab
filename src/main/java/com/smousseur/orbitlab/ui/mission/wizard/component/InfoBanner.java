package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class InfoBanner {

  public enum Variant {
    INFO,
    WARNING
  }

  private final Container root;

  public InfoBanner(String text, Variant variant) {
    ColorRGBA barColor =
        (variant == Variant.INFO)
            ? MissionWizardStyles.WIZARD_ACCENT
            : MissionWizardStyles.WIZARD_WARNING;

    root = new Container(new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
    root.setPreferredSize(new Vector3f(0, 48, 0));
    root.setBackground(MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_CARD));

    Container bar = root.addChild(new Container());
    bar.setPreferredSize(new Vector3f(4, 48, 0));
    bar.setBackground(new QuadBackgroundComponent(barColor));

    Container body = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    body.setBackground(null);

    String iconPath =
        (variant == Variant.INFO) ? "interface/wizard/info.png" : "interface/wizard/warning.png";
    body.addChild(MissionWizardStyles.iconPlaceholder(iconPath, 16, 16));

    Label msg = body.addChild(new Label(text, MissionWizardStyles.STYLE));
    msg.setFont(MissionWizardStyles.mono(12));
    msg.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
  }

  public Container getNode() {
    return root;
  }
}
