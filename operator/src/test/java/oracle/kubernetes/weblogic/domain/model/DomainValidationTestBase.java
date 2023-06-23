// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;

public class DomainValidationTestBase extends DomainTestUtils {

  protected static final String SECRET_NAME = "mysecret";
  protected static final String OVERRIDES_CM_NAME_IMAGE = "overrides-cm-image";
  protected static final String OVERRIDES_CM_NAME_MODEL = "overrides-cm-model";
  protected static final String VOLUME_MOUNT_PATH_1 = "$(MY_ENV)/bin";
  protected static final String ENV_NAME1 = "MY_ENV";
  protected static final String BAD_MY_ENV_VALUE = "projectdir";
  protected static final String GOOD_MY_ENV_VALUE = "/projectdir";
  protected static final String VOLUME_PATH_1 = "/usr";
  protected static final String END_VOLUME_MOUNT_PATH_1 = "/projectdir/bin";

  protected final KubernetesResourceLookupStub resourceLookup = new KubernetesResourceLookupStub();

  @SuppressWarnings("SameParameterValue")
  protected static class KubernetesResourceLookupStub implements KubernetesResourceLookup {
    private final Map<Class<? extends KubernetesObject>, List<? extends KubernetesObject>> definedResources
        = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    <T extends KubernetesObject> List<T> getResourceList(Class<T> type) {
      return (List<T>) definedResources.computeIfAbsent(type, (key) -> new ArrayList<>());
    }

    void undefineResource(String name, Class<? extends KubernetesObject> type, String namespace) {
      getResourceList(type).removeIf(object -> hasSpecification(object.getMetadata(), name, namespace));
    }

    public void defineResource(String name, Class<? extends KubernetesObject> type, String namespace) {
      Optional.ofNullable(getResourceList(type)).orElse(new ArrayList<>())
          .add(getPlaceholder(name, type, namespace));
    }

    @SuppressWarnings("unchecked")
    public void defineResource(KubernetesObject resource) {
      ((List) Optional.ofNullable(getResourceList(resource.getClass())).orElse(new ArrayList<>())).add(resource);
    }

    @SuppressWarnings("unchecked")
    <T extends KubernetesObject> T getPlaceholder(String name,
                                                  Class<? extends KubernetesObject> type, String namespace) {
      V1ObjectMeta meta = new V1ObjectMeta().name(name).namespace(namespace);
      if (V1ConfigMap.class.equals(type)) {
        return (T) new V1ConfigMap().metadata(meta);
      }
      if (V1Secret.class.equals(type)) {
        return (T) new V1Secret().metadata(meta);
      }
      if (ClusterResource.class.equals(type)) {
        return (T) new ClusterResource().withMetadata(meta).withClusterName(name);
      }
      if (DomainResource.class.equals(type)) {
        return (T) new DomainResource().withMetadata(meta);
      }
      throw new IllegalStateException();
    }

    @Override
    public List<V1Secret> getSecrets() {
      return getResourceList(V1Secret.class);
    }

    @Override
    public boolean isConfigMapExists(String name, String namespace) {
      return isResourceExists(name, V1ConfigMap.class, namespace);
    }

    @Override
    public ClusterResource findCluster(V1LocalObjectReference reference) {
      String name = reference.getName();
      if (name != null) {
        List<ClusterResource> clusters = getResourceList(ClusterResource.class);
        return clusters.stream().filter(cluster -> name.equals(cluster.getClusterName()))
            .findFirst().orElse(null);
      }
      return null;
    }

    @Override
    public ClusterResource findClusterInNamespace(V1LocalObjectReference reference, String namespace) {
      String name = reference.getName();
      if (name != null) {
        List<ClusterResource> clusters = getResourceList(ClusterResource.class);
        return clusters.stream().filter(cluster -> nameAndNSMatch(namespace, name, cluster))
            .findFirst().orElse(null);
      }
      return null;
    }

    private boolean nameAndNSMatch(String namespace, String name, ClusterResource cluster) {
      return name.equals(cluster.getMetadata().getName()) && namespace.equals(cluster.getNamespace());
    }

    private boolean isResourceExists(String name, Class<? extends KubernetesObject> type, String namespace) {
      return getResourceList(type).stream().anyMatch(object -> hasSpecification(object.getMetadata(), name, namespace));
    }

    boolean hasSpecification(V1ObjectMeta m, String name, String namespace) {
      return Objects.equals(name, m.getName()) && Objects.equals(namespace, m.getNamespace());
    }

    @Override
    public List<DomainResource> getDomains(String namespace) {
      return getResourceList(DomainResource.class);
    }
  }

}
