// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

public class SlammerParams {
  // Adding some of the most commonly used params for now
  private String service;
  private String operation;
  private String timeout;
  private String delay;
  private String cpu;
  private String cpuPercent;
  private String vm;
  private String vmSize;
  private String traffic;
  private String port;
  private String fill;
  private String chain;
  private String remotehost;
  private String remotesudopass;
  private String propertyFile;
  private String ociimage;

  public SlammerParams service(String service) {
    this.service = service;
    return this;
  }

  public SlammerParams operation(String operation) {
    this.operation = operation;
    return this;
  }

  public SlammerParams timeout(String timeout) {
    this.timeout = timeout;
    return this;
  }

  public SlammerParams delay(String delay) {
    this.delay = delay;
    return this;
  }

  public SlammerParams cpu(String cpu) {
    this.cpu = cpu;
    return this;
  }

  public SlammerParams cpuPercent(String cpuPercent) {
    this.cpuPercent = cpuPercent;
    return this;
  }

  public SlammerParams traffic(String traffic) {
    this.traffic = traffic;
    return this;
  }

  public SlammerParams port(String port) {
    this.port = port;
    return this;
  }

  public SlammerParams fill(String fill) {
    this.fill = fill;
    return this;
  }

  public SlammerParams vm(String vm) {
    this.vm = vm;
    return this;
  }

  public SlammerParams vmSize(String vmSize) {
    this.vmSize = vmSize;
    return this;
  }

  public SlammerParams remotehost(String remotehost) {
    this.remotehost = remotehost;
    return this;
  }

  public SlammerParams remoteSudoPass(String remotesudopass) {
    this.remotesudopass = remotesudopass;
    return this;
  }

  public SlammerParams chain(String chain) {
    this.chain = chain;
    return this;
  }

  public SlammerParams propertyFile(String propertyFile) {
    this.propertyFile = propertyFile;
    return this;
  }

  public SlammerParams ociimage(String ociimage) {
    this.ociimage = ociimage;
    return this;
  }

  public SlammerParams defaults() {
    return this;
  }

  public String getVm() {
    return vm;
  }

  public String getVmSize() {
    return vmSize;
  }

  public String getCpu() {
    return cpu;
  }

  public String getCpuPercent() {
    return cpuPercent;
  }

  public String getPort() {
    return port;
  }

  public String getFill() {
    return fill;
  }

  public String getTraffic() {
    return traffic;
  }

  public String getDelay() {
    return delay;
  }

  public String getTimeout() {
    return timeout;
  }

  public String getOperation() {
    return operation;
  }

  public String getService() {
    return service;
  }

  public String getChain() {
    return chain;
  }

  public String getRemoteHost() {
    return remotehost;
  }

  public String getRemoteSudoPass() {
    return remotesudopass;
  }

  public String getPropertyFile() {
    return propertyFile;
  }

  public String getOciImage() {
    return ociimage;
  }
}
