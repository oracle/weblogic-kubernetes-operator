# Domain resource

In this guide, we outline how to set up and configure your own [domain resource](../docs/domains/Domain.md) which can be used to configure your WLS domain. Then, you can use the domain resource to start the Kubernetes artifacts of the corresponding domain.

Swagger documentation is available [here](https://oracle.github.io/weblogic-kubernetes-operator/domains/index.html).

## Prerequisites

The following prerequisites must be fulfilled before proceeding with the creation of the resource.
* Make sure the WebLogic operator is running.
* Create a Kubernetes namespace for the domain resource unless the intention is to use the default namespace.
* Create the Kubernetes secrets `username` and `password` of the admin account in the same Kubernetes namespace as the domain resource.

# YAML files

Domain resources are defined using the domain resource YAML files. For each WLS domain you want to create and configure, you should create one domain resource YAML file and apply it. In the example below, you will find a domain resource YAML file template that you can use as a basis. Copy the template and override the default settings so that it matches all the WLS domain parameters that define your WLS domain.

For sample YAML templates, see the [Domain resource example](../kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/README.md).

# Kubernetes resources

After you have written your YAML files, please use them to create your WLS domain artifacts using the `kubectl apply -f` command.

```
$ kubectl apply -f domain-resource.yaml
```

## Verify the results

To confirm that the domain resource was created, use this command:

```
$ kubectl describe domain [domain name] -n [namespace]
```
