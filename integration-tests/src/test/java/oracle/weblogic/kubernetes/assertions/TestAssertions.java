// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Secret;
import oracle.weblogic.kubernetes.actions.impl.LoggingExporter;
import oracle.weblogic.kubernetes.assertions.impl.Apache;
import oracle.weblogic.kubernetes.assertions.impl.Application;
import oracle.weblogic.kubernetes.assertions.impl.ClusterRole;
import oracle.weblogic.kubernetes.assertions.impl.ClusterRoleBinding;
import oracle.weblogic.kubernetes.assertions.impl.Docker;
import oracle.weblogic.kubernetes.assertions.impl.Domain;
import oracle.weblogic.kubernetes.assertions.impl.Grafana;
import oracle.weblogic.kubernetes.assertions.impl.Helm;
import oracle.weblogic.kubernetes.assertions.impl.Job;
import oracle.weblogic.kubernetes.assertions.impl.Kubernetes;
import oracle.weblogic.kubernetes.assertions.impl.Nginx;
import oracle.weblogic.kubernetes.assertions.impl.Operator;
import oracle.weblogic.kubernetes.assertions.impl.PersistentVolume;
import oracle.weblogic.kubernetes.assertions.impl.PersistentVolumeClaim;
import oracle.weblogic.kubernetes.assertions.impl.Pod;
import oracle.weblogic.kubernetes.assertions.impl.Prometheus;
import oracle.weblogic.kubernetes.assertions.impl.Service;
import oracle.weblogic.kubernetes.assertions.impl.Traefik;
import oracle.weblogic.kubernetes.assertions.impl.Voyager;
import oracle.weblogic.kubernetes.assertions.impl.WitAssertion;

