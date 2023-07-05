---
title: "Initial use case"
date: 2019-02-23T17:32:31-05:00
weight: 2
---

{{< table_of_contents >}}

{{% notice note %}}
**Before you begin**: Perform the steps in [Prerequisites]({{< relref "/samples/domains/model-in-image/prerequisites.md" >}}) and then create a Model in Image `auxiliary image` by completing the steps in [Auxiliary image creation]({{< relref "/samples/domains/model-in-image/auxiliary-image-creation.md" >}}).  
{{% /notice %}}

### Overview

In this use case, you set up an initial WebLogic domain. This involves:

  - Using the [auxiliary image](#auxiliary-image) that you previously created.
  - Creating secrets for the domain.
  - Creating a Domain YAML file for the domain that references your Secrets, auxiliary image, and a WebLogic image.

After the Domain is deployed, the operator starts an 'introspector job' that converts your models into a WebLogic configuration, and then passes this configuration to each WebLogic Server in the domain.

### Auxiliary image

The sample uses an `auxiliary image` with the name `wdt-domain-image:WLS-v1` that you created in the [Auxiliary image creation]({{< relref "/samples/domains/model-in-image/auxiliary-image-creation.md" >}}) step. The WDT model files in this auxiliary image define the WebLogic domain configuration. The image contains:
- The directory where the WebLogic Deploy Tooling software is installed (also known as WDT Home), expected in an image's `/auxiliary/weblogic-deploy` directory, by default.
- WDT model YAML, property, and archive files (expected in the directory `/auxiliary/models` by default).

### Deploy resources - Introduction

In this section, you will deploy the domain resource with the new auxliliary image to namespace `sample-domain1-ns`, including the following steps:

  - Create a Secret containing your WebLogic administrator user name and password.
  - Create a Secret containing your Model in Image runtime encryption password:
    - All Model in Image domains must supply a runtime encryption Secret with a `password` value.
    - It is used to encrypt configuration that is passed around internally by the operator.
    - The value must be kept private but can be arbitrary; you can optionally supply a different secret value every time you restart the domain.
  - Deploy a Domain YAML file that references the new image.
  - Wait for the domain's Pods to start and reach their ready state.

#### Secrets

First, create the secrets needed by the domain. You have to create the WebLogic credentials secret and any other secrets that are referenced from the macros in the WDT model file. For more details about using macros in the WDT model files, see [Working with the WDT model files]({{< relref "managing-domains/domain-on-pv/model-files.md" >}}).

Run the following `kubectl` commands to deploy the required secrets:

  **NOTE**: Substitute a password of your choice for `MY_WEBLOGIC_ADMIN_PASSWORD`. This
  password should contain at least seven letters plus one digit.

  **NOTE**: Substitute a password of your choice for `MY_RUNTIME_PASSWORD`. It should
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

#### Domain resource

Now, you create a Domain YAML file. A Domain is the key resource that tells the operator how to deploy a WebLogic domain.

Copy the contents of the [domain resource YAML file](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml) file to a file called `/tmp/sample/mii-initial-domain.yaml` or similar. Alternatively, you can use the file `/tmp/sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml` that is included in the sample source.
This file contains both the domain resource and the referenced cluster resource definition.  See [Domain and Cluster resources]({{< relref "/managing-domains/domain-resource">}}).

Click [here](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml) to view the Domain YAML file.

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

**NOTE**: The web application will display a list of any data sources it finds, but at this point, we don't expect it to find any because the model doesn't contain any.

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
