// Copyright (c) 2017, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.google.gson.Gson;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.V1TokenReviewStatus;
import io.kubernetes.client.openapi.models.V1UserInfo;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.OperatorMain;
import oracle.kubernetes.operator.helpers.AuthenticationProxy;
import oracle.kubernetes.operator.helpers.AuthorizationProxy;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Resource;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Scope;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.http.rest.backend.RestBackend;
import oracle.kubernetes.operator.http.rest.model.DomainAction;
import oracle.kubernetes.operator.http.rest.model.DomainActionType;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;

import static oracle.kubernetes.common.logging.MessageKeys.INVALID_DOMAIN_UID;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;

/**
 * RestBackendImpl implements the backend of the WebLogic operator REST api by making calls to
 * Kubernetes and WebLogic. A separate instance is created for each REST request since we need to
 * hold some per-request state.
 */
public class RestBackendImpl implements RestBackend {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String INITIAL_VERSION = "1";

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"}) // used by unit test
  private static TopologyRetriever instance =
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
  }

  private void authorize(String domainUid, Operation operation) {
    if (!useAuthenticateWithTokenReview()) {
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
              Scope.CLUSTER,
              null);
    } else {
      authorized =
          atz.check(
              userInfo.getUsername(),
              userInfo.getGroups(),
              operation,
              Resource.DOMAINS,
              domainUid,
              Scope.NAMESPACE,
              getNamespace(domainUid));
    }
    if (!authorized) {
      WebApplicationException e = createWebApplicationException(Status.FORBIDDEN, null);
      LOGGER.throwing(e);
      throw e;
    }
  }

  private String getNamespace(String domainUid) {
    return getDomain(domainUid).map(DomainResource::getMetadata).map(V1ObjectMeta::getNamespace).orElse(null);
  }


  private V1UserInfo authenticate(String accessToken) {
    if (!useAuthenticateWithTokenReview()) {
      return null;
    }
    V1TokenReviewStatus status = atn.check(principal, accessToken,
        OperatorMain.isDedicated() ? getOperatorNamespace() : null);
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
    return userInfo;
  }

  private boolean isNotAuthenticated(@Nonnull V1TokenReviewStatus status) {
    return !Boolean.TRUE.equals(status.getAuthenticated());
  }

  @Override
  public Set<String> getDomainUids() {
    authorize(null, Operation.LIST);

    return getDomainStream().map(DomainResource::getDomainUid).collect(Collectors.toSet());
  }

  private Stream<DomainResource> getDomainStream() {
    return domainNamespaces.get().stream().map(this::getDomains).flatMap(Collection::stream);
  }

  private Stream<ClusterResource> getClusterStream() {
    return domainNamespaces.get().stream().map(this::getClusterResources).flatMap(Collection::stream);
  }

  private List<DomainResource> getDomains(String ns) {
    try {
      return callBuilder.listDomain(ns).getItems();
    } catch (ApiException e) {
      throw handleApiException(e);
    }
  }

  private List<ClusterResource> getClusterResources(String ns) {
    try {
      return callBuilder.listCluster(ns).getItems();
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
    authorize(domainUid, Operation.UPDATE);

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

  private void markForIntrospection(DomainResource domain) {
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

  private void markDomainForRestart(DomainResource domain) {
    updateVersionField(domain, domain.getRestartVersion(), "/spec/restartVersion");
  }

  private void updateVersionField(DomainResource domain, String version, String fieldPath) {
    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    Optional.ofNullable(version).ifPresentOrElse(
        v -> patchBuilder.replace(fieldPath, nextVersion(v)),
        () -> patchBuilder.add(fieldPath, INITIAL_VERSION));

    patchDomain(domain, patchBuilder);
  }

  private void forDomainDo(String domainUid, Consumer<DomainResource> consumer) {
    if (domainUid == null) {
      throw new AssertionError(LOGGER.formatMessage(MessageKeys.NULL_DOMAIN_UID));
    }

    getDomain(domainUid).ifPresentOrElse(consumer, () -> reportNotFound(domainUid));
  }

  private void reportNotFound(String domainUid) {
    throw createWebApplicationException(Status.NOT_FOUND, MessageKeys.MATCHING_DOMAIN_NOT_FOUND, domainUid);
  }

  private Optional<DomainResource> getDomain(String domainUid) {
    authorize(null, Operation.LIST);
    
    return getDomainStream().filter(domain -> domainUid.equals(domain.getDomainUid())).findFirst();
  }

  private Optional<ClusterResource> getClusterResource(DomainResource domain, String clusterName) {
    authorize(null, Operation.LIST);

    List<String> referencedClusterResources = getReferencedClusterResourceNames(domain);
    return getClusterStream()
        .filter(c -> isInSameNamespace(c, domain))
        .filter(c -> isReferencedByDomain(c, referencedClusterResources))
        .filter(c -> isMatchingClusterResource(clusterName, c))
        .findFirst();
  }

  private boolean isInSameNamespace(ClusterResource clusterResource, DomainResource domain) {
    return Objects.equals(clusterResource.getNamespace(), domain.getNamespace());
  }

  private boolean isReferencedByDomain(ClusterResource clusterResource, List<String> referencedClusterResources) {
    return Optional.ofNullable(referencedClusterResources)
        .map(l -> l.contains(clusterResource.getClusterResourceName()))
        .orElse(false);
  }

  private boolean isMatchingClusterResource(String clusterName, ClusterResource clusterResource) {
    return clusterName.equals(clusterResource.getClusterName());
  }

  @Override
  public Set<String> getClusters(String domainUid) {
    verifyDomain(domainUid);
    authorize(domainUid, Operation.GET);

    // Get list of WLS Configured Clusters defined for the corresponding WLS Domain identified by
    // Domain UID
    Map<String, WlsClusterConfig> wlsClusterConfigs = getWlsConfiguredClusters(domainUid);
    return wlsClusterConfigs.keySet();
  }

  @Override
  public boolean isCluster(String domainUid, String cluster) {
    authorize(domainUid, Operation.LIST);
    return getClusters(domainUid).contains(cluster);
  }

  @Override
  public void scaleCluster(String domainUid, String cluster, int managedServerCount) {
    if (managedServerCount < 0) {
      throw createWebApplicationException(
          Status.BAD_REQUEST, MessageKeys.INVALID_MANAGE_SERVER_COUNT, managedServerCount);
    }

    authorize(domainUid, Operation.UPDATE);
    forDomainDo(domainUid, d -> performScaling(d, cluster, managedServerCount));
  }

  private void performScaling(DomainResource domain, String cluster, int managedServerCount) {
    verifyWlsConfiguredClusterCapacity(domain.getDomainUid(), cluster, managedServerCount);

    getClusterResource(domain, cluster)
        .ifPresentOrElse(cr -> patchClusterResourceReplicas(cr, managedServerCount),
            () -> createClusterIfNecessary(domain, cluster, managedServerCount));
  }

  private List<String> getReferencedClusterResourceNames(DomainResource domain) {
    return domain.getSpec().getClusters().stream()
        .map(V1LocalObjectReference::getName)
        .collect(Collectors.toList());
  }

  private void createClusterIfNecessary(DomainResource domain, String cluster, int managedServerCount) {
    if (managedServerCount == Optional.of(domain.getSpec()).map(DomainSpec::getReplicas).orElse(0)) {
      return;
    }
    createCluster(domain, cluster, managedServerCount);
  }

  private void patchClusterResourceReplicas(ClusterResource cluster, int replicas) {
    Integer currentReplicas = cluster.getSpec().getReplicas();
    if (currentReplicas != null && replicas == currentReplicas) {
      return;
    }

    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    if (currentReplicas == null) {
      patchBuilder.add("/spec/replicas", replicas);
    } else {
      patchBuilder.replace("/spec/replicas", replicas);
    }
    patchCluster(cluster, patchBuilder);
  }

  private void patchCluster(ClusterResource cluster, JsonPatchBuilder patchBuilder) {
    try {
      callBuilder
              .patchCluster(
                      cluster.getClusterResourceName(), cluster.getNamespace(),
                      new V1Patch(patchBuilder.build().toString()));
    } catch (ApiException e) {
      throw handleApiException(e);
    }
  }

  private void createCluster(DomainResource domain, String clusterName, int replicas) {
    V1ObjectMeta domainMetadata = domain.getMetadata();
    String namespace = domainMetadata.getNamespace();
    ClusterResource cluster = new ClusterResource()
        .withMetadata(new V1ObjectMeta()
            .namespace(namespace)
            .name(domain.getDomainUid() + "-" + clusterName)
            .putLabelsItem("weblogic.createdByOperator", "true")
            .addOwnerReferencesItem(new V1OwnerReference()
                .apiVersion(domain.getApiVersion())
                .kind(domain.getKind())
                .name(domainMetadata.getName())
                .uid(domainMetadata.getUid())
                .controller(true)))
        .withReplicas(replicas);
    try {
      callBuilder.createCluster(namespace, cluster);
    } catch (ApiException e) {
      throw handleApiException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object createOrReplaceCluster(Map<String, Object> cluster) {
    Map<String, Object> metadata = Optional.ofNullable((Map<String, Object>) cluster.get("metadata"))
        .orElse(Collections.emptyMap());
    String namespace = (String) metadata.getOrDefault("namespace", "default");
    String name = (String) metadata.get("name");
    Object currentCluster = null;
    try {
      currentCluster = callBuilder.readClusterUntyped(name, namespace);
    } catch (ApiException apiException) {
      if (apiException.getCode() != KubernetesConstants.HTTP_NOT_FOUND) {
        throw handleApiException(apiException);
      }
    }

    if (currentCluster == null) {
      try {
        Object result = callBuilder.createClusterUntyped(namespace, cluster);
        if (LOGGER.isFineEnabled()) {
          LOGGER.fine("Created Cluster: " + result);
        }
        return result;
      } catch (ApiException f) {
        throw handleApiException(f);
      }
    }

    metadata.put("resourceVersion", Optional.ofNullable((Map<String, Object>) ((Map<String, Object>) currentCluster)
        .get("metadata")).map(m -> m.get("resourceVersion")).orElse(null));
    try {
      Object result = callBuilder.replaceClusterUntyped(name, namespace, cluster);
      if (LOGGER.isFineEnabled()) {
        LOGGER.fine("Replaced Cluster: " + result);
      }
      return result;
    } catch (ApiException e) {
      throw handleApiException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> listClusters(String namespace) {
    try {
      Map<String, Object> clusterList = (Map<String, Object>) callBuilder.listClusterUntyped(namespace);
      return Optional.of(clusterList).map(cl -> (List<Map<String, Object>>) cl.get("items"))
          .orElse(Collections.emptyList());
    } catch (ApiException e) {
      throw handleApiException(e);
    }
  }


  private void patchDomain(DomainResource domain, JsonPatchBuilder patchBuilder) {
    try {
      callBuilder
          .patchDomain(
              domain.getMetadata().getName(), domain.getMetadata().getNamespace(),
              new V1Patch(patchBuilder.build().toString()));
    } catch (ApiException e) {
      throw handleApiException(e);
    }
  }

  private void verifyWlsConfiguredClusterCapacity(String domainUid, String cluster, int requestedSize) {
    // Query WebLogic Admin Server for current configured WebLogic Cluster size
    // and verify we have enough configured managed servers to auto-scale
    WlsClusterConfig wlsClusterConfig = getWlsClusterConfig(domainUid, cluster);

    // Verify the current configured cluster size
    int clusterSize = wlsClusterConfig.getClusterSize();
    if (requestedSize > clusterSize) {
      throw createWebApplicationException(
              Status.BAD_REQUEST,
              MessageKeys.SCALE_COUNT_GREATER_THAN_CONFIGURED,
              requestedSize,
              clusterSize,
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
      WlsDomainConfig config = instance.getWlsDomainConfig(ns, domainUid);
      if (config != null) {
        return config;
      }
    }
    return new WlsDomainConfig(null);
  }

  private WebApplicationException handleApiException(ApiException e) {
    LOGGER.throwing(e);
    return createWebApplicationException(e.getCode(),
            Optional.ofNullable(new Gson().fromJson(e.getResponseBody(), V1Status.class))
                    .map(this::messageFromStatus).orElse(null));
  }

  private String messageFromStatus(V1Status status) {
    return status.getMessage();
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
    return new WebApplicationException(
            Optional.ofNullable(msg).map(m -> Response.status(status, m)).orElse(Response.status(status)).build());
  }

  protected boolean useAuthenticateWithTokenReview() {
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
