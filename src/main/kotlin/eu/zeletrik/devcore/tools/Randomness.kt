package eu.zeletrik.devcore.tools

import java.security.SecureRandom

/** The one cryptographically secure source for anything that might be used as a secret or an identifier. */
val secureRandom = SecureRandom()
