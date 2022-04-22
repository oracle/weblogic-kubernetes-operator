+++
title = "Upgrade Domain resource"
date = 2019-02-23T16:43:45-05:00
weight = 2.5
pre = "<b> </b>"
+++

### Contents

 - [Introduction](#introduction)
 - [Automated upgrade of WKO 3.x domain resource](#automated-upgrade-of-weblogicoraclev8-schema-domain-resource)
 - [Upgrade the WKO 3.x domain resource manually](#upgrade-the-weblogicoraclev8-schema-domain-resource-manually)

### Introduction
The Domain CustomResourceDefinition in Operator version 4.0 has changed significantly from previous Operator releases. For this reason, we have updated the API version of the Domain custom resource in CRD to `weblogic.oracle/v9` from `weblogic.oracle/v8`. We continue to support the Domains with API version `weblogic.oracle/v8` and provide full backward compatibility. If you want to use new fields introduced in the latest `weblogic.oracle/v9` schema, then you will need to update the API version in your Domain resource YAML. 

### Automated upgrade of `weblogic.oracle/v8` schema domain resource
The 4.0 Operator provides a seamless upgrade of the Domain resources with `weblogic.oracle/v8` version of the schema. When you create a Domain using a domain resource YAML with `weblogic.oracle/v8` schema in a namespace managed by the WKO 4.0, the [WebLogic Domain resource conversion webhook]({{< relref "/userguide/managing-operators/conversion-webhook.md" >}}) performs an automated upgrade of the domain resource to the `weblogic.oracle/v9` schema. The conversion webhook runtime converts the `weblogic.oracle/v8` configuration to the equivalent configuration in WKO 4.0. Similarly, when [upgrading the Operator version from 3.x to 4.0]({{< relref "/userguide/managing-operators/installation#upgrade-the-operator" >}}), Domains resources are seamlessly upgraded.

### Upgrade the `weblogic.oracle/v8` schema domain resource manually
Beginning with Operator version 4.0, the Operator team provides a standalone command-line tool for manually upgrading the domain resource YAML file with `weblogic.oracle/v8` schema to the `weblogic.oracle/v9` schema. If you are required to keep the upgraded Domain resource YAML file in the source control repository, then you can use this tool to generate the upgraded Domain resource YAML file. 

##### Setup
- Download the Domain upgrade tool jar file to the desired location.
 - You can find the latest jar file on the project releases page.
 - Alternatively, you can download the jar file with cURL.
   ```
   curl -m 120 -fL https://github.com/oracle/weblogic-kubernetes-operator/releases/latest/download/domain-upgrader.jar -o ./domain-upgrader.jar
   ```
 - OPTIONALLY: You may build the project (mvn clean package) to create the jar file in ./weblogic-kubernetes-operator/target (see Build From Source).
 - Set the JAVA_HOME environment variable to the location of the Java install (Java version 11+).

The Domain upgrader tool upgrades the provided V8 schema domain resource input file and writes the upgraded domain resource YAML file to the 
directory specified using the `-d` parameter.

```
Usage: java -jar domain-upgrader.jar  <input-file> [-d <output_dir>] [-f <output_file_name>] [-o --overwriteExistingFile] [-h --help]
```

| Parameter | Definition | Default |
| --- | --- | --- |
| input-file | (Required) Name of the 3.x/V8 domain resource yaml to be converted. | |
| -d, --outputDir | The directory where the tool will place the converted file. | The directory of the input file. |
| -f, --outputFile | Name of the converted file. | Base name of the input file name followed by "__converted." followed by input file extension. |
| -h, --help | Prints help message. | |
| -o, --overwriteExistingFile | Enable overwriting the existing output file, if any. | |

If the output file name is not specified using `-f` parameter, then the tool generates the file name by appending "__converted." and the input file extension to the
base name of the input file name. For example, assuming the name of the V8 domain resource yaml file to be upgraded is `domain-v8.yaml` in the current directory:

```
$ java -jar /tmp/domain-upgrader.jar domain-v8.yaml -d /tmp -f domain-v9.yaml
{"timestamp":"2022-04-18T23:11:09.182227Z","thread":1,"level":"INFO","class":"oracle.kubernetes.operator.DomainUpgrader","method":"main","timeInMillis":1650323469182,"message":"Successfully generated upgraded domain custom resource file 'domain-v9.yaml'.","exception":"","code":"","headers":{},"body":""}
```

In the above example, the tool writes the upgraded file to the `/tmp` directory with the name `domain-v9.yaml`.
```
$ ls -ltr /tmp/domain-v9.yaml
-rw-r----- 1 user dba 2818 Apr 18 23:11 /tmp/domain-v9.yaml
```
