// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

public enum DomainConditionType {
  Progressing {
    @Override
    DomainConditionType[] typesToRemove() {
      return new DomainConditionType[] {Progressing, Failed};
    }
  },
  Available,
  Failed {
    @Override
    String getStatusMessage(DomainCondition condition) {
      return condition.getMessage();
    }

    @Override
    String getStatusReason(DomainCondition condition) {
      return condition.getReason();
    }
  };

  DomainConditionType[] typesToRemove() {
    return new DomainConditionType[] {Progressing, Available, Failed};
  }

  String getStatusMessage(DomainCondition condition) {
    return null;
  }

  String getStatusReason(DomainCondition condition) {
    return null;
  }
}
