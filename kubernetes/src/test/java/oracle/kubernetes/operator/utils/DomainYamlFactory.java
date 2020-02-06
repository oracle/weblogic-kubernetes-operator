// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Map;

public abstract class DomainYamlFactory {
  public DomainValues newDomainValues() throws Exception {
    return createDefaultValues().withTestDefaults();
  }

  public abstract DomainValues createDefaultValues() throws Exception;

  public abstract GeneratedDomainYamlFiles generate(DomainValues values) throws Exception;

  public abstract String getWeblogicDomainPersistentVolumeClaimName(DomainValues inputs);

  public Map<String, String> getExpectedDomainJobAnnotations() {
    return null;
  }

  public Map<String, String> getExpectedConfigMapAnnotations() {
    return null;
  }
}
