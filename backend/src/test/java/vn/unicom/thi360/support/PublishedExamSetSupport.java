package vn.unicom.thi360.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import vn.unicom.thi360.exam.devdata.PublishedExamSetBuilder;
import vn.unicom.thi360.exam.devdata.PublishedExamSetDefinition;
import vn.unicom.thi360.questionbank.devdata.PublishedQuestionPoolBuilder;

/**
 * Test data of the published exam set (DS). The set adds hundreds of questions and exams, which would change the question
 * source of every test that shares the static PostgreSQL container of {@code AbstractIntegrationTest}; so it lives in a
 * database of its own inside that container. A test class calls {@link #useDatabase} from its
 * {@code @DynamicPropertySource} method (Flyway then runs every migration and {@code R__seed_*} script into it) and
 * {@link #ensureBuilt} before its first case: the first call creates {@code sys.admin} and runs the two builders, once per
 * JVM; later calls return the same result.
 */
public final class PublishedExamSetSupport {
    private static final String DATABASE = "published_exam_set";
    private static Built built;
    private static final java.util.Set<ApplicationContext> restored = new java.util.HashSet<>();
    private static boolean created;

    private PublishedExamSetSupport() {
    }

    /** What the one build of this JVM cost. */
    public record Built(PublishedQuestionPoolBuilder.Report questions, PublishedExamSetBuilder.Report exams,
            long questionMillis, long examMillis) {
        public long totalMillis() {
            return questionMillis + examMillis;
        }
    }

    /** Points the application context of the calling test class at the database of the set. */
    public static synchronized void useDatabase(DynamicPropertyRegistry registry, PostgreSQLContainer<?> postgres) {
        if (!created) {
            createDatabase(postgres, DATABASE);
            created = true;
        }
        pointAt(registry, urlOf(postgres, DATABASE));
    }

    /**
     * Points the context at {@code url}. {@code AbstractIntegrationTest} registers {@code spring.datasource.url} too and,
     * as a base class, is registered after (and so wins over) the subclass; the Hikari-specific key is bound after the
     * generic one and is not touched by the base class, so it decides.
     */
    public static void pointAt(DynamicPropertyRegistry registry, String url) {
        registry.add("spring.datasource.hikari.jdbc-url", () -> url);
    }

    /** A new, empty database in the container; the caller points a context at it. */
    public static String createDatabase(PostgreSQLContainer<?> postgres, String name) {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create the database " + name, e);
        }
        return urlOf(postgres, name);
    }

    public static String urlOf(PostgreSQLContainer<?> postgres, String name) {
        return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + name);
    }

    /**
     * Creates the sample accounts of the constants file, {@code sys.admin} included, when they are missing, as
     * DevDataInitializer does outside the tests (rows only: no Keycloak in this database).
     */
    public static void ensureAccounts(JdbcTemplate jdbc, ObjectMapper mapper) {
        for (var account : PublishedExamSetDefinition.load(mapper).accounts()) {
            Integer present = jdbc.queryForObject("SELECT count(*) FROM identity.user_account "
                    + "WHERE username_normalized = ? AND deleted_at IS NULL", Integer.class, account.username());
            if (present != null && present > 0) {
                continue;
            }
            Long next = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
            String email = account.username() + "@thi360.vn";
            jdbc.update("INSERT INTO identity.user_account (id, code, username, username_normalized, full_name, email, "
                    + "email_normalized, source, status, must_change_password) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 2, false)",
                    UUID.randomUUID(), String.format(java.util.Locale.ROOT, "U%06d", next), account.username(), account.username(),
                    account.username(), email, email);
        }
    }

    /** Builds the set once per JVM and returns what it cost. */
    public static synchronized Built ensureBuilt(ApplicationContext context) {
        if (built == null) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ensureAccounts(jdbc, context.getBean(ObjectMapper.class));
            long start = System.nanoTime();
            var questions = context.getBean(PublishedQuestionPoolBuilder.class).build();
            long middle = System.nanoTime();
            var exams = context.getBean(PublishedExamSetBuilder.class).build();
            long end = System.nanoTime();
            built = new Built(questions, exams, (middle - start) / 1_000_000, (end - middle) / 1_000_000);
            restored.add(context);
        }
        if (restored.add(context)) {
            // Another application context over the same database has a storage of its own: put the objects back.
            context.getBean(PublishedQuestionPoolBuilder.class).build();
        }
        return built;
    }
}
