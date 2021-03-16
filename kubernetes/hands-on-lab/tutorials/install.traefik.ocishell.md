# Oracle WebLogic Server Kubernetes Operator Tutorial #

### Install and configure Traefik  ###

The Oracle WebLogic Server Kubernetes Operator supports three load balancers or ingress controllers: Traefik, Voyager, and Apache. Samples are provided in the [documentation](https://github.com/oracle/weblogic-kubernetes-operator/blob/v2.5.0/kubernetes/samples/charts/README.md).

This tutorial demonstrates how to install the [Traefik](https://traefik.io/) ingress controller to provide load balancing for WebLogic Server clusters.

#### Install the Traefik operator with a Helm chart ####

Change to your operator local Git repository folder.
```shell
$ cd ~/weblogic-kubernetes-operator/
```
Create a namespace for Traefik:
```shell
$ kubectl create namespace traefik
```
Install the Traefik operator in the `traefik` namespace with the provided sample values:
```shell
$ helm install traefik-operator \
traefik/traefik \
--namespace traefik \
--values kubernetes/samples/charts/traefik/values.yaml  \
--set "kubernetes.namespaces={traefik}" \
--set "serviceType=LoadBalancer"
```

The output should be similar to the following:
```shell
NAME: traefik-operator
LAST DEPLOYED: Fri Mar  6 20:31:53 2020
NAMESPACE: traefik
STATUS: deployed
REVISION: 1
TEST SUITE: None
NOTES:
1. Get Traefik\'s load balancer IP/hostname:

     NOTE: It may take a few minutes for this to become available.

     You can watch the status by running:

         $ kubectl get svc traefik-operator --namespace traefik -w

     Once 'EXTERNAL-IP' is no longer '<pending>':

         $ kubectl describe svc traefik-operator --namespace traefik | grep Ingress | awk '{print $3}'

2. Configure DNS records corresponding to Kubernetes ingress resources to point to the load balancer IP/hostname found in step 1
```

The Traefik installation is basically done. Verify the Traefik (load balancer) services:
```shell
$ kubectl get service -n traefik
NAME                         TYPE           CLUSTER-IP     EXTERNAL-IP      PORT(S)                      AGE
traefik-operator             LoadBalancer   10.96.227.82   158.101.24.114   443:30299/TCP,80:31457/TCP   2m27s
traefik-operator-dashboard   ClusterIP      10.96.53.132   <none>           80/TCP                       2m27s
```
Please note the EXTERNAL-IP of the *traefik-operator* service. This is the public IP address of the load balancer that you will use to access the WebLogic Server Administration Console and the sample application.

To print only the public IP address, execute this command:
```shell
$ kubectl describe svc traefik-operator --namespace traefik | grep Ingress | awk '{print $3}'
158.101.24.114
```

Verify the `helm` charts:
```shell
$ helm list -n traefik
NAME                    NAMESPACE       REVISION        UPDATED                                 STATUS          CHART           APP VERSION
traefik-operator        traefik         1               2020-03-06 20:31:53.069061578 +0000 UTC deployed        traefik-1.86.2  1.7.20  
```
You can also access the Traefik dashboard using `curl`. Use the `EXTERNAL-IP` address from the result above:
```shell
$ curl -H 'host: traefik.example.com' http://EXTERNAL_IP_ADDRESS
```
For example:
```shell
$ curl -H 'host: traefik.example.com' http://158.101.24.114
  <a href="/dashboard/">Found</a>.
```