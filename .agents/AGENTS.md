# Spring URL Search Plugin Development Rules

This file documents rules and guardrails for developing IntelliJ Platform plugins using Kotlin and UAST.

## IntelliJ Plugin Configurations

### K2 Compiler Mode Compatibility
When developing a plugin that depends on the Kotlin plugin (`org.jetbrains.kotlin`), you must explicitly declare compatibility with the K2 compiler mode. 
Add the following extension block to `plugin.xml` inside `<idea-plugin>`:
```xml
<extensions defaultExtensionNs="org.jetbrains.kotlin">
    <supportsKotlinPluginMode supportsK2="true" />
</extensions>
```

---

## Kotlin & UAST Coding Invariants

### Accessing Annotations on UAnnotated Elements
Do not access UAST annotations using the `.annotations` property on `UClass` or `UMethod`. In Kotlin, this name clashes with `PsiClass.getAnnotations()` (returning Java `PsiAnnotation` arrays) and has been deprecated/removed in newer UAST versions.
Always use the `.uAnnotations` property which resolves unambiguously to UAST's `List<UAnnotation>`:
```kotlin
// Correct: Returns List<UAnnotation>
val classAnnotations = uClass.uAnnotations 

// Incorrect: Causes name clash / unresolved reference
val classAnnotations = uClass.annotations 
```

### Robust Annotation Matching
When inspecting annotations via UAST/PSI (e.g., checking for controller or mapping stereotypes), always verify both the **fully qualified name** and the **short name**. This ensures robustness in headless test sandboxes and when indexes are rebuilding:
```kotlin
val isController = annotations.any { ann ->
    val qName = ann.qualifiedName
    qName == "org.springframework.web.bind.annotation.RestController" || qName == "RestController"
}
```
