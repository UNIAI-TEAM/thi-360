package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Nút trong cây kiến thức (danh mục KNOWLEDGE)",
        // @ArraySchema của children không mang requiredMode lên được schema cha (R2-3) nên
        // liệt kê bắt buộc ở cấp class; springdoc gộp với requiredMode của từng field.
        requiredProperties = {"id", "code", "name", "level", "active", "children"})
public record KnowledgeNode(
        @Schema(description = "ID nút danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Mã nút (MAPID)", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên hiển thị", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Cấp trong cây: SUBJECT, CHAPTER, LESSON hoặc KNOWLEDGE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        KnowledgeLevel level,
        @Schema(description = "Mã môn liên kết hiệu lực (riêng hoặc kế thừa); null khi chưa gắn")
        String subjectCode,
        @Schema(description = "Mã khối lớp liên kết hiệu lực; null với nút cấp môn")
        String gradeCode,
        @Schema(description = "Nút đang có hiệu lực", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean active,
        // Đệ quy: implementation= làm springdoc mở rộng vô hạn (StackOverflow khi sinh
        // /v3/api-docs); items trỏ thẳng tới chính schema KnowledgeNode.
        @ArraySchema(schema = @Schema(ref = "#/components/schemas/KnowledgeNode"),
                arraySchema = @Schema(description = "Các nút con trực tiếp"))
        List<KnowledgeNode> children
) {
    public KnowledgeNode {
        children = children == null ? List.of() : List.copyOf(children);
    }

    public enum KnowledgeLevel {
        SUBJECT,
        CHAPTER,
        LESSON,
        KNOWLEDGE
    }
}
