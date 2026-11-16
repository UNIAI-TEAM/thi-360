package vn.unicom.thi360.questionbank.domain.importing;

import java.util.List;

/**
 * Ảnh chụp danh mục cần thiết để đọc một lần nhập, do lớp ứng dụng nạp một lần theo lô
 * (không truy vấn theo dòng). Các mã tra theo cột của spec 4.6: {@code subject},
 * {@code grade}, {@code difficulty}, {@code taskType}, {@code knowledge}, {@code tags}.
 */
public record ImportCatalog(
    List<ImportCatalogOption> subjects,
    List<ImportCatalogOption> grades,
    List<ImportCatalogOption> difficulties,
    List<ImportCatalogOption> taskTypes,
    List<ImportCatalogOption> tags,
    List<ImportKnowledgeNode> knowledgeNodes) {

    public ImportCatalog {
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
        grades = grades == null ? List.of() : List.copyOf(grades);
        difficulties = difficulties == null ? List.of() : List.copyOf(difficulties);
        taskTypes = taskTypes == null ? List.of() : List.copyOf(taskTypes);
        tags = tags == null ? List.of() : List.copyOf(tags);
        knowledgeNodes = knowledgeNodes == null ? List.of() : List.copyOf(knowledgeNodes);
    }

    public static ImportCatalog empty() {
        return new ImportCatalog(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
