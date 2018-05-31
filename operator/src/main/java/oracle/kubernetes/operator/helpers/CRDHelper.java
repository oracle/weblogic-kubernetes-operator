// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionNames;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionSpec;
import java.util.Collections;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/** Helper class to ensure Domain CRD is created */
public class CRDHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private CRDHelper() {}

  /**
   * Validates and, if necessary, created domains.weblogic.oracle CRD. No need to be async as
   * operator can not begin processing until CRD exists
   */
  public static void checkAndCreateCustomResourceDefinition() {
    LOGGER.entering();

    V1beta1CustomResourceDefinition crd = new V1beta1CustomResourceDefinition();
    crd.setApiVersion("apiextensions.k8s.io/v1beta1");
    crd.setKind("CustomResourceDefinition");
    V1ObjectMeta om = new V1ObjectMeta();
    om.setName("domains.weblogic.oracle");
    crd.setMetadata(om);
    V1beta1CustomResourceDefinitionSpec crds = new V1beta1CustomResourceDefinitionSpec();
    crds.setGroup(KubernetesConstants.DOMAIN_GROUP);
    crds.setVersion(KubernetesConstants.DOMAIN_VERSION);
    crds.setScope("Namespaced");
    V1beta1CustomResourceDefinitionNames crdn = new V1beta1CustomResourceDefinitionNames();
    crdn.setPlural(KubernetesConstants.DOMAIN_PLURAL);
    crdn.setSingular(KubernetesConstants.DOMAIN_SINGULAR);
    crdn.setKind("Domain");
    crdn.setShortNames(Collections.singletonList(KubernetesConstants.DOMAIN_SHORT));
    crds.setNames(crdn);
    crd.setSpec(crds);

    CallBuilderFactory factory = new CallBuilderFactory();
    V1beta1CustomResourceDefinition existingCRD = null;
    try {
      existingCRD = factory.create().readCustomResourceDefinition(crd.getMetadata().getName());

    } catch (ApiException e) {
      if (e.getCode() != CallBuilder.NOT_FOUND) {
        LOGGER.warning(MessageKeys.EXCEPTION, e);
      }
    }

    try {
      if (existingCRD == null) {
        LOGGER.info(MessageKeys.CREATING_CRD, crd.toString());
        factory.create().createCustomResourceDefinition(crd);
      }
    } catch (ApiException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
    LOGGER.exiting();
  }
}
