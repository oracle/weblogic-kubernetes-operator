// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.assertions.impl.Application;
import oracle.weblogic.kubernetes.assertions.impl.Docker;
import oracle.weblogic.kubernetes.assertions.impl.Domain;
import oracle.weblogic.kubernetes.assertions.impl.Helm;
import oracle.weblogic.kubernetes.assertions.impl.Job;
import oracle.weblogic.kubernetes.assertions.impl.Kubernetes;
import oracle.weblogic.kubernetes.assertions.impl.Nginx;
import oracle.weblogic.kubernetes.assertions.impl.Operator;
import oracle.weblogic.kubernetes.assertions.impl.Pod;
import oracle.weblogic.kubernetes.assertions.impl.Service;
import oracle.weblogic.kubernetes.assertions.impl.WitAssertion;
import org.joda.time.DateTime;

/**
 * General assertions needed by the tests to validate CRD, Domain, Pods etc.
 */
public class TestAssertions {

  /**
   * Check if Operator is running.
   *
   * @param namespace in which is operator is running
   * @return true if running false otherwise
   */
  public static Callable<Boolean> operatorIsReady(String namespace) {
    return Operator.isReady(namespace);
  }

  /**
   * Check if NGINX is running.
   *
   * @param namespace in which to check if NGINX is running
   * @return true if NGINX is running, false otherwise
   */
  public static Callable<Boolean> isNginxRunning(String namespace) {
    return Nginx.isRunning(namespace);
  }

  /**
   * Check if there are ready NGINX pods in the specified namespace.
   *
   * @param namespace in which to check if NGINX pods are in the ready state
   * @return true if there are ready NGINX pods in the specified namespace , false otherwise
   */
  public static Callable<Boolean> isNginxReady(String namespace) {
    return Nginx.isReady(namespace);
  }

  /**
   * Check if operator REST service is running.
   *
   * @param namespace in which the operator REST service exists
   * @return true if REST service is running otherwise false
   */
  public static Callable<Boolean> operatorRestServiceRunning(String namespace) {
    return () -> Operator.doesExternalRestServiceExists(namespace);
  }

  /**
   * Check if a WebLogic custom resource domain object exists in specified
   * namespace.
   *
   * @param domainUid ID of the domain
   * @param namespace in which the domain custom resource object exists
   * @return true if domain object exists
   */
  public static Callable<Boolean> domainExists(String domainUid, String domainVersion, String namespace) {
    return Domain.doesDomainExist(domainUid, domainVersion, namespace);
  }

  /**
   * Check if a pod's restartVersion has been updated.
   *
   * @param podName   name of the pod to check
   * @param domainUid WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @param expectedRestartVersion restartVersion that is expected
   * @return true if the pod's restartVersion has been updated
   */
  public static boolean podRestartVersionUpdated(
      String podName,
      String domainUid,
      String namespace,
      String expectedRestartVersion
  ) throws ApiException {
    return Kubernetes.podRestartVersionUpdated(namespace, domainUid, podName, expectedRestartVersion);
  }


  /**
   * Check if a WebLogic domain custom resource has been patched with a new WebLogic credentials secret.
   *
   * @param domainUid ID of the domain resource
   * @param namespace Kubernetes namespace in which the domain custom resource object exists
   * @param secretName name of the secret that was used to patch the domain resource
   * @return true if the domain is patched correctly
   */
  public static Callable<Boolean> domainResourceCredentialsSecretPatched(
      String domainUid,
      String namespace,
      String secretName
  ) {
    return () -> Domain.domainResourceCredentialsSecretPatched(domainUid, namespace, secretName);
  }

  /**
   * Check if a WebLogic domain custom resource has been patched with a new image.
   *
   * @param domainUid ID of the domain resource
   * @param namespace Kubernetes namespace in which the domain custom resource object exists
   * @param image name of the image that was used to patch the domain resource
   * @return true if the domain is patched correctly
   */
  public static Callable<Boolean> domainResourceImagePatched(
      String domainUid,
      String namespace,
      String image
  ) {
    return () -> Domain.domainResourceImagePatched(domainUid, namespace, image);
  }

  /**
   * Check if a WebLogic server pod has been patched with a new image.
   *
   * @param domainUid ID of the domain resource
   * @param namespace Kubernetes namespace in which the domain custom resource object exists
   * @param podName name of the WebLogic server pod
   * @param image name of the image that was used to patch the domain resource
   * @return true if the pod is patched correctly
   */
  public static Callable<Boolean> podImagePatched(
      String domainUid,
      String namespace,
      String podName,
      String containerName,
      String image
  ) throws ApiException {
    return () -> {
      return Kubernetes.podImagePatched(namespace, domainUid, podName, containerName, image);
    };
  }

