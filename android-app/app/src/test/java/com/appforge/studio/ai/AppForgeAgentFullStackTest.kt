package com.appforge.studio.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentFullStackTest {
    @Test
    fun validContractProducesFrontendApiAndDatabaseContracts() {
        val blueprint = sampleBlueprint()
        val contract = sampleContract()

        val validation = AppForgeAgentFullStackValidator.validate(contract)
        assertTrue(validation.valid)

        val project = AppForgeAgentFullStackCodegen.generate(blueprint, contract)
        val paths = project.files.map { it.path }.toSet()

        assertTrue("backend/contracts/appforge-fullstack.json" in paths)
        assertTrue("backend/contracts/openapi.json" in paths)
        assertTrue("backend/contracts/environment.txt" in paths)
        assertTrue("backend/database/schema.sql" in paths)

        val environment = project.files.first { it.path.endsWith("environment.txt") }.content
        assertTrue("APPFORGE_DATABASE_URL" in environment)
        assertTrue("APPFORGE_AUTH_PROVIDER" in environment)
        assertFalse("postgres://" in environment)

        val sql = project.files.first { it.path.endsWith("schema.sql") }.content
        assertTrue("CREATE TABLE IF NOT EXISTS tasks" in sql)
        assertTrue("id UUID NOT NULL PRIMARY KEY" in sql)
    }

    @Test
    fun authRequiredEndpointIsRejectedWhenAuthIsNone() {
        val invalid = sampleContract().copy(
            auth = AppForgeAgentAuthKind.NONE,
            endpoints = listOf(
                AppForgeAgentApiEndpointSpec(
                    id = "listTasks",
                    method = AppForgeAgentApiMethod.GET,
                    path = "/api/tasks",
                    authRequired = true,
                    responseEntity = "Task"
                )
            )
        )

        val validation = AppForgeAgentFullStackValidator.validate(invalid)
        assertFalse(validation.valid)
        assertTrue(validation.issues.any { it.field.endsWith("authRequired") })
    }

    @Test
    fun duplicateMethodAndPathIsRejected() {
        val first = AppForgeAgentApiEndpointSpec(
            id = "listTasks",
            method = AppForgeAgentApiMethod.GET,
            path = "/api/tasks",
            responseEntity = "Task"
        )
        val second = first.copy(id = "listTasksAgain")

        val invalid = sampleContract().copy(endpoints = listOf(first, second))
        val validation = AppForgeAgentFullStackValidator.validate(invalid)

        assertFalse(validation.valid)
        assertTrue(validation.issues.any { "method/path" in it.message })
    }

    @Test(expected = IllegalArgumentException::class)
    fun codegenRejectsPlatformMismatch() {
        AppForgeAgentFullStackCodegen.generate(
            sampleBlueprint(),
            sampleContract().copy(frontendPlatform = AppForgeAgentPlatform.WEB)
        )
    }

    private fun sampleBlueprint() = AppForgeAgentBlueprint(
        appName = "TaskFlow",
        prompt = "Görev takip uygulaması",
        platform = AppForgeAgentPlatform.ANDROID,
        startRoute = "/home",
        screens = listOf(
            AppForgeAgentScreenSpec(
                id = "home",
                title = "Görevler",
                route = "/home",
                purpose = "Görev listesini göster",
                components = listOf("Görev listesi")
            )
        )
    )

    private fun sampleContract() = AppForgeAgentFullStackContract(
        appName = "TaskFlow",
        frontendPlatform = AppForgeAgentPlatform.ANDROID,
        backend = AppForgeAgentBackendKind.NODE_EXPRESS,
        auth = AppForgeAgentAuthKind.EMAIL_PASSWORD,
        database = AppForgeAgentDatabaseKind.POSTGRES,
        entities = listOf(
            AppForgeAgentDataEntitySpec(
                id = "Task",
                tableName = "tasks",
                fields = listOf(
                    AppForgeAgentDataFieldSpec(
                        id = "id",
                        type = AppForgeAgentFieldType.UUID,
                        primaryKey = true
                    ),
                    AppForgeAgentDataFieldSpec(
                        id = "title",
                        type = AppForgeAgentFieldType.STRING
                    ),
                    AppForgeAgentDataFieldSpec(
                        id = "done",
                        type = AppForgeAgentFieldType.BOOLEAN
                    )
                )
            )
        ),
        endpoints = listOf(
            AppForgeAgentApiEndpointSpec(
                id = "listTasks",
                method = AppForgeAgentApiMethod.GET,
                path = "/api/tasks",
                authRequired = true,
                responseEntity = "Task"
            ),
            AppForgeAgentApiEndpointSpec(
                id = "createTask",
                method = AppForgeAgentApiMethod.POST,
                path = "/api/tasks",
                authRequired = true,
                requestEntity = "Task",
                responseEntity = "Task"
            )
        )
    )
}
