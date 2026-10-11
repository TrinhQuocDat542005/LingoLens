param([string]$ApiBaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$base = $ApiBaseUrl.TrimEnd('/')
function Send-Json([string]$Path, $Payload, $Headers = @{}) {
    Invoke-RestMethod "$base$Path" -Method Post -Headers $Headers -ContentType 'application/json' -Body ($Payload | ConvertTo-Json -Depth 10)
}
function Check([bool]$Condition, [string]$Message) { if (!$Condition) { throw $Message } }

# Run against a disposable development stack; each run creates an isolated learner.
$health = Invoke-RestMethod "$base/actuator/health"
Check ($health.status -eq 'UP') 'API is not healthy'
$email = 'review-smoke-' + [guid]::NewGuid().ToString() + '@example.com'
$password = 'SmokePassword123!'
$null = Send-Json '/api/v1/auth/register' @{email=$email;password=$password;name='Review Smoke'}
$login = Send-Json '/api/v1/auth/login' @{email=$email;password=$password}
$headers = @{Authorization='Bearer ' + $login.data.accessToken}
$account = (Invoke-RestMethod "$base/api/v1/users/me" -Headers $headers).data.id
$published = (Invoke-RestMethod "$base/api/v1/vocabularies/book" -Headers $headers).data
Check ($published.word -eq 'book') 'Published vocabulary seed is missing'
$time = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$content = @{id='book';word='book';translation=$published.translation;partOfSpeech=$published.partOfSpeech;definition=$published.definition;
    level=$published.level;synonyms=@($published.synonyms);exampleSentence=$published.exampleSentence;exampleSentenceB2=$published.exampleSentenceB2}
$null = Send-Json '/api/v1/sync/operations' @{accountId=$account;operationId=[guid]::NewGuid().ToString();kind='SAVE_WORD';payload=@{word='book';content=$content;savedAt=$time;firstLearnedAt=$time}} $headers
$operation = @{accountId=$account;operationId=[guid]::NewGuid().ToString();kind='REVIEW_WORD';payload=@{word='book';grade='GOOD';reviewedAt=$time}}
1..2 | ForEach-Object {
    $receipt = Send-Json '/api/v1/sync/operations' $operation $headers
    Check ($receipt.data.operationId -eq $operation.operationId) 'Receipt ID does not match'
}
$snapshot = (Invoke-RestMethod "$base/api/v1/sync/reviews?accountId=$account" -Headers $headers).data
Check (@($snapshot.events).Count -eq 1) 'Retry duplicated review history'
Check ($snapshot.cards[0].repetitions -eq 1) 'Retry advanced schedule twice'
Check ($snapshot.cards[0].dueAt -eq ($time + 86400000)) 'Unexpected shared scheduling result'
$null = Send-Json '/api/v1/sync/operations' @{accountId=$account;operationId=[guid]::NewGuid().ToString();kind='DELETE_WORD';payload=@{word='book'}} $headers
$operation.operationId = [guid]::NewGuid().ToString(); $operation.payload.grade = 'AGAIN'
$null = Send-Json '/api/v1/sync/operations' $operation $headers
$learning = (Invoke-RestMethod "$base/api/v1/sync/learning?accountId=$account" -Headers $headers).data
Check ($learning[0].deleted -eq $true) 'Delayed review restored a deleted word'
$snapshot = (Invoke-RestMethod "$base/api/v1/sync/reviews?accountId=$account" -Headers $headers).data
Check (@($snapshot.events).Count -eq 2) 'Delayed review history is missing'
Check ($snapshot.cards[0].lapses -eq 1) 'Again did not record a lapse'
[pscustomobject]@{health=$health.status;duplicateReplay='passed';sharedSchedule='passed';deletedHistory='passed';reviewEvents=@($snapshot.events).Count}
