# Gates: Dev A Wave 2 core integration

OWNS: GATES.md, src/main/java/com/groupa13/disasterrelief/model/core/**, src/main/java/com/groupa13/disasterrelief/dao/core/**, src/main/java/com/groupa13/disasterrelief/service/core/**, src/main/java/com/groupa13/disasterrelief/controller/DisasterController.java, src/main/java/com/groupa13/disasterrelief/controller/ZoneController.java, src/main/java/com/groupa13/disasterrelief/controller/ShelterController.java, src/main/java/com/groupa13/disasterrelief/controller/ResourceController.java, src/main/java/com/groupa13/disasterrelief/controller/InventoryController.java, src/test/java/com/groupa13/disasterrelief/core/**

Scope: Complete Dev A's Wave 2 models, prepared-JDBC DAOs, transactional services, five asynchronous database-backed controllers, atomic inventory/threshold behavior, low-stock listener boundary, and owned verification without editing another developer's files.

- [x] G0: this Wave 2 ledger defines valid executable completion checks
  CHECK: node /home/hrishinandan/.codex/skills/unlazy/scripts/gate-lint.mjs GATES.md >/dev/null 2>&1
  EXPECT: /^$/
  EVIDENCE: automatic-evidence=v1; definition-sha256=41a73781c40836c623bd05a9053283c18634a0ffd718df78d21489d866fc5358; exit=0; EXPECT=matched; output-sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855; output-bytes=0; shell=/bin/sh; cwd=/home/hrishinandan/Desktop/Project/disaster-relief-allocation-system; path=a772928f6be7/24 entries

- [x] G1: core persistence integration proves CRUD, hierarchy/cycle safety, atomic inventory replacement, concurrency, and below-threshold filtering on PostgreSQL 15
  CHECK: GRADLE_USER_HOME=/tmp/dras-wave2-gradle ./gradlew test --no-daemon --tests 'com.groupa13.disasterrelief.core.CoreDomainIntegrationTest' >/dev/null 2>&1
  EXPECT: /^$/
  EVIDENCE: automatic-evidence=v1; definition-sha256=d7360d0364215cb5d10a606322c8fdf32d42aa45d5212d130019daeccc61811b; exit=0; EXPECT=matched; output-sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855; output-bytes=0; shell=/bin/sh; cwd=/home/hrishinandan/Desktop/Project/disaster-relief-allocation-system; path=a772928f6be7/24 entries

- [x] G2: service validation, SQL error translation, and low-stock listener contracts pass their focused tests
  CHECK: GRADLE_USER_HOME=/tmp/dras-wave2-gradle ./gradlew test --no-daemon --tests 'com.groupa13.disasterrelief.core.CoreServiceTest' --tests 'com.groupa13.disasterrelief.core.LowStockNotificationTest' >/dev/null 2>&1
  EXPECT: /^$/
  EVIDENCE: automatic-evidence=v1; definition-sha256=15cee9d52185687708235cc6ce769327347994797703f0ec62f822efa51b3ba5; exit=0; EXPECT=matched; output-sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855; output-bytes=0; shell=/bin/sh; cwd=/home/hrishinandan/Desktop/Project/disaster-relief-allocation-system; path=a772928f6be7/24 entries

- [x] G3: all five Dev A controllers compile against services, contain no sample-data or placeholder backend path, and satisfy the controller source contract tests
  CHECK: GRADLE_USER_HOME=/tmp/dras-wave2-gradle ./gradlew test --no-daemon --tests 'com.groupa13.disasterrelief.core.CoreControllerContractTest' >/dev/null 2>&1
  EXPECT: /^$/
  EVIDENCE: automatic-evidence=v1; definition-sha256=49ccc0ed80196a35855e3808fc6a4b4c7102dd5a1fe8483f383d0ec186627b8a; exit=0; EXPECT=matched; output-sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855; output-bytes=0; shell=/bin/sh; cwd=/home/hrishinandan/Desktop/Project/disaster-relief-allocation-system; path=a772928f6be7/24 entries

- [x] G4: the complete repository test suite and clean application build pass with the Wave 2 sources
  CHECK: GRADLE_USER_HOME=/tmp/dras-wave2-gradle ./gradlew clean test build --no-daemon >/dev/null 2>&1
  EXPECT: /^$/
  EVIDENCE: automatic-evidence=v1; definition-sha256=b971dc35dfa2c19fc1052b43d5d7f15d0a6245bf06874ca78a379d3e781f15e2; exit=0; EXPECT=matched; output-sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855; output-bytes=0; shell=/bin/sh; cwd=/home/hrishinandan/Desktop/Project/disaster-relief-allocation-system; path=a772928f6be7/24 entries

- [x] G5: final ownership and domain review confirms Wave 2 is complete without Dev B/C scope changes
  EVIDENCE: Reviewed on 2026-10-02 after the focused PostgreSQL 15 and controller tests. Changes are confined to GATES.md and Dev A-owned core models, DAOs, services, five controllers, and core tests; no Dev B/C source, migration, FXML, or documentation file was edited. The review covered prepared statements, transaction boundaries, nullable JDBC mapping, hierarchy-cycle serialization, occupancy-safe deletion, controller validation/asynchrony, atomic inventory replacement, strict below-threshold filtering, unpooled LISTEN lifecycle, safe UI messages, and committed-secret scanning.
