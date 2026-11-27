param([string]$Csv = "review-check-culture.csv")
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$cul = (Get-Content (Join-Path $PSScriptRoot "..\..\cand-culture.json") -Raw -Encoding UTF8 | ConvertFrom-Json).places
$rows = Import-Csv (Join-Path $PSScriptRoot $Csv) -Encoding UTF8
foreach ($r in $rows) {
    $id = ($r.'카카오링크' -split '/')[-1]
    $p = $cul | Where-Object { $_.id -eq $id } | Select-Object -First 1
    $addr = if ($p.roadAddress) { $p.roadAddress } else { $p.address }
    $q = [uri]::EscapeDataString("$($p.name) $addr")
    Write-Host "== $($r.'번호') $($p.name) | $addr"
    try {
        $web = Invoke-WebRequest -Uri "http://localhost:8080/places/search-text?q=$q" -UseBasicParsing
        $ms = New-Object System.IO.MemoryStream
        $web.RawContentStream.CopyTo($ms)
        $text = [System.Text.Encoding]::UTF8.GetString($ms.ToArray())
        $res = $text | ConvertFrom-Json
        $i = 0
        foreach ($c in @($res)) {
            $i++
            Write-Host ("   {0}. {1} | {2}" -f $i, $c.displayName.text, $c.formattedAddress)
        }
    } catch {
        Write-Host "   ERROR $($_.Exception.Message)"
    }
}
