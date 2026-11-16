package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "ExamVersionPath")
public record ExamVersionPath(String pathId, List<Choice> choices) {
    @Schema(name = "ExamVersionPathChoice")
    public record Choice(String partId, String optionId, String kind, String code, String name) { }
}
