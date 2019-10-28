# Create a sample wdt domain secret
#
# This script can be used to create secret for:
#
# 1. WDT encryption passphrase - If the WDT model is encrypted, you can createwdtpassword is the passphrase for model
# decryption, you can use this to create the k8 secret to store the encryption passphrase.  The WDT encrption utility is
#  used to encrypt passwords stored in the model.  Oracle recommends using kubernetes secrets to store password instead of 
# this
#
kubectl -n sample-domain1-ns create secret generic wdt-encrypt-passphrase-secret \
  --from-literal=passphrase=welcome1

