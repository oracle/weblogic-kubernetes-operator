# Tutorial

This tutorial will teach you how to run WebLogic domains in a Kubernetes environment using the operator.  
This tutorial provides reliable and automated scripts to setup everything from scratch. 
It takes less than 20 minutes to complete. After finished, you'll have three running WebLogic domains which 
cover the three typical domain configurations.

This tutorial covers following steps:

1. Pulling the required docker images.

1. Installing WebLogic operator.

1. Installing a load balancer controller: Traefik or Voyager.

1. Creating three WebLogic domains.

   - `domain1`: domain-home-in-image case
   
   - `domain2`: domain-home-in-image case with logs on a volume
   
   - `domain3`: domain-home-on-pv case
   
1. Creating Ingress for WebLogic domains.

## Directory Structure Explained
The following is the directory structure of this Quick Start:
```
$ tree
.
├── clean.sh
├── clean-v.sh
├── domain1
│   └── domain1.yaml
├── domain2
│   ├── domain2.yaml
│   ├── pvc.yaml
│   └── pv.yaml
├── domain3
│   ├── domain3.yaml
│   ├── pvc.yaml
│   └── pv.yaml
├── domainHomeBuilder
│   ├── build.sh
│   ├── Dockerfile
│   ├── generate.sh
│   └── scripts
│       ├── create-domain.py
│       └── create-domain.sh
├── domain.sh
├── ings
│   └── voyager-ings.yaml
├── operator.sh
├── README.md
├── setup.sh
├── setup-v.sh
├── traefik.sh
└── voyager.sh

5 directories, 17 files
```

An overview of what each of these does:
- `domainHomeBuilder`: This folder contains one Dockfile, one WLST file and some shell scripts to create domain home.

  - `build.sh`: To build a docker image with a domain home in it via calling `docker build`. The generated image name is `<domainName>-image` which will be used in domain-home-in-image case.  
    `usage: ./build.sh domainName adminUser adminPwd`
    
  - `generate.sh`: To create a domain home on a host folder via calling `docker run`. And later this folder will be mounted via a PV and used in domain-home-on-pv case.  
    `usage: ./generate.sh domainName adminUser adminPwd`
    
  - `Dockerfile`: Simple docker file to build a image with a domain home in it.
  
  - `scripts/create-domain.py`: A python script which uses offline WLST to create a domain home.
  
  - `scripts/create-domain.sh`: A simple shell wrapper to call create-domain.py.
  
- shell scripts

  - `operator.sh`: To pull and delete required docker images and to create and delete the wls operator.
  
  - `traefik.sh`: To create and delete Traefik controller and Ingresses.
  
  - `voyager.sh`: To create and delete Voyager controller and Ingresses.
  
  - `domain.sh`: To create and delete WebLogic domain related resources.
  
  - `setup.sh`: a shell wrapper with all steps to create all from scratch using Traefik as load balancer. 
  
  - `clean.sh`: a shell wrapper to do the cleanup. 
  
  - `setup-v.sh`: a shell wrapper with all steps to create all from scratch using Voyager as load balancer. 
    
  - `clean-v.sh`: a shell wrapper to do the cleanup after run `setup-v.sh`. 
  
- yaml files

  - `domain1`: this folder contains one yaml file which is the domain resource.
  
  - `domain2`: this folder contains three yaml file: domain resource, PV&PVC yamls to store domain/server logs.
  
  - `domain3`: this folder contains three yaml file: domain resource, PV&PVC yamls to mount the domain home host folder.
  
  - `ings`: this folder contains ingress yaml files.
  
