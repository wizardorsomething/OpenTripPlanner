package org.opentripplanner.raptor.extensions.alternativepaths.records;

import org.opentripplanner.raptor.spi.RaptorStopNameResolver;

public record StopTransfer(int targetStop, int walkDurationSeconds) {

  public String toFormattedString(RaptorStopNameResolver resolver) {
    return resolver.apply(targetStop) + ": " + walkDurationSeconds / 60 + "min";
  }
}
