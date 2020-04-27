// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions;

import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.assertions.impl.Application;
import oracle.weblogic.kubernetes.assertions.impl.Docker;
import oracle.weblogic.kubernetes.assertions.impl.Domain;
import oracle.weblogic.kubernetes.assertions.impl.Helm;
import oracle.weblogic.kubernetes.assertions.impl.Kubernetes;
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
  public static Callable<Boolean> operatorIsRunning(String namespace) {
    return Operator.isRunning(namespace);
  }

  /**
   * Check if operator Rest service is running.
   *
   * @param namespace in which the operator rest service exists
   * @return true if rest service is running otherwise false
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
   * Check if a WebLogic domain custom resource has been patched with a new image.
   *
   * @param domainUid ID of the domain
   * @param namespace in which the domain custom resource object exists
   * @param image that was used to patch the domain resource
   * @return true if the domain is patched
   */
  public static Callable<Boolean> domainResourceImagePatched(
      String domainUid,
      String namespace,
      String image
  ) {
    return Domain.domainResourceImagePatched(domainUid, namespace, image);
  }

  /**
   * Check if a WebLogic domain custom resource has been patched with a new image.
   *
   * @param domainUid ID of the domain
   * @param namespace in which the domain custom resource object exists
   * @param podName name of the WebLogic server pod
   * @param image that was used to patch the domain resource
   * @return true if the domain is patched
   */
  public static Callable<Boolean> podImagePatched(
      String domainUid,
      String namespace,
      String podName,
      String image
  ) throws ApiException {
    return () -> {
      return Kubernetes.isPodImagePatched(namespace, domainUid, podName, image);
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
  public static Callable<Boolean> podExists(String podName, String domainUid, String namespace) throws ApiException {
    return () -> {
      return Kubernetes.doesPodExist(namespace, domainUid, podName);
    };
  }

  /**
   * Check if a Kubernetes pod is in running/ready state.
   *
   * @param podName   name of the pod to check for
   * @param domainUid WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @return true if the pod is running otherwise false
   */
  public static Callable<Boolean> podReady(String podName, String domainUid, String namespace) throws ApiException {
    return () -> {
      return Kubernetes.isPodRunning(namespace, domainUid, podName);
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
   * Check if an application is accessible inside a WebLogic server pod.
   * 
   * @param domainUID unique identifier of the Kubernetes domain custom resource instance
   * @param domainNS Kubernetes namespace where the WebLogic servers are running
   * @param podName name of the WebLogic server pod
   * @param port internal port of the managed servers
   * @param appPath the path to access the application
   * @param expectedStr the expected response from the application
   * @return true if the command succeeds 
   */
  public static Callable<Boolean> appAccessibleInPod(
      String domainUID,
      String domainNS,
      String podName,
      String port,
      String appPath,
      String expectedStr
  ) {
    return () -> {
      return Application.appAccessibleInPod(domainUID, domainNS, podName, port, appPath, expectedStr);
    };
  }

  /**
   * Check if an application is Not running inside a WebLogic server pod.
   * .
   * @param domainUID unique identifier of the Kubernetes domain custom resource instance
   * @param domainNS Kubernetes namespace where the WebLogic servers are running
   * @param podName name of the WebLogic server pod
   * @param port internal port of the managed servers
   * @param appPath the path to access the application
   * @param expectedStr the expected response from the application
   * @return true if the command succeeds 
   */
  public static Callable<Boolean> appNotAccessibleInPod(
      String domainUID,
      String domainNS,
      String podName,
      String port,
      String appPath,
      String expectedStr
  ) {
    return () -> {
      return !Application.appAccessibleInPod(domainUID, domainNS, podName, port, appPath, expectedStr);
    };
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
