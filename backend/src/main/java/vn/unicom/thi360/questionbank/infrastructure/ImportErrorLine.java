package vn.unicom.thi360.questionbank.infrastructure;

import vn.unicom.thi360.questionbank.domain.importing.ImportIssue;

/**
 * Một dòng của danh sách lỗi lưu trong {@code question_import.errors} (mirror
 * {@code ImportErrorLine} của web): số dòng, mã nguồn của dòng và lỗi chặn kèm trường/cột.
 */
public record ImportErrorLine(int rowNumber, String sourceCode, ImportIssue issue) {}
