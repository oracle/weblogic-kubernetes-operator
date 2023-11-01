+++
title = "Move MII/JRF domains to PV"
date = 2020-03-11T16:45:16-05:00
weight = 60
pre = "<b> </b>"
description = "Moving an MII/JRF domain to a persistent volume."
+++


FMW/JRF domains using the Model in Image domain home source type has been deprecated since WebLogic Kubernetes Operator 4.1.  We recommend moving your domain home to Domain on Persistent Volume (Domain on PV). For more information, see [Domain On Persistent Volume]({{< relref "/managing-domains/domain-on-pv/overview.md" >}}).

If you cannot move the domain to a persistent volume right now, you can use the following procedure.

1. Back up the OPSS wallet and save it in a secret if you have not already done it.

   The operator provides a helper script, the [OPSS wallet utility](https://orahub.oci.oraclecorp.com/weblogic-cloud/weblogic-kubernetes-operator/-/blob/main/kubernetes/samples/scripts/domain-lifecycle/opss-wallet.sh), for extracting the wallet file and storing it in a Kubernetes `walletFileSecret`. In addition, you should save the wallet file in a safely backed-up location, outside of Kubernetes. For example, the following command saves the OPSS wallet for the `sample-domain1` domain in the `sample-ns` namespace to a file named `ewallet.p12` in the `/tmp` directory and also stores it in the wallet secret named `sample-domain1-opss-walletfile-secret`.

   ```
   $ opss-wallet.sh -n sample-ns -d sample-domain1 -s -r -wf /tmp/ewallet.p12 -ws sample-domain1-opss-walletfile-secret
   ```

2. Follow the steps in [Upgrade managed domains]({{< relref "/managing-domains/major-weblogic-version-upgrade/_index.md" >}}).
3. If you are using an auxiliary image in your MII/JRF domain, then it will be used as a domain creation image. If you are _not_ using an auxiliary image in your MII/JRF domain, then create a [Domain creation image]({{< relref "/managing-domains/domain-on-pv/domain-creation-images.md" >}}).
4. You can delete the old domain resource YAML file by using this command: `$ kubectl delete -f <original domain resource YAML>`.
5. Then, create a new domain resource YAML file.  You should have at least the following changes:


   ```
   # Change type to PersistentVolume
   domainHomeSourceType: PersistentVolume
   image: <Fusion Middleware Infrastructure base image>
   ...
   serverPod:
       ...
       # specify the volume and volume mount information

       volumes:
       - name: weblogic-domain-storage-volume
         persistentVolumeClaim:
            claimName: sample-domain1-pvc-rwm1
       volumeMounts:
       - mountPath: /share
         name: weblogic-domain-storage-volume

     # specify a new configuration section, remove the old configuration section.

     configuration:

       # secrets that are referenced by model yaml macros
       # sample-domain1-rcu-access is used for JRF domains
       secrets: [ sample-domain1-rcu-access ]

       initializeDomainOnPV:
         persistentVolumeClaim:
           metadata:
               name: sample-domain1-pvc-rwm1
           spec:
               storageClassName: my-storage-class
               resources:
                   requests:
                       storage: 10Gi
         domain:
             createIfNotExists: Domain
             domainCreationImages:
             - image: 'myaux:v6'
             domainType: JRF
             domainCreationConfigMap: sample-domain1-wdt-config-map
             opss:
               # Make sure you have already saved the wallet file secret. This allows the domain to use
               # an existing JRF database schemas.
               walletFileSecret: sample-domain1-opss-walletfile-secret
               walletPasswordSecret: sample-domain1-opss-wallet-password-secret
   ```

6. Deploy the domain. If it is successful, then the domain has been migrated to a persistent volume.
