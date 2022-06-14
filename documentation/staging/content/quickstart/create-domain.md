---
title: "Create a domain"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 3
---

1. Select a user name and password for the WebLogic domain administrator credentials and use them to create a Kubernetes Secret for the domain:

    ```shell
    $ kubectl create secret generic sample-domain1-weblogic-credentials \
      --from-literal=username=ADMIN_USERNAME --from-literal=password=ADMIN_PASSWORD \
      -n sample-domain1-ns
    ```

   Replace `ADMIN_USERNAME` and `ADMIN_PASSWORD` with your choice of user name and password. Note
   that the password must be at least 8 characters long and must contain at least one non-alphabetical character.


1. Create a domain runtime encryption secret using the following command:

    ```shell
    $ kubectl -n sample-domain1-ns create secret generic \
      sample-domain1-runtime-encryption-secret \
       --from-literal=password=my_runtime_password
    ```

    These two commands create secrets named `sample-domain1-weblogic-credentials` and `sample-domain1-runtime-encryption-secret` used in the sample domain YAML file. If you want to use different secret names, then you will need to update the sample domain YAML file accordingly in the next step.

1. Create the `sample-domain1` domain using a domain resource. The domain resource does not replace the traditional domain configuration files, but instead cooperates with those files to describe the Kubernetes artifacts of the corresponding domain.

   - Use the following command to apply the sample domain resource:

       ```shell
       $ kubectl apply -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/quick-start/domain-resource.yaml
       ```

    - **NOTE**: Instead of running the previous `kubectl apply` command, you can download the WLS Domain YAML file to `/tmp/quickstart/domain-resource.yaml` or similar, using the following command, and then apply the file using `kubectl apply -f /tmp/quickstart/domain-resource.yaml`:

      ```shell
      $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/quick-start/domain-resource.yaml -o /tmp/quickstart/domain-resource.yaml
      ```
   This domain resource references a WebLogic Server installation image, the secrets you defined, and a sample "auxiliary image," which contains traditional WebLogic configuration and a WebLogic application.

     - To examine the domain resource, click [here](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/quick-start/domain-resource.yaml).
     - For detailed information, see [Domain resource]({{< relref "/managing-domains/domain-resource.md" >}}).

   {{% notice note %}}
   The Quick Start guide's sample domain resource references a WebLogic Server version 12.2.1.4 General Availability (GA) image. GA images are suitable for demonstration and development purposes _only_ where the environments are not available from the public Internet; they are **not acceptable for production use**. In production, you should always use CPU (patched) images from [OCR]({{< relref "/base-images/ocr-images.md" >}}) or create your images using the [WebLogic Image Tool]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}}) (WIT) with the `--recommendedPatches` option. For more guidance, see [Apply the Latest Patches and Updates](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/14.1.1.0&id=LOCKD-GUID-2DA84185-46BA-4D7A-80D2-9D577A4E8DE2) in _Securing a Production Environment for Oracle WebLogic Server_.
   {{% /notice %}}

1.	Confirm that the operator started the servers for the domain:

    a. Use `kubectl` to show that the Domain was created:

    ```shell
    $ kubectl describe domain sample-domain1 -n sample-domain1-ns
    ```

    b. Get the domain status using the following command. If you don't have the `jq` executable installed, then run the second command to get the domain status.
    ```shell
    $ kubectl get domain sample-domain1 -n sample-domain1-ns -o json | jq .status
    ```
    OR
    ```shell
    $ kubectl get domain sample-domain1 -n sample-domain1-ns -o jsonpath='{.status}'
    ```

    c. After a short time, you will see the Administration Server and Managed Servers running.

    ```shell
    $ kubectl get pods -n sample-domain1-ns
    ```

    d. You should also see all the Kubernetes Services for the domain.

    ```shell
    $ kubectl get services -n sample-domain1-ns
    ```

   	 If the operator didn't start the servers for the domain, see [Domain debugging]({{< relref "/managing-domains/debugging.md" >}}).

