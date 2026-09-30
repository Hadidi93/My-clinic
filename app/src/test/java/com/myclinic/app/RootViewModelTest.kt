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

    @Test
    fun `signed out shows the sign-in flow`() {
        val vm = RootViewModel(auth, FakeDoctorRepository(demoDoctor()))
        assertEquals(RootState.SignedOut, vm.state.value)
    }

    @Test
    fun `signed in with an incomplete profile shows profile setup`() {
        val vm = RootViewModel(auth, FakeDoctorRepository(demoDoctor(complete = false)))
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        assertTrue(vm.state.value is RootState.NeedsProfile)
    }

    @Test
    fun `signed in with a complete profile opens the app`() {
        val vm = RootViewModel(auth, FakeDoctorRepository(demoDoctor(complete = true)))
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        assertTrue(vm.state.value is RootState.Ready)
    }

    @Test
    fun `password reset link takes priority`() {
        val vm = RootViewModel(auth, FakeDoctorRepository(demoDoctor()))
        auth.passwordRecoveryPending.value = true
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        assertEquals(RootState.PasswordRecovery, vm.state.value)
    }

    @Test
    fun `profile that fails to load offers retry`() {
        val doctors = FakeDoctorRepository(demoDoctor()).apply { failRefresh = true }
        val vm = RootViewModel(auth, doctors)
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        assertEquals(RootState.ProfileLoadFailed, vm.state.value)
    }

    @Test
    fun `signing out clears the cached profile`() {
        val doctors = FakeDoctorRepository(demoDoctor())
        val vm = RootViewModel(auth, doctors)
        auth.state.value = AuthState.SignedIn("demo-id", "dr.demo@example.test")
        vm.signOut()
        assertEquals(RootState.SignedOut, vm.state.value)
        assertEquals(null, doctors.myProfile.value)
    }
}
