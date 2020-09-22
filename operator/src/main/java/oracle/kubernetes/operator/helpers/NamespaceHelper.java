// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Strings;

import static oracle.kubernetes.operator.helpers.HelmAccess.getHelmVariable;

/**
 * Operations for dealing with namespaces.
 */
public class NamespaceHelper {
  public static final String DEFAULT_NAMESPACE = "default";

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

}
