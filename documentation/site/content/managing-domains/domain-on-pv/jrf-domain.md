+++
title = "JRF domains"
date = 2023-04-26T16:45:16-05:00
weight = 5
pre = "<b> </b>"
description = "Important information about using JRF domains."
+++

{{< table_of_contents >}}

### Overview

A Java Required Files (JRF) domain consists of those components not included in a WebLogic Server
installation that provide common functionality for Oracle business applications and application
frameworks. They consist of a number of independently developed libraries and applications that are
deployed in a common location.

Typically, a JRF domain is used by Fusion Middleware products. The JRF domain has database requirements.
The database components are created using Repository Creation Utility (RCU); a new RCU schema is created before creating a
JRF-based domain.

### Sample WDT model for JRF domain

For the operator to create a JRF domain, your WDT model must have a `RCUDbInfo` section in `domainInfo`.  This is a sample model
snippet of a typical JRF domain

```yaml
domainInfo:
    RCUDbInfo:
        rcu_prefix: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_prefix@@'
        rcu_schema_password: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_schema_password@@'
        rcu_db_conn_string: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_db_conn_string@@'
        # DBA credentials: required if operator is running the rcu in Domain On PV
        rcu_db_user: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:dba_user@@'
        rcu_admin_password: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:dba_password@@'
```

