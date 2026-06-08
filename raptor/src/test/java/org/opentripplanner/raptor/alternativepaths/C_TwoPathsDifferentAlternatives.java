package org.opentripplanner.raptor.alternativepaths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.standard;

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
 * With two alternatives departing at the same time, with one transfer each, but the slower one having an alternative connection at the transfer
 * - RAPTOR should choose the one arriving first
 * - RAPTOR with criterion should choose both
 */
public class C_TwoPathsDifferentAlternatives implements RaptorTestConstants {

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
   *   R2: 00:01 - 00:08 - 00:10
   *   R3:         00:10 - 00:20
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
      .access("Walk 30s ~ A")
      .withTimetables(
        """
        -- R1
        A      B
        00:01  00:06
        -- R2
               B              D
               00:07          00:17
        -- R3
        A             C
        00:01         00:08
        -- R4
                      C       D
                      00:09   00:11
        -- R5
               B              D
               00:09          00:21
        """
      )
      .egress("D ~ Walk 20s");

    requestBuilder
      .searchParams()
      .earliestDepartureTime(T00_00)
      .latestArrivalTime(T00_30)
      .timetable(true);
  }

  static List<RaptorModuleTestCase> testCases() {
    var path = "Walk 30s ~ A ~ BUS R3 0:01 0:08 ~ C ~ BUS R4 0:09 0:11 ~ D ~ Walk 20s [0:00:30 0:11:20 10m50s Tₙ1]";
    return RaptorModuleTestCase.of()
      .add(standard(), path)
      .build();
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void testRaptor(RaptorModuleTestCase testCase) {
    assertEquals(testCase.expected(), testCase.run(raptorService, data, requestBuilder));
  }
}
