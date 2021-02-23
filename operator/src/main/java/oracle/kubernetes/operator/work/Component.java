// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.HashMap;
import java.util.Map;

/**
 * Interface that allows components to hook up with each other.
 *
 * @see ComponentRegistry
 */
public interface Component {
  /**
   * Creates a Component that supports the given SPI instances. If an instance in objects is a Class
   * then this class is the key for the next object instance. Otherwise, the instance's own class is
   * used.
   *
   * @param objects Objects to encapsulate; precede object with Class to specify key type
   * @return Component
   */
  static Component createFor(Object... objects) {
    Map<Class<?>, Object> comps = new HashMap<>();
    Class<?> key = null;
    for (Object o : objects) {
      if (o != null) {
        if (o instanceof Class) {
          key = (Class<?>) o;
          continue;
        }
        Class<?> k;
        if (key != null) {
          k = key;
          key = null;
        } else {
          k = o.getClass();
        }
        if (comps.put(k, o) != null) {
          throw new IllegalStateException();
        }
      }
    }

    return new Component() {
      @Override
      public <S> S getSpi(Class<S> spiType) {
        Object o = comps.get(spiType);
        if (o == null) {
          return null;
        }
        return spiType.cast(o);
      }
    };
  }

  /**
   * Gets the specified SPI.
   *
   * <p>This method works as a kind of directory service for SPIs, allowing various components to
   * define private contract and talk to each other.
   *
   * @param <S> SPI type
   * @param spiType SPI class
   * @return null if such an SPI is not provided by this object.
   */
  <S> S getSpi(Class<S> spiType);
}
