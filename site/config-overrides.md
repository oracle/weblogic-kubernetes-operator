# Configuration Overrides

**PLEASE NOTE**:  This page is a work in progress. 

---
# Overview

Use configuration overrides to customize a WebLogic domain home configuration. For example, you may want to override a JDBC Datasource xml module Username, Password, and URL so that it references a local database. Overrides can be used to customize domains as they are moved from QA to production, are deployed to different sites, or are even deployed multiple times at the same site.

How do you specify overrides? Overrides are specified by:
* Creating a Kubernetes config map that contains
  * Situational config templates.
  * A file named 'version.txt' that contains the string '2.0'.
* Setting your Domain resource `configOverride` to the name of this config map.
* Creating Kubernetes secrets that contain template macro values.
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

Overrides leverage a built-in WebLogic feature called 'Situational Config'. Situational config consists of XML formated files that closely resemble the structure of WebLogic config.xml and system resource module xml files. In addition, the attribute fields in these files can embed 'add', 'replace', and 'delete' verbs to specify the desired override action for the field.  

## Override Template Names

The Operator requires a different file name format for override templates than WebLogic's built-in Situational Config feature.  It converts the names to the format required by situational config when it moves the templates to the domain home `optconfig` directory.  The following table describes the format:

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

## Override Template Samples

Here are some sample template override files.  Use 'combine-mode="add"' to specify the values for mbean attributes that have not been set in the original configuration, and use the 'combine-mode="replace"' to specify the values for mbean fields that are already set in the original configuration.

**IMPORTANT: Your overrides may not take effect if you use 'add' for an mbean attribute that already has a value specified in your original configuration, or use 'replace' for an attribute that does not already exist.**

### Overriding config.xml

The following `config.xml` override file demonstrates setting the 'max-message-size' field on a WebLogic Server named 'admin-server', and replacing the `public-address` and `public-port` fields with values obtained from a secret named `test-host` with keys `hostname` and `port`.

```
<?xml version='1.0' encoding='UTF-8'?>
<domain xmlns="http://xmlns.oracle.com/weblogic/domain" 
        xmlns:f="http://xmlns.oracle.com/weblogic/domain-fragment" 
        xmlns:s="http://xmlns.oracle.com/weblogic/situational-config">
  <server>
    <name>admin-server</name>
    <max-message-size f:combine-mode="add">78787878</max-message-size>
    <network-access-point>
      <name>T3Channel</name>
      <public-address f:combine-mode="replace">${secret:test-host.hostname}</public-address>
      <public-port f:combine-mode="replace">${secret:test-host.port}</public-port>
    </network-access-point>
  </server>
</domain>
```

### Overriding a DataSource Module

The following `jdbc-testDS.xml` override file demonstrates setting the URL of a JDBC driver via secret.  It overrides a datasource module named "testDS".

TBD expand this sample to include username and password.

```
<?xml version='1.0' encoding='UTF-8'?>
<jdbc-data-source xmlns="http://xmlns.oracle.com/weblogic/jdbc-data-source" 
                  xmlns:f="http://xmlns.oracle.com/weblogic/jdbc-data-source-fragment" 
                  xmlns:s="http://xmlns.oracle.com/weblogic/situational-config">
  <name>testDS</name>
    <jdbc-driver-params>
      <url f:combine-mode="replace">${secret:dbsecret.url}</url>
    </jdbc-driver-params>
</jdbc-data-source>
```


# Step-by-Step Guide

