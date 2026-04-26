package com.smousseur.orbitlab.ui.mission.panel;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;

final class RowActionIcons {

  static final float ICON_SIZE = 20f;
  static final float ICON_GAP = 8f;

  private RowActionIcons() {}

  static Container vCenter(Container child, float containerHeight) {
    float vPad = Math.max(0f, (containerHeight - child.getPreferredSize().y) * 0.5f);
    Container wrap = new Container(new BoxLayout(Axis.Y, FillMode.None));
    wrap.setBackground(null);
    wrap.setInsets(new Insets3f(0, 0, 20, 0));
    wrap.setPreferredSize(new Vector3f(child.getPreferredSize().x, containerHeight, 0));
    wrap.addChild(UiKit.vSpacer(vPad));
    wrap.addChild(child);
    wrap.addChild(UiKit.vSpacer(vPad));
    return wrap;
  }

  static Container actionIconButton(String iconKey, boolean enabled, Runnable onClick) {
    final String normalTex = "icon-action-" + iconKey;
    final String hoverTex = "icon-action-" + iconKey + "-hover";
    final String disabledTex = "icon-action-" + iconKey + "-disabled";

    Container icon = new Container();
    icon.setPreferredSize(new Vector3f(ICON_SIZE, ICON_SIZE, 0));

    if (!enabled) {
      icon.setBackground(UiKit.wizardFlat(disabledTex));
      return icon;
    }

    icon.setBackground(tintedFlat(normalTex));
    MouseEventControl.addListenersToSpatial(
        icon,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial target, Spatial capture) {
            icon.setBackground(UiKit.wizardFlat(hoverTex));
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial target, Spatial capture) {
            icon.setBackground(tintedFlat(normalTex));
          }

          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            onClick.run();
            event.setConsumed();
          }
        });
    return icon;
  }

  static Container visualizeIconButton(boolean ready, boolean on, Runnable onClick) {
    final String normalTex = "icon-action-view";
    final String hoverTex = "icon-action-view-hover";
    final String disabledTex = "icon-action-view-disabled";

    Container icon = new Container();
    icon.setPreferredSize(new Vector3f(ICON_SIZE, ICON_SIZE, 0));

    if (!ready) {
      icon.setBackground(UiKit.wizardFlat(disabledTex));
      return icon;
    }

    final String idleTex = on ? normalTex : disabledTex;
    icon.setBackground(UiKit.wizardFlat(idleTex));

    MouseEventControl.addListenersToSpatial(
        icon,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial target, Spatial capture) {
            icon.setBackground(UiKit.wizardFlat(hoverTex));
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial target, Spatial capture) {
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

  private static QuadBackgroundComponent tintedFlat(String tex) {
    QuadBackgroundComponent q = UiKit.wizardFlat(tex);
    q.setColor(FormStyles.ACCENT_BRIGHT);
    return q;
  }
}
