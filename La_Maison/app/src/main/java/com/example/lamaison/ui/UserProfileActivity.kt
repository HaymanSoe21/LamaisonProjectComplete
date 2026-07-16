package com.example.lamaison.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.Window
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lamaison.BuildConfig
import com.example.lamaison.R
import com.example.lamaison.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class UserProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var ivProfilePic: ImageView
    private lateinit var btnEditPhoto: TextView
    private var currentUserEmail: String = ""
    private var identityProofUrls: List<String> = emptyList()

    private val pickProfileImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uploadProfilePicToImgBB(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        val sessionManager = SessionManager(this)

        // --- Bind UI Elements ---
        val btnBackArrow = findViewById<ImageView>(R.id.btnBackArrow)
        ivProfilePic = findViewById(R.id.ivProfilePic)
        btnEditPhoto = findViewById(R.id.btnEditPhoto)

        // Bind Dynamic Property Access Section
        val tvSectionPropertyAccess = findViewById<TextView>(R.id.tvSectionPropertyAccess)
        val cardPropertyAccess = findViewById<LinearLayout>(R.id.cardPropertyAccess)
        val tvPropertyAccessLabel = findViewById<TextView>(R.id.tvPropertyAccessLabel)

        val tvProfileName = findViewById<TextView>(R.id.tvProfileName)
        val tvProfileUsername = findViewById<TextView>(R.id.tvProfileUsername)
        val tvProfileRole = findViewById<TextView>(R.id.tvProfileRole)
        val tvProfileUnit = findViewById<TextView>(R.id.tvProfileUnit)
        val tvProfileEmail = findViewById<TextView>(R.id.tvProfileEmail)
        val tvProfilePhone = findViewById<TextView>(R.id.tvProfilePhone)
        val tvProfileNationality = findViewById<TextView>(R.id.tvProfileNationality)
        val tvProfileIdentityId = findViewById<TextView>(R.id.tvProfileIdentityId)

        val btnChangePassword = findViewById<LinearLayout>(R.id.btnChangePassword)
        val btnViewDocument = findViewById<LinearLayout>(R.id.btnViewDocument)
        val btnSignOutBottom = findViewById<Button>(R.id.btnSignOutBottom)

        val userId = auth.currentUser?.uid ?: "TENANT_DEMO_001"

        // --- INSTANT LOAD FROM CACHE ---
        val cachedRole = sessionManager.getUserRole() ?: "TENANT"
        tvProfileRole.text = cachedRole.uppercase()

        // Handle Back Arrow (<)
        btnBackArrow.setOnClickListener { finish() }

        // Launch Gallery on Edit Photo Click
        btnEditPhoto.setOnClickListener {
            pickProfileImageLauncher.launch("image/*")
        }

        // Fetch User Profile & Document Data from Firestore
        db.collection("USERS").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val fullName = doc.getString("fullName") ?: "JOHN DOE"
                    tvProfileName.text = fullName.uppercase()

                    val dbUsername = doc.getString("username") ?: ""
                    val displayUsername = if (dbUsername.isNotEmpty()) {
                        dbUsername
                    } else {
                        doc.getString("email")?.substringBefore("@") ?: fullName.lowercase().replace(" ", "")
                    }
                    tvProfileUsername.text = "@${displayUsername.removePrefix("@").lowercase()}"

                    val cloudRole = doc.getString("role") ?: cachedRole
                    tvProfileRole.text = cloudRole.uppercase()

                    currentUserEmail = doc.getString("email") ?: ""
                    tvProfileEmail.text = currentUserEmail.ifEmpty { "N/A" }

                    tvProfilePhone.text = doc.getString("phoneNumber") ?: "N/A"
                    tvProfileNationality.text = doc.getString("nationality") ?: "Unspecified"

                    val urls = doc.get("identityProofURLs") as? List<String>
                    if (urls != null) {
                        identityProofUrls = urls
                    }

                    val profilePicUrl = doc.getString("profilePicURL") ?: ""
                    if (profilePicUrl.isNotEmpty()) {
                        loadImageFromUrl(profilePicUrl, ivProfilePic)
                    }

                    val rawId = doc.getString("identityID") ?: ""
                    if (rawId.length >= 4) {
                        tvProfileIdentityId.text = "•••• •••• ${rawId.takeLast(4)}"
                    } else {
                        tvProfileIdentityId.text = rawId.ifEmpty { "Not provided" }
                    }
                }
            }

        // --- DYNAMIC PROPERTY ACCESS CONFIGURATION ---
        configurePropertyAccessSection(
            role = cachedRole,
            userId = userId,
            tvSectionTitle = tvSectionPropertyAccess,
            cardContainer = cardPropertyAccess,
            tvLabel = tvPropertyAccessLabel,
            tvValue = tvProfileUnit
        )

        // Change Password (Sends Firebase Password Reset Email)
        btnChangePassword.setOnClickListener {
            if (currentUserEmail.isEmpty()) {
                Toast.makeText(this, "No registered email found for this account.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(currentUserEmail)
                .addOnSuccessListener {
                    Toast.makeText(this, "Password reset email sent to $currentUserEmail!", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to send reset email: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        // View Document (Opens In-App Image Modal)
        btnViewDocument.setOnClickListener {
            if (identityProofUrls.isEmpty()) {
                Toast.makeText(this, "No uploaded identity documents found.", Toast.LENGTH_SHORT).show()
            } else {
                showDocumentViewerDialog(identityProofUrls)
            }
        }

        // Handle Sign Out & Clear Session
        btnSignOutBottom.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_signout, null)
            val customDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            customDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialogView.findViewById<Button>(R.id.btnDialogCancel).setOnClickListener {
                customDialog.dismiss()
            }

            dialogView.findViewById<Button>(R.id.btnDialogConfirm).setOnClickListener {
                customDialog.dismiss()
                sessionManager.logout()
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }

            customDialog.show()
        }
    }

    /**
     * Dynamically adapts the Property Access UI card based on whether the user is an Admin, Manager, or Tenant.
     */
    private fun configurePropertyAccessSection(
        role: String,
        userId: String,
        tvSectionTitle: TextView,
        cardContainer: LinearLayout,
        tvLabel: TextView,
        tvValue: TextView
    ) {
        when (role.uppercase()) {
            "ADMIN" -> {
                // 1. ADMIN ROLE: Completely omit the unit section and skip database queries
                tvSectionTitle.visibility = View.GONE
                cardContainer.visibility = View.GONE
            }
            "MANAGER" -> {
                tvSectionTitle.visibility = View.VISIBLE
                cardContainer.visibility = View.VISIBLE
                tvLabel.text = "Managed Portfolio"
                tvValue.text = "View Verified Units →"
                tvValue.setTextColor(android.graphics.Color.parseColor("#2563EB"))

                cardContainer.isClickable = true
                cardContainer.isFocusable = true
                cardContainer.setOnClickListener {
                    Toast.makeText(this, "Opening Approved Inventory...", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, ManagerReportUnitsActivity::class.java)

                    // --- NEW: Tell the destination to hide forms & bottom nav! ---
                    intent.putExtra("VIEW_MODE", "APPROVED_ONLY")

                    startActivity(intent)
                }
            }
            else -> {
                // 3. TENANT ROLE (Default): Display assigned residential unit cleanly
                tvSectionTitle.visibility = View.VISIBLE
                cardContainer.visibility = View.VISIBLE

                tvLabel.text = "Assigned Unit"
                cardContainer.isClickable = false
                cardContainer.isFocusable = false
                tvValue.setTextColor(android.graphics.Color.parseColor("#0F172A")) // Standard dark slate text

                // Execute Firestore query for Tenant's assigned room
                db.collection("UNITS").whereEqualTo("currentTenantID", userId).get()
                    .addOnSuccessListener { querySnapshot ->
                        if (!querySnapshot.isEmpty) {
                            val unitDoc = querySnapshot.documents.first()
                            tvValue.text = unitDoc.getString("unitNumber") ?: "Unassigned"
                        } else {
                            tvValue.text = "None Assigned"
                        }
                    }
            }
        }
    }

    // --- IMGBB PROFILE PHOTO UPLOAD COROUTINE ---
    private fun uploadProfilePicToImgBB(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            btnEditPhoto.text = "⏳ UPLOADING..."
            btnEditPhoto.isEnabled = false

            val base64Image = getBase64FromUri(uri)
            val uploadedUrl = uploadToImgBB(base64Image)

            if (uploadedUrl != null) {
                db.collection("USERS").document(userId)
                    .update("profilePicURL", uploadedUrl)
                    .addOnSuccessListener {
                        Toast.makeText(this@UserProfileActivity, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                        loadImageFromUrl(uploadedUrl, ivProfilePic)
                        btnEditPhoto.text = "EDIT PHOTO"
                        btnEditPhoto.isEnabled = true
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@UserProfileActivity, "Failed to save URL to database.", Toast.LENGTH_SHORT).show()
                        btnEditPhoto.text = "EDIT PHOTO"
                        btnEditPhoto.isEnabled = true
                    }
            } else {
                Toast.makeText(this@UserProfileActivity, "Image upload failed. Check connection.", Toast.LENGTH_SHORT).show()
                btnEditPhoto.text = "EDIT PHOTO"
                btnEditPhoto.isEnabled = true
            }
        }
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

    // --- ZERO-DEPENDENCY ASYNC IMAGE LOADER ---
    private fun loadImageFromUrl(url: String, imageView: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = URL(url).openStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                withContext(Dispatchers.Main) {
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- IN-APP MODAL DIALOG FOR IDENTITY PROOF DOCUMENTS ---
    private fun showDocumentViewerDialog(urls: List<String>) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val title = TextView(this).apply {
            text = "Identity Document Proof"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF0F172A.toInt())
            setPadding(0, 0, 0, 24)
        }
        container.addView(title)

        val scrollView = ScrollView(this)
        val scrollChild = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        urls.forEachIndexed { index, url ->
            val label = TextView(this).apply {
                text = if (urls.size == 1) "Uploaded Document" else "Document Page ${index + 1}"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFF64748B.toInt())
                setPadding(0, 16, 0, 8)
            }
            scrollChild.addView(label)

            val imgView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    650
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(0xFFF8FAFC.toInt())
            }
            scrollChild.addView(imgView)

            loadImageFromUrl(url, imgView)
        }

        scrollView.addView(scrollChild)
        container.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val closeBtn = Button(this).apply {
            text = "Close Viewer"
            setBackgroundColor(0xFF1E293B.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { dialog.dismiss() }
        }
        val btnParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        btnParams.topMargin = 32
        container.addView(closeBtn, btnParams)

        dialog.setContentView(container)
        dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }
}