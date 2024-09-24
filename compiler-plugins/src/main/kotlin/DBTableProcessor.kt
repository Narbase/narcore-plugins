package org.example

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated

class DBTableProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val allFiles = resolver.getAllFiles().map { it.fileName }
        logger.warn(allFiles.toList().toString())
        return emptyList()
    }
}

class DBTableProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return DBTableProcessor(environment.codeGenerator, environment.logger)
    }
}