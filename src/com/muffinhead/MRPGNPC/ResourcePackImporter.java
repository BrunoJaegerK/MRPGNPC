package com.muffinhead.MRPGNPC;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Imports Bedrock resource-pack assets used by decorative NPC models.
 * Extraction is bounded and rejects paths that escape the selected pack folder.
 */
public final class ResourcePackImporter {
    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 10000;

    private ResourcePackImporter() {
    }

    public static final class ImportResult {
        public final int files;
        public final int models;
        public final boolean hasSounds;
        public final Path packFolder;
        public final Path exportedPack;

        private ImportResult(int files, int models, boolean hasSounds, Path packFolder, Path exportedPack) {
            this.files = files;
            this.models = models;
            this.hasSounds = hasSounds;
            this.packFolder = packFolder;
            this.exportedPack = exportedPack;
        }
    }

    public static ImportResult importPack(Path archive, Path pluginFolder, String packId) throws IOException {
        if (packId == null || !packId.matches("[A-Za-z0-9_-]{1,48}")) {
            throw new IOException("Invalid pack id. Use letters, numbers, _ or - (max 48).");
        }
        if (!Files.isRegularFile(archive)) {
            throw new IOException("Import ZIP not found: " + archive);
        }

        Path packsRoot = pluginFolder.resolve("ResourcePacks").toAbsolutePath().normalize();
        Path target = packsRoot.resolve(packId).normalize();
        if (!target.startsWith(packsRoot) || Files.exists(target)) {
            throw new IOException("Pack already exists or destination is invalid: " + packId);
        }
        Files.createDirectories(packsRoot);
        Path temp = Files.createTempDirectory(packsRoot, "." + packId + "-import-");
        int fileCount = 0;
        long totalBytes = 0L;
        try {
            try (InputStream fileInput = new BufferedInputStream(Files.newInputStream(archive));
                 ZipInputStream zip = new ZipInputStream(fileInput, StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++fileCount > MAX_ENTRIES) {
                        throw new IOException("ZIP contains too many entries (limit " + MAX_ENTRIES + ").");
                    }
                    String name = entry.getName().replace('\\', '/');
                    if (name.isEmpty() || name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
                        throw new IOException("Unsafe ZIP entry: " + name);
                    }
                    Path output = temp.resolve(name).normalize();
                    if (!output.startsWith(temp)) {
                        throw new IOException("ZIP entry escapes destination: " + name);
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(output);
                        zip.closeEntry();
                        continue;
                    }
                    if (!isAllowedAsset(name)) {
                        zip.closeEntry();
                        continue;
                    }
                    Files.createDirectories(output.getParent());
                    long entryBytes = 0L;
                    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(output))) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            entryBytes += read;
                            totalBytes += read;
                            if (entryBytes > MAX_FILE_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                                throw new IOException("ZIP exceeds the configured extraction size limits.");
                            }
                            out.write(buffer, 0, read);
                        }
                    }
                    zip.closeEntry();
                }
            }

            ensureManifest(temp, packId);
            int models = generateDisplaySkins(temp, pluginFolder.resolve("Skins"), packId);
            Path exportRoot = pluginFolder.resolve("exports");
            Files.createDirectories(exportRoot);
            Path exported = exportRoot.resolve(packId + ".mcpack");
            writeZip(temp, exported);
            if (Files.exists(target)) {
                throw new IOException("Pack destination appeared during import: " + target);
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            boolean sounds = Files.exists(target.resolve("sounds/sound_definitions.json"));
            return new ImportResult(fileCount, models, sounds, target, exported);
        } catch (IOException | RuntimeException exception) {
            deleteTree(temp);
            throw exception;
        }
    }

    private static boolean isAllowedAsset(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".jar") || lower.endsWith(".class") || lower.endsWith(".exe")
                || lower.endsWith(".dll") || lower.endsWith(".bat") || lower.endsWith(".sh")) {
            return false;
        }
        return lower.endsWith(".json") || lower.endsWith(".png") || lower.endsWith(".tga")
                || lower.endsWith(".ogg") || lower.endsWith(".wav") || lower.endsWith(".mp3")
                || lower.endsWith(".m4a") || lower.endsWith(".material") || lower.endsWith(".lang")
                || lower.endsWith(".txt") || lower.endsWith(".fsh") || lower.endsWith(".vsh")
                || lower.endsWith(".bin") || lower.endsWith(".atlas");
    }

    private static void ensureManifest(Path root, String packId) throws IOException {
        Path manifest = root.resolve("manifest.json");
        if (Files.isRegularFile(manifest)) return;
        String headerId = UUID.randomUUID().toString();
        String moduleId = UUID.randomUUID().toString();
        String json = "{\n"
                + "  \"format_version\": 2,\n"
                + "  \"header\": {\n"
                + "    \"name\": \"MRPGNPC " + packId + "\",\n"
                + "    \"description\": \"Imported Bedrock model assets for MRPGNPC\",\n"
                + "    \"uuid\": \"" + headerId + "\",\n"
                + "    \"version\": [1, 0, 0],\n"
                + "    \"min_engine_version\": [1, 16, 0]\n"
                + "  },\n"
                + "  \"modules\": [{\n"
                + "    \"type\": \"resources\",\n"
                + "    \"uuid\": \"" + moduleId + "\",\n"
                + "    \"version\": [1, 0, 0]\n"
                + "  }]\n"
                + "}\n";
        Files.write(manifest, json.getBytes(StandardCharsets.UTF_8));
    }

    private static int generateDisplaySkins(Path packRoot, Path skinsRoot, String packId) throws IOException {
        Path entityRoot = packRoot.resolve("entity");
        if (!Files.isDirectory(entityRoot)) return 0;
        Files.createDirectories(skinsRoot);
        int generated = 0;
        try (Stream<Path> entities = Files.walk(entityRoot)) {
            for (Path entityFile : (Iterable<Path>) entities.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".entity.json"))::iterator) {
                String entityJson = Files.readString(entityFile, StandardCharsets.UTF_8);
                String identifier = firstGroup(entityJson, "\\\"identifier\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
                String geometryId = firstGroup(entityJson,
                        "\\\"geometry\\\"\\s*:\\s*\\{[^}]*?\\\"[^\\\"]+\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
                String texture = firstGroup(entityJson,
                        "\\\"textures\\\"\\s*:\\s*\\{[^}]*?\\\"[^\\\"]+\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
                if (identifier == null || geometryId == null || texture == null) continue;

                Path geometryFile = findFileContaining(packRoot.resolve("models"), ".geo.json", geometryId);
                Path textureFile = resolveTexture(packRoot, texture);
                if (geometryFile == null || textureFile == null) continue;

                String entityName = entityFile.getFileName().toString().replaceFirst("\\.entity\\.json$", "");
                String safeEntityName = entityName.replaceAll("[^A-Za-z0-9_-]", "_");
                Path skinFolder = skinsRoot.resolve(packId + "_" + safeEntityName).normalize();
                if (!skinFolder.startsWith(skinsRoot.normalize())) continue;
                Files.createDirectories(skinFolder);
                Files.copy(geometryFile, skinFolder.resolve("geometry.json"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(textureFile, skinFolder.resolve("skin.png"), StandardCopyOption.REPLACE_EXISTING);

                Path animationFile = findNamedAsset(packRoot.resolve("animations"), entityName, ".animation.json");
                if (animationFile != null) {
                    Files.copy(animationFile, skinFolder.resolve("animations.json"), StandardCopyOption.REPLACE_EXISTING);
                }
                Path controllerFile = findNamedAsset(packRoot.resolve("animation_controllers"),
                        entityName, ".animation_controllers.json");
                if (controllerFile != null) {
                    Files.copy(controllerFile, skinFolder.resolve("animation_controllers.json"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                String metadata = "Pack: \"" + packId + "\"\n"
                        + "EntityIdentifier: \"" + identifier + "\"\n"
                        + "GeometryIdentifier: \"" + geometryId + "\"\n"
                        + "Texture: \"" + texture + "\"\n"
                        + "EntityDefinition: \"" + packRoot.relativize(entityFile).toString().replace('\\\\', '/') + "\"\n";
                Files.writeString(skinFolder.resolve("model.yml"), metadata, StandardCharsets.UTF_8);
                generated++;
            }
        }
        return generated;
    }

    private static String firstGroup(String source, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Path findFileContaining(Path root, String suffix, String value) throws IOException {
        if (!Files.isDirectory(root)) return null;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))::iterator) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (text.contains("\"" + value + "\"")) return file;
            }
        }
        return null;
    }

    private static Path resolveTexture(Path root, String texture) {
        String clean = texture.replace('\\', '/');
        if (clean.startsWith("/")) clean = clean.substring(1);
        Path candidate = root.resolve(clean + (clean.toLowerCase(java.util.Locale.ROOT).endsWith(".png") ? "" : ".png"))
                .normalize();
        return candidate.startsWith(root.normalize()) && Files.isRegularFile(candidate) ? candidate : null;
    }

    private static Path findNamedAsset(Path root, String entityName, String suffix) throws IOException {
        if (!Files.isDirectory(root)) return null;
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(entityName + suffix))
                    .findFirst().orElse(null);
        }
    }

    private static void writeZip(Path root, Path output) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(output)));
             Stream<Path> paths = Files.walk(root)) {
            for (Path file : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                String name = root.relativize(file).toString().replace('\\\\', '/');
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
        }
    }
}
