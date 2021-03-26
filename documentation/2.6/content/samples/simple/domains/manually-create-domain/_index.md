---
title: "Manually"
date: 2019-02-23T17:32:31-05:00
weight: 1
description: "Sample for creating the domain custom resource manually."
---


In some circumstances you may wish to manually create your domain custom resource.  If you have created your own
Docker image containing your domain and the specific patches that it requires, then this approach will probably
be the most suitable for your needs.

To create the domain custom resource, just make a copy of the sample [domain.yaml](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/scripts/create-weblogic-domain/manually-create-domain/domain.yaml), and then edit
it using the instructions provided in the comments in that file.
When it is ready, you can create the domain in your Kubernetes cluster using the command:

```
$ kubectl apply -f domain.yaml
```

You can verify the domain custom resource was created using this command:

```
$ kubectl -n YOUR_NAMESPACE get domains
```

You can view the details of the domain using this command:

```
$ kubectl -n YOUR_NAMESPACE describe domain YOUR_DOMAIN
```

In both of these commands, replace `YOUR_NAMESPACE` with the namespace in which you created the domain, and
replace `YOUR_DOMAIN` with the `domainUID` you chose.
