package eu.zeletrik.devcore.tools.codes

import com.google.zxing.common.BitMatrix
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream

/** A rendered code: PNG for the preview and download, SVG for download. */
class CodeImage(val png: ByteArray, val svg: String)

/** Black modules on white; a 1D matrix (one row) is drawn as bars of [barHeight] modules. */
object CodeImages {
	/** QR: exactly 4 quiet-zone modules, at the largest whole-pixel module size that fits in 512 px. */
	fun qr(matrix: BitMatrix): CodeImage {
		val size = (512 / (matrix.width + 8)).coerceAtLeast(1)
		return render(matrix, quiet = 4, module = size, barHeight = null)
	}

	/** Barcodes keep the quiet zones the encoder already added. */
	fun barcode(matrix: BitMatrix) = render(matrix, quiet = 0, module = 2, barHeight = 60)

	private fun render(matrix: BitMatrix, quiet: Int, module: Int, barHeight: Int?): CodeImage {
		val columns = matrix.width + 2 * quiet
		val rows = (barHeight ?: matrix.height) + 2 * quiet
		fun dark(x: Int, y: Int): Boolean {
			val mx = x - quiet
			val my = if (barHeight != null) 0 else y - quiet
			val inside = mx in 0 until matrix.width && (barHeight != null && y - quiet in 0 until barHeight || my in 0 until matrix.height)
			return inside && matrix[mx, my]
		}
		val image = BufferedImage(columns * module, rows * module, BufferedImage.TYPE_BYTE_GRAY)
		for (y in 0 until rows) for (x in 0 until columns) {
			val rgb = if (dark(x, y)) 0x000000 else 0xFFFFFF
			for (dy in 0 until module) for (dx in 0 until module) image.setRGB(x * module + dx, y * module + dy, rgb)
		}
		val path = buildString {
			for (y in 0 until rows) {
				var x = 0
				while (x < columns) {
					if (!dark(x, y)) { x++; continue }
					val start = x
					while (x < columns && dark(x, y)) x++
					append("M$start ${y}h${x - start}v1h-${x - start}z")
				}
			}
		}
		val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $columns $rows" width="${columns * module}" height="${rows * module}" shape-rendering="crispEdges"><rect width="$columns" height="$rows" fill="#fff"/><path fill="#000" d="$path"/></svg>"""
		return CodeImage(png(image), svg)
	}

	/** In memory only: ImageIO.write would buffer through a temp file, and a WiFi QR holds a password. */
	private fun png(image: BufferedImage): ByteArray {
		val out = ByteArrayOutputStream()
		val writer = ImageIO.getImageWritersByFormatName("png").next()
		try {
			MemoryCacheImageOutputStream(out).use { stream ->
				writer.output = stream
				writer.write(image)
			}
		} finally {
			writer.dispose()
		}
		return out.toByteArray()
	}
}
