---
title: "Boot identity not valid"
date: 2020-03-02T08:08:19-04:01
draft: false
weight: 3
description: "One or more WebLogic Server instances in my domain will not start and I see errors in the server log like this: Boot identity not valid."
---

> One or more WebLogic Server instances in my domain will not start and I see errors in the server log like this:
>
> ***<Feb 6, 2020 12:05:35,550 AM GMT> <Critical> <Security> <BEA-090402> <Authentication denied: Boot identity not valid. The user name or password or both from the boot identity file (boot.properties) is not valid. The boot identity may have been changed since the boot identity file was created. Please edit and update the boot identity file with the proper values of username and password. The first time the updated boot identity file is used to start the server, these new values are encrypted.>***

When you see these kinds of errors, it typically means that the user name and password provided in the `weblogicCredentialsSecret` are incorrect. Prior to operator version 2.5.0, this error could have also indicated that the WebLogic domain directory's security configuration files have changed in an incompatible way between when the operator scanned
the domain directory, which occurs during the "introspection" phase, and when the server instance attempted to start. There is now a separate validation for that condition described in the [Domain secret mismatch](../domain-secret-mismatch/) FAQ entry.

Check that the user name and password credentials stored in the Kubernetes Secret, referenced by `weblogicCredentialsSecret`, contain the expected values for an account with administrative privilege for the WebLogic domain.
Then [stop all WebLogic Server instances](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers)
in the domain before restarting so that the operator will repeat its introspection and generate the corrected `boot.properties` files.

If the "Boot identity not valid" error is still not resolved, and the error is logged on a WebLogic Managed Server, then the server may be failing to contact the Administration Server. Check carefully for any errors or exceptions logged before the "Boot identity not valid" error in the Managed Server log file and look for indications of communication failure. For example:
- If you have enabled SSL for the Administration Server default channel or have configured an administration port on the Administration Server, and the SSL port is using the demo identity certificates, then a Managed Server may fail to establish an SSL connection to the Administration Server due to a hostname verification exception (such as the "SSLKeyException: Hostname verification failed"). For non-production environments, you can turn off the hostname verification by setting the `-Dweblogic.security.SSL.ignoreHostnameVerification=true` property in the Java options for starting the WebLogic Server.

  **NOTE**: Turning off hostname verification leaves WebLogic Server vulnerable to man-in-the-middle attacks. Oracle recommends leaving hostname verification on in production environments.

- For other SSL-related errors, make sure the keystores and passwords are specified correctly in the Java options for starting the WebLogic Server. See [Configuring SSL](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/secmg/ssl.html#GUID-5274E688-51EC-4A63-A35E-FC718B35C897) for details on configuring SSL in the Oracle WebLogic Server environment.

- If the DNS hostname of the Administration Server can't be resolved during Managed Server startup with errors such as "java.net.UnknownHostException: domain1-admin-server", then check the DNS server pod logs for any errors and take corrective actions to resolve those errors.
