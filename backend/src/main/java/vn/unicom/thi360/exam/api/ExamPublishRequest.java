package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(name = "ExamPublishRequest")
public record ExamPublishRequest(@NotNull List<String> pathIds, String note,
                                 @NotNull Long expectedRevision) { }
