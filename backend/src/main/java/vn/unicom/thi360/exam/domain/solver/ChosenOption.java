package vn.unicom.thi360.exam.domain.solver;

/**
 * Phương án được chọn của một phần tự chọn trong một đường cụ thể (nhãn đường).
 *
 * @param partId   id phần
 * @param optionId id phương án được chọn (kể cả id của {@link SubjectChoice})
 */
public record ChosenOption(String partId, String optionId) {
}
