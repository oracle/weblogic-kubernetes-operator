# Configuration overrides

**PLEASE NOTE**:  This page is a work in progress.

---
# Overview

Use configuration overrides (also called _situational configuration_) to customize a WebLogic domain home configuration without modifying the domain's actual `config.xml` or system resource files. For example, you may want to override a JDBC datasource XML module user name, password, and URL so that it references a local database.

## How do you specify overrides?
* Create a Kubernetes configuration map that contains:
  * Override templates (also known as situational configuration templates).
  * A file named `version.txt` that contains the string `2.0`.
* Set your domain resource `configOverrides` to the name of this configuration map.
* Create Kubernetes secrets that contain template macro values.
* Set your domain `configOverrideSecrets` to reference the aforementioned secrets.
* Start or restart your domain.

## How do overrides work during runtime?
* When a domain is first deployed, or is restarted, the operator will:
  * Resolve any macros in your override templates.
  * Place expanded override templates in the `optconfig` directory located in each WebLogic domain home directory.  
* When the WebLogic Servers start, they will:
  * Automatically load the override files from the `optconfig` directory.
  * Use the override values in the override files instead of the values specified in their `config.xml` or system resource XML files.

You can use overrides to customize domains as they are moved from QA to production, are deployed to different sites, or are even deployed multiple times at the same site.

---
# Prerequisites

* A WebLogic domain home must not already contain a situational configuration XML file in its existing `optconfig` directory.  Any existing situational configuration XML files in this directory will be deleted and replaced by your operator override templates (if any).

* If you want to override a JDBC, JMS, or WLDF module, the original module must be located in your domain home `config/jdbc`, `config/jms`, and `config/wldf` directory, respectively. These are the default locations for these types of modules.

---
# Typical overrides

Typical attributes for overrides include:

* User names, passwords, and URLs for:
  * JDBC datasources
  * JMS bridges, foreign servers, and SAF
* Network channel public addresses:
  * For remote RMI clients (T3, JMS, EJB, JTA)
  * For remote WLST clients
* Debugging
* Tuning (`MaxMessageSize`, etc.)

---
# Unsupported overrides

**IMPORTANT: The operator does not support custom overrides in the following areas.**

* Domain topology (cluster members)
* Network channel listen address, port, and enabled configuration
* Server and domain log locations
* Node Manager related configuration.  

**Specifically, do not use custom overrides for:**

* Adding or removing:
  * Servers
  * Clusters
  * Network Access Points (custom channels)
* Changing any of the following:
  * Dynamic cluster size
  * Default, SSL, and Admin channel `Enabled`, listen address, and port
  * Network Access Point (custom channel), listen address, or port
  * Server and domain log locations -- use the `logHome` domain setting instead
  * Node Manager access credentials

In addition, it is not possible to use overrides to change the name of any MBean.

Note that it's OK, even expected, to override Network Access Point `public` or `external` addresses and ports.

The behavior when using an unsupported override is undefined.

---
# Override template names and syntax

