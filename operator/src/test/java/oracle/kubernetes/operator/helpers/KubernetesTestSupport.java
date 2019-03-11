// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static java.util.Collections.emptyMap;
import static oracle.kubernetes.operator.calls.AsyncRequestStep.RESPONSE_COMPONENT_NAME;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ConfigMapList;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobList;
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
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainList;

@SuppressWarnings("WeakerAccess")
public class KubernetesTestSupport extends FiberTestSupport {
  private Map<String, DataRepository<?>> repositories = new HashMap<>();
  private Map<Class<?>, String> dataTypes = new HashMap<>();

  public static final String CONFIG_MAP = "ConfigMap";
  public static final String CUSTOM_RESOURCE_DEFINITION = "CRD";
  public static final String DOMAIN = "Domain";
  public static final String JOB = "Job";
  public static final String PV = "PersistentVolume";
  public static final String PVC = "PersistentVolumeClaim";
  public static final String POD = "Pod";
  public static final String SERVICE = "Service";

  /**
   * Installs a factory into CallBuilder to use canned responses.
   *
   * @return a memento which can be used to restore the production factory
   */
  public Memento install() {
    support(CUSTOM_RESOURCE_DEFINITION, V1beta1CustomResourceDefinition.class);
    supportNamespaced(CONFIG_MAP, V1ConfigMap.class, this::createConfigMapList);
    supportNamespaced(DOMAIN, Domain.class, this::createDomainList);
    supportNamespaced(JOB, V1Job.class, this::createJobList);
    supportNamespaced(POD, V1Pod.class, this::createPodList);
    supportNamespaced(PV, V1PersistentVolume.class, this::createPVList);
    supportNamespaced(PVC, V1PersistentVolumeClaim.class, this::createPVCList);
    supportNamespaced(SERVICE, V1Service.class, this::createServiceList);

    return new KubernetesTestSupport.StepFactoryMemento(
        new KubernetesTestSupport.RequestStepFactory());
  }

  private V1ConfigMapList createConfigMapList(List<V1ConfigMap> items) {
    return new V1ConfigMapList().items(items);
  }

  private DomainList createDomainList(List<Domain> items) {
    return new DomainList().withItems(items);
  }

  private V1PersistentVolumeList createPVList(List<V1PersistentVolume> items) {
    return new V1PersistentVolumeList().items(items);
  }

  private V1PersistentVolumeClaimList createPVCList(List<V1PersistentVolumeClaim> items) {
    return new V1PersistentVolumeClaimList().items(items);
  }

  private V1PodList createPodList(List<V1Pod> items) {
    return new V1PodList().items(items);
  }

  private V1JobList createJobList(List<V1Job> items) {
    return new V1JobList().items(items);
  }

  private V1ServiceList createServiceList(List<V1Service> items) {
    return new V1ServiceList().items(items);
  }

