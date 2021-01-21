// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.kubernetes.client.Copy;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.apis.NetworkingV1beta1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressList;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ClusterRoleBindingList;
import io.kubernetes.client.openapi.models.V1ClusterRoleList;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1EventList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceBuilder;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetList;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1RoleBindingList;
import io.kubernetes.client.openapi.models.V1RoleList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.PatchUtils;
import io.kubernetes.client.util.exception.CopyNotSupportedException;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// TODO ryan - in here we want to implement all of the kubernetes
// primitives that we need, using the API, not spawning a process
// to run kubectl.
public class Kubernetes {

  private static String PRETTY = "true";
  private static Boolean ALLOW_WATCH_BOOKMARKS = false;
  private static String RESOURCE_VERSION = "";
  private static Integer TIMEOUT_SECONDS = 5;
  private static String DOMAIN_GROUP = "weblogic.oracle";
  private static String DOMAIN_VERSION = "v8";
  private static String DOMAIN_PLURAL = "domains";
  private static String FOREGROUND = "Foreground";
  private static String BACKGROUND = "Background";
  private static int GRACE_PERIOD = 0;

  // Core Kubernetes API clients
  private static ApiClient apiClient = null;
  private static CoreV1Api coreV1Api = null;
  private static CustomObjectsApi customObjectsApi = null;
  private static RbacAuthorizationV1Api rbacAuthApi = null;
  private static DeleteOptions deleteOptions = null;

  // Extended GenericKubernetesApi clients
  private static GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> configMapClient = null;
  private static GenericKubernetesApi<V1ClusterRoleBinding, V1ClusterRoleBindingList> roleBindingClient = null;
  private static GenericKubernetesApi<Domain, DomainList> crdClient = null;
  private static GenericKubernetesApi<V1Deployment, V1DeploymentList> deploymentClient = null;
  private static GenericKubernetesApi<V1Job, V1JobList> jobClient = null;
  private static GenericKubernetesApi<V1Namespace, V1NamespaceList> namespaceClient = null;
  private static GenericKubernetesApi<V1Pod, V1PodList> podClient = null;
  private static GenericKubernetesApi<V1PersistentVolume, V1PersistentVolumeList> pvClient = null;
  private static GenericKubernetesApi<V1PersistentVolumeClaim, V1PersistentVolumeClaimList> pvcClient = null;
  private static GenericKubernetesApi<V1ReplicaSet, V1ReplicaSetList> rsClient = null;
  private static GenericKubernetesApi<V1Secret, V1SecretList> secretClient = null;
  private static GenericKubernetesApi<V1Service, V1ServiceList> serviceClient = null;
  private static GenericKubernetesApi<V1ServiceAccount, V1ServiceAccountList> serviceAccountClient = null;

  private static ConditionFactory withStandardRetryPolicy = null;

  static {
    try {
      Configuration.setDefaultApiClient(ClientBuilder.defaultClient());
      apiClient = Configuration.getDefaultApiClient();
      // disable connection and read write timeout to force the internal HTTP client
      // to keep a long running connection with the server to fix SSL connection closed issue
      apiClient.setConnectTimeout(0);
      apiClient.setReadTimeout(0);
      coreV1Api = new CoreV1Api();
      customObjectsApi = new CustomObjectsApi();
      rbacAuthApi = new RbacAuthorizationV1Api();
      initializeGenericKubernetesApiClients();
      // create standard, reusable retry/backoff policy
      withStandardRetryPolicy = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();
    } catch (IOException ioex) {
      throw new ExceptionInInitializerError(ioex);
    }
  }

  /**
   * Create static instances of GenericKubernetesApi clients.
   */
  private static void initializeGenericKubernetesApiClients() {
    // Invocation parameters aren't changing so create them as statics
    configMapClient =
        new GenericKubernetesApi<>(
            V1ConfigMap.class,  // the api type class
            V1ConfigMapList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "configmaps", // the resource plural
            apiClient //the api client
        );

    crdClient =
        new GenericKubernetesApi<>(
            Domain.class,  // the api type class
            DomainList.class, // the api list type class
            DOMAIN_GROUP, // the api group
            DOMAIN_VERSION, // the api version
            DOMAIN_PLURAL, // the resource plural
            apiClient //the api client
        );

    deploymentClient =
        new GenericKubernetesApi<>(
            V1Deployment.class,  // the api type class
            V1DeploymentList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "deployments", // the resource plural
            apiClient //the api client
        );

    jobClient =
        new GenericKubernetesApi<>(
            V1Job.class,  // the api type class
            V1JobList.class, // the api list type class
            "batch", // the api group
            "v1", // the api version
            "jobs", // the resource plural
            apiClient //the api client
        );

    namespaceClient =
        new GenericKubernetesApi<>(
            V1Namespace.class, // the api type class
            V1NamespaceList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "namespaces", // the resource plural
            apiClient //the api client
        );

    podClient =
        new GenericKubernetesApi<>(
            V1Pod.class,  // the api type class
            V1PodList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "pods", // the resource plural
            apiClient //the api client
        );

    pvClient =
        new GenericKubernetesApi<>(
            V1PersistentVolume.class,  // the api type class
            V1PersistentVolumeList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "persistentvolumes", // the resource plural
            apiClient //the api client
        );

    pvcClient =
        new GenericKubernetesApi<>(
            V1PersistentVolumeClaim.class,  // the api type class
            V1PersistentVolumeClaimList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "persistentvolumeclaims", // the resource plural
            apiClient //the api client
        );

    rsClient =
        new GenericKubernetesApi<>(
            V1ReplicaSet.class, // the api type class
            V1ReplicaSetList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "replicasets", // the resource plural
            apiClient //the api client
        );

    roleBindingClient =
        new GenericKubernetesApi<>(
            V1ClusterRoleBinding.class, // the api type class
            V1ClusterRoleBindingList.class, // the api list type class
            "rbac.authorization.k8s.io", // the api group
            "v1", // the api version
            "clusterrolebindings", // the resource plural
            apiClient //the api client
        );

    secretClient =
        new GenericKubernetesApi<>(
            V1Secret.class,  // the api type class
            V1SecretList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "secrets", // the resource plural
            apiClient //the api client
        );

    serviceClient =
        new GenericKubernetesApi<>(
            V1Service.class,  // the api type class
            V1ServiceList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "services", // the resource plural
            apiClient //the api client
        );

    serviceAccountClient =
        new GenericKubernetesApi<>(
            V1ServiceAccount.class,  // the api type class
            V1ServiceAccountList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "serviceaccounts", // the resource plural
            apiClient //the api client
        );
    deleteOptions = new DeleteOptions();
    deleteOptions.setGracePeriodSeconds(0L);
    deleteOptions.setPropagationPolicy(FOREGROUND);
  }

  // ------------------------  deployments -----------------------------------

