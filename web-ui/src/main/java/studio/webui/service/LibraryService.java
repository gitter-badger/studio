/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.service;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.reader.archive.ArchiveStoryPackReader;
import studio.core.v1.reader.binary.BinaryStoryPackReader;
import studio.core.v1.writer.binary.BinaryStoryPackWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LibraryService {

    public static final String LOCAL_LIBRARY_PROP = "studio.library";
    public static final String LOCAL_LIBRARY_PATH = "/.studio/library/";

    private final Logger LOGGER = LoggerFactory.getLogger(LibraryService.class);

    private final DatabaseMetadataService databaseMetadataService;

    public LibraryService(DatabaseMetadataService databaseMetadataService) {
        this.databaseMetadataService = databaseMetadataService;
    }

    public Optional<JsonObject> libraryInfos() {
        // Create the local library folder if needed
        File libraryFolder = new File(libraryPath());
        if (!libraryFolder.exists() || !libraryFolder.isDirectory()) {
            try {
                Files.createDirectories(Paths.get(libraryPath()));
            } catch (IOException e) {
                LOGGER.error("Failed to initialize local library", e);
                e.printStackTrace();
                return Optional.empty();
            }
        }
        return Optional.of(new JsonObject()
                .put("path", libraryPath())
        );
    }

    public JsonArray packs() {
        // Check that local library folder exists
        File libraryFolder = new File(libraryPath());
        if (!libraryFolder.exists() || !libraryFolder.isDirectory()) {
            return new JsonArray();
        } else {
            // List pack files in library folder
            try (Stream<Path> paths = Files.walk(Paths.get(libraryPath()))) {
                return new JsonArray(
                        paths
                                .filter(Files::isRegularFile)
                                .map(path -> this.readPackFile(path).map(
                                        meta -> this.getPackMetadata(meta, path.getFileName().toString())
                                ))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(Collectors.toList())
                );
            } catch (IOException e) {
                LOGGER.error("Failed to read packs from local library", e);
                throw new RuntimeException(e);
            }
        }
    }

    public Optional<File> getPackFile(String packPath) {
        // Archive format packs must first be converted to binary format
        if (packPath.endsWith(".zip")) {
            try {
                File tmp = File.createTempFile(packPath, ".pack");

                LOGGER.warn("Pack to transfer is in archive format. Converting to binary format and storing in temporary file: " + tmp.getAbsolutePath());

                LOGGER.warn("Reading archive format pack");
                ArchiveStoryPackReader packReader = new ArchiveStoryPackReader();
                FileInputStream fis = new FileInputStream(libraryPath() + packPath);
                StoryPack storyPack = packReader.read(fis);
                fis.close();

                LOGGER.warn("Writing binary format pack");
                BinaryStoryPackWriter packWriter = new BinaryStoryPackWriter();
                FileOutputStream fos = new FileOutputStream(tmp);
                packWriter.write(storyPack, fos);
                fos.close();

                return Optional.of(tmp);
            } catch (IOException e) {
                LOGGER.error("Failed to convert archive format pack to binary format");
                e.printStackTrace();
                return Optional.empty();
            }
        } else {
            return Optional.of(new File(libraryPath() + packPath));
        }
    }

    public String libraryPath() {
        // Path may be overridden by system property `studio.library`
        return System.getProperty(LOCAL_LIBRARY_PROP, System.getProperty("user.home") + LOCAL_LIBRARY_PATH);
    }

    private Optional<StoryPackMetadata> readPackFile(Path path) {
        LOGGER.debug("Reading pack file: " + path.toString());
        // Handle both binary and archive file formats
        if (path.toString().endsWith(".zip")) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                LOGGER.debug("Reading archive pack metadata.");
                ArchiveStoryPackReader packReader = new ArchiveStoryPackReader();
                return Optional.of(packReader.readMetadata(fis));
            } catch (IOException e) {
                LOGGER.error("Failed to read archive-format pack " + path.toString() + " from local library", e);
                e.printStackTrace();
                return Optional.empty();
            }
        } else if (path.toString().endsWith(".pack")) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                LOGGER.debug("Reading binary pack metadata.");
                BinaryStoryPackReader packReader = new BinaryStoryPackReader();
                Optional<StoryPackMetadata> metadata = Optional.of(packReader.readMetadata(fis));
                metadata.map(meta -> {
                    int packSectorSize = (int)Math.ceil((double)path.toFile().length() / 512d);
                    meta.setSectorSize(packSectorSize);
                    return meta;
                });
                return metadata;
            } catch (IOException e) {
                LOGGER.error("Failed to read binary-format pack " + path.toString() + " from local library", e);
                e.printStackTrace();
                return Optional.empty();
            }
        }

        // Ignore other files
        return Optional.empty();
    }

    private JsonObject getPackMetadata(StoryPackMetadata packMetadata, String path) {
        JsonObject json = new JsonObject()
                .put("uuid", packMetadata.getUuid())
                .put("version", packMetadata.getVersion())
                .put("path", path);
        Optional.ofNullable(packMetadata.getTitle()).ifPresent(title -> json.put("title", title));
        Optional.ofNullable(packMetadata.getDescription()).ifPresent(desc -> json.put("description", desc));
        Optional.ofNullable(packMetadata.getThumbnail()).ifPresent(thumb -> json.put("image", "data:image/png;base64," + Base64.getEncoder().encodeToString(thumb)));
        Optional.ofNullable(packMetadata.getSectorSize()).ifPresent(size -> json.put("sectorSize", size));
        return databaseMetadataService.getPackMetadata(packMetadata.getUuid())
                .map(metadata -> json
                        .put("title", metadata.getTitle())
                        .put("description", metadata.getDescription())
                        .put("image", metadata.getThumbnail())
                        .put("official", metadata.isOfficial())
                )
                .orElse(json);
    }

}