# Oracle WebLogic Server Kubernetes Operator Name Changes

The initial version of the WebLogic Server Kubernetes Operator did not use consistent conventions for many customer visible names.

We addressed this by:
* Standardizing the names of scripts and input files for creating operators and domains
* Standardizing the input parameter names and values
* Standardizing the names of the generated YAML files
* Requiring that the customer create and specify a directory that the operators' and domains' generated YAML files will be stored in

This changes how operators and domains are created, and also changes the generated artifacts for operators and domains.

We're not providing an upgrade tool or backward compatibility with the previous names.  Instead, customers need to use the new script, inputs file, and parameter names to recreate their operators and domains.

This document lists the customer visible naming changes.  Also, the WebLogic Server Kubernetes Operator documentation has been updated.

## Customer Visible Files

### Files for Creating and Deleting Operators and Domains

The following files are used to create the operator and to create and delete domains.

| Previous File Name | New File Name |
| --- | --- |
| `kubernetes/create-domain-job.sh` | `kubernetes/create-weblogic-domain.sh` |
| `kubernetes/create-domain-job-inputs.yaml` | `kubernetes/create-weblogic-domain-inputs.yaml` |
| `kubernetes/create-operator-inputs.yaml` | `kubernetes/create-weblogic-operator-inputs.yaml` |
| `kubernetes/create-weblogic-operator.sh` | same |
| `kubernetes/delete-domain.sh` | `kubernetes/delete-weblogic-operator-resources.sh` |

### Generated YAML Files for Operators and Domains

The create scripts generate a number of YAML files that are used to configure the corresponding Kubernetes artifacts for the operator and the domains.
Typically, customers do not use these YAML files.  However, customers can look at them.  They can also change the operator and domain configuration by editing these files and reapplying them.

#### Directory for the Generated YAML Files

Previously, these files were placed in the `kubernetes` directory (for example, `kubernetes/weblogic-operator.yaml`).  Now, they are placed in per-operator and per-domain directories (because a Kubernetes cluster can have more than one operator and an operator can manage more than one domain).

The customer must create a directory that will parent the per-operator and per-domain directories, and use the `-o` option to pass the name of that directory to the create script, for example:
  `mkdir /scratch/my-user-projects
  create-weblogic-operator.sh -o /scratch/my-user-projects`.
The pathname can be either a full path name or a relative path name.  If it's a relative pathname, then it's relative to the directory of the shell invoking the create script.

The per-operator directory name is:
  `<user project dir from -o>/weblogic-operators/<operator namespace from the input YAML file's namespace property>`.

Similarly, the per-domain directory name is:
  `<user project dir from -o>/weblogic-domains/<domain uid from the input YAML file's domainUid property>`.

#### What If I Mess Up Creating a Domain or Operator And Want To Do It Again?

* Remove the resources that were created for the domain:
  * `kubernetes/delete-weblogic-domain-resources.sh yourDomainUID`
* Remove the resources that were created for the operator:
  * `kubectl delete -f weblogic-operator.yaml`
  * `kubectl delete -f weblogic-operator-security.yaml`
* Either remove the directory that was generated for that operator or domain, or remove the generated YAML files and the copy of the input file from it.
* Make whatever changes you need in your inputs file.
* Re-run the create script.

If you run the create script without cleaning up the previously generated directory, the create script will tell you about the offending files and then exit without creating anything.

#### Location of the Input YAML Files

The create scripts support an `-i` option for specifying the location of the inputs file.  Similar to the `-o` option, the path can be either a full path name or a relative path name.  Relative path names are relative to the directory of the shell invoking the create script.

If `-i` is not specified, `kubernetes/create-weblogic-operator.sh` uses `kubernetes/create-weblogic-operator-inputs.yaml`.

Previously, `kubernetes/create-domain-job.sh` used `kubernetes/create-domain-job-inputs.yaml` as the input file if `-i` was not specified.  This behavior has been changed.  The customer must select a Kubernetes cluster-wide unique ID for the domain and set the `domainUid` property in the inputs file to that value.  This means that the customer must always modify the inputs file.

Also, we do not want the customer to have to change files in the operator's install directory.  Because of this, the `-i` option MUST be specified when calling `kubernetes/create-weblogic-operator.sh`.  The basic flow is:

* Pick a user projects directory, for example, `/scratch/my-user-projects`
* `mkdir /scratch/my-user-projects`
* Pick a unique ID for the domain, for example, `foo.com`
* `cp kubernetes/create-weblogic-domain-inputs.yaml my-inputs.yaml`
* Set the domainUid in `my-inputs.yaml` to `foo.com`
* `kubernetes/create-weblogic-operator.sh -i my-inputs.yaml -o /scratch/my-user-projects`

**Note:** `my-inputs.yaml` will be copied to `/scratch/my-user-projects/weblogic-domains/foo.com/create-weblogic-domain-inputs.yaml`

#### File Names of the Generated YAML Files

The names of several of the generated YAML files have changed.

| Previous File Name | New File Name |
| --- | --- |
| `domain-custom-resource.yaml` | same |
| `domain-job.yaml` | `create-weblogic-domain-job.yaml` |
| `persistent-volume.yaml` | `weblogic-domain-pv.yaml` |
| `persistent-volume-claim.yaml` | `weblogic-domain-pvc.yaml` |
| `rbac.yaml` | `weblogic-operator-security.yaml` |
| `traefik-deployment.yaml` | `weblogic-domain-traefik-${clusterName, lower case}.yaml` |
| `traefik-rbac.yaml` | `weblogic-domain-traefik-security-${clusterName, lower case}.yaml` |
| `weblogic-operator.yaml` | same |

