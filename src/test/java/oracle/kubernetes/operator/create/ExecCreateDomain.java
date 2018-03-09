package oracle.kubernetes.operator.create;

import java.nio.file.Files;
import java.nio.file.Path;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

/**
 * Class for running create-weblogic-domain.sh
 */
public class ExecCreateDomain {

    public static final String CREATE_SCRIPT = "src/test/scripts/unit-test-create-weblogic-domain.sh";

    public static ExecResult execCreateDomain(Path userProjectsPath, CreateOperatorInputs inputs) throws Exception {
      Path p = userProjectsPath.resolve("inputs.yaml");
      newYaml().dump(inputs, Files.newBufferedWriter(p));
      return execCreateDomain(" -g -o " + userProjectsPath.toString() + " -i " + p.toString());
    }

    public static ExecResult execCreateDomain(String options) throws Exception {
      return ExecCommand.exec(CREATE_SCRIPT + options);
    }
}
