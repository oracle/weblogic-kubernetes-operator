# Creating a production WebLogic domain run by the WebLogic operator

At this point, you've already followed the quick start and used several sample
shell scripts, helm charts and yaml files to get a tire-kicking domain up and running.

Now you want to create your own WebLogic domain, with your applications, and have the WebLogic operator run it.

This document guides you through the process.

First, you need to setup the overal environment that your domain, and other domains, will run in:

1) Install the WebLogic operator in your Kubernetes cluster (link TBD)

2) Install a load balancer in your Kubernetes cluster (link TBD)

3) Create one or more domain namespaces that domains will run in (link TBD)

4) Register these namespaces with the WebLogic operator (link TBD)

5) Register these namespaces with your load balancer (link TBD)

6) If needed, create persistent volumes that wil be used your domains

Then, you need to create your domain:

1) Create a 'domain definition' that will be used to create your domain home.  This is a WLST script or WDT model plus a set of applications.

2) Create your domain home from your domain definition (store it either in a docker image or on a persistent volume).

3) Create a secret containing the user name and password that the operator will use to start the servers in your domain.

4) Optionally create a situational configuration template that will be used to customize your domain's WebLogic configuration, e.g. to set a data source's database url and password.

5) Create a domain resource that registers your domain home with WebLogic operator.  This causes the WebLogic operator to start the servers in your domain.

6) Create an ingress that defines routing rules for your domain's clusters and servers.  This causes your load balancer to route traffic to your domain's servers.

## Persistent volumes

You'll need to create one or more persistent volumes if you want to store your domain home or WebLogic server logs on a persisent volume.  If you store your domain home in a docker image, and if you don't mind storing the server logs in the server pods (therefore they go away when a server is shut down), then you don't need to create any persistent volumes.

TBD - once we ship Annisa's log exporter, which sends WebLogic log events to Elastic Search, it won't be as important to store logs on a persistent volume.

If you need some persistent volumes, follow these steps:

1) Decide how many persistent volumes you need and what you'll store on each one.  For xample:

  * one persistent volume per domain namespace that stores all the domains' homes and server logs
  * one persistent volume per domain
  * one persistent volume for all the servers' logs (and the domain homes are in docker images)
  * one persistent volume for the server logs and another for the domain homes

2) Decide which kind of persistent volume (e.g. host path, nfs)

3) Create the persistent volumes and their corresponding persistent volume claims.

  * TBD - we'll provide some samples you can start from, e.g. host path, nfs, maybe OKE, maybe OCI


## Domain Definitions

A domain definition is a directory containing a WLST script or WDT model, plus a set of applications.

The WebLogic operator provides samples that, given a domain definition, can generate a domain home, either in a docker image or on a persistent volume.

A domain definition has the following directory structure:

```
models/
    model.py - a WLST script that can create your domain
  OR
    model.yaml - a WDT model that defines your domain's configuration

wlsdeploy/
  applications/
    This directory contains your domain's applications.
    They will be copied to your domain home's wlsdeploy/applications directory.
```

First, decide whether you want to design a new domain from scratch, or if you want to import an existing domain into the WebLogic operator.  Then follow these steps to create one.

### Designing a new domain from scratch

1) Design your domain's structure, e.g. servers, clusters, apps to deploy, security providers, data sources. 

2) Choose a domain name and admin username and password

3) Decide whether you want to use WLST or WDT to create your domain

    * WDT uses a simple yaml file containing the desired structure of your domain.  It's like config.xml, but simpler.  You'll need to download the WDT code.
    * WLST is a jython script that programmatically creates your domain.  It's more complicated, but contains more advanced features, like if statements and loops.  It's always available since it's part of WebLogic.

4) Make a copy of one of the WebLogic operator's sample domain definitions.  For example:

```
cp -r kubernetes/samples/domain-definitions/wdt/simple my-domain
```
or
```
cp -r kubernetes/samples/domain-definitions/wlst/simple my-domain
```
5) Customize your domain definition's WDT model (model/model.yaml) or WLST script (model/model.py)
6) Copy the applications you want to deploy to your domain definition's wlsdeploy/applications directory (and remove and sample applications that already there).


### Importing an existing domain

1) download the WDT tool (details TBD)

2) run WDT's discover tool, pointing it at your domain.  It will generate a WDT model that can be used to create a new domain with the same structure as the existing domain.  It also will generate a zip file containing the existing domain's applications. (details TBD)

3) make a directory for your domain definition

4) copy the generated WDT model to model/model.yaml

5) unzip the generated zip file containing the domain's applications to wlsdeploy/applications (details TBD)

6) Choose a domain name and admin username and password, then specify them in model/model.yaml

## Creating a domain home

At this point, you've already created your domain definition, and any persistent volumes.

Decide whether you want to store your domain home inside a docker image or on a persistent volume.

### Create a domain home in a docker image from a WDT model
Details TBD.  Similar to from WLST script but need to download the WDT zip too.

### Create a domain home in a docker image from a WLST script

1) Copy the sample Dockerfile into your domain definition's directory.  For example:
```
  cp kubernetes/samples/dockerfiles/domain-home-in-image/wlst/Dockerfile my-domain
```
2) Edit your copy of the docker file and set the domain name to your domain's name.

