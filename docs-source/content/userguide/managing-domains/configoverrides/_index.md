+++
title = "Configuration overrides"
date = 2019-02-23T16:45:16-05:00
weight = 5
pre = "<b> </b>"
+++

#### Contents

* [Overview](#overview)
* [Prerequisites](#prerequisites)
* [Typical overrides](#typical-overrides)
* [Unsupported overrides](#unsupported-overrides)
* [Override template names and syntax](#override-template-names-and-syntax)
  * [Override template names](#override-template-names)
  * [Override template schemas](#override-template-schemas)
  * [Override template macros](#override-template-macros)
  * [Override template syntax special requirements](#override-template-syntax-special-requirements)
  * [Override template samples](#override-template-samples)
* [Step-by-step guide](#step-by-step-guide)
* [Debugging](#debugging)
* [Internal design flow](#internal-design-flow)

---
### Overview

{{% notice note %}}
Configuration overrides can only be used in combination with Domain in Image and Domain in PV domains. For Model in Image domains, use [Model in Image Runtime Updates]({{< relref "/userguide/managing-domains/model-in-image/runtime-updates.md" >}}) instead.
{{% /notice %}}

Use configuration overrides (also called _situational configuration_) to customize a Domain in Image or Domain in PV domain's WebLogic domain home configuration without modifying the domain's actual `config.xml` or system resource files. For example, you may want to override a JDBC data source XML module user name, password, and URL so that it references a local database.

You can use overrides to customize domains as they are moved from QA to production, are deployed to different sites, or are even deployed multiple times at the same site.

#### How do you specify overrides?

* Make sure your domain home meets the prerequisites. See [Prerequisites](#prerequisites).
* Make sure your overrides are supported. See [Typical overrides](#typical-overrides) and [Unsupported overrides](#unsupported-overrides).
* Create a Kubernetes configuration map that contains:
  * Override templates (also known as situational configuration templates), with names and syntax as described in [Override template names and syntax](#override-template-names-and-syntax).
  * A file named `version.txt` that contains the exact string `2.0`.
* Set your domain resource `configuration.overridesConfigMap` to the name of this configuration map.
* If templates leverage `secret macros`:
  * Create Kubernetes Secrets that contain template macro values.
  * Set your domain `configuration.secrets` to reference the aforementioned secrets.
* Stop all running WebLogic Server pods in your domain. (See [Starting and stopping servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#starting-and-stopping-servers" >}}).)
* Start or restart your domain. (See [Starting and stopping servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#starting-and-stopping-servers" >}}) and [Restarting servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#restarting-servers" >}}).)
* Verify your overrides are taking effect.  (See [Debugging](#debugging)).

For a detailed walk-through of these steps, see the [Step-by-step guide](#step-by-step-guide).

#### How do overrides work during runtime?

* When a domain is first deployed, or is restarted after shutting down all the WebLogic Server pods, the operator will:
  * Resolve any macros in your override templates.
  * Place expanded override templates in the `optconfig` directory located in each WebLogic domain home directory.  
* When the WebLogic Servers start, they will:
  * Automatically load the override files from the `optconfig` directory.
  * Use the override values in the override files instead of the values specified in their `config.xml` or system resource XML files.

For a detailed walk-through of the runtime flow, see the [Internal design flow](#internal-design-flow).

---
### Prerequisites

* Configuration overrides can be used in combination with Domain in Image and Domain in PV domains in releases before 3.0.0.
  In release 3.0.0, configuration overrides can be used in combination with Domain in Image and Domain in PV
  domains (the `domainHomeSourceType` must be either `PersistentVolume` or `Image`). For Model in Image domains (introduced in 3.0.0)
  (`domainHomeSourceType` is `FromModel`), use [Model in Image Runtime Updates]({{< relref "/userguide/managing-domains/model-in-image/runtime-updates.md" >}}) instead.

* A WebLogic domain home must not contain any situational configuration XML file in its `optconfig` directory that was not placed there by the operator. Any existing situational configuration XML files in this directory will be deleted and replaced by your operator override templates (if any).

* If you want to override a JDBC, JMS, or WLDF (diagnostics) module, then the original module must be located in your domain home `config/jdbc`, `config/jms`, and `config/diagnostics` directory, respectively. These are the default locations for these types of modules.

---
### Typical overrides

Typical attributes for overrides include:

* User names, passwords, and URLs for:
  * JDBC data sources
  * JMS bridges, foreign servers, and SAF
* Network channel external/public addresses
  * For remote RMI clients (T3, JMS, EJB)
  * For remote WLST clients
* Network channel external/public ports
  * For remote RMI clients (T3, JMS, EJB)
* Debugging
* Tuning (`MaxMessageSize`, and such)

---
### Unsupported overrides

**IMPORTANT: The operator does not support custom overrides in the following areas.**

* Domain topology (cluster members)
* Network channel listen address, port, and enabled configuration
* Server and domain log locations
* Node Manager related configuration
* Changing any existing MBean name
* Adding or removing a module (for example, a JDBC module)

**Specifically, do not use custom overrides for:**

* Adding or removing:
  * Servers
  * Clusters
  * Network Access Points (custom channels)
  * Modules
* Changing any of the following:
  * Dynamic cluster size
  * Default, SSL, and Admin channel `Enabled`, listen address, and port
  * Network Access Point (custom channel), listen address, or port
  * Server and domain log locations -- use the `logHome` domain setting instead
  * Node Manager access credentials
  * Any existing MBean name (for example, you cannot change the domain name)

Note that it's OK, even expected, to override network access point `public` or `external` addresses and ports. Also note that external access to JMX (MBean) or online WLST requires that the network access point internal port and external port match (external T3 or HTTP tunneling access to JMS, RMI, or EJBs don't require port matching).

The behavior when using an unsupported override is undefined.

---
### Override template names and syntax

Overrides leverage a built-in WebLogic feature called "Configuration Overriding" which is often informally called "Situational Configuration." Situational configuration consists of XML formatted files that closely resemble the structure of WebLogic `config.xml` and system resource module XML files. In addition, the attribute fields in these files can embed `add`, `replace`, and `delete` verbs to specify the desired override action for the field.

#### Override template names

The operator requires a different file name format for override templates than WebLogic's built-in situational configuration feature.  It converts the names to the format required by situational configuration when it moves the templates to the domain home `optconfig` directory.  The following table describes the format:

| Original Configuration |  Required Override Name |
| --------------- |  ---------------------  |
| `config.xml`    |  `config.xml`           |
| JMS module      |  `jms-MODULENAME.xml`   |
| JDBC module     |  `jdbc-MODULENAME.xml`  |
| Diagnostics module     |  `diagnostics-MODULENAME.xml`  |

A `MODULENAME` must correspond to the MBean name of a system resource defined in your original `config.xml` file. It's not possible to add a new module by using overrides. If you need your overrides to set up a new module, then have your original configuration specify 'skeleton' modules that can be overridden.

#### Override template schemas

An override template must define the exact schemas required by the situational configuration feature.  The schemas vary based on the file type you wish to override.

_`config.xml`_
```
<?xml version='1.0' encoding='UTF-8'?>
<d:domain xmlns:d="http://xmlns.oracle.com/weblogic/domain"
          xmlns:f="http://xmlns.oracle.com/weblogic/domain-fragment"
          xmlns:s="http://xmlns.oracle.com/weblogic/situational-config">
  ...
</d:domain>
```

_`jdbc-MODULENAME.xml`_
```
<?xml version='1.0' encoding='UTF-8'?>
<jdbc:jdbc-data-source xmlns:jdbc="http://xmlns.oracle.com/weblogic/jdbc-data-source"
                       xmlns:f="http://xmlns.oracle.com/weblogic/jdbc-data-source-fragment"
                       xmlns:s="http://xmlns.oracle.com/weblogic/situational-config">
  ...
</jdbc:jdbc-data-source>
```

_`jms-MODULENAME.xml`_
```
<?xml version='1.0' encoding='UTF-8'?>
<jms:weblogic-jms xmlns:jms="http://xmlns.oracle.com/weblogic/weblogic-jms"
                  xmlns:f="http://xmlns.oracle.com/weblogic/weblogic-jms-fragment"
                  xmlns:s="http://xmlns.oracle.com/weblogic/situational-config" >
  ...
</jms:weblogic-jms>
```

_`diagnostics-MODULENAME.xml`_
```
<?xml version='1.0' encoding='UTF-8'?>
<wldf:wldf-resource xmlns:wldf="http://xmlns.oracle.com/weblogic/weblogic-diagnostics"
                    xmlns:f="http://xmlns.oracle.com/weblogic/weblogic-diagnostics-fragment"
                    xmlns:s="http://xmlns.oracle.com/weblogic/situational-config" >
  ...
</wldf:wldf-resource>
```

#### Override template macros

The operator supports embedding macros within override templates. This helps make your templates flexibly handle multiple use cases, such as specifying a different URL, user name, and password for a different deployment.

Two types of macros are supported, `environment variable macros` and `secret macros`:

* Environment variable macros have the syntax `${env:ENV-VAR-NAME}`, where the supported environment variables include `DOMAIN_UID`, `DOMAIN_NAME`, `DOMAIN_HOME`,  and `LOG_HOME`.

* Secret macros have the syntax `${secret:SECRETNAME.SECRETKEY}` and `${secret:SECRETNAME.SECRETKEY:encrypt}`.

The secret macro `SECRETNAME` field must reference the name of a Kubernetes Secret, and the `SECRETKEY` field must reference a key within that secret. For example, if you have created a secret named `dbuser` with a key named `username` that contains the value `scott`, then the macro `${secret:dbuser.username}` will be replaced with the word `scott` before the template is copied into its WebLogic Server pod.

**SECURITY NOTE: Use the `:encrypt` suffix in a secret macro to encrypt its replacement value with the WebLogic WLST `encrypt` command (instead of leaving it at its plain text value).  This is useful for overriding MBean attributes that expect encrypted values, such as the `password-encrypted` field of a data source, and is also useful for ensuring that a custom override situational configuration file the operator places in the domain home does not expose passwords in plain-text.**

#### Override template syntax special requirements

**Check each item below for best practices and to ensure custom situational configuration takes effect:**

* Reference the name of the current bean and each parent bean in any hierarchy you override.
  * Note that the `combine-mode` verbs (`add` and `replace`) should be omitted for beans that are already defined in your original domain home configuration.
       * See [Override template samples](#override-template-samples) for examples.
* Use situational config `replace` and `add` verbs as follows:
  * If you are adding a new bean that doesn't already exist in your original domain home `config.xml`, then specify `add` on the MBean itself and on each attribute within the bean.
       * See the `server-debug` stanza in [Override template samples](#override-template-samples) for an example.
  * If you are adding a new attribute to an existing bean in the domain home `config.xml`, then the attribute needs an `add` verb.
       * See the `max-message-size` stanza in [Override template samples](#override-template-samples) for an example.
  * If you are changing the value of an existing attribute within a domain home `config.xml`, then the attribute needs a `replace` verb.
       * See the `public-address` stanza in  [Override template samples](#override-template-samples) for an example.
* When overriding `config.xml`:
  * The XML namespace (`xmlns:` in the XML) must be exactly as specified in [Override template schemas](#override-template-schemas).
       * For example, use `d:` to reference `config.xml` beans and attributes, `f:` for `add` and `replace` `domain-fragment` verbs, and `s:` to reference the situational configuration schema.
  * Avoid specifying the domain name stanza, as this may cause some overrides to be ignored (for example, server-template scoped overrides).
* When overriding modules:
  * It is a best practice to use XML namespace abbreviations `jms:`, `jdbc:`, and `wldf:` respectively for JMS, JDBC, and WLDF (diagnostics) module override files.
  * A module must already exist in your original configuration if you want to override it; it's not possible to add a new module by using overrides. If you need your overrides to set up a new module, then have your original configuration specify 'skeleton' modules that can be overridden.
  * See [Overriding a data source module](#overriding-a-data-source-module) for best practice advice. Note that similar advice applies generally to other module types.
* Consider having your original configuration reference invalid user names, passwords, and URLs:
  * If your original (non-overridden) configuration references non-working user names, passwords, and URLS, then this helps guard against accidentally deploying a working configuration that's invalid for the intended environment. For example, if your base configuration references a working QA database, and there is some mistake in setting up overrides, then it's possible the running servers will connect to the QA database when you deploy to your production environment.

#### Override template samples

Here are some sample template override files.

#### Overriding `config.xml`

The following `config.xml` override file demonstrates:

 *  Setting the `max-message-size` field on a WebLogic Server named `admin-server`.  It assumes the original `config.xml` does not define this value, and so uses `add` instead of `replace`.
 *  Sets the `public-address` and `public-port` fields with values obtained from a secret named `test-host` with keys `hostname` and `port`. It assumes the original config.xml already sets these fields, and so uses `replace` instead of `add`.
 *  Sets two debug settings. It assumes the original config.xml does not have a `server-debug` stanza, so it uses `add` throughout the entire stanza.

```
<?xml version='1.0' encoding='UTF-8'?>
<d:domain xmlns:d="http://xmlns.oracle.com/weblogic/domain"
          xmlns:f="http://xmlns.oracle.com/weblogic/domain-fragment"
          xmlns:s="http://xmlns.oracle.com/weblogic/situational-config" >
    <d:server>
        <d:name>admin-server</d:name>
        <d:max-message-size f:combine-mode="add">78787878</d:max-message-size>
        <d:server-debug f:combine-mode="add">
          <d:debug-server-life-cycle f:combine-mode="add">true</d:debug-server-life-cycle>
          <d:debug-jmx-core f:combine-mode="add">true</d:debug-jmx-core>
        </d:server-debug>
        <d:network-access-point>
          <d:name>T3Channel</d:name>
          <d:public-address f:combine-mode="replace">${secret:test-host.hostname}</d:public-address>
          <d:public-port f:combine-mode="replace">${secret:test-host.port}</d:public-port>
        </d:network-access-point>
    </d:server>
</d:domain>
```

#### Overriding a data source module

The following `jdbc-testDS.xml` override template demonstrates setting the URL, user name, and password-encrypted fields of a JDBC module named `testDS` by using `secret macros`.  The generated situational configuration that replaces the macros with secret values will be located in the `DOMAIN_HOME/optconfig/jdbc` directory.   The `password-encrypted` field will be populated with an encrypted value because it uses a secret macro with an `:encrypt` suffix.  The secret is named `dbsecret` and contains three keys: `url`, `username`, and `password`.

Best practices for data source modules and their overrides:

* A data source module must already exist in your original configuration if you want to override it; it's not possible to add a new module by using overrides. If you need your overrides to set up a new module, then have your original configuration specify 'skeleton' modules that can be overridden. See the next two bulleted items for the typical contents of a skeleton data source module.
* Set your original (non-overridden) URL, username, and password to invalid values. This helps prevent accidentally starting a server without overrides, and then having the data source successfully connect to a database that's wrong for the current environment. For example, if these attributes are set to reference a QA database in your original configuration, then a mistake configuring overrides in your production Kubernetes Deployment could cause your production applications to use your QA database.
* Set your original (non-overridden) `JDBCConnectionPoolParams` `MinCapacity` and `InitialCapacity` to `0`, and set your original `DriverName` to a reference an existing JDBC Driver. This ensures that you can still successfully boot a server even when you have configured invalid URL/username/password values, your database isn't running, or you haven't specified your overrides yet.

```
<?xml version='1.0' encoding='UTF-8'?>
<jdbc:jdbc-data-source xmlns:jdbc="http://xmlns.oracle.com/weblogic/jdbc-data-source"
                       xmlns:f="http://xmlns.oracle.com/weblogic/jdbc-data-source-fragment"
                       xmlns:s="http://xmlns.oracle.com/weblogic/situational-config">

  <jdbc:name>testDS</jdbc:name>
  <jdbc:jdbc-driver-params>
    <jdbc:url f:combine-mode="replace">${secret:dbsecret.url}</jdbc:url>
    <jdbc:properties>
       <jdbc:property>
          <jdbc:name>user</jdbc:name>
          <jdbc:value f:combine-mode="replace">${secret:dbsecret.username}</jdbc:value>
       </jdbc:property>
    </jdbc:properties>
    <jdbc:password-encrypted f:combine-mode="replace">${secret:dbsecret.password:encrypt}</jdbc:password-encrypted>
  </jdbc:jdbc-driver-params>
</jdbc:jdbc-data-source>
```

---
### Step-by-step guide

* Make sure your domain home meets the prerequisites. See [Prerequisites](#prerequisites).
* Make sure your overrides are supported. See [Typical overrides](#typical-overrides) and [Unsupported overrides](#unsupported-overrides).
* Create a directory containing (A) a set of situational configuration templates for overriding the MBean properties you want to replace and (B) a `version.txt` file.
  * This directory must not contain any other files.
  * The `version.txt` file must contain exactly the string `2.0`.
      * Note: This version.txt file must stay `2.0` even when you are updating your templates from a previous deployment.
  * Templates must not override the settings listed in [Unsupported overrides](#unsupported-overrides).
  * Templates must be formatted and named as per [Override template names and syntax](#override-template-names-and-syntax).
  * Templates can embed macros that reference environment variables or Kubernetes Secrets.  See [Override template macros](#override-template-macros).
* Create a Kubernetes configuration map from the directory of templates.
  * The configuration map must be in the same Kubernetes Namespace as the domain.
  * If the configuration map is going to be used by a single `DOMAIN_UID`, then we recommend adding the `weblogic.domainUID=<mydomainuid>` label to help track the resource.
  * For example, assuming `./mydir` contains your `version.txt` and situation configuration template files:

    ```
    kubectl -n MYNAMESPACE create cm MYCMNAME --from-file ./mydir
    kubectl -n MYNAMESPACE label cm MYCMNAME weblogic.domainUID=DOMAIN_UID
    ```
* Create any Kubernetes Secrets referenced by a template 'secret macro'.
  * Secrets can have multiple keys (files) that can hold either cleartext or base64 values. We recommend that you use base64 values for passwords by using `Opaque` type secrets in their `data` field, so that they can't be easily read at a casual glance. For more information, see https://kubernetes.io/docs/concepts/configuration/secret/.
  * Secrets must be in the same Kubernetes Namespace as the domain.
  * If a secret is going to be used by a single `DOMAIN_UID`, then we recommend adding the `weblogic.domainUID=<mydomainuid>` label to help track the resource.
  * For example:

    ```
    kubectl -n MYNAMESPACE create secret generic my-secret --from-literal=key1=supersecret --from-literal=key2=topsecret
    kubectl -n MYNAMESPACE label secret my-secret weblogic.domainUID=DOMAIN_UID
    ```
* Configure the name of the configuration map in the domain CR `configuration.overridesConfigMap` field.
* Configure the names of each secret in domain CR.
  * If the secret contains the WebLogic admin `username` and `password` keys, then set the domain CR `webLogicCredentialsSecret` field.
  * For all other secrets, add them to the domain CR `configuration.secrets` field. Note: This must be in an array format even if you only add one secret (see the sample domain resource YAML below).
* Any override changes require stopping all WebLogic pods, applying your domain resource (if it changed), and restarting the WebLogic pods before they can take effect.
  * Custom override changes on an existing running domain, such as updating an override configuration map, a secret, or a domain resource, will not take effect until all running WebLogic Server pods in your domain are shutdown (so no servers are left running), and the domain is subsequently restarted with your new domain resource (if it changed), or with your existing domain resource (if you haven't changed it).
  * To stop all running WebLogic Server pods in your domain, apply a changed resource, and then start/restart the domain:
      * Set your domain resource `serverStartPolicy` to `NEVER`, wait, and apply your latest domain resource with the `serverStartPolicy` restored back to `ALWAYS` or `IF_NEEDED` (See [Starting and stopping servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#starting-and-stopping-servers" >}}).)
      * Or delete your domain resource, wait, and apply your (potentially changed) domain resource.
* See [Debugging](#debugging) for ways to check if the situational configuration is taking effect or if there are errors.

Example domain resource YAML:
```
apiVersion: "weblogic.oracle/v2"
kind: Domain
metadata:
  name: domain1
  namespace: default
  labels:
    weblogic.domainUID: domain1
spec:
  [ ... ]
  webLogicCredentialsSecret:
    name: domain1-wl-credentials-secret
  configuration:
    overridesConfigMap: domain1-overrides-config-map
    secrets: [my-secret, my-other-secret]
  [ ... ]
```

---
### Debugging

Incorrectly formatted override files may be accepted without warnings or errors and may not prevent WebLogic pods from booting. So, it is important to make sure that the template files are correct in a QA environment, otherwise your WebLogic Servers may start even though critically required overrides are failing to take effect.

On WebLogic Servers that support the `weblogic.SituationalConfig.failBootOnError` system property ( Note: It is not supported in WebLogic Server 12.2.1.3.0 ),
by default the WebLogic Server will fail to boot if any situational configuration files are invalid,
or if it encounters an error while loading situational configuration files.
By setting the `FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR` environment variable in the Kubernetes containers for the WebLogic Servers to `false`, you can start up the WebLogic Servers even with incorrectly formatted override files.

* Make sure you've followed each step in the [Step-by-step guide](#step-by-step-guide).

* If WebLogic pods do not come up at all, then:
  * In the domain's namespace, see if you can find a job named `DOMAIN_UID-introspect-domain-job` and a corresponding pod named something like `DOMAIN_UID-introspect-domain-job-xxxx`.  If so, examine:
      * `kubectl -n MYDOMAINNAMESPACE describe job INTROSPECTJOBNAME`
      * `kubectl -n MYDOMAINNAMESPACE logs INTROSPECTPODNAME`
  * Check your operator log for Warning/Error/Severe messages.
      * `kubectl -n MYOPERATORNAMESPACE logs OPERATORPODNAME`

* If WebLogic pods do start, then:
  * Search your Administration Server pod's `kubectl log` for the keyword `situational`, for example `kubectl logs MYADMINPOD | grep -i situational`.
      * The only WebLogic Server log lines that match should look something like:
         * `<Dec 14, 2018 12:20:47 PM UTC> <Info> <Management> <BEA-141330> <Loading situational configuration file: /shared/domains/domain1/optconfig/custom-situational-config.xml>`
         * This line indicates a situational configuration file has been loaded.
      * If the search yields Warning or Error lines, then the format of the custom situational configuration template is incorrect, and the Warning or Error text should describe the problem.
      * Note: The following exception may show up in your server logs when overriding JDBC modules. It is not expected to affect runtime behavior, and can be ignored (a fix is pending for them):
         ```
         java.lang.NullPointerException
           at weblogic.management.provider.internal.situationalconfig.SituationalConfigManagerImpl.registerListener(SituationalConfigManagerImpl.java:227)
           at weblogic.management.provider.internal.situationalconfig.SituationalConfigManagerImpl.start(SituationalConfigManagerImpl.java:319)
           ...
           at weblogic.management.configuration.DomainMBeanImpl.setJDBCSystemResources(DomainMBeanImpl.java:11444)
           ...
         ```
  * Look in your `DOMAIN_HOME/optconfig` directory.
    * This directory, or a subdirectory within this directory, should contain each of your custom situational configuration files.
    * If it doesn't, then this likely indicates your domain resource `configuration.overridesConfigMap` was not set to match your custom override configuration map name, or that your custom override configuration map does not contain your override files.

* If the Administration Server pod does start but fails to reach ready state or tries to restart:
  * Check for this message ` WebLogic Server failed to start due to missing or invalid situational configuration files` in the Administration Server pod's `kubectl log`
    * This suggests that the Administration Server failure to start may have been caused by errors found in a configuration override file.
      * Lines containing the String `situational` may be found in the Administration Server pod log to provide more hints.
      * For example:
        * `<Jun 20, 2019 3:48:45 AM GMT> <Warning> <Management> <BEA-141323> <The situational config file has an invalid format, it is being ignored: XMLSituationalConfigFile[/shared/domains/domain1/optconfig/jdbc/testDS-0527-jdbc-situational-config.xml] because org.xml.sax.SAXParseException; lineNumber: 8; columnNumber: 3; The element type "jdbc:jdbc-driver-params" must be terminated by the matching end-tag "</jdbc:jdbc-driver-params>".`
      * The warning message suggests a syntax error is found in the provided configuration override file for the testDS JDBC datasource.

* If you'd like to verify that the situational configuration is taking effect in the WebLogic MBean tree, then one way to do this is to compare the `server config` and `domain config` MBean tree values.
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
* To cause the WebLogic situational configuration feature to produce additional debugging information in the WebLogic Server logs, configure the `JAVA_OPTIONS` environment variable in your domain resource with:
```
-Dweblogic.debug.DebugSituationalConfig=true
-Dweblogic.debug.DebugSituationalConfigDumpXml=true
```

* **NOTE**: The WebLogic Server Administration Console will _not_ reflect any override changes. You cannot use the Console to verify overrides are taking effect.


---
### Internal design flow

* When a domain is first deployed, or is restarted, the operator runtime creates a Kubernetes introspector job named `DOMAIN_UID-introspect-domain-job`.
* The introspector job's pod:
  * Mounts the Kubernetes configuration map and secrets specified by using the operator domain resource `configuration.overridesConfigMap`, `webLogicCredentialsSecret`, and `configuration.secrets` fields.
  * Reads the mounted situational configuration templates from the configuration map and expands them to create the actual situational configuration files for the domain:
      * It expands some fixed replaceable values (for example, `${env:DOMAIN_UID}`).
      * It expands referenced secrets by reading the value from the corresponding mounted secret file (for example, `${secret:mysecret.mykey}`).
      * It optionally encrypts secrets using offline WLST to encrypt the value - useful for passwords (for example, `${secret:mysecret.mykey:encrypt}`).
      * It returns expanded situational configuration files to the operator.
      * It reports any errors when attempting expansion to the operator.
* The operator runtime:
  * Reads the expanded situational configuration files or errors from the introspector.
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
      * Module overrides are copied to the `optconfig/jdbc`, `optconfig/jms`, or `optconfig/diagnostics` directory.
  * Deletes any situational configuration files in the `optconfig` directory that do not have corresponding template files in the configuration map.
* WebLogic Servers read their overrides from their domain home's `optconfig` directory.
