# Tutorial

1. [Introduction](#introduction)
1. [Directory Structure Explained](#directory-structure-explained)
1. [Prerequisites Before Run](#prerequisites-before-run)
1. [Run with Shell Wrapper](#run-with-shell-wrapper)

## Introduction
This tutorial will teach you how to run WebLogic domains in a Kubernetes environment using the operator.  
This tutorial covers following steps:

1. Install WebLogic domains with the operator.
   1. Install the operator.
   1. Install WebLogic domains.  
   Three domains with different configurations will be covered:
      - `domain1`: domain-home-in-image 
   
      - `domain2`: domain-home-in-image and server-logs-on-pv
   
      - `domain3`: domain-home-on-pv
   
1. Configure load balancer to WebLogic domains.
   1. Install an Ingress controller: Traefik or Voyager.
   1. Install Ingress.

## Directory Structure Explained
The following is the directory structure of this tutorial:
```
├── clean.sh
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
│   ├── cleanpv
│   │   ├── run.sh
│   │   └── scripts
│   │       └── clean.sh
│   ├── wdt
│   │   ├── build.sh
│   │   ├── Dockerfile
│   │   ├── generate.sh
│   │   └── scripts
│   │       ├── create-domain.sh
│   │       ├── domain.properties
│   │       ├── simple-topology-with-app.yaml
│   │       └── simple-topology.yaml
│   └── wlst
│       ├── build.sh
│       ├── Dockerfile
│       ├── generate.sh
│       └── scripts
│           ├── create-domain.py
│           └── create-domain.sh
├── domain.sh
├── ings
│   └── voyager-ings.yaml
├── operator.sh
├── README.md
├── runTests.sh
├── setenv.sh
├── setup.sh
├── shell-wrapper.md
├── traefik.sh
├── voyager.sh
└── waitUntil.sh

11 directories, 33 files
```

An overview of what each of these does:
- `domainHomeBuilder`: This folder contains Dockfile, WLST, WDT  and shell scripts to create and clean domain home.
  - There are three subfolders: `cleanpv`, `wlst` and `wdt`.   
    The folder `cleanpv` is to clean the folder of PV.
    The folder `wlst` is to create domain with WLST and the folder `wdt` is to create domain with WDT. This two folder has similar structure and files.

    - `build.sh`: To build a docker image with a domain home in it via calling `docker build`. The generated image name is `<domainName>-image` which will be used in domain-home-in-image case.  
      `usage: ./build.sh domainName adminUser adminPwd`
    
    - `generate.sh`: To create a domain home on a host folder via calling `docker run`. And later this folder will be mounted via a PV and used in domain-home-on-pv case.  
      `usage: ./generate.sh domainName adminUser adminPwd`
    
    - `Dockerfile`: Simple docker file to build a image with a domain home in it.
  
    - `scripts`: This folder contains the scripts and configuration files to create domain.

- yaml files

  - folder `domain1`: contains yaml files for domain1.
  
  - folder `domain2`: contains yaml files for domain2.
  
  - folder `domain3`: contains yaml files for domain3.
  
  - folder `ings`: contains Ingress yaml files.
  
- shell scripts
  
  - `setenv.sh`: To setup env variables. Need to run it with `source` so other shell scripts can use the variables set by it.
  
  - `operator.sh`: To create and delete the wls operator.
  
  - `traefik.sh`: To create and delete Traefik controller and Ingresses.
  
  - `voyager.sh`: To create and delete Voyager controller and Ingresses.
  
  - `domain.sh`: To create and delete WebLogic domain related resources.
  
  - `setup.sh`: a shell wrapper to create all from scratch.
  
  - `clean.sh`: a shell wrapper to do cleanup.
  
  - `runTest.sh`: to run unit tests of this tutorial.
  
## Prerequisites Before Run
  - Have Docker installed, a Kubernetes cluster running and have `kubectl` installed and configured. If you need help on this, check out our [cheat sheet](../../site/k8s_setup.md).
  - Have Helm installed: both the Helm client (helm) and the Helm server (Tiller). See [official helm doc](https://github.com/helm/helm/blob/master/docs/install.md) for detail.

## Run with Shell Wrapper
We provide reliable and automated scripts to setup everything from scratch.  
It takes less than 20 minutes to complete. After finished, you'll have three running WebLogic domains which 
cover the three typical domain configurations and with load balancer configured.  
With the running domains, you can experiment other operator features, like scale up/down, rolling restart etc.  
See detail [here](shell-wrapper.md).
