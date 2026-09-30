package com.myclinic.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point. @HiltAndroidApp switches on dependency injection for the whole app. */
@HiltAndroidApp
class MyClinicApp : Application()
