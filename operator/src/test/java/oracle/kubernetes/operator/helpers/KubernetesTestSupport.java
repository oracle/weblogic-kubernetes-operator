// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
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
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfiguration;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfigurationList;
import io.kubernetes.client.openapi.models.VersionInfo;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonPatch;
import jakarta.json.JsonStructure;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;
import oracle.kubernetes.operator.builders.CallParams;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.calls.SimulatedStep;
import oracle.kubernetes.operator.calls.SynchronousCallDispatcher;
import oracle.kubernetes.operator.calls.SynchronousCallFactory;
import oracle.kubernetes.operator.webhooks.model.Scale;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.PartialObjectMetadata;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static oracle.kubernetes.operator.calls.AsyncRequestStep.CONTINUE;
import static oracle.kubernetes.operator.calls.AsyncRequestStep.RESPONSE_COMPONENT_NAME;

@SuppressWarnings("WeakerAccess")
public class KubernetesTestSupport extends FiberTestSupport {
  public static final VersionInfo TEST_VERSION_INFO = new VersionInfo().major("1").minor("18").gitVersion("0");
  public static final String CONFIG_MAP = "ConfigMap";
  public static final String CUSTOM_RESOURCE_DEFINITION = "CRD";
  public static final String NAMESPACE = "Namespace";
  public static final String CLUSTER = "Cluster";
  public static final String CLUSTER_STATUS = "ClusterStatus";
  public static final String DOMAIN = "Domain";
  public static final String DOMAIN_STATUS = "DomainStatus";
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

  private static final String PATH_PATTERN = "\\w+(?:.\\w+)*";
  private static final String OP_PATTERN = "=|==|!=";
  private static final String VALUE_PATTERN = ".*";
  private static final Pattern FIELD_PATTERN
        = Pattern.compile("(" + PATH_PATTERN + ")(" + OP_PATTERN + ")(" + VALUE_PATTERN + ")");

  private static final RequestParams REQUEST_PARAMS
      = new RequestParams("testcall", "junit", "testName", "body", (CallParams) null);
  public static final String DELETE_POD = "deletePod";

  private final Map<String, DataRepository<?>> repositories = new HashMap<>();
  private final Map<Class<?>, String> dataTypes = new HashMap<>();
  private Failure failure;
  private AfterCallAction afterCallAction;
  private long resourceVersion;
  private int numCalls;
  private boolean addCreationTimestamp;
  private EmptyResponse emptyResponse;

  /**
   * Installs a factory into CallBuilder to use canned responses.
   *
   * @return a memento which can be used to restore the production factory
   */
  public Memento install() {
    support(CUSTOM_RESOURCE_DEFINITION, V1CustomResourceDefinition.class);
    support(SELF_SUBJECT_ACCESS_REVIEW, V1SelfSubjectAccessReview.class);
    support(SELF_SUBJECT_RULES_REVIEW, V1SubjectRulesReviewStatus.class);
    support(SUBJECT_ACCESS_REVIEW, V1SubjectAccessReview.class);
    support(TOKEN_REVIEW, V1TokenReview.class);
    support(PV, V1PersistentVolume.class, this::createPvList);
    support(NAMESPACE, V1Namespace.class, this::createNamespaceList);
    support(VALIDATING_WEBHOOK_CONFIGURATION,
        V1ValidatingWebhookConfiguration.class, this::createValidatingWebhookConfigurationList);

    supportNamespaced(CONFIG_MAP, V1ConfigMap.class, this::createConfigMapList);
    supportNamespaced(CLUSTER, ClusterResource.class, this::createClusterList).withStatusSubresource();
    supportNamespaced(DOMAIN, DomainResource.class, this::createDomainList).withStatusSubresource();
    supportNamespaced(EVENT, CoreV1Event.class, this::createEventList);
    supportNamespaced(JOB, V1Job.class, this::createJobList);
    supportNamespaced(POD, V1Pod.class, this::createPodList);
    supportNamespaced(PODLOG, String.class);
    supportNamespaced(PODDISRUPTIONBUDGET, V1PodDisruptionBudget.class, this::createPodDisruptionBudgetList);
    supportNamespaced(PVC, V1PersistentVolumeClaim.class, this::createPvcList);
    supportNamespaced(SECRET, V1Secret.class, this::createSecretList);
    supportNamespaced(SERVICE, V1Service.class, this::createServiceList);
    supportNamespaced(SCALE, Scale.class);

    return new KubernetesTestSupportMemento();
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

  private CoreV1EventList createEventList(List<CoreV1Event> items) {
    return new CoreV1EventList().metadata(createListMeta()).items(items);
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

  private void support(String resourceName, Class<?> resourceClass) {
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, new DataRepository<>(resourceClass));
  }

  @SuppressWarnings("SameParameterValue")
  private <T> void support(
      String resourceName, Class<?> resourceClass, Function<List<T>, Object> toList) {
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, new DataRepository<>(resourceClass, toList));
  }

