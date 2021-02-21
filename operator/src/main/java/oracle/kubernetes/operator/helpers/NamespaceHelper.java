// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_NAMESPACE_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_POD_NAME_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_POD_UID_ENV;
import static oracle.kubernetes.operator.helpers.HelmAccess.getHelmVariable;
import static oracle.kubernetes.utils.OperatorUtils.isNullOrEmpty;

/**
 * Operations for dealing with namespaces.
 */
public class NamespaceHelper {
  public static final String DEFAULT_NAMESPACE = "default";

  public static String getOperatorNamespace() {
    return Optional.ofNullable(getHelmVariable(OPERATOR_NAMESPACE_ENV)).orElse(DEFAULT_NAMESPACE);
  }

  public static String getOperatorPodName() {
    return Optional.ofNullable(getHelmVariable(OPERATOR_POD_NAME_ENV)).orElse("");
  }

  public static String getOperatorPodUID() {
    return Optional.ofNullable(getHelmVariable(OPERATOR_POD_UID_ENV)).orElse("");
  }

  /**
   * Parse a string of namespace names and return them as a collection.
   * @param namespaceString a comma-separated list of namespace names
   * @return Namespace list
   */
  public static Collection<String> parseNamespaceList(String namespaceString) {
    Collection<String> namespaces
          = Stream.of(namespaceString.split(","))
          .filter(s -> !isNullOrEmpty(s))
          .map(String::trim)
          .collect(Collectors.toUnmodifiableList());

    return namespaces.isEmpty() ? Collections.singletonList(getOperatorNamespace()) : namespaces;
  }

}
