package org.opentripplanner.raptor.extensions.alternativepaths;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.view.TransitArrival;
import org.opentripplanner.raptor.rangeraptor.internalapi.RaptorRouterResult;
import org.opentripplanner.raptor.rangeraptor.standard.StdRaptorRouterResult;
import org.opentripplanner.raptor.rangeraptor.standard.StdTransferEarlyPruning;
import org.opentripplanner.raptor.rangeraptor.standard.StdWorkerState;
import org.opentripplanner.raptor.rangeraptor.standard.besttimes.BestTimes;
import org.opentripplanner.raptor.rangeraptor.standard.internalapi.ArrivedAtDestinationCheck;
import org.opentripplanner.raptor.rangeraptor.standard.internalapi.BestNumberOfTransfers;
import org.opentripplanner.raptor.rangeraptor.standard.internalapi.StopArrivalsState;
import org.opentripplanner.raptor.rangeraptor.transit.RaptorTransitCalculator;
import org.opentripplanner.raptor.spi.IntIterator;
import org.opentripplanner.raptor.spi.RaptorTransfer;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

/**
 * Tracks the state of a standard Range Raptor search, specifically the best arrival times at each
 * transit stop at the end of a particular round, along with associated data to reconstruct paths
 * etc.
 * <p>
 * This is grouped into a separate class (rather than just having the fields in the raptor worker
 * class) because we want to separate the logic of maintaining stop arrival state and performing the
 * steps of the algorithm. This also makes it possible to have more than one state implementation,
 * which has been used in the past to test different memory optimizations.
 * <p>
 * Note that this represents the entire state of the Range Raptor search for all rounds. The {@code
 * stopArrivalsState} implementation can be swapped to achieve different results.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
public final class PreprocessingRangeRaptorWorkerState<T extends RaptorTripSchedule>
  implements StdWorkerState<T> {

  /**
   * The best times to reach each stop, whether via a transfer or via transit directly. This is the
   * bare minimum to execute the algorithm.
   */
  private final BestTimes bestTimes;

  /**
   * Track the stop arrivals to be able to return some kind of result. Depending on the desired
   * result, different implementation is injected.
   */
  private final StopArrivalsState<T> stopArrivalsState;

  /**
   * Used to extract the best number-of-transfers as part of the result.
   */
  private final BestNumberOfTransfers bestNumberOfTransfers;

  /**
   * The list of egress stops can be used to terminate the search when the stops are reached.
   */
  private final ArrivedAtDestinationCheck arrivedAtDestinationCheck;

  /**
   * The calculator is used to calculate transit-related times/events like access arrival time.
   */
  private final RaptorTransitCalculator<T> calculator;

  @Nullable
  // null if early pruning is not used
  private final StdTransferEarlyPruning<T> earlyPruning;

  private final HashMap<Integer, HashSet<Integer>> routesByStop;
  private final HashMap<Integer, HashSet<Integer>> stopsReachingStop;
  private final List<Integer> egressStops;
  /**
   * create a BestTimes Range Raptor State for the given context.
   */
  public PreprocessingRangeRaptorWorkerState(
    RaptorTransitCalculator<T> calculator,
    BestTimes bestTimes,
    StopArrivalsState<T> stopArrivalsState,
    BestNumberOfTransfers bestNumberOfTransfers,
    ArrivedAtDestinationCheck arrivedAtDestinationCheck,
    List<Integer> egressStops,
    @Nullable StdTransferEarlyPruning<T> earlyPruning
  ) {
    this.calculator = calculator;
    this.bestTimes = bestTimes;
    this.stopArrivalsState = stopArrivalsState;
    this.bestNumberOfTransfers = bestNumberOfTransfers;
    this.arrivedAtDestinationCheck = arrivedAtDestinationCheck;
    this.earlyPruning = earlyPruning;

    this.routesByStop = new HashMap<>();
    this.stopsReachingStop = new HashMap<>();
    this.egressStops = egressStops;
  }

  public HashSet<Integer> relevantRoutes() {
    HashSet<Integer> stops = new HashSet<>();
    Deque<Integer> stack = new ArrayDeque<>();

    for (int egress : egressStops) {
      if (stops.add(egress)) {
        stack.push(egress);
      }
    }

    while (!stack.isEmpty()) {
      int stop = stack.pop();
      if (stopsReachingStop.containsKey(stop)) {
        for (int onwardStop : stopsReachingStop.get(stop)) {
          if (stops.add(onwardStop)) {
            stack.push(onwardStop);
          }
        }
      }
    }

    HashSet<Integer> routes = new HashSet<>();
    for (int stop : stops) {
      if (routesByStop.containsKey(stop)) {
        routes.addAll(routesByStop.get(stop));
      //} else {
        // System.out.println("Unknown stop: " + stop);
      }
    }
    return routes;
  }

  @Override
  public boolean isNewRoundAvailable() {
    return bestTimes.isCurrentRoundUpdated();
  }

  @Override
  public IntIterator stopsTouchedPreviousRound() {
    return bestTimes.stopsReachedLastRound();
  }

  @Override
  public IntIterator stopsTouchedByTransitCurrentRound() {
    return bestTimes.reachedByTransitCurrentRound();
  }

  @Override
  public boolean isDestinationReachedInCurrentRound() {
    return arrivedAtDestinationCheck.arrivedAtDestinationCurrentRound();
  }

  @Override
  public void setAccessToStop(RaptorAccessEgress accessPath, int departureTime) {
    final int durationInSeconds = accessPath.durationInSeconds();
    final int stop = accessPath.stop();

    // The time of arrival at the given stop for the current iteration
    // (or departure time at the last stop if we search backwards).
    int arrivalTime = calculator.plusDuration(departureTime, durationInSeconds);

    if (exceedsTimeLimit(arrivalTime)) {
      return;
    }

    boolean arrivedOnBoard =
      accessPath.arrivedOnBoard() && newBestTransitArrivalTime(stop, arrivalTime);
    boolean bestTime = newOverallBestTime(stop, arrivalTime);

    if (arrivedOnBoard || bestTime) {
      stopArrivalsState.setAccessTime(arrivalTime, accessPath, bestTime);
    } else {
      stopArrivalsState.rejectAccessTime(arrivalTime, accessPath);
    }
  }

  @Override
  public void transferToStops(int fromStop, Iterator<? extends RaptorTransfer> transfers) {
    int arrivalTimeTransit = bestTimes.transitArrivalTime(fromStop);
    while (transfers.hasNext()) {
      if (transferToStop(arrivalTimeTransit, fromStop, transfers.next())) {
        break;
      }
    }
  }

  @Override
  public boolean isStopReachedInPreviousRound(int stop) {
    return bestTimes.isStopReachedLastRound(stop);
  }

  /**
   * Return the "best time" found in the previous round. This is used to calculate the board/alight
   * time in the next round.
   * <p/>
   * PLEASE OVERRIDE!
   * <p/>
   * The implementation here is not correct - please override if you plan to use any result paths or
   * "rounds" as "number of transfers". The implementation is OK if the only thing you care about is
   * the "arrival time".
   */
  @Override
  public int bestTimePreviousRound(int stop) {
    // This is a simplification, *bestTimes* might get updated during the current round;
    // Hence leading to a new boarding from the same stop in the same round.
    // If we do not count rounds or track paths, this is OK. But be sure to override this
    // method with the best time from the previous round if you care about number of
    // transfers and results paths.

    return stopArrivalsState.bestTimePreviousRound(stop);
  }

  /**
   * Set the time at a transit stop iff it is optimal. This sets both the bestTime and the
   * transitTime.
   */
  @Override
  public void transitToStop(int stop, int arrivalTime, int boardStopPosition, T trip) {
    if (exceedsTimeLimit(arrivalTime)) {
      return;
    }

    int fromStop = trip.pattern().stopIndex(boardStopPosition);
    stopsReachingStop.computeIfAbsent(stop, _ -> new HashSet<>()).add(fromStop);
    routesByStop.computeIfAbsent(stop, _ -> new HashSet<>()).add(trip.pattern().patternIndex());

    if (newBestTransitArrivalTime(stop, arrivalTime)) {
      // transitTimes upper bounds bestTimes
      final boolean newOverallBestTime = newOverallBestTime(stop, arrivalTime);
      stopArrivalsState.setNewBestTransitTime(
        stop,
        arrivalTime,
        boardStopPosition,
        trip,
        newOverallBestTime
      );
    } else {
      stopArrivalsState.rejectNewBestTransitTime(stop, arrivalTime, boardStopPosition, trip);
    }
  }

  @Override
  public TransitArrival<T> previousTransit(int boardStopIndex) {
    return stopArrivalsState.previousTransit(boardStopIndex);
  }

  /**
   * @return {@code true} if the transfer loop should terminate early. The transfers are sorted
   *     by duration, so if a transfer exceeds the time limit or the early pruning bound, so will
   *     all later transfers also exceed the time limit or the early pruning bound.
   */
  private boolean transferToStop(int arrivalTimeTransit, int fromStop, RaptorTransfer transfer) {
    final int arrivalTime = calculator.plusDuration(
      arrivalTimeTransit,
      transfer.durationInSeconds()
    );

    if (exceedsTimeLimit(arrivalTime)) {
      return true;
    }
    if (earlyPruning != null && earlyPruning.exceedsBound(arrivalTime)) {
      return true;
    }

    final int toStop = transfer.stop();
    stopsReachingStop.computeIfAbsent(toStop, _ -> new HashSet<>()).add(fromStop);

    if (newOverallBestTime(toStop, arrivalTime)) {
      stopArrivalsState.setNewBestTransferTime(fromStop, arrivalTime, transfer);
    } else {
      stopArrivalsState.rejectNewBestTransferTime(fromStop, arrivalTime, transfer);
    }
    return false;
  }

  @Override
  public RaptorRouterResult<T> results() {
    return new StdRaptorRouterResult<>(
      bestTimes,
      stopArrivalsState::extractPaths,
      bestNumberOfTransfers::extractBestNumberOfTransfers
    );
  }

  /* private methods */

  private boolean newOverallBestTime(int stop, int alightTime) {
    boolean updated = bestTimes.updateNewBestTime(stop, alightTime);
    if (updated) {
      updateEarlyPruning(stop, alightTime);
    }
    return updated;
  }

  private boolean newBestTransitArrivalTime(int stop, int alightTime) {
    return bestTimes.updateBestTransitArrivalTime(stop, alightTime);
  }

  private boolean exceedsTimeLimit(int time) {
    return calculator.exceedsTimeLimit(time);
  }

  private void updateEarlyPruning(int stop, int alightTime) {
    if (earlyPruning != null) {
      earlyPruning.updateArrival(stop, alightTime);
    }
  }
}
