# Oracle SOA on Kubernetes
This folder provides the open-source samples which has several key features to assist you with deploying and managing SOA in a Kubernetes
environment. You can do the following:

* Create SOA instances in a Kubernetes persistent volume. This persistent volume can reside in an NFS file system or other Kubernetes volume types.
* Start servers based on declarative startup parameters and desired states.
* Expose the SOA Services/Composites for external access.
* Scale SOA by starting and stopping Managed Servers on demand, or by integrating with a REST API to initiate scaling based on WLDF, Prometheus, Grafana, or other rules.
* Publish operator and WebLogic Server logs into Elasticsearch and interact with them in Kibana.
* Monitor the instance using Prometheus / Grafana

The fastest way to experience the operator is to follow the [User guide](/docs-source/userguide/managing-fmw-domains/soa-suite/_index.md).

## Need more help? Have a suggestion? Come and say, "Hello!"
We have a public Slack channel where you can get in touch with us to ask questions about using the operator or give us feedback or suggestions about
what features and improvements you would like to see. We would love to hear from you. To join our channel, please [visit this site to get an invitation](https://weblogic-slack-inviter.herokuapp.com/). The 
invitation email will include details of how to access our Slack workspace. After you are logged in, please come to `#soa-k8s` and say, "hello!"

## Additional Resources
* [Using Jdeveloper to deploy composites](docs-source/supportJDEV.md)
* [Expose T3 protocol for managed servers in SOA Domain](docs-source/enablingT3.md) 
* [Persisting SOA adapters customizations](docs-source/Persisting-SOA-Adapters-Customizations.md)

