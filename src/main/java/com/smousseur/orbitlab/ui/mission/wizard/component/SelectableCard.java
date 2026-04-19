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
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class SelectableCard {

  public enum State {
    IDLE,
    HOVER,
    SELECTED,
    DISABLED
  }

  private static final float GAP_ICON_TITLE = 8f;
  private static final float GAP_TITLE_SUBTITLE = 2f;
  private static final float GAP_SUBTITLE_VALUE = 2f;
  private static final float GAP_VALUE_BADGE = 10f;

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

    Container content =
        new Container(new BoxLayout(Axis.Y, FillMode.None), MissionWizardStyles.STYLE);
    content.setBackground(null);

    Container iconNode;
    if (iconPath != null) {
      iconNode = UiKit.iconPlaceholder(iconPath, iconSize, iconSize);
    } else {
      iconNode = new Container();
      iconNode.setPreferredSize(new Vector3f(iconSize, iconSize, 0));
      iconNode.setBackground(null);
    }
    content.addChild(centerH(iconNode, width));
    content.addChild(UiKit.vSpacer(GAP_ICON_TITLE));

    Label titleLabel = new Label(title, MissionWizardStyles.STYLE);
    titleLabel.setFont(UiKit.rajdhani(16));
    titleLabel.setTextHAlignment(HAlignment.Center);
    titleLabel.setPreferredSize(
        new Vector3f(width, titleLabel.getPreferredSize().y, 0));
    content.addChild(titleLabel);
    content.addChild(UiKit.vSpacer(GAP_TITLE_SUBTITLE));

    Label subtitleLabel = new Label(subtitle, MissionWizardStyles.STYLE);
    subtitleLabel.setFont(UiKit.rajdhani(11));
    subtitleLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    subtitleLabel.setTextHAlignment(HAlignment.Center);
    subtitleLabel.setPreferredSize(
        new Vector3f(width, subtitleLabel.getPreferredSize().y, 0));
    content.addChild(subtitleLabel);

    if (value != null) {
      content.addChild(UiKit.vSpacer(GAP_SUBTITLE_VALUE));
      Label valueLabel = new Label(value, MissionWizardStyles.STYLE);
      valueLabel.setFont(UiKit.mono(11));
      valueLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
      valueLabel.setTextHAlignment(HAlignment.Center);
      valueLabel.setPreferredSize(
          new Vector3f(width, valueLabel.getPreferredSize().y, 0));
      content.addChild(valueLabel);
    }

    if (badge != null) {
      content.addChild(UiKit.vSpacer(GAP_VALUE_BADGE));
      content.addChild(centerH(badge.getNode(), width));
    }

    float contentHeight = content.getPreferredSize().y;
    float vPad = Math.max(0f, (height - contentHeight) / 2f);
    root.addChild(UiKit.vSpacer(vPad));
    root.addChild(content);
    root.addChild(UiKit.vSpacer(vPad));

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

  private static Container centerH(Container child, float cardWidth) {
    Container row =
        new Container(new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
    row.setBackground(null);
    float childWidth = child.getPreferredSize().x;
    float pad = Math.max(0f, (cardWidth - childWidth) / 2f);
    row.addChild(UiKit.hSpacer(pad));
    row.addChild(child);
    row.addChild(UiKit.hSpacer(pad));
    return row;
  }

  public void applyState(State newState) {
    this.state = newState;
    switch (newState) {
      case IDLE ->
          root.setBackground(
              UiKit.gradientBackground(MissionWizardStyles.WIZARD_BG_CARD));
      case HOVER ->
          root.setBackground(
              UiKit.gradientBackground(
                  MissionWizardStyles.WIZARD_BG_CARD_HOVER));
      case SELECTED ->
          root.setBackground(
              UiKit.gradientBackground(MissionWizardStyles.WIZARD_SELECTED));
      case DISABLED ->
          root.setBackground(
              UiKit.gradientBackground(
                  new ColorRGBA(
                      MissionWizardStyles.WIZARD_BG_CARD.r,
                      MissionWizardStyles.WIZARD_BG_CARD.g,
                      MissionWizardStyles.WIZARD_BG_CARD.b,
                      0.30f)));
    }
  }
}
