// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;

import java.util.Map;
import oracle.kubernetes.operator.utils.DomainValues;
import oracle.kubernetes.operator.utils.DomainYamlFactory;
import oracle.kubernetes.operator.utils.GeneratedDomainYamlFiles;
import oracle.kubernetes.operator.utils.ParsedApacheSecurityYaml;
import oracle.kubernetes.operator.utils.ParsedApacheYaml;
import oracle.kubernetes.operator.utils.ParsedCreateWeblogicDomainJobYaml;
import oracle.kubernetes.operator.utils.ParsedDeleteWeblogicDomainJobYaml;
import oracle.kubernetes.operator.utils.ParsedDomainCustomResourceYaml;
import oracle.kubernetes.operator.utils.ParsedTraefikSecurityYaml;
import oracle.kubernetes.operator.utils.ParsedTraefikYaml;
import oracle.kubernetes.operator.utils.ParsedVoyagerIngressYaml;
import oracle.kubernetes.operator.utils.ParsedVoyagerOperatorSecurityYaml;
import oracle.kubernetes.operator.utils.ParsedVoyagerOperatorYaml;
import oracle.kubernetes.operator.utils.ParsedWeblogicDomainPersistentVolumeClaimYaml;
import oracle.kubernetes.operator.utils.ParsedWeblogicDomainPersistentVolumeYaml;

public class HelmDomainYamlFactory extends DomainYamlFactory {
  @Override
  public HelmDomainValues createDefaultValues() {
    return new HelmDomainValues();
  }

  @Override
  public GeneratedDomainYamlFiles generate(DomainValues values) throws Exception {
    return new YamlGenerator(values).getGeneratedDomainYamlFiles();
  }

  static class YamlGenerator extends oracle.kubernetes.operator.utils.YamlGeneratorBase {
    private final DomainValues values;
    private final ProcessedChart chart;

    YamlGenerator(DomainValues inputValues) throws Exception {
      Map<String, Object> overrides = ((HelmDomainValues) inputValues).createMap();
      chart = new ProcessedChart("weblogic-domain", overrides);

      assertThat(chart.getError(), emptyOrNullString());

      values = new HelmDomainValues(chart.getValues());
    }

    @Override
    protected GeneratedDomainYamlFiles getGeneratedDomainYamlFiles() throws Exception {
      GeneratedDomainYamlFiles files =
          new GeneratedDomainYamlFiles(
              new ParsedCreateWeblogicDomainJobYaml(chart, values),
              new ParsedDeleteWeblogicDomainJobYaml(chart, values),
              new ParsedDomainCustomResourceYaml(chart, values));

      definePersistentVolumeYaml(files);
      defineLoadBalancer(files);
      return files;
    }

    private void definePersistentVolumeYaml(GeneratedDomainYamlFiles files) throws Exception {
      files.definePersistentVolumeYaml(
          new ParsedWeblogicDomainPersistentVolumeYaml(chart, values),
          new ParsedWeblogicDomainPersistentVolumeClaimYaml(chart, values));
    }

    @Override
    protected String getLoadBalancer() {
      return values.getLoadBalancer();
    }

    @Override
    protected void defineTraefikYaml(GeneratedDomainYamlFiles files) throws Exception {
      files.defineTraefikYaml(
          new ParsedTraefikYaml(chart, values), new ParsedTraefikSecurityYaml(chart, values));
    }

    @Override
    protected void defineApacheYaml(GeneratedDomainYamlFiles files) throws Exception {
      files.defineApacheYaml(
          new ParsedApacheYaml(chart, values), new ParsedApacheSecurityYaml(chart, values));
    }

    @Override
    protected void defineYoyagerYaml(GeneratedDomainYamlFiles files) throws Exception {
      files.defineYoyagerYaml(
          new ParsedVoyagerOperatorYaml(chart, values),
          new ParsedVoyagerOperatorSecurityYaml(chart),
          new ParsedVoyagerIngressYaml(chart, values));
    }
  }
}
