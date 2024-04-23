// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.AbstractMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Context of a single processing flow. */
public class Packet extends AbstractMap<String, Object> {
  private final ConcurrentMap<String, Object> delegate = new ConcurrentHashMap<>();

  public Packet() {
  }

  private Packet(Packet that) {
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

  public Fiber getFiber() {
    return Fiber.getCurrentIfSet();
  }
}
