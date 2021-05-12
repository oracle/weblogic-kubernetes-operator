---
title: "Answers for newcomers"
date: 2019-09-19T10:41:32-05:00
draft: false
weight: 1
description: "Answers to commonly asked newcomer questions."
---
#### What is the WebLogic Kubernetes Operator, how can I get started with it, where is its documentation?

It's all [here]({{< relref "/_index.md" >}}).

#### How much does it cost?

The WebLogic Kubernetes Operator (the “operator”) is open source and free.

WebLogic Server is not open source. Licensing is required for each running WebLogic Server instance, just as with any deployment of WebLogic Server. Licensing is free for a single developer desktop development environment.

#### How can I get help?

We have a public Slack channel where you can get in touch with us to ask questions about using the operator or give us feedback or suggestions about what features and improvements you would like to see. To join our channel, please [visit this site to get an invitation](https://weblogic-slack-inviter.herokuapp.com/).  The invitation email will include details of how to access our Slack workspace.  After you are logged in, please come to `#operator` and say, "hello!"

#### WebLogic Server Certification

**Q:** Which Java EE profiles are supported/certified on Kubernetes, only Web Profile or WLS Java EE full blown?

**A:** We support the full Java EE Profile.


#### WebLogic Server Configuration

**Q:** How is the WebLogic Server domain configured in a container (for example, databases, JMS, and such) that is potentially shared by many domains?

**A:** In a Kubernetes and container environment, the WebLogic domain home can be externalized to a persistent volume, or supplied in an image (by using a layer on top of a WebLogic Server image). For WebLogic domains that are supplied using an image, the domain logs and store locations optionally can be located on a persistent volume.  See the [samples]({{< relref "/samples/simple/_index.md" >}}) in this project.

When using the operator, each deployed domain is specified by a domain resource that you define which describes important aspects of the domain. These include the location of the WebLogic Server image you wish to use, a unique identifier for the domain called the `domain-uid`, any PVs or PVC the domain pods will need to mount, the WebLogic clusters and servers which you want to be running, and the location of its domain home.

Multiple deployments of the same domain are supported by specifying a unique `domain-uid` string for each deployed domain and specifying a different domain resource. The `domain-uid` is in turn used by the operator as the name-prefix and/or label for the domain's Kubernetes resources that the operator deploys for you. The WebLogic configuration of a domain's deployments optionally can by customized by specifying configuration overrides in the domain resource -- which, for example, is useful for overriding the configuration of a data source URL, user name, or password.

The operator does not specify how a WebLogic domain home configuration is created. You can use WLST, REST, or a very convenient new tool called [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) (WDT). WDT allows you to compactly specify WebLogic configuration and deployments (including JMS, data sources, applications, authenticators, and such) using a YAML file and a ZIP file (which include the binaries). The operator [samples]({{< relref "/samples/simple/_index.md" >}}) show how to create domains using WLST and using WDT.


**Q:** Is the Administration Server required? Node Manager?

**A:** Certification of both WebLogic running in containers and WebLogic in Kubernetes consists of a WebLogic domain with the Administration Server. The operator configures and runs Node Managers for you within a domain's pods - you don't need to configure them yourself - so their presence is largely transparent.

***

#### Communications

**Q:** How is location transparency achieved and the communication between WLS instances handled?

**A:** Inside the Kubernetes cluster, the operator generates a Kubernetes ClusterIP service for each WebLogic Server pod called `DOMAINUID-WEBLOGICSERVERNAME` and for each WebLogic cluster called `DOMAINUID-cluster-CLUSTERNAME`. The operator also overrides your WebLogic network listen address configuration to reflect the service names so that you don't need to do this. The services act as DNS names, and allow the pods with WebLogic Servers to move to different nodes in the Kubernetes cluster and continue communicating with other servers.  

**Q:** How is communication from outside the Kubernetes cluster handled?

**A:**

* _HTTP communication to your applications from locations outside the cluster_: Typically, this is accomplished by deploying a load balancer that redirects traffic to your domain's Kubernetes services (the operator automatically deploys these services for you); see [Ingress]({{< relref "/userguide/managing-domains/ingress/_index.md" >}}).
For an example, see the Quick Start, [Install the operator and ingress controller]({{< relref "/quickstart/install.md" >}}).

* _JMS, EJB, and other types of RMI communication with locations outside of the Kubernetes cluster_: This is typically accomplished by tunneling the RMI traffic over HTTP through a load balancer or, less commonly accomplished by using T3 or T3S directly with Kubernetes NodePorts; see [External WebLogic clients]({{< relref "/faq/external-clients.md" >}}).

* _Access the WebLogic Server Administration Console_: This can be done through a load balancer; see the [Model in Image]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}}) sample.  Or, this can be done through a Kubernetes NodePort service; run `$ kubectl explain domain.spec.adminServer.adminService.channels`.

