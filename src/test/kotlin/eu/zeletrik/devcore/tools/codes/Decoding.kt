package eu.zeletrik.devcore.tools.codes

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** Test helpers: read codes back with ZXing's own decoders. */
internal fun decode(image: BufferedImage): Result {
	val pixels = IntArray(image.width * image.height)
	image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
	val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
	return MultiFormatReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true))
}

internal fun decodePng(png: ByteArray) = decode(ImageIO.read(ByteArrayInputStream(png)))

/** Rasterises the SVG's own path (runs of `M x yhNv1h-Nz`) and decodes it. */
internal fun decodeSvg(svg: String): Result {
	val (columns, rows) = Regex("""viewBox="0 0 (\d+) (\d+)"""").find(svg)!!.destructured.toList().map(String::toInt)
	val scale = 4
	val image = BufferedImage(columns * scale, rows * scale, BufferedImage.TYPE_INT_RGB)
	for (y in 0 until image.height) for (x in 0 until image.width) image.setRGB(x, y, 0xFFFFFF)
	Regex("""M(\d+) (\d+)h(\d+)v1h-\d+z""").findAll(svg).forEach { run ->
		val (x0, y0, length) = run.destructured.toList().map(String::toInt)
		for (x in x0 until x0 + length) for (dy in 0 until scale) for (dx in 0 until scale) image.setRGB(x * scale + dx, y0 * scale + dy, 0)
	}
	return decode(image)
}
