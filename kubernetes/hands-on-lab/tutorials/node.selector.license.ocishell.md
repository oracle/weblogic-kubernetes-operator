# Oracle WebLogic Operator Tutorial #

### Assigning WebLogic Pods to Licensed Node ###

This use case similar to described in [Assigning WebLogic Pods to Nodes lab](node.selector.ocishell.md) where individual servers/pods were asssigned to specific node(s). However the focus in this use case on the license coverage.

At v1.13, Kubernetes supports clusters with up to 5000(!) nodes. However certain software like WebLogic requires license. Using `nodeSelector` feature Kubernetes ensure that WebLogic pods end up on licenced worker node(s).

In this lab you will learn how to assign all WebLogic pods (WebLogic domain) to particular node(s).

#### Assign WebLogic servers/pods to licensed nodes #####

To assign pod(s) to node(s) you need to label the desired node with custom tag. Then define the `nodeSelector` property in the domain resource definition and set the value of the label you applied on the node. Finally apply the domain configuration changes.

First get the node names using `kubectl get node`:
```bash
$ kubectl get node
NAME             STATUS    ROLES     AGE       VERSION
130.61.110.174   Ready     node      11d       v1.11.5
130.61.52.240    Ready     node      11d       v1.11.5
130.61.84.41     Ready     node      11d       v1.11.5
```

In case of OKE the node name can be the Public IP address of the node or the subnet's CIDR Block's first IP address. But obviously a unique string which identifies the node.

Now check the current pod allocation using the detailed pod information `kubectl get pod -n sample-domain1-ns -o wide`:
```bash
$ kubectl get pod -n sample-domain1-ns -o wide
NAME                             READY     STATUS    RESTARTS   AGE       IP            NODE             NOMINATED NODE
sample-domain1-admin-server      1/1       Running   0          2m        10.244.2.33   130.61.84.41     <none>
sample-domain1-managed-server1   1/1       Running   0          1m        10.244.1.8    130.61.52.240    <none>
sample-domain1-managed-server2   1/1       Running   0          1m        10.244.0.10   130.61.110.174   <none>
sample-domain1-managed-server3   1/1       Running   0          1m        10.244.2.34   130.61.84.41     <none>
```

As you can see from the result Kubernetes evenly deployed the 3 managed servers to the 3 worker nodes. In this scenario choose one of the node where you want to move all pods.

###### Labelling ######

In this example the licensed node will be: `130.61.84.41`

Label this node. The label can be any string, but now use `licensed-for-weblogic`. Execute `kubectl label nodes <nodename> <labelname>=true` command but replace your node name and label properly:
```bash
$ kubectl label nodes 130.61.84.41 licensed-for-weblogic=true
node/130.61.84.41 labeled
```
###### Modify domain resource definition ######

Open your `domain.yaml` in text editor and find the `serverPod:` entry and insert a new property inside. The provided `domain.yaml` already contains this part (starting around line #73) you just need to enable by removing the `#` (comment sign) at the beginning of the lines:
```yaml
serverPod:
  env:
  [...]
  nodeSelector:
    licensed-for-weblogic: true
```
Be careful with the indentation. You can double check the syntax in the sample [domain.yaml](../domain.yaml) where this part turned into comment.

Save the changes and apply the new domain resource definition.
```bash
$ kubectl apply -f ~/domain.yaml
domain.weblogic.oracle/sample-domain1 configured
```
The operator according to the changes will start to relocate servers. Poll the pod information and wait until the expected result:
```bash
$ kubectl get po -n sample-domain1-ns -o wide
NAME                             READY     STATUS    RESTARTS   AGE       IP            NODE           NOMINATED NODE
sample-domain1-admin-server      1/1       Running   0          4h        10.244.2.40   130.61.84.41   <none>
sample-domain1-managed-server1   1/1       Running   0          4h        10.244.2.43   130.61.84.41   <none>
sample-domain1-managed-server2   1/1       Running   0          4h        10.244.2.42   130.61.84.41   <none>
sample-domain1-managed-server3   1/1       Running   0          4h        10.244.2.41   130.61.84.41   <none>
```

##### Delete label and `nodeSelector` entries in `domain.yaml` #####

To delete the node assignment delete the node's label using `kubectl label node <nodename> <labelname>-` command but replace the node name properly:
```bash
$ kubectl label nodes 130.61.84.41 licensed-for-weblogic-
node/130.61.84.41 labeled
```
Delete or turn into comment the entries you added for node assignment in your `domain.yaml` and apply:
```bash
$ kubectl apply -f ~/domain.yaml
domain.weblogic.oracle/sample-domain1 configured
```
The pod reallocation/restart can happen based on the scheduler decision.
