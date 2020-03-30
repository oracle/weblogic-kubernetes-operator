/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

/**
 *
 * @author Sankar
 */
public class Kubernetes {

  public static Callable<Boolean> podExists(String podName, String domainUID, String namespace) {
    return () -> {
      return true;
    };
  }

  public static Callable<Boolean> podRunning(String podName, String domainUID, String namespace) {
    return () -> {
      return true;
    };
  }

  public static Callable<Boolean> podTerminating(String podName, String domainUID, String namespace) {
    return () -> {
      return true;
    };
  }

  public static boolean serviceCreated(String domainUID, String namespace) {
    return true;
  }

  public static boolean loadBalancerReady(String domainUID) {
    return true;
  }

  public static boolean adminServerReady(String domainUID, String namespace) {
    return true;
  }
}
