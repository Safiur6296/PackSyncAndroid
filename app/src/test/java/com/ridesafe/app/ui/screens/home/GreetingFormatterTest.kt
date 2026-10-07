package com.ridesafe.app.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingFormatterTest {

    @Test
    fun testDefaultAndEmptyCases() {
        assertEquals("Hi, there", formatGreeting(""))
        assertEquals("Hi, there", formatGreeting("   "))
        assertEquals("Hi, there", formatGreeting("\t\n"))
    }

    @Test
    fun testSingleWordName() {
        assertEquals("Hi, Alex", formatGreeting("Alex"))
        assertEquals("Hi, Alex", formatGreeting("  Alex  "))
    }

    @Test
    fun testFullNameUsesFirstNameOnly() {
        assertEquals("Hi, Safiur", formatGreeting("Safiur Rahaman"))
        assertEquals("Hi, John", formatGreeting("John Doe"))
        assertEquals("Hi, Jane", formatGreeting("  Jane  Mary  Smith "))
    }

    @Test
    fun testTruncatedCaseWhenFirstNameExceeds20Chars() {
        // Exactly 20 chars
        val twentyChars = "Abcdefghij1234567890"
        assertEquals("Hi, $twentyChars", formatGreeting(twentyChars))

        // 25 chars -> truncated to first 20 + ellipsis
        val longName = "AlexanderTheGreatWarriorX"
        val expected = "Hi, ${longName.take(20)}…"
        assertEquals(expected, formatGreeting(longName))
        assertEquals("Hi, AlexanderTheGreatWar…", formatGreeting(longName))
    }
}
