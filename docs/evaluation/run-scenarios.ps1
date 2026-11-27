param([string]$Date = "", [string]$Base = "http://localhost:8080")
[Console]::OutputEncoding = [Text.Encoding]::UTF8
if ($Date -eq "") { $Date = (Get-Date).AddDays(3).ToString("yyyy-MM-dd") }
$cases = @(
    @{ name = "A-가족"; startPointId = "anguk-stn"; startTime = "14:00"; endTime = "19:00"; themes = @("cafe", "history"); companion = "family"; partySize = 4 },
    @{ name = "A-연인"; startPointId = "anguk-stn"; startTime = "14:00"; endTime = "19:00"; themes = @("cafe", "history"); companion = "couple"; partySize = 2 },
    @{ name = "A-친구"; startPointId = "anguk-stn"; startTime = "14:00"; endTime = "19:00"; themes = @("cafe", "history"); companion = "friends"; partySize = 3 },
    @{ name = "A-혼자"; startPointId = "anguk-stn"; startTime = "14:00"; endTime = "19:00"; themes = @("cafe", "history"); companion = "solo"; partySize = 1 },
    @{ name = "B-가족"; startPointId = "gyeongbokgung-stn"; startTime = "17:00"; endTime = "21:00"; themes = @("culture", "walk", "food"); companion = "family"; partySize = 4 },
    @{ name = "B-연인"; startPointId = "gyeongbokgung-stn"; startTime = "17:00"; endTime = "21:00"; themes = @("culture", "walk", "food"); companion = "couple"; partySize = 2 },
    @{ name = "B-친구"; startPointId = "gyeongbokgung-stn"; startTime = "17:00"; endTime = "21:00"; themes = @("culture", "walk", "food"); companion = "friends"; partySize = 3 },
    @{ name = "B-혼자"; startPointId = "gyeongbokgung-stn"; startTime = "17:00"; endTime = "21:00"; themes = @("culture", "walk", "food"); companion = "solo"; partySize = 1 }
)
$stamp = Get-Date -Format "HHmmss"
$rows = foreach ($c in $cases) {
    $body = @{ startPointId = $c.startPointId; date = $Date; startTime = $c.startTime; endTime = $c.endTime; transport = "walk"; themes = $c.themes; companion = $c.companion; partySize = $c.partySize } | ConvertTo-Json -Compress
    $tmp = Join-Path $env:TEMP "scn-$stamp.json"
    [IO.File]::WriteAllText($tmp, $body, (New-Object Text.UTF8Encoding($false)))
    $out = Join-Path $env:TEMP "scn-$stamp-out.json"
    curl.exe -s -X POST "$Base/recommend" -H "Content-Type: application/json" --data-binary "@$tmp" -o $out
    $j = $null
    try { $j = Get-Content $out -Raw -Encoding UTF8 | ConvertFrom-Json } catch { }
    if ($null -eq $j -or $null -eq $j.courses) {
        [pscustomobject]@{ 시나리오 = $c.name; 코스수 = 0; 장소수 = ""; 출처 = ""; 코스1 = "ERROR"; 총이동분 = ""; 노트 = "" }
        Write-Host "$($c.name) ERROR"
        continue
    }
    $first = $j.courses | Select-Object -First 1
    $moves = ($j.courses | ForEach-Object { ($_.legs | Measure-Object -Property minutes -Sum).Sum }) -join "/"
    [pscustomobject]@{
        시나리오 = $c.name
        코스수 = @($j.courses).Count
        장소수 = (($j.courses | ForEach-Object { @($_.places).Count }) -join "/")
        출처 = (($j.courses | ForEach-Object { $_.source }) -join "/")
        코스1 = (($first.places | ForEach-Object { $_.name }) -join " > ")
        총이동분 = $moves
        노트 = (($j.notes.PSObject.Properties | ForEach-Object { $_.Value }) -join " | ")
    }
    Write-Host "$($c.name) 코스 $(@($j.courses).Count)개"
}
$rows | Export-Csv "$PSScriptRoot\scenario-result-$stamp.csv" -NoTypeInformation -Encoding UTF8
$rows | Format-Table 시나리오, 코스수, 장소수, 출처, 총이동분 -AutoSize
"날짜: $Date, 결과 파일: scenario-result-$stamp.csv"
