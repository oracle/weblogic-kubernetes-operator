# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

data "template_file" "provision" {
  template = file("${path.module}/files/provision.template.sh")
  vars = {
    debug                             = var.debug
  }
}
