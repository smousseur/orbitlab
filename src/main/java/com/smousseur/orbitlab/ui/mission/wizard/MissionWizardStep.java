package com.smousseur.orbitlab.ui.mission.wizard;

/**
 * The wizard's steps, in the order they are walked.
 *
 * <p><b>SITE comes before PARAMETERS</b>, and that is a MIS-7 P2 change (spec {@code
 * docs/earth-orbit/02-wizard-orbites-terrestres.md} §5). The target inclination is bounded by the
 * launch latitude — a site at {@code φ} reaches {@code [φ, 180° − φ]} and nothing else — so asking
 * for the orbit before the site meant showing a field that could not be bounded, nor its refusal
 * explained. It also reads better: where one leaves from, then where one is going.
 */
public enum MissionWizardStep {
  MISSION(0, "MISSION"),
  SITE(1, "SITE"),
  PARAMETERS(2, "PARAMETERS"),
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
