# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

admin_username=sys.argv[1]
admin_password=sys.argv[2]
admin_t3_url=sys.argv[3]
remhost=sys.argv[4]
remport=sys.argv[5]
script_name = 'setupPKI.py'
print 'script_name: ' + script_name

print 'admin_t3_url: ' + admin_t3_url
print 'admin_username: ' + admin_username
print 'admin_password: ' + admin_password
print 'remhost: ' + remhost
print 'remport: ' + remport

def setup_pki_sender():
  try:
    print 'connecting to admin server'
    connect(admin_username, admin_password, admin_t3_url)
    rlm = cmo.getSecurityConfiguration().getDefaultRealm()


    cmp = rlm.lookupCredentialMapper('PKICredentialMapper')

    mapping="type=<remote>, protocol=http, remoteHost=" + remhost + ", remotePort=" + remport + ", path=/samlSenderVouches/EchoService"
    cmp.setKeypairCredential(mapping, 'user_d1', Boolean('true'), None, 'user_d1', 'changeit')

    print 'done with setup_pki_sender'
    disconnect()
  except NameError, e:
    print('Apparently properties not set.')
    print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])
    exit(exitcode=1)
  except:
    print 'set failed'
    print dumpStack()
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

setup_pki_sender()
exit()
