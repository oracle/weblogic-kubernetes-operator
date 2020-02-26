// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonPatchBuilder;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1TokenReviewStatus;
import io.kubernetes.client.openapi.models.V1UserInfo;
import oracle.kubernetes.operator.helpers.AuthenticationProxy;
import oracle.kubernetes.operator.helpers.AuthorizationProxy;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Resource;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Scope;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;

/**
 * RestBackendImpl implements the backend of the WebLogic operator REST api by making calls to
 * Kubernetes and WebLogic. A separate instance is created for each REST request since we need to
 * hold some per-request state.
 */
public class RestBackendImpl implements RestBackend {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String NEW_CLUSTER =
      "{'clusterName':'%s','replicas':%d}".replaceAll("'", "\"");
  private static TopologyRetriever INSTANCE =
      (String ns, String domainUid) -> {
        Scan s = ScanCache.INSTANCE.lookupScan(ns, domainUid);
        if (s != null) {
          return s.getWlsDomainConfig();
        }
        return null;
      };
  private final AuthenticationProxy atn = new AuthenticationProxy();
  private final AuthorizationProxy atz = new AuthorizationProxy();
  private final String principal;
  private final Collection<String> targetNamespaces;
  private V1UserInfo userInfo;

  /**
   * Construct a RestBackendImpl that is used to handle one WebLogic operator REST request.
   *
   * @param principal is the name of the Kubernetes user to use when calling the Kubernetes REST
   *     api.
   * @param accessToken is the access token of the Kubernetes service account of the client calling
   *     the WebLogic operator REST api.
   * @param targetNamespaces a list of Kubernetes namepaces that contain domains that the WebLogic
   *     operator manages.
   */
  RestBackendImpl(String principal, String accessToken, Collection<String> targetNamespaces) {
    LOGGER.entering(principal, targetNamespaces);
    this.principal = principal;
    userInfo = authenticate(accessToken);
    this.targetNamespaces = targetNamespaces;
    LOGGER.exiting();
  }

  private void authorize(String domainUid, Operation operation) {
    LOGGER.entering(domainUid, operation);
    boolean authorized;
    if (domainUid == null) {
      authorized =
          atz.check(
              userInfo.getUsername(),
              userInfo.getGroups(),
              operation,
              Resource.DOMAINS,
              null,
              Scope.cluster,
              null);
    } else {
      authorized =
          atz.check(
              userInfo.getUsername(),
              userInfo.getGroups(),
              operation,
              Resource.DOMAINS,
              domainUid,
              Scope.namespace,
              getNamespace(domainUid));
    }
    if (authorized) {
      LOGGER.exiting();
      return;
    }
    // TBD - should we say what who the user is and what the user can't do?
    WebApplicationException e = createWebApplicationException(Status.FORBIDDEN, null);
    LOGGER.throwing(e);
    throw e;
  }

  private String getNamespace(String domainUid) {
    if (domainUid == null) {
      throw new AssertionError(formatMessage(MessageKeys.NULL_DOMAIN_UID));
    }

    return getNamespace(domainUid, getDomainsList());
  }

  private String getNamespace(String domainUid, List<Domain> domains) {
    if (domainUid == null) {
      throw new AssertionError(formatMessage(MessageKeys.NULL_DOMAIN_UID));
    }
    Domain domain = findDomain(domainUid, domains);
    return domain.getMetadata().getNamespace();
  }

  private V1UserInfo authenticate(String accessToken) {
    LOGGER.entering();
    V1TokenReviewStatus status = atn.check(principal, accessToken);
    if (status == null) {
      throw new AssertionError(formatMessage(MessageKeys.NULL_TOKEN_REVIEW_STATUS));
    }
    String error = status.getError();
    if (error != null) {
      WebApplicationException e = createWebApplicationException(Status.UNAUTHORIZED, error);
      LOGGER.throwing(e);
      throw e;
    }
    if (!status.getAuthenticated()) {
      // don't know why the user didn't get authenticated
      WebApplicationException e = createWebApplicationException(Status.UNAUTHORIZED, null);
      LOGGER.throwing(e);
      throw e;
    }
    userInfo = status.getUser();
    if (userInfo == null) {
      throw new AssertionError(formatMessage(MessageKeys.NULL_USER_INFO, status));
    }
    LOGGER.exiting(userInfo);
    return userInfo;
  }