Overrides leverage a built-in WebLogic feature called "Configuration Overriding" which is often informally called "Situational Configuration." (See [References](#References)). Situational configuration consists of XML formatted files that closely resemble the structure of WebLogic `config.xml` and system resource module XML files. In addition, the attribute fields in these files can embed `add`, `replace`, and `delete` verbs to specify the desired override action for the field.  

## Override template names

The operator requires a different file name format for override templates than WebLogic's built-in situational configuration feature.  It converts the names to the format required by situational configuration when it moves the templates to the domain home `optconfig` directory.  The following table describes the format:

| Original Configuration |  Required Override Name |
| --------------- |  ---------------------  |
| `config.xml`    |  `config.xml`           |
| JMS module      |  `jms-MODULENAME.xml`   |
| JDBC module     |  `jdbc-MODULENAME.xml`  |
| WLDF module     |  `wldf-MODULENAME.xml`  |

A `MODULENAME` must correspond to the MBean name of a system resource defined in your original `config.xml` file.

## Override template schemas

An override template must define the exact schemas required by the situational configuration feature.  The schemas vary based on the file type you wish to override.

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

## Override template macros

The operator supports embedding macros within override templates.  This helps make your templates flexibly handle multiple use cases, such as specifying a different URL, user name, and password for a different deployment.

Two types of macros are supported `environment variable macros` and `secret macros`:

* Environment variable macros have the syntax `${env:ENV-VAR-NAME}`, where the supported env vars include `DOMAIN_HOME`, `LOG_HOME`, and `DOMAIN_UID`.

* Secret macros have the syntax `${secret:SECRETNAME.SECRETKEY}` and `${secret:SECRETNAME.SECRETKEY:encrypt}`.

The secret macro `SECRETNAME` field must reference the name of a Kubernetes secret, and the `SECRETKEY` field must reference a key within that secret. For example, if you have created a secret named `dbuser` with a key named `username` that contains the value `scott`, then the macro `${secret:dbuser.username}` will be replaced with the word `scott` before the template is copied into its WebLogic server pod.

**SECURITY NOTE: Use the `:encrypt` suffix in a secret macro to encrypt its replacement value with the WebLogic WLST `encrypt` command (instead of leaving it at its plain text value).  This is useful for overriding MBean attributes that expect encrypted values, such as the `password-encrypted` field of a data source, and is also useful for ensuring that a custom override situational configuration file the operator places in the domain home does not expose passwords in plain-text.**

## Override template samples

Here are some sample template override files.  Use `combine-mode="add"` to specify the values for MBean attributes that have not been set in the original configuration, and use `combine-mode="replace"` to specify the values for MBean fields that are already set in the original configuration.

**IMPORTANT: Your overrides may not take effect if you use 'add' for an MBean attribute that already has a value specified in your original configuration, or use 'replace' for an attribute that does not already exist.**

### Overriding `config.xml`

The following `config.xml` override file demonstrates setting the `max-message-size` field on a WebLogic Server named `admin-server`, and replacing the `public-address` and `public-port` fields with values obtained from a secret named `test-host` with keys `hostname` and `port`.

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

### Overriding a data source module

The following `jdbc-testDS.xml` override template demonstrates setting the URL, user name, and password-encrypted fields of a JDBC module named `testDS` via secret macros.  The generated situational configuration that replaces the macros with secret values will be located in the `DOMAIN_HOME/optconfig/jdbc` directory.   The `password-encrypted` field will be populated with an encrypted value because it uses a secret macro with an `:encrypted` suffix.  The secret is named `dbsecret` and contains three keys: `url`, `username`, and `password`.


```
<?xml version='1.0' encoding='UTF-8'?>
<jdbc-data-source xmlns="http://xmlns.oracle.com/weblogic/jdbc-data-source"
                  xmlns:f="http://xmlns.oracle.com/weblogic/jdbc-data-source-fragment"
                  xmlns:s="http://xmlns.oracle.com/weblogic/situational-config">

  <name>testDS</name>
  <jdbc-driver-params>
    <url f:combine-mode="replace">${secret:dbsecret.url}</url>
    <properties>
       <property>
          <name>user</name>
          <value f:combine-mode="replace">${secret:dbsecret.username}</value>
       </property>
    </properties>
    <password-encrypted f:combine-mode="replace">${secret:dbsecret.password:encrypt}</password-encrypted>
  </jdbc-driver-params>
</jdbc-data-source>
```

---
# Step-by-step guide

* Create a directory containing (A) a set of situational configuration templates for overriding the MBean properties you want to replace and (B) a `version.txt` file.
  * This directory must not contain any other files.
  * The `version.txt` file must contain only the string `2.0`.
  * Templates must not override the settings listed in [Unsupported overrides](#unsupported-overrides).
  * Templates must be formatted and named as per [Override template names and syntax](#override-template-names-and-syntax) and [References](#references).
  * Templates can embed macros that reference environement variables or Kubernetes secrets.  See [Override template macros](#override-template-macros).
* Create a Kubernetes configuration map from the directory of templates.
  * The configuration map must be in the same Kubernetes namespace as the domain.
  * If the configuration map is going to be used by a single `DOMAIN_UID`, it is recommended to add the `weblogic.domainUID=<mydomainuid>` label to help track the resource.
  * For example, assuming `./mydir` contains your `version.txt` and situation configuration template files:
    ```
    kubectl -n MYNAMESPACE create cm MYCMNAME --from-file ./mydir
    kubectl -n MYNAMESPACE label cm MYCMNAME weblogic.domainUID=DOMAIN_UID
    ```
* Create any Kubernetes secrets referenced by a template macro.
  * Secrets can have multiple keys (files) that can hold either cleartext or base64 values.
  * Secrets must be in the same Kubernetes namespace as the domain.
  * If a secret is going to be used by a single `DOMAIN_UID`, it is recommended to add the `weblogic.domainUID=<mydomainuid>` label to help track the resource.
  * For example:
    ```
    kubectl -n MYNAMESPACE create secret generic my-secret --from-literal=key1=supersecret --from-literal=key2=topsecret
    kubectl -n MYNAMESPACE label secret my-secret weblogic.domainUID=DOMAIN_UID
    ```
* Configure the name of the configuration map in the domain CR `configOverrides` field.
* Configure the names of each secret in domain CR.
  * If the secret contains the WebLogic admin `username` and `password` keys, set the domain CR `webLogicCredentialsSecret` field.
  * For all other secrets, add them to domain CR `configOverrideSecrets` field.
* See [Debugging](#debugging) for ways to check if the situational configuration is taking effect or if there are errors.

---
# Debugging

* If WL pods do not come up at all, then:
  * In the domain's namespace, see if you can find a job named `DOMAIN_UID-introspect-domain-job` and a corresponding pod named something like `DOMAIN_UID-introspect-domain-job-xxxx`.  If so, examine:
    * `kubectl -n MYDOMAINNAMESPACE describe job INTROSPECTJOBNAME`
    * `kubectl -n MYDOMAINNAMESPACE logs INTROSPECTPODNAME`
  * Check your operator log for Warning/Error/Severe messages.
    * `kubectl -n MYOPERATORNAMESPACE logs OPERATORPODNAME`

* If WL pods do start, then:
  * Search your Administration Server pod's `kubectl log` for the keyword `situational`, for example `kubectl logs MYADMINPOD | grep -i situational`.
    * The only WebLogic Server log lines that match should look something like like:
      * `<Dec 14, 2018 12:20:47 PM UTC> <Info> <Management> <BEA-141330> <Loading situational configuration file: /shared/domains/domain1/optconfig/custom-situational-config.xml>`
      * This line indicates a situational configuration file has been loaded.
    * If the search yields Warning or Error lines, then the format of the custom situational configuration template is incorrect, and the Warning or Error text should describe the problem.
  * Look in your `DOMAIN_HOME/optconfig` directory.
    * This directory, or a subdirectory within this directory, should contain each of your custom situational configuration files.
    * If it doesn't, this likely indicates your domain resource `configOverrides` was not set to match your custom override configuration map name, or that your custom override configuration map does not contain your override files.

* If you'd like to verify that the situational configuration is taking effect in the WebLogic MBean tree, one way to do this is to compare the `server config` and `domain config` MBean tree values.
  * The `domain config` value should reflect the original value in your domain home configuration.
  * The `server config` value should reflect the overridden value.
  * For example, assuming your `DOMAIN_UID` is `domain1`, and your domain contains a WebLogic Server named `admin-server`, then:

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

**IMPORTANT: Custom override changes, such as updating an override configuration map, a secret, or a domain resource, will not take effect until your domain is restarted.**

**IMPORTANT: Incorrectly formatted override files are 'somewhat' silently ignored. WebLogic Servers log errors or warnings, but will still boot, and will skip overriding, when they detect an incorrectly formatted configuration override template file. So it is important to make sure template files are correct in a QA environment by checking your WebLogic pod logs for situational configuration errors and warnings, before attempting to use them in production.**

---
# Internal design flow

* When a domain is first deployed, or is restarted, the operator runtime creates an introspector Kubernetes job named `DOMAIN_UID-introspect-domain-job`.
* The introspector job's pod:
  * Mounts the Kubernetes configuration map and secrets specified via the operator domain resource `configOverrides`, `webLogicCredentialsSecret`, and `configOverrideSecrets` fields.
  * Reads the mounted situational configuration templates from the configuration map and expands them to create the actual situational configuration files for the domain:
    * It expands some fixed replaceable values (e.g. `${env:DOMAIN_UID}`).
    * It expands referenced secrets by reading the value from the corresponding mounted secret file (e.g. `${secret:mysecret.mykey}`).
    * It optionally encrypts secrets using offline WLST to encrypt the value - useful for passwords (e.g. `${secret:mysecret.mykey:encrypt}`).
    * It returns expanded situational configuration files to the operator.
    * It reports any errors when attempting expansion to the operator.
* The operator runtime:
  * Reads the expanded situational configuration files and/or errors from the introspector.
  * And, if the introspector reported no errors, it:
    * Puts situational configuration files in a configuration map named `DOMAIN_UID-weblogic-domain-introspect-cm`.
    * Mounts this configuration map into the WebLogic Server pods.
    * Starts the WebLogic Server pods.
  * Otherwise, if the introspector reported errors, it:
    * Logs warning, error, or severe messages.
    * Will not start WebLogic Server pods.
* The `startServer.sh` script in the WebLogic Server pods:
  * Copies the expanded situational configuration files to a special location where the WebLogic runtime can find them:
    * `config.xml` overrides are copied to the `optconfig` directory in its domain home.
    * Module overrides are copied to the `optconfig/jdbc`, `optconfig/jms`, or `optconfig/wldf` directory.
  * Deletes any situational configuration files in the `optconfig` directory that do not have corresponding template files in the configuration map.
* WebLogic Servers read their overrides from their domain home's `optconfig` directory.

---
# Advanced situational configuration

The WebLogic situational configuration feature provides advanced options and capabilities that are supported, but aren't covered in this document. For example, you can use a wildcard character in place of an MBean name. See [References](#references).

---
# References

See the "Managing Configuration Changes" chapter in _Oracle Fusion Middleware Understanding Domain Configuration for Oracle WebLogic Server_ version 19.1.0.0.0.
