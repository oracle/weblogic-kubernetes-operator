#!/usr/bin/env bash
set -euo pipefail

LB_IP="$1"
COMPARTMENT_OCID="$2"

if [[ -z "${LB_IP}" || -z "${COMPARTMENT_OCID}" ]]; then
  echo "[ERROR] Usage: $0 <lb-ip> <compartment-ocid>"
  exit 1
fi

OCI_CONFIG="${OCI_CLI_CONFIG_FILE:-/home/opc/.oci/config}"
OCI_PROFILE="${OCI_CLI_PROFILE:-DEFAULT}"

echo "[DEBUG] Using OCI config : ${OCI_CONFIG}"
echo "[DEBUG] Using OCI profile: ${OCI_PROFILE}"
echo "[DEBUG] LB IP           : ${LB_IP}"
echo "[DEBUG] Compartment OCID: ${COMPARTMENT_OCID}"

# ---- OCI permissions (non-interactive, silent) ----
chmod 600 "${OCI_CONFIG}" || true
chmod 600 /home/opc/.oci/oci-signing-key.pem || true
export OCI_CLI_SUPPRESS_FILE_PERMISSIONS_WARNING=True

# ---- Find LB OCID by public IP ----
LB_OCID=$(oci lb load-balancer list \
  --compartment-id "${COMPARTMENT_OCID}" \
  --all \
  --query 'data[?"ip-addresses" && "ip-addresses"[0]."ip-address"=='\'''"${LB_IP}"''\''].id | [0]' \
  --raw-output \
  --profile "${OCI_PROFILE}" \
  --config-file "${OCI_CONFIG}" || true)

if [[ -z "${LB_OCID}" || "${LB_OCID}" == "null" ]]; then
  echo "[INFO] No Load Balancer found for IP ${LB_IP}. Nothing to delete."
  exit 0
fi

echo "[INFO] Found Load Balancer OCID: ${LB_OCID}"

# ---- Check lifecycle state ----
LB_STATE=$(oci lb load-balancer get \
  --load-balancer-id "${LB_OCID}" \
  --query 'data."lifecycle-state"' \
  --raw-output \
  --profile "${OCI_PROFILE}" \
  --config-file "${OCI_CONFIG}")

echo "[INFO] Load Balancer state: ${LB_STATE}"

if [[ "${LB_STATE}" == "DELETING" ]]; then
  echo "[INFO] Load Balancer already deleting. Skipping."
  exit 0
fi

# ---- Delete (do NOT wait) ----
echo "[INFO] Deleting Load Balancer ${LB_OCID}"
oci lb load-balancer delete \
  --load-balancer-id "${LB_OCID}" \
  --force \
  --profile "${OCI_PROFILE}" \
  --config-file "${OCI_CONFIG}"

echo "[INFO] Delete request submitted (not waiting)"
