// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.TuningParametersImpl;
import org.jetbrains.annotations.NotNull;

import static com.meterware.simplestub.Stub.createStrictStub;

public abstract class TuningParametersStub implements TuningParameters {
  // Pod tuning
  static final int READINESS_INITIAL_DELAY = 1;
  static final int READINESS_TIMEOUT = 2;
  static final int READINESS_PERIOD = 3;
  static final int LIVENESS_INITIAL_DELAY = 4;
  static final int LIVENESS_PERIOD = 6;
  static final int LIVENESS_TIMEOUT = 5;
  static final long INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS = 180L;

  // Call builder tuning
  public static final int CALL_REQUEST_LIMIT = 10;
  public static final int CALL_MAX_RETRY_COUNT = 3;
  public static final int CALL_TIMEOUT_SECONDS = 5;
  static Map<String, String> namedParameters;

  /**
   * Install memento.
   * @return memento
   * @throws NoSuchFieldException on failure
   */
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
        LIVENESS_PERIOD,
        INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS);
  }

  @Override
  public MainTuning getMainTuning() {
    return new MainTuning(2, 2, 2, 2, 2, 2, 2L, 2L);
  }

  @Override
  public CallBuilderTuning getCallBuilderTuning() {
    return new CallBuilderTuning(CALL_REQUEST_LIMIT, CALL_MAX_RETRY_COUNT, CALL_TIMEOUT_SECONDS);
  }

  @Override
  public WatchTuning getWatchTuning() {
    return null;
  }

  @Override
  public String get(Object key) {
    return namedParameters.get(key);
  }

  @Override
  public String put(String key, String value) {
    return namedParameters.put(key, value);
  }

  @NotNull
  @Override
  public Set<Entry<String, String>> entrySet() {
    return namedParameters.entrySet();
  }
}
