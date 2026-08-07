package com.hj0128.moneybook

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.hj0128.moneybook.di.AppContainer

class MoneyBookApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** ViewModel Factory 안에서 컨테이너를 꺼낼 때 쓰는 헬퍼. */
fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyBookApp).container