  /**
   * Check if a Kubernetes pod exists in any state in the given namespace.
   *
   * @param podName   name of the pod to check for
   * @param domainUid UID of WebLogic domain in which the pod exists
   * @param namespace in which the pod exists
   * @return true if the pod exists in the namespace otherwise false
   */
  public static Callable<Boolean> podExists(String podName, String domainUid, String namespace) {
    return Pod.podExists(podName, domainUid, namespace);
  }

  /**
   * Check a named pod does not exist in the given namespace.
   *
   * @param podName name of the pod to check for
   * @param domainUid Uid of WebLogic domain
   * @param namespace namespace in which to check for the pod
   * @return true if the pod does not exist in the namespace otherwise false
   */
  public static Callable<Boolean> podDoesNotExist(String podName, String domainUid, String namespace) {
    return Pod.podDoesNotExist(podName, domainUid, namespace);
  }

  /**
   * Check if a Kubernetes pod is in running/ready state.
   *
   * @param podName   name of the pod to check for
   * @param domainUid WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @return true if the pod is running otherwise false
   */
  public static Callable<Boolean> podReady(String podName, String domainUid, String namespace) {
    return Pod.podReady(namespace, domainUid, podName);
  }

  /**
   * Check if a pod given by the podName is in Terminating state.
   *
   * @param podName   name of the pod to check for Terminating status
   * @param domainUid WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @return true if the pod is terminating otherwise false
   */
  public static Callable<Boolean> podTerminating(String podName, String domainUid, String namespace) {
    return Pod.podTerminating(namespace, domainUid, podName);
  }

  /**
   * Verify pods are restarted in a rolling fashion with not more than maxUnavailable pods are restarted concurrently.
   * @param pods map of pod names with its creation time stamps
   * @param maxUnavailable number of pods can concurrently restart at the same time
   * @param namespace name of the namespace in which the pod restart status to be checked
   * @return true if pods are restarted in a rolling fashion
   */
  public static boolean verifyRollingRestartOccurred(Map<String, DateTime> pods, int maxUnavailable, String namespace) {
    return Pod.verifyRollingRestartOccurred(pods, maxUnavailable, namespace);
  }


  /**
   * Check is a service exists in given namespace.
   *
   * @param serviceName the name of the service to check for
   * @param label       a Map of key value pairs the service is decorated with
   * @param namespace   in which the service is running
   * @return true if the service exists otherwise false
   */
  public static Callable<Boolean> serviceExists(
      String serviceName,
      Map<String, String> label,
      String namespace) {
    return Service.serviceExists(serviceName, label, namespace);
  }

  /**
   * Check a service does not exist in the specified namespace.
   *
   * @param serviceName the name of the service to check for
   * @param label       a Map of key value pairs the service is decorated with
   * @param namespace   in which to check whether the service exists
   * @return true if the service does not exist, false otherwise
   */
  public static Callable<Boolean> serviceDoesNotExist(String serviceName,
                                                      Map<String, String> label,
                                                      String namespace) {
    return () -> !Kubernetes.doesServiceExist(serviceName, label, namespace);
  }

  /**
   * Check if a loadbalancer pod is ready.
   *
   * @param domainUid id of the WebLogic domain custom resource domain
   * @return true, if the load balancer is ready
   */
  public static boolean loadbalancerReady(String domainUid) {
    return Kubernetes.loadBalancerReady(domainUid);
  }

  /**
   * Check if the admin server pod is ready.
   *
   * @param domainUid id of the domain in which admin server pod is running
   * @param namespace in which the pod exists
   * @return true if the admin server is ready otherwise false
   */
  public static boolean adminServerReady(String domainUid, String namespace) {
    return Kubernetes.adminServerReady(domainUid, namespace);
  }

  /**
   * Check if a adminserver T3 channel is accessible.
   *
   * @param domainUid id of the domain in which admin server pod is running
   * @param namespace in which the WebLogic server pod exists
   * @return true if the admin T3 channel is accessible otherwise false
   */
  public static boolean adminT3ChannelAccessible(String domainUid, String namespace) {
    return Domain.adminT3ChannelAccessible(domainUid, namespace);
  }

  /**
   * Check if a admin server pod admin node port is accessible.
   *
   * @param nodePort the node port of the WebLogic administration server service
   * @param userName user name to access WebLogic administration server
   * @param password password to access WebLogic administration server
   * @return true if the WebLogic administration service node port is accessible otherwise false
   * @throws java.io.IOException when connection to WebLogic administration server fails
   */
  public static boolean adminNodePortAccessible(int nodePort, String userName, String password)
      throws IOException {
    return Domain.adminNodePortAccessible(nodePort, userName, password);
  }

  /**
   * Check if a Docker image exists.
   *
   * @param imageName the name of the image to be checked
   * @param imageTag  the tag of the image to be checked
   * @return true if the image does exist, false otherwise
   */
  public static boolean dockerImageExists(String imageName, String imageTag) {
    return WitAssertion.doesImageExist(imageName, imageTag);
  }

