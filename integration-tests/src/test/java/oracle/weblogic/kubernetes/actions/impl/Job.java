// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Job {

  /**
   * Create a job.
   *
   * @param jobBody V1Job object containing job configuration data
   * @return String job name if job creation is successful
   * @throws ApiException when create job fails
   */
  public static String createNamespacedJob(V1Job jobBody) throws ApiException {
    return Kubernetes.createNamespacedJob(jobBody);
  }

  /**
   * Get V1Job object if any exists in the namespace with given job name.
   *
   * @param jobName name of the job
   * @param namespace name of the namespace in which to get the job object
   * @return V1Job object if any exists otherwise null
   * @throws ApiException when Kubernetes cluster query fails
   */
  public static V1Job getJob(String jobName, String namespace) throws ApiException {
    return Kubernetes.getJob(jobName, namespace);
  }

  /**
   * Delete a job.
   *
   * @param jobName name of the job
   * @param namespace name of the namespace
   * @return true if delete was successful
   * @throws ApiException when deletion of job fails
   */
  public static boolean deleteJob(String jobName, String namespace) throws ApiException {
    return Kubernetes.deleteJob(namespace, jobName);
  }

  /**
   * List jobs in the given namespace.
   *
   * @param namespace in which to list the jobs
   * @return V1JobList list of {@link V1Job} from Kubernetes cluster
   * @throws ApiException when list fails
   */
  public static V1JobList listJobs(String namespace) throws ApiException {
    return Kubernetes.listJobs(namespace);
  }
}