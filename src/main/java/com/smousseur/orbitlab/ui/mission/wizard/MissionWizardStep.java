package com.smousseur.orbitlab.ui.mission.wizard;

public enum MissionWizardStep {
  MISSION(0, "MISSION"),
  PARAMETERS(1, "PARAMETERS"),
  SITE(2, "SITE"),
  LAUNCHER(3, "LAUNCHER");

  private final int index;
  private final String label;

  MissionWizardStep(int index, String label) {
    this.index = index;
    this.label = label;
  }

  public int index() {
    return index;
  }

  public String label() {
    return label;
  }

  public static final int COUNT = values().length;

  public MissionWizardStep next() {
    int i = ordinal() + 1;
    MissionWizardStep[] v = values();
    return i < v.length ? v[i] : null;
  }

  public MissionWizardStep previous() {
    int i = ordinal() - 1;
    return i >= 0 ? values()[i] : null;
  }
}
