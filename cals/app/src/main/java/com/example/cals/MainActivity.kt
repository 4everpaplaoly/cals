package com.example.cals

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.cals.databinding.ActivityMainBinding
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var totalKcal: Int = 0
    private var mealList: List<String> = emptyList()

    companion object {
        const val REQUEST_IMAGE_PICK = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setTodayDate()
        loadTodayDummy()
        updateKcalText()
        updateMealView()

        binding.addMealBtn.setOnClickListener {
            openGallery()
        }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {}
                R.id.nav_camera -> {
                    val intent = Intent(this, CameraActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                R.id.nav_report -> {
                    val intent = Intent(this, ReportActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            true
        }

        binding.bottomNavigationView.selectedItemId = R.id.nav_home
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = data?.data
            if (imageUri != null) {
                val intent = Intent(this, CameraResultActivity::class.java)
                intent.putExtra("imageUri", imageUri.toString())
                startActivity(intent)
            }
        }
    }

    private fun predictCalories(imageUri: Uri) {
        val predictedKcal = (100..500).random()
        totalKcal += predictedKcal
        updateKcalText()
        updateMealView()
    }

    private fun setTodayDate() {
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        binding.todayTextView.text = today
    }

    private fun updateKcalText() {
        binding.kcalTextView.text = totalKcal.toString()
    }

    private fun updateMealView() {
        binding.dailyNutrientChart.visibility = View.VISIBLE
        binding.emptyMealTextView.visibility =
            if (mealList.isEmpty() && totalKcal == 0) View.VISIBLE else View.GONE
    }

    private fun loadTodayDummy() {
        val seed = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()).toIntOrNull() ?: 0
        val rng = Random(seed)

        val dummyMeals = listOf(
            "닭가슴살 샐러드",
            "현미밥 + 계란후라이",
            "그릭요거트 + 바나나",
            "회전초밥",
            "순대볶음",
            "라면",
            "김밥"
        )

        val count = rng.nextInt(2, 5)
        mealList = dummyMeals.shuffled(rng).take(count)

        val kcalPerMeal = mealList.map { rng.nextInt(250, 651) }
        totalKcal = kcalPerMeal.sum()

        val protein = rng.nextInt(40, 121).toDouble()
        val carbs = rng.nextInt(120, 321).toDouble()
        val sugar = rng.nextInt(10, 81).toDouble()

        binding.kcalTextView.text = totalKcal.toString()
        updatePieChart(protein, carbs, sugar)
        updateMealView()
    }

    private fun updatePieChart(protein: Double, carbs: Double, sugar: Double) {

        val chart: PieChart = binding.dailyNutrientChart

        val total = protein + carbs + sugar
        val p = if (total > 0) protein.toFloat() else 33f
        val c = if (total > 0) carbs.toFloat() else 33f
        val s = if (total > 0) sugar.toFloat() else 33f

        val entries = listOf(
            PieEntry(p, "단백질"),
            PieEntry(c, "탄수화물"),
            PieEntry(s, "당류")
        )

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#535F52"),
                Color.parseColor("#8C5555"),
                Color.parseColor("#C0D3BD")
            )
            valueTextColor = Color.BLACK
            valueTextSize = 14f
        }

        val pieData = PieData(dataSet)

        chart.data = pieData
        chart.description.isEnabled = false
        chart.setEntryLabelTextSize(12f)
        chart.setUsePercentValues(true)
        chart.animateY(800)
        chart.invalidate()
    }
}
