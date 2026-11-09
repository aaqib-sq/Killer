// Root build — Killer (slice 1).
// Version matrix mirrors the proven TRMX-GUI Android build (ADR-006):
// Gradle 8.7 · AGP 8.5.1 · Kotlin 1.9.24 · Compose compiler 1.5.14 · Compose BOM 2024.06.00
plugins {
    id("com.android.application") version "8.5.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
