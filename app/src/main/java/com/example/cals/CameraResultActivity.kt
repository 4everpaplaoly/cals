package com.example.cals

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.cals.databinding.ActivityCameraResultBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

// ⭐ Room DB
import com.example.cals.database.MealDatabase
import com.example.cals.database.MealEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraResultBinding
    private val client = OkHttpClient()

    // 🔥 Nutrition API Key
    private val NINJA_KEY = "72gW4DLStC09SubJiCk+DA==3Xv8RMwU1gtno5r7"

    // 🔥 음식 관련 키워드 필터
    private val foodKeywords = listOf(
        "food", "cuisine", "meal", "dish", "snack", "bread", "cake",
        "noodle", "ramen", "pasta", "rice", "sushi", "pizza", "burger",
        "sandwich", "chicken", "pork", "beef", "soup", "stew", "bibimbap",
        "korean", "asian", "dessert"
    )

    // ⭐ DB 객체
    private val db by lazy { MealDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUriString = intent.getStringExtra("imageUri") ?: run {
            finish(); return
        }

        val imageUri = Uri.parse(imageUriString)
        Glide.with(this).load(imageUri).into(binding.recognizedImageView)

        runImageLabeling(imageUri)

        // 다시 촬영하기
        binding.retakeBtn.setOnClickListener { finish() }

        // 네비게이션
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                R.id.nav_camera -> { /* stay */ }
                R.id.nav_report -> {
                    startActivity(Intent(this, ReportActivity::class.java))
                    finish()
                }
            }
            true
        }

        binding.bottomNavigationView.selectedItemId = R.id.nav_camera
    }

    /** -----------------------------
     *  MLKit 이미지 라벨링
     *  ----------------------------- */
    private fun runImageLabeling(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream)
            val image = InputImage.fromBitmap(bitmap, 0)

            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.3f)
                    .build()
            )

            labeler.process(image)
                .addOnSuccessListener { labels ->

                    if (labels.isEmpty()) {
                        showFailure()
                        return@addOnSuccessListener
                    }

                    labels.forEach { Log.e("MLKIT_LABEL", "${it.text} (${it.confidence})") }

                    val filtered = labels.filter { label ->
                        foodKeywords.any { key ->
                            label.text.contains(key, ignoreCase = true)
                        }
                    }

                    val candidates = (
                            filtered.sortedByDescending { it.confidence }.map { it.text } +
                                    labels.sortedByDescending { it.confidence }.map { it.text }
                            )
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(7)

                    if (candidates.isEmpty()) {
                        showFailure()
                        return@addOnSuccessListener
                    }

                    binding.failureMessageTextView.visibility = View.GONE
                    binding.retakeBtn.visibility = View.GONE
                    binding.nutritionCard.visibility = View.GONE

                    binding.recognizedLabelTextView.text =
                        "음식 후보 검색 중…\n${candidates.joinToString(", ")}"
                    binding.foodNameTextView.text = candidates.first()

                    analyzeNutritionWithRetry(candidates, 0)
                }
                .addOnFailureListener {
                    Log.e("MLKIT_FAIL", "$it")
                    showFailure()
                }

        } catch (e: Exception) {
            e.printStackTrace()
            showFailure()
        }
    }

    /** --------------------------------------------
     * Nutrition API 후보 재시도
     * - [] 이면 다음 후보
     * - calories/protein이 premium 문구면 "정보 없음"으로 표시하고 종료(시연용)
     * -------------------------------------------- */
    private fun analyzeNutritionWithRetry(candidates: List<String>, index: Int) {

        if (index >= candidates.size) {
            runOnUiThread { showFailure() }
            return
        }

        val foodName = candidates[index]
        val encoded = URLEncoder.encode(foodName, "UTF-8")
        val url = "https://api.api-ninjas.com/v1/nutrition?query=$encoded"

        Log.e("NUTRITION_TRY", "[$index/${candidates.size}] $foodName -> $url")

        val request = Request.Builder()
            .url(url)
            .addHeader("X-Api-Key", NINJA_KEY.trim())
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e("NUTRITION_ERROR", "[$foodName] $e")
                analyzeNutritionWithRetry(candidates, index + 1)
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string()
                Log.e("NUTRITION_RAW", raw ?: "null")

                if (!response.isSuccessful || raw.isNullOrEmpty()) {
                    analyzeNutritionWithRetry(candidates, index + 1)
                    return
                }

                val arr = JSONArray(raw)
                if (arr.length() == 0) {
                    analyzeNutritionWithRetry(candidates, index + 1)
                    return
                }

                val obj = arr.getJSONObject(0)

                // ✅ 숫자면 Double, 아니면 null (premium 문구 대응)
                val calories = getDoubleIfNumber(obj, "calories") // null이면 premium/누락
                val protein = getDoubleIfNumber(obj, "protein_g") // null이면 premium/누락

                val carbs = getDoubleIfNumber(obj, "carbohydrates_total_g") ?: 0.0
                val sugar = getDoubleIfNumber(obj, "sugar_g") ?: 0.0

                // ✅ 시연용 정책:
                // - calories/protein 숫자 못 받으면 0으로 두지 말고 "정보 없음" 표시 + DB에는 -1로 저장
                val caloriesForDb = calories ?: -1.0
                val proteinForDb = protein ?: -1.0

                runOnUiThread {
                    binding.failureMessageTextView.visibility = View.GONE
                    binding.retakeBtn.visibility = View.GONE
                    binding.recognizedLabelTextView.text = "영양 분석 완료"

                    val kcalText = if (calories != null) "${calories} kcal" else "칼로리 정보 없음"
                    binding.foodNameTextView.text = "$foodName\n$kcalText"

                    binding.nutritionCard.visibility = View.VISIBLE
                    binding.proteinText.text = if (protein != null) "${protein} g" else "단백질 정보 없음"
                    binding.carbsText.text = "${carbs} g"
                    binding.sugarText.text = "${sugar} g"

                    saveToDatabase(foodName, caloriesForDb, proteinForDb, carbs, sugar)
                }
            }
        })
    }

    /** ✅ JSONObject에서 key 값이 숫자일 때만 Double로 변환 */
    private fun getDoubleIfNumber(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return try {
            val v = obj.get(key)
            when (v) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** -------------------------
     * Room DB 저장
     * calories/protein이 못 오면 -1 저장(시연용)
     * ------------------------- */
    private fun saveToDatabase(
        foodName: String,
        calories: Double,
        protein: Double,
        carbs: Double,
        sugar: Double
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())

            val meal = MealEntity(
                date = today,
                foodName = foodName,
                calories = calories,
                protein = protein,
                carbs = carbs,
                sugar = sugar
            )

            db.mealDao().insertMeal(meal)
            Log.e("DB_SAVE", "저장됨: $meal")
        }
    }

    /** -------------------------
     * 인식 실패 UI
     * ------------------------- */
    private fun showFailure() {
        binding.recognizedLabelTextView.text = "인식 실패"
        binding.foodNameTextView.text = ""

        binding.nutritionCard.visibility = View.GONE
        binding.failureMessageTextView.visibility = View.VISIBLE
        binding.retakeBtn.visibility = View.VISIBLE
    }
}
