// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/**
 * Kubernetes mounts ConfigMaps in the Pod's file-system as directories where the contained files
 * are named with the keys and the contents of the file are the values. This class assists with
 * parsing this data and representing it as a Map.
 */
public class ConfigMapConsumer implements Map<String, String> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final ScheduledExecutorService threadPool;
  private final AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>(null);
  private File mountPointDir;
  private Runnable onUpdate;

  /**
   * Construct config map consumer.
   * @param executorService executor
   */
  public ConfigMapConsumer(ScheduledExecutorService executorService) {
    this.threadPool = executorService;
  }

  protected void scheduleUpdates(String mountPoint, Runnable onUpdate) {
    this.onUpdate = onUpdate;
    this.mountPointDir = new File(mountPoint);
    if (mountPointDir.exists()) {
      onUpdate.run();
      schedule();
    }

  }

  private void schedule() {
    long initialDelay = readTuningParameter("configMapUpdateInitialDelay", 3);
    long delay = readTuningParameter("configMapUpdateDelay", 10);
    ScheduledFuture<?> old =
        future.getAndSet(threadPool.scheduleWithFixedDelay(onUpdate, initialDelay, delay, TimeUnit.SECONDS));
    if (old != null) {
      old.cancel(true);
    }
  }

  /**
   * read tuning parameter.
   * @param parameter parameter
   * @param defaultValue default value
   * @return parameter value
   */
  public long readTuningParameter(String parameter, long defaultValue) {
    String val = get(parameter);
    if (val != null) {
      try {
        return Long.parseLong(val);
      } catch (NumberFormatException nfe) {
        LOGGER.warning(MessageKeys.EXCEPTION, nfe);
      }
    }

    return defaultValue;
  }

  @Override
  public int size() {
    return Optional.ofNullable(mountPointDir.list()).map(list -> list.length).orElse(0);
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean containsKey(Object key) {
    if (key instanceof String) {
      File child = new File(mountPointDir, (String) key);
      return child.exists();
    }
    return false;
  }

  @Override
  public boolean containsValue(Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String get(Object key) {
    if (key instanceof String) {
      return readValue((String) key);
    }
    return null;
  }

  @Override
  public String put(String key, String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String remove(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putAll(@Nonnull Map<? extends String, ? extends String> m) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nonnull
  public Set<String> keySet() {
    Set<String> keys = new HashSet<>();
    String[] list = mountPointDir.list();
    if (list != null) {
      Collections.addAll(keys, list);
    }
    return keys;
  }

  @Override
  @Nonnull
  public Collection<String> values() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nonnull
  public Set<Entry<String, String>> entrySet() {
    Set<Entry<String, String>> entries = new HashSet<>();
    String[] list = mountPointDir.list();
    if (list != null) {
      for (String s : list) {
        entries.add(
            new Entry<>() {

              @Override
              public String getKey() {
                return s;
              }

              @Override
              public String getValue() {
                return readValue(s);
              }

              @Override
              public String setValue(String value) {
                throw new UnsupportedOperationException();
              }
            });
      }
    }
    return entries;
  }

  private String readValue(String key) {
    File child = new File(mountPointDir, key);
    if (child.exists()) {
      try {
        return new String(Files.readAllBytes(child.toPath()));
      } catch (IOException e) {
        LOGGER.warning(MessageKeys.EXCEPTION, e);
      }
    }

    return null;
  }
}
