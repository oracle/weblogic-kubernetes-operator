---
title: "Handling security validations"
date: 2020-06-30T08:55:00-05:00
draft: false
weight: 14
description: "Why am I seeing these security warnings?"
---

> After applying the July2021 PSU, I'm now seeing security warnings, such as:
>
> Description: Production Mode is enabled but user lockout settings are not secure in realm: myrealm, i.e. LockoutThreshold should not be greater than 5, LockoutDuration should not be less than 30.
>
> SOLUTION: Update the user lockout settings (LockoutThreshold, LockoutDuration) to be secure.

WebLogic Server has a new, important feature to ensure and help you secure your WLS domains when running in production. With the July 2021 PSU applied, WebLogic Server regularly validates your domain configuration settings against a set of security configuration guidelines to determine whether the domain meets key security guidelines recommended by Oracle. For more information and additional details, see [MOS Doc 2788605.1](https://support.oracle.com/rs?type=doc&id=2788605.1) "WebLogic Server Security Warnings Displayed Through the Admin Console" and [Review Potential Security Issues](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/lockd/secure.html#GUID-4148D1BE-2D54-4DA5-8E94-A35D48DCEF1D) in _Securing a Production Environment for Oracle WebLogic Server_.

Warnings may be at the level of the JDK, or that SSL is not enabled. Some warnings may recommend updating your WebLogic configuration. You can make the recommended configuration changes using an approach that depends on your [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}):

- For Domain on PV, use the WebLogic Scripting Tool (WLST), WebLogic Server Administration Console, WebLogic Deploy Tooling (WDT), or [configuration overrides]({{< relref "/managing-domains/configoverrides/_index.md" >}}).

- For Domain in Image, create a new image with the recommended changes or use [configuration overrides]({{< relref "/managing-domains/configoverrides/_index.md" >}}).

- For Model in Image, supply model files with the recommended changes in its image's `modelHome` directory or use [runtime updates]({{< relref "/managing-domains/model-in-image/runtime-updates.md" >}}).


> Msg ID: 090985
>
> Description: Production Mode is enabled but the the file or directory /u01/oracle/user_projects/domains/domain/bin/setDomainEnv.sh is insecure since its permission is not a minimum of umask 027.
>
> SOLUTION: Change the file or directory permission to at most allow only write by owner, read by group.
>
> Description:  The file or directory SerializedSystemIni.dat is insecure since its permission is not a minimum of umask 027.
>
> SOLUTION: Change the file or directory permission to at most allow only write by owner, read by group.

When the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) (WIT) creates a [Domain Home in Image]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}), you can specify the `--target OpenShift` option so that when WIT creates the domain, it sets the correct permissions in the domain home. When no `--target` option is specified, then the domain home directory has a umask of 027.

{{% notice note %}}
For information about handling file permission warnings on the OpenShift Kubernetes Platform, see the [OpenShift]({{<relref "/security/openshift.md">}}) documentation.
{{% /notice %}}
