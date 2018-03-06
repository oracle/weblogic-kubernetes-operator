// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainSpec;

/**
 * Utility class for WebLogic configuration related classes
 */
public class Util {

  /**
   * Return prefix for names of machines that will be used servers in a dynamic cluster.
   * The prefix would be "[domainUID]-[clusterName]-machine"
   *
   * @param domainSpec DomainSpec for the weblogic operator domain
   * @param wlsClusterConfig WlsClusterConfig object containing the dynamic cluster
   * @return Prefix for names of machines that will be used servers in a dynamic cluster
   */
  static String getMachineNamePrefix(DomainSpec domainSpec, WlsClusterConfig wlsClusterConfig) {
    if (domainSpec != null && domainSpec.getDomainUID() != null
      && wlsClusterConfig != null && wlsClusterConfig.getClusterName() != null) {
      return domainSpec.getDomainUID() + "-" + wlsClusterConfig.getClusterName() + "-machine";
    }
    return null;
  }
}
