// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

import static com.meterware.simplestub.Stub.createStrictStub;

public abstract class InMemoryFileSystem extends FileSystem {
  private static InMemoryFileSystem instance;
  private final FileSystemProviderStub provider = createStrictStub(FileSystemProviderStub.class);

  public static InMemoryFileSystem createInstance() {
    return instance = createStrictStub(InMemoryFileSystem.class);
  }

  public void defineFile(String filePath, String contents) {
    instance.defineFileContents(filePath, contents);
  }

  public String getContents(String filePath) {
    return provider.fileContents.get(filePath);
  }

  @Nonnull
  public Path getPath(@Nonnull String first, @Nonnull String... more) {
    return PathStub.createPathStub(createPathString(first, more));
  }

  @Nonnull
  public Path getPath(@Nonnull URI uri) {
    return PathStub.createPathStub(createPathString(uri.getPath(), new String[0]));
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
    };

    boolean isDirectory() {
      return false;
    }

    boolean isRegularFile() {
      return false;
    }
  }

  abstract static class PathStub implements Path {
    private final String filePath;

    PathStub(String filePath) {
      this.filePath = filePath;
    }

    static PathStub createPathStub(String pathString) {
      return createStrictStub(PathStub.class, pathString);
    }

    @Override
    @Nonnull public FileSystem getFileSystem() {
      return instance;
    }

    @Override
    public Path getFileName() {
      return createPathStub(getLastElement());
    }

    @Nonnull
    protected String getLastElement() {
      final int beginIndex = filePath.lastIndexOf(File.separatorChar);
      return beginIndex < 0 ? filePath : filePath.substring(beginIndex + 1);
    }

    @Override
    public String toString() {
      return filePath;
    }
  }

  abstract static class FileSystemProviderStub extends FileSystemProvider {
    private final Map<String, String> fileContents = new HashMap<>();

    static String getFilePath(Path path) {
      if (!(path instanceof PathStub)) {
        throw new IllegalArgumentException(path.getClass() + " not supported");
      }

      return ((PathStub) path).filePath;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A extends BasicFileAttributes> A readAttributes(
        Path path, Class<A> type, LinkOption... options) {
      if (!type.equals(BasicFileAttributes.class)) {
        throw new IllegalArgumentException("attributes type " + type + " not supported");
      }
      return (A) createAttributes(getFilePath(path));
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
      if (!options.contains(StandardOpenOption.WRITE)) {
        return Optional.ofNullable(fileContents.get(getFilePath(path)))
              .map(s -> createStrictStub(ReadOnlyByteChannelStub.class, s))
              .orElseThrow(() -> new FileNotFoundException(path.toString()));
      } else {
        return createStrictStub(WriteableByteChannelStub.class, getFilePath(path));
      }
    }

    private BasicFileAttributes createAttributes(String filePath) {
      return createStrictStub(BasicFileAttributesStub.class, getPathType(filePath));
    }

    private PathType getPathType(String filePath) {
      for (String key : fileContents.keySet()) {
        if (key.startsWith(filePath + '/')) {
          return PathType.DIRECTORY;
        }
        if (key.equals(filePath)) {
          return PathType.FILE;
        }
      }
      return PathType.DIRECTORY;  // treat it as an empty directory
    }
  }

  abstract static class BasicFileAttributesStub implements BasicFileAttributes {
    private final PathType pathType;

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
    final List<Path> paths = new ArrayList<>();

    public DirectoryStreamStub(FileSystemProviderStub parent, String root) {
      for (String key : parent.fileContents.keySet()) {
        if (key.startsWith(root + "/")) {
          paths.add(PathStub.createPathStub(key));
        }
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public Iterator<T> iterator() {
      return (Iterator<T>) paths.iterator();
    }

    @Override
    public void close() {
    }
  }

  abstract static class ReadOnlyByteChannelStub implements SeekableByteChannel {
    private final byte[] contents;
    private int index = 0;

    ReadOnlyByteChannelStub(String contents) {
      this.contents = Optional.ofNullable(contents).map(String::getBytes).orElse(null);
    }

    @Override
    public long size() {
      return contents.length;
    }

    @Override
    public int read(ByteBuffer dst) {
      if (index >= contents.length) {
        return -1;
      }

      dst.put(contents);
      index = contents.length;
      return contents.length;
    }

    @Override
    public void close() {
    }
  }

  abstract static class WriteableByteChannelStub implements SeekableByteChannel {
    private final String filePath;
    private byte[] contents = new byte[0];

    WriteableByteChannelStub(String filePath) {
      this.filePath = filePath;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
      byte[] newContents = new byte[contents.length + src.limit()];
      System.arraycopy(contents, 0, newContents, 0, contents.length);
      System.arraycopy(src.array(), src.position(), newContents, contents.length, src.limit() - src.position());
      contents = newContents;
      src.position(src.limit());
      return contents.length;
    }

    @Override
    public void close() {
      instance.defineFileContents(filePath, new String(contents));
    }
  }
}
