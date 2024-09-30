package com.narbase.narcore.main

import java.io.File

object CountableNounsConverter {
    private val nounsFile = File("/home/khalid-elkhidir/work/narcore-kunafa/compiler-plugins/src/main/resources/noun.csv")
    private val pluralToSingular = mutableMapOf<String, String>()

    fun getSingularForNoun(noun: String): String {
        readFile()
        return pluralToSingular.getOrDefault(noun.lowercase(), noun)
    }

    private fun readFile() {
        if (pluralToSingular.isEmpty()) {
            nounsFile.bufferedReader()
            nounsFile.readLines().forEach { line ->
                val splitLine = line.split(",")
                pluralToSingular[splitLine.last()] = splitLine.first()
            }
        }
    }
}

fun main() {
    val pluralTestNoun = "Roles"
    println(CountableNounsConverter.getSingularForNoun(pluralTestNoun))
}