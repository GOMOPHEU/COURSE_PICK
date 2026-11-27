param([string]$Csv = "eval-sample.csv", [int]$Skip = 0, [int]$Take = 1000)
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$date = (Get-Date).AddDays(3).ToString("yyyy-MM-dd")
$themes = @{ "산책" = "walk"; "음식점" = "food"; "카페" = "cafe"; "문화시설" = "culture"; "관광명소" = "walk" }
$rows = Import-Csv (Join-Path $PSScriptRoot $Csv) -Encoding UTF8 | Select-Object -Skip $Skip -First $Take
$out = foreach ($r in $rows) {
    $theme = $themes[$r.'구분']
    $res = $null
    try {
        $q = [uri]::EscapeDataString($r.'이름')
        $res = Invoke-RestMethod -Uri "http://localhost:8080/places/$($r.'카카오ID')/match?name=$q&theme=$theme&date=$date" -Method Get
    } catch {
        $res = [pscustomobject]@{ decision = "ERROR"; source = ""; reason = $_.Exception.Message; businessStatus = ""; googleMapsUri = "" }
    }
    [pscustomobject]@{
        번호 = $r.'번호'
        구분 = $r.'구분'
        카카오ID = $r.'카카오ID'
        이름 = $r.'이름'
        decision = $res.decision
        source = $res.source
        reason = $res.reason
        businessStatus = $res.businessStatus
        googleMapsUri = $res.googleMapsUri
    }
    Write-Host "$($r.'번호') $($r.'이름') $($res.decision) $($res.reason)"
}
$stamp = Get-Date -Format "HHmmss"
$out | Export-Csv "$PSScriptRoot\match-result-$stamp.csv" -NoTypeInformation -Encoding UTF8
$out | Group-Object 구분 | ForEach-Object {
    $n = $_.Count
    $m = ($_.Group | Where-Object { $_.decision -eq "CHECK_HOURS" }).Count
    "{0}: {1}/{2} ({3}%)" -f $_.Name, $m, $n, [math]::Round(100 * $m / $n)
}
$total = $out.Count
$matched = ($out | Where-Object { $_.decision -eq "CHECK_HOURS" }).Count
"전체: $matched/$total ($([math]::Round(100 * $matched / $total))%)"
$out | Group-Object decision | ForEach-Object { "$($_.Name): $($_.Count)" }
