#!/bin/bash
# Copyright (c) 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script deletes provisioned OKE Kubernetes cluster using terraform (https://www.terraform.io/)
#

set -euo pipefail

TARGET_CLUSTER_OCID="$1"
COMPARTMENT_OCID="$2"

if [[ -z "${TARGET_CLUSTER_OCID}" || -z "${COMPARTMENT_OCID}" ]]; then
  echo "[ERROR] Usage: $0 <cluster-ocid> <compartment-ocid>"
  exit 1
fi

OCI_CONFIG="${OCI_CLI_CONFIG_FILE:-/home/opc/.oci/config}"
OCI_PROFILE="${OCI_CLI_PROFILE:-DEFAULT}"

echo "[DEBUG] Using OCI config : ${OCI_CONFIG}"
echo "[DEBUG] Using OCI profile: ${OCI_PROFILE}"
echo "[DEBUG] Target Cluster OCID: ${TARGET_CLUSTER_OCID}"
echo "[DEBUG] Compartment OCID   : ${COMPARTMENT_OCID}"

# ---- OCI permissions (non-interactive, silent) ----
chmod 600 "${OCI_CONFIG}" || true
chmod 600 /home/opc/.oci/oci-signing-key.pem || true
export OCI_CLI_SUPPRESS_FILE_PERMISSIONS_WARNING=True

echo "[INFO] Searching for LBs tagged (Oracle-Tags.CreatedBy == ${TARGET_CLUSTER_OCID})"

# List all LB OCIDs in the compartment
LB_OCIDS=$(oci lb load-balancer list \
  --compartment-id "${COMPARTMENT_OCID}" \
  --all \
  --query 'data[].id' \
  --raw-output \
  --profile "${OCI_PROFILE}" \
  --config-file "${OCI_CONFIG}" || true)

if [[ -z "${LB_OCIDS}" ]]; then
  echo "[INFO] No Load Balancers found in compartment. Nothing to delete."
  exit 0
fi

FOUND_ANY=0

for LB_OCID in ${LB_OCIDS}; do
  # Read the tag value: defined-tags.Oracle-Tags.CreatedBy
  LB_CREATED_BY=$(oci lb load-balancer get \
    --load-balancer-id "${LB_OCID}" \
    --query 'data."defined-tags"."Oracle-Tags"."CreatedBy"' \
    --raw-output \
    --profile "${OCI_PROFILE}" \
    --config-file "${OCI_CONFIG}" 2>/dev/null || true)

  # Not tagged / missing key
  if [[ -z "${LB_CREATED_BY}" || "${LB_CREATED_BY}" == "null" ]]; then
    continue
  fi

  # Only delete if it matches the target cluster OCID
  if [[ "${LB_CREATED_BY}" != "${TARGET_CLUSTER_OCID}" ]]; then
    continue
  fi

  FOUND_ANY=1

  LB_NAME=$(oci lb load-balancer get \
    --load-balancer-id "${LB_OCID}" \
    --query 'data."display-name"' \
    --raw-output \
    --profile "${OCI_PROFILE}" \
    --config-file "${OCI_CONFIG}" || true)

  LB_STATE=$(oci lb load-balancer get \
    --load-balancer-id "${LB_OCID}" \
    --query 'data."lifecycle-state"' \
    --raw-output \
    --profile "${OCI_PROFILE}" \
    --config-file "${OCI_CONFIG}" || true)

  echo "[INFO] Matched LB: ${LB_NAME:-<unknown>} (${LB_OCID}) state=${LB_STATE}"

  if [[ "${LB_STATE}" == "DELETING" ]]; then
    echo "[INFO] Already deleting. Skipping."
    continue
  fi

  echo "[INFO] Deleting Load Balancer ${LB_OCID}"
  oci lb load-balancer delete \
    --load-balancer-id "${LB_OCID}" \
    --force \
    --profile "${OCI_PROFILE}" \
    --config-file "${OCI_CONFIG}"

  echo "[INFO] Delete request submitted for ${LB_OCID} (not waiting)"
done

if [[ "${FOUND_ANY}" -eq 0 ]]; then
  echo "[INFO] No Load Balancers found with Oracle-Tags.CreatedBy == ${TARGET_CLUSTER_OCID}. Nothing to delete."
fi
