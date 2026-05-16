# Quick Start Guide - Multi-Tenant Data Pipeline

## Prerequisites Check
1. ✅ Java 17 installed: `java -version`
2. ✅ Maven installed: `mvn -version`
3. ✅ MySQL running on localhost:3306 (username: root, password: root)

## Setup (5 minutes)

### Step 1: Configure MySQL (if needed)
If your MySQL has different credentials, edit:
```
src/main/resources/application.properties
```

### Step 2: Build Project
```powershell
cd C:\Users\anant\demo2
mvn clean install
```

### Step 3: Run Application
```powershell
mvn spring-boot:run
```

Server starts at: **http://localhost:8084**

---

## Quick Test (2 minutes)

### Test 1: Create a Pipeline
```powershell
$pipeline = @"
{
  "pipelineId": "quick_test_001",
  "pipelineName": "Quick Test Pipeline",
  "tenantId": "tenant1",
  "source": {
    "type": "csv",
    "config": {
      "file_path": "test.csv",
      "delimiter": ",",
      "has_header": true,
      "encoding": "UTF-8"
    }
  },
  "transformations": [
    {
      "type": "filter",
      "config": {
        "condition": "age > 25"
      }
    },
    {
      "type": "map",
      "config": {
        "operations": {
          "name_upper": "UPPER(name)"
        }
      }
    }
  ],
  "destination": {
    "type": "database",
    "config": {
      "table": "processed_customers",
      "mode": "append"
    }
  }
}
"@

Invoke-RestMethod -Uri "http://localhost:8084/pipelines" -Method POST -Body $pipeline -ContentType "application/json"
```

### Test 2: Execute the Pipeline
```powershell
Invoke-RestMethod -Uri "http://localhost:8084/pipelines/quick_test_001/execute" -Method POST
```

### Test 3: Check Results
```powershell
# View job status
Invoke-RestMethod -Uri "http://localhost:8084/jobruns"

# View processed data
Invoke-RestMethod -Uri "http://localhost:8084/pipelines/results"
```

---

## Run Tests
```powershell
mvn test
```

Expected: **17 tests pass** ✅

---

## What's Included

### ✅ Data Ingestion
- CSV files (any delimiter, encoding)
- JSON files (object, array, JSONL)
- Automatic schema inference

### ✅ Transformations
- **Filter**: `age > 25`, `status == 'active' AND score > 50`
- **Map**: `UPPER(name)`, `ADD(price,tax)`, `DATE_FORMAT(date,'yyyy-MM-dd')`
- **Aggregate**: `GROUP BY region`, `SUM(amount)`, `AVG(price)`, `COUNT(*)`

### ✅ Output Options
- MySQL database (batch inserts, append/overwrite/upsert)
- CSV files (with optional gzip compression)
- JSON files (with optional gzip compression)

### ✅ Monitoring
- Real-time status tracking
- Detailed metrics (records read/written/filtered/failed)
- Error logging with row-level details

---

## Sample Pipelines

Ready-to-use examples in `samples/` directory:
1. `pipeline_csv_filter_map.json` - CSV with filtering
2. `pipeline_json_aggregate.json` - JSON aggregation
3. `pipeline_jsonl_to_db.json` - JSONL streaming
4. `pipeline_file_output.json` - Export to file

### Use a Sample
```powershell
$sample = Get-Content samples/pipeline_csv_filter_map.json -Raw
Invoke-RestMethod -Uri "http://localhost:8084/pipelines" -Method POST -Body $sample -ContentType "application/json"
Invoke-RestMethod -Uri "http://localhost:8084/pipelines/pipeline_001/execute" -Method POST
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/pipelines` | Create pipeline |
| GET | `/pipelines` | List all pipelines |
| GET | `/pipelines/{id}` | Get pipeline |
| PUT | `/pipelines/{id}` | Update pipeline |
| DELETE | `/pipelines/{id}` | Delete pipeline |
| POST | `/pipelines/{id}/execute` | Execute pipeline |
| GET | `/jobruns` | List job runs |
| GET | `/jobruns/pipeline/{id}` | Get runs for pipeline |
| GET | `/pipelines/results` | View processed data |

---

## Credentials API

- **POST**: Create a credential. Use when the credential name does not already exist. Example:

```powershell
$cred = @"
{
  "name": "S3_ACCESS_KEY",
  "type": "plain",
  "value": "YOUR_ACCESS_KEY"
}
"@

Invoke-RestMethod -Uri "http://localhost:8084/credentials" -Method POST -Body $cred -ContentType "application/json"
```

- **PUT**: Update an existing credential. Prefer `PUT` when changing credentials to avoid "already exists" errors.

```powershell
$cred = @"
{
  "name": "S3_ACCESS_KEY",
  "type": "plain",
  "value": "NEW_ACCESS_KEY"
}
"@

Invoke-RestMethod -Uri "http://localhost:8084/credentials/S3_ACCESS_KEY" -Method PUT -Body $cred -ContentType "application/json"
```

Note: The application resolves placeholders like `${S3_ACCESS_KEY}` from stored credentials or environment variables. Credential names must match `[A-Za-z0-9_]+`.


## Troubleshooting

### MySQL Connection Error
```
Error: "Could not connect to database"
```
**Fix**: Check MySQL is running and credentials are correct in `application.properties`

### File Not Found
```
Error: "Source file does not exist"
```
**Fix**: Use absolute path or place file in project root directory

### Tests Failing
```
Error: Test failures
```
**Fix**: Ensure H2 dependency is in pom.xml with `<scope>test</scope>`

---

## Next Steps

1. ✅ Create your own pipeline definitions
2. ✅ Process your data files
3. ✅ Monitor execution with JobRun API
4. ✅ Export results to files or database
5. ✅ Review comprehensive documentation in `README.md`

---

**You're all set!** 🚀

For detailed documentation, see:
- `README.md` - Complete guide
- `IMPLEMENTATION_SUMMARY.md` - What was implemented
- `samples/` - Example pipelines

