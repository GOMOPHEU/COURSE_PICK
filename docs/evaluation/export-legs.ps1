param([string]$Date = "", [string]$Base = "http://localhost:8080")
[Console]::OutputEncoding = [Text.Encoding]::UTF8
if ($Date -eq "") { $Date = (Get-Date).AddDays(3).ToString("yyyy-MM-dd") }
$cases = @(
    @{ name = "A-가족"; startPointId = "anguk-stn"; startTime = "14:00"; endTime = "19:00"; themes = @("cafe", "history"); companion = "family"; partySize = 4 },
    @{ name = "A-연인"; startPointId = "anguk-stn"; startTime = "14:00"; endTime = "19:00"; themes = @("cafe", "history"); companion = "couple"; partySize = 2 },
    @{ name = "B-가족"; startPointId = "gyeongbokgung-stn"; startTime = "17:00"; endTime = "21:00"; themes = @("culture", "walk", "food"); companion = "family"; partySize = 4 },
    @{ name = "B-연인"; startPointId = "gyeongbokgung-stn"; startTime = "17:00"; endTime = "21:00"; themes = @("culture", "walk", "food"); companion = "couple"; partySize = 2 }
)
$stamp = Get-Date -Format "HHmmss"
$rows = New-Object System.Collections.ArrayList
foreach ($c in $cases) {
    $body = @{ startPointId = $c.startPointId; date = $Date; startTime = $c.startTime; endTime = $c.endTime; transport = "walk"; themes = $c.themes; companion = $c.companion; partySize = $c.partySize } | ConvertTo-Json -Compress
    $tmp = Join-Path $env:TEMP "leg-$stamp.json"
    [IO.File]::WriteAllText($tmp, $body, (New-Object Text.UTF8Encoding($false)))
    $out = Join-Path $env:TEMP "leg-$stamp-out.json"
    curl.exe -s -X POST "$Base/recommend" -H "Content-Type: application/json" --data-binary "@$tmp" -o $out
    $j = $null
    try { $j = Get-Content $out -Raw -Encoding UTF8 | ConvertFrom-Json } catch { }
    if ($null -eq $j -or $null -eq $j.courses) { Write-Host "$($c.name) ERROR"; continue }
    foreach ($course in $j.courses) {
        $places = @($course.places)
        foreach ($leg in $course.legs) {
            if ($leg.from -eq "start") { continue }
            $a = $places[[int]$leg.from - 1]
            $b = $places[[int]$leg.to - 1]
            $link = "https://map.kakao.com/link/by/walk/{0},{1},{2}/{3},{4},{5}" -f $a.name, $a.lat, $a.lng, $b.name, $b.lat, $b.lng
            [void]$rows.Add([pscustomobject]@{
                시나리오 = $c.name
                코스 = $course.courseId
                출발 = $a.name
                도착 = $b.name
                서버분 = $leg.minutes
                수단 = $leg.mode
                지도앱분 = ""
                차이 = ""
                카카오길찾기 = $link
            })
        }
    }
    Write-Host "$($c.name) 완료"
}
$rows | Export-Csv "$PSScriptRoot\legs-$stamp.csv" -NoTypeInformation -Encoding UTF8
"구간 $($rows.Count)개, 가족 최대 구간: " + (($rows | Where-Object { $_.시나리오 -like "*가족" } | Measure-Object -Property 서버분 -Maximum).Maximum) + "분"
"파일: legs-$stamp.csv"