## Prerequisites
  - Have Docker installed, a Kubernetes cluster running and have `kubectl` installed and configured. If you need help on this, check out our [cheat sheet](../../site/k8s_setup.md).
  - Have Helm installed: both the Helm client (helm) and the Helm server (Tiller). See [official helm doc](https://github.com/helm/helm/blob/master/docs/install.md) for detail.

## To setup from scratch
Before run script to setup everything automatically, there is one step needs to be done manually. You need to create an empty folder which will be used as the root folder for PVs. 

```
git clone https://github.com:oracle/weblogic-kubernetes-operator.git
cd ./weblogic-kubernetes-operator/kubernetes/tutorial/
export PV_ROOT=<PVPath>  # the value is the absolute path of the folder you just created
./startup.sh
# Or you can choose to run startup-v.sh which will use Voyager as the load balancer.
```

If everything goes well, after the `setup.sh` script finishes, you'll have all the resources deployed and running on your Kuberntes cluster. Let's check the result.
- Have the Traefik controller running in namespace `traefik`.
  ```
  $ kubectl -n traefik get pod
  NAME                               READY     STATUS    RESTARTS   AGE
  traefik-operator-7c6b5767c-6nt92   1/1       Running   0          10m
  ```
  
- Have the operator running in namespace `weblogic-operator1`.
  ```
  $ kubectl -n weblogic-operator1 get pod
  NAME                                READY     STATUS    RESTARTS   AGE
  weblogic-operator-5fb9558ff-2tbvr   1/1       Running   0          8m
  ```

- Have three WebLogic domains running.
  - Three domain resources deployed.
  ```
  $ kubectl get domain --all-namespaces
  NAMESPACE   NAME      AGE
  default     domain1   4h
  test1       domain2   4h
  test1       domain3   4h
  ```
  
  - One domain named `domain1` running in namespace `default`.
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
  
  - Two domain named `domain2` and `domain3` running in namespace `test1`.
  ```
  
  $ kubectl -n test1 get all  -l weblogic.domainUID,weblogic.createdByOperator=true
  NAME                          READY     STATUS    RESTARTS   AGE
  pod/domain2-admin-server      1/1       Running   0          9m
  pod/domain2-managed-server1   1/1       Running   0          8m
  pod/domain2-managed-server2   1/1       Running   0          8m
  pod/domain3-admin-server      1/1       Running   0          9m
  pod/domain3-managed-server1   1/1       Running   0          6m
  pod/domain3-managed-server2   1/1       Running   0          6m
  
  NAME                                    TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)                          AGE
  service/domain2-admin-server            ClusterIP   None             <none>        30012/TCP,7001/TCP               28m
  service/domain2-admin-server-external   NodePort    10.105.187.192   <none>        30012:30012/TCP,7001:30703/TCP   28m
  service/domain2-cluster-cluster-1       ClusterIP   10.100.183.40    <none>        8001/TCP                         27m
  service/domain2-managed-server1         ClusterIP   None             <none>        8001/TCP                         27m
  service/domain2-managed-server2         ClusterIP   None             <none>        8001/TCP                         27m
  service/domain3-admin-server            ClusterIP   None             <none>        30012/TCP,7001/TCP               28m
  service/domain3-admin-server-external   NodePort    10.103.129.254   <none>        7001:30705/TCP                   28m
  service/domain3-cluster-cluster-1       ClusterIP   10.99.80.60      <none>        8001/TCP                         26m
  service/domain3-managed-server1         ClusterIP   None             <none>        8001/TCP                         26m
  service/domain3-managed-server2         ClusterIP   None             <none>        8001/TCP                         26m
 ```
  
- Have three Ingresses for the three domains
  ```
  $ kubectl get Ingress --all-namespaces
  NAMESPACE   NAME                         HOSTS                 ADDRESS   PORTS     AGE
  default     domain1-ingress-traefik      domain1.org                     80        12m
  test1       domain2-ingress-traefik      domain2.org                     80        12m
  test1       domain3-ingress-traefik      domain3.org                     80        12m
  ```
 
## To cleanup
```
cd weblogic-kubernetes-operator/kubernetes/tutorial/
export PV_ROOT=<PVPath>
./clean.sh
# if you run setup-v.sh, you need to run clean-v.sh to do cleanup.
```
