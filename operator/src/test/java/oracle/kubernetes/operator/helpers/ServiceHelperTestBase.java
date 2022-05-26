// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ClusterService;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.AfterEach;

public class ServiceHelperTestBase {
  protected static final String TEST_CLUSTER = "cluster-1";
  static final String DOMAIN_NAME = "domain1";
  static final String NS = "namespace";
  static final String UID = "uid1";
  static final String KUBERNETES_UID = "12345";
  final List<Memento> mementos = new ArrayList<>();
  final DomainPresenceInfo domainPresenceInfo = createPresenceInfo();

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);
  }

  private DomainPresenceInfo createPresenceInfo() {
    return new DomainPresenceInfo(
        new DomainResource()
            .withApiVersion(KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION)
            .withKind(KubernetesConstants.DOMAIN)
            .withMetadata(new V1ObjectMeta().namespace(NS).name(DOMAIN_NAME).uid(KUBERNETES_UID))
            .withSpec(createDomainSpec()));
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec().withDomainUid(UID)
        .withCluster(new Cluster().withClusterName(TEST_CLUSTER)
            .withClusterService(new ClusterService().withSessionAffinity(V1ServiceSpec.SessionAffinityEnum.CLIENTIP)));
  }
}
