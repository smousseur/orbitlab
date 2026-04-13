package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class SegmentedControl {

  private final Container root;
  private final Button[] buttons;
  private int selectedIndex = -1;

  public SegmentedControl(String... labels) {
    root =
        new Container(
            new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
    root.setBackground(null);
    buttons = new Button[labels.length];

    for (int i = 0; i < labels.length; i++) {
      final int idx = i;
      Button btn = new Button(labels[i], MissionWizardStyles.STYLE);
      btn.setFont(MissionWizardStyles.rajdhani(14));
      btn.setBackground(
          MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_CARD));
      btn.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
      btn.addClickCommands(src -> select(idx));
      buttons[i] = btn;
      root.addChild(btn);
    }
  }

  public Container getNode() {
    return root;
  }

  public SegmentedControl select(int index) {
    selectedIndex = index;
    for (int i = 0; i < buttons.length; i++) {
      buttons[i].setBackground(
          MissionWizardStyles.createGradient(
              i == index
                  ? MissionWizardStyles.WIZARD_ACCENT
                  : MissionWizardStyles.WIZARD_BG_CARD));
    }
    return this;
  }
}
