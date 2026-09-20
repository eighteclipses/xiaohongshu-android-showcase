# ============================================================
# 小红书 Demo 项目 - 示例图片资源批量下载脚本
# 用法: 在项目根目录执行  powershell -ExecutionPolicy Bypass -File resources\download_resources.ps1
# 作用: 将网上的小红书风格竖版示例图下载到 resources\ 下的分类文件夹
# ============================================================

$ErrorActionPreference = "Continue"
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$BaseDir   = Join-Path $PSScriptRoot "."          # resources 目录
$UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

# 资源清单: [分类, 保存文件名, 来源URL]
$Items = @(
    # ---- 美食 ----
    @("food", "food_01_qiaomai.jpg",   "https://aka.doubaocdn.com/s/xsGVLuou7X"),
    @("food", "food_02_baozai.jpg",    "https://aka.doubaocdn.com/s/07l7u5qNJY"),
    @("food", "food_03_cake.jpg",      "https://aka.doubaocdn.com/s/9uU236ub3z"),
    @("food", "food_04_rice.jpg",      "https://aka.doubaocdn.com/s/ugHVGUUwdL"),
    @("food", "food_05_ramen.jpg",     "https://aka.doubaocdn.com/s/58BHoGBL5G"),
    @("food", "food_06_pastry.jpg",    "https://aka.doubaocdn.com/s/VIOEbr38Ae"),
    # ---- 旅行 ----
    @("travel", "travel_01_lake.jpg",  "https://aka.doubaocdn.com/s/Huv46mPIUi"),
    @("travel", "travel_02_snow.jpg",  "https://aka.doubaocdn.com/s/756jpCqVjR"),
    @("travel", "travel_03_valley.jpg","https://aka.doubaocdn.com/s/tFUElBAbP9"),
    @("travel", "travel_04_river.jpg", "https://aka.doubaocdn.com/s/9rXChztmlb"),
    @("travel", "travel_05_autumn.jpg","https://aka.doubaocdn.com/s/AJNyvyfrhn"),
    @("travel", "travel_06_mirror.jpg","https://aka.doubaocdn.com/s/Qfvur6gXhU"),
    # ---- 穿搭 ----
    @("fashion", "fashion_01_hk.jpg",  "https://aka.doubaocdn.com/s/svXzQ1Qmmz"),
    @("fashion", "fashion_02_sanmu.jpg","https://aka.doubaocdn.com/s/jDTrbCu1Cv"),
    @("fashion", "fashion_03_flower.jpg","https://aka.doubaocdn.com/s/AG30IgDWJW"),
    @("fashion", "fashion_04_grid.jpg", "https://aka.doubaocdn.com/s/5qhGCb4nNU"),
    @("fashion", "fashion_05_blazer.jpg","https://aka.doubaocdn.com/s/9EIwqupUh5"),
    @("fashion", "fashion_06_coat.jpg", "https://aka.doubaocdn.com/s/AYUURJUzk1"),
    # ---- 美妆 ----
    @("beauty", "beauty_01_ysl.jpg",   "https://aka.doubaocdn.com/s/wUNuyMenGo"),
    @("beauty", "beauty_02_byredo.jpg","https://aka.doubaocdn.com/s/i2idg1bB49"),
    @("beauty", "beauty_03_kilian.jpg","https://aka.doubaocdn.com/s/gktAK4jBxS"),
    @("beauty", "beauty_04_still.jpg", "https://aka.doubaocdn.com/s/tMzofaPLru"),
    @("beauty", "beauty_05_intoyou.jpg","https://aka.doubaocdn.com/s/GA4YgKl1ON"),
    # ---- 萌宠 ----
    @("pets", "pets_01_catdog.jpg",    "https://aka.doubaocdn.com/s/kBQiYrJLDH"),
    @("pets", "pets_02_bichon.jpg",    "https://aka.doubaocdn.com/s/t0hEzCs61h"),
    @("pets", "pets_03_xmas.jpg",      "https://aka.doubaocdn.com/s/ew2VLAPaIF"),
    @("pets", "pets_04_toys.jpg",      "https://aka.doubaocdn.com/s/HZPnqGj7qs"),
    @("pets", "pets_05_sit.jpg",       "https://aka.doubaocdn.com/s/SS7wE2ZRIu"),
    # ---- 头像 ----
    @("avatar", "avatar_01_selfie.jpg","https://aka.doubaocdn.com/s/eitLa4DBSS"),
    @("avatar", "avatar_02_grass.jpg", "https://aka.doubaocdn.com/s/4HzX2xDmpA"),
    @("avatar", "avatar_03_train.jpg", "https://aka.doubaocdn.com/s/t9J0icWdJC")
)

$okCount = 0
$failCount = 0
$failList = @()

foreach ($it in $Items) {
    $cat   = $it[0]
    $name  = $it[1]
    $url   = $it[2]
    $dir   = Join-Path $BaseDir $cat
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $out   = Join-Path $dir $name
    try {
        $wc = New-Object System.Net.WebClient
        $wc.Headers.Add("User-Agent", $UserAgent)
        $wc.Headers.Add("Referer", "https://www.xiaohongshu.com/")
        $wc.DownloadFile($url, $out)
        $len = (Get-Item $out).Length
        if ($len -gt 0) {
            Write-Host ("[OK]   {0} ({1} bytes)" -f $name, $len)
            $okCount++
        } else {
            Write-Host ("[EMPTY] {0}" -f $name)
            $failCount++; $failList += $name
        }
    } catch {
        Write-Host ("[FAIL] {0} -> {1}" -f $name, $_.Exception.Message)
        $failCount++; $failList += $name
    }
}

Write-Host ""
Write-Host ("==== 完成: 成功 {0} 张, 失败 {1} 张 ====" -f $okCount, $failCount)
if ($failList.Count -gt 0) {
    Write-Host "失败清单:"
    $failList | ForEach-Object { Write-Host ("  - " + $_) }
    Write-Host "提示: 部分来源链接可能已过期，可重新运行脚本或更换链接重试。"
}
