// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import io.kubernetes.client.ProgressRequestBody;
import io.kubernetes.client.ProgressResponseBody;

public interface CallParams {
  /**
   * Returns a boolean indicating whether partially initialized results should be included in the
   * response.
   *
   * @return the current setting of the parameter. Defaults to including everything.
   */
  Boolean getIncludeUninitialized();

  /**
   * Returns the limit on the number of updates to send in a single reply.
   *
   * @return the current setting of the parameter. Defaults to 500.
   */
  Integer getLimit();

  /**
   * Returns the timeout for the call.
   *
   * @return the current setting. Defaults to 30 seconds.
   */
  Integer getTimeoutSeconds();

  /**
   * Returns a selector to limit results to those with matching fields.
   *
   * @return the option, if specified. Defaults to null, indicating no record filtering.
   */
  String getFieldSelector();

  /**
   * Returns a selector to limit results to those with matching labels.
   *
   * @return the option, if specified. Defaults to null, indicating no record filtering.
   */
  String getLabelSelector();

  /**
   * Returns the &#39;pretty-print&#39; option to be sent. If &#39;true&#39;, then the output is
   * pretty printed.
   *
   * @return the option, if specified. Defaults to null.
   */
  String getPretty();

  /**
   * On a watch call: when specified, shows changes that occur after that particular version of a
   * resource. Defaults to changes from the beginning of history. On a list call: when specified,
   * requests values at least as recent as the specified value. Defaults to returning the result
   * from remote storage based on quorum-read flag; - if it&#39;s 0, then we simply return what we
   * currently have in cache, no guarantee; - if set to non zero, then the result is at least as
   * fresh as given version.
   *
   * @return the current setting. Defaults to null.
   */
  String getResourceVersion();

  /**
   * Returns a listener for responses received, to specify on calls.
   *
   * @return the set listener. Defaults to null.
   */
  ProgressResponseBody.ProgressListener getProgressListener();

  /**
   * Returns a listener for requests sent, to specify on calls.
   *
   * @return the set listener. Defaults to null.
   */
  ProgressRequestBody.ProgressRequestListener getProgressRequestListener();
}
