package vn.unicom.thi360.attempt.domain.grading;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Pure stateless grading engine covering all 8 question formats defined in BE §8 and LT §9.1.
 *
 * <p>No Spring, JPA, clock, or I/O dependencies; uses only issued snapshot JSON and student answer.
 */
public final class ItemGrader {

    private static final List<BigDecimal> DEFAULT_THPTQG_TF_TABLE = List.of(
            new BigDecimal("0.1"),
            new BigDecimal("0.25"),
            new BigDecimal("0.5"),
            BigDecimal.ONE);

    private ItemGrader() {}

    public static ItemGradeResult grade(
            QuestionType type,
            JsonNode snapshotOrBody,
            AnswerValue answer,
            BigDecimal pointsMax,
            GradingPolicy policy) {
        Objects.requireNonNull(type, "questionType must not be null");
        GradingPolicy effectivePolicy = policy == null ? GradingPolicy.standard() : policy;
        JsonNode root = snapshotOrBody;
        JsonNode body = unwrapBody(root);
        BigDecimal effectiveMax = pointsMax != null
                ? pointsMax
                : decimalOrDefault(root != null ? root.path("points") : null, BigDecimal.ONE);

        return switch (type) {
            case SINGLE_CHOICE -> gradeSingleChoice(body, answer, effectiveMax);
            case MULTIPLE_CHOICE -> gradeMultipleChoice(body, answer, effectiveMax, effectivePolicy);
            case SHORT_ANSWER -> gradeShortAnswer(body, answer, effectiveMax);
            case CLOZE -> gradeCloze(body, answer, effectiveMax);
            case TRUE_FALSE -> gradeTrueFalse(body, answer, effectiveMax);
            case MATCHING -> gradeMatching(body, answer, effectiveMax);
            case ESSAY -> gradeEssay(answer, effectiveMax);
            case QUESTION_SET -> ItemGradeResult.graded(
                    ExactRational.ZERO,
                    effectiveMax,
                    ItemOutcome.BLANK,
                    detailWithExact(ExactRational.ZERO, Map.of("itemType", "QUESTION_SET")));
        };
    }

    /**
     * Rolls up graded child {@link ItemGradeResult}s into their parent {@code QUESTION_SET} item result (AC-6).
     */
    public static ItemGradeResult rollUpQuestionSet(
            List<ItemGradeResult> childResults, BigDecimal pointsMax) {
        ExactRational totalMax = ExactRational.ZERO;
        ExactRational totalEarned = ExactRational.ZERO;
        boolean anyPending = false;
        boolean allBlank = true;

        if (childResults != null) {
            for (ItemGradeResult child : childResults) {
                if (child == null) {
                    continue;
                }
                totalMax = totalMax.add(ExactRational.of(child.pointsMax()));
                if (child.pendingReview() || child.outcome() == ItemOutcome.PENDING_REVIEW) {
                    anyPending = true;
                    allBlank = false;
                } else {
                    totalEarned = totalEarned.add(
                            child.exactEarnedPoints() != null
                                    ? child.exactEarnedPoints()
                                    : ExactRational.of(child.earnedPoints()));
                    if (child.outcome() != ItemOutcome.BLANK) {
                        allBlank = false;
                    }
                }
            }
        }

        BigDecimal effectiveMax = pointsMax != null ? pointsMax : totalMax.toBigDecimal(4);
        ExactRational maxRational = ExactRational.of(effectiveMax);

        if (anyPending) {
            return ItemGradeResult.pendingReview(effectiveMax, Map.of(
                    "itemType", "QUESTION_SET",
                    "childCount", childResults == null ? 0 : childResults.size()));
        }

        ItemOutcome outcome;
        if (allBlank) {
            outcome = ItemOutcome.BLANK;
        } else if (maxRational.isPositive() && totalEarned.compareTo(maxRational) == 0) {
            outcome = ItemOutcome.CORRECT;
        } else if (totalEarned.isZero()) {
            outcome = ItemOutcome.WRONG;
        } else {
            outcome = ItemOutcome.PARTIAL;
        }

        return ItemGradeResult.graded(
                totalEarned,
                effectiveMax,
                outcome,
                detailWithExact(totalEarned, Map.of(
                        "itemType", "QUESTION_SET",
                        "childCount", childResults == null ? 0 : childResults.size())));
    }

