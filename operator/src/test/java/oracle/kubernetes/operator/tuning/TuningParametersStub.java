// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.tuning;

import java.util.HashMap;
import java.util.Map;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;

public abstract class TuningParametersStub {
  static Map<String, String> namedParameters = new HashMap<>();

  /**
   * Install memento.
   * @return memento
   * @throws NoSuchFieldException on failure
   */
  public static Memento install() throws NoSuchFieldException {
    namedParameters.clear();
    return StaticStubSupport.install(
        TuningParameters.class, "instance", new TuningParameters(namedParameters));
  }

  /**
   * Sets a tuning parameter for testing purposes.
   * @param key the name of the parameter to set
   * @param value its test value as a string
   */
  public static void setParameter(String key, String value) {
    namedParameters.put(key, value);
  }

}
