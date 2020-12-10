// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

/**
 * An interface for objects created by the ConfigMapSplitter. After the split operation creates its list of targets,
 * the splitter will invoke these methods on the first target to indicate what happened.
 */
public interface SplitterTarget {

  /**
   * Records the total number of targets created by the split operation.
   * @param numTargets the number of created targets
   */
  void recordNumTargets(int numTargets);

  /**
   * Records the location of a entry that was split.
   * @param key the key of the split entry
   * @param firstTarget the index of first target in which the entry was recorded
   * @param lastTarget the index of the last target in which the entry was recorded
   */
  void recordEntryLocation(String key, int firstTarget, int lastTarget);
}
