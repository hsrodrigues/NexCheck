package com.nexcheck.app

// Representa o documento principal salvo no Firestore
data class ChecklistData(
    val title: String = "",
    val sections: List<ChecklistSection> = emptyList()
)

// Representa cada bloco "Sanfona" (Accordion) que você criou
data class ChecklistSection(
    val id: String = "",
    val title: String = "",
    val items: List<String> = emptyList()
)