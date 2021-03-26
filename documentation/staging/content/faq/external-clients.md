---
title: "External WebLogic clients"
date: 2019-11-21T21:23:03Z
draft: false
weight: 11
description: "This FAQ describes approaches for giving external WebLogic clients or servers access to a Kubernetes hosted WebLogic cluster JMS or EJBs, and for giving Kubernetes hosted clients or servers access to remotely hosted WebLogic JMS or EJBs."
---

#### Contents

- [Overview](#overview)
- [Load balancer tunneling](#load-balancer-tunneling)
- [Kubernetes `NodePorts`](#kubernetes-nodeports)
- [Adding a WebLogic custom channel](#adding-a-weblogic-custom-channel)
  - [When is a WebLogic custom channel needed?](#when-is-a-weblogic-custom-channel-needed)
  - [Configuring a WebLogic custom channel](#configuring-a-weblogic-custom-channel)
  - [WebLogic custom channel notes](#weblogic-custom-channel-notes)
- [Setting up a `NodePort`](#setting-up-a-nodeport)
  - [Getting started](#getting-started)
  - [Sample `NodePort` resource](#sample-nodeport-resource)
  - [Table of `NodePort` attributes](#table-of-nodeport-attributes)
- [Security notes](#security-notes)
- [Enabling unknown host access](#enabling-unknown-host-access)
   - [When is it necessary to enable unknown host access?](#when-is-it-necessary-to-enable-unknown-host-access)
   - [How to enable unknown host access](#how-to-enable-unknown-host-access)
- [Optional reading](#optional-reading)

#### Overview

When WebLogic clients and servers are external to Kubernetes, and you want to give them access to Kubernetes hosted WebLogic cluster EJBs and JMS, there are two supported approaches: 

  * [Load balancer tunneling](#load-balancer-tunneling) (preferred)
  * [Kubernetes `NodePorts`](#kubernetes-nodeports)

Conversely, if giving a Kubernetes hosted WebLogic Server access to external WebLogic Server EJBs, JMS, or JTA, then consider the following:

  * You may need to [enable unknown host access](#enabling-unknown-host-access) on the external WebLogic Servers.
  * Plus, if the target server can be accessed only through a load balancer using HTTP:
    * [Set up an HTTP tunneling-enabled custom channel](#adding-a-weblogic-custom-channel) on the external WebLogic Servers.
    * Specify URLs on the source server that resolve to the load balancer's address and that start with `http` instead of `t3`.
    * Ensure the load balancer configures the HTTP flow to be 'sticky'.

{{% notice note %}}
The operator does not currently support external WebLogic JTA access to a Kubernetes hosted WebLogic cluster. This is because external JTA access requires each server in a cluster to be individually addressable, but this conflicts with the current operator requirement that a network channel in a cluster have the same port across all servers in the cluster.
{{% /notice %}}

#### Load balancer tunneling

Load balancer tunneling is the preferred approach for giving external clients and servers access to a Kubernetes hosted WebLogic cluster EJB and JMS. This approach involves configuring a network channel on the desired WebLogic cluster that accepts T3 protocol traffic that's tunneled over HTTP, deploying a load balancer that redirects external HTTP network traffic to the desired WebLogic network channel, and ensuring that EJB and JMS clients specify a URL that resolves the load balancer's network address.  

Here are the steps:

- In WebLogic, configure a custom channel for the T3 protocol that enables HTTP tunneling, and specifies an external address and port that correspond to the address and port remote clients will use to access the load balancer.  See  [Adding a WebLogic custom channel](#adding-a-weblogic-custom-channel) for samples and details.

- Set up a load balancer that redirects HTTP traffic to the custom channel. For more information on load balancers, see [Ingress]({{<relref "/userguide/managing-domains/ingress/_index.md">}}). If you're using OKE/OCI to host your Kubernetes cluster, also see [Using an OCI Load Balancer]({{<relref "/faq/oci-lb">}}).

- __Important__: Ensure that the load balancer configures the HTTP flow to be 'sticky' - for example, a Traefik load balancer has a `sticky sessions` option. This ensures that all of the packets of a tunneling client connection flow to the same pod, otherwise the connection will stall when its packets are load balanced to a different pod.

- If you are adding access for remote WebLogic Servers, then the Kubernetes hosted servers may need to [enable unknown host access](#enabling-unknown-host-access).

- Remote clients and servers can then access the custom channel using an `http://` URL instead of a `t3://` URL.

- Review the [Security notes](#security-notes).

#### Kubernetes `NodePorts`

Kubernetes `NodePorts` provide an alternative approach for giving external WebLogic EJB or JMS clients access to a Kubernetes hosted WebLogic cluster. This approach involves configuring a network channel on the desired WebLogic cluster that accepts T3 protocol traffic, and exposing a Kubernetes `NodePort` that redirects external network traffic on the Kubernetes Nodes to the network channel.


{{% notice note %}} The `NodePort` approach is available only when worker nodes are accessible by the clients, for example, when they have public IP addresses. If private worker nodes are used and access to them is possible only through a load balancer or bastion, then the `NodePort` approach is not a valid option to provide access to external clients.
{{% /notice %}}

Here are the steps:

- In WebLogic, configure a custom channel for the T3 protocol that specifies an external address and port that are suitable for remote client use.  See [Adding a WebLogic custom channel](#adding-a-weblogic-custom-channel).

- Define a Kubernetes `NodePort` to publicly expose the WebLogic ports. See [Setting up a `NodePort`](#setting-up-a-nodeport).

- If you are adding access for remote WebLogic Servers, then the Kubernetes hosted servers may need to [enable unknown host access](#enabling-unknown-host-access).

- Review the [Security notes](#security-notes).

#### Adding a WebLogic custom channel

##### When is a WebLogic custom channel needed?

WebLogic implicitly creates a multi-protocol default channel that spans the `Listen Address` and `Port` fields specified on each server in the cluster, but this channel is usually unsuitable for external network traffic from EJB and JMS clients. Instead, you may need to configure an additional dedicated WebLogic custom channel to handle remote EJB or JMS client network traffic.

A custom channel provides a way to configure an external listen address and port for use by external clients, unlike a default channel. External listen address or port configuration is needed when a channel's configured listen address or port would not work if used to form a URL in the remote client. This is because remote EJB and JMS clients internally use their client's channel's configured network information to reconnect to WebLogic when needed. (The EJB and JMS clients do not always use the initial URL specified in the client's JNDI context.)

A custom channel can be locked down using two-way SSL as a way to prevent access by unauthorized external JMS and EJB clients, only accepts protocols that are explicitly enabled for the channel, and can be configured to be the only channel that accepts EJB/JMS clients that tunnel over HTTP. A default channel may often be deliberately unencrypted for convenient internal use, or, if used externally, is used for web traffic (not tunneling traffic) only. In addition, a default channel supports several protocols but it's a best practice to limit the protocols that can be accessed by external clients. Finally, external clients may require access using HTTP tunneling in order to make connections, but it's often inadvisable to enable tunneling for an unsecured default channel that's already servicing external HTTP traffic. This is because enabling HTTP tunneling would potentially allow unauthorized external JMS and EJB clients unsecured access to the WebLogic cluster through the same HTTP path.

##### Configuring a WebLogic custom channel

The basic requirements for configuring a custom channel for remote EJB and JMS access are:

- Configure a T3 protocol network access point (NAP) with the same name and port on each server (the operator will set the listen address for you).

- Configure the external listen address and port on each NAP to match the address and port component of a URL your clients can use. For example, if you are providing access to remote clients using a load balancer, then these should match the address and port of the load balancer.

- If you want WebLogic T3 clients to tunnel through HTTP, then enable HTTP tunneling on each NAP. This is often necessary for load balancers.

- Do _NOT_ set `outbound-enabled` to `true` on the network access point (the default is `false`), because this may cause internal network traffic to stall in an attempt to route through the network access point.

- For operator controlled WebLogic clusters, ensure you haven't enabled `calculated-listen-ports` for WebLogic dynamic cluster servers. The operator requires that a channel have the same port on each server in a cluster, but `calculated-listen-ports` causes the port to be different on each server.

- For clusters that are _not_ operator controlled, minimally ensure that the server's default channel `ListenAddress` is configured. Oracle strongly recommends configuring a `ListenAddress` on all WebLogic Servers. Note that if a NAP's `ListenAddress` is left blank, then it will use the default channel's `ListenAddress`. (This is not a concern for operator controlled clusters as the operator sets the listen addresses on every WebLogic Server.)

For example, here is a snippet of a WebLogic domain `config.xml` file for channel `MyChannel` defined for an operator controlled WebLogic dynamic cluster named `cluster-1`:

```xml
<server-template>
  <name>cluster-1-template</name>
  <listen-port>8001</listen-port>
  <cluster>cluster-1</cluster>
  <network-access-point>
    <name>MyChannel</name>
    <protocol>t3</protocol>
    <public-address>some.public.address.com</public-address>
    <listen-port>7999</listen-port>
    <public-port>30999</public-port>
    <http-enabled-for-this-protocol>true</http-enabled-for-this-protocol>
    <tunneling-enabled>true</tunneling-enabled>
    <outbound-enabled>false</outbound-enabled>
    <enabled>true</enabled>
    <two-way-ssl-enabled>false</two-way-ssl-enabled>
    <client-certificate-enforced>false</client-certificate-enforced>
  </network-access-point>
</server-template>
<cluster>
  <name>cluster-1</name>
  <cluster-messaging-mode>unicast</cluster-messaging-mode>
  <dynamic-servers>
    <name>cluster-1</name>
    <server-template>cluster-1-template</server-template>
    <maximum-dynamic-server-count>5</maximum-dynamic-server-count>
    <calculated-listen-ports>false</calculated-listen-ports>
    <server-name-prefix>managed-server</server-name-prefix>
    <dynamic-cluster-size>5</dynamic-cluster-size>
    <max-dynamic-cluster-size>5</max-dynamic-cluster-size>
  </dynamic-servers>
</cluster>
```

And, here is a snippet of offline WLST code that corresponds to the above `config.xml` file snippet:

```javascript
  templateName = "cluster-1-template"
  cd('/ServerTemplates/%s' % templateName)
  templateChannelName = "MyChannel"
  create(templateChannelName, 'NetworkAccessPoint')
  cd('NetworkAccessPoints/%s' % templateChannelName)
  set('Protocol', 't3')
  set('ListenPort', 7999)
  set('PublicPort', 30999)
  set('PublicAddress', 'some.public.address.com')
  set('HttpEnabledForThisProtocol', true)
  set('TunnelingEnabled', true)
  set('OutboundEnabled', false)
  set('Enabled', true)
  set('TwoWaySslEnabled', false)
  set('ClientCertificateEnforced', false)
```

In this example:

- WebLogic binds the custom network channel to port `7999` and the default network channel to `8001`.

- The operator will automatically create a Kubernetes Service named `DOMAIN_UID-cluster-cluster-1` for both the custom and default channel.

- The operator will automatically set the `ListenAddress` on each WebLogic Server for each of its channels.

- Internal clients running in the same Kubernetes cluster as the channel can access the cluster using `t3://DOMAIN_UID-cluster-cluster-1:8001`.

- External clients would be expected to access the cluster using the custom channel with URLs like `t3://some.public.address.com:30999` or, if using tunneling, `http://some.public.address.com:30999`.

##### WebLogic custom channel notes

- Channel configuration for a configured cluster requires configuring the same network access point on each server. The operator currently doesn't test or support network channels that have a different configuration on each server in the cluster.

- Additional steps are required for external clients beyond configuring the custom channel - see [Overview](#overview).

#### Setting up a `NodePort`

##### Getting started

A Kubernetes `NodePort` exposes a port on each worker node in the Kubernetes cluster (they are not typically exposed on masters), where the port is accessible from outside of a Kubernetes cluster. This port redirects network traffic to pods within the Kubernetes cluster. Setting up a Kubernetes `NodePort` is one approach for giving external WebLogic clients access to JMS or EJBs.

If an EJB or JMS service is running on an Administration Server, then you can skip the rest of this section and use the `spec.adminServer.adminService.channels` Domain field to have the operator create a `NodePort` for you. See [Reference - Domain]({{<relref "/reference/domain-resource/_index.md">}}). Otherwise, if the EJB or JMS service is running in a WebLogic cluster or standalone WebLogic Server Managed Server, and you desire to provide access to the service using a `NodePort`, then the `NodePort` must be exposed 'manually' - see the following sample and table.

{{% notice note %}}
Setting up a `NodePort` usually also requires setting up a custom network channel. See [Adding a WebLogic Custom Channel](#adding-a-weblogic-custom-channel).
{{% /notice %}}

##### Sample `NodePort` resource

The following `NodePort` YAML file exposes an external node port of `30999` and internal port `7999` for a domain UID of `DOMAIN_UID`, a domain name of `DOMAIN_NAME`, and a cluster name of `CLUSTER_NAME`. It assumes that `7999` corresponds to a T3 protocol port of a channel that's configured on your WebLogic cluster.

```yaml
apiVersion: v1
kind: Service
metadata:
  namespace: default
  name: DOMAIN_UID-cluster-CLUSTER_NAME-ext
  labels:
    weblogic.domainUID: DOMAIN_UID
spec:
  type: NodePort
  externalTrafficPolicy: Cluster
  sessionAffinity: ClientIP
  selector:
    weblogic.domainUID: DOMAIN_UID
    weblogic.clusterName: CLUSTER_NAME
  ports:
  - name: myclustert3channel
    nodePort: 30999
    port: 7999
    protocol: TCP
    targetPort: 7999
```

##### Table of `NodePort` attributes

|Attribute|Description|
|---------|-----------|
|`metadata.name`|For this particular use case, the `NodePort` name can be arbitrary as long as it is DNS compatible. But, as a convention, it's recommended to use `DOMAIN_UID-cluster-CLUSTER_NAME-ext`. To ensure the name is DNS compatible, use all lower case and convert any underscores (`_`) to dashes (`-`).|
|`metadata.namespace`|Must match the namespace of your WebLogic cluster.|
|`metadata.labels`|Optional. It's helpful to set a `weblogic.domainUid` label so that cleanup scripts can locate all Kubernetes resources associated with a particular domain UID.|
|`spec.type`|Must be `NodePort`.|
|`spec.externalTrafficPolicy`|Set to `Cluster` for most use cases. This may lower performance, but ensures that a client that attaches to a node without any pods that match the `spec.selector` will be rerouted to a node with pods that do match. If set to `Local`, then connections to a particular node will route only to that node's pods and will fail if the node doesn't host any pods with the given `spec.selector`. It's recommended for clients of a `spec.externalTrafficPolicy: Local` `NodePort` to use a URL that resolves to a list of all nodes, such as `t3://mynode1,mynode2:30999`, so that a client connect attempt will implicitly try `mynode2` if `mynode1` fails (alternatively, use a round-robin DNS address in place of `mynode1,mynode2`).|
|`spec.sessionAffinity`|Set to `ClientIP` to ensure an HTTP tunneling connection always routes to the same pod, otherwise the connection may hang and fail.|
|`spec.selector`|Specifies a `weblogic.domainUID` and `weblogic.clusterName` to associate the `NodePort` resource with your cluster's pods. The operator automatically sets these labels on the WebLogic cluster pods that it deploys for you.|
|`spec.ports.name`|This name is arbitrary.|
|`spec.ports.nodePort`|The external port that clients will use. This must match the external port that's configured on the WebLogic configured channels/network access points. By default, Kubernetes requires that this value range from `30000` to `32767`.|
|`spec.ports.port` and `spec.targetPort`|These must match the port that's configured on the WebLogic configured channel/network access points.|

#### Enabling unknown host access

##### When is it necessary to enable unknown host access?

If a source WebLogic Server attempts to initiate an EJB, JMS, or JTA connection with a target WebLogic Server, then the target WebLogic Server will reject the connection by default if it cannot find the source server's listen address in its DNS. Such a failed connection attempt can yield log messages or exceptions like `"...RJVM has already been shutdown..."` or `"...address was valid earlier, but now we get..."`.

This means that it's usually necessary to enable unknown host access on an external WebLogic Server so that it can support EJB, JMS, or JTA communication that is initated by an operator hosted WebLogic Server. For example, if an operator hosted WebLogic Server with service address `mydomainuid-myservername` initiates a JMS connection to a remote WebLogic Server, then the remote server will implicitly attempt to lookup `mydomainuid-myservername` in its DNS as part of the connection setup, and this lookup will typically fail.

Similarly, this also means that it's necessary to enable unknown host access on an operator hosted WebLogic Server that accepts EJB or JMS connection requests from external WebLogic Servers when the external WebLogic Server's listen addresses cannot be resolved by the DNS running in the Kuberneters cluster.

##### How to enable unknown host access

To enable an 'unknown host' source WebLogic Server to initiate EJB, JMS, or JTA communication with a target WebLogic Server:
  * Set the `weblogic.rjvm.allowUnknownHost` Java system property to `true` on each target WebLogic Server.
    * For operator hosted WebLogic Servers, you can set this property by including `-Dweblogic.rjvm.allowUnknownHost=true` in the `JAVA_OPTIONS` [Domain environment variable]({{< relref "/userguide/managing-domains/domain-resource#jvm-memory-and-java-option-environment-variables" >}}) defined in the domain resource's `spec.serverPod.env` attribute.
  * Also apply patch 30656708 on each target WebLogic Server for versions 12.2.1.4 (PS4) or earlier.

#### Security notes

- With some cloud providers, a load balancer or `NodePort` may implicitly expose a port to the public Internet.

- If such a port supports a protocol suitable for WebLogic clients, note that WebLogic allows access to JNDI entries, EJB/RMI applications, and JMS by anonymous users by default.

- You can configure a custom channel with a secure protocol and two-way SSL to help prevent external access by unwanted clients. See [When is a WebLogic custom channel needed?](#when-is-a-weblogic-custom-channel-needed).

#### Optional reading

- For sample JMS client code and JMS configuration, see [Run Standalone WebLogic JMS Clients on Kubernetes](https://blogs.oracle.com/weblogicserver/run-standalone-weblogic-jms-clients-on-kubernetes).

- For a detailed discussion of using T3 in combination with port mapping, see [T3 RMI Communication for WebLogic Server Running on Kubernetes](https://blogs.oracle.com/weblogicserver/t3-rmi-communication-for-weblogic-server-running-on-kubernetes).
