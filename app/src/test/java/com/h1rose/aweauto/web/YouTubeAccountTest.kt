package com.h1rose.aweauto.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeAccountTest {
    @Test
    fun signedInWhenAccountCookiesExist() {
        assertTrue(YouTubeAccount.isSignedIn("PREF=f6=400; VISITOR_INFO1_LIVE=x; SAPISID=abc/def; YSC=1"))
        assertTrue(YouTubeAccount.isSignedIn("LOGIN_INFO=AFmmF2sw"))
        assertFalse(YouTubeAccount.isSignedIn("PREF=f6=400; VISITOR_INFO1_LIVE=x"))
        assertFalse(YouTubeAccount.isSignedIn(null))
        // 値の中に名前が入っていても、名前でなければ数えない
        assertFalse(YouTubeAccount.isSignedIn("PREF=SAPISID"))
    }

    @Test
    fun cookieNames() {
        assertEquals(setOf("A", "B", "C"), YouTubeAccount.cookieNames("A=1; B=2=3;C="))
        assertEquals(emptySet<String>(), YouTubeAccount.cookieNames(""))
    }

    @Test
    fun signInPages() {
        assertTrue(YouTubeAccount.isSignInPage(YouTubeAccount.SIGN_IN_URL))
        assertTrue(YouTubeAccount.isSignInPage("https://accounts.google.com/v3/signin/identifier?x=1"))
        assertFalse(YouTubeAccount.isSignInPage("https://m.youtube.com/signin"))
        assertFalse(YouTubeAccount.isSignInPage("https://evil.example/accounts.google.com"))
        assertFalse(YouTubeAccount.isSignInPage(null))
    }
}
