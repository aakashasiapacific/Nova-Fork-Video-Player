package com.aakash.novafork.core.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class ServerHttpTest {
    @Test
    fun aFailedSecureConnectionNeverSuggestsGoingUnencrypted() {
        val untrusted = SSLHandshakeException("PKIX path building failed").apply {
            initCause(CertificateException("unable to find valid certification path"))
        }
        val wrongName = SSLPeerUnverifiedException("Hostname jf.example.com not verified")
        val plainPort = SSLException("Unrecognized SSL message, plaintext connection?")

        val certificate = ServerHttp.unreachableMessage(untrusted, "jf.example.com")
        assertTrue(certificate, certificate.startsWith("jf.example.com's certificate isn't trusted"))
        assertEquals(certificate, ServerHttp.unreachableMessage(wrongName, "jf.example.com"))
        assertEquals(
            "Couldn't make a secure connection to jf.example.com. Does it speak https on this port?",
            ServerHttp.unreachableMessage(plainPort, "jf.example.com"),
        )
        for (message in listOf(certificate, ServerHttp.unreachableMessage(plainPort, "h"))) {
            assertFalse(message, "http://" in message)
        }
    }
}
