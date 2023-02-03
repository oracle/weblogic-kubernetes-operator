---
title: "External network access security"
date: 2019-03-08T19:07:36-05:00
weight: 3
description: "Remote access security."
---

#### WebLogic T3 and administrative channels

{{% notice warning %}}
Oracle recommends _not_ exposing any administrative, RMI, or T3 channels outside the Kubernetes cluster
unless absolutely necessary.
{{% /notice %}}

If exposing an administrative, RMI, EJB, JMS, or T3 capable
channel using a load balancer,
port forwarding, `NodePorts`, or similar,
then limit access by using a custom
dedicated WebLogic Server port that you have configured
with the T3 or administration protocol (a network access point)
instead of relaying the traffic to a default port,
leverage two-way SSL, use controls like security lists,
and/or set up a Bastion to provide access. A custom channel
is preferred over a default channel because a default port supports
multiple protocols.

When accessing T3 or RMI based channels for administrative purposes,
such as running WLST, the preferred approach is to `kubectl exec` into
the Kubernetes Pod and then run `wlst.sh`, or set up Bastion access and then run
`java weblogic.WLST` or `$ORACLE_HOME/oracle_common/common/bin/wlst.sh`
from the Bastion host to connect to the Kubernetes cluster
(some cloud environments use the term Jump Host or Jump Server instead of Bastion).

Also, if you need to use cross-domain T3 access
between clouds, data centers, and such, consider a private VPN.

#### WebLogic HTTP channels

When providing remote access to HTTP using a load balancer,
port forwarding, `NodePorts`, or similar,
Oracle recommends relaying the traffic to a dedicated
WebLogic Server port that you have configured
using a custom HTTP channel (network access point)
instead of relaying the traffic to a default port.
This helps ensure that external
traffic is limited to the HTTP protocol. A custom HTTP channel
is preferred over a default port because a default port supports
multiple protocols.

Do not enable tunneling on an HTTP channel
that is exposed for remote access unless you specifically
intend to allow it to handle T3 traffic
(tunneling allows T3 to tunnel through the channel using HTTP)
and you perform the additional steps that may be necessary
to further secure access, as described
in [WebLogic T3 and administrative channels](#weblogic-t3-and-administrative-channels).

#### Limit use of Kubernetes NodePorts

Although Kubernetes `NodePorts` are good for use in demos and getting-started guides,
they are typically not suited for production systems for multiple reasons, including:

- With some cloud providers, a `NodePort` may implicitly expose a port to the public Internet.
- They bypass almost all network security in Kubernetes.
- They allow all protocols (load balancers can limit to the HTTP protocol).
- They cannot expose standard, low-numbered ports like 80 and 443 (or even 8080 and 8443).
- Some Kubernetes cloud environments cannot expose usable `NodePorts` because their Kubernetes clusters run on a private network that cannot be reached by external clients.

#### General advice

1. _Set up administration ports_: Configure an administration port on WebLogic, or an administrative channel, to prevent
   all other channels from accepting administration-privileged traffic
   (this includes preventing administration-privileged traffic from a WebLogic console over HTTP).

1. _Be aware of anonymous defaults_:
   If an externally available port supports a protocol suitable for WebLogic
   JNDI, EJB/RMI, or JMS clients,
   then note that _by default_:
   - WebLogic enables anonymous users to access such a port.
   - JNDI entries, EJB/RMI applications, and JMS are open to anonymous users.

1. _Configure SSL_:
   You can configure two-way SSL to help prevent external access by unwanted applications
   (often SSL is setup between the caller and the load balancer, and plain-text
   traffic flows internally from the load balancer to WebLogic).

#### See also

- [External WebLogic clients]({{< relref "/managing-domains/accessing-the-domain/external-clients.md" >}})
- [Remote Console, Administration Console, WLST, and Port Forwarding access]({{< relref "/managing-domains/accessing-the-domain/_index.md" >}})
