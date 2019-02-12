#!/bin/bash

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

out="$(helm package $SCRIPTPATH/charts/weblogic-operator)"
helm_package=$(echo $out | cut -d ':' -f 2)
helm repo index $SCRIPTPATH/charts/weblogic-operator/ --url https://oracle.github.io/weblogic-kubernetes-operator/charts

cp $SCRIPTPATH/charts/weblogic-operator/index.yaml $SCRIPTPATH/../docs/charts
mv $helm_package $SCRIPTPATH/../docs/charts/