  /**
   * Create a deployment.
   *
   * @param deployment V1Deployment object containing deployment configuration data
   * @return true if creation was successful
   * @throws ApiException when create fails
   */
  public static boolean createDeployment(V1Deployment deployment) throws ApiException {
    String namespace = deployment.getMetadata().getNamespace();
    boolean status = false;
    try {
      AppsV1Api apiInstance = new AppsV1Api(apiClient);
      V1Deployment createdDeployment = apiInstance.createNamespacedDeployment(
          namespace, // String | namespace in which to create job
          deployment, // V1Deployment | body of the V1Deployment containing deployment data
          PRETTY, // String | pretty print output.
          null, // String | dry run or permanent change
          null // String | field manager who is making the change
      );
      if (createdDeployment != null) {
        status = true;
      }
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return status;
  }

  /**
   * List deployments in the given namespace.
   *
   * @param namespace namespace in which to list the deployments
   * @return list of deployment objects as {@link V1DeploymentList}
   * @throws ApiException when listing fails
   */
  public static V1DeploymentList listDeployments(String namespace) throws ApiException {
    V1DeploymentList deployments;
    try {
      AppsV1Api apiInstance = new AppsV1Api(apiClient);
      deployments = apiInstance.listNamespacedDeployment(
          namespace, // String | namespace.
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );

    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return deployments;
  }

  /**
   * Delete a deployment.
   *
   * @param namespace namespace in which to delete the deployment
   * @param name deployment name
   * @return true if deletion was successful
   * @throws ApiException when delete fails
   */
  public static boolean deleteDeployment(String namespace, String name) throws ApiException {
    try {
      AppsV1Api apiInstance = new AppsV1Api(apiClient);
      apiInstance.deleteNamespacedDeployment(
          name, // String | deployment object name.
          namespace, // String | namespace in which the deployment exists.
          PRETTY, // String | If 'true', then the output is pretty printed.
          null, // String | When present, indicates that modifications should not be persisted.
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null, // Boolean | Deprecated: use the PropagationPolicy.
          FOREGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return true;
  }

  // --------------------------- pods -----------------------------------------

  /**
   * Get a pod's log.
   *
   * @param name name of the Pod
   * @param namespace name of the Namespace
   * @return log as a String
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodLog(String name, String namespace) throws ApiException {
    return getPodLog(name, namespace, null);
  }

  /**
   * Get a pod's log.
   *
   * @param name name of the Pod
   * @param namespace name of the Namespace
   * @param container name of container for which to stream logs
   * @return log as a String or NULL when there is an error
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodLog(String name, String namespace, String container) throws ApiException {
    return getPodLog(name, namespace, container, null, null);
  }

  /**
   * Get a pod's log.
   *
   * @param name name of the Pod
   * @param namespace name of the Namespace
   * @param container name of container for which to stream logs
   * @param previous whether return previous terminated container logs
   * @return log as a String or NULL when there is an error
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodLog(String name,
                                 String namespace,
                                 String container,
                                 Boolean previous,
                                 Integer sinceSeconds)
      throws ApiException {
    String log = null;
    try {
      log = coreV1Api.readNamespacedPodLog(
          name, // name of the Pod
          namespace, // name of the Namespace
          container, // container for which to stream logs
          null, //  Boolean Follow the log stream of the pod
          null, // skip TLS verification of backend
          null, // number of bytes to read from the server before terminating the log output
          PRETTY, // pretty print output
          previous, // Boolean, Return previous terminated container logs
          sinceSeconds, // relative time (seconds) before the current time from which to show logs
          null, // number of lines from the end of the logs to show
          null // Boolean, add timestamp at the beginning of every line of log output
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return log;
  }

  /**
   * Create a pod.
   *
   * @param namespace name of the namespace
   * @param podBody V1Pod object containing pod configuration data
   * @return V1Pod object
   * @throws ApiException when create pod fails
   */
  public static V1Pod createPod(String namespace, V1Pod podBody) throws ApiException {
    V1Pod pod;
    try {
      pod = coreV1Api.createNamespacedPod(namespace, podBody, null, null, null);
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return pod;
  }

  /**
   * Delete a Kubernetes Pod.
   *
   * @param name name of the pod
   * @param namespace name of namespace
   * @return true if successful
   */
  public static boolean deletePod(String name, String namespace) {

    KubernetesApiResponse<V1Pod> response = podClient.delete(namespace, name);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete pod '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "pod in background!");
    }

    return true;
  }

  /**
   * Returns the V1Pod object given the following parameters.
   *
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod to return
   * @return V1Pod object if found otherwise null
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1Pod getPod(String namespace, String labelSelector, String podName) throws ApiException {
    V1PodList pods = listPods(namespace, labelSelector);
    for (var pod : pods.getItems()) {
      if (pod.getMetadata().getName().contains(podName)) {
        return pod;
      }
    }
    return null;
  }

  /**
   * Returns the IP address allocated to the pod with following parameters.
   *
   * @param namespace in which to check for the pod existence
   * @param labelSelectors in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod to return
   * @return IP address allocated to the pod
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodIP(String namespace, String labelSelectors, String podName) throws ApiException {
    V1Pod pod = getPod(namespace, labelSelectors, podName);
    return pod.getStatus().getPodIP();
  }

  /**
   * Get the creationTimestamp for a given pod with following parameters.
   *
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName  name of the pod
   * @return creationTimestamp DateTime from metadata of the Pod
   * @throws ApiException if Kubernetes client API call fail
   */
  public static DateTime getPodCreationTimestamp(String namespace, String labelSelector, String podName)
      throws ApiException {

    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null && pod.getMetadata() != null) {
      return pod.getMetadata().getCreationTimestamp();
    } else {
      getLogger().info("Pod doesn't exist or pod metadata is null");
      return null;
    }
  }

  /**
   * Get the container's restart count in the pod.
   * @param namespace name of the pod's namespace
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod
   * @param containerName name of the container, null if there is only one container
   * @return restart count of the container
   * @throws ApiException if Kubernetes client API call fails
   */
  public static int getContainerRestartCount(
      String namespace, String labelSelector, String podName, String containerName)
      throws ApiException {

    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null && pod.getStatus() != null) {
      List<V1ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
      // if containerName is null, get first container restart count
      if (containerName == null && containerStatuses.size() >= 1) {
        return containerStatuses.get(0).getRestartCount();
      } else {
        for (V1ContainerStatus containerStatus : containerStatuses) {
          if (containerName.equals(containerStatus.getName())) {
            return containerStatus.getRestartCount();
          }
        }
        getLogger().severe("Container {0} status doesn't exist or pod's container statuses is empty in namespace {1}",
            containerName, namespace);
      }
    } else {
      getLogger().severe("Pod {0} doesn't exist or pod status is null in namespace {1}",
          podName, namespace);
    }
    return 0;
  }

  /**
   * Get the container's image in the pod.
   * @param namespace name of the pod's namespace
   * @param labelSelector in the format "weblogic.operatorName in (%s)"
   * @param podName name of the pod
   * @param containerName name of the specific container if more then one, null if there is only one container
   * @return image used for the container
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getContainerImage(String namespace, String podName,
                                         String labelSelector, String containerName) throws ApiException {
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {
      List<V1Container> containerList = pod.getSpec().getContainers();
      if (containerName == null && containerList.size() >= 1) {
        return containerList.get(0).getImage();
      } else {
        for (V1Container container : containerList) {
          if (containerName.equals(container.getName())) {
            return container.getImage();
          }
        }
        getLogger().info("Container {0} doesn't exist in pod {1} namespace {2}",
            containerName, podName, namespace);
      }
    } else {
      getLogger().severe("Pod " + podName + " doesn't exist in namespace " + namespace);
    }
    return null;
  }

  /**
   * Get the weblogic.domainRestartVersion label from a given pod.
   *
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName  name of the pod
   * @return value of weblogic.domainRestartVersion label, null if unset or the pod is not available
   * @throws ApiException when there is error in querying the cluster
   */
  public static String getPodRestartVersion(String namespace, String labelSelector, String podName)
      throws ApiException {
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {
      // return the value of the weblogic.domainRestartVersion label
      return pod.getMetadata().getLabels().get("weblogic.domainRestartVersion");
    } else {
      getLogger().info("getPodRestartVersion(): Pod doesn't exist");
      return null;
    }
  }

  /**
   * List all pods in given namespace.
   *
   * @param namespace Namespace in which to list all pods
   * @param labelSelectors with which the pods are decorated
   * @return V1PodList list of pods or NULL when there is an error
   * @throws ApiException when there is error in querying the cluster
   */
  public static V1PodList listPods(String namespace, String labelSelectors) throws ApiException {
    V1PodList v1PodList = null;
    try {
      v1PodList
          = coreV1Api.listNamespacedPod(
              namespace, // namespace in which to look for the pods.
              Boolean.FALSE.toString(), // pretty print output.
              Boolean.FALSE, // allowWatchBookmarks requests watch events with type "BOOKMARK".
              null, // continue to query when there is more results to return.
              null, // selector to restrict the list of returned objects by their fields
              labelSelectors, // selector to restrict the list of returned objects by their labels.
              null, // maximum number of responses to return for a list call.
              null, // shows changes that occur after that particular version of a resource.
              null, // Timeout for the list/watch call.
              Boolean.FALSE // Watch for changes to the described resources.
          );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return v1PodList;
  }

  /**
   * Copy a directory from Kubernetes pod to local destination path.
   * @param pod V1Pod object
   * @param srcPath source directory location
   * @param destination destination directory path
   * @throws IOException when copy fails
   * @throws ApiException when pod interaction fails
   */
  public static void copyDirectoryFromPod(V1Pod pod, String srcPath, Path destination)
      throws IOException, ApiException, CopyNotSupportedException {
    Copy copy = new Copy();
    copy.copyDirectoryFromPod(pod, srcPath, destination);
  }

  /**
   * Copy a file from local filesystem to Kubernetes pod.
   * @param namespace namespace of the pod
   * @param pod name of the pod where the file is copied to
   * @param container name of the container
   * @param srcPath source file location
   * @param destPath destination file location on pod
   * @throws IOException when copy fails
   * @throws ApiException when pod interaction fails
   */
  public static void copyFileToPod(
      String namespace, String pod, String container, Path srcPath, Path destPath)
      throws IOException, ApiException {
    Copy copy = new Copy(apiClient);
    copy.copyFileToPod(namespace, pod, container, srcPath, destPath);
  }

  /**
   * Copy a file from Kubernetes pod to local filesystem.
   * @param namespace namespace of the pod
   * @param pod name of the pod where the file is copied from
   * @param container name of the container
   * @param srcPath source file location on the pod
   * @param destPath destination file location on local filesystem
   * @throws IOException when copy fails
   * @throws ApiException when pod interaction fails
   */
  public static void copyFileFromPod(String namespace, String pod, String container, String srcPath, Path destPath)
      throws IOException, ApiException {
    Copy copy = new Copy(apiClient);
    copy.copyFileFromPod(namespace, pod, container, srcPath, destPath);
  }

  // --------------------------- namespaces -----------------------------------
  /**
   * Create a Kubernetes namespace.
   *
   * @param name the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespace(String name) throws ApiException {
    V1ObjectMeta meta = new V1ObjectMetaBuilder().withName(name).build();
    V1Namespace namespace = new V1NamespaceBuilder().withMetadata(meta).build();

    try {
      coreV1Api.createNamespace(
          namespace, // name of the Namespace
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // name associated with the actor or entity that is making these changes
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  // --------------------------- namespaces -----------------------------------
  /**
   * Create a Kubernetes namespace.
   *
   * @param name the name of the namespace
   * @param labels list of labels for the namespace
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespace(String name, Map<String, String> labels) throws ApiException {
    V1ObjectMeta meta = new V1ObjectMetaBuilder().withName(name).withLabels(labels).build();
    V1Namespace namespace = new V1NamespaceBuilder().withMetadata(meta).build();

    try {
      coreV1Api.createNamespace(
          namespace, // name of the Namespace
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // name associated with the actor or entity that is making these changes
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Create a Kubernetes namespace.
   *
   * @param namespace - V1Namespace object containing namespace configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespace(V1Namespace namespace) throws ApiException {
    if (namespace == null) {
      throw new IllegalArgumentException(
          "Parameter 'namespace' cannot be null when calling createNamespace()");
    }

    V1Namespace ns = null;
    try {
      ns = coreV1Api.createNamespace(
          namespace, // V1Namespace configuration data object
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // name associated with the actor or entity that is making these changes
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Replace a existing namespace with configuration changes.
   *
   * @param ns V1Namespace object
   * @throws ApiException when replacing namespace fails
   */
  public static void replaceNamespace(V1Namespace ns) throws ApiException {

    try {
      coreV1Api.replaceNamespace(
          ns.getMetadata().getName(), // name of the namespace
          ns, // V1Namespace object body
          PRETTY, // pretty print the output
          null, // dry run or changes need to be permanent
          null // field manager
      );
    } catch (ApiException ex) {
      getLogger().severe(ex.getResponseBody());
      throw ex;
    }
  }

  /**
   * List namespaces in the Kubernetes cluster.
   * @return List of all Namespace names in the Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listNamespaces() throws ApiException {
    return listNamespaces(null);
  }

  /**
   * List namespaces in the Kubernetes cluster matching the label selector.
   * @return List of all Namespace names in the Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listNamespaces(String labelSelector) throws ApiException {
    ArrayList<String> nameSpaces = new ArrayList<>();
    V1NamespaceList namespaceList;
    try {
      namespaceList = coreV1Api.listNamespace(
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          labelSelector, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    for (V1Namespace namespace : namespaceList.getItems()) {
      nameSpaces.add(namespace.getMetadata().getName());
    }

    return nameSpaces;
  }

  /**
   * List namespaces in the Kubernetes cluster as V1NamespaceList.
   * @return V1NamespaceList of Namespace in the Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1NamespaceList listNamespacesAsObjects() throws ApiException {
    V1NamespaceList namespaceList;
    try {
      namespaceList = coreV1Api.listNamespace(
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          null, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return namespaceList;
  }

  /**
   * Return Namespace object for the given name from the Kubernetes cluster as V1Namespace object.
   * @name name of namespace.
   * @return V1Namespace  Namespace object from the Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1Namespace getNamespaceAsObject(String name) throws ApiException {
    try {
      V1NamespaceList namespaceList = coreV1Api.listNamespace(
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          null, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );

      if (!namespaceList.getItems().isEmpty()) {
        for (V1Namespace ns : namespaceList.getItems()) {
          if (ns.getMetadata().getName().equalsIgnoreCase(name)) {
            return ns;
          }
        }
      }
      return null;
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
  }

  /**
   * Delete a namespace for the given name.
   *
   * @param name name of namespace
   * @return true if successful delete request, false otherwise.
   */
  public static boolean deleteNamespace(String name) {

    KubernetesApiResponse<V1Namespace> response = namespaceClient.delete(name);

    if (!response.isSuccess()) {
      // status 409 means contents in the namespace being removed,
      // once done namespace will be purged
      if (response.getHttpStatusCode() == 409) {
        getLogger().warning(response.getStatus().getMessage());
        return false;
      } else {
        getLogger().warning("Failed to delete namespace: "
            + name + " with HTTP status code: " + response.getHttpStatusCode());
        return false;
      }
    }

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> getLogger().info("Waiting for namespace {0} to be deleted "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                name,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> namespaceDeleted(name),
            String.format("namespaceExists failed with ApiException for namespace %s",
                name)));

    return true;
  }

  private static Callable<Boolean> namespaceDeleted(String namespace) throws ApiException {
    return new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        List<String> namespaces = listNamespaces();
        if (!namespaces.contains(namespace)) {
          return true;
        }
        return  false;
      }
    };
  }

