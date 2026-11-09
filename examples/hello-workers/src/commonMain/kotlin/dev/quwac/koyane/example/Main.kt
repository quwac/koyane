package dev.quwac.koyane.example

import dev.quwac.koyane.workers.d1.defineD1Worker

fun main() = defineD1Worker { database ->
    createApplication(database)
}
