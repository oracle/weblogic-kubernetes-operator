// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.operator.TuningParameters;

public class CallBuilderFactory {
  private final TuningParameters tuning;

  public CallBuilderFactory() {
    this.tuning = TuningParameters.getInstance();
  }

  public CallBuilder create() {
    return CallBuilder.create(tuning != null ? tuning.getCallBuilderTuning() : null);
  }
}
