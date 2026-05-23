package com.smousseur.orbitlab.ui.mission.display;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;

final class DisplayRow {

  static final float HEIGHT = 36f;

  private static final float SWATCH_SIZE = 16f;
  private static final float LEFT_PAD = 10f;
  private static final float RIGHT_PAD = 10f;
  private static final float INNER_GAP = 8f;

  private static final ColorRGBA ROW_IDLE = new ColorRGBA(1f, 1f, 1f, 0f);

  private final Container root;

  DisplayRow(
      MissionDisplayPanelWidget.RowSnapshot s,
      float totalWidth,
      MissionDisplayPanelWidget.RowListener listener) {
    root = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(totalWidth, HEIGHT, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(0, LEFT_PAD, 0, RIGHT_PAD)));

    if (s.telemetered()) {
      TbtQuadBackgroundComponent rowBg = UiKit.textureBg("row-hover", 8);
      // root.setBackground(rowBg);
    }
    // Color swatch
    Container swatch = new Container();
    swatch.setPreferredSize(new Vector3f(SWATCH_SIZE, SWATCH_SIZE, 0));
    swatch.setBackground(new QuadBackgroundComponent(s.color()));
    root.addChild(DisplayRowIcons.vCenter(swatch, HEIGHT));
    root.addChild(UiKit.hSpacer(INNER_GAP));

    // Telemetry toggle icon
    root.addChild(
        DisplayRowIcons.vCenter(
            DisplayRowIcons.telemetryIconButton(
                s.telemetered(), () -> listener.onToggleTelemetry(s.name(), s.telemetered())),
            HEIGHT));
    root.addChild(UiKit.hSpacer(INNER_GAP));

    // Visibility eye icon
    root.addChild(
        DisplayRowIcons.vCenter(
            DisplayRowIcons.visibilityIconButton(
                s.visible(), () -> listener.onToggleVisibility(s.name())),
            HEIGHT));

    root.addChild(UiKit.hSpacer(INNER_GAP));

    // Labels (name + subtitle stacked vertically)
    float labelsWidth =
        totalWidth
            - LEFT_PAD
            - RIGHT_PAD
            - SWATCH_SIZE
            - DisplayRowIcons.ICON_SIZE
            - DisplayRowIcons.ICON_SIZE
            - 2 * INNER_GAP
            - INNER_GAP;

    Container labels = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    labels.setBackground(null);
    labels.setPreferredSize(new Vector3f(labelsWidth, HEIGHT, 0));

    Label nameLabel = labels.addChild(new Label(s.name(), FormStyles.STYLE));
    nameLabel.setFont(UiKit.sora(13));
    nameLabel.setColor(FormStyles.TEXT_PRIMARY);
    nameLabel.setTextHAlignment(HAlignment.Left);
    nameLabel.setTextVAlignment(VAlignment.Bottom);
    nameLabel.setPreferredSize(new Vector3f(labelsWidth, HEIGHT * 0.55f, 0));

    Label subtitleLabel = labels.addChild(new Label(s.subtitle(), FormStyles.STYLE));
    subtitleLabel.setFont(UiKit.ibmPlexMono(11));
    subtitleLabel.setColor(FormStyles.TEXT_SECONDARY);
    subtitleLabel.setTextHAlignment(HAlignment.Left);
    subtitleLabel.setTextVAlignment(VAlignment.Top);
    subtitleLabel.setPreferredSize(new Vector3f(labelsWidth, HEIGHT * 0.45f, 0));

    root.addChild(labels);
  }

  Container getNode() {
    return root;
  }
}
