package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop;

import org.opentripplanner.raptor.spi.RaptorTripSchedule;

public final class LeximaxComparators {

  public static <T extends RaptorTripSchedule> ArrivalParetoSetComparatorFactory<
    McStopArrival<T>
  > ofCompareC1() {
    return new ArrivalParetoSetComparatorFactory<>(LeximaxComparators::compareC1);
  }

  private static boolean leximaxComparison(int[] pathL, int[] pathR) {
    int lenL = pathL.length;
    int lenR = pathR.length;
    int minLen = Math.min(lenL, lenR);
    for (int i = 0; i < minLen; i++) {
      int lv = pathL[i];
      int rv = pathR[i];
      if (lv != rv) {
        return lv > rv;
      }
    }
    return minLen == lenL && minLen < lenR;
  }

  private static <T extends McStopArrival<?>> boolean compareC1(T l, T r) {
    LeximaxMcStopArrival<?> ll = (LeximaxMcStopArrival<?>) l;
    LeximaxMcStopArrival<?> rr = (LeximaxMcStopArrival<?>) r;
    return (
      l.arrivalTime() < r.arrivalTime() ||
      l.round() < r.round() ||
      leximaxComparison(ll.c1Path(), rr.c1Path())
    );
  }
}
