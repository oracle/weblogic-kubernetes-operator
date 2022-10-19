// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.webhooks.resource.AdmissionChecker;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.CLUSTER_NAME_2;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createCluster;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createDomainWithClustersAndStatus;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createDomainWithoutCluster;

abstract class AdmissionCheckerTestBase {
  static final String MOUNT_NAME = "bad-mount";
  static final String BAD_MOUNT_PATH = "mydir/mount";
  static final String MOUNT_PATH_WITH_TOKEN = "$(DOMAIN_HOME)/mount";
  static final String MOUNT_PATH_WITH_TOKEN_2 = "/mydir/$(DOMAIN_HOME)/mount";
  static final String GOOD_MOUNT_PATH = "/mydir/mount";
  static final String GOOD_CONTAINER_NAME = "abcdef";
  static final String BAD_PORT_NAME = "abcdefghijklmnopqrstuvw";
  static final String GOOD_PORT_NAME = "abcdef";

  final List<Memento> mementos = new ArrayList<>();
  final KubernetesTestSupport testSupport = new KubernetesTestSupport();

  final DomainResource existingDomain = createDomainWithClustersAndStatus();
  final DomainResource proposedDomain = createDomainWithClustersAndStatus();
  final DomainResource proposedDomain2 = createDomainWithoutCluster();
  final ClusterResource existingCluster = createCluster();
  final ClusterResource proposedCluster = createCluster();
  final ClusterResource proposedCluster2 = createCluster(CLUSTER_NAME_2);

  AdmissionChecker domainChecker;
  AdmissionChecker clusterChecker;

  abstract void setupCheckers();

  @BeforeEach
  public void setUp() throws NoSuchFieldException, IOException {
    mementos.add(testSupport.install());
    setupCheckers();
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }
}
