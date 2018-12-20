# Manually creating a WebLogic domain

If you are creating the domain manually, for example, using a WLST script, the domain must be configured to meet these requirements:

*	Domain directory should be in `/shared/domain`.
*	Applications directory should be in `/shared/applications`.
*	Logs should be placed in `/shared/logs`.
*	Any persistent stores should be placed in `/shared/stores`.

## Use the scripts to create the sample YAML files

The `create-weblogic-domain.sh` script can be executed without the `-e` option, which will cause it to just generate the YAML files but take no action at all against the Kubernetes environment.  This is a useful way to create the sample YAML files needed to manually create a domain.

First, make a copy of `create-weblogic-domain-inputs.yaml` and customize it.

Next, choose and create a directory that generated operator-related files will be stored in, for example, `/path/to/weblogic-operator-output-directory`.

Then, execute the script, pointing it at your inputs file and output directory:

```
$ ./create-weblogic-domain.sh \
  –i create-weblogic-domain-inputs.yaml \
  -o /path/to/weblogic-operator-output-directory
```

The following YAML files will be generated in the `/path/to/weblogic-operator-output-directory/weblogic-domains/<domainUID>` directory:

*	`domain.yaml` can be customized and used to create the domain custom resource.
*	`create-weblogic-domain-job.yaml` can be ignored when creating the domain manually.
*   `delete-domain-job.yaml` can be used to delete a domain, and can also be ignored when creating the domain manually.

## Preparing to create a persistent volume

The WebLogic domain will be shared within the Kubernetes environment using a persistent volume. Each WebLogic domain must have its own persistent volume.

The persistent volume will be mounted as `/shared` within every container that is hosting this WebLogic domain in the Kubernetes environment and will have the following structure:

*	`/shared/domain` contains domain directories
*	`/shared/applications` contains application directories
*	`/shared/logs` contains log directories
*	`/shared/stores` contains persistent stores

The file `weblogic-domain-pv.yaml` contains a template to create a persistent volume. The customizable items are listed below:

*	Persistent volume name
*	Storage class name
*	The amount of storage to allocate
*	The physical location of the persistent volume (edit). Prior to creating the persistent volume, you need to ensure this location exists and has read/write/execute permissions set for the account that Kubernetes is running from.
*	The access mode is Read/Write/Many. You must use a provider that supports Read/Write/Many.
*	The contents of the volume are retained across restarts of the Kubernetes environment.

## Creating the persistent volume

The [sample](/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/README.md) dmonstrates how to create
the persistent volume and persistent volume claim.  These must be created before continuing. 

To verify the persistent volume was created, use this command:

```
$ kubectl describe pv PV_NAME
```

Replace `PV_NAME` with the name of the persistent volume.

## Preparing to create the persistent volume claim

The [sample](/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/README.md) dmonstrates how to create
the persistent volume and persistent volume claim.  These must be created before continuing. 
The customizable items are:

*	Persistent volume claim name
*	Namespace name
*	Storage class name. This value is used to match a persistent volume with the same storage class.
*	The amount of storage being claimed from the persistent volume.
*	The access mode is Read/Write/Many.

## Creating the persistent volume claim

To create the persistent volume claim, issue the following command:

```
$ kubectl create –f weblogic-domain-pvc.yaml
```

To verify the persistent volume was created, use this command:

```
$ kubectl describe pvc PVC_NAME
```

Replace `PVC_NAME` with the name of the persistent volume claim.

## Preparing to create the domain resource

The file, `domain.yaml`, contains a template to create the domain resource.  The customizable items are:

*	Domain name
*	Namespace name
*	Administration Server name
*	Administration Server administration port

## Creating the domain resource

To create the domain resource, issue the following command:

```
$ kubectl create –f domain.yaml
```

To verify that the domain resource was created, use this command:

```
$ kubectl describe domain DOMAINUID –n NAMESPACE
```

Replace `DOMAINUID` with the name of the domain’s domainUID and `NAMESPACE` with the namespace the domain was created in.