1.	Create an ingress route for the domain, in the domain namespace, by using the following YAML file.

    a. Copy the following IngressRoute YAML to a file called `/tmp/quickstart/ingress-route.yaml` or similar:


    {{%expand "Click here to view the ingress route YAML file." %}}
    # Copyright (c) 2022, Oracle and/or its affiliates.
    # Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

    apiVersion: traefik.containo.us/v1alpha1
    kind: IngressRoute
    metadata:
      name: console
      namespace: sample-domain1-ns
    spec:
      routes:
        - kind: Rule
          match: PathPrefix(`/console`)
          services:
            - kind: Service
              name: sample-domain1-admin-server
              port: 7001
    ---
    apiVersion: traefik.containo.us/v1alpha1
    kind: IngressRoute
    metadata:
      name: quickstart
      namespace: sample-domain1-ns
    spec:
      routes:
      - kind: Rule
        match: PathPrefix(`/quickstart`)
        services:
        - kind: Service
          name: sample-domain1-cluster-cluster-1
          port: 8001
    {{% /expand %}}
    ```shell
    $ kubectl apply -f /tmp/quickstart/ingress-route.yaml \
      --namespace sample-domain1-ns
    ```

      b. **NOTE**: Instead of running the previous  `kubectl apply` command, you can download the ingress route YAML file to `/tmp/quickstart/ingress-route.yaml` or similar, using the following command:

      ```shell
      $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/quick-start/ingress-route.yaml -o /tmp/quickstart/ingress-route.yaml
      ```
      c. Then apply the file using:
      ```shell
      $ kubectl apply -f /tmp/quickstart/ingress-route.yaml \
        --namespace sample-domain1-ns
      ```

1.  To confirm that the ingress controller noticed the new ingress route and is successfully routing to the domain's server pods, send a request to the URL for the "quick start app", as shown in the following example, which will return an HTTP 200 status code.

    {{< tabs groupId="config" >}}
    {{% tab name="Single Node Cluster" %}}
        $ curl -i http://localhost:30305/quickstart/

        HTTP/1.1 200 OK
        Content-Length: 264
        Content-Type: text/html; charset=UTF-8
        Date: Tue, 24 May 2022 17:20:49 GMT
        Set-Cookie: JSESSIONID=uY73FejgzFGdKmKvG0OOF_tN-0RiHjC28X_mWglMGXN3NqP8f_qR!-1753503593; path=/; HttpOnly



        <!DOCTYPE html>
        <html>
        <body>
                <h1>Welcome to the WebLogic on Kubernetes Quick Start</font></h1><br>

                <h2>WebLogic Server Hosting the Application</h2> <b>Server Name:</b> sample-domain1-managed-server1<br><b>Server time:</b> 17:20:49<br><p>
        </body>
        </html>
    {{% /tab %}}
    {{% tab name="OKE Cluster" %}}
       $ LOADBALANCER_INGRESS_IP=$(kubectl get svc traefik-operator -n traefik -o jsonpath='{.status.loadBalancer.ingress[].ip}{"\n"}')

       $ curl -i http://${LOADBALANCER_INGRESS_IP}/quickstart/

       HTTP/1.1 200 OK
       Via: 1.1 10.68.69.7 (McAfee Web Gateway 9.2.4.34298)
       Date: Thu, 02 Jun 2022 00:22:51 GMT
       Set-Cookie: JSESSIONID=WHMhyyg-7xmJ-4dvjo6JQuWY4fg94p5_rKmbNAdk2HWWUuKujtRU!182127355; path=/; HttpOnly
       Content-Type: text/html; charset=UTF-8
       Content-Length: 264
       Proxy-Connection: Keep-Alive



       <!DOCTYPE html>
       <html>
       <body>
               <h1>Welcome to the WebLogic on Kubernetes Quick Start</font></h1><br>

               <h2>WebLogic Server Hosting the Application</h2> <b>Server Name:</b> sample-domain1-managed-server2<br><b>Server time:</b> 00:22:51<br><p>
       </body>
       </html>
    {{% /tab %}}
    {{< /tabs >}}


    {{% notice note %}} Depending on where your Kubernetes cluster is running, you may need to open firewall ports or update security lists to allow ingress to this port.
    {{% /notice %}}
1.	To access the WebLogic Server Administration Console:
    {{< tabs groupId="config" >}}
    {{% tab name="Single Node Cluster" %}}
      Open a browser to http://localhost:30305/console.
    {{% /tab %}}
    {{% tab name="OKE Cluster" %}}
      a. Get the load balancer ingress IP address using the following command:
         $ LOADBALANCER_INGRESS_IP=$(kubectl get svc traefik-operator -n traefik -o jsonpath='{.status.loadBalancer.ingress[].ip}{"\n"}')

      b. Open a browser to http://${LOADBALANCER_INGRESS_IP}/console.
    {{% /tab %}}
    {{< /tabs >}}


    {{% notice note %}} Do not use the WebLogic Server Administration Console to start or stop servers, or for scaling clusters. See [Starting and stopping servers]({{< relref "/managing-domains/domain-lifecycle/startup#starting-and-stopping-servers" >}}) and [Scaling]({{< relref "/managing-domains/domain-lifecycle/scaling.md" >}}).
    {{% /notice %}}
