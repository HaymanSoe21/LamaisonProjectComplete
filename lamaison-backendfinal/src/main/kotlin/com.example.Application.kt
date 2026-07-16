package com.example

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import java.io.File
import java.io.FileInputStream
import java.util.UUID

fun main() {
    println("🚀 Booting up La Maison Backend...")
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // 1. Initialize Firebase Admin SDK
    try {
        val serviceAccount = FileInputStream("serviceAccountKey.json")
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
            println("✅ Firebase Initialized Successfully!")
        }
    } catch (e: Exception) {
        println("⚠️ Firebase Init Error: ${e.message}")
    }

    // 2. Set up the Routing
    routing {
        post("/api/batch-process/penalties") {
            // 1. EXTRACT MANAGER ID & NEW DUE DATE FROM URL
            val managerId = call.request.queryParameters["managerId"]
            val newDueDateParam = call.request.queryParameters["newDueDate"]?.toLongOrNull()

            if (managerId == null || newDueDateParam == null) {
                println("❌ ERROR: Request rejected. Missing Manager ID or newDueDate.")
                call.respond(HttpStatusCode.BadRequest, "Error: Missing required parameters")
                return@post
            }

            println("🔥 RECEIVED REQUEST FROM MANAGER: $managerId. Starting Batch...")

            val db = FirestoreClient.getFirestore()
            val inputFile = File("batch_input.txt")
            val outputFile = File("batch_output.txt")

            try {
                // A. FETCH DATA
                val ledgersSnapshot = db.collection("FINANCIAL_LEDGERS").get().get()

                inputFile.bufferedWriter().use { writer ->
                    for (ledgerDoc in ledgersSnapshot.documents) {
                        val tenantId = ledgerDoc.getString("tenantID") ?: continue

                        val assignedManagerId = ledgerDoc.getString("managerID")

                        println("🔍 Tenant: $tenantId | Assigned To: $assignedManagerId | Requested By: $managerId")

                        if (assignedManagerId != managerId) {
                            println("⏭️ Skipping Ledger for Tenant $tenantId (Belongs to different manager or missing ID)")
                            continue
                        }

                        println("✅ MATCH! Processing Tenant $tenantId.")

                        val currentBalance = ledgerDoc.getLong("currentBalance") ?: 0L

                        val leaseSnapshot = db.collection("LEASES")
                            .whereEqualTo("tenantID", tenantId)
                            .whereEqualTo("status", "Active")
                            .get().get()

                        if (leaseSnapshot.isEmpty) {
                            println("⚠️ WARNING: Tenant $tenantId has no Active lease! Skipping.")
                            continue
                        }

                        val monthlyRent = leaseSnapshot.documents[0].getLong("monthlyRent") ?: 0L

                        // B. FORMAT FOR COBOL
                        val paddedId = tenantId.padEnd(28, ' ')
                        val safeBalanceNum = currentBalance.coerceAtMost(999999999L)
                        val safeRentNum = monthlyRent.coerceAtMost(999999999L)

                        val paddedBalance = String.format("%+09d", safeBalanceNum)
                        val paddedRent = String.format("%09d", safeRentNum)

                        writer.write("$paddedId$paddedBalance$paddedRent\n")
                    }
                }
                println("📝 Input file created for Manager $managerId's tenants.")

                // C. EXECUTE GNUCOBOL
                val process = ProcessBuilder("./PENALTY-BATCH.exe", inputFile.name, outputFile.name).start()
                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    throw Exception("COBOL Batch failed with exit code $exitCode")
                }
                println("⚙️ COBOL Processing finished successfully.")

                // D. SYNC BACK TO FIREBASE
                val batchWrite = db.batch()
                if (outputFile.exists()) {
                    outputFile.forEachLine { line ->
                        if (line.length >= 37) {
                            val outTenantId = line.substring(0, 28).trim()
                            val newBalance = line.substring(28, 37).toLong()

                            val ledgerRef = db.collection("FINANCIAL_LEDGERS").document(outTenantId)

                            val currentLedger = ledgerRef.get().get()
                            val preBatchBalance = currentLedger.getLong("currentBalance") ?: 0L
                            val calculatedStatus = if (preBatchBalance > 0) "Overdue" else "Ontime"

                            // 4. SAVE NEW DATA BACK TO FIREBASE (No date calculation here anymore)
                            batchWrite.update(ledgerRef, mapOf(
                                "currentBalance" to newBalance,
                                "nextDueDate" to newDueDateParam, // 👈 Supplied straight from Android Frontend
                                "status" to calculatedStatus
                            ))
                        }
                    }
                    batchWrite.commit().get()
                    println("✅ Database updated with COBOL results and New Due Dates!")
                }

                call.respond(HttpStatusCode.OK, "Batch processed successfully")

            } catch (e: Exception) {
                println("❌ ERROR: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            } finally {
                if (inputFile.exists()) inputFile.delete()
                if (outputFile.exists()) outputFile.delete()
            }
        }
    }
}