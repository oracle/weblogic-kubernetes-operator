// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Job;

/**
 * Parses a generated create-weblogic-domain-job.yaml file into a set of typed k8s java objects
 */
public class ParsedCreateWeblogicDomainJobYaml extends ParsedKubernetesYaml {

  private CreateDomainInputs inputs;

  public ParsedCreateWeblogicDomainJobYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    super(yamlPath);
    this.inputs = inputs;
  }

  public V1ConfigMap getCreateWeblogicDomainConfigMap() {
    return getConfigMaps().find("domain-" + inputs.getDomainUID() + "-scripts");
  }

  public V1Job getCreateWeblogicDomainJob() {
    return getJobs().find("domain-" + inputs.getDomainUID() + "-job");
  }

  public int getExpectedObjectCount() {
    return 2;
  }
}
