# Gates: Dev A Wave 1 platform baseline

OWNS: GATES.md, .gitignore, build.gradle, docker-compose.yml, .env.example, config/application.properties.example, database/migrations/**, src/main/java/com/groupa13/disasterrelief/db/**, src/main/java/com/groupa13/disasterrelief/service/core/ServiceResult.java, src/test/java/com/groupa13/disasterrelief/support/**, src/test/java/com/groupa13/disasterrelief/core/**

Scope: Deliver the Dev A Wave 1 PostgreSQL platform baseline using the ratified Wave 0 contract, without implementing Wave 2 DAOs, services, or controller replacement.

- [x] G0: this ledger defines meaningful executable completion checks
  CHECK: node /home/hrishinandan/.codex/skills/unlazy/scripts/gate-lint.mjs GATES.md >/dev/null 2>&1
  EXPECT: /^$/
  EVIDENCE: automatic-evidence=v1; definition-sha256=41a73781c40836c623bd05a9053283c18634a0ffd718df78d21489d866fc5358; exit=0; EXPECT=matched; output-sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855; output-bytes=0; shell=/bin/sh; cwd=/home/hrishinandan/Desktop/Project/disaster-relief-allocation-system; path=464cc6b637b7/24 entries

- [x] G1: Docker Compose and example files resolve the ratified dras PostgreSQL contract
  CHECK: docker compose --env-file .env.example config >/dev/null && node -e 'const fs=require("fs");const files=["docker-compose.yml",".env.example","config/application.properties.example"];const text=files.map(f=>fs.readFileSync(f,"utf8")).join("\n");for(const x of ["postgres:15-alpine","POSTGRES_DB=dras","POSTGRES_USER=dras_app","jdbc:postgresql://localhost:5432/dras","db.username=dras_app","filesystem:database/migrations"])if(!text.includes(x))throw new Error("missing "+x);if(text.includes("drras"))throw new Error("stale database contract")'
  EXPECT: /^$/
  EVIDENCE: automatic-evidence=v1; definition-sha256=2007dc74fa7d5a5e449dce48ebac58876603f93d3063f1b094b92c55910b31b5; exit=0; EXPECT=matched; output-sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855; output-bytes=0; shell=/bin/sh; cwd=/home/hrishinandan/Desktop/Project/disaster-relief-allocation-system; path=464cc6b637b7/24 entries

- [x] G2: fresh PostgreSQL migration, constraint, configuration, and transaction tests pass
  CHECK: GRADLE_USER_HOME=/tmp/dras-wave1-gradle ./gradlew test --no-daemon >/dev/null 2>&1
  EXPECT: /^$/
  EVIDENCE: automatic-evidence=v1; definition-sha256=7444afe2180e9f747cde8f86eaa382c2a342434d36834e7897bec471018fba1c; exit=0; EXPECT=matched; output-sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855; output-bytes=0; shell=/bin/sh; cwd=/home/hrishinandan/Desktop/Project/disaster-relief-allocation-system; path=464cc6b637b7/24 entries

- [x] G3: a clean test and application build passes from the Wave 1 sources
  CHECK: GRADLE_USER_HOME=/tmp/dras-wave1-gradle ./gradlew clean test build --no-daemon >/dev/null 2>&1
  EXPECT: /^$/
  EVIDENCE: automatic-evidence=v1; definition-sha256=ac993b27009daa2341c83ea786703da33c4597be3c55ddbca3aab2392331f51c; exit=0; EXPECT=matched; output-sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855; output-bytes=0; shell=/bin/sh; cwd=/home/hrishinandan/Desktop/Project/disaster-relief-allocation-system; path=464cc6b637b7/24 entries

- [x] G4: implementation files contain the new contract and no stale drras identifiers
  CHECK: node -e 'const fs=require("fs");const files=["docker-compose.yml",".env.example","config/application.properties.example","database/migrations/V002__core_schema.sql","database/migrations/V003__core_constraints_indexes.sql","src/main/java/com/groupa13/disasterrelief/db/Database.java","src/main/java/com/groupa13/disasterrelief/db/DatabaseConfig.java"];const reject=s=>{if(s.includes("drras"))throw new Error("stale contract")};let controlled=false;try{reject("drras")}catch{controlled=true}if(!controlled)throw new Error("negative control failed");const text=files.map(f=>fs.readFileSync(f,"utf8")).join("\n");reject(text);for(const x of ["address VARCHAR(255) NOT NULL","location coordinates","dras.config","dras-pool"])if(!text.includes(x))throw new Error("missing "+x)'
  EXPECT: /^$/
  EVIDENCE: automatic-evidence=v1; definition-sha256=e355a1b4fbe4a357ab338701f2446e51d502f108c9e7d22f80b00c868fa180c8; exit=0; EXPECT=matched; output-sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855; output-bytes=0; shell=/bin/sh; cwd=/home/hrishinandan/Desktop/Project/disaster-relief-allocation-system; path=464cc6b637b7/24 entries

- [x] G5: final review confirms platform-baseline scope, secret-free examples, and preserved later-wave boundaries
  EVIDENCE: Final review on 2026-10-01 after the clean PostgreSQL 15 Testcontainers gate. Changes are limited to the platform baseline, V001–V003, and their tests; committed examples contain placeholders only; Wave 2 models, DAOs, services, listener, and controller work remain untouched.
