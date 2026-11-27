package vn.unicom.thi360.feature.myexams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.MyExamCard

class MyExamsLayoutTest {

    private val bundleX = MyExamsTestFixtures.makeBundle("X", "BUNDLE-X", "Bộ đề X")
    private val bundleY = MyExamsTestFixtures.makeBundle("Y", "BUNDLE-Y", "Bộ đề Y")

    @Test
    fun groupsCheckingBundlesAndStandalonePreservingBackendOrder() {
        val cardA = MyExamsTestFixtures.makeCard(
            code = "A",
            entitlementId = null,
            state = MyExamCard.State.CHECKING,
            pendingOrderCode = "DH001",
            hasPendingFulfillment = true,
            actions = listOf(MyExamCard.Actions.CHECKING),
        )
        val cardB = MyExamsTestFixtures.makeCard(code = "B", bundles = listOf(bundleX))
        val cardC = MyExamsTestFixtures.makeCard(code = "C", bundles = emptyList())
        val cardD = MyExamsTestFixtures.makeCard(code = "D", bundles = listOf(bundleY))
        val cardE = MyExamsTestFixtures.makeCard(code = "E", bundles = listOf(bundleX))

        val grouped = MyExamsLayout.group(listOf(cardA, cardB, cardC, cardD, cardE))

        assertEquals(5, grouped.count)
        assertEquals(listOf("A"), grouped.checking.map { it.code })
        assertEquals(listOf("BUNDLE-X", "BUNDLE-Y"), grouped.bundleGroups.map { it.ref.code })
        assertEquals(listOf("B", "E"), grouped.bundleGroups[0].cards.map { it.code })
        assertEquals(listOf("D"), grouped.bundleGroups[1].cards.map { it.code })
        assertEquals(listOf("C"), grouped.standalone.map { it.code })
    }

    @Test
    fun cardWithoutBundlesProducesNoBundleGroups() {
        val cardB = MyExamsTestFixtures.makeCard(code = "B", bundles = emptyList())
        val cardC = MyExamsTestFixtures.makeCard(code = "C", bundles = emptyList())

        val grouped = MyExamsLayout.group(listOf(cardB, cardC))

        assertEquals(2, grouped.count)
        assertTrue(grouped.checking.isEmpty())
        assertTrue(grouped.bundleGroups.isEmpty())
        assertEquals(listOf("B", "C"), grouped.standalone.map { it.code })
    }

    @Test
    fun cardInMultipleBundlesAppearsOnceInPrimaryAndCrossReferencesSecondary() {
        val cardF = MyExamsTestFixtures.makeCard(
            code = "F",
            name = "Bài thi F",
            bundles = listOf(bundleX, bundleY, bundleX, bundleY),
        )

        val grouped = MyExamsLayout.group(listOf(cardF))

        assertEquals(1, grouped.count)
        assertTrue(grouped.standalone.isEmpty())
        assertEquals(listOf("BUNDLE-X", "BUNDLE-Y"), grouped.bundleGroups.map { it.ref.code })

        val groupX = grouped.bundleGroups[0]
        assertEquals(listOf("F"), groupX.cards.map { it.code })
        assertTrue(groupX.crossRefs.isEmpty())
        assertEquals(1, groupX.totalItemCount)

        val groupY = grouped.bundleGroups[1]
        assertTrue(groupY.cards.isEmpty())
        assertEquals(1, groupY.crossRefs.size)
        assertEquals("F", groupY.crossRefs[0].examCode)
        assertEquals("Bài thi F", groupY.crossRefs[0].examName)
        assertEquals(bundleX.bundleId, groupY.crossRefs[0].primaryBundle.bundleId)
        assertEquals("BUNDLE-Y", groupY.ref.code)
        assertEquals(1, groupY.totalItemCount)
    }

    @Test
    fun checkingOnlyListHasNoBundleGroupsOrStandalone() {
        val cardA = MyExamsTestFixtures.makeCard(
            code = "A",
            entitlementId = null,
            state = MyExamCard.State.CHECKING,
            pendingOrderCode = "DH001",
            hasPendingFulfillment = true,
            actions = listOf(MyExamCard.Actions.CHECKING),
        )

        val grouped = MyExamsLayout.group(listOf(cardA))

        assertEquals(1, grouped.count)
        assertEquals(listOf("A"), grouped.checking.map { it.code })
        assertTrue(grouped.bundleGroups.isEmpty())
        assertTrue(grouped.standalone.isEmpty())
    }

    @Test
    fun buildUniqueCheckingKeysDisambiguatesDuplicateNullOrderKeys() {
        val first = MyExamsTestFixtures.makeCard(
            code = "CHK-DUP",
            entitlementId = null,
            state = MyExamCard.State.CHECKING,
            pendingOrderCode = null,
        )
        val second = first.copy(pendingOrderCode = "  ")
        val withOrder = first.copy(pendingOrderCode = "DH999")

        assertEquals(
            listOf("checking::CHK-DUP", "checking::CHK-DUP#dup1", "checking:DH999:CHK-DUP"),
            buildUniqueCheckingKeys(listOf(first, second, withOrder)),
        )
    }
}
