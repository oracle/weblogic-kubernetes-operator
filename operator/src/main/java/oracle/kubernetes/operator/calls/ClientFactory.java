// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.io.IOException;

import io.kubernetes.client.openapi.ApiClient;

public interface ClientFactory {
  ApiClient get() throws IOException;
}