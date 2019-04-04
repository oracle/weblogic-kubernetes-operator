// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static java.util.Collections.emptyMap;
import static oracle.kubernetes.operator.calls.AsyncRequestStep.RESPONSE_COMPONENT_NAME;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ConfigMapList;
import io.kubernetes.client.models.V1Event;
import io.kubernetes.client.models.V1EventList;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobList;
import io.kubernetes.client.models.V1ListMeta;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1SubjectAccessReview;
import io.kubernetes.client.models.V1TokenReview;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonPatch;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.calls.SynchronousCallDispatcher;
import oracle.kubernetes.operator.calls.SynchronousCallFactory;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;

@SuppressWarnings("WeakerAccess")
public class KubernetesTestSupport extends FiberTestSupport {
  private Map<String, DataRepository<?>> repositories = new HashMap<>();
  private Map<Class<?>, String> dataTypes = new HashMap<>();
  private Failure failure;
  private long resourceVersion;

  public static final String CONFIG_MAP = "ConfigMap";
  public static final String CUSTOM_RESOURCE_DEFINITION = "CRD";
  public static final String DOMAIN = "Domain";
  public static final String EVENT = "Event";
  public static final String JOB = "Job";
  public static final String PV = "PersistentVolume";
  public static final String PVC = "PersistentVolumeClaim";
  public static final String POD = "Pod";
  public static final String PODLOG = "PodLog";
  public static final String SERVICE = "Service";
  public static final String SUBJECT_ACCESS_REVIEW = "SubjectAccessReview";
  public static final String TOKEN_REVIEW = "TokenReview";

  /**
   * Installs a factory into CallBuilder to use canned responses.
   *
   * @return a memento which can be used to restore the production factory
   */
  public Memento install() throws NoSuchFieldException {
    support(CUSTOM_RESOURCE_DEFINITION, V1beta1CustomResourceDefinition.class);
    support(SUBJECT_ACCESS_REVIEW, V1SubjectAccessReview.class);
    support(TOKEN_REVIEW, V1TokenReview.class);
    support(PV, V1PersistentVolume.class, this::createPVList);

    supportNamespaced(CONFIG_MAP, V1ConfigMap.class, this::createConfigMapList);
    supportNamespaced(DOMAIN, Domain.class, this::createDomainList);
    supportNamespaced(EVENT, V1Event.class, this::createEventList);
    supportNamespaced(JOB, V1Job.class, this::createJobList);
    supportNamespaced(POD, V1Pod.class, this::createPodList);
    supportNamespaced(PODLOG, String.class);
    supportNamespaced(PVC, V1PersistentVolumeClaim.class, this::createPVCList);
    supportNamespaced(SERVICE, V1Service.class, this::createServiceList);

    return new KubernetesTestSupportMemento();
  }

  private V1ConfigMapList createConfigMapList(List<V1ConfigMap> items) {
    return new V1ConfigMapList().metadata(createListMeta()).items(items);
  }

  private DomainList createDomainList(List<Domain> items) {
    return new DomainList().withMetadata(createListMeta()).withItems(items);
  }

  private V1EventList createEventList(List<V1Event> items) {
    return new V1EventList().metadata(createListMeta()).items(items);
  }

  private V1PersistentVolumeList createPVList(List<V1PersistentVolume> items) {
    return new V1PersistentVolumeList().metadata(createListMeta()).items(items);
  }

  private V1PersistentVolumeClaimList createPVCList(List<V1PersistentVolumeClaim> items) {
    return new V1PersistentVolumeClaimList().metadata(createListMeta()).items(items);
  }

  private V1PodList createPodList(List<V1Pod> items) {
    return new V1PodList().metadata(createListMeta()).items(items);
  }

  private V1JobList createJobList(List<V1Job> items) {
    return new V1JobList().metadata(createListMeta()).items(items);
  }

  private V1ServiceList createServiceList(List<V1Service> items) {
    return new V1ServiceList().metadata(createListMeta()).items(items);
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

  private <T> void supportNamespaced(String resourceName, Class<T> resourceClass) {
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, new NamespacedDataRepository<>(resourceClass, null));
  }

  private <T> void supportNamespaced(
      String resourceName, Class<T> resourceClass, Function<List<T>, Object> toList) {
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, new NamespacedDataRepository<>(resourceClass, toList));
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> getResources(String resourceType) {
    return ((DataRepository<T>) repositories.get(resourceType)).getResources();
  }

