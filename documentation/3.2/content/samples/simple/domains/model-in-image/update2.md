---
title: "Update 2"
date: 2019-02-23T17:32:31-05:00
weight: 4
---

This use case demonstrates concurrently deploying a domain that is similar to the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case domain to the same `sample-domain1-ns` namespace, but with a different domain UID, a different WebLogic domain name, and a different WebLogic domain encryption key. It does this by:

- Using the same image, image model YAML file, and application archive as the Initial and Update 1 use cases.
- Using the same model update ConfigMap source file as the Update 1 use case (a data source).
- Using a different (unique) domain UID, `sample-domain2`, for the new domain.
- Using a different (unique) domain name, `domain2`, for the different domains.
- Deploying secrets and a model update ConfigMap that are uniquely labeled and named for the new domain.

Note that this use case shows Model in Image's unique ability to quickly deploy a copy of a WebLogic domain that has a different WebLogic domain name and domain encryption key. This is a useful capability that is not supported by the Domain in Image [domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}):

- Domain in Image does not support overriding the domain name, but different domain names are necessary when two domains need to interoperate. This use case takes advantage of model macros to ensure that its two different domains have a different domain name:

  - First, you define the domain name in the model YAML file using the `@@ENV:CUSTOM_DOMAIN_NAME@@` environment variable macro.
  - Second, you set the value of the `CUSTOM_DOMAIN_NAME` environment variable to be different using the `env` stanza in each Domain's YAML file.

- Domain in Image requires that its images embed a WebLogic `security/SerializedSystemIni.dat` domain encryption key that cannot be changed for the image (see [Why layering matters]({{< relref "/userguide/cicd/why-layering-matters.md" >}}) in CI/CD considerations). This necessarily means that two Domain in Image domains that share the same image can decrypt each other's encrypted passwords. On the other hand, a Model in Image's domain encryption key is not embedded in the image and instead, is dynamically and uniquely created each time the domain is started.

{{% notice warning %}}
Oracle requires interoperating WebLogic domains to have different domain names. This is necessary when two domains communicate, or when a WebLogic Server or WebLogic Java client concurrently connects to multiple domains.
{{% /notice %}}

Here are the steps for this use case:

1. Make sure you have deployed the domain from the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case.

1. Create a ConfigMap with the WDT model that contains the data source definition.

   Run the following commands:

   ```shell
   $ kubectl -n sample-domain1-ns create configmap sample-domain2-wdt-config-map \
     --from-file=/tmp/mii-sample/model-configmaps/datasource
   ```
   ```shell
   $ kubectl -n sample-domain1-ns label configmap sample-domain2-wdt-config-map \
     weblogic.domainUID=sample-domain2
   ```

   If you've created your own data source file in the Update 1 use case, then substitute the file name in the `--from-file=` parameter (we suggested `/tmp/mii-sample/mydatasource.yaml` earlier). Note that the `-from-file=` parameter can reference a single file, in which case it puts the designated file in the ConfigMap, or it can reference a directory, in which case it populates the ConfigMap with all of the files in the designated directory.

   Observations:
     - We are leaving the namespace `sample-domain1-ns` unchanged for the ConfigMap because you will deploy domain `sample-domain2` to the same namespace as `sample-domain1`.
     - You name and label the ConfigMap using its associated domain UID for two reasons:
       - To make it obvious which ConfigMap belongs to which domain.
       - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all resources associated with a domain.
     - You use a different ConfigMap for the new domain for two reasons:
       - To make it easier to keep the life cycle and/or CI/CD process for the two domains simple and independent.
       - To 'future proof' the new domain so that changes to the original domain or new domain's ConfigMap can be independent.


