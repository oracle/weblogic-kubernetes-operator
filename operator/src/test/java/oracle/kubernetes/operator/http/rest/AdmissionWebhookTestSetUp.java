// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;

import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.Model;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;

/**
 * AdmissionWebhookTestSetup creates the necessary Domain resources that can be used in admission webhook
 * related test cases such as WebhookRestTest and ValidationUtilsTest.
 */
public class AdmissionWebhookTestSetUp {
  private static final String CLUSTER_NAME_1 = "C1";
  private static final String CLUSTER_NAME_2 = "C2";
  public static final String ORIGINAL_IMAGE_NAME = "abcd";
  public static final int ORIGINAL_REPLICAS = 2;
  public static final String ORIGINAL_INTROSPECT_VERSION = "1234";
  public static final String NEW_IMAGE_NAME = "NewImage";
  public static final String NEW_INTROSPECT_VERSION = "5678";
  public static final int BAD_REPLICAS = 4;
  public static final int GOOD_REPLICAS = 1;
  public static final String NEW_LOG_HOME = "/home/dir";
  public static final String AUX_IMAGE_1 = "image1";
  public static final String AUX_IMAGE_2 = "Image2";

  /**
   * Create a Domain resource model that contains the domain configuration and status for WebhookRestTest
   * and ValidationUtilsTest.
   */
  public static DomainResource createDomain() {
    DomainResource domain = createTestDomain().withStatus(createDomainStatus());
    domain.getSpec()
        .withReplicas(ORIGINAL_REPLICAS)
        .withImage(ORIGINAL_IMAGE_NAME)
        .setIntrospectVersion(ORIGINAL_INTROSPECT_VERSION);
    domain.getSpec()
        .withCluster(createCluster(CLUSTER_NAME_1))
        .withCluster(createCluster(CLUSTER_NAME_2));
    return domain;
  }

  private static Cluster createCluster(String clusterName) {
    return new Cluster().withClusterName(clusterName);
  }

  private static DomainStatus createDomainStatus() {
    return new DomainStatus()
        .addCluster(createClusterStatus(CLUSTER_NAME_1))
        .addCluster(createClusterStatus(CLUSTER_NAME_2));
  }

  private static ClusterStatus createClusterStatus(String clusterName) {
    return new ClusterStatus().withClusterName(clusterName).withMaximumReplicas(ORIGINAL_REPLICAS);
  }

  static AuxiliaryImage createAuxiliaryImage(String imageName) {
    return new AuxiliaryImage().image(imageName);
  }

  static void setAuxiliaryImages(DomainResource domain, List<AuxiliaryImage> images) {
    domain.getSpec().withConfiguration(new Configuration().withModel(new Model().withAuxiliaryImages(images)));
  }

}
