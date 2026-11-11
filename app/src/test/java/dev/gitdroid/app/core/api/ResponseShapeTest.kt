package dev.gitdroid.app.core.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shape of every GitHub response the app decodes.
 *
 * The Actions endpoints wrap their collections inside an object
 * (`{"total_count":n,"workflow_runs":[…]}`) instead of returning a bare
 * array. Decoding those as arrays throws, and because the repository layer
 * caught that and returned an empty list, the whole Actions tab looked empty
 * with no error anywhere. These tests pin the unwrapping so that class of
 * silent-empty bug has to show itself.
 */
class ResponseShapeTest {

    /** Serves [body] for any request, so the client's parsing is what is tested. */
    private class StubInterceptor(private val body: String) : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val request = chain.request()
            return Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun api(body: String): GitHubApi {
        val client = OkHttpClient.Builder().addInterceptor(StubInterceptor(body)).build()
        return GitHubApi(tokenProvider = { "t" }, client = client)
    }

    @Test
    fun `workflow runs are unwrapped out of the workflow_runs object`() = runBlocking {
        val a = api(
            """{"total_count":2,"workflow_runs":[
               {"id":11,"name":"Build APK","run_number":7,"head_branch":"main",
                "status":"completed","conclusion":"success","event":"push","workflow_id":3},
               {"id":12,"name":"Lint","run_number":8,"head_branch":"main",
                "status":"in_progress","event":"pull_request","workflow_id":4}
            ]}""",
        )
        val runs = a.listWorkflowRuns("o", "r")
        assertEquals(2, runs.size)
        assertEquals(11L, runs[0].id)
        assertEquals("Build APK", runs[0].name)
        assertEquals("success", runs[0].conclusion)
        assertEquals(8, runs[1].runNumber)
    }

    @Test
    fun `workflows are unwrapped out of the workflows object`() = runBlocking {
        val a = api(
            """{"total_count":1,"workflows":[
               {"id":33,"name":"Release","path":".github/workflows/release.yml","state":"active"}
            ]}""",
        )
        val workflows = a.listWorkflows("o", "r")
        assertEquals(1, workflows.size)
        assertEquals(33L, workflows[0].id)
        assertEquals(".github/workflows/release.yml", workflows[0].path)
    }

    @Test
    fun `jobs are unwrapped out of the jobs object`() = runBlocking {
        val a = api(
            """{"total_count":1,"jobs":[
               {"id":900,"run_id":11,"name":"build","status":"completed","conclusion":"success"}
            ]}""",
        )
        val jobs = a.workflowRunJobs("o", "r", 11)
        assertEquals(1, jobs.size)
        assertEquals("build", jobs[0].name)
    }

    @Test
    fun `artifacts are unwrapped out of the artifacts object`() = runBlocking {
        val a = api(
            """{"total_count":1,"artifacts":[
               {"id":5,"name":"app-release","size_in_bytes":2048,
                "archive_download_url":"https://api.github.com/x","expired":false}
            ]}""",
        )
        val artifacts = a.workflowRunArtifacts("o", "r", 11)
        assertEquals(1, artifacts.size)
        assertEquals("app-release", artifacts[0].name)
        assertEquals(2048L, artifacts[0].sizeBytes)
    }

    @Test
    fun `a bare array still decodes where GitHub returns one`() = runBlocking {
        val a = api("""[{"name":"main","commit":{"sha":"abc123"}},{"name":"dev","commit":{"sha":"def"}}]""")
        val branches = a.listBranches("o", "r")
        assertEquals(2, branches.size)
        assertEquals("main", branches[0].name)
        assertEquals("abc123", branches[0].sha)
    }

    @Test
    fun `a wrapped endpoint that is not wrapped reports the problem instead of emptying`() {
        // The old behaviour: decode throws, the caller catches, the tab is
        // silently empty. getWrapped must fail loudly with a useful message.
        val a = api("""[{"id":1}]""")
        val failure = runCatching { runBlocking { a.listWorkflows("o", "r") } }
        assertTrue(
            "expected a clear parse failure, got ${failure.exceptionOrNull()}",
            failure.exceptionOrNull() is ParseException,
        )
        val message = failure.exceptionOrNull()!!.message!!
        assertTrue(message, message.contains("workflows"))
    }

    @Test
    fun `repository fields GitHub spells with underscores reach the model`() = runBlocking {
        val a = api(
            """{"id":1,"name":"gitdroid","full_name":"HakaI963/gitdroid","private":false,
               "stargazers_count":128,"forks_count":12,"open_issues_count":3,
               "watchers_count":9,"default_branch":"main","language":"Kotlin",
               "archived":false,"owner":{"login":"HakaI963"}}""",
        )
        val repo = a.repository("HakaI963", "gitdroid")
        assertEquals(128, repo.stargazersCount)
        assertEquals(12, repo.forksCount)
        assertEquals(3, repo.openIssuesCount)
        assertEquals(9, repo.watchersCount)
        assertEquals("HakaI963", repo.ownerLogin)
    }
}
