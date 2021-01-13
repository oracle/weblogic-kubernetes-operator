// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.json.Json;
import javax.json.JsonPatchBuilder;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1TokenReviewStatus;
import io.kubernetes.client.openapi.models.V1UserInfo;
import oracle.kubernetes.operator.Main;
import oracle.kubernetes.operator.TuningParameters;
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
import oracle.kubernetes.operator.rest.model.DomainAction;
import oracle.kubernetes.operator.rest.model.DomainActionType;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.weblogic.domain.model.Domain;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;
import static oracle.kubernetes.operator.logging.MessageKeys.INVALID_DOMAIN_UID;

/**
 * RestBackendImpl implements the backend of the WebLogic operator REST api by making calls to
 * Kubernetes and WebLogic. A separate instance is created for each REST request since we need to
 * hold some per-request state.
 */
public class RestBackendImpl implements RestBackend {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String NEW_CLUSTER_REPLICAS =
      "{'clusterName':'%s','replicas':%d}".replaceAll("'", "\"");
  private static final String INITIAL_VERSION = "1";

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"}) // used by unit test
  private static TopologyRetriever INSTANCE =
      (String ns, String domainUid) -> {
        Scan s = ScanCache.INSTANCE.lookupScan(ns, domainUid);
        if (s != null) {
          return s.getWlsDomainConfig();
        }
        return null;
      };

  private final AuthenticationProxy atn = new AuthenticationProxy();
  private AuthorizationProxy atz = new AuthorizationProxy();
  private final String principal;
  private final Supplier<Collection<String>> domainNamespaces;
  private V1UserInfo userInfo;
  private final CallBuilder callBuilder;

  /**
   * Construct a RestBackendImpl that is used to handle one WebLogic operator REST request.
   *  @param principal is the name of the Kubernetes user to use when calling the Kubernetes REST
   *     api.
   * @param accessToken is the access token of the Kubernetes service account of the client calling
   *     the WebLogic operator REST api.
   * @param domainNamespaces a function that returns the names of the managed Kubernetes namepaces.
   */
  RestBackendImpl(String principal, String accessToken, Supplier<Collection<String>> domainNamespaces) {
    this.domainNamespaces = domainNamespaces;
    this.principal = principal;
    userInfo = authenticate(accessToken);
    callBuilder = userInfo != null ? new CallBuilder() :
        new CallBuilder().withAuthentication(accessToken);
    LOGGER.exiting();
  }

  private void authorize(String domainUid, Operation operation) {
    LOGGER.entering(domainUid, operation);
    if (!authenticateWithTokenReview()) {
      return;
    }
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
    return getDomain(domainUid).map(Domain::getMetadata).map(V1ObjectMeta::getNamespace).orElse(null);
  }


  private V1UserInfo authenticate(String accessToken) {
    LOGGER.entering();
    if (!authenticateWithTokenReview()) {
      return null;
    }
    V1TokenReviewStatus status = atn.check(principal, accessToken,
        Main.isDedicated() ? getOperatorNamespace() : null);
    if (status == null) {
      throw new AssertionError(LOGGER.formatMessage(MessageKeys.NULL_TOKEN_REVIEW_STATUS));
    }
    String error = status.getError();
    if (error != null) {
      WebApplicationException e = createWebApplicationException(Status.UNAUTHORIZED, error);
      LOGGER.throwing(e);
      throw e;
    }
    if (isNotAuthenticated(status)) {
      // don't know why the user didn't get authenticated
      WebApplicationException e = createWebApplicationException(Status.UNAUTHORIZED, null);
      LOGGER.throwing(e);
      throw e;
    }
    userInfo = status.getUser();
    if (userInfo == null) {
      throw new AssertionError(LOGGER.formatMessage(MessageKeys.NULL_USER_INFO, status));
    }
    LOGGER.exiting(userInfo);
    return userInfo;
  }

  private boolean isNotAuthenticated(@Nonnull V1TokenReviewStatus status) {
    return !Boolean.TRUE.equals(status.getAuthenticated());
  }

  @Override
  public Set<String> getDomainUids() {
    authorize(null, Operation.list);

    return getDomainStream().map(Domain::getDomainUid).collect(Collectors.toSet());
  }

  private Stream<Domain> getDomainStream() {
    return domainNamespaces.get().stream().map(this::getDomains).flatMap(Collection::stream);
  }

  private List<Domain> getDomains(String ns) {
    try {
      return callBuilder.listDomain(ns).getItems();
    } catch (ApiException e) {
      throw handleApiException(e);
    }
  }

  @Override
  public boolean isDomainUid(String domainUid) {
    return getDomain(domainUid).isPresent();
  }

