// Downloads the files of every resolvable configuration of every project into
// the Gradle cache, for the read-only dependency cache that
// .github/workflows/dependency-cache.yaml builds. Lenient: a configuration
// that can't be resolved on its own (one AGP only resolves with attributes it
// sets later) is skipped, not fatal; a job that needs it downloads it then.
allprojects {
    val all = configurations
    tasks.register("resolveDependencies") {
        doLast {
            all.filter { it.isCanBeResolved }.forEach { configuration ->
                configuration.incoming
                    .artifactView { lenient(true) }
                    .files.files
            }
        }
    }
}
