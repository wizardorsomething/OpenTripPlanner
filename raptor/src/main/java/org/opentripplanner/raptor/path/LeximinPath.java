package org.opentripplanner.raptor.path;

import java.util.Arrays;
import java.util.stream.IntStream;
import org.opentripplanner.raptor.api.path.AccessPathLeg;
import org.opentripplanner.raptor.api.path.PathLeg;
import org.opentripplanner.raptor.api.path.RaptorPath;
import org.opentripplanner.raptor.spi.RaptorStopNameResolver;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

public class LeximinPath<T extends RaptorTripSchedule> extends Path<T> {

  private int[] c1Path;

  private LeximinPath(
    int iterationDepartureTime,
    int startTime,
    int endTime,
    int numberOfTransfers,
    int c1,
    int c2
  ) {
    super(iterationDepartureTime, startTime, endTime, numberOfTransfers, c1, c2);
    c1Path = new int[1];
    c1Path[0] = c1;
  }

  public LeximinPath(int iterationDepartureTime, AccessPathLeg<T> accessLeg, int c1) {
    super(iterationDepartureTime, accessLeg, c1);
    setC1Path(accessLeg);
  }

  public LeximinPath(int iterationDepartureTime, AccessPathLeg<T> accessLeg, int c1, int c2) {
    super(iterationDepartureTime, accessLeg, c1, c2);
    setC1Path(accessLeg);
  }

  private void setC1Path(AccessPathLeg<T> accessLeg) {
    IntStream.Builder c1PathList = IntStream.builder();
    PathLeg<T> leg = accessLeg;
    c1PathList.add(leg.c1());
    while (!leg.isEgressLeg()) {
      if (leg.isTransitLeg()) {
        c1PathList.add(leg.c1());
      }
      leg = leg.nextLeg();
    }
    var array = c1PathList.build().toArray();
    this.c1Path = Arrays.copyOf(array, array.length-1);
  }

  public final int[] getC1Path() {
    return c1Path;
  }

  /**
   * Create a "dummy" path without legs. Can be used to test if a path is pareto optimal without
   * creating the whole path.
   */
  public static <T extends RaptorTripSchedule> RaptorPath<T> dummyPath(
    int iteration,
    int startTime,
    int endTime,
    int numberOfTransfers,
    int cost,
    int c2
  ) {
    return new LeximinPath<>(iteration, startTime, endTime, numberOfTransfers, cost, c2);
  }

  @Override
  public String toString() {
    return super.toString().replaceAll("(C₁).*", "$1") + c1PathToString() + "]";
  }

  @Override
  public String toString(RaptorStopNameResolver stopNameTranslator) {
    return super.toString(stopNameTranslator).replaceAll("(C₁).*", "$1") + c1PathToString() + "]";
  }

  private String c1PathToString() {
    StringBuilder sb = new StringBuilder(c1Path.length * 6);
    sb.append('[');
    for (int i = 0; i < c1Path.length; i++) {
      if (i > 0) { sb.append(", "); }
      sb.append(c1Path[i]);
    }
    sb.append(']');
    return sb.toString();
  }

  public static <T extends RaptorTripSchedule> boolean compareC1Path(RaptorPath<T> l, RaptorPath<T> r) {
    int[] sortedL = ((LeximinPath<T>) l).getC1Path().clone();
    Arrays.sort(sortedL);
    int[] sortedR = ((LeximinPath<T>) r).getC1Path().clone();
    Arrays.sort(sortedR);
    return leximinComparison(sortedL, sortedR);
  }

  private static boolean leximinComparison(int[] pathL, int[] pathR) {
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


}
