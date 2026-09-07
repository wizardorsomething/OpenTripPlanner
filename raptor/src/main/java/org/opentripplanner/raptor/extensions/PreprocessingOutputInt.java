package org.opentripplanner.raptor.extensions;

import java.util.HashSet;

public record PreprocessingOutputInt(HashSet<Integer> stops, HashSet<Integer> routes, HashSet<Integer> egresses) {};