package com.github.pop1213.springurlsearch.search

import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer

data class UrlIndexItem(
    val url: String,
    val method: String,
    val psiMethodPointer: SmartPsiElementPointer<PsiMethod>,
    val className: String,
    val methodName: String
)
