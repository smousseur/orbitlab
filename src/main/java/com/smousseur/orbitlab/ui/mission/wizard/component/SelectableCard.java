package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class SelectableCard {

  public enum State {
    IDLE,
    HOVER,
    SELECTED,
    DISABLED
  }

  private final Container root;
  private State state;

  public SelectableCard(
      float width,
      float height,
      String title,
      String subtitle,
      String value,
      Badge badge,
      State initial) {
    this(width, height, title, subtitle, value, badge, initial, null, 48);
  }

  public SelectableCard(
      float width,
      float height,
      String title,
      String subtitle,
      String value,
      Badge badge,
      State initial,
      String iconPath,
      float iconSize) {
    this.state = initial;

    root =
        new Container(
            new BoxLayout(Axis.Y, FillMode.None), MissionWizardStyles.STYLE);
    root.setPreferredSize(new Vector3f(width, height, 0));

    if (iconPath != null) {
      root.addChild(MissionWizardStyles.iconPlaceholder(iconPath, iconSize, iconSize));
    } else {
      Container iconSlot = root.addChild(new Container());
      iconSlot.setPreferredSize(new Vector3f(iconSize, iconSize, 0));
      iconSlot.setBackground(null);
    }

    Label titleLabel = root.addChild(new Label(title, MissionWizardStyles.STYLE));
    titleLabel.setFont(MissionWizardStyles.rajdhani(16));
    titleLabel.setTextHAlignment(HAlignment.Center);

    Label subtitleLabel =
        root.addChild(new Label(subtitle, MissionWizardStyles.STYLE));
    subtitleLabel.setFont(MissionWizardStyles.rajdhani(11));
    subtitleLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    subtitleLabel.setTextHAlignment(HAlignment.Center);

    if (value != null) {
      Label valueLabel = root.addChild(new Label(value, MissionWizardStyles.STYLE));
      valueLabel.setFont(MissionWizardStyles.mono(11));
      valueLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
      valueLabel.setTextHAlignment(HAlignment.Center);
    }

    if (badge != null) {
      root.addChild(badge.getNode());
    }

    applyState(initial);

    if (initial != State.DISABLED) {
      MouseEventControl.addListenersToSpatial(
          root,
          new DefaultMouseListener() {
            @Override
            public void mouseEntered(
                MouseMotionEvent event, Spatial target, Spatial capture) {
              if (state != State.SELECTED) applyState(State.HOVER);
            }

            @Override
            public void mouseExited(
                MouseMotionEvent event, Spatial target, Spatial capture) {
              if (state != State.SELECTED) applyState(State.IDLE);
            }

            @Override
            public void click(
                MouseButtonEvent event, Spatial target, Spatial capture) {
              applyState(State.SELECTED);
            }
          });
    }
  }

  public Container getNode() {
    return root;
  }

  public void applyState(State newState) {
    this.state = newState;
    switch (newState) {
      case IDLE ->
          root.setBackground(
              MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_CARD));
      case HOVER ->
          root.setBackground(
              MissionWizardStyles.createGradient(
                  MissionWizardStyles.WIZARD_BG_CARD_HOVER));
      case SELECTED ->
          root.setBackground(
              MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_SELECTED));
      case DISABLED ->
          root.setBackground(
              MissionWizardStyles.createGradient(
                  new ColorRGBA(
                      MissionWizardStyles.WIZARD_BG_CARD.r,
                      MissionWizardStyles.WIZARD_BG_CARD.g,
                      MissionWizardStyles.WIZARD_BG_CARD.b,
                      0.30f)));
    }
  }
}
