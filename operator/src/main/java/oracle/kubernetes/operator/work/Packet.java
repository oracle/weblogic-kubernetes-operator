// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Context of a single processing flow. Acts as a map and as a registry of components. */
public class Packet extends AbstractMap<String, Object> implements ComponentRegistry, ComponentEx {
  private final ConcurrentMap<String, Component> components =
      new ConcurrentHashMap<String, Component>();
  private final ConcurrentMap<String, Object> delegate = new ConcurrentHashMap<String, Object>();

  public Packet() {}

  private Packet(Packet that) {
    components.putAll(that.components);
    delegate.putAll(that.delegate);
  }

  /**
   * Clones a packet so that the new packet starts with identical values and components.
   *
   * @return Cloned packet
   */
  public Packet clone() {
    return new Packet(this);
  }

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

  @Override
  public Set<Entry<String, Object>> entrySet() {
    return delegate.entrySet();
  }

  @Override
  public Object put(String key, Object value) {
    return value != null ? delegate.put(key, value) : delegate.remove(key);
  }

  @SuppressWarnings("unchecked")
  public <T> T getValue(String key) {
    return (T) get(key);
  }
}
