// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1TokenReviewStatus;
import io.kubernetes.client.models.V1UserInfo;
import oracle.kubernetes.operator.StartupControlConstants;
import oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainList;
import oracle.kubernetes.operator.helpers.AuthenticationProxy;
import oracle.kubernetes.operator.helpers.AuthorizationProxy;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Resource;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Scope;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsRetriever;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

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

/**
 * RestBackendImpl implements the backend of the WebLogic operator REST api
 * by making calls to Kubernetes and WebLogic.  A separate instance is created
 * for each REST request since we need to hold some per-request state.
 */
public class RestBackendImpl implements RestBackend {

  private V1UserInfo userInfo;

  private final AuthenticationProxy atn = new AuthenticationProxy();
  private final AuthorizationProxy atz = new AuthorizationProxy();
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final ClientHelper clientHelper;
  private final String principal;
  private final Collection<String> targetNamespaces;

  /**
   * Construct a RestBackendImpl that is used to handle one WebLogic operator REST request.
   * @param clientHelper is used to help obtain the Kubernetes http client
   * for calling the Kubernetes REST api
   * @param principal is the name of the Kubernetes user to use when calling
   * the Kubernetes REST api.
   * @param accessToken is the access token of the Kubernetes service account of the client
   * calling the WebLogic operator REST api.
   * @param targetNamespaces a list of Kubernetes namepaces that contain domains that
   * the WebLogic operator manages.
   */
  public RestBackendImpl(ClientHelper clientHelper, String principal, String accessToken, Collection<String> targetNamespaces) {
    LOGGER.entering(principal, targetNamespaces);
    ClientHolder client = null;
    try {
      client = clientHelper.take();
      this.principal = principal;
      userInfo = authenticate(client, accessToken);
    } finally {
      recycleClient(clientHelper, client);
    }
    this.clientHelper = clientHelper;
    this.targetNamespaces = targetNamespaces;
    LOGGER.exiting();
  }

  private void authorize(ClientHolder client, String domainUID, String cluster, Operation operation) {
    // TBD - should cluster atz be different than domain atz?
    authorize(client, domainUID, operation);
  }

  private void authorize(ClientHolder client, String domainUID, Operation operation) {
    LOGGER.entering(domainUID, operation);
    boolean authorized = false;
    if (domainUID == null)  {
      authorized =
        atz.check(
          client,
          userInfo.getUsername(),
          userInfo.getGroups(),
          operation,
          Resource.domains,
          null,
          Scope.cluster,
          null
        );
    } else {
      authorized =
        atz.check(
          client,
          userInfo.getUsername(),
          userInfo.getGroups(),
          operation,
          Resource.domains,
          domainUID,
          Scope.namespace,
          getNamespace(client, domainUID)
        );
    }
    if (authorized) {
      LOGGER.exiting();
      return;
    }
    // TBD - should we say what who the user is and what the user can't do?
    WebApplicationException e =
      createWebApplicationException(Status.FORBIDDEN, null);
    LOGGER.throwing(e);
    throw e;
  }

  private String getNamespace(ClientHolder client, String domainUID) {
    if (domainUID == null) {
      throw new AssertionError(formatMessage(MessageKeys.NULL_DOMAIN_UID));
    }

    return getNamespace(domainUID, getDomainsList(client));
  }

  private String getNamespace(String domainUID, List<Domain> domains) {
    if (domainUID == null) {
      throw new AssertionError(formatMessage(MessageKeys.NULL_DOMAIN_UID));
    }
    Domain domain = findDomain(domainUID, domains);
    return domain.getMetadata().getNamespace();
  }

