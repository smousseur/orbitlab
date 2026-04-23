package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA; // <-- Nouvel import indispensable
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import java.util.List;
import java.util.function.Consumer;

public class PopupList {

  private static final float TRIGGER_HEIGHT = 36f;
  private static final float OPTION_HEIGHT = 30f;
  private static final float PAD_X = 12f;

  private final Container root;
  private final Label valueLabel;
  private final Container popup;
  private boolean open = false;
  private String selectedValue;
  private Consumer<String> onSelectListener;

  public PopupList(float width, List<String> options, String defaultValue) {
    this.selectedValue = defaultValue;

    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);

    Container trigger =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None), (String) null));
    trigger.setPreferredSize(new Vector3f(width, TRIGGER_HEIGHT, 0));
    trigger.setBackground(MissionWizardStyles.inputBg());
    trigger.setInsetsComponent(new InsetsComponent(new Insets3f(0, PAD_X, 0, PAD_X)));

    float chevronW = 12f;

    valueLabel = trigger.addChild(new Label(defaultValue, MissionWizardStyles.STYLE));
    valueLabel.setFont(UiKit.ibmPlexMono(11));
    valueLabel.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
    valueLabel.setPreferredSize(new Vector3f(width, TRIGGER_HEIGHT, 0));
    valueLabel.setTextHAlignment(HAlignment.Left);
    valueLabel.setTextVAlignment(VAlignment.Center);

    Container chevron = trigger.addChild(UiKit.wizardIcon("icon-caret-down", chevronW, 8));

    popup = new Container(new BoxLayout(Axis.Y, FillMode.None), MissionWizardStyles.STYLE);
    popup.setBackground(UiKit.wizardBg9("input", 8));
    popup.setInsets(new Insets3f(0, 12, 0, 0));

    for (String option : options) {
      Container row = popup.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
      row.setPreferredSize(new Vector3f(width + 2 * PAD_X, OPTION_HEIGHT, 0));
      // row.setInsetsComponent(new InsetsComponent(new Insets3f(0, PAD_X, 0, PAD_X)));

      TbtQuadBackgroundComponent rowBg = UiKit.wizardBg9("btn-primary", 8);
      rowBg.setColor(new ColorRGBA(1f, 1f, 1f, 0f));
      row.setBackground(rowBg);

      Label optLabel = row.addChild(new Label(option, MissionWizardStyles.STYLE));
      optLabel.setFont(UiKit.ibmPlexMono(11));
      optLabel.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
      optLabel.setPreferredSize(new Vector3f(width /*- (2 * PAD_X)*/, OPTION_HEIGHT, 0));
      optLabel.setTextHAlignment(HAlignment.Left);
      optLabel.setTextVAlignment(VAlignment.Center);

      // optLabel.setInsetsComponent(new InsetsComponent(new Insets3f(0, 12, 0, 0)));
      MouseEventControl.addListenersToSpatial(
          row,
          new DefaultMouseListener() {
            @Override
            public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
              rowBg.setColor(ColorRGBA.White);
            }

            @Override
            public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
              rowBg.setColor(new ColorRGBA(1f, 1f, 1f, 0f));
            }

            @Override
            public void click(MouseButtonEvent evt, Spatial t, Spatial c) {
              selectedValue = option;
              valueLabel.setText(option);

              if (onSelectListener != null) {
                onSelectListener.accept(option);
              }

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

  public void setOnSelect(Consumer<String> listener) {
    this.onSelectListener = listener;
  }

  private void openPopup() {
    if (!open) {
      root.attachChild(popup);
      popup.setSize(popup.getPreferredSize());
      popup.setLocalTranslation(0, -TRIGGER_HEIGHT, 100f);
      open = true;
    }
  }

  private void closePopup() {
    if (open) {
      popup.removeFromParent();
      open = false;
    }
  }
}
