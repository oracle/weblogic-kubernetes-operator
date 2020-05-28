// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.ClientBuilder;
import org.joda.time.DateTime;

import static io.kubernetes.client.util.Yaml.dump;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodRestartVersion;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

public class Kubernetes {

  private static final String OPERATOR_NAME = "weblogic-operator-";

  private static ApiClient apiClient = null;
  private static CoreV1Api coreV1Api = null;
  private static CustomObjectsApi customObjectsApi = null;
  private static final String RUNNING = "Running";

  static {
    try {
      Configuration.setDefaultApiClient(ClientBuilder.defaultClient());
      apiClient = Configuration.getDefaultApiClient();
      coreV1Api = new CoreV1Api();
      customObjectsApi = new CustomObjectsApi();
    } catch (IOException ioex) {
      throw new ExceptionInInitializerError(ioex);
    }
  }

  /**
   * Checks if a pod exists in a given namespace in any state.
   * @param namespace in which to check for the pod existence
   * @param domainUid the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if pod exists otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean doesPodExist(String namespace, String domainUid, String podName) throws ApiException {
    boolean podExist = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {
      podExist = true;
    }
    return podExist;
  }

  /**
   * Checks if a pod does not exist in a given namespace in any state.
   * @param namespace in which to check for the pod existence
   * @param domainUid the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if pod does not exists otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean doesPodNotExist(String namespace, String domainUid, String podName) throws ApiException {
    boolean podDeleted = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod == null) {
      podDeleted = true;
    } else {
      logger.info("[" + pod.getMetadata().getName() + "] still exist");
    }
    return podDeleted;
  }

  /**
   * Checks if a pod exists in a given namespace and in Running state.
   * @param namespace in which to check for the pod running
   * @param domainUid the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if pod exists and running otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean isPodRunning(String namespace, String domainUid, String podName) throws ApiException {
    boolean status = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {
      status = pod.getStatus().getPhase().equals(RUNNING);
    } else {
      logger.info("Pod doesn't exist");
    }
    return status;
  }

  /**
   * Checks if a pod is ready in a given namespace.
   *
   * @param namespace in which to check if the pod is ready
   * @param domainUid the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if the pod is in the ready condition, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean isPodReady(String namespace, String domainUid, String podName) throws ApiException {
    boolean status = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }

    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {

      // get the podCondition with the 'Ready' type field
      V1PodCondition v1PodReadyCondition = pod.getStatus().getConditions().stream()
          .filter(v1PodCondition -> "Ready".equals(v1PodCondition.getType()))
          .findAny()
          .orElse(null);

      if (v1PodReadyCondition != null) {
        status = v1PodReadyCondition.getStatus().equalsIgnoreCase("true");
        if (status) {
          logger.info("Pod {0} is READY in namespace {1}", podName, namespace);
        }
      }
    } else {
      logger.info("Pod {0} does not exist in namespace {1}", podName, namespace);
    }
    return status;
  }

  /**
   * Check if a pod exists in a given namespace and is terminating.
   * @param namespace in which to check for the pod
   * @param domainUid the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if pod is terminating otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean isPodTerminating(String namespace, String domainUid, String podName)
      throws ApiException {
    boolean terminating = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID=%s", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (null == pod) {
      logger.severe("pod does not exist");
      return false;
    } else if (pod.getMetadata().getDeletionTimestamp() != null) {
      terminating = true;
      logger.info("{0} : !!!Terminating!!!, DeletionTimeStamp : {1}",
          pod.getMetadata().getName(), pod.getMetadata().getDeletionTimestamp());
    }
    return terminating;
  }

  /**
   * Checks if a pod in a given namespace has been updated with an expected
   * weblogic.domainRestartVersion label.
   *
   * @param namespace in which to check for the pod
   * @param domainUid the label the pod is decorated with
   * @param podName name of the pod to check for
   * @param expectedRestartVersion domainRestartVersion that is expected
   * @return true if pod has been updated as expected
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean podRestartVersionUpdated(
      String namespace,
      String domainUid,
      String podName,
      String expectedRestartVersion
  ) throws ApiException {
    String restartVersion = getPodRestartVersion(namespace, "", podName);

    if (restartVersion != null && restartVersion.equals(expectedRestartVersion)) {
      logger.info("Pod {0}: domainRestartVersion has been updated to expected value {1}",
          podName, expectedRestartVersion);
      return true;
    }
    logger.info("Pod {0}: domainRestartVersion {1} does not match expected value {2}",
        podName, restartVersion, expectedRestartVersion);
    return false;
  }

  /**
   * Checks if a WebLogic server pod has been patched with an expected image.
   *
   * @param namespace Kubernetes namespace in which the pod is running
   * @param domainUid label that the pod is decorated with
   * @param podName name of the WebLogic server pod
   * @param containerName name of the container
   * @param image name of the image to check for
   * @return true if pod's image has been patched
   * @throws ApiException when there is an error in querying the Kubernetes cluster
   */
  public static boolean podImagePatched(
      String namespace,
      String domainUid,
      String podName,
      String containerName,
      String image
  ) throws ApiException {
    boolean podPatched = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null && pod.getSpec() != null) {
      List<V1Container> containers = pod.getSpec().getContainers();
      for (V1Container container : containers) {
        // look for the container
        if (container.getName().equals(containerName)
            && container.getImage().equals(image)) {
          podPatched = true;
        }
      }
    }
    return podPatched;
  }

  /**
   * Checks if an operator pod is running in a given namespace.
   * The method assumes the operator name to starts with weblogic-operator-
   * and decorated with label weblogic.operatorName:namespace
   * @param namespace in which to check for the pod existence
   * @return true if pod exists and running otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean isOperatorPodReady(String namespace) throws ApiException {
    boolean status = false;
    String labelSelector = String.format("weblogic.operatorName in (%s)", namespace);
    V1Pod pod = getPod(namespace, labelSelector, "weblogic-operator-");
    if (pod != null) {
      // get the podCondition with the 'Ready' type field
      V1PodCondition v1PodReadyCondition = pod.getStatus().getConditions().stream()
          .filter(v1PodCondition -> "Ready".equals(v1PodCondition.getType()))
          .findAny()
          .orElse(null);

      if (v1PodReadyCondition != null) {
        status = v1PodReadyCondition.getStatus().equalsIgnoreCase("true");
      }
    } else {
      logger.info("Pod doesn't exist");
    }
    return status;
  }

  /**
   * Checks if a NGINX pod is running in the specified namespace.
   * The method assumes that the NGINX pod name contains "nginx-ingress-controller".
   *
   * @param namespace in which to check if the NGINX pod is running
   * @return true if the pod is running, otherwise false
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean isNginxPodRunning(String namespace) throws ApiException {

    return isPodRunning(namespace, null, "nginx-ingress-controller");
  }

  /**
   * Check whether the NGINX pod is ready in the specified namespace.
   * The method assumes that the NGINX pod name starts with "nginx-ingress-controller".
   *
   * @param namespace in which to check if the NGINX pod is ready
   * @return true if the pod is in the ready state, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean isNginxPodReady(String namespace) throws ApiException {

    return isPodReady(namespace, null, "nginx-ingress-controller");
  }

  /**
   * Returns the V1Pod object given the following parameters.
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod to return
   * @return V1Pod object if found otherwise null
   * @throws ApiException when there is error in querying the cluster
   */
  public static V1Pod getPod(String namespace, String labelSelector, String podName) throws ApiException {
    V1PodList v1PodList =
        coreV1Api.listNamespacedPod(
            namespace, // namespace in which to look for the pods.
            Boolean.FALSE.toString(), // // pretty print output.
            Boolean.FALSE, // allowWatchBookmarks requests watch events with type "BOOKMARK".
            null, // continue to query when there is more results to return.
            null, // selector to restrict the list of returned objects by their fields
            labelSelector, // selector to restrict the list of returned objects by their labels.
            null, // maximum number of responses to return for a list call.
            null, // shows changes that occur after that particular version of a resource.
            null, // Timeout for the list/watch call.
            Boolean.FALSE // Watch for changes to the described resources.
        );
    for (V1Pod item : v1PodList.getItems()) {
      if (item.getMetadata().getName().contains(podName.trim())) {
        logger.info("Name: {0}, Namespace: {1}, Phase: {2}",
            item.getMetadata().getName(), namespace, item.getStatus().getPhase());
        return item;
      }
    }
    return null;
  }

  /**
   * Checks if a Kubernetes service object exists in a given namespace.
   * @param serviceName name of the service to check for
   * @param label the key value pair with which the service is decorated with
   * @param namespace the namespace in which to check for the service
   * @return true if the service is found otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean doesServiceExist(
      String serviceName, Map<String, String> label, String namespace)
      throws ApiException {
    boolean exist = false;
    V1Service service = getService(serviceName, label, namespace);
    if (service != null) {
      exist = true;
    }
    return exist;
  }

  /**
   * Get V1Service object for the given service name, label and namespace.
   * @param serviceName name of the service to look for
   * @param label the key value pair with which the service is decorated with
   * @param namespace the namespace in which to check for the service
   * @return V1Service object if found otherwise null
   * @throws ApiException when there is error in querying the cluster
   */
  public static V1Service getService(
      String serviceName, Map<String, String> label, String namespace)
      throws ApiException {
    String labelSelector = null;
    if (label != null) {
      String key = label.keySet().iterator().next().toString();
      String value = label.get(key).toString();
      labelSelector = String.format("%s in (%s)", key, value);
      logger.info(labelSelector);
    }
    V1ServiceList v1ServiceList
        = coreV1Api.listServiceForAllNamespaces(
        Boolean.FALSE, // allowWatchBookmarks requests watch events with type "BOOKMARK".
        null, // continue to query when there is more results to return.
        null, // selector to restrict the list of returned objects by their fields
        labelSelector, // selector to restrict the list of returned objects by their labels.
        null, // maximum number of responses to return for a list call.
        Boolean.FALSE.toString(), // pretty print output.
        null, // shows changes that occur after that particular version of a resource.
        null, // Timeout for the list/watch call.
        Boolean.FALSE // Watch for changes to the described resources.
    );
    for (V1Service service : v1ServiceList.getItems()) {
      if (service.getMetadata().getName().equals(serviceName.trim())
          && service.getMetadata().getNamespace().equals(namespace.trim())) {
        logger.info("Service Name : " + service.getMetadata().getName());
        logger.info("Service Namespace : " + service.getMetadata().getNamespace());
        Map<String, String> labels = service.getMetadata().getLabels();
        if (labels != null) {
          for (Map.Entry<String, String> entry : labels.entrySet()) {
            logger.log(Level.INFO, "Label Key: {0} Label Value: {1}",
                new Object[]{entry.getKey(), entry.getValue()});
          }
        }
        return service;
      }
    }
    return null;
  }

  /**
   * Get a list of pods from given namespace and  label.
   *
   * @param namespace in which to list all pods
   * @param labelSelectors with which the pods are decorated
   * @return V1PodList list of {@link V1Pod} from the namespace
   * @throws ApiException when there is error in querying the cluster
   */
  public static V1PodList listPods(String namespace, String labelSelectors) throws ApiException {
    V1PodList v1PodList
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
    return v1PodList;
  }

  /**
   * A utillity method to list all services in a given namespace.
   * This method can be used as diagnostic tool to get the details of services.
   * @param namespace in which to list all services
   * @param labelSelectors  with which the services are decorated
   * @throws ApiException when there is error in querying the cluster
   */
  public static void listServices(String namespace, String labelSelectors) throws ApiException {
    V1ServiceList v1ServiceList
        = coreV1Api.listServiceForAllNamespaces(
        Boolean.FALSE, // allowWatchBookmarks requests watch events with type "BOOKMARK".
        null, // continue to query when there is more results to return.
        null, // selector to restrict the list of returned objects by their fields
        labelSelectors, // selector to restrict the list of returned objects by their labels.
        null, // maximum number of responses to return for a list call.
        Boolean.FALSE.toString(), // pretty print output.
        null, // shows changes that occur after that particular version of a resource.
        null, // Timeout for the list/watch call.
        Boolean.FALSE // Watch for changes to the described resources.
        );
    List<V1Service> items = v1ServiceList.getItems();
    logger.info(Arrays.toString(items.toArray()));
    for (V1Service service : items) {
      logger.info("Service Name : " + service.getMetadata().getName());
      logger.info("Service Namespace : " + service.getMetadata().getNamespace());
      logger.info("Service ResourceVersion : " + service.getMetadata().getResourceVersion());
      logger.info("Service SelfLink : " + service.getMetadata().getSelfLink());
      logger.info("Service Uid :" + service.getMetadata().getUid());
      logger.info("Service Spec Cluster IP : " + service.getSpec().getClusterIP());
      logger.info("Service Spec getExternalIPs : " + service.getSpec().getExternalIPs());
      logger.info("Service Spec getExternalName : " + service.getSpec().getExternalName());
      logger.info("Service Spec getPorts : " + service.getSpec().getPorts());
      logger.info("Service Spec getType : " + service.getSpec().getType());
      Map<String, String> labels = service.getMetadata().getLabels();
      if (labels != null) {
        for (Map.Entry<String, String> entry : labels.entrySet()) {
          logger.log(Level.INFO, "LABEL KEY: {0} LABEL VALUE: {1}",
              new Object[]{entry.getKey(), entry.getValue()});
        }
      }
    }
  }

  /**
   * List jobs in the given namespace.
   *
   * @param namespace in which to list the jobs
   * @param labelSelectors labels to narrow the list of jobs
   * @return V1JobList list of {@link V1Job} from Kubernetes cluster
   * @throws ApiException when list fails
   */
  public static V1JobList listJobs(String namespace, String labelSelectors)
      throws ApiException {
    V1JobList list;
    try {
      BatchV1Api apiInstance = new BatchV1Api(apiClient);
      list = apiInstance.listNamespacedJob(
          namespace, // String | name of the namespace.
          null, // String | pretty print output.
          null, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          labelSelectors, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          "", // String | Shows changes that occur after that particular version of a resource.
          5, // Integer | Timeout for the list/watch call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      logger.warning(apex.getResponseBody());
      throw apex;
    }
    return list;
  }

  /**
   * Returns the V1Job object given the following parameters.
   * @param namespace in which to check for the job existence
   * @param labelSelectors the labels the job is decorated with, if any
   * @param jobName name of the job to return
   * @return V1Job object if found otherwise null
   * @throws ApiException when there is error in querying the cluster
   */
  public static V1Job getJob(String namespace, String labelSelectors, String jobName)
      throws ApiException {
    List<V1Job> jobs = listJobs(namespace, labelSelectors).getItems();
    for (V1Job job : jobs) {
      if (job != null && job.getMetadata().getName().equals(jobName)) {
        return job;
      }
    }
    return null;
  }

  /**
   * Check if a pod completed running.
   *
   * @param namespace name of the namespace in which the job exists
   * @param labelSelectors labels to narrow the job list
   * @param jobName name of the job to check for its completion status
   * @return true if completed false otherwise
   * @throws ApiException when querying pod condition fails
   */
  public static boolean isJobComplete(String namespace, String labelSelectors, String jobName)
      throws ApiException {
    boolean completionStatus = false;

    V1Job job = getJob(namespace, labelSelectors, jobName);
    if (job != null && job.getStatus() != null) {
      logger.info("\n" + dump(job.getStatus()));
      if (job.getStatus().getConditions() != null) {
        V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
            v1JobCondition
              -> "Complete".equalsIgnoreCase(v1JobCondition.getType())
                  || "Failed".equalsIgnoreCase(v1JobCondition.getType()))
            .findAny()
            .orElse(null);
        if (jobCondition != null) {
          completionStatus = jobCondition.getStatus().equalsIgnoreCase("true");
          if (jobCondition.getType().equalsIgnoreCase("failed")) {
            logger.severe("Job {0} failed", jobName);
          } else if (jobCondition.getType().equalsIgnoreCase("complete")) {
            logger.severe("Job {0} completed successfully ", jobName);
          }
        }
      }
    } else {
      logger.warning("Job doesn't exist");
    }
    return completionStatus;
  }

  public static boolean loadBalancerReady(String domainUid) {
    return true;
  }

  public static boolean adminServerReady(String domainUid, String namespace) {
    return true;
  }

  /**
   * Check if a pod is restarted based on podCreationTimestamp.
   *
   * @param podName the name of the pod to check for
   * @param domainUid the label the pod is decorated with
   * @param namespace in which the pod is running
   * @param timestamp the initial podCreationTimestamp
   * @return true if the pod's creation timestamp is later than the initial PodCreationTimestamp
   * @throws ApiException when query fails
   */
  public static boolean isPodRestarted(
      String podName, String domainUid,
      String namespace, DateTime timestamp) throws ApiException {
    DateTime newCreationTime = getPodCreationTimestamp(namespace, "", podName);

    if (newCreationTime != null
        && newCreationTime.isAfter(timestamp)) {
      logger.info("Pod {0}: new creation time {1} is later than the last creation time {2}",
          podName, newCreationTime, timestamp);
      return true;
    }
    logger.info("Pod {0}: new creation time {1} is NOT later than the last creation time {2}",
        podName, newCreationTime, timestamp);
    return false;
  }
}
