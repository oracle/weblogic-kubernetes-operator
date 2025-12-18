#!/usr/bin/env bash
set -euo pipefail

LB_IP="${1:-}"
COMPARTMENT_OCID="${2:-}"

if [[ -z "${LB_IP}" || -z "${COMPARTMENT_OCID}" ]]; then
  echo "[ERROR] Usage: $0 <lb-ip> <compartment-ocid>"
  exit 1
fi

OCI_CLI_PROFILE="DEFAULT"
OCI_CLI_CONFIG_FILE="/home/opc/.oci/config"

: "${OCI_CLI_CONFIG_FILE:?OCI_CLI_CONFIG_FILE not set}"
: "${OCI_CLI_PROFILE:?OCI_CLI_PROFILE not set}"

echo "[DEBUG] Using OCI config : ${OCI_CLI_CONFIG_FILE}"
echo "[DEBUG] Using OCI profile: ${OCI_CLI_PROFILE}"
echo "[DEBUG] LB IP           : ${LB_IP}"
echo "[DEBUG] Compartment OCID: ${COMPARTMENT_OCID}"

########################################
# Resolve Load Balancer OCID from IP
########################################
LB_OCID=$(
  oci lb load-balancer list \
    --compartment-id "${COMPARTMENT_OCID}" \
    --all \
    --query "data[?ip-addresses && ip-addresses[0].\"ip-address\"=='${LB_IP}'].id | [0]" \
    --raw-output
)

if [[ -z "${LB_OCID}" || "${LB_OCID}" == "null" ]]; then
  echo "[INFO] No Load Balancer found for IP ${LB_IP}"
  exit 0
fi

echo "[INFO] Found Load Balancer OCID: ${LB_OCID}"

########################################
# Get lifecycle state
########################################
LB_STATE=$(
  oci lb load-balancer get \
    --load-balancer-id "${LB_OCID}" \
    --query "data.\"lifecycle-state\"" \
    --raw-output 2>/dev/null || true
)

if [[ -z "${LB_STATE}" || "${LB_STATE}" == "null" ]]; then
  echo "[INFO] Load Balancer already deleted: ${LB_OCID}"
  exit 0
fi

echo "[INFO] Load Balancer state: ${LB_STATE}"

########################################
# Skip if already deleting
########################################
if [[ "${LB_STATE}" == "DELETING" ]]; then
  echo "[INFO] Load Balancer already DELETING, skipping delete"
  exit 0
fi

########################################
# Delete (fire-and-forget)
########################################
echo "[INFO] Deleting Load Balancer ${LB_OCID}"

oci lb load-balancer delete \
  --load-balancer-id "${LB_OCID}" \
  --force

echo "[SUCCESS] Delete request issued for ${LB_OCID}"
