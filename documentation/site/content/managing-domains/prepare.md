---
title: "Prepare to run a domain"
date: 2019-02-23T16:43:45-05:00
weight: 5
description: "Perform these steps to prepare your Kubernetes cluster to run a WebLogic domain."
---


Perform these steps to prepare your Kubernetes cluster to run a WebLogic domain:

1. Create the domain namespace or namespaces.  One or more domains can share a namespace. A single instance of the operator can manage multiple namespaces.

    ```shell
    $ kubectl create namespace domain-namespace-1
    ```

    Replace `domain-namespace-1` with name you want to use.  The name must follow standard Kubernetes naming conventions, that is, lowercase,
    numbers, and hyphens.

1. Create a Kubernetes Secret containing the Administration Server boot credentials.  You can do this manually or by using
   [the provided sample]({{< relref "/samples/credentials/_index.md" >}}).  To create
   the secret manually, use this command:

    ```shell
    $ kubectl -n domain-namespace-1 \
            create secret generic domain1-weblogic-credentials \
            --from-literal=username=<the user name> \
            --from-literal=password=<the actual password>
    ```

    * Replace `domain-namespace-1` with the namespace that the domain will be in.
    * Replace `domain1-weblogic-credentials` with the name of the secret. It is a recommended best practice to name the secret using the domain's `domainUID` followed by the literal string `-weblogic-credentials` where `domainUID` is a unique identifier for the domain. Many of the samples follow this practice and use a `domainUID` of `domain1` or `sample-domain1`.
    * In the third line, enter the user name for the administrative user.
    * In the fourth line, enter the password.

1. Optionally, [create a PV & PersistentVolumeClaim (PVC)]({{< relref "/samples/storage/_index.md" >}}) which can hold the domain home, logs, and application binaries.
   Even if you put your domain in an image, you may want to put the logs on a persistent volume so that they are available after the pods terminate.
   This may be instead of, or as well as, other approaches like streaming logs into Elasticsearch.
1. Optionally, [configure load balancer](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/charts/README.md) to manage access to any WebLogic clusters.