  // --------------------------- Events ---------------------------------------------------

  /**
   * List events in a namespace.
   *
   * @param namespace name of the namespace in which to list events
   * @return List of {@link V1Event} objects
   * @throws ApiException when listing events fails
   */
  public static List<V1Event> listNamespacedEvents(String namespace) throws ApiException {
    List<V1Event> events = null;
    try {
      V1EventList list = coreV1Api.listNamespacedEvent(
          namespace, // String | namespace.
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
      events = list.getItems();
      events.sort(Comparator.comparing(e -> e.getLastTimestamp()));
      Collections.reverse(events);
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return events;
  }

  // --------------------------- Custom Resource Domain -----------------------------------
  /**
   * Create a Domain Custom Resource.
   *
   * @param domain Domain custom resource model object
   * @param domVersion custom resource's version
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createDomainCustomResource(Domain domain, String... domVersion) throws ApiException {
    String domainVersion = (domVersion.length == 0) ? DOMAIN_VERSION : domVersion[0];

    if (domain == null) {
      throw new IllegalArgumentException(
          "Parameter 'domain' cannot be null when calling createDomainCustomResource()");
    }

    if (domain.metadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'domain' cannot be null when calling createDomainCustomResource()");
    }

    if (domain.metadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createDomainCustomResource()");
    }

    String namespace = domain.metadata().getNamespace();

    JsonElement json = convertToJson(domain);

    Object response;
    try {
      response = customObjectsApi.createNamespacedCustomObject(
          DOMAIN_GROUP, // custom resource's group name
          domainVersion, //custom resource's version
          namespace, // custom resource's namespace
          DOMAIN_PLURAL, // custom resource's plural name
          json, // JSON schema of the Resource to create
          null, // pretty print output
          null, // dry run
          null // field manager
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Converts a Java Object to a JSON element.
   *
   * @param obj java object to be converted
   * @return object representing Json element
   */
  private static JsonElement convertToJson(Object obj) {
    Gson gson = apiClient.getJSON().getGson();
    return gson.toJsonTree(obj);
  }

  /**
   * Delete the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteDomainCustomResource(String domainUid, String namespace) {

    KubernetesApiResponse<Domain> response = crdClient.delete(namespace, domainUid, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning(
          "Failed to delete Domain Custom Resource '" + domainUid + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
          + "domain custom resource in background!");
    }

    return true;
  }

  /**
   * Get the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return domain custom resource or null if Domain does not exist
   * @throws ApiException if Kubernetes request fails
   */
  public static Domain getDomainCustomResource(String domainUid, String namespace)
      throws ApiException {
    Object domain;
    try {
      domain = customObjectsApi.getNamespacedCustomObject(
          DOMAIN_GROUP, // custom resource's group name
          DOMAIN_VERSION, // //custom resource's version
          namespace, // custom resource's namespace
          DOMAIN_PLURAL, // custom resource's plural name
          domainUid // custom object's name
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    if (domain != null) {
      return handleResponse(domain, Domain.class);
    }

    getLogger().warning("Domain Custom Resource '" + domainUid + "' not found in namespace " + namespace);
    return null;
  }

  /**
   * Patch the Domain Custom Resource using JSON Patch.JSON Patch is a format for describing changes to a JSON document
   * using a series of operations. JSON Patch is specified in RFC 6902 from the IETF. For example, the following
   * operation will replace the "spec.restartVersion" to a value of "2".
   *
   * <p>[
   *      {"op": "replace", "path": "/spec/restartVersion", "value": "2" }
   *    ]
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patchString JSON Patch document as a String
   * @return true if patch is successful otherwise false
   */
  public static boolean patchCustomResourceDomainJsonPatch(String domainUid, String namespace,
      String patchString) {
    return patchDomainCustomResource(
        domainUid, // name of custom resource domain
        namespace, // name of namespace
        new V1Patch(patchString), // patch data
        V1Patch.PATCH_FORMAT_JSON_PATCH // "application/json-patch+json" patch format
    );
  }

  /**
   * Patch the Domain Custom Resource using JSON Merge Patch.JSON Merge Patch is a format for describing a changed
   * version to a JSON document. JSON Merge Patch is specified in RFC 7396 from the IETF. For example, the following
   * JSON object fragment would add/replace the "spec.restartVersion" to a value of "1".
   *
   * <p>{
   *      "spec" : { "restartVersion" : "1" }
   *    }
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patchString JSON Patch document as a String
   * @return true if patch is successful otherwise false
   */
  public static boolean patchCustomResourceDomainJsonMergePatch(String domainUid, String namespace,
      String patchString) {
    return patchDomainCustomResource(
        domainUid, // name of custom resource domain
        namespace, // name of namespace
        new V1Patch(patchString), // patch data
        V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH // "application/merge-patch+json" patch format
    );
  }

  /**
   * Patch the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *     "application/json-patch+json", "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchDomainCustomResource(String domainUid, String namespace,
      V1Patch patch, String patchFormat) {

    // GenericKubernetesApi uses CustomObjectsApi calls
    KubernetesApiResponse<Domain> response = crdClient.patch(
        namespace, // name of namespace
        domainUid, // name of custom resource domain
        patchFormat, // "application/json-patch+json" or "application/merge-patch+json"
        patch // patch data
    );

    if (!response.isSuccess()) {
      getLogger().warning(
          "Failed to patch " + domainUid + " in namespace " + namespace + " using patch format: "
              + patchFormat);
      return false;
    }

    return true;
  }

  /**
   * Patch the Deployment.
   *
   * @param deploymentName name of the deployment
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *     "application/json-patch+json", "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchDeployment(String deploymentName, String namespace,
                                        V1Patch patch, String patchFormat) {

    AppsV1Api apiInstance = new AppsV1Api(apiClient);
    try {
      PatchUtils.patch(
          V1Deployment.class,
          () ->
              apiInstance.patchNamespacedDeploymentCall(
                  deploymentName,
                  namespace,
                  patch,
                  null,
                  null,
                  null, // field-manager is optional
                  null,
                  null),
          patchFormat,
          apiClient);
    } catch (ApiException apiex) {
      getLogger().warning("Exception while patching the deployment {0} in namespace {1} : {2} ",
          deploymentName, namespace, apiex);
      return false;
    }
    return true;
  }

  /**
   * Converts the response to appropriate type.
   *
   * @param response response object to convert
   * @param type the type to convert into
   * @return the Java object of the type the response object is converted to
   */
  @SuppressWarnings("unchecked")
  private static <T> T handleResponse(Object response, Class<T> type) {
    JsonElement jsonElement = convertToJson(response);
    return apiClient.getJSON().getGson().fromJson(jsonElement, type);
  }

  /**
   * List Domain Custom Resources for a given namespace.
   *
   * @param namespace name of namespace
   * @return List of Domain Custom Resources
   */
  public static DomainList listDomains(String namespace) {
    KubernetesApiResponse<DomainList> response = null;
    try {
      response = crdClient.list(namespace);
    } catch (Exception ex) {
      getLogger().warning(ex.getMessage());
      throw ex;
    }
    return response != null ? response.getObject() : new DomainList();
  }

  // --------------------------- config map ---------------------------
  /**
   * Create a Kubernetes Config Map.
   *
   * @param configMap V1ConfigMap object containing config map configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createConfigMap(V1ConfigMap configMap) throws ApiException {
    if (configMap == null) {
      throw new IllegalArgumentException(
          "Parameter 'configMap' cannot be null when calling createConfigMap()");
    }

    if (configMap.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'configMap' cannot be null when calling createConfigMap()");
    }

    if (configMap.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createConfigMap()");
    }

    String namespace = configMap.getMetadata().getNamespace();

    V1ConfigMap cm;
    try {
      cm = coreV1Api.createNamespacedConfigMap(
          namespace, // config map's namespace
          configMap, // config map configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // name associated with the actor or entity that is making these changes
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Replace a Kubernetes Config Map.
   * The following command updates a complete configMap.
   *
   * @param configMap V1ConfigMap object containing config map configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean replaceConfigMap(V1ConfigMap configMap) throws ApiException {
    LoggingFacade logger = getLogger();
    if (configMap == null) {
      throw new IllegalArgumentException(
              "Parameter 'configMap' cannot be null when calling patchConfigMap()");
    }

    if (configMap.getMetadata() == null) {
      throw new IllegalArgumentException(
              "'metadata' field of the parameter 'configMap' cannot be null when calling patchConfigMap()");
    }

    if (configMap.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
              "'namespace' field in the metadata cannot be null when calling patchConfigMap()");
    }

    String namespace = configMap.getMetadata().getNamespace();

    V1ConfigMap cm;
    try {
      cm = coreV1Api.replaceNamespacedConfigMap(
              configMap.getMetadata().getName(),
              namespace,
              configMap, // config map configuration data
              PRETTY, // pretty print output
              null, // indicates that modifications should not be persisted
              null // name associated with the actor or entity that is making these changes
      );
      assertNotNull(cm, "cm replace failed ");
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * List Config Maps in the Kubernetes cluster.
   *
   * @param namespace Namespace in which to query
   * @return V1ConfigMapList of Config Maps
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1ConfigMapList listConfigMaps(String namespace) throws ApiException {

    V1ConfigMapList configMapList;
    try {
      configMapList = coreV1Api.listNamespacedConfigMap(
          namespace, // config map's namespace
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          null, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return configMapList;
  }

  /**
   * Delete Kubernetes Config Map.
   *
   * @param name name of the Config Map
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteConfigMap(String name, String namespace) {

    KubernetesApiResponse<V1ConfigMap> response = configMapClient.delete(namespace, name, deleteOptions);
    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete config map '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
          + "config map in background!");
    }

    return true;
  }

  // --------------------------- secret ---------------------------
  /**
   * Create a Kubernetes Secret.
   *
   * @param secret V1Secret object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createSecret(V1Secret secret) throws ApiException {
    if (secret == null) {
      throw new IllegalArgumentException(
          "Parameter 'secret' cannot be null when calling createSecret()");
    }

    if (secret.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'secret' cannot be null when calling createSecret()");
    }

    if (secret.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createSecret()");
    }

    String namespace = secret.getMetadata().getNamespace();

    V1Secret v1Secret;
    try {
      v1Secret = coreV1Api.createNamespacedSecret(
          namespace, // name of the Namespace
          secret, // secret configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Delete a Kubernetes Secret.
   *
   * @param name name of the Secret
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteSecret(String name, String namespace) {

    KubernetesApiResponse<V1Secret> response = secretClient.delete(namespace, name);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete secret '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
          + "secret in background!");
    }

    return true;
  }

  /**
   * List secrets in the Kubernetes cluster.
   * @param namespace Namespace in which to query
   * @return V1SecretList of secrets in the Kubernetes cluster
   */
  public static V1SecretList listSecrets(String namespace) {
    KubernetesApiResponse<V1SecretList> list = secretClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      getLogger().warning("Failed to list secrets, status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  // --------------------------- pv/pvc ---------------------------
  /**
   * Create a Kubernetes Persistent Volume.
   *
   * @param persistentVolume V1PersistentVolume object containing persistent volume configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createPv(V1PersistentVolume persistentVolume) throws ApiException {
    if (persistentVolume == null) {
      throw new IllegalArgumentException(
          "Parameter 'persistentVolume' cannot be null when calling createPv()");
    }

    V1PersistentVolume pv;
    try {
      pv = coreV1Api.createPersistentVolume(
          persistentVolume, // persistent volume configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Create a Kubernetes Persistent Volume Claim.
   *
   * @param persistentVolumeClaim V1PersistentVolumeClaim object containing Kubernetes persistent volume claim
    configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createPvc(V1PersistentVolumeClaim persistentVolumeClaim) throws ApiException {
    if (persistentVolumeClaim == null) {
      throw new IllegalArgumentException(
          "Parameter 'persistentVolume' cannot be null when calling createPvc()");
    }

    if (persistentVolumeClaim.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'persistentVolumeClaim' cannot be null when calling createPvc()");
    }

    if (persistentVolumeClaim.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createPvc()");
    }

    String namespace = persistentVolumeClaim.getMetadata().getNamespace();

    V1PersistentVolumeClaim pvc;
    try {
      pvc = coreV1Api.createNamespacedPersistentVolumeClaim(
          namespace, // name of the Namespace
          persistentVolumeClaim, // persistent volume claim configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Delete a Kubernetes Persistent Volume.
   *
   * @param name name of the Persistent Volume
   * @return true if successful
   */
  public static boolean deletePv(String name) {

    KubernetesApiResponse<V1PersistentVolume> response = pvClient.delete(name, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete persistent volume '" + name + "' "
          + "with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
          + "persistent volume in background!");
    }

    return true;
  }

  /**
   * Delete a Kubernetes Persistent Volume Claim.
   *
   * @param name name of the Persistent Volume Claim
   * @param namespace name of the namespace
   * @return true if successful
   */
  public static boolean deletePvc(String name, String namespace) {

    KubernetesApiResponse<V1PersistentVolumeClaim> response = pvcClient.delete(namespace, name, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning(
          "Failed to delete persistent volume claim '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
          + "persistent volume claim in background!");
    }

    return true;
  }

  /**
   * List all persistent volumes in the Kubernetes cluster.
   * @return V1PersistentVolumeList of Persistent Volumes in Kubernetes cluster
   */
  public static V1PersistentVolumeList listPersistentVolumes() {
    KubernetesApiResponse<V1PersistentVolumeList> list = pvClient.list();
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      getLogger().warning("Failed to list Persistent Volumes,"
          + " status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  /**
   * List persistent volumes in the Kubernetes cluster based on the label.
   * @param labels String containing the labels the PV is decorated with
   * @return V1PersistentVolumeList list of Persistent Volumes
   * @throws ApiException when listing fails
   */
  public static V1PersistentVolumeList listPersistentVolumes(String labels) throws ApiException {
    V1PersistentVolumeList listPersistentVolume;
    try {
      listPersistentVolume = coreV1Api.listPersistentVolume(
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          labels, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return listPersistentVolume;
  }

  /**
   * Get the V1PersistentVolume object in the Kubernetes cluster with specified Persistent Volume name.
   * @param pvname the name of the Persistent Volume
   * @return V1PersistentVolume the Persistent Volume object with specified name in Kubernetes cluster
   */
  public static V1PersistentVolume getPersistentVolume(String pvname) {
    KubernetesApiResponse<V1PersistentVolume> response = pvClient.get(pvname);
    if (response.isSuccess()) {
      return response.getObject();
    } else {
      getLogger().warning("Failed to get Persistent Volume {0},"
          + " status code {1}", pvname, response.getHttpStatusCode());
      return null;
    }
  }

  /**
   * List persistent volume claims in the namespace.
   * @param namespace name of the namespace in which to list
   * @return V1PersistentVolumeClaimList of Persistent Volume Claims in namespace
   */
  public static V1PersistentVolumeClaimList listPersistentVolumeClaims(String namespace) {
    KubernetesApiResponse<V1PersistentVolumeClaimList> list = pvcClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      getLogger().warning("Failed to list Persistent Volumes claims,"
          + " status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  /**
   * Get V1PersistentVolumeClaim object in the namespace with the specified Persistent Volume Claim name .
   * @param namespace namespace in which to get the Persistent Volume Claim
   * @param pvcname the name of Persistent Volume Claim
   * @return V1PersistentVolumeClaim the Persistent Volume Claims Object in specified namespace
   */
  public static V1PersistentVolumeClaim getPersistentVolumeClaim(String namespace, String pvcname) {
    KubernetesApiResponse<V1PersistentVolumeClaim> response = pvcClient.get(namespace, pvcname);
    if (response.isSuccess()) {
      return response.getObject();
    } else {
      getLogger().warning("Failed to get Persistent Volumes claim {0},"
          + " status code {1}", pvcname, response.getHttpStatusCode());
      return null;
    }
  }

  // --------------------------- service account ---------------------------
  /**
   * Create a Kubernetes Service Account.
   *
   * @param serviceAccount V1ServiceAccount object containing service account configuration data
   * @return created service account
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1ServiceAccount createServiceAccount(V1ServiceAccount serviceAccount)
      throws ApiException {
    if (serviceAccount == null) {
      throw new IllegalArgumentException(
          "Parameter 'serviceAccount' cannot be null when calling createServiceAccount()");
    }

    if (serviceAccount.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'serviceAccount' cannot be null when calling createServiceAccount()");
    }

    if (serviceAccount.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createServiceAccount()");
    }

    String namespace = serviceAccount.getMetadata().getNamespace();

    try {
      serviceAccount = coreV1Api.createNamespacedServiceAccount(
          namespace, // name of the Namespace
          serviceAccount, // service account configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return serviceAccount;
  }

  /**
   * Delete a Kubernetes Service Account.
   *
   * @param name name of the Service Account
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteServiceAccount(String name, String namespace) {

    KubernetesApiResponse<V1ServiceAccount> response = serviceAccountClient.delete(namespace, name, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete Service Account '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
          + "service account in background!");
      V1ServiceAccount serviceAccount = (V1ServiceAccount) response.getObject();
      getLogger().info(
          "Deleting Service Account " + serviceAccount.getMetadata().getName() + " in background.");
    }

    return true;
  }

  /**
   * List all service accounts in the Kubernetes cluster.
   *
   * @param namespace Namespace in which to list all service accounts
   * @return V1ServiceAccountList of service accounts
   */
  public static V1ServiceAccountList listServiceAccounts(String namespace) {
    KubernetesApiResponse<V1ServiceAccountList> list = serviceAccountClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      getLogger().warning("Failed to list service accounts, status code {0}", list.getHttpStatusCode());
      return null;
    }
  }
  // --------------------------- Services ---------------------------

  /**
   * Create a Kubernetes Service.
   *
   * @param service V1Service object containing service configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createService(V1Service service) throws ApiException {
    if (service == null) {
      throw new IllegalArgumentException(
          "Parameter 'service' cannot be null when calling createService()");
    }

    if (service.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'service' cannot be null when calling createService()");
    }

    if (service.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createService()");
    }

    String namespace = service.getMetadata().getNamespace();

    V1Service svc;
    try {
      svc = coreV1Api.createNamespacedService(
          namespace, // name of the Namespace
          service, // service configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Delete a Kubernetes Service.
   *
   * @param name name of the Service
   * @param namespace name of the namespace
   * @return true if successful
   */
  public static boolean deleteService(String name, String namespace) {

    KubernetesApiResponse<V1Service> response = serviceClient.delete(namespace, name, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete Service '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
          + "service in background!");
    }

    return true;
  }

  /**
   * Get namespaced service object.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service object to get
   * @return V1Service object if found, otherwise null
   */
  public static V1Service getNamespacedService(String namespace, String serviceName) {
    V1ServiceList listServices = listServices(namespace);
    if (!listServices.getItems().isEmpty()) {
      for (var service : listServices.getItems()) {
        if (service.getMetadata().getName().equalsIgnoreCase(serviceName)) {
          return service;
        }
      }
    }
    return null;
  }

  /**
   * Get node port of a namespaced service given the channel name.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @param channelName name of the channel for which to get the nodeport
   * @return node port if service and channel is found, otherwise -1
   */
  public static int getServiceNodePort(String namespace, String serviceName, String channelName) {
    V1Service service = getNamespacedService(namespace, serviceName);
    if (service != null) {
      V1ServicePort port = service.getSpec().getPorts().stream().filter(
          v1ServicePort -> v1ServicePort.getName().equalsIgnoreCase(channelName))
          .findAny().orElse(null);
      if (port != null) {
        return port.getNodePort();
      }
    }
    return -1;
  }

  /**
   * Get port of a namespaced service.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @return node port if service found otherwise -1
   */
  public static Integer getServiceNodePort(String namespace, String serviceName) {
    List<V1Service> services = listServices(namespace).getItems();
    for (V1Service service : services) {
      if (service.getMetadata().getName().startsWith(serviceName)) {
        return service.getSpec().getPorts().get(0).getNodePort();
      }
    }
    return -1;
  }

  /**
   * Get port of a namespaced service given the channel name.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @param channelName name of the channel for which to get the port
   * @return node port if service and channel is found, otherwise -1
   */
  public static int getServicePort(String namespace, String serviceName, String channelName) {
    V1Service service = getNamespacedService(namespace, serviceName);
    if (service != null) {
      V1ServicePort port = service.getSpec().getPorts().stream().filter(
          v1ServicePort -> v1ServicePort.getName().equalsIgnoreCase(channelName))
          .findAny().orElse(null);
      if (port != null) {
        return port.getPort();
      }
    }
    return -1;
  }

  /**
   * List services in a given namespace.
   *
   * @param namespace name of the namespace
   * @return V1ServiceList list of {@link V1Service} objects
   */
  public static V1ServiceList listServices(String namespace) {

    KubernetesApiResponse<V1ServiceList> list = serviceClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      getLogger().warning("Failed to list services in namespace {0}, status code {1}",
          namespace, list.getHttpStatusCode());
      return null;
    }
  }

  /**
   * Create a job.
   *
   * @param jobBody V1Job object containing job configuration data
   * @return String job name if job creation is successful
   * @throws ApiException when create job fails
   */
  public static String createNamespacedJob(V1Job jobBody) throws ApiException {
    String name = null;
    String namespace = jobBody.getMetadata().getNamespace();
    try {
      BatchV1Api apiInstance = new BatchV1Api(apiClient);
      V1Job createdJob = apiInstance.createNamespacedJob(
          namespace, // String | namespace in which to create job
          jobBody, // V1Job | body of the V1Job containing job data
          PRETTY, // String | pretty print output.
          null, // String | dry run or permanent change
          null // String | field manager who is making the change
      );
      if (createdJob != null) {
        name = createdJob.getMetadata().getName();
      }
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return name;
  }

  /**
   * Delete a job.
   *
   * @param namespace name of the namespace
   * @param name name of the job
   * @return true if delete was successful
   * @throws ApiException when deletion of job fails
   */
  public static boolean deleteJob(String namespace, String name) {

    KubernetesApiResponse<V1Job> response = jobClient.delete(namespace, name);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete job '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "job in background!");
    }

    return true;
  }

  /**
   * List jobs in the given namespace.
   *
   * @param namespace in which to list the jobs
   * @return V1JobList list of {@link V1Job} from Kubernetes cluster
   * @throws ApiException when list fails
   */
  public static V1JobList listJobs(String namespace) throws ApiException {
    V1JobList list;
    try {
      BatchV1Api apiInstance = new BatchV1Api(apiClient);
      list = apiInstance.listNamespacedJob(
          namespace, // String | name of the namespace.
          PRETTY, // String | pretty print output.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          TIMEOUT_SECONDS, // Integer | Timeout for the list/watch call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return list;
  }

  /**
   * Get V1Job object if any exists in the namespace with given job name.
   *
   * @param jobName name of the job
   * @param namespace name of the namespace in which to get the job object
   * @return V1Job object if any exists otherwise null
   * @throws ApiException when Kubernetes cluster query fails
   */
  public static V1Job getJob(String jobName, String namespace) throws ApiException {
    V1JobList listJobs = listJobs(namespace);
    for (V1Job job : listJobs.getItems()) {
      if (job != null && job.getMetadata() != null) {
        if (job.getMetadata().getName().equals(jobName)) {
          return job;
        }
      }
    }
    return null;
  }

  // --------------------------- replica sets ---------------------------


  /**
   * Delete a replica set.
   *
   * @param namespace name of the namespace
   * @param name name of the replica set
   * @return true if delete was successful
   * @throws ApiException if delete fails
   */
  public static boolean deleteReplicaSet(String namespace, String name) throws ApiException {
    try {
      AppsV1Api apiInstance = new AppsV1Api(apiClient);
      apiInstance.deleteNamespacedReplicaSet(
          name, // String | name of the replica set.
          namespace, // String | name of the namespace.
          PRETTY, // String | pretty print output.
          null, // String | When present, indicates that modifications should not be persisted.
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null, // Boolean | Deprecated: use the PropagationPolicy.
          FOREGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return true;
  }

  /**
   * List replica sets in the given namespace.
   *
   * @param namespace in which to list the replica sets
   * @return V1ReplicaSetList list of {@link V1ReplicaSet} objects
   * @throws ApiException when list fails
   */
  public static V1ReplicaSetList listReplicaSets(String namespace) throws ApiException {
    try {
      AppsV1Api apiInstance = new AppsV1Api(apiClient);
      V1ReplicaSetList list = apiInstance.listNamespacedReplicaSet(
          namespace, // String | namespace.
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
      return list;
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
  }

  // --------------------------- Role-based access control (RBAC)   ---------------------------

  /**
   * Create a cluster role.
   * @param clusterRole V1ClusterRole object containing cluster role configuration data
   * @return true if creation is successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createClusterRole(V1ClusterRole clusterRole) throws ApiException {
    try {
      V1ClusterRole cr = rbacAuthApi.createClusterRole(
          clusterRole, // cluster role configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Create a Cluster Role Binding.
   *
   * @param clusterRoleBinding V1ClusterRoleBinding object containing role binding configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createClusterRoleBinding(V1ClusterRoleBinding clusterRoleBinding)
      throws ApiException {
    try {
      V1ClusterRoleBinding crb = rbacAuthApi.createClusterRoleBinding(
          clusterRoleBinding, // role binding configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Create a role binding in the specified namespace.
   *
   * @param namespace the namespace in which the role binding to be created
   * @param roleBinding V1RoleBinding object containing role binding configuration data
   * @return true if the creation succeeds, false otherwise
   * @throws ApiException if Kubernetes client call fails
   */
  public static boolean createNamespacedRoleBinding(String namespace, V1RoleBinding roleBinding) throws ApiException {
    try {
      V1RoleBinding crb = rbacAuthApi.createNamespacedRoleBinding(
          namespace, // namespace where this role binding is created
          roleBinding, // role binding configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Delete Cluster Role Binding.
   *
   * @param name name of cluster role binding
   * @return true if successful, false otherwise
   */
  public static boolean deleteClusterRoleBinding(String name) {
    KubernetesApiResponse<V1ClusterRoleBinding> response = roleBindingClient.delete(name, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning(
          "Failed to delete Cluster Role Binding '" + name + " with HTTP status code: " + response
              .getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "Cluster Role Binding " + name + " in background!");
    }

    return true;
  }

  /**
   * List role bindings in all namespaces.
   *
   * @param labelSelector labels to narrow the list
   * @return V1RoleBindingList list of {@link V1RoleBinding} objects
   * @throws ApiException when listing fails
   */
  public static V1RoleBindingList listRoleBindingForAllNamespaces(String labelSelector) throws ApiException {
    V1RoleBindingList roleBindings;
    try {
      roleBindings = rbacAuthApi.listRoleBindingForAllNamespaces(
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          labelSelector, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          PRETTY, // String | If true, then the output is pretty printed.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          TIMEOUT_SECONDS, // Integer | Timeout for the list/watch call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return roleBindings;
  }

  /**
   * List cluster role bindings.
   *
   * @param labelSelector labels to narrow the list
   * @return V1ClusterRoleBindingList list of {@link V1CLusterRoleBinding} objects
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1ClusterRoleBindingList listClusterRoleBindings(String labelSelector) throws ApiException {
    V1ClusterRoleBindingList clusterRoleBindingList;
    try {
      clusterRoleBindingList = rbacAuthApi.listClusterRoleBinding(
          PRETTY, // String | If true, then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          labelSelector, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          TIMEOUT_SECONDS, // Integer | Timeout for the list/watch call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return clusterRoleBindingList;
  }

  /**
   * Delete a rolebinding in the given namespace.
   *
   * @param namespace name of the namespace
   * @param name name of the rolebinding to delete
   * @return return true if deletion was successful
   * @throws ApiException when delete rolebinding fails
   */
  public static boolean deleteNamespacedRoleBinding(String namespace, String name)
      throws ApiException {
    try {
      rbacAuthApi.deleteNamespacedRoleBinding(
          name, // String | name of the job.
          namespace, // String | name of the namespace.
          PRETTY, // String | pretty print output.
          null, // String | When present, indicates that modifications should not be persisted.
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null, // Boolean | Deprecated: use the PropagationPolicy.
          FOREGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return true;
  }

  /**
   * List role bindings in a given namespace.
   *
   * @param namespace name of the namespace
   * @return V1RoleBindingList list of {@link V1RoleBinding} objects
   * @throws ApiException when listing fails
   */
  public static V1RoleBindingList listNamespacedRoleBinding(String namespace)
      throws ApiException {
    V1RoleBindingList roleBindings;
    try {
      roleBindings = rbacAuthApi.listNamespacedRoleBinding(
          namespace, // String | namespace.
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return roleBindings;
  }


  /**
   * Delete a cluster role.
   *
   * @param name name of the cluster role to delete
   * @return true if deletion was successful
   * @throws ApiException when delete cluster role fails
   */
  public static boolean deleteClusterRole(String name) throws ApiException {
    try {
      rbacAuthApi.deleteClusterRole(
          name, // String | name of the role.
          PRETTY, // String | pretty print output.
          null, // String | When present, indicates that modifications should not be persisted.
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null, // Boolean | Deprecated: use the PropagationPolicy.
          FOREGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return true;
  }


  /**
   * List cluster roles in the Kubernetes cluster.
   *
   * @param labelSelector labels to narrow the list
   * @return V1ClusterRoleList list of {@link V1ClusterRole} objects
   * @throws ApiException when listing fails
   */
  public static V1ClusterRoleList listClusterRoles(String labelSelector) throws ApiException {
    V1ClusterRoleList roles;
    try {
      roles = rbacAuthApi.listClusterRole(
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          labelSelector, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return roles;
  }

  /**
   * Delete a role in the Kubernetes cluster in the given namespace.
   *
   * @param namespace name of the namespace
   * @param name name of the role to delete
   * @return true if deletion was successful
   * @throws ApiException when delete fails
   */
  public static boolean deleteNamespacedRole(String namespace, String name) throws ApiException {
    try {
      rbacAuthApi.deleteNamespacedRole(
          name, // String | name of the job.
          namespace, // String | name of the namespace.
          PRETTY, // String | pretty print output.
          null, // String | When present, indicates that modifications should not be persisted.
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null, // Boolean | Deprecated: use the PropagationPolicy.
          FOREGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return true;
  }

  /**
   * List roles in a given namespace.
   *
   * @param namespace name of the namespace
   * @return V1RoleList list of {@link V1Role} object
   * @throws ApiException when listing fails
   */
  public static V1RoleList listNamespacedRoles(String namespace) throws ApiException {
    V1RoleList roles;
    try {
      roles = rbacAuthApi.listNamespacedRole(
          namespace, // String | namespace.
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return roles;
  }

  /**
   * List Ingresses in the given namespace.
   *
   * @param namespace name of the namespace
   * @return NetworkingV1beta1IngressList list of {@link NetworkingV1beta1Ingress} objects
   * @throws ApiException when listing fails
   */
  public static NetworkingV1beta1IngressList listNamespacedIngresses(String namespace) throws ApiException {
    NetworkingV1beta1IngressList ingressList;
    try {
      NetworkingV1beta1Api apiInstance = new NetworkingV1beta1Api(apiClient);
      ingressList = apiInstance.listNamespacedIngress(
          namespace, // namespace
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          TIMEOUT_SECONDS, // Integer | Timeout for the list/watch call.
          ALLOW_WATCH_BOOKMARKS // Boolean | Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return ingressList;
  }

  /**
   * Delete an ingress in the specified namespace.
   *
   * @param name  ingress name to be deleted
   * @param namespace namespace in which the specified ingress exists
   * @return true if deleting ingress succeed, false otherwise
   * @throws ApiException if Kubernetes API client call fails
   */
  public static boolean deleteIngress(String name, String namespace) throws ApiException {
    try {
      NetworkingV1beta1Api apiInstance = new NetworkingV1beta1Api(apiClient);
      apiInstance.deleteNamespacedIngress(
          name, // ingress name
          namespace, // namespace
          PRETTY, // String | If 'true', then the output is pretty printed.
          null, // String | dry run or permanent change
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null, // Boolean | Deprecated: use the PropagationPolicy.
          BACKGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Get Ingress in the given namespace by name.
   *
   * @param namespace name of the namespace
   * @param name name of the Ingress object
   * @return NetworkingV1beta1Ingress Ingress object when found, otherwise null
   * @throws ApiException when get fails
   */
  public static NetworkingV1beta1Ingress getNamespacedIngress(String namespace, String name)
      throws ApiException {
    try {
      for (NetworkingV1beta1Ingress item
          : listNamespacedIngresses(namespace).getItems()) {
        if (name.equals(item.getMetadata().getName())) {
          return item;
        }
      }
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return null;
  }

  // --------------------------- Exec   ---------------------------

  /**
   * Execute a command in a container.
   *
   * @param pod The pod where the command is to be run
   * @param containerName The container in the Pod where the command is to be run. If no
   *     container name is provided than the first container in the Pod is used.
   * @param redirectToStdout copy process output to stdout
   * @param command The command to run
   * @return result of command execution
   * @throws IOException if an I/O error occurs.
   * @throws ApiException if Kubernetes client API call fails
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public static ExecResult exec(V1Pod pod, String containerName, boolean redirectToStdout,
      String... command)
      throws IOException, ApiException, InterruptedException {

    // Execute command using Kubernetes API
    KubernetesExec kubernetesExec = createKubernetesExec(pod, containerName);
    final Process proc = kubernetesExec.exec(command);

    // If redirect enabled, copy stdout and stderr to corresponding Outputstream
    final CopyingOutputStream copyOut =
        redirectToStdout ? new CopyingOutputStream(System.out) : new CopyingOutputStream(null);
    final CopyingOutputStream copyErr =
        redirectToStdout ? new CopyingOutputStream(System.err) : new CopyingOutputStream(null);

    // Start a thread to begin reading the output stream of the command
    try {
      Thread out = createStreamReader(proc.getInputStream(), copyOut,
          "Exception reading from stdout input stream.");
      out.start();

      // Start a thread to begin reading the error stream of the command
      Thread err = createStreamReader(proc.getErrorStream(), copyErr,
          "Exception reading from stderr input stream.");
      err.start();

      // wait for the process, which represents the executing command, to terminate
      proc.waitFor();

      // wait for stdout reading thread to finish any remaining output
      out.join();

      // wait for stderr reading thread to finish any remaining output
      err.join();

      // Read data from process's stdout
      String stdout = readExecCmdData(copyOut.getInputStream());

      // Read from process's stderr, if data available
      String stderr = readExecCmdData(copyErr.getInputStream());;

      ExecResult result = new ExecResult(proc.exitValue(), stdout, stderr);
      getLogger().fine("result from exec command: " + result);

      if (result.exitValue() != 0) {
        getLogger().info("result.exitValue={0}", result.exitValue());
        getLogger().info("result.stdout={0}", result.stdout());
        getLogger().info("result.stderr={0}", result.stderr());
      }

      return result;
    } finally {
      if (proc != null) {
        proc.destroy();
      }
    }
  }

  private static Thread createStreamReader(InputStream inputStream, CopyingOutputStream copyOut,
      String s) {
    return
        new Thread(
            () -> {
              try {
                ByteStreams.copy(inputStream, copyOut);
              } catch (IOException ex) {
                // "Pipe broken" is expected when process is finished so don't log
                if (ex.getMessage() != null && !ex.getMessage().contains("Pipe broken")) {
                  getLogger().warning(s, ex);
                }
              }
            });
  }

  /**
   * Create an object which can execute commands in a Kubernetes container.
   *
   * @param pod The pod where the command is to be run
   * @param containerName The container in the Pod where the command is to be run. If no
   *     container name is provided than the first container in the Pod is used.
   * @return object for executing a command in a container of the pod
   */
  public static KubernetesExec createKubernetesExec(V1Pod pod, String containerName) {
    return new KubernetesExec()
        .apiClient(apiClient) // the Kubernetes api client to dispatch the "exec" command
        .pod(pod) // The pod where the command is to be run
        .containerName(containerName) // the container in which the command is to be run
        .passStdinAsStream(); // pass a stdin stream into the container
  }

  /**
   * Create an Ingress in the specified namespace.
   *
   * @param namespace the namespace in which the ingress will be created
   * @param ingressBody NetworkingV1beta1Ingress object, representing the ingress details
   * @return the ingress created
   * @throws ApiException if Kubernetes client API call fails
   */
  public static NetworkingV1beta1Ingress createIngress(String namespace, NetworkingV1beta1Ingress ingressBody)
      throws ApiException {
    NetworkingV1beta1Ingress ingress;
    try {
      NetworkingV1beta1Api apiInstance = new NetworkingV1beta1Api(apiClient);
      ingress = apiInstance.createNamespacedIngress(
          namespace, //namespace
          ingressBody, // NetworkingV1beta1Ingress object, representing the ingress details
          PRETTY, // pretty print output
          null, // when present, indicates that modifications should not be persisted
          null // a name associated with the actor or entity that is making these changes
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }

    return ingress;
  }

  //------------------------

  private static String readExecCmdData(InputStream is) {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(is, Charsets.UTF_8))) {
      int c = 0;
      while ((c = reader.read()) != -1) {
        sb.append((char) c);
      }
    } catch (IOException e) {
      getLogger().warning("Exception thrown " + e);
    }
    return sb.toString().trim();
  }

  /**
   * Get the name of the operator pod.
   *
   * @param release release name of the operator
   * @param namespace Kubernetes namespace that the operator is running in
   * @return name of the operator pod
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getOperatorPodName(String release, String namespace) throws ApiException {
    String labelSelector = String.format("app in (%s)", release);
    V1PodList pods = listPods(namespace, labelSelector);
    for (var pod : pods.getItems()) {
      if (pod.getMetadata().getName().contains(release)) {
        return pod.getMetadata().getName();
      }
    }
    return null;
  }

  /**
   * Simple class to redirect/copy data to both the stdout stream and a buffer
   * which can be read from later.
   */
  private static class CopyingOutputStream extends OutputStream {

    final OutputStream out;
    final ByteArrayOutputStream copy = new ByteArrayOutputStream();

    CopyingOutputStream(OutputStream out) {
      this.out = out;
    }

    @Override
    public void write(int b) throws IOException {
      if (out != null) {
        out.write(b);
      }
      copy.write(b);
    }

    public InputStream getInputStream() {
      return new ByteArrayInputStream(copy.toByteArray());
    }
  }
}
