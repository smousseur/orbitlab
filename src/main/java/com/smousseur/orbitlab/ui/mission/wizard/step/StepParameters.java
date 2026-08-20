package com.smousseur.orbitlab.ui.mission.wizard.step;

import static com.smousseur.orbitlab.ui.UiKit.fieldLabelRow;
import static com.smousseur.orbitlab.ui.UiKit.newInputField;

import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.event.*;
import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.window.problem.EarthLaunchWindowRequest;
import com.smousseur.orbitlab.ui.EphemerisWindow;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.FormValues;
import com.smousseur.orbitlab.ui.mission.wizard.MissionProfile;
import com.smousseur.orbitlab.ui.mission.wizard.SiteCoordinates;
import com.smousseur.orbitlab.ui.mission.wizard.StepValues;
import com.smousseur.orbitlab.ui.mission.wizard.step.params.DynamicParameters;
import com.smousseur.orbitlab.ui.mission.wizard.step.params.EarthOrbitDynamicParameters;
import com.smousseur.orbitlab.ui.mission.wizard.step.params.GEODynamicParameters;
import com.smousseur.orbitlab.ui.mission.wizard.step.planning.PlanningPage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import org.orekit.time.AbsoluteDate;

public class StepParameters implements StepValues {

  private static final float FIELD_W = 752f;
  public static final float FIELD_H = 36f;
  public static final float ROW_GAP = 16f;
  public static final float LABEL_FIELD_GAP = 6f;
  public static final float LABEL_ICON_SIZE = 14f;

  private static final String LAUNCH_DATE_HELPER = "UTC · Orekit epoch";
  private static final String LAUNCH_DATE_FORMAT_HELPER =
      "expected format : yyyy-MM-dd HH:mm:ss (UTC)";

  private static final float HORIZON_FIELD_W = 120f;

  /** Diameter of the auto/manual status dot. */
  private static final float AUTO_DOT_SIZE = 8f;

  /** Gap between the launch-date and mission-duration columns. */
  private static final float COLUMN_GAP = 24f;

  /**
   * Width of the launch-date field, now that it shares its row. Whatever the duration column does
   * not take, less the gap — comfortably more than the {@code yyyy-MM-dd HH:mm:ss} it must show.
   */
  private static final float DATE_FIELD_W = 420f;

  /** Shortest mission the horizon field accepts: one minute, expressed in days. */
  private static final double HORIZON_MIN_DAYS = 1.0 / 1440.0;

  /** Longest mission the horizon field accepts — the cap the model enforces anyway. */
  private static final double HORIZON_MAX_DAYS =
      MissionHorizon.MAX_COAST_SECONDS / MissionHorizon.SECONDS_PER_DAY;

  private static final String HORIZON_FORMAT_HELPER =
      "Expected duration: number of days between 1 minute and " + (long) HORIZON_MAX_DAYS + " d";

  /**
   * Kept short on purpose: a column takes the width of its widest child, and the accepted range is
   * already spelled out by {@link #HORIZON_FORMAT_HELPER} the moment an entry is refused — the same
   * bargain the launch-date field makes.
   */
  private static final String HORIZON_MANUAL_HELPER = "total duration since takeoff";

  /** Which of the step's two pages is mounted. */
  private enum Page {
    FIELDS,
    PLANNING
  }

  private final Container root;
  private final Container pageHost;
  private Page page = Page.FIELDS;

  private final MissionContext missionContext;

  /** The pad the window is planned from, read live for the same reason the latitude is. */
  private final Supplier<Optional<SiteCoordinates>> launchSite;

  private final Label titleLabel;

  private final TextField missionNameField;
  private final TextField launchDateField;
  private final Label launchDateHelper;

  /** The row holding the launch-date field and the indicator, so the latter can be detached. */
  private final Container dateRow;

  /** The indicator's container, detached on GEO where no node can ever be set. */
  private final Container planningIndicator;

  /** The gap before the indicator, detached with it so GEO leaves no phantom column width. */
  private final Container planningGap;

  /** Whether {@link #planningIndicator} is currently a child of {@link #dateRow}. */
  private boolean planningIndicatorShown = true;

  /** The word {@code planning}: a text-only control, and the click target that opens the page. */
  private Button planningButton;

  /** The lit/unlit dot beside it — lit exactly when a target node is set. */
  private Panel planningDot;

  private boolean planningHovered;

