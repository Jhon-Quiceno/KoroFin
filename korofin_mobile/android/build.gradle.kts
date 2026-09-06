allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)

    // Algunos plugins (file_picker, y flutter_plugin_android_lifecycle que
    // arrastra image_picker) declaran su propio compileSdk viejo y chocan entre
    // sí. Se fuerza a que TODOS los subproyectos Android compilen contra el API
    // 36, vía reflexión para no meter el classpath del Android Gradle Plugin en
    // la raíz. Se registra en este primer bloque (antes de evaluationDependsOn)
    // para que el afterEvaluate llegue a tiempo.
    afterEvaluate {
        project.extensions.findByName("android")?.let { androidExtension ->
            try {
                androidExtension.javaClass
                    .getMethod("compileSdkVersion", Int::class.javaPrimitiveType)
                    .invoke(androidExtension, 36)
            } catch (_: Exception) {
            }
        }
    }
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
