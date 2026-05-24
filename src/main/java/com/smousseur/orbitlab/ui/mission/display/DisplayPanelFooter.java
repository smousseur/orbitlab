package com.smousseur.orbitlab.ui.mission.display;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.component.PaginationBar;

final class DisplayPanelFooter {

  static final float HEIGHT = 30f;
  private static final float PAGINATION_WIDTH = 147f;
  private static final float HIDE_ALL_WIDTH = 75f;
  private static final float SIDE_INSET = 5f;

  private final Container root;
  private final Container paginationSlot;
  private final PaginationBar pagination;
  private final Label hideAllLabel;
  private Runnable onHideAll = () -> {};

  DisplayPanelFooter(float totalWidth) {
    root = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(totalWidth, HEIGHT, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(0, SIDE_INSET, 0, SIDE_INSET)));
    root.setBackground(null);

    paginationSlot = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    paginationSlot.setBackground(null);
    paginationSlot.setPreferredSize(new Vector3f(PAGINATION_WIDTH, HEIGHT, 0));
    root.addChild(paginationSlot);

    pagination = new PaginationBar(PAGINATION_WIDTH, HEIGHT);

    float spacerWidth = totalWidth - 2 * SIDE_INSET - PAGINATION_WIDTH - HIDE_ALL_WIDTH;
    root.addChild(UiKit.hSpacer(Math.max(0, spacerWidth - 20)));

    Container hideAllButton = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    hideAllButton.setBackground(null);
    hideAllButton.setPreferredSize(new Vector3f(HIDE_ALL_WIDTH, HEIGHT, 0));
    hideAllLabel = hideAllButton.addChild(new Label("Hide all", FormStyles.STYLE));
    hideAllLabel.setFont(UiKit.sora(12));
    hideAllLabel.setColor(FormStyles.TEXT_SECONDARY);
    hideAllLabel.setTextHAlignment(HAlignment.Right);
    hideAllLabel.setTextVAlignment(VAlignment.Center);
    hideAllLabel.setPreferredSize(new Vector3f(HIDE_ALL_WIDTH - 10, HEIGHT, 0));

    MouseEventControl.addListenersToSpatial(
        hideAllButton,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            onHideAll.run();
            event.setConsumed();
          }
        });
    root.addChild(hideAllButton);
  }

  void setOnHideAll(Runnable r) {
    this.onHideAll = r != null ? r : () -> {};
  }

  void setOnPrev(Runnable r) {
    pagination.setOnPrev(r);
  }

  void setOnNext(Runnable r) {
    pagination.setOnNext(r);
  }

  void refresh(int visibleCount, int pageIndex, int pageCount) {
    hideAllLabel.setColor(visibleCount > 0 ? FormStyles.TEXT_PRIMARY : FormStyles.TEXT_LO);

    boolean showPagination = pageCount > 1;
    boolean attached = pagination.getNode().getParent() != null;
    if (showPagination) {
      if (!attached) paginationSlot.addChild(pagination.getNode());
      pagination.refresh(pageIndex, pageCount);
    } else if (attached) {
      paginationSlot.clearChildren();
    }
  }

  Container getNode() {
    return root;
  }
}
