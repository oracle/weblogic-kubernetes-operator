#!/bin/bash
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
set -o errexit

script="${BASH_SOURCE[0]}"

function usage {
  echo "usage: ${script} [-e <endpoint>] [-u <username>] [-p <password>]"
  echo "  -e OCIR endpoint (optional) "
  echo "      (default: phx.ocir.io) "
  echo "  -u user name (optional) "
  echo "      (default: \${REPO_USERNAME}) "
  echo "  -p password (optional) "
  echo "      (default: \${REPO_PASSWORD}) "
  echo "  -h Help"
  exit $1
}

ocir_endpoint="phx.ocir.io"
username="$REPO_USERNAME"
auth="$REPO_PASSWORD"

while getopts ":h:e:u:p:" opt; do
  case $opt in
    e) ocir_endpoint="${OPTARG}"
    ;;
    u) username="${OPTARG}"
    ;;
    p) auth="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

HEADERS=""
if [ -n "$username" ]; then
  HEADERS="Authorization: Basic $(echo -n "${username}:${auth}" | base64 | tr -d '\n')"
fi

RESPONSE=$(curl -sk -H "$HEADERS" "https://${ocir_endpoint}/20180419/docker/token")

echo $RESPONSE | jq '.token' | xargs echo