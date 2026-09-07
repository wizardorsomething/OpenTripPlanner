package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop;

import org.opentripplanner.raptor.spi.RaptorTripSchedule;

/**
 * Abstract super class for multi-criteria stop arrival.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
abstract sealed class AbstractStopArrivalC2Leximin<T extends RaptorTripSchedule>
  extends LeximinMcStopArrival<T>
  permits AccessStopArrivalC2Leximin, TransitStopArrivalC2Leximin, TransferStopArrivalC2Leximin {

  private final int c2;

  /**
   * Transit or transfer.
   *
   * @param previous             the previous arrival visited for the current trip
   * @param paretoRoundIncrement the increment to add to the paretoRound
   * @param stop                 stop index for this arrival
   * @param arrivalTime          the arrival time for this stop index
   * @param c1                   first criteria, the total accumulated generalized-cost-1 criteria
   * @param c2                   second criteria, the total accumulated generalized-cost-2 criteria
   */
  AbstractStopArrivalC2Leximin(
    LeximinMcStopArrival<T> previous,
    int round,
    int stop,
    int arrivalTime,
    int c1,
    int c2
  ) {
    super(previous, round, stop, arrivalTime, c1);
    this.c2 = c2;
  }

  /**
   * Initial state - first stop visited during the RAPTOR algorithm.
   */
  AbstractStopArrivalC2Leximin(
    int stop,
    int departureTime,
    int travelDuration,
    int round,
    int c1,
    int c2
  ) {
    super(stop, departureTime, travelDuration, c1, round);
    this.c2 = c2;
  }

  public final int c2() {
    return c2;
  }
}
