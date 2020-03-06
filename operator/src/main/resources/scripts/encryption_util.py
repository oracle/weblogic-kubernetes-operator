# Copyright (c) 2020, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# ------------
# Description:
# ------------
#
#   This code uses WDT encryption utility class EncryptionUtils
#   for encrypting and decryption the domain secret SerializedSystemIni.dat
#
#   This script is invoked by jython.  See modelInImage.sh encrypt_decrypt_domain_secret
#   It's the user responsibility to save off the original file if needed
#

from oracle.weblogic.deploy.encrypt import EncryptionUtils
from java.lang import String
import sys, os, traceback
from java.lang import System

def decrypt_domain_secret(domain_home, cipher_text, password):
      try:
        pwd = String(password)
        x = EncryptionUtils.decryptString(cipher_text, pwd.toCharArray());
        restored_text = String(x)
        fh = open(domain_home + "/security/SerializedSystemIni.dat", "w")
        fh.write(str(restored_text))
        fh.close()
        System.exit(0)
      except:
          exc_type, exc_obj, exc_tb = sys.exc_info()
          eeString = traceback.format_exception(exc_type, exc_obj, exc_tb)
          print eeString
          System.exit(-1)

def encrypt_domain_secret(domain_home, b64_serialized_ini, password):
      try:
        pwd = String(password)
        x = EncryptionUtils.encryptString(b64_serialized_ini, pwd.toCharArray());
        encrypted_text = String(x)
        fh = open(domain_home + "/security/SerializedSystemIni.dat", "w")
        fh.write(str(encrypted_text))
        fh.close()
        System.exit(0)
      except:
          exc_type, exc_obj, exc_tb = sys.exc_info()
          eeString = traceback.format_exception(exc_type, exc_obj, exc_tb)
          print eeString
          System.exit(-1)

if __name__ == "__main__":
    if sys.argv[1] == 'encrypt':
        encrypt_domain_secret(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        if sys.argv[1] == 'decrypt':
            decrypt_domain_secret(sys.argv[2], sys.argv[3], sys.argv[4])


