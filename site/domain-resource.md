
# Domain Resource

In this guide, we outline how to set up and configure your own domain resource which can be used to configure your WLS domain. The domain resource which can then be used to start the Kubernetes artifacts of the corresponding domain. 

## Prerequisites

The following prerequisites must be fulfilled before proceeding with the creation of the resource.
* Make sure the WebLogic Operator is running.
* Create a Kubernetes namespace for the domain custom resource unless the intention is to use the default namespace.
* Create the Kubernetes secrets `username` and `password` of the admin account in the same Kubernetes namespace as the domain custom resource.

# YAML files

Domain resources are defined via Domain Custom Resource YAML files. For each WLS domain you want to create and configure, you should create one Domain Custom Resource YAML file and apply it. In the example below, you will find a Domain Custom Resource YAML file template that you can use as a basis. Copy the template and override the default settings so that it matches all the WLS domain parameters that define your WLS domain.

For sample YAML templates, please refer to this example.
* [Domain Resource example](../kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/README.md)

# Kubernetes resources

After you have written your YAML files, please use them to create your WLS domain artifacts using the `kubectl apply -f` command.

```
  kubectl apply -f domain-resource.yaml

```

## Verify the results

To confirm that the domain custom resource was created, use this command:

```
kubectl describe domain [domain name] -n [namespace]
```
