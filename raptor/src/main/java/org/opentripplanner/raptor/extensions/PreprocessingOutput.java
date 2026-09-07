package org.opentripplanner.raptor.extensions;

import java.util.HashSet;
import org.opentripplanner.raptor.spi.RaptorRoute;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

public record PreprocessingOutput<T extends RaptorTripSchedule>(HashSet<Integer> stops, HashSet<RaptorRoute<T>> routes, HashSet<Integer> egresses) {}