package com.smousseur.orbitlab.ui.mission.display;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;

final class DisplayRowIcons {

  static final float ICON_SIZE = 20f;
  static final float ICON_GAP = 8f;

  private DisplayRowIcons() {}

  static Container vCenter(Container child, float containerHeight) {
    float vPad = Math.max(0f, (containerHeight - child.getPreferredSize().y) * 0.5f);
    Container wrap = new Container(new BoxLayout(Axis.Y, FillMode.None));
    wrap.setBackground(null);
    wrap.setInsets(new Insets3f(0, 0, 0, 0));
    wrap.setPreferredSize(new Vector3f(child.getPreferredSize().x, containerHeight, 0));
    wrap.addChild(UiKit.vSpacer(vPad));
    wrap.addChild(child);
    wrap.addChild(UiKit.vSpacer(vPad));
    return wrap;
  }

  /**
   * Telemetry toggle icon. {@code active} controls the idle texture (filled vs grayed).
   */
  static Container telemetryIconButton(boolean active, Runnable onClick) {
    String idleTex = active ? "icon-action-telemetry" : "icon-action-telemetry-disabled";
    String hoverTex = "icon-action-telemetry-hover";
    return toggleIcon(idleTex, hoverTex, onClick);
  }

  /**
   * Visibility toggle icon. {@code on} controls the idle texture (filled vs grayed).
   */
  static Container visibilityIconButton(boolean on, Runnable onClick) {
    String idleTex = on ? "icon-action-view" : "icon-action-view-disabled";
    String hoverTex = "icon-action-view-hover";
    return toggleIcon(idleTex, hoverTex, onClick);
  }

  /** Manage button icon (gear). */
  static Container manageIconButton(Runnable onClick) {
    return toggleIcon("icon-action-manage", "icon-action-manage-hover", onClick);
  }

  private static Container toggleIcon(String idleTex, String hoverTex, Runnable onClick) {
    Container icon = new Container();
    icon.setPreferredSize(new Vector3f(ICON_SIZE, ICON_SIZE, 0));
    icon.setBackground(UiKit.wizardFlat(idleTex));

    MouseEventControl.addListenersToSpatial(
        icon,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
            icon.setBackground(UiKit.wizardFlat(hoverTex));
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
            icon.setBackground(UiKit.wizardFlat(idleTex));
          }

          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            onClick.run();
            event.setConsumed();
          }
        });
    return icon;
  }
}
