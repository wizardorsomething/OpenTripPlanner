package org.opentripplanner.transit.speed_test;

import static org.opentripplanner.standalone.configure.ConstructApplication.initializeTransferCache;
import static org.opentripplanner.transit.speed_test.support.AssertSpeedTestSetup.assertTestDateHasData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.fares.service.gtfs.v1.DefaultFareService;
import org.opentripplanner.framework.application.OtpAppException;
import org.opentripplanner.framework.transaction.TimetableSnapshotParameters;
import org.opentripplanner.framework.transaction.api.RepositoryHandle;
import org.opentripplanner.framework.transaction.internal.TransactionFactory;
import org.opentripplanner.raptor.configure.RaptorConfig;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RaptorTransitData;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TransitTuningParameters;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.RaptorTransitDataMapper;
import org.opentripplanner.routing.api.response.RoutingResponse;
import org.opentripplanner.routing.framework.PathCapture;
import org.opentripplanner.routing.linking.VertexLinkerTestFactory;
import org.opentripplanner.service.realtimevehicles.internal.DefaultRealtimeVehicleRepository;
import org.opentripplanner.service.realtimevehicles.internal.RealtimeVehicleRepositoryLifecycle;
import org.opentripplanner.service.vehicleparking.internal.DefaultVehicleParkingRepository;
import org.opentripplanner.service.vehiclerental.internal.DefaultVehicleRentalService;
import org.opentripplanner.standalone.OtpStartupInfo;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.standalone.api.TestServerContext;
import org.opentripplanner.standalone.config.DebugUiConfig;
import org.opentripplanner.standalone.config.OtpConfigLoader;
import org.opentripplanner.standalone.config.RouterConfig;
import org.opentripplanner.standalone.config.routerconfig.RaptorEnvironmentFactory;
import org.opentripplanner.standalone.config.routerconfig.VectorTileConfig;
import org.opentripplanner.standalone.server.DefaultServerRequestContext;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transfer.regular.TransferRepository;
import org.opentripplanner.transfer.regular.TransferServiceTestFactory;
import org.opentripplanner.transit.repository.DefaultTimetableRepository;
import org.opentripplanner.transit.repository.TimetableRepository;
import org.opentripplanner.transit.repository.TimetableRepositoryLifecycle;
import org.opentripplanner.transit.repository.TimetableRepositorySnapshot;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitRepository;
import org.opentripplanner.transit.speed_test.model.SpeedTestProfile;
import org.opentripplanner.transit.speed_test.model.testcase.CsvFileSupport;
import org.opentripplanner.transit.speed_test.model.testcase.ExpectedResults;
import org.opentripplanner.transit.speed_test.model.testcase.TestCase;
import org.opentripplanner.transit.speed_test.model.testcase.TestCaseDefinition;
import org.opentripplanner.transit.speed_test.model.testcase.TestCases;
import org.opentripplanner.transit.speed_test.model.timer.SpeedTestTimer;
import org.opentripplanner.transit.speed_test.options.SpeedTestCmdLineOpts;
import org.opentripplanner.transit.speed_test.options.SpeedTestConfig;
import org.opentripplanner.updater.configure.UpdaterConfigurator;

/**
 * Runs the SpeedTest test cases and writes two files per profile to {@code <dir>}:
 * <ul>
 *   <li>{@code travelSearch-dump-<profile>-cases.csv}: one row per test case (description and
 *   search window).</li>
 *   <li>{@code travelSearch-dump-<profile>-raptor.csv}: one row per Raptor path, as captured by
 *   {@link PathCapture} (the path's toString, i.e. including the cost vector).</li>
 * </ul>
 * No assertions, no expected-results comparison, no JIT warm-up, no timing statistics. Test
 * cases without a result (no itineraries) get no rows in the cases file; test cases that throw
 * are reported on stderr.
 * <p>
 * Uses the same command line options as SpeedTest: -d, -p, -i, -c, -t, -0, -v. Option -n is
 * ignored (every profile is run once).
 */
public class SpeedTestDump {

  private static final String TRAVEL_SEARCH_FILENAME = "travelSearch";

  private static final String[] CASES_HEADER = { "testCaseId", "description", "searchWindow" };
  private static final String[] RAPTOR_HEADER = { "testCaseId", "raptorPath" };

  private final TransitRepository transitRepository;
  private final SpeedTestTimer timer = new SpeedTestTimer();
  private final SpeedTestCmdLineOpts opts;
  private final SpeedTestConfig config;
  private final RouterConfig routerConfig;
  private final List<TestCaseDefinition> testCaseDefinitions;
  private final Map<String, ExpectedResults> expectedResultsByTcId;
  private final OtpServerRequestContext serverContext;
  private final CsvFileSupport tcIO;

