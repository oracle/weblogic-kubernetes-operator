#!/bin/bash
# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Check if enough arguments are provided
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <IP_ADDRESS> <COMPARTMENT_ID>"
  exit 1
fi

# Assigning arguments to variables
TARGET_IP="$1"
COMPARTMENT_ID="$2"

# List load balancers in the specified compartment
LOAD_BALANCER_OCID=$(oci lb load-balancer list --compartment-id $COMPARTMENT_ID --query "data[?contains(\"ip-addresses\"[0].\"ip-address\", '$TARGET_IP')].id | [0]" --raw-output --all)
# Check if a load balancer with the specified IP was found
if [ -z "$LOAD_BALANCER_OCID" ]; then
  echo "No load balancer found with IP address: $TARGET_IP"
else
  echo "Found load balancer with OCID: $LOAD_BALANCER_OCID"
  # Delete the load balancer
  oci lb load-balancer delete --load-balancer-id $LOAD_BALANCER_OCID --force
  echo "Deletion initiated for load balancer with OCID: $LOAD_BALANCER_OCID"
fi