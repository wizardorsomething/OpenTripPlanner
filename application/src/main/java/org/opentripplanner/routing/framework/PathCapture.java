package org.opentripplanner.routing.framework;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Debug side channel: collects the string form of the Raptor paths of a routing request, so a
 * test runner (SpeedTestDump) can print them next to the itineraries. Does nothing unless enabled,
 * and the path's toString is only evaluated when enabled.
 * <p>
 * Test cases are run one after another, so a plain static collector is enough.
 */
public final class PathCapture {

  private static final ConcurrentLinkedQueue<String> PATHS = new ConcurrentLinkedQueue<>();
  private static volatile boolean enabled = false;

  private PathCapture() {}

  public static void enable() {
    enabled = true;
  }

  public static void disable() {
    enabled = false;
    PATHS.clear();
  }

  public static void clear() {
    PATHS.clear();
  }

  /** Record a path (anything with a useful toString). No-op unless enabled. */
  public static void add(String path) {
    if (enabled) {
      PATHS.add(path);
    }
  }

  /** Return and remove everything captured so far. */
  public static List<String> drain() {
    var result = new ArrayList<String>();
    String p;
    while ((p = PATHS.poll()) != null) {
      result.add(p);
    }
    return result;
  }
}
