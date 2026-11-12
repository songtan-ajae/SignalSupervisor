# 시그널 감독관 · 0.1.0

**팀 이제뭐하조** · Pleos Connect(Android Automotive)용 방향지시등 습관 코칭 앱

회전하거나 차선을 바꿀 때 깜빡이를 **제때, 충분히, 알맞은 길이로** 켰는지 판정해 운전자에게 알려 주는 앱입니다. 운행이 끝나고 5분 이상 정차하면 **100점 만점의 "깜빡이 점수"** 를 정산합니다. 깜빡이를 넣어야 했던 순간에는 빌트인캠·사이드미러 영상과 BSM(후측방 경고) 상태를 함께 기록해 두므로, 나중에 그때 주변 상황을 다시 볼 수 있습니다.

> **모의 데이터 고지**: 이 시제품의 차량 신호(깜빡이·핸들·속도), BSM, 카메라 영상, 점수 연동은 모두 **시뮬레이션**입니다. Pleos 공개 SDK에는 차속·BSM·카메라 API가 없어 어댑터 뒤에 모의 소스를 두었습니다. 실제 차량 제어나 현대차·기아 운전 점수 반영을 하지 않습니다.

![주행 화면과 알림 카드](docs/images/01_drive_card.png)

---

## 주요 기능

| 기능 | 설명 |
|---|---|
| **실시간 판정** | 회전·차선 변경마다 깜빡이를 켠 거리, 점등 시간, 끈 시점을 판정합니다. |
| **알림 카드** | 앱이 화면에 있을 때만 Pleos 시스템 알림 카드 모양으로 짧게 알려 줍니다. 음성은 쓰지 않습니다. 앱이 화면에 없으면 기록만 합니다. |
| **BSM 기록** | 조작 시점에 후측방 경고가 켜져 있었는지 기록하고, 위험했으면 "위험" 등급으로 판정합니다. |
| **전후 클립** | 판정 시점 앞뒤의 빌트인캠·사이드미러 짧은 영상(모의)을 저장합니다. 개수를 넘으면 오래된 것부터 지웁니다(FIFO). 핀으로 고정한 클립은 지우지 않습니다. |
| **운행 정산** | 5분 이상 정차하면 운행을 마감하고 100점에서 감점해 점수를 냅니다. 결과는 Pleos 알림 센터에도 남깁니다. |
| **7일 리포트** | 최근 7일 점수를 거리 가중 평균으로 보여 주고, 가장 자주 나온 문제를 코칭 포인트로 짚어 줍니다. |
| **주행 중 잠금** | 주행 중에는 리포트·기록·설정 탭을 잠가 운전에 집중하게 합니다. |
| **다크/라이트 테마** | 기본은 차량 시스템 설정을 따릅니다. |

## 판정 기준

기준은 도로교통법을 참고했습니다(`core/JudgeConfig.kt`).

| 항목 | 기준 |
|---|---|
| 미리 켜는 거리 | 일반도로 **30 m** 전, 고속도로 **100 m** 전 |
| 최소 점등 시간 | **2초** 이상, 조작이 끝날 때까지 유지 |
| 켜둠 | 조작이 끝난 뒤 **10초** 넘게 켜 두면 "켜둠" |
| 조작 감지 | 카메라 차선 넘김 > 내비 회전 지점 > 핸들 각도 순으로 결합해 판단 |
| 정산 | 운행 후 **5분 이상 정차**하면 정산 |

| 등급 | 뜻 | 감점 |
|---|---|---|
| 정상 | 기준을 모두 지킴 | 0 |
| 켜둠 | 조작 후 너무 오래 켜 둠 | −1 |
| 짧음 | 2초 미만으로 켰거나 조작 중 끔 | −2 |
| 늦음 | 기준 거리보다 늦게 켬 | −3 |
| 미점등 | 켜지 않음 | −5 |
| 위험 | 정상이 아닌데(미점등·늦음·짧음) 조작 시점에 그 방향 BSM 경고가 켜져 있었음 | −10 |

정상으로 켰더라도 BSM 경고가 켜져 있었으면 감점 없이 "주의" 메모를 남깁니다.

## 화면

### 주행 (다크)
속도, 도로 종류별 기준 거리, 좌우 BSM 상태, 이번 운행 판정 수, 감지 근거(카메라 차선·내비·핸들)를 보여 줍니다.

![주행 화면 다크](docs/images/02_drive_dark.png)

### 정산 리포트
운행 점수와 등급별 개수, 감점된 이벤트 목록을 보여 줍니다. 이벤트를 누르면 영상과 상세가 열립니다.

![정산 리포트](docs/images/03_report.png)

