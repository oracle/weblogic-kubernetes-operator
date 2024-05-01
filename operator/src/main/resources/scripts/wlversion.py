# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import weblogic.version as version_helper
fh = open('/tmp/wlsversion.txt', 'w')
fh.write(version_helper.getReleaseBuildVersion())
fh.close()
exit()
