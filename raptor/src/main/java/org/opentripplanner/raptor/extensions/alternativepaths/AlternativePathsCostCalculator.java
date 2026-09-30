package org.opentripplanner.raptor.extensions.alternativepaths;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opentripplanner.raptor.extensions.alternativepaths.records.APOutput;
import org.opentripplanner.raptor.extensions.alternativepaths.records.StopTimeEntry;
import org.opentripplanner.raptor.extensions.alternativepaths.records.StopTransfer;
import org.opentripplanner.raptor.path.LeximinMarker;
import org.opentripplanner.raptor.spi.RaptorCostCalculator;
import org.opentripplanner.raptor.spi.RaptorTransferConstraint;
import org.opentripplanner.raptor.spi.RaptorTransitDataProvider;
import org.opentripplanner.raptor.spi.RaptorTripPattern;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private final Map<Integer, List<StopTransfer>> transfersFromStop;
  private HashSet<Integer> egresses;
  private int maxAlternatives;
  private int latest;

  /**
   * Cost unit: SECONDS - The unit for all input parameters are in the OTP TRANSIT model cost unit
   * (in Raptor the unit for cost is centi-seconds).
   */
  public AlternativePathsCostCalculator(RaptorTransitDataProvider<T> transitData) {
    this.transitData = transitData;
    tripsByStop = new HashMap<>();
    transfersFromStop = new HashMap<>();
  }

  public void applyInfo(
    APOutput<T> routingInfo,
    int earliest,
    int latest
  ) {
    var initialTripsByStop = routingInfo.initialTripsByStop();
    var resolver = transitData.stopNameResolver();
    this.latest = latest;

    egresses = routingInfo.egresses();
    for (var entry : initialTripsByStop.entrySet()) {
      int stop = entry.getKey();
      tripsByStop.put(stop, new ArrayList<>());

      var latestTrips = new HashMap<Integer, T>();
      for (T trip : entry.getValue()) {
        latestTrips.merge(
          trip.pattern().patternIndex(),
          trip,
          (a, b) -> a.departure(0) >= b.departure(0) ? a : b
        );
      }

      for (var patternEntry : latestTrips.entrySet()) {
        T latestTrip = patternEntry.getValue();
        var pattern = latestTrip.pattern();
        var timetable = transitData.getRouteForIndex(patternEntry.getKey()).timetable();

        var idsInPattern = stopIndexToIdInPattern(stop, pattern);
        for (int idInPattern : idsInPattern) {
          int latestDeparture = latestTrip.departure(idInPattern);
          for (int i = 0; i < timetable.numberOfTripSchedules(); i++) {
            T trip = timetable.getTripSchedule(i);
            int arrival = trip.arrival(idInPattern);
            int departure = trip.departure(idInPattern);
            if (arrival >= earliest && departure <= latestDeparture) {
              tripsByStop
                .get(stop)
                .add(
                  new StopTimeEntry<>(trip, arrival, departure)
                );
            } else if (departure > latestDeparture){
              break;
            }
          }
        }
      }
    }
    LOG.info("Number of stops: {}", tripsByStop.size());

    var transfersFromStopSets = routingInfo.transferOptions();
    for (int stop : transfersFromStopSets.keySet()) {
      transfersFromStop.put(
        stop,
        transfersFromStopSets
          .get(stop)
          .stream()
          .sorted(Comparator.comparing(StopTransfer::walkDurationSeconds))
          .toList()
      );
    }

    int minAlternatives = Integer.MAX_VALUE;
    maxAlternatives = 0;
    for (int stop : tripsByStop.keySet()) {
      tripsByStop
        .get(stop)
        .sort(Comparator.comparing((StopTimeEntry<T> e) -> e.departureTime()).reversed());
      int s = alternativesAtStopArrival(stop, earliest).size();
      if (s < minAlternatives) {
        minAlternatives = s;
      }
      if (s > maxAlternatives) {
        maxAlternatives = s;
      }
    }
    LOG.info("Min alternatives: {}", minAlternatives);
    LOG.info("Max alternatives: {}", maxAlternatives);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Stops:");
      for (int stop : tripsByStop
        .keySet()
        .stream()
        .sorted(Comparator.comparing(resolver::apply))
        .toList()) {
        LOG.debug(
          "{}: {} alternatives: {}",
          resolver.apply(stop),
          tripsByStop.get(stop).size(),
          tripsByStop
            .get(stop)
            .stream()
            .map(StopTimeEntry::toFormattedString)
            .collect(Collectors.joining(", "))
        );
        if (transfersFromStop.containsKey(stop)) {
          LOG.debug(
            "\t{} transfers: {}",
            transfersFromStop.get(stop).size(),
            transfersFromStop
              .get(stop)
              .stream()
              .map(transfer -> transfer.toFormattedString(transitData.stopNameResolver()))
              .collect(Collectors.joining(", "))
          );
        }
        LOG.debug(
          "\t{} total alternatives at start",
          alternativesAtStopArrival(stop, earliest).size()
        );
      }
      LOG.debug(
        "{} Egress stops: {}",
        egresses.size(),
        egresses.stream().map(resolver::apply).toList()
      );
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

  private HashSet<Integer> stopIndexToIdInPattern(int stopIndex, RaptorTripPattern pattern) {
    HashSet<Integer> idsInPattern = new HashSet<>();
    for (int position = 0; position < pattern.numberOfStopsInPattern(); position++) {
      if (pattern.stopIndex(position) == stopIndex) {
        idsInPattern.add(position);
      }
    }
    if (idsInPattern.isEmpty()) {
      LOG.warn("Unknown stop index, this shouldn't happen.");
    }
    return idsInPattern;
  }

  @Override
  public int transitArrivalCost(
    int boardCost,
    int alightSlack,
    int arrivalTime,
    T trip,
    int toStopIndex
  ) {
    if (egresses.contains(toStopIndex)) {
      return maxAlternatives + 1;
    }
    if (tripsByStop.containsKey(toStopIndex)) {
      var allTrips = alternativesAtStopArrival(toStopIndex, arrivalTime);
      if (trip != null) {
        allTrips.remove(trip);
      }
      int count = allTrips.size();
      if (count > maxAlternatives) {
        // System.out.println("Impossible alternative count: " + count);
        LOG.warn(
          "Impossible alternative count at {}: {}",
          transitData.stopNameResolver().apply(toStopIndex),
          count
        );
      }
      return count;
    } else {
      // System.out.println("Unknown stop in calculator: " + stopIndex);
      return 0;
    }
  }

  private HashSet<T> alternativesAtStopArrival(int stopIndex, int arrivalTime) {
    HashSet<T> countedTrips = new HashSet<>();
    for (StopTimeEntry<T> alternative : tripsByStop.get(stopIndex)) {
      // it needs to be >=, not >, so access paths are included properly, since they may calculate arrival time as exactly equal to trip departure time
      if (alternative.departureTime() < arrivalTime) {
        break;
      }
      countedTrips.add(alternative.trip());
    }
    for (var nearbyStop : transfersFromStop.getOrDefault(stopIndex, new ArrayList<>())) {
      int arrivalTimeWithWalk = arrivalTime + nearbyStop.walkDurationSeconds();
      if (arrivalTimeWithWalk > latest) {
        break;
      }
      for (StopTimeEntry<T> alternative : tripsByStop.getOrDefault(
        nearbyStop.targetStop(),
        new ArrayList<>()
      )) {
        if (alternative.departureTime() < arrivalTimeWithWalk) {
          break;
        }
        countedTrips.add(alternative.trip());
      }
    }
    return countedTrips;
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
    // despite the name, this actually returns the maximum count
    // this is used in Leximin.dummyPath to create a c1Path of length 1
    return maxAlternatives;
  }

  @Override
  public int costEgress(int stopIndex, boolean egressHasRides) {
    return 0;
  }
}
