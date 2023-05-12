---
title: "Initial use case"
date: 2019-02-23T17:32:31-05:00
weight: 2
---

{{< table_of_contents >}}

### Overview

In this use case, you set up an initial WebLogic domain. This involves:

  - Creating an auxiliary image with:
    - A WDT archive ZIP file that contains your applications.
    - A WDT model that describes your WebLogic configuration.
    - A WDT installation that contains the binaries for running WDT.
  - Creating secrets for the domain.
  - Creating a Domain YAML file for the domain that references your Secrets, auxiliary image, and a WebLogic image.

After the Domain is deployed, the operator starts an 'introspector job' that converts your models into a WebLogic configuration, and then passes this configuration to each WebLogic Server in the domain.

{{% notice note %}}
Perform the steps in [Prerequisites for all domain types]({{< relref "/samples/domains/model-in-image/prerequisites.md" >}}) and create a Model in Image `auxiliary image` by completing the steps in [Auxiliary image creation]({{< relref "/samples/domains/model-in-image/auxiliary-image-creation.md" >}}) before performing the steps in this use case.  
If you are taking the `JRF` path through the sample, then substitute `JRF` for `WLS` in your image names and directory paths. Also note that the JRF-AI-v1 model YAML file differs from the WLS-AI-v1 YAML file (it contains an additional `domainInfo -> RCUDbInfo` stanza).
{{% /notice %}}

### Auxiliary image

The sample uses an `auxiliary image` with the name `wdt-domain-image:WLS-v1` that you created in the [Auxiliary image creation]({{< relref "/samples/domains/model-in-image/auxiliary-image-creation.md" >}}) step. The WDT model files in this auxiliary image define the WebLogic domain configuration. The image contains:
- A WebLogic Deploy Tooling installation (expected in an image’s `/auxiliary/weblogic-deploy` directory by default).
- WDT model YAML, property, and archive files (expected in the directory `/auxiliary/models` by default).

### Deploy resources - Introduction

In this section, you will deploy the new image to namespace `sample-domain1-ns`, including the following steps:

  - Create a Secret containing your WebLogic administrator user name and password.
  - Create a Secret containing your Model in Image runtime encryption password:
    - All Model in Image domains must supply a runtime encryption Secret with a `password` value.
    - It is used to encrypt configuration that is passed around internally by the operator.
    - The value must be kept private but can be arbitrary; you can optionally supply a different secret value every time you restart the domain.
  - If your domain type is `JRF`, create secrets containing your RCU access URL, credentials, and prefix.
  - Deploy a Domain YAML file that references the new image.
  - Wait for the domain's Pods to start and reach their ready state.

#### Secrets

First, create the secrets needed by both `WLS` and `JRF` type model domains. You have to create the WebLogic credentials secret and any other secrets that are referenced from the macros in the WDT model file. For more details about using macros in the WDT model files, see [Working with the WDT model files]({{< relref "managing-domains/working-with-wdt-models/model-files/_index.md" >}}).

