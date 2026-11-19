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
                ServingExams.fixedPath("default", 50, 75)));
        grant(userId, product.examId(), 2);

        JsonNode card = only(myExams(learner(userId)));

        assertThat(card.get("saleState").asText()).isEqualTo("DISCONTINUED");
        assertThat(card.get("state").asText()).isEqualTo("CAN_START");
        assertThat(actions(card)).containsExactly("START").doesNotContain("BUY_MORE", "REBUY");
        assertThat(card.get("questionCount").get("min").asInt()).as("the last serving version is still read").isEqualTo(50);
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
        assertThat(actions(entitlementCard)).containsExactly("START", "BUY_MORE");
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
        assertThat(attemptModule.getIfAvailable()).as("no attempt module in this context").isNull();
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
