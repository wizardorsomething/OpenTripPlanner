package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop;

import static org.opentripplanner.raptor.api.view.PathLegType.TRANSIT;

import org.opentripplanner.raptor.api.view.PathLegType;
import org.opentripplanner.raptor.api.view.TransitArrival;
import org.opentripplanner.raptor.api.view.TransitPathView;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

/**
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
final class TransitStopArrivalC2Leximin<T extends RaptorTripSchedule>
  extends AbstractStopArrivalC2Leximin<T>
  implements TransitPathView<T>, TransitArrival<T> {

  private final T trip;
  private final int boardStopPosition;

  TransitStopArrivalC2Leximin(
    LeximinMcStopArrival<T> previous,
    int stopIndex,
    int arrivalTime,
    int c1,
    int c2,
    int boardStopPosition,
    T trip
  ) {
    super(previous, previous.round() + 1, stopIndex, arrivalTime, c1, c2);
    this.boardStopPosition = boardStopPosition;
    this.trip = trip;
  }

  @Override
  public int boardStopPosition() {
    return boardStopPosition;
  }

  @Override
  public T trip() {
    return trip;
  }

  @Override
  public TransitArrival<T> mostRecentTransitArrival() {
    return this;
  }

  @Override
  public PathLegType arrivedBy() {
    return TRANSIT;
  }

  @Override
  public TransitPathView<T> transitPath() {
    return this;
  }

  @Override
  public boolean arrivedOnBoard() {
    return true;
  }

  @Override
  public LeximinMcStopArrival<T> addSlackToArrivalTime(int slack) {
    return new TransitStopArrivalC2Leximin<>(
      (LeximinMcStopArrival<T>) previous(),
      stop(),
      arrivalTime() + slack,
      c1(),
      c2(),
      boardStopPosition,
      trip
    );
  }
}