## Input File Contents
Some of the contents of the inputs files have changed:
* Some properties have been renamed
* Some properties that are no longer needed have been removed
* Some of the legal property values have changed
* Some properties that were optional are now required

### create-weblogic-operator-inputs.yaml

#### Property Names

| Previous Property Name | New Property Name |
| --- | --- |
| `image` | `weblogicOperatorImage` |
| `imagePullPolicy` | `weblogicOperatorImagePullPolicy` |
| `imagePullSecretName` | `weblogicOperatorImagePullSecretName` |

#### Property Values

| Previous Property Name | Property Name | Old Property Value | New Property Value |
| --- | --- | --- | --- |
| `externalRestOption` | `externalRestOption` | `none` | `NONE` |
| `externalRestOption` | `externalRestOption` | `custom-cert` | `CUSTOM_CERT` |
| `externalRestOption` | `externalRestOption` | `self-signed-cert` | `SELF_SIGNED_CERT` |

### create-weblogic-domain-inputs.yaml

#### Property Names

| Previous Property Name | New Property Name |
| --- | --- |
| `createDomainScript` | This property has been removed |
| `domainUid` | `domainUID` |
| `imagePullSecretName` | `weblogicImagePullSecretName` |
| `loadBalancerAdminPort` | `loadBalancerDashboardPort` |
| `managedServerCount` | `configuredManagedServerCount` |
| `managedServerStartCount` | `initialManagedServerReplicas` |
| `nfsServer` | `weblogicDomainStorageNFSServer` |
| `persistencePath` | `weblogicDomainStoragePath` |
| `persistenceSize` | `weblogicDomainStorageSize` |
| `persistenceType` | `weblogicDomainStorageType` |
| `persistenceStorageClass` | This property has been removed |
| `persistenceVolumeClaimName` | This property has been removed |
| `persistenceVolumeName` | This property has been removed |
| `secretName` | `weblogicCredentialsSecretName` |

#### Properties That Must be Customized
The following input properties, which used to have default values, now must be uncommented and customized.

| Previous Property Name | New Property Name | Previous Default Value | Notes |
| --- | --- | --- | --- |
| `domainUid` | `domainUID` | `domain1` | Because the domain UID is supposed to be unique across the Kubernetes cluster, the customer must choose one. |
| `persistencePath` | `weblogicDomainStoragePath` | `/scratch/k8s_dir/persistentVolume001` | The customer must select a directory for the domain's storage. |
| `nfsServer` | `weblogicDomainStorageNFSServer` | `nfsServer` | If `weblogicDomainStorageType` is NFS, then the customer must specify the name or IP of the NFS server. |

#### Property Values

| Previous Property Name | New Property Name | Old Property Value | New Property Value |
| --- | --- | --- | --- |
| `loadBalancer` | `loadBalancer` | `none` | `NONE` |
| `loadBalancer` | `loadBalancer` | `traefik` | `TRAEFIK` |
| `persistenceType` | `weblogicDomainStorageType` | `hostPath` | `HOST_PATH` |
| `persistenceType` | `weblogicDomainStorageType` | `nfs` | `NFS` |

## Kubernetes Artifact Names

| Artifact Type | Previous Name | New Name |
| --- | --- | --- |
| persistent volume | `${domainUid}-${persistenceVolume}` or `${persistenceVolume}` | `${domainUID}-weblogic-domain-pv` |
| persistent volume claim | `${domainUid}-${persistenceVolumeClaim}` or `${persistenceVolumeClaim}` | `${domainUID}-weblogic-domain-pvc` |
| storage class name | `${domainUid}` or `${persistenceStorageClass}` | `${domainUID-weblogic-domain-storage-class` |
| job | `domain-${domainUid}-job` | `${domainUID}-create-weblogic-domain-job` |
| container | `domain-job` | `create-weblogic-domain-job` |
| container | `${d.domainUID}-${d.clusterName, lower case}-traefik` | `traefik` |
| config map | `operator-config-map` | `weblogic-operator-cm` |
| config map | `${domainUid}-${clusterName, lower case}-traefik` | `${domainUID}-${clusterName, lower case}-traefik-cm` |
| config map |  `domain-${domainUid}-scripts` | `${domainUID}-create-weblogic-domain-job-cm` |
| config map | `weblogic-domain-config-map` | `weblogic-domain-cm` |
| secret | `operator-secrets` | `weblogic-operator-secrets` |
| port | `rest-https` | `rest` |
| service | `external-weblogic-operator-service` | `external-weblogic-operator-srv` |
| service | `internal-weblogic-operator-service` | `internal-weblogic-operator-srv` |
| volume & mount | `operator-config-volume` | `weblogic-operator-cm-volume` |
| volume & mount | `operator-secrets-volume` | `weblogic-operator-secrets-volume` |
| volume & mount | `config-map-scripts` | `create-weblogic-domain-job-cm-volume` |
| volume & mount | `pv-storage` | `weblogic-domain-storage-volume` |
| volume & mount | `secrets` | `weblogic-credentials-volume` |
| volume & mount | `scripts` | `weblogic-domain-cm-volume` |

**Note:** The input properties for controlling the domain's persistent volume, persistent volume claim, and storage class names have been removed.  These names are now derived from the domain UID.
