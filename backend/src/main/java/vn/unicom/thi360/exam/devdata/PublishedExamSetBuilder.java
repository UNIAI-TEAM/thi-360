package vn.unicom.thi360.exam.devdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.exam.api.CreateExamRequest;
import vn.unicom.thi360.exam.api.ExamDetailResponse;
import vn.unicom.thi360.exam.api.ExamOverviewRequest;
import vn.unicom.thi360.exam.api.ExamPublishRequest;
import vn.unicom.thi360.exam.api.ExamStructureResponse;
import vn.unicom.thi360.exam.api.ExamVersionLifecycleController.DiscontinueRequest;
import vn.unicom.thi360.exam.api.LoadExamTemplateRequest;
import vn.unicom.thi360.exam.api.UpdateExamInfoRequest;
import vn.unicom.thi360.exam.api.UpdateExamListingRequest;
import vn.unicom.thi360.exam.api.UpdateExamStructureRequest;
import vn.unicom.thi360.exam.application.ExamCheckCompletion;
import vn.unicom.thi360.exam.application.ExamCheckOrchestrator;
import vn.unicom.thi360.exam.application.ExamCheckScope;
import vn.unicom.thi360.exam.application.ExamDiscontinueService;
import vn.unicom.thi360.exam.application.ExamListingService;
import vn.unicom.thi360.exam.application.ExamPublishService;
import vn.unicom.thi360.exam.application.ExamService;
import vn.unicom.thi360.exam.application.ExamTemplateService;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.ExamTemplate;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserAuthenticationToken;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;

/**
 * Exam half of the published exam set (DS). For every exam of {@code devdata/published-exam-set.json} it goes the real way
 * as {@code sys.admin}: create from the 2B template ({@link ExamService}), fill in the numbers, fill in the sale
 * listing, check, publish every valid path (E24) and, for the one marked so, discontinue. Nothing is inserted by SQL.
 *
 * <p>Each exam is one transaction, so a run that stops leaves only complete exams and the next run builds the missing
 * ones; an exam whose code is already there (published, discontinued) is left alone. It needs the questions of
 * {@code questionbank/devdata} but never calls them: the order of the two runners is the contract.
 */
@Component
@Profile({"dev", "e2e", "test"})
public class PublishedExamSetBuilder {
    private static final Logger log = LoggerFactory.getLogger(PublishedExamSetBuilder.class);
    static final String ACTOR = "sys.admin";
    private static final int MINI_REVIEW_QUESTIONS = 5;
    private static final int MINI_MINUTES = 10;

    private final ExamService exams;
    private final ExamListingService listings;
    private final ExamCheckOrchestrator checks;
    private final ExamPublishService publisher;
    private final ExamDiscontinueService discontinuer;
    private final ExamTemplateService templates;
    private final UserDirectory users;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final EntityManager entityManager;

