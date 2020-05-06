// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions;

import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.assertions.impl.Docker;
import oracle.weblogic.kubernetes.assertions.impl.Domain;
import oracle.weblogic.kubernetes.assertions.impl.Helm;
import oracle.weblogic.kubernetes.assertions.impl.Kubernetes;
import oracle.weblogic.kubernetes.assertions.impl.Nginx;
import oracle.weblogic.kubernetes.assertions.impl.Operator;
import oracle.weblogic.kubernetes.assertions.impl.WitAssertion;

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
  public static Callable<Boolean> operatorRestServiceRunning(String namespace) throws ApiException {
    return () -> {
      return Operator.doesExternalRestServiceExists(namespace);
    };
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
   * Check if a Kubernetes pod exists in any state in the given namespace.
   *
   * @param podName   name of the pod to check for
   * @param domainUid UID of WebLogic domain in which the pod exists
   * @param namespace in which the pod exists
   * @return true if the pod exists in the namespace otherwise false
   */
  public static Callable<Boolean> podExists(String podName, String domainUid, String namespace) throws ApiException {
    return () -> {
      return Kubernetes.doesPodExist(namespace, domainUid, podName);
    };
  }

  /**
   * Check a named pod does not exist in the given namespace.
   *
   * @param podName name of the pod to check for
   * @param domainUid Uid of WebLogic domain
   * @param namespace namespace in which to check for the pod
   * @return true if the pod does not exist in the namespace otherwise false
   * @throws ApiException when cluster query fails
   */
  public static Callable<Boolean> podDoesNotExist(String podName, String domainUid, String namespace)
      throws ApiException {
    return () -> {
      return !Kubernetes.doesPodExist(namespace, domainUid, podName);
    };
  }

  /**
   * Check if a Kubernetes pod is in running/ready state.
   *
   * @param podName   name of the pod to check for
   * @param domainUid WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @return true if the pod is running otherwise false
   * @throws ApiException when Kubernetes cluster query fails to get pod
   */
  public static Callable<Boolean> podReady(String podName, String domainUid, String namespace) throws ApiException {
    return () -> {
      return Kubernetes.isPodReady(namespace, domainUid, podName);
    };
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
    return () -> {
      return Kubernetes.isPodTerminating(namespace, domainUid, podName);
    };
  }

  /**
   * Check is a service exists in given namespace.
   *
   * @param serviceName the name of the service to check for
   * @param label       a Map of key value pairs the service is decorated with
   * @param namespace   in which the service is running
   * @return true if the service exists otherwise false
   * @throws ApiException when query fails
   */
  public static Callable<Boolean> serviceExists(
      String serviceName,
      Map<String, String> label,
      String namespace
  ) throws ApiException {
    return () -> {
      return Kubernetes.doesServiceExist(serviceName, label, namespace);
    };
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
   * @param domainUid domainUID id of the domain in which admin server pod is running
   * @param namespace in which the WebLogic server pod exists
   * @return true if the admin node port is accessible otherwise false
   */
  public static boolean adminNodePortAccessible(String domainUid, String namespace) {
    return Domain.adminNodePortAccessible(domainUid, namespace);
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

}
