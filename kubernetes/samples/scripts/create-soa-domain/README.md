# Oracle SOA on Kubernetes

The WebLogic Kubernetes operator supports deployment of SOA Suite components such as Oracle Service-Oriented Architecture (SOA), Oracle Service Bus (OSB), and Oracle Enterprise Scheduler (ESS). 

{{% notice warning %}}
Oracle SOA Suite is currently only supported for non-production use in Docker and Kubernetes.  The information provided
in this document is a *preview* for early adopters who wish to experiment with Oracle SOA Suite in Kubernetes before
it is supported for production use.
{{% /notice %}}

In this release, SOA Suite domains are supported using the “domain on a persistent volume”
[model](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/choosing-a-model/) only, where the domain home is located in a persistent volume (PV).

The operator has several key features to assist you with deploying and managing SOA domains in a Kubernetes
environment. You can:

* Create SOA instances in a Kubernetes persistent volume. This persistent volume can reside in an NFS file system or other Kubernetes volume types.
* Start servers based on declarative startup parameters and desired states.
* Expose the SOA Services and Composites for external access.
* Scale SOA domains by starting and stopping Managed Servers on demand, or by integrating with a REST API to initiate scaling based on WLDF, Prometheus, Grafana, or other rules.
* Publish operator and WebLogic Server logs into Elasticsearch and interact with them in Kibana.
* Monitor the SOA instance using Prometheus and Grafana

#### Limitations

Compared to running a WebLogic Server domain in Kubernetes using the operator, the
following limitations currently exist for SOA Suite domains:

* Custom Classes and Jar Files cannot be added to a SOA composite application.
* JMS Migration is not supported.
* ATP Database is not supported.

Refer to the [User guide](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-fmw-domains/soa-suite/) for more limitations for this release.

The fastest way to experience the operator is to follow the [User guide](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-fmw-domains/soa-suite/), or you can read our [blogs](link-to-be-added), or try out the [samples](https://oracle.github.io/weblogic-kubernetes-operator/samples/simple/domains/soa-domain/).

***
The [current release of the operator](https://github.com/oracle/weblogic-kubernetes-operator/releases) is 2.4.0.
This release was published on November 15, 2019.
***

## Need more help? Have a suggestion? Come and say, "Hello!"
We have a public Slack channel where you can get in touch with us to ask questions about using the operator or give us feedback or suggestions about
what features and improvements you would like to see. We would love to hear from you. To join our channel, please [visit this site to get an invitation](https://weblogic-slack-inviter.herokuapp.com/). The
invitation email will include details of how to access our Slack workspace. After you are logged in, please come to `#soa-k8s` and say, "hello!"

## Contributing to this project
For contributions to this project follow these [guidelines](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/README.md#contributing-to-the-operator)

