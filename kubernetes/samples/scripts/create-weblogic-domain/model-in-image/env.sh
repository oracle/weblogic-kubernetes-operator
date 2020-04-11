# TBD doc/copyright

# ::: when to build base image
#  set to 'always' to always pull WL image even if it's already locally pulled to your docker image cache
#  default is 'when-missing'
#  (This actually does not build - just pulls, default is 'when-missing').
# export BASE_IMAGE_BUILD=

# ::: base image name
#  defaults to container-registry.oracle.com/middleware/weblogic for the 'WLS' domain type, 
#  and otherwise defaults to container-registry.oracle.com/middleware/fmw-infrastructure
# export BASE_IMAGE_NAME=

# ::: base image tag
#  defaults to 12.2.1.4
# export BASE_IMAGE_TAG=

# ::: when to build model image
#  set to 'when-missing' to skip building sample model image when it already exists in your docker image cache
#  defaults to 'always'
# TBD change default to 'when-missing' per MarkN
# export MODEL_IMAGE_BUILD=when-missing

# ::: model image name
#  defaults to 'model-in-image'
# export MODEL_IMAGE_NAME=

# ::: model image tag
#  defaults to 'v1'
# export MODEL_IMAGE_TAG=

# ::: model image model files
#  Location of the model .zip, .properties, and .yaml files 
#  that will be copied into the image.  Default is 'WORKDIR/model'
#  which is populated by the ./stage-model.sh script.
# export MODEL_DIR=

# ::: runtime model files
#  Location of model files that will be loaded at runtime from
#  a configmap specified by the domain resource. Default is 'WORKDIR/configmap'
#  which is populated by the ./stage-configmap.sh script.
# export CONFIGMAPDIR=

# ::: when to downlaod WDT installer
#  Set to 'always' to always download WDT even if WORKDIR already has a download, default is 'when-missing'
# export DOWNLOAD_WDT=

# ::: WDT installer URL
#  Defaults to 'https://github.com/oracle/weblogic-deploy-tooling/releases/latest'
#  Set to zip loc to download specific version, for example:
#   'https://github.com/oracle/weblogic-deploy-tooling/releases/download/weblogic-deploy-tooling-1.7.1/weblogic-deploy.zip'
# export WDT_INSTALLER_URL=

# ::: when to download WIT installer
#  Set to 'always' to always download image tool even if WORKDIR already has a download, default is 'when-missing'
# export DOWNLOAD_WIT=

# ::: WIT installer URL
#  Defaults to https://github.com/oracle/weblogic-image-tool/releases/latest
#  Set to zip loc to download specific version, for example:
#   'https://github.com/oracle/weblogic-image-tool/releases/download/release-1.8.1/imagetool.zip'
# export WIT_INSTALLER_URL=

# ::: WDT domain type
#  Set to 'WLS' to tell WDT and the sample we want a standard WLS domain, or set to 'JRF' or 'RestrictedJRF', defaults to 'WLS'
#  Used when creating image, choosing base image default, and setting up domain resource type file in the domain resource.
# export WDT_DOMAIN_TYPE=

# ::: Domain resource template
#  Use this file for a domain resource template instead
#  of sample-domain-resource-wls/k8s-domain.yaml.template
#  or sample-domain-resource-jrf/k8s-domain.yaml.template
# export DOMAIN_RESOURCE_TEMPLATE=

# ::: Domain Name
#  Default is domain1.
# export DOMAIN_NAME=

# ::: Domain UID
#  Default is sample-domain1.
# export DOMAIN_UID=

# ::: Domain Namespace
#  Default is ${DOMAIN_UID}-ns
# export DOMAIN_NAMESPACE=
