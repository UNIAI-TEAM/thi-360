package vn.unicom.thi360.exam.domain.solver;

/**
 * Lựa chọn được chọn của một phần CHOICE trong một đường cụ thể.
 *
 * @param partId     id phần
 * @param optionCode mã lựa chọn được chọn
 */
public record ChosenOption(String partId, String optionCode) {
}
