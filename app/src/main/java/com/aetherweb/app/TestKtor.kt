package com.aetherweb.app
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import java.io.File
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*

suspend fun test(call: ApplicationCall, file: File) {
    call.response.header(HttpHeaders.ContentType, "application/pdf")
    call.respondFile(file)
}
