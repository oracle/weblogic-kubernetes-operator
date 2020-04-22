# Oracle WebLogic Server Kubernetes Operator Tutorial #

### Assign a WebLogic domain to selected nodes ###

This use case is similar to the [Assign WebLogic Pods to Nodes](node.selector.ocishell.md) lab, where individual servers/pods were assigned to specific node(s). However, the focus in this use case on the license coverage.

Starting at v1.13, Kubernetes supports clusters with up to 5000(!) nodes. However, in certain situations you need to deploy a WebLogic domain on selected node(s). Using the `nodeSelector` feature, Kubernetes ensures that all WebLogic pods that belong to the domain end up on licensed worker node(s).

In this lab you will learn how to assign all the WebLogic pods (a WebLogic domain) to specific node or nodes.

#### Assigning WebLogic Servers/pods to selected nodes #####

To assign pods to nodes, you need to label the desired node with a custom tag. Then, you define the `nodeSelector` property in the domain resource definition and set the value of the label you applied on the node. Finally, you apply the domain configuration changes.

First, get the node names using `kubectl get node`:
```bash
$ kubectl get node
NAME             STATUS    ROLES     AGE       VERSION
130.61.110.174   Ready     node      11d       v1.11.5
130.61.52.240    Ready     node      11d       v1.11.5
130.61.84.41     Ready     node      11d       v1.11.5
```

In the case of OKE, the node name can be the public IP address of the node or the subnet's CIDR block's first IP address, but obviously, a unique string which identifies the node.

Now, check the current pod allocation using the detailed pod information: `kubectl get pod -n sample-domain1-ns -o wide`
```bash
$ kubectl get pod -n sample-domain1-ns -o wide
NAME                             READY     STATUS    RESTARTS   AGE       IP            NODE             NOMINATED NODE
sample-domain1-admin-server      1/1       Running   0          2m        10.244.2.33   130.61.84.41     <none>
sample-domain1-managed-server1   1/1       Running   0          1m        10.244.1.8    130.61.52.240    <none>
sample-domain1-managed-server2   1/1       Running   0          1m        10.244.0.10   130.61.110.174   <none>
sample-domain1-managed-server3   1/1       Running   0          1m        10.244.2.34   130.61.84.41     <none>
```

As you can see from the result, Kubernetes evenly deployed the 3 Managed Servers to the 3 worker nodes. In this scenario, choose one of the nodes where you want to move all the pods.

###### Labelling nodes ######

In this example, the selected node will be: `130.61.84.41`

Label this node. The label can be any string, but for now, use `node-for-weblogic`. Execute the `kubectl label nodes <nodename> <labelname>=true` command but replace your node name and label properly:
```bash
$ kubectl label nodes 130.61.84.41 node-for-weblogic=true
node/130.61.84.41 labeled
```
###### Modify the domain resource definition ######

Open your `domain.yaml` file in a text editor and find the `serverPod:` entry and insert a new property inside. The provided `domain.yaml` already contains this part (starting at around line #73); you just need to enable it by removing the `#` (comment sign) at the beginning of the lines:
```yaml
serverPod:
  env:
  [...]
  nodeSelector:
    node-for-weblogic: true
```
Be careful with the indentation. You can double check the syntax in the sample [domain.yaml](../domain.yaml) where this part is in a comment.

Save the changes and apply the new domain resource definition.
```bash
$ kubectl apply -f ~/domain.yaml
domain.weblogic.oracle/sample-domain1 configured
```
According to the changes, the operator will start to relocate servers. Poll the pod information and wait until the expected result:
```bash
$ kubectl get po -n sample-domain1-ns -o wide
NAME                             READY     STATUS    RESTARTS   AGE       IP            NODE           NOMINATED NODE
sample-domain1-admin-server      1/1       Running   0          4h        10.244.2.40   130.61.84.41   <none>
sample-domain1-managed-server1   1/1       Running   0          4h        10.244.2.43   130.61.84.41   <none>
sample-domain1-managed-server2   1/1       Running   0          4h        10.244.2.42   130.61.84.41   <none>
sample-domain1-managed-server3   1/1       Running   0          4h        10.244.2.41   130.61.84.41   <none>
```

##### Delete the label and `nodeSelector` entries in `domain.yaml` #####

To delete the node assignment, delete the node's label using the `kubectl label node <nodename> <labelname>-` command, but replace the node name properly:
```bash
$ kubectl label nodes 130.61.84.41 node-for-weblogic-
node/130.61.84.41 labeled
```
Delete or comment out the entries you added for node assignments in your `domain.yaml` file and apply:
```bash
$ kubectl apply -f ~/domain.yaml
domain.weblogic.oracle/sample-domain1 configured
```
The pod reallocation/restart will happen based on the scheduler decision.
