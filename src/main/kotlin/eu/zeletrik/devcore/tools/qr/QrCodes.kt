package eu.zeletrik.devcore.tools.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

enum class WifiSecurity(val label: String, val code: String) { WPA("WPA/WPA2/WPA3", "WPA"), WEP("WEP", "WEP"), NONE("None", "nopass") }

object QrCodes {
	/** The de facto `WIFI:` payload; `\ ; , : "` are backslash-escaped. */
	fun wifi(ssid: String, password: String, security: WifiSecurity, hidden: Boolean): String = buildString {
		append("WIFI:T:").append(security.code).append(";S:").append(escape(ssid)).append(';')
		if (security != WifiSecurity.NONE) append("P:").append(escape(password)).append(';')
		if (hidden) append("H:true;")
		append(';')
	}

	/** The text as UTF-8 bytes; the matrix has no margin, the renderer adds the quiet zone. */
	fun encode(content: String, level: ErrorCorrectionLevel): BitMatrix = try {
		val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to level, EncodeHintType.MARGIN to 0)
		QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
	} catch (_: WriterException) {
		throw IllegalArgumentException("Too much data for a QR code at error correction level $level")
	}

	private fun escape(value: String) = value.replace(Regex("""([\\;,:"])"""), "\\\\$1")
}
