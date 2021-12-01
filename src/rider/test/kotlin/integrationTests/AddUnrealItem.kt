//package integrationTests
//
//import testFrameworkExtentions.UnrealSuiteProject
//import com.jetbrains.rider.test.annotations.TestEnvironment
//import com.jetbrains.rider.test.enums.PlatformType
//import com.jetbrains.rider.test.enums.ToolsetVersion
//import org.testng.annotations.Test
//import java.time.Duration
//
//
//@TestEnvironment(platform = [PlatformType.WINDOWS], toolset = ToolsetVersion.TOOLSET_16_CPP)
//class AddUnrealItem : UnrealSuiteProject() {
//    init {
//        solutionName = "EmptyUProject"
//        needInstallRiderLink = true
//        openSolutionParams.waitForCaches = true
//        openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(90)
//        openSolutionParams.preprocessSolutionFile = { generateSolutionFromUProject(it) }
//    }
//
//    @Test
//    fun connection() {
//
//    }
//}
