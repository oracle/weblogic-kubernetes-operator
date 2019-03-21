+++
title = "Prepare to run a domain"
date = 2019-02-23T16:43:45-05:00
weight = 1
pre = "<b> </b>"
+++


Perform these steps to prepare your Kubernetes cluster to run a WebLogic domain:

1. Create the domain namespace(s).  One or more domains can share a namespace. A single instance of the operator can manage multiple namespaces.

    ```
    $ kubectl create namespace domain-namespace-1
    ```

    Replace `domain-namespace-1` with name you want to use.  The name must follow standard Kubernetes naming conventions, that is, lower case,
    numbers, and hyphens.

1. Create a Kubernetes secret containing the Administration Server boot credentials.  You can do this manually or by using
   [the provided sample]({{< relref "/samples/simple/credentials/_index.md" >}}).  To create
   the secret manually, use this command:

    ```
    $ kubectl -n domain-namespace-1 \
            create secret generic domain1-weblogic-credentials \
            --from-literal=username=weblogic \
            --from-literal=password=welcome1
    ```

    * Replace `domain-namespace-1` with the namespace that the domain will be in.
    * Replace `domain1-weblogic-credentials` with the name of the secret.  The operator expects the secret name to be
      the `domainUID` followed by the literal string `-weblogic-credentials` and many of the samples assume this name.
    * Replace the string `weblogic` in the third line with the user name for the administrative user.
    * Replace the string `welcome1` in the fourth line with the password.

1. Optionally, [create a PV & persistent volume claim (PVC)]({{< relref "/samples/simple/storage/_index.md" >}}) which can hold the domain home, logs, and application binaries.
   Even if you put your domain in a Docker image, you may want to put the logs on a persistent volume so that they are available after the pods terminate.
   This may be instead of, or as well as, other approaches like streaming logs into Elasticsearch.
1. Optionally, [configure load balancer(s)](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/README.md) to manage access to any WebLogic clusters.
