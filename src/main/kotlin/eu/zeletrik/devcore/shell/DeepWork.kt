package eu.zeletrik.devcore.shell

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val STACK_BYTES = 16L shl 20
private const val TIME_LIMIT_SECONDS = 10

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

	/** Thrown when a task outlives its time limit; the page shows it like an invalid input. */
	class TooSlow(limit: Duration) : IllegalArgumentException("Stopped after $limit: this input takes too long")

	/**
	 * The task's result, or its own exception or error, rethrown unchanged on the calling thread. A task still running
	 * after [limit] is abandoned with [TooSlow] so the page answers; Java can't stop a computation, so its thread
	 * finishes it in the background, and every task must stay bounded on its own.
	 */
	fun <T> run(limit: Duration = TIME_LIMIT_SECONDS.seconds, task: () -> T): T {
		val future = pool.submit(Callable(task))
		val failure = try {
			return future.get(limit.inWholeMilliseconds, TimeUnit.MILLISECONDS)
		} catch (e: ExecutionException) {
			e.cause ?: e
		} catch (_: TimeoutException) {
			future.cancel(true)
			TooSlow(limit)
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
			IllegalStateException("Interrupted while waiting for a result", e)
		}
		throw failure
	}
}
