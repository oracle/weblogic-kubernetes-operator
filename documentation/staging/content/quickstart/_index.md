+++
title = "Quick Start"
date = 2019-02-22T15:27:38-05:00
weight = 1
pre = "<b> </b>"
+++


The Quick Start guide provides a simple tutorial to help you get the operator up and running quickly. Use this Quick Start guide to create a WebLogic Server deployment in a Kubernetes cluster with the WebLogic Kubernetes Operator. Please note that this walk-through is for demonstration purposes only, not for use in production.
These instructions assume that you are already familiar with Kubernetes. If you need more detailed instructions, please
refer to [Manage operators]({{< relref "/managing-operators/_index.md" >}}).

{{% notice note %}}
All Kubernetes distributions and managed services have small differences. In particular,
the way that persistent storage and load balancers are managed varies significantly.  
You may need to adjust the instructions in this guide to suit your particular flavor of Kubernetes.
{{% /notice %}}



For this exercise, you’ll need a Kubernetes cluster. If you need help setting one up, check out our [cheat sheet]({{< relref "/managing-operators/k8s-setup.md" >}}).

The operator uses Helm to create and deploy the necessary resources and then run the operator in a Kubernetes cluster. For Helm installation and usage information, see [Prepare for installation]({{< relref "/managing-operators/preparation.md" >}}).

In this Quick Start guide, we will first install the WebLogic Kubernetes operator, then create a [Model in Image]({{< relref "/managing-domains/model-in-image/_index.md" >}}) [auxiliary image]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}) that contains a WebLogic domain and WebLogic applications defined using WDT model YAML and archive files, and finally use a Kubernetes create command to deploy a domain resource YAML that references the image.  At that time, the operator will automatically detect the domain resource, and deploy the domain's WebLogic administration server and WebLogic managed server pods. See [Model in Image sample]({{< relref "/samples/domains/model-in-image/_index.md" >}}) for a complete sample.
