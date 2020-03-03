# Copyright (c) 2020, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
import weblogic.security.internal.SerializedSystemIni as SerializedSystemIni
import weblogic.security.internal.encryption.ClearOrEncryptedService as ClearOrEncryptedService
import sys
import java.lang.System as System

def decrypt_model(domain_dir, input, output):
    try:
        fin = open(input)
        text = fin.read()
        fin.close()
        system_ini = SerializedSystemIni.getEncryptionService(domain_dir)
        encryption_service = ClearOrEncryptedService(system_ini)
        decrypted_text = encryption_service.decrypt(text)
        fout = open(output, "w")
        fout.write(decrypted_text)
        fout.close()
    except Exception, e:
        raise e

def encrypt_model(domain_dir, input, output):
    try:
        fin = open(input)
        text = fin.read()
        fin.close()
        system_ini = SerializedSystemIni.getEncryptionService(domain_dir)
        encryption_service = ClearOrEncryptedService(system_ini)
        encrypted_text = encryption_service.encrypt(text)
        fout = open(output, "w")
        fout.write(encrypted_text)
        fout.close()
    except Exception, e:
        raise e

if __name__ == "__main__":
    try:
        action = sys.argv[1]
        domain_dir = sys.argv[2]
        input_file = sys.argv[3]
        output_file = sys.argv[4]
        if action == "decrypt":
            decrypt_model(domain_dir, input_file, output_file)
        else:
            if action == "encrypt":
                encrypt_model(domain_dir, input_file, output_file)
            else:
                raise Exception("Invalid action")
        System.exit(0)
    except Exception, e:
        print e
        System.exit(-1)
