# Meeting Agenda

* Background
* Demos
* Discussion

## Background

* Customers have different samples needs at different times

  * Initially they just want to get a domain up and running without a lot of fuss,
  and start to learn some of the Operator concepts

  * Then they want to  use more complex WLS and Operator features,
  gradually moving from tire-kicking to production

* The 2.0 samples are perhaps too complex for the first, and not small enough or
  flexible enough for the second

* Lily and Tom both wrote new POC samples to improve this.

* The POCs take different approaches.

## Demos

* Demo Lily's samples
* Demo Tom's samples
* Keep the focus on understanding the samples, instead of designing on the fly
  otherwise we're going to run out of time and may not get through both demos

## Discussion

* If there's any time left, let's start discussing what we want to provide for post-2.0
* Maybe we want both sets
* Maybe we want to merge them
* Where do the current samples fit in? (esp. since QA uses them)
* ...

# Tom's Demo Notes

## Agenda
1) Motivation
2) Architecture
3) Demo

## Motivation
As a user, I've already run the quickstart's tire kicking samples.
Now I want to gradually get to production:

1) Continue tire kicking, but use more WebLogic and Operator features
2) Run my applications, but in a testing environment
3) Run my applications in a production environment
4) May want to move an on-premise domain into the Operator

There's a big gap once I want to move beyond the current samples:

1) They bake-in the general shape of the domain
    and only let you adjust parts of the domain,
    e.g. # of servers, server names, port numbers,
    but it isn't easy to use other WLS features like datasources and custom apps,
    or other Operator features like customizing the pod's security context
    
2) They're hard to copy and modify because they're big
    (because they're parameterized, do a lot of error checking and have many comments)

3) The customer doesn't see the native WLS and Operator terms
    e.g. they view the world through the sample-specific inputs files,
    instead of seeing the generated WLS configuration and generated domain resource.

## Architecture

### Overview

1) Pre-requisites - the customer has already:
    a) installed the operator
    b) created one or more domain namespaces
    c) registered the domain namespaces with the operator
    d) installed a load balancer
    e) registered the domain namespaces with the load balancer
    
2) Break the process of creating a domain and registering it with the operator
    and load balancer into a number of smaller, ordered, independent steps.
    
3) Determine the common use cases for each step,
    e.g. "define the domain's topology" step:
       * simple WDT model
       * simple WLST script
       * complex WDT model
       * ...
    
4) Provide an example file for each common use case of each step:
    yaml files, Dockerfiles, WLST scripts, WDT models, applications
    no shell scripts!
    
    Sometimes it may be more appropriate to just provide documentation, e.g.

    sample kubectl commands for creating the secret with the domain's boot creds

    example WDT model and domain resource 'snippets' that can be cut and
    pasted into simpler example files.


5) The customer does the following:
    a) for each step, pick the closest use case to what they need
    b) make a copy of the corresponding example
    c) edit it (e.g. customize the domain uid, add/remove features)
    d) manually use their copy (e.g. kubectl apply -f, docker build)

This means the customer is completely in control and is always talking in native
WLS or Operator terms.

The customer also gets to mix and match various independent choices,
e.g. the shape of the domain v.s. whether to put it in an image or on a pv.

It also means the customer is operating at a low level and needs to coordinate
names across the various levels
e.g.  domain uid, cluster name, converting WLS mixed case names to K8s lowercase names.

It also means the customer needs a basic understanding of the steps and choices
they need to make at each step - i.e. these samples are NOT for initial tire kicking.

### The 'Steps'

1) create a 'domain definition'
     a set of applications plus a WDT model or a WLST script
     that defines your domain's topology and deploys these apps

2) create a persistent volume (optional)

3) create a domain home from a domain definition and optional pv

4) create a secret containing the domain's boot credentials
    (the demo does this through documentation)

5) create a sit config template that customizes your domain's WLS config (optional)
    (the demo doesn't cover this yet)

6) create a domain resource that registers your domain with the operator

7) create an ingress/... that registers your domain with your load balancer

### 'domain definition' use cases
* Simple domain from a WDT model and a test webapp
* Simple domain from a WLST script and a test webapp
* Use WDT to 'import' an existing simple domain and test webapp
* Complex domain from a WDT model and some apps (not done yet)
* ...

### 'create a persistent volume' use cases
* per domain namespace shared hostpath pv
* per domain hostpath pv
* per domain namespace shared NFS pv (not done yet)
* per domain NFS pv (not done yet)
* TBD - OKE, OCI, ... ?

### 'create a domain home' use cases
* Domain home in image from a WDT domain def
* Domain home in an image from a WLST domain def
* Domain home on a shared pv from a WDT domain def
* Domain home on a per-domain pv from a WDT domain def
* ...

### 'create a domain resource' use cases
* Domain home and logs in an image
* Domain home and logs on a shared pv
* Domain home and logs on a per-domain pv
* Domain home in an image and logs on a shared pv
* ...

### 'create an ingress' use cases
* Traefik, single cluster
* Voyager, single cluster (not done yet)
* ...

## Tom's Demo

### Prerequisites
Follow the quickstart to:
* install the operator in the weblogic-sample-operator-ns
* install traefik
* create the sample-domain1-ns namespace
* register it with the operator and traefik
* download https://github.com/oracle/weblogic-deploy-tooling/releases/download/weblogic-deploy-tooling-0.14/weblogic-deploy.zip
  wget didn't work
  curl didn't work
  firefox did, cp /Users/tmoreau/Downloads/weblogic-deploy.zip .

### WDT, simple domain in image
* domain def choice: Simple domain from a WDT model and a test webapp
* pv choice: none
* domain home choice: WDT, domain in image
* domain resource choice: domain and logs in image
* ingress choice: Traefik, single cluster
* see demo-wdt-new-domain-and-logs-in-image.md

### Import Existing Domain Using WDT
* domain def choice - Use WDT to 'import' an existing simple domain and test webapp
* pv choice: none
* domain home choice: WDT, domain in image
* domain resource choice: domain and logs in image
* ingress choice: Traefik, single cluster
* see demo-wdt-discovered-domain-and-logs-in-image.md

### WDT, simple domain on shared PV
* domain def choice: Simple domain from a WDT model and a test webapp
* pv choice: per domain namespace shared host path pv
* domain home choice: WDT, domain on shared pv
* domain resource choice: domain and logs on shared pv
* ingress choice: Traefik, single cluster
* see demo-wdt-new-domain-and-logs-on-shared-pv.md
