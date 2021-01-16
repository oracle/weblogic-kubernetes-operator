// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import java.util.ArrayList;
import java.util.List;

/**
 * LinkContainerModel is the base class of WebLogic operator model classes that support links to
 * related REST endpoints.
 */
public class LinkContainerModel extends BaseModel {

  private final List<LinkModel> links = new ArrayList<>();

  /**
   * Get the links.
   *
   * @return a List of LinkModels.
   */
  public List<LinkModel> getLinks() {
    return links;
  }

  /**
   * Add the standard self and canonical links to the list of links.
   *
   * @param selfHref - the self link's hypertext reference.
   */
  public void addSelfLinks(String selfHref) {
    addLink("self", selfHref);
    addLink("canonical", selfHref);
  }

  /**
   * Add the standard self, canonical and parent links to the list of links.
   *
   * @param selfHref - the self link's hypertext reference.
   * @param parentHref - the parent link's hypertext reference.
   */
  public void addSelfAndParentLinks(String selfHref, String parentHref) {
    addSelfLinks(selfHref);
    addLink("parent", parentHref);
  }

  /**
   * Add a link to an action resource.
   *
   * @param action - the name of the action (i.e. the terminal path segment in the url)
   * @param actionHref - the action link's hypertext reference.
   */
  public void addActionLink(String action, String actionHref) {
    addLink("action", action, actionHref);
  }

  /**
   * Add an untitled link to a resource.
   *
   * @param rel - the link's relationship.
   * @param href - the link's hypertext reference.
   */
  public void addLink(String rel, String href) {
    links.add(new LinkModel(rel, href));
  }

  /**
   * Add a link to a resource.
   *
   * @param rel - the link's relationship.
   * @param title -the link's title.
   * @param href - the link's hypertext reference.
   */
  public void addLink(String rel, String title, String href) {
    links.add(new LinkModel(rel, title, href));
  }

  @Override
  protected String propertiesToString() {
    StringBuilder sb = new StringBuilder();
    sb.append("links=[");
    boolean first = true;
    for (LinkModel link : getLinks()) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(link);
      first = false;
    }
    sb.append("]");
    return sb.toString();
  }
}
