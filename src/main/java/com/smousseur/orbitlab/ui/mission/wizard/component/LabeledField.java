package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class LabeledField {

  private final Container root;

  public LabeledField(String labelText, Panel input, String helperText) {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);

    Label label =
        root.addChild(new Label(labelText, MissionWizardStyles.STYLE));
    label.setFont(MissionWizardStyles.rajdhani(12));
    label.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    root.addChild(input);

    if (helperText != null) {
      Label helper =
          root.addChild(new Label(helperText, MissionWizardStyles.STYLE));
      helper.setFont(MissionWizardStyles.mono(10));
      helper.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    }
  }

  public Container getNode() {
    return root;
  }
}