import static oracle.weblogic.kubernetes.actions.TestActions.listSecrets;


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
   * Check if there are ready Apache pods in the specified namespace.
   *
   * @param namespace in which to check if Apache pods are in the ready state
   * @return true if there are ready Apache pods in the specified namespace , false otherwise
   */
  public static Callable<Boolean> isApacheReady(String namespace) {
    return Apache.isReady(namespace);
  }

  /**
   * Check traefik controller pod is ready in the specified namespace.
   *
   * @param namespace in which to check for traefik pod readiness
   * @return true if traefik pod is ready, false otherwise
   */
  public static Callable<Boolean> isTraefikReady(String namespace) {
    return Traefik.isReady(namespace);
  }

  /**
   * Check if Voyager pod is running.
   *
   * @param namespace in which to check if Voyager pod is running
   * @param podName name of Voyager ingress controller pod or ingress resource pod
   * @return true if Voyager pod is running, false otherwise
   */
  public static Callable<Boolean> isVoyagerRunning(String namespace, String podName) {
    return Voyager.isRunning(namespace, podName);
  }

  /**
   * Check if Voyager pods is in the ready state in a given namespace.
   *
   * @param namespace in which to check if Voyager pod is in the ready state
   * @param podName name of Voyager ingress controller pod or ingress resource pod
   * @return true if Voyager pod is in the ready state, false otherwise
   */
  public static Callable<Boolean> isVoyagerReady(String namespace, String podName) {
    return Voyager.isReady(namespace, podName);
  }

  /**
   * Check if ELK Stack pod is ready in a given namespace.
   *
   * @param namespace in which to check ELK Stack pod is ready
   * @param podName name of ELK Stack pod
   * @return true if ELK Stack pod is in the ready state, false otherwise
   */
  public static Callable<Boolean> isElkStackPodReady(String namespace, String podName) {
    return LoggingExporter.isReady(namespace, podName);
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
   * @param domainVersion version of the domain resource definition
   * @param namespace in which the domain custom resource object exists
   * @return true if domain object exists
   */
  public static Callable<Boolean> domainExists(String domainUid, String domainVersion, String namespace) {
    return () -> Domain.doesDomainExist(domainUid, domainVersion, namespace);
  }

  /**
   * Check if a WebLogic custom resource domain object does not exist in specified namespace.
   *
   * @param domainUid ID of the domain
   * @param domainVersion version of the domain resource definition
   * @param namespace in which the domain custom resource object exists
   * @return true if domain object exists
   */
  public static Callable<Boolean> domainDoesNotExist(String domainUid, String domainVersion, String namespace) {
    return () -> !Domain.doesDomainExist(domainUid, domainVersion, namespace);
  }

  /**
   * Check if a pod's restartVersion has been updated.
   *
   * @param podName   name of the pod to check
   * @param domainUid WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @param expectedRestartVersion restartVersion that is expected
   * @return true if the pod's restartVersion has been updated
   * @throws ApiException if Kubernetes client API call fails
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
   * Check if a pod's introspectVersion has been updated.
   *
   * @param podName   name of the pod to check
   * @param namespace in which the pod is running
   * @param expectedIntrospectVersion introspectVersion that is expected
   * @return true if the pod's introspectVersion has been updated
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean podIntrospectVersionUpdated(
      String podName,
      String namespace,
      String expectedIntrospectVersion
  ) throws ApiException {
    return Kubernetes.podIntrospectVersionUpdated(namespace, podName, expectedIntrospectVersion);
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
   * @param containerName name of the container inside the pod where the image is used
   * @param image name of the image that was used to patch the domain resource
   * @return true if the pod is patched correctly
   */
  public static Callable<Boolean> podImagePatched(
      String domainUid,
      String namespace,
      String podName,
      String containerName,
      String image
  ) {
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
   * Check if a Kubernetes pod is in initializing state.
   *
   * @param podName   name of the pod to check for
   * @param domainUid WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is initializing
   * @return true if the pod is initializing otherwise false
   */
  public static Callable<Boolean> podInitializing(String podName, String domainUid, String namespace) {
    return Pod.podInitializing(namespace, domainUid, podName);
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
  public static boolean verifyRollingRestartOccurred(Map<String, OffsetDateTime> pods,
                                                     int maxUnavailable, String namespace) {
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
   * Check if an application is accessible inside a WebLogic server pod.
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
    return Application.appAccessibleInPodKubectl(namespace, podName, port, appPath, expectedResponse);
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
    return !Application.appAccessibleInPodKubectl(namespace, podName, port, appPath, expectedResponse);
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
   * Check Helm release status is deployed.
   * @param releaseName release name which unique in a namespace
   * @param namespace namespace name
   * @param status expected helm release status
   * @return true on success
   */
  public static boolean checkHelmReleaseStatus(String releaseName, String namespace, String status) {
    return Helm.checkHelmReleaseStatus(releaseName, namespace, status);
  }

  /**
   * Check Helm release status is deployed.
   * @param releaseName release name which unique in a namespace
   * @param namespace namespace name
   * @return true on success
   */
  public static boolean isHelmReleaseFailed(String releaseName, String namespace) {
    return Helm.isReleaseFailed(releaseName, namespace);
  }

  /**
   * Check Helm release revision against expected.
   * @param releaseName release name which is unique in a namespace
   * @param namespace namespace name
   * @param revision expected revision for the helm release
   * @return true on success
   */
  public static boolean checkHelmReleaseRevision(String releaseName, String namespace, String revision) {
    return Helm.checkHelmReleaseRevision(releaseName, namespace, revision);
  }

  /**
   * Check if a pod is restarted based on podCreationTimestamp.
   *
   * @param podName the name of the pod to check for
   * @param namespace in which the pod is running
   * @param timestamp the initial podCreationTimestamp
   * @return true if the pod new timestamp is not equal to initial PodCreationTimestamp otherwise false
   */
  public static Callable<Boolean> isPodRestarted(
      String podName,
      String namespace,
      OffsetDateTime timestamp
  ) {
    return () -> {
      return Kubernetes.isPodRestarted(podName, namespace,timestamp);
    };
  }

  /**
   * Check if the oeprator pod in a given namespace is restarted based on podCreationTimestamp.
   *
   * @param namespace in which the pod is running
   * @param timestamp the initial podCreationTimestamp
   * @return true if the pod new timestamp is not equal to initial PodCreationTimestamp otherwise false
   */
  public static Callable<Boolean> isOperatorPodRestarted(
      String namespace,
      OffsetDateTime timestamp
  ) {
    return () -> {
      return Kubernetes.isOperatorPodRestarted(namespace, timestamp);
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
                                           OffsetDateTime podOriginalCreationTimestamp) {
    return Domain.podStateNotChanged(podName, domainUid, namespace, podOriginalCreationTimestamp);
  }

  /**
   * Check if a job completed running.
   *
   * @param jobName name of the job to check for its completion status
   * @param labelSelectors label selectors used to get the right pod object
   * @param namespace name of the namespace in which the job running
   * @return true if completed false otherwise
   */
  public static Callable<Boolean> jobCompleted(String jobName, String labelSelectors, String namespace) {
    return Job.jobCompleted(namespace, labelSelectors, jobName);
  }

  /**
   * Check if Prometheus is running.
   *
   * @param namespace in which is prometheus is running
   * @return true if running false otherwise
   */
  public static Callable<Boolean> isPrometheusReady(String namespace) {
    return Prometheus.isReady(namespace);
  }

  /**
   * Check if Grafana is running.
   *
   * @param namespace in which is grafana is running
   * @return true if running false otherwise
   */
  public static Callable<Boolean> isGrafanaReady(String namespace) {
    return Grafana.isReady(namespace);
  }

  /*
   * Check whether persistent volume with pvName exists.
   *
   * @param pvName persistent volume to check
   * @param labelSelector String containing the labels the PV is decorated with
   * @return true if the persistent volume exists, false otherwise
   */
  public static Callable<Boolean> pvExists(String pvName, String labelSelector) {
    return PersistentVolume.pvExists(pvName, labelSelector);
  }

  /**
   * Check whether persistent volume claims with pvcName exists in the specified namespace.
   *
   * @param pvcName persistent volume claim to check
   * @param namespace the namespace in which the persistent volume claim to be checked
   * @return true if the persistent volume claim exists in the namespace, false otherwise
   */
  public static Callable<Boolean> pvcExists(String pvcName, String namespace) {
    return PersistentVolumeClaim.pvcExists(pvcName, namespace);
  }

  /**
   * Check whether the cluster role exists.
   *
   * @param clusterRoleName name of the cluster role
   * @return true if cluster role exists, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean clusterRoleExists(String clusterRoleName) throws ApiException {
    return ClusterRole.clusterRoleExists(clusterRoleName);
  }

  /**
   * Check whether the cluster role binding exists.
   *
   * @param clusterRoleBindingName name of the cluster role binding
   * @return true if cluster role binding exists, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean clusterRoleBindingExists(String clusterRoleBindingName) throws ApiException {
    return ClusterRoleBinding.clusterRoleBindingExists(clusterRoleBindingName);
  }

  /**
   * Check whether the secret exists in the specified namespace.
   *
   * @param secretName name of the secret
   * @param namespace namespace in which the secret exists
   * @return true if secret exists, false otherwise
   */
  public static boolean secretExists(String secretName, String namespace) {
    for (V1Secret secret : listSecrets(namespace).getItems()) {
      if (secret.getMetadata() != null) {
        String name = secret.getMetadata().getName();
        if (name != null && name.equals(secretName)) {
          return true;
        }
      }
    }

    return false;
  }
}
