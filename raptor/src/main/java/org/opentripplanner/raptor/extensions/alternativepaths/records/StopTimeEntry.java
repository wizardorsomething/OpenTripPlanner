package org.opentripplanner.raptor.extensions.alternativepaths.records;

import org.opentripplanner.raptor.spi.RaptorTripSchedule;

public record StopTimeEntry<T extends RaptorTripSchedule>(
  T trip,
  int arrivalTime,
  int departureTime
) {
  public String toFormattedString() {
    return "%s (%s)".formatted(trip.pattern().debugInfo(), formatSecondsAsTime(arrivalTime));
  }

  private static String formatSecondsAsTime(int secondsSinceMidnight) {
    return "%02d:%02d".formatted(secondsSinceMidnight / 3600, (secondsSinceMidnight % 3600) / 60);
  }
}
