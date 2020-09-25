// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.google.common.base.Strings;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

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
  public static SelectionStrategy getSelectionStrategy() {
    SelectionStrategy strategy =
        Optional.ofNullable(tuningAndConfig().get(SELECTION_STRATEGY_KEY))
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
    return "true".equalsIgnoreCase(Optional.ofNullable(tuningAndConfig().get("dedicated")).orElse("false"));
  }

  public enum SelectionStrategy {
    List {
      @Override
      public boolean isSelected(@Nonnull String namespaceName) {
        return getConfiguredDomainNamespaces().contains(namespaceName);
      }

      @Override
      public Collection<String> getConfiguredDomainNamespaces() {
        return parseNamespaceList(getNamespaceList());
      }

      private String getNamespaceList() {
        return Optional.ofNullable(HelmAccess.getHelmSpecifiedNamespaceList()).orElse(getInternalNamespaceList());
      }

      private String getInternalNamespaceList() {
        return Optional.ofNullable(getConfiguredNamespaceList()).orElse(getOperatorNamespace());
      }

      private String getConfiguredNamespaceList() {
        return Optional.ofNullable(tuningAndConfig().get("domainNamespaces"))
              .orElse(tuningAndConfig().get("targetNamespaces"));
      }
    },
    LabelSelector {
      @Override
      public String getLabelSelector() {
        return tuningAndConfig().get("domainNamespaceLabelSelector");
      }

      @Override
      public boolean isSelected(@Nonnull String namespaceName) {
        return true;  // filtering is done by Kubernetes list call
      }
    },
    RegExp {
      @Override
      public boolean isSelected(@Nonnull String namespaceName) {
        try {
          return Pattern.compile(getRegExp()).matcher(namespaceName).find();
        } catch (PatternSyntaxException e) {
          LOGGER.severe(MessageKeys.EXCEPTION, e);
          return false;
        }
      }

      private String getRegExp() {
        return tuningAndConfig().get("domainNamespaceRegExp");
      }
    },
    Dedicated {
      @Override
      public boolean isSelected(@Nonnull String namespaceName) {
        return namespaceName.equals(getOperatorNamespace());
      }

      @Override
      public Collection<String> getConfiguredDomainNamespaces() {
        return Collections.singleton(getOperatorNamespace());
      }
    };

    public abstract boolean isSelected(@Nonnull String namespaceName);

    public String getLabelSelector() {
      return null;
    }

    public Collection<String> getConfiguredDomainNamespaces() {
      return null;
    }
  }
}
