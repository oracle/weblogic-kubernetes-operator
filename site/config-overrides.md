# Configuration Overrides

**PLEASE NOTE**:  This page is a work in progress. 

---
# Overview

Use configuration overrides to customize a WebLogic domain home configuration. For example, you may want to override a JDBC Datasource xml module Username, Password, and URL so that it references a local database. Overrides can be used to customize domains as they are moved from QA to production, deployed to different sites, or even deployed multiple times at the same site.

How do you specify overrides? Overrides are specified by:
* Defining situational config templates and deploying them via a Kubernetes config map.
* Including a file named 'version.txt' that contains the string '2.0' in the same config map.
* Setting your Domain resource `configOverride` to the name of this config map.
* Defining template macro values via Kubernetes secrets.
* Setting your Domain `configOverrideSecrets` to reference the aforementioned secrets.
* Starting or restarting your domain.

How do overrides work during runtime? 
* When a Domain is first deployed, or is restarted, the Operator will:
  * Resolve any macros in your override templates.
  * Place expanded override templates in the `optconfig` directory located in each WebLogic Domain Home directory.  
* When the WebLogic Servers start, they will:
  * Automatically load the override files from the `optconfig` directory.
  * Use the override values in the override files instead of the values specified in their config.xml or system resource xml.

---
# Prerequisites

* A Domain Home must not already contain situational config xml in its existing `optconfig` directory.  Any existing situational config xml in this directory will be deleted and replaced by your Operator override templates (if any).

* If you want to override a JDBC, JMS, or WLDF module it must be located in your Domain Home `config/jdbc`, `config/jms`, and `config/wldf` directory respectively.  These are the default locations for these types of modules.

---
# Typical Overrides

Typical attributes for overrides include:

* Usernames, Passwords, and URLs for
  * JDBC Datasources
  * JMS Bridges, Foreign Servers, and SAF
* Network Channel Public Addresses
  * For remote RMI clients (T3, JMS, EJB, JTA)
  * For remote WLST clients
* Debugging
* Tuning (MaxMessageSize, etc.)

---
# Unsupported Overrides

**IMPORTANT: The Operator does not support custom overrides in the following areas.**

* Domain topology (cluster members)
* Network Channel Listen Address, Port, and Enabled configuration 
* Server and Domain log locations 
* Node manager related configuration.  

**Specifically, do not use custom overrides for:**

* Adding/removing
  * Servers
  * Clusters
  * Network Access Points (Custom Channels)
* Changing any of the following
  * Dynamic Cluster Size 
  * Default, SSL, and Admin Channel 'Enabled', Listen Address, and Port
  * Network Access Point (Custom Channel) Listen Address or Port
  * Server and domain log locations -- use the logHome Domain setting instead
  * Node manager access credentials

In addition, it is not possible to use overrides to change the name of any bean.

Note that it's OK, even expected, to override Network Access Point 'public' or 'external' addresses and ports.

The behavior when using an unsupported override is undefined.

---
# Override Template Names and Syntax

Overrides leverage a built-in WebLogic feature called 'Situational Config'.  

Situational config consists of XML formated files that closely resemble the structure of WebLogic config.xml and system resource module xml files.  

In addition, the attribute fields in these files can embed 'add', 'replace', and 'delete' verbs to specify the desired override action for the field.  

Use 'add' to specify the values for mbean attributes that have not been set in the original configuration, and use the 'replace' verb to specify the values for mbean fields that are already set in the original configuration.

**IMPORTANT: Your overrides may not take effect if you use 'add' for an mbean attribute that already has a value specified in your original configuration, or use 'replace' for an attribute that does not already exist.**

## Override Template Names

The Operator requires a different file name format for override templates than WebLogic's built-in Situational Config feature.  It converts the names to the format required by situational config when it moves the templates to the domain home `optconfig` directory.  Consult the following table for the required file name format:

| Original Config |  Required Override Name |
| --------------- |  ---------------------  |
| `config.xml`    |  `config.xml`           |
| JMS module      |  `jms-MODULENAME.xml`   |
| JDBC module     |  `jdbc-MODULENAME.xml`  |
| WLDF module     |  `wldf-MODULENAME.xml`  |

