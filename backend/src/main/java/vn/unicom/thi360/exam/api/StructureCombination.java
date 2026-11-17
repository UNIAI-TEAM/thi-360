package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Mã tổ hợp đang hiệu lực từ Global List")
public record StructureCombination(String code, String nameVi, String nameEn, String examCode,
        List<String> subjectCodes) {
    public StructureCombination {
        subjectCodes = subjectCodes == null ? List.of() : List.copyOf(subjectCodes);
    }
}
