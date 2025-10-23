// Copyright (c) 2019, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.Serial;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.common.KubernetesType;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.EventsV1Event;
import io.kubernetes.client.openapi.models.EventsV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Scale;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfiguration;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfigurationList;
import io.kubernetes.client.openapi.models.VersionInfo;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.CreateOptions;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import io.kubernetes.client.util.generic.options.GetOptions;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import io.kubernetes.client.util.generic.options.UpdateOptions;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonPatch;
import jakarta.json.JsonStructure;
import oracle.kubernetes.operator.calls.KubernetesApi;
import oracle.kubernetes.operator.calls.KubernetesApiFactory;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.calls.ResponseStep;
import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.calls.RetryStrategyFactory;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

@SuppressWarnings("WeakerAccess")
public class KubernetesTestSupport extends FiberTestSupport {
  public static final VersionInfo TEST_VERSION_INFO = new VersionInfo().major("1").minor("18").gitVersion("0");
  public static final String CONFIG_MAP = "ConfigMap";
  public static final String CUSTOM_RESOURCE_DEFINITION = "CRD";
  public static final String NAMESPACE = "Namespace";
  public static final String CLUSTER = "Cluster";
  public static final String DOMAIN = "Domain";
  public static final String EVENT = "Event";
  public static final String JOB = "Job";
  public static final String PV = "PersistentVolume";
  public static final String PVC = "PersistentVolumeClaim";
  public static final String POD = "Pod";
  public static final String PODDISRUPTIONBUDGET = "PodDisruptionBudget";
  public static final String PODLOG = "PodLog";
  public static final String SECRET = "Secret";
  public static final String SERVICE = "Service";
  public static final String SCALE = "Scale";
  public static final String SUBJECT_ACCESS_REVIEW = "SubjectAccessReview";
  public static final String SELF_SUBJECT_ACCESS_REVIEW = "SelfSubjectAccessReview";
  public static final String SELF_SUBJECT_RULES_REVIEW = "SelfSubjectRulesReview";
  public static final String TOKEN_REVIEW = "TokenReview";
  public static final String VALIDATING_WEBHOOK_CONFIGURATION = "ValidatingWebhookConfiguration";

  public static final String[] SUB_RESOURCE_SUFFIXES = {"Status", "Metadata"};

  private static final String PATH_PATTERN = "\\w+(?:.\\w+)*";
  private static final String OP_PATTERN = "=|==|!=";
  private static final String VALUE_PATTERN = ".*";
  private static final Pattern FIELD_PATTERN
        = Pattern.compile("(" + PATH_PATTERN + ")(" + OP_PATTERN + ")(" + VALUE_PATTERN + ")");

  private final Map<String, DataRepository<? extends KubernetesType>> repositories = new HashMap<>();
  private final Map<Class<?>, String> dataTypes = new HashMap<>();
  private Failure failure;
  private AfterCallAction afterCallAction;
  private long resourceVersion;
  private int numCalls;
  private boolean addCreationTimestamp;
  private EmptyResponse emptyResponse;
  private VersionInfo versionInfo = TEST_VERSION_INFO;
  private RetryStrategy retryStrategy;

  /**
   * Installs a factory into CallBuilder to use canned responses.
   *
   * @return a memento which can be used to restore the production factory
   */
  public Memento install() throws NoSuchFieldException {
    support(CUSTOM_RESOURCE_DEFINITION, V1CustomResourceDefinition.class);
    supportCreateOnlyNoMetadata(SELF_SUBJECT_ACCESS_REVIEW, V1SelfSubjectAccessReview.class);
    supportCreateOnlyNoMetadata(SELF_SUBJECT_RULES_REVIEW, V1SelfSubjectRulesReview.class);
    supportCreateOnlyNoMetadata(SUBJECT_ACCESS_REVIEW, V1SubjectAccessReview.class);
    supportCreateOnlyNoMetadata(TOKEN_REVIEW, V1TokenReview.class);
    support(PV, V1PersistentVolume.class, this::createPvList);
    support(NAMESPACE, V1Namespace.class, this::createNamespaceList);
    support(VALIDATING_WEBHOOK_CONFIGURATION,
        V1ValidatingWebhookConfiguration.class, this::createValidatingWebhookConfigurationList);

    supportNamespaced(CONFIG_MAP, V1ConfigMap.class, this::createConfigMapList);
    supportNamespaced(CLUSTER, ClusterResource.class, this::createClusterList).withStatusSubresource();
    supportNamespaced(DOMAIN, DomainResource.class, this::createDomainList).withStatusSubresource();
    supportNamespaced(EVENT, EventsV1Event.class, this::createEventList);
    supportNamespaced(JOB, V1Job.class, this::createJobList);
    supportNamespaced(POD, V1Pod.class, this::createPodList);
    supportNamespaced(PODLOG, RequestBuilder.StringObject.class);
    supportNamespaced(PODDISRUPTIONBUDGET, V1PodDisruptionBudget.class, this::createPodDisruptionBudgetList);
    supportNamespaced(PVC, V1PersistentVolumeClaim.class, this::createPvcList);
    supportNamespaced(SECRET, V1Secret.class, this::createSecretList);
    supportNamespaced(SERVICE, V1Service.class, this::createServiceList);
    supportNamespaced(SCALE, V1Scale.class);

    return new Memento() {
      private final List<Memento> mementos = new ArrayList<>();

      {
        mementos.add(StaticStubSupport.install(
                RequestBuilder.class, "kubernetesApiFactory", new KubernetesApiFactoryImpl()));
        mementos.add(StaticStubSupport.install(
                ResponseStep.class, "retryStrategyFactory", new RetryStrategyFactoryImpl()));
      }

      @Override
      public void revert() {
        mementos.forEach(Memento::revert);
      }

      @Override
      public <T> T getOriginalValue() {
        return null;
      }
    };
  }

  private class RetryStrategyFactoryImpl implements RetryStrategyFactory {
    @Override
    public RetryStrategy create(int maxRetryCount, Step retryStep) {
      return retryStrategy;
    }
  }

  public KubernetesTestSupport addRetryStrategy(RetryStrategy retryStrategy) {
    this.retryStrategy = retryStrategy;
    return this;
  }

  static V1ObjectMeta getMetadata(@Nonnull Object resource) {
    return KubernetesUtils.getResourceMetadata(resource);
  }

  private ClusterList createClusterList(List<ClusterResource> items) {
    return new ClusterList().withMetadata(createListMeta()).withItems(items);
  }

