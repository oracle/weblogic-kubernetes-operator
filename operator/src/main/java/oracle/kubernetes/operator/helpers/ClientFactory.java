// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.function.Supplier;

import io.kubernetes.client.ApiClient;

public interface ClientFactory extends Supplier<ApiClient> {

}
