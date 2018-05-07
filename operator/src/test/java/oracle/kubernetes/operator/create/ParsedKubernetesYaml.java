// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1RoleBinding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import org.apache.commons.codec.binary.Base64;

/** Holds the results of a kubernetes yaml file that has been converted to k8s typed java objects */
public class ParsedKubernetesYaml {

  private Map<String, TypeHandler<?>> kindToHandler = new HashMap<>();
  private int objectCount = 0;

  @SuppressWarnings("rawtypes")
  protected ParsedKubernetesYaml(Path path) throws Exception {
    // create handlers for all the supported k8s types
    kindToHandler.put(KIND_CONFIG_MAP, new ConfigMapHandler());
    kindToHandler.put(KIND_CLUSTER_ROLE, new ClusterRoleHandler());
    kindToHandler.put(KIND_CLUSTER_ROLE_BINDING, new ClusterRoleBindingHandler());
    kindToHandler.put(KIND_DEPLOYMENT, new DeploymentHandler());
    kindToHandler.put(KIND_DOMAIN, new DomainHandler());
    kindToHandler.put(KIND_JOB, new JobHandler());
    kindToHandler.put(KIND_NAMESPACE, new NamespaceHandler());
    kindToHandler.put(KIND_PERSISTENT_VOLUME, new PersistentVolumeHandler());
    kindToHandler.put(KIND_PERSISTENT_VOLUME_CLAIM, new PersistentVolumeClaimHandler());
    kindToHandler.put(KIND_ROLE_BINDING, new RoleBindingHandler());
    kindToHandler.put(KIND_SECRET, new SecretHandler());
    kindToHandler.put(KIND_SERVICE, new ServiceHandler());
    kindToHandler.put(KIND_SERVICE_ACCOUNT, new ServiceAccountHandler());

    // convert the input stream into a set of maps that represent the yaml
    for (Object object : newYaml().loadAll(Files.newInputStream(path))) {
      // convert each map to its corresponding k8s class
      // printObject("", object);
      add((Map) object);
    }
  }

  public int getObjectCount() {
    return objectCount;
  }

  @SuppressWarnings("rawtypes")
  private void add(Map objectAsMap) {
    if (objectAsMap == null) {
      // there is no object.  e.g. the yaml has:
      // ---
      // ---
      return;
    }
    // extract the kind of k8s object from the map so that we can figure out
    // what k8s class handles that kind of k8s object
    String kind = (String) objectAsMap.get("kind");
    getHandler(kind).add(objectAsMap);
    objectCount++;
  }

  /*
    private void printObject(String indent, Object obj) {
      if (obj == null) {
        System.out.println("MOREAUT_DEBUG " + indent + " obj null");
      } else {
        System.out.println("MOREAUT_DEBUG " + indent + " obj " + obj.getClass());
        indent = indent + "  ";
        if (obj instanceof Map) {
          Map map = (Map)obj;
          for (Map.Entry e : (java.util.Set<Map.Entry>)(map.entrySet())) {
            Object key = e.getKey();
            Object val = e.getValue();
            System.out.println("MOREAUT_DEBUG " + indent + " key " + key.getClass() + " " + key);
            printObject(indent + "  ", val);
          }
        } else if (obj instanceof java.util.List) {
          java.util.List list = (java.util.List)obj;
          for (int i = 0; i < list.size(); i++) {
            System.out.println("MOREAUT_DEBUG " + indent + " item " + i);
            printObject(indent + " ", list.get(i));
          }
        } else {
          System.out.println("MOREAUT_DEBUG " + indent + " random val " + obj);
        }
      }
    }
  */

