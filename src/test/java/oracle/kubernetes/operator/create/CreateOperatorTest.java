// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.io.*;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.apache.commons.codec.binary.Base64;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

/**
 * Base test class for testing create-weblogic-operator.sh
 */
public class CreateOperatorTest extends CreateTest {

  protected static final String CREATE_SCRIPT = "src/test/scripts/unit-test-create-weblogic-operator.sh";
  protected static final String DEFAULT_INPUTS = "kubernetes/create-weblogic-operator-inputs.yaml";
  protected static final String WEBLOGIC_OPERATOR_YAML = "weblogic-operator.yaml";
  protected static final String WEBLOGIC_OPERATOR_SECURITY_YAML = "weblogic-operator-security.yaml";

  protected CreateOperatorInputs newInputs() throws Exception {
    return
      readDefaultInputsFile()
        .namespace("test-operator-namespace")
        .serviceAccount("test-operator-service-account")
        .targetNamespaces("test-target-namespace1,test-target-namespace2")
        .image("test-operator-image")
        .imagePullPolicy("Never")
        .javaLoggingLevel("FINEST");
  }

  protected CreateOperatorInputs enableDebugging(CreateOperatorInputs inputs) {
    return
      inputs
        .remoteDebugNodePortEnabled("true")
        .internalDebugHttpPort("9090")
        .externalDebugHttpPort("30090");
  }

  protected CreateOperatorInputs setupExternalRestSelfSignedCert(CreateOperatorInputs inputs) {
    return
      inputs
        .externalRestHttpsPort("30070")
        .externalRestOption("self-signed-cert")
        .externalSans("DNS:localhost");
  }

  protected CreateOperatorInputs setupExternalRestCustomCert(CreateOperatorInputs inputs) {
    return
      inputs
        .externalRestHttpsPort("30070")
        .externalRestOption("custom-cert")
        .externalOperatorCert("test-custom-certificate-pem")
        .externalOperatorKey(
          Base64.encodeBase64String("test-custom-private-key-pem".getBytes())
        );
  }

  protected CreateOperatorInputs readDefaultInputsFile() throws IOException {
    Reader r = Files.newBufferedReader(defaultInputsPath(), Charset.forName("UTF-8"));
    return (CreateOperatorInputs)newYaml().loadAs(r, CreateOperatorInputs.class);
  }

  protected Path defaultInputsPath() {
    return FileSystems.getDefault().getPath(DEFAULT_INPUTS);
  }

  protected Path weblogicOperatorYamlPath(CreateOperatorInputs inputs) {
    return weblogicOperatorPath(inputs).resolve(WEBLOGIC_OPERATOR_YAML);
  }

  protected Path weblogicOperatorSecurityYamlPath(CreateOperatorInputs inputs) {
    return weblogicOperatorPath(inputs).resolve(WEBLOGIC_OPERATOR_SECURITY_YAML);
  }

  protected Path weblogicOperatorPath(CreateOperatorInputs inputs) {
    return scratch().userProjects().resolve("weblogic-operators").resolve(inputs.getNamespace());
  }

  protected ExecResult execCreateOperator(CreateOperatorInputs inputs) throws Exception {
    Path p = scratch().path().resolve("inputs.yaml");
    newYaml().dump(inputs, Files.newBufferedWriter(p));
    return execCreateOperator(" -g -o " + scratch().userProjects().toString() + " -i " + p.toString());
  }

  protected ExecResult execCreateOperator(String options) throws Exception {
    return exec(CREATE_SCRIPT + options);
  }
}
