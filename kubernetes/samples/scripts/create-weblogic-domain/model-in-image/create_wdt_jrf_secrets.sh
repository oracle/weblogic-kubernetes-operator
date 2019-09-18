# Create a sample wdt domain secret
#
# wdtpassword is the passphrase for model decryption, if you model or variable files have been encrypted.
# opsspassphrase is the passphrase for extracting the OPSS wallet key.  It allows the JRF infrastruture
#   database schema to be reused for lifecycle updates.   It must be at least 8 characters long including
#   1 special character or digit
#  
#
kubectl -n sample-domain1-ns create secret generic simple-domain1-wdt-secret \
  --from-literal=wdtpassword=welcome1 \
  --from-literal=opsspassphrase=welcome1