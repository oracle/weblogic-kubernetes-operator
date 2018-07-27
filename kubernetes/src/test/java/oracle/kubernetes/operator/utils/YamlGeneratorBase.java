// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

public abstract class YamlGeneratorBase {
  protected abstract GeneratedDomainYamlFiles getGeneratedDomainYamlFiles() throws Exception;

  protected void defineLoadBalancer(GeneratedDomainYamlFiles files) throws Exception {
    switch (getLoadBalancer()) {
      case DomainValues.LOAD_BALANCER_TRAEFIK:
        defineTraefikYaml(files);
        break;
      case DomainValues.LOAD_BALANCER_APACHE:
        defineApacheYaml(files);
        break;
      case DomainValues.LOAD_BALANCER_VOYAGER:
        defineYoyagerYaml(files);
        break;
    }
  }

  protected abstract String getLoadBalancer();

  protected abstract void defineTraefikYaml(GeneratedDomainYamlFiles files) throws Exception;

  protected abstract void defineApacheYaml(GeneratedDomainYamlFiles files) throws Exception;

  protected abstract void defineYoyagerYaml(GeneratedDomainYamlFiles files) throws Exception;
}