  /* Setup: copied unchanged from SpeedTest, so the routing context is identical. */
  public SpeedTestDump(
    SpeedTestCmdLineOpts opts,
    SpeedTestConfig config,
    RouterConfig routerConfig,
    Graph graph,
    TransitRepository transitRepository,
    TransferRepository transferRepository
  ) {
    this.opts = opts;
    this.config = config;
    this.routerConfig = routerConfig;
    this.transitRepository = transitRepository;

    this.tcIO = new CsvFileSupport(
      opts.rootDir(),
      TRAVEL_SEARCH_FILENAME,
      config.feedId(),
      opts.replaceExpectedResultsFiles()
    );

    this.testCaseDefinitions = tcIO.readTestCaseDefinitions();
    this.expectedResultsByTcId = tcIO.readExpectedResults();

    var transitService = new DefaultTransitService(transitRepository);

    TransitTuningParameters tuningParameters = routerConfig.transitTuningConfig();
    var scheduledRaptorData = RaptorTransitDataMapper.map(
      tuningParameters,
      transitRepository,
      transferRepository
    );

    transitRepository.initRaptorTransitData(scheduledRaptorData);

    var parameters = TimetableSnapshotParameters.DEFAULT;
    var registry = TransactionFactory.createRepositoryRegistry();
    var timetableSnapshot = new DefaultTimetableRepository(
      new RaptorTransitData(transitRepository.getRaptorTransitData()),
      transitRepository.copyTripCalendarForRealTimeUpdates()
    );
    RepositoryHandle<TimetableRepositorySnapshot, TimetableRepository> timetableHandle =
      registry.registerRepositorySnapshot(
        timetableSnapshot,
        new TimetableRepositoryLifecycle(
          timetableSnapshot,
          parameters.purgeExpiredData(),
          LocalDate::now
        )
      );
    var realtimeVehicleHandle = registry.registerRepository(
      new DefaultRealtimeVehicleRepository(),
      new RealtimeVehicleRepositoryLifecycle()
    );
    var threadFactory = java.util.concurrent.Executors.defaultThreadFactory();
    var transitUpdateManager = TransactionFactory.createUpdateManagerWithPeriodicCommits(
      "speedtest",
      registry,
      threadFactory,
      parameters.maxSnapshotFrequency()
    );
    var streetUpdateManager = TransactionFactory.createUpdateManagerWithAtomicCommits(
      "speedtest-street",
      TransactionFactory.createRepositoryRegistry(),
      threadFactory
    );

    UpdaterConfigurator.configure(
      graph,
      DeduplicatorService.NOOP,
      VertexLinkerTestFactory.of(graph),
      realtimeVehicleHandle,
      new DefaultVehicleRentalService(),
      new DefaultVehicleParkingRepository(),
      transitRepository,
      null,
      null,
      transitUpdateManager,
      streetUpdateManager,
      timetableHandle,
      routerConfig.updaterConfig()
    );
    if (transitRepository.getUpdaterManager() != null) {
      transitRepository.getUpdaterManager().startUpdaters();
    }

    var raptorConfig = new RaptorConfig<TripSchedule>(
      routerConfig.transitTuningConfig(),
      RaptorEnvironmentFactory.create(routerConfig.transitTuningConfig().searchThreadPoolSize())
    );

    var vertexLinker = VertexLinkerTestFactory.of(graph);

    var transactionScope = registry.scope();
    this.serverContext = new DefaultServerRequestContext(
      DebugUiConfig.DEFAULT,
      new DefaultFareService(),
      routerConfig.flexParameters(),
      graph,
      TestServerContext.createLinkingContextFactory(graph, vertexLinker, transitService),
      timer.getRegistry(),
      null,
      raptorConfig,
      realtimeVehicleHandle.repositorySnapshot(transactionScope),
      List.of(),
      routerConfig.routingRequestDefaults(),
      TestServerContext.createStreetLimitationParametersService(),
      TransferServiceTestFactory.transferService(transferRepository),
      transactionScope,
      routerConfig.transitTuningConfig(),
      new DefaultTransitService(
        transitRepository,
        timetableHandle.repositorySnapshot(transactionScope)
      ),
      null,
      null,
      VectorTileConfig.DEFAULT,
      TestServerContext.createVehicleParkingService(),
      TestServerContext.createVehicleRentalService(),
      vertexLinker,
      TestServerContext.createViaTransferResolver(graph, transitService),
      TestServerContext.createWorldEnvelopeService(),
      null,
      null,
      null,
      TestServerContext.createStreetDetailsService(),
      null,
      null,
      null,
      null,
      null,
      null,
      null
    );

    initializeTransferCache(routerConfig.transitTuningConfig(), transitRepository);

    timer.setUp(opts.groupResultsByCategory());
  }

