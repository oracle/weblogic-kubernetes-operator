+++
title = "Domain resource"
date = 2019-02-23T16:43:45-05:00
weight = 2
pre = "<b> </b>"
+++




Use this document to set up and configure your own [domain resource](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/docs/domains/Domain.md) which can be used to configure your WLS domain. Then, you can use the domain resource to start the Kubernetes artifacts of the corresponding domain.

Swagger documentation is available [here](https://oracle.github.io/weblogic-kubernetes-operator/swagger/index.html).

#### Prerequisites

The following prerequisites must be fulfilled before proceeding with the creation of the resource:

* Make sure the WebLogic operator is running.
* Create a Kubernetes namespace for the domain resource unless the intention is to use the default namespace.
* Create the Kubernetes secrets `username` and `password` of the administrative account in the same Kubernetes namespace as the domain resource.

#### YAML files

Domain resources are defined using the domain resource YAML files. For each WLS domain you want to create and configure, you should create one domain resource YAML file and apply it. In the example referenced below, the sample script, `create-domain.sh`, generates a domain resource YAML file that you can use as a basis. Copy the file and override the default settings so that it matches all the WLS domain parameters that define your WLS domain.

See the [WebLogic sample domain home on a persistent volume README]({{< relref "/samples/simple/domains/domain-home-on-pv/_index.md" >}}).

#### Kubernetes resources

After you have written your YAML files, you use them to create your WLS domain artifacts using the `kubectl apply -f` command.

```
$ kubectl apply -f domain-resource.yaml
```

#### Verify the results

To confirm that the domain resource was created, use this command:

```
$ kubectl describe domain [domain name] -n [namespace]
```
