package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ExamVersionChange")
public record ExamVersionChange(String id, String kind, String where, String before, String after,
                                String blockId, String warning) { }
