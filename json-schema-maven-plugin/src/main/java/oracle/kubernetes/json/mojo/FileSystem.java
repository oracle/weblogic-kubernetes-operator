// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;

abstract class FileSystem {

  static final FileSystem LIVE_FILE_SYSTEM = new LiveFileSystem();

  abstract URL toURL(File file) throws MalformedURLException;

  abstract File[] listFiles(File directory);

  abstract File[] listFiles(File directory, FilenameFilter filter);

  abstract boolean exists(File file);

  abstract boolean isDirectory(File file);

  abstract boolean isWritable(File directory);

  abstract void createDirectory(File directory);

  abstract Writer createWriter(File file) throws IOException;

  abstract Reader createReader(File file) throws IOException;

  abstract long getLastModified(File file);

  private static class LiveFileSystem extends FileSystem {

    @Override
    URL toURL(File file) throws MalformedURLException {
      return file.toURI().toURL();
    }

    File[] listFiles(File directory) {
      return directory.listFiles();
    }

    File[] listFiles(File directory, FilenameFilter filter) {
      return directory.listFiles(filter);
    }

    @Override
    boolean exists(File file) {
      return file.exists();
    }

    @Override
    boolean isDirectory(File file) {
      return file.isDirectory();
    }

    @Override
    boolean isWritable(File directory) {
      return directory.canWrite();
    }

    @Override
    void createDirectory(File directory) {
      directory.mkdirs();
    }

    @Override
    Writer createWriter(File file) throws IOException {
      return new FileWriter(file);
    }

    @Override
    Reader createReader(File file) throws IOException {
      return new FileReader(file);
    }

    @Override
    long getLastModified(File file) {
      return file.lastModified();
    }
  }
}
