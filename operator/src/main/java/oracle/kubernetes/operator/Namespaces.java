// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.HelmAccess;
import oracle.kubernetes.operator.helpers.NamespaceHelper;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STOPPED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.STOP_MANAGING_NAMESPACE;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;

/**
 * A class which manages the strategy for recognizing the namespaces in which the operator will manage
 * domains.
 */
public class Namespaces {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  static final String SELECTION_STRATEGY_KEY = "domainNamespaceSelectionStrategy";
  /**
   * The key in a Packet of the collection of existing namespaces that are designated as domain namespaces.
   */
  private static final String ALL_DOMAIN_NAMESPACES = "ALL_DOMAIN_NAMESPACES";

  /**
   * Returns true if the specified string is the name of a domain namespace.
   * @param ns a namespace name
   */
  static boolean isDomainNamespace(String ns) {
    return getSelectionStrategy().isDomainNamespace(ns);
  }

  /**
   * Returns a (possibly empty) collection of strings which designate namespaces for the operator to manage.
   */
  static @Nullable Collection<String> getConfiguredDomainNamespaces() {
    return getSelectionStrategy().getConfiguredDomainNamespaces();
  }


  /**
   * Returns an array of the label selectors that will determine that a namespace is being used to manage domains.
   */
  static String[] getLabelSelectors() {
    return getSelectionStrategy().getLabelSelectors();
  }

  static <R> R getSelection(NamespaceStrategyVisitor<R> visitor) {
    return getSelectionStrategy().getSelection(visitor);
  }

  public enum SelectionStrategy {
    List {
      @Override
      public boolean isDomainNamespace(@Nonnull String namespaceName) {
        return getConfiguredDomainNamespaces().contains(namespaceName);
      }

      @Override
      public @Nonnull Collection<String> getConfiguredDomainNamespaces() {
        return NamespaceHelper.parseNamespaceList(getNamespaceList());
      }

      @Override
      public <V> V getSelection(NamespaceStrategyVisitor<V> visitor) {
        return visitor.getListStrategySelection();
      }

      private String getNamespaceList() {
        return Optional.ofNullable(HelmAccess.getHelmSpecifiedNamespaceList()).orElse(getInternalNamespaceList());
      }

      private String getInternalNamespaceList() {
        return Optional.ofNullable(getConfiguredNamespaceList()).orElse(getOperatorNamespace());
      }

      private String getConfiguredNamespaceList() {
        return Optional.ofNullable(TuningParameters.getInstance().get("domainNamespaces"))
              .orElse(TuningParameters.getInstance().get("targetNamespaces"));
      }
    },
    LabelSelector {
      @Override
      public String[] getLabelSelectors() {
        return new String[]{TuningParameters.getInstance().get("domainNamespaceLabelSelector")};
      }

      @Override
      public <V> V getSelection(NamespaceStrategyVisitor<V> visitor) {
        return visitor.getLabelSelectorStrategySelection();
      }

      @Override
      public boolean isDomainNamespace(@Nonnull String namespaceName) {
        return true;  // filtering is done by Kubernetes list call
      }
    },
    RegExp {
      @Override
      public boolean isDomainNamespace(@Nonnull String namespaceName) {
        try {
          return getCompiledPattern(getRegExp()).matcher(namespaceName).find();
        } catch (PatternSyntaxException e) {
          LOGGER.severe(MessageKeys.EXCEPTION, e);
          return false;
        }
      }

      @Override
      public <V> V getSelection(NamespaceStrategyVisitor<V> visitor) {
        return visitor.getRegexpStrategySelection();
      }

      private String getRegExp() {
        return TuningParameters.getInstance().get("domainNamespaceRegExp");
      }

      private Pattern getCompiledPattern(String regExp) {
        return compiledPatterns.computeIfAbsent(regExp, Pattern::compile);
      }
    },
    Dedicated {
      @Override
      public boolean isDomainNamespace(@Nonnull String namespaceName) {
        return namespaceName.equals(getOperatorNamespace());
      }

      @Override
      public Collection<String> getConfiguredDomainNamespaces() {
        return Collections.singleton(getOperatorNamespace());
      }

      @Override
      public <V> V getSelection(NamespaceStrategyVisitor<V> visitor) {
        return visitor.getDedicatedStrategySelection();
      }
    };

    static final String[] NO_SELECTORS = new String[0];

    public abstract boolean isDomainNamespace(@Nonnull String namespaceName);

    public String[] getLabelSelectors() {
      return NO_SELECTORS;
    }

    public @Nullable Collection<String> getConfiguredDomainNamespaces() {
      return null;
    }

    public abstract <V> V getSelection(NamespaceStrategyVisitor<V> visitor);

    private static final Map<String, Pattern> compiledPatterns = new WeakHashMap<>();
  }

  /**
   * Returns a modifiable collection of found namespace names in a packet.
   * Callers should use this to add to the collection.
   *
   * @param packet the packet passed to a step
   */
  @SuppressWarnings("unchecked")
  static Collection<String> getFoundDomainNamespaces(Packet packet) {
    if (!packet.containsKey(ALL_DOMAIN_NAMESPACES)) {
      packet.put(ALL_DOMAIN_NAMESPACES, new HashSet<>());
    }
    return (Collection<String>) packet.get(ALL_DOMAIN_NAMESPACES);
  }

