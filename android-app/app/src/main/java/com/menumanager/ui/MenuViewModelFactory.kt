package com.menumanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.menumanager.data.MenuRepository

class MenuViewModelFactory(private val repository: MenuRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MenuViewModel::class.java)) {
            return MenuViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
