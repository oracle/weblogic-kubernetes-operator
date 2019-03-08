// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_API_SERVICE;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_CLUSTER_ROLE;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_CLUSTER_ROLE_BINDING;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_CONFIG_MAP;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_DEPLOYMENT;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_DOMAIN;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_INGRESS;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_JOB;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_NAMESPACE;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_PERSISTENT_VOLUME;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_PERSISTENT_VOLUME_CLAIM;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_ROLE;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_ROLE_BINDING;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_SECRET;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_SERVICE;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_SERVICE_ACCOUNT;
import static oracle.kubernetes.operator.utils.YamlUtils.newYaml;

import com.appscode.voyager.client.models.V1beta1Ingress;
import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1ClusterRole;
import io.kubernetes.client.models.V1ClusterRoleBinding;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1Role;
import io.kubernetes.client.models.V1RoleBinding;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1beta1APIService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.apache.commons.codec.binary.Base64;

/** Holds the results of a kubernetes yaml file that has been converted to k8s typed java objects */
@SuppressWarnings("unchecked")
public class ParsedKubernetesYaml {

  private Map<String, TypeHandler<?>> kindToHandler = new HashMap<>();
  private int objectCount = 0;

  public ParsedKubernetesYaml(YamlReader factory) throws Exception {
    defineHandlers();

    for (Object document : factory.getYamlDocuments()) {
      add((Map) document);
    }
  }

