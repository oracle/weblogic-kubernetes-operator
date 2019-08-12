---
title: "Coherence Requirements"
date: 2019-08-12T12:41:38-04:00
draft: false
---

If you are running Coherence on Kubernetes, either inside a WebLogic domain
or standalone, then there are some additional requirements to make sure
that Coherence can form clusters. 

Note that some Fusion Middleware products, like SOA Suite, use Coherence
and so these requirements apply to them.

#### Unicast and Well Known Address 
When the first Coherence process starts, it will form a cluster.  The next
Coherence process to start (i.e. in a different pod) will use UDP to try
to contact the master.  Most Kubernetes overlay network providers do not
support multicast, so you should configure Coherence to use unicast and 
provide a "well known address" so that members can find the master. 
This is normally done by using the cluster address, i.e. the address of
the Kubernetes service that is pointing to all cluster members.

{{% notice note %}}
If you create your domain using the samples provided in this project, the
cluster will be able to form, you do not need to manually specify WKA.
However, the information is included below for completeness.
{{% /notice %}}

For example, suppose you have a domain called `domain1` and in that domain
you have a cluster called `cluster1` and Coherence is running in that
cluster.  You would therefore use the cluster service that is created by
the operator as the well known address.  This service, in this example, 
would be called `domain1-cluster-cluster1` and follows the pattern:

```
domainUID  "-cluster-"  cluster name
```

Where `-cluster-` is a string literal. 

The Coherence well known addresses are provided in a variable named
`coherence.wka` as shown in the example below:

```
-Dcoherence.wka=domain1-cluster-cluster1
```

#### Operating system library requirements

In order for coherence clusters to form correctly, the `conntrack` library
must be installed.  Most Kubernetes distributions will do this for you.
If you have issues with clusters not forming, you should check that 
`conntrack` is installed using this command (or equivalent):

```
$ rpm -qa | grep conntrack
libnetfilter_conntrack-1.0.6-1.el7_3.x86_64
conntrack-tools-1.4.4-4.el7.x86_64
```

You should see output similar to that shown above.  If you do not, then you
should install `conntrack` using your operating system tools.

#### Firewall (iptables) requirements

Some Kubernetes distributions create `iptables` rules that block some
types of traffic that Coherence requires to form clusters.  If you are
not able to form clusters, you can check for this issue using the
following command:

```
# iptables -t nat -v  -L POST_public_allow -n
Chain POST_public_allow (1 references)
pkts bytes target     prot opt in     out     source               destination
164K   11M MASQUERADE  all  --  *      !lo     0.0.0.0/0            0.0.0.0/0
   0     0 MASQUERADE  all  --  *      !lo     0.0.0.0/0            0.0.0.0/0
```

If you see output similar to the example above, i.e. if you see any entries 
in this chain, then you need to remove them.  You can remove the entries
using this command:

```
# iptables -t nat -v -D POST_public_allow 1
```

Note that you will need to run that command for each line. So in the example
above, you would need to run it twice. 

Once you are done, you can run the previous command again and verify that
the output is now an empty list.

After making this change, restart your domain(s) and the Coherence cluster
should now form correctly. 



