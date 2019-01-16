# Manually creating the domain

In some circumstances you may wish to manually create your domain custom resource.  If you have created your own
Docker image containing your domain and the specific patches that you require, then this approach will probably
be most suitable for your needs.

To create the domain custom resource, just make a copy of the sample [domain.yaml](./domain.yaml), and then edit 
it as per the instructions provided in the comments in that file.
When it is ready, you can create the domain in your Kubernetes cluster using the command:

```
$ kubectl apply -f domain.yaml
```

You can verify the domain custom resource was created using this command:

```
$ kubectl -n YOUR_NAMESPACE get domains
```

You can view details of the domain using this command:

```
$ kubectl -n YOUR_NAMESPACE describe domain YOUR_DOMAIN
```

In both of these commands, replace `YOUR_NAMESPACE` with the namespace that you created the domain in, and
replace `YOUR_DOMAIN` with the `domainUID` you chose.


