# Manually creating a WebLogic domain

**ATTENTION EARLY ACCESS USERS** This page is not ready for general consumption yet, we have some rough notes in here, we are working on writing better doc for how to set up this integration.

If creating the domain manually, using a WLST script for example, the domain must be configured to meet these requirements:

*	Domain directory should be in `/shared/domain`.
*	Applications directory should be in `/shared/applications`.
*	Logs should be placed in `/shared/logs`.
*	Any persistent stores should be placed in `/shared/stores`.

## Use the scripts to create the sample YAML files

The `create-domain-job.sh` script described in the previous section can be executed with the “-g” option, which will cause it to generate the YAML files but take no action at all against the Kubernetes environment.  This is a useful way to create the sample YAML files needed to manually create a domain.  Execute the script as follows:

```
./create-domain-job.sh –i create-domain-job-inputs.yaml –g
```

The following YAML files will be generated in the current directory:

*	`persistent-volume.yaml` can be customized and used to create the persistent volume for this domain.
*	`persistent-volume-claim.yaml` can be customized and used to create the persistent volume claim for this domain.
*	`domain-job.yaml` can be ignored when creating the domain manually.
*	`domain-custom-resource.yaml` can be customized and used to create the domain custom resource.

## Preparing to create a persistent volume

The WebLogic domain will be shared within the Kubernetes environment using a persistent volume. Each WebLogic domain must have its own persistent volume.

The persistent volume will be mounted as `/shared` within every container that is hosting this WebLogic domain in the Kubernetes environment and will have the following structure:

*	`/shared/domain` contains domain directories
*	`/shared/applications` contains application directories
*	`/shared/logs` contains log directories
*	`/shared/stores` contains persistent stores

The file `persistent-volume.yaml` contains a template to create a persistent volume. The customizable items are listed below:

*	Persistent volume name
*	Storage class name
*	The amount of storage to allocate
*	The physical location of the persistent volume (edit). Prior to creating the persistent volume you need to ensure this location exists and has read/write/execute permission set for the account that Kubernetes is running from.
*	The access mode is Read/Write Many. You must use a provider that supports Read/Write Many.
*	The contents of the volume are retained across restarts of the Kubernetes environment.

## Creating the persistent volume

To create the persistent volume issue the following command:

```
kubectl create –f persistent-volume.yaml
```

To verify the persistent volume was created, use this command:

```
kubectl describe pv PV_NAME
```

Replace `PV_NAME` with the name of the persistent volume.

## Preparing to create the persistent volume claim

The file `persistent-volume-claim.yaml` contains a template to claim a portion of the persistent volume storage. The customizable items are listed below:

*	Persistent volume claim name
*	Namespace name
*	Storage class name. This value is used to match a persistent volume with the same storage class.
*	The amount of storage being claimed from the persistent volume.
*	The access mode is Read/Write Many.

## Creating the persistent volume claim

To create the persistent volume claim issue the following command:

```
kubectl create –f persistent-volume-claim.yaml
```

To verify the persistent volume was created, use this command:

```
kubectl describe pvc PVC_NAME
```

Replace `PVC_NAME` with the name of the persistent volume claim.

## Preparing to create the domain custom resource

The file `domain-custom-resource.yaml` contains a template to create the domain custom resource.  The customizable items are listed below:

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
