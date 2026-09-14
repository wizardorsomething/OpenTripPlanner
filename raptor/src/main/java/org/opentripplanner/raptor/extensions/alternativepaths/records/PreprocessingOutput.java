package org.opentripplanner.raptor.extensions.alternativepaths.records;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Function;

public record PreprocessingOutput<R>(
  HashMap<Integer, HashSet<R>> routesByStop,
  HashMap<Integer, HashSet<StopTransfer>> transferOptions,
  HashSet<Integer> egresses) {

  public <S> PreprocessingOutput<S> mapRoutes(Function<R, S> mapper) {
    HashMap<Integer, HashSet<S>> mapped = new HashMap<>();
    for (Integer id : routesByStop.keySet()) {
      HashSet<S> mappedRoutes = new HashSet<>();
      for (R original : routesByStop.get(id)) {
        mappedRoutes.add(mapper.apply(original));
      }
      mapped.put(id, mappedRoutes);
    }
    return new PreprocessingOutput<>(mapped, transferOptions, egresses);
  }
}