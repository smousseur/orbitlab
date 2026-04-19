package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class SegmentedControl {

  private final Container root;
  private final Button[] buttons;
  private int selectedIndex = -1;

  public SegmentedControl(String... labels) {
    root = new Container(new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
    root.setBackground(UiKit.wizardBg9("toggle-group", 8));
    buttons = new Button[labels.length];

    for (int i = 0; i < labels.length; i++) {
      final int idx = i;
      Button btn = new Button(labels[i], MissionWizardStyles.STYLE);
      btn.setFont(UiKit.sora(13));
      btn.setBackground(null);
      btn.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
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
      if (i == index) {
        buttons[i].setBackground(UiKit.wizardBg9("toggle-btn-active", 6));
        buttons[i].setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
      } else {
        buttons[i].setBackground(null);
        buttons[i].setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
      }
    }
    return this;
  }
}
