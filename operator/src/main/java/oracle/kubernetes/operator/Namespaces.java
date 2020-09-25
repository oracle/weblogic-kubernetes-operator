// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import javax.annotation.Nonnull;

import oracle.kubernetes.operator.helpers.NamespaceHelper;
import oracle.kubernetes.operator.work.Packet;

public class Namespaces {
  /** The key in a Packet of the collection of existing namespaces that are designated as domain namespaces. */
  static final String ALL_DOMAIN_NAMESPACES = "ALL_DOMAIN_NAMESPACES";

  NamespaceHelper.SelectionStrategy selectionStrategy = NamespaceHelper.getSelectionStrategy();
  private Collection<String> configuredDomainNamespaces = selectionStrategy.getConfiguredDomainNamespaces();
  boolean isFullRecheck;

  public Namespaces(boolean isFullRecheck) {
    this.isFullRecheck = isFullRecheck;
  }

  static @Nonnull Collection<String> getAllDomainNamespaces(Packet packet) {
    return Optional.ofNullable(getFoundDomainNamespaces(packet)).orElse(Collections.emptyList());
  }

  @SuppressWarnings("unchecked")
  private static Collection<String> getFoundDomainNamespaces(Packet packet) {
    return (Collection<String>) packet.get(ALL_DOMAIN_NAMESPACES);
  }

  @Nonnull Collection<String> getConfiguredDomainNamespaces() {
    return Optional.ofNullable(configuredDomainNamespaces).orElse(Collections.emptyList());
  }
}
