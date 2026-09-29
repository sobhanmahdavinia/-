package ir.payesh.masraf

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        TrustedClock.init(this)
        LimitStore.init(this)
    }
}
