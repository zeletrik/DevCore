package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.barcode.BarcodeView
import eu.zeletrik.devcore.tools.barcode.Symbology
import eu.zeletrik.devcore.tools.lorem.LoremView
import eu.zeletrik.devcore.tools.password.PasswordView
import eu.zeletrik.devcore.tools.random.RandomNumberView
import eu.zeletrik.devcore.tools.random.RandomPortView
import eu.zeletrik.devcore.tools.qr.QrCodeView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class GeneratorViewsBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `QR - downloads only while a code is shown, WiFi mode swaps the inputs`() {
		val view = navigate(QrCodeView::class.java)
		assertNull(view.code.current)
		assertFalse(view.code.png.isEnabled || view.code.svg.isEnabled)
		test(view.input).setValue("hello")
		assertNotNull(view.code.current)
		assertTrue(view.code.png.isEnabled && view.code.svg.isEnabled && view.code.image.isVisible)
		test(view.wifiMode).selectItem("Wi-Fi network")
		assertFalse(view.input.isVisible)
		assertNull(view.code.current)
		test(view.ssid).setValue("home")
		assertNotNull(view.code.current)
	}

	@Test
	fun `barcode - shows the encoded value, and an error clears the code`() {
		val view = navigate("barcode-generator?input=400638133393", BarcodeView::class.java)
		test(view.symbology).selectItem(Symbology.EAN_13.label)
		assertEquals("4006381333931", view.encoded.value)
		test(view.input).setValue("4006381333932")
		assertEquals("Check digit should be 1", view.input.errorMessage)
		assertNull(view.code.current)
		assertFalse(view.code.png.isEnabled)
	}

	@Test
	fun `generators show output on open, regenerate, and keep the output on invalid options`() {
		val password = navigate(PasswordView::class.java)
		assertEquals(20, password.output.value.length)
		assertEquals("≈ 129 bits each", password.entropy.text)
		val first = password.output.value
		test(password.generateButton).click()
		assertTrue(first != password.output.value)
		val before = password.output.value
		test(password.length).setValue(129)
		assertTrue(password.length.isInvalid)
		assertEquals(before, password.output.value)
		test(password.length).setValue(20)
		test(password.sets).deselectAll()
		assertTrue(password.sets.isInvalid)
		assertTrue(navigate(LoremView::class.java).output.value.startsWith("Lorem ipsum dolor sit amet"))
		assertTrue(navigate(RandomNumberView::class.java).output.value.toLong() in 1..100)
		assertTrue(navigate(RandomPortView::class.java).output.value.toInt() in 1024..65535)
	}

	@Test
	fun `random number errors land on the right field`() {
		val view = navigate(RandomNumberView::class.java)
		val before = view.output.value
		test(view.max).setValue("0")
		assertEquals("Must be at least the minimum", view.max.errorMessage)
		assertEquals(before, view.output.value)
		test(view.max).setValue("9223372036854775808")
		assertTrue(view.max.isInvalid)
		assertEquals(before, view.output.value)
	}
}