A `MODULENAME` must correspond to the mbean name of a system resource defined in your original config.xml.

## Override Template Schemas

An override template must define the exact schemas required by the Situational Config feature.  The schemas vary based on the file type you wish to override.

_`config.xml`_
```
<?xml version='1.0' encoding='UTF-8'?>
<domain xmlns="http://xmlns.oracle.com/weblogic/domain"
        xmlns:f="http://xmlns.oracle.com/weblogic/domain-fragment" 
        xmlns:s="http://xmlns.oracle.com/weblogic/situational-config">
  ...
<domain>
```

_`jdbc-MODULENAME.xml`_
```
<?xml version='1.0' encoding='UTF-8'?>
<jdbc-data-source xmlns="http://xmlns.oracle.com/weblogic/jdbc-data-source" 
                  xmlns:f="http://xmlns.oracle.com/weblogic/jdbc-data-source-fragment" 
                  xmlns:s="http://xmlns.oracle.com/weblogic/situational-config">
  ...
<jdbc-data-source>
```

_`jms-MODULENAME.xml`_
```
<weblogic-jms xmlns="http://xmlns.oracle.com/weblogic/weblogic-jms"
              xmlns:f="http://xmlns.oracle.com/weblogic/weblogic-jms-fragment"
              xmlns:s="http://xmlns.oracle.com/weblogic/situational-config" >
  ...
<weblogic-jms>
```

_`wldf-MODULENAME.xml`_
```
<?xml version='1.0' encoding='UTF-8'?>
<wldf-resource
  xmlns:"http://xmlns.oracle.com/weblogic/weblogic-diagnostics"
  xmlns:f="http://xmlns.oracle.com/weblogic/weblogic-diagnostics-fragment"
  xmlns:s="http://xmlns.oracle.com/weblogic/situational-config" >
  ...
<wldf-resource>
```

## Override Template Macros

The Operator supports embedding macros within override templates.  This helps make your templates flexibly handle multiple use cases, such as specifying a different URL, username, and password for a different deployment.

Two types of macros are supported 'environment variable macros' and 'secret macros':

* Environment variable macros have the syntax `${env:ENV-VAR-NAME}`, where the supported env vars include `DOMAIN_HOME`, `LOG_HOME`, and `DOMAIN_UID`.

* Secret macros have the syntax `${secret:SECRETNAME.SECRETKEY}` and ${secret:SECRETNAME.SECRETKEY:encrypt}.

The secret macro SECRETNAME field is used to reference the name of a Secret, and the SECRETKEY field is used to reference a key within that secret.   For example, if you have defined a secret named `dbuser` with a key named `username` that contains the value `scott`, then the macro `${secret:dbuser.username}` will be replaced with the word `scott` before the template is copied into its WebLogic server pod.

**SECURITY NOTE:** Use the `:encrypt` suffix in a secret macro to encrypt its replacement value with the WebLogic WLST encrypt command instead of leaving it at its plain text value.  This is useful for overriding mbean attributes that expect encrypted values, such as the `password-encrypted` field of a data source, and for ensuring that a custom override situational config file the Operator places in the domain home does not expose plain-text passwords.

## Sample Override Templates

TBD

# Putting it together step by step

1. Create a directory to containing (A) a set of situational configuration templates for overriding the mbean properties you want to replace and (B) a version.txt file.
  * This directory must not contain any other files.
  * The version.txt file must contain only the string `2.0`.
  * Template files must not override the settings listed in [Unsupported Overrides].
  * Template files must be formatted and named as per [Override Template Names and Syntax] and [References].
    * _NOTE:_ The template file name format is different than the file name format required when creating situational config files without using the kubernetes operator.
    * JDBC, JMS, WLDF module template files must override an existing module
      * The file name must start with `jdbc`, `jms`, or `wldf` and must end with `-<module-name>.xml`.
      * <module-name> must match the name of a system resource mbean (not the name of its file).
      * E.g. to override data source named `myds`, use `jdbc-myds.xml`.
    * A `config.xml` override template must be named `config.xml`.
    * Templates can embed macros that reference environement variables or Kubernetes secrets.  See [Override Template Macros].
