import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource

class TestSource : AnimeHttpSource() {
    override val name = "test"
    override val lang = "en"
}

fun main() {
    val src = TestSource()
    println("ID is: ${src.id}")
}
