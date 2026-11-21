package vn.unicom.thi360.attempt.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AttemptChoiceSummary", description = "Thông tin lựa chọn đã khóa của lượt thi hoặc phần thi")
public record AttemptChoiceSummary(
        @Schema(description = "Mã nhánh, phương án hoặc tổ hợp môn đã chọn", requiredMode = Schema.RequiredMode.REQUIRED)
                String optionCode,
        @Schema(description = "Danh sách mã môn đã chọn (nếu có)", nullable = true)
                List<String> subjectCodes,
        @Schema(description = "Số câu theo từng môn đã chọn (nếu có)", nullable = true)
                Map<String, Integer> subjectCounts) {

    public AttemptChoiceSummary {
        subjectCodes = subjectCodes == null ? null : List.copyOf(subjectCodes);
        subjectCounts = subjectCounts == null ? null : Map.copyOf(subjectCounts);
    }

    public static AttemptChoiceSummary fromRaw(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        Object rawCode = map.get("optionCode");
        String optionCode = rawCode != null ? rawCode.toString().trim() : null;
        if (optionCode == null || optionCode.isEmpty()) {
            return null;
        }
        List<String> subjectCodes = null;
        if (map.get("subjectCodes") instanceof List<?> rawList && !rawList.isEmpty()) {
            List<String> list = new ArrayList<>(rawList.size());
            for (Object elem : rawList) {
                if (elem != null && !elem.toString().isBlank()) {
                    list.add(elem.toString());
                }
            }
            if (!list.isEmpty()) {
                subjectCodes = list;
            }
        }
        Map<String, Integer> subjectCounts = null;
        if (map.get("subjectCounts") instanceof Map<?, ?> rawCounts && !rawCounts.isEmpty()) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            rawCounts.forEach((k, v) -> {
                if (k != null && v instanceof Number num) {
                    counts.put(k.toString(), num.intValue());
                }
            });
            if (!counts.isEmpty()) {
                subjectCounts = counts;
            }
        }
        return new AttemptChoiceSummary(optionCode, subjectCodes, subjectCounts);
    }
}
