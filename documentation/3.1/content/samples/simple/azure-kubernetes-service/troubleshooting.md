---
title: "Troubleshooting"
date: 2020-11-24T18:22:31-05:00
weight: 3
description: "Troubleshooting."
---


- [Access Administration Console](#fail-to-access-administration-console): Possible causes for Administration Console inaccessibility
- [Domain debugging](#domain-debugging)
- [Pod Error](#get-pod-error-details): How to get details of the pod error
- [WebLogic Image Tool failure](#weblogic-image-tool-failure)
- [WebLogic Kubernetes Operator installation failure](#weblogic-kubernetes-operator-installation-failure)   
   - [System pods are pending](#the-aks-cluster-system-pods-are-pending)
   - [WebLogic Kubernetes Operator ErrImagePull](#weblogic-kubernetes-operator-errimagepull)
- [WSL2 bad timestamp](#wsl2-bad-timestamp)
- [Cannot attach ACR due to not being Owner of subscription](#cannot-attach-acr-due-to-not-being-owner-of-subscription)
- [Virtual Machine size is not supported](#virtual-machine-size-is-not-supported)

#### Get pod error details

You may get the following message while creating the WebLogic domain: `"the job status is not Completed!"`

```text
status on iteration 20 of 20
pod domain1-create-weblogic-sample-domain-job-nj7wl status is Init:0/1
The create domain job is not showing status completed after waiting 300 seconds.
Check the log output for errors.
Error from server (BadRequest): container "create-weblogic-sample-domain-job" in pod "domain1-create-weblogic-sample-domain-job-nj7wl" is waiting to start: PodInitializing
[ERROR] Exiting due to failure - the job status is not Completed!
```

You can get further error details by running `kubectl describe pod`, as shown here:

```bash
$ kubectl describe pod <your-pod-name>
```

This is an output example:

```bash
$ kubectl describe pod domain1-create-weblogic-sample-domain-job-nj7wl
Events:
Type     Reason       Age                  From                                        Message
----     ------       ----                 ----                                        -------
Normal   Scheduled    4m2s                 default-scheduler                           Successfully assigned default/domain1-create-weblogic-sample-domain-job-qqv6k to aks-nodepool1-58449474-vmss000001
Warning  FailedMount  119s                 kubelet, aks-nodepool1-58449474-vmss000001  Unable to mount volumes for pod "domain1-create-weblogic-sample-domain-job-qqv6k_default(15706980-73cb-11ea-b804-b2c91b494b00)": timeout expired waiting for volumes to attach or mount for pod "default"/"domain1-create-weblogic-sample-domain-job-qqv6k". list of unmounted volumes=[weblogic-sample-domain-storage-volume]. list of unattached volumes=[create-weblogic-sample-domain-job-cm-volume weblogic-sample-domain-storage-volume weblogic-credentials-volume default-token-zr7bq]
Warning  FailedMount  114s (x9 over 4m2s)  kubelet, aks-nodepool1-58449474-vmss000001  MountVolume.SetUp failed for volume "wls-azurefile" : Couldn't get secret default/azure-secrea
```

#### Fail to access Administration Console

Here are some common reasons for this failure, along with some tips to help you investigate.

* **Create WebLogic domain job fails**

  Check the deploy log and find the failure details with `kubectl describe pod podname`.
  Please go [Getting pod error details](#get-pod-error-details).

* **Process of starting the servers is still running**

   Check with `kubectl get svc` and if `domainUID-admin-server`, `domainUID-managed-server1`, and `domainUID-managed-server2` are not listed,
   we need to wait some more for the Administration Server to start.

The following output is an example of when the Administration Server has started.

```bash
$ kubectl get svc
NAME                               TYPE           CLUSTER-IP    EXTERNAL-IP     PORT(S)              AGE
domain1-admin-server               ClusterIP      None          <none>          30012/TCP,7001/TCP   7m3s
domain1-admin-server-ext           NodePort       10.0.78.211   <none>          7001:30701/TCP       7m3s
domain1-admin-server-external-lb   LoadBalancer   10.0.6.144    40.71.233.81    7001:32758/TCP       7m32s
domain1-cluster-1-lb               LoadBalancer   10.0.29.231   52.142.39.152   8001:31022/TCP       7m30s
domain1-cluster-cluster-1          ClusterIP      10.0.80.134   <none>          8001/TCP             1s
domain1-managed-server1            ClusterIP      None          <none>          8001/TCP             1s
domain1-managed-server2            ClusterIP      None          <none>          8001/TCP             1s
internal-weblogic-operator-svc     ClusterIP      10.0.1.23     <none>          8082/TCP             9m59s
kubernetes                         ClusterIP      10.0.0.1      <none>          443/TCP              16m
```

If services are up but the WLS Administration Console is still not available, use `kubectl describe domain` to check domain status.

```bash
$ kubectl describe domain domain1
```

Make sure the status of `cluster-1` is `ServersReady` and `Available`. The status of `admin-server`, `managed-server1`, and `managed-server2` should be `RUNNING`. Otherwise, the cluster is likely still in the process of becoming fully ready.

{{%expand "Click here to view the example status." %}}
```yaml
Status:
   Clusters:
   Cluster Name:      cluster-1
   Maximum Replicas:  5
   Minimum Replicas:  1
   Ready Replicas:    2
   Replicas:          2
   Replicas Goal:     2
   Conditions:
   Last Transition Time:  2020-07-06T05:39:32.539Z
   Reason:                ServersReady
   Status:                True
   Type:                  Available
   Replicas:                2
   Servers:
   Desired State:  RUNNING
   Node Name:      aks-nodepool1-11471722-vmss000001
   Server Name:    admin-server
   State:          RUNNING
   Cluster Name:   cluster-1
   Desired State:  RUNNING
   Node Name:      aks-nodepool1-11471722-vmss000001
   Server Name:    managed-server1
   State:          RUNNING
   Cluster Name:   cluster-1
   Desired State:  RUNNING
   Node Name:      aks-nodepool1-11471722-vmss000001
   Server Name:    managed-server2
   State:          RUNNING
   Cluster Name:   cluster-1
   Desired State:  SHUTDOWN
   Server Name:    managed-server3
   Cluster Name:   cluster-1
   Desired State:  SHUTDOWN
   Server Name:    managed-server4
   Cluster Name:   cluster-1
   Desired State:  SHUTDOWN
   Server Name:    managed-server5
```
{{% /expand %}}

#### Domain debugging

For some suggestions for debugging problems with Model in Image after your Domain YAML file is deployed, see [Debugging](/weblogic-kubernetes-operator/userguide/managing-domains/model-in-image/debugging/).

#### WSL2 bad timestamp

If you are running with WSL2, you may run into the [bad timestamp issue](https://github.com/microsoft/WSL/issues/4245), which blocks Azure CLI. You may see the following error:

```shell
$ kubectl get pod
Unable to connect to the server: x509: certificate has expired or is not yet valid: current time 2020-11-25T15:58:10+08:00 is before 2020-11-27T04:25:04Z
```

You can run the following command to update WSL2 system time:

```
# Fix the outdated systime time
$ sudo hwclock -s

# Check systime time
$ data
Fri Nov 27 13:07:14 CST 2020
```

#### Timeout for the operator installation

You may run into a timeout while installing the operator and get the following error:

```
$ helm install weblogic-operator kubernetes/charts/weblogic-operator \
   --namespace sample-weblogic-operator-ns \
   --set serviceAccount=sample-weblogic-operator-sa \
   --set "enableClusterRoleBinding=true" \
   --set "domainNamespaceSelectionStrategy=LabelSelector" \
   --set "domainNamespaceLabelSelector=weblogic-operator\=enabled" \
--wait
Error: timed out waiting for the condition
```

Make sure you are working with the master branch. Remove the operator and install again.

```bash
$ helm uninstall weblogic-operator -n sample-weblogic-operator-ns
release "weblogic-operator" uninstalled
```

Check out master and install the operator.

```bash
$ cd weblogic-kubernetes-operator
$ git checkout master
$ helm install weblogic-operator kubernetes/charts/weblogic-operator \
   --namespace sample-weblogic-operator-ns \
   --set serviceAccount=sample-weblogic-operator-sa \
   --set "enableClusterRoleBinding=true" \
   --set "domainNamespaceSelectionStrategy=LabelSelector" \
   --set "domainNamespaceLabelSelector=weblogic-operator\=enabled" \
   --wait
```

#### WebLogic Image Tool failure

If your version of WIT is older than 1.9.8, you will get an error running `./imagetool/bin/imagetool.sh` if the Docker buildkit is enabled.

Here is the warning message shown:

```text
failed to solve with frontend dockerfile.v0: failed to create LLB definition: failed to parse stage name "WDT_BUILD": invalid reference format: repository name must be lowercase
```

To resolve the error, either upgrade to a newer version of WIT or disable the Docker buildkit with the following commands and run the `imagetool` command again.

```bash
$ export DOCKER_BUILDKIT=0
$ export COMPOSE_DOCKER_CLI_BUILD=0
```

#### WebLogic Kubernetes Operator installation failure

Currently, we meet two cases that block the operator installation:

* The system pods in the AKS cluster are pending.
* The operator image is unavailable.

Follow these steps to dig into the error.

##### The AKS cluster system pods are pending

If system pods in the AKS cluster are pending, it will block the operator installation.

This is an error example with warning message **no nodes available to schedule pods**.

```bash
$ kubectl get pod -A
NAMESPACE                     NAME                                        READY   STATUS    RESTARTS   AGE
default                       weblogic-operator-c5c78b8b5-ssvqk           0/1     Pending   0          13m
kube-system                   coredns-79766dfd68-wcmkd                    0/1     Pending   0          3h22m
kube-system                   coredns-autoscaler-66c578cddb-tc946         0/1     Pending   0          3h22m
kube-system                   dashboard-metrics-scraper-6f5fb5c4f-9f5mb   0/1     Pending   0          3h22m
kube-system                   kubernetes-dashboard-849d5c99ff-xzknj       0/1     Pending   0          3h22m
kube-system                   metrics-server-7f5b4f6d8c-bqzrn             0/1     Pending   0          3h22m
kube-system                   tunnelfront-765bf6df59-msj27                0/1     Pending   0          3h22m
sample-weblogic-operator-ns   weblogic-operator-f86b879fd-v2xrz           0/1     Pending   0          35m

$ kubectl describe pod weblogic-operator-f86b879fd-v2xrz -n sample-weblogic-operator-ns
...
Events:
  Type     Reason            Age                 From               Message
  ----     ------            ----                ----               -------
  Warning  FailedScheduling  71s (x25 over 36m)  default-scheduler  no nodes available to schedule pods
```

If you run into this error, remove the AKS cluster and create a new one.

Run the `kubectl get pod -A` to make sure all the system pods are running.

```bash
$ kubectl get pod -A
NAMESPACE                     NAME                                        READY   STATUS    RESTARTS   AGE
kube-system                   coredns-79766dfd68-ch5b9                    1/1     Running   0          3h44m
kube-system                   coredns-79766dfd68-sxk4g                    1/1     Running   0          3h43m
kube-system                   coredns-autoscaler-66c578cddb-s5qm5         1/1     Running   0          3h44m
kube-system                   dashboard-metrics-scraper-6f5fb5c4f-wtckh   1/1     Running   0          3h44m
kube-system                   kube-proxy-fwll6                            1/1     Running   0          3h42m
kube-system                   kube-proxy-kq6wj                            1/1     Running   0          3h43m
kube-system                   kube-proxy-t2vbb                            1/1     Running   0          3h43m
kube-system                   kubernetes-dashboard-849d5c99ff-hrz2w       1/1     Running   0          3h44m
kube-system                   metrics-server-7f5b4f6d8c-snnbt             1/1     Running   0          3h44m
kube-system                   omsagent-8tf4j                              1/1     Running   0          3h43m
kube-system                   omsagent-n9b7k                              1/1     Running   0          3h42m
kube-system                   omsagent-rcmgr                              1/1     Running   0          3h43m
kube-system                   omsagent-rs-787ff54d9d-w7tp5                1/1     Running   0          3h44m
kube-system                   tunnelfront-794845c84b-v9f98                1/1     Running   0          3h44m
```

##### WebLogic Kubernetes Operator ErrImagePull

If you got an error of **ErrImagePull** from pod status, use `docker pull` to check the operator image. If an error occurs, you can switch to a version that is greater than `3.1.1`.

```bash
$ docker pull ghcr.io/oracle/weblogic-kubernetes-operator:<version>

# Example: pull 3.1.1.
$ docker pull ghcr.io/oracle/weblogic-kubernetes-operator:3.1.1
3.1.1: Pulling from oracle/weblogic-kubernetes-operator
980316e41237: Pull complete
c980371d97ea: Pull complete
db19c8ff0d12: Pull complete
550f44317ae5: Pull complete
be2e701f5ee0: Pull complete
1cb891615559: Pull complete
4f4fb700ef54: Pull complete
Digest: sha256:6b060ec1989fcb26e1acb0d0b906d81fce6b8ec2e0a30fa2b9d9099290eb6416
Status: Downloaded newer image for ghcr.io/oracle/weblogic-kubernetes-operator:3.1.1
ghcr.io/oracle/weblogic-kubernetes-operator:3.1.1
```

#### Cannot attach ACR due to not being Owner of subscription

If you're unable to create an ACR and you're using a service principal, you can use manual Role Assignments to grant access to the ACR as described in [Azure Container Registry authentication with service principals](https://docs.microsoft.com/en-us/azure/container-registry/container-registry-auth-service-principal).

First, find the `objectId` of the service principal used when the AKS cluster was created. You will need the output from `az ad sp create-for-rbac`, which you were directed to save to a file.  Within the output, you need the value of the `name` property. It will start with `http`.  Get the `objectId` with this command.

```bash
$ az ad sp show --id http://<your-name-from-the-saved-output> | grep objectId
"objectId": "nror4p30-qnoq-4129-o89r-p60n71805npp",
```

Next, assign the `acrpull` role to that service principal with this command.

```bash
$ az role assignment create --assignee-object-id <your-objectId-from-above> --scope $AKS_PERS_RESOURCE_GROUP --role acrpull
{
  "canDelegate": null,
  "condition": null,
...
  "type": "Microsoft.Authorization/roleAssignments"
}
```

After you do this, re-try the command that gave the error.

#### Virtual Machine size is not supported

If you run into the following error when creating the AKS cluster, please use an available VM size in the region.

```bash
$ az aks create \
   --resource-group $AKS_PERS_RESOURCE_GROUP \
   --name $AKS_CLUSTER_NAME \
   --node-count 2 \
   --generate-ssh-keys \
   --nodepool-name nodepool1 \
   --node-vm-size Standard_DS2_v2 \
   --location $AKS_PERS_LOCATION \
   --service-principal $SP_APP_ID \
   --client-secret $SP_CLIENT_SECRET

BadRequestError: Operation failed with status: 'Bad Request'. Details: Virtual Machine size: 'Standard_DS2_v2' is not supported for subscription subscription-id in location 'eastus'. The available VM sizes are 'basic_a0,basic_a1,basic_a2,basic_a3,basic_a4,standard_a2'. Please refer to aka.ms/aks-vm-sizes for the details.
ResourceNotFoundError: The Resource 'Microsoft.ContainerService/managedClusters/wlsaks1613726008' under resource group 'wlsresourcegroup1613726008' was not found. For more details please go to https://aka.ms/ARMResourceNotFoundFix
```

As shown in the example, you can use `standard_a2`; pay attention to the CPU and memory of that size; make sure it meets your memory requirements.
