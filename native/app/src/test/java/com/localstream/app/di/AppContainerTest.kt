package com.localstream.app.di

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class AppContainerTest {

    @Test
    fun okHttpClient_isExposedAndMemoized() {
        val container = AppContainer(context = null)
        val client1 = container.okHttpClient
        val client2 = container.okHttpClient
        assertNotNull(client1)
        assertSame(client1, client2)
    }
}
