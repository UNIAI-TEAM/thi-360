package vn.unicom.thi360.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PageQueryTests {

    @Test
    void capsSizeAt100_andParsesAllowedSortField() {
        PageQuery query = PageQuery.of(0, 250, "username,desc", Set.of("username", "createdAt"));

        assertThat(query.page()).isEqualTo(0);
        assertThat(query.size()).isEqualTo(100);
        assertThat(query.sort()).isEqualTo(Sort.by(Sort.Order.desc("username")));
    }

    @Test
    void rejectsDisallowedSortField() {
        assertThatThrownBy(() -> PageQuery.of(0, 20, "secretField,asc", Set.of("username")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void mapsSpringPageToPageResponse() {
        var springPage = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);
        PageResponse<String> response = PageResponse.from(springPage);

        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalItems()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
