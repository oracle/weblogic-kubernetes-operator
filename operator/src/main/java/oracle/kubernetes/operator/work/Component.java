// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

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
   * Gets the specified SPI.
   *
   * <p>This method works as a kind of directory service for SPIs, allowing various components to
   * define private contract and talk to each other.
   *
   * @param <S> SPI type
   * @param spiType SPI class
   * @return null if such an SPI is not provided by this object.
   */
  <S> S getSPI(Class<S> spiType);

  /**
   * Creates a Component that supports the given SPI instances. If an instance in objects is a Class
   * then this class is the key for the next object instance. Otherwise, the instance's own class is
   * used.
   *
   * @param objects Objects to encapsulate; precede object with Class to specify key type
   * @return Component
   */
  public static Component createFor(Object... objects) {
    Map<Class<?>, Object> comps = new HashMap<Class<?>, Object>();
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
      public <S> S getSPI(Class<S> spiType) {
        Object o = comps.get(spiType);
        if (o == null) {
          return null;
        }
        return spiType.cast(o);
      }
    };
  }
}
