#!/usr/bin/bash
# Copyright (c) 2024 Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

oci ce cluster create-kubeconfig --cluster-id ${cluster_id} --file $HOME/.kube/config  --region ${region} --token-version 2.0.0 --kube-endpoint ${endpoint}
