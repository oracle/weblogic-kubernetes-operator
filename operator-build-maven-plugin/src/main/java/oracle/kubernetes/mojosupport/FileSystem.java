// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojosupport;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;

public abstract class FileSystem {

  public static final FileSystem LIVE_FILE_SYSTEM = new LiveFileSystem();

  public abstract URL toUrl(File file) throws MalformedURLException;

  public abstract File[] listFiles(File directory);

  public abstract File[] listFiles(File directory, FilenameFilter filter);

  public abstract boolean exists(File file);

  public abstract boolean isDirectory(File file);

  public abstract boolean isWritable(File directory);

  public abstract void createDirectory(File directory);

  public abstract Writer createWriter(File file) throws IOException;

  public abstract Reader createReader(File file) throws IOException;

  public abstract long getLastModified(File file);

  private static class LiveFileSystem extends FileSystem {

    @Override
    public URL toUrl(File file) throws MalformedURLException {
      return file.toURI().toURL();
    }

    public File[] listFiles(File directory) {
      return directory.listFiles();
    }

    public File[] listFiles(File directory, FilenameFilter filter) {
      return directory.listFiles(filter);
    }

    @Override
    public boolean exists(File file) {
      return file.exists();
    }

    @Override
    public boolean isDirectory(File file) {
      return file.isDirectory();
    }

    @Override
    public boolean isWritable(File directory) {
      return directory.canWrite();
    }

    @Override
    public void createDirectory(File directory) {
      directory.mkdirs();
    }

    @Override
    public Writer createWriter(File file) throws IOException {
      return new FileWriter(file);
    }

    @Override
    public Reader createReader(File file) throws IOException {
      return new FileReader(file);
    }

    @Override
    public long getLastModified(File file) {
      return file.lastModified();
    }
  }
}
