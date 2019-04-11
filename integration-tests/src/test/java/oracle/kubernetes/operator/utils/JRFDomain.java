// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Map;
import oracle.kubernetes.operator.BaseTest;

/** JRF Domain class with all the utility methods */
public class JRFDomain extends Domain {

  private static final String FMWINFRA_DOCKER_IMAGENAME =
      "phx.ocir.io/weblogick8s/oracle/fmw-infrastructure";
  private static final String FMWINFRA_DOCKER_IMAGETAG = "12.2.1.3";

  /**
   * JRFDomain constructor
   *
   * @param inputYaml - jrf domain input yaml file, which should contain the properties used for jrf
   *     domain creation
   * @throws Exception - if any error occurs
   */
  public JRFDomain(String inputYaml) throws Exception {
    // read input domain yaml to test
    this(TestUtils.loadYaml(inputYaml));
  }

  /**
   * JRFDomain constructor
   *
   * @param inputDomainMap - jrf domain input properties map, which should contain the properties
   *     used for domain creation
   * @throws Exception - if any error occurs
   */
  public JRFDomain(Map<String, Object> inputDomainMap) throws Exception {
    initialize(inputDomainMap);
    updateDomainMapForJRF();
    createPV();
    createSecret();
    generateInputYaml();
    callCreateDomainScript(userProjectsDir);
    // TODO: add load balancer later
    // createLoadBalancer();
  }

  /**
   * update the domainMap with jrf specific information
   *
   * @throws Exception - if any error occurs
   */
  private void updateDomainMapForJRF() throws Exception {

    // jrf specific input parameter
    domainMap.put("image", FMWINFRA_DOCKER_IMAGENAME + ":" + FMWINFRA_DOCKER_IMAGETAG);

    if (!domainMap.containsKey("domainHomeImageBase")) {
      // update jrf/create-domain-script.sh with domain_name and rcuprefix
      TestUtils.replaceStringInFile(
          BaseTest.getResultDir() + "/jrf/create-domain-script.sh", "%RCUPREFIX%", domainUid);
      TestUtils.replaceStringInFile(
          BaseTest.getResultDir() + "/jrf/create-domain-script.sh", "%DOMAIN_NAME%", domainUid);
      domainMap.put("createDomainFilesDir", BaseTest.getResultDir() + "/jrf");
    }

    if (System.getenv("IMAGE_PULL_SECRET_FMWINFRA") != null) {
      domainMap.put("imagePullSecretName", System.getenv("IMAGE_PULL_SECRET_FMWINFRA"));
    } else {
      domainMap.put("imagePullSecretName", "ocir-store}");
    }
  }
}
