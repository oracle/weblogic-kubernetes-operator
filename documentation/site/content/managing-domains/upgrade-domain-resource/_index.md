+++
title = "Upgrade Domain resource"
date = 2019-02-23T16:43:45-05:00
weight = 4
pre = "<b> </b>"
description = "Upgrade Domain resources."
+++

{{< table_of_contents >}}

### Operator 4.0 domain resource API version change
The Domain CustomResourceDefinition in operator version 4.0 has changed significantly from previous operator releases. For this reason, we have updated the API version of the Domain custom resource in the CRD from `weblogic.oracle/v8` to `weblogic.oracle/v9`. We continue to support the Domains with API version `weblogic.oracle/v8` and provide full backward compatibility. If you want to use the new fields introduced in the latest `weblogic.oracle/v9` schema, then you will need to update the API version in your Domain resource YAML file.

### Automated upgrade of `weblogic.oracle/v8` schema domain resource

{{% notice note %}}
The automated upgrade described in this section converts `weblogic.oracle/v8` schema auxiliary image configuration into low-level Kubernetes schema, for example, init containers and volumes.
Instead of relying on the generated low-level schema, Oracle recommends using a simplified `weblogic.oracle/v9` schema configuration for auxiliary images, as documented in the
Auxiliary Images [Configuration]({{<relref "/managing-domains/model-in-image/auxiliary-images#configuration" >}}) section.
{{% /notice %}}

The 4.0 operator provides a seamless upgrade of the Domain resources with the `weblogic.oracle/v8` version of the schema. When you create a Domain using a domain resource YAML file with `weblogic.oracle/v8` schema in a namespace managed by the 4.0 operator, the `WebLogic Domain resource conversion webhook` explained in the [Upgrade operator from version 3.x to 4.x]({{< relref "/managing-operators/conversion-webhook.md" >}}) document, performs an automated upgrade of the domain resource to the `weblogic.oracle/v9` schema. The conversion webhook runtime converts the `weblogic.oracle/v8` configuration to the equivalent configuration in operator 4.0. Similarly, when [upgrading the operator version]({{< relref "/managing-operators/installation#upgrade-the-operator" >}}), Domain resources with `weblogic.oracle/v8` schema are seamlessly upgraded.

### Upgrade the `weblogic.oracle/v8` schema domain resource manually

{{% notice note %}}
The manual upgrade tooling described in this section converts `weblogic.oracle/v8` schema auxiliary image configuration into low-level Kubernetes schema, for example, init containers and volumes.
Instead of relying on the generated low-level schema, Oracle recommends using a simplified `weblogic.oracle/v9` schema configuration for auxiliary images, as documented in the
Auxiliary Images [Configuration]({{<relref "/managing-domains/model-in-image/auxiliary-images#configuration" >}}) section.
{{% /notice %}}

Beginning with operator version 4.0, you can use a standalone command-line tool for manually upgrading the domain resource YAML file with `weblogic.oracle/v8` schema to the `weblogic.oracle/v9` schema. If you are required to keep the upgraded Domain resource YAML file in the source control repository, then you can use this tool to generate the upgraded Domain resource YAML file.

#### Setup
- Download the Domain upgrade tool JAR file to the desired location.
  - You can find the latest JAR file on the [project releases](https://github.com/oracle/weblogic-kubernetes-operator/releases) page.
  - Alternatively, you can download the JAR file with cURL.
   ```
   curl -m 120 -fL https://github.com/oracle/weblogic-kubernetes-operator/releases/latest/download/domain-upgrader.jar -o ./domain-upgrader.jar
   ```
 - OPTIONALLY: You may build the project (mvn clean package) to create the JAR file in `./weblogic-kubernetes-operator/target` (see Build From Source).
 - Set the `JAVA_HOME` environment variable to the location of the Java installation (Java version 11+).

The Domain upgrader tool upgrades the provided V8 schema domain resource input file and writes the upgraded domain resource YAML file to the
directory specified using the `-d` parameter.

```
Usage: java -jar domain-upgrader.jar  <input-file> [-d <output_dir>] [-f <output_file_name>] [-o --overwriteExistingFile] [-h --help]
```

| Parameter | Definition | Default |
| --- | --- | --- |
| `input-file` | (Required) Name of the operator 3.x/V8 domain resource YAML file to be converted. | |
| `-d`, `--outputDir` | The directory where the tool will place the converted file. | The directory of the input file. |
| `-f`, `--outputFile` | Name of the converted file. | Base name of the input file name followed by `"__converted."` followed by the input file extension. |
| `-h`, `--help` | Prints help message. | |
| `-o`, `--overwriteExistingFile` | Enable overwriting the existing output file, if any. | |

If the output file name is not specified using the `-f` parameter, then the tool generates the file name by appending `"__converted."` and the input file extension to the
base name of the input file name. For example, assuming the name of the V8 domain resource YAML file to be upgraded is `domain-v8.yaml` in the current directory:

```
$ java -jar /tmp/domain-upgrader.jar domain-v8.yaml -d /tmp -f domain-v9.yaml
{"timestamp":"2022-04-18T23:11:09.182227Z","thread":1,"level":"INFO","class":"oracle.kubernetes.operator.DomainUpgrader","method":"main","timeInMillis":1650323469182,"message":"Successfully generated upgraded domain custom resource file 'domain-v9.yaml'.","exception":"","code":"","headers":{},"body":""}
```

In the previous example, the tool writes the upgraded file to the `/tmp` directory with the name `domain-v9.yaml`.
```
$ ls -ltr /tmp/domain-v9.yaml
-rw-r----- 1 user dba 2818 Apr 18 23:11 /tmp/domain-v9.yaml
```

{{% notice note %}}
The manual upgrade tooling creates init containers with names prefixed with `compat-` when converting the auxiliary image configuration of the `weblogic.oracle/v8` schema. The operator generates only init containers with names starting with either `compat-` or `wls-shared-` in the introspector job pod. To alter the generated init container's name, the new name must start with either `compat-` or `wls-shared-`.
{{% /notice %}}