### 기록 · 클립 상세
빌트인캠과 사이드미러 영상(모의), 깜빡이·BSM·조작 타임라인, 켠 거리, 점등 시간, BSM 상태, 감지 근거를 한 화면에서 확인합니다. 중요한 클립은 핀으로 보관할 수 있습니다.

![클립 상세](docs/images/04_clip_detail.png)

### 최근 7일
일별 점수와 거리 가중 평균, 등급별 횟수, 이번 주 코칭 포인트를 보여 줍니다.

![7일 리포트](docs/images/05_week.png)

### 설정
클립 보관 개수, 위험 알림음(차량 자체 경고음과 겹치지 않도록 기본은 꺼짐), 화면 테마를 바꿀 수 있습니다.

![설정](docs/images/06_settings.png)

### 시뮬레이터 (개발용)
실제 차량 신호가 없으므로 시나리오 재생과 수동 조작으로 앱을 시험합니다. 재생 속도, 정차 5분 자동 빨리 감기, 7일 샘플 기록 채우기를 지원합니다.

![시뮬레이터](docs/images/07_simulator.png)

| # | 시나리오 | 내용 | 기대 점수 |
|---|---|---|---|
| S1 | 모범 운전 | 일반도로 · 차선 변경 + 좌회전 | 100 |
| S2 | 깜빡 놓침 | 일반도로 · 무점등 차선 변경 + 늦은 우회전 | 92 |
| S3 | BSM 위험 | 고속도로 · 늦은 점등 + 후측방 경고 | 90 |
| S4 | 깜빡이 길이 | 일반도로 · 짧음 + 켜둠 + 연속 차선 변경 | 97 |

"최근 7일 샘플 기록 채우기"의 7일 점수는 **94.1**입니다. 기대값은 `shared/scenarios.json`에 있고 단위 테스트로 검증합니다.

---

## 빌드

### 요구 사항
- JDK 17
- Android SDK (compileSdk 37 플랫폼, 빌드 도구)
- Gradle 9.3.1 래퍼 포함 · AGP 9.1.0 · Kotlin 2.2.10 · Jetpack Compose
- 앱: minSdk 28, targetSdk 34, 패키지 `dev.ijemwohajo.signalsupervisor`

### 명령

Windows(Git Bash) 기준입니다.

```bash
export JAVA_HOME="<JDK 17 경로>"
export ANDROID_HOME="<Android SDK 경로>"
cd android
./gradlew testDebugUnitTest assembleDebug
```

결과물은 `android/app/build/outputs/apk/debug/app-debug.apk`입니다.

### 릴리스 서명 (선택)

`android/keystore.properties`가 있을 때만 서명이 적용됩니다. 없으면 서명 없는 release APK가 나옵니다. `*.jks`와 `keystore.properties`는 커밋하지 마세요.

```properties
storeFile=signal-release.jks
storePassword=<비밀번호>
keyAlias=signal
keyPassword=<비밀번호>
```

### Pleos 에뮬레이터에 설치

운전자 사용자(user 10)에 설치합니다.

```bash
adb install -r --user 10 android/app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant --user 10 dev.ijemwohajo.signalsupervisor android.permission.POST_NOTIFICATIONS
adb shell am start --user 10 -n dev.ijemwohajo.signalsupervisor/.MainActivity
```

### 아이콘

Pleos 아이콘은 현대자동차그룹 소유 리소스라 이 저장소에 없습니다. 기본 빌드는 `android/app/fallback-icons`의 대체 아이콘을 씁니다. 로컬 Pleos 에뮬레이터에서 추출한 SVG가 있으면 `python android/tools/import_pleos_icons.py <icons-svg 경로>`로 `android/app/pleos-icons/`(Git 제외)를 만들 수 있습니다. 이렇게 빌드한 APK는 **공개 배포하면 안 됩니다.** 위 스크린샷은 Pleos 아이콘을 넣은 로컬 빌드로 찍었습니다.

## 폴더 구성

| 경로 | 내용 |
|---|---|
| `android/app/src/main/java/.../core/` | 판정 엔진(`TripRecorder`), 기준(`JudgeConfig`), 점수, 클립 저장소, 시나리오 재생 |
| `android/app/src/main/java/.../source/` | 차량·내비·카메라 신호 어댑터 인터페이스 |
| `android/app/src/main/java/.../app/` | 앱 상태(`AppGraph`), 저장(`TripRepository`), 정산 알림 |
| `android/app/src/main/java/.../ui/` | Compose 화면 |
| `android/app/src/test/` | 단위 테스트 39개 |
| `android/app/fallback-icons/` | 대체 아이콘 |
| `shared/scenarios.json` | 시나리오·기대값 (앱 assets와 테스트가 공유) |

## 버전

첫 업로드 0.1.0 → 마이너 업데이트 0.1.0_1 → 업데이트 0.1.1
