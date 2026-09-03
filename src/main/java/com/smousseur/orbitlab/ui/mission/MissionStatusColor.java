package com.smousseur.orbitlab.ui.mission;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.ui.form.FormStyles;
import java.awt.*;
import java.util.stream.Stream;

public enum MissionStatusColor {
  UPDATING(FormStyles.TEXT_LO),
  CREATING(FormStyles.TEXT_LO),
  DRAFT(FormStyles.TEXT_SECONDARY),
  COMPUTING(FormStyles.WARNING),
  READY(FormStyles.SUCCESS),
  FAILED(FormStyles.DANGER);

  private final ColorRGBA color;

  MissionStatusColor(ColorRGBA color) {
    this.color = color;
  }

  private ColorRGBA getColor() {
    return color;
  }

  public static ColorRGBA forStatus(MissionStatus status) {
    return Stream.of(MissionStatusColor.values())
        .filter(mc -> mc.name().equals(status.name()))
        .map(MissionStatusColor::getColor)
        .findFirst()
        .orElseThrow(() -> new OrbitlabException("Unknown color for mission status: " + status));
  }
}
