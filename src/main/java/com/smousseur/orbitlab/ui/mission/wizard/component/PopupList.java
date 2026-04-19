package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import java.util.List;

public class PopupList {

  private static final float TRIGGER_HEIGHT = 36f;
  private static final float OPTION_HEIGHT = 30f;
  private static final float PAD_X = 12f;

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
            new Container(new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE));
    trigger.setPreferredSize(new Vector3f(width, TRIGGER_HEIGHT, 0));
    trigger.setBackground(MissionWizardStyles.inputBg());
    trigger.setInsetsComponent(new InsetsComponent(new Insets3f(6, PAD_X, 6, PAD_X)));

    valueLabel = trigger.addChild(new Label(defaultValue, MissionWizardStyles.STYLE));
    valueLabel.setFont(UiKit.ibmPlexMono(11));
    valueLabel.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    trigger.addChild(UiKit.hSpacer(8));
    Container chevron = trigger.addChild(UiKit.wizardIcon("icon-caret-down", 12, 8));
    chevron.setBackground(chevron.getBackground());

    popup =
        new Container(new BoxLayout(Axis.Y, FillMode.None), MissionWizardStyles.STYLE);
    popup.setPreferredSize(new Vector3f(width, 0, 0));
    popup.setBackground(UiKit.wizardBg9("input", 8));

    for (String option : options) {
      Container row = popup.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
      row.setPreferredSize(new Vector3f(width, OPTION_HEIGHT, 0));
      row.setInsetsComponent(new InsetsComponent(new Insets3f(6, PAD_X, 6, PAD_X)));

      Label optLabel = row.addChild(new Label(option, MissionWizardStyles.STYLE));
      optLabel.setFont(UiKit.ibmPlexMono(11));
      optLabel.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

      MouseEventControl.addListenersToSpatial(
          row,
          new DefaultMouseListener() {
            @Override
            public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
              row.setBackground(UiKit.wizardBg9("card-launcher-hover", 10));
            }

            @Override
            public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
              row.setBackground(null);
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
          public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
            trigger.setBackground(MissionWizardStyles.inputFocusBg());
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
            if (!open) trigger.setBackground(MissionWizardStyles.inputBg());
          }

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
