// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Kubernetes mounts ConfigMaps in the Pod's file-system as directories where the contained
 * files are named with the keys and the contents of the file are the values.  This class
 * assists with parsing this data and representing it as a Map.
 */
public class ConfigMapConsumer implements Map<String, String> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final File mountPointDir;

  public ConfigMapConsumer(String mountPoint) {
    this.mountPointDir = new File(mountPoint);
  }

  @Override
  public int size() {
    String[] list = mountPointDir.list();
    return list == null ? null : list.length;
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
  public void putAll(Map<? extends String, ? extends String> m) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<String> keySet() {
    Set<String> keys = new HashSet<>();
    String[] list = mountPointDir.list();
    if (list != null) {
      for (String s : list) {
        keys.add(s);
      }
    }
    return keys;
  }

  @Override
  public Collection<String> values() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<Entry<String, String>> entrySet() {
    Set<Entry<String, String>> entries = new HashSet<>();
    String[] list = mountPointDir.list();
    if (list != null) {
      for (String s : list) {
        entries.add(new Entry<String, String>() {

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
