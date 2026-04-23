package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BorderLayout; // <-- NOUVEL IMPORT
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
  private final Container trigger;
  private final Label valueLabel;
  private final Container popup;
  private final float popupDeltaWidth;
  private final float popupDeltaPosX;
  private boolean open = false;
  private String selectedValue;
  private Consumer<String> onSelectListener;

  public PopupList(
      float width,
      float popupDeltaWidth,
      float popupDeltaPosX,
      List<String> options,
      String defaultValue) {
    this.selectedValue = defaultValue;
    this.popupDeltaWidth = popupDeltaWidth;
    this.popupDeltaPosX = popupDeltaPosX;
    // ROOT : purement structurel, on lui retire tout style
    root = new Container(new BoxLayout(Axis.Y, FillMode.None), (String) null);
    root.setBackground(null);

    trigger = root.addChild(new Container(MissionWizardStyles.STYLE));
    trigger.setLayout(new BorderLayout());
    trigger.setPreferredSize(new Vector3f(width, TRIGGER_HEIGHT, 0));

    TbtQuadBackgroundComponent triggerBg = MissionWizardStyles.inputBg();
    triggerBg.setMargin(0, 0);
    trigger.setBackground(triggerBg);
    trigger.setInsetsComponent(new InsetsComponent(new Insets3f(0, PAD_X, 0, PAD_X)));

    valueLabel = new Label(defaultValue, MissionWizardStyles.STYLE);
    valueLabel.setInsets(new Insets3f(0, PAD_X, 0, 0));
    valueLabel.setFont(UiKit.ibmPlexMono(11));
    valueLabel.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
    valueLabel.setTextHAlignment(HAlignment.Left);
    valueLabel.setTextVAlignment(VAlignment.Center);
    trigger.addChild(valueLabel, BorderLayout.Position.Center);

    Container rightSide = new Container(new BoxLayout(Axis.X, FillMode.None), (String) null);
    rightSide.setBackground(null);
    rightSide.addChild(UiKit.hSpacer(8f));

    Container chevronWrap = new Container(new BoxLayout(Axis.Y, FillMode.None), (String) null);
    chevronWrap.setBackground(null);
    float vPad = (TRIGGER_HEIGHT - 8f) * 0.5f;
    chevronWrap.addChild(UiKit.vSpacer(vPad));

    Container chevronIcon = UiKit.wizardIcon("icon-caret-down", 12f, 8f);
    chevronIcon.setBorder(null);
    chevronIcon.setInsetsComponent(new InsetsComponent(new Insets3f(0, 0, 0, 5)));
    chevronWrap.addChild(chevronIcon);
    chevronWrap.addChild(UiKit.vSpacer(vPad));
    rightSide.addChild(chevronWrap);
    trigger.addChild(rightSide, BorderLayout.Position.East);

    popup = new Container(MissionWizardStyles.STYLE);
    popup.setLayout(new BoxLayout(Axis.Y, FillMode.None));
    TbtQuadBackgroundComponent popupBg = UiKit.wizardBg9("input", 8);
    popupBg.setMargin(0, 0);
    popup.setBackground(popupBg);
    popup.setInsetsComponent(new InsetsComponent(new Insets3f(0, 0, 0, 0)));

    for (String option : options) {
      Container row = popup.addChild(new Container(new BorderLayout(), (String) null));
      row.setPreferredSize(new Vector3f(width, OPTION_HEIGHT, 0));
      row.setInsetsComponent(new InsetsComponent(new Insets3f(0, 0, 0, 0)));

      TbtQuadBackgroundComponent rowBg = UiKit.wizardBg9("btn-primary", 8);
      rowBg.setMargin(0, 0);
      rowBg.setColor(new ColorRGBA(1f, 1f, 1f, 0f));
      row.setBackground(rowBg);
      Label optLabel = new Label(option, MissionWizardStyles.STYLE);
      optLabel.setInsetsComponent(new InsetsComponent(new Insets3f(0, PAD_X, 0, PAD_X)));
      optLabel.setFont(UiKit.ibmPlexMono(11));
      optLabel.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
      optLabel.setTextHAlignment(HAlignment.Left);
      optLabel.setTextVAlignment(VAlignment.Center);
      row.addChild(optLabel, BorderLayout.Position.Center);

      MouseEventControl.addListenersToSpatial(
          row,
          new DefaultMouseListener() {
            @Override
            public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
              rowBg.setColor(new ColorRGBA(1f, 1f, 1f, 0.33f));
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
            TbtQuadBackgroundComponent focusBg = MissionWizardStyles.inputFocusBg();
            focusBg.setMargin(0, 0);
            trigger.setBackground(focusBg);
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
            if (!open) {
              TbtQuadBackgroundComponent baseBg = MissionWizardStyles.inputBg();
              baseBg.setMargin(0, 0);
              trigger.setBackground(baseBg);
            }
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
      popup.setSize(popup.getPreferredSize().add(new Vector3f(popupDeltaWidth, 0, 0)));
      popup.setLocalTranslation(
          trigger.getLocalTranslation().x + popupDeltaPosX,
          trigger.getLocalTranslation().y - TRIGGER_HEIGHT,
          100f);
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
