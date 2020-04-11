# TBD copyright

# TBD comment doc

# MII SAMPLE defaults specific to the test scripts in this directory
# For more defaults so $WORKDIR/env.sh

# ::: Directory for sample's temp files
#  should have 10GB? of space
#  default is '/tmp/$USER/model-in-image-sample-work-dir
# export WORKDIR=

# ::: Operator settings
#  Defaults are 'sample-weblogic-operator', '${OPER_NAME}-ns', '${OPER_NAME}-sa', 'weblogic-kubernetes-operator', and 'test'
# export OPER_NAME=
# export OPER_NAMESPACE=
# export OPER_SA=
# export OPER_IMAGE_NAME=
# export OPER_IMAGE_TAG=

# ::: DB Settings (needed if WDT_DOMAIN_TYPE is JRF)
#  Defaults are 'default', 30011, 'container-registry.oracle.com/database/enterprise', and '12.2.0.1-slim', and 'docker-secret'
# export DB_NAMESPACE=
# export DB_NODE_PORT=
# export DB_IMAGE_NAME=
# export DB_IMAGE_TAG=
# export DB_IMAGE_PULL_SECRET=

# ::: Traefik name and namespace
#  Defaults are 'traefik-operator' and '${TRAEFIK_NAME}-ns'
#  TBD need a pull secret
# export TRAEFIK_NAME=
# export TRAEFIK_NAMESPACE=
