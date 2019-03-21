---
title: "Requirements"
date: 2019-02-23T17:19:19-05:00
draft: false
weight: 1
---

In addition to the requirements listed in the [User guide]({{< relref "/userguide/introduction/introduction.md#prerequisites" >}}), the following software is also required to obtain and build the operator:

* Git (1.8 or later recommended)
* Java Developer Kit (1.8u131 or later recommended; please use 1.8, tests will not work on 1.9 or later versions)
* Apache Maven (3.3 or later recommended)

The operator is written primarily in Java, BASH shell scripts, and WLST scripts.  The Java code uses features introduced in Java 1.8 -- for example, closures -- but does not use any Java 1.9 features.

Because the target runtime environment for the operator is Oracle Linux, no particular effort has been made to ensure the build or tests run on any other operating system.  Please be aware that Oracle will not provide support, or accept pull requests to add support, for other operating systems.

#### Obtaining the operator source code

The operator source code is published on GitHub at https://github.com/oracle/weblogic-kubernetes-operator.  Developers may clone this repository to a local machine or, if desired, create a fork in their personal namespace and clone the fork.  Developers who are planning to submit a pull request are advised to create a fork.

To clone the repository from GitHub, issue this command:

```
$ git clone https://github.com/oracle/weblogic-kubernetes-operator.git
```
