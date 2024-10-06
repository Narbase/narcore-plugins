package com.narbase.narcore.main

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import java.io.OutputStream

fun generateImport(qualifier: String, ksType: KSType): String {
    val str = "import $qualifier.${ksType.makeNotNullable().declaration}"
    return str
}
fun generateImport(qualifiedName: KSName): String {
    val str = "import ${qualifiedName.getQualifier()}.${qualifiedName.getShortName()}"
    return str
}
fun generateImport(fullQualifiedName: String): String {
    val str = "import $fullQualifiedName"
    return str
}

fun KSType.getTypeArgument(): String {
    return this.toString().substringAfter("<").substringBeforeLast(">")
        .plus(this.toString().substringAfterLast(">"))
}

fun KSPLogger.withIndent(message: String, levels: Int = 1) = warn("\t".repeat(levels) + message)

fun KSPLogger.newLine() = warn("\n")

fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}

fun OutputStream.appendLine(str: String = "") {
    appendText(str + System.lineSeparator())
}

fun OutputStream.appendLineWithIndent(str: String = "", levels: Int = 1) {
    appendText("\t".repeat(levels) + str + System.lineSeparator())
}