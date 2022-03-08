---
title: "Coherence requirements"
date: 2019-08-12T12:41:38-04:00
draft: false
weight: 12
description: "If you are running Coherence on Kubernetes, either inside a WebLogic domain
or standalone, then there are some additional requirements to make sure
that Coherence can form clusters."
---

If you are running Coherence on Kubernetes, either inside a WebLogic domain
or standalone, then there are some additional requirements to make sure
that Coherence can form clusters.

Note that some Fusion Middleware products, like SOA Suite, use Coherence
and so these requirements apply to them.

#### Unicast and Well Known Address
When the first Coherence process starts, it will form a cluster.  The next
Coherence process to start (for example, in a different pod), will use UDP to try
to contact the senior member.  

If you create a WebLogic domain which contains a Coherence cluster
using the samples provided in this project, then that cluster will
be configured correctly so that it is able to form;
you do not need to do any additional manual configuration.

If you are running Coherence standalone (outside a
WebLogic domain), then you should configure Coherence to use unicast and
provide a "well known address (WKA)" so that all members can find the senior
member.  Most Kubernetes overlay network providers do not
support multicast.  

This is done by specifying Coherence well known addresses in a variable named
`coherence.wka` as shown in the following example:

```
-Dcoherence.wka=my-cluster-service
```

In this example `my-cluster-service` should be the name of the Kubernetes
service that is pointing to all of the members of that Coherence cluster.

For more information about running Coherence in Kubernetes outside of
a WebLogic domain, refer to the [Coherence operator documentation](https://oracle.github.io/coherence-operator/).

#### Operating system library requirements

In order for Coherence clusters to form correctly, the `conntrack` library
must be installed.  Most Kubernetes distributions will do this for you.
If you have issues with clusters not forming, then you should check that
`conntrack` is installed using this command (or equivalent):

```shell
$ rpm -qa | grep conntrack
```
```
libnetfilter_conntrack-1.0.6-1.el7_3.x86_64
conntrack-tools-1.4.4-4.el7.x86_64
```

You should see output similar to that shown above.  If you do not, then you
should install `conntrack` using your operating system tools.

#### Firewall (iptables) requirements

Some Kubernetes distributions create `iptables` rules that block some
types of traffic that Coherence requires to form clusters.  If you are
not able to form clusters, then you can check for this issue using the
following command:

```shell
$ iptables -t nat -v  -L POST_public_allow -n
```
```
Chain POST_public_allow (1 references)
pkts bytes target     prot opt in     out     source               destination
164K   11M MASQUERADE  all  --  *      !lo     0.0.0.0/0            0.0.0.0/0
   0     0 MASQUERADE  all  --  *      !lo     0.0.0.0/0            0.0.0.0/0
```

If you see output similar to the example above, for example, if you see any entries
in this chain, then you need to remove them.  You can remove the entries
using this command:

```shell
$ iptables -t nat -v -D POST_public_allow 1
```

Note that you will need to run that command for each line. So in the example
above, you would need to run it twice.

After you are done, you can run the previous command again and verify that
the output is now an empty list.

After making this change, restart your domains and the Coherence cluster
should now form correctly.

#### Make iptables updates permanent across reboots

The recommended way to make `iptables` updates permanent across reboots is
to create a `systemd` service that applies the necessary updates during
the startup process.

Here is an example; you may need to adjust this to suit your own
environment:

* Create a `systemd` service:
  
```shell
$ echo 'Set up systemd service to fix iptables nat chain at each reboot (so Coherence will work)...'
```
```shell
$ mkdir -p /etc/systemd/system/
```
```shell
$ cat > /etc/systemd/system/fix-iptables.service << EOF
[Unit]
Description=Fix iptables
After=firewalld.service
After=docker.service

[Service]
ExecStart=/sbin/fix-iptables.sh

[Install]
WantedBy=multi-user.target
EOF
```

* Create the script to update `iptables`:

```shell
$ cat > /sbin/fix-iptables.sh << EOF
#!/bin/bash
echo 'Fixing iptables rules for Coherence issue...'
TIMES=$((`iptables -t nat -v -L POST_public_allow -n --line-number | wc -l` - 2))
COUNTER=1
while [ $COUNTER -le $TIMES ]; do
  iptables -t nat -v -D POST_public_allow 1
  ((COUNTER++))
done
EOF
```

* Start the service (or just reboot):

```shell
$ echo 'Start the systemd service to fix iptables nat chain...'
```
```shell
$ systemctl enable --now fix-iptables
```
