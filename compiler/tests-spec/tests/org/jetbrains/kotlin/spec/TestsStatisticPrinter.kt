/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.spec

import java.io.File

private abstract class StatElement {
    var counter = 0
    abstract val elements: MutableMap<*, out StatElement>?
    open fun increment() {
        counter++
    }
}

private class TestTypeStat(private val paragraph: StatElement) : StatElement() {
    override val elements = null
    override fun increment() {
        super.increment()
        paragraph.increment()
    }
}

private class ParagraphStat(private val section: StatElement) : StatElement() {
    override val elements = sortedMapOf<String, TestTypeStat>()
    override fun increment() {
        super.increment()
        section.increment()
    }
}

private class SectionStat(private val area: StatElement) : StatElement() {
    override val elements = sortedMapOf<Int, ParagraphStat>()
    override fun increment() {
        super.increment()
        area.increment()
    }
}

private class AreaStat : StatElement() {
    override val elements = sortedMapOf<String, SectionStat>()
}

object TestsStatisticPrinter {
    private const val TEST_DATA_DIR = "./testData"

    private val specTestAreas = listOf("diagnostics", "psi", "codegen")

    private fun incrementStatCounters(testAreaStats: AreaStat, sectionName: String, paragraphNumber: Int, testType: String) {
        val section = testAreaStats.elements.computeIfAbsent(sectionName) { SectionStat(testAreaStats) }
        val paragraph = section.elements.computeIfAbsent(paragraphNumber) { ParagraphStat(section) }

        paragraph.elements.computeIfAbsent(testType) { TestTypeStat(paragraph) }.increment()
    }

    private fun collectStatistic(): Map<String, AreaStat> {
        val statistic = mutableMapOf<String, AreaStat>()

        specTestAreas.forEach {
            val specTestArea = it
            val specTestsPath = "$TEST_DATA_DIR/$specTestArea"

            statistic[specTestArea] = AreaStat()

            File(specTestsPath).walkTopDown().forEach areaTests@{
                if (!it.isFile || it.extension != "kt") return@areaTests

                val testInfoMatcher = SpecTestValidator.testPathPattern.matcher(it.path)

                if (!testInfoMatcher.find()) return@areaTests

                val sectionNumber = testInfoMatcher.group("sectionNumber")
                val sectionName = testInfoMatcher.group("sectionName")
                val paragraphNumber = testInfoMatcher.group("paragraphNumber").toInt()
                val testType = testInfoMatcher.group("testType")
                val section = "$sectionNumber $sectionName"

                incrementStatCounters(statistic[specTestArea]!!, section, paragraphNumber, testType)
            }
        }

        return statistic
    }

    fun print() {
        val statistic = collectStatistic()

        println("--------------------------------------------------")
        println("SPEC TESTS STATISTIC")
        println("--------------------------------------------------")

        statistic.forEach {
            println("${it.key.toUpperCase()}: ${it.value.counter} tests")

            it.value.elements.forEach {
                println("  ${it.key.toUpperCase()}: ${it.value.counter} tests")

                it.value.elements.forEach {
                    val testTypes = mutableListOf<String>()

                    it.value.elements.forEach {
                        testTypes.add("${it.key}: ${it.value.counter}")
                    }

                    println("    PARAGRAPH ${it.key}: ${it.value.counter} tests (${testTypes.joinToString(", ")})")
                }
            }
        }

        println("--------------------------------------------------")
    }
}