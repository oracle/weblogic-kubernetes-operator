// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

interface CompatibilityCheck {
  enum CompatibilityScope {
    DOMAIN {
      @Override
      public boolean contains(CompatibilityScope scope) {
        return switch (scope) {
          case DOMAIN, MINIMUM -> true;
          default -> false;
        };
      }
    },
    POD {
      @Override
      public boolean contains(CompatibilityScope scope) {
        return true;
      }
    },
    UNKNOWN {
      @Override
      public boolean contains(CompatibilityScope scope) {
        return switch (scope) {
          case UNKNOWN, MINIMUM -> true;
          default -> false;
        };
      }
    },
    MINIMUM {
      @Override
      public boolean contains(CompatibilityScope scope) {
        return false;
      }
    };

    public abstract boolean contains(CompatibilityScope scope);
  }

  String getIncompatibility();

  CompatibilityScope getScope();
}
