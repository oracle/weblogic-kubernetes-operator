// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.models.V1Pod;
import java.util.function.Function;

public class UnitTestHash implements Function<V1Pod, String> {
  public static Memento install() throws NoSuchFieldException {
    return StaticStubSupport.install(AnnotationHelper.class, "HASH_FUNCTION", new UnitTestHash());
  }

  @Override
  public String apply(V1Pod v1Pod) {
    return Integer.toString(v1Pod.hashCode());
  }
}
