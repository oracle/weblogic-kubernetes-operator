// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1RoleBinding;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.newYaml;
import org.apache.commons.codec.binary.Base64;

/**
 * Holds the results of a kubernetes yaml file that has been converted to k8s typed java objects
 */
public class ParsedKubernetesYaml {

  private Map<String,TypeHandler> kindToHandler = new HashMap();

  public ParsedKubernetesYaml(Path path) throws Exception {
    this(Files.newInputStream(path));
  }

  public ParsedKubernetesYaml(InputStream is) {
    // create handlers for all the supported k8s types
    kindToHandler.put(KIND_CONFIG_MAP, new ConfigMapHandler());
    kindToHandler.put(KIND_CLUSTER_ROLE, new ClusterRoleHandler());
    kindToHandler.put(KIND_CLUSTER_ROLE_BINDING, new ClusterRoleBindingHandler());
    kindToHandler.put(KIND_DEPLOYMENT, new DeploymentHandler());
    kindToHandler.put(KIND_NAMESPACE, new NamespaceHandler());
    kindToHandler.put(KIND_ROLE_BINDING, new RoleBindingHandler());
    kindToHandler.put(KIND_SECRET, new SecretHandler());
    kindToHandler.put(KIND_SERVICE, new ServiceHandler());
    kindToHandler.put(KIND_SERVICE_ACCOUNT, new ServiceAccountHandler());

    // convert the input stream into a set of maps that represent the yaml
    for (Object object : newYaml().loadAll(is)) {
      // convert each map to its corresponding k8s class
      add((Map)object);
    }
  }

  public void add(Map objectAsMap) {
    if (objectAsMap == null) {
      // there is no object.  e.g. the yaml has:
      // ---
      // ---
      return;
    }
    // extract the kind of k8s object from the map so that we can figure out
    // what k8s class handles that kind of k8s object
    String kind = (String)objectAsMap.get("kind");
    getHandler(kind).add(objectAsMap);
  }

  public TypeHandler<V1ConfigMap> getConfigMaps() {
    return (TypeHandler<V1ConfigMap>)getHandler(KIND_CONFIG_MAP);
  }

  public TypeHandler<V1beta1ClusterRole> getClusterRoles() {
    return (TypeHandler<V1beta1ClusterRole>)getHandler(KIND_CLUSTER_ROLE);
  }

  public TypeHandler<V1beta1ClusterRoleBinding> getClusterRoleBindings() {
    return (TypeHandler<V1beta1ClusterRoleBinding>)getHandler(KIND_CLUSTER_ROLE_BINDING);
  }

  public TypeHandler<ExtensionsV1beta1Deployment> getDeployments() {
    return (TypeHandler<ExtensionsV1beta1Deployment>)getHandler(KIND_DEPLOYMENT);
  }

  public TypeHandler<V1Namespace> getNamespaces() {
    return (TypeHandler<V1Namespace>)getHandler(KIND_NAMESPACE);
  }

  public TypeHandler<V1beta1RoleBinding> getRoleBindings() {
    return (TypeHandler<V1beta1RoleBinding>)getHandler(KIND_ROLE_BINDING);
  }

  public TypeHandler<V1Secret> getSecrets() {
    return (TypeHandler<V1Secret>)getHandler(KIND_SECRET);
  }

  public TypeHandler<V1Service> getServices() {
    return (TypeHandler<V1Service>)getHandler(KIND_SERVICE);
  }

  public TypeHandler<V1ServiceAccount> getServiceAccounts() {
    return (TypeHandler<V1ServiceAccount>)getHandler(KIND_SERVICE_ACCOUNT);
  }

  private TypeHandler getHandler(String kind) {
    TypeHandler handler = kindToHandler.get(kind);
    if (handler == null) {
      throw new AssertionError("Unsupported kubernetes artifact kind : " + kind);
    }
    return handler;
  }

  public static abstract class TypeHandler<T extends Object> {

    private Class k8sClass;
    private List<T> instances = new ArrayList();

    protected TypeHandler(Class k8sClass) {
      this.k8sClass = k8sClass;
    }

    public T find(String name) {
      T result = null;
      for (T instance : instances) {
        if (name.equals(getName(instance))) {
          if (result == null) {
            result = instance;
          } else {
            throw new AssertionError("Found more than one instance with the name '" + name + "' for the type '" + this.getClass() + "'");
          }
        }
      }
      return result;
    }

