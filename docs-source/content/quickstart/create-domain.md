---
title: "Create a domain"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 6
---


1. Create a Kubernetes secret containing the `username` and `password` for the domain using the [`create-weblogic-credentials`](http://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh) script:

    ```bash
    $ kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh \
      -u weblogic -p welcome1 -n sample-domain1-ns -d sample-domain1
    ```

    The sample will create a secret named `domainUID-weblogic-credentials` where the `domainUID` is replaced
    with the value you provided.  For example, the command above would create a secret named
    `sample-domain1-weblogic-credentials`.

1.	Create a new image with a domain home by running the [`create-domain`](http://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain.sh) script.
Follow the directions in the [sample]({{< relref "/samples/simple/domains/domain-home-in-image/_index.md" >}}),
including:

    * Copying the sample `kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain-inputs.yaml` file and updating your copy with the `domainUID` (`sample-domain1`),
domain namespace (`sample-domain1-ns`), and the `domainHomeImageBase` (`store/oracle/weblogic:12.2.1.3`).

    * Setting `weblogicCredentialsSecretName` to the name of the secret containing the WebLogic credentials, in this case, `sample-domain1-weblogic-credentials`.

    * Leaving the `image` empty unless you need to tag the new image that the script builds to a different name.
{{% notice note %}}
If you set the `domainHomeImageBuildPath` property to `./docker-images/OracleWebLogic/samples/12213-domain-home-in-image-wdt`, make sure that your `JAVA_HOME` is set to a Java JDK version 1.8 or later.
{{% /notice %}}

    For example, assuming you named your copy `my-inputs.yaml`:

    ```bash
    $ cd kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image
    $ ./create-domain.sh -i my-inputs.yaml -o /some/output/directory -u weblogic -p welcome1 -e
    ```

    You need to provide the WebLogic administration user name and password in the `-u` and `-p` options
    respectively, as shown in the example.
{{% notice note %}}
When using this sample, the WebLogic Server credentials that you specify, in three separate places, must be consistent:

- The secret that you create for the credentials.
- The properties files in the sample project you choose to create the Docker image from.
- The parameters you supply to the `create-domain.sh` script.
{{% /notice %}}


    If you specify the `-e` option, the script will generate the
    Kubernetes YAML files *and* apply them to your cluster.  If you omit the `-e` option, the
    script will just generate the YAML files, but will not take any action on your cluster.

    If you run the sample from a machine that is remote to the Kubernetes cluster, and you need to push the new image to a registry that is local to the cluster, you need to do the following:

    * Set the `image` property in the inputs file to the target image name (including the registry hostname/port, and the tag if needed).
    * If you want Kubernetes to pull the image from a private registry, create a Kubernetes secret to hold your credentials and set the `imagePullSecretName` property in the inputs file to the name of the secret.
{{% notice note %}}
The Kubernetes secret must be in the same namespace where the domain will be running.
For more information, see [domain home in image protection]({{<relref "/security/domain-security/image-protection.md#weblogic-domain-in-docker-image-protection">}})
in the ***Security*** section.
{{% /notice %}}
    * Run the `create-domain.sh` script without the `-e` option.
    * Push the `image` to the registry.
    * Run the following command to create the domain.

    ```bash
    $ kubectl apply -f /some/output/directory/weblogic-domains/sample-domain1/domain.yaml
    ```

1.	Confirm that the operator started the servers for the domain:

    a. Use `kubectl` to show that the domain resource was created:

    ```bash
    $ kubectl describe domain sample-domain1 -n sample-domain1-ns
    ```

    b. After a short time, you will see the Administration Server and Managed Servers running.

    ```bash
    $ kubectl get pods -n sample-domain1-ns
    ```

    c. You should also see all the Kubernetes services for the domain.

    ```bash
    $ kubectl get services -n sample-domain1-ns
    ```

1.	Create an Ingress for the domain, in the domain namespace, by using the [sample](http://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/ingress-per-domain/README.md) Helm chart:

    ```bash
    $ helm install kubernetes/samples/charts/ingress-per-domain \
      --name sample-domain1-ingress \
      --namespace sample-domain1-ns \
      --set wlsDomain.domainUID=sample-domain1 \
      --set traefik.hostname=sample-domain1.org
    ```

1.	To confirm that the load balancer noticed the new Ingress and is successfully routing to the domain's server pods,
    you can send a request to the URL for the "WebLogic ReadyApp framework" which will return a HTTP 200 status code, as
    shown in the example below.  If you used the host-based routing Ingress sample, you will need to
    provide the hostname in the `-H` option.

    Substitute the Node IP address of the worker node for `your.server.com`. You can find it by running:

    ```bash
    $ kubectl get po -n sample-domain1-ns -o wide
    ```
    ```
    $ curl -v -H 'host: sample-domain1.org' http://your.server.com:30305/weblogic/ready
      About to connect() to your.server.com port 30305 (#0)
        Trying 10.196.1.64...
        Connected to your.server.com (10.196.1.64) port 30305 (#0)
    > GET /weblogic/ HTTP/1.1
    > User-Agent: curl/7.29.0
    > Accept: */*
    > host: domain1.org
    >
    < HTTP/1.1 200 OK
    < Content-Length: 0
    < Date: Thu, 20 Dec 2018 14:52:22 GMT
    < Vary: Accept-Encoding
    <   Connection #0 to host your.server.com left intact
    ```
{{% notice note %}}
Depending on where your Kubernetes cluster is running, you may need to open firewall ports or update security lists to allow ingress to this port.
{{% /notice %}}


1.	To access the WLS Administration Console:

    a. Edit the `my-inputs.yaml` file (assuming that you named your copy `my-inputs.yaml`) to set `exposedAdminNodePort: true`.

    b. Open a browser to `http://your.server.com:30701`.

    c. As in step 5, substitute the Node IP address of the worker node for `your.server.com`.
