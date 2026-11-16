package vn.unicom.thi360.questionbank.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Thu thập id tài nguyên mà một {@link QuestionSnapshot} dùng: mọi {@code data-asset-id}
 * trong các trường HTML (đúng danh sách đã đi qua {@link QuestionHtmlSanitizer}) cộng
 * {@code attachmentIds} (model note BE-2b mục 3: snapshot chỉ giữ id).
 *
 * <p>Với {@code QUESTION_SET}, {@code body.children} nhúng trong snapshot cha chỉ để đọc lại;
 * tài nguyên trong nội dung câu con thuộc ref của dòng câu con (quyết định lane 8.4) nên bộ
 * thu thập bỏ qua HTML của children — chỉ lấy {@code passage} của bộ.
 */
public final class QuestionAssetRefCollector {

    private QuestionAssetRefCollector() {
    }

    /** Id tài nguyên của snapshot (không trùng, giữ thứ tự gặp đầu). */
    public static Set<UUID> assetIdsOf(QuestionSnapshot snapshot) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (snapshot == null) {
            return ids;
        }
        collectHtml(snapshot.content(), ids);
        collectHtml(snapshot.solution(), ids);
        for (String mistake : nullToEmpty(snapshot.commonMistakes())) {
            collectHtml(mistake, ids);
        }
        collectBody(snapshot.body(), ids);
        ids.addAll(nullToEmpty(snapshot.attachmentIds()));
        return ids;
    }

    private static void collectBody(QuestionBody body, Set<UUID> ids) {
        if (body == null) {
            return;
        }
        switch (body) {
            case QuestionBody.SingleChoiceBody b -> collectOptions(b.options(), ids);
            case QuestionBody.MultipleChoiceBody b -> collectOptions(b.options(), ids);
            case QuestionBody.ShortAnswerBody b -> collectHtml(b.inputHint(), ids);
            case QuestionBody.ClozeBody b -> {
                collectHtml(b.passage(), ids);
                for (QuestionBody.ClozeBlank blank : nullToEmpty(b.blanks())) {
                    for (String answer : nullToEmpty(blank.answers())) {
                        collectHtml(answer, ids);
                    }
                }
            }
            case QuestionBody.QuestionSetBody b -> collectHtml(b.passage(), ids);
            case QuestionBody.TrueFalseBody b -> {
                for (QuestionBody.TrueFalseStatement s : nullToEmpty(b.statements())) {
                    collectHtml(s.content(), ids);
                }
            }
            case QuestionBody.MatchingBody b -> {
                collectSides(b.left(), ids);
                collectSides(b.right(), ids);
            }
            case QuestionBody.EssayBody b -> {
                collectHtml(b.gradingGuide(), ids);
                for (QuestionBody.EssayCriterion c : nullToEmpty(b.rubric())) {
                    collectHtml(c.description(), ids);
                }
                collectHtml(b.sampleAnswer(), ids);
            }
        }
    }

    private static void collectOptions(List<QuestionBody.ChoiceOption> options, Set<UUID> ids) {
        for (QuestionBody.ChoiceOption o : nullToEmpty(options)) {
            collectHtml(o.content(), ids);
        }
    }

    private static void collectSides(List<QuestionBody.MatchingSide> sides, Set<UUID> ids) {
        for (QuestionBody.MatchingSide s : nullToEmpty(sides)) {
            collectHtml(s.content(), ids);
        }
    }

    /** {@code data-asset-id} không phải UUID bị bỏ qua: HTML đã lọc không chứa id hỏng. */
    private static void collectHtml(String html, Set<UUID> ids) {
        if (html == null || html.isEmpty()) {
            return;
        }
        Document document = Jsoup.parseBodyFragment(html);
        for (Element img : document.select("img[data-asset-id]")) {
            String raw = img.attr("data-asset-id").trim();
            if (raw.isEmpty()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(raw));
            } catch (IllegalArgumentException malformed) {
                // Bỏ qua: lỗi id đã được bộ lọc chặn trước.
            }
        }
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
