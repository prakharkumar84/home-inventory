package com.prakharkumar.homeinventory

import android.app.Application

class InventoryApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
