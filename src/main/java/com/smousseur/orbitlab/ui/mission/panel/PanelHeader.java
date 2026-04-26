package com.smousseur.orbitlab.ui.mission.panel;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BorderLayout;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;

public class PanelHeader {

  private static final float HEIGHT = 88f;
  private static final float PAD_X = 32f;
  private static final float PAD_Y = 20f;
  private static final float CLOSE_ICON_SIZE = 14f;

  private final Container root;
  private Runnable onClose = () -> {};

  public PanelHeader(float width) {
    float innerWidth = width - 2 * PAD_X;

    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(FormStyles.headerBg());
    root.setPreferredSize(new Vector3f(width, HEIGHT, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(PAD_Y, PAD_X, PAD_Y, PAD_X)));

    Container brandRow = root.addChild(new Container(new BorderLayout()));
    brandRow.setBackground(null);
    brandRow.setPreferredSize(new Vector3f(innerWidth, 18f, 0));

    Container brandLeft = new Container(new BoxLayout(Axis.X, FillMode.None));
    brandLeft.setBackground(null);
    brandLeft.addChild(UiKit.wizardIcon("icon-brand-globe", 18, 18));
    brandLeft.addChild(UiKit.hSpacer(8));

    Label brandName = brandLeft.addChild(new Label("ORBITLAB", FormStyles.STYLE));
    brandName.setFont(UiKit.orbitron(13));
    brandName.setColor(FormStyles.ACCENT_BRIGHT);

    Label brandSep = brandLeft.addChild(new Label("  /  ", FormStyles.STYLE));
    brandSep.setFont(UiKit.ibmPlexMono(11));
    brandSep.setColor(FormStyles.TEXT_LO);

    Label brandSub = brandLeft.addChild(new Label("MISSIONS", FormStyles.STYLE));
    brandSub.setFont(UiKit.ibmPlexMono(11));
    brandSub.setColor(FormStyles.TEXT_LO);

    brandRow.addChild(brandLeft, BorderLayout.Position.West);
    brandRow.addChild(buildCloseButton(), BorderLayout.Position.East);

    root.addChild(UiKit.vSpacer(10));

    Container titleRow = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    titleRow.setBackground(null);
    titleRow.setPreferredSize(new Vector3f(innerWidth, 32f, 0));

    Label title = titleRow.addChild(new Label("MISSION ROSTER", FormStyles.STYLE));
    title.setFont(UiKit.orbitron(16));
    title.setColor(FormStyles.TEXT_PRIMARY);
    title.setPreferredSize(new Vector3f(innerWidth, 26f, 0));
  }

  public Container getNode() {
    return root;
  }

  public void setOnClose(Runnable action) {
    this.onClose = action != null ? action : () -> {};
  }

  private Container buildCloseButton() {
    Container icon = new Container();
    icon.setBackground(UiKit.wizardFlat("icon-close-lo"));
    icon.setPreferredSize(new Vector3f(CLOSE_ICON_SIZE, CLOSE_ICON_SIZE, 0));
    MouseEventControl.addListenersToSpatial(
        icon,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
            icon.setBackground(UiKit.wizardFlat("icon-close-red"));
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
            icon.setBackground(UiKit.wizardFlat("icon-close-lo"));
          }

          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            onClose.run();
            event.setConsumed();
          }
        });
    return icon;
  }
}
