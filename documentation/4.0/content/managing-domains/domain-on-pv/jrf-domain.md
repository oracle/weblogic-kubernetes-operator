+++
title = "JRF domain"
date = 2023-04-26T16:45:16-05:00
weight = 25
pre = "<b> </b>"
description = "JRF domain"
+++

{{< table_of_contents >}}

### JRF domain

`Java Required Files (JRF)` domain consists of those components not included in the WebLogic Server 
installation that provide common functionality for Oracle business applications and application 
frameworks. It consists of a number of independently developed libraries and applications that are
deployed into a common location.

Typically, JRF domain is used by Fusion Middleware products. The JRF domain has a database requirements,
the database components are created using `Repository Creation Utility (RCU)`.  A new `RCU schema` is created before creating a
`JRF` based domain.

### Importance of domain home directory backup.

A `JRF` domain has a one-to-one relationship with the `RCU schema`,  once a domain is created using a particular `RCU schema`,  
that particular schema cannot be reused by another domain and the same schema cannot be shared across different domain.  Any attempts to
create a new domain using the schema that had already been used will result in an error.

Since after the domain is created, and the existing schema cannot be reused, it is critical to back up the domain home directory and database.
If the domain home is not properly backed up, and is corrupted or deleted, then you may have to drop the existing `RCU schema` 
and recreating a new `RCU schema` before creating the domain again, this applies potentially losing all existing data.  Backing up the existing domain home therefore should be
the highest priority in your Kubernetes environment.   

This is especially important for `Domain On PV` deployment, where the domain is continually 
updated after initial deployment.  For example, deployed new applications, added custom `OPSS` keystores, added `OWSM` policies etc...  
The original models used to create the domain will not match the existing state of the domain (the models are not the source of truth) 
and if you use the original models to create the domain again, you will lose all the updates that you have done.

### Disaster recovery when the domain home directory is destroyed

When a `JRF` domain is created, an encryption key `OPSS wallet` is stored in the file system where the domain home resides.
This specific wallet key can be exported and used to create a new domain but allowing it to connect to existing `RCU schema`, 
without this specific wallet key, there is no way to reconnect to the original `RCU schema`.  Therefore, it is a good
idea to back up this encryption key as a last resort for disaster recovery.

When a `JRF` domain on persistent volume is created, the operator stores the `OPSS wallet` in a configmap
, you can extract and back up in a safe location after the domain is created.

In the domain resource YAML, you can provide two secrets in the `opss` section:

```
     domain:
          createIfNotExists: domain
          domainType: JRF
          ...
          opss:
           walletFileSecret: jrf-wallet-file-secret
           walletPasswordSecret: sample-domain1-opss-wallet-password-secret
```

`walletPasswordSecret` is required during initial deployment.  This secret contains the password used to
encrypt the exported `OPSS wallet`.  You can create a Kubernetes secret with the key `walletPassword` containing the password. 
The password must be at least 8 alphanumeric characters with one special character.

Once the domain is created, the operator will automatically export the `OPSS wallet` and 
store it in an introspector configmap, the name of the configmap follows the pattern `<domain uid>-weblogic-domain-introspect-cm` 
with key `ewallet.p12`.  You can export this file and save it in a safe place, we provide a utility 
 [OPSS wallet utility](https://orahub.oci.oraclecorp.com/weblogic-cloud/weblogic-kubernetes-operator/-/blob/main/kubernetes/samples/scripts/domain-lifecycle/opss-wallet.sh)
for extracting this file and store it in a Kubernetes secret `walletFileSecret`.

For disaster recovery, in case you need to recreate the domain and reconnect with existing `RCU schema`:

- delete the existing domain home directory on PV
- specify the `walletFileSecret` - A kubernetes secret with key `walletFile` containing the exported `OPSS wallet`
 file `ewallet.p12`
- update the `introspectVersion` in the domain resource.

The operator will create a new domain but connecting back to the original `RCU schema`. This will not create a domain 
with all the updates to the domain made after the initial deployment, but you will be able to access the original `RCU schema` database without
losing all its data.