  /**
   * Whether the indicator has ever been painted, so its first paint cannot be mistaken for a no-op
   * by the guard in {@link #applyPlanningIndicator()}.
   *
   * <p>Nothing today reaches that case: the style paints a button {@code TEXT_PRIMARY}, and the
   * first colour this indicator computes is never that one. It is kept as the defence it would have
   * to be the day the style default and an indicator state coincide, which would otherwise leave the
   * dot showing a plain style background for the life of the wizard.
   */
  private boolean planningPainted;

  private final TextField horizonField;
  private final Label horizonHelper;

  /** The word {@code auto}: a text-only control, and the click target that restores the default. */
  private Button autoButton;

  /** The lit/unlit dot beside it. It, and not the button's weight, is what shows the state. */
  private Panel autoDot;

  private boolean autoHovered;

  /**
   * Whether the duration field still shows the derived default. This is the whole of the "auto"
   * state: while it holds, {@link #getValues()} omits the key entirely, which is what makes the
   * wizard's auto mode survive a round-trip through the raw value map without a flag of its own
   * (spec {@code docs/mission-horizon/01-horizon-explicite.md} §7).
   */
  private boolean horizonAuto = true;

  /** The last text this step wrote into the duration field, to tell a prefill from a user edit. */
  private String lastAutoHorizonText = "";

  /** Entry that was refused, kept so the error state clears as soon as it is edited. */
  private String rejectedLaunchDate;

  /** Same, for the duration field. */
  private String rejectedHorizon;

  private final PlanningPage planningPage = new PlanningPage();

  private DynamicParameters dynamicParameters;
  private final EnumMap<MissionProfile, DynamicParameters> dynamicParametersMap =
      new EnumMap<>(MissionProfile.class);
  private final Container dynamicParametersContainer;
  private MissionProfile shownProfile;

  /**
   * The card the user picked. Held here rather than in {@code MissionContext}: the profile is a
   * wizard concept, and the mission context belongs to the simulation layer (spec {@code
   * docs/earth-orbit/02-wizard-orbites-terrestres.md} §3). The mission <em>type</em> keeps going
   * through the context, for the launcher step that only needs that much.
   */
  private MissionProfile selectedProfile = MissionProfile.LEO;

