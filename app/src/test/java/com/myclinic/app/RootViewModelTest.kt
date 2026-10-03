package com.myclinic.app

import com.myclinic.app.data.auth.AuthState
import com.myclinic.app.ui.root.RootState
import com.myclinic.app.ui.root.RootViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RootViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val patients = FakePatientRepository()
    private val notifications = FakeNotificationRepository()

    @Test
    fun `signed out shows the sign-in flow`() {
        val vm = RootViewModel(auth, FakeDoctorRepository(demoDoctor()), patients, notifications)
        assertEquals(RootState.SignedOut, vm.state.value)
    }

    @Test
    fun `signed in with an incomplete profile shows profile setup`() {
        val vm = RootViewModel(auth, FakeDoctorRepository(demoDoctor(complete = false)), patients, notifications)
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        assertTrue(vm.state.value is RootState.NeedsProfile)
    }

    @Test
    fun `signed in with a complete profile opens the app`() {
        val vm = RootViewModel(auth, FakeDoctorRepository(demoDoctor(complete = true)), patients, notifications)
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        assertTrue(vm.state.value is RootState.Ready)
    }

    @Test
    fun `a brief session reload keeps the open screens instead of restarting at home`() {
        val vm = RootViewModel(auth, FakeDoctorRepository(demoDoctor(complete = true)), patients, notifications)
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        val ready = vm.state.value
        assertTrue(ready is RootState.Ready)
        auth.state.value = AuthState.Loading // e.g. back from the camera app
        assertEquals(ready, vm.state.value)
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        assertEquals(ready, vm.state.value)
    }

    @Test
    fun `password reset link takes priority`() {
        val vm = RootViewModel(auth, FakeDoctorRepository(demoDoctor()), patients, notifications)
        auth.passwordRecoveryPending.value = true
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        assertEquals(RootState.PasswordRecovery, vm.state.value)
    }

    @Test
    fun `profile that fails to load offers retry`() {
        val doctors = FakeDoctorRepository(demoDoctor()).apply { failRefresh = true }
        val vm = RootViewModel(auth, doctors, patients, notifications)
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        assertEquals(RootState.ProfileLoadFailed, vm.state.value)
    }

    @Test
    fun `signing out clears the cached profile`() {
        val doctors = FakeDoctorRepository(demoDoctor())
        val vm = RootViewModel(auth, doctors, patients, notifications)
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        vm.signOut()
        assertEquals(RootState.SignedOut, vm.state.value)
        assertEquals(null, doctors.myProfile.value)
    }

    @Test
    fun `signing in starts sync and signing out wipes the offline patient data`() {
        val vm = RootViewModel(auth, FakeDoctorRepository(demoDoctor()), patients, notifications)
        val clearedAtStart = patients.cleared // signed-out start also clears (e.g. expired session)
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        assertEquals(1, patients.syncStarted)
        assertEquals(1, notifications.registered)
        vm.signOut()
        assertEquals(clearedAtStart + 1, patients.cleared)
        assertEquals("the phone stops getting pushes for this account", 1, notifications.unregistered)
    }
}
