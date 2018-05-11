// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressList;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class InMemoryDatabaseTest {

  private static final String NS1 = "namespace1";
  private static final String NS2 = "namespace2";
  private static final String NAME1 = "name1";
  private static final String NAME2 = "name2";

  private InMemoryDatabase<V1beta1Ingress, V1beta1IngressList> database =
      new InMemoryDatabase<V1beta1Ingress, V1beta1IngressList>() {
        @Override
        V1beta1IngressList createList(List<V1beta1Ingress> items) {
          return new V1beta1IngressList().items(items);
        }
      };

  @Test
  public void whenItemAbsent_readThrowsException() throws Exception {
    try {
      database.read(keys().name(NAME1).namespace(NS1).map());
      fail("Should have thrown an InMemoryDatabaseException");
    } catch (InMemoryDatabaseException e) {
      assertThat(e.getCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
    }
  }

  @Test
  public void whenItemPresent_createThrowsException() throws Exception {
    createItem(NAME1, NS1);

    try {
      database.create(
          new V1beta1Ingress().metadata(new V1ObjectMeta().namespace(NS1).name(NAME1)),
          keys().namespace(NS1).map());
      fail("Should have thrown an InMemoryDatabaseException");
    } catch (InMemoryDatabaseException e) {
      assertThat(e.getCode(), equalTo(HttpURLConnection.HTTP_CONFLICT));
    }
  }

  private V1beta1Ingress createItem(String name, String namespace) {
    V1beta1Ingress item =
        new V1beta1Ingress().metadata(new V1ObjectMeta().namespace(namespace).name(name));
    database.create(item, keys().namespace(namespace).map());
    return item;
  }

  @Test
  public void afterItemCreated_canRetrieveIt() throws Exception {
    V1beta1Ingress item = createItem(NAME1, NS1);

    assertThat(database.read(keys().name(NAME1).namespace(NS1).map()), equalTo(item));
  }

  @Test
  public void whenItemAbsent_replaceThrowsException() throws Exception {
    try {
      database.replace(
          new V1beta1Ingress().metadata(new V1ObjectMeta().namespace(NS1).name(NAME1)),
          keys().namespace(NS1).map());
      fail("Should have thrown an InMemoryDatabaseException");
    } catch (InMemoryDatabaseException e) {
      assertThat(e.getCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
    }
  }

  @Test
  public void afterReplaceItem_canRetrieveNewItem() throws Exception {
    createItem(NAME1, NS1).kind("old item");

    V1beta1Ingress replacement =
        new V1beta1Ingress()
            .metadata(new V1ObjectMeta().namespace(NS1).name(NAME1))
            .kind("new item");
    database.replace(replacement, keys().namespace(NS1).map());

    assertThat(database.read(keys().name(NAME1).namespace(NS1).map()), equalTo(replacement));
  }

  @Test(expected = InMemoryDatabaseException.class)
  public void afterItemDeleted_cannotRetrieveIt() throws Exception {
    createItem(NAME1, NS1);
    database.delete(keys().name(NAME1).namespace(NS1).map());

    database.read(keys().name(NAME1).namespace(NS1).map());
  }

  @Test
  public void whenItemToDeletedAbsent_throwException() throws Exception {
    try {
      database.delete(keys().name(NAME1).namespace(NS1).map());
      fail("Should have thrown an InMemoryDatabaseException");
    } catch (InMemoryDatabaseException e) {
      assertThat(e.getCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
    }
  }

  @Test
  public void afterItemsCreated_canListMatches() throws Exception {
    V1beta1Ingress item1 = createItem(NAME1, NS1);
    V1beta1Ingress item2 = createItem(NAME2, NS1);
    V1beta1Ingress item3 = createItem(NAME1, NS2);

    assertThat(
        database.list(keys().namespace(NS1).map()).getItems(), containsInAnyOrder(item1, item2));
    assertThat(
        database.list(keys().name(NAME1).map()).getItems(), containsInAnyOrder(item1, item3));
  }

  private MapMaker keys() {
    return new MapMaker();
  }

  static class MapMaker {
    private Map<String, String> keys = new HashMap<>();

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
