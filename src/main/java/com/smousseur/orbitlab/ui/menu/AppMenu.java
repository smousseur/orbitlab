package com.smousseur.orbitlab.ui.menu;

import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.*;
import com.simsilica.lemur.core.GuiControl;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.simsilica.lemur.style.ElementId;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.form.ModalBackdrop;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Top-left application menu: an {@code ORBITLAB} title button plus a dropdown list of entries.
 *
 * <p>Purely presentation — it holds no open/closed truth and runs no action. Clicks are reported
 * through {@link #setOnTitleClicked(Runnable)}, {@link #setOnItemClicked(Consumer)} and {@link
 * #setOnDismiss(Runnable)}; the owning app state decides what they mean and pushes the resulting
 * state back with {@link #setOpen(boolean)}, {@link #setChecked(String, boolean)} and {@link
 * #setEnabled(String, boolean)}.
 *
 * <p>Everything the widget shows comes from the {@code form} style: the title carries {@code
 * menu.title.button} and the entries {@code menu.item}. The title's hover is the style's own
 * {@code highlightColor}, so the only calls that touch a skin attribute here are an entry's hover,
 * check and disabled transitions — see {@code docs/menu/01-menu-applicatif.md} §6.1.
 */
public final class AppMenu implements AutoCloseable {

  /**
   * Width of the title button. {@code ORBITLAB} measures 67 px in Sora 13 and the {@code button}
   * selector spends 44 px on its own insets; the rest is the breathing room around the two glyphs.
   */
  private static final float TITLE_WIDTH = 176f;

  private static final float DROPDOWN_WIDTH = 236f;
  private static final float ITEM_HEIGHT = 32f;
  private static final float ICON_SIZE = 16f;
  private static final float CHECK_SIZE = 14f;
  private static final float CARET_WIDTH = 12f;
  private static final float CARET_HEIGHT = 8f;

  /** Vertical insets of the {@code menu} selector, counted twice in the dropdown's height. */
  private static final float DROPDOWN_PAD_Y = 8f;

  /** Gap between an entry's icon and its label, as a half-margin on each side of the icon box. */
  private static final float ICON_HALF_GAP = 3f;

  /** Same idea on the title, which has more room to spend than a 30-pixel entry. */
  private static final float TITLE_ICON_HALF_GAP = 4f;

  /** Half-gap kept to the left of the caret, which is aligned on the button's right inset. */
  private static final float CARET_HALF_GAP = 3f;

  private static final float SEPARATOR_HEIGHT = 1f;

  /**
   * Breathing room kept above and below a separator. The entries' hover frames are full-bleed, so
   * without it the rule would sit flush against the row it parts from the group below.
   */
  private static final float SEPARATOR_GAP = 6f;

  /** Vertical gap between the title button and the dropdown it opens. */
  private static final float DROPDOWN_GAP = 4f;

  /** Extra component layer, holding the trailing glyph: the title's caret, an entry's check. */
  private static final String LAYER_TRAILING = "trailing";

  private static final ColorRGBA HOVER_TINT = new ColorRGBA(1f, 1f, 1f, 0.18f);
  private static final ColorRGBA IDLE_TINT = new ColorRGBA(1f, 1f, 1f, 0f);

  private final Node parent;
  private final Button title;
  private final Container dropdown;
  private final ModalBackdrop catcher;
  private final Map<String, ItemView> itemsById = new LinkedHashMap<>();

  private boolean open;

  private Runnable onTitleClicked = () -> {};
  private Runnable onDismiss = () -> {};
  private Consumer<String> onItemClicked = id -> {};

  public AppMenu(ApplicationContext context, List<AppMenuItem> items) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(items, "items");
    this.parent = context.guiGraph().getMissionPanelNode();

    title = new Button("ORBITLAB", new ElementId("menu.title.button"), FormStyles.STYLE);
    title.setPreferredSize(new Vector3f(TITLE_WIDTH, AppStyles.HUD_MENU_HEIGHT_PX, 0));
    addTrailingLayer(title);
    IconComponent globe =
        UiKit.tintedIcon("wizard/icon-brand-globe", ICON_SIZE, FormStyles.TEXT_PRIMARY);
    globe.setHAlignment(HAlignment.Left);
    globe.setMargin(TITLE_ICON_HALF_GAP, 0f);
    globe.setOffset(new Vector3f(-TITLE_ICON_HALF_GAP, 0f, 0f));
    title.setIcon(globe);
    IconComponent caret =
        UiKit.tintedIcon(
            "wizard/icon-caret-down", CARET_WIDTH, CARET_HEIGHT, FormStyles.ACCENT_BRIGHT);
    caret.setHAlignment(HAlignment.Right);
    caret.setMargin(CARET_HALF_GAP, 0f);
    caret.setOffset(new Vector3f(CARET_HALF_GAP, 0f, 0f));
    title.getControl(GuiControl.class).setComponent(LAYER_TRAILING, caret);
    title.addClickCommands(src -> onTitleClicked.run());
    parent.attachChild(title);

    dropdown =
        new Container(
            new BoxLayout(Axis.Y, FillMode.None), new ElementId("menu"), FormStyles.STYLE);
    orderMenuLayers(dropdown);
    dropdown.setPreferredSize(new Vector3f(DROPDOWN_WIDTH, dropdownHeight(items), 0));
    for (AppMenuItem item : items) {
      if (item.separatorBefore()) {
        dropdown.addChild(UiKit.vSpacer(SEPARATOR_GAP));
        dropdown.addChild(separator());
        dropdown.addChild(UiKit.vSpacer(SEPARATOR_GAP));
      }
      ItemView view = new ItemView(item);
      itemsById.put(item.id(), view);
      dropdown.addChild(view.button);
    }

    catcher = new ModalBackdrop(UiLayers.MENU_CATCHER, new ColorRGBA(0f, 0f, 0f, 0f));
    catcher.setOnClick(() -> onDismiss.run());
  }

  /** Invoked when the title button is clicked. */
  public void setOnTitleClicked(Runnable action) {
    this.onTitleClicked = action != null ? action : () -> {};
  }

  /** Invoked with the entry id when an entry is clicked, disabled entries included. */
  public void setOnItemClicked(Consumer<String> action) {
    this.onItemClicked = action != null ? action : id -> {};
  }

  /** Invoked when the user clicks anywhere outside the open menu. */
  public void setOnDismiss(Runnable action) {
    this.onDismiss = action != null ? action : () -> {};
  }

  /**
   * Anchors the menu in the top-left corner.
   *
   * @param screenHeight height of the render surface in pixels
   * @param topOffset pixels between the top of the screen and the top of the title button — the HUD
   *     margin today, the breadcrumb band plus that margin once {@code NAV-4} lands
   */
  public void layoutTopLeft(int screenHeight, float topOffset) {
    float titleTop = screenHeight - topOffset;
    title.setLocalTranslation(AppStyles.HUD_MARGIN_PX, titleTop, UiLayers.MENU_TITLE);
    dropdown.setLocalTranslation(
        AppStyles.HUD_MARGIN_PX,
        titleTop - AppStyles.HUD_MENU_HEIGHT_PX - DROPDOWN_GAP,
        UiLayers.MENU_DROPDOWN);
  }

  /** Shows or hides the dropdown and its outside-click catcher. */
  public void setOpen(boolean value) {
    if (value == open) return;
    open = value;
    if (open) {
      parent.attachChild(catcher.getNode());
      parent.attachChild(dropdown);
    } else {
      dropdown.removeFromParent();
      catcher.getNode().removeFromParent();
    }
  }

  /** Shows or hides the check mark of a toggle entry. Unknown or non-toggle ids are ignored. */
  public void setChecked(String itemId, boolean value) {
    ItemView view = itemsById.get(itemId);
    if (view != null) {
      view.setChecked(value);
    }
  }

  /** Greys out an entry, or restores it. Unknown ids are ignored. */
  public void setEnabled(String itemId, boolean value) {
    ItemView view = itemsById.get(itemId);
    if (view != null) {
      view.setEnabled(value);
    }
  }

  /** Keeps the outside-click catcher covering the whole screen. Call once per frame. */
  public void update(Camera camera) {
    catcher.update(camera);
  }

  @Override
  public void close() {
    setOpen(false);
    title.removeFromParent();
  }

  /**
   * Inserts a {@link #LAYER_TRAILING} layer between the leading icon and the text, leaving Lemur's
   * default order untouched otherwise. This is what the title chip uses: it wears the {@code
   * button} selector exactly as every other button of the form style does, and gains only a place
   * to hang its caret.
   */
  private static void addTrailingLayer(Label label) {
    label
        .getControl(GuiControl.class)
        .setLayerOrder(
            Panel.LAYER_INSETS,
            Panel.LAYER_BORDER,
            Panel.LAYER_BACKGROUND,
            Label.LAYER_ICON,
            LAYER_TRAILING,
            Label.LAYER_SHADOW_TEXT,
            Label.LAYER_TEXT);
  }

  /**
   * Re-orders an element's component stack so the skin is drawn <i>before</i> the insets, then
   * appends the content layers.
   *
   * <p>Lemur's default is the reverse — insets first — which makes them an outer margin: the
   * background is painted inside them and the element's own bounds stay empty around it. The
   * dropdown wants padding, not margin. An entry's hover frame has to cover its whole row, or two
   * consecutive frames stand 14 px apart with the insets showing between them; and the shell has to
   * reach its own edges rather than shrink onto the list. Only the two elements of the dropdown are
   * treated this way — the title chip keeps the stack the form style gives it.
   *
   * @param panel the element whose stack is re-ordered
   * @param contentLayers the layers that sit inside the insets, in draw order
   */
  private static void orderMenuLayers(Panel panel, String... contentLayers) {
    String[] order = new String[3 + contentLayers.length];
    order[0] = Panel.LAYER_BORDER;
    order[1] = Panel.LAYER_BACKGROUND;
    order[2] = Panel.LAYER_INSETS;
    System.arraycopy(contentLayers, 0, order, 3, contentLayers.length);
    panel.getControl(GuiControl.class).setLayerOrder(order);
  }

  private static float dropdownHeight(List<AppMenuItem> items) {
    float height = 2 * DROPDOWN_PAD_Y;
    for (AppMenuItem item : items) {
      height += ITEM_HEIGHT;
      if (item.separatorBefore()) {
        height += SEPARATOR_HEIGHT + 2 * SEPARATOR_GAP;
      }
    }
    return height;
  }

  /**
   * A 1-pixel rule. The repository's only divider texture is vertical (1×20), so a tinted panel
   * does the job here, as {@code TimelineWidget} already does in the other direction. The width
   * given here is a placeholder: a vertical {@code BoxLayout} stretches every child to the
   * dropdown's content width.
   */
  private static Container separator() {
    Container rule = new Container();
    rule.setInsets(new Insets3f(0, 10, 0, 10));
    rule.setPreferredSize(new Vector3f(DROPDOWN_WIDTH, SEPARATOR_HEIGHT, 0));
    rule.setBackground(new QuadBackgroundComponent(FormStyles.BORDER));
    rule.setAlpha(0.3f);
    return rule;
  }

  /** One rendered entry, plus the state transitions the style cannot express. */
  private final class ItemView {

    private final AppMenuItem item;
    private final Button button;
    private final IconComponent icon;
    private final TbtQuadBackgroundComponent hoverBg;

    private boolean checked;
    private boolean enabled = true;
    private boolean hovered;

    private ItemView(AppMenuItem item) {
      this.item = item;
      button = new Button(item.label(), new ElementId("menu.item"), FormStyles.STYLE);
      button.setTextVAlignment(VAlignment.Center);
      button.setPreferredSize(new Vector3f(DROPDOWN_WIDTH, ITEM_HEIGHT, 0));

      // The icon box is 6 px wider than the icon, then shifted back by half of that: the glyph
      // starts flush with the entry's left inset and leaves exactly 6 px before the label.
      icon = UiKit.tintedIcon(item.iconName(), ICON_SIZE, FormStyles.TEXT_SECONDARY);
      icon.setHAlignment(HAlignment.Left);
      icon.setMargin(ICON_HALF_GAP, 0f);
      icon.setOffset(new Vector3f(-ICON_HALF_GAP, 0f, 0f));
      button.setIcon(icon);
      orderMenuLayers(
          button, Label.LAYER_ICON, LAYER_TRAILING, Label.LAYER_SHADOW_TEXT, Label.LAYER_TEXT);

      // The row skin comes from the style, transparent, and never leaves the entry: Lemur clones a
      // styled component per element, so tinting this one only lights up this row.
      hoverBg = button.getBackground() instanceof TbtQuadBackgroundComponent bg ? bg : null;

      button.addClickCommands(src -> onItemClicked.accept(item.id()));
      MouseEventControl.addListenersToSpatial(
          button,
          new DefaultMouseListener() {
            @Override
            public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
              hovered = true;
              refresh();
            }

            @Override
            public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
              hovered = false;
              refresh();
            }
          });
    }

    private void setChecked(boolean value) {
      if (!item.isToggle() || value == checked) return;
      checked = value;
      button.getControl(GuiControl.class).setComponent(LAYER_TRAILING, value ? checkIcon() : null);
      refresh();
    }

    private void setEnabled(boolean value) {
      if (value == enabled) return;
      enabled = value;
      refresh();
    }

    private void refresh() {
      if (hoverBg != null) {
        hoverBg.setColor(hovered && enabled ? HOVER_TINT : IDLE_TINT);
      }

      ColorRGBA text;
      ColorRGBA glyph;
      if (!enabled) {
        text = FormStyles.TEXT_LO;
        glyph = FormStyles.TEXT_LO;
      } else if (checked) {
        text = FormStyles.TEXT_PRIMARY;
        glyph = FormStyles.ACCENT_BRIGHT;
      } else {
        text = FormStyles.TEXT_SECONDARY;
        glyph = FormStyles.TEXT_SECONDARY;
      }
      button.setColor(text);
      icon.setColor(glyph);
    }

    private IconComponent checkIcon() {
      IconComponent check =
          UiKit.tintedIcon("wizard/icon-check-white", CHECK_SIZE, FormStyles.ACCENT_BRIGHT);
      check.setHAlignment(HAlignment.Right);
      return check;
    }
  }
}
