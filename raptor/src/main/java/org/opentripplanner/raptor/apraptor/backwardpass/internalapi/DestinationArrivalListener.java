package org.opentripplanner.raptor.apraptor.backwardpass.internalapi;

import org.opentripplanner.raptor.api.model.RaptorAccessEgress;

public interface DestinationArrivalListener {
  void newDestinationArrival(
    int round,
    int fromStopArrivalTime,
    boolean stopReachedOnBoard,
    RaptorAccessEgress egressPath
  );
}
