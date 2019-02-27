// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.Map;

/**
 * Registry for component delegates. It is expected that implementations of ComponentRegistry will
 * delegate to registered {@link Component}s in its own implementation of {@link
 * Component#getSPI(java.lang.Class)}, either before or after it considers its own SPI
 * implementations.
 */
public interface ComponentRegistry extends Component {
  /**
   * Returns the map of {@link Component}s registered with this object, keyed by name.
   *
   * @return map of registered components
   */
  public Map<String, Component> getComponents();
}
