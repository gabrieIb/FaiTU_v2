package com.menumanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.menumanager.data.HouseholdRepository

class HouseholdViewModelFactory(
    private val repository: HouseholdRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HouseholdViewModel::class.java)) {
            return HouseholdViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
