// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.google.common.base.Strings;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;

class DomainPresenceControl {

  // This method fills in null values which would interfere with the general DomainSpec.equals()
  // method
  static void normalizeDomainSpec(DomainSpec spec) {
    normalizeImage(spec);
    normalizeImagePullPolicy(spec);
  }

  private static void normalizeImage(DomainSpec spec) {
    if (Strings.isNullOrEmpty(spec.getImage())) {
      spec.setImage(KubernetesConstants.DEFAULT_IMAGE);
    }
  }

  private static void normalizeImagePullPolicy(DomainSpec spec) {
    if (Strings.isNullOrEmpty(spec.getImagePullPolicy())) {
      spec.setImagePullPolicy(
          (spec.getImage().endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX))
              ? KubernetesConstants.ALWAYS_IMAGEPULLPOLICY
              : KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY);
    }
  }
}
