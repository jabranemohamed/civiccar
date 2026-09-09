package tn.civiccare.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Stockage local persistant (volume Docker). Les clés sont générées par l'application
 * (UUID + suffixe) et validées : aucune traversée de chemin possible.
 */
@Component
public class LocalMediaStorage implements MediaStorage {

    private static final Pattern SAFE_KEY = Pattern.compile("^[a-f0-9-]{36}(_thumb)?\\.jpg$");

    private final Path root;

    public LocalMediaStorage(@Value("${civiccare-media.storage-dir:./data/media}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    private Path resolve(String key) {
        if (!SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Clé média invalide");
        }
        return root.resolve(key);
    }

    @Override
    public void put(String key, byte[] content) throws IOException {
        Files.write(resolve(key), content);
    }

    @Override
    public InputStream get(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }
}
