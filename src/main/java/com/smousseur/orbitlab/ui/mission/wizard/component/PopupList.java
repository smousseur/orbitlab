package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import java.util.List;

public class PopupList {

  private final Container root;
  private final Label valueLabel;
  private final Container popup;
  private boolean open = false;
  private String selectedValue;

  public PopupList(float width, List<String> options, String defaultValue) {
    this.selectedValue = defaultValue;

    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);

    Container trigger =
        root.addChild(
            new Container(
                new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE));
    trigger.setPreferredSize(new Vector3f(width, 32, 0));
    trigger.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_CARD));

    valueLabel =
        trigger.addChild(new Label(defaultValue, MissionWizardStyles.STYLE));
    valueLabel.setFont(MissionWizardStyles.mono(14));
    valueLabel.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    Label chevron = trigger.addChild(new Label("v", MissionWizardStyles.STYLE));
    chevron.setFont(MissionWizardStyles.mono(14));
    chevron.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    chevron.setTextHAlignment(HAlignment.Right);

    popup =
        new Container(
            new BoxLayout(Axis.Y, FillMode.None), MissionWizardStyles.STYLE);
    popup.setPreferredSize(new Vector3f(width, 0, 0));
    popup.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_DEEP));

    for (String option : options) {
      Container row =
          popup.addChild(
              new Container(new BoxLayout(Axis.X, FillMode.None)));
      row.setPreferredSize(new Vector3f(width, 28, 0));
      row.setBackground(
          MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_DEEP));

      Label optLabel =
          row.addChild(new Label(option, MissionWizardStyles.STYLE));
      optLabel.setFont(MissionWizardStyles.mono(14));
      optLabel.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

      MouseEventControl.addListenersToSpatial(
          row,
          new DefaultMouseListener() {
            @Override
            public void mouseEntered(
                MouseMotionEvent evt, Spatial t, Spatial c) {
              row.setBackground(
                  MissionWizardStyles.createGradient(
                      MissionWizardStyles.WIZARD_BG_CARD));
            }

            @Override
            public void mouseExited(
                MouseMotionEvent evt, Spatial t, Spatial c) {
              row.setBackground(
                  MissionWizardStyles.createGradient(
                      MissionWizardStyles.WIZARD_BG_DEEP));
            }

            @Override
            public void click(MouseButtonEvent evt, Spatial t, Spatial c) {
              selectedValue = option;
              valueLabel.setText(option);
              closePopup();
            }
          });
    }

    MouseEventControl.addListenersToSpatial(
        trigger,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent evt, Spatial t, Spatial c) {
            if (open) closePopup();
            else openPopup();
          }
        });
  }

  public Container getNode() {
    return root;
  }

  public String getSelectedValue() {
    return selectedValue;
  }

  private void openPopup() {
    if (!open) {
      root.addChild(popup);
      open = true;
    }
  }

  private void closePopup() {
    if (open) {
      root.removeChild(popup);
      open = false;
    }
  }
}
