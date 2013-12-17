/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package interactivespaces.resource.repository.internal;

import interactivespaces.InteractiveSpacesException;
import interactivespaces.configuration.Configuration;
import interactivespaces.resource.NamedVersionedResource;
import interactivespaces.resource.NamedVersionedResourceCollection;
import interactivespaces.resource.Version;
import interactivespaces.resource.repository.ResourceRepositoryStorageManager;
import interactivespaces.system.InteractiveSpacesEnvironment;
import interactivespaces.util.io.FileSupport;
import interactivespaces.util.io.FileSupportImpl;

import com.google.common.collect.Maps;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A {@link ResourceRepositoryStorageManager} which stores resources in the file
 * system.
 *
 * <p>
 * This storage manager assumes that staged activities are stored as ZIP files.
 *
 * @author Keith M. Hughes
 */
public class FileSystemResourceRepositoryStorageManager implements ResourceRepositoryStorageManager {

  /**
   * Configuration property for the location of the activities repository.
   *
   * <p>
   * TODO(keith): Eventually deprecate these and have only 1 set of parameters
   * but will require changing all masters.
   */
  public static final String CONFIGURATION_REPOSITORY_ACTIVITY_LOCATION =
      "interactivespaces.repository.activities.location";

  /**
   * Where files will be staged during installation by the manager.
   *
   * <p>
   * TODO(keith): Eventually deprecate these and have only 1 set of parameters
   * but will require changing all masters. This one will just be
   * interactivespaces.repository.staging.location.
   */
  public static final String CONFIGURATION_REPOSITORY_STAGING_LOCATION =
      "interactivespaces.repository.activities.staging.location";

  /**
   * Default value for the location of the activities repository.
   *
   * <p>
   * TODO(keith): Eventually deprecate these and have only 1 set of parameters
   * but will require changing all masters.
   */
  public static final String DEFAULT_REPOSITORY_ACTIVITY_LOCATION = "repository/interactivespaces/activities";

  /**
   * Configuration property for the location of the resource bundles repository.
   *
   * <p>
   * TODO(keith): Eventually deprecate these and have only 1 set of parameters
   * but will require changing all masters.
   */
  public static final String CONFIGURATION_REPOSITORY_BUNDLE_RESOURCE_LOCATION =
      "interactivespaces.repository.resources.bundles.location";

  /**
   * Default value for the location of the data repository.
   */
  public static final String DEFAULT_REPOSITORY_DATA_LOCATION = "repository/interactivespaces/data";

  /**
   * Default value for the location of the resources repository.
   */
  public static final String DEFAULT_REPOSITORY_BUNDLE_RESOURCE_LOCATION =
      "repository/interactivespaces/resources/bundles";

  /**
   * Extension placed on activity archives for transmission.
   */
  public static final String RESOURCE_ARCHIVE_EXTENSION = "zip";

  /**
   * The filename prefix for a staged resource.
   */
  private static final String STAGED_RESOURCE_FILENAME_PREFIX = "resource-";

  /**
   * The filename suffix for a staged resource.
   */
  private static final String STAGED_RESOURCE_FILENAME_SUFFIX = ".zip";

  /**
   * Directory where files are staged.
   */
  private File stagingDirectory;

  /**
   * Base location of the activity repository.
   */
  private File activityRepositoryBaseLocation;

  /**
   * Path to the repository in the file system.
   */
  private String activityRepositoryPath;

  /**
   * Base location of the data repository.
   */
  private File dataRepositoryBaseLocation;

  /**
   * Base location of the generic resource repository.
   */
  private File resourceRepositoryBaseLocation;

  /**
   * Path to the generic resource repository in the file system.
   */
  private String bundleResourceRepositoryPath;

  /**
   * Map of staging handles to staging files.
   */
  private final Map<String, File> stagingFiles = Maps.newConcurrentMap();

  /**
   * The Interactive Spaces environment.
   */
  private InteractiveSpacesEnvironment spaceEnvironment;

  /**
   * The file support to use.
   */
  private final FileSupport fileSupport = FileSupportImpl.INSTANCE;

