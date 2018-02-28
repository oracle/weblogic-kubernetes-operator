// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashMap;
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

import org.apache.commons.codec.binary.Base64;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

/**
 * Holds the results of a kubernetes yaml file that has been converted to k8s typed java objects
 */
public class ParsedKubernetesYaml {

  private static final String KIND_CONFIG_MAP = "ConfigMap";
  private static final String KIND_CLUSTER_ROLE = "ClusterRole";
  private static final String KIND_CLUSTER_ROLE_BINDING = "ClusterRoleBinding";
  private static final String KIND_DEPLOYMENT = "Deployment";
  private static final String KIND_NAMESPACE = "Namespace";
  private static final String KIND_ROLE_BINDING = "RoleBinding";
  private static final String KIND_SECRET = "Secret";
  private static final String KIND_SERVICE = "Service";
  private static final String KIND_SERVICE_ACCOUNT = "ServiceAccount";

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

  public V1ConfigMap getConfigMap(String name) {
    return (V1ConfigMap)find(KIND_CONFIG_MAP, name);
  }

  public V1beta1ClusterRole getClusterRole(String name) {
    return (V1beta1ClusterRole)find(KIND_CLUSTER_ROLE, name);
  }

  public V1beta1ClusterRoleBinding getClusterRoleBinding(String name) {
    return (V1beta1ClusterRoleBinding)find(KIND_CLUSTER_ROLE_BINDING, name);
  }

  public ExtensionsV1beta1Deployment getDeployment(String name) {
    return (ExtensionsV1beta1Deployment)find(KIND_DEPLOYMENT, name);
  }

  public V1Namespace getNamespace(String name) {
    return (V1Namespace)find(KIND_NAMESPACE, name);
  }

  public V1beta1RoleBinding getRoleBinding(String name) {
    return (V1beta1RoleBinding)find(KIND_ROLE_BINDING, name);
  }

  public V1Secret getSecret(String name) {
    return (V1Secret)find(KIND_SECRET, name);
  }

  public V1Service getService(String name) {
    return (V1Service)find(KIND_SERVICE, name);
  }

  public V1ServiceAccount getServiceAccount(String name) {
    return (V1ServiceAccount)find(KIND_SERVICE_ACCOUNT, name);
  }

  private Object find(String kind, String name) {
    return getHandler(kind).find(name);
  }

  private TypeHandler getHandler(String kind) {
    TypeHandler handler = kindToHandler.get(kind);
    if (handler == null) {
      throw new AssertionError("Unsupported kubernetes artifact kind : " + kind);
    }
    return handler;
  }

  public int getInstanceCount() {
    int count = 0;
    for (TypeHandler handler : kindToHandler.values()) {
      count += handler.getInstanceCount();
    }
    return count;
  }

  private static abstract class TypeHandler<T extends Object> {

    private Class k8sClass;
    private Map<String,T> instances = new HashMap<String,T>();

    protected TypeHandler(Class k8sClass) {
      this.k8sClass = k8sClass;
    }

    public void add(Map objectAsMap) {
      // convert the map to a yaml string then convert the yaml string to the
      // corresponding k8s class
      String yaml = newYaml().dump(objectAsMap);
      T instance = (T)newYaml().loadAs(yaml, k8sClass);
      String name = getName(instance);
      instances.put(name, instance);
    }

    protected String getName(T instance) {
      return getMetadata(instance).getName();
    }

    protected abstract V1ObjectMeta getMetadata(T instance);

    public T find(String name) {
      return instances.get(name);
    }

    public int getInstanceCount() {
      return instances.size();
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
