# Configuration Overrides

> **PLEASE NOTE**:  This page is a work in progress. 

---
## Overview

Use configuration overrides to customize a WebLogic domain home configuration for a particular Kubernetes environment. For example, you may want to override a JDBC Datasource xml module Username, Password, and URL so that it references a local database. Overrides can be used to customize domains as they are moved from QA to production, deployed to different sites, or even deployed multiple times at the same site.

How do you specify overrides? Overrides are specified via a combination of:
* Defining situational config templates deployed using a Kubernetes config map.
* Including a file named 'version.txt' that contains the string '2.0' in the same config map.
* Setting your Domain resource `configOverride` to the name of this config map.
* Defining template macro values via Kubernetes secrets.
* Setting `configOverrideSecrets` to reference the aforementioned override config map and secrets.
* Starting or restarting your domain.

How do overrides work during runtime? When a Domain is first deployed, or is restarted, the Operator will resolve any macros in your override templates and place them in the 'optconfig' directory located in each WebLogic Domain Home directory.  When the WebLogic Servers start, they will automatically load the override files from this directory, and use the overrides instead of the values specified in their config.xml or system resource xml.

---
## Prerequisites

* A Domain Home must not already contain situational config xml in its existing 'optconfig' directory.  Any existing situational config xml in this directory will be deleted and replaced by your Operator override templates (if any).

* If you want to override a JDBC, JMS, or WLDF module it must be located in your Domain Home `config/jdbc`, `config/jms`, and `config/wldf` directory respectively.  These are the default locations for these types of modules.

---
## Typical Overrides

Typical attributes for overrides include:

* Usernames, Passwords, and URLs on
  * Datasources
  * JMS Bridges, JMS Foreign Servers, and JMS SAF
* Network Channel Public Addresses
  * For remote RMI clients (T3, JMS, EJB, JTA)
  * For remote WLST clients
* Debugging
* Tuning (MaxMessageSize, etc.)

---
## Unsupported Overrides

**IMPORTANT**: The Operator does not support custom overrides in these areas:

* The domain topology the Operator itself reads prior to starting WebLogic pods.
* Network configuration the Operator itself overrides or reads.
* Server and Domain log locations the Operator itself overrides.
* Node manager related configuration.  

Specifically, this includes at least:

* Changing any of the following
  * Dynamic Cluster Size 
  * Default, SSL, and Admin Enabled settings
  * Default, SSL, and Admin Listen Addresses or Ports
  * Network Access Point Listen Addresses or Ports
  * Node manager access credentials
  * Server and domain log locations (use the logHome Domain setting instead)
* Adding/removing
  * Servers
  * Clusters
  * Network Access Points

In addition, it is not possible to use overrides to change the name of any bean.

Note that it's OK, even expected, to override Network Access Point 'public' or 'external' addresses and ports.

The behavior when using an unsupported override is undefined.

---
## Override Template Names and Syntax

Overrides leverage a built-in WebLogic feature called 'Situational Config'.  Situational config consists of XML formated files that closely resemble the structure of WebLogic config.xml and system resource module xml files.  In addition, the attribute fields in these files can embed 'add', 'replace', and 'delete' (TBD) verbs to specify the desired override action for the field.  Use 'add' to specify the values for mbean attributes that have not been set in the original configuration, and use the 'replace' verb to specify the .

> **Important:**  Your overrides may not take effect if you use 'add' for an mbean attribute that already has a value specified in your original configuration, or use 'replace' for an attribute that does not already exist.

### Override Template Names

| Original Config |  Name format           |
| --------------- |  --------------------- |
| `config.xml`    |  `config.xml`          |
| JMS module      |  `jms-MODULENAME.xml`  |
| JDBC module     |  `jdbc-MODULENAME.xml` |
| WLDF module     |  `wldf-MODULENAME.xml` |

A `MODULENAME` must correspond to the mbean name of a system resource defined in your original config.xml.

> __Note:__ The Operator requires a different file name format for override templates than WebLogic's built-in Situational Config feature.  It converts the names to the format required by situational config after it populates and moves the templates to the domain home 'optconfig' directory.

### Override Template Schemas

An override template must define specific schemas required by situational config and by the configuration itself.  The required schemas vary based on the file type you wish to override.

`_config.xml_`
```
<?xml version='1.0' encoding='UTF-8'?>
<domain xmlns="http://xmlns.oracle.com/weblogic/domain"
        xmlns:f="http://xmlns.oracle.com/weblogic/domain-fragment" 
        xmlns:s="http://xmlns.oracle.com/weblogic/situational-config">
  ...
<domain>
```

`jdbc-MODULENAME.xml`
```
<?xml version='1.0' encoding='UTF-8'?>
<jdbc-data-source xmlns="http://xmlns.oracle.com/weblogic/jdbc-data-source" 
                  xmlns:f="http://xmlns.oracle.com/weblogic/jdbc-data-source-fragment" 
                  xmlns:s="http://xmlns.oracle.com/weblogic/situational-config">
  ...
<jdbc-data-source>
```

`jms-MODULENAME.xml`
```
<weblogic-jms xmlns="http://xmlns.oracle.com/weblogic/weblogic-jms"
              xmlns:f="http://xmlns.oracle.com/weblogic/weblogic-jms-fragment"
              xmlns:s="http://xmlns.oracle.com/weblogic/situational-config" >
  ...
<weblogic-jms>
```

`wldf-MODULENAME.xml`
```
<?xml version='1.0' encoding='UTF-8'?>
<wldf-resource
  xmlns:"http://xmlns.oracle.com/weblogic/weblogic-diagnostics"
  xmlns:f="http://xmlns.oracle.com/weblogic/weblogic-diagnostics-fragment"
  xmlns:s="http://xmlns.oracle.com/weblogic/situational-config" >
  ...
<wldf-resource>
```

### Override Template Macros

TBD

### Sample Override Templates

TBD

## Putting it together step by step

TBD

## Debugging

TBD

## Internal Design Flow

1. When a Domain is first deployed, or is restarted, the operator runtime creates an introspector Kubernetes job named `DOMAIN_UID-introspect-domain-job`
2. The Kubernetes config map and secrets specified via the operator Domain resource `configOverride`, `webLogicCredentialsSecret`, and `configOverrideSecrets` fields are mounted into the pod.
2. The introspector job's pod reads the mounted situational config templates from the config map and expands them to create the actual situational config files for the domain:
  a. It expands some fixed replaceable values (e.g. ${env:DOMAIN_UID}).
  b. It expands referenced secrets by reading value from the corresponding mounted secret file (e.g. ${secret:mysecret.mykey}).
  c. It optionally encrypts secrets using offline WLST to encrypt the value - useful for passwords (e.g. ${secret:mysecret.mykey:encrypt}).
  d. It returns expanded situational config files to the operator.
  3. It reports any errors when attempting expansion to the operator.
3. The operator runtime reads the expanded situational config files and/or errors from the introspector, and, if the introspector reports no errors, puts situational config files in a config map named `DOMAIN_UID-weblogic-domain-introspect-cm`, and mounts this config map into the WebLogic Server pods.
4. The startServer.sh script in the WebLogic Server pods copies the expanded situational config files in a location that the WebLogic runtime can find them (specifically the `optconfig` directory in its domain home directory for config.xml overrides, and `optconfig/jdbc`, `optconfig/jms`, or `optconfig/wldf` for module override files).  The script also deletes any situational config files in this directory that do not have corresponding template files in the config map.
5. WebLogic Servers read their overrides from their domain home's 'optconfig' directory.

## References

TBD

