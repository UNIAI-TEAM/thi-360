package vn.unicom.thi360.feature.password

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel

class ChangePasswordDraftViewModel : ViewModel() {
    val oldPasswordState = TextFieldState()
    val newPasswordState = TextFieldState()
    val confirmPasswordState = TextFieldState()

    fun clearAll() {
        oldPasswordState.clearText()
        newPasswordState.clearText()
        confirmPasswordState.clearText()
    }
}
