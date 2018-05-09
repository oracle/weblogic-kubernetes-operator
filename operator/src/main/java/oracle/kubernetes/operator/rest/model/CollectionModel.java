// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import java.util.ArrayList;
import java.util.List;

/** ItemModel is the base class for collection resources. */
public class CollectionModel<T extends ItemModel> extends LinkContainerModel {

  private List<T> items = new ArrayList<T>();

  /**
   * Get the items in the collection.
   *
   * @return a List of items.
   */
  public List<T> getItems() {
    return items;
  }

  /**
   * Set the items in the collection.
   *
   * @param items - a List of items.
   */
  public void setItems(List<T> items) {
    this.items = items;
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
