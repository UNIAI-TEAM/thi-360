package vn.unicom.thi360.exam.domain.solver;

/** Assignment of one counting unit of a selected question to a topic quota. */
public record TopicAssignment(String level, String scopeId, String quotaId, int unitIndex) {}
