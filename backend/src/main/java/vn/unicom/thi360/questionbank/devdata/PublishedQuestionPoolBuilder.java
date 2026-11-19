package vn.unicom.thi360.questionbank.devdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.QuestionAssetUploadResponse;
import vn.unicom.thi360.questionbank.api.QuestionDetail;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.application.DevQuestionAssetSeeder;
import vn.unicom.thi360.questionbank.application.QuestionAssetService;
import vn.unicom.thi360.questionbank.application.QuestionService;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.domain.QuestionBody.ChoiceOption;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserAuthenticationToken;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * Question half of the published exam set (DS). It runs as {@code sys.admin} through the real
 * {@link QuestionService} and {@link QuestionAssetService}: sample questions that the 2B sample bank lacks for three
 * disjoint attempts of every published exam ({@link PublishedQuestionPool}), the sample audio used by the new listening
 * questions, and the question with images of the constants file. Every question has a stable source code
 * {@code DS-Q-*}; a rerun creates only the ones that are missing. It never calls the exam half. Single builder: one JVM
 * builds a database — two dev/e2e instances against the same half-seeded database would race on the missing codes, and
 * the loser stops its startup (R1-9).
 */
@Component
@Profile({"dev", "e2e", "test"})
public class PublishedQuestionPoolBuilder {
    private static final Logger log = LoggerFactory.getLogger(PublishedQuestionPoolBuilder.class);
    static final String ACTOR = "sys.admin";
    static final String CODE_PREFIX = "DS-Q-";
    static final String RESOURCE = "devdata/published-exam-set.json";
    static final String AUDIO_FILE = "hsca-en-listening-single-01.mp3";
    static final String AUDIO_NAME = "ds-listening.mp3";
    private static final int CHUNK = 20;
    private static final int THREADS = 6;

    private static final String[] GRADES = {"G10", "G11", "G12"};
    private static final String[] LEVELS = {"NB", "TH", "VD", "VDC"};
    private static final UUID[] GRADE_IDS = {id("301"), id("302"), id("303")};
    private static final UUID[] LEVEL_IDS = {id("401"), id("402"), id("403"), id("404")};