    public T find(String name, String namespace) {
      T result = null;
      for (T instance : instances) {
        V1ObjectMeta md = getMetadata(instance);
        if (name.equals(getName(instance)) && namespace.equals(getNamespace(instance))) {
          if (result == null) {
            result = instance;
          } else {
            throw new AssertionError("Found more than one instance with the name '" + name + "' and namespace '" + namespace + "' for the type '" + this.getClass() + "'");
          }
        }
      }
      return result;
    }

    public void add(Map objectAsMap) {
      // convert the map to a yaml string then convert the yaml string to the
      // corresponding k8s class
      String yaml = newYaml().dump(objectAsMap);
      T instance = (T)newYaml().loadAs(yaml, k8sClass);
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
    private ConfigMapHandler() { super(V1ConfigMap.class); }
    @Override protected V1ObjectMeta getMetadata(V1ConfigMap instance) { return instance.getMetadata(); }
  }

  private static class ClusterRoleHandler extends TypeHandler<V1beta1ClusterRole> {
    private ClusterRoleHandler() { super(V1beta1ClusterRole.class); }
    @Override protected V1ObjectMeta getMetadata(V1beta1ClusterRole instance) { return instance.getMetadata(); }
  }

  private static class ClusterRoleBindingHandler extends TypeHandler<V1beta1ClusterRoleBinding> {
    private ClusterRoleBindingHandler() { super(V1beta1ClusterRoleBinding.class); }
    @Override protected V1ObjectMeta getMetadata(V1beta1ClusterRoleBinding instance) { return instance.getMetadata(); }
  }

  private static class DeploymentHandler extends TypeHandler<ExtensionsV1beta1Deployment> {
    private DeploymentHandler() { super(ExtensionsV1beta1Deployment.class); }
    @Override protected V1ObjectMeta getMetadata(ExtensionsV1beta1Deployment instance) { return instance.getMetadata(); }
  }

  private static class NamespaceHandler extends TypeHandler<V1Namespace> {
    private NamespaceHandler() { super(V1Namespace.class); }
    @Override protected V1ObjectMeta getMetadata(V1Namespace instance) { return instance.getMetadata(); }
  }

  private static class RoleBindingHandler extends TypeHandler<V1beta1RoleBinding> {
    private RoleBindingHandler() { super(V1beta1RoleBinding.class); }
    @Override protected V1ObjectMeta getMetadata(V1beta1RoleBinding instance) { return instance.getMetadata(); }
  }

  private static class SecretHandler extends TypeHandler<V1Secret> {
    private SecretHandler() { super(V1Secret.class); }
    @Override protected V1ObjectMeta getMetadata(V1Secret instance) { return instance.getMetadata(); }

    @Override public void add(Map objectAsMap) {
      convertSecretsFromBase64EncodedStringsToByteArrays(objectAsMap);
      super.add(objectAsMap);
    }

    /**
     * The kubernetes server expects that secrets in yaml are base64 encoded strings.
     * On the other hand, the kubernetes secrets class expects that secrets in yaml are byte arrays.
     * Convert from base64 encoded strings to byte arrays to that the yaml can be parsed
     * into the kubernetes secrets class. YUCK!
     * I'm assuming that at some point in the future, the kubernetes secrets class will catch
     * up and expect base64 encoded strings too.
     */
    private static void convertSecretsFromBase64EncodedStringsToByteArrays(Map objectAsMap) {
      Map origData = (Map)objectAsMap.get("data");
      if (origData == null || origData.size() == 0) {
        return;
      }
      Map newData = new HashMap();
      objectAsMap.put("data", newData);
      for (Object secretName : origData.keySet()) {
        Object secret = origData.get(secretName);
        String secretValueAsBase64EncodedString = (String)secret;
        byte[] secretAsBytes = Base64.decodeBase64(secretValueAsBase64EncodedString);
        newData.put(secretName, secretAsBytes);
      }
    }
  }

  private static class ServiceHandler extends TypeHandler<V1Service> {
    private ServiceHandler() { super(V1Service.class); }
    @Override protected V1ObjectMeta getMetadata(V1Service instance) { return instance.getMetadata(); }
  }

  private static class ServiceAccountHandler extends TypeHandler<V1ServiceAccount> {
    private ServiceAccountHandler() { super(V1ServiceAccount.class); }
    @Override protected V1ObjectMeta getMetadata(V1ServiceAccount instance) { return instance.getMetadata(); }
  }
}
