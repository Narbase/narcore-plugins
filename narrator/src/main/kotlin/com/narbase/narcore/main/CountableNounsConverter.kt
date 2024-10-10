package com.narbase.narcore.main

import java.io.BufferedReader
import java.io.File

object CountableNounsConverter {
    private var nounsReader = javaClass.getResourceAsStream("/noun.csv")?.bufferedReader()
    private val pluralToSingular = mutableMapOf<String, String>()

    fun getSingularForNoun(noun: String): String {
        readFile()
        return pluralToSingular.getOrDefault(noun.lowercase(), noun)
    }

    private fun readFile() {
        if (pluralToSingular.isEmpty()) {
            nounsReader?.readLines()?.forEach { line ->
                val splitLine = line.split(",")
                pluralToSingular[splitLine.last()] = splitLine.first()
            }
        }
    }
}