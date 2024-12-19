# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

hostname=sys.argv[1]
connect('weblogic','welcome1','t3://'+hostname+':7001')
shutdown('managed-server1', 'Server', ignoreSessions='true', force='true')
shutdown('managed-server2', 'Server', ignoreSessions='true', force='true')
shutdown('admin-server', 'Server', ignoreSessions='true', force='true')
disconnect()
exit()

