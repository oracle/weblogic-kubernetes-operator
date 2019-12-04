---
title: "External WebLogic Clients"
date: 2019-11-21T21:23:03Z
draft: false
weight: 6
---

### Approaches

There are two supported approaches for giving external WebLogic EJB or JMS clients access to a Kubernetes hosted WebLogic cluster: [Load Balancer Tunneling](#load-balancer-tunneling) and [Kubernetes NodePorts](#kubernetes-nodeports).

{{% notice note %}}
This FAQ is for remote EJB and JMS clients - not JTA clients. The operator does not currently support external WebLogic JTA access to a WebLogic cluster, as (A) external JTA access requires each server in the cluster to be individually addressable by the client, but this conflicts with (B) the current operator requirement that a network channel in a cluster have the same port across all servers in the cluster.
{{% /notice %}}

#### Load Balancer Tunneling

The Load Balancer Tunneling approach for giving external WebLogic EJB or JMS clients access to a Kubernetes hosted WebLogic cluster involves configuring a network channel on the desired WebLogic cluster that accepts T3 protocol traffic that's tunnelled over HTTP, deploying a load balancer that redirects external HTTP network traffic to the desired WebLogic network channel, and ensuring that EJB and JMS clients specify a URL that resolves the load balancer's network address.  

Here are the specific steps:

- Configure a custom channel for the T3 protocol in WebLogic that (A) enables HTTP Tunneling, and (B) specifies an external address and port that correspond to the address and port remote clients will use to access the load balancer.  See  [Adding a WebLogic Custom Channel](#adding-a-weblogic-custom-channel) for samples and details.

- Set up a load balancer that redirects HTTP traffic to the custom channel. For a discussion of load balancers, see [Ingress]({{<relref "/userguide/managing-domains/ingress/_index.md">}}). If you're also using OKE/OCI to host your Kubernetes cluster, also see [Using an OCI Load Balancer]({{<relref "/faq/oci-lb">}}).

- __Important__: Ensure that the load balancer configures the HTTP flow to be 'sticky' - for example, a Traefik load balancer has a `sticky sessions` option. This ensures that all of the packets of a tunneling client connection flow to the same pod, otherwise the connection will stall when its packets are load balanced to a different pod.

- Remote clients can then access the custom channel using an `http://` URL instead of a `t3://` URL.

- Review the [Security Notes](#security-notes) below.

#### Kubernetes NodePorts

The Kubernetes NodePorts approach for giving external WebLogic EJB or JMS clients access to a Kubernetes hosted WebLogic cluster involves configuring a network channel on the desired WebLogic cluster that accepts T3 protocol traffic, and deploying Kubernetes NodePort that redirects external network traffic on the Kubernetes nodes to the network channel.

Here are the specific steps:

- Configure a custom channel for the T3 protocol in WebLogic that specifies an external address and port that are suitable for remote client use.  See [Adding a WebLogic Custom Channel](#adding-a-weblogic-custom-channel).

- Deploy a Kubernetes NodePort to publicly expose the WebLogic ports. See [Setting up a NodePort](#setting-up-a-nodeport).

- Review the [Security Notes](#security-notes) below.

### Adding a WebLogic Custom Channel

#### When is a WebLogic Custom Channel Needed?

WebLogic implicitly creates a multi-protocol default channel that spans the `Listen Address` and `Port` fields specified on each server in the cluster, but this channel is usually unsuitable for external network traffic from EJB and JMS clients. Instead, you may need to configure an additional dedicated WebLogic custom channel to handle remote EJB or JMS client network traffic. 

A custom channel provides a way to configure an external listen address and port for use by external clients, unlike a default channel. External listen address and/or port configuration is needed when a channel's configured listen address and/or port would not work if used to form a URL in the remote client. This is because remote EJB and JMS clients internally use their client's channel's configured network information to reconnect to WebLogic when needed. (The EJB and JMS clients do not always use the initial URL specified in the client's JNDI context.)

A custom channel can be locked down using two-way SSL as a way to prevent access by unauthorizzed external JMS and EJB clients, only accepts protocols that are explicitly enabled for the channel, and can be configured to be the only channel that accepts EJB/JMS clients that tunnel over HTTP. A default channel may often be deliberately unencrypted for convenient internal use, or, if used externally, is only used for web traffic (not tunneling traffic). In addition, a default channel supports several protocols but it's a best practice to limit the protocols that can be accessed by external clients. Finally, external clients may require access using HTTP tunneling in order to make connections, but it's often inadvisable to enable tunneling for an unsecured default channel that's already servicing external HTTP traffic. This is because enabling HTTP tunneling would potentially allow unauthorized external JMS and EJB clients unsecured access to the WebLogic cluster through the same HTTP path.

#### Configuring a WebLogic Custom Channel

The basic requirements for configuring a custom channel for remote EJB and JMS access are:

- Configure a T3 protocol network-access-point (NAP) with the same name and port on each server (the operator will set the listen address for you).

- Configure the external listen address and port on each NAP to match the address and port component of a URL your clients can use. For example, if you are providing access to remote clients using a load balancer then these should match the address and port of the load balancer.

- If you want WebLogic T3 clients to tunnel through HTTP, then enable HTTP tunneling on each NAP. This is often necessary for load balancers.

- Do _NOT_ set `outbound-enabled` to `true` on the network-access-point (the default is `false`), as this may cause internal network traffic to stall in an attempt to route through the network-access-point.

- Ensure you haven't enabled `calculated-listen-ports` for WebLogic dynamic cluster servers. The operator requires that a channel have the same port on each server in a cluster, but `calculated-listen-ports` causes the port to be different on each server.

For example, here is a snippet of a WebLogic domain's `config.xml` for channel `MyChannel` defined for a WebLogic dynamic cluster named `cluster-1`:

```
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

And here is a snippet of offline WLST code that corresponds to the above config.xml snippet:

```
  templateName = "cluster-1-template"
  cd('/ServerTemplates/%s' % templateName)
  templateChannelName = "MyChannel"
  create(templateChannelName, 'NetworkAccessPoint')
  cd('NetworkAccessPoints/%s' % templateChannelName)
  set('Protocol', 't3')
  set('ListenPort', 7999)
  set('PublicPort', 30999)
  set('HttpEnabledForThisProtocol', true)
  set('TunnelingEnabled', true)
  set('OutboundEnabled', false)
  set('Enabled', true)
  set('TwoWaySslEnabled', false)
  set('ClientCertificateEnforced', false)
```

In this example:

- WebLogic binds the custom network channel to port `7999` and the default network channel to `8001`.

- The operator will automatically create a Kubernetes service named `DOMAIN_UID-cluster-cluster-1` for both the custom and default channel.

- Internal clients running in the same Kubernetes cluster as the channel can access the cluster using `t3://DOMAIN_UID-cluster-cluster-1:8001`.

- External clients would be expected to access the cluster using the custom channel using URLs like `t3://some.public.address.com:30999` or, if using tunneling, `http://some.public.address.com:30999`.

#### WebLogic Custom Channel Notes 

- Channel configuration for a configured cluster requires configuring the same network-access-point on each server. The operator currently doesn't test or support network channels that have a different configuration on each server in the cluster.

- Additional steps are required for external clients beyond configuring the custom channel - see [Approaches](#approaches).

### Setting up a NodePort

#### Getting Started

A Kubernetes NodePort exposes a port on each machine that hosts the Kubernetes cluster where the port is accessible from outside of a Kubernetes cluster. This port redirects network traffic to pods within the Kubernetes cluster. Setting up a Kubernetes NodePort is one approach for giving external WebLogic clients access to JMS or EJBs.

If an EJB or JMS service is running on an Administration Server, then you can skip the rest of this section and use the `spec.adminServer.adminService.channels` domain resource attribute to have the operator create a NodePort for you. See [Reference - Domain resource]({{<relref "/reference/domain-resource/_index.md">}}). Otherwise, if the EJB or JMS service is running in a WebLogic cluster or standalone WebLogic Managed Server, and you desire to provide access to the service using a NodePort, then the NodePort must be deployed 'manually' - see the following sample and table.

{{% notice note %}}
Setting up a NodePort usually also requires setting up a custom network channel. See [Adding a WebLogic Custom Channel](#adding-a-weblogic-custom-channel) above.
{{% /notice %}}

#### Sample NodePort Resource

The following NodePort YAML deploys an external node port of `30999` and internal port `7999` for a domain UID of `DOMAIN_UID`, a domain name of `DOMAIN_NAME`, and a cluster name of `CLUSTER_NAME`. It assumes that `7999` corresponds to a T3 protocol port of a channel that's configured on your WebLogic cluster.

```
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

#### Table of NodePort Attributes

|Attribute|Description|
|---------|-----------|
|`metadata.name`|For this particular use case, the NodePort name can be arbitrary as long as it is DNS compatible. But, as a convention it's recommended to use `DOMAIN_UID-cluster-CLUSTER_NAME-ext`. To ensure the name is DNS compatible, use all lower case and convert any underscores (`_`) to dashes (`-`).|
|`metadata.namespace`|Must match the namespace of your WebLogic cluster.|
|`metadata.labels`|Optional. It's helpful to set a `weblogic.domainUid` label so that cleanup scripts can locate all Kubernetes resources associated with a particular domain UID.|
|`spec.type`|Must be `NodePort`.|
|`spec.externalTrafficPolicy`|Set to `Cluster` for most use cases. This may lower performance, but ensures that a client that attaches to a node without any pods that match the `spec.selector` will be rerouted to a node with pods that do match. If set to `Local`, then connections to a particular Node will only route to that Node's pods and will fail if the Node doesn't host any pods with the given `spec.selector`. It's recommended for clients of a `spec.externalTrafficPolicy: Local` NodePort to use a URL that resolves to a list of all nodes such as `t3://mynode1,mynode2:30999` so that a client connect attempt will implicitly try `mynode2` if `mynode1` fails (alternatively, use a round-robin DNS address in place of `mynode1,mynode2`).|
|`spec.sessionAffinity`|Set to `ClientIP` to ensure an HTTP tunneling connection always routes to the same pod, otherwise the connection may hang and fail.|
|`spec.selector`|Specifies a `weblogic.domainUID` and `weblogic.clusterName` to associate the NodePort resource with your cluster's pods. The operator automatically sets these labels on the WebLogic cluster pods that it deploys for you.|
|`spec.ports.name`|This name is arbitrary.|
|`spec.ports.nodePort`|The external port that clients will use. This must match the external port that's configured on the WebLogic configured channels/network-access-points. By default, Kubernetes requires that this value range from `30000` to `32767`.|
|`spec.ports.port` and `spec.targetPort`|These must match the port that's configured on the WebLogic configured channel/network-access-point(s).|

### Security Notes

- With some cloud providers, a load balancer or NodePort may implicitly expose a port to the public Internet. 

- If such a port supports a protocol suitable for WebLogic clients, note that WebLogic allows access to JNDI entries, EJB/RMI applications, and JMS by anonymous users by default.

- You can configure a custom channel with a secure protocol and two-way SSL to help prevent external access by unwanted clients. See [When is a WebLogic Custom Channel needed?](#when-is-a-weblogic-custom-channel-needed?).


### Optional Reading

- See [Run Standalone WebLogic JMS Clients on Kubernetes](https://blogs.oracle.com/weblogicserver/run-standalone-weblogic-jms-clients-on-kubernetes) for sample JMS client code and JMS configuration.

- See [T3 RMI Communication for WebLogic Server Running on Kubernetes](https://blogs.oracle.com/weblogicserver/t3-rmi-communication-for-weblogic-server-running-on-kubernetes) for a deep-level discussion of using T3 in combination with port mapping.