    public PublishedExamSetBuilder(ExamService exams, ExamListingService listings, ExamCheckOrchestrator checks,
            ExamPublishService publisher, ExamDiscontinueService discontinuer, ExamTemplateService templates,
            UserDirectory users, JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactionManager,
            EntityManager entityManager) {
        this.exams = exams;
        this.listings = listings;
        this.checks = checks;
        this.publisher = publisher;
        this.discontinuer = discontinuer;
        this.templates = templates;
        this.users = users;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    /** What one build did: the exams it built and the ones that were already there. */
    public record Report(int built, int existing) { }

    /** Builds every exam that is missing, mains first and then the mini copies. Fails before any write without sys.admin. */
    public Report build() {
        UserDirectory.UserSummary actor = users.findActiveByUsername(ACTOR).orElseThrow(() -> new IllegalStateException(
                "Cannot build the published exam set: the account '" + ACTOR + "' does not exist or is not active. "
                        + "Create it first (DevDataInitializer) or set thi360.devdata.published-exams.enabled=false."));
        PublishedExamSetDefinition definition = PublishedExamSetDefinition.load(mapper);
        return runAs(actor, () -> buildAs(definition, actor));
    }

    private Report buildAs(PublishedExamSetDefinition definition, UserDirectory.UserSummary actor) {
        int built = 0;
        int existing = 0;
        List<String> failures = new ArrayList<>();
        for (PublishedExamSetDefinition.Exam exam : definition.exams()) {
            try {
                boolean done = Boolean.TRUE.equals(transaction.execute(status -> buildExam(definition, exam, actor)));
                if (done) {
                    built++;
                } else {
                    existing++;
                }
            } catch (RuntimeException e) {
                // One exam failing does not stop the others: the complete ones stay, the next run finishes the rest.
                failures.add(e.getMessage());
            }
        }
        log.atInfo().addKeyValue("event", "published_exam_set.built").addKeyValue("built", built)
                .addKeyValue("existing", existing).addKeyValue("failed", failures.size())
                .log("Published exam set verified");
        if (!failures.isEmpty()) {
            throw new IllegalStateException(failures.size() + " exam(s) of the published exam set could not be built:\n"
                    + String.join("\n", failures));
        }
        return new Report(built, existing);
    }

    /** True when the exam was built now, false when it was already there. */
    private boolean buildExam(PublishedExamSetDefinition definition, PublishedExamSetDefinition.Exam def,
            UserDirectory.UserSummary actor) {
        List<UUID> found = jdbc.queryForList("SELECT id FROM exam.exam WHERE code_normalized = ?", UUID.class,
                def.code().trim().toLowerCase(java.util.Locale.ROOT));
        if (!found.isEmpty()) {
            String status = jdbc.queryForObject("SELECT status FROM exam.exam WHERE id = ?", String.class, found.getFirst());
            if (!"DRAFT".equals(status)) {
                return false;
            }
        }
        try {
            UUID id = found.isEmpty() ? create(def) : found.getFirst();
            fill(def, id);
            fillListing(def, id);
            publish(def, id, actor);
            if (def.code().equals(definition.discontinuedExam())) {
                sync();
                discontinuer.discontinue(id, key("discontinue", def), new DiscontinueRequest(revision(id)));
            }
            return true;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot build the exam " + def.code() + ": "
                    + (e.getMessage() == null ? e.toString() : e.getMessage()), e);
        }
    }

    // ------------------------------------------------------------------------------------------ steps

    private UUID create(PublishedExamSetDefinition.Exam def) {
        List<ExamOverviewRequest.Row> overview = null;
        Integer minutes = def.durationMinutes();
        if (def.mini() && minutes == null) {
            minutes = MINI_MINUTES;
        }
        if (def.review()) {
            overview = List.of(new ExamOverviewRequest.Row(firstPartId(def.template()), null, null, null,
                    def.mini() ? MINI_REVIEW_QUESTIONS : 10, minutes, null));
        }
        ExamDetailResponse created = exams.createExam(key("create", def), new CreateExamRequest(def.kind(), def.scope(),
                def.partCode(), def.subjectCode(), def.code(), def.name(), minutes, null, def.name(), null,
                def.buildMode(), overview));
        sync();
        if (def.template() != null && !def.template().equals(created.templateCode())) {
            // The default template of the kind and scope is not the one this exam is built on (SP2E Toan 2025).
            exams.loadTemplate(created.id(), new LoadExamTemplateRequest(def.template(), Boolean.TRUE, "USE_TEMPLATE",
                    created.revision()));
            sync();
        }
        return created.id();
    }

    /** Fills in what a template leaves to the admin: the question type of a review group, and the size of a mini. */
    private void fill(PublishedExamSetDefinition.Exam def, UUID id) {
        if (def.review()) {
            ExamStructureResponse structure = exams.getExamStructure(id);
            ObjectNode root = mapper.valueToTree(new UpdateExamStructureRequest(structure.parts(), structure.examQuotas(),
                    structure.revision()));
            ObjectNode block = (ObjectNode) root.path("parts").get(0).path("sections").get(0).path("blocks").get(0);
            if (block.path("questionType").isMissingNode() || block.path("questionType").isNull()) {
                block.put("questionType", "SINGLE_CHOICE");
                write(id, mapper.convertValue(root, UpdateExamStructureRequest.class));
            }
            return;
        }
        if (!def.mini()) {
            return;
        }
        ExamStructureResponse structure = exams.getExamStructure(id);
        MiniStructure.Result mini = MiniStructure.shrink(
                new ExamStructureDocument(structure.parts(), structure.examQuotas()),
                def.sectionUnits() == null ? java.util.Map.of() : def.sectionUnits());
        write(id, new UpdateExamStructureRequest(mini.document().parts(), List.of(), structure.revision()));
        ExamDetailResponse detail = exams.getExamDetail(id);
        boolean shared = "SHARED".equals(detail.timing());
        boolean linear = "LINEAR".equals(detail.practiceScoring());
        exams.updateExamInfo(id, new UpdateExamInfoRequest(null, null, null, null, null, null, null, null, null, null,
                shared ? (def.durationMinutes() == null ? MINI_MINUTES : def.durationMinutes()) : mini.minutes(),
                linear ? null : mini.maxPoints(), null, linear ? mini.maxPoints() : null, null, null, null, detail.revision()));
        sync();
    }

