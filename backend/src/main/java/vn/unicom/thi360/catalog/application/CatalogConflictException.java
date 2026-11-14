package vn.unicom.thi360.catalog.application;

import java.util.List;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;

public class CatalogConflictException extends ConflictException {

    private final List<FieldError> fieldErrors;

    public CatalogConflictException(String code, List<FieldError> fieldErrors) {
        super(code);
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
