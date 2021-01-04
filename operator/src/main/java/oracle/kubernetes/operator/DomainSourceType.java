// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

public enum DomainSourceType {
  Image {
    @Override
    public String getDefaultDomainHome(String uid) {
      return "/u01/oracle/user_projects/domains";
    }
  },
  PersistentVolume {
    @Override
    public boolean hasLogHomeByDefault() {
      return true;
    }

    @Override
    public String getDefaultDomainHome(String uid) {
      return "/shared/domains/" + uid;
    }
  },
  FromModel {
    @Override
    public String getDefaultDomainHome(String uid) {
      return "/u01/domains/" + uid;
    }

  };

  public boolean hasLogHomeByDefault() {
    return false;
  }

  public abstract String getDefaultDomainHome(String uid);

}
