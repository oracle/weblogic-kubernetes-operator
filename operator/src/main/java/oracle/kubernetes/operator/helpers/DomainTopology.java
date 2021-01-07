// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Domain topology.
 */
public class DomainTopology {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private boolean domainValid;
  private WlsDomainConfig domain;
  private List<String> validationErrors;

  @SuppressWarnings("unused") // Used by parser
  public DomainTopology() {
  }

  public DomainTopology(WlsDomainConfig domain) {
    this.domain = domain;
    this.domainValid = true;
  }

  /**
   * Parses a topology yaml. If validation errors occur, logs them and returns null.
   * @param topologyYaml the YAML to parse
   * @param errorReporter processing for the validation errors
   * @return a valid topology or null
   */
  public static DomainTopology parseDomainTopologyYaml(String topologyYaml, Consumer<List<String>> errorReporter) {
    final DomainTopology domainTopology = parseDomainTopologyYaml(topologyYaml);
    if (domainTopology == null || domainTopology.getDomainValid()) {
      return domainTopology;
    } else {
      errorReporter.accept(domainTopology.validationErrors);
      return null;
    }
  }

  /**
   * parse domain topology yaml.
   * @param topologyYaml topology yaml.
   * @return parsed object hierarchy
   */
  public static DomainTopology parseDomainTopologyYaml(String topologyYaml) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    try {
      DomainTopology domainTopology = mapper.readValue(topologyYaml, DomainTopology.class);

      LOGGER.fine(
          ReflectionToStringBuilder.toString(domainTopology, ToStringStyle.MULTI_LINE_STYLE));

      return domainTopology;

    } catch (Exception e) {
      LOGGER.warning(MessageKeys.CANNOT_PARSE_TOPOLOGY, e);
    }

    return null;
  }

  /**
   * check if domain is valid.
   * @return true, if valid
   */
  public boolean getDomainValid() {
    return domainValid && getValidationErrors().isEmpty();
  }

  @SuppressWarnings("unused") // used by parser
  public void setDomainValid(boolean domainValid) {
    this.domainValid = domainValid;
  }

  @SuppressWarnings("unused") // used by parser
  public void setValidationErrors(List<String> validationErrors) {
    this.validationErrors = validationErrors;
  }


  public WlsDomainConfig getDomain() {
    this.domain.processDynamicClusters();
    return this.domain;
  }

  /**
   * Retrieve validation errors.
   * @return validation errors
   */
  public List<String> getValidationErrors() {
    if (validationErrors == null) {
      validationErrors = Collections.emptyList();
    }

    if (!domainValid && validationErrors.isEmpty()) {
      // add a log message that domain was marked invalid since we have no validation
      // errors from introspector.
      validationErrors = new ArrayList<>();
      validationErrors.add(
          "Error, domain is invalid although there are no validation errors from introspector job.");
    }

    return validationErrors;
  }

  /**
   * to string.
   * @return string
   */
  public String toString() {
    if (domainValid) {
      return "domain: " + domain;
    } else {
      return "domainValidationErrors: " + validationErrors;
    }
  }
}
