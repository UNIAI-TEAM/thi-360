package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OptionKind {
    SUBJECT,
    BRANCH,
    SUBJECT_CHOICE;

    @JsonCreator
    public static OptionKind fromValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
