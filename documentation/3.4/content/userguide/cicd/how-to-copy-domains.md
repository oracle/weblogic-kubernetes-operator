---
title: "Copy domains"
date: 2019-04-11T13:48:15-04:00
draft: false
weight: 5
description: "How to copy domains."
---

The recommended approach to save a copy of a Domain in Image or Domain in PV
domain is to simply ZIP (or tar)
the domain directory.  However, there is a very important caveat with this
recommendation - when you unzip the domain, it must go back into exactly
the same location (Domain Home) in the (new) file system.  Using this
approach will maintain the same domain encryption key.  

The best practice/recommended approach is to create a "primordial domain"
which does not contain any applications or resources,
and to create a ZIP file of this domain before starting any servers.  

> **The domain ZIP file must be created before starting servers.**  

When servers are started the first time, they will encrypt various other data.
Make sure that you create the ZIP file before starting servers for the first time.
The primordial domain ZIP file should be stored in a safe place where the CI/CD
can get it when needed, for example in a secured Artifactory repository (or
something similar).  

{{% notice warning %}}
Remember, anyone who gets access to this ZIP file can get access
to the domain encryption key, so it needs to be protected appropriately.
{{% /notice %}}

Every time you run your CI/CD pipeline to create a new mutation of the domain,
it should retrieve and unzip the primordial domain first, and then apply changes
to that domain using tools like WDT or WLST (see [here]({{< relref "/userguide/cicd/tools.md" >}})).

> **Always use external state.**

You should always keep state outside the image.  This means that you should
use JDBC stores for leasing tables, JMS and Transaction stores,
EJB timers, JMS queues, and so on.  This ensures that data will not be lost when
a container is destroyed.  

We recommend that state be kept in a database to take advantage of built-in
database server HA, and the fact that disaster recovery of sites across all
but the shortest distances almost always requires using a single database
server to consolidate and replicate data (DataGuard).
