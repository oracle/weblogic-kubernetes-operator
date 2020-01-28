// Copyright (c) 2020, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Optional;
import java.util.function.Function;

import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Resource;
import oracle.kubernetes.operator.helpers.CrdHelper;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.Step;

/**
 * Controls the steps that the operator runs at startup based on if the dedicated setting is set 
 * to true or false, and whether the operator's service account has the necessary permission.
 */
public class StartupControl {
  static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  static final String OPERATOR_DEDICATED_ENV = "OPERATOR_DEDICATED";
  static final String OPERATOR_NAMESPACE_ENV = "OPERATOR_NAMESPACE";
  
  private static Function<String,String> getHelmVariable = System::getenv;
  private KubernetesVersion version;

  public StartupControl(KubernetesVersion version) {
    this.version = version;
  }

  // Some of the steps will be skipped if the oeprator does not have the necessary permission.
  private Step getStep(Step stepToStart, Step readNamespaceStep) {
    if (isClusterAccessAllowed(Resource.CRDS, Operation.get)) {
      stepToStart = CrdHelper.createDomainCrdStep(getVersion(), stepToStart);
    } else {
      LOGGER.warning(MessageKeys.CRD_NO_READ_ACCESS);
    }
    if (isClusterAccessAllowed(Resource.NAMESPACES, Operation.list)) {
      stepToStart = Step.chain(stepToStart, readNamespaceStep);
    } else {
      LOGGER.warning(MessageKeys.NS_NO_READ_ACCESS);
    }
    return stepToStart;
  }

  public KubernetesVersion getVersion() {
    return version;
  }

  /**
   * Computes steps to run before or after the proposed start step.
   * @param proposedStartStep the first step to run if nothing is added
   * @param readNamespaceStep a step that reads the existing namespaces
   * @return a chain of start steps
   */
  Step getSteps(Step proposedStartStep, Step readNamespaceStep) {
    return getStep(proposedStartStep, readNamespaceStep);
  }

  /**
   * Checks if the dedicated set to true via the operator helm chart or an environment variable.
   * @return true if either the environment variable or the tuning parameter is set to true
   */
  public boolean isDedicated() {
    final String result = Optional.ofNullable(getHelmVariable.apply(OPERATOR_DEDICATED_ENV))
          .orElse(TuningParameters.getInstance().get("dedicated"));

    return "true".equals(result);
  }

  public static String getOperatorNamespace() {
    return Optional.ofNullable(getHelmVariable.apply("OPERATOR_NAMESPACE")).orElse("default");
  }

  /**
   * Checks if the operator can perform a specific operation on a given Kubernetes cluster-wide resource.
   * @return true if either "dedicated" is set to false or the operator has given the required
   *         permission via RBAC policies
   */
  public boolean isClusterAccessAllowed(Resource resource, Operation op) {
    return !isDedicated() || HealthCheckHelper.isClusterResourceAccessAllowed(version, resource, op);
  }
}
