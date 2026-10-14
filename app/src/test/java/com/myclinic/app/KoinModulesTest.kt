package com.myclinic.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import com.myclinic.app.di.appModules
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Checks that Koin can build every screen's view model and every repository
 * (no missing piece that would only crash when that screen opens).
 * Context, SavedStateHandle and WorkerParameters come from Android at run time.
 */
@OptIn(KoinExperimentalAPI::class)
class KoinModulesTest {
    @Test
    fun `every dependency can be created`() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(Context::class, SavedStateHandle::class, WorkerParameters::class),
        )
    }
}
