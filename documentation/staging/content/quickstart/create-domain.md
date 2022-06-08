---
title: "Create a domain"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 7
---

1.  Select a user name and password, following the required rules for password creation (at least 8 alphanumeric characters with at least one number or special character).

1. Create a Kubernetes Secret for the WebLogic domain administrator credentials containing the `user name` and `password` for the domain. For example, if the `user name` is `weblogic` and `password` is `welcome1`, then run the following command:

    ```shell
    $ kubectl create secret generic sample-domain1-weblogic-credentials \
      --from-literal=username=weblogic --from-literal=password=welcome1 \
      -n sample-domain1-ns
    ```

   If you selected a different user name and password, then replace `weblogic` and `welcome1` with your user name and password.
    

1. Create a domain runtime encryption secret using the following command:

    ```shell
    $ kubectl -n sample-domain1-ns create secret generic \
      sample-domain1-runtime-encryption-secret \
       --from-literal=password=my_runtime_password
    ```

    The above two commands create secrets named `sample-domain1-weblogic-credentials` and `sample-domain1-runtime-encryption-secret` used in the sample domain YAML file. If you want to use different secret names, then you will need to update the sample doman YAML file accordingly in the next step.

1. Use one of the following two options to create the domain using a [domain resource]({{< relref "/managing-domains/domain-resource.md" >}}). A domain resource can be used to configure the operation of your WebLogic Server domain. The domain resource does not replace the traditional domain configuration files, but instead cooperates with those files to describe the Kubernetes artifacts of the corresponding domain. For more details about domain resource, see [Domain resource]({{< relref "/managing-domains/domain-resource.md" >}}).
   - **Option 1**: If you decided to use the ready-made, off-the-shelf auxiliary image and skipped the optional [create auxiliary image]({{< relref "/quickstart/create-auxiliary-image.md" >}}) section, then create the domain using following command to apply the sample domain resource. 

       ```shell
       $ kubectl apply -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/quick-start/quick-start-domain-resource.yaml
       ```

      You can download the WLS Domain YAML file using the following command to a file called `/tmp/quickstart/quick-start-domain-resource.yaml` or similar and make any changes before running the `kubectl apply` command.

      ```shell
      $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/quick-start/quick-start-domain-resource.yaml -o /tmp/quickstart/quick-start-domain-resource.yaml
      ```
   - **Option 2**: If you created an auxiliary image using optional [create auxiliary image]({{< relref "/quickstart/create-auxiliary-image.md" >}}) section, then use the following steps to create the domain.

       1. Prepare the domain resource.
          1. Copy the following WLS Domain YAML to a file called `/tmp/quickstart/quick-start-domain-resource.yaml` or similar. 

                {{%expand "Click here to view the WLS Domain YAML file using auxiliary images." %}}
    # Copyright (c) 2022, Oracle and/or its affiliates.
    # Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

    apiVersion: "weblogic.oracle/v9"
    kind: Domain
    metadata:
      name: sample-domain1
      namespace: sample-domain1-ns
      labels:
        weblogic.domainUID: sample-domain1
  
    spec:
      configuration:
  
        model:
          # Optional auxiliary image(s) containing WDT model, archives, and install.
          # Files are copied from `sourceModelHome` in the aux image to the `/aux/models` directory
          # in running WebLogic Server pods, and files are copied from `sourceWDTInstallHome`
          # to the `/aux/weblogic-deploy` directory. Set `sourceModelHome` and/or `sourceWDTInstallHome`
          # to "None" if you want skip such copies.
          #   `image`                - Image location
          #   `imagePullPolicy`      - Pull policy, default `IfNotPresent`
          #   `sourceModelHome`      - Model file directory in image, default `/auxiliary/models`.
          #   `sourceWDTInstallHome` - WDT install directory in image, default `/auxiliary/weblogic-deploy`.
          auxiliaryImages:
          - image: "quick-start-aux-image:v1"
            #imagePullPolicy: IfNotPresent
            #sourceWDTInstallHome: /auxiliary/weblogic-deploy
            #sourceModelHome: /auxiliary/models
  
          # Optional configmap for additional models and variable files
          #configMap: sample-domain1-wdt-config-map
  
          # All 'FromModel' domains require a runtimeEncryptionSecret with a 'password' field
          runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret
  
      # Set to 'FromModel' to indicate 'Model in Image'.
      domainHomeSourceType: FromModel
  
      # The WebLogic Domain Home, this must be a location within
      # the image for 'Model in Image' domains.
      domainHome: /u01/domains/sample-domain1
  
      # The WebLogic Server image that the Operator uses to start the domain
      image: "container-registry.oracle.com/middleware/weblogic:12.2.1.4"
  
      # Defaults to "Always" if image tag (version) is ':latest'
      imagePullPolicy: "IfNotPresent"
  
      # Identify which Secret contains the credentials for pulling an image
      imagePullSecrets:
      - name: weblogic-repo-credentials
  
      # Identify which Secret contains the WebLogic Admin credentials,
      # the secret must contain 'username' and 'password' fields.
      webLogicCredentialsSecret:
        name: sample-domain1-weblogic-credentials
  
      # Whether to include the WebLogic Server stdout in the pod's stdout, default is true
      includeServerOutInPodLog: true
  
      # Whether to enable overriding your log file location, see also 'logHome'
      #logHomeEnabled: false
  
      # The location for domain log, server logs, server out, introspector out, and Node Manager log files
      # see also 'logHomeEnabled', 'volumes', and 'volumeMounts'.
      #logHome: /shared/logs/sample-domain1
  
      # Set which WebLogic Servers the Operator will start
      # - "NEVER" will not start any server in the domain
      # - "ADMIN_ONLY" will start up only the administration server (no managed servers will be started)
      # - "IF_NEEDED" will start all non-clustered servers, including the administration server, and clustered servers up to their replica count.
      serverStartPolicy: "IF_NEEDED"
  
      # Settings for all server pods in the domain including the introspector job pod
      serverPod:
        # Optional new or overridden environment variables for the domain's pods
        # - This sample uses CUSTOM_DOMAIN_NAME in its image model file
        #   to set the WebLogic domain name
        env:
        - name: CUSTOM_DOMAIN_NAME
          value: "domain1"
        - name: JAVA_OPTIONS
          value: "-Dweblogic.StdoutDebugEnabled=false"
        - name: USER_MEM_ARGS
          value: "-Djava.security.egd=file:/dev/./urandom -Xms256m -Xmx512m "
        resources:
          requests:
            cpu: "250m"
            memory: "768Mi"
  
        # Optional volumes and mounts for the domain's pods. See also 'logHome'.
        #volumes:
        #- name: weblogic-domain-storage-volume
        #  persistentVolumeClaim:
        #    claimName: sample-domain1-weblogic-sample-pvc
        #volumeMounts:
        #- mountPath: /shared
        #  name: weblogic-domain-storage-volume
  
      # The desired behavior for starting the domain's administration server.
      adminServer:
        # The serverStartState legal values are "RUNNING" or "ADMIN"
        # "RUNNING" means the listed server will be started up to "RUNNING" mode
        # "ADMIN" means the listed server will be start up to "ADMIN" mode
        serverStartState: "RUNNING"
        # Setup a Kubernetes node port for the administration server default channel
        #adminService:
        #  channels:
        #  - channelName: default
        #    nodePort: 30701
  
      # The number of managed servers to start for unlisted clusters
      replicas: 1
  
      # The desired behavior for starting a specific cluster's member servers
      clusters:
      - clusterName: cluster-1
        serverStartState: "RUNNING"
        serverPod:
          # Instructs Kubernetes scheduler to prefer nodes for new cluster members where there are not
          # already members of the same cluster.
          affinity:
            podAntiAffinity:
              preferredDuringSchedulingIgnoredDuringExecution:
                - weight: 100
                  podAffinityTerm:
                    labelSelector:
                      matchExpressions:
                        - key: "weblogic.clusterName"
                          operator: In
                          values:
                            - $(CLUSTER_NAME)
                    topologyKey: "kubernetes.io/hostname"
        # The number of managed servers to start for this cluster
        replicas: 2
  
      # Change the restartVersion to force the introspector job to rerun
      # and apply any new model configuration, to also force a subsequent
      # roll of your domain's WebLogic Server pods.
      restartVersion: '1'
  
      # Changes to this field cause the operator to repeat its introspection of the
      #  WebLogic domain configuration.
      introspectVersion: '1'
  
        # Secrets that are referenced by model yaml macros
        # (the model yaml in the optional configMap or in the image)
        #secrets:
        #- sample-domain1-datasource-secret
                {{% /expand %}}
       2. If you chose a different name and tag for the auxiliary image you created, then update the image field under `spec.configuration.model.auxiliaryImages` section to use that name and tag.
          For example, if you named the auxiliary image as `my-aux-image:v1`, then update the `spec.configuration.model.auxiliaryImages` section as shown below.
            ```
                   auxiliaryImages:
                   - image: "my-aux-image:v1"
            ```

       3. If you chose non-default values for any other fields such as `spec.image`, `spec.imagePullSecrets`, `spec.webLogicCredentialsSecret`, and `spec.configuration.model.runtimeEncryptionSecret`, then update those fields accordingly.

       4. Create the domain by applying the domain resource. Run the following command:

          ```shell
          $ kubectl apply -f /tmp/quickstart/quick-start-domain-resource.yaml
          ```

   {{% notice warning %}}
   The quick-start guide uses GA image of the WebLogic server version 12.2.1.4. GA images are suitable for demonstration and development purposes _only_ where the environments are not available from the public Internet; they are **not acceptable for production use**. In production, you should always use CPU (patched) images from [OCR]({{< relref "/base-images/ocr-images.md" >}}) or create your images using the [WebLogic Image Tool]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}}) (WIT) with the `--recommendedPatches` option. For more guidance, see [Apply the Latest Patches and Updates](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/14.1.1.0&id=LOCKD-GUID-2DA84185-46BA-4D7A-80D2-9D577A4E8DE2) in _Securing a Production Environment for Oracle WebLogic Server_.
   {{% /notice %}}

