package eu.zeletrik.devcore.tools.password

import eu.zeletrik.devcore.tools.secureRandom
import kotlin.math.floor
import kotlin.math.log2
import kotlin.random.asKotlinRandom

enum class CharSet(val label: String, val chars: String) {
	LOWER("Lowercase", "abcdefghijklmnopqrstuvwxyz"),
	UPPER("Uppercase", "ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
	DIGITS("Digits", "0123456789"),
	SYMBOLS("Symbols", "!#$%&()*+,-./:;<=>?@[]^_{|}~"),
}

object Passwords {
	private const val AMBIGUOUS = "Il1O0o|"
	private val random = secureRandom.asKotlinRandom()

	/** EFF large word list (CC BY 3.0 US), 7,776 words: dice number, tab, word. */
	val words: List<String> by lazy {
		Passwords::class.java.getResourceAsStream("/eff_large_wordlist.txt")!!.bufferedReader().readLines()
			.filter { it.isNotBlank() }
			.map { it.substringAfter('\t').trim() }
	}

	private fun pools(sets: Set<CharSet>, excludeAmbiguous: Boolean) =
		CharSet.entries.filter { it in sets }.map { set -> set.chars.filterNot { excludeAmbiguous && it in AMBIGUOUS } }

	/** Every selected set appears at least once; the rest is drawn from all of them, then shuffled. */
	fun password(length: Int, sets: Set<CharSet>, excludeAmbiguous: Boolean): String {
		require(sets.isNotEmpty()) { "Select at least one character set" }
		val pools = pools(sets, excludeAmbiguous)
		val all = pools.joinToString("")
		val chars = pools.map { it.random(random) } + List(length - pools.size) { all.random(random) }
		return chars.shuffled(random).joinToString("")
	}

	fun passphrase(count: Int, separator: String, capitalize: Boolean): String =
		List(count) {
			words.random(random).let { word -> if (capitalize) word.replaceFirstChar { it.uppercase() } else word }
		}
			.joinToString(separator)

	fun passwordEntropy(length: Int, sets: Set<CharSet>, excludeAmbiguous: Boolean) =
		floor(length * log2(pools(sets, excludeAmbiguous).sumOf { it.length }.toDouble())).toInt()

	fun passphraseEntropy(count: Int) = floor(count * log2(words.size.toDouble())).toInt()
}
