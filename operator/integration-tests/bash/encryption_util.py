# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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

def decrypt_file(cipher_text, password, outputfile):
      try:
        pwd = String(password)
        x = EncryptionUtils.decryptString(cipher_text, pwd.toCharArray());
        restored_text = String(x)
        fh = open(outputfile, "w")
        fh.write(str(restored_text))
        fh.close()
        System.exit(0)
      except:
          exc_type, exc_obj, exc_tb = sys.exc_info()
          eeString = traceback.format_exception(exc_type, exc_obj, exc_tb)
          print eeString
          System.exit(-1)

def encrypt_file(clear_text, password, outputfile):
      try:
        pwd = String(password)
        x = EncryptionUtils.encryptString(clear_text, pwd.toCharArray());
        encrypted_text = String(x)
        fh = open(outputfile, "w")
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
        encrypt_file(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        if sys.argv[1] == 'decrypt':
            decrypt_file(sys.argv[2], sys.argv[3], sys.argv[4])


