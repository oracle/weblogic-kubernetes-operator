# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

admin_username=sys.argv[1]
admin_password=sys.argv[2]
admin_t3_url=sys.argv[3]
alias=sys.argv[4]
key_pass=sys.argv[5]
sport=sys.argv[6]
keystore_path=sys.argv[7]
keystore_name=sys.argv[8]
script_name = 'setupAdminSSL.py'
print 'script_name: ' + script_name

print 'admin_t3_url: ' + admin_t3_url
print 'admin_username: ' + admin_username
print 'admin_password: ' + admin_password

def setup_ssl():
  try:
    print 'connecting to admin server'
    connect(admin_username, admin_password, admin_t3_url)
    print 'Running setup_ssl'
    edit()

    #server=cmo.lookupServer('admin-server')
    #cd ('Servers/admin-server')

    startEdit()
    cd ('Servers/admin-server')

    set('KeyStores','CustomIdentityAndCustomTrust')
    set('CustomIdentityKeyStoreFileName', keystore_path + '/' + keystore_name)
    set('CustomIdentityKeyStorePassPhrase',key_pass)
    set('CustomTrustKeyStoreFileName',keystore_path + '/TrustKeyStore.jks')
    set('CustomTrustKeyStorePassPhrase', key_pass)
    set('CustomTrustKeyStoreType','JKS')
    set('CustomIdentityKeyStoreType','JKS')

    get('CustomIdentityKeyStoreFileName')

    cd ('SSL/admin-server')
    set('Enabled', Boolean('true'))
    set('ListenPort', Integer(sport))
    set('TwoWaySSLEnabled', Boolean('true'))
    set('UseServerCerts', Boolean('true'))
    set('ClientCertificateEnforced', Boolean('false'))
    set('ServerPrivateKeyAlias', alias)
    set('ServerPrivateKeyPassPhrase', key_pass)
    set('IdentityAndTrustLocations', 'KeyStores')


    save()
    activate(block='true')

    print 'done with sslconfig'
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

setup_ssl()
exit()
