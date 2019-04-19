---
title: "How to copy domains"
date: 2019-04-11T13:48:15-04:00
draft: false
weight: 5
description: "How to copy domains."
---

The recommended approach to save a copy of a domain is to simply ZIP (or tar)
the domain directory.  However, there is a very important caveat with this
recommendation - when you unzip the domain, it must go back into exactly
the same location (Domain Home) in the (new) file system.  Using this
approach will maintain the same domain encryption key.  

The best practice/recommended approach is to create a "primordial domain"
(as described previously) which does not contain any applications or resources,
and to create a ZIP file of this domain before starting any servers.  


> The domain ZIP must be created before starting servers.  

When servers are started the first time, they will encrypt various other data.
Make sure you create the ZIP file before starting servers for the first time.
The primordial domain ZIP file should be stored in a safe place where the CI/CD
can get it when needed, for example in a secured Artifactory repository (or
something similar).  Remember, anyone who gets access to this ZIP file can get access
to the domain encryption key, so it needs to be protected appropriately.

Every time you run your CI/CD pipeline to create a new mutation of the domain,
it should retrieve and unzip the primordial domain first, and then apply changes
to that domain using tools like WDT or WLST (see below).


> Always use external state.

You should always keep state outside the Docker image.  This means that you should
use JDBC stores for leasing tables, JMS and Transaction stores, HTTP sessions,
EJB timers, JMS queues, and so on.  This ensures that data will not be lost when
a container is destroyed.