  /**
   * Check if an application is accessible inside a WebLogic server pod using
   * "kubectl exec" command.
   *
   * @param namespace Kubernetes namespace where the WebLogic server pod is running
   * @param podName name of the WebLogic server pod
   * @param port internal port of the managed server running in the pod
   * @param appPath path to access the application
   * @param expectedResponse the expected response from the application
   * @return true if the command succeeds
   */
  public static boolean appAccessibleInPodKubectl(
      String namespace,
      String podName,
      String port,
      String appPath,
      String expectedResponse
  ) {
    return Application.appAccessibleInPodKubectl(namespace, podName, port, appPath, expectedResponse);
  }

  /**
   * Check if the given WebLogic credentials are valid by using the credentials to
   * invoke a RESTful Management Services command.
   *
   * @param host hostname of the admin server pod
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @return true if the RESTful Management Services command succeeded
   */
  public static Callable<Boolean> credentialsValid(
      String host,
      String podName,
      String namespace,
      String username,
      String password) {
    return () -> Domain.credentialsValid(host, podName, namespace, username, password);
  }

  /**
   * Check if the given WebLogic credentials are NOT valid by using the credentials to
   * invoke a RESTful Management Services command.
   *
   * @param host hostname of the admin server pod
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @return true if the RESTful Management Services command failed with exitCode 401
   */
  public static Callable<Boolean> credentialsNotValid(
      String host,
      String podName,
      String namespace,
      String username,
      String password) {
    return () -> Domain.credentialsNotValid(host, podName, namespace, username, password);
  }

  /**
   * Check if an application is accessible inside a WebLogic server pod using
   * Kubernetes Java client API.
   *
   * @param namespace Kubernetes namespace where the WebLogic server pod is running
   * @param podName name of the WebLogic server pod
   * @param port internal port of the managed server running in the pod
   * @param appPath path to access the application
   * @param expectedResponse the expected response from the application
   * @return true if the command succeeds
   */
  public static boolean appAccessibleInPod(
      String namespace,
      String podName,
      String port,
      String appPath,
      String expectedResponse
  ) {
    return Application.appAccessibleInPod(namespace, podName, port, appPath, expectedResponse);
  }

  /**
   * Check if an application is Not running inside a WebLogic server pod.
   * .
   * @param namespace Kubernetes namespace where the WebLogic server pod is running
   * @param podName name of the WebLogic server pod
   * @param port internal port of the managed server running in the pod
   * @param appPath path to access the application
   * @param expectedResponse the expected response from the application
   * @return true if the command succeeds
   */
  public static boolean appNotAccessibleInPod(
      String namespace,
      String podName,
      String port,
      String appPath,
      String expectedResponse
  ) {
    return !Application.appAccessibleInPod(namespace, podName, port, appPath, expectedResponse);
  }

  /**
   * Check if the Docker image containing the search string exists.
   * @param searchString search string
   * @return true on success
   */
  public static boolean doesImageExist(String searchString) {
    return Docker.doesImageExist(searchString);
  }

  /**
   * Check Helm release status is deployed.
   * @param releaseName release name which unique in a namespace
   * @param namespace namespace name
   * @return true on success
   */
  public static boolean isHelmReleaseDeployed(String releaseName, String namespace) {
    return Helm.isReleaseDeployed(releaseName, namespace);
  }

  /**
   * Check if a pod is restarted based on podCreationTimestamp.
   *
   * @param podName the name of the pod to check for
   * @param domainUid the label the pod is decorated with
   * @param namespace in which the pod is running
   * @param timestamp the initial podCreationTimestamp
   * @return true if the pod new timestamp is not equal to initial PodCreationTimestamp otherwise false
   * @throws ApiException when query fails
   */
  public static Callable<Boolean> isPodRestarted(
      String podName,
      String domainUid,
      String namespace,
      DateTime timestamp
  ) throws ApiException {
    return () -> {
      return Kubernetes.isPodRestarted(podName,domainUid,namespace,timestamp);
    };
  }

  /**
   * Verify the pod state is not changed.
   *
   * @param podName the name of the pod to check
   * @param domainUid the label the pod is decorated with
   * @param namespace the namespace in which the pod exists
   * @param podOriginalCreationTimestamp the pod original creation timestamp
   * @return true if the pod state is not changed, false otherwise
   */
  public static boolean podStateNotChanged(String podName,
                                           String domainUid,
                                           String namespace,
                                           DateTime podOriginalCreationTimestamp) {
    return Domain.podStateNotChanged(podName, domainUid, namespace, podOriginalCreationTimestamp);
  }

  /**
   * Check if a job completed running.
   *
   * @param namespace name of the namespace in which the job running
   * @param jobName name of the job to check for its completion status
   * @return true if completed false otherwise
   */
  public static Callable<Boolean> jobCompleted(String jobName, String labelSelectors, String namespace) {
    return Job.jobCompleted(namespace, labelSelectors, jobName);
  }

}