* Create a directory containing (A) a set of situational configuration templates for overriding the mbean properties you want to replace and (B) a version.txt file.
  * This directory must not contain any other files.
  * The version.txt file must contain only the string `2.0`.
  * Template files must not override the settings listed in [Unsupported Overrides](#unsupported-overrides).
  * Template files must be formatted and named as per [Override Template Names and Syntax](#override-template-names-and-syntax) and [References](#references).
    * JDBC, JMS, WLDF module template files must override an existing module
      * The file name must start with `jdbc`, `jms`, or `wldf` and must end with `-<module-name>.xml`.
      * <module-name> must match the name of a system resource mbean (not the name of its file).
      * E.g. to override data source named `myds`, use `jdbc-myds.xml`.
    * A `config.xml` override template must be named `config.xml`.
    * Templates can embed macros that reference environement variables or Kubernetes secrets.  See [Override Template Macros](#override-template-macros).
* Create a kubernetes config map from the directory of templates.
  * The config map must be in the same kubernetes namespace as the domain.
  * If the config map is going to be used by a single DOMAIN_UID, it is recommended to add the 'weblogic.domainUID=<mydomainuid>' label to help track the resource.
  * For example, assuming './mydir' contains your version.txt and situation config template files:
    ```
    kubectl -n MYNAMESPACE create cm MYCMNAME --from-file ./mydir
    kubectl -n MYNAMESPACE label cm MYCMNAME weblogic.domainUID=DOMAIN_UID
    ```
* Create any kubernetes secrets referenced by a template macro.
  * Secrets can have multiple keys (files) that can hold either cleartext or base64 values
  * Secrets must be in the same kubernetes namespace as the domain
  * If a secret is going to be used by a single DOMAIN_UID, it is recommended to add the 'weblogic.domainUID=<mydomainuid>' label to help track the resource.
  * For example:
    ```
    kubectl -n MYNAMESPACE create secret generic my-secret --from-literal=key1=supersecret --from-literal=key2=topsecret
    kubectl -n MYNAMESPACE label secret my-secret weblogic.domainUID=DOMAIN_UID
    ```
* Configure the name of the config map in the Domain CR `configOverrides` field.
* Configure the names of each secret in Domain CR.
  * If the secret contains the WebLogic admin `username` and `password` keys, set the Domain CR `webLogicCredentialsSecret` field.
  * For all other secrets, add them to Domain CR `configOverrideSecrets` field.
* See [Debugging](#debugging) for ways to check if sit cfg is taking effect or if there are errors.

# Debugging

* If WL pods do not come up at all, then:
  * In the domain's namespace, see if you can find a job named 'DOMAIN_UID-introspect-domain-job' and a corresponding pod named something like 'DOMAIN_UID-introspect-domain-job-xxxx'.  If so, examine:
    * `kubectl -n MYDOMAINNAMESPACE describe job INTROSPECTJOBNAME`
    * `kubectl -n MYDOMAINNAMESPACE logs INTROSPECTPODNAME`
  * Check your operator log for Warning/Error/Severe messages.
    * `kubectl -n MYOPERATORNAMESPACE logs OPERATORPODNAME`

* If WL pods do start, then:
  * Search your admin server pod's `kubectl log` for the keyword `situational`, for example `kubectl logs MYADMINPOD | grep -i situational`.
    * The only WebLogic Server log lines that match should look something like like 
      * `<Dec 14, 2018 12:20:47 PM UTC> <Info> <Management> <BEA-141330> <Loading situational config file: /shared/domains/domain1/optconfig/custom-situational-config.xml>`
      * This line indicates a situational config file has been loaded.
    * If the search yields Warning or Error lines, then the format of the custom situational config template is incorrect, and the Warning or Error text should describe the problem.
  * Look in your 'DOMAIN_HOME/optconfig' directory.
    * This directory, or a subdirectory within this directory, should contain each of your custom situational files.
    * If it doesn't, this likely indicates your Domain Resource configOverrides was not set to match your custom override config map name, or that your custom override config map does not contain your override files.

* If you fix an error, then your override config map needs to be updated, and the Domain needs to be restarted.

* If you'd like to verify that situational config is taking effect in the WebLogic bean tree, one way to do this is to compare the 'server config' and 'domain config' bean tree values. 
  * The 'domain config' value should reflect the original value in your domain home configuration.
  * The 'server config' value should reflect the overridden value. 
  * For example, assuming your DOMAIN_UID is `domain1`, and your domain contains a WebLogic Server named 'admin-server', then:

  ```
  kubectl exec -it domain1-admin-server /bin/bash
  $ wlst.sh
  > connect(MYADMINUSERNAME, MYADMINPASSWORD, 't3://domain1-admin-server:7001')
  > domainConfig()
  > get('/Servers/admin-server/MaxMessageSize')
  > serverConfig()
  > get('/Servers/admin-server/MaxMessageSize')
  > exit()
  ```

**IMPORTANT: WebLogic Servers will still boot, and will skip overriding, when they detect an incorrectly formatted config override template file.  So it is important to make template files are correct in a QA environment before attempting to use them in production, as a custom override may be critical for correctness.**

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

WebLogic Situational Config feature provides advanced options and capabilities that are supported, but aren't covered in this document. For example, you can use a wildcard character in place of an mbean name. See [References](#references).

# References

See the 'Managing Configuration Changes' chapter in 'OracleÂ Fusion Middleware Understanding Domain Configuration for Oracle WebLogic Sever' version TBD.

# Release Notes

TBD These are to be moved to a central release notes section?

