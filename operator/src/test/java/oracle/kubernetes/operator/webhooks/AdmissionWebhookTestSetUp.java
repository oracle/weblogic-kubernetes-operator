// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.webhooks.model.Scale;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.DomainCreationImage;
import oracle.kubernetes.weblogic.domain.model.DomainOnPV;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.InitializeDomainOnPV;
import oracle.kubernetes.weblogic.domain.model.Model;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;

/**
 * AdmissionWebhookTestSetup creates the necessary Domain resources that can be used in admission webhook
 * related test cases such as WebhookRestTest and admission checker tests.
 */
class AdmissionWebhookTestSetUp {
  static final String CLUSTER_NAME_1 = "C1";
  static final String CLUSTER_NAME_2 = "C2";
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
   * and admission checker tests.
   */
  public static DomainResource createDomainWithClustersAndStatus() {
    DomainResource domain = createTestDomain().withStatus(createDomainStatus());
    domain.getSpec()
        .withReplicas(ORIGINAL_REPLICAS)
        .withImage(ORIGINAL_IMAGE_NAME)
        .setIntrospectVersion(ORIGINAL_INTROSPECT_VERSION);
    domain.getSpec()
        .withCluster(new V1LocalObjectReference().name(CLUSTER_NAME_1))
        .withCluster(new V1LocalObjectReference().name(CLUSTER_NAME_2));
    return domain;
  }

  /**
   * Create a Domain resource model that contains the domain configuration without any clusters for WebhookRestTest
   * and admission checker tests.
   */
  public static DomainResource createDomainWithoutCluster() {
    DomainResource domain = createTestDomain();
    domain.getSpec()
        .withReplicas(ORIGINAL_REPLICAS)
        .withImage(ORIGINAL_IMAGE_NAME)
        .setIntrospectVersion(ORIGINAL_INTROSPECT_VERSION);
    return domain;
  }

  /**
   * Create a Cluster resource model that contains the cluster configuration and status for WebhookRestTest
   * and ValidationUtilsTest.
   *
   * @param clusterName the name of the cluster resource
   * @return the cluster resource created
   */
  public static ClusterResource createCluster(String clusterName) {
    ClusterResource clusterResource =
        new ClusterResource().withMetadata(new V1ObjectMeta().name(clusterName).namespace(NS))
            .spec(createClusterSpec(clusterName));
    clusterResource.setApiVersion((KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.CLUSTER_VERSION));
    clusterResource.setStatus(createClusterStatus(clusterName));
    return clusterResource;
  }

  /**
   * Create a Cluster resource model that contains the cluster configuration and status for WebhookRestTest
   * and ValidationUtilsTest.
   *
   * @return the cluster resource created
   */
  public static ClusterResource createCluster() {
    ClusterResource clusterResource =
        new ClusterResource().withMetadata(new V1ObjectMeta().name(CLUSTER_NAME_1).namespace(NS))
            .spec(createClusterSpec(CLUSTER_NAME_1));
    clusterResource.setApiVersion((KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.CLUSTER_VERSION));
    clusterResource.setStatus(createClusterStatus(CLUSTER_NAME_1));
    return clusterResource;
  }

  private static ClusterSpec createClusterSpec(String clusterName) {
    return new ClusterSpec().withClusterName(clusterName);
  }

  private static DomainStatus createDomainStatus() {
    return new DomainStatus()
        .addCluster(createClusterStatus(CLUSTER_NAME_1))
        .addCluster(createClusterStatus(CLUSTER_NAME_2));
  }

  private static ClusterStatus createClusterStatus(String clusterName) {
    return new ClusterStatus().withClusterName(clusterName).withMaximumReplicas(ORIGINAL_REPLICAS);
  }

  public static AuxiliaryImage createAuxiliaryImage(String imageName) {
    return new AuxiliaryImage().image(imageName);
  }

  public static void setAuxiliaryImages(DomainResource domain, List<AuxiliaryImage> images) {
    domain.getSpec().withConfiguration(new Configuration().withModel(new Model().withAuxiliaryImages(images)));
  }

  public static void setDomainCreationImages(DomainResource domain, List<DomainCreationImage> images) {
    domain.getSpec().withConfiguration(new Configuration()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().domain(new DomainOnPV().domainCreationImages(images))));
  }

  /**
   * Create a scale model that contains the scale request and status for WebhookRestTest
   * and ValidationUtilsTest.
   *
   * @param clusterName the name of the cluster resource
   * @param replicas the replica count of the scale request
   * @return the scale object created
   */
  public static Scale createScale(String clusterName, String replicas) {
    Map<String, String> spec = new HashMap<>();
    spec.put("replicas", replicas);

    return new Scale().metadata(new V1ObjectMeta().name(clusterName).namespace(NS)).spec(spec);
  }
}
