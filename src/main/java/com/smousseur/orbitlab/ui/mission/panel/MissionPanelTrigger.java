package com.smousseur.orbitlab.ui.mission.panel;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import java.util.Objects;

/** Small toggle button anchored top-left that opens and closes the mission panel modal. */
public final class MissionPanelTrigger implements AutoCloseable {

  private static final float MARGIN_PX = AppStyles.HUD_MARGIN_PX;

  private final Button button;
  private Runnable onClick = () -> {};

  public MissionPanelTrigger(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    Node missionPanelNode = context.guiGraph().getMissionPanelNode();

    button = new Button("Missions", FormStyles.STYLE);
    button.setFont(UiKit.sora(12));
    button.setColor(FormStyles.TEXT_PRIMARY);
    button.setBackground(UiKit.gradientBackground(AppStyles.ICE_ACCENT));
    button.setInsetsComponent(new InsetsComponent(new Insets3f(6, 14, 6, 14)));
    button.addClickCommands(src -> onClick.run());

    missionPanelNode.attachChild(button);
  }

  public void layoutTopLeft(int screenWidth, int screenHeight) {
    float y = screenHeight - MARGIN_PX;
    button.setLocalTranslation(MARGIN_PX, y, 0f);
  }

  public void setOnClick(Runnable action) {
    this.onClick = action != null ? action : () -> {};
  }

  /**
   * Visually communicates whether the trigger is currently enabled. When disabled, the button stays
   * clickable but is rendered semi-transparent to signal the panel is already open.
   */
  public void setEnabled(boolean enabled) {
    ColorRGBA base = AppStyles.ICE_ACCENT;
    ColorRGBA tint = enabled ? base : new ColorRGBA(base.r, base.g, base.b, base.a * 0.4f);
    button.setBackground(UiKit.gradientBackground(tint));
    button.setColor(enabled ? FormStyles.TEXT_PRIMARY : FormStyles.TEXT_LO);
  }

  @Override
  public void close() {
    button.removeFromParent();
  }
}