  @Override
  public Set<String> getDomainUids() {
    LOGGER.entering();
    authorize(null, Operation.list);
    Set<String> result = new TreeSet<>();
    List<Domain> domains = getDomainsList();
    for (Domain domain : domains) {
      result.add(domain.getDomainUid());
    }
    LOGGER.exiting(result);
    return result;
  }

  private List<Domain> getDomainsList() {
    Collection<List<Domain>> c = new ArrayList<>();
    try {
      for (String ns : targetNamespaces) {
        DomainList dl = new CallBuilder().listDomain(ns);

        if (dl != null) {
          c.add(dl.getItems());
        }
      }
      return c.stream().flatMap(Collection::stream).collect(Collectors.toList());
    } catch (ApiException e) {
      throw handleApiException(e);
    }
  }

  @Override
  public boolean isDomainUid(String domainUid) {
    LOGGER.entering(domainUid);
    authorize(null, Operation.list);
    boolean result = getDomainUids().contains(domainUid);
    LOGGER.exiting(result);
    return result;
  }

  @Override
  public Set<String> getClusters(String domainUid) {
    LOGGER.entering(domainUid);
    if (!isDomainUid(domainUid)) {
      throw new AssertionError(formatMessage(MessageKeys.INVALID_DOMAIN_UID, domainUid));
    }
    authorize(domainUid, Operation.get);

    // Get list of WLS Configured Clusters defined for the corresponding WLS Domain identified by
    // Domain UID
    Map<String, WlsClusterConfig> wlsClusterConfigs = getWlsConfiguredClusters(domainUid);
    Set<String> result = wlsClusterConfigs.keySet();
    LOGGER.exiting(result);
    return result;
  }

  @Override
  public boolean isCluster(String domainUid, String cluster) {
    LOGGER.entering(domainUid, cluster);
    authorize(domainUid, Operation.list);
    boolean result = getClusters(domainUid).contains(cluster);
    LOGGER.exiting(result);
    return result;
  }

  @Override
  public void scaleCluster(String domainUid, String cluster, int managedServerCount) {
    LOGGER.entering(domainUid, cluster, managedServerCount);

    if (managedServerCount < 0) {
      throw createWebApplicationException(
          Status.BAD_REQUEST, MessageKeys.INVALID_MANAGE_SERVER_COUNT, managedServerCount);
    }

    authorize(domainUid, Operation.update);

    List<Domain> domains = getDomainsList();
    Domain domain = findDomain(domainUid, domains);

    verifyWlsConfiguredClusterCapacity(domain, cluster, managedServerCount);

    patchDomain(domain, cluster, managedServerCount);
    LOGGER.exiting();
  }

  private void patchDomain(Domain domain, String cluster, int replicas) {
    if (replicas == domain.getReplicaCount(cluster)) {
      return;
    }

    try {
      JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
      int index = getClusterIndex(domain, cluster);
      if (index < 0) {
        patchBuilder.add("/spec/clusters/0", String.format(NEW_CLUSTER, cluster, replicas));
      } else {
        patchBuilder.replace("/spec/clusters/" + index + "/replicas", replicas);
      }

      new CallBuilder()
          .patchDomain(
              domain.getDomainUid(), domain.getMetadata().getNamespace(),
              new V1Patch(patchBuilder.build().toString()));
    } catch (ApiException e) {
      throw handleApiException(e);
    }
  }

