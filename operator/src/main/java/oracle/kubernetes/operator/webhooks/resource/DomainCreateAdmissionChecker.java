// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.resource;

import java.util.ArrayList;
import java.util.List;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponse;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponseStatus;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.jetbrains.annotations.NotNull;

/**
 * DomainCreateAdmissionChecker provides the validation functionality for the validating webhook. It takes
 * a proposed domain resource and returns a result to indicate if the proposed resource is allowed, and if not,
 * what the problem is.
 *
 * <p>Currently it checks the following:
 * <ul>
 * <li>There are fatal domain validation errors.
 * </li>
 * </ul>
 * </p>
 */

public class DomainCreateAdmissionChecker extends AdmissionChecker {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");

  private final DomainResource proposedDomain;
  final List<String> warnings = new ArrayList<>();

  /**
   * Construct a DomainCreateAdmissionChecker.
   */
  public DomainCreateAdmissionChecker(@NotNull DomainResource proposedDomain) {
    this.proposedDomain = proposedDomain;
  }

  @Override
  AdmissionResponse validate() {
    LOGGER.fine("Validating new DomainResource " + proposedDomain);

    AdmissionResponse response = new AdmissionResponse().allowed(isProposedChangeAllowed());
    if (!response.isAllowed()) {
      return response.status(new AdmissionResponseStatus().message(createMessage()));
    } else if (!warnings.isEmpty()) {
      return response.warnings(warnings);
    }
    return response;
  }

  @Override
  public boolean isProposedChangeAllowed() {
    return hasNoFatalValidationErrors(proposedDomain);
  }

  @Override
  public boolean hasException() {
    return false;
  }

}