1. Create the secrets that are referenced by the WDT model files in the image and ConfigMap; they also will be referenced by the Domain YAML file.

   Run the following commands:
   ```
   # spec.webLogicCredentialsSecret
   ```
   ```shell
   $ kubectl -n sample-domain1-ns create secret generic \
     sample-domain2-weblogic-credentials \
      --from-literal=username=weblogic --from-literal=password=welcome1
   ```
   ```shell
   $ kubectl -n sample-domain1-ns label  secret \
     sample-domain2-weblogic-credentials \
     weblogic.domainUID=sample-domain2
   ```
   ```
   # spec.configuration.model.runtimeEncryptionSecret
   ```
   ```shell
   $ kubectl -n sample-domain1-ns create secret generic \
     sample-domain2-runtime-encryption-secret \
      --from-literal=password=my_runtime_password
   ```
   ```shell
   $ kubectl -n sample-domain1-ns label  secret \
     sample-domain2-runtime-encryption-secret \
     weblogic.domainUID=sample-domain2
   ```
   ```
   # referenced by spec.configuration.secrets and by the data source model YAML in the ConfigMap
   ```
   ```shell
   $ kubectl -n sample-domain1-ns create secret generic \
      sample-domain2-datasource-secret \
      --from-literal='user=sys as sysdba' \
      --from-literal='password=incorrect_password' \
      --from-literal='max-capacity=1' \
      --from-literal='url=jdbc:oracle:thin:@oracle-db.default.svc.cluster.local:1521/devpdb.k8s'
   ```
   ```shell
   $ kubectl -n sample-domain1-ns label  secret \
      sample-domain2-datasource-secret \
      weblogic.domainUID=sample-domain2
   ```

   Observations:
     - We are leaving the namespace `sample-domain1-ns` unchanged for each secret because you will deploy domain `sample-domain2` to the same namespace as `sample-domain1`.
     - You name and label the secrets using their associated domain UID for two reasons:
       - To make it obvious which secret belongs to which domain.
       - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all resources associated with a domain.
     - You use a different set of secrets for the new domain for two reasons:
       - To make it easier to keep the life cycle and/or CI/CD process for the two domains simple and independent.
       - To 'future proof' the new domain so that changes to the original domain's secrets or new domain's secrets can be independent.
     - We deliberately specify an incorrect password and a low maximum pool capacity in the data source secret because we will demonstrate dynamically correcting the data source attributes for `sample-domain1` in the [Update 4]({{< relref "/samples/simple/domains/model-in-image/update4.md" >}}) use case.

   If you're following the `JRF` path through the sample, then you also need to deploy the additional secret referenced by macros in the `JRF` model `RCUDbInfo` clause, plus an `OPSS` wallet password secret. For details about the uses of these secrets, see the [Model in Image]({{< relref "/userguide/managing-domains/model-in-image/_index.md" >}}) user documentation. Note that we are using the RCU prefix `FMW2` for this domain, because the first domain is already using `FMW1`.

   {{%expand "Click here for the commands for deploying additional secrets for JRF." %}}

   ```shell
   $ kubectl -n sample-domain1-ns create secret generic \
     sample-domain2-rcu-access \
      --from-literal=rcu_prefix=FMW2 \
      --from-literal=rcu_schema_password=Oradoc_db1 \
      --from-literal=rcu_db_conn_string=oracle-db.default.svc.cluster.local:1521/devpdb.k8s
   ```
   ```shell
   $ kubectl -n sample-domain1-ns label  secret \
     sample-domain2-rcu-access \
     weblogic.domainUID=sample-domain2
   ```
   ```shell
   $ kubectl -n sample-domain1-ns create secret generic \
     sample-domain2-opss-wallet-password-secret \
      --from-literal=walletPassword=welcome1
   ```
   ```shell
   $ kubectl -n sample-domain1-ns label  secret \
     sample-domain2-opss-wallet-password-secret \
     weblogic.domainUID=sample-domain2
   ```
   {{% /expand %}}

