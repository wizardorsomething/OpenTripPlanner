package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop;

import static org.opentripplanner.raptor.api.model.AbstractAccessEgressDecorator.accessEgressWithExtraSlack;
import static org.opentripplanner.raptor.api.view.PathLegType.ACCESS;

import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.view.AccessPathView;
import org.opentripplanner.raptor.api.view.PathLegType;
import org.opentripplanner.raptor.spi.RaptorConstants;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

/**
 * Represent a access stop arrival.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
final class AccessStopArrivalLeximin<T extends RaptorTripSchedule> extends LeximinMcStopArrival<T> {

  private final int departureTime;
  private final RaptorAccessEgress access;

  AccessStopArrivalLeximin(int departureTime, RaptorAccessEgress access) {
    super(
      access.stop(),
      departureTime,
      access.durationInSeconds(),
      access.c1(),
      access.numberOfRides()
    );
    this.departureTime = departureTime;
    this.access = access;
  }

  AccessStopArrivalLeximin(int departureTime, RaptorAccessEgress access, int cost) {
    super(
      access.stop(),
      departureTime,
      access.durationInSeconds(),
      cost,
      access.numberOfRides()
    );
    this.departureTime = departureTime;
    this.access = access;
  }

  @Override
  public int c2() {
    return RaptorConstants.NOT_SET;
  }

  @Override
  public PathLegType arrivedBy() {
    return ACCESS;
  }

  @Override
  public AccessPathView accessPath() {
    return () -> access;
  }

  @Override
  public boolean arrivedOnBoard() {
    return access.arrivedOnBoard();
  }

  @Override
  public LeximinMcStopArrival<T> timeShiftNewArrivalTime(int newRequestedArrivalTime) {
    int newArrivalTime = access.latestArrivalTime(newRequestedArrivalTime);

    if (newArrivalTime == RaptorConstants.TIME_NOT_SET) {
      throw new IllegalStateException(
        "The arrival should not have been accepted if it does not have a legal arrival-time."
      );
    }
    if (newArrivalTime == arrivalTime()) {
      return this;
    }
    int newDepartureTime = newArrivalTime - access.durationInSeconds();

    return new AccessStopArrivalLeximin<>(newDepartureTime, access);
  }

  @Override
  public LeximinMcStopArrival<T> addSlackToArrivalTime(int slack) {
    return new AccessStopArrivalLeximin<>(departureTime, accessEgressWithExtraSlack(access, slack));
  }
}
