package vn.unicom.thi360.feature.password

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import java.util.WeakHashMap

class ChangePasswordDraftViewModel : ViewModel() {
    val oldPasswordState = TextFieldState()
    val newPasswordState = TextFieldState()
    val confirmPasswordState = TextFieldState()

    init {
        synchronized(activeDrafts) {
            activeDrafts[this] = Unit
        }
    }

    fun clearAll() {
        oldPasswordState.clearText()
        newPasswordState.clearText()
        confirmPasswordState.clearText()
    }

    override fun onCleared() {
        clearAll()
        synchronized(activeDrafts) {
            activeDrafts.remove(this)
        }
    }

    companion object {
        private val activeDrafts = WeakHashMap<ChangePasswordDraftViewModel, Unit>()

        fun clearAllActiveDrafts() {
            val snapshot = synchronized(activeDrafts) { activeDrafts.keys.toList() }
            snapshot.forEach { it.clearAll() }
        }
    }
}