  /**
   * Gets the configured domain namespace selection strategy.
   *
   * @return Selection strategy
   */
  static SelectionStrategy getSelectionStrategy() {
    SelectionStrategy strategy =
          Optional.ofNullable(TuningParameters.getInstance().get(SELECTION_STRATEGY_KEY))
                .map(SelectionStrategy::valueOf)
                .orElse(SelectionStrategy.List);

    if (SelectionStrategy.List.equals(strategy) && isDeprecatedDedicated()) {
      return SelectionStrategy.Dedicated;
    }
    return strategy;
  }

  // Returns true if the deprecated way to specify the dedicated namespace strategy is being used.
  // This value will only be used if the 'list' namespace strategy is specified or defaulted.
  private static boolean isDeprecatedDedicated() {
    return "true".equalsIgnoreCase(getDeprecatedDedicatedSetting());
  }

  private static String getDeprecatedDedicatedSetting() {
    return Optional.ofNullable(TuningParameters.getInstance().get("dedicated")).orElse("false");
  }


  // checks the list of namespace names collected above. If any configured namespaces are not found, logs a warning.
  static class NamespaceListAfterStep extends Step {

    private final DomainNamespaces domainNamespaces;

    NamespaceListAfterStep(DomainNamespaces domainNamespaces) {
      this.domainNamespaces = domainNamespaces;
    }

    @Override
    public NextAction apply(Packet packet) {
      NamespaceValidationContext validationContext = new NamespaceValidationContext(packet);
      getNonNullConfiguredDomainNamespaces().forEach(validationContext::validateConfiguredNamespace);
      List<StepAndPacket> nsStopEventSteps = getCreateNSStopEventSteps(packet, validationContext);
      stopRemovedNamespaces(validationContext);
      return doNext(Step.chain(createNamespaceWatchStopEventsStep(nsStopEventSteps), getNext()), packet);
    }

    private List<StepAndPacket> getCreateNSStopEventSteps(Packet packet, NamespaceValidationContext validationContext) {
      return domainNamespaces.getNamespaces().stream()
          .filter(validationContext::isNoLongerActiveDomainNamespace)
          .map(n -> createNSStopEventDetails(packet, n)).collect(Collectors.toList());
    }

    private StepAndPacket createNSStopEventDetails(Packet packet, String namespace) {
      LOGGER.info(MessageKeys.END_MANAGING_NAMESPACE, namespace);
      return new StepAndPacket(
          Step.chain(
              createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).resourceName(namespace).namespace(namespace)),
              createEventStep(new EventData(STOP_MANAGING_NAMESPACE).resourceName(namespace)
                  .namespace(getOperatorNamespace()))),
          packet.copy());
    }

    private Step createNamespaceWatchStopEventsStep(List<StepAndPacket> nsStopEventDetails) {
      return new NamespaceWatchStopEventsStep(nsStopEventDetails);
    }

    static class NamespaceWatchStopEventsStep extends Step {
      final List<StepAndPacket> nsStopEventDetails;

      NamespaceWatchStopEventsStep(List<StepAndPacket> nsStopeventDetails) {
        this.nsStopEventDetails = nsStopeventDetails;
      }

      @Override
      public NextAction apply(Packet packet) {
        if (nsStopEventDetails.isEmpty()) {
          return doNext(getNext(), packet);
        } else {
          return doForkJoin(getNext(), packet, nsStopEventDetails);
        }
      }



    }

    @Nonnull
    static Collection<String> getNonNullConfiguredDomainNamespaces() {
      return Optional.ofNullable(getConfiguredDomainNamespaces()).orElse(Collections.emptyList());
    }

    // Halts processing of any managed namespaces that are no longer to be managed, either because
    // they have been deleted from the Kubernetes cluster or because the operator is no longer configured for them.
    private void stopRemovedNamespaces(NamespaceValidationContext validationContext) {
      domainNamespaces.getNamespaces().stream()
            .filter(validationContext::isNoLongerActiveDomainNamespace)
            .forEach(domainNamespaces::stopNamespace);
    }
  }


  private static class NamespaceValidationContext {

    final Collection<String> allDomainNamespaces;

    NamespaceValidationContext(Packet packet) {
      allDomainNamespaces = Optional.ofNullable(getFoundDomainNamespaces(packet)).orElse(Collections.emptyList());
    }

    private boolean isNoLongerActiveDomainNamespace(String ns) {
      return !allDomainNamespaces.contains(ns);
    }

    private void validateConfiguredNamespace(String namespace) {
      if (isNoLongerActiveDomainNamespace(namespace)) {
        try (LoggingContext ignored = LoggingContext.setThreadContext().namespace(namespace)) {
          LOGGER.warning(MessageKeys.NAMESPACE_IS_MISSING, namespace);
        }
      }
    }
  }

}
