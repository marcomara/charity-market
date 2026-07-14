$baseUrl = "http://localhost:8080"

Write-Host ""
Write-Host "========================================"
Write-Host "CHARITY MARKET COMPLETE API TEST"
Write-Host "========================================"
Write-Host ""


Write-Host "1. Testing server health..."

$health = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseUrl/q/health"

$health | ConvertTo-Json -Depth 10

if ($health.status -ne "UP") {
    throw "The server health check failed."
}

Write-Host "Server is running correctly."

Write-Host ""
Write-Host "2. Creating donor..."

$testId = Get-Date -Format "yyyyMMddHHmmss"

$donorBody = @{
    name  = "Mario Rossi $testId"
    email = "mario.$testId@example.com"
    phone = "+39 333 1234567"
} | ConvertTo-Json

$donor = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/api/donors" `
    -ContentType "application/json" `
    -Body $donorBody

$donor | Format-List

if ([string]::IsNullOrWhiteSpace($donor.id)) {
    throw "The donor was created without an ID."
}

if ($donor.name -ne "Mario Rossi $testId") {
    throw "The returned donor name is incorrect."
}

Write-Host "Donor created successfully."
Write-Host "Donor ID: $($donor.id)"


Write-Host ""
Write-Host "3. Verifying donor list..."

$donors = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseUrl/api/donors"

$createdDonor = $donors |
        Where-Object { $_.id -eq $donor.id } |
        Select-Object -First 1

if ($null -eq $createdDonor) {
    throw "The created donor was not found in GET /api/donors."
}

$createdDonor | Format-List

Write-Host "Donor list verified successfully."


Write-Host ""
Write-Host "4. Testing donor validation..."

$invalidDonorBody = @{
    name  = ""
    email = "not-an-email"
} | ConvertTo-Json

try {
    Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/api/donors" `
        -ContentType "application/json" `
        -Body $invalidDonorBody

    throw "Invalid donor data was unexpectedly accepted."
}
catch {
    $statusCode = $_.Exception.Response.StatusCode.value__

    if ($statusCode -eq 400) {
        Write-Host "Invalid donor correctly rejected with HTTP 400."
    }
    else {
        Write-Host "Donor validation returned HTTP $statusCode."
        throw
    }
}


Write-Host ""
Write-Host "5. Creating item..."

$itemCode = "BOOK-$testId"

$itemBody = @{
    code                = $itemCode
    name                = "The Hobbit"
    donorId             = $donor.id
    condition           = "GOOD"
    suggestedPriceCents = 500
} | ConvertTo-Json

$item = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/api/items" `
    -ContentType "application/json" `
    -Body $itemBody

$item | Format-List

if ([string]::IsNullOrWhiteSpace($item.id)) {
    throw "The item was created without an ID."
}

if ($item.donorId -ne $donor.id) {
    throw "The item is not linked to the correct donor."
}

if ($item.status -ne "AVAILABLE") {
    throw "A newly created item should have status AVAILABLE."
}

if ($item.suggestedPriceCents -ne 500) {
    throw "The suggested price is incorrect."
}

Write-Host "Item created successfully."
Write-Host "Item ID: $($item.id)"
Write-Host "Item code: $($item.code)"


Write-Host ""
Write-Host "6. Finding item by ID..."

$itemById = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseUrl/api/items/$($item.id)"

$itemById | Format-List

if ($itemById.id -ne $item.id) {
    throw "GET item by ID returned the wrong item."
}

Write-Host "Item lookup by ID works."


Write-Host ""
Write-Host "7. Finding item by code..."

$itemByCode = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseUrl/api/items/by-code/$itemCode"

$itemByCode | Format-List

if ($itemByCode.id -ne $item.id) {
    throw "GET item by code returned the wrong item."
}

Write-Host "Item lookup by code works."


Write-Host ""
Write-Host "8. Verifying item list..."

$items = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseUrl/api/items"

$createdItem = $items |
        Where-Object { $_.id -eq $item.id } |
        Select-Object -First 1

if ($null -eq $createdItem) {
    throw "The created item was not found in GET /api/items."
}

if ($createdItem.status -ne "AVAILABLE") {
    throw "The item should still be AVAILABLE before the sale."
}

Write-Host "Inventory list verified successfully."


Write-Host ""
Write-Host "9. Testing duplicate item code..."

try {
    Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/api/items" `
        -ContentType "application/json" `
        -Body $itemBody

    throw "A duplicate item code was unexpectedly accepted."
}
catch {
    $statusCode = $_.Exception.Response.StatusCode.value__

    if ($statusCode -eq 409) {
        Write-Host "Duplicate item code correctly rejected with HTTP 409."
    }
    else {
        Write-Host "Duplicate item test returned HTTP $statusCode."
        throw
    }
}


Write-Host ""
Write-Host "10. Creating sale..."

$saleBody = @{
    paymentMethod = "CASH"
    lines = @(
        @{
            itemId          = $item.id
            finalPriceCents = 450
        }
    )
} | ConvertTo-Json -Depth 10

$sale = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/api/sales" `
    -ContentType "application/json" `
    -Body $saleBody

$sale | ConvertTo-Json -Depth 10

if ([string]::IsNullOrWhiteSpace($sale.id)) {
    throw "The sale was created without an ID."
}

if ($sale.totalCents -ne 450) {
    throw "The sale total should be 450 cents."
}

if ($sale.itemCount -ne 1) {
    throw "The sale should contain one item."
}

if ($sale.paymentMethod -ne "CASH") {
    throw "The payment method is incorrect."
}

Write-Host "Sale created successfully."
Write-Host "Sale ID: $($sale.id)"
Write-Host "Sale total: $($sale.totalCents) cents"


Write-Host ""
Write-Host "11. Finding sale by ID..."

$saleById = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseUrl/api/sales/$($sale.id)"

$saleById | ConvertTo-Json -Depth 10

if ($saleById.id -ne $sale.id) {
    throw "GET sale by ID returned the wrong sale."
}

Write-Host "Sale lookup works."


Write-Host ""
Write-Host "12. Verifying sale list..."

$sales = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseUrl/api/sales"

$createdSale = $sales |
        Where-Object { $_.id -eq $sale.id } |
        Select-Object -First 1

if ($null -eq $createdSale) {
    throw "The created sale was not found in GET /api/sales."
}

Write-Host "Sale list verified successfully."


Write-Host ""
Write-Host "13. Verifying item status after sale..."

$soldItem = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseUrl/api/items/$($item.id)"

$soldItem | Format-List

if ($soldItem.status -ne "SOLD") {
    throw "The item should have status SOLD after the sale."
}

Write-Host "Item correctly changed from AVAILABLE to SOLD."

Write-Host ""
Write-Host "14. Testing duplicate sale prevention..."

try {
    Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/api/sales" `
        -ContentType "application/json" `
        -Body $saleBody

    throw "The same item was unexpectedly sold twice."
}
catch {
    $statusCode = $_.Exception.Response.StatusCode.value__

    if ($statusCode -eq 409) {
        Write-Host "Second sale correctly rejected with HTTP 409."
    }
    else {
        Write-Host "Duplicate sale test returned HTTP $statusCode."
        throw
    }
}


Write-Host ""
Write-Host "15. Testing duplicate item inside one sale..."

$secondItemCode = "GAME-$testId"

$secondItemBody = @{
    code                = $secondItemCode
    name                = "Board Game"
    donorId             = $donor.id
    condition           = "LIKE_NEW"
    suggestedPriceCents = 800
} | ConvertTo-Json

$secondItem = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/api/items" `
    -ContentType "application/json" `
    -Body $secondItemBody


$duplicateLineSaleBody = @{
    paymentMethod = "CARD"
    lines = @(
        @{
            itemId          = $secondItem.id
            finalPriceCents = 700
        },
        @{
            itemId          = $secondItem.id
            finalPriceCents = 700
        }
    )
} | ConvertTo-Json -Depth 10

try {
    Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/api/sales" `
        -ContentType "application/json" `
        -Body $duplicateLineSaleBody

    throw "The same item was accepted twice in one sale."
}
catch {
    $statusCode = $_.Exception.Response.StatusCode.value__

    if ($statusCode -eq 400) {
        Write-Host "Duplicate sale line correctly rejected with HTTP 400."
    }
    else {
        Write-Host "Duplicate sale line test returned HTTP $statusCode."
        throw
    }
}

$secondItemAfterFailure = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseUrl/api/items/$($secondItem.id)"

if ($secondItemAfterFailure.status -ne "AVAILABLE") {
    throw "The failed transaction incorrectly modified the item."
}

Write-Host "Transaction rollback verified."

Write-Host ""
Write-Host "========================================"
Write-Host "ALL TESTS COMPLETED SUCCESSFULLY"
Write-Host "========================================"
Write-Host ""
Write-Host "Donor created: $($donor.id)"
Write-Host "Item created:  $($item.id)"
Write-Host "Sale created:  $($sale.id)"
Write-Host "Final item status: $($soldItem.status)"