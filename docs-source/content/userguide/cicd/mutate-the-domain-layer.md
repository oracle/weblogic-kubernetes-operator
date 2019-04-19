---
title: "Mutate the domain layer"
date: 2019-04-11T13:43:41-04:00
draft: false
weight: 4
description: "How to mutate the domain layer."
---

If you need to mutate the domain layer, and keep the same domain encryption keys,
then there are some choices about how to implement that, as alluded to previously.
Let's explore those in some more detail now.

The first option is to implement each mutation as a delta to the previous state.
This is conceptually similar to how immutable objects (like Java Strings) are
implemented, a "copy on write" approach applied to the domain configuration as a
unit.  This does have the advantage that it is simple to implement, but the
disadvantage that your builds would depend on the previous good build and
this is somewhat contrary to typical CI/CD practices.  You also have to work
out what to do with the bad builds, or "holes" in the sequence.

![Mutating the previous state](/weblogic-kubernetes-operator/images/n-1.png)

An alternative is to capture a "primordial state" of the domain before starting
the sequence.  In practical terms, this might mean creating a very simple domain
with no applications or resources in it, and "saving" it before ever starting
any servers.  This primordial domain (let’s call it t=0) would then be used
to build each mutation.  So each state is built from t=0, plus all of the
changes up to that point.  

Said another way, each build would start with t=0 as the base image and extend it.
This eliminates the need to keep each intermediate state, and would also likely
have benefits when you remove things from the domain, because you would not have
"lost" ("whited out" is the Docker layer term) space in the intermediate layers.
Although, these layers tend to be relatively small, so this is possibly not a big issue.

![Rebuilding from a primordial state](/weblogic-kubernetes-operator/images/primordial.png)

This approach is probably an improvement.  It does get interesting though when you
update a lower layer, for example when you patch WebLogic or update the JDK.  When
this happens, you need to create another base image, shown in the diagram as v2 t-0.
All of the mutations in this new chain are based on this new base image.  So that
still leaves us with the problem of how to take the domain from the first series
(v1 t=0 to t=3) and "copy" it across to the second series (v2).
