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
        return candidates.stream().max(byYear.thenComparing(ExamTemplate::getCode)).orElseThrow();
    }

    ExamTemplateResponse toResponse(ExamTemplate t) {
        JsonNode structure = t.getStructure();
        return new ExamTemplateResponse(
                t.getCode(),
                t.getKindCode(),
                t.getScope(),
                t.getPartCode(),
                t.getSubjectCode(),
                t.getStructureYear(),
                t.getMatrixYear(),
                t.getTiming(),
                "SHARED".equals(t.getTiming()) ? t.getDurationMinutes() : null,
                t.getMaxScore(),
                t.getExpertNote(),
                t.isActive(),
                structure);
    }
}
