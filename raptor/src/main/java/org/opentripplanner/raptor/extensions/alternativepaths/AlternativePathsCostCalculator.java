package org.opentripplanner.raptor.extensions.alternativepaths;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opentripplanner.raptor.extensions.alternativepaths.records.APOutput;
import org.opentripplanner.raptor.extensions.alternativepaths.records.StopTimeEntry;
import org.opentripplanner.raptor.extensions.alternativepaths.records.StopTransfer;
import org.opentripplanner.raptor.path.APMarker;
import org.opentripplanner.raptor.spi.RaptorCostCalculator;
import org.opentripplanner.raptor.spi.RaptorTransferConstraint;
import org.opentripplanner.raptor.spi.RaptorTransitDataProvider;
import org.opentripplanner.raptor.spi.RaptorTripPattern;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

record TripKey(int route, int id) {}

/**
 * The responsibility for the cost calculator is to calculate the default multi-criteria cost.
 * <p/>
 * This class is immutable and thread safe.
 */
public final class AlternativePathsCostCalculator<T extends RaptorTripSchedule>
  implements RaptorCostCalculator<T>, APMarker {

  private static final Logger LOG = LoggerFactory.getLogger(AlternativePathsCostCalculator.class);

  private final RaptorTransitDataProvider<T> transitData;
  private final Map<Integer, List<StopTimeEntry<T>>> tripsByStop;
  private final Map<Integer, List<StopTransfer>> transfersFromStop;
  private HashSet<Integer> egresses;
  private int maxAlternatives;
  private int earliest;
  private int latest;
  private int maxWalkingDuration;

  /**
   * Cost unit: SECONDS - The unit for all input parameters are in the OTP TRANSIT model cost unit
   * (in Raptor the unit for cost is centi-seconds).
   */
  public AlternativePathsCostCalculator(RaptorTransitDataProvider<T> transitData) {
    this.transitData = transitData;
    tripsByStop = new HashMap<>();
    transfersFromStop = new HashMap<>();
  }

  private TripKey keyOf(T trip) {
    return new TripKey(trip.pattern().patternIndex(), trip.tripSortIndex());
  }

  public void applyInfo(APOutput<T> routingInfo, int earliest, int latest, int maxWalkingDuration) {
    var initialTripsByStop = routingInfo.initialTripsByStop();
    var resolver = transitData.stopNameResolver();
    this.earliest = earliest;
    this.latest = latest;
    this.maxWalkingDuration = maxWalkingDuration;

    egresses = routingInfo.egresses();
    for (var entry : initialTripsByStop.entrySet()) {
      int stop = entry.getKey();
      tripsByStop.put(stop, new ArrayList<>());
      if (!egresses.contains(stop)) {
        var latestTrips = new HashMap<Integer, T>();
        for (T trip : entry.getValue()) {
          latestTrips.merge(trip.pattern().patternIndex(), trip, (a, b) ->
            a.departure(0) >= b.departure(0) ? a : b
          );
        }

        for (var patternEntry : latestTrips.entrySet()) {
          T latestTrip = patternEntry.getValue();
          var pattern = latestTrip.pattern();
          var timetable = transitData.getRouteForIndex(patternEntry.getKey()).timetable();

          int idInPattern = stopIndexToIdInPattern(stop, pattern);
          int latestDeparture = latestTrip.departure(idInPattern);
          for (int i = 0; i < timetable.numberOfTripSchedules(); i++) {
            T trip = timetable.getTripSchedule(i);
            int arrival = trip.arrival(idInPattern);
            int departure = trip.departure(idInPattern);
            if (arrival >= earliest && departure <= latestDeparture) {
              tripsByStop.get(stop).add(new StopTimeEntry<>(trip, arrival, departure));
            } else if (departure > latestDeparture) {
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

    for (var list : tripsByStop.values()) {
      list.sort(Comparator.comparingInt((StopTimeEntry<T> e) -> e.departureTime()).reversed());
    }
    maxAlternatives = 0;
    for (int stop : tripsByStop.keySet()) {
      int s = alternativesAtStopArrival(stop, earliest, 0).size();
      if (s > maxAlternatives) {
        maxAlternatives = s;
      }
    }
    LOG.info("Max alternatives: {}", maxAlternatives);
    if (LOG.isDebugEnabled()) {
      printInfo(tripsByStop.keySet());
      LOG.debug(
        "{} Egress stops: {}",
        egresses.size(),
        egresses.stream().map(resolver::apply).toList()
      );
    }
  }

  public void printInfo(Set<Integer> keys) {
    var resolver = transitData.stopNameResolver();
    LOG.debug("Stops:");
    for (int stop : keys.stream().sorted(Comparator.comparing(resolver::apply)).toList()) {
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
      var allAlternatives = alternativesAtStopArrival(stop, earliest, 0);
      LOG.debug(
        "\t{} total alternatives at start: {}",
        allAlternatives.size(),
        allAlternatives
          .stream()
          .map(
            t -> transitData.getRouteForIndex(t.route()).pattern().debugInfo() + " no. " + t.id()
          )
          .collect(Collectors.joining(", "))
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

  private int stopIndexToIdInPattern(int stopIndex, RaptorTripPattern pattern) {
    int largestId = -1;
    for (int position = 0; position < pattern.numberOfStopsInPattern(); position++) {
      if (pattern.stopIndex(position) == stopIndex) {
        return position;
      }
    }
    LOG.warn("Unknown stop index, this shouldn't happen.");
    return largestId;
  }

  @Override
  public int transitArrivalCost(
    int arrivalTime,
    int alightSlack,
    int transferDuration,
    T trip,
    int toStopIndex
  ) {
    if (transferDuration == 0 && egresses.contains(toStopIndex)) {
      return maxAlternatives + 1;
    }
    if (tripsByStop.containsKey(toStopIndex)) {
      var allTrips = alternativesAtStopArrival(toStopIndex, arrivalTime, transferDuration);
      if (trip != null) {
        allTrips.remove(keyOf(trip));
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

  private HashSet<TripKey> alternativesAtStopArrival(
    int stopIndex,
    int arrivalTime,
    int transferDuration
  ) {
    HashSet<TripKey> countedTrips = new HashSet<>();
    for (StopTimeEntry<T> alternative : tripsByStop.get(stopIndex)) {
      // it needs to be <, not <=, so access paths are included properly, since they may calculate arrival time as exactly equal to trip departure time
      if (alternative.departureTime() < arrivalTime) {
        break;
      }
      countedTrips.add(keyOf(alternative.trip()));
    }
    for (var nearbyStop : transfersFromStop.getOrDefault(stopIndex, new ArrayList<>())) {
      int arrivalTimeWithWalk = arrivalTime + nearbyStop.walkDurationSeconds();
      if (
        arrivalTimeWithWalk > latest ||
        (transferDuration + nearbyStop.walkDurationSeconds() > maxWalkingDuration)
      ) {
        break;
      }
      for (StopTimeEntry<T> alternative : tripsByStop.getOrDefault(
        nearbyStop.targetStop(),
        new ArrayList<>()
      )) {
        if (alternative.departureTime() < arrivalTimeWithWalk) {
          break;
        }
        countedTrips.add(keyOf(alternative.trip()));
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