  /**
   * Builds the parameters step.
   *
   * @param missionContext the context carrying the selected mission type
   * @param launchLatitudeDeg the live launch latitude, for the inclination field's bounds
   * @param launchSite the live pad coordinates, for the launch window — empty while any of the
   *     three fields is unreadable, which is what keeps a window from being computed on a
   *     substituted zero
   */
  public StepParameters(
      MissionContext missionContext,
      DoubleSupplier launchLatitudeDeg,
      Supplier<Optional<SiteCoordinates>> launchSite) {
    this.missionContext = missionContext;
    this.launchSite = launchSite;
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(new QuadBackgroundComponent(new ColorRGBA(0, 0, 0, 0)));
    root.setPreferredSize(new Vector3f(FormStyles.CONTENT_WIDTH, FormStyles.CONTENT_HEIGHT, 0));

    titleLabel = new Label("PARAMETERS " + selectedProfile.title(), FormStyles.STYLE);
    Label title = root.addChild(titleLabel);
    title.setFont(UiKit.orbitron(13));
    title.setColor(FormStyles.TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(6));

    Label subtitle = root.addChild(new Label("// target orbit configuration", FormStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(FormStyles.TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // --- Mission Name ---
    root.addChild(fieldLabelRow("MISSION NAME", "lbl-edit", LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    missionNameField = newInputField("ORBITLAB-LEO-001", FIELD_W, FIELD_H);
    root.addChild(missionNameField);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // --- Dynamic parameters, one panel per card ---
    for (MissionProfile profile : MissionProfile.earthOrbitProfiles()) {
      dynamicParametersMap.put(
          profile, new EarthOrbitDynamicParameters(profile, launchLatitudeDeg));
    }
    dynamicParametersMap.put(MissionProfile.GEO, new GEODynamicParameters(200, 2000));
    dynamicParameters = dynamicParametersMap.get(selectedProfile);
    dynamicParametersContainer = new Container(new BoxLayout(Axis.Y, FillMode.None));
    dynamicParametersContainer.setBackground(null);
    root.addChild(dynamicParametersContainer);

    // --- Launch date and mission duration, side by side ---
    // Two columns rather than two stacked blocks: the step has no vertical room to spare (the root
    // is pinned to FormStyles.CONTENT_HEIGHT and nothing clips, so an overflow lands on the
    // footer),
    // and the two fields belong together anyway — when it starts, and how long it runs.
    launchDateField = newInputField(OrekitTime.utcNowString(), DATE_FIELD_W, FIELD_H);
    launchDateHelper = new Label(LAUNCH_DATE_HELPER, FormStyles.STYLE);
    launchDateHelper.setFont(UiKit.ibmPlexMono(11));
    launchDateHelper.setColor(FormStyles.TEXT_LO);

    horizonField = newInputField("", HORIZON_FIELD_W, FIELD_H);
    // Under its own column, like every other field's helper: same slot, same role — provenance
    // while
    // the entry is good, the reason while it is refused. The auto/manual state is NOT written here;
    // the button carries it, and saying it twice is what made this line read as noise.
    horizonHelper = new Label("", FormStyles.STYLE);
    horizonHelper.setFont(UiKit.ibmPlexMono(11));
    horizonHelper.setColor(FormStyles.TEXT_LO);

    Container columns = new Container(new BoxLayout(Axis.X, FillMode.None));
    columns.setBackground(null);
    dateRow = new Container(new BoxLayout(Axis.X, FillMode.None));
    dateRow.setBackground(null);
    dateRow.addChild(launchDateField);
    planningGap = UiKit.hSpacer(16f);
    dateRow.addChild(planningGap);
    planningIndicator = buildPlanningIndicator();
    dateRow.addChild(planningIndicator);
    columns.addChild(fieldColumn("LAUNCH DATE", "lbl-clock", dateRow, launchDateHelper));
    columns.addChild(UiKit.hSpacer(COLUMN_GAP));
    columns.addChild(
        fieldColumn("MISSION DURATION", "lbl-clock", buildHorizonRow(), horizonHelper));
    root.addChild(columns);

    for (DynamicParameters params : dynamicParametersMap.values()) {
      CursorEventControl.addListenersToSpatial(
          params.getContainer(),
          new DefaultCursorListener() {
            @Override
            public void cursorButtonEvent(
                CursorButtonEvent event, Spatial target, Spatial capture) {
              if (event.getButtonIndex() == 0 && event.isPressed()) {
                Spatial currentFocus = GuiGlobals.getInstance().getFocusManagerState().getFocus();
                if (currentFocus != null && target != currentFocus) {
                  if (!(target instanceof TextField)) {
                    GuiGlobals.getInstance().requestFocus(null);
                  }
                }
              }
            }
          });
    }
    pageHost = new Container(new BoxLayout(Axis.Y, FillMode.None));
    pageHost.setBackground(null);
    pageHost.setPreferredSize(new Vector3f(FormStyles.CONTENT_WIDTH, FormStyles.CONTENT_HEIGHT, 0));
    pageHost.addChild(root);
    planningPage.setOnBack(() -> showPage(Page.FIELDS));

    updateDynamicParameters(0);
    refreshHorizonFromDerived();
  }

  /**
   * One labelled column: the field's label above, the control, and its helper line below. Nothing
   * here is given a preferred width — each column takes the width of its widest child, which is
   * what keeps a helper longer than its field from being clipped.
   *
   * @param label the field label, in the form's uppercase convention
   * @param iconName the wizard icon shown beside the label
   * @param control the field, or a row of widgets acting as one
   * @param helper the helper line, already styled
   * @return the assembled column
   */
  private static Container fieldColumn(String label, String iconName, Panel control, Label helper) {
    Container column = new Container(new BoxLayout(Axis.Y, FillMode.None));
    column.setBackground(null);
    column.addChild(fieldLabelRow(label, iconName, LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    column.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    column.addChild(control);
    column.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    column.addChild(helper);
    return column;
  }

  /**
   * Wraps a widget shorter than the field it sits beside so it lines up on the row's centre line
   * rather than on its top edge. Padding, not alignment: a widget sizes itself to its content, so
   * there is no box for an alignment to work inside — the same reason {@code
   * DynamicParameters.buildSliderRow} centres its slider this way.
   *
   * @param child the widget to centre against a {@link #FIELD_H}-tall row
   * @return the wrapper to add to the row
   */
  private static Container centeredInRow(Panel child) {
    Container wrap = new Container(new BoxLayout(Axis.Y, FillMode.None));
    wrap.setBackground(null);
    float pad = Math.max(0f, (FIELD_H - child.getPreferredSize().y) * 0.5f);
    // Padded on both sides, not just the top: that makes the wrapper exactly FIELD_H tall, so
    // wrapping something already centred is a no-op instead of shifting it down by half the
    // leftover — which is precisely how the auto indicator ended up sitting low.
    wrap.addChild(UiKit.vSpacer(pad));
    wrap.addChild(child);
    wrap.addChild(UiKit.vSpacer(pad));
    return wrap;
  }

  /**
   * The duration field, its unit and the AUTO button.
   *
   * <p>No custom insets on the button: they shrink the content box that {@code VAlignment.Center}
   * then centres in, so a bottom inset visibly pushes the label up. Preferred size plus alignment
   * is enough, and it matches the recipe {@code WizardFooter} already uses for its own buttons.
   */
  private Container buildHorizonRow() {
    // No preferred size on the row: forcing one made the sum of the children exceed it — the unit
    // label reports a width of its own whatever is asked of it — and Lemur then squeezed the last
    // child, so the button rendered narrower than its text and wrapped to two lines.
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.addChild(horizonField);
    row.addChild(UiKit.hSpacer(8f));

    Label unit = new Label("days", FormStyles.STYLE);
    unit.setFont(UiKit.ibmPlexMono(11));
    unit.setColor(FormStyles.TEXT_LO);
    row.addChild(centeredInRow(unit));

    row.addChild(UiKit.hSpacer(16f));

    // Not wrapped again: the indicator centres its own two parts against the row already.
    row.addChild(buildAutoIndicator());

    return row;
  }

  /**
   * The auto/manual control: a status dot and the word {@code auto}, no button chrome.
   *
   * <p>A filled pill was tried first and read wrong — the strongest element of the whole form was
   * the secondary control, heavier than the field it governs. The state now lives in the dot, which
   * lets the control itself stay quiet.
   */
  private Container buildAutoIndicator() {
    Container indicator = new Container(new BoxLayout(Axis.X, FillMode.None));
    indicator.setBackground(null);

    // Reuses the slider thumb's texture purely for its shape — it is the only round asset in the
    // wizard atlas. The tint is what carries the state; the texture only makes the dot a disc
    // instead of a square. Missing asset degrades to a plain coloured quad, which still reads.
    autoDot = new Panel(AUTO_DOT_SIZE, AUTO_DOT_SIZE, FormStyles.STYLE);
    indicator.addChild(centeredInRow(autoDot));
    indicator.addChild(UiKit.hSpacer(7f));

    // Still a Button, for its click and hover handling — but stripped of every visual: no
    // background, and the style's (10, 22, 10, 22) insets cleared, or the word would sit in a
    // 44-pixel-wide invisible box.
    autoButton = new Button("auto", FormStyles.STYLE);
    autoButton.setBackground(null);
    autoButton.setInsets(new Insets3f(0, 0, 0, 0));
    autoButton.setFont(UiKit.ibmPlexMono(11));
    autoButton.addClickCommands(src -> resetHorizonToDerived());
    MouseEventControl.addListenersToSpatial(
        autoButton,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
            autoHovered = true;
            applyAutoIndicator();
          }

          @Override
          public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
            autoHovered = false;
            applyAutoIndicator();
          }
        });
    indicator.addChild(centeredInRow(autoButton));

    applyAutoIndicator();
    return indicator;
  }

  /**
   * Paints the indicator from the state it reports. This is the only place the auto/manual
   * distinction is shown, which is why the helper line below no longer repeats it: dot lit and word
   * in the accent while the duration is derived, both dimmed once the user has typed their own.
   */
  private void applyAutoIndicator() {
    ColorRGBA tint = horizonAuto ? FormStyles.ACCENT_BRIGHT : FormStyles.BORDER;
    QuadBackgroundComponent dotBg = UiKit.wizardFlat("slider-thumb");
    dotBg.setColor(tint);
    autoDot.setBackground(dotBg);

    ColorRGBA word;
    if (horizonAuto) {
      word = FormStyles.ACCENT_BRIGHT;
    } else {
      word = autoHovered ? FormStyles.TEXT_SECONDARY : FormStyles.TEXT_LO;
    }
    autoButton.setColor(word);
  }

  /**
   * The planning control: a status dot and the word {@code planning}, no button chrome — the same
   * grammar as the duration's AUTO indicator, and for the same reason. It says the state as well as
   * opening the page: the dot is lit exactly when a target node is set, which is exactly when the
   * launch date is governed by a window rather than by what was typed.
   */
  private Container buildPlanningIndicator() {
    Container indicator = new Container(new BoxLayout(Axis.X, FillMode.None));
    indicator.setBackground(null);

    planningDot = new Panel(AUTO_DOT_SIZE, AUTO_DOT_SIZE, FormStyles.STYLE);
    indicator.addChild(centeredInRow(planningDot));
    indicator.addChild(UiKit.hSpacer(7f));

    planningButton = new Button("planning", FormStyles.STYLE);
    planningButton.setBackground(null);
    planningButton.setInsets(new Insets3f(0, 0, 0, 0));
    planningButton.setFont(UiKit.ibmPlexMono(11));
    planningButton.addClickCommands(source -> showPage(Page.PLANNING));
    MouseEventControl.addListenersToSpatial(
        planningButton,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
            planningHovered = true;
            applyPlanningIndicator();
          }

          @Override
          public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
            planningHovered = false;
            applyPlanningIndicator();
          }
        });
    indicator.addChild(centeredInRow(planningButton));

    applyPlanningIndicator();
    return indicator;
  }

  /**
   * Paints the planning indicator from the node the page holds: dot lit and word in the accent while
   * a plane is being waited for, both dimmed while none is.
   *
   * <p>Unlike {@link #applyAutoIndicator()}, which only ever runs on a change, this one is called
   * from {@link #update(float)} — the node lives on the other page and can be edited without this
   * step hearing about it. The entry is therefore parsed on every frame, which is cheap; what the
   * guard skips is the repaint, which loads a background component, re-attaches it and invalidates
   * the row's layout, the way {@link #setHorizonHelper} skips its own. The word's colour is enough
   * to detect the no-op: the accent means, and only means, that a node is set.
   */
  private void applyPlanningIndicator() {
    boolean planned = planningPage.parsedRaanDeg().isPresent();
    ColorRGBA word;
    if (planned) {
      word = FormStyles.ACCENT_BRIGHT;
    } else {
      word = planningHovered ? FormStyles.TEXT_SECONDARY : FormStyles.TEXT_LO;
    }
    if (planningPainted && word.equals(planningButton.getColor())) {
      return;
    }
    planningPainted = true;

    QuadBackgroundComponent dotBg = UiKit.wizardFlat("slider-thumb");
    dotBg.setColor(planned ? FormStyles.ACCENT_BRIGHT : FormStyles.BORDER);
    planningDot.setBackground(dotBg);
    planningButton.setColor(word);
  }

  /**
   * Attaches or detaches the planning indicator to match the card on screen. A GEO mission carries
   * no target node — {@code MissionSpec.Geo} has no such component and {@code
   * MissionWizardAppState.scheduledDateFor} only schedules an {@code EarthOrbit} — so the control is
   * absent there rather than greyed: nothing could ever light it.
   */
  private void updatePlanningIndicator() {
    boolean shown = hasTargetNode();
    if (shown == planningIndicatorShown) {
      return;
    }
    if (shown) {
      dateRow.addChild(planningGap);
      dateRow.addChild(planningIndicator);
    } else {
      dateRow.removeChild(planningGap);
      dateRow.removeChild(planningIndicator);
    }
    planningIndicatorShown = shown;
  }

  /**
   * Whether the card on screen has a target node at all — the one predicate {@link
   * #updatePlanningIndicator()} and {@link #validateTargetNode()} must share, since one decides
   * whether the entry point is shown and the other whether a refusal can be raised.
   *
   * @return whether the selected profile carries a target node
   */
  private boolean hasTargetNode() {
    return selectedProfile != MissionProfile.GEO;
  }

  /** Hands the duration back to the derived policy, clearing any refused entry. */
  private void resetHorizonToDerived() {
    horizonAuto = true;
    clearHorizonRejection();
    refreshHorizonFromDerived();
    applyAutoIndicator();
  }

  /**
   * Keeps the duration field in step with the target orbit while it is on auto, and notices the
   * first keystroke that takes it off auto.
   *
   * <p>The auto state needs no widget of its own: the field's text either is what this step last
   * wrote, or it is not.
   */
  private void updateHorizon() {
    if (horizonAuto && !horizonField.getText().equals(lastAutoHorizonText)) {
      // The user typed over the prefill. From here the value is theirs until AUTO is pressed.
      horizonAuto = false;
      applyAutoIndicator();
    }
    if (horizonAuto) {
      refreshHorizonFromDerived();
      return;
    }
    if (rejectedHorizon != null) {
      if (!rejectedHorizon.equals(horizonField.getText())) {
        clearHorizonRejection();
      }
      return;
    }
    setHorizonHelper(HORIZON_MANUAL_HELPER, FormStyles.TEXT_LO);
  }

  /** Writes the derived duration into the field and says where the number comes from. */
  private void refreshHorizonFromDerived() {
    String text = formatDays(dynamicParameters.defaultHorizonDays());
    if (!text.equals(horizonField.getText())) {
      horizonField.setText(text);
    }
    lastAutoHorizonText = text;
    setHorizonHelper(
        derivedHelper(MissionHorizon.defaultFor(missionContext.getSelectedMissionType())),
        FormStyles.TEXT_LO);
  }

  /**
   * The provenance line for a derived horizon, in the wizard's language. Built here rather than
   * from {@code MissionHorizon.describe()}: that one is English, because the code is, and the UI is
   * not.
   */
  private static String derivedHelper(MissionHorizon horizon) {
    return switch (horizon) {
      case MissionHorizon.Revolutions r ->
          r.count() + (r.count() > 1 ? " revolutions" : " revolution") + " of the target orbit";
      // Not produced by defaultFor today; the switch stays exhaustive so adding a case is a
      // compile error here rather than a silently wrong line on screen.
      case MissionHorizon.FixedDuration ignored -> HORIZON_MANUAL_HELPER;
      case MissionHorizon.TrailingCoast ignored -> HORIZON_MANUAL_HELPER;
    };
  }

  /** Writes the helper line, skipping the no-op: this runs on every frame of the wizard. */
  private void setHorizonHelper(String text, ColorRGBA color) {
    if (!text.equals(horizonHelper.getText())) {
      horizonHelper.setText(text);
    }
    horizonHelper.setColor(color);
  }

  private static String formatDays(double days) {
    return String.format(java.util.Locale.ROOT, "%.2f", days);
  }

  public Container getNode() {
    return pageHost;
  }

  /**
   * Mounts one of the step's two pages.
   *
   * <p>A step is always entered by its main page — see {@link #onStepEntered()} — so the only thing
   * that mounts the planning page without a click is a refusal on a field the planning page holds.
   */
  private void showPage(Page target) {
    if (page == target) {
      return;
    }
    pageHost.clearChildren();
    pageHost.addChild(target == Page.FIELDS ? root : planningPage.getNode());
    page = target;
  }

  /** Called by the wizard whenever this step is shown: a step opens on its fields. */
  public void onStepEntered() {
    showPage(Page.FIELDS);
  }

  @Override
  public Map<String, Object> getValues() {
    Map<String, Object> values = new HashMap<>();
    values.put(FormField.MISSION_NAME.key(), missionNameField.getText());
    values.putAll(dynamicParameters.getDynamicValues());
    values.putAll(planningPage.getValues());
    values.put(FormField.LAUNCH_DATE.key(), launchDateField.getText());
    // Published only when overridden: an absent key IS the auto state, which is what lets a mission
    // reopened in the wizard come back on auto without a second key to carry it.
    if (!horizonAuto) {
      values.put(FormField.MISSION_HORIZON_DAYS.key(), horizonField.getText());
    }
    return values;
  }

  @Override
  public void applyValues(Map<String, Object> values) {
    String name = FormValues.string(values, FormField.MISSION_NAME);
    if (name != null) {
      missionNameField.setText(name);
    }
    String launchDate = FormValues.string(values, FormField.LAUNCH_DATE);
    if (launchDate != null) {
      launchDateField.setText(launchDate);
      clearLaunchDateRejection();
    }
    // Applied to the parameters of the profile carried by the values, not to the ones currently on
    // screen: the panel is swapped by update(), which has not necessarily run yet.
    DynamicParameters target = dynamicParametersMap.get(profileOf(values));
    if (target != null) {
      target.applyValues(values);
    }
    planningPage.applyValues(values);
    applyHorizon(values);
  }

  /**
   * Restores the duration field from previously published values. The key's absence is meaningful —
   * it means the mission was left on the derived default — so it puts the field back on auto rather
   * than leaving whatever the previous edit had shown.
   */
  private void applyHorizon(Map<String, Object> values) {
    Object raw = values.get(FormField.MISSION_HORIZON_DAYS.key());
    if (raw == null || raw.toString().isBlank()) {
      resetHorizonToDerived();
      return;
    }
    horizonAuto = false;
    clearHorizonRejection();
    horizonField.setText(raw.toString().trim());
    applyAutoIndicator();
  }

  /**
   * Switches the step to the card the user picked, and tells the mission context about the type
   * behind it. Called by the wizard rather than polled, because the profile lives here and not in
   * {@code MissionContext}.
   *
   * @param profile the selected profile
   */
  public void setProfile(MissionProfile profile) {
    this.selectedProfile = profile;
  }

  /**
   * Reads the profile out of the raw values, falling back on the one on screen. A value map written
   * before P2 carries no profile at all, only a type; it then resolves to GEO or to the currently
   * selected Earth-orbit card, which is the historical behaviour.
   */
  private MissionProfile profileOf(Map<String, Object> values) {
    String raw = FormValues.string(values, FormField.MISSION_PROFILE);
    if (raw != null) {
      try {
        return MissionProfile.valueOf(raw);
      } catch (IllegalArgumentException ignored) {
        // Falls through to the type-based reading below.
      }
    }
    String type = FormValues.string(values, FormField.MISSION_TYPE);
    if (MissionType.GEO.name().equals(type)) {
      return MissionProfile.GEO;
    }
    return selectedProfile;
  }

  public void update(float tpf) {
    titleLabel.setText("PARAMETERS " + selectedProfile.title());
    if (rejectedLaunchDate != null && !rejectedLaunchDate.equals(launchDateField.getText())) {
      clearLaunchDateRejection();
    }
    updateDynamicParameters(tpf);
    updatePlanningIndicator();
    // After the panel swap, so the derived duration is read off the parameters now on screen.
    updateHorizon();
    applyPlanningIndicator();
    planningPage.update(tpf);
    planningPage.refresh(
        currentWindowRequest(), TimeConverter.parseUtcDate(launchDateField.getText()).orElse(null));
  }

  /**
   * The window inputs, assembled from the three places that hold them: the site step, the panel on
   * screen, and this step's own planning page.
   *
   * <p><b>Null as soon as one of them cannot supply its part.</b> An unreadable pad is not worth a
   * wrong answer: a window computed at latitude 0 because the user was mid-keystroke would be a
   * false answer presented as a true one (spec {@code docs/mission-window/02-timeline-wizard.md}
   * §6).
   *
   * @return the request, or null while the form cannot describe one
   */
  private EarthLaunchWindowRequest currentWindowRequest() {
    Optional<Double> raan = planningPage.parsedRaanDeg();
    Optional<SiteCoordinates> site = launchSite.get();
    if (raan.isEmpty() || site.isEmpty()) {
      return null;
    }
    Optional<DynamicParameters.TargetOrbit> orbit =
        dynamicParameters.targetOrbit(site.get().latitude());
    return orbit
        .map(
            target ->
                new EarthLaunchWindowRequest(
                    site.get().latitude(),
                    site.get().longitude(),
                    site.get().altitude(),
                    target.plane(),
                    raan.get(),
                    target.semiMajorAxis()))
        .orElse(null);
  }

  /**
   * Checks the launch date and marks the field when it cannot be used, so a bad entry is caught
   * while the wizard is still open rather than swallowed at mission creation.
   *
   * <p>Accepts the same entries as the timeline date field, including the ISO form this very field
   * is prefilled with.
   *
   * @return the reason the date was refused, or empty when it is usable
   */
  public Optional<String> validateLaunchDate() {
    String text = launchDateField.getText();
    Optional<AbsoluteDate> parsed = TimeConverter.parseUtcDate(text);
    if (parsed.isEmpty()) {
      return Optional.of(rejectLaunchDate(text, LAUNCH_DATE_FORMAT_HELPER));
    }
    if (!EphemerisWindow.covers(parsed.get())) {
      return Optional.of(
          rejectLaunchDate(
              text, "hors couverture ephemeride : " + EphemerisWindow.rangeLabel().orElse("")));
    }
    clearLaunchDateRejection();
    return Optional.empty();
  }

  private String rejectLaunchDate(String text, String message) {
    rejectedLaunchDate = text;
    launchDateField.setColor(FormStyles.DANGER);
    launchDateHelper.setText(message);
    launchDateHelper.setColor(FormStyles.DANGER);
    return message;
  }

  private void clearLaunchDateRejection() {
    rejectedLaunchDate = null;
    launchDateField.setColor(FormStyles.TEXT_PRIMARY);
    launchDateHelper.setText(LAUNCH_DATE_HELPER);
    launchDateHelper.setColor(FormStyles.TEXT_LO);
  }

  /**
   * Checks the mission duration and marks the field when it cannot be used, on the same contract as
   * {@link #validateLaunchDate()}: caught while the wizard is still open rather than degraded
   * silently to the derived default at mission creation.
   *
   * <p>Always accepts an untouched field: the derived default is by construction within bounds.
   *
   * @return the reason the duration was refused, or empty when it is usable
   */
  public Optional<String> validateHorizon() {
    if (horizonAuto) {
      clearHorizonRejection();
      return Optional.empty();
    }
    String text = horizonField.getText().trim();
    double days;
    try {
      days = Double.parseDouble(text);
    } catch (NumberFormatException e) {
      return Optional.of(rejectHorizon(text, HORIZON_FORMAT_HELPER));
    }
    if (!Double.isFinite(days) || days < HORIZON_MIN_DAYS || days > HORIZON_MAX_DAYS) {
      return Optional.of(rejectHorizon(text, HORIZON_FORMAT_HELPER));
    }
    clearHorizonRejection();
    return Optional.empty();
  }

  /**
   * Checks the target inclination against the launch site, on the contract of {@link
   * #validateLaunchDate()}. Delegated to the panel on screen, which is the only one that knows
   * whether its profile has an inclination to check at all.
   *
   * <p>Covers the target node too, which lives on the planning page rather than on the panel: both
   * describe the plane being aimed at, so one refusal serves them both.
   *
   * <p>Marking only: which page the marks are then shown on is {@link #revealRefusal()}'s, so that
   * the choice weighs every refused field of the step and not just the two this method makes.
   *
   * @return the reason the inclination or the node was refused, or empty when both are usable
   */
  public Optional<String> validateTargetPlane() {
    Optional<String> inclination = dynamicParameters.validateTargetPlane();
    // Both run, neither short-circuits: a user with two bad fields should see both marked.
    Optional<String> node = validateTargetNode();
    return inclination.isPresent() ? inclination : node;
  }

  /**
   * Checks the target node, where the card has one to check.
   *
   * <p>GEO has none: {@code MissionSpec.Geo} carries no node component, which is why the planning
   * indicator is detached there. Without this gate a node typed on another card would still be read
   * and could refuse a GEO mission over a field whose entry point that card has just removed. The
   * standing refusal is cleared rather than left, on the same reasoning: a mark must not outlive the
   * field able to show it.
   *
   * @return the reason the node was refused, or empty when it is usable or has no meaning here
   */
  private Optional<String> validateTargetNode() {
    if (!hasTargetNode()) {
      planningPage.clearRejection();
      return Optional.empty();
    }
    return planningPage.validateTargetNode();
  }

  /**
   * Mounts the page holding whichever field was refused, reading the marks the validators left
   * rather than running them again.
   *
   * <p>It exists because entering a step resets it to its fields page ({@link #onStepEntered()}),
   * which would otherwise clobber the page a refusal had just selected — the wizard refuses on the
   * launcher step too, since the stepper lets the parameters be flown over.
   */
  public void revealRefusal() {
    switch (RefusedPage.choose(
        rejectedLaunchDate != null,
        rejectedHorizon != null,
        dynamicParameters.hasRejection(),
        planningPage.hasRejection())) {
      case FIELDS -> showPage(Page.FIELDS);
      case PLANNING -> showPage(Page.PLANNING);
      case NONE -> {}
    }
  }

  private String rejectHorizon(String text, String message) {
    rejectedHorizon = text;
    horizonField.setColor(FormStyles.DANGER);
    setHorizonHelper(message, FormStyles.DANGER);
    return message;
  }

  private void clearHorizonRejection() {
    rejectedHorizon = null;
    horizonField.setColor(FormStyles.TEXT_PRIMARY);
    // The neutral line; on auto, the next updateHorizon() replaces it with the derived description.
    setHorizonHelper(HORIZON_MANUAL_HELPER, FormStyles.TEXT_LO);
  }

  private void updateDynamicParameters(float tpf) {
    if (selectedProfile != shownProfile) {
      DynamicParameters next =
          Optional.ofNullable(dynamicParametersMap.get(selectedProfile))
              .orElseThrow(
                  () ->
                      new OrbitlabException(
                          "No dynamic parameters for mission profile " + selectedProfile));
      dynamicParametersContainer.clearChildren();
      dynamicParametersContainer.addChild(next.getContainer());
      dynamicParameters = next;
      shownProfile = selectedProfile;
    }
    dynamicParameters.update(tpf);
  }
}