  @Override
  public void startup() {
    Configuration systemConfiguration = spaceEnvironment.getSystemConfiguration();

    File baseInstallDir = spaceEnvironment.getFilesystem().getInstallDirectory();

    stagingDirectory =
        new File(baseInstallDir,
            systemConfiguration.getRequiredPropertyString(CONFIGURATION_REPOSITORY_STAGING_LOCATION));
    ensureWriteableDirectory("staging", stagingDirectory);

    // TODO(keith): Check repository base same way as above.
    activityRepositoryPath =
        systemConfiguration.getPropertyString(CONFIGURATION_REPOSITORY_ACTIVITY_LOCATION,
            DEFAULT_REPOSITORY_ACTIVITY_LOCATION);
    activityRepositoryBaseLocation = new File(baseInstallDir, activityRepositoryPath);
    ensureWriteableDirectory("activity", activityRepositoryBaseLocation);

    // TODO(peringknife): Check repository base same way as above.
    String dataRepositoryPath = DEFAULT_REPOSITORY_DATA_LOCATION;
    dataRepositoryBaseLocation = new File(baseInstallDir, dataRepositoryPath);
    ensureWriteableDirectory("data", dataRepositoryBaseLocation);

    bundleResourceRepositoryPath =
        systemConfiguration.getPropertyString(CONFIGURATION_REPOSITORY_BUNDLE_RESOURCE_LOCATION,
            DEFAULT_REPOSITORY_BUNDLE_RESOURCE_LOCATION);
    resourceRepositoryBaseLocation = new File(baseInstallDir, bundleResourceRepositoryPath);
    ensureWriteableDirectory("bundle resource", resourceRepositoryBaseLocation);
  }

  /**
   * Ensure that the directory exists and is writeable, or create it.
   *
   * @param type
   *          the type of the directory
   * @param directory
   *          the directory
   */
  private void ensureWriteableDirectory(String type, File directory) {
    if (directory.exists()) {
      if (!directory.isDirectory()) {
        throw new InteractiveSpacesException(String.format("Resource repository %s directory %s is not a directory",
            type, directory.getAbsolutePath()));
      } else if (!directory.canWrite()) {
        throw new InteractiveSpacesException(String.format("Resource repository %s directory %s is not writable", type,
            directory.getAbsolutePath()));
      }
    } else {
      if (!directory.mkdirs()) {
        throw new InteractiveSpacesException(String.format("Could not create activity repository %s directory %s",
            type, directory.getAbsolutePath()));
      }
    }
  }

  @Override
  public void shutdown() {
    // Nothing to do.
  }

  @Override
  public boolean containsResource(String category, String name, Version version) {
    return getRepositoryFile(category, name, version).exists();
  }

  @Override
  public String getRepositoryResourceName(String category, String name, Version version) {
    // TODO(keith): Fix, cheesy
    String suffix = RESOURCE_ARCHIVE_EXTENSION;
    if (category.equals(RESOURCE_CATEGORY_CONTAINER_BUNDLE)) {
      suffix = "jar";
    }

    return name + "-" + version.toString() + "." + suffix;
  }

  @Override
  public String stageResource(InputStream resourceStream) {
    try {
      File stagedFile =
          File.createTempFile(STAGED_RESOURCE_FILENAME_PREFIX, STAGED_RESOURCE_FILENAME_SUFFIX, stagingDirectory);
      fileSupport.copyInputStream(resourceStream, stagedFile);

      // +2 to get beyond path separator.
      String handle = stagedFile.getAbsolutePath().substring(stagingDirectory.getAbsolutePath().length() + 1);
      stagingFiles.put(handle, stagedFile);

      return handle;
    } catch (IOException e) {
      throw new InteractiveSpacesException("Could not stage resource file", e);
    }
  }

  @Override
  public void removeStagedReource(String stageHandle) {
    File stageFile = stagingFiles.remove(stageHandle);
    if (stageFile != null) {
      stageFile.delete();
    }
  }

  @Override
  public InputStream getStagedResourceDescription(String descriptorFileName, String stageHandle) {
    File stageFile = stagingFiles.get(stageHandle);
    if (stageFile != null) {
      try {
        ZipFile zip = new ZipFile(stageFile);
        ZipEntry entry = zip.getEntry(descriptorFileName);

        return new MyZipInputStream(zip, zip.getInputStream(entry));
      } catch (Exception e) {
        throw new InteractiveSpacesException(String.format("Cannot get resource description from staged resource %s",
            stageHandle), e);
      }
    } else {
      throw new InteractiveSpacesException(String.format("%s is not a valid staging handle", stageHandle));
    }
  }

  @Override
  public void commitResource(String category, String name, Version version, String stageHandle) {
    File stagingFile = stagingFiles.get(stageHandle);
    try {
      if (stagingFile != null) {
        fileSupport.copyFile(stagingFile, getRepositoryFile(category, name, version));
      } else {
        throw new InteractiveSpacesException("Unknown staging handle " + stageHandle);
      }
    } catch (Exception e) {
      throw new InteractiveSpacesException(String.format("Could not copy staging handle %s to %s", stageHandle,
          stagingFile.getAbsolutePath()), e);
    }
  }

  @Override
  public InputStream getResourceStream(String category, String name, Version version) {
    File resourceFile = getRepositoryFile(category, name, version);
    try {
      return new FileInputStream(resourceFile);
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  @Override
  public NamedVersionedResourceCollection<NamedVersionedResource> getAllResources(String category) {
    NamedVersionedResourceCollection<NamedVersionedResource> resources =
        NamedVersionedResourceCollection.newNamedVersionedResourceCollection();

    File[] files = getBaseLocation(category).listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return pathname.getName().endsWith(".jar");
      }
    });

