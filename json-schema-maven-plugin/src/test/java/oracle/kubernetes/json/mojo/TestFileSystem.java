// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestFileSystem extends FileSystem {

  private Map<File, List<File>> directories = new HashMap<File, List<File>>();

  private Map<File, String> contents = new HashMap<File, String>();

  private Set<File> writeOnlyFiles = new HashSet<File>();

  private Map<File, Long> lastModified = new HashMap<File, Long>();

  private Map<File, URL> urls = new HashMap<>();

  long lastModificationTime = 0;

  public void touch(File file) {
    setLastModified(file, ++lastModificationTime);
  }

  public void setLastModified(File file, long lastmodifiedTime) {
    addFileIfNotDefined(file);
    lastModified.put(file, lastmodifiedTime);
  }

  private void addFileIfNotDefined(File file) {
    if (contents.containsKey(file)) return;
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
    if (directories.containsKey(dir)) return;
    addToParent(dir);
    directories.put(dir, new ArrayList<File>());
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

  void defineURL(File file, URL url) {
    urls.put(file, url);
  }

  @Override
  URL toURL(File file) throws MalformedURLException {
    return urls.get(file);
  }

  File[] listFiles(File directory) {
    return isDirectory(directory) ? getDirectoryContents(directory, null) : null;
  }

  File[] listFiles(File directory, FilenameFilter filter) {
    return isDirectory(directory) ? getDirectoryContents(directory, filter) : null;
  }

  private File[] getDirectoryContents(File directory, FilenameFilter filter) {
    List<File> files = new ArrayList<File>();
    for (File file : directories.get(directory))
      if (filter == null || filter.accept(file.getParentFile(), file.getName())) files.add(file);
    return files.toArray(new File[files.size()]);
  }

  private File[] toArray(List<File> files) {
    return files.toArray(new File[files.size()]);
  }

  boolean exists(File file) {
    return contents.containsKey(file) || isDirectory(file);
  }

  boolean isDirectory(File file) {
    return directories.containsKey(file);
  }

  boolean isWritable(File file) {
    return !writeOnlyFiles.contains(file);
  }

  void createDirectory(File directory) {
    if (!isDirectory(directory)) directories.put(directory, new ArrayList<File>());
  }

  @Override
  long getLastModified(File file) {
    return lastModified.containsKey(file) ? lastModified.get(file) : 0;
  }

  @Override
  Writer createWriter(File file) throws IOException {
    if (!exists(file.getParentFile()))
      throw new IOException("Parent directory " + file.getParentFile() + " does not exist");
    return new TestFileWriter(file);
  }

  @Override
  Reader createReader(File file) throws IOException {
    return new TestFileReader(file);
  }

  class TestFileWriter extends Writer {
    private StringBuilder sb = new StringBuilder();
    private File file;

    TestFileWriter(File file) {
      this.file = file;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
      sb.append(cbuf, off, len);
    }

    @Override
    public void flush() throws IOException {}

    @Override
    public void close() throws IOException {
      contents.put(file, sb.toString());
    }
  }

  class TestFileReader extends Reader {
    private StringReader reader;

    TestFileReader(File file) {
      reader = new StringReader(contents.get(file));
    }

    @Override
    public void close() throws IOException {}

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
      return reader.read(cbuf, off, len);
    }
  }
}