  @Override
  public void performDomainAction(String domainUid, DomainAction params) {
    verifyDomain(domainUid);
    authorize(domainUid, Operation.update);

    switch (Optional.ofNullable(params.getAction()).orElse(DomainActionType.UNKNOWN)) {
      case INTROSPECT:
        introspect(domainUid);
        break;
      case RESTART:
        restartDomain(domainUid);
        break;
      default:
        throw new WebApplicationException(Status.BAD_REQUEST);
    }
  }

  private void verifyDomain(String domainUid) {
    if (!isDomainUid(domainUid)) {
      throw new WebApplicationException(LOGGER.formatMessage(INVALID_DOMAIN_UID, domainUid), Status.BAD_REQUEST);
    }
  }

  private void introspect(String domainUid) {
    forDomainDo(domainUid, this::markForIntrospection);
  }

  private void markForIntrospection(Domain domain) {
    updateVersionField(domain, domain.getIntrospectVersion(), "/spec/introspectVersion");
  }

  private String nextVersion(String version) {
    try {
      return Integer.toString(Integer.parseInt(version) + 1);
    } catch (NumberFormatException e) {
      return INITIAL_VERSION;
    }
  }

  private void restartDomain(String domainUid) {
    forDomainDo(domainUid, this::markDomainForRestart);
  }

  private void markDomainForRestart(Domain domain) {
    updateVersionField(domain, domain.getRestartVersion(), "/spec/restartVersion");
  }

  private void updateVersionField(Domain domain, String version, String fieldPath) {
    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    Optional.ofNullable(version).ifPresentOrElse(
        v -> patchBuilder.replace(fieldPath, nextVersion(v)),
        () -> patchBuilder.add(fieldPath, INITIAL_VERSION));

    patchDomain(domain, patchBuilder);
  }

  private void forDomainDo(String domainUid, Consumer<Domain> consumer) {
    if (domainUid == null) {
      throw new AssertionError(LOGGER.formatMessage(MessageKeys.NULL_DOMAIN_UID));
    }

    getDomain(domainUid).ifPresentOrElse(consumer, () -> reportNotFound(domainUid));
  }

  private void reportNotFound(String domainUid) {
    throw createWebApplicationException(Status.NOT_FOUND, MessageKeys.MATCHING_DOMAIN_NOT_FOUND, domainUid);
  }

  private Optional<Domain> getDomain(String domainUid) {
    authorize(null, Operation.list);
    
    return getDomainStream().filter(domain -> domainUid.equals(domain.getDomainUid())).findFirst();
  }

  @Override
  public Set<String> getClusters(String domainUid) {
    LOGGER.entering(domainUid);
    verifyDomain(domainUid);
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
    forDomainDo(domainUid, d -> performScaling(d, cluster, managedServerCount));
    LOGGER.exiting();
  }

  private void performScaling(Domain domain, String cluster, int managedServerCount) {
    verifyWlsConfiguredClusterCapacity(domain, cluster, managedServerCount);
    patchClusterReplicas(domain, cluster, managedServerCount);
  }

  private void patchClusterReplicas(Domain domain, String cluster, int replicas) {
    if (replicas == domain.getReplicaCount(cluster)) {
      return;
    }

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    int index = getClusterIndex(domain, cluster);
    if (index < 0) {
      patchBuilder.add("/spec/clusters/0", String.format(NEW_CLUSTER_REPLICAS, cluster, replicas));
    } else {
      patchBuilder.replace("/spec/clusters/" + index + "/replicas", replicas);
    }

    patchDomain(domain, patchBuilder);
  }

  private void patchDomain(Domain domain, JsonPatchBuilder patchBuilder) {
    try {
      callBuilder
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
    for (String ns : domainNamespaces.get()) {
      WlsDomainConfig config = INSTANCE.getWlsDomainConfig(ns, domainUid);
      if (config != null) {
        return config;
      }
    }
    return new WlsDomainConfig(null);
  }

  private WebApplicationException handleApiException(ApiException e) {
    // TBD - what about e.getResponseHeaders?
    return createWebApplicationException(e.getCode(), e.getResponseBody());
  }

  private WebApplicationException createWebApplicationException(
      Status status, String msgId, Object... params) {
    String msg = LOGGER.formatMessage(msgId, params);
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

  protected boolean authenticateWithTokenReview() {
    return "true".equalsIgnoreCase(Optional.ofNullable(TuningParameters.getInstance().get("tokenReviewAuthentication"))
        .orElse("false"));
  }

  V1UserInfo getUserInfo() {
    return userInfo;
  }

  // Intended for unit tests
  RestBackendImpl withAuthorizationProxy(AuthorizationProxy authorizationProxy) {
    this.atz = authorizationProxy;
    return this;
  }

  CallBuilder getCallBuilder() {
    return this.callBuilder;
  }

  interface TopologyRetriever {
    WlsDomainConfig getWlsDomainConfig(String ns, String domainUid);
  }
}
