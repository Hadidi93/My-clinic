package com.myclinic.app

import com.myclinic.app.ui.auth.LoginViewModel
import com.myclinic.domain.error.AuthError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val vm = LoginViewModel(auth)

    @Test
    fun `invalid email is flagged and nothing is sent`() {
        vm.onEmailChange("not-an-email")
        vm.onPasswordChange("whatever123")
        vm.signIn()
        assertTrue(vm.state.value.showEmailError)
        assertTrue(auth.signInCalls.isEmpty())
    }

    @Test
    fun `successful sign in clears the password`() {
        vm.onEmailChange("dr.demo@example.test")
        vm.onPasswordChange("demoPassword1")
        vm.signIn()
        assertEquals(1, auth.signInCalls.size)
        assertEquals("", vm.state.value.password)
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun `wrong password shows a friendly error`() {
        auth.signInError = AuthError.INVALID_CREDENTIALS
        vm.onEmailChange("dr.demo@example.test")
        vm.onPasswordChange("wrongPassword1")
        vm.signIn()
        assertEquals(AuthError.INVALID_CREDENTIALS, vm.state.value.error)
        assertEquals(false, vm.state.value.loading)
    }
}
