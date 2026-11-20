package vn.unicom.thi360.commerce.entitlement.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttemptState;
import vn.unicom.thi360.commerce.storefront.api.ProductCard;

/**
 * {@code GET /me/exams} (TM 12): the cards, their buttons and their numbers. Plan CM-4b AC-12 to AC-21, AC-23, AC-24.
 * The exam module, the order lookup and (when a test asks) the attempt module are fakes; the entitlements are real, granted
 * through {@code EntitlementGrantService}.
 */
class MyExamsApiIT extends AbstractExamAccessIT {

    @Autowired private ObjectProvider<AttemptStatusProvider> attemptModule;

    @Test
    void discontinuedWithAttemptsCanStartOnly() throws Exception {
        UUID userId = activeAccount();
        Product product = discontinued();
        examPort.serve(ServingExams.serving(product.examId(), product.code(), "DISCONTINUED",
                ServingExams.fixedPath("default", 44, 66)));
        grant(userId, product.examId(), 2);

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("saleState").asText()).isEqualTo("DISCONTINUED");
        assertThat(card.get("state").asText()).isEqualTo("CAN_START");
        assertThat(actions(card)).containsExactly("START").doesNotContain("BUY_MORE", "REBUY");
        // The projection says 50 questions and 75 minutes; the frozen serving version says 44 and 66.
        assertThat(card.get("questionCount").get("min").asInt()).as("the last serving version is still read").isEqualTo(44);
        assertThat(card.get("questionCount").get("max").asInt()).isEqualTo(44);
        assertThat(card.get("durationMinutes").get("min").asInt()).isEqualTo(66);
        assertThat(card.get("durationMinutes").get("max").asInt()).isEqualTo(66);
    }

    @Test
    void lastRemainingAttemptShowsStart() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        useAttempt(userId, grant(userId, product.examId(), 2).entitlementId());

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("attemptsLeft").asInt()).as("the boundary: exactly one attempt left").isEqualTo(1);
        assertThat(card.get("state").asText()).isEqualTo("CAN_START");
        assertThat(actions(card)).containsExactly("START", "HISTORY", "BUY_MORE");
    }

    @Test
    void fullyRevokedOnSaleIsExhaustedAndCanRebuy() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        grantService.revokeUnused(grant(userId, product.examId(), 2).grantId());

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("attemptsLeft").asInt()).isZero();
        assertThat(card.get("attemptsTotal").asInt()).as("granted minus revoked").isZero();
        assertThat(card.get("state").asText()).isEqualTo("EXHAUSTED");
        assertThat(actions(card)).containsExactly("HISTORY", "REBUY");
    }

    @Test
    void exhaustedOnSaleCanRebuy() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        useAttempt(userId, grant(userId, product.examId(), 1).entitlementId());

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("state").asText()).isEqualTo("EXHAUSTED");
        assertThat(card.get("attemptsLeft").asInt()).isZero();
        assertThat(actions(card)).contains("REBUY").doesNotContain("START", "BUY_MORE", "CONTINUE");
    }

    @Test
    void exhaustedDiscontinuedKeepsHistory() throws Exception {
        UUID userId = activeAccount();
        Product product = discontinued();
        useAttempt(userId, grant(userId, product.examId(), 1).entitlementId());

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("state").asText()).isEqualTo("EXHAUSTED");
        assertThat(actions(card)).containsExactly("HISTORY").doesNotContain("REBUY");
    }

    @Test
    void fullyRevokedUnusedDiscontinuedKeepsHistory() throws Exception {
        UUID userId = activeAccount();
        Product product = discontinued();
        grantService.revokeUnused(grant(userId, product.examId(), 2).grantId());

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("state").asText()).isEqualTo("EXHAUSTED");
        assertThat(card.get("attemptsLeft").asInt()).isZero();
        assertThat(actions(card)).containsExactly("HISTORY").doesNotContain("REBUY", "BUY_MORE");
    }

    @Test
    void onSaleWithAttemptsCanBuyMore() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        grant(userId, product.examId(), 3);

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("state").asText()).isEqualTo("CAN_START");
        assertThat(actions(card)).containsExactly("START", "BUY_MORE");
    }

    @Test
    void continueComesFirst() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        var line = grant(userId, product.examId(), 1);
        UUID attemptId = reserveAttempt(userId, line.entitlementId());
        attempts().open(userId, line.entitlementId(), attemptId, OpenAttemptState.STARTING);

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("attemptsLeft").asInt()).as("the only attempt is held by the open one").isZero();
        assertThat(card.get("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(actions(card)).as("an open attempt at zero left on sale: continue, no purchase button")
                .containsExactly("CONTINUE", "HISTORY");
        assertThat(card.get("activeAttemptId").asText()).isEqualTo(attemptId.toString());
        assertThat(card.get("activeAttemptState").asText()).isEqualTo("STARTING");
    }

    @Test
    void resultActionsAreAdded() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        var line = grant(userId, product.examId(), 3);
        useAttempt(userId, line.entitlementId());
        UUID resultAttempt = UUID.randomUUID();
        attempts().result(userId, line.entitlementId(), resultAttempt);

        JsonNode card = only(myExams(learner(userId)));

        assertThat(actions(card)).containsExactly("START", "VIEW_RESULT", "HISTORY", "BUY_MORE");
        assertThat(card.get("resultAttemptId").asText()).isEqualTo(resultAttempt.toString());
    }

    @Test
    void paidOrderShowsCheckingCard() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        pendingOrders.add(userId, "ORD-PAID01", product.examId());

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("state").asText()).isEqualTo("CHECKING");
        assertThat(card.get("pendingOrderCode").asText()).isEqualTo("ORD-PAID01");
        assertThat(card.get("hasPendingFulfillment").asBoolean()).isTrue();
        assertThat(card.get("entitlementId").isNull()).isTrue();
        assertThat(card.get("code").asText()).isEqualTo(product.code());
        assertThat(actions(card)).containsExactly("CHECKING").doesNotContain("REBUY", "BUY_MORE", "START");
    }

    @Test
    void everyStuckTopUpGetsItsOwnCheckingCardBesideTheEntitlementCard() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        var line = grant(userId, product.examId(), 2);
        pendingOrders.add(userId, "ORD-TOPUP1", product.examId());
        pendingOrders.add(userId, "ORD-TOPUP2", product.examId());

        JsonNode items = myExams(learner(userId)).get("items");

        assertThat(items).as("two paid orders and the entitlement: three cards").hasSize(3);
        assertThat(items.get(0).get("state").asText()).isEqualTo("CHECKING");
        assertThat(items.get(0).get("pendingOrderCode").asText()).isEqualTo("ORD-TOPUP1");
        assertThat(items.get(1).get("state").asText()).isEqualTo("CHECKING");
        assertThat(items.get(1).get("pendingOrderCode").asText()).isEqualTo("ORD-TOPUP2");
        for (int i = 0; i < 2; i++) {
            assertThat(items.get(i).get("code").asText()).isEqualTo(product.code());
            assertThat(items.get(i).get("entitlementId").isNull()).as("a paid order has no entitlement yet").isTrue();
            assertThat(actions(items.get(i))).containsExactly("CHECKING");
        }
        JsonNode entitlementCard = items.get(2);
        assertThat(entitlementCard.get("entitlementId").asText()).isEqualTo(line.entitlementId().toString());
        assertThat(entitlementCard.get("state").asText()).isEqualTo("CAN_START");
        assertThat(entitlementCard.get("hasPendingFulfillment").asBoolean()).isTrue();
        assertThat(entitlementCard.get("pendingOrderCode").asText()).isEqualTo("ORD-TOPUP1");
        assertThat(actions(entitlementCard)).as("no second purchase while an order is pending")
                .containsExactly("START");
    }

    @Test
    void paidOrderOnExhaustedExamIsTheOnlyCheckingCard() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        var line = grant(userId, product.examId(), 1);
        useAttempt(userId, line.entitlementId());
        pendingOrders.add(userId, "ORD-REBUY1", product.examId());

        JsonNode items = myExams(learner(userId)).get("items");

        assertThat(items).as("the paid order and the entitlement: two cards").hasSize(2);
        JsonNode orderCard = items.get(0);
        assertThat(orderCard.get("state").asText()).isEqualTo("CHECKING");
        assertThat(orderCard.get("pendingOrderCode").asText()).isEqualTo("ORD-REBUY1");
        assertThat(actions(orderCard)).containsExactly("CHECKING");
        JsonNode entitlementCard = items.get(1);
        assertThat(entitlementCard.get("state").asText())
                .as("the entitlement keeps its own exhausted state; the order alone is CHECKING")
                .isEqualTo("EXHAUSTED");
        assertThat(entitlementCard.get("hasPendingFulfillment").asBoolean()).isTrue();
        assertThat(entitlementCard.get("pendingOrderCode").asText()).isEqualTo("ORD-REBUY1");
        assertThat(actions(entitlementCard)).as("history, but no second purchase while the order is pending")
                .containsExactly("HISTORY");
        assertThat(items.findValuesAsText("state")).as("exactly one card with state CHECKING")
                .containsExactly("CHECKING", "EXHAUSTED");

        // TM 10.2 query precedence is unchanged: the product is CHECKING while the paid order is unfulfilled.
        assertThat(only(queryAccess(learner(userId), product.code())).get("state").asText())
                .isEqualTo("CHECKING");
    }

    @Test
    void attemptsComeFromGrantedEntitlement() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        grant(userId, product.examId(), 2);

        // The product is published again with 5 attempts: what was bought stays what was bought.
        jdbc.update("UPDATE commerce.storefront_product SET attempts_granted = 5, version_no = 2,"
                + " card = jsonb_set(card, '{attemptsGranted}', '5') WHERE exam_id = ?", product.examId());

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("attemptsLeft").asInt()).isEqualTo(2);
        assertThat(card.get("attemptsTotal").asInt()).isEqualTo(2);
    }

    @Test
    void questionCountFollowsPaths() throws Exception {
        UUID bothPaths = activeAccount();
        UUID onePath = activeAccount();
        Product product = onSale();
        examPort.serve(ServingExams.serving(product.examId(), product.code(), "PUBLISHED",
                ServingExams.optionPath("a", "A00", 48, 60), ServingExams.optionPath("b", "B00", 50, 75)));
        grant(bothPaths, product.examId(), 1, List.of("A00", "B00"), 1);
        grant(onePath, product.examId(), 1, List.of("A00"), 1);

        JsonNode both = only(myExams(learner(bothPaths)));
        JsonNode one = only(myExams(learner(onePath)));

        assertThat(both.get("questionCount").get("min").asInt()).isEqualTo(48);
        assertThat(both.get("questionCount").get("max").asInt()).as("paths of different size give a range").isEqualTo(50);
        assertThat(both.get("durationMinutes").get("min").asInt()).isEqualTo(60);
        assertThat(both.get("durationMinutes").get("max").asInt()).isEqualTo(75);
        assertThat(one.get("questionCount").get("min").asInt()).isEqualTo(48);
        assertThat(one.get("questionCount").get("max").asInt()).as("only the bought path counts").isEqualTo(48);
        assertThat(one.get("durationMinutes").get("max").asInt()).isEqualTo(60);
    }

    @Test
    void rangeIsOverTheGrantsWithUsableAttemptsOnly() throws Exception {
        UUID revokedFirst = activeAccount();
        UUID depletedFirst = activeAccount();
        UUID allDepleted = activeAccount();
        Product product = onSale();
        examPort.serve(ServingExams.serving(product.examId(), product.code(), "PUBLISHED",
                ServingExams.optionPath("a", "A00", 48, 60), ServingExams.optionPath("b", "B00", 50, 75)));
        // Old grant A (path a) is gone, the new grant B (path b) still has an attempt: only B's path counts.
        grantService.revokeUnused(grant(revokedFirst, product.examId(), 1, List.of("A00"), 1).grantId());
        grant(revokedFirst, product.examId(), 1, List.of("B00"), 1);
        useAttempt(depletedFirst, grant(depletedFirst, product.examId(), 1, List.of("A00"), 1).entitlementId());
        grant(depletedFirst, product.examId(), 1, List.of("B00"), 1);
        // Nothing is left on either grant: the newest grant's options are shown.
        useAttempt(allDepleted, grant(allDepleted, product.examId(), 1, List.of("A00"), 1).entitlementId());
        useAttempt(allDepleted, grant(allDepleted, product.examId(), 1, List.of("B00"), 1).entitlementId());

        for (UUID userId : List.of(revokedFirst, depletedFirst, allDepleted)) {
            JsonNode card = only(myExams(learner(userId)));
            assertThat(card.get("questionCount").get("min").asInt()).isEqualTo(50);
            assertThat(card.get("questionCount").get("max").asInt()).isEqualTo(50);
            assertThat(card.get("durationMinutes").get("min").asInt()).isEqualTo(75);
            assertThat(card.get("durationMinutes").get("max").asInt()).isEqualTo(75);
        }
    }

    @Test
    void rangeFollowsTheOldestUsableGrantOnly() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        examPort.serve(ServingExams.serving(product.examId(), product.code(), "PUBLISHED",
                ServingExams.optionPath("a", "A00", 48, 60), ServingExams.optionPath("b", "B00", 50, 75)));
        // Older grant A (path a) and newer grant B (path b) both still have an attempt: prepare takes A's next attempt
        // first (E21 FIFO), so the card ranges over A's paths only, never the union.
        grant(userId, product.examId(), 1, List.of("A00"), 1);
        grant(userId, product.examId(), 1, List.of("B00"), 1);

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("attemptsLeft").asInt()).isEqualTo(2);
        assertThat(card.get("questionCount").get("min").asInt()).isEqualTo(48);
        assertThat(card.get("questionCount").get("max").asInt())
                .as("grant B's 50 is not in the next attempt's scope").isEqualTo(48);
        assertThat(card.get("durationMinutes").get("min").asInt()).isEqualTo(60);
        assertThat(card.get("durationMinutes").get("max").asInt()).isEqualTo(60);
    }

    @Test
    void fifoGrantWithoutServingPathGivesNoRange() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        // The path grant A bought was removed on republication; grant B still has an attempt on the served path.
        examPort.serve(ServingExams.serving(product.examId(), product.code(), "PUBLISHED",
                ServingExams.optionPath("b", "B00", 50, 75)));
        grant(userId, product.examId(), 1, List.of("A00"), 1);
        grant(userId, product.examId(), 1, List.of("B00"), 1);

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("questionCount").isNull())
                .as("the next attempt is taken from grant A, which has no serving path; B's metrics do not show")
                .isTrue();
        assertThat(card.get("durationMinutes").isNull()).isTrue();
        assertThat(card.get("state").asText()).isEqualTo("CAN_START");
    }

    @Test
    void noEligibleServingPathGivesNoRangeNotTheCatalogOne() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        // The exam was republished: the path A00 bought is gone, only B00 is served now.
        examPort.serve(ServingExams.serving(product.examId(), product.code(), "PUBLISHED",
                ServingExams.optionPath("b", "B00", 50, 75)));
        grant(userId, product.examId(), 1, List.of("A00"), 1);

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("questionCount").isNull()).as("the projection's 50 is not what A00 can be served").isTrue();
        assertThat(card.get("durationMinutes").isNull()).isTrue();
        assertThat(card.get("state").asText()).isEqualTo("CAN_START");
    }

    @Test
    void emptyListIsOk() throws Exception {
        UUID userId = activeAccount();

        JsonNode response = myExams(learner(userId));

        assertThat(response.get("items").isArray()).isTrue();
        assertThat(response.get("items")).isEmpty();
    }

    @Test
    void cardsAreOrderedCheckingFirstThenNewestEntitlement() throws Exception {
        UUID userId = activeAccount();
        Product older = onSale();
        Product newer = onSale();
        Product waiting = onSale();
        grant(userId, older.examId(), 1);
        grant(userId, newer.examId(), 1);
        pendingOrders.add(userId, "ORD-WAIT01", waiting.examId());

        JsonNode items = myExams(learner(userId)).get("items");

        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("code").asText()).isEqualTo(waiting.code());
        assertThat(items.get(1).get("code").asText()).isEqualTo(newer.code());
        assertThat(items.get(2).get("code").asText()).isEqualTo(older.code());
    }

    @Test
    void worksWithDefaultAttemptStatusProvider() throws Exception {
        assertThat(AttemptStatusProvider.EMPTY.findOpenAttempt(UUID.randomUUID(), UUID.randomUUID()))
                .isEmpty();
        assertThat(AttemptStatusProvider.EMPTY.findLatestResultAttempt(UUID.randomUUID(), UUID.randomUUID()))
                .isEmpty();
        assertThat(attemptModule.getIfAvailable()).isNotNull();
        UUID userId = activeAccount();
        Product product = onSale();
        grant(userId, product.examId(), 2);

        JsonNode card = only(myExams(learner(userId)));
        JsonNode access = only(queryAccess(learner(userId), product.code()));

        assertThat(card.get("state").asText()).isEqualTo("CAN_START");
        assertThat(card.get("activeAttemptId").isNull()).isTrue();
        assertThat(card.get("resultAttemptId").isNull()).isTrue();
        assertThat(actions(card)).containsExactly("START", "BUY_MORE");
        assertThat(access.get("state").asText()).isEqualTo("CAN_START");
        assertThat(access.get("activeAttemptId").isNull()).isTrue();
    }

    @Test
    void cardCarriesSpecFields() throws Exception {
        UUID userId = activeAccount();
        UUID examId = UUID.randomUUID();
        String code = "PART" + examId.toString().replace("-", "").substring(0, 8).toUpperCase();
        ProductCard card = new ProductCard(code, "Phan Toan " + code, null, "/api/v1/public/storefront/covers/abc",
                new vn.unicom.thi360.commerce.storefront.api.ExamKindRef("HSA", "Kỳ thi HSA", "HSA exam"), "PART",
                name("Toán học", "Mathematics"), "REVIEW", new vn.unicom.thi360.commerce.storefront.api.IntRange(30, 30),
                new vn.unicom.thi360.commerce.storefront.api.IntRange(45, 45), 3, 99_000L, 99_000L, null, 1);
        Product product = insertProduct(examId, code, "ON_SALE", card);
        useAttempt(userId, grant(userId, product.examId(), 3).entitlementId());

        JsonNode item = only(myExams(learner(userId)));

        assertThat(fieldNames(item)).containsExactlyInAnyOrder("entitlementId", "code", "name", "examKind", "format",
                "scopeLabel", "buildMode", "coverUrl", "questionCount", "durationMinutes", "attemptsLeft",
                "attemptsTotal", "saleState", "state", "activeAttemptId", "activeAttemptState", "resultAttemptId",
                "hasPendingFulfillment", "pendingOrderCode", "actions");
        assertThat(item.get("code").asText()).isEqualTo(code);
        assertThat(item.get("name").asText()).isEqualTo("Phan Toan " + code);
        assertThat(item.get("examKind").get("code").asText()).isEqualTo("HSA");
        assertThat(item.get("examKind").get("nameVi").asText()).isEqualTo("Kỳ thi HSA");
        assertThat(item.get("examKind").get("nameEn").asText()).isEqualTo("HSA exam");
        assertThat(item.get("format").asText()).isEqualTo("PART");
        assertThat(item.get("scopeLabel").get("nameVi").asText()).isEqualTo("Toán học");
        assertThat(item.get("scopeLabel").get("nameEn").asText()).isEqualTo("Mathematics");
        assertThat(item.get("buildMode").asText()).isEqualTo("REVIEW");
        assertThat(item.get("coverUrl").asText()).isEqualTo("/api/v1/public/storefront/covers/abc");
        assertThat(item.get("questionCount").get("min").asInt()).as("the card of the projection, no serving paths").isEqualTo(30);
        assertThat(item.get("durationMinutes").get("max").asInt()).isEqualTo(45);
        assertThat(item.get("attemptsLeft").asInt()).isEqualTo(2);
        assertThat(item.get("saleState").asText()).isEqualTo("ON_SALE");
        assertThat(item.get("state").asText()).isEqualTo("CAN_START");
    }

    private static List<String> actions(JsonNode card) {
        List<String> names = new ArrayList<>();
        card.get("actions").forEach(action -> names.add(action.asText()));
        return names;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
