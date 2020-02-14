// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.job;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for the create domain kubernetes job.
 */
@Ignore
public class CreateDomainTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void createDomainJobTest() {
    try {
      // Copy the create domain scripts to the temporary folder
      final Path srcFile1 = Paths.get("kubernetes/create-domain-job.sh");
      final Path srcFile2 = Paths.get("kubernetes/internal/domain-job-template.yaml");
      final Path srcFile3 = Paths.get("kubernetes/internal/persistent-volume-template.yaml");
      final Path srcFile4 = Paths.get("kubernetes/internal/persistent-volume-claim-template.yaml");

      final Path dstDir = folder.getRoot().toPath();
      Path createJobScript = dstDir.resolve(srcFile1.getFileName());
      final Path createJobTemplate = dstDir.resolve(srcFile2.getFileName());
      final Path createPvTemplate = dstDir.resolve(srcFile3.getFileName());
      final Path createPvcTemplate = dstDir.resolve(srcFile4.getFileName());

      Files.copy(srcFile1, createJobScript);
      Files.copy(srcFile2, createJobTemplate);
      Files.copy(srcFile3, createPvTemplate);
      Files.copy(srcFile4, createPvcTemplate);

      // Generate a domain UID
      final String domainUid = "domain" + "9999";

      // Edit the script to generate a domain for this test
      String content = new String(Files.readAllBytes(createJobScript));
      content = content.replace("pv001-claim", "pv9999-claim");
      content = content.replace("domain1", domainUid);
      Files.write(createJobScript, content.getBytes());

      // Generate the create domain job for kubernetes
      Process p = new ProcessBuilder("/bin/sh", createJobScript.toString()).start();
      p.waitFor();
      Assert.assertTrue(p.exitValue() == 0);

      File createDomainJob =
          new File(dstDir.toString() + File.separator + "create-domain-job.yaml");
      Assert.assertTrue(createDomainJob.exists());

    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }
  }
}
