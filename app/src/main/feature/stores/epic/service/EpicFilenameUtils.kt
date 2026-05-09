package com.winlator.cmod.feature.stores.epic.service

private val REGEX_FILENAME_UNSAFE = Regex("[^a-zA-Z0-9_-]")

/**
 * Replaces any character that isn't ASCII alphanumeric, underscore, or hyphen with an
 * underscore. Intended for turning identifiers (app names, namespaces, catalog ids)
 * into safe filename components.
 */
internal fun String.sanitizeForFilename(): String = REGEX_FILENAME_UNSAFE.replace(this, "_")
