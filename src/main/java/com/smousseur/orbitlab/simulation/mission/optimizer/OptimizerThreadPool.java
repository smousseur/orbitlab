package com.smousseur.orbitlab.simulation.mission.optimizer;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import org.hipparchus.util.FastMath;

/**
 * The single work-stealing pool a CMA-ES generation's offspring are evaluated on (OPT-1 / L1). A
 * holder singleton, on the pattern of {@code OrekitService}: the mission orchestrator computes one
 * mission at a time, so a shared pool has no cross-computation contention, and it replaces the
 * {@code Executors.newFixedThreadPool} the exploration phase used to create per pass (and never
 * made daemon).
 *
 * <p><b>Sized {@code availableProcessors − 1}</b> — one core left for the JME render thread, the
 * size the exploration pool already used. <b>Daemon and named</b>, so it never holds the process
 * open at shutdown, which the old non-daemon pools did during an exploration
 * ({@code REL-21}).
 *
 * <p>A {@code ForkJoinPool} rather than a fixed pool, and from L1a on: L1b nests exploration runs
 * over their own offspring, and only work-stealing keeps a controller thread that is waiting on a
 * result from starving a worker.
 */
public final class OptimizerThreadPool {

  private OptimizerThreadPool() {}

  private static final class Holder {
    static final ForkJoinPool INSTANCE =
        new ForkJoinPool(
            FastMath.max(1, Runtime.getRuntime().availableProcessors() - 1),
            OptimizerThreadPool::newDaemonWorker,
            null,
            false);
  }

  private static ForkJoinWorkerThread newDaemonWorker(ForkJoinPool pool) {
    ForkJoinWorkerThread thread =
        ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
    thread.setName("cmaes-eval-" + thread.getPoolIndex());
    thread.setDaemon(true);
    return thread;
  }

  /**
   * The shared evaluation pool.
   *
   * @return the pool
   */
  public static ForkJoinPool get() {
    return Holder.INSTANCE;
  }
}
