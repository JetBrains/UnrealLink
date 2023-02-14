package testFrameworkExtentions

import org.testng.annotations.AfterClass
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeClass

/** Class for Unreal tests. TODO doc
 */
abstract class UnrealClassProject : UnrealBase() {

    @BeforeClass
    override fun configureSettings() {
        super.configureSettings()
    }

    @BeforeClass(dependsOnMethods = ["configureSettings"])
    override fun putSolutionToTempDir() {
        super.putSolutionToTempDir()
    }

    @AfterMethod
    fun testTeardown() {
        collectUnrealLogs()
    }

    @AfterClass(alwaysRun = true)
    fun classTeardown() {
//        deleteTempDir()
        closeProject()
    }
}