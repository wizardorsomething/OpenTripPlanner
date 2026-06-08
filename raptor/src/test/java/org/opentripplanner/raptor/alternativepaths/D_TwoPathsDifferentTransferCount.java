package org.opentripplanner.raptor.alternativepaths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.multiCriteria;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.standard;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor._data.RaptorTestConstants;
import org.opentripplanner.raptor._data.api.PathUtils;
import org.opentripplanner.raptor._data.transit.TestTransitData;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.raptor.configure.RaptorTestFactory;
import org.opentripplanner.raptor.moduletests.support.RaptorModuleTestCase;

/**
 * FEATURE UNDER TEST
 * <p>
 * With two alternatives departing at the same time, with one transfer each
 * - RAPTOR should choose the one arriving first
 * - RAPTOR with criterion should choose the one arriving first
 */
public class D_TwoPathsDifferentTransferCount implements RaptorTestConstants {

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
        A      E
        00:01  00:11
        -- R2
               E              D
               00:12          00:27
        -- R3
        A      B
        00:01  00:06
        -- R4
               B      C
               00:07  00:12
        -- R5
                      C       D
                      00:13   00:18
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
    var path1 = "Walk 30s ~ A ~ BUS R3 0:01 0:06 ~ B ~ BUS R4 0:07 0:12 ~ C ~ BUS R5 0:13 0:18 ~ D ~ Walk 20s [0:00:30 0:18:20 17m50s Tₙ2 C₁2_920]";
    var path2 = "Walk 30s ~ A ~ BUS R1 0:01 0:11 ~ E ~ BUS R2 0:12 0:27 ~ D ~ Walk 20s [0:00:30 0:27:20 26m50s Tₙ1 C₁2_860]";
    var path = path1 + "\n" + path2;
    return RaptorModuleTestCase.of()
      .add(standard(), PathUtils.withoutCost(path))
      .add(multiCriteria(), path)
      .build();
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void test1(RaptorModuleTestCase testCase) {
    var output = testCase.run(raptorService, data, requestBuilder);
    assertEquals(testCase.expected(), output);
  }
}