    private static ItemGradeResult gradeSingleChoice(
            JsonNode body, AnswerValue answer, BigDecimal effectiveMax) {
        if (!(answer instanceof AnswerValue.SingleChoice sc)
                || sc.optionId() == null
                || sc.optionId().isBlank()) {
            return ItemGradeResult.graded(
                    ExactRational.ZERO,
                    effectiveMax,
                    ItemOutcome.BLANK,
                    detailWithExact(ExactRational.ZERO, Map.of()));
        }

        boolean matched = false;
        JsonNode options = body != null ? body.path("options") : null;
        if (options != null && options.isArray()) {
            for (JsonNode option : options) {
                String id = option.path("id").asText(null);
                if (Objects.equals(sc.optionId(), id) && option.path("correct").asBoolean(false)) {
                    matched = true;
                    break;
                }
            }
        }

        ExactRational earned = matched ? ExactRational.of(effectiveMax) : ExactRational.ZERO;
        ItemOutcome outcome = matched ? ItemOutcome.CORRECT : ItemOutcome.WRONG;
        return ItemGradeResult.graded(
                earned,
                effectiveMax,
                outcome,
                detailWithExact(earned, Map.of("selectedOptionId", sc.optionId())));
    }

    private static ItemGradeResult gradeMultipleChoice(
            JsonNode body, AnswerValue answer, BigDecimal effectiveMax, GradingPolicy policy) {
        if (!(answer instanceof AnswerValue.MultipleChoice mc) || mc.optionIds() == null) {
            return ItemGradeResult.graded(
                    ExactRational.ZERO,
                    effectiveMax,
                    ItemOutcome.BLANK,
                    detailWithExact(ExactRational.ZERO, Map.of()));
        }

        Set<String> chosen = new HashSet<>();
        for (String id : mc.optionIds()) {
            if (id != null && !id.isBlank()) {
                chosen.add(id);
            }
        }
        if (chosen.isEmpty()) {
            return ItemGradeResult.graded(
                    ExactRational.ZERO,
                    effectiveMax,
                    ItemOutcome.BLANK,
                    detailWithExact(ExactRational.ZERO, Map.of()));
        }

        Set<String> correctIds = new HashSet<>();
        JsonNode options = body != null ? body.path("options") : null;
        if (options != null && options.isArray()) {
            for (JsonNode option : options) {
                if (option.path("correct").asBoolean(false)) {
                    String id = option.path("id").asText(null);
                    if (id != null && !id.isBlank()) {
                        correctIds.add(id);
                    }
                }
            }
        }

        int correctChosen = 0;
        int wrongChosen = 0;
        for (String selectedId : chosen) {
            if (correctIds.contains(selectedId)) {
                correctChosen++;
            } else {
                wrongChosen++;
            }
        }

        String rule = body != null ? body.path("scoringRule").asText("ALL_OR_NOTHING") : "ALL_OR_NOTHING";
        ExactRational earned;
        ItemOutcome outcome;

        if ("PARTIAL".equals(rule)) {
            if (wrongChosen > 0 || correctIds.isEmpty() || correctChosen == 0) {
                earned = ExactRational.ZERO;
                outcome = ItemOutcome.WRONG;
            } else if (correctChosen == correctIds.size()) {
                earned = ExactRational.of(effectiveMax);
                outcome = ItemOutcome.CORRECT;
            } else {
                ExactRational fraction = switch (policy.multipleChoicePartialMode()) {
                    case PROPORTIONAL -> ExactRational.of(correctChosen, correctIds.size());
                    case HALF_CREDIT -> HalfCreditMultipleChoice.partialFraction(
                            correctChosen, correctIds.size(), wrongChosen);
                };
                earned = ExactRational.of(effectiveMax).multiply(fraction);
                outcome = ItemOutcome.PARTIAL;
            }
        } else {
            boolean exactMatch = !correctIds.isEmpty() && chosen.equals(correctIds);
            earned = exactMatch ? ExactRational.of(effectiveMax) : ExactRational.ZERO;
            outcome = exactMatch ? ItemOutcome.CORRECT : ItemOutcome.WRONG;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("scoringRule", rule);
        extra.put("selectedCount", chosen.size());
        extra.put("correctSelectedCount", correctChosen);
        extra.put("wrongSelectedCount", wrongChosen);
        extra.put("totalExpectedCount", correctIds.size());
        return ItemGradeResult.graded(earned, effectiveMax, outcome, detailWithExact(earned, extra));
    }

    private static ItemGradeResult gradeShortAnswer(
            JsonNode body, AnswerValue answer, BigDecimal effectiveMax) {
        String rawText = (answer instanceof AnswerValue.ShortAnswer sa) ? sa.text() : null;
        String normalizedStudent = normalizeShortAnswer(rawText);
        if (normalizedStudent.isEmpty()) {
            return ItemGradeResult.graded(
                    ExactRational.ZERO,
                    effectiveMax,
                    ItemOutcome.BLANK,
                    detailWithExact(ExactRational.ZERO, Map.of()));
        }

        boolean matched = matchesAcceptedAnswer(body != null ? body.path("answers") : null, normalizedStudent);
        ExactRational earned = matched ? ExactRational.of(effectiveMax) : ExactRational.ZERO;
        ItemOutcome outcome = matched ? ItemOutcome.CORRECT : ItemOutcome.WRONG;
        return ItemGradeResult.graded(
                earned,
                effectiveMax,
                outcome,
                detailWithExact(earned, Map.of("normalizedMatch", matched)));
    }

    private static ItemGradeResult gradeCloze(
            JsonNode body, AnswerValue answer, BigDecimal effectiveMax) {
        JsonNode blanksNode = body != null ? body.path("blanks") : null;
        Map<String, String> studentBlanks =
                (answer instanceof AnswerValue.Cloze cloze && cloze.blanks() != null)
                        ? cloze.blanks()
                        : Map.of();

        int blankCount = blanksNode != null && blanksNode.isArray() ? blanksNode.size() : 0;
        if (blankCount == 0) {
            return ItemGradeResult.graded(
                    ExactRational.ZERO,
                    effectiveMax,
                    ItemOutcome.BLANK,
                    detailWithExact(ExactRational.ZERO, Map.of("blankResults", List.of())));
        }

        ExactRational evenSplit = ExactRational.of(effectiveMax)
                .divide(ExactRational.of(blankCount, 1));
        ExactRational totalEarned = ExactRational.ZERO;
        List<Map<String, Object>> blankResults = new ArrayList<>(blankCount);
        int correctUnits = 0;
        int wrongUnits = 0;
        int blankUnits = 0;

        for (JsonNode blank : blanksNode) {
            String blankId = blank.path("id").asText("");
            ExactRational blankMax = blank.hasNonNull("points")
                    ? ExactRational.of(decimalOrDefault(blank.get("points"), BigDecimal.ZERO))
                    : evenSplit;
            String studentRaw = studentBlanks.get(blankId);
            String normalizedStudent = normalizeShortAnswer(studentRaw);

            ItemOutcome blankOutcome;
            ExactRational blankEarned;
            if (normalizedStudent.isEmpty()) {
                blankOutcome = ItemOutcome.BLANK;
                blankEarned = ExactRational.ZERO;
                blankUnits++;
            } else if (matchesAcceptedAnswer(blank.path("answers"), normalizedStudent)) {
                blankOutcome = ItemOutcome.CORRECT;
                blankEarned = blankMax;
                correctUnits++;
            } else {
                blankOutcome = ItemOutcome.WRONG;
                blankEarned = ExactRational.ZERO;
                wrongUnits++;
            }

            totalEarned = totalEarned.add(blankEarned);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", blankId);
            entry.put("outcome", blankOutcome.name());
            entry.put("pointsEarned", blankEarned.toBigDecimal(4));
            entry.put("pointsMax", blankMax.toBigDecimal(4));
            entry.put("exactFraction", blankEarned.toFractionString());
            blankResults.add(entry);
        }

        ExactRational maxRational = ExactRational.of(effectiveMax);
        ExactRational clampedEarned = clampEarned(totalEarned, maxRational);

        ItemOutcome itemOutcome;
        if (blankUnits == blankCount) {
            itemOutcome = ItemOutcome.BLANK;
        } else if (correctUnits == blankCount) {
            itemOutcome = ItemOutcome.CORRECT;
        } else if (clampedEarned.isZero()) {
            itemOutcome = ItemOutcome.WRONG;
        } else {
            itemOutcome = ItemOutcome.PARTIAL;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("blankResults", blankResults);
        extra.put("correctUnitCount", correctUnits);
        extra.put("wrongUnitCount", wrongUnits);
        extra.put("blankUnitCount", blankUnits);
        return ItemGradeResult.graded(clampedEarned, effectiveMax, itemOutcome, detailWithExact(clampedEarned, extra));
    }

    private static ItemGradeResult gradeTrueFalse(
            JsonNode body, AnswerValue answer, BigDecimal effectiveMax) {
        JsonNode statementsNode = body != null ? body.path("statements") : null;
        int count = statementsNode != null && statementsNode.isArray() ? statementsNode.size() : 0;
        Map<String, Boolean> studentStatements =
                (answer instanceof AnswerValue.TrueFalse tf && tf.statements() != null)
                        ? tf.statements()
                        : Map.of();

        if (count == 0) {
            return ItemGradeResult.graded(
                    ExactRational.ZERO,
                    effectiveMax,
                    ItemOutcome.BLANK,
                    detailWithExact(ExactRational.ZERO, Map.of()));
        }

        boolean anyAnswered = false;
        int matchedCount = 0;
        ExactRational perStatementSum = ExactRational.ZERO;
        ExactRational evenSplit = ExactRational.of(effectiveMax).divide(ExactRational.of(count, 1));
        List<Map<String, Object>> statementResults = new ArrayList<>(count);

        for (JsonNode stmt : statementsNode) {
            String stmtId = stmt.path("id").asText("");
            boolean expected = stmt.path("answer").asBoolean(false);
            Boolean given = studentStatements.get(stmtId);
            if (given != null) {
                anyAnswered = true;
            }
            boolean matched = given != null && given.booleanValue() == expected;
            if (matched) {
                matchedCount++;
            }
            ExactRational stmtMax = stmt.hasNonNull("points")
                    ? ExactRational.of(decimalOrDefault(stmt.get("points"), BigDecimal.ZERO))
                    : evenSplit;
            if (matched) {
                perStatementSum = perStatementSum.add(stmtMax);
            }

            Map<String, Object> stmtEntry = new LinkedHashMap<>();
            stmtEntry.put("id", stmtId);
            stmtEntry.put("answered", given != null);
            stmtEntry.put("matched", matched);
            statementResults.add(stmtEntry);
        }

        if (!anyAnswered) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("statementResults", statementResults);
            extra.put("matchedCount", 0);
            extra.put("statementCount", count);
            return ItemGradeResult.graded(
                    ExactRational.ZERO,
                    effectiveMax,
                    ItemOutcome.BLANK,
                    detailWithExact(ExactRational.ZERO, extra));
        }

        String method = body.path("scoringMethod").asText("PER_STATEMENT");
        ExactRational earned;
        if ("COUNT_TABLE".equals(method)) {
            List<BigDecimal> table = resolveCountTable(body.path("scoreTable"), count);
            if (matchedCount == 0) {
                earned = ExactRational.ZERO;
            } else if (matchedCount <= table.size()) {
                earned = ExactRational.of(table.get(matchedCount - 1));
            } else {
                earned = ExactRational.of(table.getLast());
            }
        } else {
            earned = perStatementSum;
        }

        ExactRational maxRational = ExactRational.of(effectiveMax);
        ExactRational clampedEarned = clampEarned(earned, maxRational);
        ItemOutcome outcome;
        if (matchedCount == count && (maxRational.isZero() || clampedEarned.compareTo(maxRational) >= 0)) {
            outcome = ItemOutcome.CORRECT;
        } else if (clampedEarned.isZero()) {
            outcome = ItemOutcome.WRONG;
        } else {
            outcome = ItemOutcome.PARTIAL;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("scoringMethod", method);
        extra.put("statementResults", statementResults);
        extra.put("matchedCount", matchedCount);
        extra.put("statementCount", count);
        return ItemGradeResult.graded(clampedEarned, effectiveMax, outcome, detailWithExact(clampedEarned, extra));
    }

    private static List<BigDecimal> resolveCountTable(JsonNode scoreTableNode, int statementCount) {
        if (scoreTableNode != null && scoreTableNode.isArray() && !scoreTableNode.isEmpty()) {
            List<BigDecimal> table = new ArrayList<>(scoreTableNode.size());
            for (JsonNode item : scoreTableNode) {
                table.add(decimalOrDefault(item, BigDecimal.ZERO));
            }
            return table;
        }
        if (statementCount == 4) {
            return DEFAULT_THPTQG_TF_TABLE;
        }
        throw new IllegalStateException(
                "Missing scoreTable for COUNT_TABLE TRUE_FALSE question with " + statementCount + " statements");
    }

    private static ItemGradeResult gradeMatching(
            JsonNode body, AnswerValue answer, BigDecimal effectiveMax) {
        JsonNode expectedPairs = body != null ? body.path("pairs") : null;
        int pairCount = expectedPairs != null && expectedPairs.isArray() ? expectedPairs.size() : 0;
        Map<String, String> studentPairs =
                (answer instanceof AnswerValue.Matching matching && matching.pairs() != null)
                        ? matching.pairs()
                        : Map.of();

        Set<String> declaredLeftIds = new HashSet<>();
        if (expectedPairs != null && expectedPairs.isArray()) {
            for (JsonNode pair : expectedPairs) {
                String leftId = pair.path("leftId").asText("");
                if (!leftId.isBlank()) {
                    declaredLeftIds.add(leftId);
                }
            }
        }
        Map<String, String> relevantStudentPairs = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : studentPairs.entrySet()) {
            if (entry.getKey() != null && declaredLeftIds.contains(entry.getKey())) {
                relevantStudentPairs.put(entry.getKey(), entry.getValue());
            }
        }

        boolean anyAnswered = false;
        for (String val : relevantStudentPairs.values()) {
            if (val != null && !val.isBlank()) {
                anyAnswered = true;
                break;
            }
        }
        if (!anyAnswered || pairCount == 0) {
            return ItemGradeResult.graded(
                    ExactRational.ZERO,
                    effectiveMax,
                    ItemOutcome.BLANK,
                    detailWithExact(ExactRational.ZERO, Map.of()));
        }

        boolean reuseRight = body != null && body.path("reuseRight").asBoolean(false);
        Set<String> duplicatedRights = findDuplicateRights(relevantStudentPairs);
        ExactRational evenSplit = ExactRational.of(effectiveMax).divide(ExactRational.of(pairCount, 1));
        ExactRational totalEarned = ExactRational.ZERO;
        int matchedCount = 0;
        List<Map<String, Object>> pairResults = new ArrayList<>(pairCount);

        for (JsonNode pair : expectedPairs) {
            String leftId = pair.path("leftId").asText("");
            String expectedRightId = pair.path("rightId").asText("");
            ExactRational pairMax = pair.hasNonNull("points")
                    ? ExactRational.of(decimalOrDefault(pair.get("points"), BigDecimal.ZERO))
                    : evenSplit;

            String givenRightId = relevantStudentPairs.get(leftId);
            boolean disallowedDuplicate = !reuseRight && givenRightId != null && duplicatedRights.contains(givenRightId);
            boolean matched = !disallowedDuplicate
                    && givenRightId != null
                    && Objects.equals(expectedRightId, givenRightId);

            if (matched) {
                matchedCount++;
                totalEarned = totalEarned.add(pairMax);
            }

            Map<String, Object> pairEntry = new LinkedHashMap<>();
            pairEntry.put("leftId", leftId);
            pairEntry.put("matched", matched);
            pairEntry.put("pointsEarned", (matched ? pairMax : ExactRational.ZERO).toBigDecimal(4));
            pairResults.add(pairEntry);
        }

        ExactRational maxRational = ExactRational.of(effectiveMax);
        ExactRational clampedEarned = clampEarned(totalEarned, maxRational);
        ItemOutcome outcome;
        if (matchedCount == pairCount && (maxRational.isZero() || clampedEarned.compareTo(maxRational) >= 0)) {
            outcome = ItemOutcome.CORRECT;
        } else if (clampedEarned.isZero()) {
            outcome = ItemOutcome.WRONG;
        } else {
            outcome = ItemOutcome.PARTIAL;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("reuseRight", reuseRight);
        extra.put("matchedCount", matchedCount);
        extra.put("pairCount", pairCount);
        extra.put("pairResults", pairResults);
        return ItemGradeResult.graded(clampedEarned, effectiveMax, outcome, detailWithExact(clampedEarned, extra));
    }

    private static ExactRational clampEarned(ExactRational earned, ExactRational maxRational) {
        ExactRational upper = maxRational.compareTo(ExactRational.ZERO) < 0 ? ExactRational.ZERO : maxRational;
        if (earned.compareTo(ExactRational.ZERO) < 0) {
            return ExactRational.ZERO;
        }
        if (earned.compareTo(upper) > 0) {
            return upper;
        }
        return earned;
    }

    private static Set<String> findDuplicateRights(Map<String, String> studentPairs) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (String rightId : studentPairs.values()) {
            if (rightId != null && !rightId.isBlank() && !seen.add(rightId)) {
                duplicates.add(rightId);
            }
        }
        return duplicates;
    }

    private static ItemGradeResult gradeEssay(AnswerValue answer, BigDecimal effectiveMax) {
        String html = (answer instanceof AnswerValue.Essay essay) ? essay.html() : null;
        if (isBlankEssayHtml(html)) {
            return ItemGradeResult.graded(
                    ExactRational.ZERO,
                    effectiveMax,
                    ItemOutcome.BLANK,
                    detailWithExact(ExactRational.ZERO, Map.of("blankEssay", true)));
        }
        Integer wordCount = (answer instanceof AnswerValue.Essay essay) ? essay.wordCount() : null;
        Map<String, Object> detail = new LinkedHashMap<>();
        if (wordCount != null) {
            detail.put("wordCount", wordCount);
        }
        return ItemGradeResult.pendingReview(effectiveMax, detail);
    }

    public static boolean isBlankEssayHtml(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        String text = doc.body().text();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isWhitespace(cp)
                    && !Character.isSpaceChar(cp)
                    && Character.getType(cp) != Character.FORMAT) {
                return false;
            }
        }
        return true;
    }

    /**
     * Normalizes short-answer / cloze inputs per BE §8 (B7): trims Unicode whitespace, collapses
     * internal whitespace to a single ASCII space, applies Unicode NFC, and lowercases with ROOT.
     * Punctuation ({@code ,} vs {@code .}) and fractions ({@code 1/2} vs {@code 0.5}) are NOT rewritten.
     */
    public static String normalizeShortAnswer(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String collapsed = trimAndCollapseUnicodeWhitespace(raw);
        if (collapsed.isEmpty()) {
            return "";
        }
        return Normalizer.normalize(collapsed, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static String trimAndCollapseUnicodeWhitespace(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        boolean pendingSpace = false;
        int i = 0;
        while (i < input.length()) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                if (!sb.isEmpty()) {
                    pendingSpace = true;
                }
            } else {
                if (pendingSpace) {
                    sb.append(' ');
                    pendingSpace = false;
                }
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    private static boolean matchesAcceptedAnswer(JsonNode answersNode, String normalizedStudent) {
        if (answersNode == null || !answersNode.isArray()) {
            return false;
        }
        for (JsonNode accepted : answersNode) {
            if (!accepted.isTextual()) {
                continue;
            }
            String normAccepted = normalizeShortAnswer(accepted.asText());
            if (!normAccepted.isEmpty() && normAccepted.equals(normalizedStudent)) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode unwrapBody(JsonNode snapshotOrBody) {
        if (snapshotOrBody == null || snapshotOrBody.isMissingNode() || snapshotOrBody.isNull()) {
            return null;
        }
        JsonNode body = snapshotOrBody.path("body");
        if (body.isObject()) {
            return body;
        }
        return snapshotOrBody;
    }

    private static BigDecimal decimalOrDefault(JsonNode node, BigDecimal fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Map<String, Object> detailWithExact(
            ExactRational earned, Map<String, Object> extra) {
        Map<String, Object> map = new HashMap<>(extra);
        map.put("exactFraction", earned.toFractionString());
        return Map.copyOf(map);
    }
}
