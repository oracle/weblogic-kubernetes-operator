// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions;

import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.assertions.impl.Domain;
import oracle.weblogic.kubernetes.assertions.impl.Kubernetes;
import oracle.weblogic.kubernetes.assertions.impl.Operator;
import oracle.weblogic.kubernetes.assertions.impl.Traefik;
import oracle.weblogic.kubernetes.assertions.impl.WITAssertion;

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
   * Check if Traefik is running.
   *
   * @param namespace in which is traefik is running
   * @return true if running false otherwise
   */
  public static Callable<Boolean> traefikIsRunning(String namespace) {
    return Traefik.isRunning(namespace);
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
   * @param domainUID ID of the domain
   * @param namespace in which the domain custom resource object exists
   * @return true if domain object exists
   */
  public static Callable<Boolean> domainExists(String domainUID, String domainVersion, String namespace) {
    return Domain.doesDomainExist(domainUID, domainVersion, namespace);
  }

  /**
   * Check if a Kubernetes pod exists in any state in the given namespace.
   *
   * @param podName name of the pod to check for
   * @param domainUID UID of WebLogic domain in which the pod exists
   * @param namespace in which the pod exists
   * @return true if the pod exists in the namespace otherwise false
   */
  public static Callable<Boolean> podExists(String podName, String domainUID, String namespace) throws ApiException {
    return () -> {
      return Kubernetes.doesPodExist(namespace, domainUID, podName);
    };
  }

  /**
   * Check if a Kubernetes pod is in running/ready state.
   *
   * @param podName name of the pod to check for
   * @param domainUID WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @return true if the pod is running otherwise false
   */
  public static Callable<Boolean> podReady(String podName, String domainUID, String namespace) throws ApiException {
    return () -> {
      return Kubernetes.isPodRunning(namespace, domainUID, podName);
    };
  }

  /**
   * Check if a pod given by the podName is in Terminating state.
   *
   * @param podName name of the pod to check for Terminating status
   * @param domainUID WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @return true if the pod is terminating otherwise false
   */
  public static Callable<Boolean> podTerminating(String podName, String domainUID, String namespace) {
    return () -> {
      return Kubernetes.isPodTerminating(namespace, domainUID, podName);
    };
  }

  /**
   * Check is a service exists in given namespace.
   *
   * @param serviceName the name of the service to check for
   * @param label a Map of key value pairs the service is decorated with
   * @param namespace in which the service is running
   * @return true if the service exists otherwise false
   * @throws ApiException when query fails
   */
  public static boolean serviceReady(
      String serviceName,
      Map<String, String> label,
      String namespace
  )throws ApiException {
    return Kubernetes.doesServiceExist(serviceName, label, namespace);
  }

  /**
   * Check if a loadbalancer pod is ready.
   *
   * @param domainUID id of the WebLogic domain custom resource domain
   * @return
   */
  public static boolean loadbalancerReady(String domainUID) {
    return Kubernetes.loadBalancerReady(domainUID);
  }

  /**
   * Check if the admin server pod is ready.
   *
   * @param domainUID id of the domain in which admin server pod is running
   * @param namespace in which the pod exists
   * @return true if the admin server is ready otherwise false
   */
  public static boolean adminServerReady(String domainUID, String namespace) {
    return Kubernetes.adminServerReady(domainUID, namespace);
  }

  /**
   * Check if a adminserver T3 channel is accessible.
   *
   * @param domainUID id of the domain in which admin server pod is running
   * @param namespace in which the WebLogic server pod exists
   * @return true if the admin T3 channel is accessible otherwise false
   */
  public static boolean adminT3ChannelAccessible(String domainUID, String namespace) {
    return Domain.adminT3ChannelAccessible(domainUID, namespace);
  }

  /**
   * Check if a admin server pod admin node port is accessible.
   *
   * @param domainUID domainUID id of the domain in which admin server pod is running
   * @param namespace in which the WebLogic server pod exists
   * @return true if the admin node port is accessible otherwise false
   */
  public static boolean adminNodePortAccessible(String domainUID, String namespace) {
    return Domain.adminNodePortAccessible(domainUID, namespace);
  }
  
  /**
   * Check if a Docker image exists.
   * @param imageName the name of the image to be checked
   * @param imageTag  the tag of the image to be checked
   * @return true if the image does exist, false otherwise
   */
  public static boolean dockerImageExists(String imageName, String imageTag) {
    return WITAssertion.doesImageExist(imageName, imageTag);
  }

}