  public static void main(String[] args) {
    try {
      SpeedTestCmdLineOpts opts = new SpeedTestCmdLineOpts(args);
      SetupHelper.loadOtpFeatures(opts);
      var config = SpeedTestConfig.config(opts.rootDir());
      var routerConfig = new OtpConfigLoader(opts.rootDir()).loadRouterConfig();
      OtpStartupInfo.logInfo("Run Speed Test Dump");
      var model = SetupHelper.loadGraph(opts.rootDir(), config.graph());
      var transitRepository = model.transitRepository();
      var transferRepository = model.transferRepository();
      var buildConfig = model.buildConfig();
      var graph = model.graph();

      var dump = new SpeedTestDump(
        opts,
        config,
        routerConfig,
        graph,
        transitRepository,
        transferRepository
      );

      assertTestDateHasData(transitRepository, config, buildConfig);

      dump.run();

      if (transitRepository.getUpdaterManager() != null) {
        transitRepository.getUpdaterManager().stop();
      }
    } catch (OtpAppException ae) {
      System.err.println(ae.getMessage());
      System.exit(1);
    } catch (Exception e) {
      System.err.println(e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  public void run() throws IOException {
    PathCapture.enable();
    try {
      for (SpeedTestProfile profile : opts.profiles()) {
        var testCases = TestCases.of()
          .withSkipCost(opts.skipCost())
          .withIncludeIds(opts.testCaseIds())
          .withIncludeCategories(opts.includeCategories())
          .withDefinitions(testCaseDefinitions)
          .withExpectedResultsById(expectedResultsByTcId)
          .build();

        var base = TRAVEL_SEARCH_FILENAME + "-dump-" + profile.name().toLowerCase();
        var casesFile = new File(opts.rootDir(), base + "-cases.csv");
        var raptorFile = new File(opts.rootDir(), base + "-raptor.csv");

        try (
          BufferedWriter casesOut = Files.newBufferedWriter(
            casesFile.toPath(),
            StandardCharsets.UTF_8
          );
          BufferedWriter raptorOut = Files.newBufferedWriter(
            raptorFile.toPath(),
            StandardCharsets.UTF_8
          )
        ) {
          writeRow(casesOut, CASES_HEADER);
          writeRow(raptorOut, RAPTOR_HEADER);

          for (TestCase testCase : testCases.iterable()) {
            dumpTestCase(casesOut, raptorOut, testCase, profile);
            // Flush per test case, so a crash or Ctrl-C keeps everything done so far
            casesOut.flush();
            raptorOut.flush();
          }
        }
        System.err.println("Wrote " + casesFile.getPath() + " and " + raptorFile.getPath());
      }
    } finally {
      PathCapture.disable();
    }
  }

  private void dumpTestCase(
    BufferedWriter casesOut,
    BufferedWriter raptorOut,
    TestCase testCase,
    SpeedTestProfile profile
  ) throws IOException {
    String id = String.valueOf(testCase.id());

    PathCapture.clear();
    RoutingResponse response;
    try {
      response = performRouting(testCase, profile);
    } catch (Exception e) {
      System.err.println(
        "TC " + id + " threw " + e.getClass().getSimpleName() + ": " + e.getMessage()
      );
      return;
    }

    for (String path : PathCapture.drain()) {
      writeRow(raptorOut, id, path);
    }

    writeRow(casesOut, id, testCase.definition().idAndDescription(), searchWindow(response));
  }

  private RoutingResponse performRouting(TestCase testCase, SpeedTestProfile profile) {
    var speedTestRequest = new SpeedTestRequest(
      testCase,
      opts,
      config,
      profile,
      routerConfig.routingRequestDefaults(),
      transitRepository.getTimeZone()
    );
    return serverContext.routingService().route(speedTestRequest.toRouteRequest());
  }

  private static String searchWindow(RoutingResponse response) {
    var metadata = response.getMetadata();
    return metadata == null
      ? ""
      : metadata.pageDepartureTimeStart() + "," + metadata.pageDepartureTimeEnd();
  }

  /* CSV */

  private static void writeRow(BufferedWriter out, String... cells) throws IOException {
    var sb = new StringBuilder();
    for (int i = 0; i < cells.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(escape(cells[i]));
    }
    sb.append('\n');
    out.write(sb.toString());
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    if (
      value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
    ) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
