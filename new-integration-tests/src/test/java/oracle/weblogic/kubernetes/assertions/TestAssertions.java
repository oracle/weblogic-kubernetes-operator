// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions;

import oracle.weblogic.kubernetes.assertions.impl.Domain;
import oracle.weblogic.kubernetes.assertions.impl.Operator;

import java.util.concurrent.Callable;
import oracle.weblogic.kubernetes.assertions.impl.Kubernetes;

// as in the actions, it is intended tests only use these assertaions and do
// not go direct to the impl classes
public class TestAssertions {

  /**
   * Check if Operator is running.
   * @param namespace in which is operator is running
   * @return true if running false otherwise
   */
  public static Callable<Boolean> operatorIsRunning(String namespace) {
    return Operator.isRunning(namespace);
  }

  /**
   * Check if operator Rest service is running.
   * @param namespace the operator rest service exists
   * @return true if rest service is running otherwise false
   */
  public static boolean operatorRestServiceRunning(String namespace) {
    return Operator.isRestServiceCreated(namespace);
  }

  /**
   * Check if a weblogic custom resource domain exists in specified namespace and all its pods are running.
   * @param domainUID ID of the domain
   * @param namespace in which the domain custom resource exists
   * @return true if domain exists and pods running otherwise false
   */
  public static Callable<Boolean> domainExists(String domainUID, String namespace) {
    return Domain.exists(domainUID, namespace);
  }

  /**
   * Check if a kubernetes pod is in running/ready state.
   * @param podName name of the pod to check for
   * @param domainUID weblogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @return true if the pod is running otherwise false
   */
  public static Callable<Boolean> podReady(String podName, String domainUID, String namespace) {
    return Kubernetes.podRunning(podName, domainUID, namespace);
  }

  /**
   * Check if a pod given by the podName is in Terminating state.
   * @param podName name of the pod to check for Terminating status
   * @param domainUID weblogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @return true if the pod is terminating otherwise false
   */
  public static Callable<Boolean> podTerminating(String podName, String domainUID, String namespace) {
    return Kubernetes.podTerminating(podName, domainUID, namespace);
  }

  /**
   * Check is a service exists in given namespace.
   * @param serviceName  the name of the service to check for
   * @param namespace in which the service is running
   * @return true if the service exists otherwise false
   */
  public static boolean serviceReady(String serviceName, String namespace) {
    return Kubernetes.serviceCreated(serviceName, namespace);
  }

  /**
   * Check if a loadbalancer pod is ready.
   * @param domainUID id of the weblogic domain custom resource domain
   * @return
   */
  public static boolean loadbalancerReady(String domainUID) {
    return Kubernetes.loadBalancerReady(domainUID);
  }

  /**
   * Check if the admin server pod is ready.
   * @param domainUID id of the domain in which admin server pod is running
   * @param namespace in which the pod exists
   * @return true if the admin server is ready otherwise false
   */
  public static boolean adminServerReady(String domainUID, String namespace) {
    return Kubernetes.adminServerReady(domainUID, namespace);
  }

  /**
   * Check if a adminserver T3 channel is accessible.
   * @param domainUID id of the domain in which admin server pod is running
   * @param namespace in which the weblogic server pod exists
   * @return true if the admin T3 channel is accessible otherwise false
   */
  public static boolean adminT3ChannelAccessible(String domainUID, String namespace) {
    return Domain.adminT3ChannelAccessible(domainUID, namespace);
  }

  /**
   * Check if a admin server pod admin node port is accessible.
   * @param domainUID domainUID id of the domain in which admin server pod is running
   * @param namespace in which the weblogic server pod exists
   * @return true if the admin node port is accessible otherwise false
   */
  public static boolean adminNodePortAccessible(String domainUID, String namespace) {
    return Domain.adminNodePortAccessible(domainUID, namespace);
  }

}
