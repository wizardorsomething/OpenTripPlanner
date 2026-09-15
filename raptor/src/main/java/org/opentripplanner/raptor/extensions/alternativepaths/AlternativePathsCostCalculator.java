package org.opentripplanner.raptor.extensions.alternativepaths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opentripplanner.raptor.extensions.alternativepaths.records.PreprocessingOutput;
import org.opentripplanner.raptor.extensions.alternativepaths.records.StopTransfer;
import org.opentripplanner.raptor.path.LeximinMarker;
import org.opentripplanner.raptor.spi.RaptorCostCalculator;
import org.opentripplanner.raptor.spi.RaptorRoute;
import org.opentripplanner.raptor.spi.RaptorTransferConstraint;
import org.opentripplanner.raptor.spi.RaptorTransitDataProvider;
import org.opentripplanner.raptor.spi.RaptorTripPattern;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

record StopTimeEntry<T extends RaptorTripSchedule>(T trip, int arrivalTime, int departureTime) {

  public String toFormattedString() {
    return "%s (%s)".formatted(trip.pattern().debugInfo(), formatSecondsAsTime(arrivalTime));
  }

  private static String formatSecondsAsTime(int secondsSinceMidnight) {
    return "%02d:%02d".formatted(
      secondsSinceMidnight / 3600,
      (secondsSinceMidnight % 3600) / 60
    );
  }
}

/**
 * The responsibility for the cost calculator is to calculate the default multi-criteria cost.
 * <p/>
 * This class is immutable and thread safe.
 */
public final class AlternativePathsCostCalculator<T extends RaptorTripSchedule>
  implements RaptorCostCalculator<T>, LeximinMarker {

  private static final Logger LOG = LoggerFactory.getLogger(AlternativePathsCostCalculator.class);

  private final RaptorTransitDataProvider<T> transitData;
  private final Map<Integer, List<StopTimeEntry<T>>> tripsByStop;
  private Map<Integer, HashSet<StopTransfer>> transfersFromStop;
  private HashSet<Integer> egresses;
  private int minAlternatives;
  private int maxAlternatives;
  private final int scalingFactor = 100;

  /**
   * Cost unit: SECONDS - The unit for all input parameters are in the OTP TRANSIT model cost unit
   * (in Raptor the unit for cost is centi-seconds).
   */
  public AlternativePathsCostCalculator(RaptorTransitDataProvider<T> transitData) {
    this.transitData = transitData;
    tripsByStop = new HashMap<>();
    transfersFromStop = new HashMap<>();
  }

  public void applyRoutes(PreprocessingOutput<RaptorRoute<T>> routingInfo, int earliest, int latest) {
    var routesByStop = routingInfo.routesByStop();
    var resolver = transitData.stopNameResolver();
    this.transfersFromStop = routingInfo.transferOptions();
    egresses = routingInfo.egresses();
    for (int stop : routesByStop.keySet()) {
      tripsByStop.put(stop, new ArrayList<>());
      for (RaptorRoute<T> route : routesByStop.get(stop)) {
        var timetable = route.timetable();
        var pattern = route.pattern();
        int nTrips = timetable.numberOfTripSchedules();
        int idInPattern = stopIndexToIdInPattern(stop, pattern);
        for (int i = 0; i < nTrips; i++) {
          T trip = timetable.getTripSchedule(i);
          if (trip.arrival(idInPattern) > earliest && trip.departure(idInPattern) < latest) {
            tripsByStop.get(stop).add(new StopTimeEntry<>(trip, trip.arrival(idInPattern), trip.departure(idInPattern)));
          }
        }
      }
    }
    LOG.info("Number of stops: {}", tripsByStop.size());

    minAlternatives = Integer.MAX_VALUE;
    maxAlternatives = 0;
    for (int stop : tripsByStop.keySet()) {
      int s = stopArrivalCount(stop, earliest);
      if (s < minAlternatives) {
        minAlternatives = s;
      }
      if (s > maxAlternatives) {
        maxAlternatives = s;
      }
    }
    LOG.info("Min alternatives: {}", minAlternatives);
    LOG.info("Max alternatives: {}", maxAlternatives);
    System.out.println("Min alternatives: " + minAlternatives);
    System.out.println("Max alternatives: " + maxAlternatives);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Stops:");
      for (int stop : tripsByStop.keySet().stream().sorted().toList()) {
        LOG.debug("{}: {} alternatives: {}", resolver.apply(stop), tripsByStop.get(stop).size(), tripsByStop.get(stop).stream().map(StopTimeEntry::toFormattedString).collect(Collectors.joining(", ")));
        if (transfersFromStop.containsKey(stop)) {
          LOG.debug("{} transfers: {}", transfersFromStop.get(stop).size(), transfersFromStop.get(stop).stream().map(transfer -> transfer.toFormattedString(transitData.stopNameResolver())).collect(Collectors.joining(", ")));
        }
      }
      LOG.debug("{} Egress stops: {}", egresses.size(), egresses.stream().map(resolver::apply).toList());
    }
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
    return 0;
  }

  @Override
  public int transitCost(int transitDuration, T tripScheduledBoarded) {
    return 0;
  }

  private int stopIndexToIdInPattern(int stopIndex, RaptorTripPattern pattern) {
    for (int position = 0; position < pattern.numberOfStopsInPattern(); position++) {
      if (pattern.stopIndex(position) == stopIndex) {
        return position;
      }
    }
    LOG.warn("Unknown stop index, this shouldn't happen.");
    return -1;
  }

  @Override
  public int transitArrivalCost(
    int boardCost,
    int alightSlack,
    int transitDuration,
    T trip,
    int toStopIndex
  ) {
    if (egresses.contains(toStopIndex)) {
      return (maxAlternatives+1) * scalingFactor;
    }
    return stopArrivalCost(toStopIndex, trip.arrival(stopIndexToIdInPattern(toStopIndex, trip.pattern())));
  }

  private int stopArrivalCount(
    int stopIndex,
    int arrivalTime
  ) {
    int count = 0;
    for (StopTimeEntry<T> alternative : tripsByStop.get(stopIndex)) {
      if (alternative.departureTime() > arrivalTime) {
        count += 1;
      }
    }
    for (var nearbyStop : transfersFromStop.getOrDefault(stopIndex, new HashSet<>())) {
      int arrivalTimeWithWalk = arrivalTime + nearbyStop.walkDurationSeconds();
      for (StopTimeEntry<T> alternative : tripsByStop.getOrDefault(nearbyStop.targetStop(), new ArrayList<>())) {
        if (alternative.departureTime() > arrivalTimeWithWalk) {
          count += 1;
        }
      }
    }
    return count;
  }

  public int stopArrivalCost(
    int stopIndex,
    int arrivalTime
  ) {
    if (tripsByStop.containsKey(stopIndex)) {
      int count = stopArrivalCount(stopIndex, arrivalTime);
      if (count > maxAlternatives) {
        // System.out.println("Impossible alternative count: " + count);
        LOG.warn("Impossible alternative count at {}: {}", transitData.stopNameResolver().apply(stopIndex), count);
      }
      return count * scalingFactor;
    } else {
      // System.out.println("Unknown stop in calculator: " + stopIndex);
      return 0;
    }
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
      // @TODO not a guaranteed lower bound since it's not filtered
      return minAlternatives * scalingFactor * minNumTransfers;
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

}