Refer to [WebLogic Deploy Tooling Connect to a database](https://oracle.github.io/weblogic-deploy-tooling/userguide/database/connect-db/) for more information.

#### Importance of domain home directory backup

A JRF domain has a one-to-one relationship with the RCU schema.  After a domain is created using a particular RCU schema,
that schema _cannot_ be reused by another domain and the same schema _cannot_ be shared across different domains.  Any attempts to
create a new domain using the schema that had already been used, will result in an error.

{{% notice warning %}}
If the domain home is not properly backed up, you potentially can lose existing data if the domain home is corrupted or deleted.
That's because recreating the domain requires dropping the existing RCU schema and creating a new RCU schema. Therefore, backing up the existing domain home should be
the highest priority in your Kubernetes environment.
{{% /notice %}}   

This is especially important for a Domain on PV deployment, where the domain is continually
updated after its initial deployment.  For example, you already deployed new applications, added custom OPSS keystores, added OWSM policies, and such.
The original models used to create the domain will not match the existing state of the domain (the models are not the source of truth)
and, if you use the original models to create the domain again, then you will lose all the updates that you have made. In order to preserve the domain updates, you should restore the domain from a backup copy of the domain home directory and connect to the existing RCU schema from the database backup.

#### Download OPSS wallet and store in a Kubernetes Secret

After the domain is created, the operator will automatically export the OPSS wallet and
store it in an introspector ConfigMap; the name of the ConfigMap follows the pattern `<domain uid>-weblogic-domain-introspect-cm`
with the key `ewallet.p12`.  Export this file and put it in a safe place. The operator provides a
[OPSS wallet utility](https://orahub.oci.oraclecorp.com/weblogic-cloud/weblogic-kubernetes-operator/-/blob/main/kubernetes/samples/scripts/domain-lifecycle/opss-wallet.sh)
for extracting this file and storing it in a Kubernetes `walletFileSecret`.  You should also save the wallet file in a safely backed-up location, outside of Kubernetes.

For example,

```
$ opss-wallet.sh -n sample-ns -d sample-domain1 -s -r -wf /tmp/ewallet.p12 -ws jrf-wallet-file-secret
```

#### Disaster recovery for Domain on PV deployment

When a JRF domain is created, an OPSS wallet is stored in the file system where the domain home resides.
This specific wallet key can be exported and used to create a new domain. There is no way to reuse the original RCU schema without this specific wallet key.
Therefore, for disaster recovery, **you should back up this OPSS wallet**.

After the operator creates the JRF domain, it stores the OPSS wallet in a ConfigMap. See [Download and save the OPSS wallet](#download-opss-wallet-and-store-in-a-kubernetes-secret).

Oracle recommends that you save the OPSS wallet file in a safe, backed-up location __immediately__ after an initial JRF domain is created. In addition, you should make sure to store the wallet in a Kubernetes Secret in the same namespace. This will allow the secret to be available when the domain needs to be recovered in a disaster scenario or if the domain directory gets corrupted.

In the domain resource YAML file, you can provide two secrets in the `opss` section under `configuration.initializeDomainOnPV.domain`:

```
     ...
     #  For domain on PV, opss settings are under `configuration.initializeDomainOnPV.domain.opss`
     #  DO NOT specify it under `configuration`
     #
     configuration:
        initializeDomainOnPV:
        ...
           domain:
              createIfNotExists: Domain
              domainType: JRF
              ...
              opss:
               walletFileSecret: jrf-wallet-file-secret
               walletPasswordSecret: sample-domain1-opss-wallet-password-secret
```

`walletPasswordSecret` is required during initial deployment.  This secret contains the password used to
encrypt the exported OPSS wallet.  You can create a Kubernetes Secret with the key `walletPassword` containing the password.
The password must have a minimum length of eight characters and contain alphabetic characters combined with numbers or special characters.

If the domain home directory is corrupted, and you have a recent backup of the domain home directory, then perform the following steps to recover the domain.

1. Restore the domain home directory from the backup copy.
2. Update the `restartVersion` of the domain resource to restart the domain. For example,

   ```
   $ kubectl -n sample-ns patch domain sample-domain1 --type='JSON' -p='[ { "op" : "replace", "path" : "/spec/restartVersion", "value" : "15" }]'
   ```
3. After the domain is restarted, check the WebLogic domain configuration to ensure that it has the latest changes.
   **NOTE**: If you made any changes that are persisted in the domain home directory after your last backup, you must reapply those changes to the domain home directory.
   However, because the operator will reconnect to the same RCU schema, the data stored in the OPSS, MDS, or OWSM tables will be current.

4. Reapply any domain configuration changes persisted to the domain home directory, such as
   data source connections, JMS destinations, or new application EAR deployments, after your last backup. To make these changes, use WLST, the WebLogic Server Administration Console, or Enterprise Manager.

In the rare scenario where the domain home directory is corrupted, and you do **not** have a recent backup of the domain home directory, or if the backup copy is also corrupted, then you can recreate the domain from the WDT model files without losing any RCU schema data.

1. Delete the existing domain home directory on PV.
2. Specify the `walletFileSecret` - A Kubernetes Secret with the key `walletFile` containing the exported OPSS wallet
   file `ewallet.p12` you created earlier.

   For example,
   ```
   $ kubectl -n sample-ns patch domain sample-domain1 --type='JSON' -p='[ { "op" : "add", "path" : "/spec/configuration/initializeDomainOnPV/domain/opss/walletFileSecret", "value" : "jrf-wallet-file-secret" }]'
   ```

3. Update the `introspectVersion` in the domain resource.

   ```
   $ kubectl -n sample-ns patch domain sample-domain1 --type='JSON' -p='[ { "op" : "replace", "path" : "/spec/intropsectVersion", "value" : "15" }]'
   ```

4. The operator will then create a new domain from the existing WDT models and reuse the original RCU schema.

   **NOTE**:
   All the updates made to the domain after the initial deployment will **not** be available in the recovered domain.
   However, this allows you to access the original RCU schema database without losing all its data.

5. Apply all the domain configuration changes persisted to the domain home file system, such as data source connections, JMS destinations, or new application EAR deployments, that are not in the WDT model files. These are the changes you have made _after_ the initial domain deployment.

6. Update the `restartVersion` of the domain resource to restart the domain.

   ```
   $ kubectl -n sample-ns patch domain sample-domain1 --type='JSON' -p='[ { "op" : "replace", "path" : "/spec/restartVersion", "value" : "15" }]'
   ```

#### Disaster recovery for Model in Image domains

**NOTE**: JRF support in Model in Image domains has been deprecated since operator version 4.1.0; use the Domain on PV [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) instead.

When a JRF domain is created, an OPSS wallet is stored in the file system where the domain home resides.
This specific wallet key can be exported and used to create a new domain. There is no way to reuse the original RCU schema without this specific wallet key.
Therefore, for disaster recovery, **you should back up this OPSS wallet**.

After the operator creates the JRF domain, it stores the OPSS wallet in a ConfigMap. See [Download and save the OPSS wallet](#download-opss-wallet-and-store-in-a-kubernetes-secret).

In the domain resource YAML file, you can provide two secrets in the `opss` section under `configuration`:

```
     configuration:
        model:
           ...
        opss:
           walletFileSecret: jrf-wallet-file-secret
           walletPasswordSecret: sample-domain1-opss-wallet-password-secret
```

`walletPasswordSecret` is required during initial deployment.  This secret contains the password used to
encrypt the exported OPSS wallet.  You can create a Kubernetes Secret with the key `walletPassword` containing the password.
The password must have a minimum length of eight characters and contain alphabetic characters combined with numbers or special characters.

In case the domain home directory is corrupted and you need to recreate the domain, and reuse the existing RCU schema:

1. Specify the `walletFileSecret` - A Kubernetes Secret with the key `walletFile` containing the exported OPSS wallet
   file `ewallet.p12` that you have created earlier.

   For example,
   ```
     $ kubectl -n sample-ns patch domain sample-domain1 --type='JSON' -p='[ { "op" : "add", "path" : "/spec/configuration/initializeDomainOnPV/domain/opss/walletFileSecret", "value" : "jrf-wallet-file-secret" }]'
   ```

2. Update the `introspectVersion` in the domain resource.

   ```
   $ kubectl -n sample-ns patch domain sample-domain1 --type='JSON' -p='[ { "op" : "replace", "path" : "/spec/intropsectVersion", "value" : "15" }]'
   ```

The operator will create a new domain from the existing WDT models and reuse the original RCU schema.
