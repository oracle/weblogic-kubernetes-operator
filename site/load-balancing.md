**TODO** review

# Load Balancing

There are several different types of load balancers available, and the 
options vary depending on which flavor of Kubernetes you are running 
and on your cloud provider (if any). 

The Oracle WebLogic Server Kubernetes Operator is tested with a number
of load balancers.  The following pages provide details about how 
to configure some common load balancers for use with the operator:

* [Apache HTTP Server](apache.md)
* [Traefik](traefik.md)
* [HAProxy/Voyager](voyager.md)


### Set up load balancers

Use these [scripts and Helm charts](kubernetes/samples/README.md) to install Traefik, Apache, or Voyager load balancers.
