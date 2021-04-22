+++
title = "Apply patched binary images to a running domain"
date = 2019-02-23T16:43:45-05:00
weight = 9
pre = "<b> </b>"
+++


When updating WebLogic binaries of a running domain in Kubernetes with a patched container image,
the operator applies the update in a Zero Downtime (ZDT) fashion. The procedure for the operator
to update the running domain differs depending on the [domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

For Domain in PV, the operator can apply the update to the running domain without modifying the patched container image. For Model in Image (MII) and Domain in Image,
before the operator can apply the update, the patched container images need to be modified to add the domain home or a
WebLogic Deploy Tooling (WDT) model and archive. You use the WebLogic Image Tool (WIT) to update or rebase a patched
container image with a WDT model or with a domain inside of the image.

In all three domain home source types, you edit the [Domain Resource]({{< relref "/userguide/managing-domains/domain-resource#domain-resource-attribute-references" >}})
to inform the operator of the name of the new patched image so that it can manage the update of the WebLogic domain.

##### Domain in PV

Edit the Domain Resource image reference with the new image name/tag (for example, `oracle/weblogic:12.2.1.4-patched`).
Then, the operator performs a rolling restart of the WebLogic domain to update the Oracle Home of the servers.

##### Model in Image
Use WIT [Update Image](https://github.com/oracle/weblogic-image-tool/blob/master/site/update-image.md) to extend patched container images with a WDT model and application archive file.
Then, the operator performs a rolling update of the domain, updating the ORACLE HOME of each server pod in a ZDT fashion.

For example:

Update an image, `oracle/weblogic:12.2.1.4-patched`, to create an MII image tagged `mydomain:v2`,
which includes a WDT model file, `./wdt/my_domain.yaml`, and a WDT archive, `./wdt/my_domain.zip`.

```shell
$imagetool update --fromImage oracle/weblogic:12.2.1.4-patched --wdtModelOnly
--tag mydomain:v2 --wdtArchive ./wdt/my_domain.zip --wdtModel ./wdt/my_domain.yaml --wdtVersion latest
```

Then, edit the Domain Resource image reference with the new image name/tag (`mydomain:2`).

##### Domain in Image
Use WIT [Rebase Image](https://github.com/oracle/weblogic-image-tool/blob/master/site/rebase-image.md) to update the Oracle Home
for an existing domain image using the patched Oracle Home from a patched container image. Then, the operator performs a rolling update
of the domain, updating the Oracle Home of each server pod in a ZDT fashion.

For example:

WIT copies the domain from the source image, `mydomain:v1`, to a new image, `mydomain:v2`, based on a target
image named `oracle/weblogic:12.2.1.4-patched`.

**Note**: On each image, Oracle Home and the JDK must be installed in the same directories.

```shell
$imagetool rebase --tag mydomain:v2 --sourceImage mydomain:v1 --targetImage oracle/weblogic:12.2.1.4-patched
```

Then, edit the Domain Resource image reference with the new image name/tag (`mydomain:2`).