Run the following `kubectl` commands to deploy the required secrets:

  __NOTE:__ Substitute a password of your choice for MY_WEBLOGIC_ADMIN_PASSWORD. This
  password should contain at least seven letters plus one digit.

  __NOTE:__ Substitute a password of your choice for MY_RUNTIME_PASSWORD. It should
  be unique and different than the admin password, but this is not required.

  ```shell
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-weblogic-credentials \
     --from-literal=username=weblogic --from-literal=password=MY_WEBLOGIC_ADMIN_PASSWORD
  ```
  ```shell
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-weblogic-credentials \
    weblogic.domainUID=sample-domain1
  ```
  ```shell
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-runtime-encryption-secret \
     --from-literal=password=MY_RUNTIME_PASSWORD
  ```
  ```shell
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-runtime-encryption-secret \
    weblogic.domainUID=sample-domain1
  ```

  Some important details about these secrets:

  - The WebLogic credentials secret is required and must contain `username` and `password` fields. You reference it in `spec.webLogicCredentialsSecret` field of Domain YAML and macros in the `domainInfo.AdminUserName` and `domainInfo.AdminPassWord` fields your model YAML file.
  - The Model WDT runtime secret is a special secret required by Model in Image. It must contain a `password` field and must be referenced using the `spec.model.runtimeEncryptionSecret` field in its Domain. It is used to encrypt data as it's internally passed using log files from the domain's introspector job and on to its WebLogic Server pods. It must remain the same for as long as the domain is deployed to Kubernetes but can be changed between deployments.
  - Delete a secret before creating it, otherwise the create command will fail if the secret already exists.
  - Name and label the secrets using their associated domain UID to clarify which secrets belong to which domains and make it easier to clean up a domain.
  Some important details about these secrets:

  If you're following the `JRF` path through the sample, then you also need to deploy the additional secret referenced by macros in the `JRF` model `RCUDbInfo` clause, plus an `OPSS` wallet password secret. For details about the uses of these secrets, see the [Model in Image]({{< relref "/managing-domains/model-in-image/_index.md" >}}) user documentation.

  {{%expand "Click here for the commands for deploying additional secrets for JRF." %}}

  __NOTE__: Replace `MY_RCU_SCHEMA_PASSWORD` with the RCU schema password
  that you chose in the prequisite steps when
  [setting up JRF]({{< relref "/samples/domains/model-in-image/prerequisites#additional-prerequisites-for-jrf-domains" >}}).

  ```shell
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-rcu-access \
     --from-literal=rcu_prefix=FMW1 \
     --from-literal=rcu_schema_password=MY_RCU_SCHEMA_PASSWORD \
     --from-literal=rcu_db_conn_string=oracle-db.default.svc.cluster.local:1521/devpdb.k8s
  ```
  ```shell
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-rcu-access \
    weblogic.domainUID=sample-domain1
  ```

  __NOTES__:
  - Replace `MY_OPSS_WALLET_PASSWORD` with a password of your choice.
    The password can contain letters and digits.
  - The domain's JRF RCU schema will be automatically initialized
    plus generate a JRF OPSS wallet file upon first use.
    If you plan to save and reuse this wallet file,
    as is necessary for reusing RCU schema data after a migration or restart,
    then it will also be necessary to use this same password again.

  ```shell
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-opss-wallet-password-secret \
     --from-literal=walletPassword=MY_OPSS_WALLET_PASSWORD
  ```
  ```shell
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-opss-wallet-password-secret \
    weblogic.domainUID=sample-domain1
  ```

  {{% /expand %}}

#### Domain resource

Now, you create a Domain YAML file. A Domain is the key resource that tells the operator how to deploy a WebLogic domain.

Copy the following to a file called `/tmp/sample/mii-initial.yaml` or similar, or use the file `/tmp/sample/domain-resources/WLS-AI/mii-initial-d1-WLS-AI-v1.yaml` that is included in the sample source.
Copy the contents of [the WLS domain resource YAML file](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml) file that is included in the sample source to a file called `/tmp/sample/mii-initial-domain.yaml` or similar.

Click [here](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml) to view the WLS Domain YAML file.

