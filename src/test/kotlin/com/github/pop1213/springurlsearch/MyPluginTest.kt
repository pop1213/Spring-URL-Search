package com.github.pop1213.springurlsearch

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.pop1213.springurlsearch.services.UrlIndexService

class MyPluginTest : BasePlatformTestCase() {

    fun testUrlCombinationAndMatching() {
        val indexService = project.service<UrlIndexService>()

        val controllerCode = """
            package com.example;
            
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PostMapping;
            
            @RestController
            @RequestMapping("/api/v1")
            public class MyController {
            
                @GetMapping("/users")
                public String getUsers() {
                    return "users";
                }
                
                @PostMapping("/users")
                public String createUser() {
                    return "created";
                }
            }
        """.trimIndent()

        // Configure virtual file in mock project
        myFixture.configureByText("MyController.java", controllerCode)

        // Rebuild the in-memory URL index
        indexService.rebuildIndex()

        // Check index results
        val items = indexService.getAllItems()

        assertEquals(2, items.size)

        val getMapping = items.find { it.method == "GET" }
        assertNotNull(getMapping)
        assertEquals("/api/v1/users", getMapping?.url)
        assertEquals("MyController", getMapping?.className?.substringAfterLast('.'))
        assertEquals("getUsers", getMapping?.methodName)

        val postMapping = items.find { it.method == "POST" }
        assertNotNull(postMapping)
        assertEquals("/api/v1/users", postMapping?.url)
        assertEquals("MyController", postMapping?.className?.substringAfterLast('.'))
        assertEquals("createUser", postMapping?.methodName)
    }

    fun testApiCommonTest() {
        val indexService = project.service<UrlIndexService>()

        val controllerCode = """
            package com.example;
            
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.GetMapping;
            
            @RestController
            @RequestMapping(path = "/api/common")
            public class CommonController {
            
                @GetMapping("/test")
                public String doTest() {
                    return "test";
                }
            }
        """.trimIndent()

        myFixture.configureByText("CommonController.java", controllerCode)
        indexService.rebuildIndex()

        val items = indexService.getAllItems()
        val mapping = items.find { it.url == "/api/common/test" }
        assertNotNull("Mapping for /api/common/test should be found", mapping)
        assertEquals("GET", mapping?.method)
        assertEquals("CommonController", mapping?.className?.substringAfterLast('.'))
        assertEquals("doTest", mapping?.methodName)
    }
}
