// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.meterware.simplestub.Stub.createStrictStub;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import java.util.HashMap;
import java.util.Map;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.TuningParametersImpl;

public abstract class TuningParametersStub implements TuningParameters {
  static final int READINESS_INITIAL_DELAY = 1;
  static final int READINESS_TIMEOUT = 2;
  static final int READINESS_PERIOD = 3;
  static final int LIVENESS_INITIAL_DELAY = 4;
  static final int LIVENESS_PERIOD = 6;
  static final int LIVENESS_TIMEOUT = 5;
  static Map<String, String> namedParameters;

  public static Memento install() throws NoSuchFieldException {
    namedParameters = new HashMap<>();
    return StaticStubSupport.install(
        TuningParametersImpl.class, "INSTANCE", createStrictStub(TuningParametersStub.class));
  }

  @Override
  public PodTuning getPodTuning() {
    return new PodTuning(
        READINESS_INITIAL_DELAY,
        READINESS_TIMEOUT,
        READINESS_PERIOD,
        LIVENESS_INITIAL_DELAY,
        LIVENESS_TIMEOUT,
        LIVENESS_PERIOD);
  }

  @Override
  public MainTuning getMainTuning() {
    return new MainTuning(2, 2, 2, 2, 2, 2, 2L, 2L);
  }

  @Override
  public CallBuilderTuning getCallBuilderTuning() {
    return null;
  }

  @Override
  public String get(Object key) {
    return namedParameters.get(key);
  }
}
