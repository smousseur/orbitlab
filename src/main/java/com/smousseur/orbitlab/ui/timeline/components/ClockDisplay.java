package com.smousseur.orbitlab.ui.timeline.components;

import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.core.GuiControl;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.KeyAction;
import com.simsilica.lemur.focus.FocusChangeEvent;
import com.simsilica.lemur.focus.FocusChangeListener;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.EphemerisWindow;
import com.smousseur.orbitlab.ui.timeline.TimelineStyles;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;

/**
 * Clock display cluster: editable date label (left-aligned) + "UTC" suffix label.
 *
 * <p>Clicking the date swaps the label for a text field prefilled with the current date; {@code
 * Enter} seeks the clock to the entered date, {@code Escape} and focus loss cancel. Label and field
 * share the same text origin, font and box, so entering edit mode moves nothing on screen.
 *
 * <p>Call {@link #update(AbsoluteDate)} each frame: it refreshes the date text, and freezes that
 * refresh while editing so the per-frame clock value cannot overwrite what is being typed.
 */
public class ClockDisplay {

  /** Width of {@code yyyy-MM-dd HH:mm:ss} in share-tech-mono 12 (19 glyphs, 6 px advance). */
  private static final float DATE_TEXT_WIDTH = 116f;

  /** Line height of share-tech-mono 12, used to centre text rather than its box. */
  private static final float MONO_12_LINE_HEIGHT = 14f;

  private static final float TEXT_BOX_HEIGHT = 16f;
  private static final float BOX_PAD_X = 6f;
  private static final float BOX_WIDTH = DATE_TEXT_WIDTH + 2f * BOX_PAD_X;

  /** Matches the transport button height so the edit box reads as a control of the same family. */
  private static final float BOX_HEIGHT = 24f;

  private static final float UTC_LABEL_WIDTH = 24f;
  private static final float UTC_LABEL_HEIGHT = 14f;
  private static final float DATE_UTC_GAP = 4f;

  private static final float HINT_WIDTH = 224f;
  private static final float HINT_HEIGHT = 34f;
  private static final float HINT_GAP = 8f;
  private static final float HINT_PAD = 8f;
  private static final float HINT_LINE_HEIGHT = 12f;

  private static final String FORMAT_HINT = "yyyy-MM-dd HH:mm:ss";

  private final Container root;
  private final SimulationClock clock;
  private final Label dateLabel;
  private final TextField dateField;
  private final Panel box;
  private final Panel hitTarget;
  private final Panel hintBorder;
  private final Panel hintFill;
  private final Label hintTitle;
  private final Label hintDetail;
  private final float leftEdgeX;

  private boolean editing = false;
  private boolean hintVisible = false;
  private String rejectedText = null;

