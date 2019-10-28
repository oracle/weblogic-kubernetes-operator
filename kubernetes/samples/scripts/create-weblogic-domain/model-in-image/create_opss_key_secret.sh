# Create a sample opss key secret
#
# This script can be used to create secrets for:
#
# 1. OPSS Key passphrase. The OPSS key passphrae is the passphrase used for extracting a JRF domain OPSS key wallet, the secret allows
#  the infrastructure database schema to be reused in lifecycle updates or shared across different domains. If this is not set, the 
#  operator generate one for you but you will not be able to extract the key wallet and shared across the domains and if the domain is 
#  deleted, the data in the infrastructure domain cannot be assessed again.
#
kubectl -n sample-domain1-ns create secret generic opss-key-passphrase-secret \
  --from-literal=passphrase=welcome1