  private V1ConfigMapList createConfigMapList(List<V1ConfigMap> items) {
    return new V1ConfigMapList().metadata(createListMeta()).items(items);
  }

  private DomainList createDomainList(List<DomainResource> items) {
    return new DomainList().withMetadata(createListMeta()).withItems(items);
  }

  private EventsV1EventList createEventList(List<EventsV1Event> items) {
    return new EventsV1EventList().metadata(createListMeta()).items(items);
  }

  private V1PersistentVolumeList createPvList(List<V1PersistentVolume> items) {
    return new V1PersistentVolumeList().metadata(createListMeta()).items(items);
  }

  private V1PersistentVolumeClaimList createPvcList(List<V1PersistentVolumeClaim> items) {
    return new V1PersistentVolumeClaimList().metadata(createListMeta()).items(items);
  }

  private V1NamespaceList createNamespaceList(List<V1Namespace> items) {
    return new V1NamespaceList().metadata(createListMeta()).items(items);
  }

  private V1ValidatingWebhookConfigurationList createValidatingWebhookConfigurationList(
      List<V1ValidatingWebhookConfiguration> items) {
    return new V1ValidatingWebhookConfigurationList().metadata(createListMeta()).items(items);
  }

  private V1PodList createPodList(List<V1Pod> items) {
    return new V1PodList().metadata(createListMeta()).items(items);
  }

  private V1JobList createJobList(List<V1Job> items) {
    return new V1JobList().metadata(createListMeta()).items(items);
  }

  private V1SecretList createSecretList(List<V1Secret> items) {
    return new V1SecretList().metadata(createListMeta()).items(items);
  }

  private V1ServiceList createServiceList(List<V1Service> items) {
    return new V1ServiceList().metadata(createListMeta()).items(items);
  }

  private V1PodDisruptionBudgetList createPodDisruptionBudgetList(List<V1PodDisruptionBudget> items) {
    return new V1PodDisruptionBudgetList().metadata(createListMeta()).items(items);
  }

  private V1ListMeta createListMeta() {
    return new V1ListMeta().resourceVersion(Long.toString(++resourceVersion));
  }

