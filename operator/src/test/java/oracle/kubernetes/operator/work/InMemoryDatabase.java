// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ObjectMeta;

public abstract class InMemoryDatabase<T, L> {

  private final Map<DatabaseKey, T> contents = new HashMap<>();

  /**
   * Create DB.
   * @param item item
   * @param keys keys
   */
  public void create(T item, Map<String, String> keys) {
    T t = contents.get(new DatabaseKey(keys, item));
    if (t != null) {
      throw new InMemoryDatabaseException(HttpURLConnection.HTTP_CONFLICT, "Item already exists");
    }

    contents.put(new DatabaseKey(keys, item), item);
  }

  void delete(Map<String, String> keys) {
    T removed = contents.remove(new DatabaseKey(keys));
    if (removed == null) {
      throw new InMemoryDatabaseException(HttpURLConnection.HTTP_NOT_FOUND, "No such item");
    }
  }

  L list(Map<String, String> searchKeys) {
    List<T> foundItems = new ArrayList<>();
    for (DatabaseKey key : contents.keySet()) {
      if (key.matches(searchKeys)) {
        foundItems.add(contents.get(key));
      }
    }

    return createList(foundItems);
  }

  T read(Map<String, String> keys) {
    T t = contents.get(new DatabaseKey(keys));
    if (t == null) {
      throw new InMemoryDatabaseException(HttpURLConnection.HTTP_NOT_FOUND, "No such item");
    }
    return t;
  }

  abstract L createList(List<T> items);

  void replace(T item, Map<String, String> keys) {
    DatabaseKey databaseKey = new DatabaseKey(keys, item);
    T t = contents.get(databaseKey);
    if (t == null) {
      throw new InMemoryDatabaseException(HttpURLConnection.HTTP_NOT_FOUND, "No such item");
    }

    contents.put(databaseKey, item);
  }

  private static class DatabaseKey {
    private final Map<String, String> keys;

    DatabaseKey(@Nonnull Map<String, String> keys) {
      this.keys = new HashMap<>(keys);
    }

    DatabaseKey(@Nonnull Map<String, String> keys, Object o) {
      this(keys);
      String name = getName(o);
      if (name != null) {
        this.keys.put("name", name);
      }
    }

    private String getName(Object o) {
      try {
        V1ObjectMeta meta = (V1ObjectMeta) o.getClass().getMethod("getMetadata").invoke(o);
        return meta.getName();
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        return null;
      }
    }

    boolean matches(Map<String, String> searchKeys) {
      for (String key : searchKeys.keySet()) {
        if (!Objects.equals(searchKeys.get(key), keys.get(key))) {
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean equals(Object o) {
      return o == this || ((o instanceof DatabaseKey) && keys.equals(((DatabaseKey) o).keys));
    }

    @Override
    public int hashCode() {
      return Objects.hash(keys);
    }
  }
}
