package org.opentripplanner.raptor.alternativepaths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.multiCriteriaAP;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor._data.RaptorTestConstants;
import org.opentripplanner.raptor._data.transit.TestTransitData;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.raptor.configure.RaptorTestFactory;
import org.opentripplanner.raptor.moduletests.support.RaptorModuleTestCase;

/**
 * FEATURE UNDER TEST
 * <p>
 * Raptor should return a path if it exists for the most basic case with one route with one trip, an
 * access and an egress path.
 */
public class PathCountTest implements RaptorTestConstants {

  private final TestTransitData data = new TestTransitData();
  private final RaptorRequestBuilder<TestTripSchedule> requestBuilder = data.requestBuilder();
  private final RaptorService<TestTripSchedule> raptorService = RaptorTestFactory.raptorService();

  /**
   * Stops: 0..3
   *
   * Stop on route (stop indexes):
   *   R1:  1 - 2 - 3
   *
   * Schedule:
   *   R1: 00:01 - 00:06 - 00:16
   *
   * Access (toStop & duration):
   *   1  30s
   *
   * Egress (fromStop & duration):
   *   3  20s
   */
  @BeforeEach
  void setup() {
    data
      .access("Walk 1s ~ A")
      .egress("C ~ Walk 1s");

    requestBuilder
      .searchParams()
      .earliestDepartureTime(T00_00)
      .latestArrivalTime(T00_30)
      .timetable(true);
  }

  static List<RaptorModuleTestCase> testCases() {
    // Cost: 1 at egress stop (maxAlternatives=1, count=0), 4 from walking paths.
    var path = "Walk 1s ~ A ~ BUS R1 0:01 0:16 ~ C ~ Walk 1s [0:00:59 0:16:01 15m2s Tₙ0 C₁[1]]";
    return RaptorModuleTestCase.of().add(multiCriteriaAP(), path).build();
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void testAccessStop(RaptorModuleTestCase testCase) {
    // R2 goes through first stop, but not destination
    data.withTimetables(
        """
        -- R1
        A      B      C
        00:01  00:06  00:16
        -- R2
        A      B      E
        00:03  00:10  00:20
        """
      );
    assertEquals(testCase.expected(), testCase.run(raptorService, data, requestBuilder));

  }

  @ParameterizedTest
  @MethodSource("testCases")
  void testMiddleStop(RaptorModuleTestCase testCase) {
    // R2 goes through second stop, but not destination
    data.withTimetables(
        """
        -- R1
        A      B      C
        00:01  00:06  00:16
        -- R2
        D      B      E
        00:03  00:10  00:20
        """
      );
    assertEquals(testCase.expected(), testCase.run(raptorService, data, requestBuilder));
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void testEgressStop(RaptorModuleTestCase testCase) {
    // R2 goes only through destination
    data.withTimetables(
        """
        -- R1
        A      B      C
        00:01  00:06  00:16
        -- R2
        D      E      C
        00:03  00:10  00:20
        """
      );
    assertEquals(testCase.expected(), testCase.run(raptorService, data, requestBuilder));
  }

}