2. Create a kubernetes config map from the directory of templates.
  * The config map must be in the same kubernetes namespace as the domain.
  * It is recommended, but not required, to add the `weblogic.domainUID` label to the config map. This will help you keep track of the fact that the config map is associated with a particular domain.
  * Similarly, it is also recommended, but not required, to embed the domainUID within the name of the config map.
  * For example, assuming './mydir' contains your version.txt and situation config template files:
    `kubectl -n MYNAMESPACE create cm DOMAIN_UID-MYCMNAME --from-file ./mydir`
    `kubectl -n MYNAMESPACE label cm DOMAIN_UID-MYCMNAME weblogic.domainUID=DOMAIN_UID`
3. Create any kubernetes secrets referenced by a template macro.
  * Secrets can have multiple keys (files) that can hold either cleartext or base64 values
  * Secrets must be in the same kubernetes namespace as the domain
  * It is recommended that the customer add the 'weblogic.domainUID=<mydomainuid>' label to each secret.  This will help you keep track of the fact that the secret is associated with a particular domain.
  * Similarly, it is also recommended, but not required, to embed the domainUID within the name of the secret.
4. Configure the name of the config map in the Domain CR `configOverrides` field.
5. Configure the names of each secret in Domain CR.
  * If the secret contains the WebLogic admin `username` and `password` keys, set the Domain CR `webLogicCredentialsSecret` field.
  * For all other secrets, add them to Domain CR `configOverrideSecrets` field.
6. See [Debugging] for ways to check if sit cfg is taking effect or if there are errors.

# Debugging

TBD

* Search server pod log for the keyword `situational`.  The only hits should be lines that look like TBD to indicate a situational config file has been loaded.   If you see Warning or Error lines, then the format of the custom situational config template is incorrect.

* If you discover an error, the override config map needs to be updated, and the Domain needs to be restarted.

* _IMPORTANT_ WebLogic Servers will still boot, and skip overriding, when they detect incorrectly formatted config override template files.  It is therefore important to make sure they're correct in a QA environment before attempting to use them in production, where a custom override may be critical for correctness.

# Internal Design Flow

1. When a Domain is first deployed, or is restarted, the operator runtime creates an introspector Kubernetes job named `DOMAIN_UID-introspect-domain-job`
1. The introspector job's pod mounts the Kubernetes config map and secrets specified via the operator Domain resource `configOverride`, `webLogicCredentialsSecret`, and `configOverrideSecrets` fields.
1. The introspector job's pod reads the mounted situational config templates from the config map and expands them to create the actual situational config files for the domain:
  1. It expands some fixed replaceable values (e.g. ${env:DOMAIN_UID}).
  1. It expands referenced secrets by reading value from the corresponding mounted secret file (e.g. ${secret:mysecret.mykey}).
  1. It optionally encrypts secrets using offline WLST to encrypt the value - useful for passwords (e.g. ${secret:mysecret.mykey:encrypt}).
  1. It returns expanded situational config files to the operator.
  1. It reports any errors when attempting expansion to the operator.
1. The operator runtime reads the expanded situational config files and/or errors from the introspector, and, if the introspector reports no errors, puts situational config files in a config map named `DOMAIN_UID-weblogic-domain-introspect-cm`, and mounts this config map into the WebLogic Server pods.
1. The startServer.sh script in the WebLogic Server pods copies the expanded situational config files in a location that the WebLogic runtime can find them (specifically the `optconfig` directory in its domain home directory for config.xml overrides, and `optconfig/jdbc`, `optconfig/jms`, or `optconfig/wldf` for module override files).  The script also deletes any situational config files in this directory that do not have corresponding template files in the config map.
1. WebLogic Servers read their overrides from their domain home's 'optconfig' directory.

# Advanced Situational Config

WebLogic Situational Config feature provides advanced options and capabilities that aren't covered in this document.  See [References].

# References

TBD

# Release Notes

TBD These are to be moved to a release notes section?

