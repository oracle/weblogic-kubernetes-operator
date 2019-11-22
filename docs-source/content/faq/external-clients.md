---
title: "External WebLogic Clients"
date: 2019-11-21T21:23:03Z
draft: false
weight: 6
---

## WIP - This is a DRAFT!

> __This document is an early draft.__
> __Do not merge.__
> __There is no need to review yet.__


### Approaches

There are at least three available approaches for supporting WebLogic EJB or JMS clients that run external to your Kubernetes cluster.

#### Federation

- Deploy the remote clients in another Kubernetes cluster.
- Federate the two clusters so that they effectively share the same DNS namespace. See [Federation](https://kubernetes.io/docs/concepts/cluster-administration/federation/) in the Kubernetes documentation.  Note that federation is a relatively new Kubernetes feature as of 2019.
- If the addresses for remote access from the client cluster to the remote cluster are not the same as the addresses used by clients running within the remote cluster itself, then it is often necessary to configure a custom channel for the T3 protocol in the remote cluster.  See TBD.

#### Load Balancer Tunneling

- Setup a load balancer. For a discussion of load balancers see TBD.
- Configure a custom channel for the T3 protocol in WebLogic that (a) enables HTTP Tunneling, and (b) specifies an external address and port that are suitable for remote client use.  See TBD
- Remote clients can then access the custom channel using an 'http://' URL instead of a 't3://' URL.

#### Kubernetes Node Ports

- Configure a custom channel for the T3 protocol in WebLogic that specifies an external address and port that are suitable for remote client use.  See TBD
- Deploy  Kubernetes NodePort to publicly expose the WebLogic ports. See TBD

### Adding a WebLogic Custom Channel

It's often necessary to configure a dedicated custom channel to handle remote EJB or JMS client network traffic. This is because:

- A default channel does not provide the opportunity to configure both an external listen address and/or port. External listen address and/or port configuration is needed when a channel's configured listen address and/or port would be incorrect for use in remote client URLs. This is because remote EJB and JMS clients access WebLogic via JNDI objects that implicitly embed WebLogic 'smart stubs' which use the client's channel's configured network information to reconnect to WebLogic as needed.
- A default channel exposes multiple protocols and it's a best practice to limit the protocols that can be accessed by external clients.
- A default channel may often be left insecure if it is only accessible internally, but it is often desirable to secure externally accessable channels (two-way SSL).

The basic requirements for configuring a custom channel for remote EJB and JMS access are:

- Configure a T3 protocol network-access-point (NAP) with the same name and same port on each server (the operator will set the listen address for you).
- Configure the external listen address on each NAP to match the address component of a URLs your clients can use.
- Ensure the external listen port on the NAP matches the port component of the URLs your clients use.
- If you want WebLogic T3 clients to tunnel through HTTP, then enable HTTP tunneling on each NAP.
- Do _NOT_ set `outbound enabled` to true on the network access point (the default is 'false').
- For example, the config.xml stanza for channel 'MyT3Channel' might look like the following in your 'config.xml':
  ```
  <server-template>
    <name>cluster-1-template</name>
    <listen-port>8001</listen-port>
    <cluster>cluster-1</cluster>
    <network-access-point>
      <name>MyChannel</name>
      <protocol>t3</protocol>
      <public-address>TBD tbarnes-1.subnet2ad2phx.devweblogicphx.oraclevcn.com</public-address>
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

### Setting up a NodePort

> __SECURITY NOTE:__  TBD

> __NOTE:__ If the EJB or JMS service is running on an Admin Server, then there are domain resource attributes available to have the Operator create Node Ports for you, see TBD.

If the EJB or JMS service is running in a WebLogic Cluster than the NodePort must be deployed manually; for example, the following deploys an external node port of 30999 and internal port 7999 for domain UID of DOMAIN_UID, a domain name of DOMAIN_NAME, and a cluster name of CLUSTER_NAME.

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

|Attribute|Description|
|---------|-----------|
|metadata.name|For this particular use case, the name can be arbitrary as long as it is DNS compatible. But, as a convention it's recommended to use `DOMAIN_UID-cluster-CLUSTER_NAME-ext`. To ensure the name is DNS compatible, use all lower case and convert any underscores (`_`) to dashes (`-`).|
|metadata.namespace|Must match the namespace of your WebLogic cluster.|
|metadata.labels weblogic.domainUid|Optional. This is helpful for cleanup scripts to locate Kubernetes resources associated with a particular domain UID.|
|spec.type|Must be `NodePort`|
|spec.externalTrafficPolicy|`Cluster` may lower performance, but is recommended. If set to `TBD`, then connections will only route to the current Node and will fail if the current Node doesn't host any pods with the given selector.|
|spec.sessionAffinity|Set to `ClientIP` to ensure an HTTP tunneling connection always routes to the same pod, otherwise the connection may hang and fail.|
|spec.selector|Specify a weblogic.domainUID and weblogic.clusterName to associate the NodePort resource with your cluster's pods. The operator automatically sets these labels on the WebLogic cluster pods that it deploys for you.|
|spec.ports.name|This name is arbitrary.|
|spec.ports.nodePort|The external port that clients will use. This must match the external port that's configured on the WebLogic configured channels/network-access-points.  Kubernetes requires that this value be set between TBD and TBD (inclusive?).|
|spec.ports.port and spec.targetPort|These must match the port that's configured on the WebLogic configured channel/network-access-point(s)|


### References

- [Lily's blog] http://foo.bar.baz "JMS in K8S TBD"
- [Shean's blog] http://foo.bar.baz "External T3 TBD"
- [URL format] http://foo.bar.baz "WL URL Doc"
- [Foreign JMS] http://foo.bar.baz "Foreign JMS Server FAQ"
- [Admin Node Ports] http://foo.bar.baz "Admin Server Custom Channels"
- [Kubernetes Federation](https://kubernetes.io/docs/concepts/cluster-administration/federation/)
- [Kubernetes Node Ports] http://foo.bar.baz

