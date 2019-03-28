// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static com.meterware.simplestub.Stub.createStrictStub;

import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;

public abstract class InMemoryFileSystem extends FileSystem {
  private FileSystemProviderStub provider = createStrictStub(FileSystemProviderStub.class);

  private static InMemoryFileSystem instance;

  public static InMemoryFileSystem createInstance() {
    return instance = createStrictStub(InMemoryFileSystem.class);
  }

  public void defineFile(String filePath, String contents) {
    instance.defineFileContents(filePath, contents);
  }

  @Nonnull
  public Path getPath(String first, String... more) {
    return createStrictStub(PathStub.class, createPathString(first, more));
  }

  private String createPathString(String first, String[] more) {
    return more.length == 0 ? first : first + "/" + String.join("/", more);
  }

  @Override
  public FileSystemProvider provider() {
    return provider;
  }

  private void defineFileContents(String filePath, String contents) {
    provider.fileContents.put(filePath, contents);
  }

  abstract static class PathStub implements Path {
    private String filePath;

    PathStub(String filePath) {
      this.filePath = filePath;
    }

    @Override
    public FileSystem getFileSystem() {
      return instance;
    }

    @Override
    public Path getFileName() {
      return this;
    }

    @Override
    public String toString() {
      return filePath;
    }
  }

  abstract static class FileSystemProviderStub extends FileSystemProvider {
    private Map<String, String> fileContents = new HashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <A extends BasicFileAttributes> A readAttributes(
        Path path, Class<A> type, LinkOption... options) {
      if (!type.equals(BasicFileAttributes.class))
        throw new IllegalArgumentException("attributes type " + type + " not supported");
      return (A) createAttributes(getFilePath(path));
    }

    static String getFilePath(Path path) {
      if (!(path instanceof PathStub))
        throw new IllegalArgumentException(path.getClass() + " not supported");

      return ((PathStub) path).filePath;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DirectoryStream<Path> newDirectoryStream(
        Path dir, DirectoryStream.Filter<? super Path> filter) {
      return createStrictStub(DirectoryStreamStub.class, this, getFilePath(dir));
    }

    @Override
    public SeekableByteChannel newByteChannel(
        Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
        throws FileNotFoundException {
      return Optional.ofNullable(fileContents.get(getFilePath(path)))
          .map(s -> createStrictStub(SeekableByteChannelStub.class, s))
          .orElseThrow(() -> new FileNotFoundException(path.toString()));
    }

    private BasicFileAttributes createAttributes(String filePath) {
      return createStrictStub(BasicFileAttributesStub.class, isDirectory(filePath));
    }

    private PathType isDirectory(String filePath) {
      for (String key : fileContents.keySet()) {
        if (key.startsWith(filePath + '/')) return PathType.DIRECTORY;
        if (key.equals(filePath)) return PathType.FILE;
      }
      return PathType.NONE;
    }
  }

  enum PathType {
    DIRECTORY {
      @Override
      boolean isDirectory() {
        return true;
      }
    },
    FILE {
      @Override
      boolean isRegularFile() {
        return true;
      }
    },
    NONE;

    boolean isDirectory() {
      return false;
    };

    boolean isRegularFile() {
      return false;
    }
  }

  abstract static class BasicFileAttributesStub implements BasicFileAttributes {
    private PathType pathType;

    BasicFileAttributesStub(PathType pathType) {
      this.pathType = pathType;
    }

    @Override
    public boolean isDirectory() {
      return pathType.isDirectory();
    }

    @Override
    public boolean isRegularFile() {
      return pathType.isRegularFile();
    }

    @Override
    public Object fileKey() {
      return null;
    }
  }

  abstract static class DirectoryStreamStub<T> implements DirectoryStream<T> {
    List<Path> paths = new ArrayList<>();

    public DirectoryStreamStub(FileSystemProviderStub parent, String root) {
      for (String key : parent.fileContents.keySet())
        if (key.startsWith(root + "/")) paths.add(createStrictStub(PathStub.class, key));
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public Iterator<T> iterator() {
      return (Iterator<T>) paths.iterator();
    }

    @Override
    public void close() {}
  }

  abstract static class SeekableByteChannelStub implements SeekableByteChannel {
    private byte[] contents;
    private int index = 0;

    SeekableByteChannelStub(String contents) {
      this.contents = Optional.ofNullable(contents).map(String::getBytes).orElse(null);
    }

    @Override
    public long size() {
      return contents.length;
    }

    @Override
    public int read(ByteBuffer dst) {
      if (index >= contents.length) return -1;

      dst.put(contents);
      index = contents.length;
      return contents.length;
    }

    @Override
    public void close() {}
  }
}
