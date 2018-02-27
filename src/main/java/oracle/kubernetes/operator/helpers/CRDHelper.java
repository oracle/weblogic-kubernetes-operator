// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionNames;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionSpec;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/**
 * Helper class to ensure Domain CRD is created
 *
 */
public class CRDHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  
  private CRDHelper() {}

  /**
   * Validates and, if necessary, created domains.weblogic.oracle CRD.  No need
   * to be async as operator can not begin processing until CRD exists
   * @param client Client holder
   */
  public static void checkAndCreateCustomResourceDefinition(ClientHolder client) {
    LOGGER.entering();

    V1beta1CustomResourceDefinition crd = new V1beta1CustomResourceDefinition();
    crd.setApiVersion("apiextensions.k8s.io/v1beta1");
    crd.setKind("CustomResourceDefinition");
    V1ObjectMeta om = new V1ObjectMeta();
    om.setName("domains.weblogic.oracle");
    crd.setMetadata(om);
    V1beta1CustomResourceDefinitionSpec crds = new V1beta1CustomResourceDefinitionSpec();
    crds.setGroup("weblogic.oracle");
    crds.setVersion("v1");
    crds.setScope("Namespaced");
    V1beta1CustomResourceDefinitionNames crdn = new V1beta1CustomResourceDefinitionNames();
    crdn.setPlural("domains");
    crdn.setSingular("domain");
    crdn.setKind("Domain");
    crdn.setShortNames(Collections.singletonList("dom"));
    crds.setNames(crdn);
    crd.setSpec(crds);

    V1beta1CustomResourceDefinition existingCRD = null;
    try {

      existingCRD = client.callBuilder().readCustomResourceDefinition(
          crd.getMetadata().getName());

    } catch (ApiException e) {
      if (e.getCode() != CallBuilder.NOT_FOUND) {
        LOGGER.warning(MessageKeys.EXCEPTION, e);
      }
    }

    try {
      if (existingCRD == null) {
        LOGGER.info(MessageKeys.CREATING_CRD, crd.toString());
        client.callBuilder().createCustomResourceDefinition(crd);
      }
    } catch (ApiException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
    LOGGER.exiting();
  }
}
