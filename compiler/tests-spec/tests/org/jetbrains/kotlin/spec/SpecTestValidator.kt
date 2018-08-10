/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.spec

import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

enum class TestType(val type: String) {
    POSITIVE("pos"),
    NEGATIVE("neg");

    companion object {
        private val map = TestType.values().associateBy(TestType::type)
        fun fromValue(type: String) = map[type]
    }
}

enum class TestArea {
    PSI,
    DIAGNOSTICS,
    CODEGEN
}

data class TestCase(
    val number: Int,
    val description: String,
    val unexpectedBehavior: Boolean,
    val issues: List<String>?
)

class TestInfo(
    val testArea: TestArea,
    val testType: TestType,
    val sectionNumber: String,
    val sectionName: String,
    val paragraphNumber: Int,
    val sentenceNumber: Int,
    val sentence: String?,
    val testNumber: Int,
    val description: String?,
    val cases: List<TestCase>?,
    val unexpectedBehavior: Boolean,
    val issues: List<String>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestInfo) return false

        return this.testArea == other.testArea
                && this.testType == other.testType
                && this.sectionNumber == other.sectionNumber
                && this.testNumber == other.testNumber
                && this.paragraphNumber == other.paragraphNumber
                && this.sentenceNumber == other.sentenceNumber
    }
}

enum class SpecTestValidationFailedReason(val description: String) {
    FILENAME_NOT_VALID(
        "Incorrect test filename or folder name.\n" +
                "It must match the following path pattern: " +
                "testsData/<diagnostic|psi|codegen>/s<sectionNumber>_<sectionName>/p-<paragraph>/<pos|neg>/<sentence>_<testNumber>.kt " +
                "(example: testsData/diagnostic/s-16.30_when-expression/p-3/pos/1.3.kt)"
    ),
    METAINFO_NOT_VALID("Incorrect meta info in test file."),
    FILENAME_AND_METAINFO_NOT_CONSISTENCY("Test info from filename and file content is not consistency"),
    TEST_IS_NOT_POSITIVE("Test is not positive because it contains error elements (PsiErrorElement or diagnostic with error severity)."),
    TEST_IS_NOT_NEGATIVE("Test is not negative because it not contains error type elements (PsiErrorElement or diagnostic with error severity)."),
    UNKNOWN("Unknown validation error.")
}

class SpecTestValidationException(val reason: SpecTestValidationFailedReason) : Exception()

abstract class SpecTestValidator(private val testDataFile: File, private val testArea: TestArea) {
    private lateinit var testInfoByFilename: TestInfo
    private lateinit var testInfoByContent: TestInfo
    private val testInfo by lazy { testInfoByContent }

    companion object {
        private const val INTEGER_REGEX = "[1-9]\\d*"
        private const val TEST_UNEXPECTED_BEHAVIOUR = "(?:\n\\s*(?<unexpectedBehaviour>UNEXPECTED BEHAVIOUR))"
        private const val TEST_ISSUES = "(?:\n\\s*ISSUES:\\s*(?<issues>(KT-[1-9]\\d*)(,\\s*KT-[1-9]\\d*)*))"

        private val testAreaRegex = "(?<testArea>${TestArea.values().joinToString("|")})"
        private val testTypeRegex = "(?<testType>${TestType.values().joinToString("|")})"

        val testPathPattern: Pattern =
            Pattern.compile("^.*?/(?<testArea>diagnostics|psi|codegen)/s-(?<sectionNumber>(?:$INTEGER_REGEX)(?:\\.$INTEGER_REGEX)*)_(?<sectionName>[\\w-]+)/p-(?<paragraphNumber>$INTEGER_REGEX)/(?<testType>pos|neg)/(?<sentenceNumber>$INTEGER_REGEX)\\.(?<testNumber>$INTEGER_REGEX)\\.kt$")
        val testContentMetaInfoPattern: Pattern =
            Pattern.compile("\\/\\*\\s+KOTLIN $testAreaRegex SPEC TEST \\($testTypeRegex\\)\\s+SECTION (?<sectionNumber>(?:$INTEGER_REGEX)(?:\\.$INTEGER_REGEX)*):\\s*(?<sectionName>.*?)\\s+PARAGRAPH:\\s*(?<paragraphNumber>$INTEGER_REGEX)\\s+SENTENCE\\s*(?<sentenceNumber>$INTEGER_REGEX):\\s*(?<sentence>.*?)\\s+NUMBER:\\s*(?<testNumber>$INTEGER_REGEX)\\s+DESCRIPTION:\\s*(?<testDescription>.*?)$TEST_UNEXPECTED_BEHAVIOUR?$TEST_ISSUES?\\s+\\*\\/\\s+")
        val testCaseInfoPattern: Pattern =
            Pattern.compile("(?:(?:\\/\\*\n\\s*)|(?:\\/\\/\\s*))CASE DESCRIPTION:\\s*(?<testCaseDescription>.*?)$TEST_UNEXPECTED_BEHAVIOUR?$TEST_ISSUES?\n(\\s\\*\\/)?")

        private fun getTestInfo(
            testInfoMatcher: Matcher,
            directMappedTestTypeEnum: Boolean = false,
            withDetails: Boolean = false,
            testCases: List<TestCase>? = null,
            unexpectedBehavior: Boolean = false,
            issues: List<String>? = null
        ): TestInfo {
            val testDescription = if (withDetails) testInfoMatcher.group("testDescription") else null

            return TestInfo(
                TestArea.valueOf(testInfoMatcher.group("testArea").toUpperCase()),
                if (directMappedTestTypeEnum)
                    TestType.valueOf(testInfoMatcher.group("testType")) else
                    TestType.fromValue(testInfoMatcher.group("testType"))!!,
                testInfoMatcher.group("sectionNumber"),
                testInfoMatcher.group("sectionName"),
                testInfoMatcher.group("paragraphNumber").toInt(),
                testInfoMatcher.group("sentenceNumber").toInt(),
                if (withDetails) testInfoMatcher.group("sentence") else null,
                testInfoMatcher.group("testNumber").toInt(),
                testDescription,
                testCases,
                unexpectedBehavior,
                issues
            )
        }

        private fun getSingleTestCase(testInfoMatcher: Matcher): TestCase {
            val testDescription = testInfoMatcher.group("testDescription")
            val unexpectedBehaviour = testInfoMatcher.group("unexpectedBehaviour") != null
            val issues = testInfoMatcher.group("issues")?.split(",")

            return TestCase(
                1,
                testDescription,
                unexpectedBehaviour,
                issues
            )
        }

        private fun getTestCasesInfo(testCaseInfoMatcher: Matcher, testInfoMatcher: Matcher): List<TestCase> {
            val testCases = mutableListOf<TestCase>()
            var testCasesCounter = 1

            while (testCaseInfoMatcher.find()) {
                val unexpectedBehaviour = testCaseInfoMatcher.group("unexpectedBehaviour") != null
                val issues = testCaseInfoMatcher.group("issues")?.split(Regex(",\\s*"))

                testCases.add(
                    TestCase(
                        testCasesCounter++,
                        testCaseInfoMatcher.group("testCaseDescription"),
                        unexpectedBehaviour,
                        issues
                    )
                )
            }

            if (testCases.isEmpty()) {
                testCases.add(getSingleTestCase(testInfoMatcher))
            }

            return testCases
        }

        fun testMetaInfoFilter(fileContent: String): String {
            val fileContentWithoutTestInfo = testContentMetaInfoPattern.matcher(fileContent).replaceAll("")
            val fileContentWithoutCasesInfo = testCaseInfoPattern.matcher(fileContentWithoutTestInfo).replaceAll("")

            return fileContentWithoutCasesInfo
        }
    }

