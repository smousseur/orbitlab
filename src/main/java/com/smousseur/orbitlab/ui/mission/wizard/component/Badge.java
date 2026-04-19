package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class Badge {

  public enum Variant {
    SUCCESS,
    WARNING,
    MUTED
  }

  private final Container root;

  public Badge(String text, Variant variant) {
    root = new Container(new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
    root.setInsetsComponent(new InsetsComponent(new Insets3f(3, 8, 3, 8)));

    String bgTex;
    ColorRGBA fg;
    String iconName = null;
    switch (variant) {
      case SUCCESS -> {
        bgTex = "badge-ready";
        fg = MissionWizardStyles.WIZARD_SUCCESS;
        iconName = "icon-check-success";
      }
      case WARNING -> {
        bgTex = "badge-wip";
        fg = MissionWizardStyles.WIZARD_WARNING;
      }
      default -> {
        bgTex = "badge-ready";
        fg = MissionWizardStyles.WIZARD_TEXT_SECONDARY;
      }
    }

    root.setBackground(UiKit.wizardBg9(bgTex, 7));

    if (iconName != null) {
      root.addChild(UiKit.wizardIcon(iconName, 10, 10));
      root.addChild(UiKit.hSpacer(4));
    }

    Label label = root.addChild(new Label(text, MissionWizardStyles.STYLE));
    label.setFont(UiKit.ibmPlexMono(11));
    label.setColor(fg);
    label.setPreferredSize(new Vector3f(label.getPreferredSize().x, label.getPreferredSize().y, 0));
  }

  public Container getNode() {
    return root;
  }
}
