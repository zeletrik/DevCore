package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.router.Route
import eu.zeletrik.devcore.tools.barcode.BarcodeView
import eu.zeletrik.devcore.tools.base64.Base64View
import eu.zeletrik.devcore.tools.basicauth.BasicAuthView
import eu.zeletrik.devcore.tools.casing.CaseConverterView
import eu.zeletrik.devcore.tools.certificate.CertificateView
import eu.zeletrik.devcore.tools.chmod.ChmodView
import eu.zeletrik.devcore.tools.color.ColorView
import eu.zeletrik.devcore.tools.cron.CronView
import eu.zeletrik.devcore.tools.dataformat.DataFormatView
import eu.zeletrik.devcore.tools.datasize.DataSizeView
import eu.zeletrik.devcore.tools.diff.TextDiffView
import eu.zeletrik.devcore.tools.dockercompose.DockerComposeView
import eu.zeletrik.devcore.tools.hash.HashGeneratorView
import eu.zeletrik.devcore.tools.hashing.PasswordHashingView
import eu.zeletrik.devcore.tools.htmlentities.HtmlEntitiesView
import eu.zeletrik.devcore.tools.json.JsonFormatterView
import eu.zeletrik.devcore.tools.jsondiff.JsonDiffView
import eu.zeletrik.devcore.tools.jsonpath.JsonPathView
import eu.zeletrik.devcore.tools.jwt.JwtView
import eu.zeletrik.devcore.tools.keys.KeyPairView
import eu.zeletrik.devcore.tools.lines.LineToolsView
import eu.zeletrik.devcore.tools.lorem.LoremView
import eu.zeletrik.devcore.tools.markdown.MarkdownView
import eu.zeletrik.devcore.tools.morse.MorseView
import eu.zeletrik.devcore.tools.nato.NatoView
import eu.zeletrik.devcore.tools.numberbase.NumberBaseView
import eu.zeletrik.devcore.tools.password.PasswordView
import eu.zeletrik.devcore.tools.qr.QrCodeView
import eu.zeletrik.devcore.tools.random.RandomNumberView
import eu.zeletrik.devcore.tools.random.RandomPortView
import eu.zeletrik.devcore.tools.regex.RegexView
import eu.zeletrik.devcore.tools.sql.SqlFormatterView
import eu.zeletrik.devcore.tools.stringescape.StringEscapeView
import eu.zeletrik.devcore.tools.subnet.SubnetView
import eu.zeletrik.devcore.tools.textstats.TextStatisticsView
import eu.zeletrik.devcore.tools.timestamp.TimestampView
import eu.zeletrik.devcore.tools.timezone.TimeZoneView
import eu.zeletrik.devcore.tools.unicode.UnicodeEscapeView
import eu.zeletrik.devcore.tools.url.UrlEncoderView
import eu.zeletrik.devcore.tools.urlparser.UrlParserView
import eu.zeletrik.devcore.tools.uuid.UuidGeneratorView
import eu.zeletrik.devcore.tools.xml.XmlFormatterView

