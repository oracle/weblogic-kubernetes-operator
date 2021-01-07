// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;

/**
 * Provides a way for unit tests to simulate Helm values.
 */
public class HelmAccessStub {
  private static final Map<String,String> variables = new HashMap<>();
  private static final Function<String,String> getVariable = variables::get;

  public static Memento install() throws NoSuchFieldException {
    variables.clear();
    return StaticStubSupport.install(HelmAccess.class, "getHelmVariableFunction", getVariable);
  }

  public static void defineVariable(String name, String value) {
    variables.put(name, value);
  }

}
