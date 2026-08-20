Je regarde d'abord ce qui existe déjà pour ne pas dupliquer une abstraction voisine.## Ma recommandation en une phrase

**Balayage grossier + affinage par section dorée**, avec un problème qui expose *une* fonction de mérite scalaire en m/s et *son propre* pas de balayage — et une évaluation en deux étages (géométrie fermée pour le criblage, vol propagé pour la confirmation).

Le reste de la réponse justifie ce choix et pose les types.

---

## 1. Le découpage que je propose

Quatre types, et un seul solveur.

| Type | Rôle |
|---|---|
| `LaunchWindowProblem` | **l'abstraction de l'objectif** — sait, pour une date, ce que coûte le tir et s'il est faisable. C'est le seul point d'extension (Lune aujourd'hui, ISS demain). |
| `LaunchWindowSearch` | **les critères de recherche** — plage, pas de balayage, **précision**, seuil d'acceptation, nombre de créneaux voulus. |
| `LaunchWindowCandidate` | une évaluation : date, Δv, ou le motif du refus. |
| `LaunchWindow` | un créneau : ouverture, meilleur instant, fermeture. |
| `LaunchWindowSolver` | la numérique, **une seule implémentation**. |

**Pourquoi le solveur n'est pas abstrait.** Ce qui change d'un objectif à l'autre, c'est la fonction de mérite, pas la façon de chercher son minimum : un balayage/affinage sur ℝ ne sait rien de la Lune. Une interface `RefinementStrategy` avec une seule implémentation serait du vide. La couture existe (l'affinage tient en une méthode), on la sortira au deuxième cas qui la réclame — la règle de `dette-technique.md` §6.3.

**Un point de nommage** : `LaunchWindowProblem` et pas `…Objective`, parce que `MissionObjective` existe déjà et dit autre chose (l'orbite visée). Et c'est votre mot : « chaque implémentation définit son problème ».

---

## 2. L'unité de la fonction de mérite : des m/s, toujours

C'est la décision structurante. Les critères que cite la roadmap sont hétérogènes — alignement de plan (rad), RAAN cible (rad), géométrie Terre-Lune (distance) — et rien ne permet de les comparer ni de les seuiller tant qu'ils ne sont pas dans la même unité.

Le Δv les ramène tous à un même axe : un résidu de plan θ vaut `2·v·sin(θ/2)` de correction, un excès de distance lunaire vaut ce que la vis-viva en dit. Deux bénéfices immédiats :

- le **seuil d'acceptation** est un budget d'ergols, donc une grandeur que la mission connaît déjà ;
- l'UI demandée par la roadmap (« timeline des créneaux ouverts, avec le Δv associé ») lit le champ tel quel, sans conversion par type d'objectif.

Corollaire : `Double.POSITIVE_INFINITY` (ou un `refusal` non nul) encode l'infaisable, et le solveur n'a aucun cas particulier à connaître.

---

## 3. Les types

```java
package com.smousseur.orbitlab.simulation.mission.window;

import java.time.Duration;
import org.orekit.time.AbsoluteDate;

/**
 * What a launch window is being searched <em>for</em>: the abstraction MIS-2 turns on.
 *
 * <p>An implementation answers one question — <b>what would it cost to leave at this instant, and
 * can it be done at all?</b> — and declares the timescale on which that answer varies. Everything
 * else (scanning, bracketing, refining, cutting the window edges) belongs to {@link
 * LaunchWindowSolver} and is the same for every target.
 *
 * <p><b>The cost is always a Δv in m/s</b>, never a plane angle or a distance. Heterogeneous
 * criteria — plane alignment, target RAAN, Earth-Moon geometry — cannot be compared or thresholded
 * until they are in one unit, and the unit that carries meaning here is the one the propellant
 * budget is written in. A residual out-of-plane angle θ is worth {@code 2·v·sin(θ/2)}; state it
 * that way and the acceptance threshold becomes a budget rather than a tuning constant.
 *
 * <p><b>The initial state is not a parameter of the search</b>, and that is the reason it does not
 * appear anywhere in this package: it is a <em>function of the candidate date</em>. A translunar
 * parking orbit is built around where the Moon will be; an ascent state is the launch site rotated
 * to that epoch. An implementation derives it from the epoch it is handed.
 */
public interface LaunchWindowProblem {

  /**
   * @return a human-readable name, used in logs and in the wizard's window timeline
   */
  String name();

  /**
   * Evaluates one candidate epoch.
   *
   * <p><b>Must never throw</b> for an epoch that simply does not work: an unreachable geometry is a
   * {@link LaunchWindowCandidate#refused refusal}, which is data the solver walks past, whereas an
   * exception would abort a sweep because one of its two hundred samples was bad. Throwing stays
   * legitimate for a broken configuration — a negative mass, a missing ephemeris.
   *
   * <p>Called on the order of {@code span/step + 30·windows} times, so it is what sets the cost of
   * a search. Keep it closed-form; see {@link #confirm}.
   *
   * @param epoch the candidate launch (or injection) date
   * @return the cost of leaving at that instant, or a refusal
   */
  LaunchWindowCandidate evaluate(AbsoluteDate epoch);

  /**
   * The coarse sweep step this problem needs to be sampled at, which only the problem can know.
   *
   * <p><b>This is a sampling theorem, not a preference.</b> The step must be shorter than half the
   * narrowest feature of the merit function, or the sweep steps over the windows entirely: the
   * Earth-Moon geometry is monthly and tolerates hours, an ISS plane alignment recurs about twice a
   * day and is minutes wide. A search that imposed its own step would silently return "no window"
   * on the second case.
   *
   * @return the coarse sampling step
   */
  Duration coarseStep();

  /**
   * Confirms a refined candidate under the full model — the expensive second tier.
   *
   * <p>The default does nothing, which is the honest answer for a problem whose {@link #evaluate}
   * is already the truth. It exists for the translunar case, where the closed-form Lambert cost is
   * a good ranking and a poor verdict: what actually refuses an epoch there is the flown perilune
   * floor, and measuring it costs some thirty propagations of four days. Screening on the cheap
   * criterion and confirming only the handful of survivors is what keeps a sixty-day search under a
   * minute instead of over an hour.
   *
   * @param candidate the refined candidate to confirm
   * @return the confirmed candidate, possibly re-costed, or a refusal
   */
  default LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
    return candidate;
  }
}
```


```java
package com.smousseur.orbitlab.simulation.mission.window;

import org.orekit.time.AbsoluteDate;

/**
 * One evaluation of one epoch: what it costs, or why it cannot be flown.
 *
 * <p>A refusal carries its reason as text because that reason is the only thing a user can act on —
 * "the lunar declination exceeds the parking inclination" and "the perilune floor is 135 km against
 * a 100 km target" are two different pieces of advice, and an enum would flatten both into
 * {@code INFEASIBLE}.
 *
 * @param epoch the evaluated date
 * @param deltaV the cost of leaving at {@code epoch} (m/s), {@link Double#POSITIVE_INFINITY} when
 *     refused
 * @param refusal why the epoch cannot be flown, or {@code null} when it can
 */
public record LaunchWindowCandidate(AbsoluteDate epoch, double deltaV, String refusal) {

  /**
   * @param epoch the evaluated date
   * @param deltaV the cost of leaving at that date (m/s)
   * @return a flyable candidate
   */
  public static LaunchWindowCandidate of(AbsoluteDate epoch, double deltaV) {
    return new LaunchWindowCandidate(epoch, deltaV, null);
  }

  /**
   * @param epoch the evaluated date
   * @param refusal why it cannot be flown
   * @return a refused candidate, at infinite cost
   */
  public static LaunchWindowCandidate refused(AbsoluteDate epoch, String refusal) {
    return new LaunchWindowCandidate(epoch, Double.POSITIVE_INFINITY, refusal);
  }

  /**
   * @return {@code true} when this epoch can be flown at a finite cost
   */
  public boolean feasible() {
    return refusal == null && Double.isFinite(deltaV);
  }
}
```


```java
package com.smousseur.orbitlab.simulation.mission.window;

import java.time.Duration;
import org.orekit.time.AbsoluteDate;

/**
 * An open slot: the interval over which the cost stays inside the search's budget, and the instant
 * inside it that costs least.
 *
 * <p><b>An interval and not a date</b>, because that is what the word means and what the wizard has
 * to draw: an instant cannot be aimed at by a countdown, and the width of the slot <em>is</em> the
 * operational margin. A caller that only wants a date reads {@link #best}.
 *
 * @param opening the first instant whose cost is within budget
 * @param best the cheapest candidate of the slot
 * @param closing the last instant whose cost is within budget
 */
public record LaunchWindow(AbsoluteDate opening, LaunchWindowCandidate best, AbsoluteDate closing) {

  /**
   * @return how long the slot stays open
   */
  public Duration duration() {
    return Duration.ofMillis(Math.round(closing.durationFrom(opening) * 1000.0));
  }

  /**
   * @return the cheapest date of the slot — the one a mission is scheduled on
   */
  public AbsoluteDate date() {
    return best.epoch();
  }
}
```


```java
package com.smousseur.orbitlab.simulation.mission.window;

import com.smousseur.orbitlab.core.OrbitlabException;
import java.time.Duration;
import org.orekit.time.AbsoluteDate;

/**
 * The search criteria: where to look, how finely, and what counts as acceptable.
 *
 * <p><b>{@code precision} is a duration and not an iteration count</b>, because it is the only form
 * the caller can reason about: "to the minute" is a decision, "twelve golden-section steps" is an
 * implementation detail. It is also what bounds the cost — the refinement of one window takes
 * {@code log(step/precision)/log(1.618)} evaluations, so asking for a second instead of a minute
 * costs nine more evaluations, not sixty times more.
 *
 * <p><b>{@code maxDeltaV} is what makes a window a window.</b> Without an acceptance threshold a
 * merit function has minima but no edges, and the answer degenerates to a list of instants.
 *
 * @param start the first date considered
 * @param span how far past {@code start} the search runs
 * @param step the coarse sweep step; must come from {@link LaunchWindowProblem#coarseStep()} unless
 *     the caller knows better
 * @param precision the time resolution the optimum and the edges are refined to
 * @param maxDeltaV the cost above which an epoch is not offered (m/s)
 * @param maxWindows how many slots to return, cheapest first
 */
public record LaunchWindowSearch(
    AbsoluteDate start,
    Duration span,
    Duration step,
    Duration precision,
    double maxDeltaV,
    int maxWindows) {

  public LaunchWindowSearch {
    if (span.isNegative() || span.isZero()) {
      throw new OrbitlabException("the search span must be positive, got " + span);
    }
    if (step.isNegative() || step.isZero() || step.compareTo(span) > 0) {
      throw new OrbitlabException("the sweep step must be positive and fit in the span, got " + step);
    }
    if (precision.isNegative() || precision.isZero() || precision.compareTo(step) >= 0) {
      // A precision coarser than the step would refine nothing; equal to it, the refinement is a
      // no-op that still costs its evaluations. Both are configuration mistakes, not edge cases.
      throw new OrbitlabException(
          "the precision must be positive and finer than the sweep step, got " + precision);
    }
    if (!(maxDeltaV > 0.0)) {
      throw new OrbitlabException("the delta-v budget must be positive, got " + maxDeltaV);
    }
    if (maxWindows < 1) {
      throw new OrbitlabException("at least one window must be asked for, got " + maxWindows);
    }
  }

  /**
   * The usual search: the problem's own sweep step, a precision a tenth of it, and one window.
   *
   * @param start the first date considered
   * @param span how far past {@code start} to look
   * @param problem the problem whose sampling scale is adopted
   * @param maxDeltaV the acceptance budget (m/s)
   * @return the search
   */
  public static LaunchWindowSearch over(
      AbsoluteDate start, Duration span, LaunchWindowProblem problem, double maxDeltaV) {
    Duration step = problem.coarseStep();
    return new LaunchWindowSearch(start, span, step, step.dividedBy(10L), maxDeltaV, 1);
  }

  /**
   * @return a copy of this search returning up to {@code count} windows
   */
  public LaunchWindowSearch withMaxWindows(int count) {
    return new LaunchWindowSearch(start, span, step, precision, maxDeltaV, count);
  }

  /** @return the search span in seconds */
  public double spanSeconds() {
    return span.toNanos() / 1.0e9;
  }

  /** @return the sweep step in seconds */
  public double stepSeconds() {
    return step.toNanos() / 1.0e9;
  }

  /** @return the refinement resolution in seconds */
  public double precisionSeconds() {
    return precision.toNanos() / 1.0e9;
  }
}
```


---

## 4. Le solveur : la méthode, et pourquoi celle-là

Trois passes, la même pour tous les objectifs.

1. **Balayage grossier** au pas du problème → une grille de coûts.
2. **Détection des minima locaux** sur trois points consécutifs (`c[i] ≤ c[i−1]` et `c[i] ≤ c[i+1]`) → un encadrement `[t(i−1), t(i+1)]` par créneau.
3. **Affinage par section dorée** dans cet encadrement jusqu'à `precision`, puis **bissection sur le seuil** vers la gauche et vers la droite pour les deux bords.

```java
package com.smousseur.orbitlab.simulation.mission.window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

/**
 * Finds the open slots of a {@link LaunchWindowProblem} over a time range (MIS-2).
 *
 * <p><b>Three passes, and the same three whatever the target is</b> — which is why this class is
 * concrete and has no strategy seam: a sweep over ℝ knows nothing about the Moon, and only the
 * merit function changes.
 *
 * <ol>
 *   <li><b>Coarse sweep</b> at the problem's own step, producing a grid of costs.
 *   <li><b>Bracketing</b> — every grid sample cheaper than both its neighbours brackets a local
 *       minimum in {@code [t(i-1), t(i+1)]}. Three points are the whole detector.
 *   <li><b>Golden-section refinement</b> inside each bracket down to the requested precision, then
 *       a <b>bisection on the acceptance threshold</b> outwards from the optimum for the two edges.
 * </ol>
 *
 * <p><b>Why golden section rather than the obvious alternatives.</b>
 *
 * <ul>
 *   <li><em>A fine brute-force sweep</em> costs {@code span/precision} evaluations — sixty days to
 *       the minute is 86 400 of them. Free for a closed-form criterion, unaffordable the day a
 *       problem propagates, and this class must not be re-chosen when that happens.
 *   <li><em>A derivative method</em> (Newton, gradient) needs a derivative the problem cannot
 *       supply analytically; approximating it by finite differences is two evaluations per step for
 *       a method that diverges where the merit function is flat — and it <em>is</em> flat around
 *       the optimum, that being the definition of one.
 *   <li><em>A global optimizer</em> (CMA-ES, already in the repository) is non-deterministic and
 *       returns one point, when the question is a list of intervals over a one-dimensional range.
 *   <li><em>Golden section</em> is derivative-free, contracts by a fixed 0.618 per <b>single</b>
 *       evaluation, and cannot diverge inside a bracket. Roughly twenty-four evaluations take an
 *       eight-hour bracket to the second.
 * </ul>
 *
 * <p>Everything is done on offsets in seconds from the search start rather than on {@link
 * AbsoluteDate}, so the caching and the interval arithmetic stay on one type.
 */
public class LaunchWindowSolver {
  private static final Logger logger = LogManager.getLogger(LaunchWindowSolver.class);

  /** {@code (√5 − 1)/2}: the contraction ratio that lets one of the two probes be reused. */
  private static final double GOLDEN_RATIO = 0.6180339887498949;

  /** Hard cap on bisection steps, so a pathological merit function cannot spin. */
  private static final int MAX_EDGE_STEPS = 40;

  private final LaunchWindowProblem problem;
  private final Map<Long, LaunchWindowCandidate> evaluations = new HashMap<>();
  private LaunchWindowSearch search;

  /**
   * @param problem the target whose windows are searched
   */
  public LaunchWindowSolver(LaunchWindowProblem problem) {
    this.problem = problem;
  }

  /**
   * Lists the open slots of this solver's problem, cheapest first.
   *
   * @param search the search criteria
   * @return the windows found, at most {@link LaunchWindowSearch#maxWindows()}, possibly empty
   */
  public List<LaunchWindow> solve(LaunchWindowSearch search) {
    this.search = search;
    this.evaluations.clear();

    double span = search.spanSeconds();
    double step = search.stepSeconds();
    int samples = (int) Math.floor(span / step) + 1;

    double[] grid = new double[samples];
    for (int i = 0; i < samples; i++) {
      grid[i] = costAt(i * step);
    }

    List<LaunchWindow> windows = new ArrayList<>();
    for (int i = 0; i < samples; i++) {
      if (!bracketsMinimum(grid, i)) {
        continue;
      }
      double low = Math.max(0.0, (i - 1) * step);
      double high = Math.min(span, (i + 1) * step);
      LaunchWindowCandidate best = problem.confirm(evaluate(refine(low, high)));
      if (!best.feasible() || best.deltaV() > search.maxDeltaV()) {
        logger.debug(
            "[{}] the candidate near {} is not offered: {}",
            problem.name(),
            best.epoch(),
            best.feasible() ? best.deltaV() + " m/s over budget" : best.refusal());
        continue;
      }
      double centre = best.epoch().durationFrom(search.start());
      windows.add(
          new LaunchWindow(
              search.start().shiftedBy(edge(centre, -step, 0.0)),
              best,
              search.start().shiftedBy(edge(centre, step, span))));
    }

    windows.sort(Comparator.comparingDouble(w -> w.best().deltaV()));
    List<LaunchWindow> kept =
        windows.subList(0, Math.min(search.maxWindows(), windows.size()));
    logger.info(
        "[{}] {} window(s) over {} sampled at {}, {} evaluation(s)",
        problem.name(),
        kept.size(),
        search.span(),
        search.step(),
        evaluations.size());
    return List.copyOf(kept);
  }

  /**
   * Whether sample {@code i} is cheaper than both its neighbours, an out-of-range neighbour being
   * read as infinitely expensive so the two ends of the sweep can hold a window.
   */
  private boolean bracketsMinimum(double[] grid, int i) {
    if (!Double.isFinite(grid[i])) {
      return false;
    }
    double before = i > 0 ? grid[i - 1] : Double.POSITIVE_INFINITY;
    double after = i < grid.length - 1 ? grid[i + 1] : Double.POSITIVE_INFINITY;
    return grid[i] <= before && grid[i] <= after;
  }

  /**
   * Golden-section search on {@code [low, high]}, contracting to the requested precision.
   *
   * <p>Unimodality inside the bracket is assumed, and it is the sweep step that buys it: a bracket
   * two steps wide holds one feature when the step obeys {@link LaunchWindowProblem#coarseStep()}.
   * That is the single reason the step belongs to the problem and not to the search.
   *
   * @return the offset of the cheapest point found (s from the search start)
   */
  private double refine(double low, double high) {
    double a = low;
    double b = high;
    double c = b - GOLDEN_RATIO * (b - a);
    double d = a + GOLDEN_RATIO * (b - a);
    double costC = costAt(c);
    double costD = costAt(d);

    while (b - a > search.precisionSeconds()) {
      if (costC <= costD) {
        b = d;
        d = c;
        costD = costC;
        c = b - GOLDEN_RATIO * (b - a);
        costC = costAt(c);
      } else {
        a = c;
        c = d;
        costC = costD;
        d = a + GOLDEN_RATIO * (b - a);
        costD = costAt(d);
      }
    }
    return costC <= costD ? c : d;
  }

  /**
   * Walks outwards from the optimum in steps of {@code stride} until the cost leaves the budget,
   * then bisects the last acceptable and the first unacceptable point down to the precision.
   *
   * @param centre the optimum's offset (s)
   * @param stride the sweep step, negative to walk backwards (s)
   * @param bound the offset the walk stops at — 0 backwards, the span forwards (s)
   * @return the offset of the edge (s)
   */
  private double edge(double centre, double stride, double bound) {
    double inside = centre;
    double outside = centre + stride;
    while (Math.signum(stride) * (bound - outside) > 0.0 && withinBudget(outside)) {
      inside = outside;
      outside += stride;
    }
    if (!withinBudget(outside)) {
      for (int i = 0; i < MAX_EDGE_STEPS
          && Math.abs(outside - inside) > search.precisionSeconds(); i++) {
        double middle = 0.5 * (inside + outside);
        if (withinBudget(middle)) {
          inside = middle;
        } else {
          outside = middle;
        }
      }
      return inside;
    }
    // The slot is still open at the end of the searched range: report the bound rather than
    // pretending the window closes there, and let the caller widen the span if it cares.
    return bound;
  }

  private boolean withinBudget(double offset) {
    return costAt(offset) <= search.maxDeltaV();
  }

  private double costAt(double offset) {
    return evaluate(offset).deltaV();
  }

  /**
   * One evaluation, memoised at the search's precision. Golden section and the two bisections
   * revisit the same instants; without the cache a window costs about a third more evaluations,
   * which matters as soon as an evaluation propagates.
   */
  private LaunchWindowCandidate evaluate(double offset) {
    long key = Math.round(offset / search.precisionSeconds());
    return evaluations.computeIfAbsent(
        key, k -> problem.evaluate(search.start().shiftedBy(k * search.precisionSeconds())));
  }
}
```


---

## 5. Deux pièges que ce découpage désamorce, et un troisième à connaître

**a. Le pas de balayage n'appartient pas au chercheur.** C'est l'erreur classique : un `LaunchWindowSolver` qui impose « un jour » trouve les créneaux lunaires et rate *tous* les créneaux ISS, qui reviennent deux fois par jour et durent quelques minutes — et il ne rate pas bruyamment, il renvoie une liste vide. D'où `LaunchWindowProblem.coarseStep()`, et le commentaire qui dit que c'est un théorème d'échantillonnage.

**b. L'état initial n'est pas un paramètre du solveur.** La signature actuelle `solve(SpacecraftState initialState, …)` fige un état alors qu'il *dépend de la date candidate* : l'orbite de parking translunaire est construite autour de la position de la Lune à l'arrivée, un état d'ascension est le site de tir tourné à cette époque-là. Le problème le dérive de l'époque qu'on lui passe ; rien d'autre ne peut le faire correctement.

**c. Ce qui reste inconnu, et qui se mesurera.** L'unimodalité dans l'encadrement. La section dorée ne diverge pas, mais sur un encadrement qui contient deux minima elle en rend un arbitrairement. Le pas du problème est ce qui l'évite, et c'est exactement ce qu'un test doit vérifier (§7).

---

## 6. Le premier problème concret — et une remarque qui compte pour la Lune

Voici l'implémentation translunaire, livrée sous le nom
`simulation/mission/window/problem/TranslunarInjectionPlanWindowProblem` — le paquet `problem`
existe pour que le paquet `window` reste l'ossature et n'accumule pas les cibles.

Elle est construite **sur la mission**, et pas sur une masse. C'est ce qui lui permet de porter les
deux étages sans dupliquer une seule règle : le criblage lit la masse du véhicule, la confirmation
appelle l'étage d'injection de la mission elle-même.

```java
public class TranslunarInjectionPlanWindowProblem implements LaunchWindowProblem {

  /** Sweep step: six hours resolves a monthly criterion by a factor of a hundred and twenty. */
  private static final Duration COARSE_STEP = Duration.ofHours(6);

  private final LunarTransferMission mission;

  @Override
  public LaunchWindowCandidate evaluate(AbsoluteDate epoch) {
    try {
      return LaunchWindowCandidate.of(
          epoch,
          TranslunarInjectionPlan.keplerianInjectionDeltaV(epoch, mission.getVehicle().getMass()));
    } catch (OrbitlabException refused) {
      // The declination guard, and any other closed-form refusal: data, not a failure.
      return LaunchWindowCandidate.refused(epoch, refused.getMessage());
    }
  }

  @Override
  public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
    AbsoluteDate epoch = candidate.epoch();
    try {
      SpacecraftState parking = mission.getInitialState(epoch);
      SpacecraftState injected = mission.getStages().getFirst().enter(parking, mission);
      double deltaV = injected.getPVCoordinates().getVelocity()
          .subtract(parking.getPVCoordinates().getVelocity()).getNorm();
      return LaunchWindowCandidate.of(epoch, deltaV);
    } catch (OrbitlabException refused) {
      return LaunchWindowCandidate.refused(epoch, refused.getMessage());
    }
  }
}
```

**Pourquoi la confirmation appelle l'étage de la mission et non `TranslunarInjectionPlan.solve`.**
Ce qui refuse une époque ici n'est pas le seed, qui converge toujours, mais le plancher de périlune
*volé* — 135 km mesurés à une époque du mois contre une cible à 100 km — et le plancher de dépletion
de l'étage actif. Les deux verdicts vivent dans `TranslunarInjectionStage.enter` : l'appeler est ce
qui garantit qu'une époque offerte est une époque que la mission sait voler. Une réimplémentation
dans le problème pourrait diverger, et la divergence se paierait en mission `FAILED` sur le thread
d'optimisation *après* avoir été programmée. Le coût confirmé est lu comme le saut de vitesse à
travers l'étage, parce que `enter` rend un état et pas un plan — l'impulsion étant appliquée à date
et position fixes, c'est le même nombre.

Elle réclame **une seule couture publique** dans `TranslunarInjectionPlan`, plutôt que d'ouvrir `keplerianSeedVelocity` et `boundaryConditions` qui sont package-private à dessein :

```java
// ... existing code ...
  /**
   * Applies this plan's impulse to its parking state: the velocity gains {@link #deltaV} and the mass
   * drops by Tsiolkovsky, which for an impulse is exact and not an approximation.
   *
   * @param exhaustVelocity the effective exhaust velocity {@code Isp·g0} (m/s)
   * @return the post-injection state
   */
  public SpacecraftState applyTo(SpacecraftState state, double exhaustVelocity) {
    return applyImpulse(state, deltaV, exhaustVelocity);
  }

  /**
   * What the injection costs at an epoch, on the Lambert seed alone — <b>closed form, no
   * propagation, microseconds</b>.
   *
   * <p>The seam MIS-2 screens epochs through ({@code TranslunarWindowProblem}). It aims at the
   * Moon's centre rather than at an offset aim point, which is the right call for a <em>ranking</em>
   * criterion: the offset is worth a handful of m/s against the three-odd km/s of the injection, and
   * resolving it is what costs the thirty propagations {@link #solve} spends. The verdict on a
   * screened epoch stays with {@code solve}.
   *
   * @param injectionDate the date the impulse is applied
   * @param mass the spacecraft mass at injection (kg)
   * @return the magnitude of the injection impulse (m/s)
   * @throws OrbitlabException when the geometry at that epoch admits no transfer plane
   */
  public static double keplerianInjectionDeltaV(AbsoluteDate injectionDate, double mass) {
    SpacecraftState parking = parkingState(injectionDate, mass);
    AbsoluteDate arrival = injectionDate.shiftedBy(TIME_OF_FLIGHT_SECONDS);
    Vector3D seed =
        keplerianSeedVelocity(
            parking, boundaryConditions(parking, arrival, moonPosition(arrival)));
    return seed.subtract(parking.getPVCoordinates().getVelocity()).getNorm();
  }

  /**
   * An offset bracket: {@code low} undershoots the target perilune, {@code high} overshoots it.
// ... existing code ...
```


`LunarTransferMission.firstFlyableDate` disparaît avec cette confirmation : elle faisait la même
chose en marche forcée, un jour après l'autre, en s'arrêtant à la première date qui passe au lieu de
la meilleure du mois.

---

## 7. Ce que je testerais en premier

Trois propriétés, toutes sans propagation, sur un problème **synthétique** (une sinusoïde dont on connaît les minima) plutôt que sur la Lune :

1. **Le minimum trouvé est le vrai**, à la précision demandée — c'est le test de la section dorée.
2. **Les bords encadrent le seuil** : `cost(opening) ≤ maxDeltaV < cost(opening − precision)`.
3. **Un pas trop grossier rate les créneaux**, et le test l'assère — pour que la raison d'être de `coarseStep()` soit écrite quelque part d'exécutable et non seulement dans un Javadoc.

Puis un seul test sur la Lune : *le Δv de la meilleure date d'un mois est inférieur à celui d'une date prise au hasard*, ce qui vérifie le câblage sans figer un chiffre d'éphéméride.

**Ce que je ne ferais pas maintenant** : la timeline du wizard. Elle lit `List<LaunchWindow>` et rien d'autre ; tant que le solveur ne rend pas des créneaux mesurés sur un vrai plan de parking fixe, elle afficherait une géométrie qui n'a pas encore de sens opérationnel.

---

## 8. Ce qui a été livré, et ce que la mesure dit

Le paquet `window` (ossature) et `window/problem` (cibles) sont en place, la démonstration lunaire
programme sa date par le solveur, et la confirmation vole l'étage d'injection de la mission. Les
chiffres qui suivent viennent de `TranslunarInjectionPlanWindowProblemTest` sur janvier 2026, plage
de 30 jours, pas de 6 h, précision 10 min, budget 4 000 m/s, marge 5 m/s.

| Mesure | Valeur |
|---|---|
| Évaluations fermées du criblage | **199** |
| Confirmations propagées | **1** — deux minima encadrés, un seul assez bon marché pour être confirmé |
| Durée du test complet | **14,7 s**, init Orekit comprise |
| Écart criblage → confirmé | **6 m/s** (3 183 → 3 177) |
| Refus sur le mois | **aucun** — les 30° d'inclinaison de parking couvrent la déclinaison lunaire |
| Créneau rendu | **12,9 j sur 30**, de T+2,8 j à T+15,7 j, seuil effectif 3 187,8 m/s |

**Trois enseignements, et le deuxième corrige le document.**

*Le deux-étages tient.* Six m/s d'écart sur 3 180, soit 0,2 % : le seed de Lambert visant le centre
lunaire classe correctement les époques tout en coûtant quatre ordres de grandeur de moins que le
vol. Il *sur*-estime, de surcroît, donc la confirmation ne peut pas faire sortir un créneau du
budget par surprise.

*Le critère translunaire n'a pas de fenêtre, et le §6 le disait trop faiblement.* Le coût criblé va
de **3 182,8 à 3 196,9 m/s sur le mois, soit 14 m/s d'amplitude** — pas les « quelques dizaines »
annoncées. Sur 3 180 m/s, aucun budget d'acceptation qu'une mission écrirait vraiment ne découpe un
intervalle là-dedans. Ce que ce problème livre est donc la **meilleure date du mois** et le
**verdict de faisabilité** ; le créneau de 12,9 jours ci-dessus n'existe que parce qu'une marge de
5 m/s a été demandée, et sa largeur dit la platitude du critère plus qu'elle ne dit une contrainte
opérationnelle. Un vrai créneau demande un critère qui a du relief — l'alignement de plan d'un site
de tir fixe, la deuxième implémentation.

*Le seuil doit se dire relativement.* `LaunchWindowSearch.margin` est le combien-de-plus-que-le-
meilleur, en m/s ; le seuil effectif est le plus bas des deux, `min(maxDeltaV, ancre + marge)`. Deux
décisions de mise en œuvre valent d'être écrites :

- **L'ancre est un coût *criblé*, pas confirmé.** C'est la marche de bord qui consomme le seuil, et
  elle ne lit que des coûts criblés : ancrer sur le confirmé décalerait les deux de la surcharge de
  confirmation — 6 à 8 m/s ici, contre une marge que l'appelant met à 5.
- **Les minima sont confirmés du moins cher au plus cher, et le premier accepté fixe l'ancre.** Les
  suivants au-delà du seuil ne sont jamais confirmés : c'est là qu'est l'économie (2 confirmations
  → 1, 378 évaluations → 199 sur la démonstration). Et l'ancre étant le premier minimum *que le
  modèle complet accepte*, un optimum refusé n'entraîne pas toute la recherche avec lui — sans quoi
  un mois dont la meilleure date est infaisable serait rendu vide.

Les créneaux qui se recouvrent sont fusionnés, l'optimum le moins cher étant conservé : deux minima
dont les bords se rejoignent sont **un** intervalle sur lequel le coût ne sort jamais du budget,
donc une seule opportunité. Sans cela `maxWindows` dépensait son quota sur le même intervalle rendu
deux fois.

**Le solveur ne porte plus rien.** La grille, le cache d'évaluations et les critères vivent dans une
classe imbriquée `Run`, une par appel ; `LaunchWindowSolver` ne garde que son problème. Une instance
est donc réutilisable et réentrante, et deux threads peuvent la solliciter en même temps — ce qui
compte parce que ces recherches partent d'où une mission est programmée : le thread de rendu par le
wizard, le thread d'init pour la démonstration lunaire, le thread d'optimisation le jour où un
créneau sera recalculé au lancement. Le défaut évité n'aurait pas levé d'exception : le second appel
remettait le cache à zéro sous le premier, qui rendait alors un créneau mesuré sur deux recherches
différentes. **Ce qui n'est pas rendu thread-safe pour autant, c'est le problème** — un critère
fermé est immuable et partageable, un `confirm` qui vole une étape de mission ne l'est pas, et la
règle appartient au problème.

---

## 9. Le deuxième problème : la fenêtre terrestre

`EarthLaunchWindowProblem` est la cible que l'interface attendait — la première dont le critère a
un créneau et pas seulement un classement. L'inclinaison de la mission *étant* l'inclinaison cible,
le seul désaccord entre le plan que le pas de tir atteint à l'instant *t* et le plan visé est le
**RAAN**, qui balaie un tour complet par jour sidéral. Le coût est le changement de plan impulsionnel
`2·v·sin(θ/2)` de l'angle résiduel entre les deux normales.

Mesures (Kourou, 51,6°, orbite à 400 km, `EarthLaunchWindowProblemTest`) :

| Mesure | Valeur | Prédiction indépendante |
|---|---|---|
| Pente du coût près de l'alignement | 0,438 m/s par seconde | `v·sin(i)·ω⊕` |
| Créneau pour 50 m/s de marge | **3 min 52 s** | `2·marge/pente` = 3 min 48 s |
| Récurrence | **1 jour sidéral** (86 164 s à 60 s près) | — |
| Coût à un demi-jour sidéral | 12 020 m/s | `2·v·sin(i)` = 12 020 m/s |
| Plancher à l'optimum | 1,1 m/s depuis Kourou, 35,9 m/s depuis 45° | écart géodésique/géocentrique |
| Coût d'une recherche d'un jour | 85 évaluations fermées | — |

**Trois choses que ces chiffres tranchent.**

*Le §5.a se trompait deux fois.* L'alignement revient **une** fois par jour sidéral et non deux :
le site traverse bien le plan cible deux fois par jour, mais les deux passages sont servis par les
deux azimuts, et `NodeBranch` est fixé par la mission. Et le pas de balayage n'a pas à être en
minutes : ce qui est large de quelques minutes est le *créneau*, pas le *minimum*. La fonction est
en cosinus, lisse, un minimum par jour sidéral — un pas d'une heure l'échantillonne vingt-quatre
fois par période, et ce sont la section dorée et la bissection de bord qui descendent à la seconde.

*Le vrai piège était sur l'autre axe.* `over(...)` dérivait `precision = pas/10`, soit six minutes
pour un pas d'une heure — plus grossier que le créneau lui-même, et silencieusement. D'où
`LaunchWindowProblem.refinementPrecision()`, exactement l'argument qui a mis `coarseStep()` sur le
problème, appliqué à la résolution : le problème connaît ses deux échelles, et elles diffèrent ici
de quatre ordres de grandeur.

*Le plancher rend la marge relative indispensable.* L'azimut vient de la latitude **géodésique** par
trigonométrie sphérique (`LaunchPlane.launchAzimuth`), tandis que le plan est élevé sur la position
inertielle réelle du pas de tir, de latitude géocentrique plus basse. Le plan atteint est donc
incliné d'une fraction de degré à côté de la cible, et aucun instant du jour ne referme cet écart :
1,1 m/s depuis Kourou, 35,9 m/s depuis 45°. C'est un décalage **constant dans le temps**, qui ne
déplace pas le minimum — un seuil absolu devrait donc être informé de ce plancher, et réinformé pour
chaque pas de tir. Le seuil relatif du §8 l'absorbe sans rien savoir.

Ce problème ne refuse rien et ne confirme rien : une inclinaison inatteignable est écartée à la
construction, et la forme fermée *est* la vérité pour un alignement de plan.

---

## 10. Le câblage : ce que devient la date de lancement du wizard

**Une correction d'abord.** Le §8 disait que la ligne à remplacer était le
`entry.getScheduledDate().orElseGet(clock::now)` de `MissionOrchestratorAppState:194`. C'est faux :
le wizard **a déjà** un champ LAUNCH DATE, et `MissionWizardAppState` appelle déjà
`setScheduledDate` avec ce qu'il contient, à la création comme à l'édition. Le `orElseGet` n'est
donc que le repli des entrées sans date — la démonstration lunaire, un appel programmatique. La
vraie couture est dans `MissionWizardAppState`, et elle ne remplace pas la date saisie : **elle la
lit comme un plancher**.

Le trajet complet :

| Étage | Ce qui est ajouté |
|---|---|
| `FormField.TARGET_RAAN` | la clé, en degrés ; son absence est signifiante |
| `EarthOrbitDynamicParameters` | le champ, sur la ligne de l'inclinaison |
| `MissionFactory` | `targetRaanOrNull(values)` |
| `MissionSpec.EarthOrbit` | la composante `Double targetRaan` + `hasTargetRaan()` |
| `WizardPrefill` | le retour du champ à la réouverture |
| `EarthLaunchWindowPlanner` | spec + date plancher → le créneau à voler |
| `MissionWizardAppState` | `scheduledDateFor(spec, date saisie)` |

**Trois décisions valent d'être écrites.**

*Le nul est le cas courant.* `targetRaan` est nullable et se lit par `hasTargetRaan()` — la règle
« `Optional` est un type de retour, jamais un champ » du `CLAUDE.md`, et le précédent est `siteName`
juste à côté. Une inclinaison seule est atteinte à chaque instant du jour : seule une mission qui
rejoint un plan **existant** a un créneau à attendre. Publier une valeur par défaut ferait patienter
toutes les autres pour rien. Un constructeur à onze arguments conserve la forme d'avant MIS-2, si
bien qu'aucun appelant existant ne bouge.

*Un nœud illisible est refusé, pas dégradé.* L'horizon peut retomber sur son défaut dérivé parce
qu'une durée est une préférence ; un nœud qu'on n'a pas su lire est une **intention**, et le laisser
tomber ferait partir la mission à la date saisie comme si rien n'avait été demandé. Le champ passe
en rouge et l'étape est refusée.

*Le plus tôt, pas le moins cher.* `LaunchWindowSolver` ordonne par coût, ce qui est la bonne réponse
générale ; ici les alignements de deux jours consécutifs diffèrent de moins d'un m/s — c'est la même
opportunité, répétée — donc trier par coût repousserait le tir d'un jour pour rien. Le planificateur
prend le plus tôt.

**Un comportement de bord qui tombe juste tout seul**, et qui valide après coup le seuil relatif du
§8 : si la date demandée tombe *dans* un créneau, la borne de la plage de recherche est un minimum
de la grille et le solveur la retient — le tir a lieu tout de suite. Si elle tombe dix minutes après
l'ouverture, ce même minimum de bord coûte ~260 m/s, sort de la marge ancrée sur le vrai alignement
du lendemain, et le solveur le retire : la réponse est le créneau du lendemain. Aucune de ces deux
branches n'est écrite dans le planificateur ; elles découlent du seuil relatif. Les deux sont
testées.

**Ce que le champ RAAN veut dire — et ce qu'il ne veut pas dire.** Un RAAN saisi est un **nombre, pas
une orbite** : le plan qu'il décrit est fixe dans le repère d'inclinaison et y reste. Un plan qui est
réellement en orbite, lui, précesse — la formule séculaire J2 `dΩ/dt = −3/2·J2·(Re/p)²·n·cos i`
donne **5,00 °/jour** pour une orbite à 400 km et 51,6°, ce qui confirme le « ~5°/jour » de la fiche
MIS-2. Sur les 26 h que balaie `EarthLaunchWindowPlanner`, cela fait 5,4° de nœud, donc 4,25° entre
les plans, donc **~570 m/s** (dérivé, non mesuré) — plus de dix fois la marge de 50 m/s qui définit
le créneau.

Autrement dit : une mission qui saisit le RAAN de l'ISS à sa création et part vingt heures plus tard
rejoint **exactement** le plan saisi, et rate l'ISS. Le champ veut donc dire « le plan tel qu'il sera
au décollage », et tant qu'il n'y a pas de cible réelle c'est à l'utilisateur de le saisir ainsi.
Suivre une cible qui précesse est une **seconde implémentation** de l'interface, dont la normale est
fonction de l'époque au lieu d'être une constante : quelques lignes, aucune structure à reprendre.

---

**Ce qui reste ouvert** :

1. **La timeline du wizard.** Elle lit `List<LaunchWindow>` et rien d'autre, et elle a enfin quelque
   chose à montrer : un créneau de quatre minutes qui revient chaque jour sidéral. Le planificateur
   ne rend aujourd'hui qu'une date au champ LAUNCH DATE ; l'intervalle est calculé puis jeté. Le
   point à trancher avant d'écrire quoi que ce soit est la place : l'étape des paramètres est déjà
   à saturation verticale (cf. §10), donc la timeline ne peut pas y être une ligne de plus.
2. **Une cible TLE**, qui fournirait le RAAN au lieu de le faire saisir. **Ce n'est pas un reste de
   MIS-2**, contrairement à ce que ce document a d'abord dit : le graphe de dépendances de la
   roadmap fait entrer la « source éphéméride TLE » dans **MIS-6**, et la mention de la précession
   J2 dans la fiche MIS-2 décrit ce que le critère devra encaisser le jour où il y aura une cible,
   pas un livrable autonome. Sans mission de rendez-vous, un chargeur de TLE n'alimente rien — et
   la couture qui l'accueillera est déjà en place (paragraphe ci-dessus).