  @SuppressWarnings("unchecked")
  public <T> T getResourceWithName(String resourceType, String name) {
    return (T)
        getResources(resourceType).stream()
            .filter(o -> name.equals(KubernetesUtils.getResourceName(o)))
            .findFirst()
            .orElse(null);
  }

  @SafeVarargs
  public final <T> void defineResources(T... resources) {
    for (T resource : resources) getDataRepository(resource).createResourceInNamespace(resource);
  }

  public void definePodLog(String name, String namespace, Object contents) {
    repositories.get(PODLOG).createResourceInNamespace(name, namespace, contents);
  }

  @SuppressWarnings("unchecked")
  private <T> DataRepository<T> getDataRepository(T resource) {
    return (DataRepository<T>) repositories.get(dataTypes.get(resource.getClass()));
  }

  public void doOnCreate(String resourceType, Consumer<?> consumer) {
    repositories.get(resourceType).addCreateAction(consumer);
  }

  public void doOnUpdate(String resourceType, Consumer<?> consumer) {
    repositories.get(resourceType).addUpdateAction(consumer);
  }

  /**
   * Specifies that any operation should fail if it matches the specified conditions. Applies to
   * namespaced resources.
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
   * non-namespaced resources.
   *
   * @param resourceType the type of resource
   * @param name the name of the resource
   * @param httpStatus the status to associate with the failure
   */
  public void failOnResource(String resourceType, String name, int httpStatus) {
    failOnResource(resourceType, name, null, httpStatus);
  }
  /*

    public void runOnOperation(String resourceType, String name, String namespace, Consumer<?> consumer) {
      this.consumers.add(consumer)
    }
  */

  private class KubernetesTestSupportMemento implements Memento {