  private <T extends KubernetesType> void support(String resourceName, Class<T> resourceClass) {
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, new DataRepository<>(resourceClass));
  }

  @SuppressWarnings("SameParameterValue")
  private <T extends KubernetesType> void support(
      String resourceName, Class<T> resourceClass, Function<List<T>, KubernetesListObject> toList) {
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, new DataRepository<>(resourceClass, toList));
  }

  private <T extends KubernetesType> void supportCreateOnlyNoMetadata(String resourceName, Class<T> resourceClass) {
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, new CreateOnlyNoMetadataDataRepository<>(resourceClass));
  }

  @SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
  private <T extends KubernetesType> NamespacedDataRepository<T> supportNamespaced(
      String resourceName, Class<T> resourceClass) {
    final NamespacedDataRepository<T> dataRepository = new NamespacedDataRepository<>(resourceClass, null);
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, dataRepository);
    return dataRepository;
  }

  private <T extends KubernetesType> NamespacedDataRepository<T> supportNamespaced(
      String resourceName, Class<T> resourceClass, Function<List<T>, KubernetesListObject> toList) {
    final NamespacedDataRepository<T> dataRepository = new NamespacedDataRepository<>(resourceClass, toList);
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, dataRepository);
    return dataRepository;
  }

  /**
   * Clears the number of calls made to Kubernetes.
   */
  public void clearNumCalls() {
    numCalls = 0;
  }

  /**
   * Returns the number of calls made to Kubernetes.
   * @return a non-negative integer
   */
  public int getNumCalls() {
    return numCalls;
  }

  public void setAddCreationTimestamp(boolean addCreationTimestamp) {
    this.addCreationTimestamp = addCreationTimestamp;
  }

  @SuppressWarnings("unchecked")
  private <T extends KubernetesType> DataRepository<T> selectRepository(String resourceType) {
    if (resourceType == null) {
      return null;
    }
    String key = resourceType;
    for (String suffix : SUB_RESOURCE_SUFFIXES) {
      if (key.endsWith(suffix)) {
        key = key.substring(0, key.length() - suffix.length());
        break;
      }
    }
    return (DataRepository<T>) repositories.get(key);
  }

  @SuppressWarnings("unchecked")
  public <T extends KubernetesType> List<T> getResources(String resourceType) {
    return ((DataRepository<T>) selectRepository(resourceType)).getResources();
  }

  /**
   * get resource with name.
   * @param resourceType resource type
   * @param name name
   * @param <T> type
   * @return resource
   */
  @SuppressWarnings("unchecked")
  public <T> T getResourceWithName(String resourceType, String name) {
    return (T)
        getResources(resourceType).stream()
            .filter(o -> name.equals(KubernetesUtils.getResourceName(o)))
            .findFirst()
            .orElse(null);
  }

  /**
   * define resources.
   * @param resources resources.
   * @param <T> type
   */
  @SafeVarargs
  public final <T extends KubernetesType> void defineResources(T... resources) {
    for (T resource : resources) {
      getDataRepository(resource).createResourceInNamespace(resource);
    }
  }

  /**
   * delete resources.
   * @param resources resources.
   * @param <T> type
   */
  @SafeVarargs
  public final <T extends KubernetesType> void deleteResources(T... resources) {
    for (T resource : resources) {
      getDataRepository(resource).deleteResourceInNamespace(resource);
    }
  }

  public void definePodLog(String name, String namespace, Object contents) {
    repositories.get(PODLOG).createResourceInNamespace(name, namespace,
            new RequestBuilder.StringObject(contents.toString()));
  }

  public void setVersionInfo(VersionInfo versionInfo) {
    this.versionInfo = versionInfo;
  }

  /**
   * Deletes the specified namespace and all resources in that namespace.
   * @param namespaceName the name of the namespace to delete
   */
  public void deleteNamespace(String namespaceName) {
    repositories.get(NAMESPACE).data.remove(namespaceName);
    repositories.values().stream()
          .filter(NamespacedDataRepository.class::isInstance)
          .forEach(r -> ((NamespacedDataRepository<?>) r).deleteNamespace(namespaceName));
  }

  @SuppressWarnings("unchecked")
  private <T extends KubernetesType> DataRepository<T> getDataRepository(T resource) {
    return (DataRepository<T>) repositories.get(dataTypes.get(resource.getClass()));
  }

  public void doOnCreate(String resourceType, Consumer<?> consumer) {
    selectRepository(resourceType).addCreateAction(consumer);
  }

  public void doOnUpdate(String resourceType, Consumer<?> consumer) {
    selectRepository(resourceType).addUpdateAction(consumer);
  }

  public void doOnDelete(String resourceType, Consumer<DeletionContext> consumer) {
    selectRepository(resourceType).addDeleteAction(consumer);
  }

  public record DeletionContext(String name, String namespace, Long gracePeriodSeconds) {

  }

  /**
   * Specifies that a read operation should fail if it matches the specified conditions. Applies to
   * namespaced resources and replaces any existing failure checks.
   *
   * @param resourceType the type of resource
   * @param name the name of the resource
   * @param namespace the namespace containing the resource
   * @param httpStatus the status to associate with the failure
   */
  public void failOnRead(String resourceType, String name, String namespace, int httpStatus) {
    failure = new Failure(Operation.read, resourceType, name, namespace, httpStatus);
  }

  /**
   * Specifies that a list operation should fail if it matches the specified conditions. Applies to
   * namespaced resources and replaces any existing failure checks.
   *
   * @param resourceType the type of resource
   * @param namespace the namespace containing the resource
   * @param httpStatus the status to associate with the failure
   */
  public void failOnList(String resourceType, String namespace, int httpStatus) {
    failure = new Failure(Operation.list, resourceType, null, namespace, httpStatus);
  }

  /**
   * Specifies that a create operation should fail if it matches the specified conditions. Applies to
   * namespaced resources and replaces any existing failure checks.
   *
   * @param resourceType the type of resource
   * @param namespace the namespace containing the resource
   * @param httpStatus the status to associate with the failure
   */
  public void failOnCreate(String resourceType, String namespace, int httpStatus) {
    failure = new Failure(Operation.create, resourceType, null, namespace, httpStatus);
  }

  /**
   * Specifies that a create operation should fail if it matches the specified conditions. Applies to
   * namespaced resources and replaces any existing failure checks.
   *
   * @param resourceType the type of resource
   * @param namespace the namespace containing the resource
   * @param status the Kubernetes error status
   * @param httpCode the HTTP return code
   */
  public void failOnCreate(String resourceType, String namespace, V1Status status, int httpCode) {
    failure = new Failure(Operation.create, resourceType, null, namespace, status, httpCode);
  }

  /**
   * Specifies that a replace operation should fail if it matches the specified conditions. Applies to
   * namespaced resources and replaces any existing failure checks.
   *
   * @param resourceType the type of resource
   * @param name the name of the resource
   * @param namespace the namespace containing the resource
   * @param httpStatus the status to associate with the failure
   */
  public void failOnReplace(String resourceType, String name, String namespace, int httpStatus) {
    failure = new Failure(Operation.replace, resourceType, name, namespace, httpStatus);
  }

  /**
   * Specifies that a replace operation should fail if it matches the specified conditions. Applies to
   * namespaced resources and replaces any existing failure checks.
   *
   * @param resourceType the type of resource
   * @param name the name of the resource
   * @param namespace the namespace containing the resource
   * @param httpStatus the status to associate with the failure
   */
  public void failOnReplaceStatus(String resourceType, String name, String namespace, int httpStatus) {
    failure = new Failure(Operation.replaceStatus, resourceType, name, namespace, httpStatus);
  }

  /**
   * Specifies that a delete operation should fail if it matches the specified conditions. Applies to
   * namespaced resources and replaces any existing failure checks.
   *
   * @param resourceType the type of resource
   * @param name the name of the resource
   * @param namespace the namespace containing the resource
   * @param httpStatus the status to associate with the failure
   */
  public void failOnDelete(String resourceType, String name, String namespace, int httpStatus) {
    failure = new Failure(Operation.delete, resourceType, name, namespace, httpStatus);
  }

  /**
   * Specifies that any operation should fail if it matches the specified conditions. Applies to
   * namespaced resources and replaces any existing failure checks.
   *
   * @param resourceType the type of resource
   * @param name the name of the resource
   * @param namespace the namespace containing the resource
   * @param httpStatus the status to associate with the failure
   */
  public void failOnResource(String resourceType, String name, String namespace, int httpStatus) {
    failure = new Failure(resourceType, name, namespace, httpStatus);
  }

  /**
   * Specifies that any operation should fail if it matches the specified conditions. Applies to
   * namespaced resources and replaces any existing failure checks.
   *
   * @param resourceType the type of resource
   * @param name the name of the resource
   * @param namespace the namespace containing the resource
   * @param status the kubernetes status to associate with the failure
   * @param httpCode the HTTP return code
   */
  public void failOnResource(@Nonnull String resourceType, String name, String namespace,
                             V1Status status, int httpCode) {
    failure = new Failure(resourceType, name, namespace, status, httpCode);
  }

  /**
   * Cancels the currently defined 'failure' condition established by the various 'failOnResource' methods.
   */
  public void cancelFailures() {
    failure = null;
  }

  /**
   * Specifies that status replacement operation should respond with a null result if it matches the specified
   * conditions. Applies to domain resources.
   *
   * @param resourceType the type of resource
   * @param name the name of the resource
   * @param namespace the namespace containing the resource
   */
  public void returnEmptyResult(String resourceType, String name, String namespace) {
    emptyResponse = new EmptyResponse(Operation.replaceStatus, resourceType, name, namespace);
  }

  /**
   * Specifies that a read operation should respond with a null result if it matches the specified conditions.
   * Applies to domain resources.
   *
   * @param resourceType the type of resource
   * @param name the name of the resource
   * @param namespace the namespace containing the resource
   */
  public void returnEmptyResultOnRead(String resourceType, String name, String namespace) {
    emptyResponse = new EmptyResponse(Operation.read, resourceType, name, namespace);
  }

  /**
   * Cancels the currently defined 'emptyresponse' condition established by the various 'returnEmptyResult' methods.
   */
  public void cancelEmptyResponse() {
    emptyResponse = null;
  }

  /**
   * Specifies an action to perform after completing the next matching invocation.
   * @param resourceType the type of resource
   * @param call the call string
   * @param action the action to perform
   */
  public void doAfterCall(@Nonnull String resourceType, @Nonnull String call, @Nonnull Runnable action) {
    afterCallAction = new AfterCallAction(resourceType, call, action);
  }

  @SuppressWarnings("unused")
  private enum Operation {
    create {
      @Override
      <T extends KubernetesType> KubernetesApiResponse<T> execute(CallContext<T> callContext,
                                                                  DataRepository<T> dataRepository) {
        return callContext.createResource(dataRepository);
      }
    },
    delete {
      @Override
      <T extends KubernetesType> KubernetesApiResponse<T> execute(CallContext<T> callContext,
                                                                  DataRepository<T> dataRepository) {
        return callContext.deleteResource(dataRepository);
      }
    },
    read {
      @Override
      <T extends KubernetesType> KubernetesApiResponse<T> execute(CallContext<T> callContext,
                                                                  DataRepository<T> dataRepository) {
        return callContext.readResource(dataRepository);
      }
    },
    readMetadata {
      @Override
      <T extends KubernetesType> KubernetesApiResponse<T> execute(CallContext<T> callContext,
                                                                  DataRepository<T> dataRepository) {
        return callContext.readMetadata(dataRepository);
      }
    },
    replace {
      @Override
      <T extends KubernetesType> KubernetesApiResponse<T> execute(CallContext<T> callContext,
                                                                  DataRepository<T> dataRepository) {
        return callContext.replaceResource(dataRepository);
      }
    },
    replaceStatus {
      @Override
      <T extends KubernetesType> KubernetesApiResponse<T> execute(CallContext<T> callContext,
                                                                  DataRepository<T> dataRepository) {
        return callContext.replaceResourceStatus(dataRepository);
      }
    },
    list {
      @Override
      <T extends KubernetesType> KubernetesApiResponse<T> execute(CallContext<T> callContext,
                                                                  DataRepository<T> dataRepository) {
        return callContext.listResources(callContext.getLimit(), callContext.getContinue(), dataRepository);
      }
    },
    patch {
      @Override
      <T extends KubernetesType> KubernetesApiResponse<T> execute(CallContext<T> callContext,
                                                                  DataRepository<T> dataRepository) {
        return callContext.patchResource(dataRepository);
      }
    },
    getVersion {
      @Override
      @SuppressWarnings("unchecked")
      <T extends KubernetesType> KubernetesApiResponse<T> execute(CallContext<T> callContext,
                                                                  DataRepository<T> dataRepository) {
        return (KubernetesApiResponse<T>) new KubernetesApiResponse<>(
            new RequestBuilder.VersionInfoObject(callContext.getVersionInfo()));
      }
    },
    deleteCollection {
      @Override
      <T extends KubernetesType> KubernetesApiResponse<T> execute(CallContext<T> callContext,
                                                                  DataRepository<T> dataRepository) {
        return callContext.deleteCollection(dataRepository);
      }
    };

    abstract <T extends KubernetesType> KubernetesApiResponse<T> execute(CallContext<T> callContext,
                                                                         DataRepository<T> dataRepository);
  }

  static class Failure {
    private final String resourceType;
    private final String name;
    private final String namespace;
    private final V1Status status;
    private final int httpCode;
    private Operation operation;

    public Failure(String resourceType, String name, String namespace, int httpCode) {
      this(resourceType, name, namespace, new V1Status().message("failure reported in test"), httpCode);
    }

    Failure(@Nonnull String resourceType, String name, String namespace, V1Status status, int httpCode) {
      this.resourceType = resourceType;
      this.name = name;
      this.namespace = namespace;
      this.status = status;
      this.httpCode = httpCode;
    }

    Failure(Operation operation, String resourceType, String name, String namespace, int httpCode) {
      this(resourceType, name, namespace, httpCode);
      this.operation = operation;
    }

    Failure(Operation operation, String resourceType, String name, String namespace, V1Status status, int httpCode) {
      this(resourceType, name, namespace, status, httpCode);
      this.operation = operation;
    }

    boolean matches(String resourceType, String resourceName, String resourceNamespace, Operation operation) {
      return this.resourceType.equals(resourceType)
          && (this.operation == null || this.operation == operation)
          && (name == null || Objects.equals(name, resourceName))
          && (namespace == null || Objects.equals(namespace, resourceNamespace));
    }

    <D extends KubernetesType> KubernetesApiResponse<D> getExceptionResponse() {
      return new KubernetesApiResponse<>(status, httpCode);
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
            .append("resourceType", resourceType)
            .append("name", name)
            .append("namespace", namespace)
            .append("operation", operation)
            .toString();
    }
  }

  static class EmptyResponse {
    private final String resourceType;
    private final String name;
    private final String namespace;
    private Operation operation;

    public EmptyResponse(String resourceType, String name, String namespace) {
      this.resourceType = resourceType;
      this.name = name;
      this.namespace = namespace;
    }

    public EmptyResponse(Operation operation, String resourceType, String name, String namespace) {
      this(resourceType, name, namespace);
      this.operation = operation;
    }

    boolean matches(Operation operation, String resourceType, String name) {
      return this.resourceType.equals(resourceType)
          && (this.operation == null || this.operation == operation)
          && (name == null || Objects.equals(this.name, name));
    }

    boolean matches(Operation operation, String name) {
      return (this.operation == null || this.operation == operation)
          && (name == null || Objects.equals(this.name, name));
    }
  }

  static class AfterCallAction {
    private final String resourceType;
    private final String call;
    private final Runnable action;

    AfterCallAction(@Nonnull String resourceType, @Nonnull String call, @Nonnull Runnable action) {
      this.resourceType = resourceType;
      this.call = call;
      this.action = action;
    }

    boolean matches(String resourceType, String call) {
      return this.resourceType.equals(resourceType)
          && (this.call.equals(call));
    }

    void doAction() {
      action.run();
    }
  }

  private class KubernetesApiFactoryImpl implements KubernetesApiFactory {
    @Override
    public <A extends KubernetesObject, L extends KubernetesListObject> KubernetesApi<A, L>
        create(Class<A> apiTypeClass, Class<L> apiListTypeClass,
               String apiGroup, String apiVersion, String resourcePlural, UnaryOperator<ApiClient> clientSelector) {
      return new KubernetesApi<>() {
        @Override
        public KubernetesApiResponse<A> get(String name, GetOptions getOptions) {
          return new CallContext<A>(
              Operation.read, getResourceName(apiTypeClass), null, name)
              .execute();
        }

        @Override
        public KubernetesApiResponse<A> get(String namespace, String name, GetOptions getOptions) {
          return new CallContext<A>(
              Operation.read, getResourceName(apiTypeClass), namespace, name)
              .execute();
        }

        @Override
        public KubernetesApiResponse<L> list(ListOptions listOptions) {
          return new CallContext<L>(
              Operation.list, getResourceName(apiTypeClass), null, null, null, null,
              listOptions.getFieldSelector(), listOptions.getLabelSelector(), null)
              .execute();
        }

        @Override
        public KubernetesApiResponse<L> list(String namespace, ListOptions listOptions) {
          return new CallContext<L>(
              Operation.list, getResourceName(apiTypeClass), namespace, null, null, null,
              listOptions.getFieldSelector(), listOptions.getLabelSelector(), null)
              .execute();
        }

        @Override
        public KubernetesApiResponse<A> create(A object, CreateOptions createOptions) {
          V1ObjectMeta meta = object.getMetadata();
          return new CallContext<>(
              Operation.create, getResourceName(apiTypeClass),
                  Optional.ofNullable(meta).map(V1ObjectMeta::getNamespace).orElse(null),
                  Optional.ofNullable(meta).map(V1ObjectMeta::getName).orElse(null), object)
              .execute();
        }

        @Override
        public KubernetesApiResponse<A> create(String namespace, A object, CreateOptions createOptions) {
          V1ObjectMeta meta = object.getMetadata();
          return new CallContext<>(
              Operation.create, getResourceName(apiTypeClass), namespace, meta.getName(), object)
              .execute();
        }

        @Override
        public KubernetesApiResponse<A> update(A object, UpdateOptions updateOptions) {
          V1ObjectMeta meta = object.getMetadata();
          return new CallContext<>(
              Operation.replace, getResourceName(apiTypeClass),
                  Optional.ofNullable(meta).map(V1ObjectMeta::getNamespace).orElse(null),
                  Optional.ofNullable(meta).map(V1ObjectMeta::getName).orElse(null), object)
              .execute();
        }

        @Override
        public KubernetesApiResponse<A> updateStatus(A object, Function<A, Object> status,
                                                     UpdateOptions updateOptions) {
          V1ObjectMeta meta = object.getMetadata();
          return new CallContext<>(
              Operation.replaceStatus, getResourceName(apiTypeClass), meta.getNamespace(), meta.getName(), object)
              .execute();
        }

        @Override
        public KubernetesApiResponse<A> patch(String name, String patchType, V1Patch patch,
                                              PatchOptions patchOptions) {
          return new CallContext<A>(
              Operation.patch, getResourceName(apiTypeClass), null, name, patch)
              .execute();
        }

        @Override
        public KubernetesApiResponse<A> patch(String namespace, String name, String patchType, V1Patch patch,
                                              PatchOptions patchOptions) {
          return new CallContext<A>(
              Operation.patch, getResourceName(apiTypeClass), namespace, name, patch)
              .execute();
        }

        @Override
        public KubernetesApiResponse<A> delete(String name, DeleteOptions deleteOptions) {
          return new CallContext<A>(
                  Operation.delete, getResourceName(apiTypeClass), null, name, null, null,
                  null, null, deleteOptions.getGracePeriodSeconds())
                  .execute();
        }

        @Override
        public KubernetesApiResponse<A> delete(String namespace, String name, DeleteOptions deleteOptions) {
          return new CallContext<A>(
                  Operation.delete, getResourceName(apiTypeClass), namespace, name, null, null,
                  null, null, deleteOptions.getGracePeriodSeconds())
                  .execute();
        }

        @Override
        public KubernetesApiResponse<RequestBuilder.V1StatusObject> deleteCollection(
                String namespace, ListOptions listOptions, DeleteOptions deleteOptions) {
          return new CallContext<RequestBuilder.V1StatusObject>(
                  Operation.deleteCollection, getResourceName(apiTypeClass), namespace, null, null, null,
                  listOptions.getFieldSelector(), listOptions.getLabelSelector(), deleteOptions.getGracePeriodSeconds())
                  .execute();
        }

        @Override
        public KubernetesApiResponse<RequestBuilder.StringObject> logs(
                String namespace, String name, String container) {
          return new CallContext<RequestBuilder.StringObject>(
                  Operation.read, PODLOG, namespace, name)
                  .execute();
        }

        @Override
        public KubernetesApiResponse<RequestBuilder.VersionInfoObject> getVersionCode() {
          return new CallContext<RequestBuilder.VersionInfoObject>(
              Operation.getVersion, null, null, null)
              .execute();
        }
      };
    }
  }

  private String getResourceName(Class<?> resourceType) {
    return dataTypes.get(resourceType);
  }

  private class DataRepository<T extends KubernetesType> {
    protected final Map<String, T> data = new HashMap<>();
    private final Class<T> resourceType;
    private Function<List<T>, KubernetesListObject> listFactory;
    private final Map<String, List<T>> continuations = new HashMap<>();
    protected List<Consumer<T>> onCreateActions = new ArrayList<>();
    private List<Consumer<T>> onUpdateActions = new ArrayList<>();
    private List<Consumer<DeletionContext>> onDeleteActions = new ArrayList<>();
    private Method getStatusMethod;
    private Method setStatusMethod;

    public DataRepository(Class<T> resourceType) {
      this.resourceType = resourceType;
    }

    public DataRepository(Class<T> resourceType, Function<List<T>, KubernetesListObject> listFactory) {
      this.resourceType = resourceType;
      this.listFactory = listFactory;
    }

    public DataRepository(Class<T> resourceType, NamespacedDataRepository<T> parent) {
      this.resourceType = resourceType;
      copyFieldsFromParent(parent);
    }

    public void copyFieldsFromParent(DataRepository<T> parent) {
      onCreateActions = parent.onCreateActions;
      onUpdateActions = parent.onUpdateActions;
      onDeleteActions = parent.onDeleteActions;
      getStatusMethod = parent.getStatusMethod;
      setStatusMethod = parent.setStatusMethod;
    }

    @SuppressWarnings("UnusedReturnValue")
    DataRepository<T> withStatusSubresource() {
      try {
        getStatusMethod = resourceType.getMethod("getStatus");
        setStatusMethod = resourceType.getMethod("setStatus", getStatusMethod.getReturnType());
      } catch (NoSuchMethodException e) {
        throw new RuntimeException("Resource type " + resourceType + " may not defined with a status subdomain");
      }
      return this;
    }


    @SuppressWarnings("unchecked")
    void createResourceInNamespace(String name, String namespace, Object resource) {
      data.put(name, (T) resource);
    }

    void createResourceInNamespace(T resource) {
      createResource(getMetadata(resource).getNamespace(), withOptionalCreationTimeStamp(resource));
    }

    private T withOptionalCreationTimeStamp(T resource) {
      if (addCreationTimestamp) {
        getMetadata(resource).setCreationTimestamp(SystemClock.now());
      }
      return resource;
    }

    T createResource(String namespace, T resource) {
      String name = getName(resource);
      if (name != null) {
        if (hasElementWithName(name)) {
          throw new AlreadyExistsException(getResourceName(), name, namespace);
        }
        data.put(name, resource);
      }

      onCreateActions.forEach(a -> a.accept(resource));
      return resource;
    }

    void deleteResourceInNamespace(T resource) {
      deleteResource(getMetadata(resource).getNamespace(), resource);
    }

    void deleteResource(String namespace, T resource) {
      String name = getName(resource);
      if (name != null) {
        if (!hasElementWithName(getName(resource))) {
          throw new NotFoundException(getResourceName(), name, namespace);
        }
        data.remove(name);
      }
    }

    T deleteResource(String name, String namespace) {
      if (!hasElementWithName(name)) {
        throw new NotFoundException(getResourceName(), name, namespace);
      }
      return getDeleteResult(data.remove(name), name, namespace, getResourceName());
    }

    @SuppressWarnings("unchecked")
    T listResources(String namespace, Integer limit, String cont, String fieldSelector, String... labelSelectors) {
      if (listFactory == null) {
        throw new UnsupportedOperationException("list operation not supported");
      }

      List<T> resources;
      if (cont != null) {
        resources = continuations.remove(cont);
      } else {
        resources = getResources(namespace, fieldSelector, labelSelectors);
      }

      if (limit == null || limit > resources.size()) {
        return (T) listFactory.apply(resources);
      }

      KubernetesListObject list = listFactory.apply(resources.subList(0, limit));
      cont = UUID.randomUUID().toString();
      continuations.put(cont, resources.subList(limit, resources.size()));
      try {
        V1ListMeta meta = list.getMetadata();
        if (meta != null) {
          meta.setContinue(cont);
        }
      } catch (SecurityException | IllegalArgumentException e) {
        // no-op, no-log
      }

      return (T) list;
    }

    List<T> getResources(String namespace, String fieldSelector, String... labelSelectors) {
      return getResources(fieldSelector, labelSelectors);
    }

    List<T> getResources(String fieldSelector, String... labelSelectors) {
      return data.values().stream()
          .filter(withFields(fieldSelector))
          .filter(withLabels(labelSelectors))
          .collect(Collectors.toCollection(ArrayList::new));
    }

    List<T> getResources() {
      return new ArrayList<>(data.values());
    }

    private Predicate<Object> withLabels(String[] labelSelectors) {
      return o -> (ArrayUtils.isEmpty(labelSelectors) || hasLabels(getMetadata(o), labelSelectors));
    }

    private boolean hasLabels(V1ObjectMeta metadata, String[] selectors) {
      return Arrays.stream(selectors).allMatch(s -> hasLabel(metadata, s));
    }

    private boolean hasLabel(V1ObjectMeta metadata, String selector) {
      String[] split = selector.split("=");
      return includesLabel(metadata.getLabels(), split[0], split.length == 1 ? null : split[1]);
    }

    private boolean includesLabel(Map<String, String> labels, String key, String value) {
      if (labels == null || !labels.containsKey(key)) {
        return false;
      }
      return value == null || value.equals(labels.get(key));
    }

    private Predicate<Object> withFields(String fieldSelector) {
      return o -> (fieldSelector == null) || allSelectorsMatch(o, fieldSelector);
    }

    private boolean allSelectorsMatch(Object o, String fieldSelector) {
      return Arrays.stream(fieldSelector.split(",")).allMatch(f -> hasField(o, f));
    }

    private boolean hasField(Object object, String fieldSpec) {
      Matcher fieldMatcher = FIELD_PATTERN.matcher(fieldSpec);
      if (!fieldMatcher.find()) {
        return false;
      }

      return new FieldMatcher(fieldSpec).matches(object);
    }

    T replaceResource(String name, T resource) {
      setName(resource, name);

      Optional.ofNullable(data.get(name)).ifPresent(old -> optionallyCopyStatusSubresource(old, resource));
      data.put(name, withOptionalCreationTimeStamp(resource));
      onUpdateActions.forEach(a -> a.accept(resource));
      return resource;
    }

    private void optionallyCopyStatusSubresource(T fromResource, T toResource) {
      if (getStatusMethod != null) {
        copyResourceStatus(fromResource, toResource);
      }
    }

    T replaceResourceStatus(String name, T resource) {
      setName(resource, name);

      T current = data.get(name);
      if (current == null) {
        throw new NotFoundException(getResourceName(), name, null);
      }
      copyResourceStatus(resource, current);
      incrementResourceVersion(getMetadata(current));
      onUpdateActions.forEach(a -> a.accept(current));
      if (emptyResponse != null && emptyResponse.matches(Operation.replaceStatus,name)) {
        cancelEmptyResponse();
        return null;
      }
      return current;
    }

    private void incrementResourceVersion(V1ObjectMeta metadata) {
      metadata.setResourceVersion(incrementString(metadata.getResourceVersion()));
    }

    private String incrementString(String string) {
      try {
        return Integer.toString(Integer.parseInt(string) + 1);
      } catch (NumberFormatException e) {
        return "0";
      }
    }

    private void copyResourceStatus(T fromResources, T toResource) {
      try {
        setStatusMethod.invoke(toResource, getStatusMethod.invoke(fromResources));
      } catch (NullPointerException | IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException("Status subresource not defined");
      }
    }

    @SuppressWarnings("unchecked")
    private T getDeleteResult(T resource, String name, String namespace, String resourceType) {
      if (POD.equals(resourceType)) {
        V1Pod pod = (V1Pod) resource;
        V1ObjectMeta meta = pod.getMetadata();
        if (meta == null) {
          meta = new V1ObjectMeta().name(name).namespace(namespace);
          pod.setMetadata(meta);
        }
        if (meta.getDeletionTimestamp() == null) {
          meta.setDeletionTimestamp(SystemClock.now());
        } else if (SystemClock.now().isAfter(meta.getDeletionTimestamp())) {
          throw new NotFoundException(getResourceName(), name, namespace);
        }
        return resource;
      } else {
        return (T) new RequestBuilder.V1StatusObject(new V1Status().code(200));
      }
    }

    private String getResourceName() {
      return dataTypes.get(resourceType);
    }

    public V1Status deleteResourceCollection(String namespace) {
      data.clear();
      return new V1Status().code(200);
    }

    public T readResource(String name, String namespace) {
      if (!data.containsKey(name)) {
        throw new NotFoundException(getResourceName(), name, namespace);
      }

      if (emptyResponse != null && emptyResponse.matches(Operation.read, name)) {
        cancelEmptyResponse();
        return null;
      }
      return data.get(name);
    }

    private JsonArray fromV1Patch(V1Patch patch) {
      return Json.createReader(new StringReader(patch.getValue())).readArray();
    }

    public T patchResource(String name, String namespace, V1Patch body) {
      if (!data.containsKey(name)) {
        throw new NotFoundException(getResourceName(), name, namespace);
      }

      JsonPatch patch = Json.createPatch(fromV1Patch(body));
      JsonStructure result = patch.apply(toJsonStructure(data.get(name)));
      T resource = fromJsonStructure(result);
      Optional.ofNullable(data.get(name)).ifPresent(old -> optionallyCopyStatusSubresource(old, resource));
      data.put(name, resource);
      onUpdateActions.forEach(a -> a.accept(resource));
      return resource;
    }

    T fromJsonStructure(JsonStructure jsonStructure) {
      return new JSON().deserialize(jsonStructure.toString(), resourceType);
    }

    JsonStructure toJsonStructure(T src) {
      String json = new JSON().getGson().toJson(src);
      return Json.createReader(new StringReader(json)).read();
    }

    boolean hasElementWithName(String name) {
      return data.containsKey(name);
    }

    private String getName(@Nonnull Object resource) {
      return Optional.ofNullable(getMetadata(resource)).map(V1ObjectMeta::getName).orElse(null);
    }

    private void setName(@Nonnull Object resource, String name) {
      getMetadata(resource).setName(name);
    }

    @SuppressWarnings("unchecked")
    void addCreateAction(Consumer<?> consumer) {
      onCreateActions.add((Consumer<T>) consumer);
    }

    @SuppressWarnings("unchecked")
    void addUpdateAction(Consumer<?> consumer) {
      onUpdateActions.add((Consumer<T>) consumer);
    }

    void addDeleteAction(Consumer<DeletionContext> consumer) {
      onDeleteActions.add(consumer);
    }

    public void sendDeleteCallback(String name, String namespace, Long gracePeriodSeconds) {
      onDeleteActions.forEach(a -> a.accept(new DeletionContext(name, namespace, gracePeriodSeconds)));
    }

    static class FieldMatcher {
      private String path;
      private String op;
      private String value;

      FieldMatcher(String fieldSpec) {
        Matcher fieldMatcher = FIELD_PATTERN.matcher(fieldSpec);
        if (fieldMatcher.find()) {
          path = fieldMatcher.group(1);
          op = fieldMatcher.group(2);
          value = fieldMatcher.group(3);
        }
      }

      boolean matches(Object object) {
        String fieldValue = getFieldValue(object);
        boolean matches = fieldValue.equals(value);
        if (op.equals("!=")) {
          return !matches;
        } else {
          return matches;
        }
      }

      private String getFieldValue(Object object) {
        String[] split = path.split("\\.");
        Object result = object;
        for (String link : split) {
          result = result == null ? null : getSubField(result, link);
        }
        return result == null ? "" : result.toString();
      }

      private Object getSubField(Object object, String fieldName) {
        try {
          Class<?> aaClass = object.getClass();
          Field field = aaClass.getDeclaredField(fieldName);
          field.setAccessible(true);
          return field.get(object);
        } catch (NoSuchFieldException | IllegalAccessException e) {
          return "";
        }
      }
    }
  }

  private class CreateOnlyNoMetadataDataRepository<T extends KubernetesType> extends DataRepository<T> {
    private static final String NAME = "unnamed";

    public CreateOnlyNoMetadataDataRepository(Class<T> resourceType) {
      super(resourceType);
    }

    @Override
    T createResource(String namespace, T resource) {
      T existing = data.putIfAbsent(NAME, resource);
      if (existing != null) {
        return existing;
      }

      onCreateActions.forEach(a -> a.accept(resource));
      return resource;
    }
  }

  private class NamespacedDataRepository<T extends KubernetesType> extends DataRepository<T> {
    private final Map<String, DataRepository<T>> repositories = new HashMap<>();
    private final Class<T> resourceType;

    NamespacedDataRepository(Class<T> resourceType, Function<List<T>, KubernetesListObject> listFactory) {
      super(resourceType, listFactory);
      this.resourceType = resourceType;
    }

    void deleteNamespace(String namespace) {
      repositories.remove(namespace);
    }

    @Override
    void createResourceInNamespace(String name, String namespace, Object resource) {
      inNamespace(namespace).createResourceInNamespace(name, namespace, resource);
    }

    @Override
    T createResource(String namespace, T resource) {
      return inNamespace(namespace).createResource(namespace, resource);
    }

    @Override
    void deleteResource(String namespace, T resource) {
      inNamespace(namespace).deleteResource(namespace, resource);
    }

    @Override
    T deleteResource(String name, String namespace) {
      return inNamespace(namespace).deleteResource(name, namespace);
    }

    private DataRepository<T> inNamespace(String namespace) {
      return repositories.computeIfAbsent(namespace, n -> new DataRepository<>(resourceType, this));
    }

    @Override
    T replaceResource(String name, T resource) {
      return inNamespace(getMetadata(resource).getNamespace()).replaceResource(name, resource);
    }

    @Override
    T replaceResourceStatus(String name, T resource) {
      return inNamespace(getMetadata(resource).getNamespace()).replaceResourceStatus(name, resource);
    }

    @Override
    public V1Status deleteResourceCollection(String namespace) {
      return inNamespace(namespace).deleteResourceCollection(namespace);
    }

    @Override
    public T readResource(String name, String namespace) {
      return inNamespace(namespace).readResource(name, namespace);
    }

    @Override
    public T patchResource(String name, String namespace, V1Patch body) {
      return inNamespace(namespace).patchResource(name, namespace, body);
    }

    @Override
    List<T> getResources(String namespace, String fieldSelector, String... labelSelectors) {
      return inNamespace(namespace).getResources(fieldSelector, labelSelectors);
    }

    @Override
    List<T> getResources() {
      List<T> result = new ArrayList<>();
      for (DataRepository<T> repository : repositories.values()) {
        result.addAll(repository.getResources());
      }
      return result;
    }
  }

  private class CallContext<D extends KubernetesType> {
    private final String fieldSelector;
    private final String[] labelSelector;
    private final Long gracePeriodSeconds;
    private String resourceType;
    private String requestNamespace;
    private String requestName;
    private D requestBody;
    private Operation operation;
    private V1Patch patch;
    private String cont = null;

    CallContext(Operation operation, String resourceType, String namespace, String name) {
      this(operation, resourceType, namespace, name, null, null, null, null, null);
    }

    CallContext(Operation operation, String resourceType, String namespace, String name, D body) {
      this(operation, resourceType, namespace, name, body, null, null, null, null);
    }

    CallContext(Operation operation, String resourceType, String namespace, String name, V1Patch patch) {
      this(operation, resourceType, namespace, name, null, patch, null, null, null);
    }

    CallContext(Operation operation, String resourceType,
                String namespace, String name, D body, V1Patch patch,
                String fieldSelector, String labelSelector, Long gracePeriodSeconds) {
      this.operation = operation;
      this.resourceType = resourceType;
      this.requestNamespace = namespace;
      this.requestName = name;
      this.requestBody = body;
      this.patch = patch;
      this.fieldSelector = fieldSelector;
      this.labelSelector = labelSelector == null ? null : labelSelector.split(",");
      this.gracePeriodSeconds = gracePeriodSeconds;
    }

    public void setContinue(String cont) {
      this.cont = cont;
    }

    public String getContinue() {
      return cont;
    }

    public Integer getLimit() {
      return null;
    }

    private VersionInfo getVersionInfo() {
      return versionInfo;
    }

    private KubernetesApiResponse<D> execute() {
      try {
        if (failure != null && failure.matches(resourceType, requestName, requestNamespace, operation)) {
          try {
            return failure.getExceptionResponse();
          } finally {
            failure = null;
          }
        }

        numCalls++;
        return operation.execute(this, selectRepository(resourceType));
      } finally {
        if (afterCallAction != null && afterCallAction.matches(resourceType, operation.name())) {
          afterCallAction.doAction();
          afterCallAction = null;
        }
      }
    }

    @SuppressWarnings("unchecked")
    <T extends KubernetesType> KubernetesApiResponse<T> createResource(DataRepository<T> dataRepository) {
      try {
        return new KubernetesApiResponse<>(dataRepository.createResource(requestNamespace, (T) requestBody));
      } catch (AlreadyExistsException aee) {
        return new KubernetesApiResponse<>(new V1Status().message(aee.getMessage()), HttpURLConnection.HTTP_CONFLICT);
      }
    }

    @SuppressWarnings("unchecked")
    private <T extends KubernetesType> KubernetesApiResponse<T> replaceResource(DataRepository<T> dataRepository) {
      return new KubernetesApiResponse<>(dataRepository.replaceResource(requestName, (T) requestBody));
    }

    @SuppressWarnings("unchecked")
    private <T extends KubernetesType> KubernetesApiResponse<T> replaceResourceStatus(
        DataRepository<T> dataRepository) {
      try {
        return new KubernetesApiResponse<>(dataRepository.replaceResourceStatus(requestName, (T) requestBody));
      } catch (NotFoundException nfe) {
        return new KubernetesApiResponse<>(new V1Status().message(nfe.getMessage()), HttpURLConnection.HTTP_NOT_FOUND);
      }
    }

    private <T extends KubernetesType> KubernetesApiResponse<T> deleteResource(DataRepository<T> dataRepository) {
      try {
        dataRepository.sendDeleteCallback(requestName, requestNamespace, gracePeriodSeconds);
        return new KubernetesApiResponse<>(delete(dataRepository));
      } catch (NotFoundException nfe) {
        return new KubernetesApiResponse<>(new V1Status().message(nfe.getMessage()), HttpURLConnection.HTTP_NOT_FOUND);
      }
    }

    private <T extends KubernetesType> T delete(DataRepository<T> dataRepository) {
      if (POD.equals(resourceType)) {
        T resource = dataRepository.readResource(requestName, requestNamespace);
        if (resource != null) {
          V1ObjectMeta meta = getMetadata(resource);
          if (meta.getDeletionTimestamp() == null) {
            meta.setDeletionTimestamp(SystemClock.now().plusSeconds(1));
            return resource;
          } else if (meta.getDeletionTimestamp().isAfter(SystemClock.now())) {
            return resource;
          }
        }
      }
      return dataRepository.deleteResource(requestName, requestNamespace);
    }

    private <T extends KubernetesType> KubernetesApiResponse<T> patchResource(DataRepository<T> dataRepository) {
      try {
        return new KubernetesApiResponse<>(dataRepository.patchResource(requestName, requestNamespace, patch));
      } catch (NotFoundException nfe) {
        return new KubernetesApiResponse<>(new V1Status().message(nfe.getMessage()), HttpURLConnection.HTTP_NOT_FOUND);
      }
    }

    private <T extends KubernetesType> KubernetesApiResponse<T> listResources(Integer limit, String cont,
                                                                              DataRepository<T> dataRepository) {
      return new KubernetesApiResponse<>(
          dataRepository.listResources(requestNamespace, limit, cont, fieldSelector, labelSelector));
    }

    private <T extends KubernetesType> KubernetesApiResponse<T> readResource(DataRepository<T> dataRepository) {
      try {
        return new KubernetesApiResponse<>(dataRepository.readResource(requestName, requestNamespace));
      } catch (NotFoundException nfe) {
        return new KubernetesApiResponse<>(new V1Status().message(nfe.getMessage()), HttpURLConnection.HTTP_NOT_FOUND);
      }
    }

    @SuppressWarnings("unchecked")
    private <T extends KubernetesType> KubernetesApiResponse<T> readMetadata(DataRepository<T> dataRepository) {
      final T resource = dataRepository.readResource(requestName, requestNamespace);
      try {
        final T metadataOnly = (T) resource.getClass().getConstructor().newInstance();
        resource.getClass().getMethod("setMetadata", V1ObjectMeta.class)
            .invoke(metadataOnly, ((KubernetesObject) resource).getMetadata());
        return new KubernetesApiResponse<>(metadataOnly);
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    public <T extends KubernetesType> KubernetesApiResponse<T> deleteCollection(DataRepository<T> dataRepository) {
      return new KubernetesApiResponse<>(
          dataRepository.deleteResourceCollection(requestNamespace), HttpURLConnection.HTTP_OK);
    }
  }

  static class AlreadyExistsException extends RuntimeException {
    @Serial
    private static final long serialVersionUID  = 1L;

    public AlreadyExistsException(String resourceType, String name, String namespace) {
      super(String.format("%s named %s already exists in namespace %s", resourceType, name, namespace));
    }
  }

  public static class NotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID  = 1L;

    public NotFoundException(String resourceType, String name, String namespace) {
      super(String.format("No %s named %s found in namespace %s", resourceType, name, namespace));
    }
  }
}
