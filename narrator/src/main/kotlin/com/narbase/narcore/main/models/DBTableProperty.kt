package com.narbase.narcore.main.models

import com.google.devtools.ksp.symbol.KSType

data class DBTableProperty(
    val name: String,
    val ksType: KSType,
    val qualifier: String
)
