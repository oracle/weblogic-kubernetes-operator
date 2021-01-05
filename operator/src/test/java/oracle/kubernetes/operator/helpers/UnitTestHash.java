// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.function.Function;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;

public class UnitTestHash implements Function<Object, String> {
  public static Memento install() throws NoSuchFieldException {
    return StaticStubSupport.install(AnnotationHelper.class, "HASH_FUNCTION", new UnitTestHash());
  }

  @Override
  public String apply(Object object) {
    return Integer.toString(object.hashCode());
  }
}