  private int getClusterIndex(Domain domain, String cluster) {
    for (int i = 0; i < domain.getSpec().getClusters().size(); i++) {
      if (cluster.equals(domain.getSpec().getClusters().get(i).getClusterName())) {
        return i;
      }
    }

    return -1;
  }

  private void verifyWlsConfiguredClusterCapacity(
      Domain domain, String cluster, int requestedSize) {
    // Query WebLogic Admin Server for current configured WebLogic Cluster size
    // and verify we have enough configured managed servers to auto-scale
    WlsClusterConfig wlsClusterConfig = getWlsClusterConfig(domain.getDomainUid(), cluster);

    // Verify the current configured cluster size
    int maxClusterSize = wlsClusterConfig.getMaxClusterSize();
    if (requestedSize > maxClusterSize) {
      throw createWebApplicationException(
          Status.BAD_REQUEST,
          MessageKeys.SCALE_COUNT_GREATER_THAN_CONFIGURED,
          requestedSize,
          maxClusterSize,
          cluster,
          cluster);
    }
  }

  private WlsClusterConfig getWlsClusterConfig(String domainUid, String cluster) {
    return getWlsDomainConfig(domainUid).getClusterConfig(cluster);
  }

  private Map<String, WlsClusterConfig> getWlsConfiguredClusters(String domainUid) {
    return getWlsDomainConfig(domainUid).getClusterConfigs();
  }

  /**
   * Find the WlsDomainConfig corresponding to the given domain UID.
   *
   * @param domainUid The domain UID
   * @return The WlsDomainConfig containing the WebLogic configuration corresponding to the given
   *     domain UID. This method returns an empty configuration object if no configuration is found.
   */
  WlsDomainConfig getWlsDomainConfig(String domainUid) {
    for (String ns : targetNamespaces) {
      WlsDomainConfig config = INSTANCE.getWlsDomainConfig(ns, domainUid);
      if (config != null) {
        return config;
      }
    }
    return new WlsDomainConfig(null);
  }

  private Domain findDomain(String domainUid, List<Domain> domains) {
    for (Domain domain : domains) {
      if (domainUid.equals(domain.getDomainUid())) {
        return domain;
      }
    }

    throw createWebApplicationException(
        Status.NOT_FOUND, MessageKeys.MATCHING_DOMAIN_NOT_FOUND, domainUid);
  }

  private WebApplicationException handleApiException(ApiException e) {
    // TBD - what about e.getResponseHeaders?
    return createWebApplicationException(e.getCode(), e.getResponseBody());
  }

  private WebApplicationException createWebApplicationException(
      Status status, String msgId, Object... params) {
    String msg = formatMessage(msgId, params);
    return createWebApplicationException(status, msg);
  }

  private WebApplicationException createWebApplicationException(Status status, String msg) {
    return createWebApplicationException(status.getStatusCode(), msg);
  }

  private WebApplicationException createWebApplicationException(int status, String msg) {
    ResponseBuilder rb = Response.status(status);
    if (msg != null) {
      rb.entity(msg);
    }
    return new WebApplicationException(rb.build());
  }

  private String formatMessage(String msgId, Object... params) {
    if (params == null || params.length == 0) {
      return getResourceBundle().getString(msgId);
    }

    String msg = getResourceBundle().getString(msgId);
    MessageFormat formatter = new MessageFormat(msg);
    return formatter.format(params);
  }

  private ResourceBundle getResourceBundle() {
    for (Logger l = LOGGER.getUnderlyingLogger(); l != null; l = l.getParent()) {
      ResourceBundle rb = l.getResourceBundle();
      if (rb != null) {
        return rb;
      }
    }
    throw new AssertionError(formatMessage(MessageKeys.RESOURCE_BUNDLE_NOT_FOUND));
  }

  interface TopologyRetriever {
    WlsDomainConfig getWlsDomainConfig(String ns, String domainUid);
  }
}
