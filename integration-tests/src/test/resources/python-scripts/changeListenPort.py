# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

admin_username=sys.argv[1]
admin_password=sys.argv[2]
admin_t3_url=sys.argv[3]
setListenPortEnabled=sys.argv[4]
server_name=sys.argv[5]
script_name = 'changeListenPort.py'
print 'script_name: ' + script_name

print 'admin_t3_url: ' + admin_t3_url
print 'admin_username: ' + admin_username
print 'admin_password: ' + admin_password
print 'setListenPortEnabled: ' + setListenPortEnabled
print 'server_name: ' + server_name

def change_listenportenabled():
  try:
    print 'connecting to admin server'
    connect(admin_username, admin_password, admin_t3_url)
    print 'Running change_listenportenabled(setListenPortEnabled=' + setListenPortEnabled + ')'
    edit()
    startEdit()


    cd ('ServerTemplates/cluster-1-template')
    isListenPortEnabled = eval(setListenPortEnabled)
    cmo.setListenPortEnabled(isListenPortEnabled)


    #configure SSL
    cd('SSL/cluster-1-template')
    cmo.setUseServerCerts(true)

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

change_listenportenabled()
exit()
