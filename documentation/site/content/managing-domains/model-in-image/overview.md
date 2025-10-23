+++
title = "Overview"
date = 2020-03-11T16:45:16-05:00
weight = 10
pre = "<b> </b>"
description = "Introduction to Model in Image, description of its runtime behavior, and references."
+++

{{< table_of_contents >}}

### Introduction

Model in Image is an alternative to the operator's Domain in Image and Domain on PV domain home source types. For a comparison, see [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}). Unlike Domain on PV and Domain in Image, Model in Image eliminates the need to pre-create your WebLogic domain home prior to deploying your Domain YAML file.

It enables:

 - Defining a WebLogic domain home configuration using WebLogic Deploy Tooling (WDT) model files and application archives.
 - Embedding these files in a single image that also contains a WebLogic installation,
   and using the WebLogic Image Tool (WIT) to generate this image. Or, alternatively,
   embedding the files in one or more application-specific images.
 - Optionally, supplying additional model files using a Kubernetes ConfigMap.
 - Supplying Kubernetes Secrets that resolve macro references within the models.
   For example, a secret can be used to supply a database credential.
 - Updating WDT model files at runtime. The WDT models are considered the source of truth and match the domain configuration at all times.  For example, you can add a data source
   to a running domain. See [Runtime updates](#runtime-updates) for details.

This feature is supported for standard WLS domains. **For JRF domains**, use [Domain on PV]({{< relref "/managing-domains/domain-on-pv/overview.md" >}}).

### WebLogic Deploy Tooling models

WDT models are a convenient and simple alternative to WebLogic Scripting Tool (WLST)
configuration scripts.
They compactly define a WebLogic domain using YAML files and support including
application archives in a ZIP file. For a description of the model format
and its integration with Model in Image,
see [Usage]({{< relref "/managing-domains/model-in-image/usage.md" >}})
and [Model files]({{< relref "/managing-domains/model-in-image/model-files.md" >}}).
The WDT model format is fully described in the open source,
[WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) GitHub project.

### Runtime behavior

When you deploy a Model in Image domain resource YAML file:

  - The operator will run a Kubernetes Job called the 'introspector job' that:
    - For an [Auxiliary Image]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}) deployment, an init container is used to copy and set up the WDT installer in the main container, and all the WDT models are also copied to the main container.
    - Sets up the call parameters for WDT to create the domain. The ordering of the models follow the pattern [Model files naming and ordering]({{< relref "/managing-domains/model-in-image/model-files#model-file-naming-and-loading-order" >}}).
    - Runs WDT tooling to generate a domain home using the parameters from the previous step.
    - Encrypts the domain salt key `SerializedSystemIni.dat`.
    - Packages the domain home and passes it to the operator.  The packaged domain has two parts. The first part `primordial domain` contains the basic configuration including the encrypted salt key. The second part `domain config` contains the rest of the configuration `config/**/*.xml`.  These files are compressed but do not contain any applications, libraries, key stores, and such, because they can be restored from the WDT archives.   

  - After the introspector job completes:
    - The operator creates one or more ConfigMaps following the pattern `DOMAIN_UID-weblogic-domain-introspect-cm***`.  These ConfigMaps contain the packaged domains from the introspector job and other information for starting the domain.

  - After completion of the introspector job, the operator will start the domain:
    - For an [Auxiliary Image]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}) deployment, an init container is used to copy and set up the WDT installer in the main container, and all the WDT models are also copied to the main container first.    
    - Restore the packaged domains in the server pod.
    - Restore applications, libraries, key stores, and such, from the WDT archives.
    - Decrypt the domain salt key.
    - Start the domain.


### Using demo SSL certificates in v14.1.2.0.0 or later

{{% notice note %}}
Beginning with WebLogic Server version 14.1.2.0.0, when a domain is `production` mode enabled, it is automatically `secure mode` enabled, therefore, all communications with the domain are using SSL channels and non-secure listening ports are disabled.  If there are no custom certificates configured for the SSL channels, then the server uses the demo SSL certificates.
The demo SSL certificates are now domain specific and generated when the domain is first created,
unlike previous releases, which were distributed with the WebLogic product installation.  Oracle recommends using custom SSL
certificates in a production environment.
{{% /notice %}}

The certificates are created under the domain home `security` folder.

```
-rw-r-----  1 oracle oracle  1275 Feb 15 15:55 democakey.der
-rw-r-----  1 oracle oracle  1070 Feb 15 15:55 democacert.der
-rw-r-----  1 oracle oracle  1478 Feb 15 15:55 DemoTrust.p12
-rw-r-----  1 oracle oracle  1267 Feb 15 15:55 demokey.der
-rw-r-----  1 oracle oracle  1099 Feb 15 15:55 democert.der
-rw-r-----  1 oracle oracle  1144 Feb 15 15:55 DemoCerts.props
-rw-r-----  1 oracle oracle  2948 Feb 15 15:55 DemoIdentity.p12
```

For Model in Image domains, whenever you change any security credentials including, but not limited to, the Administration Server credentials, RCU credentials, and such, the domain will be recreated and a new set of demo SSL certificates will be generated. The SSL certificates are valid for 6 months, then they expire.

The demo CA certificate expires in 5 years, however, whenever the domain is recreated, the entire set of certificates are regenerated so you _must_ import the demo CA certificate again.  

If you have any external client that needs to communicate with WebLogic Servers using SSL, then you need to import the current self-signing CA certificate, `democacert.der`,
into your local trust store.

```shell
 keytool -importcert -keystore <keystore path> -alias wlscacert  -file $HOME/Downloads/democacer.der
```

If you are using the WebLogic Scripting Tool, before starting the WLST session, you can set the following system properties.

```shell
 export WLST_PROPERTIES="-Dweblogic.security.TrustKeyStore=DemoTrust -Dweblogic.security.SSL.ignoreHostnameVerification=true"
```


### Runtime updates

Model updates can be applied at runtime by changing an image, secrets, a domain resource, or a WDT model ConfigMap after initial deployment.

Some updates may be applied to a running domain without requiring any WebLogic pod restarts (an online update),
but others may require rolling the pods to propagate the update's changes (an offline update),
and still others may require shutting down the entire domain before applying the update (a full domain restart update).
_It is the administrator's responsibility to make the necessary changes to a domain resource to initiate the correct type of update._

See [Runtime updates]({{< relref "/managing-domains/model-in-image/runtime-updates.md" >}}).

### Continuous integration and delivery (CI/CD)

To understand how Model in Image works with CI/CD, see [CI/CD considerations]({{< relref "/managing-domains/cicd/_index.md" >}}).

### References

 - [Model in Image sample]({{< relref "/samples/domains/model-in-image/_index.md" >}})
 - [WebLogic Deploy Tooling (WDT)](https://oracle.github.io/weblogic-deploy-tooling/)
 - [WebLogic Image Tool (WIT)](https://oracle.github.io/weblogic-image-tool/)
 - Domain [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md), [documentation]({{< relref "/managing-domains/domain-resource.md" >}})
 - HTTP load balancers: Ingress [documentation]({{< relref "/managing-domains/accessing-the-domain/ingress/_index.md" >}}), [sample]({{< relref "/samples/ingress/_index.md" >}})
 - [CI/CD considerations]({{< relref "/managing-domains/cicd/_index.md" >}})
