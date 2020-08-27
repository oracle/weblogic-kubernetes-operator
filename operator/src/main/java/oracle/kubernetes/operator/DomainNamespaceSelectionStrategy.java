// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import oracle.kubernetes.operator.helpers.NamespaceHelper;

import static oracle.kubernetes.operator.helpers.HelmAccess.getHelmVariable;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;

enum DomainNamespaceSelectionStrategy {
  List {
    @Override
    public Collection<String> getDomainNamespaces() {
      return NamespaceHelper.parseNamespaceList(getNamespaceList());
    }

    public String getNamespaceList() {
      return Optional.ofNullable(getHelmSpecifiedNamespaceList()).orElse(getConfiguredNamespaceList());
    }

    public String getHelmSpecifiedNamespaceList() {
      return getHelmVariable("OPERATOR_DOMAIN_NAMESPACES");
    }

    public String getConfiguredNamespaceList() {
      return Optional.ofNullable(tuningAndConfig().get("domainNamespaces"))
            .orElse(tuningAndConfig().get("targetNamespaces"));
    }
  },

  LabelSelector {
    @Override
    public boolean isRequireList() {
      return true;
    }

    @Override
    public String getLabelSelector() {
      return tuningAndConfig().get("domainNamespaceLabelSelector");
    }
  },

  RegExp {
    @Override
    public boolean isRequireList() {
      return true;
    }

    @Override
    public String getRegExp() {
      return tuningAndConfig().get("domainNamespaceRegExp");
    }
  },

  Dedicated {
    @Override
    public Collection<String> getDomainNamespaces() {
      return Collections.singleton(getOperatorNamespace());
    }
  };

  public boolean isRequireList() {
    return false;
  }

  public String getLabelSelector() {
    return null;
  }

  public String getRegExp() {
    return null;
  }

  public Collection<String> getDomainNamespaces() {
    return null;
  }

  public TuningParameters tuningAndConfig() {
    return TuningParameters.getInstance();
  }
}
