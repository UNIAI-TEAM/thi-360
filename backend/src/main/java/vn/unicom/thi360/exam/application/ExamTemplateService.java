package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.exam.api.ExamTemplateResponse;
import vn.unicom.thi360.exam.domain.ExamTemplate;
import vn.unicom.thi360.exam.domain.ExamTemplateCheckFacts;
import vn.unicom.thi360.exam.infrastructure.ExamTemplateRepository;

/** Đọc nguyên mẫu cấu trúc (exam_template) và chọn mẫu khớp phạm vi (spec 5.2). */
@Service
public class ExamTemplateService {

    private final ExamTemplateRepository templateRepository;

    public ExamTemplateService(ExamTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /** Toàn bộ mẫu (màn tra cứu nguyên mẫu); mới nhất theo năm cấu trúc trước. */
    @Transactional(readOnly = true)
    public List<ExamTemplateResponse> listExamTemplates() {
        List<ExamTemplateResponse> out = new ArrayList<>();
        for (ExamTemplate t : templateRepository.findAll()) {
            out.add(toResponse(t));
        }
        out.sort(Comparator.comparing(ExamTemplateResponse::code));
        return out;
    }

    @Transactional(readOnly = true)
    public ExamTemplate findActive(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) {
            return null;
        }
        return templateRepository.findById(templateCode.trim())
                .filter(ExamTemplate::isActive)
                .orElse(null);
    }

