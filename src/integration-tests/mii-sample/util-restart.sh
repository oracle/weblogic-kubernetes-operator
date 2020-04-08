#!/bin/bash

# TBD move into sample, copyright, and doc

DOMAIN_NAMESPACE=${1:-${DOMAIN_NAMESPACE:-sample-domain1-ns}}
DOMAIN_UID=${2:-${DOMAIN_UID:-sample-domain1}}
currentRV=`kubectl -n ${DOMAIN_NAMESPACE} get domain ${DOMAIN_UID} -o=jsonpath='{.spec.restartVersion}'`
if [ $? = 0 ]; then
  # we enter here only if the previous command succeeded

  nextRV=$((currentRV + 1))

  echo "Rolling domain from restartVersion='${currentRV}' to restartVersion='${nextRV}'."

  kubectl -n ${DOMAIN_NAMESPACE} patch domain ${DOMAIN_UID} --type='json' \
    -p='[{"op": "replace", "path": "/spec/restartVersion", "value": "'${nextRV}'" }]'

  kubectl -n ${DOMAIN_NAMESPACE} get pods --watch=true
fi

