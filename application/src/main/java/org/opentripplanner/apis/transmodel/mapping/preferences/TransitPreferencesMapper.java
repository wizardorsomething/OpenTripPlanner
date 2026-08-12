package org.opentripplanner.apis.transmodel.mapping.preferences;

import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import org.opentripplanner.apis.transmodel.model.TransportModeSlack;
import org.opentripplanner.apis.transmodel.model.plan.RelaxCostType;
import org.opentripplanner.apis.transmodel.support.DataFetcherDecorator;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.raptor.api.request.RaptorProfile;
import org.opentripplanner.routing.api.request.framework.CostLinearFunction;
import org.opentripplanner.routing.api.request.preference.TransitPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransitPreferencesMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TransitPreferencesMapper.class);

  public static void mapTransitPreferences(
    TransitPreferences.Builder transit,
    DataFetchingEnvironment environment,
    DataFetcherDecorator callWith
  ) {
    callWith.argument(
      "preferred.otherThanPreferredLinesPenalty",
      transit::withOtherThanPreferredRoutesPenalty
    );
    transit.withBoardSlack(builder -> {
      callWith.argument("boardSlackDefault", builder::withDefaultSec);
      callWith.argument("boardSlackList", (Object v) ->
        TransportModeSlack.mapIntoDomain(builder, v)
      );
    });
    transit.withAlightSlack(builder -> {
      callWith.argument("alightSlackDefault", builder::withDefaultSec);
      callWith.argument("alightSlackList", (Object v) ->
        TransportModeSlack.mapIntoDomain(builder, v)
      );
    });
    if (OTPFeature.AlternativePaths.isOn()) {
      transit.withRaptor(b -> b.withProfile(RaptorProfile.MULTI_CRITERIA_AP));
    }
    callWith.argument("ignoreRealtimeUpdates", transit::withIgnoreRealtimeUpdates);
    callWith.argument("includePlannedCancellations", transit::withIncludePlannedCancellations);
    callWith.argument("includeRealtimeCancellations", transit::withIncludeRealtimeCancellations);
    callWith.argument("relaxTransitGroupPriority", it ->
      transit.withRelaxTransitGroupPriority(
        RelaxCostType.mapToDomain((Map<String, Object>) it, CostLinearFunction.NORMAL)
      )
    );
  }
}
