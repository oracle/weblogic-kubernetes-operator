# Run with Shell Wrapper

- [Setup from Scratch](#setup-from-scratch)
   1. [Run Setup Script](#run-setup-script)
   1. [Check the Operator](#check-the-operator)
   1. [Check the WebLogic Domains](#check-the-webLogic-domains)
   1. [Check the Load Balancer](#check-the-load-balancer)
      1. [With Traefik](#with-traefik)
      1. [With Voyager](#with-voyager)
- [Customize Setup](#customize-setup)
- [Cleanup](#cleanup)

## Setup from Scratch
### Run Setup Script
**NOTE:** Before run script to setup everything automatically, there is one manual step. You need to create an empty folder which will be used as the root folder for PVs. 

```
git clone https://github.com:oracle/weblogic-kubernetes-operator.git
cd ./weblogic-kubernetes-operator/kubernetes/tutorial/
export PV_ROOT=<PVPath>  # the value is the absolute path of the PV folder you just created
./startup.sh # use Traefik as the load balancer
# Or you can choose to run startup-v.sh which will use Voyager as the load balancer.
```

If everything goes well, after the setup script finishes, you'll have all the resources deployed and running on your Kubernetes cluster. Let's check the result.

### Check the Operator
Have the operator running in namespace `weblogic-operator1`.
  ```
  $ kubectl -n weblogic-operator1 get pod
  NAME                                READY     STATUS    RESTARTS   AGE
  weblogic-operator-5fb9558ff-2tbvr   1/1       Running   0          8m
  ```

### Check the WebLogic Domains
- Have three WebLogic domains running.
  - Three domain resources deployed.
  ```
  $ kubectl get domain --all-namespaces
  NAMESPACE   NAME      AGE
  default     domain1   4h
  test1       domain2   4h
  test1       domain3   4h
  ```
  
  - Domain named `domain1` is running in namespace `default`.
  ```
  $ kubectl -n default get all  -l weblogic.domainUID=domain1,weblogic.createdByOperator=true
  NAME                          READY     STATUS    RESTARTS   AGE
  pod/domain1-admin-server      1/1       Running   0          9m
  pod/domain1-managed-server1   1/1       Running   0          8m
  pod/domain1-managed-server2   1/1       Running   0          8m
  
  NAME                                    TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)                          AGE
  service/domain1-admin-server            ClusterIP   None            <none>        30012/TCP,7001/TCP               28m
  service/domain1-admin-server-external   NodePort    10.109.14.252   <none>        30012:30712/TCP,7001:30701/TCP   28m
  service/domain1-cluster-cluster-1       ClusterIP   10.103.24.97    <none>        8001/TCP                         27m
  service/domain1-managed-server1         ClusterIP   None            <none>        8001/TCP                         27m
  service/domain1-managed-server2         ClusterIP   None            <none>        8001/TCP                         27m
  ```
  You can access the admin console via URL `http://<hostname>:30701/console` with user/pwd `weblogic/welcome1` and you can run WLST to the admin server on host machine with admin url `t3://<hostname>:30712`.
  
  - Domain named `domain2` is running in namespace `test1`.
  ```
  $ kubectl -n test1 get all  -l weblogic.domainUID=domain2,weblogic.createdByOperator=true
  NAME                          READY     STATUS    RESTARTS   AGE
  pod/domain2-admin-server      1/1       Running   0          18h
  pod/domain2-managed-server1   1/1       Running   0          18h
  pod/domain2-managed-server2   1/1       Running   0          18h

  NAME                                    TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)                          AGE
  service/domain2-admin-server            ClusterIP   None             <none>        30012/TCP,7001/TCP               18h
  service/domain2-admin-server-external   NodePort    10.105.187.192   <none>        30012:30012/TCP,7001:30703/TCP   18h
  service/domain2-cluster-cluster-1       ClusterIP   10.100.183.40    <none>        8001/TCP                         18h
  service/domain2-managed-server1         ClusterIP   None             <none>        8001/TCP                         18h
  service/domain2-managed-server2         ClusterIP   None             <none>        8001/TCP                         18h
  ```
  You can access the admin console via URL `http://<hostname>:30703/console` with user/pwd `weblogic/welcome2` and you can run WLST to the admin server on host machine with admin url `t3://<hostname>:30012`.
  
  - Domain named `domain3` is running in namespace `test1`.
  ```
  $ kubectl -n test1 get all  -l weblogic.domainUID=domain3,weblogic.createdByOperator=true
  NAME                          READY     STATUS    RESTARTS   AGE
  pod/domain3-admin-server      1/1       Running   0          18h
  pod/domain3-managed-server1   1/1       Running   0          18h
  pod/domain3-managed-server2   1/1       Running   0          18h

  NAME                                    TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)              AGE
  service/domain3-admin-server            ClusterIP   None             <none>        30012/TCP,7001/TCP   18h
  service/domain3-admin-server-external   NodePort    10.103.129.254   <none>        7001:30705/TCP       18h
  service/domain3-cluster-cluster-1       ClusterIP   10.99.80.60      <none>        8001/TCP             18h
  service/domain3-managed-server1         ClusterIP   None             <none>        8001/TCP             18h
  service/domain3-managed-server2         ClusterIP   None             <none>        8001/TCP             18h
  ```
  You can access the admin console via URL `http://<hostname>:30705/console` with user/pwd `weblogic/welcome3`. No t3 port is exported in this domain.
  
### Check the Load Balancer
#### With Traefik
- Have the Traefik controller running in namespace `traefik`.
  ```
  $ kubectl -n traefik get pod,svc
  NAME                                    READY     STATUS    RESTARTS   AGE
  pod/traefik-controller-79c699fb-fsfx7   1/1       Running   0          57m

  NAME                                   TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)                      AGE
  service/traefik-controller             NodePort    10.111.156.107   <none>        80:30305/TCP,443:30443/TCP   57m
  service/traefik-controller-dashboard   ClusterIP   10.97.135.235    <none>        80/TCP                       57m
  ```
  
- Have three Ingresses created for the three domains
  ```
  $ kubectl get ing --all-namespaces
  NAMESPACE   NAME                         HOSTS                 ADDRESS   PORTS     AGE
  default     domain1-ingress-traefik      domain1.org                     80        12m
  test1       domain2-ingress-traefik      domain2.org                     80        12m
  test1       domain3-ingress-traefik      domain3.org                     80        12m
  ```
- Access WebLogic Domains via Traefik
  - To access "WebLogic Ready App" in the cluster in `domain1`, run   
  `curl -v -H 'host: domain1.org' http://$hostname:30305/weblogic/`
  - To access "WebLogic Ready App" in the cluster in `domain2`, run  
  `curl -v -H 'host: domain2.org' http://$hostname:30305/weblogic/`
  - To access "WebLogic Ready App" in the cluster in `domain3`, run  
  `curl -v -H 'host: domain3.org' http://$hostname:30305/weblogic/`

#### With Voyager
- Have the Voyager controller running in namespace `voyager`.
  ```
  $ kubectl -n voyager get pod
  NAME                                            READY     STATUS    RESTARTS   AGE
  pod/voyager-voyager-operator-77cbfdcb86-46drn   1/1       Running   0          11h
  ```
  
- Have one Voyager Ingress created in namespace `default` which contains routing rules to the three WebLogic domains.
  ```
  $ kubectl get ingresses.voyager.appscode.com --all-namespaces
  NAMESPACE   NAME      AGE
  default     ings      11h
  ```
  This Voyager Ingress results in one HAProxy pod created and some related services created by Voyager controller.
  ```
  $ kubectl get pod,svc -l origin=voyager
  NAME                                READY     STATUS    RESTARTS   AGE
  pod/voyager-ings-86547ccf7c-fm24s   1/1       Running   0          11h

  NAME                         TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)        AGE
  service/voyager-ings         NodePort    10.104.3.242    <none>        80:30307/TCP   11h
  service/voyager-ings-stats   ClusterIP   10.111.168.56   <none>        56789/TCP      11h
  ```
  
- Access WebLogic Domains via Voyager
  - To access "WebLogic Ready App" in the cluster in `domain1`, run  
  `curl -v -H 'host: domain1.org' http://$hostname:30307/weblogic/`
  - To access "WebLogic Ready App" in the cluster in `domain2`, run  
  `curl -v -H 'host: domain2.org' http://$hostname:30307/weblogic/`
  - To access "WebLogic Ready App" in the cluster in `domain3`, run  
  `curl -v -H 'host: domain3.org' http://$hostname:30307/weblogic/`

## Customize Setup
You can create your own setup shell wrapper to do some customization, specially if you choose to run one or two domains.  
Uncomments some lines to the following file and generate your own setup shell file.
```
#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
set -e   # Exit immediately if a command exits with a non-zero status.

SECONDS=0
./domain.sh checkPV

# create operator
./operator.sh pullImages
./operator.sh create
./domain.sh createPV

# run domain1
./domain.sh createDomain1
./domain.sh waitUntilReady default domain1

# run domain2
./domain.sh createDomain2
./domain.sh waitUntilReady test1 domain2

# run domain3
./domain.sh createDomain3
./domain.sh waitUntilReady test1 domain3

# setup load balancer
./traefik.sh createCon
./traefik.sh createIng

echo "$0 took $(($SECONDS / 60)) minutes and $(($SECONDS % 60)) seconds to finish."
```

## Cleanup
```
cd weblogic-kubernetes-operator/kubernetes/tutorial/
export PV_ROOT=<PVPath>
./clean.sh  # if you run setup-v.sh, you need to run clean-v.sh to do cleanup.
# Then go to PV_ROOT and clean this folder. 
# The owner of subfolders can be different from current user since they are created by container so you need sudo to delete them.
cd $PV_ROOT
sudo rm -rf shared logs
```

