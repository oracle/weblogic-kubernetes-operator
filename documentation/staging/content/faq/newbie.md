administration port---
title: "Answers for newcomers"
date: 2019-09-19T10:41:32-05:00
draft: false
weight: 1
description: "Answers to commonly asked newcomer questions."
---
#### What is the WebLogic Server Kubernetes Operator, how can I get started with it, where is its documentation?

It's all [here](https://oracle.github.io/weblogic-kubernetes-operator/).

#### How much does it cost?

The WebLogic Server Kubernetes Operator (the “operator”) is open source and free.

WebLogic Server is not open source. Licensing is required for each running WebLogic Server instance, just as with any deployment of WebLogic Server. Licensing is free for a single developer desktop development environment.

#### How can I get help?

We have a public Slack channel where you can get in touch with us to ask questions about using the operator or give us feedback or suggestions about what features and improvements you would like to see. To join our channel, please [visit this site to get an invitation](https://weblogic-slack-inviter.herokuapp.com/).  The invitation email will include details of how to access our Slack workspace.  After you are logged in, please come to `#operator` and say, "hello!"

#### WebLogic Server Certification

**Q:** Which Java EE profiles are supported/certified on Kubernetes, only Web Profile or WLS Java EE full blown?

**A:** We support the full Java EE Profile.


#### WebLogic Server Configuration

**Q:** How is the WebLogic Server domain configured in a Docker container (for example, databases, JMS, and such) that is potentially shared by many domains?

**A:** In a Kubernetes and Docker environment, the WebLogic domain home can be externalized to a persistent volume, or supplied in an image (by using a layer on top of a WebLogic Server image). For WebLogic domains that are supplied using an image, the domain logs and store locations optionally can be located on a persistent volume.  See the [samples]({{< relref "/samples/simple/_index.md" >}}) in this project.

When using the operator, each deployed domain is specified by a domain resource that you define which describes important aspects of the domain. These include the location of the WebLogic Server image you wish to use, a unique identifier for the domain called the `domain-uid`, any PVs or PVC the domain pods will need to mount, the WebLogic clusters and servers which you want to be running, and the location of its domain home.

Multiple deployments of the same domain are supported by specifying a unique `domain-uid` string for each deployed domain and specifying a different domain resource. The `domain-uid` is in turn used by the operator as the name-prefix and/or label for the domain's Kubernetes resources that the operator deploys for you. The WebLogic configuration of a domain's deployments optionally can by customized by specifying configuration overrides in the domain resource -- which, for example, is useful for overriding the configuration of a data source URL, user name, or password.

The operator does not specify how a WebLogic domain home configuration is created. You can use WLST, REST, or a very convenient new tool called [WebLogic Server Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling) (WDT). WDT allows you to compactly specify WebLogic configuration and deployments (including JMS, data sources, applications, authenticators, and such) using a YAML file and a ZIP file (which include the binaries). The operator [samples]({{< relref "/samples/simple/_index.md" >}}) show how to create domains using WLST and using WDT.


**Q:** Is the Administration Server required? Node Manager?

**A:** Certification of both WebLogic on Docker and WebLogic in Kubernetes consists of a WebLogic domain with the Administration Server. The operator configures and runs Node Managers for you within a domain's pods - you don't need to configure them yourself - so their presence is largely transparent.

***

#### Communications

**Q:** How is location transparency achieved and the communication between WLS instances handled? T3s?

**A:** Inside the Kubernetes cluster, we use the cluster IP (acts as a DNS name) and ingress controller, which allows for the pods with WebLogic Servers to move to different nodes in the Kubernetes cluster and continue communicating with other servers.  

For T3 communication outside the Kubernetes cluster, we use NodePort and configure a WebLogic channel for T3 RMI.  See the blog, [T3 RMI Communication for WebLogic Server Running on Kubernetes](https://blogs.oracle.com/weblogicserver/t3-rmi-communication-for-weblogic-server-running-on-kubernetes), which explains RMI T3 communication in detail. All QoS are supported for T3 and HTTP communication (SSL, administration port).

**Q:** Are clusters supported on Kubernetes using both multicast and unicast?

**A:** Only unicast is supported. Most Kubernetes network fabrics do not support multicast communication. Weave claims to support multicast but it is a commercial network fabric.  We have certified only on Flannel, which supports unicast only.


**Q:** Docker resources: We use an SSL listener, SSL port, and an administration port on each instance. How do we map these resources?

**A:** Yes, you can use an SSL listener, SSL port, and an administration port in both Docker and Kubernetes.  See to the blog, [T3 RMI Communication for WebLogic Server Running on Kubernetes](https://blogs.oracle.com/weblogicserver/t3-rmi-communication-for-weblogic-server-running-on-kubernetes), which describes how to map these ports in Kubernetes.

***

#### Transactions

**Q:** Are XA transactions and recovery also supported?

**A:** Yes, XA transactions are supported. We are expanding our certification to include more complex cross-domain XA transaction use cases.

***

**Q:** For binding EJBs (presentation/business-tier), are unique and/or dynamic domain-names used?

**A:** We do not enforce unique domain names. If you deploy two domains that must interoperate using RMI/EJB/JMS/JTA/and such, or that share RMI/EJB/JMS/JTA/and such clients, which concurrently communicate with both domains, then, as usual, the domain names must be configured to be different (even if they have different `domain-uids`).

***

#### Load Balancers

**Q:** Load balancing and failover inside a DataCenter (HTTPS and T3s)?

**A:** We originally certified on Traefik with the Kubernetes cluster; this is a very basic load balancer.  We have also certified other more sophisticated load balancers. See [Ingress]({{< relref "/userguide/managing-domains/ingress/_index.md" >}}).

***

#### Life Cycle and Scaling

**Q:** How to deal with grow and shrink? Cluster and non-cluster mode.

**A:** You can scale and shrink a configured WebLogic cluster (a set of preconfigured Managed Servers) or a dynamic WebLogic cluster (a cluster that uses templated Managed Servers) using different methods. See the blog, [Automatic Scaling of WebLogic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/automatic-scaling-of-weblogic-clusters-on-kubernetes-v2).
* Manually, using Kubernetes command-line interface, `kubectl`.
* WLDF rules and policies; when the rule is met the Administration Server sends a REST call to the operator which calls the Kubernetes API to start a new pod/container/server.
* We have developed and made open source the [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter) which exports WebLogic metrics to Prometheus and Grafana.  In Prometheus, you can set rules similar to WLDF and when these rules are met, a REST call is made to the operator which invokes a Kubernetes API to start a new pod. See to blog, [Announcing the New WebLogic Monitoring Exporter](https://blogs.oracle.com/weblogicserver/announcing-the-new-weblogic-monitoring-exporter-v2).


**Q:** Container life cycle: How to properly spin up and gracefully shut down WLS in a container?

**A:** The operator manages container/pod/WebLogic Server life cycle automatically; it uses the Node Manager (internally) and additional approaches  to do the following operations:
* Entrypoint - start WebLogic Server.
* Liveliness probe – check if the WebLogic Server is alive.
* Readiness probe – query if the WebLogic Server is ready to receive requests.
* Shutdown Hook – gracefully shut down WebLogic Server.

These operations also can be done manually using the Kubernetes command-line interface, `kubectl`.

***


#### Patching and Upgrades

**Q:** Patching: rolling upgrades, handling of one-off-patches and overlays, CPUs, and such.

**A:** Patches are applied using OPatch and rolled out in the following fashion:
* Upgrade or patch a WebLogic cluster in a Docker environment.
* Apply PSUs for important security upgrades.
* In Dockerfile, extend the image and apply patch using OPatch.
* Operator starts rolling upgrade calling Kubernetes API.
Starting the pods/containers/servers in a rolling fashion can be invoked manually, calling the Kubernetes API directly.  See the blog, [Patching WebLogic Server in a Kubernetes Environment](https://blogs.oracle.com/weblogicserver/patching-weblogic-server-in-a-kubernetes-environment).

***

#### Security

**Q:** Certificates and CA provisioning and containers

**A:** The operator will generate self-signed certificates based on the subject alternative names that you provide and will automatically configure the key stores, and such, for SSL. The alternative is that you can provide your own certificates that you get from a CA or that you create.

***

#### Diagnostics and Logging

**Q:** Integration with Ecosystems: logging, monitoring (OS, JVM and application level), and such.

**A:** WebLogic Server logs are persisted to an external volume.  We are working on a project to integrate WebLogic Server logs with the Elastic Stack.  

With regards to monitoring, all the tools that are traditionally used to monitor WebLogic Server can be used in Docker and Kubernetes.  In addition, as mentioned previously, we have developed the [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter), which exports WebLogic metrics in a format that can be read and displayed in dashboards like Prometheus and Grafana.