    private fun hasUnexpectedBehavior(testCases: List<TestCase>, testInfoMatcher: Matcher) =
        testCases.any { it.unexpectedBehavior } || testInfoMatcher.group("unexpectedBehaviour") != null

    private fun getIssues(testCases: List<TestCase>, testInfoMatcher: Matcher): List<String> {
        val issues = mutableListOf<String>()

        testCases.forEach {
            if (it.issues != null) issues.addAll(it.issues)
        }

        val testIssues = testInfoMatcher.group("issues")?.split(Regex(",\\s*"))

        if (testIssues != null) issues.addAll(testIssues)

        return issues.distinct()
    }

    fun parseTestInfo() {
        val testInfoByFilenameMatcher = testPathPattern.matcher(testDataFile.path)

        if (!testInfoByFilenameMatcher.find()) {
            throw SpecTestValidationException(SpecTestValidationFailedReason.FILENAME_NOT_VALID)
        }

        val fileContent = testDataFile.readText()
        val testInfoByContentMatcher = testContentMetaInfoPattern.matcher(fileContent)

        if (!testInfoByContentMatcher.find()) {
            throw SpecTestValidationException(SpecTestValidationFailedReason.METAINFO_NOT_VALID)
        }

        val testCasesMatcher = testCaseInfoPattern.matcher(fileContent)
        val testCases = getTestCasesInfo(testCasesMatcher, testInfoByContentMatcher)

        testInfoByFilename = getTestInfo(testInfoByFilenameMatcher)
        testInfoByContent = getTestInfo(
            testInfoByContentMatcher,
            withDetails = true,
            directMappedTestTypeEnum = true,
            testCases = testCases,
            unexpectedBehavior = hasUnexpectedBehavior(testCases, testInfoByContentMatcher),
            issues = getIssues(testCases, testInfoByContentMatcher)
        )

        if (testInfoByFilename != testInfoByContent) {
            throw SpecTestValidationException(SpecTestValidationFailedReason.FILENAME_AND_METAINFO_NOT_CONSISTENCY)
        }
    }

    protected fun validateTestType(computedTestType: TestType) {
        if (computedTestType != testInfo.testType) {
            val isNotNegative = computedTestType == TestType.POSITIVE && testInfo.testType == TestType.NEGATIVE
            val isNotPositive = computedTestType == TestType.NEGATIVE && testInfo.testType == TestType.POSITIVE
            val reason = when {
                isNotNegative -> SpecTestValidationFailedReason.TEST_IS_NOT_NEGATIVE
                isNotPositive -> SpecTestValidationFailedReason.TEST_IS_NOT_POSITIVE
                else -> SpecTestValidationFailedReason.UNKNOWN
            }
            throw SpecTestValidationException(reason)
        }
    }

    fun printTestInfo() {
        println("--------------------------------------------------")
        if (testInfoByContent.unexpectedBehavior) {
            println("(!!!) HAS UNEXPECTED BEHAVIOUR (!!!)")
        }
        println("$testArea ${testInfoByFilename.testType} SPEC TEST")
        println("SECTION: ${testInfoByFilename.sectionNumber} ${testInfoByContent.sectionName} (paragraph: ${testInfoByFilename.paragraphNumber})")
        println("SENTENCE ${testInfoByContent.sentenceNumber}: ${testInfoByContent.sentence}")
        println("TEST NUMBER: ${testInfoByContent.testNumber}")
        println("NUMBER OF TEST CASES: ${testInfoByContent.cases!!.size}")
        println("DESCRIPTION: ${testInfoByContent.description}")
        if (testInfoByContent.issues!!.isNotEmpty()) {
            println("LINKED ISSUES: ${testInfoByContent.issues!!.joinToString(", ")}")
        }
    }
}