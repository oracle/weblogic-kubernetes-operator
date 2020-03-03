# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import time as systime

connect(sys.argv[1],sys.argv[2],sys.argv[3])
edit()
startEdit()
cd('/Servers/admin-server')
cmo.setAcceptBacklog(4000)
save()
activate()
