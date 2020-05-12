// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.net.http.HttpClient;
import java.util.function.Supplier;

public interface HttpClientFactory extends Supplier<HttpClient> {}
