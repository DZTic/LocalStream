package com.localstream.app.di

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun imageOkHttpClient_sharesConnectionsButHasNoHttpCache() {
        val container = AppContainer(context = null)
        val images = container.imageOkHttpClient
        assertNull(images.cache)
        assertSame(container.okHttpClient.connectionPool, images.connectionPool)
        assertSame(container.okHttpClient.dispatcher, images.dispatcher)
    }
}
