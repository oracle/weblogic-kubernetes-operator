**TODO** write me


# Creating or obtaining WebLogic Docker images

You will need Docker images to run your WebLogic domains in Kubernetes.
There are two main options available:

* Use a Docker image which contains the WebLogic Server binaries but
  not the domain, or
* Use a Docker image which contains both the WebLogic Server binaries
  and the domain directory. 
  
If you want to use the first option, you will need to obtain the standard 
WebLogic Server image from the Docker Store [see here](#obtaining-standard-images-from-the-docker-store)
and then create a new image with the mandatory patches applied as described in [this section](#creating-a-custom-images-with-patches-applied).
If you want to use additional patches, you can customize that process to include additional patches.

If you want to use the second option, which includes the domain directory
inside the Docker image, then you will need to build your own Docker images
as described in [this section](#creating-a-custom-image-with-your-domain-inside-the-image).


## Obtaining standard images from the Docker store 

Oracle provides a [WebLogic Server 12.2.1.3.0 Docker image](https://store.docker.com/_/oracle-weblogic-server-12c) in the
[Docker Store](https://store.docker.com).  To obtain that image, you 
must have a Docker Store account, log on to the Docker Store, navigate 
to that image and click on the "Proceed to Checkout" button which will 
prompt you to read and accept the license agreement for the image. 
Once you have accepted the license agreement, you will be abel to 
pull the image using your Docker store credentials. 

First, you will need to login to the Docker Store:

```
docker login 
```

Then, you can pull the image with this command:

```
docker pull store/oracle/weblogic:12.2.1.3
```

Additional information about using this image is available on the
Docker Store.

## Creating a custom images with patches applied 

The Oracle WebLogic Server Kubernetes Operator requires patch 28076014.
This patch does have some prerequisites that will also need to be applied. 

[This sample](https://github.com/oracle/docker-images/blob/master/OracleWebLogic/samples/12213-patch-wls-for-k8s/README.md) in 
the Oracle GitHub Docker images repository demonstrates how to create 
a patched image.

The `Dockerfile` in that sample lists the base image as follows:

``` 
FROM oracle/weblogic:12.2.1.3-developer
```

You can change this to use the standard WebLogic Server image you 
downloaded from the Docker Store by updating the `FROM` statement
as follows:

```
FROM store/oracle/weblogic:12.2.1.3
```

After running the `docker build` as described in the sample, you 
will have created a Docker image with the necessary patches to 
run WebLogic 12.2.1.3 in Kubernetes using the operator.

## Creating a custom image with your domain inside the image

**TODO** write me 

