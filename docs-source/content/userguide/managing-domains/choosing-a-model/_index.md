+++
title = "Choose a domain home source type"
date = 2019-02-23T16:44:54-05:00
weight = 1
pre = "<b> </b>"
+++

> TBD/WIP: This is a work in progress. Please do not post review comments here.

> TBD/WIP: Change 'configuration overrides' to a link throughout. Anything else need links?

When using the operator to deploy a WebLogic domain, you have the choice of the following WebLogic domain home source types:

 - **Domain in PV**: Supply your domain home configuration in a persistent volume (Domain in PV).
 - **Domain in Image**: Supply your domain home in a Docker image (Domain in Image).
 - **Model in Image**: Supply a WebLogic Deployment Tool model file in a Docker image (Model in Image).

There are advantages to all approaches, and there are sometimes technical limitations of various cloud providers that may make one approach better suited to your needs.  You can also mix and match on a domain-by-domain basis.

| Domain in PV | Domain in Image | Model in Image |
| --- | --- | --- |
| Let's you use the same standard read-only Docker image for every server in every domain. | Requires a different image for each domain, but all servers in that domain use the same image. | Same as Domain in Image. |
| No state is kept in Docker images making them completely throw away (cattle not pets). | Runtime state should not be kept in the images, but applications and configuration are. | Same as Domain in Image.|
| The domain is long-lived, so you can mutate the configuration or deploy new applications using standard methods (Administration Console, WLST, and such). You can also mutate configuration using configuration overrides. | If you want to mutate the domain home configuration, then you can apply configuration overrides or create a new image. If you want to deploy application updates, you must create a new image. | If you want to mutate the domain home configuration, then you can override it with additional model files supplied in a config map or you can supply a new image. If you want to deploy application updates, you must create a new image. |
| You can use configuration overrides to mutate the domain at runtime, but this requires shutting down the entire domain first and then restarting for the change to take effect. | You can use configuration overrides to mutate the domain home at runtime, but this requires shutting down the entire domain first and then restarting it for the change to take effect. | You can deploy model files to a config map to mutate the domain at runtime, and do not need to restart the entire domain for the change. Instead, you can initiate a rolling upgrade which restarts your WebLogic Server pods one at a time. Also, the model file syntax is far simpler and less error prone than the configuration override syntax, and, unlike configuration overrides, allows you to directly add data sources and JMS modules. |
| Logs are automatically placed on persistent storage.  | Logs are kept in the images and sent to the pod's log (stdout) by default. To change their location, you can set domain resource `logHomeEnabled` to true and configure the desired directory using `logHome`. | Same as Domain in Image. |
| Patches can be applied by simply changing the image and rolling the domain.  | To apply patches, you must create a new domain-specific image and then roll the domain.  | Same as Domain in Image. |
| Many cloud providers do not provide persistent volumes that are shared across availability zones, so you may not be able to use a single persistent volume.  You may need to use some kind of volume replication technology or a clustered file system. | You do not have to worry about volume replication across availability zones since each pod has its own copy of the domain.  WebLogic replication will handle propagation of any online configuration changes. If you are using a JRF domain, the domain's RCU wallet must be extracted from the domain and supplied to the failed over or restarted domain. | Same as Domain in Image. |
| CI/CD pipelines may be more complicated because you would probably need to run WLST against the live domain directory to effect changes.  | CI/CD pipelines are simpler because you can create the whole domain in the image and don't have to worry about a persistent copy of the domain.  | CI/CD pipelines are even simpler because you don't need to generate a domain home. The operator will create a domain home for you based on the model that you supply. |
| There are less images to manage and store, which could provide significant storage and network savings.  |  There are more images to manage and store in this approach. | Same as Domain in Image.|
| You may be able to use standard Oracle-provided images or, at least, a very small number of self-built images, for example, with patches installed. | You may need to do more work to set up processes to build and maintain your images. | Same as Domain in Image.|
