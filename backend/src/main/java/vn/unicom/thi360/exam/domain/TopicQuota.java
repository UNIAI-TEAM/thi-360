package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TopicQuota(
        String id,
        String name,
        List<UUID> nodeIds,
        Integer count,
        QuotaBasis basis,
        QuotaEnforcement enforcement,
        List<String> gradeCodes,
        List<String> difficultyCodes,
        List<String> groupIds,
        BigDecimal percent) {
    public TopicQuota {
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
        gradeCodes = gradeCodes == null ? List.of() : List.copyOf(gradeCodes);
        difficultyCodes = difficultyCodes == null ? List.of() : List.copyOf(difficultyCodes);
        groupIds = groupIds == null ? List.of() : List.copyOf(groupIds);
    }

    /** Hàm dựng cũ (9 tham số) — percent để trống. */
    public TopicQuota(String id, String name, List<UUID> nodeIds, Integer count,
            QuotaBasis basis, QuotaEnforcement enforcement, List<String> gradeCodes,
            List<String> difficultyCodes, List<String> groupIds) {
        this(id, name, nodeIds, count, basis, enforcement, gradeCodes, difficultyCodes,
                groupIds, null);
    }
}
