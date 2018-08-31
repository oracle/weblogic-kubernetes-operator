// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

class DomainPresenceControl {

  // This method fills in null values which would interfere with the general DomainSpec.equals()
  // method
  static void normalizeDomainSpec(DomainSpec spec) {
    normalizeImage(spec);
    normalizeImagePullPolicy(spec);
    normalizeExportT3Channels(spec);
    normalizeStartupControl(spec);
  }

  private static void normalizeImage(DomainSpec spec) {
    if (isNotDefined(spec.getImage())) spec.setImage(KubernetesConstants.DEFAULT_IMAGE);
  }

  private static void normalizeImagePullPolicy(DomainSpec spec) {
    if (isNotDefined(spec.getImagePullPolicy())) {
      spec.setImagePullPolicy(
          (spec.getImage().endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX))
              ? KubernetesConstants.ALWAYS_IMAGEPULLPOLICY
              : KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY);
    }
  }

  private static void normalizeExportT3Channels(DomainSpec spec) {
    if (spec.getExportT3Channels() == null) spec.setExportT3Channels(new ArrayList<>());
  }

  private static void normalizeStartupControl(DomainSpec spec) {
    if (isNotDefined(spec.getStartupControl()))
      spec.setStartupControl(StartupControlConstants.AUTO_STARTUPCONTROL);
  }

  private static boolean isNotDefined(String value) {
    return value == null || value.length() == 0;
  }

  static void cancelDomainStatusUpdating(DomainPresenceInfo info) {
    ScheduledFuture<?> statusUpdater = info.getStatusUpdater().getAndSet(null);
    if (statusUpdater != null) {
      statusUpdater.cancel(true);
    }
  }
}
