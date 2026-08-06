package org.opentripplanner.raptor.extensions.alternativepaths;

import java.util.List;
import org.opentripplanner.raptor.spi.RaptorCostCalculator;
import org.opentripplanner.raptor.spi.RaptorTransferConstraint;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The responsibility for the cost calculator is to calculate the default multi-criteria cost.
 * <p/>
 * This class is immutable and thread safe.
 */
public final class AlternativePathsCostCalculator<T extends RaptorTripSchedule>
  implements RaptorCostCalculator<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AlternativePathsCostCalculator.class);
  private final List<int[]> activeTripPatternsPerStop;
  private final int maxTripPatternCount;

  /**
   * Cost unit: SECONDS - The unit for all input parameters are in the OTP TRANSIT model cost unit
   * (in Raptor the unit for cost is centi-seconds).
   */
  public AlternativePathsCostCalculator(List<int[]> activeTripPatternsPerStop) {
    this.activeTripPatternsPerStop = activeTripPatternsPerStop;
    int max = 0;
    for (int[] arr : activeTripPatternsPerStop) {
      if (arr.length > max) {
        max = arr.length;
      }
    }
    this.maxTripPatternCount = max;
    LOG.info("Created an AP Cost Calculator");
    LOG.info("Max trip pattern count: {}", maxTripPatternCount);
  }

  @Override
  public int boardingCost(
    boolean firstBoarding,
    int prevArrivalTime,
    int boardStopIndex,
    int boardTime,
    T trip,
    RaptorTransferConstraint transferConstraints
  ) {
    if (transferConstraints.isRegularTransfer()) {
      return boardingCostRegularTransfer(firstBoarding, prevArrivalTime, boardStopIndex, boardTime);
    } else {
      return boardingCostConstrainedTransfer(
        prevArrivalTime,
        boardStopIndex,
        boardTime,
        0,
        firstBoarding,
        transferConstraints
      );
    }
  }

  @Override
  public int transitCost(int transitDuration, T tripScheduledBoarded) {
    return 0;
  }

  @Override
  public int transitArrivalCost(
    int boardCost,
    int alightSlack,
    int transitDuration,
    T trip,
    int toStopIndex
  ) {
    return activeTripPatternsPerStop.get(toStopIndex).length * 100;
  }

  @Override
  public int waitCost(int waitTimeInSeconds) {
    return 0;
  }

  @Override
  public int calculateRemainingMinCost(
    int minTravelDuration,
    int minNumTransfers,
    int fromStopIndex
  ) {
    if (minNumTransfers > -1) {
      // @TODO this isn't actually a lower bound, just a kinda educated guess
      return maxTripPatternCount * minNumTransfers;
    } else {
      // Remove cost that was added during alighting similar as we do in the costEgress() method
      // @TODO What does minNumTransfers <= -1 mean???
      return 0;
    }
  }

  @Override
  public int costEgress(int stopIndex, boolean egressHasRides) {
    return 0;
  }

  /** This is public for test purposes only */
  public int boardingCostRegularTransfer(
    boolean firstBoarding,
    int prevArrivalTime,
    int boardStop,
    int boardTime
  ) {
    return 0;
  }

  /* private methods */

  private int boardingCostConstrainedTransfer(
    int prevArrivalTime,
    int boardStopIndex,
    int boardTime,
    int transitReluctanceIndex,
    boolean firstBoarding,
    RaptorTransferConstraint txConstraints
  ) {
    return boardingCostRegularTransfer(firstBoarding, prevArrivalTime, boardStopIndex, boardTime);
  }
}