/** The tool registry: navigation, Home, search and titles all derive from these entries. */
enum class Tool(
	val title: String,
	val description: String,
	val category: Category,
	val icon: VaadinIcon,
	val keywords: List<String>,
	val view: Class<out Component>,
) {
	BARCODE(
		"Barcode Generator",
		"Generate Code 128, EAN-13, EAN-8, UPC-A and Code 39 barcodes as PNG or SVG",
		Category.GENERATORS,
		VaadinIcon.BARCODE,
		listOf("barcode", "ean", "upc", "code128", "code39", "gtin"),
		BarcodeView::class.java,
	),
	BASE64(
		"Base64 Encoder/Decoder",
		"Encode text to Base64 or decode it back",
		Category.ENCODING,
		VaadinIcon.CODE,
		listOf("base64", "b64", "encode", "decode"),
		Base64View::class.java,
	),
	BASIC_AUTH(
		"Basic Auth Header",
		"Build an HTTP Basic Authorization header, or read one back",
		Category.SECURITY,
		VaadinIcon.USER_CARD,
		listOf("basic auth", "authorization", "header", "http", "credentials", "base64"),
		BasicAuthView::class.java,
	),
	CASE_CONVERTER(
		"Case Converter",
		"Convert text to camelCase, snake_case, kebab-case and seven more cases at once",
		Category.TEXT,
		VaadinIcon.TEXT_HEIGHT,
		listOf("case", "camel", "snake", "kebab", "pascal", "constant", "title", "upper", "lower"),
		CaseConverterView::class.java,
	),
	CERTIFICATE(
		"Certificate Decoder",
		"Read X.509 certificates and certificate requests (CSR) from PEM",
		Category.SECURITY,
		VaadinIcon.DIPLOMA,
		listOf("x509", "certificate", "csr", "pem", "tls", "ssl", "san"),
		CertificateView::class.java,
	),
	CHMOD(
		"Chmod Calculator",
		"Convert Unix permissions between octal, symbolic and a checkbox grid",
		Category.CONVERTERS,
		VaadinIcon.TOOLS,
		listOf("chmod", "permissions", "unix", "octal", "rwx", "umask"),
		ChmodView::class.java,
	),
	COLOR(
		"Color Converter",
		"Convert colors between Hex, RGB, HSL, HSV, CMYK and CSS names, with a picker",
		Category.CONVERTERS,
		VaadinIcon.PALETTE,
		listOf("color", "colour", "hex", "rgb", "hsl", "hsv", "cmyk", "css"),
		ColorView::class.java,
	),
	CRON(
		"Cron Explainer",
		"Describe a cron expression in plain English and list its next runs",
		Category.DATE_TIME,
		VaadinIcon.CALENDAR_CLOCK,
		listOf("cron", "crontab", "schedule", "quartz", "spring", "next run"),
		CronView::class.java,
	),
	DATA_FORMAT(
		"Data Format Converter",
		"Convert between JSON, YAML, TOML, properties and CSV",
		Category.DATA,
		VaadinIcon.FILE_TREE,
		listOf("yaml", "toml", "csv", "properties", "json", "convert", "config"),
		DataFormatView::class.java,
	),
	DATA_SIZE(
		"Data Size Converter",
		"Convert between bits, bytes, decimal (kB, MB…) and binary (KiB, MiB…) units",
		Category.CONVERTERS,
		VaadinIcon.SCALE,
		listOf("bytes", "kb", "mb", "gb", "kib", "mib", "bits", "size"),
		DataSizeView::class.java,
	),
	DOCKER_COMPOSE(
		"Docker Compose Converter",
		"Convert docker run commands to a Compose file and back",
		Category.CONVERTERS,
		VaadinIcon.BOAT,
		listOf("docker", "compose", "docker-compose", "run", "container", "service", "yaml"),
		DockerComposeView::class.java,
	),
	HASH(
		"Hash Generator",
		"Compute MD5, SHA-1 and SHA-2 digests or HMACs of text",
		Category.SECURITY,
		VaadinIcon.HASH,
		listOf("md5", "sha", "sha1", "sha256", "sha512", "digest", "checksum", "hmac"),
		HashGeneratorView::class.java,
	),
	HTML_ENTITIES(
		"HTML Entity Encoder/Decoder",
		"Escape text for HTML, or turn entities back into characters",
		Category.ENCODING,
		VaadinIcon.TAGS,
		listOf("html", "entity", "entities", "escape", "amp", "nbsp"),
		HtmlEntitiesView::class.java,
	),
	JSON(
		"JSON Formatter",
		"Format, validate and minify JSON without touching its numbers, and expand JSON inside strings",
		Category.DATA,
		VaadinIcon.CURLY_BRACKETS,
		listOf(
			"json", "format", "pretty", "minify", "beautify", "validate",
			"unescape", "escaped", "nested", "embedded", "stringified", "sns", "sqs",
		),
		JsonFormatterView::class.java,
	),
	JSONPATH(
		"JSONPath Tester",
		"Query JSON with a JSONPath expression; numbers keep every digit",
		Category.DATA,
		VaadinIcon.FILTER,
		listOf("jsonpath", "json", "query", "filter", "path", "select"),
		JsonPathView::class.java,
	),
	JSON_DIFF(
		"JSON Diff",
		"Compare two JSON documents and list what changed by path, ignoring key order",
		Category.DATA,
		VaadinIcon.SPLIT_H,
		listOf("json", "diff", "compare", "changes", "delta", "difference", "semantic", "structural"),
		JsonDiffView::class.java,
	),
	JWT(
		"JWT Decoder/Encoder",
		"Inspect a JSON Web Token and check its HS256/384/512 signature, or sign a new one",
		Category.SECURITY,
		VaadinIcon.KEY,
		listOf("jwt", "token", "bearer", "claims", "jose", "sign", "hs256", "verify"),
		JwtView::class.java,
	),
	KEY_PAIR(
		"Key Pair Generator",
		"Generate Ed25519 or RSA key pairs as PEM and an OpenSSH public key",
		Category.SECURITY,
		VaadinIcon.KEY_O,
		listOf("ssh", "keygen", "rsa", "ed25519", "pem", "public key", "private key"),
		KeyPairView::class.java,
	),
	LINE_TOOLS(
		"Line Tools",
		"Sort, deduplicate, reverse, shuffle and trim lines",
		Category.TEXT,
		VaadinIcon.LIST_OL,
		listOf("lines", "sort", "dedupe", "unique", "reverse", "shuffle", "trim"),
		LineToolsView::class.java,
	),
	LOREM(
		"Lorem Ipsum Generator",
		"Generate placeholder paragraphs, sentences or words",
		Category.GENERATORS,
		VaadinIcon.PARAGRAPH,
		listOf("lorem", "ipsum", "placeholder", "dummy text", "filler"),
		LoremView::class.java,
	),
	MARKDOWN(
		"Markdown to HTML",
		"Convert CommonMark with tables, strikethrough and autolinks to HTML source",
		Category.DATA,
		VaadinIcon.FILE_TEXT_O,
		listOf("markdown", "md", "commonmark", "gfm", "html"),
		MarkdownView::class.java,
	),
	MORSE(
		"Morse Code",
		"Translate text to ITU Morse code and back",
		Category.ENCODING,
		VaadinIcon.ELLIPSIS_DOTS_H,
		listOf("morse", "itu", "dot", "dash", "telegraph"),
		MorseView::class.java,
	),
	NATO(
		"NATO Phonetic Alphabet",
		"Spell text with the NATO/ICAO alphabet",
		Category.ENCODING,
		VaadinIcon.MICROPHONE,
		listOf("nato", "icao", "phonetic", "spelling", "alfa", "bravo"),
		NatoView::class.java,
	),
	NUMBER_BASE(
		"Number Base Converter",
		"Convert integers of any size between binary, octal, decimal and hexadecimal",
		Category.CONVERTERS,
		VaadinIcon.ABACUS,
		listOf("binary", "octal", "hex", "decimal", "base", "radix", "bigint"),
		NumberBaseView::class.java,
	),
	PASSWORD(
		"Password Generator",
		"Generate strong passwords or EFF word-list passphrases, with an entropy estimate",
		Category.GENERATORS,
		VaadinIcon.PASSWORD,
		listOf("password", "passphrase", "diceware", "random", "secure", "entropy"),
		PasswordView::class.java,
	),
	PASSWORD_HASHING(
		"Password Hashing",
		"Hash a password with bcrypt or Argon2id, or check one against a hash",
		Category.SECURITY,
		VaadinIcon.LOCK,
		listOf("bcrypt", "argon2", "password", "hash", "verify", "phc"),
		PasswordHashingView::class.java,
	),
	QR(
		"QR Code Generator",
		"Generate QR codes for text, links and Wi-Fi networks as PNG or SVG",
		Category.GENERATORS,
		VaadinIcon.QRCODE,
		listOf("qr", "qrcode", "wifi", "code", "png", "svg"),
		QrCodeView::class.java,
	),
	RANDOM_NUMBER(
		"Random Number Generator",
		"Draw uniform random integers in a range, optionally unique",
		Category.GENERATORS,
		VaadinIcon.CUBES,
		listOf("random", "number", "integer", "dice", "unique"),
		RandomNumberView::class.java,
	),
	RANDOM_PORT(
		"Random Port Generator",
		"Pick distinct random ports, skipping the well-known ones by default",
		Category.NETWORK,
		VaadinIcon.PLUG,
		listOf("port", "random", "tcp", "udp", "network"),
		RandomPortView::class.java,
	),
	REGEX(
		"Regex Tester",
		"Try a Java regular expression: matches, groups and replacement",
		Category.TEXT,
		VaadinIcon.ASTERISK,
		listOf("regex", "regexp", "regular expression", "pattern", "match", "group", "replace"),
		RegexView::class.java,
	),
	SQL(
		"SQL Formatter",
		"Lay out SQL in six dialects, keywords uppercased or as written",
		Category.DATA,
		VaadinIcon.DATABASE,
		listOf("sql", "format", "query", "postgres", "mysql", "beautify"),
		SqlFormatterView::class.java,
	),
	STRING_ESCAPE(
		"String Escape/Unescape",
		"Escape text as a JSON, Java or JavaScript string literal, and back",
		Category.ENCODING,
		VaadinIcon.QUOTE_RIGHT,
		listOf("string", "escape", "unescape", "literal", "quote", "backslash"),
		StringEscapeView::class.java,
	),
	SUBNET(
		"Subnet Calculator",
		"Calculate IPv4 and IPv6 CIDR ranges: network, masks, hosts and address type",
		Category.NETWORK,
		VaadinIcon.SITEMAP,
		listOf("subnet", "cidr", "ip", "ipv4", "ipv6", "netmask", "network"),
		SubnetView::class.java,
	),
	TEXT_DIFF(
		"Text Diff",
		"Compare two texts line by line as a unified diff",
		Category.TEXT,
		VaadinIcon.EXCHANGE,
		listOf("diff", "compare", "patch", "unified", "changes"),
		TextDiffView::class.java,
	),
	TEXT_STATISTICS(
		"Text Statistics",
		"Count characters, words, lines, sentences and bytes",
		Category.TEXT,
		VaadinIcon.BAR_CHART,
		listOf("count", "words", "characters", "length", "statistics", "bytes"),
		TextStatisticsView::class.java,
	),
	TIMESTAMP(
		"Timestamp Converter",
		"Convert Unix time to ISO-8601 and back, in any time zone",
		Category.DATE_TIME,
		VaadinIcon.CLOCK,
		listOf("unix", "epoch", "time", "date", "iso", "timezone"),
		TimestampView::class.java,
	),
	TIME_ZONE(
		"Time Zone Converter",
		"Show a local date-time in other time zones, DST included",
		Category.DATE_TIME,
		VaadinIcon.GLOBE,
		listOf("timezone", "time zone", "tz", "utc", "offset", "dst", "convert"),
		TimeZoneView::class.java,
	),
	UNICODE_ESCAPE(
		"Unicode Escape/Unescape",
		"Write characters as \\uXXXX, \\u{X}, &#xX; or U+XXXX, and back",
		Category.ENCODING,
		VaadinIcon.FONT,
		listOf("unicode", "escape", "code point", "utf-16", "surrogate", "emoji"),
		UnicodeEscapeView::class.java,
	),
	URL(
		"URL Encoder/Decoder",
		"Percent-encode text or decode it back",
		Category.ENCODING,
		VaadinIcon.LINK,
		listOf("url", "uri", "percent", "encode", "decode", "escape"),
		UrlEncoderView::class.java,
	),
	URL_PARSER(
		"URL Parser",
		"Split a URL into its parts and decode its query parameters",
		Category.NETWORK,
		VaadinIcon.CONNECT,
		listOf("url", "uri", "query", "parameters", "parse", "host"),
		UrlParserView::class.java,
	),
	UUID(
		"UUID Generator",
		"Generate random v4 or time-ordered v7 UUIDs, and ULIDs",
		Category.GENERATORS,
		VaadinIcon.RANDOM,
		listOf("uuid", "guid", "v4", "v7", "id", "ulid"),
		UuidGeneratorView::class.java,
	),
	XML(
		"XML Formatter",
		"Pretty-print or minify XML, safely: DTDs and entities are never resolved",
		Category.DATA,
		VaadinIcon.FILE_CODE,
		listOf("xml", "format", "pretty", "minify", "beautify", "indent"),
		XmlFormatterView::class.java,
	),
	;

	/** Public URL contract, declared once on the view's @Route. */
	val id: String get() = view.getAnnotation(Route::class.java).value

	fun matches(query: String) =
		title.contains(query, ignoreCase = true) || keywords.any { it.contains(query, ignoreCase = true) }

	companion object {
		fun sorted() = entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Tool::title))

		fun of(view: Class<*>) = entries.firstOrNull { it.view == view }

		fun byId(id: String) = entries.firstOrNull { it.id == id }
	}
}