3) Select a name for your docker image (e.g. your domain name or domain uid).

4) Build your image.  For example:
```
  docker rmi my-domain:latest
  docker build --force-rm=true -t my-domain:latest my-domain
```

### Create a domain home on a persistent volume from a WDT model
Details TBD. This will be more complicated than domain home in image.  Something like:

1) Select the persistent volume for your domain home

2) Create a directory there for your domain?

3) Create a config map that contains your domain definition (might need to zip up the applications since config maps only are one level deep) and the WDT zip.

4) Create a job that mounts the persistent volume claim and config map, and runs WDT to create the domain home, and copies the applications to the persistent volume

### Create a domain home on a persistent volume from a WLST script
Details TBD.  Similar to WDT.

## Register your domain with the operator

You've already created your domain home, as well as any persistent volumes your domain will need.

You already know whether your domain home is in an image or on a persistent volume.

You also know the structure of your domain (e.g. the admin username and password, the server and cluster names, the port numbers, the channels).

And you've already created  a Kubernetes namespace that your domain will be added to.

The next steps are:

1) Select a domain uid for your domain

2) Create a Kubernetes secret containing the admin username and password

3) Optionally create configuration overrides for your domain (e.g. to change a datasource's database url and password).

4) Create a domain resource that registers your domain home with the WebLogic operator

### Create a Kubernetes secret containing your domain's admin username and password

TBD
* tell them to run kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh?
*  Just include sample kubernetes commands here?
* I've been shooting for the customer always copies and edits, v.s. uses sample scripts.

### Create configuration overrides for your domain
Link TBD?

Major points:

* This is meant for making minor tweaks to your domain, e.g. database urls and passwords, so that the same domain image can be used in multiple scenarios (e.g. testing, production).  Isn't meant for making major modifications to your domain's topology.

* Create a situational configuration template and store it in a config map in your domain namespace

* Store any required credentials in a secret in your domain namespace

### Create a domain resource for your domain

A domain resource tells the WebLogic operator how to manage a WebLogic domain.

Here are the steps for creating one.

1) Make some decisions about how you want to run your domain in Kubernetes

   * If your domain home is in a docker image, decide whether you want your server logs to live on a persistent volume.  If so, you'll need to know the name of its persistent volume claim. (TBD - didn't you do this earlier when you decided which persistent volumes to create?)

  * Decide on the initial replicas count for your clusters.  It can't be more than the number of servers you configured in the cluster.

  * Decide which admin server channels you want to expose outside the Kuberentes cluster
     and choose node port numbers for each one.

2) Make a copy of an sample domain yaml file, selecting the one closest to your use case
   * kubernetes/samples/domain-resources/domain-home-and-logs-in-image/domain.yaml
   * kubernetes/samples/domain-resources/domain-home-in-image-logs-on-pv/domain.yaml
   * kubernetes/samples/domain-resources/domain-home-and-logs-on-pv/domain.yaml

TBD - how many samples do we want to provide?
  * Just a few, and each is full featured, mostly commented out
  * Just a few, and each is minimal, with pointers to docs that have yaml snippets the customers can add to them.
  * ...

3) Customize your domain resource yalm file:
   * domain namespace
   * domain uid
   * image name
   * pvc name
   * admin username and password secret name
   * config override config map & secrets names
   * exported admin server channels and node ports
   * clusters' replicas

4) Use 'kubectl apply -f' to create your domain resource.  This will cause the WebLogic operator to start the servers in your domain.

5) Wait for the servers in your domain to start
e.g. use kubectl get po -n <your domain namespace> to track when your servers have started)

6) If you've exposed any of the admin server's channels outside the kubernetes cluster, then you can try to browse to the admin console.  For example:
```
http://<your host name>:<node port number you assigned to one of the admin server channels>/console
```

## Register your domain with your load balancer

You've already created your domain home created a domain resource, and the WebLogic operator has already started the servers in your domain.

You already know your domain uid, and your domain's clusters, servers and port numbers.  You also know what kind of load balancer you're using.

Now you need to tell your load balancer to start routing requests to your domain's servers.

TBD
* Do we want to tell them to use the existing kubernetes/samples/charts/ingress-per-domain helm chart?
*  Do we want to just provide sample yaml files they can copy, modify and apply instead?
* I'm leaning towards the latter.

Follow these steps:

1) Select the public address you want your customers to see, e.g. my-domain.org

2) make a copy of a sample per-domain load balancer yaml file, based on your load balancer type:
    * kubernetes/samples/load-balancer/traefik/domain-lb.yaml
    * kubernetes/load-balancer/voyager/domain-lb.yaml
    * load-balancer/apache/domain-lb.yaml (not sure apache is this simple...)

3) Customize your load balancer yaml file
 (domain uid, cluster names, port numbers, public addresses)

4) Use 'kubectl apply -f' to create your load balancer Kubernetes resources.  This will cause your load balancer to start the servers in your domain.

5) Verify that you can go through the load balancer to get to the ready app (to see if your servers are running), e.g. ping the WebLogic ready app on the servers in your cluster.Ping the weblogic ready app on one of your clusters
```
   NEED THE TRAILING "/" !!!!

   curl -v -H 'host: <public address for your cluster>' http://${HOSTNAME}:<load balancer port #>/weblogic/
```
