package tn.civiccare.media;

import java.io.IOException;
import java.io.InputStream;

/** Port de stockage des médias. Implémentation locale fournie ; adaptateur S3 possible en production. */
public interface MediaStorage {

    void put(String key, byte[] content) throws IOException;

    InputStream get(String key) throws IOException;

    void delete(String key) throws IOException;

    boolean exists(String key);
}
