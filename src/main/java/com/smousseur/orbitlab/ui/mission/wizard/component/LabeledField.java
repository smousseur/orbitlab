package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class LabeledField {

  private static final float LABEL_ICON_SIZE = 14f;
  private static final float LABEL_GAP = 6f;
  private static final float INPUT_GAP = 6f;
  private static final float HELPER_GAP = 4f;

  private final Container root;

  public LabeledField(String labelText, Panel input, String helperText) {
    this(labelText, input, helperText, null);
  }

  public LabeledField(String labelText, Panel input, String helperText, String iconName) {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);

    Container labelRow =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    labelRow.setBackground(null);

    if (iconName != null) {
      labelRow.addChild(UiKit.wizardIcon(iconName, LABEL_ICON_SIZE, LABEL_ICON_SIZE));
      labelRow.addChild(UiKit.hSpacer(LABEL_GAP));
    }

    Label label = labelRow.addChild(new Label(labelText, MissionWizardStyles.STYLE));
    label.setFont(UiKit.ibmPlexMono(11));
    label.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(INPUT_GAP));
    root.addChild(input);

    if (helperText != null) {
      root.addChild(UiKit.vSpacer(HELPER_GAP));
      Label helper = root.addChild(new Label(helperText, MissionWizardStyles.STYLE));
      helper.setFont(UiKit.ibmPlexMono(11));
      helper.setColor(MissionWizardStyles.WIZARD_TEXT_LO);
    }
  }

  public Container getNode() {
    return root;
  }
}
