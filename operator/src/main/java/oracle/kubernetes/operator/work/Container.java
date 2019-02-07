// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Root of the SPI implemented by the container. */
public class Container implements ComponentRegistry, ComponentEx {
  private final Map<String, Component> components = new ConcurrentHashMap<String, Component>();

  /**
   * Constant that represents a "no {@link Container}", which always returns null from {@link
   * #getSPI(Class)}.
   */
  public static final Container NONE = new NoneContainer();

  private static final class NoneContainer extends Container {}

  @Override
  public <S> S getSPI(Class<S> spiType) {
    for (Component c : components.values()) {
      S s = c.getSPI(spiType);
      if (s != null) {
        return s;
      }
    }
    return null;
  }

  @Override
  public Map<String, Component> getComponents() {
    return components;
  }

  @Override
  public <E> Iterable<E> getIterableSPI(Class<E> spiType) {
    E item = getSPI(spiType);
    if (item != null) {
      return Collections.singletonList(item);
    }
    return Collections.emptySet();
  }
}
