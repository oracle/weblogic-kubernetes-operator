// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

/**
 * All parameters needed to install ELK Stack and WebLogic Logging Exporter.
 */
public class LoggingExporterParams {

  // Adding some of the most commonly used params for now
  private String elasticsearchName;
  private String elasticsearchImage;
  private String loggingExporterNamespace;
  private int elasticsearchHttpPort;
  private int elasticsearchHttpsPort;
  private String kibanaName;
  private String kibanaImage;
  private String kibanaType;
  private int kibanaContainerPort;
  private LoggingExporterParams loggingExporterParams;

  public LoggingExporterParams elasticsearchName(String elasticsearchName) {
    this.elasticsearchName = elasticsearchName;
    return this;
  }

  public LoggingExporterParams elasticsearchImage(String elasticsearchImage) {
    this.elasticsearchImage = elasticsearchImage;
    return this;
  }

  public LoggingExporterParams loggingExporterNamespace(String loggingExporterNamespace) {
    this.loggingExporterNamespace = loggingExporterNamespace;
    return this;
  }

  public LoggingExporterParams elasticsearchHttpPort(int elasticsearchHttpPort) {
    this.elasticsearchHttpPort = elasticsearchHttpPort;
    return this;
  }

  public LoggingExporterParams elasticsearchHttpsPort(int elasticsearchHttpsPort) {
    this.elasticsearchHttpsPort = elasticsearchHttpsPort;
    return this;
  }

  public LoggingExporterParams kibanaName(String kibanaName) {
    this.kibanaName = kibanaName;
    return this;
  }

  public LoggingExporterParams kibanaImage(String kibanaImage) {
    this.kibanaImage = kibanaImage;
    return this;
  }

  public LoggingExporterParams kibanaType(String kibanaType) {
    this.kibanaType = kibanaType;
    return this;
  }

  public LoggingExporterParams kibanaContainerPort(int kibanaContainerPort) {
    this.kibanaContainerPort = kibanaContainerPort;
    return this;
  }

  public void elkStackParams(LoggingExporterParams loggingExporterParams) {
    this.loggingExporterParams = loggingExporterParams;
  }

  public LoggingExporterParams getLoggingExporterParamsParams() {
    return this;
  }

  public String getElasticsearchName() {
    return elasticsearchName;
  }

  public String getElasticsearchImage() {
    return elasticsearchImage;
  }

  public String getLoggingExporterNamespace() {
    return loggingExporterNamespace;
  }

  public int getElasticsearchHttpPort() {
    return elasticsearchHttpPort;
  }

  public int getElasticsearchHttpsPort() {
    return elasticsearchHttpsPort;
  }

  public String getKibanaName() {
    return kibanaName;
  }

  public String getKibanaImage() {
    return kibanaImage;
  }

  public String getKibanaType() {
    return kibanaType;
  }

  public int getKibanaContainerPort() {
    return kibanaContainerPort;
  }
}
