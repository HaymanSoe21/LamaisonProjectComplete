package com.example.lamaison.ui

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lamaison.BuildConfig
import com.example.lamaison.R
import com.example.lamaison.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SignupActivity : AppCompatActivity() {

    // --- INTERNAL AUTHORIZATION CONSTANTS ---
    companion object {
        private const val INTERNAL_ADMIN_CODE = "ADM2026SEC"
    }

    private var nrcFrontUri: Uri? = null
    private var nrcBackUri: Uri? = null
    private var passportUri: Uri? = null
    private var currentUploadTarget = ""

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            when (currentUploadTarget) {
                "FRONT" -> {
                    nrcFrontUri = it
                    findViewById<TextView>(R.id.btnUploadNrcFront).text = "✅ Front Selected"
                }
                "BACK" -> {
                    nrcBackUri = it
                    findViewById<TextView>(R.id.btnUploadNrcBack).text = "✅ Back Selected"
                }
                "PASSPORT" -> {
                    passportUri = it
                    findViewById<TextView>(R.id.btnUploadPassport).text = "✅ Passport Selected"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // --- 1. Bind UI Elements ---
        val llMyanmarSection = findViewById<LinearLayout>(R.id.llMyanmarSection)
        val llForeignerSection = findViewById<LinearLayout>(R.id.llForeignerSection)
        val rgCitizenship = findViewById<RadioGroup>(R.id.rgCitizenship)

        val spRole = findViewById<Spinner>(R.id.spRole)
        val spNrcRegion = findViewById<Spinner>(R.id.spNrcRegion)
        val spNrcTownship = findViewById<Spinner>(R.id.spNrcTownship)
        val spNrcType = findViewById<Spinner>(R.id.spNrcType)
        val spForeignerDocType = findViewById<Spinner>(R.id.spForeignerDocType)

        val etSecretCode = findViewById<EditText>(R.id.etSecretCode)
        val tvLoginAction = findViewById<TextView>(R.id.tvLoginAction)
        val btnSignup = findViewById<Button>(R.id.btnSignup)

        val btnUploadNrcFront = findViewById<TextView>(R.id.btnUploadNrcFront)
        val btnUploadNrcBack = findViewById<TextView>(R.id.btnUploadNrcBack)
        val btnUploadPassport = findViewById<TextView>(R.id.btnUploadPassport)

        // --- 2. Populate Static Dropdowns ---
        val roles = arrayOf("Tenant", "Manager", "Admin")
        spRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)

        val adapterType = ArrayAdapter.createFromResource(this, R.array.nrc_types, android.R.layout.simple_spinner_item)
        adapterType.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spNrcType.adapter = adapterType

        val docTypes = arrayOf("PV", "PJ", "PE", "PB", "PS", "PR", "PD", "PO")
        spForeignerDocType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, docTypes)

        // --- 3. Handle Role Selection -> Show/Hide Secret Code Field ---
        spRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedRole = parent?.getItemAtPosition(position).toString()
                if (selectedRole == "Manager" || selectedRole == "Admin") {
                    etSecretCode.visibility = View.VISIBLE
                    if (selectedRole == "Manager") {
                        etSecretCode.hint = "Enter Token"
                    } else {
                        etSecretCode.hint = "Enter Admin Secret Code"
                    }
                } else {
                    etSecretCode.visibility = View.GONE
                    etSecretCode.text.clear()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // --- 4. Handle Dynamic NRC Logic (Region -> Township) ---
        val adapterRegion = ArrayAdapter.createFromResource(this, R.array.nrc_regions, android.R.layout.simple_spinner_item)
        adapterRegion.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spNrcRegion.adapter = adapterRegion

        spNrcRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedRegion = parent?.getItemAtPosition(position).toString()
                updateTownshipSpinner(selectedRegion, spNrcTownship)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // --- 5. Handle Citizenship Switching ---
        rgCitizenship.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbMyanmar) {
                llMyanmarSection.visibility = View.VISIBLE
                llForeignerSection.visibility = View.GONE
            } else {
                llMyanmarSection.visibility = View.GONE
                llForeignerSection.visibility = View.VISIBLE
            }
        }

        // --- 6. Handle Image Upload Clicks ---
        btnUploadNrcFront.setOnClickListener { currentUploadTarget = "FRONT"; pickImageLauncher.launch("image/*") }
        btnUploadNrcBack.setOnClickListener { currentUploadTarget = "BACK"; pickImageLauncher.launch("image/*") }
        btnUploadPassport.setOnClickListener { currentUploadTarget = "PASSPORT"; pickImageLauncher.launch("image/*") }

        tvLoginAction.paintFlags = tvLoginAction.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        tvLoginAction.setOnClickListener { finish() }

        // --- 7. HANDLE SIGN UP ACTION & REGEX VALIDATIONS ---
        btnSignup.setOnClickListener {
            val fullName = findViewById<EditText>(R.id.etFullName).text.toString().trim()
            val rawUsername = findViewById<EditText>(R.id.etUsername).text.toString().trim()
            val email = findViewById<EditText>(R.id.etGmail).text.toString().trim()
            val phone = findViewById<EditText>(R.id.etPhone).text.toString().trim()
            val password = findViewById<EditText>(R.id.etPassword).text.toString().trim()
            val secretCode = etSecretCode.text.toString().trim().uppercase()
            val role = spRole.selectedItem.toString()

            val isMyanmar = rgCitizenship.checkedRadioButtonId == R.id.rbMyanmar
            val nationality = if (isMyanmar) "Myanmar" else "Foreigner"

            val identityDocumentType = if (isMyanmar) "NRC" else spForeignerDocType.selectedItem.toString()
            val identityID = if (isMyanmar) {
                val region = spNrcRegion.selectedItem.toString()
                val township = spNrcTownship.selectedItem.toString()
                val type = spNrcType.selectedItem.toString()
                val number = findViewById<EditText>(R.id.etNrcNumber).text.toString().trim()
                if (number.isEmpty()) "" else "$region$township$type$number"
            } else {
                findViewById<EditText>(R.id.etPassportNumber).text.toString().trim()
            }

            // A. Required Fields Check
            if (fullName.isEmpty() || rawUsername.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || identityID.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // B. Username Format Regex (3-15 alphanumeric/underscores)
            val cleanUsername = rawUsername.lowercase()
            val usernameRegex = "^[a-z0-9_]{3,15}$".toRegex()
            if (!cleanUsername.matches(usernameRegex)) {
                Toast.makeText(this, "Username must be 3-15 chars (letters, numbers, underscores only).", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // C. Email Format Regex
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid Gmail / Email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // D. Phone Format Regex (Starts with 09 or +959, followed by exactly 9 digits)
            val phoneRegex = "^(09|\\+959)[2-9]\\d{6,10}$".toRegex()
            if (!phone.matches(phoneRegex)) {
                Toast.makeText(this, "Phone must start with 09 or +959 followed by 7-11 digits.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // E. Role Validation Check
            if (role == "Admin") {
                if (secretCode != INTERNAL_ADMIN_CODE) {
                    Toast.makeText(this, "Invalid Admin Authorization Code!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            } else if (role == "Manager") {
                // Must start with the new LaMaison token format
                if (!secretCode.startsWith("LM-MGR-") || secretCode.length < 10) {
                    Toast.makeText(this, "Invalid token!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            }

            // F. Image Verification Check
            if (isMyanmar && (nrcFrontUri == null || nrcBackUri == null)) {
                Toast.makeText(this, "Please select both Front and Back NRC photos.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (!isMyanmar && passportUri == null) {
                Toast.makeText(this, "Please select your passport photo page.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // --- 8. PRE-FLIGHT CHECKS & EXECUTION ---
            lifecycleScope.launch {
                btnSignup.isEnabled = false
                btnSignup.text = "Verifying..."

                val db = FirebaseFirestore.getInstance()
                val usernameRef = db.collection("USERNAMES").document(cleanUsername)
                var managerTokenRef: DocumentReference? = null

                try {
                    // STEP 1: Verify Manager Token BEFORE uploading images!
                    if (role == "Manager") {
                        btnSignup.text = "Verifying Security Token..."
                        managerTokenRef = db.collection("MANAGER_TOKENS").document(secretCode)
                        val tokenSnap = managerTokenRef.get().await()

                        if (!tokenSnap.exists() || tokenSnap.getString("status") != "ACTIVE") {
                            Toast.makeText(this@SignupActivity, "❌ ERROR: Invalid, Expired, or Revoked Security Token.", Toast.LENGTH_LONG).show()
                            resetSignupButton(btnSignup)
                            return@launch
                        }
                    }

                    // STEP 2: Check Username
                    btnSignup.text = "Checking Username..."
                    val usernameSnapshot = usernameRef.get().await()
                    if (usernameSnapshot.exists()) {
                        Toast.makeText(this@SignupActivity, "❌ Username '@$cleanUsername' is already taken! Please choose another.", Toast.LENGTH_LONG).show()
                        resetSignupButton(btnSignup)
                        return@launch
                    }

                    // STEP 3: Image Uploads
                    btnSignup.text = "Uploading Photos..."
                    val proofUrls = mutableListOf<String>()

                    if (isMyanmar) {
                        uploadToImgBB(getBase64FromUri(nrcFrontUri!!))?.let { proofUrls.add(it) }
                        uploadToImgBB(getBase64FromUri(nrcBackUri!!))?.let { proofUrls.add(it) }
                    } else {
                        uploadToImgBB(getBase64FromUri(passportUri!!))?.let { proofUrls.add(it) }
                    }

                    if (proofUrls.isEmpty()) {
                        Toast.makeText(this@SignupActivity, "Image upload failed. Check internet connection.", Toast.LENGTH_SHORT).show()
                        resetSignupButton(btnSignup)
                        return@launch
                    }

                    // STEP 4: Create Firebase Auth Account
                    btnSignup.text = "Creating Account..."
                    val authResult = FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).await()
                    val userId = authResult.user?.uid ?: throw Exception("Failed to retrieve User ID.")

                    // STEP 5: ATOMIC TRANSACTION (Saves profile & burns token instantly)
                    db.runTransaction { transaction ->

                        if (transaction.get(usernameRef).exists()) {
                            throw Exception("Username was taken just now! Try another.")
                        }

                        // Burn the Manager Token if applicable
                        if (role == "Manager" && managerTokenRef != null) {
                            val freshToken = transaction.get(managerTokenRef)
                            if (freshToken.getString("status") != "ACTIVE") {
                                throw Exception("Security token was consumed by someone else during verification!")
                            }

                            val tokenUpdates = mapOf(
                                "status" to "USED",
                                "usedBy" to userId,
                                "usedAt" to System.currentTimeMillis()
                            )
                            transaction.update(managerTokenRef, tokenUpdates)
                        }

                        // Claim username
                        transaction.set(usernameRef, hashMapOf("uid" to userId, "createdAt" to System.currentTimeMillis()))

                        // Save master user profile
                        val newUser = User(
                            userID = userId,
                            username = cleanUsername,
                            role = role,
                            fullName = fullName,
                            email = email,
                            phoneNumber = phone,
                            nationality = nationality,
                            identityDocumentType = identityDocumentType,
                            identityID = identityID,
                            identityProofURLs = proofUrls,
                            profilePicURL = "",
                            createdAt = System.currentTimeMillis()
                        )
                        transaction.set(db.collection("USERS").document(userId), newUser)
                        null
                    }.await()

                    Toast.makeText(this@SignupActivity, "🎉 Account & @$cleanUsername created successfully!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                    finish()

                } catch (e: Exception) {
                    Toast.makeText(this@SignupActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    resetSignupButton(btnSignup)
                }
            }
        }
    }

    private fun resetSignupButton(button: Button) {
        button.isEnabled = true
        button.text = "Sign Up"
    }

    private fun getBase64FromUri(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private suspend fun uploadToImgBB(base64Image: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.IMGBB_API_KEY
                val url = URL("https://api.imgbb.com/1/upload?key=$apiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true

                val postData = "image=" + URLEncoder.encode(base64Image, "UTF-8")
                connection.outputStream.write(postData.toByteArray())

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    return@withContext jsonObject.getJSONObject("data").getString("url")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }

    private fun updateTownshipSpinner(region: String, townshipSpinner: Spinner) {
        val arrayResId = when (region) {
            "1/" -> R.array.nrc_townships_region_1
            "2/" -> R.array.nrc_townships_region_2
            "3/" -> R.array.nrc_townships_region_3
            "4/" -> R.array.nrc_townships_region_4
            "5/" -> R.array.nrc_townships_region_5
            "6/" -> R.array.nrc_townships_region_6
            "7/" -> R.array.nrc_townships_region_7
            "8/" -> R.array.nrc_townships_region_8
            "9/" -> R.array.nrc_townships_region_9
            "10/" -> R.array.nrc_townships_region_10
            "11/" -> R.array.nrc_townships_region_11
            "12/" -> R.array.nrc_townships_region_12
            "13/" -> R.array.nrc_townships_region_13
            "14/" -> R.array.nrc_townships_region_14
            else -> R.array.nrc_townships_region_1
        }

        val adapterTownship = ArrayAdapter.createFromResource(this, arrayResId, android.R.layout.simple_spinner_item)
        adapterTownship.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        townshipSpinner.adapter = adapterTownship
    }
}