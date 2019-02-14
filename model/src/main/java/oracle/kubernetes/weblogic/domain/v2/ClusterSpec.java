// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import java.util.Map;
import javax.annotation.Nonnull;

public abstract class ClusterSpec {
  /**
   * Returns the labels applied to server instance services.
   *
   * @return a map of labels
   */
  @Nonnull
  public abstract Map<String, String> getServiceLabels();

  /**
   * Returns the annotations applied to service instance services.
   *
   * @return a map of annotations
   */
  @Nonnull
  public abstract Map<String, String> getServiceAnnotations();

  /**
   * Returns the labels applied to the cluster service.
   *
   * @return a map of labels
   */
  @Nonnull
  public abstract Map<String, String> getClusterLabels();

  /**
   * Returns the annotations applied to the cluster service.
   *
   * @return a map of annotations
   */
  @Nonnull
  public abstract Map<String, String> getClusterAnnotations();
}
