#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
WOCHARTPATH="$SCRIPTPATH/charts/weblogic-operator"

if uname | grep -q "Darwin"; then
    mod_time_fmt="-f %m"
else
    mod_time_fmt="-c %Y"
fi

# Find latest file in source directory
unset -v latest
for file in "$WOCHARTPATH"/*; do
  [[ $file -nt $latest ]] && latest=$file
done

srctime="$(stat $mod_time_fmt $latest)"

out="$(helm package $WOCHARTPATH -d $SCRIPTPATH)"
helm_package=$(echo $out | cut -d ':' -f 2)
helm_package_name=$(basename $helm_package)

dsttime=0
if [ -f $SCRIPTPATH/../docs/charts/$helm_package_name ]; then
  dsttime="$(stat $mod_time_fmt $SCRIPTPATH/../docs/charts/$helm_package_name)"
fi

if [ $srctime \> $dsttime ];
then
  if [[ ! -e $SCRIPTPATH/../docs/charts ]]; then
    mkdir $SCRIPTPATH/../docs/charts
  fi
  mv -f $helm_package $SCRIPTPATH/../docs/charts/
  helm repo index $SCRIPTPATH/../docs/charts/ --url https://oracle.github.io/weblogic-kubernetes-operator/charts
else
  rm $helm_package
fi;