  private V1UserInfo authenticate(ClientHolder client, String accessToken) {
    LOGGER.entering();
    V1TokenReviewStatus status = atn.check(client, principal, accessToken);
    if (status == null) {
      throw new AssertionError(formatMessage(MessageKeys.NULL_TOKEN_REVIEW_STATUS));
    }
    String error = status.getError();
    if (error != null) {
      WebApplicationException e =
        createWebApplicationException(Status.UNAUTHORIZED, error);
      LOGGER.throwing(e);
      throw e;
    }
    if (!status.isAuthenticated()) {
      // don't know why the user didn't get authenticated
      WebApplicationException e =
        createWebApplicationException(Status.UNAUTHORIZED, null);
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

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<String> getDomainUIDs() {
    LOGGER.entering();
    Set<String> result = null;
    ClientHolder client = null;
    try {
      client = clientHelper.take();
      result = getDomainUIDs(client);
    } finally {
      recycleClient(clientHelper, client);
    }
    LOGGER.exiting(result);
    return result;
  }

  private Set<String> getDomainUIDs(ClientHolder client) {
    authorize(client, null, Operation.list);
    Set<String> result = new TreeSet<>();
    List<Domain> domains = getDomainsList(client);
    for (Domain domain : domains) {
      result.add(domain.getSpec().getDomainUID());
    }
    return result;
  }

  private List<Domain> getDomainsList(ClientHolder client) {
    Collection<List<Domain>> c = new ArrayList<List<Domain>>();
    try {
      for (String ns : targetNamespaces) {
        DomainList dl = client.callBuilder().listDomain(ns);
        
        if (dl != null) {
          c.add(dl.getItems());
        }
      }
      return c.stream().flatMap(Collection::stream).collect(Collectors.toList());
    } catch (ApiException e) {
      throw handleApiException(e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isDomainUID(String domainUID) {
    LOGGER.entering(domainUID);
    ClientHolder client = null;
    boolean result = false;
    try {
      client = clientHelper.take();
      result = isDomainUID(client, domainUID);
    } finally {
      recycleClient(clientHelper, client);
    }
    LOGGER.exiting(result);
    return result;
  }

  private boolean isDomainUID(ClientHolder client, String domainUID) {
    authorize(client, null, Operation.list);
    return getDomainUIDs(client).contains(domainUID);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<String> getClusters(String domainUID) {
    LOGGER.entering(domainUID);
    ClientHolder client = null;
    Set<String> result = null;
    try {
      client = clientHelper.take();
      result = getClusters(client, domainUID);
    } finally {
      recycleClient(clientHelper, client);
    }
    LOGGER.exiting(result);
    return result;
  }

  private Set<String> getClusters(ClientHolder client, String domainUID) {
    if (!isDomainUID(client, domainUID)) {
      throw new AssertionError(formatMessage(MessageKeys.INVALID_DOMAIN_UID, domainUID));
    }
    authorize(client, domainUID, Operation.get);

    // Get list of WLS Configured Clusters defined for the corresponding WLS Domain identified by Domain UID
    Domain domain = findDomain(client, domainUID);
    String namespace = getNamespace(client, domainUID);
    String adminServerServiceName = getAdminServerServiceName(domain);
    String adminSecretName = getAdminServiceSecretName(domain);
    Map<String, WlsClusterConfig> wlsClusterConfigs = getWLSConfiguredClusters(client, namespace, adminServerServiceName, adminSecretName);
    return wlsClusterConfigs.keySet();
  }

  private static String getAdminServerServiceName(Domain domain) {
    String adminServerServiceName = domain.getSpec().getDomainUID() + "-" + domain.getSpec().getAsName();
    return CallBuilder.toDNS1123LegalName(adminServerServiceName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isCluster(String domainUID, String cluster) {
    LOGGER.entering(domainUID, cluster);
    ClientHolder client = null;
    boolean result = false;
    try {
      client = clientHelper.take();
      result = isCluster(client, domainUID, cluster);
    } finally {
      recycleClient(clientHelper, client);
    }
    LOGGER.exiting(result);
    return result;
  }

  private boolean isCluster(ClientHolder client, String domainUID, String cluster) {
    authorize(client, domainUID, cluster, Operation.list);
    return getClusters(client, domainUID).contains(cluster);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void scaleCluster(String domainUID, String cluster, int managedServerCount) {
    LOGGER.entering(domainUID, cluster, managedServerCount);

    if (managedServerCount < 0) {
      throw createWebApplicationException(Status.BAD_REQUEST, MessageKeys.INVALID_MANAGE_SERVER_COUNT, managedServerCount);
    }

    ClientHolder client = null;
    try {
      client = clientHelper.take();
      scaleCluster(client, domainUID, cluster, managedServerCount);
    } finally {
      recycleClient(clientHelper, client);
    }
    LOGGER.exiting();
  }

  private void scaleCluster(ClientHolder client, String domainUID, String cluster, int managedServerCount) {
    authorize(client, domainUID, cluster, Operation.update);

    List<Domain> domains = getDomainsList(client);
    Domain domain = findDomain(domainUID, domains);

    String namespace = getNamespace(domainUID, domains);

    verifyWLSConfiguredClusterCapacity(client, namespace, domain, cluster, managedServerCount);

    updateReplicasForDomain(client, namespace, domain, cluster, managedServerCount);
  }

  private void updateReplicasForDomain(ClientHolder client, String namespace, Domain domain, String cluster, int managedServerCount) {
    // Capacity of configured cluster is valid for scaling
    // Set replicas value on corresponding ClusterStartup (if defined)
    // or on the Domain level replicas value for cluster not defined in a ClusterStartup
    String domainUID = domain.getSpec().getDomainUID();
    boolean domainModified = false;
    ClusterStartup clusterStartup = getClusterStartup(domain, cluster);
    int currentReplicasCount = clusterStartup != null ? clusterStartup.getReplicas() : domain.getSpec().getReplicas();

    if (managedServerCount != currentReplicasCount) {
      if (clusterStartup != null) {
        // set replica value on corresponding ClusterStartup
        clusterStartup.setReplicas(managedServerCount);
        domainModified = true;
      } else if (StartupControlConstants.AUTO_STARTUPCONTROL.equals(domain.getSpec().getStartupControl())){
        // set replica on Domain for cluster not defined in ClusterStartup
        domain.getSpec().setReplicas(managedServerCount);
        domainModified = true;
      } else {
        // WebLogic Cluster is not defined in ClusterStartup AND Startup Control is not spec'd as AUTO
        // so scaling will not occur since Domain.spec.Replicas property will be ignored.
        throw createWebApplicationException(Status.BAD_REQUEST, MessageKeys.SCALING_AUTO_CONTROL_AUTO, cluster);
      }
    }

    if (domainModified) {
      try {
        // Write out the Domain with updated replica values
        // TODO: Can we patch instead of replace?
        client.callBuilder().replaceDomain(domainUID, namespace, domain);
      } catch (ApiException e) {
        LOGGER.finer("Unexpected exception when updating Domain " + domainUID + " in namespace " + namespace, e);
        throw new WebApplicationException(e.getMessage());
      }
    }
  }

  private void verifyWLSConfiguredClusterCapacity(ClientHolder client, String namespace, Domain domain, String cluster, int managedServerCount) {
    // Query WebLogic Admin Server for current configured WebLogic Cluster size
    // and verify we have enough configured managed servers to auto-scale
    String adminServerServiceName = getAdminServerServiceName(domain);
    String adminSecretName = getAdminServiceSecretName(domain);
    int clusterSize = getWLSConfiguredClusterSize(client, adminServerServiceName, cluster, namespace, adminSecretName);
    if (managedServerCount > clusterSize) {
      throw createWebApplicationException(Status.BAD_REQUEST, MessageKeys.SCALE_COUNT_GREATER_THAN_CONFIGURED, managedServerCount, clusterSize, cluster, cluster);
    }
  }

  private static String getAdminServiceSecretName(Domain domain) {
    return domain.getSpec().getAdminSecret() == null ? null : domain.getSpec().getAdminSecret().getName();
  }

  private ClusterStartup getClusterStartup(Domain domain, String cluster) {
    List<ClusterStartup> clusterStartups = domain.getSpec().getClusterStartup();
    for (ClusterStartup clusterStartup : clusterStartups) {
      String clusterName = clusterStartup.getClusterName();
      if (cluster.equals(clusterName)) {
        return clusterStartup;
      }
    }

    return null;
  }

  private int getWLSConfiguredClusterSize(ClientHolder client, String adminServerServiceName, String cluster, String namespace, String adminSecretName) {
    WlsRetriever wlsConfigRetriever = WlsRetriever.create(client.getHelper(), namespace, adminServerServiceName, adminSecretName);
    WlsDomainConfig wlsDomainConfig = wlsConfigRetriever.readConfig(principal);
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig(cluster);
    return wlsClusterConfig.getClusterSize();
  }

  private Map<String, WlsClusterConfig> getWLSConfiguredClusters(ClientHolder client, String namespace, String adminServerServiceName,  String adminSecretName) {
    WlsRetriever wlsConfigRetriever = WlsRetriever.create(client.getHelper(), namespace, adminServerServiceName, adminSecretName);
    WlsDomainConfig wlsDomainConfig = wlsConfigRetriever.readConfig(principal);
    return wlsDomainConfig.getClusterConfigs();
  }

  private Domain findDomain(ClientHolder client, String domainUID) {
    List<Domain> domains = getDomainsList(client);
    return findDomain(domainUID, domains);
  }

  private Domain findDomain(String domainUID, List<Domain> domains) {
    for (Domain domain : domains) {
      if (domainUID.equals(domain.getSpec().getDomainUID())) {
        return domain;
      }
    }

    throw createWebApplicationException(Status.NOT_FOUND, MessageKeys.MATCHING_DOMAIN_NOT_FOUND, domainUID);
  }

  private WebApplicationException handleApiException(ApiException e) {
    // TBD - what about e.getResponseHeaders?
    return createWebApplicationException(e.getCode(), e.getResponseBody());
  }

  private WebApplicationException createWebApplicationException(Status status, String msgId, Object... params) {
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

  private void recycleClient(ClientHelper clientHelper, ClientHolder client) {
    if (client != null) {
      clientHelper.recycle(client);
    }
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