    if (files != null) {
      for (File file : files) {
        JarFile jarFile = null;
        try {
          jarFile = new JarFile(file);
          Manifest manifest = jarFile.getManifest();
          Attributes attributes = manifest.getMainAttributes();
          String name = attributes.getValue("Bundle-SymbolicName");
          String version = attributes.getValue("Bundle-Version");
          NamedVersionedResource resource = new NamedVersionedResource(name, Version.parseVersion(version));

          resources.addResource(resource.getName(), resource.getVersion(), resource);
        } catch (IOException e) {
          spaceEnvironment.getLog().error(
              String.format("Could not open resource file jar manifest for %s", file.getAbsolutePath()), e);
        } finally {
          // For some reason Closeables does not work with JarFile despite it
          // claiming it is Closeable in the Javadoc.
          if (jarFile != null) {
            try {
              jarFile.close();
            } catch (IOException e) {
              // Don't care.
            }
          }
        }
      }
    }
    return resources;
  }

  @Override
  public OutputStream newResourceOutputStream(String category, String name, Version version) {
    File resourceFile = getRepositoryFile(category, name, version);
    try {
      return new FileOutputStream(resourceFile);
    } catch (FileNotFoundException e) {
      throw new InteractiveSpacesException("Could not create resource output stream " + resourceFile.getPath(), e);
    }
  }

  /**
   * Get the repository filename used for a given activity.
   *
   * @param category
   *          category of the resource
   * @param name
   *          name of the resource
   * @param version
   *          version of the resource
   *
   * @return the file which contains the resource
   */
  private File getRepositoryFile(String category, String name, Version version) {
    File baseLocation = getBaseLocation(category);

    return new File(baseLocation, getRepositoryResourceName(category, name, version));
  }

  /**
   * Get the base location for files from a give category.
   *
   * @param category
   *          the category
   *
   * @return the base location
   */
  private File getBaseLocation(String category) {
    // TODO(keith): Fix, cheesy
    File baseLocation = resourceRepositoryBaseLocation;
    if (RESOURCE_CATEGORY_ACTIVITY.equals(category)) {
      baseLocation = activityRepositoryBaseLocation;
    } else if (RESOURCE_CATEGORY_DATA.equals(category)) {
      baseLocation = dataRepositoryBaseLocation;
    } else if (RESOURCE_CATEGORY_CONTAINER_BUNDLE.equals(category)) {
      baseLocation = resourceRepositoryBaseLocation;
    }

    return baseLocation;
  }

  /**
   * Set the space environment to use.
   *
   * @param spaceEnvironment
   *          the spaceEnvironment to set
   */
  public void setSpaceEnvironment(InteractiveSpacesEnvironment spaceEnvironment) {
    this.spaceEnvironment = spaceEnvironment;
  }

  /**
   * An {@link InputStream} based on a zip entry's input stream which will close
   * the underlying zip file when the stream is closed.
   *
   * @author Keith M. Hughes
   */
  public static class MyZipInputStream extends InputStream {

    /**
     * The zip file which gave the entry.
     */
    private final ZipFile zip;

    /**
     * Input stream from the zip entry being read.
     */
    private final InputStream inputStream;

    /**
     * Construct a zip file input stream.
     *
     * @param zip
     *          the zip file the stream is for
     * @param inputStream
     *          the input stream from the zip
     */
    public MyZipInputStream(ZipFile zip, InputStream inputStream) {
      this.zip = zip;
      this.inputStream = inputStream;
    }

    @Override
    public int available() throws IOException {
      return inputStream.available();
    }

    @Override
    public void close() throws IOException {
      // Closing the zip closes all input streams created.
      zip.close();
    }

    @Override
    public boolean equals(Object obj) {
      return inputStream.equals(obj);
    }

    @Override
    public int hashCode() {
      return inputStream.hashCode();
    }

    @Override
    public void mark(int readlimit) {
      inputStream.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
      return inputStream.markSupported();
    }

    @Override
    public int read() throws IOException {
      return inputStream.read();
    }

    @Override
    public int read(byte[] arg0, int arg1, int arg2) throws IOException {
      return inputStream.read(arg0, arg1, arg2);
    }

    @Override
    public int read(byte[] b) throws IOException {
      return inputStream.read(b);
    }

    @Override
    public void reset() throws IOException {
      inputStream.reset();
    }

    @Override
    public long skip(long arg0) throws IOException {
      return inputStream.skip(arg0);
    }

    @Override
    public String toString() {
      return inputStream.toString();
    }
  }
}
