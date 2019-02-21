// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1TokenReviewStatus;
import io.kubernetes.client.models.V1UserInfo;
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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import oracle.kubernetes.operator.helpers.AuthenticationProxy;
import oracle.kubernetes.operator.helpers.AuthorizationProxy;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Resource;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Scope;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ConflictRetry;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainList;

/**
 * RestBackendImpl implements the backend of the WebLogic operator REST api by making calls to
 * Kubernetes and WebLogic. A separate instance is created for each REST request since we need to
 * hold some per-request state.
 */
public class RestBackendImpl implements RestBackend {

  private V1UserInfo userInfo;

  private final AuthenticationProxy atn = new AuthenticationProxy();
  private final AuthorizationProxy atz = new AuthorizationProxy();
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final String principal;
  private final Collection<String> targetNamespaces;

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

  private void authorize(String domainUID, String cluster, Operation operation) {
    // TBD - should cluster atz be different than domain atz?
    authorize(domainUID, operation);
  }

  private void authorize(String domainUID, Operation operation) {
    LOGGER.entering(domainUID, operation);
    boolean authorized;
    if (domainUID == null) {
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
              domainUID,
              Scope.namespace,
              getNamespace(domainUID));
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

  private String getNamespace(String domainUID) {
    if (domainUID == null) {
      throw new AssertionError(formatMessage(MessageKeys.NULL_DOMAIN_UID));
    }

    return getNamespace(domainUID, getDomainsList());
  }

  private String getNamespace(String domainUID, List<Domain> domains) {
    if (domainUID == null) {
      throw new AssertionError(formatMessage(MessageKeys.NULL_DOMAIN_UID));
    }
    Domain domain = findDomain(domainUID, domains);
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
    if (!status.isAuthenticated()) {
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
  public Set<String> getDomainUIDs() {
    LOGGER.entering();
    authorize(null, Operation.list);
    Set<String> result = new TreeSet<>();
    List<Domain> domains = getDomainsList();
    for (Domain domain : domains) {
      result.add(domain.getDomainUID());
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
  public boolean isDomainUID(String domainUID) {
    LOGGER.entering(domainUID);
    authorize(null, Operation.list);
    boolean result = getDomainUIDs().contains(domainUID);
    LOGGER.exiting(result);
    return result;
  }

  @Override
  public Set<String> getClusters(String domainUID) {
    LOGGER.entering(domainUID);
    if (!isDomainUID(domainUID)) {
      throw new AssertionError(formatMessage(MessageKeys.INVALID_DOMAIN_UID, domainUID));
    }
    authorize(domainUID, Operation.get);

    // Get list of WLS Configured Clusters defined for the corresponding WLS Domain identified by
    // Domain UID
    Map<String, WlsClusterConfig> wlsClusterConfigs = getWLSConfiguredClusters(domainUID);
    Set<String> result = wlsClusterConfigs.keySet();
    LOGGER.exiting(result);
    return result;
  }

  @Override
  public boolean isCluster(String domainUID, String cluster) {
    LOGGER.entering(domainUID, cluster);
    authorize(domainUID, cluster, Operation.list);
    boolean result = getClusters(domainUID).contains(cluster);
    LOGGER.exiting(result);
    return result;
  }

  @Override
  public void scaleCluster(String domainUID, String cluster, int managedServerCount) {
    LOGGER.entering(domainUID, cluster, managedServerCount);

    if (managedServerCount < 0) {
      throw createWebApplicationException(
          Status.BAD_REQUEST, MessageKeys.INVALID_MANAGE_SERVER_COUNT, managedServerCount);
    }

    authorize(domainUID, cluster, Operation.update);

    List<Domain> domains = getDomainsList();
    Domain domain = findDomain(domainUID, domains);

    String namespace = getNamespace(domainUID, domains);

    verifyWLSConfiguredClusterCapacity(domain, cluster, managedServerCount);

    if (updateReplicasForDomain(domain, cluster, managedServerCount)) {
      overwriteDomain(
          namespace,
          domain,
          () -> getDomainForConflictRetry(domainUID, cluster, managedServerCount));
    }
    LOGGER.exiting();
  }

  private boolean updateReplicasForDomain(Domain domain, String cluster, int newReplicaCount) {
    if (newReplicaCount != domain.getReplicaCount(cluster)) {
      domain.setReplicaCount(cluster, newReplicaCount);
      return true;
    }
    return false;
  }

  private void overwriteDomain(
      String namespace, final Domain domain, ConflictRetry<Domain> conflictRetry) {
    try {
      // Write out the Domain with updated replica values
      // TODO: Can we patch instead of replace?
      new CallBuilder()
          .replaceDomainWithConflictRetry(domain.getDomainUID(), namespace, domain, conflictRetry);
    } catch (ApiException e) {
      LOGGER.finer(
          String.format(
              "Unexpected exception when updating Domain %s in namespace %s",
              domain.getDomainUID(), namespace),
          e);
      throw new WebApplicationException(e.getMessage());
    }
  }

  Domain getDomainForConflictRetry(String domainUid, String cluster, int newReplicaCount) {
    Domain domain = findDomain(domainUid, getDomainsList());
    if (updateReplicasForDomain(domain, cluster, newReplicaCount)) {
      return domain;
    }
    return null;
  }

  private void verifyWLSConfiguredClusterCapacity(
      Domain domain, String cluster, int requestedSize) {
    // Query WebLogic Admin Server for current configured WebLogic Cluster size
    // and verify we have enough configured managed servers to auto-scale
    WlsClusterConfig wlsClusterConfig = getWlsClusterConfig(domain.getDomainUID(), cluster);

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

  private WlsClusterConfig getWlsClusterConfig(String domainUID, String cluster) {
    return getWlsDomainConfig(domainUID).getClusterConfig(cluster);
  }

  private Map<String, WlsClusterConfig> getWLSConfiguredClusters(String domainUID) {
    return getWlsDomainConfig(domainUID).getClusterConfigs();
  }

  static interface TopologyRetriever {
    public WlsDomainConfig getWlsDomainConfig(String ns, String domainUID);
  }

  static final TopologyRetriever INSTANCE =
      (String ns, String domainUID) -> {
        Scan s = ScanCache.INSTANCE.lookupScan(ns, domainUID);
        if (s != null) {
          return s.getWlsDomainConfig();
        }
        return null;
      };

  /**
   * Find the WlsDomainConfig corresponding to the given domain UID.
   *
   * @param domainUID The domain UID
   * @return The WlsDomainConfig containing the WebLogic configuration corresponding to the given
   *     domain UID. This method returns an empty configuration object if no configuration is found.
   */
  WlsDomainConfig getWlsDomainConfig(String domainUID) {
    for (String ns : targetNamespaces) {
      WlsDomainConfig config = INSTANCE.getWlsDomainConfig(ns, domainUID);
      if (config != null) {
        return config;
      }
    }
    return new WlsDomainConfig(null);
  }

  private Domain findDomain(String domainUID, List<Domain> domains) {
    for (Domain domain : domains) {
      if (domainUID.equals(domain.getDomainUID())) {
        return domain;
      }
    }

    throw createWebApplicationException(
        Status.NOT_FOUND, MessageKeys.MATCHING_DOMAIN_NOT_FOUND, domainUID);
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
}
