// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;

class DomainRecheck {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final DomainProcessor domainProcessor;
  private final DomainNamespaces domainNamespaces;
  private final boolean fullRecheck;

  DomainRecheck(MainDelegate delegate, boolean fullRecheck) {
    this(delegate.getDomainProcessor(), delegate.getDomainNamespaces(), fullRecheck);
  }

  DomainRecheck(MainDelegate delegate) {
    this(delegate, false);
  }

  DomainRecheck(DomainProcessor domainProcessor, DomainNamespaces domainNamespaces) {
    this(domainProcessor, domainNamespaces, false);
  }

  DomainRecheck(DomainProcessor domainProcessor, DomainNamespaces domainNamespaces, boolean fullRecheck) {
    this.domainProcessor = domainProcessor;
    this.domainNamespaces = domainNamespaces;
    this.fullRecheck = fullRecheck;
  }

  NamespaceRulesReviewStep createOperatorNamespaceReview() {
    return new NamespaceRulesReviewStep(getOperatorNamespace());
  }

  NamespaceRulesReviewStep createNamespaceReview(String namespace) {
    return new NamespaceRulesReviewStep(namespace);
  }

  Step createReadNamespacesStep() {
    return Namespaces.getSelection(new ReadNamespacesStepsVisitor());
  }


  /**
   * This step logs warnings to the operator console if the specified domain namespace lacks the required privileges.
   */
  class NamespaceRulesReviewStep extends Step {
    private final String ns;

    private NamespaceRulesReviewStep(@Nonnull String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction apply(Packet packet) {
      NamespaceStatus nss = domainNamespaces.getNamespaceStatus(ns);

      // we don't have the domain presence information yet
      // we add a logging context to pass the namespace information to the LoggingFormatter
      packet.getComponents().put(
          LoggingContext.LOGGING_CONTEXT_KEY,
          Component.createFor(new LoggingContext().namespace(ns)));

      nss.getRulesReviewStatus().updateAndGet(prev -> {
        if (prev != null) {
          return prev;
        }

        try {
          return HealthCheckHelper.getAccessAuthorizations(ns);
        } catch (Throwable e) {
          LOGGER.warning(MessageKeys.EXCEPTION, e);
        }
        return null;
      });

      return doNext(packet);
    }

  }

  class ReadNamespacesStepsVisitor implements NamespaceStrategyVisitor<Step> {

    @Override
    public Step getDedicatedStrategySelection() {
      return createStartNamespacesStep(Collections.singletonList(getOperatorNamespace()));
    }

    @Override
    public Step getDefaultSelection() {
      return readExistingNamespaces();
    }
  }

  /**
   * Reads the existing namespaces from Kubernetes and performs appropriate processing on those
   * identified as domain namespaces.
   */
  Step readExistingNamespaces() {
    return new CallBuilder()
          .withLabelSelectors(Namespaces.getLabelSelectors())
          .listNamespaceAsync(new NamespaceListResponseStep());
  }

  private class NamespaceListResponseStep extends DefaultResponseStep<V1NamespaceList> {

    private NamespaceListResponseStep() {
      super(new Namespaces.NamespaceListAfterStep(domainNamespaces));
    }

    // If unable to list the namespaces, we may still be able to start them if we are using
    // a strategy that specifies them explicitly.
    @Override
    protected NextAction onFailureNoRetry(Packet packet, CallResponse<V1NamespaceList> callResponse) {
      return useBackupStrategy(callResponse)
            ? doNext(createStartNamespacesStep(Namespaces.getConfiguredDomainNamespaces()), packet)
            : super.onFailureNoRetry(packet, callResponse);
    }

    // Returns true if the failure wasn't due to authorization, and we have a list of namespaces to manage.
    private boolean useBackupStrategy(CallResponse<V1NamespaceList> callResponse) {
      return Namespaces.getConfiguredDomainNamespaces() != null && isNotAuthorizedOrForbidden(callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1NamespaceList> callResponse) {
      final Set<String> domainNamespaces = getNamespacesToStart(getNames(callResponse.getResult()));
      Namespaces.getFoundDomainNamespaces(packet).addAll(domainNamespaces);

      return doContinueListOrNext(callResponse, packet, createNextSteps(domainNamespaces));
    }

    private Step createNextSteps(Set<String> namespacesToStartNow) {
      List<Step> nextSteps = new ArrayList<>();
      if (!namespacesToStartNow.isEmpty()) {
        nextSteps.add(createStartNamespacesStep(namespacesToStartNow));
        if (Namespaces.getConfiguredDomainNamespaces() == null) {
          nextSteps.add(
                RunInParallel.perNamespace(namespacesToStartNow, DomainRecheck.this::createNamespaceReview));
        }
      }
      nextSteps.add(getNext());
      return Step.chain(nextSteps.toArray(new Step[0]));
    }

    private Set<String> getNamespacesToStart(List<String> namespaceNames) {
      return namespaceNames.stream().filter(Namespaces::isDomainNamespace).collect(Collectors.toSet());
    }

    private List<String> getNames(V1NamespaceList result) {
      return result.getItems().stream()
            .map(V1Namespace::getMetadata)
            .filter(Objects::nonNull)
            .map(V1ObjectMeta::getName)
            .collect(Collectors.toList());
    }
  }

  Step createStartNamespacesStep(Collection<String> domainNamespaces) {
    return RunInParallel.perNamespace(domainNamespaces, this::startNamespaceSteps);
  }

  Step startNamespaceSteps(String ns) {
    return Step.chain(
          createNamespaceReview(ns),
          new StartNamespaceBeforeStep(ns),
          domainNamespaces.readExistingResources(ns, domainProcessor));
  }

  private class StartNamespaceBeforeStep extends Step {

    private final String ns;

    StartNamespaceBeforeStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction apply(Packet packet) {
      NamespaceStatus nss = domainNamespaces.getNamespaceStatus(ns);
      if (fullRecheck || !nss.isNamespaceStarting().getAndSet(true)) {
        return doNext(packet);
      } else {
        return doEnd(packet);
      }
    }
  }

  /**
   * Given a list of namespace names and a method that creates steps for the namespace,
   * will create the appropriate steps and run them in parallel, waiting for all to complete
   * before proceeding.
   */
  static class RunInParallel extends Step {

    protected final Function<String, Step> stepFactory;
    private final Collection<String> domainNamespaces;

    RunInParallel(Collection<String> domainNamespaces, Function<String, Step> stepFactory) {
      this.domainNamespaces = domainNamespaces;
      this.stepFactory = stepFactory;
    }

    public static Step perNamespace(Collection<String> domainNamespaces, Function<String, Step> stepFactory) {
      return new RunInParallel(domainNamespaces, stepFactory);
    }

    @Override
    protected String getDetail() {
      return Optional.ofNullable(domainNamespaces).map(d -> String.join(",", d)).orElse(null);
    }

    @Override
    public NextAction apply(Packet packet) {
      if (domainNamespaces == null) {
        return doNext(packet);
      } else {
        Collection<StepAndPacket> startDetails = new ArrayList<>();

        for (String ns : domainNamespaces) {
          try (LoggingContext ignored = LoggingContext.setThreadContext().namespace(ns)) {
            startDetails.add(new StepAndPacket(stepFactory.apply(ns), packet.clone()));
          }
        }
        return doForkJoin(getNext(), packet, startDetails);
      }
    }
  }
}
