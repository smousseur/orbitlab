package com.smousseur.orbitlab.ui.mission.wizard.step.params;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.core.GuiControl;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.lemur.event.CursorButtonEvent;
import com.simsilica.lemur.event.CursorEventControl;
import com.simsilica.lemur.event.CursorMotionEvent;
import com.simsilica.lemur.event.DefaultCursorListener;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import java.util.Map;

import static com.smousseur.orbitlab.ui.mission.wizard.step.StepParameters.*;

public abstract class DynamicParameters {
  protected static final float FIELD_W = 752f;
  protected static final float SLIDER_TRACK_H = 2f;
  protected static final float THUMB_SIZE = 16f;
  protected static final float VALUE_FIELD_W = 80f;
  protected static final float KM_LABEL_W = 22f;
  protected static final float SLIDER_TEXT_GAP = 8f;
  private static final float SLIDER_ROW_H = 32f;
  protected static final float SLIDER_W =
      FIELD_W - VALUE_FIELD_W - KM_LABEL_W - 2 * SLIDER_TEXT_GAP;

  protected Container container;

  protected abstract Container createContainer();

  public Container getContainer() {
    return container;
  }

  public abstract void update(float tpf);

  public abstract Map<String, Object> getDynamicValues();

  public void setVisible(boolean visible) {
    if (container != null) {
      container.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }
  }

  protected Container getSliderContainer(String label, Slider slider, TextField field) {
    Container sliderContainer = new Container();
    sliderContainer.addChild(
        UiKit.fieldLabelRow(label, "lbl-ruler", LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    sliderContainer.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    sliderContainer.addChild(buildSliderRow(slider, field));
    sliderContainer.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    sliderContainer.addChild(rangeBoundsRow());
    sliderContainer.addChild(UiKit.vSpacer(ROW_GAP));
    return sliderContainer;
  }

  private Container rangeBoundsRow() {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    Label min = row.addChild(new Label("160 km", FormStyles.STYLE));
    min.setFont(UiKit.ibmPlexMono(11));
    min.setColor(FormStyles.TEXT_LO);

    Label max = new Label("2 000 km", FormStyles.STYLE);
    max.setFont(UiKit.ibmPlexMono(11));
    max.setColor(FormStyles.TEXT_LO);

    float used = min.getPreferredSize().x + max.getPreferredSize().x;
    float trailing = Math.max(0f, SLIDER_W - used);
    row.addChild(UiKit.hSpacer(trailing));
    row.addChild(max);
    return row;
  }

  private Container buildSliderRow(Slider slider, TextField sliderField) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.setPreferredSize(new Vector3f(FIELD_W, SLIDER_ROW_H, 0));

    float vPad = (SLIDER_ROW_H - SLIDER_TRACK_H) * 0.5f;
    Container sliderWrap = new Container(new BoxLayout(Axis.Y, FillMode.None));
    sliderWrap.setBackground(null);
    sliderWrap.setPreferredSize(new Vector3f(SLIDER_W, SLIDER_ROW_H, 0));
    sliderWrap.addChild(UiKit.vSpacer(vPad));
    sliderWrap.addChild(slider);
    sliderWrap.addChild(UiKit.vSpacer(vPad));
    row.addChild(sliderWrap);

    row.addChild(UiKit.hSpacer(SLIDER_TEXT_GAP));

    sliderField.setFont(UiKit.ibmPlexMono(11));
    sliderField.setPreferredSize(new Vector3f(VALUE_FIELD_W, SLIDER_ROW_H, 0));
    sliderField.setInsets(new Insets3f(-7, 0, 10, 0));
    sliderField.setInsetsComponent(new InsetsComponent(new Insets3f(3, 10, 0, 0)));
    row.addChild(sliderField);

    row.addChild(UiKit.hSpacer(SLIDER_TEXT_GAP));

    Label km = row.addChild(new Label("km", FormStyles.STYLE));
    km.setFont(UiKit.ibmPlexMono(11));
    km.setColor(FormStyles.TEXT_LO);

    return row;
  }

  protected Slider buildSlider(double valueMin, double valueMax, double valueDefault) {
    Slider slider =
        new Slider(
            new DefaultRangedValueModel(valueMin, valueMax, valueDefault),
            Axis.X,
            FormStyles.STYLE);
    slider.setBackground(null);
    slider.setInsets(new Insets3f(0, 0, 0, 0));
    slider.setPreferredSize(new Vector3f(SLIDER_W, SLIDER_TRACK_H, 0));

    Panel range = slider.getRangePanel();
    range.setBackground(new QuadBackgroundComponent(new ColorRGBA(FormStyles.BORDER)));
    range.setPreferredSize(new Vector3f(SLIDER_W, SLIDER_TRACK_H, 0));
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
            double valueRange = valueMax - valueMin;

            double newValue = slider.getModel().getValue() + (percentDelta * valueRange);

            slider.getModel().setValue(Math.max(valueMin, Math.min(valueMax, newValue)));

            event.setConsumed();
          }
        });

    return slider;
  }

  protected boolean updateSlider(
      Slider slider,
      VersionedReference<Double> sliderRef,
      TextField field,
      boolean isFieldFocused,
      double valueMin,
      double valueMax) {
    if (slider == null || field == null) return false;

    if (sliderRef.update()) {
      field.setText(Long.toString(Math.round(slider.getModel().getValue())));
    }

    Spatial focused = GuiGlobals.getInstance().getFocusManagerState().getFocus();
    boolean isFocused = focused == field;
    if (isFieldFocused && !isFocused) {
      commitAltitudeFieldToSlider(slider, sliderRef, field, valueMin, valueMax);
    }

    return isFocused;
  }

  protected void commitAltitudeFieldToSlider(
      Slider slider,
      VersionedReference<Double> sliderRef,
      TextField field,
      double valueMin,
      double valueMax) {
    try {
      double v = Double.parseDouble(field.getText().trim());
      v = Math.max(valueMin, Math.min(valueMax, v));
      slider.getModel().setValue(v);
      sliderRef.update();
      field.setText(Long.toString(Math.round(v)));
    } catch (NumberFormatException e) {
      field.setText(Long.toString(Math.round(slider.getModel().getValue())));
    }
  }

  private static void hideButton(Button btn) {
    btn.setText("");
    btn.setBackground(null);
    btn.setInsets(new Insets3f(0, 0, 0, 0));
    btn.setPreferredSize(new Vector3f(0, 0, 0));
  }
}
