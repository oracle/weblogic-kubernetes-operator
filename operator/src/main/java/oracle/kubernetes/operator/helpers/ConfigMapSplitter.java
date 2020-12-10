// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * A Kubernetes ConfigMap has a hard size limit; attempts to create one larger will fail. This is a
 * problem when we need to store more data in a config map. Our solution is to split the data among multiple maps.
 *
 * @param <T> the kind of target object to create, which will ultimately be used to create config maps
 */
public class ConfigMapSplitter<T extends SplitterTarget> {

  // The limit for a Kubernetes Config Map is 1MB, including all components of the map. We use a data limit a bit
  // below that to ensure that the map structures, including the keys, metadata and the results of JSON encoding, don't
  // accidentally put us over the limit.
  @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"}) // not private or local so that unit tests can set it.
  private static int DATA_LIMIT = 900_000;

  private final BiFunction<Map<String, String>, Integer, T> factory;

  private final List<T> result = new ArrayList<>();
  private Map<String, String> current;
  private int remainingRoom;
  private final Map<String, Location> locations = new HashMap<>();

  /**
   * Constructs a splitter object.
   *
   * @param factory a function that the splitter should use to create its target objects.
   */
  public ConfigMapSplitter(BiFunction<Map<String, String>, Integer, T> factory) {
    this.factory = factory;
  }

  /**
   * Given a map, splits it so that no map has more total data than the specified limit, and returns a list of
   * target objects built from the resultant maps. This may result in some maps receiving partial value for the largest
   * items. If the target type implements CountRecorder, the 'recordCount' method of the first target will be invoked
   * with the number of targets created.
   *
   * @param data the map to split.
   */
  public List<T> split(Map<String, String> data) {
    startSplitResult();
    for (DataEntry dataEntry : getSortedEntrySizes(data)) {
      addToSplitResult(dataEntry);
    }
    recordSplitResult();

    recordTargetInfo(result.get(0), result.size());
    return result;
  }

  @Nonnull
  private List<DataEntry> getSortedEntrySizes(Map<String, String> data) {
    return data.entrySet().stream().map(DataEntry::new).sorted().collect(Collectors.toList());
  }

  private void startSplitResult() {
    current = new HashMap<>();
    remainingRoom = DATA_LIMIT;
  }

  /**
   * Adds the specified data entry to one or more split results, recording its location if it is not wholly
   * in the first split result.
   * @param entry a data entry
   */
  private void addToSplitResult(DataEntry entry) {
    int startIndex = result.size();
    while (entry.getRemainingLength() > 0) {
      remainingRoom -= entry.addToMap(current, remainingRoom);
      if (remainingRoom == 0) {
        recordSplitResult();
        startSplitResult();
      }
      if (!result.isEmpty()) {
        locations.put(entry.key, createLocation(entry, startIndex));
      }
    }
  }

  private void recordSplitResult() {
    result.add(factory.apply(current, result.size()));
  }

  private void recordTargetInfo(T target, int size) {
    target.recordNumTargets(size);

    for (Location location : locations.values()) {
      target.recordEntryLocation(location.key, location.first, location.last);
    }
  }

  private Location createLocation(DataEntry entry, int startIndex) {
    return new Location(entry, startIndex, result.size());
  }

  static class DataEntry implements Comparable<DataEntry> {
    private final String key;
    private String value;

    public DataEntry(Map.Entry<String, String> mapEntry) {
      key = mapEntry.getKey();
      value = mapEntry.getValue();
    }

    /**
     * Adds to the specified map, as much of this entry as will fit, removing it from the string
     * still to be added. Returns the number of characters added.
     * @param map the map to update
     * @param limit the maximum number of characters to add
     */
    int addToMap(Map<String, String> map, int limit) {
      final int numCharsAdded = Math.min(limit, value.length());
      map.put(key, value.substring(0, numCharsAdded));
      value = value.substring(numCharsAdded);

      return numCharsAdded;
    }

    private int getRemainingLength() {
      return value.length();
    }

    @Override
    public int compareTo(@Nonnull DataEntry o) {
      return Integer.compare(getRemainingLength(), o.getRemainingLength());
    }
  }

  static class Location {
    private final String key;
    private final int first;
    private final int last;

    Location(DataEntry entry, int first, int last) {
      this.key = entry.key;
      this.first = first;
      this.last = last;
    }

  }

}
