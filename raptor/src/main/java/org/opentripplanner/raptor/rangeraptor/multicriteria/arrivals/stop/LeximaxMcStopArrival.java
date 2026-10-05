package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop;

import static org.opentripplanner.raptor.api.model.RaptorValueType.ROUNDS;

import java.util.Arrays;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.opentripplanner.utils.time.TimeUtils;

/**
 * Abstract super class for multi-criteria stop arrival.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
public abstract sealed class LeximaxMcStopArrival<T extends RaptorTripSchedule>
  extends McStopArrival<T>
  permits AccessStopArrivalLeximax, TransferStopArrivalLeximax, TransitStopArrivalLeximax {

  // collection of costs
  private int[] c1Path;

  /**
   * Transit or transfer.
   *
   * @param previous     the previous arrival visited for the current trip
   * @param round        the RangeRaptor round
   * @param stop         stop index for this arrival
   * @param arrivalTime  the arrival time for this stop index
   * @param c1           the accumulated criteria-one(cost) at this stop arrival
   */
  protected LeximaxMcStopArrival(
    LeximaxMcStopArrival<T> previous,
    int round,
    int stop,
    int arrivalTime,
    int c1
  ) {
    super(previous, round, stop, arrivalTime, c1);
  }

  /**
   * Initial state - first stop visited during the RAPTOR algorithm.
   */
  protected LeximaxMcStopArrival(
    int stop,
    int departureTime,
    int travelDuration,
    int initialC1,
    int round
  ) {
    super(stop, departureTime, travelDuration, initialC1, round);
    this.c1Path = new int[1];
    this.c1Path[0] = initialC1;
    // System.out.println(toString());
  }

  /**
   *
   * @return The costs at each stop in the current path, sorted
   */
  public final int[] c1Path() {
    return c1Path;
  }

  public void setC1Path(int[] path) {
    this.c1Path = path;
  }

  /**
   * Add the given amount of slack to the arrival-time. This is used to add extraordinary
   * wait-time to an arrival - for example, in via-search where a minimum-wait-time can be set.
   */
  public abstract LeximaxMcStopArrival<T> addSlackToArrivalTime(int slack);

  @Override
  public String toString() {
    return (
      TimeUtils.timeToStrCompact(arrivalTime()) +
      " " +
      ROUNDS.format(round()) +
      Arrays.toString(c1Path)
    );
  }

  protected void assignCost(int[] previousC, int c) {
    int idx = Arrays.binarySearch(previousC, c);
    if (idx < 0) {
      idx = -(idx + 1);
    }
    c1Path = new int[previousC.length + 1];
    System.arraycopy(previousC, 0, c1Path, 0, idx);
    System.arraycopy(previousC, idx, c1Path, idx + 1, previousC.length - idx);
    c1Path[idx] = c;
    // System.out.println(toString());
  }
}
