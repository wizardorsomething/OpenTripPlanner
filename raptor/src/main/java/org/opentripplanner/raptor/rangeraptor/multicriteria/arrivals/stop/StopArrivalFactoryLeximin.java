package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop;

import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.view.PatternRideView;
import org.opentripplanner.raptor.spi.RaptorTransfer;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

/**
 * This class is responsible for creating StopArrivals which support accumulated criteria ONE.
 */
public class StopArrivalFactoryLeximin<T extends RaptorTripSchedule> implements McStopArrivalFactory<T> {

  @Override
  public McStopArrival<T> createAccessStopArrival(
    int departureTime,
    RaptorAccessEgress accessPath
  ) {
    return new AccessStopArrivalLeximin<>(departureTime, accessPath);
  }

  public McStopArrival<T> createAccessStopArrival(
    int departureTime,
    RaptorAccessEgress accessPath,
    int cost
  ) {
    return new AccessStopArrivalLeximin<>(departureTime, accessPath, cost);
  }

  @Override
  public McStopArrival<T> createTransitStopArrival(
    PatternRideView<T, McStopArrival<T>> ride,
    int alightStop,
    int stopArrivalTime,
    int c1
  ) {
    return new TransitStopArrivalLeximin<>(
      (LeximinMcStopArrival<T>) ride.prevArrival(),
      alightStop,
      stopArrivalTime,
      c1,
      ride.boardStopPosition(),
      ride.trip()
    );
  }

  @Override
  public McStopArrival<T> createTransferStopArrival(
    McStopArrival<T> previous,
    RaptorTransfer transfer,
    int arrivalTime
  ) {
    return new TransferStopArrivalLeximin<>((LeximinMcStopArrival<T>) previous, transfer, arrivalTime);
  }
}