    private void write(UUID id, UpdateExamStructureRequest request) {
        exams.updateStructure(id, request);
        sync();
    }

    private void fillListing(PublishedExamSetDefinition.Exam def, UUID id) {
        PublishedExamSetDefinition.Listing listing = def.listing();
        listings.update(id, new UpdateExamListingRequest(def.name() + " - sample exam of the published exam set",
                "Practise the paper", "Students preparing for the exam", listing.priceVnd(), listing.attemptsGranted(),
                true, true, true, null, listing.resultView(), def.name(), null, revision(id)));
        sync();
    }

    private void publish(PublishedExamSetDefinition.Exam def, UUID id, UserDirectory.UserSummary actor) {
        ExamCheckCompletion.Actor checkActor = new ExamCheckCompletion.Actor(actor.id(), actor.username(),
                AuditActorType.USER);
        ObjectNode result = checks.check(id, ExamCheckScope.from(null, null, null), checkActor, null);
        sync();
        if (!"VALID".equals(result.path("status").asText())) {
            throw new IllegalStateException("the check is " + result.path("status").asText() + ": " + summary(result));
        }
        ArrayNode paths = mapper.createArrayNode();
        for (JsonNode path : result.path("paths")) {
            if ("VALID".equals(path.path("result").asText())) {
                paths.add(path.path("pathId").asText());
            }
        }
        List<String> ids = new ArrayList<>();
        paths.forEach(path -> ids.add(path.asText()));
        publisher.publish(id, key("publish", def), new ExamPublishRequest(ids, "Published exam set", revision(id)));
        sync();
    }

    // ------------------------------------------------------------------------------------------ helpers

    private String firstPartId(String templateCode) {
        ExamTemplate template = templates.findActive(templateCode);
        if (template == null) {
            throw new IllegalStateException("template " + templateCode + " is missing");
        }
        return template.getStructure().path("parts").get(0).path("id").asText();
    }

    private long revision(UUID id) {
        return jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, id);
    }

    /** Services mix entities and SQL: push the entities out and drop them so the next step reads the rows. */
    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * A new key every time: each exam is one transaction, so no key outlives a failed run, and a key of an earlier run
     * must never answer for an exam that has since been removed.
     */
    private static String key(String step, PublishedExamSetDefinition.Exam def) {
        return "ds-" + step + "-" + def.code() + "-" + UUID.randomUUID();
    }

    /** The blocking issues of a check, one short entry each: code, subject and block, required against available. */
    private String summary(ObjectNode result) {
        List<String> entries = new ArrayList<>();
        for (JsonNode group : result.path("groups")) {
            for (JsonNode issue : group.path("issues")) {
                if (!"ERROR".equals(issue.path("severity").asText())) {
                    continue;
                }
                StringBuilder entry = new StringBuilder(issue.path("code").asText());
                String where = issue.path("subjectCode").asText("") + " " + issue.path("blockName").asText("")
                        + " " + issue.path("partName").asText("");
                if (!where.isBlank()) {
                    entry.append(" [").append(where.trim()).append(']');
                }
                if (issue.has("required") && !issue.path("required").isNull()) {
                    entry.append(" required=").append(issue.path("required").asText()).append(" available=")
                            .append(issue.path("available").asText());
                }
                entries.add(entry.toString());
            }
        }
        return String.join("; ", entries);
    }

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
