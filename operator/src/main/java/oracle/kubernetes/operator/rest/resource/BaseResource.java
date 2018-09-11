// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import oracle.kubernetes.operator.rest.AuthenticationFilter;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.rest.model.LinkContainerModel;

/**
 * BaseResource is the base resource of all the WebLogic operator's REST resources. It provides a
 * number of common utilities that they use.
 */
public class BaseResource {

  private String pathSegment;
  private BaseResource parent;

  @Context private UriInfo uriInfo;
  @Context private ContainerRequestContext containerRequestContext;

  protected BaseResource(BaseResource parent, String pathSegment) {
    this.parent = parent;
    this.pathSegment = pathSegment;
  }

  protected BaseResource getParent() {
    return parent;
  }

  protected String getPathSegment() {
    return pathSegment;
  }

  protected RestBackend getBackend() {
    return (RestBackend)
        getContainerRequestContext().getProperty(AuthenticationFilter.REST_BACKEND_PROPERTY);
  }

  protected UriInfo getUriInfo() {
    return getRoot().uriInfo;
  }

  protected ContainerRequestContext getContainerRequestContext() {
    return getRoot().containerRequestContext;
  }

  protected BaseResource getRoot() {
    return (getParent() != null) ? getParent().getRoot() : this;
  }

  protected void addSelfAndParentLinks(LinkContainerModel lc) {
    if (getParent() == null) {
      lc.addSelfLinks(href());
    } else {
      lc.addSelfAndParentLinks(href(), getParent().href());
    }
  }

  protected void addActionLink(LinkContainerModel lc, String action) {
    lc.addActionLink(action, href(action));
  }

  protected void addLink(LinkContainerModel lc, String rel) {
    lc.addLink(rel, href(rel));
  }

  protected WebApplicationException notFound(String pathSegment) {
    String notFoundHref = href(pathSegment);
    return new WebApplicationException(
        Response.status(Status.NOT_FOUND).entity(notFoundHref).build());
  }

  protected String href(String... pathSegments) {

    UriBuilder b = getUriInfo().getBaseUriBuilder();

    // traverse my parents to find the path segments to this resource.
    // insert each into an array starting at 0 so that the array
    // starts from the root resource when we're finished.
    List<String> resourceSegments = new ArrayList<String>();
    for (BaseResource r = this; r != null; r = r.getParent()) {
      resourceSegments.add(0, r.getPathSegment());
    }
    for (String seg : resourceSegments) {
      b.segment(seg);
    }

    // then append the sub resource path:
    for (String seg : pathSegments) {
      b.segment(seg);
    }

    // return relative paths since the Operator runs in a docker container
    // and the host/port that a client sees outside the container doesn't
    // match the one used inside the container - i.e. we don't want to leak
    // the host/port inside the container to the client
    return b.build().getPath();
  }
}
