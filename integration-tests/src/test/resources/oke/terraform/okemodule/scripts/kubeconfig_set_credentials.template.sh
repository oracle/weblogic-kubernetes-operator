#!/usr/bin/bash
# Copyright (c) 2024 Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

$${KUBERNETES_CLI:-kubectl}  config set-credentials "user-${cluster_id_11}" --exec-command="$HOME/bin/token_helper.sh" \
  --exec-arg="ce" \
  --exec-arg="cluster" \
  --exec-arg="generate-token" \
  --exec-arg="--cluster-id" \
  --exec-arg="${cluster_id}" \
  --exec-arg="--region" \
  --exec-arg="${region}"
