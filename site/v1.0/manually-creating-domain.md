> **WARNING** This documentation is for version 1.0 of the operator.  To view documenation for the current release, [please click here](/site).

# Manually creating a WebLogic domain

**PLEASE NOTE**: This page is a work in progress. We have some rough notes here and are working on writing better documentation for how to set up this procedure.

If you are creating the domain manually, for example, using a WLST script, the domain must be configured to meet these requirements:

*	Domain directory should be in `/shared/domain`.
*	Applications directory should be in `/shared/applications`.
*	Logs should be placed in `/shared/logs`.
*	Any persistent stores should be placed in `/shared/stores`.

## Use the scripts to create the sample YAML files

The `create-weblogic-domain.sh` script can be executed with the `-g` option, which will cause it to generate the YAML files but take no action at all against the Kubernetes environment.  This is a useful way to create the sample YAML files needed to manually create a domain.

First, make a copy of `create-weblogic-domain-inputs.yaml` and customize it.

Next, choose and create a directory that generated operator-related files will be stored in, for example, `/path/to/weblogic-operator-output-directory`.

Then, execute the script, pointing it at your inputs file and output directory:

```
./create-weblogic-domain.sh –g \
  –i create-weblogic-domain-inputs.yaml \
  -o /path/to/weblogic-operator-output-directory
```

The following YAML files will be generated in the `/path/to/weblogic-operator-output-directory/weblogic-domains/<domainUID>` directory:

*	`weblogic-domain-pv.yaml` can be customized and used to create the persistent volume for this domain.
*	`weblogic-domain-pvc.yaml` can be customized and used to create the persistent volume claim for this domain.
*	`create-weblogic-domain-job.yaml` can be ignored when creating the domain manually.
*	`domain-custom-resource.yaml` can be customized and used to create the domain custom resource.

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
*	The access mode is Read/Write Many. You must use a provider that supports Read/Write Many.
*	The contents of the volume are retained across restarts of the Kubernetes environment.

## Creating the persistent volume

To create the persistent volume, issue the following command:

```
kubectl create –f weblogic-domain-pv.yaml
```

To verify the persistent volume was created, use this command:

```
kubectl describe pv PV_NAME
```

Replace `PV_NAME` with the name of the persistent volume.

## Preparing to create the persistent volume claim

The file, `weblogic-domain-pvc.yaml`, contains a template to claim a portion of the persistent volume storage. The customizable items are:

*	Persistent volume claim name
*	Namespace name
*	Storage class name. This value is used to match a persistent volume with the same storage class.
*	The amount of storage being claimed from the persistent volume.
*	The access mode is Read/Write Many.

## Creating the persistent volume claim

To create the persistent volume claim, issue the following command:

```
kubectl create –f weblogic-domain-pvc.yaml
```

To verify the persistent volume was created, use this command:

```
kubectl describe pvc PVC_NAME
```

Replace `PVC_NAME` with the name of the persistent volume claim.

## Preparing to create the domain custom resource

The file, `domain-custom-resource.yaml`, contains a template to create the domain custom resource.  The customizable items are:

*	Domain name
*	Namespace name
*	Administration Server name
*	Administration Server administration port

## Creating the domain custom resource

To create the domain custom resource, issue the following command:

```
kubectl create –f domain-custom-resource.yaml
```

To verify that the domain custom resource was created, use this command:

```
kubectl describe domain DOMAINUID –n NAMESPACE
```

Replace `DOMAINUID` with the name of the domain’s domainUID and `NAMESPACE` with the namespace the domain was created in.
