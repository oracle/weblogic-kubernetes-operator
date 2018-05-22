#!/usr/bin/env bash

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/internal/utility.sh
outputRoot=/scratch/lihhe/wls-operator-hack/output/weblogic-domains

function create() {
  createVoyagerOperator \
      $outputRoot/domain1/voyager-operator-security.yaml \
      $outputRoot/domain1/voyager-operator.yaml

  domainUID=domain1
  namespace=default
  createVoyagerIngress $outputRoot/domain1/weblogic-domain-voyager-ingress.yaml

  #domainUID=domain2
  #namespace=test1
  #createVoyagerIngress $outputRoot/domain2/weblogic-domain-voyager-ingress.yaml
}

function delete() {
  domainUID=domain1
  namespace=default
  deleteVoyagerIngress $outputRoot/domain1/weblogic-domain-voyager-ingress.yaml

  #domainUID=domain2
  #namespace=test1
  #deleteVoyagerIngress $outputRoot/domain2/weblogic-domain-voyager-ingress.yaml

  deleteVoyagerOperator
}

function usage() {
  echo "usage: $0 create|delete"
  exit 1
}

function main() {
  if [ "$#" != 1 ] ; then
    usage
  fi

  if test $1 = "delete"; then
    delete
  elif test $1 = "create"; then
    create
  fi
}

main "$@"