Click [here](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources/JRF/mii-initial-d1-JRF-v1.yaml) to view the JRF Domain YAML file.


  **NOTE**: Before you deploy the domain custom resource, ensure all nodes in your Kubernetes cluster [can access `auxiliary-image` and other images]({{< relref "/samples/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

  Run the following command to create the domain custom resource:

  ```shell
  $ kubectl apply -f /tmp/sample/mii-initial-domain.yaml
  ```

   The domain resource references the cluster resource, a WebLogic Server installation image, the secrets you defined, and a sample `auxiliary image`, which contains a traditional WebLogic configuration and a WebLogic application. For detailed information, see [Domain and cluster resources]({{< relref "/managing-domains/domain-resource.md" >}}).

#### Verify the domain
Run the following `kubectl describe domain` command to check the status and events for the created domain.

```shell
$ kubectl describe domain sample-domain1 -n sample-domain1-ns
```

#### Verify the pods
  If you run `kubectl get pods -n sample-domain1-ns --watch`, then you will see the introspector job run and your WebLogic Server pods start. The output will look something like this:

  {{%expand "Click here to expand." %}}
  ```shell
  $ kubectl get pods -n sample-domain1-ns --watch
    ```
    ```
  NAME                                         READY   STATUS    RESTARTS   AGE
  sample-domain1-introspector-lqqj9   0/1   Pending   0     0s
  sample-domain1-introspector-lqqj9   0/1   ContainerCreating   0     0s
  sample-domain1-introspector-lqqj9   1/1   Running   0     1s
  sample-domain1-introspector-lqqj9   0/1   Completed   0     65s
  sample-domain1-introspector-lqqj9   0/1   Terminating   0     65s
  sample-domain1-admin-server   0/1   Pending   0     0s
  sample-domain1-admin-server   0/1   ContainerCreating   0     0s
  sample-domain1-admin-server   0/1   Running   0     1s
  sample-domain1-admin-server   1/1   Running   0     32s
  sample-domain1-managed-server1   0/1   Pending   0     0s
  sample-domain1-managed-server2   0/1   Pending   0     0s
  sample-domain1-managed-server1   0/1   ContainerCreating   0     0s
  sample-domain1-managed-server2   0/1   ContainerCreating   0     0s
  sample-domain1-managed-server1   0/1   Running   0     2s
  sample-domain1-managed-server2   0/1   Running   0     2s
  sample-domain1-managed-server1   1/1   Running   0     43s
  sample-domain1-managed-server2   1/1   Running   0     42s
  ```
  {{% /expand %}}

For a more detailed view of this activity,
you can use the `waitForDomain.sh` sample lifecycle script.
This script provides useful information about a domain's pods and
optionally waits for its `Completed` status condition to become `True`.
A `Completed` domain indicates that all of its expected
pods have reached a `ready` state
plus their target `restartVersion`, `introspectVersion`, and `image`.
For example:
```shell
$ cd /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/domain-lifecycle
$ ./waitForDomain.sh -n sample-domain1-ns -d sample-domain1 -p Completed
```

If you see an error, then consult [Debugging]({{< relref "/managing-domains/debugging.md" >}}).

#### Invoke the web application

Now that all the initial use case resources have been deployed, you can invoke the sample web application through the Traefik ingress controller's NodePort.

**Note**: The web application will display a list of any data sources it finds, but at this point, we don't expect it to find any because the model doesn't contain any.

- Send a web application request to the load balancer for the application, as shown in the following example.

    {{< tabs groupId="config" >}}
    {{% tab name="Request from a local machine" %}}
        $ curl -s -S -m 10 -H 'host: sample-domain1-cluster-cluster-1.sample.org' http://localhost:30305/myapp_war/index.jsp

    {{% /tab %}}
    {{% tab name="Request from a remote machine" %}}

        $ K8S_CLUSTER_ADDRESS=$(kubectl cluster-info | grep DNS | sed 's/^.*https:\/\///g' | sed 's/:.*$//g')

        $ curl -s -S -m 10 -H 'host: sample-domain1-cluster-cluster-1.sample.org' http://${K8S_CLUSTER_ADDRESS}:30305/myapp_war/index.jsp

    {{% /tab %}}
    {{< /tabs >}}
- You will see output like the following:

   ```html
   <html><body><pre>
   *****************************************************************

   Hello World! This is version 'v1' of the sample JSP web-app.

   Welcome to WebLogic Server 'managed-server2'!

     domain UID  = 'sample-domain1'
     domain name = 'domain1'

   Found 1 local cluster runtime:
     Cluster 'cluster-1'

   Found min threads constraint runtime named 'SampleMinThreads' with configured count: 1

   Found max threads constraint runtime named 'SampleMaxThreads' with configured count: 10

   Found 0 local data sources:

   *****************************************************************
   </pre></body></html>
   ```

 If you want to continue to the [Update 1]({{< relref "/samples/domains/model-in-image/update1.md" >}}) use case, then leave your domain running.

 To remove the resources you have created in this sample, see [Cleanup]({{< relref "/samples/domains/model-in-image/cleanup.md" >}}).
