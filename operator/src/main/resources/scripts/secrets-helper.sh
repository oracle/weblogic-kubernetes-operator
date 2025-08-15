#!/usr/bin/env bash
# Copyright (c) 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

source ${SCRIPTPATH}/utils.sh

# Set strict error handling
set -eo pipefail

# Configuration
JWT_FILE="/var/run/secrets/kubernetes.io/serviceaccount/token"
CA_CERT="/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"
NS="/var/run/secrets/kubernetes.io/serviceaccount/namespace"
K8S_API_URL="https://kubernetes.default.svc"
CURL_TIMEOUT=30
MAX_RETRIES=3

# Read the JWT token
get_jwt() {
    if [[ ! -f "$JWT_FILE" ]]; then
        trace SEVERE "JWT file not found: $JWT_FILE"
        exitOrLoop
    fi

    if [[ ! -r "$JWT_FILE" ]]; then
        trace SEVERE "Cannot read JWT file: $JWT_FILE"
        exitOrLoop
    fi

    JWT=$(cat "$JWT_FILE")
    if [[ -z "$JWT" ]]; then
        trace SEVERE "JWT token is empty"
        exitOrLoop
    fi

    trace "JWT token loaded successfully"
    return 0
}

# Retry function for network calls
retry_command() {
    local max_attempts=$1
    shift
    local attempt=1

    while [[ $attempt -le $max_attempts ]]; do
        if "$@"; then
            return 0
        fi

        if [[ $attempt -eq $max_attempts ]]; then
            trace SEVERE "Command failed after $max_attempts attempts: $*"
            exitOrLoop
        fi

        trace "Attempt $attempt failed, retrying in 2 seconds..."
        sleep 2
        ((attempt++))
    done
}

