package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop;

import static org.opentripplanner.raptor.api.view.PathLegType.TRANSIT;

import org.opentripplanner.raptor.api.view.PathLegType;
import org.opentripplanner.raptor.api.view.TransitArrival;
import org.opentripplanner.raptor.api.view.TransitPathView;
import org.opentripplanner.raptor.spi.RaptorConstants;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

/**
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
final class TransitStopArrivalLeximax<T extends RaptorTripSchedule>
  extends LeximaxMcStopArrival<T>
  implements TransitPathView<T>, TransitArrival<T> {

  private final T trip;
  private final int boardStopPosition;

  TransitStopArrivalLeximax(
    LeximaxMcStopArrival<T> previousState,
    int stopIndex,
    int arrivalTime,
    int totalCost,
    int boardStopPosition,
    T trip
  ) {
    super(previousState, previousState.round() + 1, stopIndex, arrivalTime, totalCost);
    this.boardStopPosition = boardStopPosition;
    this.trip = trip;
  }

  @Override
  public int c2() {
    return RaptorConstants.NOT_SET;
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
  public LeximaxMcStopArrival<T> addSlackToArrivalTime(int slack) {
    return new TransitStopArrivalLeximax<>(
      (LeximaxMcStopArrival<T>) previous(),
      stop(),
      arrivalTime() + slack,
      c1(),
      boardStopPosition,
      trip
    );
  }
}