  @SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
  private <T> NamespacedDataRepository<Object> supportNamespaced(String resourceName, Class<T> resourceClass) {
    final NamespacedDataRepository<Object> dataRepository = new NamespacedDataRepository<>(resourceClass, null);
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, dataRepository);
    return dataRepository;
  }

  private <T> NamespacedDataRepository<T> supportNamespaced(
      String resourceName, Class<T> resourceClass, Function<List<T>, Object> toList) {
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

  private DataRepository<?> selectRepository(String resourceType) {
    String key = resourceType;
    for (String suffix : RequestParams.SUB_RESOURCE_SUFFIXES) {
      if (key.endsWith(suffix)) {
        key = key.substring(0, key.length() - suffix.length());
        break;
      }
    }
    return repositories.get(key);
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> getResources(String resourceType) {
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
  public final <T> void defineResources(T... resources) {
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
  public final <T> void deleteResources(T... resources) {
    for (T resource : resources) {
      getDataRepository(resource).deleteResourceInNamespace(resource);
    }
  }

  public void definePodLog(String name, String namespace, Object contents) {
    repositories.get(PODLOG).createResourceInNamespace(name, namespace, contents);
  }

  /**
   * Deletes the specified namespace and all resources in that namespace.
   * @param namespaceName the name of the namespace to delete
   */
  public void deleteNamespace(String namespaceName) {
    repositories.get(NAMESPACE).data.remove(namespaceName);
    repositories.values().stream()
          .filter(r -> r instanceof NamespacedDataRepository)
          .forEach(r -> ((NamespacedDataRepository<?>) r).deleteNamespace(namespaceName));
  }

  @SuppressWarnings("unchecked")
  private <T> DataRepository<T> getDataRepository(T resource) {
    return (DataRepository<T>) repositories.get(dataTypes.get(resource.getClass()));
  }

  public void doOnCreate(String resourceType, Consumer<?> consumer) {
    selectRepository(resourceType).addCreateAction(consumer);
  }

  public void doOnUpdate(String resourceType, Consumer<?> consumer) {
    selectRepository(resourceType).addUpdateAction(consumer);
  }

  public void doOnDelete(String resourceType, Consumer<Integer> consumer) {
    selectRepository(resourceType).addDeleteAction(consumer);
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
   * @param apiException the Kubernetes exception to associate with the failure
   */
  public void failOnCreate(String resourceType, String namespace, ApiException apiException) {
    failure = new Failure(Operation.create, resourceType, null, namespace, apiException);
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
   * Specifies that a replace operation should fail if it matches the specified conditions. Applies to
   * namespaced resources and replaces any existing failure checks.
   *
   * @param resourceType the type of resource
   * @param name the name of the resource
   * @param namespace the namespace containing the resource
   */
  public void failOnReplaceWithStreamResetException(String resourceType, String name, String namespace) {
    ApiException ae = new ApiException("StreamResetException: stream was reset: NO_ERROR",
            new StreamResetException(ErrorCode.NO_ERROR), 0, null, null);
    failure = new Failure(Operation.replace, resourceType, name, namespace, ae);
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
   * @param apiException the kubernetes failure to associate with the failure
   */
  public void failOnResource(@Nonnull String resourceType, String name, String namespace, ApiException apiException) {
    failure = new Failure(resourceType, name, namespace, apiException);
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
      <T> Object execute(CallContext callContext, DataRepository<T> dataRepository) {
        return callContext.createResource(dataRepository);
      }

      @Override
      public String getName(RequestParams requestParams) {
        return KubernetesUtils.getResourceName(requestParams.body);
      }
    },
    delete {
      @Override
      <T> Object execute(CallContext callContext, DataRepository<T> dataRepository) {
        return callContext.deleteResource(dataRepository);
      }
    },
    read {
      @Override
      <T> Object execute(CallContext callContext, DataRepository<T> dataRepository) {
        return callContext.readResource(dataRepository);
      }
    },
    readMetadata {
      @Override
      <T> Object execute(CallContext callContext, DataRepository<T> dataRepository) {
        return callContext.readMetadata(dataRepository);
      }
    },
    replace {
      @Override
      <T> Object execute(CallContext callContext, DataRepository<T> dataRepository) {
        return callContext.replaceResource(dataRepository);
      }
    },
    replaceStatus {
      @Override
      <T> Object execute(CallContext callContext, DataRepository<T> dataRepository) {
        return callContext.replaceResourceStatus(dataRepository);
      }
    },
    list {
      @Override
      <T> Object execute(CallContext callContext, DataRepository<T> dataRepository) {
        return callContext.listResources(
                callContext.getLimit(),
                callContext.getContinue(), dataRepository);
      }
    },
    patch {
      @Override
      <T> Object execute(CallContext callContext, DataRepository<T> dataRepository) {
        return callContext.patchResource(dataRepository);
      }
    },
    deleteCollection {
      @Override
      <T> Object execute(CallContext callContext, DataRepository<T> dataRepository) {
        return callContext.deleteCollection(dataRepository);
      }
    },
    getVersion {
      @Override
      <T> Object execute(CallContext callContext, DataRepository<T> dataRepository) {
        return TEST_VERSION_INFO;
      }
    };

    abstract <T> Object execute(CallContext callContext, DataRepository<T> dataRepository);

    public String getName(RequestParams requestParams) {
      return requestParams.name;
    }
  }

  static class Failure {
    private final String resourceType;
    private final String name;
    private final String namespace;
    private final ApiException apiException;
    private Operation operation;

    public Failure(String resourceType, String name, String namespace, int httpStatus) {
      this(resourceType, name, namespace, new ApiException(httpStatus, "failure reported in test"));
    }

    Failure(@Nonnull String resourceType, String name, String namespace, ApiException apiException) {
      this.resourceType = resourceType;
      this.name = name;
      this.namespace = namespace;
      this.apiException = apiException;
    }

    Failure(Operation operation, String resourceType, String name, String namespace, int httpStatus) {
      this(resourceType, name, namespace, httpStatus);
      this.operation = operation;
    }

    Failure(Operation operation, String resourceType, String name, String namespace, ApiException apiException) {
      this(resourceType, name, namespace, apiException);
      this.operation = operation;
    }

    boolean matches(String resourceType, RequestParams requestParams, Operation operation) {
      return this.resourceType.equals(resourceType)
          && (this.operation == null || this.operation == operation)
          && (name == null || Objects.equals(name, requestParams.name))
          && (namespace == null || Objects.equals(namespace, requestParams.namespace));
    }

    HttpErrorException getException() {
      return new HttpErrorException(apiException);
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

    boolean matches(String resourceType, RequestParams requestParams) {
      return this.resourceType.equals(resourceType)
          && (this.call.equals(requestParams.call));
    }

    void doAction() {
      action.run();
    }
  }

  static class HttpErrorException extends RuntimeException {
    private final ApiException apiException;

    HttpErrorException(ApiException apiException) {
      this.apiException = apiException;
    }

    ApiException getApiException() {
      return apiException;
    }
  }

  private class KubernetesTestSupportMemento implements Memento {

    public KubernetesTestSupportMemento() {
      CallBuilder.setStepFactory(new AsyncRequestStepFactoryImpl());
      CallBuilder.setCallDispatcher(new CallDispatcherImpl());
    }

    @Override
    public void revert() {
      CallBuilder.resetStepFactory();
      CallBuilder.resetCallDispatcher();
    }

    @Override
    public <T> T getOriginalValue() {
      throw new UnsupportedOperationException();
    }
  }

  private class AsyncRequestStepFactoryImpl implements AsyncRequestStepFactory {

    @Override
    public <T> Step createRequestAsync(
        ResponseStep<T> next,
        RequestParams requestParams,
        CallFactory<T> factory,
        RetryStrategy retryStrategy,
        Pool<ApiClient> helper,
        int timeoutSeconds,
        int maxRetryCount,
        Integer gracePeriodSeconds,
        String fieldSelector,
        String labelSelector,
        String resourceVersion) {
      return new KubernetesTestSupport.SimulatedResponseStep(
          next, requestParams, fieldSelector, labelSelector, gracePeriodSeconds);
    }
  }

  private class CallDispatcherImpl implements SynchronousCallDispatcher {
    @SuppressWarnings("unchecked")
    @Override
    public <T> T execute(
        SynchronousCallFactory<T> factory, RequestParams requestParams, Pool<ApiClient> helper)
        throws ApiException {
      try {
        return (T) new CallContext(requestParams).execute();
      } catch (HttpErrorException e) {
        throw e.getApiException();
      }
    }
  }

  private class DataRepository<T> {
    private final Map<String, T> data = new HashMap<>();
    private final Class<?> resourceType;
    private Function<List<T>, Object> listFactory;
    private final Map<String, List<T>> continuations = new HashMap<>();
    private List<Consumer<T>> onCreateActions = new ArrayList<>();
    private List<Consumer<T>> onUpdateActions = new ArrayList<>();
    private List<Consumer<Integer>> onDeleteActions = new ArrayList<>();
    private Method getStatusMethod;
    private Method setStatusMethod;

    public DataRepository(Class<?> resourceType) {
      this.resourceType = resourceType;
    }

    public DataRepository(Class<?> resourceType, Function<List<T>, Object> listFactory) {
      this.resourceType = resourceType;
      this.listFactory = listFactory;
    }

    public DataRepository(Class<?> resourceType, NamespacedDataRepository<T> parent) {
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
          throw new RuntimeException("element exists");
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
          throw new RuntimeException("element doesn't exist");
        }
        data.remove(name);
      }
    }

    T deleteResource(String name, String namespace, String call) {
      if (!hasElementWithName(name)) {
        throw new NotFoundException(getResourceName(), name, namespace);
      }
      data.remove(name);
      return getDeleteResult(name, namespace, call);
    }

    Object listResources(String namespace, Integer limit, String cont, String fieldSelector, String... labelSelectors) {
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
        return listFactory.apply(resources);
      }

      Object list = listFactory.apply(resources.subList(0, limit));
      cont = UUID.randomUUID().toString();
      continuations.put(cont, resources.subList(limit, resources.size()));
      try {
        Method m = list.getClass().getMethod("getMetadata");
        Object meta = m.invoke(list);
        if (meta instanceof V1ListMeta) {
          ((V1ListMeta) meta).setContinue(cont);
        }
      } catch (NoSuchMethodException
              | SecurityException
              | IllegalAccessException
              | IllegalArgumentException
              | InvocationTargetException e) {
        // no-op, no-log
      }

      return list;
    }

    List<T> getResources(String namespace, String fieldSelector, String... labelSelectors) {
      return getResources(fieldSelector, labelSelectors);
    }

    List<T> getResources(String fieldSelector, String... labelSelectors) {
      return data.values().stream()
          .filter(withFields(fieldSelector))
          .filter(withLabels(labelSelectors))
          .collect(Collectors.toList());
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
        throw new IllegalStateException();
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
    private T getDeleteResult(String name, String namespace, String call) {
      if (call.equals(DELETE_POD)) {
        return (T) new V1Pod().metadata(new V1ObjectMeta().name(name).namespace(namespace));
      } else {
        return (T) new V1Status().code(200);
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

    V1ObjectMeta getMetadata(@Nonnull Object resource) {
      return KubernetesUtils.getResourceMetadata(resource);
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

    void addDeleteAction(Consumer<Integer> consumer) {
      onDeleteActions.add(consumer);
    }

    public void sendDeleteCallback(Integer gracePeriodSeconds) {
      onDeleteActions.forEach(a -> a.accept(gracePeriodSeconds));
    }

    class FieldMatcher {
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

  private class NamespacedDataRepository<T> extends DataRepository<T> {
    private final Map<String, DataRepository<T>> repositories = new HashMap<>();
    private final Class<?> resourceType;

    NamespacedDataRepository(Class<?> resourceType, Function<List<T>, Object> listFactory) {
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
    T deleteResource(String name, String namespace, String call) {
      return inNamespace(namespace).deleteResource(name, namespace, call);
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

  private class CallContext {
    private final RequestParams requestParams;
    private final String fieldSelector;
    private final String[] labelSelector;
    private final Integer gracePeriodSeconds;
    private String resourceType;
    private Operation operation;
    private String cont = null;

    CallContext(RequestParams requestParams) {
      this(requestParams, null, null, null);
    }

    CallContext(RequestParams requestParams, String fieldSelector, String labelSelector, Integer gracePeriodSeconds) {
      this.requestParams = requestParams;
      this.fieldSelector = fieldSelector;
      this.labelSelector = labelSelector == null ? null : labelSelector.split(",");
      this.gracePeriodSeconds = gracePeriodSeconds;

      parseCallName(requestParams);
    }

    public void setContinue(String cont) {
      this.cont = cont;
    }

    public String getContinue() {
      return cont;
    }

    public Integer getLimit() {
      return Optional.ofNullable(requestParams)
          .map(RequestParams::getCallParams).map(CallParams::getLimit).orElse(null);
    }

    private void parseCallName(RequestParams requestParams) {
      resourceType = requestParams.getResourceType();
      operation = getOperation(requestParams);

      if (isDeleteCollection()) {
        selectDeleteCollectionOperation();
      }
    }

    @Nonnull
    private Operation getOperation(RequestParams requestParams) {
      return requestParams.call.equals("getVersion")
            ? Operation.getVersion
            : Operation.valueOf(requestParams.getOperationName());

    }

    private boolean isDeleteCollection() {
      return resourceType.endsWith("Collection");
    }

    private void selectDeleteCollectionOperation() {
      resourceType = resourceType.substring(0, resourceType.indexOf("Collection"));
      operation = Operation.deleteCollection;
    }

    private Object execute() {
      try {
        if (failure != null && failure.matches(resourceType, requestParams, operation)) {
          try {
            throw failure.getException();
          } finally {
            failure = null;
          }
        }

        return operation.execute(this, selectRepository(resourceType));
      } finally {
        if (afterCallAction != null && afterCallAction.matches(resourceType, requestParams)) {
          afterCallAction.doAction();
          afterCallAction = null;
        }
      }
    }

    @SuppressWarnings("unchecked")
    <T> T createResource(DataRepository<T> dataRepository) {
      return dataRepository.createResource(requestParams.namespace, (T) requestParams.body);
    }

    @SuppressWarnings("unchecked")
    private <T> T replaceResource(DataRepository<T> dataRepository) {
      return dataRepository.replaceResource(requestParams.name, (T) requestParams.body);
    }

    @SuppressWarnings("unchecked")
    private <T> T replaceResourceStatus(DataRepository<T> dataRepository) {
      return dataRepository.replaceResourceStatus(requestParams.name, (T) requestParams.body);
    }

    private <T> T deleteResource(DataRepository<T> dataRepository) {
      dataRepository.sendDeleteCallback(gracePeriodSeconds);
      return dataRepository.deleteResource(requestParams.name, requestParams.namespace, requestParams.call);
    }

    private <T> T patchResource(DataRepository<T> dataRepository) {
      return dataRepository.patchResource(
          requestParams.name, requestParams.namespace, (V1Patch) requestParams.body);
    }

    private <T> Object listResources(Integer limit, String cont, DataRepository<T> dataRepository) {
      return dataRepository.listResources(requestParams.namespace, limit, cont, fieldSelector, labelSelector);
    }

    private <T> T readResource(DataRepository<T> dataRepository) {
      return dataRepository.readResource(requestParams.name, requestParams.namespace);
    }

    private <T> PartialObjectMetadata readMetadata(DataRepository<T> dataRepository) {
      final T resource = dataRepository.readResource(requestParams.name, requestParams.namespace);
      final V1ObjectMeta metadata = ((KubernetesObject) resource).getMetadata();
      return new PartialObjectMetadata(metadata);
    }

    public <T> V1Status deleteCollection(DataRepository<T> dataRepository) {
      return dataRepository.deleteResourceCollection(requestParams.namespace);
    }
  }

  private class SimulatedResponseStep extends Step implements SimulatedStep {
    private final CallContext callContext;

    SimulatedResponseStep(
          ResponseStep<?> next, RequestParams requestParams,
          String fieldSelector, String labelSelector, Integer gracePeriodSeconds) {
      super(next);
      callContext = new CallContext(requestParams, fieldSelector, labelSelector, gracePeriodSeconds);
      if (next != null) {
        next.setPrevious(this);
      }
    }

    @Override
    protected String getDetail() {
      return getRequestParams().call;
    }

    @Override
    public NextAction apply(Packet packet) {
      numCalls++;
      try {
        Component oldResponse = packet.getComponents().remove(RESPONSE_COMPONENT_NAME);
        if (oldResponse != null) {
          CallResponse<?> old = oldResponse.getSpi(CallResponse.class);
          if (old != null && old.getResult() != null) {
            // called again, access continue value, if available
            callContext.setContinue(accessContinue(old.getResult()));
          }
        }

        Object callResult = callContext.execute();
        CallResponse<Object> callResponse = createResponse(callResult);
        packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(callResponse));
        // clear out earlier results.  Replicating the behavior as in AsyncRequestStep.apply()
        packet.remove(CONTINUE);
      } catch (NotFoundException e) {
        packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(createResponse(e, getRequestParams())));
      } catch (HttpErrorException e) {
        packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(createResponse(e, getRequestParams())));
      } catch (JsonException e) {
        packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(createResponse(e, getRequestParams())));
      } catch (Exception e) {
        packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(createResponse(e, getRequestParams())));
      }

      return doNext(packet);
    }

    private RequestParams getRequestParams() {
      return this.callContext.requestParams;
    }

    /**
     * Access continue field, if any, from list metadata.
     * @param result Kubernetes list result
     * @return Continue value
     */
    private String accessContinue(Object result) {
      String cont = "";
      if (result != null) {
        try {
          Method m = result.getClass().getMethod("getMetadata");
          Object meta = m.invoke(result);
          if (meta instanceof V1ListMeta) {
            return ((V1ListMeta) meta).getContinue();
          }
        } catch (NoSuchMethodException
                | SecurityException
                | IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException e) {
          // no-op, no-log
        }
      }
      return cont;
    }

    private <T> CallResponse<T> createResponse(T callResult) {
      return CallResponse.createSuccess(REQUEST_PARAMS, callResult, HTTP_OK);
    }

    private CallResponse<?> createResponse(NotFoundException e, RequestParams requestParams) {
      return CallResponse.createFailure(requestParams, new ApiException(e), HTTP_NOT_FOUND);
    }

    private CallResponse<?> createResponse(HttpErrorException e, RequestParams requestParams) {
      return CallResponse.createFailure(requestParams, e.getApiException(), e.getApiException().getCode());
    }

    private CallResponse<?> createResponse(JsonException e, RequestParams requestParams) {
      return CallResponse.createFailure(requestParams, new ApiException(e), HTTP_INTERNAL_ERROR);
    }

    private CallResponse<?> createResponse(Throwable t, RequestParams requestParams) {
      return CallResponse.createFailure(requestParams, new ApiException(t), HTTP_UNAVAILABLE);
    }
  }

  static class NotFoundException extends RuntimeException {
    public NotFoundException(String resourceType, String name, String namespace) {
      super(String.format("No %s named %s found in namespace %s", resourceType, name, namespace));
    }
  }
}
