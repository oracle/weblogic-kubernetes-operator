// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

/**
 * Generates the domain yaml files for a set of valid domain input params. Creates and managed the
 * user projects directory that the files are stored in. Parses the generated yaml files into typed
 * java objects.
 */
public class GeneratedDomainYamlFiles {

  private ParsedCreateWeblogicDomainJobYaml createWeblogicDomainJobYaml;
  private ParsedDeleteWeblogicDomainJobYaml deleteWeblogicDomainJobYaml;
  private ParsedDomainCustomResourceYaml domainCustomResourceYaml;
  private ParsedTraefikYaml traefikYaml;
  private ParsedTraefikSecurityYaml traefikSecurityYaml;
  private ParsedApacheYaml apacheYaml;
  private ParsedApacheSecurityYaml apacheSecurityYaml;
  private ParsedVoyagerOperatorYaml voyagerOperatorYaml;
  private ParsedVoyagerOperatorSecurityYaml voyagerOperatorSecurityYaml;
  private ParsedVoyagerIngressYaml voyagerIngressYaml;
  private ParsedWeblogicDomainPersistentVolumeYaml weblogicDomainPersistentVolumeYaml;
  private ParsedWeblogicDomainPersistentVolumeClaimYaml weblogicDomainPersistentVolumeClaimYaml;

  public GeneratedDomainYamlFiles(
      ParsedCreateWeblogicDomainJobYaml createDomainJobYaml,
      ParsedDeleteWeblogicDomainJobYaml deleteDomainJobYaml,
      ParsedDomainCustomResourceYaml domainYaml) {
    this.createWeblogicDomainJobYaml = createDomainJobYaml;
    this.deleteWeblogicDomainJobYaml = deleteDomainJobYaml;
    this.domainCustomResourceYaml = domainYaml;
  }

  public void definePersistentVolumeYaml(
      ParsedWeblogicDomainPersistentVolumeYaml persistentVolumeYaml,
      ParsedWeblogicDomainPersistentVolumeClaimYaml persistentVolumeClaimYaml) {
    this.weblogicDomainPersistentVolumeYaml = persistentVolumeYaml;
    this.weblogicDomainPersistentVolumeClaimYaml = persistentVolumeClaimYaml;
  }

  public void defineYoyagerYaml(
      ParsedVoyagerOperatorYaml voyagerOperatorYaml,
      ParsedVoyagerOperatorSecurityYaml voyagerOperatorSecurityYaml,
      ParsedVoyagerIngressYaml voyagerIngressYaml) {
    this.voyagerOperatorYaml = voyagerOperatorYaml;
    this.voyagerOperatorSecurityYaml = voyagerOperatorSecurityYaml;
    this.voyagerIngressYaml = voyagerIngressYaml;
  }

  public void defineApacheYaml(
      ParsedApacheYaml apacheYaml, ParsedApacheSecurityYaml apacheSecurityYaml) {
    this.apacheYaml = apacheYaml;
    this.apacheSecurityYaml = apacheSecurityYaml;
  }

  public void defineTraefikYaml(
      ParsedTraefikYaml traefikYaml, ParsedTraefikSecurityYaml traefikSecurityYaml) {
    this.traefikYaml = traefikYaml;
    this.traefikSecurityYaml = traefikSecurityYaml;
  }

  public ParsedCreateWeblogicDomainJobYaml getCreateWeblogicDomainJobYaml() {
    return createWeblogicDomainJobYaml;
  }

  public ParsedDeleteWeblogicDomainJobYaml getDeleteWeblogicDomainJobYaml() {
    return deleteWeblogicDomainJobYaml;
  }

  public ParsedDomainCustomResourceYaml getDomainCustomResourceYaml() {
    return domainCustomResourceYaml;
  }

  public ParsedTraefikYaml getTraefikYaml() {
    return traefikYaml;
  }

  public ParsedTraefikSecurityYaml getTraefikSecurityYaml() {
    return traefikSecurityYaml;
  }

  public ParsedApacheYaml getApacheYaml() {
    return apacheYaml;
  }

  public ParsedApacheSecurityYaml getApacheSecurityYaml() {
    return apacheSecurityYaml;
  }

  public ParsedVoyagerOperatorYaml getVoyagerOperatorYaml() {
    return voyagerOperatorYaml;
  }

  public ParsedVoyagerOperatorSecurityYaml getVoyagerOperatorSecurityYaml() {
    return voyagerOperatorSecurityYaml;
  }

  public ParsedVoyagerIngressYaml getVoyagerIngressYaml() {
    return voyagerIngressYaml;
  }

  public ParsedWeblogicDomainPersistentVolumeYaml getWeblogicDomainPersistentVolumeYaml() {
    return weblogicDomainPersistentVolumeYaml;
  }

  public ParsedWeblogicDomainPersistentVolumeClaimYaml
      getWeblogicDomainPersistentVolumeClaimYaml() {
    return weblogicDomainPersistentVolumeClaimYaml;
  }
}
