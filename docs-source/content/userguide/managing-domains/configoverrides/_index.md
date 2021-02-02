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
* [Overrides distribution](#overrides-distribution)
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

Use configuration overrides (also called _situational configuration_) to customize a Domain in Image or Domain in PV domain's WebLogic domain configuration without modifying the domain's actual `config.xml` or system resource files. For example, you may want to override a JDBC data source XML module user name, password, and URL so that it references a local database.

You can use overrides to customize domains as they are moved from QA to production, are deployed to different sites, or are even deployed multiple times at the same site. Beginning with operator version 3.0.0, you can now modify configuration overrides for running WebLogic Server instances and have these new overrides take effect dynamically. There are [limitations](#unsupported-overrides) to the WebLogic configuration attributes that can be modified by overrides and only changes to dynamic configuration MBean attributes may be changed while a server is running. Other changes, specifically overrides to non-dynamic MBeans, must be applied when servers are starting or restarting.

#### How do you specify overrides?

* Make sure your domain home meets the prerequisites. See [Prerequisites](#prerequisites).
* Make sure your overrides are supported. See [Typical overrides](#typical-overrides) and [Unsupported overrides](#unsupported-overrides).
* Create a Kubernetes ConfigMap that contains:
  * Override templates (also known as situational configuration templates), with names and syntax as described in [Override template names and syntax](#override-template-names-and-syntax).
  * A file named `version.txt` that contains the exact string `2.0`.
* Set your Domain `configuration.overridesConfigMap` field to the name of this ConfigMap.
* If templates leverage secret macros:
  * Create Kubernetes Secrets that contain template macro values.
  * Set your domain `configuration.secrets` to reference the aforementioned Secrets.
* If your configuration overrides modify non-dynamic MBean attributes and you currently have WebLogic Server instances from this domain running:
  * Decide if the changes you are making to non-dynamic MBean attributes can be applied by rolling the affected clusters or Managed Server instances or if the change required a full domain shutdown.
  * If a full domain shut down is requried, stop all running WebLogic Server instance Pods in your domain and then restart them. (See [Starting and stopping servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#starting-and-stopping-servers" >}}).)
  * Otherwise, simply restart your domain, which includes rolling clusters. (See [Restarting servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#restarting-servers" >}}).)
* Verify your overrides are taking effect.  (See [Debugging](#debugging)).

For a detailed walk-through of these steps, see the [Step-by-step guide](#step-by-step-guide).

#### How do overrides work during runtime?

* Configuration overrides are processed during the operator's [introspection]({{< relref "/userguide/managing-domains/domain-lifecycle/introspection.md" >}}) phase.
* Introspection automatically occurs when:
  1. The operator is starting a WebLogic Server instance when there are currently no other servers running. This occurs when the operator first starts servers for a domain or when starting servers following a full domain shutdown.
  2. For Model in Image, the operator determines that at least one WebLogic Server instance that is currently running must be shut down and restarted. This could be a rolling of one or more clusters, the shut down and restart of one or more WebLogic Server instances, or a combination.
* You can [initiate introspection]({{< relref "/userguide/managing-domains/domain-lifecycle/introspection/_index.md#initiating-introspection" >}}) by changing the value of the Domain `introspectVersion` field.
* For configuration overrides and during introspection, the operator will:
  * Resolve any macros in your override templates.
  * Place expanded override templates in the `optconfig` directory located in each WebLogic domain home directory.  
* When the WebLogic Server instances start, they will:
  * Automatically load the override files from the `optconfig` directory.
  * Use the override values in the override files instead of the values specified in their `config.xml` or system resource XML files.
* WebLogic Server instances monitor the files in the `optconfig` directory so that if these files change while the server is running, WebLogic will detect and use the new configuration values based on the updated contents of these files. This only works for changes to configuration overrides related to dynamic configuration MBean attributes.  

For a detailed walk-through of the runtime flow, see the [Internal design flow](#internal-design-flow).

---
### Prerequisites

* Configuration overrides can be used in combination with Domain in Image and Domain in PV domains.
  For Model in Image domains (introduced in 3.0.0), use [Model in Image Runtime Updates]({{< relref "/userguide/managing-domains/model-in-image/runtime-updates.md" >}}) instead.

* A WebLogic domain home must not contain any configuration overrides XML file in its `optconfig` directory that was not placed there by the operator. Any existing configuration overrides XML files in this directory will be deleted and replaced by your operator override templates, if any.

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

See [overrides distribution](#overrides-distribution) for a discussion of distributing new or changed configuration overrides to already running WebLogic Server instances.

---
### Unsupported overrides

**IMPORTANT: The operator does not support customer-provided overrides in the following areas.**

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

Note that it's supported, even expected, to override network access point `public` or `external` addresses and ports. Also note that external access to JMX (MBean) or online WLST requires that the network access point internal port and external port match (external T3 or HTTP tunneling access to JMS, RMI, or EJBs don't require port matching).

The behavior when using an unsupported override is undefined.

### Overrides distribution

The operator generates the final configuration overrides, combining customer-provided configuration overrides and operator-generated overrides, during the operator's introspection phase. These overrides are then used when starting or restarting WebLogic Server instances. Starting with operator version 3.0.0, these [overrides can also be distributed]({{< relref "/userguide/managing-domains/domain-lifecycle/introspection/_index.md#distributing-changes-to-configuration-overrides" >}}) and applied to already running WebLogic Server instances.

For [Domain in PV]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting/_index.md#domain-in-pv" >}}), the ability to change WebLogic domain configuration using traditional management transactions involving the Administration Console or WLST can be combined with the ability to initiate a repeat introspection and distribute updated configuration overrides. This combination supports use cases such as defining a new WebLogic cluster and then immediately starting Managed Server cluster members.

---
### Override template names and syntax

Overrides leverage a built-in WebLogic feature called "Configuration Overriding" which is often informally called "Situational Configuration." Configuration overriding consists of XML formatted files that closely resemble the structure of WebLogic `config.xml` and system resource module XML files. In addition, the attribute fields in these files can embed `add`, `replace`, and `delete` verbs to specify the desired override action for the field.

#### Override template names

The operator requires a different file name format for override templates than WebLogic's built-in configuration overrides feature.  It converts the names to the format required by the configuration overrides feature when it moves the templates to the domain home `optconfig` directory.  The following table describes the format:

| Original Configuration |  Required Override Name |
| --------------- |  ---------------------  |
| `config.xml`    |  `config.xml`           |
| JMS module      |  `jms-MODULENAME.xml`   |
| JDBC module     |  `jdbc-MODULENAME.xml`  |
| Diagnostics module     |  `diagnostics-MODULENAME.xml`  |

A `MODULENAME` must correspond to the MBean name of a system resource defined in your original `config.xml` file. It's not possible to add a new module by using overrides. If you need your overrides to set up a new module, then have your original configuration specify 'skeleton' modules that can be overridden.

#### Override template schemas

An override template must define the exact schemas required by the configuration overrides feature.  The schemas vary based on the file type you wish to override.

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

Two types of macros are supported, environment variable macros and secret macros:

* Environment variable macros have the syntax `${env:ENV-VAR-NAME}`, where the supported environment variables include `DOMAIN_UID`, `DOMAIN_NAME`, `DOMAIN_HOME`,  and `LOG_HOME`.

* Secret macros have the syntax `${secret:SECRETNAME.SECRETKEY}` and `${secret:SECRETNAME.SECRETKEY:encrypt}`.

The secret macro `SECRETNAME` field must reference the name of a Kubernetes Secret, and the `SECRETKEY` field must reference a key within that Secret. For example, if you have created a Secret named `dbuser` with a key named `username` that contains the value `scott`, then the macro `${secret:dbuser.username}` will be replaced with the word `scott` before the template is copied into its WebLogic Server instance Pod.

{{% notice warning %}}
**SECURITY NOTE**: Use the `:encrypt` suffix in a secret macro to encrypt its replacement value with the WebLogic WLST `encrypt` command (instead of leaving it at its plain text value).  This is useful for overriding MBean attributes that expect encrypted values, such as the `password-encrypted` field of a data source, and is also useful for ensuring that a custom overrides configuration file the operator places in the domain home does not expose passwords in plain-text.
{{% /notice %}}

#### Override template syntax special requirements

**Check each item below for best practices and to ensure custom overrides configuration takes effect:**

* Reference the name of the current bean and each parent bean in any hierarchy you override.
  * Note that the `combine-mode` verbs (`add` and `replace`) should be omitted for beans that are already defined in your original domain home configuration.
       * See [Override template samples](#override-template-samples) for examples.
* Use `replace` and `add` verbs as follows:
  * If you are adding a new bean that doesn't already exist in your original domain home `config.xml`, then specify `add` on the MBean itself and on each attribute within the bean.
       * See the `server-debug` stanza in [Override template samples](#override-template-samples) for an example.
  * If you are adding a new attribute to an existing bean in the domain home `config.xml`, then the attribute needs an `add` verb.
       * See the `max-message-size` stanza in [Override template samples](#override-template-samples) for an example.
  * If you are changing the value of an existing attribute within a domain home `config.xml`, then the attribute needs a `replace` verb.
       * See the `public-address` stanza in  [Override template samples](#override-template-samples) for an example.
* When overriding `config.xml`:
  * The XML namespace (`xmlns:` in the XML) must be exactly as specified in [Override template schemas](#override-template-schemas).
       * For example, use `d:` to reference `config.xml` beans and attributes, `f:` for `add` and `replace` `domain-fragment` verbs, and `s:` to reference the configuration overrides schema.
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
 *  Sets the `public-address` and `public-port` fields with values obtained from a Secret named `test-host` with keys `hostname` and `port`. It assumes the original config.xml already sets these fields, and so uses `replace` instead of `add`.
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

The following `jdbc-testDS.xml` override template demonstrates setting the URL, user name, and password-encrypted fields of a JDBC module named `testDS` by using `secret macros`.  The generated configuration overrides that replaces the macros with secret values will be located in the `DOMAIN_HOME/optconfig/jdbc` directory.   The `password-encrypted` field will be populated with an encrypted value because it uses a secret macro with an `:encrypt` suffix.  The Secret is named `dbsecret` and contains three keys: `url`, `username`, and `password`.

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
* Make sure your overrides are supported. See [Typical overrides](#typical-overrides), [Overrides distribution](#overrides-distribution), and [Unsupported overrides](#unsupported-overrides).
* Create a directory containing (A) a set of configuration overrides templates for overriding the MBean properties you want to replace and (B) a `version.txt` file.
  * This directory must not contain any other files.
  * The `version.txt` file must contain exactly the string `2.0`.
      * Note: This version.txt file must stay `2.0` even when you are updating your templates from a previous deployment.
  * Templates must not override the settings listed in [Unsupported overrides](#unsupported-overrides).
  * Templates must be formatted and named as per [Override template names and syntax](#override-template-names-and-syntax).
  * Templates can embed macros that reference environment variables or Kubernetes Secrets.  See [Override template macros](#override-template-macros).
* Create a Kubernetes ConfigMap from the directory of templates.
  * The ConfigMap must be in the same Kubernetes Namespace as the domain.
  * If the ConfigMap is going to be used by a single `DOMAIN_UID`, then we recommend adding the `weblogic.domainUID=<mydomainuid>` label to help track the resource.
  * For example, assuming `./mydir` contains your `version.txt` and situation configuration template files:

    ```
    kubectl -n MYNAMESPACE create cm MYCMNAME --from-file ./mydir
    kubectl -n MYNAMESPACE label cm MYCMNAME weblogic.domainUID=DOMAIN_UID
    ```
* Create any Kubernetes Secrets referenced by a template "secret macro".
  * Secrets can have multiple keys (files) that can hold either cleartext or base64 values. We recommend that you use base64 values for passwords by using `Opaque` type secrets in their `data` field, so that they can't be easily read at a casual glance. For more information, see https://kubernetes.io/docs/concepts/configuration/secret/.
  * Secrets must be in the same Kubernetes Namespace as the domain.
  * If a Secret is going to be used by a single `DOMAIN_UID`, then we recommend adding the `weblogic.domainUID=<mydomainuid>` label to help track the resource.
  * For example:

    ```
    kubectl -n MYNAMESPACE create secret generic my-secret --from-literal=key1=supersecret --from-literal=key2=topsecret
    kubectl -n MYNAMESPACE label secret my-secret weblogic.domainUID=DOMAIN_UID
    ```
* Configure the name of the ConfigMap in the Domain YAML file `configuration.overridesConfigMap` field.
* Configure the names of each Secret in Domain YAML file.
  * If the Secret contains the WebLogic admin `username` and `password` keys, then set the Domain YAML file `webLogicCredentialsSecret` field.
  * For all other Secrets, add them to the Domain YAML file `configuration.secrets` field. Note: This must be in an array format even if you only add one Secret (see the sample Domain YAML below).
* Changes to configuration overrides, including the contents of the ConfigMap containing the override templates or the contents of referenced Secrets, do not take effect until the operator runs or repeats its [introspection]({{< relref "/userguide/managing-domains/domain-lifecycle/introspection.md" >}}) of the WebLogic domain configuration.
* If your configuration overrides modify non-dynamic MBean attributes and you currently have WebLogic Server instances from this domain running:
  * Decide if the changes you are making to non-dynamic MBean attributes can be applied by rolling the affected clusters or Managed Server instances, or if the change requires a full domain shutdown. (See [Overrides distribution](#overrides-distribution))
  * If a full domain shut down is requried, stop all running WebLogic Server instance Pods in your domain and then restart them. (See [Starting and stopping servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#starting-and-stopping-servers" >}}).)
  * Otherwise, simply restart your domain, which includes rolling clusters. (See [Restarting servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#restarting-servers" >}}).)
* See [Debugging](#debugging) for ways to check if the configuration overrides are taking effect or if there are errors.

Example Domain YAML:
```
apiVersion: "weblogic.oracle/v8"
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

Use this information to verify that your overrides are taking effect or if there are errors.

__Background notes:__

- The WebLogic Server Administration Console will _not_ reflect any override changes.
  - You cannot use the Console to verify that overrides are taking effect.
  - Instead, you can check overrides using WLST; see the `wlst.sh` script below for details.

- Incorrect override files may be silently accepted without warnings or errors.
  - For example, WebLogic Server instance Pods may fully start regardless of XML
    override syntax errors or if the specified name of an MBean is incorrect.
  - So, it is important to make sure that the template files are correct in a QA environment,
    otherwise, WebLogic Servers may start even though critically required
    overrides are failing to take effect.

- Some incorrect overrides may be detected on WebLogic Server versions that
  support the `weblogic.SituationalConfig.failBootOnError` system property
  (not applicable to WebLogic Server 12.2.1.3.0).
  - If the system property is supported, then, by default, WebLogic Server will fail to boot
    if it encounters a syntax error while loading configuration overrides files.
  - If you _want_ to start up WebLogic Servers with incorrectly formatted override files,
    then disable this check by setting the `FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR`
    environment variable in the Kubernetes
    containers for the WebLogic Servers to `false`.


__Debugging steps:__

* Make sure that you've followed each step in the [Step-by-step guide](#step-by-step-guide).

* If WebLogic Server instance Pods do not come up at all, then:
  * Examine your Domain resource status: `kubectl -n MYDOMAINNAMESPACE describe domain MYDOMAIN`
  * Check events for the Domain: `kubectl -n MY_NAMESPACE get events --sort-by='.lastTimestamp'`.
  For more information, see [Domain events]({{< relref "/userguide/managing-domains/domain-events.md" >}}).

  * Check the introspector job and its log.
    * In the domain's namespace, see if you can find a job named `DOMAIN_UID-introspector`
      and a corresponding pod named something like `DOMAIN_UID-introspector-xxxx`.  If so, examine:
      * `kubectl -n MYDOMAINNAMESPACE describe job INTROSPECTJOBNAME`
      * `kubectl -n MYDOMAINNAMESPACE logs INTROSPECTPODNAME`
    * The introspector log is mirrored to the Domain resource `spec.logHome` directory
      when `spec.logHome` is configured and `spec.logHomeEnabled` is true.
  * Check the operator log for `Warning`/`Error`/`Severe` messages.
      * `kubectl -n MYOPERATORNAMESPACE logs OPERATORPODNAME`

* If WebLogic Server instance Pods do start, then:
  * Search your Administration Server Pod's `kubectl log` for the keyword `situational`, for example, `kubectl logs MYADMINPOD | grep -i situational`.
      * The only WebLogic Server log lines that match should look something like:
         * `<Dec 14, 2018 12:20:47 PM UTC> <Info> <Management> <BEA-141330> <Loading situational configuration file: /shared/domains/domain1/optconfig/custom-situational-config.xml>`
         * This line indicates a configuration overrides file has been loaded.
      * If the search yields `Warning` or `Error` lines, then the format of the custom configuration overrides template is incorrect, and the `Warning` or `Error` text should describe the problem.
      * **Note**: The following exception may show up in the server logs when overriding JDBC modules. It is not expected to affect runtime behavior, and can be ignored:
         ```
         java.lang.NullPointerException
           at weblogic.management.provider.internal.situationalconfig.SituationalConfigManagerImpl.registerListener(SituationalConfigManagerImpl.java:227)
           at weblogic.management.provider.internal.situationalconfig.SituationalConfigManagerImpl.start(SituationalConfigManagerImpl.java:319)
           ...
           at weblogic.management.configuration.DomainMBeanImpl.setJDBCSystemResources(DomainMBeanImpl.java:11444)
           ...
         ```
  * Look in your `DOMAIN_HOME/optconfig` directory.
    * This directory, or a subdirectory within this directory, should contain each of your custom configuration overrides files.
    * If it doesn't, then this likely indicates that your Domain YAML file `configuration.overridesConfigMap` was not set to match your custom override ConfigMap name, or that your custom override ConfigMap does not contain your override files.

* If the Administration Server Pod does start but fails to reach ready state or tries to restart:
  * Check for this message ` WebLogic Server failed to start due to missing or invalid situational configuration files` in the Administration Server Pod's `kubectl log`
    * This suggests that the Administration Server failure to start may have been caused by errors found in a configuration override file.
      * Lines containing the String `situational` may be found in the Administration Server Pod log to provide more hints.
      * For example:
      ```
        <Jun 20, 2019 3:48:45 AM GMT> <Warning> <Management> <BEA-141323>
        <The situational config file has an invalid format, it is being ignored: XMLSituationalConfigFile[/shared/domains/domain1/optconfig/jdbc/testDS-0527-jdbc-situational-config.xml] because org.xml.sax.SAXParseException; lineNumber: 8; columnNumber: 3; The element type "jdbc:jdbc-driver-params" must be terminated by the matching end-tag "</jdbc:jdbc-driver-params>".
        ```
      * The warning message suggests a syntax error is found in the provided configuration override file for the `testDS` JDBC data source.
  * Check for pod-related Kubernetes events: `kubectl -n MY_NAMESPACE get events --sort-by='.lastTimestamp'`.

* If you'd like to verify that the configuration overrides are taking effect in the WebLogic MBean tree, then one way to do this is to compare the `server config` and `domain config` MBean tree values.
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
* To cause the WebLogic configuration overrides feature to produce additional debugging information in the WebLogic Server logs, configure the `JAVA_OPTIONS` environment variable in your Domain YAML file with:
  ```
  -Dweblogic.debug.DebugSituationalConfig=true
  -Dweblogic.debug.DebugSituationalConfigDumpXml=true
  ```


---
### Internal design flow

* The operator generates the final configuration overrides, which include the merging of operator-generated overrides and the processing of any customer-provided configuration overrides templates and Secrets, during its introspection phase.
* The operator creates a Kubernetes Job for introspection named `DOMAIN_UID-introspector`.
* The introspector Job's Pod:
  * Mounts the Kubernetes ConfigMap and Secrets specified by using the operator Domain `configuration.overridesConfigMap`, `webLogicCredentialsSecret`, and `configuration.secrets` fields.
  * Reads the mounted configuration overrides templates from the ConfigMap and expands them to create the actual configuration overrides files for the domain:
      * It expands some fixed replaceable values (for example, `${env:DOMAIN_UID}`).
      * It expands referenced Secrets by reading the value from the corresponding mounted secret file (for example, `${secret:mysecret.mykey}`).
      * It optionally encrypts secrets using offline WLST to encrypt the value - useful for passwords (for example, `${secret:mysecret.mykey:encrypt}`).
      * It returns expanded configuration overrides files to the operator.
      * It reports any errors when attempting expansion to the operator.
* The operator runtime:
  * Reads the expanded configuration overrides files or errors from the introspector.
  * And, if the introspector reported no errors, it:
      * Puts configuration overrides files in one or more ConfigMaps whose names start with `DOMAIN_UID-weblogic-domain-introspect-cm`.
      * Mounts these ConfigMaps into the WebLogic Server instance Pods.
  * Otherwise, if the introspector reported errors, it:
      * Logs warning, error, or severe messages.
      * Will not start WebLogic Server instance Pods; however, any already running Pods are preserved.
* The `startServer.sh` script in the WebLogic Server instance Pods:
  * Copies the expanded configuration overrides files to a special location where the WebLogic runtime can find them:
      * `config.xml` overrides are copied to the `optconfig` directory in its domain home.
      * Module overrides are copied to the `optconfig/jdbc`, `optconfig/jms`, or `optconfig/diagnostics` directory.
  * Deletes any configuration overrides files in the `optconfig` directory that do not have corresponding template files in the ConfigMap.
* WebLogic Servers read their overrides from their domain home's `optconfig` directory.
* If WebLogic Server instance Pods are already running when introspection is repeated and this new introspection generates different configuration overrides then:
  * After the operator updates the ConfigMap, Kubernetes modifies the mounted files in running containers to match the new contents of the ConfigMap.
  * The rate of this periodic sync of ConfigMap data by `kubelet` is configurable, but defaults to 10 seconds.
  * If `overridesDistributionStrategy` is DYNAMIC, then the `livenessProbe.sh` script, which is already periodically invoked by Kubernetes, will perform the same actions as `startServer.sh` to update the files in `optconfig`.
  * WebLogic Server instances monitor the files in `optconfig` and dynamically update the active configuration based on the current contents of the configuration overrides files.
  * Otherwise, if the `overridesDistributionStrategy` is ON_RESTART, then the updated files at the ConfigMap's mount point are not copied to `optconfig` while the WebLogic Server instance is running and, therefore, don't affect the active configuration.

{{% notice note %}} Changes to configuration overrides distributed to running WebLogic Server instances can only take effect if the corresponding WebLogic configuration MBean attribute is "dynamic". For instance, the Data Source `passwordEncrypted` attribute is dynamic while the `Url` attribute is non-dynamic.
{{% /notice %}}
