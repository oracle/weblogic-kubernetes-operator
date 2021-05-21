// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

interface CompatibilityCheck {
  enum CompatibilityScope {
    DOMAIN {
      @Override
      public boolean contains(CompatibilityScope scope) {
        switch (scope) {
          case DOMAIN:
          case MINIMUM:
            return true;
          case POD:
          case UNKNOWN:
          default:
            return false;
        }
      }
    },
    POD {
      @Override
      public boolean contains(CompatibilityScope scope) {
        switch (scope) {
          case DOMAIN:
          case POD:
          case UNKNOWN:
          case MINIMUM:
            return true;
          default:
            return false;

        }
      }
    },
    UNKNOWN {
      @Override
      public boolean contains(CompatibilityScope scope) {
        switch (scope) {
          case DOMAIN:
          case POD:
          default:
            return false;
          case UNKNOWN:
          case MINIMUM:
            return true;
        }
      }
    },
    MINIMUM {
      @Override
      public boolean contains(CompatibilityScope scope) {
        switch (scope) {
          case DOMAIN:
          case POD:
          case UNKNOWN:
          default:
            return false;
        }
      }
    };

    public abstract boolean contains(CompatibilityScope scope);
  }

  boolean isCompatible();

  String getIncompatibility();

  String getScopedIncompatibility(CompatibilityScope scope);

  CompatibilityScope getScope();

  default CompatibilityCheck ignoring(String... keys) {
    return this;
  }
}