* _Access the WebLogic Remote Console_: This can be done using a load balancer or Kubernetes NodePort service; see [Use the Remote Console]({{< relref "/userguide/managing-domains/accessing-the-domain/admin-console.md" >}}).


**Q:** Are clusters supported on Kubernetes using both multicast and unicast?

**A:** Only unicast is supported. Most Kubernetes network fabrics do not support multicast communication. Weave claims to support multicast but it is a commercial network fabric.  We have certified on Flannel and Calico, which support unicast only.

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

**A:** You can scale and shrink a configured WebLogic cluster (a set of preconfigured Managed Servers) or a dynamic WebLogic cluster (a cluster that uses templated Managed Servers) using different methods. See [Scaling]({{< relref "/userguide/managing-domains/domain-lifecycle/scaling.md" >}}).
* Manually, using Kubernetes command-line interface, `kubectl`.
* WLDF rules and policies; when the rule is met the Administration Server sends a REST call to the operator which calls the Kubernetes API to start a new pod/container/server.
* We have developed and made open source the [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter) which exports WebLogic metrics to Prometheus and Grafana.  In Prometheus, you can set rules similar to WLDF and when these rules are met, a REST call is made to the operator which invokes a Kubernetes API to start a new pod.


**Q:** Container life cycle: How to properly spin up and gracefully shut down WLS in a container?

**A:** The operator manages container/pod/WebLogic Server life cycle automatically; it uses the Node Manager (internally) and additional approaches  to do the following operations:
* Entrypoint - start WebLogic Server.
* Liveliness probe – check if the WebLogic Server is alive.
* Readiness probe – query if the WebLogic Server is ready to receive requests.
* Shutdown hook – gracefully shut down WebLogic Server.

These operations also can be done manually using the Kubernetes command-line interface, `kubectl`.

For more information, see the [Domain life cycle]({{< relref "/userguide/managing-domains/domain-lifecycle/_index.md" >}}) documentation.

***


#### Patching and Upgrades

**Q:** Patching: rolling upgrades, handling of one-off-patches and overlays, CPUs, and such.

**A:** For relevant information, see [Patch WebLogic Server images]({{< relref "/userguide/base-images/#patch-weblogic-server-images" >}}), [Rolling restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}}), and [CI/CD considerations]({{< relref "/userguide/cicd/_index.md" >}}).

***


#### Diagnostics and Logging

**Q:** Integration with ecosystems: logging, monitoring (OS, JVM and application level), and such.

**A:** WebLogic Server `stdout` logging is echoed to their pod logs by default, and WebLogic Server file logs are optionally persisted to an external volume.  We are working on a project to integrate WebLogic Server logs with the Elastic Stack.  See [Elastic Stack]({{< relref "/samples/simple/elastic-stack/_index.md" >}}).

With regards to monitoring, all the tools that are traditionally used to monitor WebLogic Server can be used running in containers and Kubernetes.  In addition, as mentioned previously, we have developed the [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter), which exports WebLogic metrics in a format that can be read and displayed in dashboards like Prometheus and Grafana.
