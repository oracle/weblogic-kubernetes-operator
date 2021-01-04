// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojosupport;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

@SuppressWarnings("unused")
public class TestFileSystem extends FileSystem {

  protected static final File[] NO_FILES = new File[0];
  long lastModificationTime = 0;
  private final Map<File, List<File>> directories = new HashMap<>();
  private final Map<File, String> contents = new HashMap<>();
  private final Set<File> writeOnlyFiles = new HashSet<>();
  private final Map<File, Long> lastModified = new HashMap<>();
  private final Map<File, URL> urls = new HashMap<>();

  /**
   * Clear all defined test files.
   */
  public void clear() {
    directories.clear();
    contents.clear();
    writeOnlyFiles.clear();
    lastModified.clear();
    urls.clear();
  }

  public void touch(File file) {
    setLastModified(file, ++lastModificationTime);
  }

  public void setLastModified(File file, long lastmodifiedTime) {
    addFileIfNotDefined(file);
    lastModified.put(file, lastmodifiedTime);
  }

  private void addFileIfNotDefined(File file) {
    if (contents.containsKey(file)) {
      return;
    }
    addToParent(file);
    contents.put(file, "");
  }

  private void addToParent(File file) {
    File parent = file.getParentFile();
    if (parent != null) {
      addDirectoryIfNotDefined(parent);
      directories.get(parent).add(file);
    }
  }

  private void addDirectoryIfNotDefined(File dir) {
    if (directories.containsKey(dir)) {
      return;
    }
    addToParent(dir);
    directories.put(dir, new ArrayList<>());
  }

  public void defineFileContents(File file, String data) {
    touch(file);
    contents.put(file, data);
  }

  public String getContents(File file) {
    return contents.get(file);
  }

  public void makeWriteOnly(File file) {
    touch(file);
    writeOnlyFiles.add(file);
  }

  public void defineUrl(File file, URL url) {
    urls.put(file, url);
  }

  @Override
  public  URL toUrl(File file) {
    return urls.get(file);
  }

  public File[] listFiles(File directory) {
    return isDirectory(directory) ? getDirectoryContents(directory, null) : NO_FILES;
  }

  public File[] listFiles(File directory, FilenameFilter filter) {
    return isDirectory(directory) ? getDirectoryContents(directory, filter) : NO_FILES;
  }

  private File[] getDirectoryContents(File directory, FilenameFilter filter) {
    List<File> files = new ArrayList<>();
    for (File file : directories.get(directory)) {
      if (filter == null || filter.accept(file.getParentFile(), file.getName())) {
        files.add(file);
      }
    }
    return toArray(files);
  }

  private File[] toArray(List<File> files) {
    return files.toArray(NO_FILES);
  }

  public boolean exists(File file) {
    return contents.containsKey(file) || isDirectory(file);
  }

  public boolean isDirectory(File file) {
    return directories.containsKey(file);
  }

  public boolean isWritable(File file) {
    return !writeOnlyFiles.contains(file);
  }

  /**
   * Creates the specified directory.
   * @param directory a file which describes a directory
   */
  public void createDirectory(File directory) {
    if (!isDirectory(directory)) {
      directories.put(directory, new ArrayList<>());
    }
  }

  @Override
  public long getLastModified(File file) {
    return lastModified.containsKey(file) ? lastModified.get(file) : 0;
  }

  @Override
  public Writer createWriter(File file) throws IOException {
    if (!exists(file.getParentFile())) {
      throw new IOException("Parent directory " + file.getParentFile() + " does not exist");
    }
    return new TestFileWriter(file);
  }

  @Override
  public Reader createReader(File file) {
    return new TestFileReader(file);
  }

  class TestFileWriter extends Writer {
    private final StringBuilder sb = new StringBuilder();
    private final File file;

    TestFileWriter(File file) {
      this.file = file;
    }

    @Override
    public void write(@Nonnull char[] cbuf, int off, int len) {
      sb.append(cbuf, off, len);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
      contents.put(file, sb.toString());
    }
  }

  class TestFileReader extends Reader {
    private final StringReader reader;

    TestFileReader(File file) {
      reader = new StringReader(contents.get(file));
    }

    @Override
    public void close() {
    }

    @Override
    public int read(@Nonnull char[] cbuf, int off, int len) throws IOException {
      return reader.read(cbuf, off, len);
    }
  }
}