  public ClockDisplay(Container root, float capsuleHeight, float rightEnd, SimulationClock clock) {
    this.root = root;
    this.clock = clock;

    float utcX = rightEnd - UTC_LABEL_WIDTH;
    float boxX = utcX - DATE_UTC_GAP - BOX_WIDTH;
    float textX = boxX + BOX_PAD_X;
    this.leftEdgeX = boxX;

    // Text is aligned on the glyph line, not on its box, so label and field land on the same pixel
    // whatever their box height.
    float textY = -(capsuleHeight - MONO_12_LINE_HEIGHT) * 0.5f;

    Label utcLabel = new Label("UTC", TimelineStyles.STYLE);
    utcLabel.setFont(TimelineStyles.mono(10));
    utcLabel.setFontSize(10f);
    utcLabel.setColor(AppStyles.TL_TEXT_MUTED);
    utcLabel.setBackground(null);
    utcLabel.setTextVAlignment(VAlignment.Center);
    utcLabel.setPreferredSize(new Vector3f(UTC_LABEL_WIDTH, UTC_LABEL_HEIGHT, 0f));
    utcLabel.setSize(utcLabel.getPreferredSize());
    place(utcLabel, root, utcX, UTC_LABEL_HEIGHT, capsuleHeight, 1f);

    box = new Panel(BOX_WIDTH, BOX_HEIGHT, TimelineStyles.STYLE);
    box.setBackground(null);
    box.setSize(box.getPreferredSize());
    place(box, root, boxX, BOX_HEIGHT, capsuleHeight, 0.5f);

    dateLabel = new Label(TimeConverter.formatDate(clock.now()), TimelineStyles.STYLE);
    dateLabel.setFont(TimelineStyles.mono(12));
    dateLabel.setFontSize(12f);
    dateLabel.setColor(AppStyles.TL_CYAN);
    dateLabel.setBackground(null);
    dateLabel.setTextHAlignment(HAlignment.Left);
    dateLabel.setTextVAlignment(VAlignment.Top);
    dateLabel.setPreferredSize(new Vector3f(DATE_TEXT_WIDTH, TEXT_BOX_HEIGHT, 0f));
    dateLabel.setSize(dateLabel.getPreferredSize());
    dateLabel.setLocalTranslation(textX, textY, 1f);
    root.attachChild(dateLabel);

    dateField = new TextField("", TimelineStyles.STYLE);
    dateField.setFont(TimelineStyles.mono(12));
    dateField.setFontSize(12f);
    dateField.setColor(AppStyles.TL_CYAN);
    dateField.setBackground(null);
    dateField.setPreferredSize(new Vector3f(DATE_TEXT_WIDTH, TEXT_BOX_HEIGHT, 0f));
    dateField.setSize(dateField.getPreferredSize());
    dateField.setLocalTranslation(textX, textY, 1f);
    dateField.getActionMap().put(new KeyAction(KeyInput.KEY_RETURN), (src, key) -> commitEdit());
    dateField.getActionMap().put(new KeyAction(KeyInput.KEY_NUMPADENTER), (src, key) -> commitEdit());
    dateField.getActionMap().put(new KeyAction(KeyInput.KEY_ESCAPE), (src, key) -> endEdit());
    dateField
        .getControl(GuiControl.class)
        .addFocusChangeListener(
            new FocusChangeListener() {
              @Override
              public void focusGained(FocusChangeEvent event) {
                // no-op: edit mode is entered by click, never by focus alone
              }

              @Override
              public void focusLost(FocusChangeEvent event) {
                endEdit();
              }
            });

    // Transparent pick target: the date text alone is unreliable to hit, and it lets the whole box
    // react to hover.
    hitTarget = new Panel(BOX_WIDTH, BOX_HEIGHT, TimelineStyles.STYLE);
    hitTarget.setBackground(new QuadBackgroundComponent(new ColorRGBA(0f, 0f, 0f, 0f)));
    hitTarget.setSize(hitTarget.getPreferredSize());
    place(hitTarget, root, boxX, BOX_HEIGHT, capsuleHeight, 3f);
    hitTarget.addMouseListener(
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
            setBoxBackground("btn-hover.png", 0.10f);
            dateLabel.setColor(AppStyles.TL_TEXT_MAIN);
          }

          @Override
          public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
            setBoxBackground(null, 0f);
            dateLabel.setColor(AppStyles.TL_CYAN);
          }

