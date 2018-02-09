#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

hostname="${HOSTNAME}"

# this file lives in git_project_dir/src/integration-tests/bash
# need to go up 3 levels to get to the git project dir
git_project_dir="${script_dir}/../../.."

# the script to generate a cert lives in the kubernetes/internal directory
k8s_internal_dir="${git_project_dir}/kubernetes/internal"

cert_dir="${k8s_internal_dir}/weblogic-operator-cert"
cert_file="${cert_dir}/weblogic-operator.cert.pem"
key_file="${cert_dir}/weblogic-operator.key.pem"
echo "Generating a self-signed certificate for the operator for ${hostname}"
$k8s_internal_dir/generate-weblogic-operator-cert.sh DNS:localhost,DNS:${hostname}

config_dir="${git_project_dir}/config"
secrets_dir="${git_project_dir}/secrets"

rm -rf ${config_dir}
rm -rf ${secrets_dir}

mkdir ${config_dir}
mkdir ${secrets_dir}

base64 -i ${cert_file} | tr -d '\n' > ${config_dir}/externalOperatorCert
cp ${key_file} ${secrets_dir}/externalOperatorKey

rm -rf ${cert_dir}
