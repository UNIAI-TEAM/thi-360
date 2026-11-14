package vn.unicom.thi360.shared.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import vn.unicom.thi360.shared.web.AppMessages;

public record PageQuery(
        int page,
        int size,
        Sort sort
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    private static final AppMessages MESSAGES = AppMessages.defaultInstance();
    public static PageQuery of(
            Integer page,
            Integer size,
            String sortParam,
            Collection<String> allowedSortFields,
            Sort defaultSort) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        if (resolvedPage < 0) {
            throw new ValidationException(List.of(
                    new FieldError("page", "INVALID", MESSAGES.get("validation.pageQuery.page.min"))));
        }

        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (resolvedSize < 1) {
            throw new ValidationException(List.of(
                    new FieldError("size", "INVALID", MESSAGES.get("validation.pageQuery.size.min"))));
        }
        if (resolvedSize > MAX_SIZE) {
            resolvedSize = MAX_SIZE;
        }

        Set<String> allowed = allowedSortFields == null ? Set.of() : Set.copyOf(allowedSortFields);
        Sort resolvedSort = parseSort(sortParam, allowed, defaultSort == null ? Sort.unsorted() : defaultSort);
        return new PageQuery(resolvedPage, resolvedSize, resolvedSort);
    }

    public static PageQuery of(
            Integer page,
            Integer size,
            String sortParam,
            Collection<String> allowedSortFields) {
        return of(page, size, sortParam, allowedSortFields, Sort.unsorted());
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, sort);
    }

    private static Sort parseSort(String sortParam, Set<String> allowed, Sort defaultSort) {
        if (sortParam == null || sortParam.isBlank()) {
            return defaultSort;
        }
        String trimmed = sortParam.trim();
        String[] parts = trimmed.split(",", -1);
        String field = parts[0].trim();
        if (field.isEmpty() || !allowed.contains(field)) {
            throw new ValidationException(List.of(
                    new FieldError(
                            "sort",
                            "INVALID_SORT_FIELD",
                            MESSAGES.get("validation.pageQuery.sort.invalidField", field))));
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1 && !parts[1].isBlank()) {
            String dir = parts[1].trim().toLowerCase(Locale.ROOT);
            if ("asc".equals(dir)) {
                direction = Sort.Direction.ASC;
            } else if ("desc".equals(dir)) {
                direction = Sort.Direction.DESC;
            } else {
                throw new ValidationException(List.of(
                        new FieldError(
                                "sort",
                                "INVALID_SORT_DIRECTION",
                                MESSAGES.get("validation.pageQuery.sort.invalidDirection", parts[1]))));
            }
        }
        List<Sort.Order> orders = new ArrayList<>();
        orders.add(new Sort.Order(direction, field));
        return Sort.by(orders);
    }
}
