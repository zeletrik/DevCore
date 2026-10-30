package eu.zeletrik.devcore.tools

/** Invalid input in a named field other than the tool's main input; the view puts [message] on that field. */
class FieldError(val field: String, message: String) : IllegalArgumentException(message)
