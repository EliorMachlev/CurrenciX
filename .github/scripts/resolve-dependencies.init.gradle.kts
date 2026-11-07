// Downloads the files of every resolvable configuration of every project into
// the Gradle cache, for the read-only dependency cache that
// .github/workflows/dependency-cache.yaml builds.
//
// Each configuration is resolved leniently, because some can't be resolved on
// their own (AGP's, which only resolve with attributes its tasks add), and
// those are skipped. A failed download is different: it fails the task, so a
// cache missing what a rate-limited request didn't fetch is never saved, and
// the next run (weekly, or by hand) builds it again.
val downloadFailure = Regex("""Could not (GET|HEAD|download)|status code \d{3}|timed out|Connection reset""")

fun Throwable.chain(): Sequence<Throwable> = generateSequence(this) { it.cause }

allprojects {
    val all = configurations
    tasks.register("resolveDependencies") {
        doLast {
            val failed =
                all.filter { it.isCanBeResolved }.flatMap { configuration ->
                    val artifacts =
                        configuration.incoming
                            .artifactView { lenient(true) }
                            .artifacts
                    // The files first: that is what downloads them.
                    artifacts.artifactFiles.files
                    artifacts.failures
                        .filter { failure -> failure.chain().any { downloadFailure.containsMatchIn(it.message.orEmpty()) } }
                        .map { "${configuration.name}: ${it.message}" }
                }
            check(failed.isEmpty()) { "Downloads failed:\n" + failed.joinToString("\n") }
        }
    }
}
