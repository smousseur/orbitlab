package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.core.GuiControl;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.lemur.event.*;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class StepParameters {

  private static final float FIELD_W = 752f;
  private static final float FIELD_H = 36f;
  private static final float ROW_GAP = 16f;
  private static final float LABEL_ICON_SIZE = 14f;
  private static final float LABEL_GAP = 6f;
  private static final float LABEL_FIELD_GAP = 6f;
  private static final float SLIDER_ROW_H = 32f;
  private static final float SLIDER_TRACK_H = 2f;
  private static final float THUMB_SIZE = 16f;
  private static final float VALUE_FIELD_W = 80f;
  private static final float KM_LABEL_W = 22f;
  private static final float SLIDER_TEXT_GAP = 8f;
  private static final float SLIDER_W = FIELD_W - VALUE_FIELD_W - KM_LABEL_W - 2 * SLIDER_TEXT_GAP;

  private final Container root;

  private Slider altitudeSlider;
  private TextField altitudeField;
  private double altitudeMin;
  private double altitudeMax;
  private VersionedReference<Double> altitudeSliderRef;
  private boolean altitudeFieldWasFocused;

  public StepParameters() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(new QuadBackgroundComponent(new ColorRGBA(0, 0, 0, 0)));
    root.setPreferredSize(
        new Vector3f(
            MissionWizardStyles.WIZARD_CONTENT_WIDTH,
            MissionWizardStyles.WIZARD_CONTENT_HEIGHT,
            0));

    Label title = root.addChild(new Label("PARAMETERS — LEO", MissionWizardStyles.STYLE));
    title.setFont(UiKit.orbitron(13));
    title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(6));

    Label subtitle =
        root.addChild(new Label("// target orbit configuration", MissionWizardStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // --- Mission Name ---
    root.addChild(fieldLabelRow("MISSION NAME", "lbl-edit"));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    root.addChild(newInputField("ORBITLAB-LEO-001"));

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // --- Target Altitude ---
    root.addChild(fieldLabelRow("TARGET ALTITUDE", "lbl-ruler"));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    root.addChild(buildSliderRow());
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    root.addChild(rangeBoundsRow());

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // --- Launch Date ---
    root.addChild(fieldLabelRow("LAUNCH DATE", "lbl-clock"));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    root.addChild(newInputField("2026-06-15T06:00:00Z"));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    Label helper = root.addChild(new Label("UTC · Orekit epoch", MissionWizardStyles.STYLE));
    helper.setFont(UiKit.ibmPlexMono(11));
    helper.setColor(MissionWizardStyles.WIZARD_TEXT_LO);

    CursorEventControl.addListenersToSpatial(
        root,
        new DefaultCursorListener() {
          @Override
          public void cursorButtonEvent(CursorButtonEvent event, Spatial target, Spatial capture) {
            if (event.getButtonIndex() == 0 && event.isPressed()) {
              Spatial currentFocus = GuiGlobals.getInstance().getFocusManagerState().getFocus();
              if (currentFocus != null && target != currentFocus) {
                if (!(target instanceof TextField)) {
                  GuiGlobals.getInstance().requestFocus(null);
                }
              }
            }
          }
        });
  }

  public Container getNode() {
    return root;
  }

  public void update(float tpf) {
    if (altitudeSlider == null || altitudeField == null) return;

    if (altitudeSliderRef.update()) {
      altitudeField.setText(Long.toString(Math.round(altitudeSlider.getModel().getValue())));
    }

    Spatial focused = GuiGlobals.getInstance().getFocusManagerState().getFocus();
    boolean isFocused = focused == altitudeField;
    if (altitudeFieldWasFocused && !isFocused) {
      commitAltitudeFieldToSlider();
    }
    altitudeFieldWasFocused = isFocused;
  }

  private void commitAltitudeFieldToSlider() {
    try {
      double v = Double.parseDouble(altitudeField.getText().trim());
      v = Math.max(altitudeMin, Math.min(altitudeMax, v));
      altitudeSlider.getModel().setValue(v);
      altitudeSliderRef.update();
      altitudeField.setText(Long.toString(Math.round(v)));
    } catch (NumberFormatException e) {
      altitudeField.setText(Long.toString(Math.round(altitudeSlider.getModel().getValue())));
    }
  }

  private Container fieldLabelRow(String text, String iconName) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.addChild(UiKit.wizardIcon(iconName, LABEL_ICON_SIZE, LABEL_ICON_SIZE));
    row.addChild(UiKit.hSpacer(LABEL_GAP));
    Label label = row.addChild(new Label(text, MissionWizardStyles.STYLE));
    label.setFont(UiKit.ibmPlexMono(11));
    label.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    return row;
  }

  private Container rangeBoundsRow() {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    Label min = row.addChild(new Label("160 km", MissionWizardStyles.STYLE));
    min.setFont(UiKit.ibmPlexMono(11));
    min.setColor(MissionWizardStyles.WIZARD_TEXT_LO);

    Label max = new Label("2 000 km", MissionWizardStyles.STYLE);
    max.setFont(UiKit.ibmPlexMono(11));
    max.setColor(MissionWizardStyles.WIZARD_TEXT_LO);

    float used = min.getPreferredSize().x + max.getPreferredSize().x;
    float trailing = Math.max(0f, SLIDER_W - used);
    row.addChild(UiKit.hSpacer(trailing));
    row.addChild(max);
    return row;
  }

  private TextField newInputField(String value) {
    TextField f = new TextField(value, MissionWizardStyles.STYLE);
    f.setFont(UiKit.ibmPlexMono(13));
    f.setPreferredSize(new Vector3f(FIELD_W, FIELD_H, 0));
    f.setInsets(new Insets3f(0, 0, 10, 0));
    return f;
  }

  private Container buildSliderRow() {
    altitudeMin = 160;
    altitudeMax = 2000;

    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.setPreferredSize(new Vector3f(FIELD_W, SLIDER_ROW_H, 0));

    float vPad = (SLIDER_ROW_H - SLIDER_TRACK_H) * 0.5f;
    Container sliderWrap = new Container(new BoxLayout(Axis.Y, FillMode.None));
    sliderWrap.setBackground(null);
    sliderWrap.setPreferredSize(new Vector3f(SLIDER_W, SLIDER_ROW_H, 0));
    sliderWrap.addChild(UiKit.vSpacer(vPad));
    altitudeSlider = buildSlider();
    sliderWrap.addChild(altitudeSlider);
    sliderWrap.addChild(UiKit.vSpacer(vPad));
    row.addChild(sliderWrap);

    row.addChild(UiKit.hSpacer(SLIDER_TEXT_GAP));

    altitudeField =
        new TextField(Long.toString(Math.round((double) 550)), MissionWizardStyles.STYLE);
    altitudeField.setFont(UiKit.ibmPlexMono(11));
    altitudeField.setInsets(new Insets3f(6, 10, 6, 10));
    altitudeField.setPreferredSize(new Vector3f(VALUE_FIELD_W, SLIDER_ROW_H, 0));
    row.addChild(altitudeField);

    row.addChild(UiKit.hSpacer(SLIDER_TEXT_GAP));

    Label km = row.addChild(new Label("km", MissionWizardStyles.STYLE));
    km.setFont(UiKit.ibmPlexMono(11));
    km.setColor(MissionWizardStyles.WIZARD_TEXT_LO);

    altitudeSliderRef = altitudeSlider.getModel().createReference();

    return row;
  }

  private Slider buildSlider() {
    Slider slider =
        new Slider(new DefaultRangedValueModel(160, 2000, 550), Axis.X, MissionWizardStyles.STYLE);
    slider.setBackground(null);
    slider.setInsets(new Insets3f(0, 0, 0, 0));
    slider.setPreferredSize(new Vector3f(StepParameters.SLIDER_W, SLIDER_TRACK_H, 0));

    Panel range = slider.getRangePanel();
    range.setBackground(
        new QuadBackgroundComponent(new ColorRGBA(MissionWizardStyles.WIZARD_BORDER)));
    range.setPreferredSize(new Vector3f(StepParameters.SLIDER_W, SLIDER_TRACK_H, 0));
    range.setInsets(new Insets3f(0, 0, 0, 0));

    Button thumb = slider.getThumbButton();
    thumb.setText("");
    thumb.setBackground(UiKit.wizardFlat("btn-primary"));
    thumb.setInsets(new Insets3f(0, 0, 0, 0));
    Vector3f thumbSize = new Vector3f(THUMB_SIZE, THUMB_SIZE, 0);
    thumb.setPreferredSize(thumbSize);
    thumb.getControl(GuiControl.class).setSize(thumbSize);

    hideButton(slider.getDecrementButton());
    hideButton(slider.getIncrementButton());

    // Replace mouse control with custom drag handler
    CursorEventControl cec = thumb.getControl(CursorEventControl.class);
    if (cec != null) {
      thumb.removeControl(cec); // Détruit le ButtonDragger privé et les effets de survol
    }

    CursorEventControl.addListenersToSpatial(
        thumb,
        new DefaultCursorListener() {
          private float lastX;
          private boolean dragging = false;

          @Override
          public void cursorButtonEvent(CursorButtonEvent event, Spatial target, Spatial capture) {
            if (event.getButtonIndex() == 0) {
              if (event.isPressed()) {
                GuiGlobals.getInstance().requestFocus(null);
                lastX = event.getX();
                dragging = true;
                event.setConsumed();
              } else {
                dragging = false;
              }
            }
          }

          @Override
          public void cursorMoved(CursorMotionEvent event, Spatial target, Spatial capture) {
            if (!dragging) return;
            float currentX = event.getX();
            float deltaX = currentX - lastX;
            lastX = currentX;

            float scaleX = slider.getWorldScale().x;
            double localDelta = deltaX / scaleX;

            double draggableWidth = slider.getRangePanel().getSize().x - thumb.getSize().x;
            if (draggableWidth <= 0) return;

            double percentDelta = localDelta / draggableWidth;
            double valueRange = (double) 2000 - (double) 160;

            double newValue = slider.getModel().getValue() + (percentDelta * valueRange);

            slider.getModel().setValue(Math.max(160, Math.min(2000, newValue)));

            event.setConsumed();
          }
        });

    return slider;
  }

  private static void hideButton(Button btn) {
    btn.setText("");
    btn.setBackground(null);
    btn.setInsets(new Insets3f(0, 0, 0, 0));
    btn.setPreferredSize(new Vector3f(0, 0, 0));
  }
}
