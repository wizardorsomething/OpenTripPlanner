package org.opentripplanner.raptor.alternativepaths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.multiCriteria;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.multiCriteriaAP;
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
 * With two alternatives departing and arriving at the same time, with one transfer each, but one having an alternative connection at the transfer
 * - RAPTOR should choose either (random - in practice chooses always the second one)
 * - RAPTOR with criterion should choose the one with additional alternative
 */
public class C_TwoPathsDifferentAlternatives2 implements RaptorTestConstants {

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
                      00:09   00:17
        -- R5
                      C       D
                      00:10   00:27
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
    var pathOriginal = "Walk 30s ~ A ~ BUS R1 0:01 0:06 ~ B ~ BUS R2 0:07 0:17 ~ D ~ Walk 20s [0:00:30 0:17:20 16m50s Tₙ1 C₁2_260]";
    var path1 =
      "Walk 30s ~ A ~ BUS R3 0:01 0:08 ~ C ~ BUS R4 0:09 0:17 ~ D ~ Walk 20s [0:00:30 0:17:20 16m50s Tₙ1 C₁[1, 1, 0]]";
    var path2 =
      "Walk 30s ~ A ~ BUS R1 0:01 0:06 ~ B ~ BUS R2 0:07 0:17 ~ D ~ Walk 20s [0:00:30 0:17:20 16m50s Tₙ1 C₁[1, 2, 0]]";
    return RaptorModuleTestCase.of()
      .add(standard().forwardOnly(), PathUtils.withoutCostAP(path2))
      .add(multiCriteria(), pathOriginal)
      .add(multiCriteriaAP(), path1)
      .build();
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void testRaptor(RaptorModuleTestCase testCase) {
    assertEquals(testCase.expected(), testCase.run(raptorService, data, requestBuilder));
  }
}
