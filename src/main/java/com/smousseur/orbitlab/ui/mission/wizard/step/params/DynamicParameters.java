package com.smousseur.orbitlab.ui.mission.wizard.step.params;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.core.GuiControl;
import com.simsilica.lemur.event.CursorButtonEvent;
import com.simsilica.lemur.event.CursorEventControl;
import com.simsilica.lemur.event.CursorMotionEvent;
import com.simsilica.lemur.event.DefaultCursorListener;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import java.util.Map;

public abstract class DynamicParameters {
  protected static final float FIELD_W = 752f;
  protected static final float SLIDER_TRACK_H = 2f;
  protected static final float THUMB_SIZE = 16f;
  protected static final float VALUE_FIELD_W = 80f;
  protected static final float KM_LABEL_W = 22f;
  protected static final float SLIDER_TEXT_GAP = 8f;
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

  protected Slider buildSlider() {
    Slider slider =
        new Slider(new DefaultRangedValueModel(160, 2000, 550), Axis.X, FormStyles.STYLE);
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
