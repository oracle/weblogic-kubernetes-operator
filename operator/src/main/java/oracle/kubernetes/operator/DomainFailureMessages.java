// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

import static oracle.kubernetes.common.logging.MessageKeys.TOO_MANY_REPLICAS_FAILURE;

public class DomainFailureMessages {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private DomainFailureMessages() {
  }

  public static String createReplicaFailureMessage(String clusterName, int specifiedReplicaCount, int maxReplicaCount) {
    return LOGGER.formatMessage(TOO_MANY_REPLICAS_FAILURE, specifiedReplicaCount, clusterName, maxReplicaCount);
  }
}
