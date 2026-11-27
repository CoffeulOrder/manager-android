package com.ormo.manager.domain

import com.ormo.manager.data.InMemoryMenuRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionSaleTest {

    private val linked = listOf("아메리카노", "카페라떼")
    private val menus = linked.map { MenuItem(it, 4_000, MenuCategory.COFFEE, optionGroups = listOf("원두")) }
    private val group = OptionGroup(
        "원두", required = true,
        options = listOf(MenuOption("오리지널", 0), MenuOption("블렌드", 0)),
    )

    private fun repository() = InMemoryMenuRepository(menus, listOf(group))

    private fun InMemoryMenuRepository.option(name: String) =
        optionGroups.value.single().options.single { it.name == name }

    @Test
    fun menuSwitches_makePartlySoldOut_thenMergeIntoSoldOut() {
        val repository = repository()

        repository.setOptionSoldOutForMenu("블렌드", "카페라떼", true)
        assertEquals(OptionSale.PARTLY_SOLD_OUT, repository.option("블렌드").saleState(linked))
        assertTrue(repository.isOptionSoldOutFor("블렌드", "카페라떼"))
        assertFalse(repository.isOptionSoldOutFor("블렌드", "아메리카노"))

        repository.setOptionSoldOutForMenu("블렌드", "아메리카노", true)
        assertTrue(repository.option("블렌드").soldOut)

        repository.setOptionSoldOutForMenu("블렌드", "아메리카노", false)
        assertEquals(setOf("카페라떼"), repository.option("블렌드").soldOutMenus)

        repository.setOptionSoldOut("블렌드", false)
        assertEquals(OptionSale.ON_SALE, repository.option("블렌드").saleState(linked))
    }

    @Test
    fun renamedMenu_keepsItsSoldOutOption() {
        val repository = repository()
        repository.setOptionSoldOutForMenu("블렌드", "카페라떼", true)

        repository.saveMenu("카페라떼", menus[1].copy(name = "오트라떼"))

        assertTrue(repository.isOptionSoldOutFor("블렌드", "오트라떼"))
        assertFalse(repository.isOptionSoldOutFor("블렌드", "카페라떼"))
    }

    @Test
    fun requiredGroup_withNothingLeft_makesTheMenuUnavailable() {
        val repository = repository()
        repository.setOptionSoldOutForMenu("블렌드", "카페라떼", true)
        assertEquals(emptySet<String>(), repository.unavailableNames())

        repository.setOptionSoldOutForMenu("오리지널", "카페라떼", true)

        assertEquals(setOf("카페라떼"), repository.unavailableNames())
    }

    @Test
    fun warning_appearsOnlyForRequiredGroupsWithSomethingSoldOut() {
        val blend = MenuOption("블렌드", 0, soldOut = true)
        val original = MenuOption("오리지널", 0)
        val soldBlend = group.copy(options = listOf(original, blend))

        assertNull(optionWarning(soldBlend, original, menus))
        assertNull(optionWarning(soldBlend.copy(required = false), blend, menus))
        assertEquals(OptionWarning.LastChoices(listOf("오리지널"), 2), optionWarning(soldBlend, blend, menus))

        val allGone = group.copy(options = listOf(original.copy(soldOut = true), blend))
        assertEquals(OptionWarning.AllGone(2), optionWarning(allGone, blend, menus))
    }

    @Test
    fun topicParticle_followsTheLastSound() {
        assertEquals("은", topicParticle("원두 선택"))
        assertEquals("는", topicParticle("사이즈"))
        assertEquals("는", topicParticle("HOT / ICE"))
    }
}
