package vn.unicom.thi360.questionbank.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * Puts the sample audio of the dev question data into object storage (spec "Dữ liệu audio cho
 * dev"). {@code R__seed_questionbank_dev_data.sql} can only insert the {@code question_asset}
 * rows; this runner writes the bytes under the {@code object_key} of each of those rows, taking
 * the file from {@code db/devdata/assets/<file_name>}. An object that already exists is left
 * alone, so a second run writes nothing. It touches object storage only: no Keycloak, no
 * database writes. The test profile uses an empty in-memory storage, so the runner is needed
 * there as well.
 */
@Component
@Profile({"dev", "test", "e2e"})
public class DevQuestionAssetSeeder implements ApplicationRunner {

    /** Object-key prefix of the sample rows in the repeatable seed. */
    static final String SEED_KEY_PREFIX = "questions/dev-seed/";
    static final String RESOURCE_DIRECTORY = "db/devdata/assets/";

    private static final Logger log = LoggerFactory.getLogger(DevQuestionAssetSeeder.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectStorage objectStorage;

    public DevQuestionAssetSeeder(JdbcTemplate jdbcTemplate, ObjectStorage objectStorage) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.objectStorage = Objects.requireNonNull(objectStorage);
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Returns the number of objects written; objects already in storage are skipped. */
    public int seed() {
        List<SeedAsset> assets = jdbcTemplate.query(
                "SELECT object_key, file_name, mime FROM questionbank.question_asset "
                        + "WHERE starts_with(object_key, ?) ORDER BY object_key",
                (rs, rowNum) -> new SeedAsset(rs.getString("object_key"), rs.getString("file_name"), rs.getString("mime")),
                SEED_KEY_PREFIX);
        int written = 0;
        for (SeedAsset asset : assets) {
            if (objectStorage.exists(asset.objectKey())) {
                continue;
            }
            objectStorage.put(asset.objectKey(), read(asset), asset.mime());
            written++;
        }
        log.atInfo()
                .addKeyValue("event", "dev_question_asset_seed.verified")
                .addKeyValue("totalAssets", assets.size())
                .addKeyValue("written", written)
                .log("Dev question assets verified");
        return written;
    }

    private static byte[] read(SeedAsset asset) {
        ClassPathResource resource = new ClassPathResource(RESOURCE_DIRECTORY + asset.fileName());
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Sample asset file is missing: " + resource.getPath(), e);
        }
    }

    private record SeedAsset(String objectKey, String fileName, String mime) {
    }
}