1.	Confirm that the operator started the servers for the domain:

    a. Use `kubectl` to show that the Domain was created and it's status:

    ```shell
    $ kubectl describe domain sample-domain1 -n sample-domain1-ns
    ```

    b. Get the domain status using the following command. If you don't have `jq` executable installed, then run the second command below to get the domain status.
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

1.	See [Domain debugging]({{< relref "/managing-domains/debugging.md" >}}) if the operator didn't start the servers for the domain.

1.	Create an ingress route for the domain, in the domain namespace, by using the following YAML file. Copy the following WLS Domain YAML to a file called `/tmp/quickstart/quick-start-ingress-route.yaml` or similar:


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
    $ kubectl apply -f /tmp/quickstart/quick-start-ingress-route.yaml \
      --namespace sample-domain1-ns 
    ```

      Alternatively, you can download the ingress route YAML file using the following command to a file called `/tmp/quickstart/quick-start-ingress-route.yaml` or similar and make any changes before running the `kubectl apply` command.

      ```shell
      $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/quick-start/quick-start-ingress-route.yaml -o /tmp/quickstart/quick-start-ingress-route.yaml
      ```



1.  To confirm that the ingress controller noticed the new ingress route and is successfully routing to the domain's server pods, you can send a request to the URL for the "quick start app", as shown in the example below, which will return an HTTP 200 status code.

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
                <h1>Welcome to WebLogic on Kubernetes Quick Start</font></h1><br>

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
               <h1>Welcome to WebLogic on Kubernetes Quick Start</font></h1><br>

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
      a. Open a browser to `http://localhost:30305/console`.
    {{% /tab %}}
    {{% tab name="OKE Cluster" %}}
      a. Get the LoadBalancer Ingress IP address using the following command.
         $ LOADBALANCER_INGRESS_IP=$(kubectl get svc traefik-operator -n traefik -o jsonpath='{.status.loadBalancer.ingress[].ip}{"\n"}')

      b. Open a browser to `http://${LOADBALANCER_INGRESS_IP}/console`.
    {{% /tab %}}
    {{< /tabs >}}


    {{% notice note %}} Do not use the WebLogic Server Administration Console to start or stop servers. See [Starting and stopping servers]({{< relref "/managing-domains/domain-lifecycle/startup#starting-and-stopping-servers" >}}).
    {{% /notice %}}