  @SuppressWarnings("unchecked")
  public TypeHandler<V1ConfigMap> getConfigMaps() {
    return (TypeHandler<V1ConfigMap>) getHandler(KIND_CONFIG_MAP);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<V1beta1ClusterRole> getClusterRoles() {
    return (TypeHandler<V1beta1ClusterRole>) getHandler(KIND_CLUSTER_ROLE);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<V1beta1ClusterRoleBinding> getClusterRoleBindings() {
    return (TypeHandler<V1beta1ClusterRoleBinding>) getHandler(KIND_CLUSTER_ROLE_BINDING);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<ExtensionsV1beta1Deployment> getDeployments() {
    return (TypeHandler<ExtensionsV1beta1Deployment>) getHandler(KIND_DEPLOYMENT);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<Domain> getDomains() {
    return (TypeHandler<Domain>) getHandler(KIND_DOMAIN);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<V1Job> getJobs() {
    return (TypeHandler<V1Job>) getHandler(KIND_JOB);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<V1Namespace> getNamespaces() {
    return (TypeHandler<V1Namespace>) getHandler(KIND_NAMESPACE);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<V1PersistentVolume> getPersistentVolumes() {
    return (TypeHandler<V1PersistentVolume>) getHandler(KIND_PERSISTENT_VOLUME);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<V1PersistentVolumeClaim> getPersistentVolumeClaims() {
    return (TypeHandler<V1PersistentVolumeClaim>) getHandler(KIND_PERSISTENT_VOLUME_CLAIM);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<V1beta1RoleBinding> getRoleBindings() {
    return (TypeHandler<V1beta1RoleBinding>) getHandler(KIND_ROLE_BINDING);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<V1Secret> getSecrets() {
    return (TypeHandler<V1Secret>) getHandler(KIND_SECRET);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<V1Service> getServices() {
    return (TypeHandler<V1Service>) getHandler(KIND_SERVICE);
  }

  @SuppressWarnings("unchecked")
  public TypeHandler<V1ServiceAccount> getServiceAccounts() {
    return (TypeHandler<V1ServiceAccount>) getHandler(KIND_SERVICE_ACCOUNT);
  }

  private TypeHandler<?> getHandler(String kind) {
    TypeHandler<?> handler = kindToHandler.get(kind);
    if (handler == null) {
      throw new AssertionError("Unsupported kubernetes artifact kind : " + kind);
    }
    return handler;
  }

  public abstract static class TypeHandler<T extends Object> {

    private Class<?> k8sClass;
    private List<T> instances = new ArrayList<>();

    protected TypeHandler(Class<?> k8sClass) {
      this.k8sClass = k8sClass;
    }

    public T find(String name) {
      T result = null;
      for (T instance : instances) {
        if (name.equals(getName(instance))) {
          if (result == null) {
            result = instance;
          } else {
            throw new AssertionError(
                "Found more than one instance with the name '"
                    + name
                    + "' for the type '"
                    + this.getClass()
                    + "'");
          }
        }
      }
      return result;
    }

    protected T find(String name, String namespace) {
      T result = null;
      for (T instance : instances) {
        // V1ObjectMeta md = getMetadata(instance);
        if (name.equals(getName(instance)) && namespace.equals(getNamespace(instance))) {
          if (result == null) {
            result = instance;
          } else {
            throw new AssertionError(
                "Found more than one instance with the name '"
                    + name
                    + "' and namespace '"
                    + namespace
                    + "' for the type '"
                    + this.getClass()
                    + "'");
          }
        }
      }
      return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void add(Map objectAsMap) {
      // convert the map to a yaml string then convert the yaml string to the
      // corresponding k8s class
      String yaml = newYaml().dump(objectAsMap);
      T instance = (T) newYaml().loadAs(yaml, k8sClass);
      instances.add(instance);
    }

    protected abstract V1ObjectMeta getMetadata(T instance);

    private String getName(T instance) {
      return getMetadata(instance).getName();
    }

    private String getNamespace(T instance) {
      return getMetadata(instance).getNamespace();
    }
  }

  private static class ConfigMapHandler extends TypeHandler<V1ConfigMap> {
    private ConfigMapHandler() {
      super(V1ConfigMap.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1ConfigMap instance) {
      return instance.getMetadata();
    }
  }

  private static class ClusterRoleHandler extends TypeHandler<V1beta1ClusterRole> {
    private ClusterRoleHandler() {
      super(V1beta1ClusterRole.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1beta1ClusterRole instance) {
      return instance.getMetadata();
    }
  }

  private static class ClusterRoleBindingHandler extends TypeHandler<V1beta1ClusterRoleBinding> {
    private ClusterRoleBindingHandler() {
      super(V1beta1ClusterRoleBinding.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1beta1ClusterRoleBinding instance) {
      return instance.getMetadata();
    }
  }

  private static class DeploymentHandler extends TypeHandler<ExtensionsV1beta1Deployment> {
    private DeploymentHandler() {
      super(ExtensionsV1beta1Deployment.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(ExtensionsV1beta1Deployment instance) {
      return instance.getMetadata();
    }
  }

  private static class DomainHandler extends TypeHandler<Domain> {
    private DomainHandler() {
      super(Domain.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(Domain instance) {
      return instance.getMetadata();
    }
  }

  private static class JobHandler extends TypeHandler<V1Job> {
    private JobHandler() {
      super(V1Job.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1Job instance) {
      return instance.getMetadata();
    }
  }

  private static class NamespaceHandler extends TypeHandler<V1Namespace> {
    private NamespaceHandler() {
      super(V1Namespace.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1Namespace instance) {
      return instance.getMetadata();
    }
  }

  private static class PersistentVolumeHandler extends TypeHandler<V1PersistentVolume> {
    private PersistentVolumeHandler() {
      super(V1PersistentVolume.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1PersistentVolume instance) {
      return instance.getMetadata();
    }
  }

  private static class PersistentVolumeClaimHandler extends TypeHandler<V1PersistentVolumeClaim> {
    private PersistentVolumeClaimHandler() {
      super(V1PersistentVolumeClaim.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1PersistentVolumeClaim instance) {
      return instance.getMetadata();
    }
  }

  private static class RoleBindingHandler extends TypeHandler<V1beta1RoleBinding> {
    private RoleBindingHandler() {
      super(V1beta1RoleBinding.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1beta1RoleBinding instance) {
      return instance.getMetadata();
    }
  }

  private static class SecretHandler extends TypeHandler<V1Secret> {
    private SecretHandler() {
      super(V1Secret.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1Secret instance) {
      return instance.getMetadata();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void add(Map objectAsMap) {
      convertSecretsFromBase64EncodedStringsToByteArrays(objectAsMap);
      super.add(objectAsMap);
    }

    /**
     * The kubernetes server expects that secrets in yaml are base64 encoded strings. On the other
     * hand, the kubernetes secrets class expects that secrets in yaml are byte arrays. Convert from
     * base64 encoded strings to byte arrays to that the yaml can be parsed into the kubernetes
     * secrets class. YUCK! I'm assuming that at some point in the future, the kubernetes secrets
     * class will catch up and expect base64 encoded strings too.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void convertSecretsFromBase64EncodedStringsToByteArrays(Map objectAsMap) {
      Map origData = (Map) objectAsMap.get("data");
      if (origData == null || origData.size() == 0) {
        return;
      }
      Map newData = new HashMap();
      objectAsMap.put("data", newData);
      for (Object secretName : origData.keySet()) {
        Object secret = origData.get(secretName);
        String secretValueAsBase64EncodedString = (String) secret;
        byte[] secretAsBytes = Base64.decodeBase64(secretValueAsBase64EncodedString);
        newData.put(secretName, secretAsBytes);
      }
    }
  }

  private static class ServiceHandler extends TypeHandler<V1Service> {
    private ServiceHandler() {
      super(V1Service.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1Service instance) {
      return instance.getMetadata();
    }
  }

  private static class ServiceAccountHandler extends TypeHandler<V1ServiceAccount> {
    private ServiceAccountHandler() {
      super(V1ServiceAccount.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1ServiceAccount instance) {
      return instance.getMetadata();
    }
  }
}
