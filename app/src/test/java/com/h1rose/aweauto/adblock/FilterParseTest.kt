package com.h1rose.aweauto.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterParseTest {
    private fun parse(vararg lines: String): Pair<Set<String>, Set<String>> {
        val block = HashSet<String>()
        val allow = HashSet<String>()
        lines.forEach { AdBlocker.parseLine(it, block, allow) }
        return block to allow
    }

    @Test
    fun domainRules() {
        val (block, allow) = parse(
            "! comment",
            "||ads.example.com^",
            "||tracker.example^\$third-party",
            "||Upper.Example.com^\$important",
            "@@||ok.example.com^",
            "0.0.0.0 hosts.example.net",
        )
        assertEquals(setOf("ads.example.com", "tracker.example", "upper.example.com", "hosts.example.net"), block)
        assertEquals(setOf("ok.example.com"), allow)
    }

    @Test
    fun ignoresRulesThatAreNotWholeDomain() {
        val (block, _) = parse(
            "||example.com/ads/*",
            "||example.com^\$script",
            "||example.com^\$domain=foo.com",
            "example.com##.ad-banner",
            "/banner/*",
            "0.0.0.0 localhost",
        )
        assertEquals(emptySet<String>(), block)
    }
}