# Login to HashiCorp Vault
# Usage: login_hashicorp <role> <vault_url> [insecure]
login_hashicorp() {
    local role="$1"
    local vault_url="$2"
    local insecure="${3:-false}"

    # Validate inputs
    if [[ -z "$role" ]]; then
        trace SEVERE "Role parameter is required"
        exitOrLoop
    fi

    if [[ -z "$vault_url" ]]; then
        trace SEVERE "Vault URL parameter is required"
        exitOrLoop
    fi

    # Validate URL format
    if [[ ! "$vault_url" =~ ^https?:// ]]; then
        trace SEVERE "Invalid vault URL format: $vault_url"
        exitOrLoop
    fi

    #trace "Attempting to login to Vault with role: $role"

    local curl_opts=("--silent" "--show-error" "--fail" "--connect-timeout" "$CURL_TIMEOUT")
    if [[ "$insecure" == "true" ]]; then
        curl_opts+=("--insecure")
        #trace "Using insecure SSL connection"
    fi

    local payload
    payload=$(jq -n --arg jwt "$JWT" --arg role "$role" '{jwt: $jwt, role: $role}')

    local response
    if ! response=$(retry_command $MAX_RETRIES curl "${curl_opts[@]}" \
        --request POST \
        --header "Content-Type: application/json" \
        --data "$payload" \
        "$vault_url/v1/auth/kubernetes/login"); then
        trace SEVERE "Failed to connect to Vault at: $vault_url"
        exitOrLoop
    fi

    local client_token
    if ! client_token=$(echo "$response" | jq -r '.auth.client_token // empty'); then
        trace SEVERE "Failed to parse Vault response"
        exitOrLoop
    fi

    if [[ -z "$client_token" || "$client_token" == "null" ]]; then
        local error_msg
        error_msg=$(echo "$response" | jq -r '.errors[]? // "Unknown error"' 2>/dev/null || echo "Unknown error")
        trace SEVERE "Vault authentication failed: $error_msg"
        exitOrLoop
    fi

    #trace "Successfully authenticated with Vault"
    echo "$client_token"
    return 0
}

# Get secrets from HashiCorp Vault
# Usage: get_hashicorp_secrets <client_token> <vault_url> <path> <secret_name> [insecure]
get_hashicorp_secrets() {
    local client_token="$1"
    local vault_url="$2"
    local path="$3"
    local secret_name="$4"
    local insecure="${5:-false}"

    # Validate inputs
    if [[ -z "$client_token" ]]; then
        trace SEVERE "Client token parameter is required"
        exitOrLoop
    fi

    if [[ -z "$vault_url" || -z "$path" || -z "$secret_name" ]]; then
        trace SEVERE "All parameters (vault_url, path, secret_name) are required"
        exitOrLoop
    fi

    #trace "Retrieving secret: $secret_name from path: $path"

    local curl_opts=("--silent" "--show-error" "--fail" "--connect-timeout" "$CURL_TIMEOUT")
    if [[ "$insecure" == "true" ]]; then
        curl_opts+=("--insecure")
    fi

    local url="$vault_url/v1/$path/$secret_name"
    local response

    if ! response=$(retry_command $MAX_RETRIES curl "${curl_opts[@]}" \
        --header "X-Vault-Token: $client_token" \
        "$url"); then
        trace SEVERE "Failed to retrieve secret from: $url"
        exitOrLoop
    fi

    local result
    if ! result=$(echo "$response" | jq -r '.data.data // empty'); then
        trace SEVERE "Failed to parse secret response"
        exitOrLoop
    fi

    if [[ -z "$result" || "$result" == "null" ]]; then
        trace SEVERE "Secret not found or empty: $secret_name"
        exitOrLoop
    fi

    #trace "Successfully retrieved secret: $secret_name"
    echo "$result"
    return 0
}

get_k8s_secrets() {
    local namespace="$1"
    local secret_name="$2"

    # Validate inputs
    if [[ -z "$namespace" || -z "$secret_name" ]]; then
        trace SEVERE "Both namespace and secret_name parameters are required"
        exitOrLoop
    fi

    # Validate CA certificate exists
    if [[ ! -f "$CA_CERT" ]]; then
        trace SEVERE "CA certificate not found: $CA_CERT"
        exitOrLoop
    fi

    #trace "Retrieving K8s secret: $secret_name from namespace: $namespace"

    local url="$K8S_API_URL/api/v1/namespaces/$namespace/secrets/$secret_name"
    local response

    if ! response=$(retry_command $MAX_RETRIES curl \
        --silent \
        --show-error \
        --fail \
        --connect-timeout "$CURL_TIMEOUT" \
        --cacert "$CA_CERT" \
        --header "Authorization: Bearer $JWT" \
        "$url"); then
        trace SEVERE "Failed to retrieve K8s secret: $secret_name"
        exitOrLoop
    fi

    local result
    if ! result=$(echo "$response" | jq -r '.data // empty'); then
        trace SEVERE "Failed to parse K8s secret response"
        exitOrLoop
    fi

    if [[ -z "$result" || "$result" == "null" ]]; then
        trace SEVERE "K8s secret not found or empty: $secret_name"
        exitOrLoop
    fi

    #trace "Successfully retrieved K8s secret: $secret_name"
    echo "$result"
    return 0
}

# Get WebLogic Kubernetes Operator (WKO) domain
# Usage: get_wko_domain <namespace> <domain_name>
get_wko_domain() {
    local namespace="$1"
    local domain_name="$2"

    # Validate inputs
    if [[ -z "$namespace" || -z "$domain_name" ]]; then
        trace SEVERE "Both namespace and domain_name parameters are required"
        exitOrLoop
    fi

    # Validate CA certificate exists
    if [[ ! -f "$CA_CERT" ]]; then
        trace SEVERE "CA certificate not found: $CA_CERT"
        exitOrLoop
    fi

    #trace "Retrieving WKO domain: $domain_name from namespace: $namespace"

    local url="$K8S_API_URL/apis/weblogic.oracle/v9/namespaces/$namespace/domains/$domain_name"
    local response

    if ! response=$(retry_command $MAX_RETRIES curl \
        --silent \
        --show-error \
        --fail \
        --connect-timeout "$CURL_TIMEOUT" \
        --cacert "$CA_CERT" \
        --header "Authorization: Bearer $JWT" \
        "$url"); then
        trace SEVERE "Failed to retrieve WKO domain: $domain_name"
        exitOrLoop
    fi

    local result
    if ! result=$(echo "$response" | jq -r '. // empty'); then
        trace SEVERE "Failed to parse WKO domain response"
        exitOrLoop
    fi

    if [[ -z "$result" || "$result" == "null" ]]; then
        trace SEVERE "WKO domain not found or empty: $domain_name"
        exitOrLoop
    fi

    #trace "Successfully retrieved WKO domain: $domain_name"
    echo "$result"
    return 0
}

# json file key value pair
# output dir
# base64 decrypt or not
extract_secret_to_dir() {

  input_json="$1"

  # Output directory
  output_dir="$2"

  secret_name="$3"

  base_64_decode="$4"

  # Create the output directory if it doesn't exist
  mkdir -p "$output_dir/$secret_name"
  if [ $? -ne 0 ] ; then
    trace SEVERE "Error: Cannot create directory $output_dir"
    exitOrLoop
  fi
  # Get all keys from .data and loop through them
  keys=$(echo "$input_json" | jq -r 'keys[]')

  if [ $? -ne 0 ] ; then
    trace SEVERE "Error: jq cannot process keys $keys"
    exitOrLoop
  fi
  for key in $keys; do
    # Extract and decode the value for the current key
    value=$(echo "$input_json" | jq -r ".\"$key\"")
    if [[ $? -eq 0 ]]; then
      if [ "$base_64_decode" == "true" ] ; then
        echo "$value" | base64 -d > "$output_dir/$secret_name/$key"
      else
        echo "$value" > "$output_dir/$secret_name/$key"
      fi
      #trace "Created $output_dir/$secret_name/$key with decoded value"
    else
      trace SEVERE "Error: Failed to extract or decode $key"
      exitOrLoop
    fi
  done

}

get_current_namespace() {
  cat $NS
}

get_domain_config_secrets() {
  get_wko_domain "$(get_current_namespace)" "$DOMAIN_UID" | jq -r '.spec.configuration.secrets'
}

get_domain_weblogic_credential_secret() {
  get_wko_domain "$(get_current_namespace)" "$DOMAIN_UID" | jq -r '.spec.webLogicCredentialsSecret'
}

get_domain_runtime_encryption_secret() {
  get_wko_domain "$(get_current_namespace)" "$DOMAIN_UID" | jq -r '.spec.configuration.model.runtimeEncryptionSecret'
}

get_domain_opss_wallet_password_secret() {
  get_wko_domain "$(get_current_namespace)" "$DOMAIN_UID" | jq -r '.spec.configuration.opss.walletPasswordSecret'
}

get_domain_opss_wallet_file_secret() {
  get_wko_domain "$(get_current_namespace)" "$DOMAIN_UID" | jq -r '.spec.configuration.opss.walletFileSecret'
}

process_k8s_config_secrets() {
  list=$(get_domain_config_secrets)
  if [ "null" != "$list" ] ; then
    output_path="/weblogic-operator/tmpfs/config-overrides-secrets"
    echo $list | jq -r '.[]' | while IFS= read -r item; do
       secret_data=$(get_k8s_secrets "$(get_current_namespace)" "$item")
       extract_secret_to_dir "$secret_data" "$output_path" "$item" "true"
    done
  fi
}

process_hashicorp_config_secrets() {
  list=$(get_domain_config_secrets)
  if [ "null" != "$list" ] ; then
    role=$1
    vault_url=$2
    secret_path=$3
    output_path="/weblogic-operator/tmpfs/config-overrides-secrets"
    client_token=$(login_hashicorp "$role" "$vault_url")
    echo $list | jq -r '.[]' | while IFS= read -r item; do
       secret_data=$(get_hashicorp_secrets "$client_token" "$vault_url" "$secret_path" "$item")
       extract_secret_to_dir "$secret_data" "$output_path" "$item" "false"
    done
  fi
}

process_k8s_weblogic_credential() {
  secret_name=$(get_domain_weblogic_credential_secret)
  if [ "null" != "$secret_name" ] ; then
    secret_data=$(get_k8s_secrets "$(get_current_namespace)" "$secret_name")
    extract_secret_to_dir "$secret_data" "/weblogic-operator/tmpfs/secrets" "$secret_name" "true"
  else
    trace SEVERE "Error: spec.webLogicCredentialsSecret not set in domain resource"
    exitOrLoop
  fi
}

process_k8s_runtime_encryption_secret() {
  secret_name=$(get_domain_runtime_encryption_secret)
  if [ "null" != "$secret_name" ] ; then
    secret_data=$(get_k8s_secrets "$(get_current_namespace)" "$secret_name")
    extract_secret_to_dir "$secret_data" "/weblogic-operator/tmpfs/model-runtime-secret" "$secret_name" "true"
  else
    trace SEVERE "Error: spec.configuration.model.runtimeEncryptionSecret not set in domain resource"
    exitOrLoop
  fi
}

process_k8s_opss_secret() {
  secret_name=$(get_domain_opss_wallet_password_secret)
  if [ "null" != "$secret_name" ] ; then
    secret_data=$(get_k8s_secrets "$(get_current_namespace)" "$secret_name")
    extract_secret_to_dir "$secret_data" "/weblogic-operator/tmpfs/opss-walletkey-secret" "$secret_name" "true"
  fi
  secret_name=$(get_domain_opss_wallet_file_secret)
  if [ "null" != "$secret_name" ] ; then
    secret_data=$(get_k8s_secrets "$(get_current_namespace)" "$secret_name")
    extract_secret_to_dir "$secret_data" "/weblogic-operator/tmpfs/opss-walletfile-secret" "$secret_name" "true"
  fi
}


process_hashicorp_weblogic_credential() {
  secret_name=$(get_domain_weblogic_credential_secret)
  if [ "null" != "$secret_name" ] ; then
    role=$1
    vault_url=$2
    secret_path=$3
    secret_data=$(get_hashicorp_secrets "$client_token" "$vault_url" "$secret_path" "$secret_name")
    extract_secret_to_dir "$secret_data" "/weblogic-operator/tmpfs/secrets" "$secret_name" "true"
  else
    trace SEVERE "Error: spec.webLogicCredentialsSecret not set in domain resource"
    exitOrLoop
  fi
}

process_hashicorp_runtime_encryption_secret() {
  secret_name=$(get_domain_runtime_encryption_secret)
  if [ "null" != "$secret_name" ] ; then
    role=$1
    vault_url=$2
    secret_path=$3
    secret_data=$(get_hashicorp_secrets "$client_token" "$vault_url" "$secret_path" "$secret_name")
    extract_secret_to_dir "$secret_data" "/weblogic-operator/tmpfs/model-runtime-secret" "$secret_name" "true"
  else
    trace SEVERE "Error: spec.configuration.model.runtimeEncryptionSecret not set in domain resource"
    exitOrLoop
  fi
}

process_hashicorp_opss_secret() {
  secret_name=$(get_domain_opss_wallet_password_secret)
  role=$1
  vault_url=$2
  secret_path=$3
  if [ "null" != "$secret_name" ] ; then
    secret_data=$(get_hashicorp_secrets "$client_token" "$vault_url" "$secret_path" "$secret_name")
    extract_secret_to_dir "$secret_data" "/weblogic-operator/tmpfs/opss-walletkey-secret" "$secret_name" "true"
  fi
  secret_name=$(get_domain_opss_wallet_file_secret)
  if [ "null" != "$secret_name" ] ; then
    secret_data=$(get_hashicorp_secrets "$client_token" "$vault_url" "$secret_path" "$secret_name")
    extract_secret_to_dir "$secret_data" "/weblogic-operator/tmpfs/opss-walletfile-secret" "$secret_name" "true"
  fi
}

