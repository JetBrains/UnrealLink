package testFrameworkExtentions

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod

/** Class for Unreal tests. TODO doc
 */
abstract class UnrealTestProject : UnrealBase() {

    @BeforeMethod
    override fun configureSettings() {
        super.configureSettings()
    }

    @BeforeMethod(dependsOnMethods = ["configureSettings"])
    override fun putSolutionToTempDir() {
        super.putSolutionToTempDir()
    }

    @BeforeMethod(dependsOnMethods = ["putSolutionToTempDir"])
    override fun prepareAndOpenSolution(parameters: Array<Any>) {
        super.prepareAndOpenSolution(parameters)
    }

    @AfterMethod(alwaysRun = true)
    fun testTeardown() {
        collectUnrealLogs()
//        deleteTempDir()
        closeProject()
    }
}