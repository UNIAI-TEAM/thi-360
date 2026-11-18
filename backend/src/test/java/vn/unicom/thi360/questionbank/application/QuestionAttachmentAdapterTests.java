package vn.unicom.thi360.questionbank.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.storage.ObjectStorage;

class QuestionAttachmentAdapterTests {
    /** Storage that records every call and fails like S3ObjectStorage.exists does when the connection is lost. */
    private static final class FakeStorage implements ObjectStorage {
        final List<String> asked = new ArrayList<>();
        final boolean down;
        final List<String> present;

        FakeStorage(boolean down, String... present) {
            this.down = down;
            this.present = List.of(present);
        }

        @Override public void put(String key, byte[] content, String contentType) { throw new UnsupportedOperationException(); }
        @Override public Optional<byte[]> get(String key) { throw new UnsupportedOperationException(); }
        @Override public void delete(String key) { throw new UnsupportedOperationException(); }

        @Override
        public boolean exists(String key) {
            asked.add(key);
            if (down) throw new ExternalServiceException("INTERNAL_ERROR");
            return present.contains(key);
        }
    }

    @SuppressWarnings("unchecked")
    private static NamedParameterJdbcTemplate rows(QuestionAttachmentAdapter.Row... rows) {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of(rows));
        return jdbc;
    }

    private static QuestionAttachmentAdapter.Row row(VersionKey key, String name, String objectKey) {
        return new QuestionAttachmentAdapter.Row(key, UUID.randomUUID(), name, "audio/mpeg", objectKey);
    }

    @Test
    void lostStorageConnectionFailsTheCallInsteadOfReportingMissingFiles() {
        var key = new VersionKey(UUID.randomUUID(), 1);
        var adapter = new QuestionAttachmentAdapter(rows(row(key, "a.mp3", "questions/a")), new FakeStorage(true));
        assertThatThrownBy(() -> adapter.attachments(List.of(key), true)).isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void asksStorageOncePerDistinctFileAndReportsPresence() {
        var first = new VersionKey(UUID.randomUUID(), 1);
        var second = new VersionKey(UUID.randomUUID(), 2);
        var storage = new FakeStorage(false, "questions/shared");
        var adapter = new QuestionAttachmentAdapter(rows(row(first, "shared.mp3", "questions/shared"),
                row(second, "shared.mp3", "questions/shared"), row(second, "gone.mp3", "questions/gone")), storage);
        var result = adapter.attachments(List.of(first, second), true);
        assertThat(storage.asked).containsExactly("questions/shared", "questions/gone");
        assertThat(result.get(first)).extracting(a -> a.readable()).containsExactly(true);
        assertThat(result.get(second)).extracting(a -> a.readable()).containsExactly(true, false);
    }

    @Test
    void withoutVerificationOrAttachmentsStorageIsNotCalled() {
        var key = new VersionKey(UUID.randomUUID(), 1);
        var storage = new FakeStorage(false);
        assertThat(new QuestionAttachmentAdapter(rows(row(key, "a.mp3", "questions/a")), storage)
                .attachments(List.of(key), false).get(key)).extracting(a -> a.readable()).containsOnlyNulls();
        assertThat(new QuestionAttachmentAdapter(rows(), storage).attachments(List.of(key), true)).isEmpty();
        assertThat(storage.asked).isEmpty();
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        assertThat(new QuestionAttachmentAdapter(jdbc, storage).attachments(List.of(), true)).isEmpty();
        verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }
}
