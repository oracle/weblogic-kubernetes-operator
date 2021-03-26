+++
title = "Domain in Image"
date = 2019-02-23T16:45:16-05:00
weight = 4
pre = "<b> </b>"
+++

{{% children style="h4" description="true" %}}

{{% notice warning %}}
Oracle strongly recommends storing a domain image as private in the registry.
A container image that contains a WebLogic domain home has sensitive information
including keys and credentials that are used to access external resources
(for example, the data source password). For more information, see
[WebLogic domain in container image protection]({{<relref "/security/domain-security/image-protection#weblogic-domain-in-container-image-protection">}}).
{{% /notice %}}
