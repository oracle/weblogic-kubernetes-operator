// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1IngressList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryDatabaseTest {

  private static final String NS1 = "namespace1";
  private static final String NS2 = "namespace2";
  private static final String NAME1 = "name1";
  private static final String NAME2 = "name2";

  private final InMemoryDatabase<V1Ingress, V1IngressList> database =
      new InMemoryDatabase<>() {
        @Override
        V1IngressList createList(List<V1Ingress> items) {
          return new V1IngressList().items(items);
        }
      };

  @Test
  void whenItemAbsent_readThrowsException() {
    Map<String, String> keys = keys().name(NAME1).namespace(NS1).map();
    InMemoryDatabaseException thrown = assertThrows(InMemoryDatabaseException.class, () -> {
      database.read(keys);
    });
    assertThat(thrown.getCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  void whenItemPresent_createThrowsException() {
    createItem(NAME1, NS1);

    V1Ingress item = new V1Ingress().metadata(new V1ObjectMeta().namespace(NS1).name(NAME1));
    Map<String, String> keys = keys().namespace(NS1).map();
    InMemoryDatabaseException thrown = assertThrows(InMemoryDatabaseException.class, () -> {
      database.create(item, keys);
    });
    assertThat(thrown.getCode(), equalTo(HttpURLConnection.HTTP_CONFLICT));
  }

  private V1Ingress createItem(String name, String namespace) {
    V1Ingress item =
        new V1Ingress().metadata(new V1ObjectMeta().namespace(namespace).name(name));
    database.create(item, keys().namespace(namespace).map());
    return item;
  }

  @Test
  void afterItemCreated_canRetrieveIt() {
    V1Ingress item = createItem(NAME1, NS1);

    assertThat(database.read(keys().name(NAME1).namespace(NS1).map()), equalTo(item));
  }

  @Test
  void whenItemAbsent_replaceThrowsException() {
    V1Ingress item = new V1Ingress().metadata(new V1ObjectMeta().namespace(NS1).name(NAME1));
    Map<String, String> keys = keys().namespace(NS1).map();
    InMemoryDatabaseException thrown = assertThrows(InMemoryDatabaseException.class, () -> {
      database.replace(item, keys);
    });
    assertThat(thrown.getCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  void afterReplaceItem_canRetrieveNewItem() {
    createItem(NAME1, NS1).kind("old item");

    V1Ingress replacement =
        new V1Ingress()
            .metadata(new V1ObjectMeta().namespace(NS1).name(NAME1))
            .kind("new item");
    database.replace(replacement, keys().namespace(NS1).map());

    assertThat(database.read(keys().name(NAME1).namespace(NS1).map()), equalTo(replacement));
  }

  @Test
  void afterItemDeleted_cannotRetrieveIt() {
    createItem(NAME1, NS1);
    database.delete(keys().name(NAME1).namespace(NS1).map());

    Map<String, String> keys = keys().name(NAME1).namespace(NS1).map();
    assertThrows(InMemoryDatabaseException.class,
          () -> database.read(keys));
  }

  @Test
  void whenItemToDeletedAbsent_throwException() {
    Map<String, String> keys = keys().name(NAME1).namespace(NS1).map();
    InMemoryDatabaseException thrown = assertThrows(InMemoryDatabaseException.class, () -> {
      database.delete(keys);
    });
    assertThat(thrown.getCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  void afterItemsCreated_canListMatches() {
    V1Ingress item1 = createItem(NAME1, NS1);
    V1Ingress item2 = createItem(NAME2, NS1);
    V1Ingress item3 = createItem(NAME1, NS2);

    assertThat(
        database.list(keys().namespace(NS1).map()).getItems(), containsInAnyOrder(item1, item2));
    assertThat(
        database.list(keys().name(NAME1).map()).getItems(), containsInAnyOrder(item1, item3));
  }

  private MapMaker keys() {
    return new MapMaker();
  }

  static class MapMaker {
    private final Map<String, String> keys = new HashMap<>();

    public Map<String, String> map() {
      return keys;
    }

    public MapMaker namespace(String namespace) {
      keys.put("namespace", namespace);
      return this;
    }

    public MapMaker name(String name) {
      keys.put("name", name);
      return this;
    }
  }
}