  @SuppressWarnings("SameParameterValue")
  private void support(String resourceName, Class<?> resourceClass) {
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, new DataRepository());
  }

  private <T> void supportNamespaced(
      String resourceName, Class<T> resourceClass, Function<List<T>, Object> toList) {
    dataTypes.put(resourceClass, resourceName);
    repositories.put(resourceName, new NamespacedDataRepository<>(toList));
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> getResources(String resourceType) {
    return ((DataRepository<T>) repositories.get(resourceType)).getResources();
  }

  @SafeVarargs
  public final <T> void defineResources(T... resources) {
    for (T resource : resources) getDataRepository(resource).createResourceInNamespace(resource);
  }

  @SuppressWarnings("unchecked")
  private <T> DataRepository<T> getDataRepository(T resource) {
    return (DataRepository<T>) repositories.get(dataTypes.get(resource.getClass()));
  }

  private static class StepFactoryMemento implements Memento {
    private AsyncRequestStepFactory oldFactory;

    StepFactoryMemento(AsyncRequestStepFactory newFactory) {
      oldFactory = CallBuilder.setStepFactory(newFactory);
    }

    @Override
    public void revert() {
      CallBuilder.resetStepFactory();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOriginalValue() {
      return (T) oldFactory;
    }
  }

  private class RequestStepFactory implements AsyncRequestStepFactory {

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

  private class DataRepository<T> {
    private Map<String, T> data = new HashMap<>();
    private Method getMetadataMethod;

    void createResourceInNamespace(T resource) {
      createResource(getMetadata(resource).getNamespace(), resource);
    }

    T createResource(String namespace, T resource) {
      if (hasElementWithName(getName(resource))) throw new RuntimeException("element exists");

      data.put(getName(resource), resource);
      return resource;
    }

    Object listResources(String namespace, String fieldSelector, String... labelSelectors) {
      throw new UnsupportedOperationException("list operation not supported");
    }

    List<T> getResources(String fieldSelector, String... labelSelectors) {
      assert fieldSelector == null : "field selector not implemented";
      return data.values().stream().filter(withLabels(labelSelectors)).collect(Collectors.toList());
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
      if (!labels.containsKey(key)) return false;
      return value == null || value.equals(labels.get(key));
    }

    T replaceResource(String name, T resource) {
      setName(resource, name);

      data.put(name, resource);
      return resource;
    }

    V1Status deleteResource(String namespace, String name) {
      if (!hasElementWithName(name)) throw new NotFoundException();
      data.remove(name);

      return new V1Status().code(200);
    }

    public T readResource(String namespace, String name) {
      if (!data.containsKey(name)) throw new NotFoundException();
      return data.get(name);
    }

    boolean hasElementWithName(String name) {
      return data.containsKey(name);
    }

    private String getName(@Nonnull Object resource) {
      return getMetadata(resource).getName();
    }

    private V1ObjectMeta getMetadata(@Nonnull Object resource) {
      try {
        return (V1ObjectMeta) getMetadataMethod(resource).invoke(resource);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException("unable to get name");
      }
    }

    private Method getMetadataMethod(Object resource) {
      if (getMetadataMethod == null) {
        try {
          getMetadataMethod = resource.getClass().getDeclaredMethod("getMetadata");
        } catch (NoSuchMethodException e) {
          throw new RuntimeException("unable to get metadata");
        }
      }
      return getMetadataMethod;
    }

    private void setName(@Nonnull Object resource, String name) {
      getMetadata(resource).setName(name);
    }

    List<T> getResources() {
      return new ArrayList<>(data.values());
    }
  }

  private class NamespacedDataRepository<T> extends DataRepository<T> {
    private Map<String, DataRepository<T>> repositories = new HashMap<>();
    private Function<List<T>, Object> listFactory;

    NamespacedDataRepository(Function<List<T>, Object> listFactory) {
      this.listFactory = listFactory;
    }

    @Override
    T createResource(String namespace, T resource) {
      return inNamespace(namespace).createResource(null, resource);
    }

    private DataRepository<T> inNamespace(String namespace) {
      return repositories.computeIfAbsent(namespace, n -> new DataRepository<>());
    }

    @Override
    V1Status deleteResource(String namespace, String name) {
      return inNamespace(namespace).deleteResource(null, name);
    }

    @Override
    public T readResource(String namespace, String name) {
      return inNamespace(namespace).readResource(null, name);
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

  @SuppressWarnings("unchecked")
  private enum Operation {
    create {
      @Override
      Object execute(SimulatedResponseStep simulatedResponseStep, DataRepository dataRepository) {
        return simulatedResponseStep.createResource(dataRepository);
      }
    },
    delete {
      @Override
      Object execute(SimulatedResponseStep simulatedResponseStep, DataRepository dataRepository) {
        return simulatedResponseStep.deleteResource(dataRepository);
      }
    },
    read {
      @Override
      Object execute(SimulatedResponseStep simulatedResponseStep, DataRepository dataRepository) {
        return simulatedResponseStep.readResource(dataRepository);
      }
    },
    replace {
      @Override
      Object execute(SimulatedResponseStep simulatedResponseStep, DataRepository dataRepository) {
        return simulatedResponseStep.replaceResource(dataRepository);
      }
    },
    list {
      @Override
      Object execute(SimulatedResponseStep simulatedResponseStep, DataRepository dataRepository) {
        return simulatedResponseStep.listResources(dataRepository);
      }
    };

    abstract Object execute(
        SimulatedResponseStep simulatedResponseStep, DataRepository dataRepository);
  }

  private class SimulatedResponseStep extends Step {

    private final RequestParams requestParams;
    private final String fieldSelector;
    private final String[] labelSelector;
    private String resourceType;
    private Operation operation;

    SimulatedResponseStep(
        Step next, RequestParams requestParams, String fieldSelector, String labelSelector) {
      super(next);
      this.requestParams = requestParams;
      this.fieldSelector = fieldSelector;
      this.labelSelector = labelSelector == null ? null : labelSelector.split(",");

      parseCallName(requestParams.call);
    }

    private void parseCallName(String callName) {
      int i = indexOfFirstCapital(callName);
      this.resourceType = callName.substring(i);
      this.operation = Operation.valueOf(callName.substring(0, i));
    }

    private int indexOfFirstCapital(String callName) {
      for (int i = 0; i < callName.length(); i++)
        if (Character.isUpperCase(callName.charAt(i))) return i;

      throw new RuntimeException(callName + " is not a valid call name");
    }

    @SuppressWarnings("unchecked")
    <T> T createResource(DataRepository<T> dataRepository) {
      return dataRepository.createResource(requestParams.namespace, (T) requestParams.body);
    }

    @SuppressWarnings("unchecked")
    <T> T replaceResource(DataRepository<T> dataRepository) {
      return dataRepository.replaceResource(requestParams.name, (T) requestParams.body);
    }

    Object deleteResource(DataRepository dataRepository) {
      return dataRepository.deleteResource(requestParams.namespace, requestParams.name);
    }

    Object listResources(DataRepository dataRepository) {
      return dataRepository.listResources(requestParams.namespace, fieldSelector, labelSelector);
    }

    <T> T readResource(DataRepository<T> dataRepository) {
      return dataRepository.readResource(requestParams.namespace, requestParams.name);
    }

    @Override
    public NextAction apply(Packet packet) {
      try {
        Object callResult = operation.execute(this, repositories.get(resourceType));
        CallResponse callResponse = createResponse(callResult);
        packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(callResponse));
      } catch (NotFoundException e) {
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

    private CallResponse createResponse(Throwable t) {
      return new CallResponse<>(null, new ApiException(t), HTTP_UNAVAILABLE, emptyMap());
    }
  }

  static class NotFoundException extends RuntimeException {}
}