1. Set up a Domain YAML file that is similar to your Update 1 use case Domain YAML file but with a different domain UID, domain name, model update ConfigMap reference, and Secret references:

    - Option 1: Update a copy of your Domain YAML file from the Update 1 use case.

      - In the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case, we suggested creating a file named `/tmp/mii-sample/mii-update1.yaml` or using the `/tmp/mii-sample/domain-resources/WLS/mii-update1-d1-WLS-v1-ds.yaml` file that is supplied with the sample.
        - We suggest copying this Domain YAML file and naming the copy `/tmp/mii-sample/mii-update2.yaml` before making any changes.

        - Working on a copy is not strictly necessary, but it helps keep track of your work for the different use cases in this sample and provides you a backup of your previous work.

      - Change the `/tmp/mii-sample/mii-update2.yaml` Domain YAML file name and `weblogic.domainUID` label to `sample-domain2`.

        The final result will look something like this:

          ```yaml
          apiVersion: "weblogic.oracle/v8"
          kind: Domain
          metadata:
            name: sample-domain2
            namespace: sample-domain1-ns
            labels:
              weblogic.domainUID: sample-domain2
          ```

        > __NOTE__: We are leaving the namespace `sample-domain1-ns` unchanged because you will be deploying domain `sample-domain2` to the same namespace as `sample-domain1`.

      - Change the `/tmp/mii-sample/mii-update2.yaml` Domain YAML file's `CUSTOM_DOMAIN_NAME` environment variable from `domain1` to `domain2`.

        The model file in the image uses macro `@@ENV:CUSTOM_DOMAIN_NAME@@` to reference this environment variable when setting its domain name.

        Specifically, change the corresponding Domain `spec.serverPod.env` YAML file stanza to look something like this:

        ```yaml
        ...
        spec:
          ...
          serverPod:
          ...
            env:
            - name: CUSTOM_DOMAIN_NAME
              value: "domain2"
          ...
        ```

      - Change the `/tmp/mii-sample/mii-update2.yaml` Domain YAML file's `spec.domainHome` value to `/u01/domains/sample-domain2`. The corresponding YAML file stanza will look something like this:

        ```yaml
        ...
        spec:
          ...
          domainHome: /u01/domains/sample-domain2
          ...
        ```

        (This change is not strictly needed, but it is a helpful convention to decorate a WebLogic domain's home directory with its domain name or domain UID.)

      - Change the `/tmp/mii-sample/mii-update2.yaml` secret references in the `spec.webLogicCredentialsSecret` and `spec.configuration.secrets` stanzas to reference this use case's secrets. Specifically, change:

          ```yaml
          spec:
            ...
            webLogicCredentialsSecret:
              name: sample-domain1-weblogic-credentials
            ...
            configuration:
            ...
              secrets:
              - sample-domain1-datasource-secret
              ...
              model:
                ...
                runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret
          ```

        To this:
    
          ```yaml
          spec:
            ...
            webLogicCredentialsSecret:
              name: sample-domain2-weblogic-credentials
            ...
            configuration:
              ...
              secrets:
              - sample-domain2-datasource-secret
              ...
              model:
                ...
                runtimeEncryptionSecret: sample-domain2-runtime-encryption-secret
          ```

        > __NOTE__: If you are following the `JRF` path through the sample, similarly change your `spec.configuration.opss.walletPasswordSecret` and the RCU secret name referenced in `spec.configuration.secrets`.


      - Change the Domain YAML file's `spec.configuration.model.configMap` value from `sample-domain1-wdt-config-map` to `sample-domain2-wdt-config-map`. The corresponding YAML file stanza will look something like this:
         ```yaml
         spec:
           ...
           configuration:
             ...
             model:
             ...
               configMap: sample-domain2-wdt-config-map
         ```

      - Now, compare your original and changed Domain YAML files to double check your changes.

          ```shell
          $ diff /tmp/mii-sample/mii-update1.yaml /tmp/mii-sample/mii-update2.yaml
          ```
          ```
          9c9
          <   name: sample-domain1
          ---
          >   name: sample-domain2
          13c13
          <     weblogic.domainUID: sample-domain1
          ---
          >     weblogic.domainUID: sample-domain2
          21c21
          <   domainHome: /u01/domains/sample-domain1
          ---
          >   domainHome: /u01/domains/sample-domain2
          36c36
          <     name: sample-domain1-weblogic-credentials
          ---
          >     name: sample-domain2-weblogic-credentials
          46c46
          <   #logHome: /shared/logs/sample-domain1
          ---
          >   #logHome: /shared/logs/sample-domain2
          61c61
          <       value: "domain1"
          ---
          >       value: "domain2"
          71c71
          <     #    claimName: sample-domain1-weblogic-sample-pvc
          ---
          >     #    claimName: sample-domain2-weblogic-sample-pvc
          110c110
          <       configMap: sample-domain1-wdt-config-map
          ---
          >       configMap: sample-domain2-wdt-config-map
          113c113
          <       runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret
          ---
          >       runtimeEncryptionSecret: sample-domain2-runtime-encryption-secret
          118c118
          <     - sample-domain1-datasource-secret
          ---
          >     - sample-domain2-datasource-secret
          ```

        {{%expand "Click here to see additional 'diff' expected for the JRF path through the sample." %}}

        ```
        <       walletPasswordSecret: sample-domain1-opss-wallet-password-secret
        ---
        >       walletPasswordSecret: sample-domain2-opss-wallet-password-secret
        130c130
        <       #walletFileSecret: sample-domain1-opss-walletfile-secret
        ---
        >       #walletFileSecret: sample-domain2-opss-walletfile-secret
        ```
        {{% /expand %}}

        > __NOTE__: The diff should _not_ contain a namespace change. You are deploying domain `sample-domain2` to the same namespace as `sample-domain1` (namespace `sample-domain1-ns`).


      - Apply your changed Domain YAML file:

          > **Note**: Before you deploy the Domain YAML file, determine if you have Kubernetes cluster worker nodes that are remote to your local machine. If so, you need to put the Domain's image in a location that these nodes can access and you may also need to modify your Domain YAML file to reference the new location. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/simple/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

          ```shell
          $ kubectl apply -f /tmp/mii-sample/mii-update2.yaml
          ```

    - Option 2: Use the updated Domain YAML file that is supplied with the sample:

        > **Note**: Before you deploy the Domain YAML file, determine if you have Kubernetes cluster worker nodes that are remote to your local machine. If so, you need to put the Domain's image in a location that these nodes can access and you may also need to modify your Domain YAML file to reference the new location. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/simple/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

        ```shell
        $ kubectl apply -f /tmp/miisample/domain-resources/WLS/mii-update2-d2-WLS-v1-ds.yaml
        ```

1. Wait for `sample-domain2` to start.

   If you run `kubectl get pods -n sample-domain1-ns --watch`, then you will see the introspector job for `sample-domain2` run and your WebLogic Server pods start. The output will look something like this:

   {{%expand "Click here to expand." %}}
   ```shell
   $ kubectl get pods -n sample-domain1-ns --watch
   ```
   ```
   NAME                             READY   STATUS    RESTARTS   AGE
   sample-domain1-admin-server      1/1     Running   0          5d2h
   sample-domain1-managed-server1   1/1     Running   1          5d2h
   sample-domain1-managed-server2   1/1     Running   2          5d2h
   sample-domain2-introspector-plssr   0/1     Pending   0          0s
   sample-domain2-introspector-plssr   0/1     Pending   0          0s
   sample-domain2-introspector-plssr   0/1     ContainerCreating   0          0s
   sample-domain2-introspector-plssr   1/1     Running             0          2s
   sample-domain2-introspector-plssr   0/1     Completed           0          69s
   sample-domain2-introspector-plssr   0/1     Terminating         0          71s
   sample-domain2-introspector-plssr   0/1     Terminating         0          71s
   sample-domain2-admin-server                  0/1     Pending             0          0s
   sample-domain2-admin-server                  0/1     Pending             0          0s
   sample-domain2-admin-server                  0/1     ContainerCreating   0          0s
   sample-domain2-admin-server                  0/1     Running             0          1s
   sample-domain2-admin-server                  1/1     Running             0          34s
   sample-domain2-managed-server1               0/1     Pending             0          0s
   sample-domain2-managed-server1               0/1     Pending             0          0s
   sample-domain2-managed-server1               0/1     ContainerCreating   0          0s
   sample-domain2-managed-server2               0/1     Pending             0          0s
   sample-domain2-managed-server2               0/1     Pending             0          0s
   sample-domain2-managed-server2               0/1     ContainerCreating   0          0s
   sample-domain2-managed-server1               0/1     Running             0          1s
   sample-domain2-managed-server2               0/1     Running             0          1s
   sample-domain2-managed-server1               1/1     Running             0          45s
   sample-domain2-managed-server2               1/1     Running             0          45s
   ```
   {{% /expand %}}

   For a more detailed view of this activity, you can instead call `/tmp/mii-sample/utils/wl-pod-wait.sh -n sample-domain1-ns -d sample-domain2 -p 3`. The output will look something like this:

   {{%expand "Click here to expand." %}}
   ```shell
   $ ./wl-pod-wait.sh -n sample-domain1-ns -d sample-domain2 -p 3
   ```
   ```
   @@ [2020-05-13T17:06:00][seconds=1] Info: Waiting up to 1000 seconds for exactly '3' WebLogic Server pods to reach the following criteria:
   @@ [2020-05-13T17:06:00][seconds=1] Info:   ready='true'
   @@ [2020-05-13T17:06:00][seconds=1] Info:   image='model-in-image:WLS-v1'
   @@ [2020-05-13T17:06:00][seconds=1] Info:   domainRestartVersion='1'
   @@ [2020-05-13T17:06:00][seconds=1] Info:   namespace='sample-domain1-ns'
   @@ [2020-05-13T17:06:00][seconds=1] Info:   domainUID='sample-domain2'

   @@ [2020-05-13T17:06:00][seconds=1] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-13T17:06:00][seconds=1] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                                          VERSION  IMAGE  READY  PHASE
   --------------------------------------------  -------  -----  -----  ---------
   'sample-domain2-introspector-plssr'           ''       ''     ''     'Running'

   @@ [2020-05-13T17:07:03][seconds=64] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-13T17:07:03][seconds=64] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                                          VERSION  IMAGE  READY  PHASE
   --------------------------------------------  -------  -----  -----  -----------
   'sample-domain2-introspector-plssr'           ''       ''     ''     'Succeeded'

   @@ [2020-05-13T17:07:06][seconds=67] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-13T17:07:06][seconds=67] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME  VERSION  IMAGE  READY  PHASE
   ----  -------  -----  -----  -----

   @@ [2020-05-13T17:07:14][seconds=75] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-13T17:07:14][seconds=75] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                           VERSION  IMAGE                    READY    PHASE
   -----------------------------  -------  -----------------------  -------  ---------
   'sample-domain2-admin-server'  '1'      'model-in-image:WLS-v1'  'false'  'Running'

   @@ [2020-05-13T17:07:47][seconds=108] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-13T17:07:47][seconds=108] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain2-admin-server'     '1'      'model-in-image:WLS-v1'  'true'   'Running'
   'sample-domain2-managed-server1'  '1'      'model-in-image:WLS-v1'  'false'  'Pending'

   @@ [2020-05-13T17:07:49][seconds=110] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-13T17:07:49][seconds=110] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain2-admin-server'     '1'      'model-in-image:WLS-v1'  'true'   'Running'
   'sample-domain2-managed-server1'  '1'      'model-in-image:WLS-v1'  'false'  'Running'
   'sample-domain2-managed-server2'  '1'      'model-in-image:WLS-v1'  'false'  'Pending'

   @@ [2020-05-13T17:07:50][seconds=111] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-13T17:07:50][seconds=111] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain2-admin-server'     '1'      'model-in-image:WLS-v1'  'true'   'Running'
   'sample-domain2-managed-server1'  '1'      'model-in-image:WLS-v1'  'false'  'Running'
   'sample-domain2-managed-server2'  '1'      'model-in-image:WLS-v1'  'false'  'Running'

   @@ [2020-05-13T17:08:32][seconds=153] Info: '2' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-13T17:08:32][seconds=153] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain2-admin-server'     '1'      'model-in-image:WLS-v1'  'true'   'Running'
   'sample-domain2-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'   'Running'
   'sample-domain2-managed-server2'  '1'      'model-in-image:WLS-v1'  'false'  'Running'

   @@ [2020-05-13T17:08:34][seconds=155] Info: '3' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-13T17:08:34][seconds=155] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY   PHASE
   --------------------------------  -------  -----------------------  ------  ---------
   'sample-domain2-admin-server'     '1'      'model-in-image:WLS-v1'  'true'  'Running'
   'sample-domain2-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'  'Running'
   'sample-domain2-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'  'Running'


   @@ [2020-05-13T17:08:34][seconds=155] Info: Success!

   ```
   {{% /expand %}}

1. After the `sample-domain2` domain is running, you can call its sample web application to verify that it's fully active.

   Send a web application request to the ingress controller for `sample-domain2`:

   ```shell
   $ curl -s -S -m 10 -H 'host: sample-domain2-cluster-cluster-1.mii-sample.org' \
      http://localhost:30305/myapp_war/index.jsp
   ```

   Or, if Traefik is unavailable and your `domain2` Administration Server pod is running, you can run `kubectl exec`:

   ```shell
   $ kubectl exec -n sample-domain1-ns sample-domain2-admin-server -- bash -c \
     "curl -s -S -m 10 http://sample-domain2-cluster-cluster-1:8001/myapp_war/index.jsp"
   ```

   You will see something like the following:

    ```html
    <html><body><pre>
    *****************************************************************

    Hello World! This is version 'v1' of the mii-sample JSP web-app.

    Welcome to WebLogic Server 'managed-server1'!

      domain UID  = 'sample-domain2'
      domain name = 'domain2'

    Found 1 local cluster runtime:
      Cluster 'cluster-1'

    Found min threads constraint runtime named 'SampleMinThreads' with configured count: 1

    Found max threads constraint runtime named 'SampleMaxThreads' with configured count: 10

    Found 1 local data source:
      Datasource 'mynewdatasource':  State='Running', testPool='Failed'
        ---TestPool Failure Reason---
        NOTE: Ignore 'mynewdatasource' failures until the sample's Update 4 use case.
        ---
        ...
        ... invalid host/username/password
        ...
        -----------------------------

    *****************************************************************
    </pre></body></html>

    ```

A `TestPool Failure` is expected because we will demonstrate dynamically correcting the data source attributes for `sample-domain1` in [Update 4]({{< relref "/samples/simple/domains/model-in-image/update4.md" >}}).

If you see an error other than the expected `TestPool Failure`, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

You will not be using the `sample-domain2` domain again in this sample; if you wish, you can shut it down now by calling `kubectl -n sample-domain1-ns delete domain sample-domain2`.

To remove the resources you have created in the samples, see [Cleanup]({{< relref "/samples/simple/domains/model-in-image/cleanup.md" >}}).
