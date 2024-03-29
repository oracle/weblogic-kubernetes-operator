// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

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
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Object> delegate = new ConcurrentHashMap<>();

  public Packet() {
  }

  /**
   * Adds the specified component to this packet so that it can be retrieved. Returns the modified packet.
   * @param component a component which knows how to add itself to a packet.
   */
  public Packet with(PacketComponent component) {
    if (component != null) {
      component.addToPacket(this);
    }
    return this;
  }

  private Packet(Packet that) {
    components.putAll(that.components);
    delegate.putAll(that.delegate);
  }

  /**
   * Copies a packet so that the new packet starts with identical values and components.
   *
   * @return Cloned packet
   */
  public Packet copy() {
    return new Packet(this);
  }

  /**
   * Get SPI by class.
   * @param spiType SPI class
   * @param <S> SPI class
   * @return implementation object
   */
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