    private final QuestionService questions;
    private final QuestionAssetService assets;
    private final DevQuestionAssetSeeder audioSeeder;
    private final ObjectStorage storage;
    private final UserDirectory users;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public PublishedQuestionPoolBuilder(QuestionService questions, QuestionAssetService assets,
            DevQuestionAssetSeeder audioSeeder, ObjectStorage storage, UserDirectory users, JdbcTemplate jdbc,
            ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.questions = questions;
        this.assets = assets;
        this.audioSeeder = audioSeeder;
        this.storage = storage;
        this.users = users;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** What one build did: the questions it created and the ones that were already there. */
    public record Report(int created, int existing) { }

    /**
     * Builds what is missing. Fails before any write when {@code sys.admin} is missing. Each chunk of questions is one
     * transaction, so a run that stops leaves only complete questions and the next run continues.
     */
    public Report build() {
        UserDirectory.UserSummary actor = users.findActiveByUsername(ACTOR).orElseThrow(() -> new IllegalStateException(
                "Cannot build the published exam set: the account '" + ACTOR + "' does not exist or is not active. "
                        + "Create it first (DevDataInitializer) or set thi360.devdata.published-exams.enabled=false."));
        // The 2B sample audio is what several published exams check for (ATTACHMENT_MISSING); its seeder is a runner
        // that boots after this one, so it is asked now (it only writes the missing objects to storage).
        audioSeeder.seed();
        return runAs(actor, this::buildAs);
    }

    private Report buildAs() {
        Set<String> existing = new HashSet<>(jdbc.queryForList(
                "SELECT source_code FROM questionbank.question WHERE parent_id IS NULL AND source_code LIKE 'DS-Q-%'",
                String.class));
        List<PoolSpec> specs = specs();
        int present = 0;
        UUID audio = specs.stream().anyMatch(spec -> spec.audio) ? ensureAudio() : null;
        var chunksBySubject = new java.util.LinkedHashMap<String, List<Runnable>>();
        int created = 0;
        for (PoolSpec spec : specs) {
            List<Integer> missing = new ArrayList<>();
            for (int n = 1; n <= spec.count; n++) {
                if (existing.contains(code(spec, n))) {
                    present++;
                } else {
                    missing.add(n);
                }
            }
            for (int from = 0; from < missing.size(); from += CHUNK) {
                List<Integer> chunk = missing.subList(from, Math.min(missing.size(), from + CHUNK));
                chunksBySubject.computeIfAbsent(spec.subject, ignored -> new ArrayList<>())
                        .add(() -> transaction.executeWithoutResult(status -> createChunk(spec, chunk, audio)));
                created += chunk.size();
            }
        }
        List<Runnable> subjects = new ArrayList<>();
        for (List<Runnable> chunks : chunksBySubject.values()) {
            subjects.add(() -> chunks.forEach(Runnable::run));
        }
        runChunks(subjects);
        created += ensureImageQuestion(existing) ? 1 : 0;
        log.atInfo().addKeyValue("event", "published_question_pool.built").addKeyValue("created", created)
                .addKeyValue("existing", present).log("Published exam set questions verified");
        return new Report(created, present);
    }

    /**
     * Each subject is processed sequentially because its source revision row serializes writes; different subjects
     * run concurrently. Every chunk remains a separate transaction and each worker receives the actor context.
     */
    private void runChunks(List<Runnable> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        SecurityContext context = SecurityContextHolder.getContext();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(THREADS, task -> {
            Thread thread = new Thread(task, "ds-questions");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (Runnable chunk : chunks) {
                futures.add(pool.submit(() -> {
                    SecurityContextHolder.setContext(context);
                    try {
                        chunk.run();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            for (var future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while building the sample questions", e);
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new IllegalStateException("Cannot build the sample questions: " + e.getCause(), e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * The fixed families plus the H-SCA chemistry short answers: their answers are ions and symbol strings and are listed
     * in the constants file, so a test can type them.
     */
    private List<PoolSpec> specs() {
        JsonNode chemistry = constants().path("hscaChemistry");
        List<String> answers = new ArrayList<>();
        chemistry.path("answers").forEach(answer -> answers.add(answer.asText()));
        List<PoolSpec> specs = new ArrayList<>(PublishedQuestionPool.SPECS);
        if (!answers.isEmpty()) {
            specs.add(PoolSpec.of(chemistry.path("familyCode").asText(), "HOA", QuestionType.SHORT_ANSWER, answers.size(),
                    "0.25").formats(vn.unicom.thi360.questionbank.spi.AnswerFormat.valueOf(
                            chemistry.path("answerFormat").asText())).chemAnswers(answers));
        }
        return specs;
    }

    private void createChunk(PoolSpec spec, List<Integer> chunk, UUID audioId) {
        List<UUID> ids = new ArrayList<>();
        for (int n : chunk) {
            QuestionDetail detail = questions.create(UUID.randomUUID().toString(), request(spec, n, audioId));
            ids.add(detail.summary().id());
        }
        var result = questions.bulkReady(UUID.randomUUID().toString(), new BulkQuestionStatusRequest(ids));
        if (!result.skipped().isEmpty()) {
            throw new IllegalStateException("Sample questions of " + spec.family + " are not ready: " + result.skipped());
        }
    }

    // ------------------------------------------------------------------------------------------ requests

    static String code(PoolSpec spec, int n) {
        // ROOT: the source codes are keys, so the default locale must never change the digits (R1-5).
        return CODE_PREFIX + spec.family + "-" + String.format(java.util.Locale.ROOT, "%03d", n);
    }

    private SaveQuestionRequest request(PoolSpec spec, int n, UUID audioId) {
        String code = code(spec, n);
        int levels = spec.veryHard ? 4 : 3;
        int cell = n - 1;
        String grade = GRADES[cell % 3];
        String level = LEVELS[(cell / 3) % levels];
        QuestionBody body = switch (spec.type) {
            case QUESTION_SET -> setBody(spec, code, cell, levels);
            case CLOZE -> cloze(code, spec.blanks, spec.points);
            default -> body(spec.type, spec, spec.points, cell);
        };
        List<UUID> attachments = spec.audio ? List.of(audioId) : List.of();
        return new SaveQuestionRequest(spec.type, spec.subject, grade, level, null, spec.competency,
                spec.type == QuestionType.QUESTION_SET || spec.type == QuestionType.CLOZE ? null : spec.points, List.of(),
                spec.tags, "<p>" + code + " question</p>", "<p>" + code + " solution</p>", List.of(), attachments,
                body, code);
    }

    private QuestionBody setBody(PoolSpec spec, String code, int cell, int levels) {
        List<QuestionType> types = spec.mixedChildren.isEmpty()
                ? java.util.Collections.nCopies(spec.setSize, spec.childType) : spec.mixedChildren;
        List<QuestionBody.ChildQuestion> children = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            int childCell = cell * types.size() + i;
            QuestionType type = types.get(i);
            QuestionBody childBody = body(type, spec, spec.points, childCell);
            children.add(new QuestionBody.ChildQuestion(type, "<p>" + code + " child " + (i + 1) + "</p>", childBody,
                    null, spec.points, GRADE_IDS[childCell % 3], LEVEL_IDS[(childCell / 3) % levels], null, null));
        }
        return new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET, "<p>" + code + " passage</p>", null, children);
    }

    private static QuestionBody body(QuestionType type, PoolSpec spec, BigDecimal points, int cell) {
        return switch (type) {
            case SINGLE_CHOICE -> new QuestionBody.SingleChoiceBody(type, options(spec.options, 1), true);
            case MULTIPLE_CHOICE -> new QuestionBody.MultipleChoiceBody(type, options(spec.options, 2),
                    spec.partialMultiple ? QuestionBody.MultipleChoiceRule.PARTIAL
                            : QuestionBody.MultipleChoiceRule.ALL_OR_NOTHING, true);
            case SHORT_ANSWER -> shortAnswer(spec, cell);
            case TRUE_FALSE -> trueFalse(spec.statements, points);
            case MATCHING -> matching(spec.matchingSide, points);
            case CLOZE -> cloze("DS", spec.blanks, points);
            default -> throw new IllegalArgumentException("Unsupported sample type " + type);
        };
    }

    private static QuestionBody shortAnswer(PoolSpec spec, int cell) {
        if (spec.formats.isEmpty()) {
            return new QuestionBody.ShortAnswerBody(QuestionType.SHORT_ANSWER, List.of("1"), "", null);
        }
        var format = spec.formats.get(cell % spec.formats.size());
        String answer = switch (format) {
            case INTEGER -> "1";
            case DECIMAL -> "1.5";
            case FRACTION -> "1/2";
            default -> spec.chemAnswers.get(cell % spec.chemAnswers.size());
        };
        return new QuestionBody.ShortAnswerBody(QuestionType.SHORT_ANSWER, List.of(answer), "", format);
    }

    private static List<ChoiceOption> options(int count, int correct) {
        List<ChoiceOption> options = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = String.valueOf((char) ('a' + i));
            options.add(new ChoiceOption(id, "<p>Option " + id + "</p>", i < correct));
        }
        return options;
    }

    private static QuestionBody trueFalse(int statements, BigDecimal points) {
        List<QuestionBody.TrueFalseStatement> list = new ArrayList<>();
        for (int i = 0; i < statements; i++) {
            String id = String.valueOf((char) ('a' + i));
            list.add(new QuestionBody.TrueFalseStatement(id, "<p>Statement " + id + "</p>", i % 2 == 0, null));
        }
        // Count table: 10%, 25%, 50% and the whole of the points, as the sample bank of 2B does.
        List<BigDecimal> table = new ArrayList<>();
        BigDecimal[] shares = {new BigDecimal("0.1"), new BigDecimal("0.25"), new BigDecimal("0.5"), BigDecimal.ONE};
        if (points.compareTo(new BigDecimal("6")) == 0) {
            table = List.of(BigDecimal.ONE, new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("6"));
        } else {
            for (int i = 0; i < statements; i++) {
                table.add(i == statements - 1 ? points : points.multiply(shares[Math.min(i, 2)]));
            }
        }
        return new QuestionBody.TrueFalseBody(QuestionType.TRUE_FALSE, list,
                QuestionBody.TrueFalseScoringMethod.COUNT_TABLE, table);
    }

    private static QuestionBody matching(int side, BigDecimal points) {
        List<QuestionBody.MatchingSide> left = new ArrayList<>();
        List<QuestionBody.MatchingSide> right = new ArrayList<>();
        List<QuestionBody.MatchingPair> pairs = new ArrayList<>();
        BigDecimal each = points.divide(BigDecimal.valueOf(side), 6, RoundingMode.HALF_UP).stripTrailingZeros();
        for (int i = 1; i <= side; i++) {
            left.add(new QuestionBody.MatchingSide("l" + i, "<p>Left " + i + "</p>"));
            right.add(new QuestionBody.MatchingSide("r" + i, "<p>Right " + i + "</p>"));
            pairs.add(new QuestionBody.MatchingPair("l" + i, "r" + i, each));
        }
        return new QuestionBody.MatchingBody(QuestionType.MATCHING, left, right, pairs, false);
    }

    private static QuestionBody cloze(String code, int blanks, BigDecimal blankPoints) {
        List<QuestionBody.ClozeBlank> list = new ArrayList<>();
        StringBuilder passage = new StringBuilder("<p>" + code);
        for (int i = 1; i <= blanks; i++) {
            list.add(new QuestionBody.ClozeBlank("b" + i, List.of("ans" + i), blankPoints));
            passage.append(" [[b").append(i).append("]]");
        }
        return new QuestionBody.ClozeBody(QuestionType.CLOZE, passage.append("</p>").toString(), list);
    }

    // ------------------------------------------------------------------------------------------ assets

    /**
     * The sample listening file of the 2B bank, uploaded once through the real attachment upload. A row whose object is
     * gone from storage (a wiped bucket, a new in-memory store) gets its object back, so the set is whole again.
     */
    private UUID ensureAudio() {
        return ensureAsset(AUDIO_NAME, "ATTACHMENT", "audio/mpeg", "db/devdata/assets/" + AUDIO_FILE,
                QuestionAssetKind.ATTACHMENT);
    }

    private UUID ensureImage(String fileName) {
        return ensureAsset(fileName, "IMAGE", "image/png", "devdata/assets/" + fileName, QuestionAssetKind.IMAGE);
    }

    private UUID ensureAsset(String fileName, String kind, String mime, String resource, QuestionAssetKind uploadKind) {
        var found = jdbc.queryForList("SELECT id, object_key FROM questionbank.question_asset WHERE file_name = ? "
                + "AND kind = ? ORDER BY created_at LIMIT 1", fileName, kind);
        if (!found.isEmpty()) {
            String key = (String) found.getFirst().get("object_key");
            if (!storage.exists(key)) {
                storage.put(key, read(resource), mime);
            }
            return (UUID) found.getFirst().get("id");
        }
        return assets.upload(uploadKind, new BytesMultipartFile(fileName, mime, read(resource))).id();
    }

    /**
     * The question of the constants file with an image in its prompt, in one option and in its solution (AC-21). A row
     * left in DRAFT by an earlier interrupted run is taken to READY; a READY transition that is skipped fails the build
     * instead of silently keeping an unusable question (R1-4).
     */
    private boolean ensureImageQuestion(Set<String> existing) {
        JsonNode image = constants().path("imageQuestion");
        String code = image.path("questionCode").asText();
        if (code.isBlank()) {
            return false;
        }
        UUID prompt = null;
        UUID option = null;
        UUID solution = null;
        for (JsonNode asset : image.path("assets")) {
            UUID id = ensureImage(asset.path("fileName").asText());
            switch (asset.path("role").asText()) {
                case "PROMPT" -> prompt = id;
                case "OPTION" -> option = id;
                case "SOLUTION" -> solution = id;
                default -> throw new IllegalStateException("Unknown image role in " + RESOURCE);
            }
        }
        if (existing.contains(code)) {
            String status = jdbc.queryForObject("SELECT status FROM questionbank.question WHERE source_code = ? "
                    + "AND parent_id IS NULL", String.class, code);
            if (!"DRAFT".equals(status)) {
                return false;
            }
            UUID id = jdbc.queryForObject("SELECT id FROM questionbank.question WHERE source_code = ? "
                    + "AND parent_id IS NULL", UUID.class, code);
            transaction.executeWithoutResult(tx -> markReady(id));
            return true;
        }
        Objects.requireNonNull(prompt, "PROMPT image");
        Objects.requireNonNull(option, "OPTION image");
        Objects.requireNonNull(solution, "SOLUTION image");
        List<ChoiceOption> options = List.of(
                new ChoiceOption("a", "<p>Option a <img data-asset-id='" + option + "' alt='option'></p>", true),
                new ChoiceOption("b", "<p>Option b</p>", false), new ChoiceOption("c", "<p>Option c</p>", false),
                new ChoiceOption("d", "<p>Option d</p>", false));
        SaveQuestionRequest request = new SaveQuestionRequest(QuestionType.SINGLE_CHOICE, "TOAN", "G12", "TH", null,
                null, BigDecimal.ONE, List.of(), List.of(),
                "<p>" + code + " <img data-asset-id='" + prompt + "' alt='prompt'></p>",
                "<p>Solution <img data-asset-id='" + solution + "' alt='solution'></p>", List.of(), List.of(),
                new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, options, true), code);
        transaction.executeWithoutResult(status -> {
            QuestionDetail detail = questions.create(UUID.randomUUID().toString(), request);
            markReady(detail.summary().id());
        });
        return true;
    }

    /** Takes the question to READY through the bulk transition and fails the transaction when it is skipped. */
    private void markReady(UUID id) {
        var result = questions.bulkReady(UUID.randomUUID().toString(), new BulkQuestionStatusRequest(List.of(id)));
        if (!result.skipped().isEmpty()) {
            throw new IllegalStateException("The image question " + id + " cannot be taken to READY: "
                    + result.skipped());
        }
    }

    JsonNode constants() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }

    private static byte[] read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Sample file is missing: " + path, e);
        }
    }

    private static UUID id(String suffix) {
        return UUID.fromString("00000000-0000-7000-8200-000000000" + suffix);
    }

    // ------------------------------------------------------------------------------------------ actor

    private <T> T runAs(UserDirectory.UserSummary actor, Supplier<T> work) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new CurrentUserAuthenticationToken(
                new CurrentUser(actor.id(), actor.username(), Realm.APP, Access.MANAGE, PermissionSet.all()), null));
        SecurityContextHolder.setContext(context);
        try {
            return work.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
