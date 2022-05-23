// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.net.URISyntaxException;

import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;

/** Helper class to ensure Cluster CRD is created. */
public class ClusterCrdHelper {

  private ClusterCrdHelper() {
  }

  /**
   * Used by build to generate crd-validation.yaml
   * @param args Arguments that must be one value giving file name to create
   */
  public static void main(String... args) throws URISyntaxException {
    if (args == null || args.length != 1) {
      throw new IllegalArgumentException();
    }

    CrdHelper.writeCrdFiles(args[0], KubernetesConstants.CLUSTERS_CRD_NAME, KubernetesConstants.CLUSTER_VERSION,
        KubernetesConstants.CLUSTER_PLURAL, KubernetesConstants.CLUSTER_SINGULAR,
        KubernetesConstants.CLUSTER, KubernetesConstants.CLUSTER_SHORT, ClusterSpec.class,
        ClusterStatus.class);
  }

  /**
   * Create step for creating Cluster CRD.
   * @param version Kubernetes version.
   * @param productVersion Product version.
   * @return Step for creating Cluster CRD.
   */
  public static Step createClusterCrdStep(KubernetesVersion version, SemanticVersion productVersion) {
    return new CrdHelper.CrdStep(version, productVersion, null,
        KubernetesConstants.CLUSTERS_CRD_NAME, KubernetesConstants.CLUSTER_VERSION,
        KubernetesConstants.CLUSTER_PLURAL, KubernetesConstants.CLUSTER_SINGULAR,
        KubernetesConstants.CLUSTER, KubernetesConstants.CLUSTER_SHORT, ClusterSpec.class,
        ClusterStatus.class);
  }

  /**
   * Create step for creating Cluster CRD.
   * @param version Kubernetes version.
   * @param productVersion Product version.
   * @param certificates Security certificates.
   * @return Step for creating Cluster CRD.
   */
  public static Step createClusterCrdStep(KubernetesVersion version, SemanticVersion productVersion,
                                         Certificates certificates) {
    return new CrdHelper.CrdStep(version, productVersion, certificates, KubernetesConstants.CLUSTERS_CRD_NAME,
        KubernetesConstants.CLUSTER_VERSION, KubernetesConstants.CLUSTER_PLURAL,
        KubernetesConstants.CLUSTER_SINGULAR, KubernetesConstants.CLUSTER,
        KubernetesConstants.CLUSTER_SHORT, ClusterSpec.class, ClusterStatus.class);
  }
}
