// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Root of the SPI implemented by the container. */
public class Container implements ComponentRegistry, ComponentEx {
  /**
   * Constant that represents a "no {@link Container}", which always returns null from {@link
   * #getSpi(Class)}.
   */
  public static final Container NONE = new NoneContainer();
  private final Map<String, Component> components = new ConcurrentHashMap<String, Component>();

  @Override
  public <S> S getSpi(Class<S> spiType) {
    for (Component c : components.values()) {
      S s = c.getSpi(spiType);
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
  public <E> Iterable<E> getIterableSpi(Class<E> spiType) {
    E item = getSpi(spiType);
    if (item != null) {
      return Collections.singletonList(item);
    }
    return Collections.emptySet();
  }

  private static final class NoneContainer extends Container {
  }
}
