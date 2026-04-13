package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Label;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class Badge {

  public enum Variant {
    SUCCESS,
    WARNING,
    MUTED
  }

  private final Container root;

  public Badge(String text, Variant variant) {
    root = new Container(MissionWizardStyles.STYLE);

    ColorRGBA bg;
    ColorRGBA fg;
    switch (variant) {
      case SUCCESS -> {
        bg =
            new ColorRGBA(
                MissionWizardStyles.WIZARD_SUCCESS.r,
                MissionWizardStyles.WIZARD_SUCCESS.g,
                MissionWizardStyles.WIZARD_SUCCESS.b,
                0.80f);
        fg = MissionWizardStyles.WIZARD_TEXT_PRIMARY;
      }
      case WARNING -> {
        bg =
            new ColorRGBA(
                MissionWizardStyles.WIZARD_WARNING.r,
                MissionWizardStyles.WIZARD_WARNING.g,
                MissionWizardStyles.WIZARD_WARNING.b,
                0.80f);
        fg = MissionWizardStyles.WIZARD_TEXT_PRIMARY;
      }
      default -> {
        bg = MissionWizardStyles.WIZARD_BG_CARD;
        fg = MissionWizardStyles.WIZARD_TEXT_SECONDARY;
      }
    }

    root.setBackground(MissionWizardStyles.createGradient(bg));
    Label label = root.addChild(new Label(text, MissionWizardStyles.STYLE));
    label.setFont(MissionWizardStyles.rajdhani(10));
    label.setColor(fg);
  }

  public Container getNode() {
    return root;
  }
}
