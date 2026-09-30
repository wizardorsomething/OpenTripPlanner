package org.opentripplanner.raptor.extensions.alternativepaths.records;

import java.util.HashMap;
import java.util.HashSet;

public record APOutput<T>(
  HashMap<Integer, HashSet<T>> initialTripsByStop,
  HashMap<Integer, HashSet<StopTransfer>> transferOptions,
  HashSet<Integer> egresses
) {
}
