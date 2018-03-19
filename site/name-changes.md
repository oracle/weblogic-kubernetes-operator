# Oracle WebLogic Server Kubernetes Operator Name Changes

The initial version of the WebLogic Server Kubernetes Operator did not use consistent conventions for many customer visible names.

We've recently addressed this by:
* standardizing the names of scripts and input files for creating operators and domains
* standardizing the input parameter names and values
* standardizing the names of the generated yaml files
* requiring that the custom create and specify a directory that the operators' and domains' generated yaml files will be stored in

This changes how operators and domains are created, and also changes the generated artifacts for operators and domains.

We're not providing an upgrade tool or backwards compatibility with the previous names.  Instead, customers need to use the new script, inputs file and parameter names to recreate their operators and domains.

This document lists the customer visible naming changes.  Also, the WebLogic Server Kubernetes Operator documentation has been updated.

## Customer Visible Files

### Files for Creating Operators and Domains

The following files are used to create the operator and to create domains.

| Previous File Name | New File Name |
| --- | --- |
| kubernetes/create-domain-job.sh | kubernetes/create-weblogic-domain.sh |
| kubernetes/create-domain-job-inputs.yaml | kubernetes/create-weblogic-domain-inputs.yaml |
| kubernetes/create-operator-inputs.yaml | kubernetes/create-weblogic-operator-inputs.yaml |
| kubernetes/create-weblogic-operator.sh | same |

### Generated YAML Files for Operators and Domains

The create scripts generate a number of yaml files that are used to configure the corresponding Kubernetes artifacts for the operator and the domains.
Normally, customers do not use these yaml files.  However, customers can look at them.  They can also change the operator and domain configuration by editing these files and reapplying them.

#### Directory for the Generated YAML Files

Previously, these files were placed in the kubernetes directory (e.g. kubernetes/weblogic-operator.yaml).  Now, they are placed in per-operator and per-domain directories (since a Kubernetes cluster can have more than one operator and an operator can manage more than one domain).

The customer must create a directory that will parent the per-operator and per-domain directories, and use the -o option to pass the name of that directory to the create script, for example:
  mkdir /scratch/my-user-projects
  create-weblogic-operator.sh -o /scratch/my-user-projects
The pathname can either be a full path name, or a relative path name.  If it's a relative pathname, then it's relative to the directory of the shell invoking the create script.

The per-operator directory name is:
  <user project dir from -o>/weblogic-operators/<operator namespace from the input yaml file's namespace property>

Similarly, the per-domain directory name is:
  <user project dir from -o>/weblogic-domains/<domain uid from the input yaml file's domainUid property>

#### What If I Mess Up Creating a Domain or Operator And Want To Do It Again?

* TBD - destroy the operator / domain - I don't think we provide scripts for this yet, but will soon
* either remove the directory that was generated for that operator / domain, or remove the generated yaml files and the copy of the input file from it
* make whatever changes you need in your inputs file
* re-run the create script

If you run the create script without cleaning up the previously generated directory, the create script will tell you about the offending files and then exit without creating anything.

#### Location of the Input YAML Files

The create scripts support a -i option for specifying the location of the inputs file.  Similar to the -o option, the path can either be a full path name or a relative path name.  Relative path names are relative to the directory of the shell invoking the create script.

If -i is not specified, kubernetes/create-weblogic-operator.sh uses kubernetes/create-weblogic-operator-inputs.yaml.

Previously, kubernetes/create-domain-job.sh used kubernetes/create-domain-job-inputs.yaml as the input file if -i was not specified.  This behavior has been changed.  The customer must select a world wide unique id for the domain and set the domainUid property in the inputs file to that value.  This means that the customer must always modify the inputs file.

Also, we do not want the customer to have to change files in the weblogic operator's install directory.  Because of this, the -i option MUST be specified when calling kubernetes/create-weblogic-operator.sh.  The basic flow is:

* pick a user projects directory, e.g. /scratch/my-user-projects
* mkdir /scratch/my-user-projects
* pick a unique id for the domain, e.g. foo.com
* cp kubernetes/create-weblogic-domain-inputs.yaml my-inputs.yaml
* set the domainUid in my-inputs.yaml to foo.com
* kubernetes/create-weblogic-operator.sh -i my-inputs.yaml -o /scratch/my-user-projects

Note: my-inputs.yaml will be copied to /scratch/my-user-projects/weblogic-domains/foo.com/create-weblogic-domain-inputs.yaml

#### File Names of the Generated YAML File

The names of several of the generated YAML files have changed.

| Previous File Name | New File Name |
| --- | --- |
| domain-custom-resource.yaml | same |
| domain-job.yaml | create-weblogic-domain-job.yaml |
| persistent-volume.yaml | weblogic-domain-persistent-volume.yaml |
| persistent-volume-claim.yaml | weblogic-domain-persistent-volume-claim.yaml |
| rbac.yaml | weblogic-operator-security.yaml |
| traefik-deployment.yaml | traefik.yaml |
| traefik-rbac.yaml | traefik-security.yaml |
| weblogic-operator.yaml | same |

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
| image | weblogicOperatorImage |
| imagePullPolicy | weblogicOperatorImagePullPolicy |
| imagePullSecretName | weblogicOperatorImagePullSecretName |

#### Property Values

| Previous Property Name | Property Name | Old Property Value | New Property Value |
| --- | --- | --- | --- |
| externalRestOption | externalRestOption | none | NONE |
| externalRestOption | externalRestOption | custom-cert | CUSTOM_CERT |
| externalRestOption | externalRestOption | self-signed-cert | SELF_SIGNED_CERT |

### create-weblogic-domain-inputs.yaml

#### Property Names

| Previous Property Name | New Property Name |
| --- | --- |
| createDomainScript | This property has been removed |
| domainUid | domainUID |
| imagePullSecretName | weblogicImagePullSecretName |
| loadBalancerAdminPort | loadBalancerDashboardPort |
| managedServerCount | configuredManagedServerCount |
| managedServerStartCount | initialManagedServerReplicas |
| nfsServer | weblogicDomainStorageNFSServer |
| persistencePath | weblogicDomainStoragePath |
| persistenceSize | weblogicDomainStorageSize |
| persistenceType | weblogicDomainStorageType |
| persistenceStorageClass | This property has been removed |
| persistenceVolumeClaimName | This property has been removed |
| persistenceVolumeName | This property has been removed |
| secretName | weblogicCredentialsSecretName |

#### Properties that must be customized
The following input properties, which used to have default values, now must be uncommented and customized:
| Previous Property Name | New Property Name | Previous Default Value | Notes |
| --- | --- | --- | --- |
| domainUid | domainUID | domain1 | Since the domain UID is supposed to be globally unique, the customer must choose one. |
| persistencePath | weblogicDomainStoragePath | /scratch/k8s_dir/persistentVolume001 | The customer must select a directory for the domain's storage. |
| nfsServer | weblogicDomainStorageNFSServer | nfsServer | If weblogicDomainStorageType is NFS, then the customer must specify the name or IP of the NFS server. |

#### Property Values

| Previous Property Name | New Property Name | Old Property Value | New Property Value |
| --- | --- | --- | --- |
| loadBalancer | loadBalancer | none | NONE |
| loadBalancer | loadBalancer | traefik | TRAEFIK |
| persistenceType | weblogicDomainStorageType | hostPath | HOST_PATH |
| persistenceType | weblogicDomainStorateType | nfs | NFS |