  // create handlers for all the supported k8s types
  private void defineHandlers() {
    kindToHandler.put(KIND_API_SERVICE, new APIServiceHandler());
    kindToHandler.put(KIND_CONFIG_MAP, new ConfigMapHandler());
    kindToHandler.put(KIND_CLUSTER_ROLE, new ClusterRoleHandler());
    kindToHandler.put(KIND_CLUSTER_ROLE_BINDING, new ClusterRoleBindingHandler());
    kindToHandler.put(KIND_DEPLOYMENT, new DeploymentHandler());
    kindToHandler.put(KIND_DOMAIN, new DomainHandler());
    kindToHandler.put(KIND_INGRESS, new IngressHandler());
    kindToHandler.put(KIND_JOB, new JobHandler());
    kindToHandler.put(KIND_NAMESPACE, new NamespaceHandler());
    kindToHandler.put(KIND_PERSISTENT_VOLUME, new PersistentVolumeHandler());
    kindToHandler.put(KIND_PERSISTENT_VOLUME_CLAIM, new PersistentVolumeClaimHandler());
    kindToHandler.put(KIND_ROLE, new RoleHandler());
    kindToHandler.put(KIND_ROLE_BINDING, new RoleBindingHandler());
    kindToHandler.put(KIND_SECRET, new SecretHandler());
    kindToHandler.put(KIND_SERVICE, new ServiceHandler());
    kindToHandler.put(KIND_SERVICE_ACCOUNT, new ServiceAccountHandler());
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

  TypeHandler<V1beta1APIService> getAPIServices() {
    return (TypeHandler<V1beta1APIService>) getHandler(KIND_API_SERVICE);
  }

  TypeHandler<V1ConfigMap> getConfigMaps() {
    return (TypeHandler<V1ConfigMap>) getHandler(KIND_CONFIG_MAP);
  }

  TypeHandler<V1ClusterRole> getClusterRoles() {
    return (TypeHandler<V1ClusterRole>) getHandler(KIND_CLUSTER_ROLE);
  }

  TypeHandler<V1ClusterRoleBinding> getClusterRoleBindings() {
    return (TypeHandler<V1ClusterRoleBinding>) getHandler(KIND_CLUSTER_ROLE_BINDING);
  }

  TypeHandler<ExtensionsV1beta1Deployment> getDeployments() {
    return (TypeHandler<ExtensionsV1beta1Deployment>) getHandler(KIND_DEPLOYMENT);
  }

  TypeHandler<Domain> getDomains() {
    return (TypeHandler<Domain>) getHandler(KIND_DOMAIN);
  }

  TypeHandler<V1beta1Ingress> getIngresses() {
    return (TypeHandler<V1beta1Ingress>) getHandler(KIND_INGRESS);
  }

  TypeHandler<V1Job> getJobs() {
    return (TypeHandler<V1Job>) getHandler(KIND_JOB);
  }

  TypeHandler<V1Namespace> getNamespaces() {
    return (TypeHandler<V1Namespace>) getHandler(KIND_NAMESPACE);
  }

  TypeHandler<V1PersistentVolume> getPersistentVolumes() {
    return (TypeHandler<V1PersistentVolume>) getHandler(KIND_PERSISTENT_VOLUME);
  }

  TypeHandler<V1PersistentVolumeClaim> getPersistentVolumeClaims() {
    return (TypeHandler<V1PersistentVolumeClaim>) getHandler(KIND_PERSISTENT_VOLUME_CLAIM);
  }

  TypeHandler<V1Role> getRoles() {
    return (TypeHandler<V1Role>) getHandler(KIND_ROLE);
  }

  TypeHandler<V1RoleBinding> getRoleBindings() {
    return (TypeHandler<V1RoleBinding>) getHandler(KIND_ROLE_BINDING);
  }

  TypeHandler<V1Secret> getSecrets() {
    return (TypeHandler<V1Secret>) getHandler(KIND_SECRET);
  }

  TypeHandler<V1Service> getServices() {
    return (TypeHandler<V1Service>) getHandler(KIND_SERVICE);
  }

  TypeHandler<V1ServiceAccount> getServiceAccounts() {
    return (TypeHandler<V1ServiceAccount>) getHandler(KIND_SERVICE_ACCOUNT);
  }

  private TypeHandler<?> getHandler(String kind) {
    TypeHandler<?> handler = kindToHandler.get(kind);
    if (handler == null) {
      throw new AssertionError("Unsupported kubernetes artifact kind : " + kind);
    }
    return handler;
  }

  public abstract static class TypeHandler<T> {

    private Class<?> k8sClass;
    private List<T> instances = new ArrayList<>();

    TypeHandler(Class<?> k8sClass) {
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
      if (result == null) {
        throw new AssertionError(
            "No instance with name '"
                + name
                + "' for the type '"
                + this.getClass()
                + "' among "
                + getInstanceNames());
      }
      return result;
    }

    private String getInstanceNames() {
      StringBuilder sb = new StringBuilder();
      for (T instance : instances) {
        sb.append(sb.length() == 0 ? "[" : ", ");
        sb.append(getName(instance));
      }
      sb.append("]");
      return sb.toString();
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

  private static class APIServiceHandler extends TypeHandler<V1beta1APIService> {
    private APIServiceHandler() {
      super(V1beta1APIService.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1beta1APIService instance) {
      return instance.getMetadata();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void add(Map objectAsMap) {
      convertCaBundleFromBase64EncodedStringToByteArray(objectAsMap);
      super.add(objectAsMap);
    }

    /**
     * The kubernetes server expects that the caBundle in yaml is a base64 encoded string. On the
     * other hand, the kubernetes APIServiceSpec class expects that the caBundle in yaml is a byte
     * array. Convert from a base64 encoded string to a byte array so that the yaml can be parsed
     * into the kubernetes APIServiceSpec class. YUCK! I'm assuming that at some point in the
     * future, the kubernetes APIServiceSpec class will catch up and expect base64 encoded strings
     * too.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void convertCaBundleFromBase64EncodedStringToByteArray(Map objectAsMap) {
      Map specAsMap = (Map) objectAsMap.get("spec");
      if (specAsMap == null) {
        return;
      }
      Object caBundle = specAsMap.get("caBundle");
      if (caBundle == null) return;

      byte[] caBundleAsBytes;
      if (caBundle instanceof byte[]) caBundleAsBytes = (byte[]) caBundle;
      else {
        String caBundleValueAsBase64EncodedString = (String) caBundle;
        caBundleAsBytes = Base64.decodeBase64(caBundleValueAsBase64EncodedString);
      }
      specAsMap.put("caBundle", caBundleAsBytes);
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

  private static class ClusterRoleHandler extends TypeHandler<V1ClusterRole> {
    private ClusterRoleHandler() {
      super(V1ClusterRole.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1ClusterRole instance) {
      return instance.getMetadata();
    }
  }

  private static class ClusterRoleBindingHandler extends TypeHandler<V1ClusterRoleBinding> {
    private ClusterRoleBindingHandler() {
      super(V1ClusterRoleBinding.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1ClusterRoleBinding instance) {
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

  private static class IngressHandler extends TypeHandler<V1beta1Ingress> {
    private IngressHandler() {
      super(V1beta1Ingress.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1beta1Ingress instance) {
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

  private static class RoleHandler extends TypeHandler<V1Role> {
    private RoleHandler() {
      super(V1Role.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1Role instance) {
      return instance.getMetadata();
    }
  }

  private static class RoleBindingHandler extends TypeHandler<V1RoleBinding> {
    private RoleBindingHandler() {
      super(V1RoleBinding.class);
    }

    @Override
    protected V1ObjectMeta getMetadata(V1RoleBinding instance) {
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
        byte[] secretAsBytes =
            secret instanceof String ? decodeString((String) secret) : (byte[]) secret;
        newData.put(secretName, secretAsBytes);
      }
    }

    private static byte[] decodeString(String secret) {
      String secretValueAsBase64EncodedString = secret;
      return Base64.decodeBase64(secretValueAsBase64EncodedString);
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
