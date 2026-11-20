package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.exam.api.ExamTemplateResponse;
import vn.unicom.thi360.exam.domain.ExamTemplate;
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
     * One summary per template part: the question count per question type of its blocks that are not omitted. A part
     * with options takes the blocks of the first option only, because a paper is built from one option.
     */
    static List<ExamTemplateResponse.PartSummary> partSummaries(JsonNode structure) {
        List<ExamTemplateResponse.PartSummary> summaries = new ArrayList<>();
        for (JsonNode part : structure.path("parts")) {
            List<JsonNode> blocks = new ArrayList<>();
            collectBlocks(part.path("sections"), blocks);
            collectBlocks(part.path("subjectPool"), blocks);
            JsonNode firstOption = part.path("options").path(0);
            if (blocks.isEmpty() && !firstOption.isMissingNode()) {
                collectBlocks(firstOption.path("sections"), blocks);
                firstOption.path("blocks").forEach(blocks::add);
            }
            java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            for (JsonNode block : blocks) {
                if (block.path("omitted").asBoolean(false)) continue;
                int count = block.hasNonNull("questionCount") ? block.path("questionCount").asInt()
                        : block.path("setCount").asInt(0) * block.path("setSize").asInt(0);
                counts.merge(block.path("questionType").asText("ANY"), count, Integer::sum);
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

    private static void collectBlocks(JsonNode sections, List<JsonNode> into) {
        for (JsonNode section : sections) section.path("blocks").forEach(into::add);
    }
}
