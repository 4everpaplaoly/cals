package com.example.cals

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.example.cals.databinding.ActivityReportBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setTodayDate()
        setupChart()
        loadWeeklyDummyData()
        loadLocalCalendar()

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                R.id.nav_camera -> {
                    startActivity(Intent(this, CameraActivity::class.java))
                    finish()
                }
                R.id.nav_report -> {}
            }
            true
        }

        binding.bottomNavigationView.selectedItemId = R.id.nav_report
    }

    private fun setTodayDate() {
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        binding.reportTextView.text = dateFormat.format(Date())
    }

    private fun setupChart() {
        binding.weekNutrientChart.apply {
            setNoDataText("주간 데이터가 없어요")
            setNoDataTextColor(Color.GRAY)
            description.isEnabled = false
            setUsePercentValues(false)
            setDrawEntryLabels(true)
            invalidate()
        }
    }

    private fun loadWeeklyDummyData() {
        val seed = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()).toIntOrNull() ?: 0
        val rng = Random(seed)

        val daily = List(7) { rng.nextInt(1200, 2301).toFloat() }
        val avg = daily.average().toInt()
        binding.avgKcalTextView.text = avg.toString()

        val labels = listOf("월", "화", "수", "목", "금", "토", "일")
        val entries = labels.zip(daily).map { (d, kcal) -> PieEntry(kcal, d) }

        val dataSet = PieDataSet(entries, "Weekly Calories").apply {
            colors = listOf(
                Color.parseColor("#535F52"),
                Color.parseColor("#8C5555"),
                Color.parseColor("#C0D3BD"),
                Color.parseColor("#A3B18A"),
                Color.parseColor("#D4A373"),
                Color.parseColor("#6C584C"),
                Color.parseColor("#ADC178")
            )
            valueTextColor = Color.BLACK
            valueTextSize = 14f
        }

        binding.weekNutrientChart.data = PieData(dataSet)
        binding.weekNutrientChart.invalidate()

        binding.recommdedDetailTextView.text = "이번 주는 평균 ${avg}kcal예요. 단백질을 한 끼에 꼭 넣어보세요."
        binding.notRecommendedTextView.text = "단 음료나 야식은 주간 평균을 크게 올리니 가능한 줄여보세요."
    }

    private fun loadLocalCalendar() {
        val webView = binding.googleCalendarView

        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.webChromeClient = WebChromeClient()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        val html = """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <style>
                :root{
                  --bg:#F8F0E6;
                  --card:#FFFFFF;
                  --text:#535F52;
                  --muted:#8C5555;
                  --line:#C0D3BD;
                }
                body{margin:0;background:var(--bg);font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Noto Sans KR",sans-serif;color:var(--text);}
                .wrap{padding:14px;}
                .card{background:var(--card);border:1px solid var(--line);border-radius:16px;box-shadow:0 2px 10px rgba(0,0,0,.06);padding:14px;}
                .head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px;}
                .title{font-size:18px;font-weight:800;letter-spacing:-.2px;}
                .btns{display:flex;gap:8px;}
                button{border:1px solid var(--line);background:#fff;color:var(--text);padding:8px 10px;border-radius:12px;font-weight:700;}
                button:active{transform:scale(.98);}
                table{width:100%;border-collapse:separate;border-spacing:6px;}
                th{font-size:12px;color:var(--muted);font-weight:800;padding:6px 0;}
                td{height:44px;background:#fff;border:1px solid var(--line);border-radius:12px;text-align:center;font-weight:800;}
                td.muted{opacity:.35;}
                td.today{outline:3px solid var(--muted);}
              </style>
            </head>
            <body>
              <div class="wrap">
                <div class="card">
                  <div class="head">
                    <div class="title" id="monthTitle"></div>
                    <div class="btns">
                      <button onclick="prevMonth()">이전</button>
                      <button onclick="goToday()">오늘</button>
                      <button onclick="nextMonth()">다음</button>
                    </div>
                  </div>
                  <table>
                    <thead>
                      <tr>
                        <th>일</th><th>월</th><th>화</th><th>수</th><th>목</th><th>금</th><th>토</th>
                      </tr>
                    </thead>
                    <tbody id="calBody"></tbody>
                  </table>
                </div>
              </div>

              <script>
                const pad = n => (n<10? '0'+n : ''+n);
                let view = new Date();

                function render(){
                  const y = view.getFullYear();
                  const m = view.getMonth();
                  const first = new Date(y, m, 1);
                  const last = new Date(y, m+1, 0);
                  const startDow = first.getDay();
                  const totalDays = last.getDate();

                  document.getElementById('monthTitle').textContent = y + '.' + pad(m + 1);

                  const body = document.getElementById('calBody');
                  body.innerHTML = '';

                  const today = new Date();
                  const isSameMonth = (today.getFullYear()===y && today.getMonth()===m);

                  let day = 1;
                  for(let r=0; r<6; r++){
                    const tr = document.createElement('tr');
                    for(let c=0; c<7; c++){
                      const td = document.createElement('td');
                      const idx = r*7 + c;

                      if(idx < startDow || day > totalDays){
                        td.className = 'muted';
                        td.textContent = '';
                      }else{
                        td.textContent = day;
                        if(isSameMonth && day === today.getDate()){
                          td.classList.add('today');
                        }
                        day++;
                      }
                      tr.appendChild(td);
                    }
                    body.appendChild(tr);
                    if(day > totalDays) break;
                  }
                }

                function prevMonth(){
                  view = new Date(view.getFullYear(), view.getMonth()-1, 1);
                  render();
                }
                function nextMonth(){
                  view = new Date(view.getFullYear(), view.getMonth()+1, 1);
                  render();
                }
                function goToday(){
                  const t = new Date();
                  view = new Date(t.getFullYear(), t.getMonth(), 1);
                  render();
                }

                render();
              </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}
