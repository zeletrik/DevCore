package eu.zeletrik.devcore.shell

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private const val STACK_BYTES = 16L shl 20

/**
 * Runs deeply recursive tool work (parsers and formatters that recurse once per nesting level) on platform threads
 * with a large stack; the caller blocks until the result is in. Tasks take and return plain values and never touch
 * Vaadin components or the session, which belong to the request thread.
 */
object DeepWork {
	private val threads = AtomicInteger()
	private val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()) { task ->
		Thread(null, task, "devcore-deep-${threads.incrementAndGet()}", STACK_BYTES).apply { isDaemon = true }
	}

	/** The task's result, or its own exception or error, rethrown unchanged on the calling thread. */
	fun <T> run(task: () -> T): T {
		val future = pool.submit(Callable(task))
		try {
			return future.get()
		} catch (e: ExecutionException) {
			throw e.cause ?: e
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
			throw IllegalStateException("Interrupted while waiting for a result", e)
		}
	}
}