    /** Read recorded provenance even after a template is retired. */
    @Transactional(readOnly = true)
    ExamTemplate findProvenance(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) return null;
        return templateRepository.findById(templateCode.trim()).orElse(null);
    }

    /**
     * The template {@link #findBestMatch} would pick for the SUBJECT scope of a kind and subject, as its flag columns
     * only (FU-BE15-R1-5). The order is the one of {@code findBestMatch}: default first, then the latest structure year,
     * then the highest code; null when the subject has no active standard template.
     */
    @Transactional(readOnly = true)
    public ExamTemplateCheckFacts findSubjectStandardFacts(String kindCode, String subjectCode) {
        return templateRepository.findSubjectStandardFacts(kindCode, subjectCode).stream()
                .max(Comparator.comparing(ExamTemplateCheckFacts::defaultTemplate)
                        .thenComparing(facts -> facts.structureYear() == null ? 0 : facts.structureYear())
                        .thenComparing(ExamTemplateCheckFacts::code))
                .orElse(null);
    }

    /** The flag columns of the template an exam was loaded from, even when it is retired; null when it is unknown. */
    @Transactional(readOnly = true)
    public ExamTemplateCheckFacts findCheckFacts(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) return null;
        return templateRepository.findCheckFacts(templateCode.trim()).orElse(null);
    }

    /** The structure JSON of one template: read only when a lock of that template applies to the exam being checked. */
    @Transactional(readOnly = true)
    public JsonNode readStructure(String templateCode) {
        return templateRepository.findStructureByCode(templateCode).orElseThrow();
    }

    /** Select only the configured default; review templates are shared by subjects. */
    @Transactional(readOnly = true)
    public ExamTemplate findDefault(String kindCode, String scope, String partCode,
            String subjectCode, String buildMode) {
        return templateRepository.findByKindCodeAndScopeAndActiveTrue(kindCode, scope).stream()
                .filter(t -> t.isDefaultTemplate() && buildMode.equals(t.getBuildMode()))
                .filter(t -> java.util.Objects.equals(partCode, t.getPartCode()))
                .filter(t -> java.util.Objects.equals(subjectCode, t.getSubjectCode())
                        || ("REVIEW".equals(buildMode) && t.getSubjectCode() == null))
                .findFirst().orElse(null);
    }

    /**
     * Whether the subject has any active dedicated PART template (H41). When it does, PART exams of
     * that subject come only from those templates: a missing part code is {@code EXAM_PART_NOT_FOUND}
     * instead of a cut from the subject template.
     */
    @Transactional(readOnly = true)
    public boolean hasPartTemplates(String kindCode, String subjectCode) {
        if (subjectCode == null) {
            return false;
        }
        return templateRepository
                .findByKindCodeAndScopeAndSubjectCodeAndActiveTrue(kindCode, "PART", subjectCode)
                .stream().anyMatch(t -> "STANDARD".equals(t.getBuildMode()));
    }

    /**
     * Mẫu mới nhất khớp kỳ thi + phạm vi; khi phạm vi PART/SUBJECT khớp thêm phần/môn.
     * Ưu tiên mẫu có {@code structureYear} bằng {@code year} (nếu truyền), sau đó năm lớn nhất.
     */
    @Transactional(readOnly = true)
    public ExamTemplate findBestMatch(String kindCode, String scope, String partCode,
            String subjectCode, Integer year) {
        List<ExamTemplate> candidates;
        if ("PART".equals(scope) && partCode != null) {
            candidates = templateRepository
                    .findByKindCodeAndScopeAndPartCodeAndActiveTrue(kindCode, scope, partCode);
        } else if ("SUBJECT".equals(scope) && subjectCode != null) {
            candidates = templateRepository
                    .findByKindCodeAndScopeAndSubjectCodeAndActiveTrue(kindCode, scope, subjectCode);
        } else {
            candidates = templateRepository.findByKindCodeAndScopeAndActiveTrue(kindCode, scope);
        }
        candidates = candidates.stream().filter(t -> "STANDARD".equals(t.getBuildMode())).toList();
        if (candidates.isEmpty()) {
            return null;
        }
        Comparator<ExamTemplate> byYear = Comparator.comparing(
                t -> t.getStructureYear() == null ? 0 : t.getStructureYear());
        if (year != null) {
            List<ExamTemplate> sameYear = candidates.stream()
                    .filter(t -> year.equals(t.getStructureYear()))
                    .toList();
            if (!sameYear.isEmpty()) {
                return sameYear.stream().max(byYear.thenComparing(ExamTemplate::getCode)).orElseThrow();
            }
        }
        return candidates.stream().max(Comparator.comparing(ExamTemplate::isDefaultTemplate)
                .thenComparing(byYear).thenComparing(ExamTemplate::getCode)).orElseThrow();
    }

    ExamTemplateResponse toResponse(ExamTemplate t) {
        JsonNode structure = t.getStructure();
        return new ExamTemplateResponse(
                t.getCode(),
                t.getKindCode(),
                t.getScope(),
                t.getPartCode(),
                t.getSubjectCode(),
                t.getLabel(),
                t.getSummary(),
                t.getBuildMode(),
                t.isDefaultTemplate(),
                t.getStructureYear(),
                t.getMatrixYear(),
                t.getTiming(),
                "SHARED".equals(t.getTiming()) ? t.getDurationMinutes() : null,
                t.getMaxScore(),
                t.getExpertNote(),
                t.isActive(),
                structure,
                t.isPublishable(),
                t.isFixedCounts(),
                t.isFixedBlockCounts(),
                t.isOmittedLocked(),
                partSummaries(structure));
    }

    /**
     * One summary per template part: the question count per question type of its non-omitted blocks, matching FE
     * {@code partTypeCounts} and {@link vn.unicom.thi360.exam.domain.BlockCounts}. Choice parts inspect the first
     * option unless it is {@code SUBJECT_CHOICE} (whose sections cover more subjects than one paper takes);
     * {@code subjectPool}-only parts and {@code SUBJECT_CHOICE} parts return empty counts, and non-positive counts
     * (such as unconfigured review placeholders) are omitted.
     */
    static List<ExamTemplateResponse.PartSummary> partSummaries(JsonNode structure) {
        List<ExamTemplateResponse.PartSummary> summaries = new ArrayList<>();
        for (JsonNode part : structure.path("parts")) {
            java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            boolean choice = "CHOICE".equals(part.path("participation").path("mode").asText())
                    || "CHOICE".equals(part.path("mode").asText())
                    || (part.path("sections").isEmpty() && !part.path("options").path(0).isMissingNode());
            if (choice) {
                JsonNode firstOption = part.path("options").path(0);
                if (!firstOption.isMissingNode() && !"SUBJECT_CHOICE".equals(firstOption.path("kind").asText())) {
                    addSectionBlockCounts(firstOption.path("sections"), counts);
                    addDirectBlockCounts(firstOption.path("blocks"), counts);
                }
            } else {
                addSectionBlockCounts(part.path("sections"), counts);
            }
            summaries.add(new ExamTemplateResponse.PartSummary(
                    part.hasNonNull("id") ? part.path("id").asText() : null,
                    part.hasNonNull("order") ? part.path("order").asInt() : null,
                    part.path("name").asText(""),
                    part.hasNonNull("questionCount") ? part.path("questionCount").asInt() : null,
                    counts.entrySet().stream()
                            .map(entry -> new ExamTemplateResponse.QuestionTypeCount(entry.getKey(), entry.getValue()))
                            .toList()));
        }
        return summaries;
    }

    private static void addSectionBlockCounts(JsonNode sections, java.util.Map<String, Integer> counts) {
        for (JsonNode section : sections) {
            JsonNode blocks = section.path("blocks");
            int fixedInSection = 0;
            int remainderBlocks = 0;
            for (JsonNode block : blocks) {
                if (block.path("omitted").asBoolean(false)) continue;
                String type = block.path("questionType").asText("ANY");
                if (isRemainderBlock(block, type)) {
                    remainderBlocks++;
                } else {
                    fixedInSection += fixedBlockCount(block, type);
                }
            }
            int remainderCount = remainderBlocks == 1 && section.hasNonNull("questionCount")
                    ? Math.max(0, section.path("questionCount").asInt() - fixedInSection)
                    : 0;
            for (JsonNode block : blocks) {
                if (block.path("omitted").asBoolean(false)) continue;
                String type = block.path("questionType").asText("ANY");
                int count = isRemainderBlock(block, type) ? remainderCount : fixedBlockCount(block, type);
                if (count > 0) counts.merge(type, count, Integer::sum);
            }
        }
    }

    private static void addDirectBlockCounts(JsonNode blocks, java.util.Map<String, Integer> counts) {
        for (JsonNode block : blocks) {
            if (block.path("omitted").asBoolean(false)) continue;
            String type = block.path("questionType").asText("ANY");
            int count = fixedBlockCount(block, type);
            if (count > 0) counts.merge(type, count, Integer::sum);
        }
    }

    private static boolean isRemainderBlock(JsonNode block, String type) {
        return !"QUESTION_SET".equals(type) && !"CLOZE".equals(type) && !block.hasNonNull("questionCount");
    }

    private static int fixedBlockCount(JsonNode block, String type) {
        if ("QUESTION_SET".equals(type) || "CLOZE".equals(type)) {
            int setCount = block.path("setCount").asInt(0);
            int setSize = block.path("setSize").asInt(0);
            if (setCount > 0 && setSize > 0) {
                long product = (long) setCount * setSize;
                return product > Integer.MAX_VALUE ? 0 : (int) product;
            }
        }
        return block.hasNonNull("questionCount") ? block.path("questionCount").asInt(0) : 0;
    }
}
