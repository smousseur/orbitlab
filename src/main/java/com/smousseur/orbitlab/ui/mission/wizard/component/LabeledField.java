package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class LabeledField {

  private final Container root;

  public LabeledField(String labelText, Panel input, String helperText) {
    this(labelText, input, helperText, null);
  }

  public LabeledField(String labelText, Panel input, String helperText, String iconPath) {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);

    Container labelRow =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    labelRow.setBackground(null);

    if (iconPath != null) {
      labelRow.addChild(UiKit.iconPlaceholder(iconPath, 14, 14));
      Label spacer = labelRow.addChild(new Label(" ", MissionWizardStyles.STYLE));
      spacer.setBackground(null);
    }

    Label label = labelRow.addChild(new Label(labelText, MissionWizardStyles.STYLE));
    label.setFont(UiKit.rajdhani(12));
    label.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    root.addChild(input);

    if (helperText != null) {
      Label helper =
          root.addChild(new Label(helperText, MissionWizardStyles.STYLE));
      helper.setFont(UiKit.mono(10));
      helper.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    }
  }

  public Container getNode() {
    return root;
  }
}
