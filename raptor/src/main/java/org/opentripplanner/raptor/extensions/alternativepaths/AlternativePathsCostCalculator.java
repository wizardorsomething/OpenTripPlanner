package org.opentripplanner.raptor.extensions.alternativepaths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.opentripplanner.raptor.spi.RaptorCostCalculator;
import org.opentripplanner.raptor.spi.RaptorRoute;
import org.opentripplanner.raptor.spi.RaptorTransferConstraint;
import org.opentripplanner.raptor.spi.RaptorTransitDataProvider;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

record StopTimeEntry<T extends RaptorTripSchedule>(T trip, int arrivalTime, int departureTime) {}

/**
 * The responsibility for the cost calculator is to calculate the default multi-criteria cost.
 * <p/>
 * This class is immutable and thread safe.
 */
public final class AlternativePathsCostCalculator<T extends RaptorTripSchedule>
  implements RaptorCostCalculator<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AlternativePathsCostCalculator.class);

  private final RaptorTransitDataProvider<T> transitData;
  private final Map<Integer, List<StopTimeEntry<T>>> tripsByStop;
  private final Map<String, List<StopTimeEntry<T>>> tripsByHub;
  private final Map<Integer, String> tripToHub;
  private int minAlternatives;
  private int maxAlternatives;

  /**
   * Cost unit: SECONDS - The unit for all input parameters are in the OTP TRANSIT model cost unit
   * (in Raptor the unit for cost is centi-seconds).
   */
  public AlternativePathsCostCalculator(RaptorTransitDataProvider<T> transitData, List<? extends RaptorRoute<T>> routes) {
    this.transitData = transitData;
    tripsByStop = new HashMap<>();
    tripsByHub = new HashMap<>();
    tripToHub = new HashMap<>();
  }

  public void applyRoutes(HashSet<RaptorRoute<T>> routes, int earliest, int latest) {
    for (RaptorRoute<T> route : routes) {
      var timetable = route.timetable();
      var pattern = route.pattern();
      int nStops = pattern.numberOfStopsInPattern();
      int nTrips = timetable.numberOfTripSchedules();
      // LOG.info("Route: {}", route.pattern().debugInfo());
      // LOG.info("nStops: {}", nStops);
      // LOG.info("nTrips: {}", nTrips);
      var stopNameResolver = transitData.stopNameResolver();
      for (int j = 0; j < nStops; j++) {
        int stopIndex = pattern.stopIndex(j);
        String hub = stopNameResolver.apply(stopIndex).replaceFirst("\\s?\\(\\d+\\)$", "");
        tripToHub.computeIfAbsent(stopIndex, _ -> hub);
        for (int k = 0; k < nTrips; k++) {
          T trip = timetable.getTripSchedule(k);
          if (trip.arrival(j) >= earliest && trip.departure(j) <= latest) {
            tripsByStop.computeIfAbsent(stopIndex, _ -> new ArrayList<>()).add(new StopTimeEntry<>(trip, trip.arrival(j), trip.departure(j)));
            tripsByHub.computeIfAbsent(hub, _ -> new ArrayList<>()).add(new StopTimeEntry<>(trip, trip.arrival(j), trip.departure(j)));
          }
        }
      }
    }
    LOG.info("Number of routes: {}", routes.size());
    LOG.info("Number of stops: {}", tripsByStop.size());
    LOG.info("Number of hubs: {}", tripsByHub.size() );
    minAlternatives = Integer.MAX_VALUE;
    maxAlternatives = 0;
    for (String hub : tripsByHub.keySet()) {
      LOG.debug("{}: {} alternatives", hub, tripsByHub.get(hub).size());
    }
    for (List<StopTimeEntry<T>> trips : tripsByHub.values()) {
      int s = trips.size();
      if (s < minAlternatives) {
        minAlternatives = s;
      }
      if (s > maxAlternatives) {
        maxAlternatives = s;
      }
    }
    LOG.info("Min alternatives: {}", minAlternatives);
    LOG.info("Max alternatives: {}", maxAlternatives);
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
    if (transferConstraints.isRegularTransfer()) {
      return boardingCostRegularTransfer(firstBoarding, prevArrivalTime, boardStopIndex, boardTime);
    } else {
      return boardingCostConstrainedTransfer(
        prevArrivalTime,
        boardStopIndex,
        boardTime,
        0,
        firstBoarding,
        transferConstraints
      );
    }
  }

  @Override
  public int transitCost(int transitDuration, T tripScheduledBoarded) {
    return 0;
  }

  @Override
  public int transitArrivalCost(
    int boardCost,
    int alightSlack,
    int transitDuration,
    T trip,
    int toStopIndex
  ) {
    var pattern = trip.pattern();
    int stopIdInPattern = -1;
    for (int position = 0; position < pattern.numberOfStopsInPattern(); position++) {
      if (pattern.stopIndex(position) == toStopIndex) {
        stopIdInPattern = position;
      }
    }
    return stopCost(toStopIndex, trip.arrival(stopIdInPattern), trip);
  }

  public int stopCost(
    int stopIndex,
    int arrivalTime,
    T trip
  ) {

    int count = 0;
    if (tripsByStop.containsKey(stopIndex)) {
      for (StopTimeEntry<T> alternative : tripsByHub.get(tripToHub.get(stopIndex))) {
        if (alternative.trip() != trip && alternative.departureTime() > arrivalTime) {
          count += 1;
        }
      }
      if (count > maxAlternatives) {
        LOG.warn("Impossible alternative count: {}", tripsByHub.get(tripToHub.get(stopIndex)));
      }
    } else {
      // System.out.println(stopIndex);
      return (maxAlternatives+2) * 100;
    }
    // System.out.println(transitData.stopNameResolver().apply(stopIndex) + ": " + count);
    // LOG.info("{}: {}", transitData.stopNameResolver().apply(stopIndex), count);

    return (maxAlternatives+1 - count) * 100;
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
      return minAlternatives * 100 * minNumTransfers;
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

  /** This is public for test purposes only */
  public int boardingCostRegularTransfer(
    boolean firstBoarding,
    int prevArrivalTime,
    int boardStop,
    int boardTime
  ) {
    return 0;
  }

  /* private methods */

  private int boardingCostConstrainedTransfer(
    int prevArrivalTime,
    int boardStopIndex,
    int boardTime,
    int transitReluctanceIndex,
    boolean firstBoarding,
    RaptorTransferConstraint txConstraints
  ) {
    return boardingCostRegularTransfer(firstBoarding, prevArrivalTime, boardStopIndex, boardTime);
  }
}
