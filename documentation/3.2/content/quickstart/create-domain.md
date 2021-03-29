---
title: "Create a domain"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 6
---


1. For use in the following steps:

   * Select a user name and password, following the required rules for password creation (at least 8 alphanumeric characters with at least one number or special character).
   * Pick or create a directory to which you can write output.

1. Create a Kubernetes Secret for the WebLogic domain administrator credentials containing the `username` and `password` for the domain, using the [create-weblogic-credentials](http://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh) script:

    ```shell
    $ kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh \
      -u <username> -p <password> -n sample-domain1-ns -d sample-domain1
    ```

    The sample will create a secret named `domainUID-weblogic-credentials` where the `domainUID` is replaced
    with the value specified by the `-d` flag.  For example, the command above would create a secret named
    `sample-domain1-weblogic-credentials`.

1.	Create a new image with a domain home by running the [create-domain](http://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain.sh) script. First, copy the sample [create-domain-inputs.yaml](http://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain-inputs.yaml) file and update your copy with:  
       * `domainUID`: `sample-domain1`
       * `image`: Leave empty unless you need to tag the new image that the script builds to a different name.
          For example if you are using a remote cluster that will need to pull the image from a container registry,
          then you should set this value to the fully qualified image name.  Note that you will need to
          push the image manually.
       * `weblogicCredentialsSecretName`: `sample-domain1-weblogic-credentials`
       * `namespace`: `sample-domain1-ns`
       * `domainHomeImageBase`: `container-registry.oracle.com/middleware/weblogic:12.2.1.4`

    For example, assuming you named your copy `my-inputs.yaml`:

    ```shell
    $ cd kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image
    ```
    ```shell
    $ ./create-domain.sh -i my-inputs.yaml -o /<your output directory> -u <username> -p <password> -e
    ```
    {{% notice note %}}You need to provide the same WebLogic domain administrator user name and password in the `-u` and `-p` options
    respectively, as you provided when creating the Kubernetes Secret in Step 2.
    {{% /notice %}}

1.	Confirm that the operator started the servers for the domain:

    a. Use `kubectl` to show that the Domain was created:

    ```shell
    $ kubectl describe domain sample-domain1 -n sample-domain1-ns
    ```

    b. After a short time, you will see the Administration Server and Managed Servers running.

    ```shell
    $ kubectl get pods -n sample-domain1-ns
    ```

    c. You should also see all the Kubernetes Services for the domain.

    ```shell
    $ kubectl get services -n sample-domain1-ns
    ```

1.	Create an ingress for the domain, in the domain namespace, by using the [sample](http://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/ingress-per-domain/README.md) Helm chart:

    ```shell
    $ helm install sample-domain1-ingress kubernetes/samples/charts/ingress-per-domain \
      --namespace sample-domain1-ns \
      --set wlsDomain.domainUID=sample-domain1 \
      --set traefik.hostname=sample-domain1.org
    ```


1.	To confirm that the ingress controller noticed the new ingress and is successfully routing to the domain's server pods,
    you can send a request to the URL for the "WebLogic ReadyApp framework", as
    shown in the example below, which will return an HTTP 200 status code.   

    ```shell
    $ curl -v -H 'host: sample-domain1.org' http://localhost:30305/weblogic/ready
    ```
    ```
      About to connect() to localhost port 30305 (#0)
        Trying 10.196.1.64...
        Connected to localhost (10.196.1.64) port 30305 (#0)
    > GET /weblogic/ HTTP/1.1
    > User-Agent: curl/7.29.0
    > Accept: */*
    > host: domain1.org
    >
    < HTTP/1.1 200 OK
    < Content-Length: 0
    < Date: Thu, 20 Dec 2018 14:52:22 GMT
    < Vary: Accept-Encoding
    <   Connection #0 to host localhost left intact
    ```
    {{% notice note %}} Depending on where your Kubernetes cluster is running, you may need to open firewall ports or update security lists to allow ingress to this port.
    {{% /notice %}}


1.	To access the WebLogic Server Administration Console:

    a. Edit the `my-inputs.yaml` file (assuming that you named your copy `my-inputs.yaml`) to set `exposedAdminNodePort: true`.

    b. Open a browser to `http://localhost:30701`.

    {{% notice note %}} Do not use the WebLogic Server Administration Console to start or stop servers. See [Starting and stopping servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup#starting-and-stopping-servers" >}}).
    {{% /notice %}}
