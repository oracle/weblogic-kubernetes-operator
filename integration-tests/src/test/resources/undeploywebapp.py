# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

import time as systime
connect(sys.argv[1],sys.argv[2],sys.argv[3])
undeploy(sys.argv[4],timeout=60000)