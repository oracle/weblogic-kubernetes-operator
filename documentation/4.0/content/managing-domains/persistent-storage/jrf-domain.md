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
the database components are created using `Repository Creation Utility (RCU)`.   

### Importance of domain home directory backup.

A JRF domain has a one to one relationship with a `RCU schema`,  once a domain is created using a particular `RCU schema`,  that particular schema
cannot be reused by another new domain and the same schema cannot be shared across different domain.  Any attempts to
create a new domain using a schema that had already been used will result in error.

Since after the domain is created, and the existing schema cannot be reused, it is critical to backup the domain home directory and database.
If the domain home is not properly backed up, and is corrupted or deleted, then you may have to drop the existing `RCU schema` and recreating a new `RCU schema` before
creating the domain again, this applies potentially losing all existing data.  Backing up the existing domain home therefore should be
the highest priority in your Kubernetes environment.   

This is especially true for `Domain On PV` deployment, where the domain is continually 
updated by your normal operations.  For example, applications deployed, custom `OPSS` keystores added, new `OWSM` policies added etc...  
The original models used to create the domain will not match the existing state of the domain (the models are not the source of truth) 
and if you use the original models to create the domain again, you will lose all the updates you have done.

### Specifying OPSS encryption key information.

When a `JRF` domain is created, an encryption key `OPSS wallet` is stored in the file system where the domain home resides.
This specific wallet key can be exported and used to create a new domain but allowing it to connect to existing `RCU schema`, 
without this specific wallet key, there is no way to reconnect back to that original `RCU schema`.  Therefore, it is a good
idea to save off this wallet key as a last resort disaster recovery option.

Operator provides a way as a last resort to recover from a disaster situation where the domain home directory including its backups are destroyed.

```
     domain:
          createIfNotExists: domain
          domainType: JRF
          ...
          opss:
           walletFileSecret: jrf-wallet-file-secret
           walletPasswordSecret: sample-domain1-opss-wallet-password-secret
```

You can provide two secrets in the `opss` section.  

`walletPasswordSecret` is required during initial deployment.  This secret contains the password used to
encrypt the exported `OPSS wallet`.  You can create a Kubernetes secret with the key `walletPassword` containing the password. 
The password must be at least 8 alphanumeric characters with one special character.

Once the domain is created, the Operator will automatically export the `OPSS wallet` and 
stored it in the introspector configmap, the name follows the patter `<domain uid>-weblogic-domain-introspect-cm` 
with key `ewallet.p12`.  You can export this file and save it in a safe place, we provide a utility 
(TODO: link here) for extracting this file and store it in a Kubernetes secret.

For disaster recovery, in case you need to recreate the domain and reconnect with existing `RCU schema`:

- delete the existing domain home directory on PV
- specify the `walletFileSecret` - A kubernetes secret with key `walletFile` containing the exported `OPSS wallet`
 file.
- update the `introspectVersion` in the domain resource.

The Operator will create a new domain but connecting back to the original `RCU schema`. This will not create a domain with all the changes made after the initial deployment but at least your will not lose all the data in the `RCU` database.






