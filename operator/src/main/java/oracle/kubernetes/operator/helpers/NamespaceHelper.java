// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Strings;
import oracle.kubernetes.operator.Main;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

import static oracle.kubernetes.operator.helpers.HelmAccess.getHelmVariable;

/**
 * Operations for dealing with namespaces.
 */
public class NamespaceHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public static final String DEFAULT_NAMESPACE = "default";
  public static final String SELECTION_STRATEGY_KEY = "domainNamespaceSelectionStrategy";

  private static final String operatorNamespace = computeOperatorNamespace();

  private static String computeOperatorNamespace() {
    return Optional.ofNullable(getHelmVariable("OPERATOR_NAMESPACE")).orElse(DEFAULT_NAMESPACE);
  }

  public static String getOperatorNamespace() {
    return operatorNamespace;
  }

  /**
   * Parse a string of namespace names and return them as a collection.
   * @param namespaceString a comma-separated list of namespace names
   */
  public static Collection<String> parseNamespaceList(String namespaceString) {
    Collection<String> namespaces
          = Stream.of(namespaceString.split(","))
          .filter(s -> !Strings.isNullOrEmpty(s))
          .map(String::trim)
          .collect(Collectors.toUnmodifiableList());

    return namespaces.isEmpty() ? Collections.singletonList(operatorNamespace) : namespaces;
  }

  public static TuningParameters tuningAndConfig() {
    return TuningParameters.getInstance();
  }

  /**
   * Gets the configured domain namespace selection strategy.
   * @return Selection strategy
   */
  public static Main.Namespaces.SelectionStrategy getSelectionStrategy() {
    Main.Namespaces.SelectionStrategy strategy =
        Optional.ofNullable(tuningAndConfig().get(SELECTION_STRATEGY_KEY))
              .map(Main.Namespaces.SelectionStrategy::valueOf)
              .orElse(Main.Namespaces.SelectionStrategy.List);

    if (Main.Namespaces.SelectionStrategy.List.equals(strategy) && isDeprecatedDedicated()) {
      return Main.Namespaces.SelectionStrategy.Dedicated;
    }
    return strategy;
  }

  // Returns true if the deprecated way to specify the dedicated namespace strategy is being used.
  // This value will only be used if the 'list' namespace strategy is specified or defaulted.
  private static boolean isDeprecatedDedicated() {
    return "true".equalsIgnoreCase(Optional.ofNullable(tuningAndConfig().get("dedicated")).orElse("false"));
  }

}