    public KubernetesTestSupportMemento() throws NoSuchFieldException {
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
        ClientPool helper,
        int timeoutSeconds,
        int maxRetryCount,
        String fieldSelector,
        String labelSelector,
        String resourceVersion) {
      return new KubernetesTestSupport.SimulatedResponseStep(
          next, requestParams, fieldSelector, labelSelector);
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
        throw new ApiException(e.status, "failure reported in test");
      }
    }
  }

  private class DataRepository<T> {
    private Map<String, T> data = new HashMap<>();
    private Class<?> resourceType;
    private Function<List<T>, Object> listFactory;
    private List<Consumer<T>> onCreateActions = new ArrayList<>();
    private List<Consumer<T>> onUpdateActions = new ArrayList<>();

    public DataRepository(Class<?> resourceType) {
      this.resourceType = resourceType;
    }

    public DataRepository(Class<?> resourceType, Function<List<T>, Object> listFactory) {
      this.resourceType = resourceType;
      this.listFactory = listFactory;
    }

    public DataRepository(Class<?> resourceType, NamespacedDataRepository<T> parent) {
      this.resourceType = resourceType;
      onCreateActions = ((DataRepository<T>) parent).onCreateActions;
      onUpdateActions = ((DataRepository<T>) parent).onUpdateActions;
    }

    void createResourceInNamespace(T resource) {
      createResource(getMetadata(resource).getNamespace(), resource);
    }

    @SuppressWarnings("unchecked")
    void createResourceInNamespace(String name, String namespace, Object resource) {
      data.put(name, (T) resource);
    }

    T createResource(String namespace, T resource) {
      String name = getName(resource);
      if (name != null) {
        if (hasElementWithName(getName(resource))) throw new RuntimeException("element exists");
        data.put(getName(resource), resource);
      }

      onCreateActions.forEach(a -> a.accept(resource));
      return resource;
    }

    Object listResources(String namespace, String fieldSelector, String... labelSelectors) {
      if (listFactory == null)
        throw new UnsupportedOperationException("list operation not supported");

      return listFactory.apply(getResources(fieldSelector, labelSelectors));
    }

    List<T> getResources(String fieldSelector, String... labelSelectors) {
      return data.values().stream()
          .filter(withFields(fieldSelector))
          .filter(withLabels(labelSelectors))
          .collect(Collectors.toList());
    }

    private Predicate<Object> withLabels(String[] labelSelectors) {
      return o -> labelSelectors == null || hasLabels(getMetadata(o), labelSelectors);
    }

    private boolean hasLabels(V1ObjectMeta metadata, String[] selectors) {
      return Arrays.stream(selectors).allMatch(s -> hasLabel(metadata, s));
    }

    private boolean hasLabel(V1ObjectMeta metadata, String selector) {
      String[] split = selector.split("=");
      return includesLabel(metadata.getLabels(), split[0], split.length == 1 ? null : split[1]);
    }

    private boolean includesLabel(Map<String, String> labels, String key, String value) {
      if (labels == null || !labels.containsKey(key)) return false;
      return value == null || value.equals(labels.get(key));
    }

    private Predicate<Object> withFields(String fieldSelector) {
      return o -> (fieldSelector == null) || allSelectorsMatch(o, fieldSelector);
    }

    private boolean allSelectorsMatch(Object o, String fieldSelector) {
      return Arrays.stream(fieldSelector.split(",")).allMatch(f -> hasField(o, f));
    }

    private final String path = "\\w+(?:.\\w+)*";
    private final String op = "=|==|!=";
    private final String value = ".*";
    private final Pattern fieldPat = Pattern.compile("(" + path + ")(" + op + ")(" + value + ")");

    private boolean hasField(Object object, String fieldSpec) {
      Matcher fieldMatcher = fieldPat.matcher(fieldSpec);
      if (!fieldMatcher.find()) return false;

      return new FieldMatcher(fieldSpec).matches(object);
    }

    class FieldMatcher {
      private String path;
      private String op;
      private String value;

      FieldMatcher(String fieldSpec) {
        Matcher fieldMatcher = fieldPat.matcher(fieldSpec);
        if (fieldMatcher.find()) {
          path = fieldMatcher.group(1);
          op = fieldMatcher.group(2);
          value = fieldMatcher.group(3);
        }
      }

      boolean matches(Object object) {
        String fieldValue = getFieldValue(object);
        boolean matches = fieldValue.equals(value);
        if (op.equals("!=")) return !matches;
        else return matches;
      }

      private String getFieldValue(Object object) {
        String[] split = path.split("\\.");
        Object result = object;
        for (String link : split) result = result == null ? null : getSubField(result, link);
        return result == null ? "" : result.toString();
      }

      private Object getSubField(Object object, String fieldName) {
        try {
          Class<?> aClass = object.getClass();
          Field field = aClass.getDeclaredField(fieldName);
          field.setAccessible(true);
          return field.get(object);
        } catch (NoSuchFieldException | IllegalAccessException e) {
          return "";
        }
      }
    }

    T replaceResource(String name, T resource) {
      setName(resource, name);

      data.put(name, resource);
      onUpdateActions.forEach(a -> a.accept(resource));
      return resource;
    }

    V1Status deleteResource(String name, String namespace) {
      if (!hasElementWithName(name))
        throw new NotFoundException(getResourceName(), name, namespace);
      data.remove(name);

      return new V1Status().code(200);
    }

    private String getResourceName() {
      return dataTypes.get(resourceType);
    }

    public V1Status deleteResourceCollection(String namespace) {
      data.clear();
      return new V1Status().code(200);
    }

    public T readResource(String name, String namespace) {
      if (!data.containsKey(name)) throw new NotFoundException(getResourceName(), name, namespace);
      return data.get(name);
    }

    public T patchResource(String name, String namespace, List<JsonObject> body) {
      if (!data.containsKey(name)) throw new NotFoundException(getResourceName(), name, namespace);

      JsonPatch patch = Json.createPatch(toJsonArray(body));
      JsonStructure result = patch.apply(toJsonStructure(data.get(name)));
      T resource = fromJsonStructure(result);
      data.put(name, resource);
      onUpdateActions.forEach(a -> a.accept(resource));
      return resource;
    }

    @SuppressWarnings("unchecked")
    T fromJsonStructure(JsonStructure jsonStructure) {
      return (T) new Gson().fromJson(jsonStructure.toString(), resourceType);
    }

    JsonStructure toJsonStructure(T src) {
      String json = new Gson().toJson(src);
      return Json.createReader(new StringReader(json)).read();
    }

    JsonArray toJsonArray(List<JsonObject> patch) {
      JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
      for (JsonObject jsonObject : patch) arrayBuilder.add(toJsonValue(jsonObject));
      return arrayBuilder.build();
    }

    private JsonValue toJsonValue(JsonObject jsonObject) {
      return Json.createReader(new StringReader(jsonObject.toString())).readValue();
    }

    boolean hasElementWithName(String name) {
      return data.containsKey(name);
    }

    private String getName(@Nonnull Object resource) {
      return Optional.ofNullable(getMetadata(resource)).map(V1ObjectMeta::getName).orElse(null);
    }

    private V1ObjectMeta getMetadata(@Nonnull Object resource) {
      return KubernetesUtils.getResourceMetadata(resource);
    }

    private void setName(@Nonnull Object resource, String name) {
      getMetadata(resource).setName(name);
    }

    List<T> getResources() {
      return new ArrayList<>(data.values());
    }

    @SuppressWarnings("unchecked")
    public void addCreateAction(Consumer<?> consumer) {
      onCreateActions.add((Consumer<T>) consumer);
    }

    @SuppressWarnings("unchecked")
    public void addUpdateAction(Consumer<?> consumer) {
      onUpdateActions.add((Consumer<T>) consumer);
    }
  }

  private class NamespacedDataRepository<T> extends DataRepository<T> {
    private Map<String, DataRepository<T>> repositories = new HashMap<>();
    private Class<?> resourceType;
    private Function<List<T>, Object> listFactory;

    NamespacedDataRepository(Class<?> resourceType, Function<List<T>, Object> listFactory) {
      super(resourceType);
      this.resourceType = resourceType;
      this.listFactory = listFactory;
    }

    @Override
    void createResourceInNamespace(String name, String namespace, Object resource) {
      inNamespace(namespace).createResourceInNamespace(name, namespace, resource);
    }

    @Override
    T createResource(String namespace, T resource) {
      return inNamespace(namespace).createResource(namespace, resource);
    }

    private DataRepository<T> inNamespace(String namespace) {
      return repositories.computeIfAbsent(namespace, n -> new DataRepository<>(resourceType, this));
    }

    @Override
    V1Status deleteResource(String name, String namespace) {
      return inNamespace(namespace).deleteResource(name, namespace);
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
    public T patchResource(String name, String namespace, List<JsonObject> body) {
      return inNamespace(namespace).patchResource(name, namespace, body);
    }

    @Override
    Object listResources(String namespace, String fieldSelector, String... labelSelectors) {
      return listFactory.apply(inNamespace(namespace).getResources(fieldSelector, labelSelectors));
    }

    @Override
    List<T> getResources() {
      List<T> result = new ArrayList<>();
      for (DataRepository<T> repository : repositories.values())
        result.addAll(repository.getResources());
      return result;
    }
  }

  @SuppressWarnings({"unchecked", "unused"})
  private enum Operation {
    create {
      @Override
      Object execute(CallContext callContext, DataRepository dataRepository) {
        return callContext.createResource(dataRepository);
      }

      @Override
      public String getName(RequestParams requestParams) {
        return KubernetesUtils.getResourceName(requestParams.body);
      }
    },
    delete {
      @Override
      Object execute(CallContext callContext, DataRepository dataRepository) {
        return callContext.deleteResource(dataRepository);
      }
    },
    read {
      @Override
      Object execute(CallContext callContext, DataRepository dataRepository) {
        return callContext.readResource(dataRepository);
      }
    },
    replace {
      @Override
      Object execute(CallContext callContext, DataRepository dataRepository) {
        return callContext.replaceResource(dataRepository);
      }
    },
    list {
      @Override
      Object execute(CallContext callContext, DataRepository dataRepository) {
        return callContext.listResources(dataRepository);
      }
    },
    patch {
      @Override
      Object execute(CallContext callContext, DataRepository dataRepository) {
        return callContext.patchResource(dataRepository);
      }
    },
    deleteCollection {
      @Override
      Object execute(CallContext callContext, DataRepository dataRepository) {
        return callContext.deleteCollection(dataRepository);
      }
    };

    abstract Object execute(CallContext callContext, DataRepository dataRepository);

    public String getName(RequestParams requestParams) {
      return requestParams.name;
    }
  }

  private class CallContext {
    private final RequestParams requestParams;
    private String resourceType;
    private Operation operation;
    private final String fieldSelector;
    private final String[] labelSelector;

    CallContext(RequestParams requestParams) {
      this(requestParams, null, null);
    }

    CallContext(RequestParams requestParams, String fieldSelector, String labelSelector) {
      this.requestParams = requestParams;
      this.fieldSelector = fieldSelector;
      this.labelSelector = labelSelector == null ? null : labelSelector.split(",");

      parseCallName(requestParams.call);
    }

    private void parseCallName(String callName) {
      int i = indexOfFirstCapital(callName);
      resourceType = callName.substring(i);
      operation = Operation.valueOf(callName.substring(0, i));

      if (isDeleteCollection()) selectDeleteCollectionOperation();
    }

    private boolean isDeleteCollection() {
      return resourceType.endsWith("Collection");
    }

    private void selectDeleteCollectionOperation() {
      resourceType = resourceType.substring(0, resourceType.indexOf("Collection"));
      operation = Operation.deleteCollection;
    }

    private int indexOfFirstCapital(String callName) {
      for (int i = 0; i < callName.length(); i++)
        if (Character.isUpperCase(callName.charAt(i))) return i;

      throw new RuntimeException(callName + " is not a valid call name");
    }

    private Object execute() {
      if (failure != null && failure.matches(resourceType, requestParams, operation))
        throw failure.getException();

      return operation.execute(this, repositories.get(resourceType));
    }

    @SuppressWarnings("unchecked")
    <T> T createResource(DataRepository<T> dataRepository) {
      return dataRepository.createResource(requestParams.namespace, (T) requestParams.body);
    }

    @SuppressWarnings("unchecked")
    private <T> T replaceResource(DataRepository<T> dataRepository) {
      return dataRepository.replaceResource(requestParams.name, (T) requestParams.body);
    }

    private Object deleteResource(DataRepository dataRepository) {
      return dataRepository.deleteResource(requestParams.name, requestParams.namespace);
    }

    @SuppressWarnings("unchecked")
    private List<JsonObject> asJsonObject(Object body) {
      return (List<JsonObject>) body;
    }

    private Object patchResource(DataRepository dataRepository) {
      return dataRepository.patchResource(
          requestParams.name, requestParams.namespace, asJsonObject(requestParams.body));
    }

    private Object listResources(DataRepository dataRepository) {
      return dataRepository.listResources(requestParams.namespace, fieldSelector, labelSelector);
    }

    private <T> T readResource(DataRepository<T> dataRepository) {
      return dataRepository.readResource(requestParams.name, requestParams.namespace);
    }

    public Object deleteCollection(DataRepository dataRepository) {
      return dataRepository.deleteResourceCollection(requestParams.namespace);
    }
  }

  private class SimulatedResponseStep extends Step {

    private CallContext callContext;

    SimulatedResponseStep(
        Step next, RequestParams requestParams, String fieldSelector, String labelSelector) {
      super(next);
      callContext = new CallContext(requestParams, fieldSelector, labelSelector);
    }

    @Override
    public NextAction apply(Packet packet) {
      try {
        Object callResult = callContext.execute();
        CallResponse callResponse = createResponse(callResult);
        packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(callResponse));
      } catch (NotFoundException e) {
        packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(createResponse(e)));
      } catch (HttpErrorException e) {
        packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(createResponse(e)));
      } catch (Exception e) {
        packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(createResponse(e)));
      }

      return doNext(packet);
    }

    private CallResponse createResponse(Object callResult) {
      return new CallResponse<>(callResult, null, HTTP_OK, emptyMap());
    }

    private CallResponse createResponse(NotFoundException e) {
      return new CallResponse<>(null, new ApiException(e), HTTP_NOT_FOUND, emptyMap());
    }

    private CallResponse createResponse(HttpErrorException e) {
      return new CallResponse<>(null, new ApiException(e), e.status, emptyMap());
    }

    private CallResponse createResponse(Throwable t) {
      return new CallResponse<>(null, new ApiException(t), HTTP_UNAVAILABLE, emptyMap());
    }
  }

  static class Failure {
    private String resourceType;
    private String name;
    private String namespace;
    private int httpStatus;

    public Failure(String resourceType, String name, String namespace, int httpStatus) {
      this.resourceType = resourceType;
      this.name = name;
      this.namespace = namespace;
      this.httpStatus = httpStatus;
    }

    public boolean matches(String resourceType, RequestParams requestParams, Operation operation) {
      return this.resourceType.equals(resourceType)
          && Objects.equals(name, operation.getName(requestParams))
          && Objects.equals(namespace, requestParams.namespace);
    }

    public HttpErrorException getException() {
      return new HttpErrorException(httpStatus);
    }
  }

  class NotFoundException extends RuntimeException {
    public NotFoundException(String resourceType, String name, String namespace) {
      super(String.format("No %s named %s found in namespace %s", resourceType, name, namespace));
    }
  }

  static class HttpErrorException extends RuntimeException {
    private int status;

    public HttpErrorException(int status) {
      this.status = status;
    }
  }
}
