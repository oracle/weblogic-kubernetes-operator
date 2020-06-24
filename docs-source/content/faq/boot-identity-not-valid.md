---
title: "Boot identity not valid"
date: 2020-03-02T08:08:19-04:01
draft: false
weight: 20
---

> One or more WebLogic Server instances in my domain will not start and I see errors in the server log like this:
>
> ***<Feb 6, 2020 12:05:35,550 AM GMT> <Critical> <Security> <BEA-090402> <Authentication denied: Boot identity not valid. The user name or password or both from the boot identity file (boot.properties) is not valid. The boot identity may have been changed since the boot identity file was created. Please edit and update the boot identity file with the proper values of username and password. The first time the updated boot identity file is used to start the server, these new values are encrypted.>***

When you see these kinds of errors, it means that the user name and password provided in the `weblogicCredentialsSecret` are incorrect. Prior to operator version 2.5.0, this error could
have also indicated that the WebLogic domain directory's security configuration files have changed in an incompatible way between when the operator scanned
the domain directory, which occurs during the "introspection" phase, and when the server instance attempted to start. There is now a separate validation for that condition described in the [Domain secret mismatch](../domain-secret-mismatch/) FAQ entry.

Check that the user name and password credentials stored in the Kubernetes Secret referenced by `weblogicCredentialsSecret` contain the expected values for an account with administrative privilege for the WebLogic domain.
Then [stop all WebLogic Server instances](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers)
in the domain before restarting so that the operator will repeat its introspection and generate the corrected `boot.properties` files.
