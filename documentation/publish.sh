#!/bin/bash
# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script uses Hugo to generate the site for the project documentation and for archived versions.
set -o errexit
set -o pipefail

script="${BASH_SOURCE[0]}"

function usage {
  echo "usage: ${script} [-o <directory>] [-h]"
  echo "  -o Output directory (optional) "
  echo "      (default: \${WORKSPACE}/documentation, if \${WORKSPACE} defined, else /tmp/weblogic-kubernetes-operator) "
  echo "  -h Help"
  exit $1
}

if [[ -z "${WORKSPACE}" ]]; then
  outdir="/tmp/weblogic-kubernetes-operator"
else
  outdir="${WORKSPACE}/documentation"
fi

while getopts "o:h" opt; do
  case $opt in
    o) outdir="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -d "${outdir}" ]; then
  rm -Rf "${outdir:?}/*"
else
  mkdir -m777 -p "${outdir}"
fi

echo "Building documentation for current version and for selected archived versions..."
hugo -s site -d "${outdir}/3.3" -b https://oracle.github.io/weblogic-kubernetes-operator/3.3

echo "Successfully generated documentation in ${outdir}..."




