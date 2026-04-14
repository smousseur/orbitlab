package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class ProgressBar {

  private final float trackWidth;
  private final float trackHeight;
  private final Container root;
  private final Container fill;

  public ProgressBar(float width, float height) {
    this.trackWidth = width;
    this.trackHeight = height;

    root = new Container();
    root.setPreferredSize(new Vector3f(width, height, 0));
    root.setBackground(
        new QuadBackgroundComponent(
            new ColorRGBA(
                MissionWizardStyles.WIZARD_BORDER.r,
                MissionWizardStyles.WIZARD_BORDER.g,
                MissionWizardStyles.WIZARD_BORDER.b,
                0.50f)));

    fill = new Container();
    fill.setPreferredSize(new Vector3f(0, height, 0));
    fill.setBackground(new QuadBackgroundComponent(MissionWizardStyles.WIZARD_ACCENT));
    root.attachChild(fill);
  }

  public Container getNode() {
    return root;
  }

  public void setProgress(float progress) {
    float clamped = Math.max(0f, Math.min(1f, progress));
    fill.setPreferredSize(new Vector3f(trackWidth * clamped, trackHeight, 0));
  }
}
