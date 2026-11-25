package vn.unicom.thi360

import android.app.Application
import vn.unicom.thi360.core.preferences.AppPreferencesRepository
import vn.unicom.thi360.core.preferences.DataStoreAppPreferencesRepository

class Thi360Application : Application() {
    val preferencesRepository: AppPreferencesRepository by lazy {
        DataStoreAppPreferencesRepository.create(this)
    }
}