          @Override
          public void mouseButtonEvent(MouseButtonEvent event, Spatial target, Spatial capture) {
            if (event.isPressed() && event.getButtonIndex() == MouseInput.BUTTON_LEFT) {
              beginEdit();
              event.setConsumed();
            }
          }
        });

    // Rejection hint, parked above the capsule: there is no room inside it for a date range.
    float hintX = boxX + BOX_WIDTH - HINT_WIDTH;
    float hintTopY = HINT_GAP + HINT_HEIGHT;
    hintBorder = new Panel(HINT_WIDTH, HINT_HEIGHT, TimelineStyles.STYLE);
    hintBorder.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_ROSE, 0.55f)));
    hintBorder.setSize(hintBorder.getPreferredSize());
    hintBorder.setLocalTranslation(hintX, hintTopY, 4f);

    hintFill = new Panel(HINT_WIDTH - 2f, HINT_HEIGHT - 2f, TimelineStyles.STYLE);
    hintFill.setBackground(
        new QuadBackgroundComponent(new ColorRGBA(0.06f, 0.14f, 0.22f, 0.96f)));
    hintFill.setSize(hintFill.getPreferredSize());
    hintFill.setLocalTranslation(hintX + 1f, hintTopY - 1f, 4.5f);

    hintTitle = hintLabel(AppStyles.TL_ROSE);
    hintTitle.setLocalTranslation(hintX + HINT_PAD, hintTopY - HINT_PAD, 5f);

    hintDetail = hintLabel(AppStyles.TL_TEXT_DIM);
    hintDetail.setLocalTranslation(
        hintX + HINT_PAD, hintTopY - HINT_PAD - HINT_LINE_HEIGHT, 5f);
  }

  /**
   * Updates the date label text. Call once per frame.
   *
   * <p>While editing, the label is left untouched — refreshing it would fight the keyboard — and the
   * call is used instead to clear a rejection state as soon as the entry is edited again.
   */
  public void update(AbsoluteDate now) {
    if (editing) {
      if (rejectedText != null && !rejectedText.equals(dateField.getText())) {
        clearRejection();
      }
      return;
    }
    dateLabel.setText(TimeConverter.formatDate(now));
  }

  /** X coordinate of the cluster's left edge, used to position divider 3 to its left. */
  public float leftEdge() {
    return leftEdgeX;
  }

  private void beginEdit() {
    if (editing) {
      return;
    }
    editing = true;
    hitTarget.removeFromParent();
    dateLabel.removeFromParent();
    setBoxBackground("btn-active.png", 0.18f);
    dateField.setColor(AppStyles.TL_CYAN);
    dateField.setText(TimeConverter.formatDate(clock.now()));
    dateField.getDocumentModel().end(false);
    root.attachChild(dateField);
    GuiGlobals.getInstance().requestFocus(dateField);
  }

  /** Leaves edit mode without seeking, restoring the live label. */
  private void endEdit() {
    if (!editing) {
      return;
    }
    editing = false;
    clearRejection();
    dateField.removeFromParent();
    setBoxBackground(null, 0f);
    dateLabel.setColor(AppStyles.TL_CYAN);
    dateLabel.setText(TimeConverter.formatDate(clock.now()));
    root.attachChild(dateLabel);
    root.attachChild(hitTarget);
    if (GuiGlobals.getInstance().getFocusManagerState().getFocus() == dateField) {
      GuiGlobals.getInstance().requestFocus(null);
    }
  }

  private void commitEdit() {
    if (!editing) {
      return;
    }
    Optional<AbsoluteDate> parsed = TimeConverter.parseUtcDate(dateField.getText());
    if (parsed.isEmpty()) {
      reject("format de date non reconnu", FORMAT_HINT);
      return;
    }
    AbsoluteDate target = parsed.get();
    if (!EphemerisWindow.covers(target)) {
      reject("date hors couverture ephemeride", EphemerisWindow.rangeLabel().orElse(""));
      return;
    }
    endEdit();
    clock.seek(target);
  }

  /** Keeps the field open on a bad entry so the user can fix it, and explains what is accepted. */
  private void reject(String title, String detail) {
    rejectedText = dateField.getText();
    dateField.setColor(AppStyles.TL_ROSE);
    hintTitle.setText(title);
    hintDetail.setText(detail);
    if (!hintVisible) {
      hintVisible = true;
      root.attachChild(hintBorder);
      root.attachChild(hintFill);
      root.attachChild(hintTitle);
      root.attachChild(hintDetail);
    }
  }

  private void clearRejection() {
    rejectedText = null;
    dateField.setColor(AppStyles.TL_CYAN);
    if (hintVisible) {
      hintVisible = false;
      hintBorder.removeFromParent();
      hintFill.removeFromParent();
      hintTitle.removeFromParent();
      hintDetail.removeFromParent();
    }
  }

  private static Label hintLabel(ColorRGBA color) {
    Label label = new Label("", TimelineStyles.STYLE);
    label.setFont(TimelineStyles.mono(10));
    label.setFontSize(10f);
    label.setColor(color);
    label.setBackground(null);
    label.setTextHAlignment(HAlignment.Left);
    label.setTextVAlignment(VAlignment.Top);
    label.setPreferredSize(
        new Vector3f(HINT_WIDTH - 2f * HINT_PAD, HINT_LINE_HEIGHT, 0f));
    label.setSize(label.getPreferredSize());
    return label;
  }

  /**
   * Swaps the box background, reusing a capsule button texture so the edit box belongs to the same
   * visual family as the transport and speed controls.
   *
   * <p>The box is placed absolutely rather than laid out by its parent container, so nothing would
   * reshape a freshly assigned background on its own — hence the explicit resize.
   */
  private void setBoxBackground(String texture, float fallbackAlpha) {
    if (texture == null) {
      box.setBackground(null);
    } else {
      TbtQuadBackgroundComponent bg = TimelineStyles.buttonBackground(texture);
      box.setBackground(
          bg != null
              ? bg
              : new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, fallbackAlpha)));
    }
    box.setSize(box.getPreferredSize());
  }

  private static void place(
      Spatial s, Container root, float x, float height, float capsuleHeight, float z) {
    float y = -(capsuleHeight - height) * 0.5f;
    s.setLocalTranslation(x, y, z);
    root.attachChild(s);
  }

  private static ColorRGBA withAlpha(ColorRGBA c, float alpha) {
    return new ColorRGBA(c.r, c.g, c.b, alpha);
  }
}
