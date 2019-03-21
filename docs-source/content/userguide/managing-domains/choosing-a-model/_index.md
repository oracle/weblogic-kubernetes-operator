+++
title = "Choose a model"
date = 2019-02-23T16:44:54-05:00
weight = 1
pre = "<b> </b>"
+++

When using the operator, a WebLogic domain can be located either in a persistent volume (PV) or in a Docker image.
There are advantages to both approaches, and there are sometimes technical limitations of various
cloud providers that may make one approach better suited to your needs.
You can also mix and match on a domain-by-domain basis.

| Domain on a persistent volume | Domain in a Docker image |
| --- | --- |
| Let's you use the same standard read-only Docker image for every server in every domain. | Requires a different image for each domain, but all servers in that domain use the same image. |
| No state is kept in Docker images making them completely throw away (cattle not pets). | Runtime state should not be kept in the images, but applications and configuration are. |
| The domain is long-lived, so you can mutate the configuration or deploy new applications using standard methods (Administration Console, WLST, and such). | If you want to mutate the domain configuration or deploy application updates, you must create a new image. |
| Logs are automatically placed on persistent storage.  | Logs are kept in the images, and sent to the pod's log (stdout) unless you manually place them on persistent storage.  |
| Patches can be applied by simply changing the image and rolling the domain.  | To apply patches, you must create a new domain-specific image and then roll the domain.  |
| Many cloud providers do not provide persistent volumes that are shared across availability zones, so you may not be able to use a single persistent volume.  You may need to use some kind of volume replication technology or a clustered file system. | You do not have to worry about volume replication across availability zones since each pod has its own copy of the domain.  WebLogic replication will handle propagation of any online configuration changes.  |
| CI/CD pipelines may be more complicated because you would probably need to run WLST against the live domain directory to effect changes.  | CI/CD pipelines are simpler because you can create the whole domain in the image and don't have to worry about a persistent copy of the domain.  |
| There are less images to manage and store, which could provide significant storage and network savings.  |  There are more images to manage and store in this approach. |
| You may be able to use standard Oracle-provided images or, at least, a very small number of self-built images, for example, with patches installed. | You may need to do more work to set up processes to build and maintain your images. |
