package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.questionbank.application.DbQuestionAssetResolver;
import vn.unicom.thi360.questionbank.domain.QuestionAsset;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;
import vn.unicom.thi360.questionbank.infrastructure.QuestionAssetRepository;
import vn.unicom.thi360.questionbank.spi.HtmlContentSanitizer;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;

@Transactional
class QuestionAssetResolverWiringIT extends AbstractIntegrationTest {
    @Autowired QuestionAssetResolver resolver;
    @Autowired QuestionAssetRepository assets;
    @Autowired HtmlContentSanitizer sanitizer;

    @Test
    void realResolverActive() {
        UUID image = UUID.randomUUID();
        UUID attachment = UUID.randomUUID();
        for (var entry : java.util.Map.of(image, QuestionAssetKind.IMAGE,
                attachment, QuestionAssetKind.ATTACHMENT).entrySet()) {
            assets.saveAndFlush(new QuestionAsset(entry.getKey(), entry.getValue(),
                    "questions/" + entry.getKey(), "fixture", "application/octet-stream",
                    1, new byte[32], null, null, null, Instant.now()));
        }
        assertThat(resolver).isInstanceOf(DbQuestionAssetResolver.class);
        assertThat(resolver.servingPath(image)).contains("/api/v1/admin/question-assets/" + image);
        assertThat(resolver.servingPath(attachment)).isEmpty();
        assertThat(resolver.servingPath(attachment, QuestionAssetKind.ATTACHMENT)).isPresent();
        assertThat(resolver.servingPath(image, QuestionAssetKind.ATTACHMENT)).isEmpty();
        String html = "<img data-asset-id='" + image + "'>";
        assertThat(sanitizer.sanitize(html, HtmlContentSanitizer.Options.textOnly()).isClean()).isFalse();
    }
}
