// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import java.util.ArrayList;
import java.util.List;

/** ItemModel is the base class for collection resources. */
public class CollectionModel<T extends ItemModel> extends LinkContainerModel {

  private final List<T> items = new ArrayList<>();

  /**
   * Get the items in the collection.
   *
   * @return a List of items.
   */
  public List<T> getItems() {
    return items;
  }

  /**
   * Add an item to the collection.
   *
   * @param item - the item to add to the collection.
   */
  public void addItem(T item) {
    items.add(item);
  }

  @Override
  protected String propertiesToString() {
    StringBuilder sb = new StringBuilder();
    sb.append("items=[");
    boolean first = true;
    for (T item : getItems()) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(item);
      first = false;
    }
    sb.append("]");
    sb.append(", ");
    sb.append(super.propertiesToString());
    return sb.toString();
  }
}
