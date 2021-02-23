# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import sys
import os
import traceback

from java.util import Base64
from java.nio.file import Files
from java.nio.file import Path
from java.io import File
from java.io import FileOutputStream

script_name = 'application_deployment.py'
print 'script_name: ' + script_name

print 'admin_host: ' + admin_host
print 'admin_port: ' + admin_port
print 'admin_username: ' + admin_username
print 'admin_password: ' + admin_password
print 'targets: ' + targets
print 'mounted archive: ' + node_archive_path


t3url = "t3://" + admin_host + ":" + admin_port
print 't3url: ' + t3url

archive_name = os.path.basename(node_archive_path)
print 'archive_name: ' + archive_name
application_name = archive_name.split('.')[0]
print 'application_name: ' + application_name

def usage():
  print 'Call script as: '
  print 'wlst.sh ' + script_name + ' -skipWLSModuleScanning -loadProperties domain.properties'

def decode_archive():
  try:
    print 'decoding archive...'
    encoded_archive_bytes = Files.readAllBytes(File(node_archive_path).toPath())
    decoded_archive_bytes = Base64.getMimeDecoder().decode(encoded_archive_bytes)
    fos = FileOutputStream(File(archive_name))
    fos.write(decoded_archive_bytes)
    print 'successfully decoded archive'
  except:
    print 'Decoding application archive failed'
    print dumpStack()
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

def deploy_application():
  try:
    print 'connecting to admin server'
    connect(admin_username, admin_password, t3url)
    print 'Running deploy(' + application_name + ', ' + archive_name + ', ' + targets + 'remote=\'true\', upload=\'true\')'
    deploy(application_name, archive_name, targets, remote='true', upload='true')
    print 'done with deployment'
    disconnect()
  except NameError, e:
    print('Apparently properties not set.')
    print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])
    usage()
    exit(exitcode=1)
  except:
    print 'Deployment failed'
    print dumpStack()
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

decode_archive()
deploy_application()
exit()

