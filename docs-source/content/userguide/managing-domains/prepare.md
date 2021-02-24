+++
title = "Prepare to run a domain"
date = 2019-02-23T16:43:45-05:00
weight = 1
pre = "<b> </b>"
+++


Perform these steps to prepare your Kubernetes cluster to run a WebLogic domain:

1. Create the domain namespace or namespaces.  One or more domains can share a namespace. A single instance of the operator can manage multiple namespaces.

    ```
    $ kubectl create namespace domain-namespace-1
    ```

    Replace `domain-namespace-1` with name you want to use.  The name must follow standard Kubernetes naming conventions, that is, lower case,
    numbers, and hyphens.

1. Create a Kubernetes Secret containing the Administration Server boot credentials.  You can do this manually or by using
   [the provided sample]({{< relref "/samples/simple/credentials/_index.md" >}}).  To create
   the secret manually, use this command:

    ```
    $ kubectl -n domain-namespace-1 \
            create secret generic domain1-weblogic-credentials \
            --from-literal=username=username \
            --from-literal=password=password
    ```

    * Replace `domain-namespace-1` with the namespace that the domain will be in.
    * Replace `domain1-weblogic-credentials` with the name of the secret. It is a recommended best practice to name the secret using the domain's `domainUID` followed by the literal string `-weblogic-credentials` where `domainUID` is a unique identifier for the domain. Many of the samples follow this practice and use a `domainUID` of `domain1` or `sample-domain1`.
    * Replace the string `username` in the third line with the user name for the administrative user.
    * Replace the string `password` in the fourth line with the password.

1. Optionally, [create a PV & PersistentVolumeClaim (PVC)]({{< relref "/samples/simple/storage/_index.md" >}}) which can hold the domain home, logs, and application binaries.
   Even if you put your domain in an image, you may want to put the logs on a persistent volume so that they are available after the pods terminate.
   This may be instead of, or as well as, other approaches like streaming logs into Elasticsearch.
1. Optionally, [configure load balancer](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/README.md) to manage access to any WebLogic clusters.
